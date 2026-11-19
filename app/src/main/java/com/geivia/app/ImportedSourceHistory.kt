package com.geivia.app

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** Local, bounded audit trail with immutable source snapshots for Studio edits. */
internal object ImportedSourceHistory {
    data class Entry(
        val timestamp: Long,
        val path: String,
        val action: String,
        val detail: String,
        val snapshot: String? = null
    )

    fun record(projectRoot: File, source: File, action: String, detail: String = "") {
        runCatching {
            val root = projectRoot.canonicalFile
            val target = source.canonicalFile
            requireInside(root, target)
            require(target.isFile && target.extension.lowercase() in setOf("xml", "kt"))
            require(target.length() in 1..MAX_SNAPSHOT_BYTES)
            val directory = File(root, HISTORY_DIR)
            if (!directory.exists()) require(directory.mkdirs())
            val snapshots = File(directory, SNAPSHOT_DIR)
            if (!snapshots.exists()) require(snapshots.mkdirs())
            val timestamp = System.currentTimeMillis()
            val relative = target.relativeTo(root).invariantSeparatorsPath
            val snapshotName = "${timestamp}_${Integer.toHexString(relative.hashCode())}.src"
            val snapshotFile = File(snapshots, snapshotName)
            target.copyTo(snapshotFile, overwrite = false)
            val entries = read(File(directory, HISTORY_FILE)).toMutableList()
            entries += Entry(timestamp, relative, action, detail, snapshotName)
            val retained = entries.takeLast(MAX_ENTRIES)
            val temp = File(directory, HISTORY_FILE + ".tmp")
            temp.writeText(retained.joinToString("\n") { encode(it) } + "\n", Charsets.UTF_8)
            val history = File(directory, HISTORY_FILE)
            if (!temp.renameTo(history)) {
                temp.delete()
                snapshotFile.delete()
                throw IllegalStateException("Não foi possível atualizar o histórico local.")
            }
            val keep = retained.mapNotNull { it.snapshot }.toSet()
            snapshots.listFiles()?.filter { it.isFile && it.name !in keep }?.forEach { it.delete() }
        }
    }

    fun recent(projectRoot: File, source: File, limit: Int = 20): List<Entry> = runCatching {
        val root = projectRoot.canonicalFile
        val target = source.canonicalFile
        requireInside(root, target)
        val relative = target.relativeTo(root).invariantSeparatorsPath
        read(File(File(root, HISTORY_DIR), HISTORY_FILE))
            .filter { it.path == relative }
            .sortedByDescending { it.timestamp }
            .take(limit.coerceIn(1, MAX_ENTRIES))
    }.getOrDefault(emptyList())

    fun preview(projectRoot: File, entry: Entry, currentSource: File): String {
        val root = projectRoot.canonicalFile
        val target = currentSource.canonicalFile
        requireInside(root, target)
        require(entry.path == target.relativeTo(root).invariantSeparatorsPath) {
            "A entrada pertence a outro arquivo-fonte."
        }
        val snapshot = snapshotFile(root, entry)
            ?: throw IllegalArgumentException("Esta entrada não possui uma cópia histórica.")
        require(snapshot.isFile && snapshot.length() in 1..MAX_SNAPSHOT_BYTES) {
            "A cópia histórica não existe ou excede o limite permitido."
        }
        val historical = snapshot.readText(Charsets.UTF_8)
        val current = target.readText(Charsets.UTF_8)
        val oldLines = historical.lines()
        val newLines = current.lines()
        val result = StringBuilder()
        result.append("VERSÃO HISTÓRICA · ").append(formatTime(entry.timestamp)).append("\n")
        result.append("AÇÃO: ").append(entry.action).append("\n\n")
        result.append("COMPARAÇÃO SIMPLES POR LINHA (até 240 diferenças)\n")
        val count = maxOf(oldLines.size, newLines.size)
        var differences = 0
        for (index in 0 until count) {
            val oldLine = oldLines.getOrNull(index)
            val newLine = newLines.getOrNull(index)
            if (oldLine != newLine && differences < MAX_DIFF_LINES) {
                if (oldLine != null) result.append("- ").append(oldLine).append("\n")
                if (newLine != null) result.append("+ ").append(newLine).append("\n")
                differences++
            }
        }
        if (differences == 0) result.append("Nenhuma diferença de texto encontrada.\n")
        if (differences >= MAX_DIFF_LINES) result.append("\n[Comparação limitada.]")
        result.append("\n\nCONTEÚDO DA VERSÃO HISTÓRICA\n").append(historical.take(MAX_PREVIEW_CHARS))
        if (historical.length > MAX_PREVIEW_CHARS) result.append("\n[Conteúdo truncado na prévia.]")
        return result.toString()
    }

