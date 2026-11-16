package com.geivia.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinuxRuntimePreflightTest {
    @Test fun supportsAndroidApi28Arm64WithEnoughStorage() {
        val result = LinuxRuntimePreflight.evaluate(
            androidApi = 28,
            supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
            availableBytes = LinuxRuntimePreflight.DEFAULT_REQUIRED_BYTES
        )
        assertTrue(result.supported)
        assertTrue(result.reasons.isEmpty())
    }

    @Test fun rejectsAndroidBelowApi28() {
        val result = LinuxRuntimePreflight.evaluate(
            androidApi = 26,
            supportedAbis = listOf("arm64-v8a"),
            availableBytes = LinuxRuntimePreflight.DEFAULT_REQUIRED_BYTES
        )
        assertFalse(result.supported)
        assertTrue(result.reasons.any { it.contains("Android 9") })
    }

    @Test fun rejectsDevicesWithoutArm64() {
        val result = LinuxRuntimePreflight.evaluate(
            androidApi = 35,
            supportedAbis = listOf("armeabi-v7a"),
            availableBytes = LinuxRuntimePreflight.DEFAULT_REQUIRED_BYTES
        )
        assertFalse(result.supported)
        assertTrue(result.reasons.any { it.contains("ARM64") })
    }

    @Test fun rejectsInsufficientStorage() {
        val result = LinuxRuntimePreflight.evaluate(
            androidApi = 35,
            supportedAbis = listOf("arm64-v8a"),
            availableBytes = LinuxRuntimePreflight.DEFAULT_REQUIRED_BYTES - 1
        )
        assertFalse(result.supported)
        assertTrue(result.reasons.any { it.contains("armazenamento") })
    }

    @Test fun rejectsApkMissingNativeRuntimeComponents() {
        val available = LinuxRuntimePreflight.REQUIRED_NATIVE_LIBRARIES - "libproot-loader.so"
        val result = LinuxRuntimePreflight.evaluate(
            androidApi = 35,
            supportedAbis = listOf("arm64-v8a"),
            availableBytes = LinuxRuntimePreflight.DEFAULT_REQUIRED_BYTES,
            availableNativeLibraries = available
        )
        assertFalse(result.supported)
        assertTrue(result.missingNativeLibraries == listOf("libproot-loader.so"))
        assertTrue(result.reasons.any { it.contains("libproot-loader.so") })
    }

    @Test fun reportsAllIncompatibilitiesTogether() {
        val result = LinuxRuntimePreflight.evaluate(
            androidApi = 26,
            supportedAbis = listOf("armeabi-v7a"),
            availableBytes = 0,
            availableNativeLibraries = emptySet()
        )
        assertFalse(result.supported)
        assertTrue(result.reasons.size == 4)
    }
}
