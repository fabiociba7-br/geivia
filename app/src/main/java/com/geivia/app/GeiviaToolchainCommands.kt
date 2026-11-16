package com.geivia.app

/** Installs and maintains the default GEIVIA development profile inside Debian. */
internal object GeiviaToolchainCommands {
    fun installDefaultProfile(): List<String> = listOf(
        "login", "debian", "--",
        "/bin/sh", "-lc",
        """
        set -eu
        export DEBIAN_FRONTEND=noninteractive
        apt-get update
        apt-get install -y --no-install-recommends \
          ca-certificates curl wget git openssh-client unzip zip xz-utils tar jq nano less \
          default-jdk-headless nodejs npm python3 python3-pip python3-venv \
          build-essential cmake pkg-config make
        npm config set fund false
        npm config set audit false
        npm install --global firebase-tools@latest
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
        for p in git default-jdk-headless nodejs npm python3 cmake build-essential; do
          echo "PACKAGE:\${'$'}p"
          dpkg-query -W -f='INSTALLED:\${'$'}{Version}\n' "\${'$'}p" 2>/dev/null || echo 'INSTALLED:not-installed'
          apt-cache policy "\${'$'}p" | sed -n 's/^  Candidate: /CANDIDATE:/p'
        done
        echo "PACKAGE:firebase-tools"
        npm list --global firebase-tools --depth=0 2>/dev/null | tail -n 2 || true
        echo "CANDIDATE:\${'$'}(npm view firebase-tools version)"
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
        "set -eu; npm install --global firebase-tools@latest; firebase --version"
    )

    fun updateAll(): List<String> = listOf(
        "login", "debian", "--", "/bin/sh", "-lc",
        """
        set -eu
        export DEBIAN_FRONTEND=noninteractive
        apt-get update
        apt-get upgrade -y
        npm install --global firebase-tools@latest
        echo 'GEIVIA_UPDATE_ALL_OK'
        """.trimIndent()
    )
}
