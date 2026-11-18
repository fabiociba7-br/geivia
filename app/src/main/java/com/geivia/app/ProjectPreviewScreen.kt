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
import java.io.File

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
    var showFiles by remember(projectDir) { mutableStateOf(true) }
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
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    when (workspace.kind) {
                        ProjectWorkspaceInfo.Kind.ANDROID -> "Projeto Android nativo detectado"
                        ProjectWorkspaceInfo.Kind.FLUTTER -> "Projeto Flutter detectado"
                        ProjectWorkspaceInfo.Kind.WEB -> "Projeto Web detectado"
                        ProjectWorkspaceInfo.Kind.NODE -> "Projeto Node.js detectado"
                        ProjectWorkspaceInfo.Kind.UNKNOWN -> "Tipo de projeto não identificado"
                    },
                    style = MaterialTheme.typography.titleSmall
                )
                Text(workspace.root.absolutePath, style = MaterialTheme.typography.bodySmall)
                Text("Arquivos indexados: ${workspace.files.size}", style = MaterialTheme.typography.bodySmall)
                if (workspace.kind == ProjectWorkspaceInfo.Kind.ANDROID) {
                    Text("Manifest Android: ${if (workspace.hasManifest) "encontrado" else "não encontrado"}")
                    Text("Gradle Wrapper: ${if (workspace.hasGradleWrapper) "encontrado" else "não encontrado"}")
                    Text("Módulos Gradle: ${workspace.moduleNames.joinToString().ifBlank { "não identificados" }}")
                    Text("Firebase: ${if (workspace.hasFirebaseConfig) "configuração encontrada" else "sem firebase.json"}")
                    Text("A prévia executável de um app Android exige compilar e instalar um APK. Esta tela confirma a leitura e a estrutura do projeto; não simula o app nativo dentro de uma página HTML.")
                } else if (workspace.webEntry != null) {
                    Text("Página de entrada: ${workspace.webEntry.relativeTo(workspace.root).invariantSeparatorsPath}")
                } else {
                    Text("O projeto foi reconhecido e sua estrutura está disponível. Não foi encontrada uma entrada Web para renderização em WebView.")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEditor, modifier = Modifier.weight(1f)) { Text("Editor") }
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
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("ESTRUTURA DO PROJETO", style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = { showFiles = !showFiles }) { Text(if (showFiles) "Ocultar" else "Mostrar") }
            }
            if (showFiles) {
                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)) {
                    items(workspace.relativeFiles, key = { it }) { path ->
                        Text(path, Modifier.fillMaxWidth().padding(vertical = 5.dp), style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