    fun restore(projectRoot: File, entry: Entry, source: File) {
        val root = projectRoot.canonicalFile
        val target = source.canonicalFile
        requireInside(root, target)
        require(target.isFile && target.extension.lowercase() in setOf("xml", "kt"))
        require(entry.path == target.relativeTo(root).invariantSeparatorsPath) {
            "A entrada pertence a outro arquivo-fonte."
        }
        val snapshot = snapshotFile(root, entry)
            ?: throw IllegalArgumentException("Esta entrada não possui uma cópia histórica.")
        require(snapshot.isFile && snapshot.length() in 1..MAX_SNAPSHOT_BYTES)
        validateSnapshot(snapshot, target.extension)
        val temp = File(target.parentFile, target.name + ".geivia.history.tmp")
        val rollback = File(target.parentFile, target.name + ".geivia.history.rollback")
        val beforeHistory = File(target.parentFile, target.name + ".geivia.before-history")
        require(!temp.exists() && !rollback.exists()) {
            "Há uma restauração anterior incompleta; preserve os arquivos temporários."
        }
        try {
            snapshot.copyTo(temp, overwrite = false)
            validateSnapshot(temp, target.extension)
            target.copyTo(beforeHistory, overwrite = true)
            require(target.renameTo(rollback)) { "Não foi possível preservar a versão atual." }
            if (!temp.renameTo(target)) {
                val recovered = rollback.renameTo(target)
                throw IllegalStateException(
                    if (recovered) "A restauração falhou; a versão atual foi recuperada."
                    else "Falha crítica. A versão atual está em ${rollback.name}."
                )
            }
            require(rollback.delete()) {
                "Versão restaurada, mas a cópia temporária ${rollback.name} permaneceu."
            }
        } finally {
            if (temp.exists()) temp.delete()
        }
        record(projectRoot, target, "Restauração do histórico", "Restaurada a entrada de ${formatTime(entry.timestamp)}")
    }

    fun formatTime(timestamp: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        .apply { timeZone = TimeZone.getDefault() }.format(Date(timestamp))

    private fun snapshotFile(projectRoot: File, entry: Entry): File? {
        val name = entry.snapshot ?: return null
        if (!name.matches(Regex("[0-9]+_[0-9a-f]+\\.src"))) return null
        val root = projectRoot.canonicalFile
        val historyDirectory = File(root, HISTORY_DIR).canonicalFile
        if (!historyDirectory.path.startsWith(root.path.trimEnd(File.separatorChar) + File.separator)) return null
        val directory = File(historyDirectory, SNAPSHOT_DIR).canonicalFile
        if (directory.parentFile != historyDirectory) return null
        val candidate = File(directory, name).canonicalFile
        return candidate.takeIf { it.parentFile == directory }
    }

    private fun validateSnapshot(file: File, extension: String) {
        require(file.length() in 1..MAX_SNAPSHOT_BYTES)
        when (extension.lowercase()) {
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
                file.inputStream().use { factory.newDocumentBuilder().parse(it) }
            }
            "kt" -> require(!file.readText(Charsets.UTF_8).contains('\u0000'))
            else -> throw IllegalArgumentException("Tipo de arquivo não suportado.")
        }
    }

    private fun requireInside(root: File, target: File) {
        require(target.path.startsWith(root.path.trimEnd(File.separatorChar) + File.separator)) {
            "Arquivo fora da pasta do projeto."
        }
    }

    private fun read(file: File): List<Entry> {
        if (!file.isFile || file.length() > MAX_HISTORY_BYTES) return emptyList()
        return file.readLines(Charsets.UTF_8).mapNotNull { line ->
            runCatching {
                val fields = line.split('|')
                require(fields.size == 4 || fields.size == 5)
                Entry(fields[0].toLong(), decode(fields[1]), decode(fields[2]), decode(fields[3]), fields.getOrNull(4)?.takeIf { it.isNotBlank() }?.let(::decode))
            }.getOrNull()
        }.takeLast(MAX_ENTRIES)
    }

    private fun encode(entry: Entry): String =
        listOf(entry.timestamp.toString(), encode(entry.path), encode(entry.action), encode(entry.detail), entry.snapshot?.let(::encode) ?: "")
            .joinToString("|")

    private fun encode(value: String): String =
        android.util.Base64.encodeToString(value.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)

    private fun decode(value: String): String =
        String(android.util.Base64.decode(value, android.util.Base64.DEFAULT), Charsets.UTF_8)

    private const val HISTORY_DIR = ".geivia"
    private const val HISTORY_FILE = "history.log"
    private const val SNAPSHOT_DIR = "history_snapshots"
    private const val MAX_ENTRIES = 100
    private const val MAX_HISTORY_BYTES = 128L * 1024L
    private const val MAX_SNAPSHOT_BYTES = 2L * 1024L * 1024L
    private const val MAX_PREVIEW_CHARS = 16000
    private const val MAX_DIFF_LINES = 240
}
