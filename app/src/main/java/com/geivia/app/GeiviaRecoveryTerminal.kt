package com.geivia.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import id.or.oo.pr.engine.ProotLauncher

private val TerminalBackground = Color(0xFF101820)
private val TerminalForeground = Color(0xFFE2EDF5)
private val TerminalAccent = Color(0xFF7EE787)

@Composable
internal fun GeiviaRecoveryTerminal(
    controller: LinuxRuntimeController,
    onSessionActive: (Boolean) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var session by remember { mutableStateOf<ProotLauncher.Session?>(null) }
    var command by remember { mutableStateOf("") }
    var output by remember {
        mutableStateOf("Terminal GEIVIA\nDebian/PRoot • ambiente Linux privado do aplicativo\n")
    }
    var starting by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var fullScreen by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    fun append(text: String) {
        output = (output + text).takeLast(120000)
    }

    fun startTerminal() {
        if (starting || session != null) return
        starting = true
        scope.launch {
            try {
                val opened = withContext(Dispatchers.IO) {
                    controller.prepareEngine()
                    controller.startDebianShell()
                }
                session = opened
                onSessionActive(true)
                append("\n[GEIVIA] Sessão Debian iniciada.\n")
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            controller.readSession(opened) { chunk ->
                                scope.launch { append(chunk) }
                            }
                        }
                        append("\n[GEIVIA] Sessão encerrada.\n")
                    } catch (error: Throwable) {
                        append("\n[ERRO] ${error.message ?: "Falha ao ler a sessão."}\n")
                    } finally {
                        session = null
                        onSessionActive(false)
                    }
                }
            } catch (error: Throwable) {
                onSessionActive(false)
                append("\n[ERRO] ${error.message ?: "Não foi possível iniciar o Debian."}\n")
            } finally {
                starting = false
            }
        }
    }

    fun sendText(value: String) {
        val active = session ?: return
        if (value.isEmpty() || busy) return
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    controller.send(active, value)
                }
            } catch (error: Throwable) {
                append("\n[ERRO DE ENTRADA] ${error.message ?: "Não foi possível enviar os dados."}\n")
            } finally {
                busy = false
            }
        }
    }

    fun executeInput() {
        val value = command
        if (value.isBlank() || session == null || busy) return
        command = ""
        sendText(if (value.endsWith("\n")) value else value + "\n")
    }

    fun copyOutput() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("GEIVIA terminal", output))
        Toast.makeText(context, "Saída copiada.", Toast.LENGTH_SHORT).show()
    }

    fun pasteInput() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val pasted = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        if (pasted.isNotEmpty()) command += pasted
    }

    LaunchedEffect(output, fullScreen) {
        if (fullScreen) scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Terminal Linux do GEIVIA", color = Color(0xFF102B43), fontSize = 17.sp)
        Text(
            "Terminal Debian/PRoot integrado. A sessão permanece ativa ao abrir ou fechar a visualização em tela cheia.",
            color = Color(0xFF587083), fontSize = 12.sp, lineHeight = 17.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    fullScreen = true
                    if (session == null) startTerminal()
                },
                enabled = !starting
            ) { Text(if (starting) "Iniciando…" else if (session == null) "Abrir terminal" else "Voltar ao terminal") }
            OutlinedButton(
                onClick = { fullScreen = true },
                enabled = session != null
            ) { Text("Tela cheia") }
        }
        if (session != null) {
            Text("Sessão Linux ativa", color = Color(0xFF18794E), fontSize = 11.sp)
        } else {
            Text("A sessão será iniciada ao abrir o terminal.", color = Color(0xFF587083), fontSize = 11.sp)
        }
    }

    if (fullScreen) {
        Dialog(
            onDismissRequest = { fullScreen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = TerminalBackground
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("GEIVIA TERMINAL", color = TerminalAccent, fontSize = 15.sp, fontFamily = FontFamily.Monospace)
                            Text(
                                if (session != null) "Debian/PRoot • sessão ativa" else if (starting) "Iniciando Debian…" else "Sessão encerrada",
                                color = TerminalForeground, fontSize = 11.sp
                            )
                        }
                        OutlinedButton(onClick = { fullScreen = false }) { Text("Minimizar") }
                    }
                    Divider(color = Color(0xFF344653))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { output = "" }) { Text("Limpar tela") }
                        OutlinedButton(onClick = { copyOutput() }) { Text("Copiar") }
                        OutlinedButton(onClick = { pasteInput() }, enabled = session != null) { Text("Colar") }
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(TerminalBackground, RoundedCornerShape(6.dp))
                            .verticalScroll(scrollState)
                            .padding(8.dp)
                    ) {
                        Text(
                            output.ifEmpty { " " },
                            color = TerminalForeground,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it.take(16000) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Comando ou entrada para o shell") },
                        placeholder = { Text("Digite ou cole comandos; várias linhas são aceitas") },
                        enabled = session != null && !busy,
                        minLines = 2,
                        maxLines = 5
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { executeInput() },
                            enabled = session != null && !busy && command.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) { Text(if (busy) "Enviando…" else "Enviar") }
                        OutlinedButton(
                            onClick = { sendText("\u0003") },
                            enabled = session != null && !busy
                        ) { Text("Interromper") }
                        OutlinedButton(
                            onClick = {
                                controller.cancel()
                                append("\n[GEIVIA] Encerramento solicitado. Aguarde a confirmação de fechamento da sessão.\n")
                            },
                            enabled = session != null
                        ) { Text("Encerrar") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { command += "\t" }, enabled = session != null) { Text("Tab") }
                        OutlinedButton(onClick = { command += "\u0004" }, enabled = session != null) { Text("Ctrl+D") }
                        OutlinedButton(onClick = { command = "" }) { Text("Apagar entrada") }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Ambiente isolado do GEIVIA. Não é root do Android. Use Interromper para enviar Ctrl+C ao processo em primeiro plano.",
                        color = Color(0xFF9AA9B8), fontSize = 10.sp, lineHeight = 14.sp
                    )
                }
            }
        }
    }
}
