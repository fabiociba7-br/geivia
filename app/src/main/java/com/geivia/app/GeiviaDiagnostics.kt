package com.geivia.app

/**
 * Safe, user-facing diagnostic primitives.
 *
 * Findings deliberately use stable codes and controlled messages instead of raw
 * exception text, shell output, environment variables, or credential-bearing data.
 */
enum class GeiviaDiagnosticArea {
    DEVICE,
    STORAGE,
    LINUX_RUNTIME,
    DEBIAN,
    TOOLCHAIN,
    CLOUD_INTEGRATION
}

enum class GeiviaDiagnosticStatus {
    OK,
    ATTENTION,
    BLOCKED,
    NOT_CHECKED
}

enum class GeiviaDiagnosticSeverity {
    INFO,
    WARNING,
    ERROR
}

enum class GeiviaDiagnosticCode {
    ANDROID_VERSION_UNSUPPORTED,
    ARM64_UNAVAILABLE,
    STORAGE_INSUFFICIENT,
    NATIVE_LIBRARY_MISSING,
    LINUX_RUNTIME_READY,
    DEBIAN_NOT_VERIFIED,
    TOOLCHAIN_NOT_VERIFIED
}

enum class GeiviaDiagnosticAction {
    NONE,
    FREE_STORAGE,
    UPDATE_OR_REINSTALL_APP,
    REVIEW_DEVICE_COMPATIBILITY,
    RUN_ENVIRONMENT_SETUP,
    RETRY_VERIFICATION
}

data class GeiviaDiagnosticFinding(
    val code: GeiviaDiagnosticCode,
    val area: GeiviaDiagnosticArea,
    val status: GeiviaDiagnosticStatus,
    val severity: GeiviaDiagnosticSeverity,
    val userMessage: String,
    val recommendedAction: GeiviaDiagnosticAction = GeiviaDiagnosticAction.NONE
)

data class GeiviaDiagnosticReport(
    val checkedAtEpochMillis: Long,
    val findings: List<GeiviaDiagnosticFinding>
) {
    init {
        require(checkedAtEpochMillis >= 0L) { "O horário do diagnóstico não pode ser negativo." }
        require(findings.map { it.code }.distinct().size == findings.size) {
            "Um diagnóstico não pode repetir o mesmo código de verificação."
        }
    }

    val blockedCount: Int
        get() = findings.count { it.status == GeiviaDiagnosticStatus.BLOCKED }

    val attentionCount: Int
        get() = findings.count { it.status == GeiviaDiagnosticStatus.ATTENTION }

    val overallStatus: GeiviaDiagnosticStatus
        get() = when {
            findings.isEmpty() -> GeiviaDiagnosticStatus.NOT_CHECKED
            blockedCount > 0 -> GeiviaDiagnosticStatus.BLOCKED
            attentionCount > 0 -> GeiviaDiagnosticStatus.ATTENTION
            findings.all { it.status == GeiviaDiagnosticStatus.OK } -> GeiviaDiagnosticStatus.OK
            else -> GeiviaDiagnosticStatus.NOT_CHECKED
        }

    /**
     * Export only stable diagnostic codes and controlled user messages.
     * Never append raw logs, exception messages, paths, tokens or environment values.
     */
    fun toSafeText(): String = buildString {
        appendLine("Relatório de diagnóstico GEIVIA")
        appendLine("Verificado em (epoch ms): $checkedAtEpochMillis")
        appendLine("Estado geral: ${overallStatus.name}")
        appendLine("Verificações:")
        findings.forEach { finding ->
            appendLine("- ${finding.code.name} | ${finding.status.name} | ${finding.userMessage}")
            if (finding.recommendedAction != GeiviaDiagnosticAction.NONE) {
                appendLine("  Próxima ação: ${finding.recommendedAction.name}")
            }
        }
        appendLine("Relatório sanitizado: não contém tokens, senhas, chaves ou saída bruta do terminal.")
    }
}

