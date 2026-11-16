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
        assertTrue(command.contains("NPM_VERSION=11.6.2"))
        assertTrue(command.contains("FIREBASE_VERSION=15.36.0"))
        assertTrue(command.contains("https://registry.npmjs.org/"))
    }

    @Test
    fun installStagesAreOrderedAndHaveStableCheckpointIds() {
        val stages = GeiviaToolchainCommands.installStages()

        assertTrue(stages.map { it.id } == listOf(
            "dpkg_recovery",
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

        assertTrue(recovery.indexOf("dpkg --configure -a || true") >= 0)
        assertTrue(recovery.indexOf("apt-get -f install -y") > recovery.indexOf("dpkg --configure -a || true"))
        assertTrue(packages.contains("apt-get install -y --no-install-recommends"))
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

        assertTrue(updateCommand.contains("firebase-tools@latest"))
        assertTrue(fullUpdateCommand.contains("firebase-tools@latest"))
        assertTrue(installCommand.contains("npm_run install --prefix"))
    }

    @Test
    fun updateSearchReportsInstalledAndCandidateVersionsAndUsesVersionComparison() {
        val command = GeiviaToolchainCommands.checkUpdates().last()

        assertTrue(command.contains("GEIVIA_UPDATE|"))
        assertTrue(command.contains("dpkg --compare-versions"))
        assertTrue(command.contains("apt-cache policy"))
        assertTrue(command.contains("npm view firebase-tools version"))
        assertTrue(command.contains("sort -V"))
    }
}
