package com.geivia.app

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Local, bounded audit trail for Studio source changes. History failures never
 * invalidate an already-saved source edit.
 */
internal object ImportedSourceHistory {
    data class Entry(val timestamp: Long, val path: String, val action: String, val detail: String)

    fun record(projectRoot: File, source: File, action: String, detail: String = "") {
        runCatching {
            val root = projectRoot.canonicalFile
            val target = source.canonicalFile
            require(target.path.startsWith(root.path.trimEnd(File.separatorChar) + File.separator))
            val directory = File(root, ".geivia")
            if (!directory.exists()) require(directory.mkdirs())
            val history = File(directory, "history.log")
            val entries = read(history).toMutableList()
            entries += Entry(System.currentTimeMillis(), target.relativeTo(root).invariantSeparatorsPath, action, detail)
            val retained = entries.takeLast(MAX_ENTRIES)
            val temp = File(directory, "history.log.tmp")
            temp.writeText(retained.joinToString("\n") { encode(it) } + "\n", Charsets.UTF_8)
            if (history.exists()) {
                val old = File(directory, "history.log.previous")
                if (old.exists()) old.delete()
                history.copyTo(old, overwrite = true)
            }
            if (!temp.renameTo(history)) {
                temp.delete()
                throw IllegalStateException("Não foi possível atualizar o histórico local.")
            }
        }
    }

    fun recent(projectRoot: File, source: File, limit: Int = 20): List<Entry> = runCatching {
        val root = projectRoot.canonicalFile
        val target = source.canonicalFile
        require(target.path.startsWith(root.path.trimEnd(File.separatorChar) + File.separator))
        val relative = target.relativeTo(root).invariantSeparatorsPath
        read(File(File(root, ".geivia"), "history.log"))
            .filter { it.path == relative }
            .sortedByDescending { it.timestamp }
            .take(limit)
    }.getOrDefault(emptyList())

    fun formatTime(timestamp: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        .apply { timeZone = TimeZone.getDefault() }.format(Date(timestamp))

    private fun read(file: File): List<Entry> {
        if (!file.isFile || file.length() > MAX_HISTORY_BYTES) return emptyList()
        return file.readLines(Charsets.UTF_8).mapNotNull { line ->
            runCatching {
                val fields = line.split('|')
                require(fields.size == 4)
                Entry(fields[0].toLong(), decode(fields[1]), decode(fields[2]), decode(fields[3]))
            }.getOrNull()
        }.takeLast(MAX_ENTRIES)
    }

    private fun encode(entry: Entry): String =
        "${entry.timestamp}|${encode(entry.path)}|${encode(entry.action)}|${encode(entry.detail)}"

    private fun encode(value: String): String =
        android.util.Base64.encodeToString(value.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)

    private fun decode(value: String): String =
        String(android.util.Base64.decode(value, android.util.Base64.DEFAULT), Charsets.UTF_8)

    private const val MAX_ENTRIES = 100
    private const val MAX_HISTORY_BYTES = 128L * 1024L
}
