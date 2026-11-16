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

    @Test
    fun installStagesAreOrderedAndHaveStableCheckpointIds() {
        val stages = GeiviaToolchainCommands.installStages()

        assertTrue(stages.map { it.id } == listOf(
            "dpkg_recovery",
            "apt_update",
            "apt_packages",
            "npm_config",
            "firebase_cli",
            "toolchain_verify"
        ))
        assertTrue(stages.all { it.command.isNotEmpty() && it.timeoutMs > 0 })
        assertTrue(stages.last().command == GeiviaToolchainCommands.verifyDefaultProfile())
    }

    @Test
    fun packageRepairStagePrecedesAptUpdateAndPackageInstall() {
        val stages = GeiviaToolchainCommands.installStages()
        val recovery = stages.first { it.id == "dpkg_recovery" }.command.last()
        val packages = stages.first { it.id == "apt_packages" }.command.last()

        assertTrue(recovery.indexOf("dpkg --configure -a || true") >= 0)
        assertTrue(recovery.indexOf("apt-get -f install -y") > recovery.indexOf("dpkg --configure -a || true"))
        assertTrue(packages.contains("apt-get install -y --no-install-recommends"))
    }

    @Test
    fun firebaseCliStageInstallsTheGlobalFirebaseToolsPackage() {
        val stage = GeiviaToolchainCommands.installStages().first { it.id == "firebase_cli" }

        assertTrue(stage.command.last().contains("npm install --global firebase-tools@latest"))
    }

}
