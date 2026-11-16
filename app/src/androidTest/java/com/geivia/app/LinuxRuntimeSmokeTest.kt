package com.geivia.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import id.or.oo.pr.engine.PtyNative
import org.junit.runner.RunWith

/** Runs on a real ARM64 Android device/emulator; the hosted CI does not execute this test. */
@RunWith(AndroidJUnit4::class)
class LinuxRuntimeSmokeTest {
    @Test
    fun packagedNativeEngineCanRunPrCliListThroughPty() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val controller = LinuxRuntimeController(context)
        controller.prepareEngine()

        val output = StringBuilder()
        val exitCode = controller.execute(listOf("list")) { chunk ->
            output.append(chunk)
        }

        assertEquals("pr-cli list must exit successfully", 0, exitCode)
        assertFalse("pr-cli list should produce diagnostic output", output.isBlank())
    }
    @Test
    fun nativeCancellationTerminatesPtyProcessGroup() {
        val (fd, pid) = PtyNative.forkPtyWithPid(
            "/system/bin/sh",
            arrayOf("/system/bin/sh", "-c", "sleep 60"),
            emptyArray(),
            24,
            80
        )
        assertTrue("PTY should start", fd >= 0 && pid > 0)
        try {
            assertEquals("SIGTERM should be sent to the child process group", 0, PtyNative.kill(pid))
            assertTrue("PTY child should exit after cancellation", PtyNative.waitPidBlocking(pid) >= 128)
        } finally {
            PtyNative.close(fd)
        }
    }

    @Test
    fun requiredNativeRuntimeLibrariesArePackaged() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val nativeDir = java.io.File(context.applicationInfo.nativeLibraryDir)
        listOf("libptyjni.so", "libptykill.so", "libpr-cli.so", "libproot.so", "libproot-loader.so").forEach { name ->
            assertTrue("Missing native runtime library: $name", java.io.File(nativeDir, name).isFile)
        }
    }
}
