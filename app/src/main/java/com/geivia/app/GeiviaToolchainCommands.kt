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
     * Installs the prebuilt Linux ARM64 Firebase CLI bundle.
     * The archive is built on a native ARM64 runner to avoid slow npm installs on phones.
     */
    private fun firebaseCliInstallCommand(): String = """
        set -eu
        FIREBASE_ROOT=/opt/geivia/firebase
        FIREBASE_ENTRY="${'$'}FIREBASE_ROOT/node_modules/firebase-tools/lib/bin/firebase.js"
        if [ ! -f "${'$'}FIREBASE_ENTRY" ]; then
          ARCHIVE=/tmp/geivia-firebase-cli-arm64.tar.gz
          mkdir -p "${'$'}FIREBASE_ROOT"
          curl -fL --retry 3 --retry-delay 2 \
            "https://github.com/fabiociba7-br/geivia/releases/download/firebase-cli-arm64/firebase-cli-arm64.tar.gz" \
            -o "${'$'}ARCHIVE"
          tar -xzf "${'$'}ARCHIVE" -C "${'$'}FIREBASE_ROOT"
          rm -f "${'$'}ARCHIVE"
        fi
        test -f "${'$'}FIREBASE_ENTRY"
        mkdir -p /usr/local/bin
        cat > /usr/local/bin/firebase <<'GEIVIA_FIREBASE_WRAPPER'
        #!/bin/sh
        exec node /opt/geivia/firebase/node_modules/firebase-tools/lib/bin/firebase.js "${'$'}@"
        GEIVIA_FIREBASE_WRAPPER
        chmod 755 /usr/local/bin/firebase
        firebase --version
        echo GEIVIA_FIREBASE_CLI_OK
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
