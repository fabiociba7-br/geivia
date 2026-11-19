package com.geivia.app

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Non-destructive recovery for source files edited by GEIVIA Studio.
 * Backup files are retained after restoration so the operation can be repeated.
 */
internal object ImportedSourceRecovery {
    enum class Version(val suffix: String, val label: String) {
        LATEST(".geivia.bak", "última versão"),
        PREVIOUS(".geivia.bak.previous", "versão anterior")
    }

    fun available(source: File, projectRoot: File, version: Version): Boolean {
        val backup = backupFile(source, projectRoot, version) ?: return false
        return backup.isFile && backup.length() in 1..MAX_SOURCE_BYTES
    }

    fun restore(source: File, projectRoot: File, version: Version) {
        val backup = backupFile(source, projectRoot, version)
            ?: throw IllegalArgumentException("Arquivo-fonte fora da pasta do projeto ou caminho inválido.")
        require(source.isFile && source.extension.lowercase() in setOf("xml", "kt")) {
            "A recuperação aceita somente arquivos XML ou Kotlin existentes."
        }
        require(backup.isFile && backup.length() in 1..MAX_SOURCE_BYTES) {
            "A versão selecionada não existe ou excede o limite permitido."
        }

        val candidate = File(source.parentFile, source.name + ".geivia.restore.tmp")
        val rollback = File(source.parentFile, source.name + ".geivia.restore.rollback")
        require(!candidate.exists() && !rollback.exists()) {
            "Há uma recuperação anterior incompleta. Preserve os arquivos temporários antes de continuar."
        }

        try {
            backup.copyTo(candidate, overwrite = false)
            validateCandidate(candidate)
            require(source.renameTo(rollback)) { "Não foi possível preservar o arquivo atual." }
            if (!candidate.renameTo(source)) {
                val restored = rollback.renameTo(source)
                throw IllegalStateException(
                    if (restored) "A restauração falhou; o arquivo atual foi recolocado."
                    else "Falha crítica: o arquivo atual permanece em ${rollback.name}."
                )
            }
            // Backups remain untouched; rollback is removed only after successful replacement.
            require(rollback.delete()) { "A versão foi restaurada, mas a cópia temporária ${rollback.name} não pôde ser removida." }
        } finally {
            if (candidate.exists()) candidate.delete()
        }
    }

    private fun backupFile(source: File, projectRoot: File, version: Version): File? {
        val root = runCatching { projectRoot.canonicalFile }.getOrNull() ?: return null
        val target = runCatching { source.canonicalFile }.getOrNull() ?: return null
        val rootPrefix = root.path.trimEnd(File.separatorChar) + File.separator
        if (!target.path.startsWith(rootPrefix)) return null
        if (target.extension.lowercase() !in setOf("xml", "kt")) return null
        val backup = File(target.parentFile, target.name + version.suffix)
        val canonicalBackup = runCatching { backup.canonicalFile }.getOrNull() ?: return null
        if (canonicalBackup.parentFile != target.parentFile) return null
        return canonicalBackup
    }

    private fun validateCandidate(file: File) {
        require(file.length() in 1..MAX_SOURCE_BYTES) { "A cópia selecionada está vazia ou excede o limite permitido." }
        when (file.name.substringBefore(".geivia").substringAfterLast(".").lowercase()) {
            "xml" -> {
                val factory = DocumentBuilderFactory.newInstance().apply {
                    isNamespaceAware = true
                    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                    setFeature("http://xml.org/sax/features/external-general-entities", false)
                    setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                    setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                    setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                    setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
                }
                FileInputStream(file).use { factory.newDocumentBuilder().parse(it) }
            }
            "kt" -> {
                val source = file.readText(Charsets.UTF_8)
                require(!source.contains('\u0000')) { "A cópia Kotlin contém dados inválidos." }
            }
            else -> throw IllegalArgumentException("Tipo de arquivo não suportado para recuperação.")
        }
    }

    private const val MAX_SOURCE_BYTES = 2L * 1024L * 1024L
}
