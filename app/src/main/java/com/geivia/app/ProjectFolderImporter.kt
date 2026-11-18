package com.geivia.app

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.File
import java.io.InputStream

/**
 * Imports a user-selected project directory into GEIVIA's private Debian home.
 * Relative paths are preserved and the source directory is never modified.
 */
internal object ProjectFolderImporter {
    private const val MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024
    private const val MAX_ENTRIES = 50_000
    private const val MAX_DEPTH = 64

    fun import(context: Context, treeUri: Uri, destinationRoot: File): File {
        val projectsRoot = File(destinationRoot, "geivia-projects").canonicalFile
        require(projectsRoot.mkdirs() || projectsRoot.isDirectory) {
            "Não foi possível preparar a pasta de projetos."
        }

        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
        val rootName = queryName(context, rootUri) ?: "projeto-importado"
        val safeRootName = safeSegment(rootName)
        val project = uniqueProjectDirectory(projectsRoot, safeRootName)
        require(project.mkdirs()) { "Não foi possível criar a pasta de destino do projeto." }

        var totalBytes = 0L
        var entries = 0

        fun copyDocument(documentId: String, relativePath: String, depth: Int) {
            require(depth <= MAX_DEPTH) {
                "A estrutura do projeto ultrapassa o limite de $MAX_DEPTH níveis."
            }
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            val projection = arrayOf(
                Document.COLUMN_DOCUMENT_ID,
                Document.COLUMN_DISPLAY_NAME,
                Document.COLUMN_MIME_TYPE
            )
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)
                val nameColumn = cursor.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)

                while (cursor.moveToNext()) {
                    val childId = cursor.getString(idColumn) ?: continue
                    val displayName = cursor.getString(nameColumn) ?: "arquivo"
                    val mimeType = cursor.getString(mimeColumn)
                    val segment = safeSegment(displayName)
                    val childRelative = if (relativePath.isEmpty()) segment else "$relativePath/$segment"
                    val target = File(project, childRelative).canonicalFile
                    require(target.path.startsWith(project.path + File.separator)) {
                        "A pasta selecionada contém um caminho inválido."
                    }
                    entries++
                    require(entries <= MAX_ENTRIES) {
                        "O projeto contém mais de $MAX_ENTRIES arquivos e pastas."
                    }

                    if (mimeType == Document.MIME_TYPE_DIR) {
                        require(target.mkdirs() || target.isDirectory) {
                            "Não foi possível criar a pasta $childRelative."
                        }
                        copyDocument(childId, childRelative, depth + 1)
                    } else {
                        require(target.parentFile?.let { it.mkdirs() || it.isDirectory } == true) {
                            "Não foi possível preparar a pasta de $childRelative."
                        }
                        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                        val input = context.contentResolver.openInputStream(documentUri)
                            ?: error("Não foi possível ler o arquivo $childRelative.")
                        input.use { source ->
                            target.outputStream().buffered().use { output ->
                                val buffer = ByteArray(32 * 1024)
                                while (true) {
                                    val count = source.read(buffer)
                                    if (count < 0) break
                                    totalBytes += count
                                    require(totalBytes <= MAX_TOTAL_BYTES) {
                                        "O projeto ultrapassa o limite de 2 GB para importação."
                                    }
                                    output.write(buffer, 0, count)
                                }
                            }
                        }
                    }
                }
            } ?: throw IllegalStateException("Não foi possível listar a pasta selecionada.")
        }

        try {
            copyDocument(rootId, "", 0)
            // Resolve a single enclosing directory when the picker points one level above the actual project.
            // firebase.json is metadata and must never replace the Android project's root.
            return ProjectWorkspaceInfo.resolveRoot(project)
        } catch (error: Throwable) {
            project.deleteRecursively()
            throw error
        }
    }

    private fun queryName(context: Context, uri: Uri): String? {
        context.contentResolver.query(
            uri,
            arrayOf(Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(Document.COLUMN_DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return null
    }

    private fun safeSegment(name: String): String {
        val cleaned = name.trim()
            .replace('/', '_')
            .replace(92.toChar(), '_')
            .replace(Regex("[\\p{Cntrl}]"), "_")
        require(cleaned.isNotBlank() && cleaned != "." && cleaned != "..") {
            "A pasta selecionada contém um nome de arquivo inválido."
        }
        return cleaned.take(240)
    }

    private fun uniqueProjectDirectory(root: File, name: String): File {
        var candidate = File(root, name).canonicalFile
        require(candidate.parentFile == root) { "Nome da pasta do projeto inválido." }
        var suffix = 2
        while (candidate.exists()) {
            candidate = File(root, "$name ($suffix)").canonicalFile
            suffix++
        }
        return candidate
    }
}
