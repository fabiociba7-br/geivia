package com.geivia.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
internal fun GeiviaToolchainUpdatesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val controller = remember { LinuxRuntimeController(context) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf("Toque em “Verificar atualizações” para consultar as versões disponíveis nos repositórios oficiais.") }
    var output by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    DisposableEffect(controller) { onDispose { controller.cancel() } }

    fun runTask(label: String, command: List<String>) {
        if (busy) return
        scope.launch {
            busy = true
            status = label
            output = ""
            try {
                val code = controller.execute(command) { chunk ->
                    scope.launch { output = (output + chunk).takeLast(18000) }
                }
                check(code == 0) { "$label falhou (código $code)." }
                status = "$label concluído."
                if (label.startsWith("Verificação")) report = output
            } catch (e: Exception) {
                status = "Falha: ${e.message ?: "erro não identificado"}"
            } finally { busy = false }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("GEIVIA • MANUTENÇÃO", color = Color(0xFF07528D), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text("Atualizações das ferramentas", color = Color(0xFF102B43), fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("Consulta versões instaladas e candidatas. Debian usa os repositórios configurados; Firebase CLI consulta a versão publicada no npm. Atualizações podem alterar dependências e baixar arquivos.", color = Color(0xFF587083), fontSize = 13.sp, lineHeight = 19.sp)
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF4FA)), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("VERIFICAÇÃO", color = Color(0xFF07528D), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text(report, color = Color(0xFF102B43), fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
        if (status.isNotBlank()) Text(status, color = if (status.startsWith("Falha")) Color(0xFFB42318) else Color(0xFF18794E), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Button(onClick = { runTask("Verificação de atualizações", GeiviaToolchainCommands.checkUpdates()) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Verificar atualizações") }
        Text("ATUALIZAÇÃO INDIVIDUAL", color = Color(0xFF07528D), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        val apt = listOf(
            "git" to "Git",
            "default-jdk-headless" to "Java (JDK)",
            "nodejs" to "Node.js",
            "npm" to "npm",
            "python3" to "Python 3",
            "cmake" to "CMake",
            "build-essential" to "Ferramentas de compilação"
        )
        apt.forEach { (pkg, label) ->
            OutlinedButton(onClick = { runTask("Atualizando $label", GeiviaToolchainCommands.updateDebianPackage(pkg)) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Atualizar $label") }
        }
        OutlinedButton(onClick = { runTask("Atualizando Firebase CLI", GeiviaToolchainCommands.updateFirebaseCli()) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Atualizar Firebase CLI") }
        Divider()
        Text("ATUALIZAR TUDO", color = Color(0xFF07528D), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text("Atualiza os pacotes Debian instalados e o Firebase CLI. Faça isso com bateria e internet disponíveis.", color = Color(0xFF587083), fontSize = 12.sp)
        Button(onClick = { runTask("Atualização completa", GeiviaToolchainCommands.updateAll()) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Atualizar todas as ferramentas") }
        if (output.isNotBlank()) {
            Text("SAÍDA TÉCNICA", color = Color(0xFF07528D), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Column(Modifier.fillMaxWidth().heightIn(max = 260.dp).background(Color(0xFF101D2B), RoundedCornerShape(10.dp)).verticalScroll(rememberScrollState()).padding(10.dp)) {
                Text(output, color = Color(0xFFE2E8F0), fontSize = 10.sp, lineHeight = 14.sp)
            }
        }
        OutlinedButton(onClick = onBack, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Voltar ao painel") }
    }
}
