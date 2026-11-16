package com.geivia.app

import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
internal fun ProjectPreviewScreen(projectDir: String, onBack: () -> Unit) {
    val root = remember(projectDir) { File(projectDir).canonicalFile }
    val entry = remember(root) {
        listOf("index.html", "public/index.html", "dist/index.html", "build/index.html")
            .map { File(root, it) }.firstOrNull { it.isFile }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("PRÉVIA DO PROJETO", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onBack) { Text("Voltar") }
        }
        if (entry == null) {
            Text("Não encontrei uma página HTML de entrada. Para projetos Android, o GEIVIA precisará compilar e instalar uma versão de teste; para web, inclua index.html ou gere dist/build.", Modifier.padding(16.dp))
        } else {
            Text("Prévia local: ${entry.relativeTo(root).path}", Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.labelSmall)
            AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = { ctx ->
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
            })
        }
    }
}
