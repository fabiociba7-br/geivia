package com.geivia.app

import org.junit.Assert.assertTrue
import org.junit.Test

class GeiviaToolchainCommandsTest {
    @Test
    fun interruptedDpkgRecoveryRunsBeforePackageInstallation() {
        val command = GeiviaToolchainCommands.installDefaultProfile().last()

        val tolerantConfigure = command.indexOf("dpkg --configure -a || true")
        val dependencyRepair = command.indexOf("apt-get -f install -y")
        val strictConfigure = command.indexOf("dpkg --configure -a", tolerantConfigure + 1)
        val recoveryComplete = command.indexOf("GEIVIA_PACKAGE_RECOVERY_OK")
        val aptUpdate = command.indexOf("apt-get update")
        val packageInstall = command.indexOf("apt-get install -y --no-install-recommends")

        assertTrue(tolerantConfigure >= 0)
        assertTrue(dependencyRepair > tolerantConfigure)
        assertTrue(strictConfigure > dependencyRepair)
        assertTrue(recoveryComplete > strictConfigure)
        assertTrue(aptUpdate > recoveryComplete)
        assertTrue(packageInstall > aptUpdate)
    }

    @Test
    fun recoveryKeepsNonInteractivePackageConfiguration() {
        val command = GeiviaToolchainCommands.installDefaultProfile().last()

        assertTrue(command.contains("export DEBIAN_FRONTEND=noninteractive"))
        assertTrue(command.contains("GEIVIA_TOOLCHAIN_OK"))
        assertTrue(command.contains("firebase-tools@latest"))
    }
}
