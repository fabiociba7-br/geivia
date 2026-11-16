package com.geivia.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets

private val DiagnosticInk = Color(0xFF102B43)
private val DiagnosticMuted = Color(0xFF587083)
private val DiagnosticBlue = Color(0xFF07528D)
private val DiagnosticGreen = Color(0xFF236342)
private val DiagnosticAmber = Color(0xFF9A641B)
private val DiagnosticRed = Color(0xFF9B3434)

@Composable
fun GeiviaDiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember(context) { LinuxRuntimeController(context.applicationContext) }
    var report by remember { mutableStateOf<GeiviaDiagnosticReport?>(null) }
    var running by remember { mutableStateOf(false) }
    var pendingExportText by remember { mutableStateOf<String?>(null) }
    val exportReportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val textToSave = pendingExportText
        if (uri != null && textToSave != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        val output = context.contentResolver.openOutputStream(uri)
                            ?: error("Não foi possível abrir o arquivo escolhido.")
                        output.use { it.write(textToSave.toByteArray(StandardCharsets.UTF_8)) }
                    }.isSuccess
                }
                Toast.makeText(
                    context,
                    if (saved) "Relatório salvo. Agora você pode acessá-lo pelo Termux." else "Não foi possível salvar o relatório.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        pendingExportText = null
    }

    fun runDiagnostics() {
        if (running) return
        running = true
        scope.launch {
            val liveChecks = withContext(Dispatchers.IO) {
                runCatching {
                    controller.prepareEngine()
                    val debianCode = controller.execute(
                        LinuxRuntimeCommands.testDebianBase(),
                        timeoutMs = 60_000L
                    ) { /* Deliberately discard raw terminal output. */ }
                    val toolchainCode = if (debianCode == 0) {
                        controller.execute(
                            GeiviaToolchainCommands.verifyDefaultProfile(),
                            timeoutMs = 90_000L
                        ) { /* Never retain or export command output. */ }
                    } else {
                        -1
                    }
                    Pair(debianCode == 0, toolchainCode == 0)
                }.getOrElse { false to false }
            }
            report = collectGeiviaDiagnostics(context, liveChecks.first, liveChecks.second)
            running = false
        }
    }

    LaunchedEffect(Unit) { runDiagnostics() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("GEIVIA", color = DiagnosticBlue, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        Text("Diagnóstico e solução de problemas", color = DiagnosticInk, fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
        Text(
            "O GEIVIA testa o aparelho, inicia o Debian e verifica os comandos essenciais. É uma verificação somente leitura: não instala, remove nem atualiza pacotes e não altera projetos na nuvem.",
            color = DiagnosticMuted, fontSize = 13.sp, lineHeight = 19.sp
        )

        if (running) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F7FA)), shape = RoundedCornerShape(14.dp)) {
                Text("Executando testes reais do ambiente Linux. Isso pode levar até alguns minutos…", modifier = Modifier.padding(15.dp), color = DiagnosticBlue, fontSize = 13.sp)
            }
        }

        report?.let { current ->
            val (headline, color) = when (current.overallStatus) {
                GeiviaDiagnosticStatus.OK -> "Verificações aprovadas" to DiagnosticGreen
                GeiviaDiagnosticStatus.ATTENTION -> "Alguns itens precisam de atenção" to DiagnosticAmber
                GeiviaDiagnosticStatus.BLOCKED -> "Há requisitos que impedem o funcionamento" to DiagnosticRed
                GeiviaDiagnosticStatus.NOT_CHECKED -> "Diagnóstico incompleto" to DiagnosticMuted
            }
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F7FA)), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(15.dp)) {
                    Text(headline, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "${current.findings.size} verificações • ${current.blockedCount} bloqueios • ${current.attentionCount} alertas",
                        color = DiagnosticMuted, fontSize = 12.sp
                    )
                    Text("Última verificação: " + android.text.format.DateFormat.format("dd/MM/yyyy HH:mm", current.checkedAtEpochMillis), color = DiagnosticMuted, fontSize = 11.sp)
                }
            }

            current.findings.forEach { finding ->
                val statusColor = when (finding.status) {
                    GeiviaDiagnosticStatus.OK -> DiagnosticGreen
                    GeiviaDiagnosticStatus.ATTENTION -> DiagnosticAmber
                    GeiviaDiagnosticStatus.BLOCKED -> DiagnosticRed
                    GeiviaDiagnosticStatus.NOT_CHECKED -> DiagnosticMuted
                }
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(13.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(diagnosticAreaLabel(finding.area), color = DiagnosticBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(diagnosticStatusLabel(finding.status), color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(finding.userMessage, color = DiagnosticInk, fontSize = 13.sp, lineHeight = 19.sp)
                        if (finding.recommendedAction != GeiviaDiagnosticAction.NONE) {
                            Spacer(Modifier.height(6.dp))
                            Text("Próxima ação: " + diagnosticActionLabel(finding.recommendedAction), color = DiagnosticAmber, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Relatório GEIVIA", current.toSafeText()))
                    Toast.makeText(context, "Relatório sanitizado copiado.", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Copiar relatório técnico seguro") }
            OutlinedButton(
                onClick = {
                    pendingExportText = current.toSafeText()
                    exportReportLauncher.launch("GEIVIA-diagnostico.txt")
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Salvar relatório para compartilhar com o Termux") }
        }

        GeiviaRecoveryTerminal(controller)

        Button(onClick = { runDiagnostics() }, enabled = !running, modifier = Modifier.fillMaxWidth()) {
            Text(if (running) "Verificando…" else "Executar diagnóstico novamente")
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Voltar ao painel") }
        Text(
            "O arquivo exportado contém somente o relatório sanitizado: não inclui senhas, tokens, chaves privadas, caminhos internos ou saída bruta do terminal. Os testes não instalam, removem nem atualizam pacotes.",
            color = DiagnosticMuted, fontSize = 10.sp, lineHeight = 15.sp
        )
    }
}

private fun collectGeiviaDiagnostics(
    context: Context,
    debianWorks: Boolean,
    toolchainWorks: Boolean
): GeiviaDiagnosticReport {
    val stats = runCatching { StatFs(context.filesDir.absolutePath) }.getOrNull()
    val availableBytes = stats?.availableBytes ?: 0L
    val nativeDir = runCatching { context.applicationInfo.nativeLibraryDir }.getOrNull()
    val nativeFiles = nativeDir?.let { File(it).listFiles()?.mapNotNull { file -> file.name }?.toSet() } ?: emptySet()
    val nativeLibraries = LinuxRuntimePreflight.REQUIRED_NATIVE_LIBRARIES.filter { it in nativeFiles }.toSet()

    val runtimeReport = GeiviaDiagnosticEngine.evaluateRuntime(
        androidApi = Build.VERSION.SDK_INT,
        supportedAbis = Build.SUPPORTED_ABIS.toList(),
        availableBytes = availableBytes,
        checkedAtEpochMillis = System.currentTimeMillis(),
        availableNativeLibraries = nativeLibraries
    )

    val findings = runtimeReport.findings.toMutableList()
    findings += GeiviaDiagnosticFinding(
        code = GeiviaDiagnosticCode.DEBIAN_NOT_VERIFIED,
        area = GeiviaDiagnosticArea.DEBIAN,
        status = if (debianWorks) GeiviaDiagnosticStatus.OK else GeiviaDiagnosticStatus.ATTENTION,
        severity = if (debianWorks) GeiviaDiagnosticSeverity.INFO else GeiviaDiagnosticSeverity.WARNING,
        userMessage = if (debianWorks) "O Debian iniciou e executou o teste de shell e arquivos essenciais." else "O GEIVIA não conseguiu confirmar a execução do Debian. Retome a configuração do ambiente; seus arquivos não foram apagados.",
        recommendedAction = if (debianWorks) GeiviaDiagnosticAction.NONE else GeiviaDiagnosticAction.RUN_ENVIRONMENT_SETUP
    )
    findings += GeiviaDiagnosticFinding(
        code = GeiviaDiagnosticCode.TOOLCHAIN_NOT_VERIFIED,
        area = GeiviaDiagnosticArea.TOOLCHAIN,
        status = if (toolchainWorks) GeiviaDiagnosticStatus.OK else GeiviaDiagnosticStatus.ATTENTION,
        severity = if (toolchainWorks) GeiviaDiagnosticSeverity.INFO else GeiviaDiagnosticSeverity.WARNING,
        userMessage = if (toolchainWorks) "Git, Java, Node.js, npm, Python e Firebase CLI responderam aos testes de versão." else if (debianWorks) "O Debian está funcionando, mas pelo menos uma ferramenta essencial não respondeu. Retome a instalação/verificação das ferramentas." else "As ferramentas não puderam ser verificadas porque o Debian não iniciou.",
        recommendedAction = if (toolchainWorks) GeiviaDiagnosticAction.NONE else GeiviaDiagnosticAction.RUN_ENVIRONMENT_SETUP
    )

    return GeiviaDiagnosticReport(runtimeReport.checkedAtEpochMillis, findings)
}

private fun diagnosticAreaLabel(area: GeiviaDiagnosticArea): String = when (area) {
    GeiviaDiagnosticArea.DEVICE -> "APARELHO"
    GeiviaDiagnosticArea.STORAGE -> "ARMAZENAMENTO"
    GeiviaDiagnosticArea.LINUX_RUNTIME -> "MOTOR LINUX"
    GeiviaDiagnosticArea.DEBIAN -> "DEBIAN"
    GeiviaDiagnosticArea.TOOLCHAIN -> "FERRAMENTAS"
    GeiviaDiagnosticArea.CLOUD_INTEGRATION -> "INTEGRAÇÕES"
}

private fun diagnosticStatusLabel(status: GeiviaDiagnosticStatus): String = when (status) {
    GeiviaDiagnosticStatus.OK -> "APROVADO"
    GeiviaDiagnosticStatus.ATTENTION -> "ATENÇÃO"
    GeiviaDiagnosticStatus.BLOCKED -> "BLOQUEADO"
    GeiviaDiagnosticStatus.NOT_CHECKED -> "NÃO VERIFICADO"
}

private fun diagnosticActionLabel(action: GeiviaDiagnosticAction): String = when (action) {
    GeiviaDiagnosticAction.NONE -> "Nenhuma ação necessária."
    GeiviaDiagnosticAction.FREE_STORAGE -> "Libere espaço no aparelho e execute novamente."
    GeiviaDiagnosticAction.UPDATE_OR_REINSTALL_APP -> "Atualize o GEIVIA pela versão oficial."
    GeiviaDiagnosticAction.REVIEW_DEVICE_COMPATIBILITY -> "Confira a compatibilidade do aparelho."
    GeiviaDiagnosticAction.RUN_ENVIRONMENT_SETUP -> "Volte à configuração do ambiente Linux."
    GeiviaDiagnosticAction.RETRY_VERIFICATION -> "Execute a verificação novamente."
}
