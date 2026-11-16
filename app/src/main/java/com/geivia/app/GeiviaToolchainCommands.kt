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
     * Installs Firebase CLI from the public npm registry.
     * npm 11.6.2 is pinned because npm 9 repeatedly failed while renaming cache entries
     * in the GEIVIA Debian/PRoot environment. The cache is isolated and retryable.
     */
    private fun firebaseCliInstallCommand(): String = """
        set -eu
        FIREBASE_ROOT=/opt/geivia/firebase
        FIREBASE_ENTRY="${'$'}FIREBASE_ROOT/node_modules/firebase-tools/lib/bin/firebase.js"
        FIREBASE_VERSION=15.36.0
        NPM_VERSION=11.6.2
        FIREBASE_CACHE=/opt/geivia/npm-cache
        NPM_CLI=/usr/local/lib/node_modules/npm/bin/npm-cli.js

        mkdir -p "${'$'}FIREBASE_ROOT" "${'$'}FIREBASE_CACHE" /usr/local/bin
        export npm_config_cache="${'$'}FIREBASE_CACHE"
        export npm_config_registry=https://registry.npmjs.org/
        npm config set fund false
        npm config set audit false

        # Bootstrap the tested npm version, retrying once with a clean isolated cache.
        if [ "$(npm --version 2>/dev/null || true)" != "${'$'}NPM_VERSION" ]; then
          npm install --global "npm@${'$'}NPM_VERSION" --no-audit --no-fund --prefer-online --maxsockets=1 --cache "${'$'}FIREBASE_CACHE" || {
            echo GEIVIA_NPM_CACHE_RECOVERY
            rm -rf "${'$'}FIREBASE_CACHE"
            mkdir -p "${'$'}FIREBASE_CACHE"
            npm install --global "npm@${'$'}NPM_VERSION" --no-audit --no-fund --prefer-online --maxsockets=1 --cache "${'$'}FIREBASE_CACHE"
          }
        fi

        # Prefer the npm 11 CLI directly so an older /usr/bin/npm cannot shadow it.
        if [ -f "${'$'}NPM_CLI" ] && [ "$(node "${'$'}NPM_CLI" --version)" = "${'$'}NPM_VERSION" ]; then
          npm_run() { node "${'$'}NPM_CLI" "${'$'}@"; }
        else
          npm_run() { npm "${'$'}@"; }
        fi
        ACTUAL_NPM_VERSION="$(npm_run --version)"
        if [ "${'$'}ACTUAL_NPM_VERSION" != "${'$'}NPM_VERSION" ]; then
          echo "GEIVIA_NPM_VERSION_MISMATCH expected=${'$'}NPM_VERSION actual=${'$'}ACTUAL_NPM_VERSION"
          exit 1
        fi

        # Keep a correct existing install; otherwise install and retry once after cache recovery.
        if [ ! -f "${'$'}FIREBASE_ENTRY" ] || [ "$(node "${'$'}FIREBASE_ENTRY" --version 2>/dev/null || true)" != "${'$'}FIREBASE_VERSION" ]; then
          npm_run install --prefix "${'$'}FIREBASE_ROOT" --omit=dev --no-audit --no-fund --prefer-online --maxsockets=1 --cache "${'$'}FIREBASE_CACHE" firebase-tools@"${'$'}FIREBASE_VERSION" || {
            echo GEIVIA_FIREBASE_CACHE_RECOVERY
            rm -rf "${'$'}FIREBASE_CACHE" "${'$'}FIREBASE_ROOT/node_modules"
            mkdir -p "${'$'}FIREBASE_CACHE" "${'$'}FIREBASE_ROOT"
            npm_run install --prefix "${'$'}FIREBASE_ROOT" --omit=dev --no-audit --no-fund --prefer-online --maxsockets=1 --cache "${'$'}FIREBASE_CACHE" firebase-tools@"${'$'}FIREBASE_VERSION"
          }
        fi

        test -f "${'$'}FIREBASE_ENTRY"
        cat > /usr/local/bin/firebase <<'GEIVIA_FIREBASE_WRAPPER'
        #!/bin/sh
        exec node /opt/geivia/firebase/node_modules/firebase-tools/lib/bin/firebase.js "${'$'}@"
        GEIVIA_FIREBASE_WRAPPER
        chmod 755 /usr/local/bin/firebase
        ACTUAL_FIREBASE_VERSION="$(firebase --version)"
        if [ "${'$'}ACTUAL_FIREBASE_VERSION" != "${'$'}FIREBASE_VERSION" ]; then
          echo "GEIVIA_FIREBASE_VERSION_MISMATCH expected=${'$'}FIREBASE_VERSION actual=${'$'}ACTUAL_FIREBASE_VERSION"
          exit 1
        fi
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
        check_apt() {
          package="$1"
          installed="$(dpkg-query -W -f='${'$'{Version}' "$package" 2>/dev/null || true)"
          candidate="$(apt-cache policy "$package" | awk '/Candidate:/{print ${'$'2; exit}')"
          [ -n "$installed" ] || installed="não instalado"
          [ -n "$candidate" ] || candidate="(none)"
          available=0
          if [ "$installed" != "não instalado" ] && [ "$candidate" != "(none)" ] && dpkg --compare-versions "$candidate" gt "$installed"; then
            available=1
          fi
          printf 'GEIVIA_UPDATE|%s|%s|%s|%s\n' "$package" "$installed" "$candidate" "$available"
        }
        check_apt git
        check_apt default-jdk-headless
        check_apt nodejs
        check_apt npm
        check_apt python3
        check_apt cmake
        check_apt build-essential
        installed_firebase="$(firebase --version 2>/dev/null | head -n 1 || true)"
        latest_firebase="$(npm view firebase-tools version)"
        [ -n "$installed_firebase" ] || installed_firebase="não instalado"
        available_firebase=0
        if [ "$installed_firebase" != "não instalado" ] && [ "$latest_firebase" != "$installed_firebase" ] &&
           [ "$(printf '%s\n' "$installed_firebase" "$latest_firebase" | sort -V | tail -n 1)" = "$latest_firebase" ]; then
          available_firebase=1
        fi
        printf 'GEIVIA_UPDATE|firebase-tools|%s|%s|%s\n' "$installed_firebase" "$latest_firebase" "$available_firebase"
        echo GEIVIA_UPDATE_REPORT_END
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
        """
        set -eu
        FIREBASE_ROOT=/opt/geivia/firebase
        FIREBASE_CACHE=/opt/geivia/npm-cache
        NPM_CLI=/usr/local/lib/node_modules/npm/bin/npm-cli.js
        mkdir -p "$FIREBASE_ROOT" "$FIREBASE_CACHE"
        export npm_config_cache="$FIREBASE_CACHE"
        export npm_config_registry=https://registry.npmjs.org/
        if [ -f "$NPM_CLI" ] && [ "$(node "$NPM_CLI" --version)" = "11.6.2" ]; then
          node "$NPM_CLI" install --prefix "$FIREBASE_ROOT" --omit=dev --no-audit --no-fund --prefer-online --maxsockets=1 firebase-tools@latest
        else
          npm install --prefix "$FIREBASE_ROOT" --omit=dev --no-audit --no-fund --prefer-online --maxsockets=1 firebase-tools@latest
        fi
        cat > /usr/local/bin/firebase <<'GEIVIA_FIREBASE_WRAPPER'
        #!/bin/sh
        exec node /opt/geivia/firebase/node_modules/firebase-tools/lib/bin/firebase.js "$@"
        GEIVIA_FIREBASE_WRAPPER
        chmod 755 /usr/local/bin/firebase
        firebase --version
        """.trimIndent()
    )
    fun updateAll(): List<String> = listOf(
        "login", "debian", "--", "/bin/sh", "-lc",
        """
        set -eu
        export DEBIAN_FRONTEND=noninteractive
        apt-get update
        apt-get upgrade -y
        ${updateFirebaseCli().last()}
        echo 'GEIVIA_UPDATE_ALL_OK'
        """.trimIndent()
    )
}
