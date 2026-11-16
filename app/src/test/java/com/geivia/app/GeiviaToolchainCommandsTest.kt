package com.geivia.app

import org.junit.Assert.assertTrue
import org.junit.Test

class GeiviaToolchainCommandsTest {
    @Test
    fun interruptedDpkgRecoveryRunsBeforePackageInstallation() {
        val command = GeiviaToolchainCommands.installDefaultProfile().last()

        val firstConfigure = command.indexOf("dpkg --configure -a")
        val dependencyRepair = command.indexOf("apt-get -f install -y")
        val secondConfigure = command.indexOf("dpkg --configure -a", firstConfigure + 1)
        val audit = command.indexOf("dpkg --audit")
        val recoveryComplete = command.indexOf("GEIVIA_PACKAGE_RECOVERY_OK")
        val aptUpdate = command.indexOf("apt-get update")
        val packageInstall = command.indexOf("apt-get install -y --no-install-recommends")

        assertTrue(firstConfigure >= 0)
        assertTrue(!command.contains("dpkg --configure -a || true"))
        assertTrue(dependencyRepair > firstConfigure)
        assertTrue(secondConfigure > dependencyRepair)
        assertTrue(audit > secondConfigure)
        assertTrue(recoveryComplete > audit)
        assertTrue(aptUpdate > recoveryComplete)
        assertTrue(packageInstall > aptUpdate)
    }

    @Test
    fun recoveryKeepsNonInteractivePackageConfiguration() {
        val command = GeiviaToolchainCommands.installDefaultProfile().last()

        assertTrue(command.contains("export DEBIAN_FRONTEND=noninteractive"))
        assertTrue(command.contains("GEIVIA_TOOLCHAIN_OK"))
        assertTrue(command.contains("NPM_VERSION=11.6.2"))
        assertTrue(command.contains("FIREBASE_VERSION=15.36.0"))
        assertTrue(command.contains("https://registry.npmjs.org/"))
    }

    @Test
    fun installStagesAreOrderedAndHaveStableCheckpointIds() {
        val stages = GeiviaToolchainCommands.installStages()

        assertTrue(stages.map { it.id } == listOf(
            "dpkg_recovery_v2",
            "apt_update",
            "apt_packages",
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

        assertTrue(recovery.contains("dpkg --configure -a"))
        assertTrue(!recovery.contains("dpkg --configure -a || true"))
        assertTrue(recovery.contains("dpkg --audit"))
        assertTrue(packages.contains("GEIVIA_PACKAGE_RECOVERY_BEGIN"))
        assertTrue(packages.indexOf("dpkg --configure -a") < packages.indexOf("apt-get install -y --no-install-recommends"))
        assertTrue(packages.contains("apt-get -f install -y"))
    }

    @Test
    fun firebaseCliStageInstallsFirebaseCliFromNpmWithCacheRecovery() {
        val stage = GeiviaToolchainCommands.installStages().first { it.id == "firebase_cli" }
        val command = stage.command.last()

        assertTrue(command.contains("FIREBASE_ROOT=/opt/geivia/firebase"))
        assertTrue(command.contains("FIREBASE_VERSION=15.36.0"))
        assertTrue(command.contains("npm install --global"))
        assertTrue(command.contains("NPM_VERSION=11.6.2"))
        assertTrue(command.contains("node \"\$NPM_CLI\""))
        assertTrue(command.contains("npm_run install --prefix"))
        assertTrue(command.contains("--maxsockets=1"))
        assertTrue(command.contains("GEIVIA_FIREBASE_CACHE_RECOVERY"))
        assertTrue(command.contains("rm -rf \"\$FIREBASE_CACHE\" \"\$FIREBASE_ROOT/node_modules\""))
        assertTrue(command.contains("GEIVIA_FIREBASE_CLI_OK"))
        assertTrue(command.contains("node /opt/geivia/firebase/node_modules/firebase-tools/lib/bin/firebase.js"))
    }

    @Test
    fun firebaseCliInstallerUsesPublicNpmRegistryAndIsIdempotent() {
        val command = GeiviaToolchainCommands.installStages().first { it.id == "firebase_cli" }.command.last()

        assertTrue(command.contains("https://registry.npmjs.org") || command.contains("npm_run install --prefix"))
        assertTrue(command.contains("if [ ! -f \"\$FIREBASE_ENTRY\" ]"))
        assertTrue(command.contains("test -f \"\$FIREBASE_ENTRY\""))
        assertTrue(command.contains("chmod 755 /usr/local/bin/firebase"))
        assertTrue(!command.contains("releases/download/firebase-cli-arm64"))
    }

    @Test
    fun firebaseCliUpdatesUseTheSameNpmInstallRoutine() {
        val installCommand = GeiviaToolchainCommands.installStages().first { it.id == "firebase_cli" }.command.last()
        val updateCommand = GeiviaToolchainCommands.updateFirebaseCli().last()
        val fullUpdateCommand = GeiviaToolchainCommands.updateAll().last()

        assertTrue(updateCommand.contains("npm_run install --prefix"))
        assertTrue(fullUpdateCommand.contains("npm_run install --prefix"))
        assertTrue(installCommand.contains("npm_run install --prefix"))
    }

}
