package com.geivia.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeiviaDiagnosticsTest {
    @Test
    fun reportsRuntimeReadyWhenAllPrerequisitesPass() {
        val report = GeiviaDiagnosticEngine.evaluateRuntime(
            androidApi = 35,
            supportedAbis = listOf("arm64-v8a"),
            availableBytes = LinuxRuntimePreflight.DEFAULT_REQUIRED_BYTES,
            checkedAtEpochMillis = 123L
        )

        assertEquals(GeiviaDiagnosticStatus.OK, report.overallStatus)
        assertEquals(1, report.findings.size)
        assertEquals(GeiviaDiagnosticCode.LINUX_RUNTIME_READY, report.findings.single().code)
    }

    @Test
    fun reportsAllRuntimeBlockersTogether() {
        val report = GeiviaDiagnosticEngine.evaluateRuntime(
            androidApi = 26,
            supportedAbis = listOf("armeabi-v7a"),
            availableBytes = 0L,
            checkedAtEpochMillis = 456L,
            availableNativeLibraries = emptySet()
        )

        assertEquals(GeiviaDiagnosticStatus.BLOCKED, report.overallStatus)
        assertEquals(4, report.blockedCount)
        assertTrue(report.findings.any { it.code == GeiviaDiagnosticCode.ANDROID_VERSION_UNSUPPORTED })
        assertTrue(report.findings.any { it.code == GeiviaDiagnosticCode.ARM64_UNAVAILABLE })
        assertTrue(report.findings.any { it.code == GeiviaDiagnosticCode.STORAGE_INSUFFICIENT })
        assertTrue(report.findings.any { it.code == GeiviaDiagnosticCode.NATIVE_LIBRARY_MISSING })
    }

    @Test
    fun safeReportContainsGuidanceButNoRawRuntimeValues() {
        val report = GeiviaDiagnosticEngine.evaluateRuntime(
            androidApi = 35,
            supportedAbis = listOf("arm64-v8a"),
            availableBytes = 0L,
            checkedAtEpochMillis = 789L
        )

        val safeText = report.toSafeText()
        assertTrue(safeText.contains("STORAGE_INSUFFICIENT"))
        assertTrue(safeText.contains("Libere armazenamento"))
        assertTrue(safeText.contains("Relatório sanitizado"))
        assertFalse(safeText.contains("/data/"))
        assertFalse(safeText.contains("token="))
    }

    @Test
    fun emptyReportIsNotCheckedRatherThanPassing() {
        val report = GeiviaDiagnosticReport(checkedAtEpochMillis = 1L, findings = emptyList())
        assertEquals(GeiviaDiagnosticStatus.NOT_CHECKED, report.overallStatus)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateDiagnosticCodes() {
        val finding = GeiviaDiagnosticFinding(
            code = GeiviaDiagnosticCode.LINUX_RUNTIME_READY,
            area = GeiviaDiagnosticArea.LINUX_RUNTIME,
            status = GeiviaDiagnosticStatus.OK,
            severity = GeiviaDiagnosticSeverity.INFO,
            userMessage = "Verificação aprovada."
        )
        GeiviaDiagnosticReport(1L, listOf(finding, finding))
    }
}
