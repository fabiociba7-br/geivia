package com.geivia.app

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.File
import java.io.InputStream

/**
 * Copies only user-selected documents into the app-private terminal workspace.
 * No external URI is exposed to the command interpreter.
 */
internal class TerminalWorkspaceImporter(private val context: Context, filesDir: File) {
    private val importsDir = File(File(filesDir, "terminal-workspace"), "imports").canonicalFile

    init {
        require(importsDir.mkdirs() || importsDir.isDirectory) {
            "Não foi possível preparar a pasta de importações."
        }
        val workspace = File(filesDir, "terminal-workspace").canonicalFile
        require(importsDir.path.startsWith(workspace.path + File.separator)) {
            "Destino de importação inválido."
        }
    }

    fun importFiles(uris: List<Uri>): List<File> {
        require(uris.isNotEmpty()) { "Nenhum arquivo foi selecionado." }
        require(uris.size <= MAX_FILES) { "Selecione no máximo $MAX_FILES arquivos por vez." }
        val imported = mutableListOf<File>()
        var totalBytes = 0L
        uris.forEach { uri ->
            val name = queryDisplayName(uri) ?: "arquivo-importado"
            val file = context.contentResolver.openInputStream(uri)?.use { input ->
                importStream(input, name, MAX_FILE_BYTES, MAX_TOTAL_BYTES - totalBytes).also {
                    totalBytes += it.length()
                }
            } ?: throw IllegalStateException("Não foi possível abrir: $name")
            imported += file
        }
        return imported
    }

    fun importTree(treeUri: Uri): List<File> {
        val imported = mutableListOf<File>()
        var totalBytes = 0L
        var visited = 0

        fun visit(documentId: String, depth: Int) {
            require(depth <= MAX_DEPTH) { "A pasta selecionada é profunda demais para importar com segurança." }
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            val projection = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)
                val nameColumn = cursor.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val childId = cursor.getString(idColumn) ?: continue
                    val name = cursor.getString(nameColumn) ?: "arquivo-importado"
                    val mime = cursor.getString(mimeColumn)
                    if (mime == Document.MIME_TYPE_DIR) {
                        visit(childId, depth + 1)
                    } else {
                        visited++
                        require(visited <= MAX_FILES) { "A pasta contém mais de $MAX_FILES arquivos. Divida a importação em partes." }
                        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                        val importedFile = context.contentResolver.openInputStream(documentUri)?.use { input ->
                            importStream(input, name, MAX_FILE_BYTES, MAX_TOTAL_BYTES - totalBytes).also {
                                totalBytes += it.length()
                            }
                        } ?: throw IllegalStateException("Não foi possível abrir: $name")
                        imported += importedFile
                    }
                }
            } ?: throw IllegalStateException("Não foi possível listar a pasta selecionada.")
        }

        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        visit(rootId, 0)
        return imported
    }

    private fun queryDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) return cursor.getString(index)
                }
            }
        return uri.lastPathSegment
    }

    private fun importStream(input: InputStream, displayName: String, perFileLimit: Long, remainingTotal: Long): File {
        require(remainingTotal > 0) { "A importação atingiu o limite total de 25 MB." }
        val safeName = sanitizeDisplayName(displayName)
        val destination = uniqueDestination(safeName)
        val temp = File(importsDir, ".import-${System.nanoTime()}.tmp")
        var copied = 0L
        try {
            temp.outputStream().buffered().use { output ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    copied += count
                    require(copied <= perFileLimit) { "O arquivo $safeName excede o limite de 5 MB." }
                    require(copied <= remainingTotal) { "A importação atingiu o limite total de 25 MB." }
                    output.write(buffer, 0, count)
                }
            }
            require(temp.renameTo(destination)) { "Não foi possível finalizar a cópia de $safeName." }
            return destination
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun uniqueDestination(name: String): File {
        val base = File(importsDir, name).canonicalFile
        require(base.parentFile == importsDir) { "Nome de arquivo inválido." }
        if (!base.exists()) return base
        val stem = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "").takeIf { it.isNotEmpty() }?.let { ".$it" }.orEmpty()
        var suffix = 2
        while (suffix < 10_000) {
            val candidate = File(importsDir, "$stem ($suffix)$extension").canonicalFile
            if (!candidate.exists() && candidate.parentFile == importsDir) return candidate
            suffix++
        }
        throw IllegalStateException("Não foi possível criar um nome exclusivo para $name.")
    }

    companion object {
        const val MAX_FILE_BYTES = 5L * 1024 * 1024
        const val MAX_TOTAL_BYTES = 25L * 1024 * 1024
        const val MAX_FILES = 100
        const val MAX_DEPTH = 8

        fun sanitizeDisplayName(displayName: String): String {
            val leaf = displayName.replace('\\', '/').substringAfterLast('/').trim()
            val cleaned = leaf
                .replace(Regex("[^\\p{L}\\p{N}._()-]"), "_")
                .trim()
                .trim('.')
                .take(100)
            return cleaned.takeIf { it.isNotBlank() && it != "." && it != ".." } ?: "arquivo-importado"
        }
    }
}
