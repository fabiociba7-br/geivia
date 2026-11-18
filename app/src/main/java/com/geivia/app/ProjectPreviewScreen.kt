package com.geivia.app

import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.text.selection.SelectionContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private enum class ProjectContentTab { FILES, SOURCE, WEB_PREVIEW }

@Composable
internal fun ProjectPreviewScreen(
    projectDir: String,
    onBack: () -> Unit,
    onEditor: () -> Unit,
    onTerminal: () -> Unit,
    onOperations: () -> Unit
) {
    val workspace = remember(projectDir) {
        runCatching {
            val root = File(projectDir).canonicalFile
            require(root.isDirectory) { "A pasta do projeto ativo não existe mais." }
            ProjectWorkspaceInfo.scan(root)
        }.getOrNull()
    }
    var selectedPath by remember(projectDir) { mutableStateOf<String?>(null) }
    var tab by remember(projectDir) { mutableStateOf(ProjectContentTab.FILES) }
    val sourceExtensions = setOf("kt", "kts", "java", "xml", "json", "gradle", "properties", "yaml", "yml", "html", "htm", "css", "js", "ts", "tsx", "jsx", "dart", "md", "txt", "toml", "sh", "sql", " rules")
    val visibleFiles = workspace?.files.orEmpty().filter { file ->
        val ext = file.extension.lowercase()
        ext in sourceExtensions || file.name in setOf("gradlew", "Dockerfile", "Makefile", ".firebaserc", "firebase.json")
    }.sortedBy { it.relativeTo(workspace!!.root).invariantSeparatorsPath.lowercase() }
    LaunchedEffect(projectDir, visibleFiles.size) {
        if (selectedPath == null || visibleFiles.none { it.relativeTo(workspace!!.root).invariantSeparatorsPath == selectedPath }) {
            val preferred = visibleFiles.firstOrNull { it.extension.lowercase() in setOf("kt", "java", "dart", "html", "xml") }
                ?: visibleFiles.firstOrNull()
            selectedPath = preferred?.relativeTo(workspace!!.root)?.invariantSeparatorsPath
        }
    }
    val selectedFile = remember(workspace, selectedPath) {
        val root = workspace?.root
        if (root == null || selectedPath == null) null else runCatching {
            val file = File(root, selectedPath!!).canonicalFile
            require(file.toPath().startsWith(root.toPath())) { "Caminho fora da pasta do projeto." }
            file.takeIf { it.isFile }
        }.getOrNull()
    }
    val sourceText by produceState<String?>(initialValue = null, selectedFile) {
        value = if (selectedFile == null) null else withContext(Dispatchers.IO) {
            runCatching {
                require(selectedFile.length() <= 1024L * 1024L) { "Arquivo maior que 1 MB. Abra pelo Terminal para consultar o conteúdo completo." }
                val bytes = selectedFile.readBytes()
                require(bytes.none { it == 0.toByte() }) { "Este arquivo parece ser binário e não pode ser exibido como texto." }
                String(bytes, Charsets.UTF_8)
            }.getOrElse { "Não foi possível abrir este arquivo: ${it.message}" }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("PROJETO ATIVO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(workspace?.name ?: "Nenhum projeto carregado", style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = onBack) { Text("Voltar") }
        }
        if (workspace == null) {
            Text("Não foi possível abrir a pasta do projeto. Volte à Central de Projetos e importe a pasta novamente.", Modifier.padding(16.dp))
            return@Column
        }
        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    when (workspace.kind) {
                        ProjectWorkspaceInfo.Kind.ANDROID -> "Projeto Android nativo"
                        ProjectWorkspaceInfo.Kind.FLUTTER -> "Projeto Flutter"
                        ProjectWorkspaceInfo.Kind.WEB -> "Projeto Web"
                        ProjectWorkspaceInfo.Kind.NODE -> "Projeto Node.js"
                        ProjectWorkspaceInfo.Kind.UNKNOWN -> "Tipo de projeto não identificado"
                    },
                    style = MaterialTheme.typography.titleSmall
                )
                Text("${workspace.files.size} arquivos indexados · ${visibleFiles.size} arquivos de texto reconhecidos", style = MaterialTheme.typography.bodySmall)
                Text(workspace.root.absolutePath, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = onEditor, modifier = Modifier.weight(1f)) { Text("Editor GEIVIA") }
                    OutlinedButton(onClick = onTerminal, modifier = Modifier.weight(1f)) { Text("Terminal") }
                    OutlinedButton(onClick = onOperations, modifier = Modifier.weight(1f)) { Text("Ferramentas") }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = tab == ProjectContentTab.FILES, onClick = { tab = ProjectContentTab.FILES }, label = { Text("Arquivos") })
            FilterChip(selected = tab == ProjectContentTab.SOURCE, onClick = { tab = ProjectContentTab.SOURCE }, label = { Text("Código-fonte") })
            if (workspace.kind == ProjectWorkspaceInfo.Kind.WEB && workspace.webEntry != null) {
                FilterChip(selected = tab == ProjectContentTab.WEB_PREVIEW, onClick = { tab = ProjectContentTab.WEB_PREVIEW }, label = { Text("Prévia Web") })
            }
        }
        when (tab) {
            ProjectContentTab.FILES -> {
                Text("ARQUIVOS DO PROJETO", Modifier.padding(start = 14.dp, bottom = 4.dp), style = MaterialTheme.typography.labelMedium)
                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)) {
                    items(visibleFiles, key = { it.relativeTo(workspace.root).invariantSeparatorsPath }) { file ->
                        val path = file.relativeTo(workspace.root).invariantSeparatorsPath
                        Row(
                            Modifier.fillMaxWidth().clickable { selectedPath = path; tab = ProjectContentTab.SOURCE }.padding(vertical = 9.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(path, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text(if (file.extension.lowercase() in setOf("xml", "html", "htm")) "Tela" else "Código", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                        }
                        HorizontalDivider()
                    }
                }
            }
            ProjectContentTab.SOURCE -> {
                Text(selectedPath ?: "Selecione um arquivo", Modifier.padding(horizontal = 14.dp), style = MaterialTheme.typography.labelMedium)
                if (visibleFiles.isNotEmpty()) {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                            Text(selectedPath ?: "Escolher arquivo")
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                            visibleFiles.forEach { file ->
                                val path = file.relativeTo(workspace.root).invariantSeparatorsPath
                                DropdownMenuItem(text = { Text(path, fontSize = 12.sp) }, onClick = { selectedPath = path; expanded = false })
                            }
                        }
                    }
                }
                if (sourceText == null) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
                } else {
                    SelectionContainer {
                        Text(
                            sourceText.orEmpty(),
                            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
            ProjectContentTab.WEB_PREVIEW -> {
                val entry = workspace.webEntry
                if (entry != null) {
                    AndroidView(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.allowFileAccess = true
                                settings.allowContentAccess = false
                                settings.allowFileAccessFromFileURLs = false
                                settings.allowUniversalAccessFromFileURLs = false
                                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                webViewClient = WebViewClient()
                                webChromeClient = WebChromeClient()
                                loadUrl(android.net.Uri.fromFile(entry).toString())
                            }
                        }
                    )
                }
            }
        }
    }
}
