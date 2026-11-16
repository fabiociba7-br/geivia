package com.geivia.app

/** Argument vectors for repeatable Debian installation and guest-runtime checks. */
internal object LinuxRuntimeCommands {
    fun installDebian(): List<String> = listOf("install", "debian")

    /** The distro suite validates the rootfs and basic PRoot execution without adding toolchains. */
    fun testDebianBase(): List<String> = listOf("test", "debian", "--suite", "distro")

    /** Runs a small command inside the guest and emits a stable success marker for diagnostics. */
    fun smokeCheckDebian(): List<String> = listOf(
        "login", "debian", "--",
        "/bin/sh", "-lc",
        "test -x /bin/sh && test -d /root && test -w /root && printf 'GEIVIA_DEBIAN_SHELL_OK\\n' && id && uname -m"
    )
}
