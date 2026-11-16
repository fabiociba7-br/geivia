package com.geivia.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
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
}
