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

/** Real source browser/editor for the imported project, with path containment checks. */
@Composable
internal fun ProjectCodeEditorScreen(projectDir: String, onBack: () -> Unit) {
    val root = remember(projectDir) { runCatching { File(projectDir).canonicalFile.takeIf { it.isDirectory } }.getOrNull() }
    var refreshToken by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var selectedPath by remember(projectDir) { mutableStateOf<String?>(null) }
    var source by remember(projectDir) { mutableStateOf("") }
    var status by remember(projectDir) { mutableStateOf("Selecione um arquivo do projeto para abrir.") }
    var dirty by remember(projectDir) { mutableStateOf(false) }

    val files = remember(root, refreshToken) {
        root?.walkTopDown()
            ?.onEnter { dir -> dir == root || dir.name !in setOf(".git", ".gradle", "build", "node_modules", ".idea", ".dart_tool") }
            ?.filter { it.isFile }?.take(10001)?.toList().orEmpty()
    }
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

    fun openFile(file: File) {
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

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("ARQUIVOS DO PROJETO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(root?.name ?: "Projeto indisponível", style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = onBack) { Text("Voltar") }
        }
        if (root == null) {
            Text("A pasta importada não está disponível. Importe o projeto novamente.", Modifier.padding(16.dp))
            return@Column
        }
        Text(files.size.toString() + if (files.size > 10000) "+ arquivos encontrados" else " arquivos encontrados", Modifier.padding(horizontal = 14.dp), style = MaterialTheme.typography.bodySmall)
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
                                refreshToken++
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
}

private const val MAX_EDIT_BYTES = 1024L * 1024L
