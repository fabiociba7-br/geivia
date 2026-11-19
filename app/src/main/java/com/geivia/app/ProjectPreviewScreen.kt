package com.geivia.app

import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import java.io.FileInputStream
import java.net.URLConnection

/**
 * Serves project files through an isolated virtual origin.
 *
 * Using file:// together with allowFileAccessFromFileURLs=false prevents local
 * CSS, JavaScript and image files from being loaded. Enabling that setting would
 * expose unrelated device files to project HTML, so only files canonically
 * contained in the selected project directory are served here.
 */
private class ProjectPreviewWebViewClient(projectRoot: File) : WebViewClient() {
    private val root = projectRoot.canonicalFile
    private val rootPrefix = root.path.trimEnd(File.separatorChar) + File.separator

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val uri = request.url
        if (uri.scheme != "https" || uri.host != "geivia.local") return null
        if (!request.method.equals("GET", ignoreCase = true)) {
            return WebResourceResponse("text/plain", "utf-8", 405, "Method Not Allowed", emptyMap(), null)
        }

        return try {
            val relativePath = Uri.decode(uri.encodedPath.orEmpty()).trimStart('/')
            val requested = if (relativePath.isBlank()) File(root, "index.html") else File(root, relativePath)
            val target = requested.canonicalFile

            // Reject traversal and symlinks that point outside the selected project.
            if (target != root && !target.path.startsWith(rootPrefix)) {
                return WebResourceResponse("text/plain", "utf-8", 403, "Forbidden", emptyMap(), null)
            }
            if (!target.isFile || !target.canRead()) {
                return WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), null)
            }

            val mime = mimeTypeFor(target)
            val charset = if (mime.startsWith("text/") || mime in setOf(
                    "application/javascript",
                    "application/json",
                    "application/xml",
                    "image/svg+xml"
                )
            ) "utf-8" else null

            WebResourceResponse(
                mime,
                charset,
                200,
                "OK",
                mapOf("Cache-Control" to "no-cache"),
                FileInputStream(target)
            )
        } catch (_: Exception) {
            WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), null)
        }
    }

    private fun mimeTypeFor(file: File): String {
        return when (file.extension.lowercase()) {
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js", "mjs" -> "application/javascript"
            "json", "map" -> "application/json"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "avif" -> "image/avif"
            "ico" -> "image/x-icon"
            "bmp" -> "image/bmp"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "eot" -> "application/vnd.ms-fontobject"
            "xml" -> "application/xml"
            "txt" -> "text/plain"
            "wasm" -> "application/wasm"
            else -> URLConnection.guessContentTypeFromName(file.name) ?: "application/octet-stream"
        }
    }
}

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
                        "Esta tela não vai inventar um layout aproximado e apresentá-lo como se fosse o jogo real. A prévia fiel será feita executando o projeto compilado dentro do fluxo do GEIVIA.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
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
                        // Project assets are served only from the selected project root
                        // by ProjectPreviewWebViewClient; broad file access stays disabled.
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.allowFileAccessFromFileURLs = false
                        settings.allowUniversalAccessFromFileURLs = false
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        webViewClient = ProjectPreviewWebViewClient(workspace.root)
                        webChromeClient = WebChromeClient()
                        val relativeEntry = workspace.webEntry.relativeTo(workspace.root).invariantSeparatorsPath
                        val encodedEntry = relativeEntry.split('/').joinToString("/") { Uri.encode(it) }
                        loadUrl("https://geivia.local/$encodedEntry")
                    }
                }
            )
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
                    items(workspace.relativeFiles, key = { it }) { path ->
                        Text(path, Modifier.fillMaxWidth().padding(vertical = 5.dp), style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
