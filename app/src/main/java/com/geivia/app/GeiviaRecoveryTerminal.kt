package com.geivia.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import id.or.oo.pr.engine.ProotLauncher

@Composable
internal fun GeiviaRecoveryTerminal(controller: LinuxRuntimeController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var session by remember { mutableStateOf<ProotLauncher.Session?>(null) }
    var command by remember { mutableStateOf("") }
    var output by remember {
        mutableStateOf("Terminal de recuperação GEIVIA\nO terminal executa comandos dentro do Debian/PRoot do aplicativo.\n")
    }
    var starting by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    fun append(text: String) {
        output = (output + text).takeLast(24000)
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
                append("\n[GEIVIA] Sessão Debian iniciada. Digite um comando e toque em Executar.\n")
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            controller.readSession(opened) { chunk -> scope.launch { append(chunk) } }
                        }
                        append("\n[GEIVIA] Sessão encerrada.\n")
                    } catch (error: Throwable) {
                        append("\n[ERRO] A sessão foi encerrada: ${error.message ?: "falha de leitura do terminal"}.\n")
                    } finally {
                        session = null
                    }
                }
            } catch (error: Throwable) {
                append("\n[ERRO] ${error.message ?: "Não foi possível iniciar o Debian."}\n")
            } finally {
                starting = false
            }
        }
    }

    fun sendCommand() {
        val active = session ?: return
        val value = command.trim()
        if (value.isEmpty() || busy) return
        command = ""
        append("\n$ $value\n")
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { controller.send(active, value + "\n") }
            } catch (error: Throwable) {
                append("\n[ERRO] ${error.message ?: "Falha ao enviar comando."}\n")
            } finally {
                busy = false
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("Terminal de recuperação", color = Color(0xFF102B43), fontSize = 18.sp)
        Text(
            "Use este terminal para corrigir problemas no Debian e continuar a instalação sem sair do GEIVIA. Os comandos podem instalar, atualizar ou alterar pacotes dentro do ambiente Linux.",
            color = Color(0xFF587083), fontSize = 12.sp, lineHeight = 17.sp
        )
        Text(
            "Ambiente isolado: Debian/PRoot do GEIVIA. Não é um terminal root do Android e não acessa dados privados de outros aplicativos.",
            color = Color(0xFF9A641B), fontSize = 11.sp, lineHeight = 15.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { startTerminal() }, enabled = session == null && !starting) {
                Text(if (starting) "Iniciando…" else "Abrir terminal")
            }
            OutlinedButton(onClick = {
                controller.cancel()
                session = null
                append("\n[GEIVIA] Solicitação de encerramento enviada.\n")
            }, enabled = session != null) { Text("Encerrar") }
        }
        OutlinedButton(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("GEIVIA terminal", output))
                Toast.makeText(context, "Saída do terminal copiada.", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Copiar saída do terminal") }
        Text("Sugestões para reparar o dpkg:", color = Color(0xFF102B43), fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { command = "dpkg --configure -a" }, enabled = session != null) {
                Text("Corrigir dpkg", fontSize = 11.sp)
            }
            OutlinedButton(onClick = { command = "apt-get -f install -y" }, enabled = session != null) {
                Text("Reparar pacotes", fontSize = 11.sp)
            }
        }
        Column(
            Modifier.fillMaxWidth().height(230.dp)
                .background(Color(0xFF101820), RoundedCornerShape(10.dp))
                .verticalScroll(rememberScrollState()).padding(10.dp)
        ) {
            Text(output, color = Color(0xFFE2EDF5), fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp)
        }
        OutlinedTextField(
            value = command,
            onValueChange = { command = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Comando Linux") },
            placeholder = { Text("Ex.: dpkg --configure -a") },
            enabled = session != null && !busy,
            singleLine = true
        )
        Button(onClick = { sendCommand() }, enabled = session != null && !busy && command.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "Enviando…" else "Executar comando")
        }
        Spacer(Modifier.height(2.dp))
    }
}
