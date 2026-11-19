package com.geivia.app

import java.io.File
import java.security.MessageDigest

/**
 * A staged change to an imported screen. Draft operations are never written
 * to source files until the user reviews and explicitly applies the batch.
 */
internal sealed interface ImportedScreenChange {
    val componentId: String

    data class Text(
        override val componentId: String,
        val originalValue: String,
        val newValue: String
    ) : ImportedScreenChange

    data class Dimensions(
        override val componentId: String,
        val originalWidth: String,
        val originalHeight: String,
        val newWidth: String,
        val newHeight: String
    ) : ImportedScreenChange

    data class Move(
        override val componentId: String,
        val parentId: String?,
        val originalIndex: Int,
        val newIndex: Int
    ) : ImportedScreenChange
}

internal object ImportedScreenDraft {
    const val MAX_CHANGES = 100

    fun fingerprint(file: File): String {
        require(file.isFile) { "Arquivo-fonte não encontrado." }
        require(file.length() <= 2L * 1024L * 1024L) { "Arquivo-fonte excede o limite para edição visual." }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Keep only the latest staged value for a component/property pair.
     * The original value remains the one read from disk when the draft began.
     */
    fun stage(
        current: List<ImportedScreenChange>,
        change: ImportedScreenChange
    ): List<ImportedScreenChange> {
        val key: (ImportedScreenChange) -> String = {
            val kind = when (it) {
                is ImportedScreenChange.Text -> "text"
                is ImportedScreenChange.Dimensions -> "dimensions"
                is ImportedScreenChange.Move -> "move"
            }
            "$kind:${it.componentId}"
        }
        val original = current.firstOrNull { key(it) == key(change) }
        val merged = when {
            original is ImportedScreenChange.Text && change is ImportedScreenChange.Text ->
                change.copy(originalValue = original.originalValue)
            original is ImportedScreenChange.Dimensions && change is ImportedScreenChange.Dimensions ->
                change.copy(
                    originalWidth = original.originalWidth,
                    originalHeight = original.originalHeight
                )
            original is ImportedScreenChange.Move && change is ImportedScreenChange.Move ->
                change.copy(originalIndex = original.originalIndex, parentId = original.parentId)
            else -> change
        }
        val withoutSame = current.filterNot { key(it) == key(change) }
        val returnsToOriginal = when (merged) {
            is ImportedScreenChange.Text -> merged.newValue == merged.originalValue
            is ImportedScreenChange.Dimensions ->
                merged.newWidth == merged.originalWidth && merged.newHeight == merged.originalHeight
            is ImportedScreenChange.Move -> merged.newIndex == merged.originalIndex
        }
        if (returnsToOriginal) return withoutSame
        require(withoutSame.size < MAX_CHANGES) { "Limite de alterações em rascunho atingido." }
        return withoutSame + merged
    }

    fun describe(change: ImportedScreenChange): String = when (change) {
        is ImportedScreenChange.Text -> "Texto · @id/${change.componentId}: “${change.originalValue}” → “${change.newValue}”"
        is ImportedScreenChange.Dimensions -> "Dimensões · @id/${change.componentId}: ${change.originalWidth} × ${change.originalHeight} → ${change.newWidth} × ${change.newHeight}"
        is ImportedScreenChange.Move -> "Posição · @id/${change.componentId}: item ${change.originalIndex + 1} → ${change.newIndex + 1}"
    }
}
