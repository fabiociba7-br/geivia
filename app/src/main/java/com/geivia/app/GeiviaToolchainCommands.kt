package com.geivia.app

/** Installs and maintains the default GEIVIA development profile inside Debian. */
internal object GeiviaToolchainCommands {
    fun installDefaultProfile(): List<String> = listOf(
        "login", "debian", "--",
        "/bin/sh", "-lc",
        """
        set -eu
        export DEBIAN_FRONTEND=noninteractive
        # Recover an interrupted dpkg transaction before starting a new install.
        echo 'GEIVIA_PACKAGE_RECOVERY_BEGIN'
        dpkg --configure -a || true
        apt-get -f install -y
        dpkg --configure -a
        echo 'GEIVIA_PACKAGE_RECOVERY_OK'
        apt-get update
        apt-get install -y --no-install-recommends \
          ca-certificates curl wget git openssh-client unzip zip xz-utils tar jq nano less \
          default-jdk-headless nodejs npm python3 python3-pip python3-venv \
          build-essential cmake pkg-config make
        npm config set fund false
        npm config set audit false
        ${firebaseCliInstallCommand()}
        printf '\nGEIVIA_TOOLCHAIN_VERSIONS\n'
        git --version
        java -version
        node --version
        npm --version
        python3 --version
        firebase --version
        printf 'GEIVIA_TOOLCHAIN_OK\n'
        """.trimIndent()
    )


    /**
     * Uses a dedicated npm cache and retries after cache ENOENT/rename failures.
     * This avoids reusing a potentially damaged default /root/.npm cache.
     */
    private fun firebaseCliInstallCommand(): String = """
        set -eu
        NPM_CACHE=/root/.cache/geivia-npm
        mkdir -p "${'$'}NPM_CACHE/_cacache/tmp"
        npm config set cache "${'$'}NPM_CACHE" --global
        npm cache verify --cache "${'$'}NPM_CACHE" || {
          npm cache clean --force --cache "${'$'}NPM_CACHE" || true
          mkdir -p "${'$'}NPM_CACHE/_cacache/tmp"
        }
        INSTALLED=0
        for attempt in 1 2 3; do
          echo "GEIVIA_FIREBASE_INSTALL_ATTEMPT_${'$'}attempt"
          if npm install --global firebase-tools@latest \
            --cache "${'$'}NPM_CACHE" \
            --prefer-online \
            --fetch-retries=5 \
            --fetch-retry-mintimeout=20000 \
            --fetch-retry-maxtimeout=120000; then
            firebase --version
            echo GEIVIA_FIREBASE_CLI_OK
            INSTALLED=1
            break
          fi
          echo "Firebase CLI install attempt ${'$'}attempt failed; rebuilding the dedicated npm cache."
          npm cache clean --force --cache "${'$'}NPM_CACHE" || true
          rm -rf "${'$'}NPM_CACHE/_cacache/tmp"
          mkdir -p "${'$'}NPM_CACHE/_cacache/tmp"
          sleep "${'$'}attempt"
        done
        if [ "${'$'}INSTALLED" -ne 1 ]; then
          echo GEIVIA_FIREBASE_CLI_FAILED
          exit 1
        fi
    """.trimIndent()

    data class InstallStage(
        val id: String,
        val title: String,
        val command: List<String>,
        val timeoutMs: Long
    )

    /** Small idempotent stages allow the UI to checkpoint and resume after a failure. */
    fun installStages(): List<InstallStage> = listOf(
        InstallStage(
            "dpkg_recovery", "Recuperação do gerenciador de pacotes (dpkg)",
            listOf("login", "debian", "--", "/bin/sh", "-lc",
                "set -eu; export DEBIAN_FRONTEND=noninteractive; dpkg --configure -a || true; apt-get -f install -y; dpkg --configure -a; echo GEIVIA_DPKG_RECOVERY_OK"),
            15 * 60 * 1000L
        ),
        InstallStage(
            "apt_update", "Atualização dos índices do Debian",
            listOf("login", "debian", "--", "/bin/sh", "-lc",
                "set -eu; apt-get update; echo GEIVIA_APT_UPDATE_OK"),
            15 * 60 * 1000L
        ),
        InstallStage(
            "apt_packages", "Instalação dos pacotes básicos",
            listOf("login", "debian", "--", "/bin/sh", "-lc",
                """set -eu; export DEBIAN_FRONTEND=noninteractive; apt-get install -y --no-install-recommends ca-certificates curl wget git openssh-client unzip zip xz-utils tar jq nano less default-jdk-headless nodejs npm python3 python3-pip python3-venv build-essential cmake pkg-config make; echo GEIVIA_APT_PACKAGES_OK"""),
            45 * 60 * 1000L
        ),
        InstallStage(
            "npm_config", "Configuração do npm",
            listOf("login", "debian", "--", "/bin/sh", "-lc",
                "set -eu; npm config set fund false; npm config set audit false; echo GEIVIA_NPM_CONFIG_OK"),
            3 * 60 * 1000L
        ),
        InstallStage(
            "npm_cache_recovery", "Preparação e verificação do cache do npm",
            listOf("login", "debian", "--", "/bin/sh", "-lc",
                "set -eu; NPM_CACHE=/root/.cache/geivia-npm; mkdir -p \"\$NPM_CACHE/_cacache/tmp\"; npm config set cache \"\$NPM_CACHE\" --global; npm cache verify --cache \"\$NPM_CACHE\" || { npm cache clean --force --cache \"\$NPM_CACHE\" || true; mkdir -p \"\$NPM_CACHE/_cacache/tmp\"; }; echo GEIVIA_NPM_CACHE_READY"),
            5 * 60 * 1000L
        ),
        InstallStage(
            "firebase_cli", "Instalação da Firebase CLI com recuperação automática do cache",
            listOf("login", "debian", "--", "/bin/sh", "-lc", firebaseCliInstallCommand()),
            45 * 60 * 1000L
        ),
        InstallStage(
            "toolchain_verify", "Verificação final das ferramentas",
            verifyDefaultProfile(),
            3 * 60 * 1000L
        )
    )

