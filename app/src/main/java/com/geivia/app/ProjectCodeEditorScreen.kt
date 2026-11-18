package com.geivia.app

import android.util.AtomicFile
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Real source browser/editor for the imported project, with path containment checks. */
@Composable
internal fun ProjectCodeEditorScreen(projectDir: String, initialFilePath: String? = null, onBack: () -> Unit) {
    // Same resolved root used by the preview and terminal, so every screen sees the same project.
    val root = remember(projectDir) {
        runCatching {
            ProjectWorkspaceInfo.resolveRoot(File(projectDir)).canonicalFile.takeIf { it.isDirectory }
        }.getOrNull()
    }
    var query by remember { mutableStateOf("") }
    var selectedPath by remember(projectDir) { mutableStateOf<String?>(null) }
    var source by remember(projectDir) { mutableStateOf("") }
    var status by remember(projectDir) { mutableStateOf("Selecione um arquivo do projeto para abrir.") }
    var dirty by remember(projectDir) { mutableStateOf(false) }
    var pendingOpen by remember(projectDir) { mutableStateOf<File?>(null) }
    var confirmLeave by remember(projectDir) { mutableStateOf(false) }

    // Listing a large project on the main thread can freeze the UI, so it runs on IO.
    val filesState = produceState<List<File>?>(null, root) {
        value = withContext(Dispatchers.IO) {
            val base = root ?: return@withContext emptyList<File>()
            base.walkTopDown()
                .onEnter { dir -> dir == base || dir.name !in ProjectWorkspaceInfo.IGNORED_DIRS }
                .filter { it.isFile }
                .take(10001)
                .sortedWith(compareBy<File>({ editorFilePriority(it) }, { it.relativeTo(base).invariantSeparatorsPath }))
                .toList()
        }
    }
    val files = filesState.value.orEmpty()
    val visibleFiles = remember(files, query) {
        files.asSequence()
            .filter { query.isBlank() || it.relativeTo(root!!).invariantSeparatorsPath.contains(query, ignoreCase = true) }
            .take(10000).toList()
    }

    fun safeFile(relative: String): File? {
        val base = root ?: return null
        return runCatching {
            val candidate = File(base, relative).canonicalFile
            candidate.takeIf { it.path.startsWith(base.path + File.separator) && it.isFile }
        }.getOrNull()
    }

    fun loadFile(file: File) {
        val relative = runCatching { file.canonicalFile.relativeTo(root!!).invariantSeparatorsPath }.getOrNull()
        if (relative == null) { status = "Arquivo fora da pasta do projeto."; return }
        if (file.length() > MAX_EDIT_BYTES) { status = "Arquivo maior que 1 MB. Ele aparece na lista, mas não pode ser aberto neste editor."; return }
        val text = runCatching { file.readText(Charsets.UTF_8) }.getOrElse {
            status = "Não foi possível ler este arquivo como texto UTF-8."; return
        }
        if (text.contains('\u0000')) { status = "Arquivo binário: visualização de código indisponível."; return }
        selectedPath = relative
        source = text
        dirty = false
        status = "Arquivo carregado: " + relative
    }

    fun openFile(file: File) {
        if (dirty && selectedPath != null) pendingOpen = file else loadFile(file)
    }

    LaunchedEffect(initialFilePath, filesState.value != null) {
        if (filesState.value == null || selectedPath != null) return@LaunchedEffect
        val requested = initialFilePath?.takeIf { it.isNotBlank() }
        val firstUsefulFile = files.firstOrNull { editorFilePriority(it) == 0 }
        val target = requested?.let(::safeFile) ?: firstUsefulFile
        if (target != null) {
            loadFile(target)
        } else if (requested != null) {
            status = "A tela foi localizada no índice, mas o arquivo não está disponível no projeto."
        } else {
            status = "Nenhum arquivo de código reconhecido. Use a busca para localizar um arquivo."
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("ARQUIVOS DO PROJETO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(root?.name ?: "Projeto indisponível", style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = { if (dirty) confirmLeave = true else onBack() }) { Text("Voltar") }
        }
        if (root == null) {
            Text("A pasta importada não está disponível. Importe o projeto novamente.", Modifier.padding(16.dp))
            return@Column
        }
        Text(
            if (filesState.value == null) "Lendo arquivos do projeto…"
            else files.size.toString() + if (files.size > 10000) "+ arquivos encontrados" else " arquivos encontrados",
            Modifier.padding(horizontal = 14.dp), style = MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            singleLine = true, label = { Text("Buscar arquivo ou pasta") }
        )
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LazyColumn(Modifier.weight(0.9f).fillMaxHeight().padding(start = 12.dp)) {
                items(visibleFiles, key = { it.relativeTo(root).invariantSeparatorsPath }) { file ->
                    val relative = file.relativeTo(root).invariantSeparatorsPath
                    TextButton(onClick = { openFile(file) }, modifier = Modifier.fillMaxWidth()) {
                        Text(relative, modifier = Modifier.fillMaxWidth(), maxLines = 2, fontSize = 11.sp,
                            color = if (relative == selectedPath) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                    HorizontalDivider()
                }
            }
            Column(Modifier.weight(1.1f).fillMaxHeight().padding(end = 12.dp)) {
                Text(selectedPath ?: "Nenhum arquivo aberto", style = MaterialTheme.typography.labelSmall, maxLines = 2)
                OutlinedTextField(
                    value = source, onValueChange = { source = it; dirty = true },
                    modifier = Modifier.weight(1f).fillMaxWidth(), enabled = selectedPath != null,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    placeholder = { Text("O conteúdo do arquivo aparecerá aqui.") }
                )
                Button(
                    onClick = {
                        val path = selectedPath
                        val target = path?.let { safeFile(it) }
                        if (target == null) {
                            status = "O arquivo selecionado não existe mais ou está fora do projeto."
                        } else if (source.toByteArray(Charsets.UTF_8).size > MAX_EDIT_BYTES) {
                            status = "O conteúdo excede o limite de 1 MB."
                        } else {
                            runCatching {
                                val atomic = AtomicFile(target)
                                val stream = atomic.startWrite()
                                try {
                                    stream.write(source.toByteArray(Charsets.UTF_8))
                                    atomic.finishWrite(stream)
                                } catch (error: Throwable) {
                                    atomic.failWrite(stream)
                                    throw error
                                }
                            }.onSuccess {
                                dirty = false
                                status = "Salvo no projeto: " + path
                            }.onFailure {
                                status = "Falha ao salvar: " + (it.message ?: "erro de gravação")
                            }
                        }
                    },
                    enabled = selectedPath != null && dirty,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                ) { Text("Salvar arquivo") }
            }
        }
        Text(status + if (dirty) " • alterações não salvas" else "",
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (dirty) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    }
    pendingOpen?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingOpen = null },
            title = { Text("Alterações não salvas") },
            text = { Text("O arquivo aberto tem alterações não salvas. Abrir outro arquivo vai descartá-las.") },
            confirmButton = { TextButton(onClick = { pendingOpen = null; loadFile(target) }) { Text("Descartar e abrir") } },
            dismissButton = { TextButton(onClick = { pendingOpen = null }) { Text("Continuar editando") } }
        )
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Alterações não salvas") },
            text = { Text("Sair agora vai descartar as alterações não salvas deste arquivo.") },
            confirmButton = { TextButton(onClick = { confirmLeave = false; onBack() }) { Text("Descartar e sair") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Continuar editando") } }
        )
    }

}

/** Código-fonte primeiro; configurações e recursos continuam acessíveis na lista. */
private fun editorFilePriority(file: File): Int = when (file.extension.lowercase()) {
    "kt", "java", "dart", "js", "jsx", "ts", "tsx", "html", "htm", "xml", "css", "scss", "vue", "svelte" -> 0
    "gradle", "kts", "json", "yaml", "yml", "toml", "properties", "md", "txt", "sh", "rules" -> 1
    "png", "jpg", "jpeg", "webp", "svg", "gif", "ico", "ttf", "otf", "woff", "woff2" -> 2
    else -> 3
}

private const val MAX_EDIT_BYTES = 1024L * 1024L
