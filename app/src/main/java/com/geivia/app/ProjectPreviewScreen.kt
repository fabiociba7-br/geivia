package com.geivia.app

import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun ProjectPreviewScreen(
    projectDir: String,
    onBack: () -> Unit,
    onEditor: () -> Unit,
    onCodeEditor: () -> Unit,
    onTerminal: () -> Unit,
    onOperations: () -> Unit
) {
    // Project indexing can involve thousands of files. Never walk the project tree on
    // the UI thread: doing so was capable of freezing or terminating the app on large projects.
    var retryScan by remember(projectDir) { mutableIntStateOf(0) }
    val workspaceState = produceState<Pair<ProjectWorkspaceInfo?, String?>?>(null, projectDir, retryScan) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                require(projectDir.isNotBlank()) { "Nenhum projeto foi selecionado." }
                val selected = File(projectDir).canonicalFile
                require(selected.isDirectory) { "A pasta do projeto ativo não existe mais." }
                ProjectWorkspaceInfo.scan(selected)
            }.fold(
                onSuccess = { it to null },
                onFailure = { null to (it.message ?: "Não foi possível analisar o projeto.") }
            )
        }
    }
    val workspace = workspaceState.value?.first
    val scanError = workspaceState.value?.second
    val screensState = produceState<List<ImportedScreen>>(emptyList(), projectDir, workspace?.root?.absolutePath) {
        value = withContext(Dispatchers.IO) {
            workspace?.takeIf { it.kind == ProjectWorkspaceInfo.Kind.ANDROID }
                ?.let { runCatching { ImportedScreenDiscovery.scan(it.root, maxFiles = 1800) }.getOrDefault(emptyList()) }
                ?: emptyList()
        }
    }
    var showFiles by remember(projectDir) { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("PRÉVIA DO PROJETO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(workspace?.name ?: "Carregando projeto…", style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = onBack) { Text("Voltar") }
        }

        if (workspaceState.value == null || workspace == null) {
            if (workspaceState.value == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                Text("Lendo os arquivos do projeto em segundo plano…", Modifier.padding(16.dp))
            } else {
                Card(Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Não foi possível abrir a prévia", style = MaterialTheme.typography.titleMedium)
                        Text(scanError ?: "O projeto não pôde ser analisado.", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onBack) { Text("Voltar") }
                            Button(onClick = { retryScan++ }) { Text("Tentar novamente") }
                        }
                    }
                }
            }
            return@Column
        }

        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    when (workspace.kind) {
                        ProjectWorkspaceInfo.Kind.ANDROID -> "Aplicativo Android nativo"
                        ProjectWorkspaceInfo.Kind.FLUTTER -> "Aplicativo Flutter"
                        ProjectWorkspaceInfo.Kind.WEB -> "Projeto Web"
                        ProjectWorkspaceInfo.Kind.NODE -> "Projeto Node.js"
                        ProjectWorkspaceInfo.Kind.UNKNOWN -> "Tipo de projeto não identificado"
                    },
                    style = MaterialTheme.typography.titleSmall
                )
                Text("Arquivos analisados: ${workspace.files.size}", style = MaterialTheme.typography.bodySmall)
                if (workspace.kind == ProjectWorkspaceInfo.Kind.ANDROID) {
                    Text("Telas-fonte identificadas: ${screensState.value.size}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Para mostrar o jogo exatamente como aparece no aparelho, o GEIVIA precisa compilar e executar o próprio aplicativo. Uma reconstrução por leitura de textos não representa corretamente mesas, cartas, imagens, animações ou posicionamento.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "As miniaturas abaixo são prévias reconstruídas das telas-fonte identificadas. Elas não executam o app, não reproduzem estado, animações ou integrações nativas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedButton(onClick = onTerminal, modifier = Modifier.fillMaxWidth()) {
                        Text("Preparar build de prévia")
                    }
                    Text(
                        "O botão abre o terminal existente do GEIVIA para preparar o comando no ambiente do projeto. Nenhuma compilação é iniciada automaticamente; execute o comando manualmente. Esta tela não dispara operações remotas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (workspace.webEntry != null) {
                    Text("Página de entrada: ${workspace.webEntry.relativeTo(workspace.root).invariantSeparatorsPath}", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("A estrutura foi reconhecida, mas não há uma página de entrada Web para renderizar.", style = MaterialTheme.typography.bodySmall)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEditor, modifier = Modifier.weight(1f)) { Text("Editor visual") }
                    OutlinedButton(onClick = onCodeEditor, modifier = Modifier.weight(1f)) { Text("Arquivos e código") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onTerminal, modifier = Modifier.weight(1f)) { Text("Terminal") }
                    Button(onClick = onOperations, modifier = Modifier.weight(1f)) { Text("Ferramentas") }
                }
            }
        }

        if (workspace.kind == ProjectWorkspaceInfo.Kind.WEB && workspace.webEntry != null) {
            Text("PRÉVIA WEB", Modifier.padding(start = 14.dp, top = 12.dp), style = MaterialTheme.typography.labelMedium)
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
                        loadUrl(android.net.Uri.fromFile(workspace.webEntry).toString())
                    }
                }
            )
        } else if (workspace.kind == ProjectWorkspaceInfo.Kind.ANDROID) {
            if (screensState.value.isEmpty()) {
                Card(Modifier.fillMaxWidth().padding(12.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Nenhuma tela reconstruível encontrada", style = MaterialTheme.typography.titleSmall)
                        Text("O GEIVIA não vai inventar uma interface para representar o aplicativo. Abra o editor visual para criar uma representação manual ou consulte os arquivos-fonte.", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onEditor, modifier = Modifier.weight(1f)) { Text("Editor visual") }
                            OutlinedButton(onClick = onCodeEditor, modifier = Modifier.weight(1f)) { Text("Arquivos") }
                        }
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Text("PRÉVIAS RECONSTRUÍDAS DO CÓDIGO", style = MaterialTheme.typography.labelMedium)
                        Text("Leitura parcial do código-fonte; não é execução nativa.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(screensState.value, key = { it.sourcePath + "#" + it.name }) { screen ->
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(screen.name, style = MaterialTheme.typography.titleSmall)
                            ImportedScreenPreview(screen)
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("ARQUIVOS DO PROJETO", style = MaterialTheme.typography.labelMedium)
                            TextButton(onClick = { showFiles = !showFiles }) { Text(if (showFiles) "Ocultar" else "Mostrar") }
                        }
                    }
                    if (showFiles) {
                        items(workspace.relativeFiles, key = { "file_$it" }) { filePath ->
                            Text(filePath, Modifier.fillMaxWidth().padding(vertical = 5.dp), style = MaterialTheme.typography.bodySmall)
                            HorizontalDivider()
                        }
                    }
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("ARQUIVOS DO PROJETO", style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = { showFiles = !showFiles }) { Text(if (showFiles) "Ocultar" else "Mostrar") }
            }
            if (showFiles) {
                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)) {
                    items(workspace.relativeFiles, key = { it }) { filePath ->
                        Text(filePath, Modifier.fillMaxWidth().padding(vertical = 5.dp), style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
