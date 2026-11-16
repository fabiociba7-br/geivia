package com.geivia.app

/** Argument vectors for repeatable Debian installation and guest-runtime checks. */
internal object LinuxRuntimeCommands {
    fun installDebian(): List<String> = listOf("install", "debian")

    /** Fast direct PRoot smoke test; avoids the upstream distro test-binary deployment. */
    fun testDebianBase(): List<String> = listOf(
        "login", "debian", "--",
        "/bin/sh", "-lc",
        "set -eu; test -x /bin/sh; test -d /etc; test -w /tmp; printf 'GEIVIA_DEBIAN_SHELL_OK\\n'; id; uname -m; printf 'geivia-ok' > /tmp/.geivia-smoke-test; rm -f /tmp/.geivia-smoke-test"
    )

    fun smokeCheckDebian(): List<String> = testDebianBase()
}
