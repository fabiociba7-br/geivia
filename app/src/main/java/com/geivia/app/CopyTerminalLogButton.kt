package com.geivia.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

private fun sanitizeTerminalLog(value: String): String {
    var safe = value
    val sensitivePatterns = listOf(
        Regex("(?i)(access_token|refresh_token|client_secret|password|authorization_code|verification_code|code)(\\\\s*[=:]\\\\s*)[^\\\\s]+"),
        Regex("(?i)(--token\\\\s+)[^\\\\s]+"),
        Regex("\\\\b[A-Z0-9]{4}-[A-Z0-9]{4}\\\\b")
    )
    sensitivePatterns.forEachIndexed { index, pattern ->
        safe = if (index == 2) safe.replace(pattern, "[CÓDIGO REDIGIDO]")
        else safe.replace(pattern) { match -> match.groupValues[1] + match.groupValues[2] + "[REDACTED]" }
    }
    return safe
}

@Composable
internal fun CopyTerminalLogButton(log: String, label: String = "Copiar log", modifier: Modifier = Modifier) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Log GEIVIA", sanitizeTerminalLog(log)))
            Toast.makeText(context, "Log copiado com credenciais e códigos ocultados.", Toast.LENGTH_SHORT).show()
        },
        enabled = log.isNotBlank(),
        modifier = modifier
    ) { Text(label) }
}
