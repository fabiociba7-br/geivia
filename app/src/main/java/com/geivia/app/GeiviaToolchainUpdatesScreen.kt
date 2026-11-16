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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ToolUpdateStatus(
    val packageName: String,
    val installedVersion: String,
    val availableVersion: String,
    val updateAvailable: Boolean
)

private fun parseToolUpdateReport(raw: String): Map<String, ToolUpdateStatus> =
    raw.lineSequence().mapNotNull { line ->
        if (!line.startsWith("GEIVIA_UPDATE|")) return@mapNotNull null
        val fields = line.split("|")
        if (fields.size != 5) return@mapNotNull null
        ToolUpdateStatus(fields[1], fields[2], fields[3], fields[4] == "1")
    }.associateBy { it.packageName }

@Composable
internal fun GeiviaToolchainUpdatesScreen(onBack: () -> Unit) {
    val controller = remember { LinuxRuntimeController(LocalContext.current) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf("Toque em “Buscar atualizações” para consultar as versões instaladas e disponíveis.") }
    var output by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var updates by remember { mutableStateOf<Map<String, ToolUpdateStatus>>(emptyMap()) }
    var searched by remember { mutableStateOf(false) }

    DisposableEffect(controller) { onDispose { controller.cancel() } }

    fun runTask(label: String, command: List<String>, invalidatesSearch: Boolean = false) {
        if (busy) return
        scope.launch {
            busy = true
            status = label
            output = ""
            try {
                val collected = StringBuilder()
                val code = withContext(Dispatchers.IO) {
                    controller.execute(command) { chunk -> synchronized(collected) { collected.append(chunk) } }
                }
                val log = synchronized(collected) { collected.toString() }
                output = log.takeLast(30000)
                check(code == 0) { label + " falhou (código " + code + ")." }
                status = label + " concluído."
                if (label.startsWith("Busca")) {
                    updates = parseToolUpdateReport(log)
                    searched = true
                    report = if (updates.isEmpty()) "A consulta terminou, mas não retornou versões estruturadas. Confira o log técnico."
                    else updates.values.joinToString("\n") { item ->
                        toolLabel(item.packageName) + ": " + item.installedVersion + " → " + item.availableVersion +
                            " • " + if (item.updateAvailable) "Atualização disponível" else "Atualizado"
                    }
                } else if (invalidatesSearch) {
                    updates = emptyMap()
                    searched = false
                    report = "Atualização concluída. Faça uma nova busca para conferir as versões."
                }
            } catch (e: Exception) {
                status = "Falha: " + (e.message ?: "erro não identificado")
            } finally { busy = false }
        }
    }

    val tools = listOf(
        "git" to "Git", "default-jdk-headless" to "Java (JDK)", "nodejs" to "Node.js",
        "npm" to "npm", "python3" to "Python 3", "cmake" to "CMake",
        "build-essential" to "Ferramentas de compilação", "firebase-tools" to "Firebase CLI"
    )

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("GEIVIA • MANUTENÇÃO", color = Color(0xFF07528D), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text("Atualizações das ferramentas", color = Color(0xFF102B43), fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("A busca compara a versão instalada com a versão candidata nos repositórios oficiais. Atualizações só ficam disponíveis quando uma versão mais nova é confirmada.", color = Color(0xFF587083), fontSize = 13.sp, lineHeight = 19.sp)
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF4FA)), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("RESULTADO DA BUSCA", color = Color(0xFF07528D), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text(report, color = Color(0xFF102B43), fontSize = 11.sp, lineHeight = 17.sp)
            }
        }
        if (status.isNotBlank()) Text(status, color = if (status.startsWith("Falha")) Color(0xFFB42318) else Color(0xFF18794E), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Button(onClick = { runTask("Busca de atualizações", GeiviaToolchainCommands.checkUpdates()) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Buscar atualizações") }
        Text("ATUALIZAÇÕES DISPONÍVEIS", color = Color(0xFF07528D), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        if (!searched) Text("Faça uma busca para habilitar somente as atualizações necessárias.", color = Color(0xFF587083), fontSize = 12.sp)
        else if (updates.values.none { it.updateAvailable }) Text("Todas as ferramentas verificadas estão atualizadas.", color = Color(0xFF18794E), fontSize = 12.sp)

        tools.forEach { pair ->
            val pkg = pair.first
            val label = pair.second
            val item = updates[pkg]
            val available = item?.updateAvailable == true
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(10.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, color = Color(0xFF102B43), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(if (!searched) "Aguardando busca" else if (available) "Nova versão" else "Atualizado",
                            color = if (available) Color(0xFF18794E) else Color(0xFF587083), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    if (item != null) Text("Instalada: " + item.installedVersion + "  •  Disponível: " + item.availableVersion, color = Color(0xFF587083), fontSize = 10.sp)
                    OutlinedButton(
                        onClick = {
                            val command = if (pkg == "firebase-tools") GeiviaToolchainCommands.updateFirebaseCli() else GeiviaToolchainCommands.updateDebianPackage(pkg)
                            runTask("Atualizando " + label, command, invalidatesSearch = true)
                        },
                        enabled = !busy && searched && available,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Atualizar " + label) }
                }
            }
        }
        Button(onClick = { runTask("Atualização completa", GeiviaToolchainCommands.updateAll(), invalidatesSearch = true) },
            enabled = !busy && searched && updates.values.any { it.updateAvailable }, modifier = Modifier.fillMaxWidth()) {
            Text("Atualizar somente ferramentas com versão nova")
        }
        if (output.isNotBlank()) {
            Text("LOG DA OPERAÇÃO", color = Color(0xFF07528D), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            CopyTerminalLogButton(output, modifier = Modifier.fillMaxWidth())
            Column(Modifier.fillMaxWidth().heightIn(max = 260.dp).background(Color(0xFF101D2B), RoundedCornerShape(10.dp)).verticalScroll(rememberScrollState()).padding(10.dp)) {
                Text(output, color = Color(0xFFE2E8F0), fontSize = 10.sp, lineHeight = 14.sp)
            }
        }
        OutlinedButton(onClick = onBack, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Voltar ao painel") }
    }
}

private fun toolLabel(packageName: String): String = when (packageName) {
    "default-jdk-headless" -> "Java (JDK)"
    "build-essential" -> "Ferramentas de compilação"
    "firebase-tools" -> "Firebase CLI"
    "nodejs" -> "Node.js"
    "python3" -> "Python 3"
    else -> packageName
}