/** Converts the existing runtime preflight into stable, safe diagnostic findings. */
object GeiviaDiagnosticEngine {
    fun evaluateRuntime(
        androidApi: Int,
        supportedAbis: List<String>,
        availableBytes: Long,
        checkedAtEpochMillis: Long,
        availableNativeLibraries: Set<String> = LinuxRuntimePreflight.REQUIRED_NATIVE_LIBRARIES
    ): GeiviaDiagnosticReport {
        val findings = mutableListOf<GeiviaDiagnosticFinding>()

        if (androidApi < LinuxRuntimePreflight.MIN_ANDROID_API) {
            findings += GeiviaDiagnosticFinding(
                code = GeiviaDiagnosticCode.ANDROID_VERSION_UNSUPPORTED,
                area = GeiviaDiagnosticArea.DEVICE,
                status = GeiviaDiagnosticStatus.BLOCKED,
                severity = GeiviaDiagnosticSeverity.ERROR,
                userMessage = "Este aparelho precisa do Android 9 ou superior para usar o ambiente Linux integrado.",
                recommendedAction = GeiviaDiagnosticAction.REVIEW_DEVICE_COMPATIBILITY
            )
        }

        if (LinuxRuntimePreflight.REQUIRED_ABI !in supportedAbis) {
            findings += GeiviaDiagnosticFinding(
                code = GeiviaDiagnosticCode.ARM64_UNAVAILABLE,
                area = GeiviaDiagnosticArea.DEVICE,
                status = GeiviaDiagnosticStatus.BLOCKED,
                severity = GeiviaDiagnosticSeverity.ERROR,
                userMessage = "O aparelho não oferece a arquitetura ARM64 necessária ao ambiente Linux.",
                recommendedAction = GeiviaDiagnosticAction.REVIEW_DEVICE_COMPATIBILITY
            )
        }

        if (availableBytes < LinuxRuntimePreflight.DEFAULT_REQUIRED_BYTES) {
            findings += GeiviaDiagnosticFinding(
                code = GeiviaDiagnosticCode.STORAGE_INSUFFICIENT,
                area = GeiviaDiagnosticArea.STORAGE,
                status = GeiviaDiagnosticStatus.BLOCKED,
                severity = GeiviaDiagnosticSeverity.ERROR,
                userMessage = "O espaço livre está abaixo do necessário. Libere armazenamento e execute o diagnóstico novamente.",
                recommendedAction = GeiviaDiagnosticAction.FREE_STORAGE
            )
        }

        val missing = LinuxRuntimePreflight.REQUIRED_NATIVE_LIBRARIES - availableNativeLibraries
        if (missing.isNotEmpty()) {
            findings += GeiviaDiagnosticFinding(
                code = GeiviaDiagnosticCode.NATIVE_LIBRARY_MISSING,
                area = GeiviaDiagnosticArea.LINUX_RUNTIME,
                status = GeiviaDiagnosticStatus.BLOCKED,
                severity = GeiviaDiagnosticSeverity.ERROR,
                userMessage = "Faltam componentes internos do ambiente Linux no aplicativo. Atualize ou reinstale o GEIVIA.",
                recommendedAction = GeiviaDiagnosticAction.UPDATE_OR_REINSTALL_APP
            )
        }

        if (findings.isEmpty()) {
            findings += GeiviaDiagnosticFinding(
                code = GeiviaDiagnosticCode.LINUX_RUNTIME_READY,
                area = GeiviaDiagnosticArea.LINUX_RUNTIME,
                status = GeiviaDiagnosticStatus.OK,
                severity = GeiviaDiagnosticSeverity.INFO,
                userMessage = "Os requisitos básicos do aparelho para preparar o ambiente Linux foram atendidos."
            )
        }

        return GeiviaDiagnosticReport(
            checkedAtEpochMillis = checkedAtEpochMillis,
            findings = findings
        )
    }
}
