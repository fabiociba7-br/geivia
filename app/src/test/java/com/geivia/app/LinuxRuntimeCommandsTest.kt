package com.geivia.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinuxRuntimeCommandsTest {
    @Test
    fun installTargetsThePinnedDebianAlias() {
        assertEquals(listOf("install", "debian"), LinuxRuntimeCommands.installDebian())
    }

    @Test
    fun baseTestUsesDirectShellInsteadOfTheUpstreamDistroSuite() {
        val command = LinuxRuntimeCommands.testDebianBase()
        assertEquals(listOf("login", "debian", "--", "/bin/sh", "-lc"), command.take(5))
        assertTrue(command.last().contains("test -x /bin/sh"))
        assertTrue(command.last().contains("test -w /tmp"))
        assertTrue(command.last().contains("GEIVIA_DEBIAN_SHELL_OK"))
        assertTrue(command.last().contains("uname -m"))
        assertTrue(command.none { it == "test" || it == "--suite" || it == "distro" })
    }

    @Test
    fun smokeCheckUsesTheSameDirectValidationCommand() {
        assertEquals(LinuxRuntimeCommands.testDebianBase(), LinuxRuntimeCommands.smokeCheckDebian())
    }
}
