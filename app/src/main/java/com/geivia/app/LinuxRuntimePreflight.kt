package com.geivia.app

/**
 * Checks whether this device can host the planned ARM64 PRoot runtime.
 *
 * This is a capability gate only: it does not install or execute Linux binaries.
 * The legacy restricted terminal remains available when a check fails.
 */
data class LinuxRuntimePreflightResult(
    val supported: Boolean,
    val reasons: List<String>,
    val availableBytes: Long,
    val requiredBytes: Long
)

object LinuxRuntimePreflight {
    const val MIN_ANDROID_API = 28
    const val REQUIRED_ABI = "arm64-v8a"
    const val DEFAULT_REQUIRED_BYTES = 2L * 1024L * 1024L * 1024L

    fun evaluate(
        androidApi: Int,
        supportedAbis: List<String>,
        availableBytes: Long,
        requiredBytes: Long = DEFAULT_REQUIRED_BYTES
    ): LinuxRuntimePreflightResult {
        val reasons = mutableListOf<String>()

        if (androidApi < MIN_ANDROID_API) {
            reasons += "O ambiente Linux integrado exige Android 9 (API 28) ou superior."
        }
        if (REQUIRED_ABI !in supportedAbis) {
            reasons += "Este aparelho não informa suporte à arquitetura ARM64 (arm64-v8a)."
        }
        if (availableBytes < requiredBytes) {
            reasons += "Espaço insuficiente. Libere armazenamento antes de preparar o ambiente Linux."
        }

        return LinuxRuntimePreflightResult(
            supported = reasons.isEmpty(),
            reasons = reasons,
            availableBytes = availableBytes,
            requiredBytes = requiredBytes
        )
    }
}
