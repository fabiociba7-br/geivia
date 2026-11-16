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

@Composable
internal fun CopyTerminalLogButton(log: String, label: String = "Copiar log", modifier: Modifier = Modifier) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Log GEIVIA", log))
            Toast.makeText(context, "Log copiado.", Toast.LENGTH_SHORT).show()
        },
        enabled = log.isNotBlank(),
        modifier = modifier
    ) { Text(label) }
}
