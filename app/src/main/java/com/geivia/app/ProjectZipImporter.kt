package com.geivia.app

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipInputStream

internal object ProjectZipImporter {
    private const val MAX_EXPANDED_BYTES = 2L * 1024 * 1024 * 1024
    private const val MAX_ENTRIES = 50_000

    fun import(context: Context, uri: Uri, destinationRoot: File): File {
        val root = File(destinationRoot, "geivia-projects").canonicalFile
        require(root.mkdirs() || root.isDirectory) { "Não foi possível preparar a pasta de projetos." }
        val project = File(root, "import-" + System.currentTimeMillis()).canonicalFile
        require(project.parentFile == root && project.mkdirs()) { "Não foi possível criar a pasta do projeto." }
        var expanded = 0L
        var entries = 0
        try {
            val input = context.contentResolver.openInputStream(uri) ?: error("Não foi possível abrir o ZIP selecionado.")
            input.use { raw ->
                ZipInputStream(raw.buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        entries++
                        require(entries <= MAX_ENTRIES) { "O ZIP contém arquivos demais (limite: $MAX_ENTRIES)." }
                        val name = entry.name.replace('\\', '/')
                        require(name.isNotBlank() && !name.startsWith("/") && !name.split('/').any { it == ".." }) {
                            "O ZIP contém um caminho inseguro: $name"
                        }
                        val target = File(project, name).canonicalFile
                        require(target.path == project.path || target.path.startsWith(project.path + File.separator)) {
                            "O ZIP tenta gravar fora da pasta do projeto."
                        }
                        if (entry.isDirectory) {
                            require(target.mkdirs() || target.isDirectory) { "Não foi possível criar uma pasta do ZIP." }
                        } else {
                            require(target.parentFile?.let { it.mkdirs() || it.isDirectory } == true) {
                                "Não foi possível preparar a pasta de um arquivo do ZIP."
                            }
                            target.outputStream().buffered().use { output ->
                                val buffer = ByteArray(32 * 1024)
                                while (true) {
                                    val count = zip.read(buffer)
                                    if (count < 0) break
                                    expanded += count
                                    require(expanded <= MAX_EXPANDED_BYTES) { "O ZIP expandido excede 2 GB." }
                                    output.write(buffer, 0, count)
                                }
                            }
                        }
                        zip.closeEntry()
                    }
                }
            }
            val configs = project.walkTopDown().maxDepth(4).filter { it.isFile && it.name == "firebase.json" && !it.path.contains("/node_modules/") }.toList()
            require(configs.size <= 1) { "O ZIP contém mais de um firebase.json. Separe os projetos antes de importar." }
            return configs.firstOrNull()?.parentFile ?: project
        } catch (error: Throwable) {
            project.deleteRecursively()
            throw error
        }
    }
}