    fun verifyDefaultProfile(): List<String> = listOf(
        "login", "debian", "--",
        "/bin/sh", "-lc",
        "set -eu; command -v git; command -v java; command -v node; command -v npm; command -v python3; command -v firebase; git --version; java -version; node --version; npm --version; python3 --version; firebase --version; printf 'GEIVIA_TOOLCHAIN_OK\\n'"
    )

    fun checkUpdates(): List<String> = listOf(
        "login", "debian", "--", "/bin/sh", "-lc",
        """
        set -eu
        export DEBIAN_FRONTEND=noninteractive
        apt-get update
        echo 'GEIVIA_UPDATE_REPORT_BEGIN'
        echo 'PACKAGE:git'
        dpkg-query -W git 2>/dev/null || echo 'INSTALLED:not-installed'
        apt-cache policy git | sed -n 's/^  Candidate: /CANDIDATE:/p'
        echo 'PACKAGE:default-jdk-headless'
        dpkg-query -W default-jdk-headless 2>/dev/null || echo 'INSTALLED:not-installed'
        apt-cache policy default-jdk-headless | sed -n 's/^  Candidate: /CANDIDATE:/p'
        echo 'PACKAGE:nodejs'
        dpkg-query -W nodejs 2>/dev/null || echo 'INSTALLED:not-installed'
        apt-cache policy nodejs | sed -n 's/^  Candidate: /CANDIDATE:/p'
        echo 'PACKAGE:npm'
        dpkg-query -W npm 2>/dev/null || echo 'INSTALLED:not-installed'
        apt-cache policy npm | sed -n 's/^  Candidate: /CANDIDATE:/p'
        echo 'PACKAGE:python3'
        dpkg-query -W python3 2>/dev/null || echo 'INSTALLED:not-installed'
        apt-cache policy python3 | sed -n 's/^  Candidate: /CANDIDATE:/p'
        echo 'PACKAGE:cmake'
        dpkg-query -W cmake 2>/dev/null || echo 'INSTALLED:not-installed'
        apt-cache policy cmake | sed -n 's/^  Candidate: /CANDIDATE:/p'
        echo 'PACKAGE:build-essential'
        dpkg-query -W build-essential 2>/dev/null || echo 'INSTALLED:not-installed'
        apt-cache policy build-essential | sed -n 's/^  Candidate: /CANDIDATE:/p'
        echo 'PACKAGE:firebase-tools'
        npm list --global firebase-tools --depth=0 2>/dev/null || true
        echo 'FIREBASE_CLI_LATEST_VERSION'
        npm view firebase-tools version
        echo 'GEIVIA_UPDATE_REPORT_END'
        """.trimIndent()
    )

    fun updateDebianPackage(packageName: String): List<String> {
        require(packageName in setOf("git", "default-jdk-headless", "nodejs", "npm", "python3", "cmake", "build-essential"))
        return listOf(
            "login", "debian", "--", "/bin/sh", "-lc",
            "set -eu; export DEBIAN_FRONTEND=noninteractive; apt-get update; apt-get install -y --only-upgrade $packageName"
        )
    }

    fun updateFirebaseCli(): List<String> = listOf(
        "login", "debian", "--", "/bin/sh", "-lc",
        firebaseCliInstallCommand() + "\nfirebase --version"
    )

    fun updateAll(): List<String> = listOf(
        "login", "debian", "--", "/bin/sh", "-lc",
        """
        set -eu
        export DEBIAN_FRONTEND=noninteractive
        apt-get update
        apt-get upgrade -y
        ${firebaseCliInstallCommand()}
        echo 'GEIVIA_UPDATE_ALL_OK'
        """.trimIndent()
    )
}
