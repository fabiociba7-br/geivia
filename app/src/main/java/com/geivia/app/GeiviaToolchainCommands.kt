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
     * Tries independent cache locations. PRoot-backed filesystems can expose
     * rename/ENOENT races in npm's cacache; retrying the same directory is not
     * sufficient, so each fallback uses a fresh cache root.
     */
    /**
     * Installs Firebase CLI using a serialized npm download and isolated caches.
     * The official standalone Linux binary is not selected because its published
     * Linux asset is not architecture-aware and can fail on ARM64 devices.
     */
    private fun firebaseCliInstallCommand(): String = """
        set -eu
        if command -v firebase >/dev/null 2>&1 && firebase --version; then
          echo GEIVIA_FIREBASE_CLI_OK
          exit 0
        fi
        INSTALLED=0
        for NPM_CACHE in /tmp/geivia-npm-cache /root/.cache/geivia-npm /root/.npm; do
          echo "GEIVIA_NPM_CACHE_LOCATION_$NPM_CACHE"
          rm -rf "$NPM_CACHE/_cacache/tmp" || true
          mkdir -p "$NPM_CACHE/_cacache/tmp"
          if npm install --global firebase-tools@latest \
              --cache "$NPM_CACHE" \
              --prefer-online \
              --no-audit \
              --no-fund \
              --fetch-retries=5 \
              --fetch-timeout=120000 \
              --maxsockets=1; then
            if command -v firebase >/dev/null 2>&1 && firebase --version; then
              echo GEIVIA_FIREBASE_CLI_OK
              INSTALLED=1
              break
            fi
          fi
          echo "Firebase CLI installation failed with cache $NPM_CACHE; cleaning before fallback."
          npm cache clean --force --cache "$NPM_CACHE" || true
        done
        if [ "$INSTALLED" -ne 1 ]; then
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
}NPM_CACHE/_cacache/tmp"
          mkdir -p "${'
            if command -v firebase >/dev/null 2>&1 && firebase --version; then
              echo GEIVIA_FIREBASE_CLI_OK
              INSTALLED=1
              break
            fi
          fi
          echo "Firebase CLI installation failed with cache ${'$'}NPM_CACHE; trying a different cache location."
          npm cache clean --force --cache "${'$'}NPM_CACHE" || true
          rm -rf "${'$'}NPM_CACHE/_cacache/tmp"
          mkdir -p "${'$'}NPM_CACHE/_cacache/tmp"
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
            "npm_cache_recovery_v2", "Teste do sistema de arquivos e preparação do cache npm",
            listOf("login", "debian", "--", "/bin/sh", "-lc",
                "set -eu; NPM_CACHE=/tmp/geivia-npm-cache; mkdir -p \"\$NPM_CACHE/_cacache/tmp\" \"\$NPM_CACHE/_cacache/content-v2/sha512\"; printf probe > \"\$NPM_CACHE/_cacache/tmp/rename-probe\"; mv \"\$NPM_CACHE/_cacache/tmp/rename-probe\" \"\$NPM_CACHE/_cacache/content-v2/sha512/rename-probe\"; rm -f \"\$NPM_CACHE/_cacache/content-v2/sha512/rename-probe\"; npm config set cache \"\$NPM_CACHE\" --global; npm cache verify --cache \"\$NPM_CACHE\" || { npm cache clean --force --cache \"\$NPM_CACHE\" || true; mkdir -p \"\$NPM_CACHE/_cacache/tmp\"; }; echo GEIVIA_NPM_CACHE_READY"),
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
}NPM_CACHE/_cacache/tmp"
          if npm install --global firebase-tools@latest \
            --cache "${'
            if command -v firebase >/dev/null 2>&1 && firebase --version; then
              echo GEIVIA_FIREBASE_CLI_OK
              INSTALLED=1
              break
            fi
          fi
          echo "Firebase CLI installation failed with cache ${'$'}NPM_CACHE; trying a different cache location."
          npm cache clean --force --cache "${'$'}NPM_CACHE" || true
          rm -rf "${'$'}NPM_CACHE/_cacache/tmp"
          mkdir -p "${'$'}NPM_CACHE/_cacache/tmp"
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
            "npm_cache_recovery_v2", "Teste do sistema de arquivos e preparação do cache npm",
            listOf("login", "debian", "--", "/bin/sh", "-lc",
                "set -eu; NPM_CACHE=/tmp/geivia-npm-cache; mkdir -p \"\$NPM_CACHE/_cacache/tmp\" \"\$NPM_CACHE/_cacache/content-v2/sha512\"; printf probe > \"\$NPM_CACHE/_cacache/tmp/rename-probe\"; mv \"\$NPM_CACHE/_cacache/tmp/rename-probe\" \"\$NPM_CACHE/_cacache/content-v2/sha512/rename-probe\"; rm -f \"\$NPM_CACHE/_cacache/content-v2/sha512/rename-probe\"; npm config set cache \"\$NPM_CACHE\" --global; npm cache verify --cache \"\$NPM_CACHE\" || { npm cache clean --force --cache \"\$NPM_CACHE\" || true; mkdir -p \"\$NPM_CACHE/_cacache/tmp\"; }; echo GEIVIA_NPM_CACHE_READY"),
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
}NPM_CACHE" \
            --prefer-online \
            --maxsockets=1 \
            --no-audit \
            --no-fund \
            --fetch-retries=5 \
            --fetch-retry-mintimeout=20000 \
            --fetch-retry-maxtimeout=120000; then
            if command -v firebase >/dev/null 2>&1 && firebase --version; then
              echo GEIVIA_FIREBASE_CLI_OK
              INSTALLED=1
              break
            fi
          fi
          echo "Firebase CLI installation failed with cache ${'$'}NPM_CACHE; trying a different cache location."
          npm cache clean --force --cache "${'$'}NPM_CACHE" || true
          rm -rf "${'$'}NPM_CACHE/_cacache/tmp"
          mkdir -p "${'$'}NPM_CACHE/_cacache/tmp"
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
            "npm_cache_recovery_v2", "Teste do sistema de arquivos e preparação do cache npm",
            listOf("login", "debian", "--", "/bin/sh", "-lc",
                "set -eu; NPM_CACHE=/tmp/geivia-npm-cache; mkdir -p \"\$NPM_CACHE/_cacache/tmp\" \"\$NPM_CACHE/_cacache/content-v2/sha512\"; printf probe > \"\$NPM_CACHE/_cacache/tmp/rename-probe\"; mv \"\$NPM_CACHE/_cacache/tmp/rename-probe\" \"\$NPM_CACHE/_cacache/content-v2/sha512/rename-probe\"; rm -f \"\$NPM_CACHE/_cacache/content-v2/sha512/rename-probe\"; npm config set cache \"\$NPM_CACHE\" --global; npm cache verify --cache \"\$NPM_CACHE\" || { npm cache clean --force --cache \"\$NPM_CACHE\" || true; mkdir -p \"\$NPM_CACHE/_cacache/tmp\"; }; echo GEIVIA_NPM_CACHE_READY"),
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
}NPM_CACHE" || true
          rm -rf "${'
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
            "npm_cache_recovery_v2", "Teste do sistema de arquivos e preparação do cache npm",
            listOf("login", "debian", "--", "/bin/sh", "-lc",
                "set -eu; NPM_CACHE=/tmp/geivia-npm-cache; mkdir -p \"\$NPM_CACHE/_cacache/tmp\" \"\$NPM_CACHE/_cacache/content-v2/sha512\"; printf probe > \"\$NPM_CACHE/_cacache/tmp/rename-probe\"; mv \"\$NPM_CACHE/_cacache/tmp/rename-probe\" \"\$NPM_CACHE/_cacache/content-v2/sha512/rename-probe\"; rm -f \"\$NPM_CACHE/_cacache/content-v2/sha512/rename-probe\"; npm config set cache \"\$NPM_CACHE\" --global; npm cache verify --cache \"\$NPM_CACHE\" || { npm cache clean --force --cache \"\$NPM_CACHE\" || true; mkdir -p \"\$NPM_CACHE/_cacache/tmp\"; }; echo GEIVIA_NPM_CACHE_READY"),
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
}NPM_CACHE/_cacache/tmp"
          mkdir -p "${'
            if command -v firebase >/dev/null 2>&1 && firebase --version; then
              echo GEIVIA_FIREBASE_CLI_OK
              INSTALLED=1
              break
            fi
          fi
          echo "Firebase CLI installation failed with cache ${'$'}NPM_CACHE; trying a different cache location."
          npm cache clean --force --cache "${'$'}NPM_CACHE" || true
          rm -rf "${'$'}NPM_CACHE/_cacache/tmp"
          mkdir -p "${'$'}NPM_CACHE/_cacache/tmp"
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
            "npm_cache_recovery_v2", "Teste do sistema de arquivos e preparação do cache npm",
            listOf("login", "debian", "--", "/bin/sh", "-lc",
                "set -eu; NPM_CACHE=/tmp/geivia-npm-cache; mkdir -p \"\$NPM_CACHE/_cacache/tmp\" \"\$NPM_CACHE/_cacache/content-v2/sha512\"; printf probe > \"\$NPM_CACHE/_cacache/tmp/rename-probe\"; mv \"\$NPM_CACHE/_cacache/tmp/rename-probe\" \"\$NPM_CACHE/_cacache/content-v2/sha512/rename-probe\"; rm -f \"\$NPM_CACHE/_cacache/content-v2/sha512/rename-probe\"; npm config set cache \"\$NPM_CACHE\" --global; npm cache verify --cache \"\$NPM_CACHE\" || { npm cache clean --force --cache \"\$NPM_CACHE\" || true; mkdir -p \"\$NPM_CACHE/_cacache/tmp\"; }; echo GEIVIA_NPM_CACHE_READY"),
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
}NPM_CACHE/_cacache/tmp"
          if npm install --global firebase-tools@latest \
            --cache "${'
            if command -v firebase >/dev/null 2>&1 && firebase --version; then
              echo GEIVIA_FIREBASE_CLI_OK
              INSTALLED=1
              break
            fi
          fi
          echo "Firebase CLI installation failed with cache ${'$'}NPM_CACHE; trying a different cache location."
          npm cache clean --force --cache "${'$'}NPM_CACHE" || true
          rm -rf "${'$'}NPM_CACHE/_cacache/tmp"
          mkdir -p "${'$'}NPM_CACHE/_cacache/tmp"
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
            "npm_cache_recovery_v2", "Teste do sistema de arquivos e preparação do cache npm",
            listOf("login", "debian", "--", "/bin/sh", "-lc",
                "set -eu; NPM_CACHE=/tmp/geivia-npm-cache; mkdir -p \"\$NPM_CACHE/_cacache/tmp\" \"\$NPM_CACHE/_cacache/content-v2/sha512\"; printf probe > \"\$NPM_CACHE/_cacache/tmp/rename-probe\"; mv \"\$NPM_CACHE/_cacache/tmp/rename-probe\" \"\$NPM_CACHE/_cacache/content-v2/sha512/rename-probe\"; rm -f \"\$NPM_CACHE/_cacache/content-v2/sha512/rename-probe\"; npm config set cache \"\$NPM_CACHE\" --global; npm cache verify --cache \"\$NPM_CACHE\" || { npm cache clean --force --cache \"\$NPM_CACHE\" || true; mkdir -p \"\$NPM_CACHE/_cacache/tmp\"; }; echo GEIVIA_NPM_CACHE_READY"),
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
}NPM_CACHE" \
            --prefer-online \
            --maxsockets=1 \
            --no-audit \
            --no-fund \
            --fetch-retries=5 \
            --fetch-retry-mintimeout=20000 \
            --fetch-retry-maxtimeout=120000; then
            if command -v firebase >/dev/null 2>&1 && firebase --version; then
              echo GEIVIA_FIREBASE_CLI_OK
              INSTALLED=1
              break
            fi
          fi
          echo "Firebase CLI installation failed with cache ${'$'}NPM_CACHE; trying a different cache location."
          npm cache clean --force --cache "${'$'}NPM_CACHE" || true
          rm -rf "${'$'}NPM_CACHE/_cacache/tmp"
          mkdir -p "${'$'}NPM_CACHE/_cacache/tmp"
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
            "npm_cache_recovery_v2", "Teste do sistema de arquivos e preparação do cache npm",
            listOf("login", "debian", "--", "/bin/sh", "-lc",
                "set -eu; NPM_CACHE=/tmp/geivia-npm-cache; mkdir -p \"\$NPM_CACHE/_cacache/tmp\" \"\$NPM_CACHE/_cacache/content-v2/sha512\"; printf probe > \"\$NPM_CACHE/_cacache/tmp/rename-probe\"; mv \"\$NPM_CACHE/_cacache/tmp/rename-probe\" \"\$NPM_CACHE/_cacache/content-v2/sha512/rename-probe\"; rm -f \"\$NPM_CACHE/_cacache/content-v2/sha512/rename-probe\"; npm config set cache \"\$NPM_CACHE\" --global; npm cache verify --cache \"\$NPM_CACHE\" || { npm cache clean --force --cache \"\$NPM_CACHE\" || true; mkdir -p \"\$NPM_CACHE/_cacache/tmp\"; }; echo GEIVIA_NPM_CACHE_READY"),
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
}NPM_CACHE/_cacache/tmp"
          mkdir -p "${'
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
            "npm_cache_recovery_v2", "Teste do sistema de arquivos e preparação do cache npm",
            listOf("login", "debian", "--", "/bin/sh", "-lc",
                "set -eu; NPM_CACHE=/tmp/geivia-npm-cache; mkdir -p \"\$NPM_CACHE/_cacache/tmp\" \"\$NPM_CACHE/_cacache/content-v2/sha512\"; printf probe > \"\$NPM_CACHE/_cacache/tmp/rename-probe\"; mv \"\$NPM_CACHE/_cacache/tmp/rename-probe\" \"\$NPM_CACHE/_cacache/content-v2/sha512/rename-probe\"; rm -f \"\$NPM_CACHE/_cacache/content-v2/sha512/rename-probe\"; npm config set cache \"\$NPM_CACHE\" --global; npm cache verify --cache \"\$NPM_CACHE\" || { npm cache clean --force --cache \"\$NPM_CACHE\" || true; mkdir -p \"\$NPM_CACHE/_cacache/tmp\"; }; echo GEIVIA_NPM_CACHE_READY"),
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
}NPM_CACHE/_cacache/tmp"
          mkdir -p "${'
            if command -v firebase >/dev/null 2>&1 && firebase --version; then
              echo GEIVIA_FIREBASE_CLI_OK
              INSTALLED=1
              break
            fi
          fi
          echo "Firebase CLI installation failed with cache ${'$'}NPM_CACHE; trying a different cache location."
          npm cache clean --force --cache "${'$'}NPM_CACHE" || true
          rm -rf "${'$'}NPM_CACHE/_cacache/tmp"
          mkdir -p "${'$'}NPM_CACHE/_cacache/tmp"
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
            "npm_cache_recovery_v2", "Teste do sistema de arquivos e preparação do cache npm",
            listOf("login", "debian", "--", "/bin/sh", "-lc",
                "set -eu; NPM_CACHE=/tmp/geivia-npm-cache; mkdir -p \"\$NPM_CACHE/_cacache/tmp\" \"\$NPM_CACHE/_cacache/content-v2/sha512\"; printf probe > \"\$NPM_CACHE/_cacache/tmp/rename-probe\"; mv \"\$NPM_CACHE/_cacache/tmp/rename-probe\" \"\$NPM_CACHE/_cacache/content-v2/sha512/rename-probe\"; rm -f \"\$NPM_CACHE/_cacache/content-v2/sha512/rename-probe\"; npm config set cache \"\$NPM_CACHE\" --global; npm cache verify --cache \"\$NPM_CACHE\" || { npm cache clean --force --cache \"\$NPM_CACHE\" || true; mkdir -p \"\$NPM_CACHE/_cacache/tmp\"; }; echo GEIVIA_NPM_CACHE_READY"),
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
}NPM_CACHE/_cacache/tmp"
          if npm install --global firebase-tools@latest \
            --cache "${'
            if command -v firebase >/dev/null 2>&1 && firebase --version; then
              echo GEIVIA_FIREBASE_CLI_OK
              INSTALLED=1
              break
            fi
          fi
          echo "Firebase CLI installation failed with cache ${'$'}NPM_CACHE; trying a different cache location."
          npm cache clean --force --cache "${'$'}NPM_CACHE" || true
          rm -rf "${'$'}NPM_CACHE/_cacache/tmp"
          mkdir -p "${'$'}NPM_CACHE/_cacache/tmp"
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
            "npm_cache_recovery_v2", "Teste do sistema de arquivos e preparação do cache npm",
            listOf("login", "debian", "--", "/bin/sh", "-lc",
                "set -eu; NPM_CACHE=/tmp/geivia-npm-cache; mkdir -p \"\$NPM_CACHE/_cacache/tmp\" \"\$NPM_CACHE/_cacache/content-v2/sha512\"; printf probe > \"\$NPM_CACHE/_cacache/tmp/rename-probe\"; mv \"\$NPM_CACHE/_cacache/tmp/rename-probe\" \"\$NPM_CACHE/_cacache/content-v2/sha512/rename-probe\"; rm -f \"\$NPM_CACHE/_cacache/content-v2/sha512/rename-probe\"; npm config set cache \"\$NPM_CACHE\" --global; npm cache verify --cache \"\$NPM_CACHE\" || { npm cache clean --force --cache \"\$NPM_CACHE\" || true; mkdir -p \"\$NPM_CACHE/_cacache/tmp\"; }; echo GEIVIA_NPM_CACHE_READY"),
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
}NPM_CACHE" \
            --prefer-online \
            --maxsockets=1 \
            --no-audit \
            --no-fund \
            --fetch-retries=5 \
            --fetch-retry-mintimeout=20000 \
            --fetch-retry-maxtimeout=120000; then
            if command -v firebase >/dev/null 2>&1 && firebase --version; then
              echo GEIVIA_FIREBASE_CLI_OK
              INSTALLED=1
              break
            fi
          fi
          echo "Firebase CLI installation failed with cache ${'$'}NPM_CACHE; trying a different cache location."
          npm cache clean --force --cache "${'$'}NPM_CACHE" || true
          rm -rf "${'$'}NPM_CACHE/_cacache/tmp"
          mkdir -p "${'$'}NPM_CACHE/_cacache/tmp"
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
            "npm_cache_recovery_v2", "Teste do sistema de arquivos e preparação do cache npm",
            listOf("login", "debian", "--", "/bin/sh", "-lc",
                "set -eu; NPM_CACHE=/tmp/geivia-npm-cache; mkdir -p \"\$NPM_CACHE/_cacache/tmp\" \"\$NPM_CACHE/_cacache/content-v2/sha512\"; printf probe > \"\$NPM_CACHE/_cacache/tmp/rename-probe\"; mv \"\$NPM_CACHE/_cacache/tmp/rename-probe\" \"\$NPM_CACHE/_cacache/content-v2/sha512/rename-probe\"; rm -f \"\$NPM_CACHE/_cacache/content-v2/sha512/rename-probe\"; npm config set cache \"\$NPM_CACHE\" --global; npm cache verify --cache \"\$NPM_CACHE\" || { npm cache clean --force --cache \"\$NPM_CACHE\" || true; mkdir -p \"\$NPM_CACHE/_cacache/tmp\"; }; echo GEIVIA_NPM_CACHE_READY"),
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
}NPM_CACHE/_cacache/tmp"
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
            "npm_cache_recovery_v2", "Teste do sistema de arquivos e preparação do cache npm",
            listOf("login", "debian", "--", "/bin/sh", "-lc",
                "set -eu; NPM_CACHE=/tmp/geivia-npm-cache; mkdir -p \"\$NPM_CACHE/_cacache/tmp\" \"\$NPM_CACHE/_cacache/content-v2/sha512\"; printf probe > \"\$NPM_CACHE/_cacache/tmp/rename-probe\"; mv \"\$NPM_CACHE/_cacache/tmp/rename-probe\" \"\$NPM_CACHE/_cacache/content-v2/sha512/rename-probe\"; rm -f \"\$NPM_CACHE/_cacache/content-v2/sha512/rename-probe\"; npm config set cache \"\$NPM_CACHE\" --global; npm cache verify --cache \"\$NPM_CACHE\" || { npm cache clean --force --cache \"\$NPM_CACHE\" || true; mkdir -p \"\$NPM_CACHE/_cacache/tmp\"; }; echo GEIVIA_NPM_CACHE_READY"),
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
}NPM_CACHE/_cacache/tmp"
          mkdir -p "${'
            if command -v firebase >/dev/null 2>&1 && firebase --version; then
              echo GEIVIA_FIREBASE_CLI_OK
              INSTALLED=1
              break
            fi
          fi
          echo "Firebase CLI installation failed with cache ${'$'}NPM_CACHE; trying a different cache location."
          npm cache clean --force --cache "${'$'}NPM_CACHE" || true
          rm -rf "${'$'}NPM_CACHE/_cacache/tmp"
          mkdir -p "${'$'}NPM_CACHE/_cacache/tmp"
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
            "npm_cache_recovery_v2", "Teste do sistema de arquivos e preparação do cache npm",
            listOf("login", "debian", "--", "/bin/sh", "-lc",
                "set -eu; NPM_CACHE=/tmp/geivia-npm-cache; mkdir -p \"\$NPM_CACHE/_cacache/tmp\" \"\$NPM_CACHE/_cacache/content-v2/sha512\"; printf probe > \"\$NPM_CACHE/_cacache/tmp/rename-probe\"; mv \"\$NPM_CACHE/_cacache/tmp/rename-probe\" \"\$NPM_CACHE/_cacache/content-v2/sha512/rename-probe\"; rm -f \"\$NPM_CACHE/_cacache/content-v2/sha512/rename-probe\"; npm config set cache \"\$NPM_CACHE\" --global; npm cache verify --cache \"\$NPM_CACHE\" || { npm cache clean --force --cache \"\$NPM_CACHE\" || true; mkdir -p \"\$NPM_CACHE/_cacache/tmp\"; }; echo GEIVIA_NPM_CACHE_READY"),
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
}NPM_CACHE/_cacache/tmp"
          if npm install --global firebase-tools@latest \
            --cache "${'
            if command -v firebase >/dev/null 2>&1 && firebase --version; then
              echo GEIVIA_FIREBASE_CLI_OK
              INSTALLED=1
              break
            fi
          fi
          echo "Firebase CLI installation failed with cache ${'$'}NPM_CACHE; trying a different cache location."
          npm cache clean --force --cache "${'$'}NPM_CACHE" || true
          rm -rf "${'$'}NPM_CACHE/_cacache/tmp"
          mkdir -p "${'$'}NPM_CACHE/_cacache/tmp"
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
            "npm_cache_recovery_v2", "Teste do sistema de arquivos e preparação do cache npm",
            listOf("login", "debian", "--", "/bin/sh", "-lc",
                "set -eu; NPM_CACHE=/tmp/geivia-npm-cache; mkdir -p \"\$NPM_CACHE/_cacache/tmp\" \"\$NPM_CACHE/_cacache/content-v2/sha512\"; printf probe > \"\$NPM_CACHE/_cacache/tmp/rename-probe\"; mv \"\$NPM_CACHE/_cacache/tmp/rename-probe\" \"\$NPM_CACHE/_cacache/content-v2/sha512/rename-probe\"; rm -f \"\$NPM_CACHE/_cacache/content-v2/sha512/rename-probe\"; npm config set cache \"\$NPM_CACHE\" --global; npm cache verify --cache \"\$NPM_CACHE\" || { npm cache clean --force --cache \"\$NPM_CACHE\" || true; mkdir -p \"\$NPM_CACHE/_cacache/tmp\"; }; echo GEIVIA_NPM_CACHE_READY"),
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
}NPM_CACHE" \
            --prefer-online \
            --maxsockets=1 \
            --no-audit \
            --no-fund \
            --fetch-retries=5 \
            --fetch-retry-mintimeout=20000 \
            --fetch-retry-maxtimeout=120000; then
            if command -v firebase >/dev/null 2>&1 && firebase --version; then
              echo GEIVIA_FIREBASE_CLI_OK
              INSTALLED=1
              break
            fi
          fi
          echo "Firebase CLI installation failed with cache ${'$'}NPM_CACHE; trying a different cache location."
          npm cache clean --force --cache "${'$'}NPM_CACHE" || true
          rm -rf "${'$'}NPM_CACHE/_cacache/tmp"
          mkdir -p "${'$'}NPM_CACHE/_cacache/tmp"
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
            "npm_cache_recovery_v2", "Teste do sistema de arquivos e preparação do cache npm",
            listOf("login", "debian", "--", "/bin/sh", "-lc",
                "set -eu; NPM_CACHE=/tmp/geivia-npm-cache; mkdir -p \"\$NPM_CACHE/_cacache/tmp\" \"\$NPM_CACHE/_cacache/content-v2/sha512\"; printf probe > \"\$NPM_CACHE/_cacache/tmp/rename-probe\"; mv \"\$NPM_CACHE/_cacache/tmp/rename-probe\" \"\$NPM_CACHE/_cacache/content-v2/sha512/rename-probe\"; rm -f \"\$NPM_CACHE/_cacache/content-v2/sha512/rename-probe\"; npm config set cache \"\$NPM_CACHE\" --global; npm cache verify --cache \"\$NPM_CACHE\" || { npm cache clean --force --cache \"\$NPM_CACHE\" || true; mkdir -p \"\$NPM_CACHE/_cacache/tmp\"; }; echo GEIVIA_NPM_CACHE_READY"),
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
