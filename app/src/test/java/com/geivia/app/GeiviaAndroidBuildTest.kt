package com.geivia.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeiviaAndroidBuildTest {
    @Test
    fun androidToolchainUsesDebianStableJdkInsteadOfPinnedJava17Package() {
        val command = GeiviaAndroidBuild.installToolchain().last()

        assertTrue(command.contains("default-jdk-headless"))
        assertFalse(command.contains("openjdk-17-jdk-headless"))
        assertTrue(command.contains("command -v javac"))
        assertTrue(command.contains("readlink -f"))
    }

    @Test
    fun androidToolchainConfiguresGradleAndAndroidSdk() {
        val command = GeiviaAndroidBuild.installToolchain().last()

        assertTrue(command.contains("gradle-8.10.2-bin.zip"))
        assertTrue(command.contains("ANDROID_HOME=/opt/android-sdk"))
        assertTrue(command.contains("cmdline-tools/latest/bin/sdkmanager"))
        assertTrue(command.contains("platforms;android-35"))
        assertTrue(command.contains("build-tools;35.0.0"))
        assertTrue(command.contains("sdkmanager"))
        assertTrue(command.contains("GEIVIA_ANDROID_TOOLCHAIN_OK"))
    }

    @Test
    fun androidBuildExportsDetectedJdkAndSdkPaths() {
        val command = GeiviaAndroidBuild.buildCommand("/tmp/sample")

        assertTrue(command.last().contains("command -v javac"))
        assertTrue(command.last().contains("ANDROID_SDK_ROOT"))
        assertTrue(command.last().contains("./gradlew assembleDebug"))
    }
}
