package com.geivia.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Environment
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

private val DiagnosticInk = Color(0xFF102B43)
private val DiagnosticMuted = Color(0xFF587083)
private val DiagnosticBlue = Color(0xFF07528D)
private val DiagnosticGreen = Color(0xFF236342)
private val DiagnosticAmber = Color(0xFF9A641B)
private val DiagnosticRed = Color(0xFF9B3434)

@Composable
fun GeiviaDiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var report by remember { mutableStateOf<GeiviaDiagnosticReport?>(null) }
    var running by remember { mutableStateOf(false) }

    fun runDiagnostics() {
        running = true
        report = collectGeiviaDiagnostics(context)
        running = false
    }

    LaunchedEffect(Unit) { runDiagnostics() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("GEIVIA", color = DiagnosticBlue, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        Text("Diagnóstico e solução de problemas", color = DiagnosticInk, fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
        Text(
            "Verifique o aparelho e o ambiente Linux. O diagnóstico é somente leitura: não apaga arquivos, não reinstala ferramentas e não altera projetos na nuvem.",
            color = DiagnosticMuted, fontSize = 13.sp, lineHeight = 19.sp
        )

        report?.let { current ->
            val (headline, color) = when (current.overallStatus) {
                GeiviaDiagnosticStatus.OK -> "Verificações básicas aprovadas" to DiagnosticGreen
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
                            Text(finding.area.name.replace('_', ' '), color = DiagnosticBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(finding.status.name, color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
        }

        Button(onClick = { runDiagnostics() }, enabled = !running, modifier = Modifier.fillMaxWidth()) {
            Text(if (running) "Verificando..." else "Executar diagnóstico novamente")
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Voltar ao painel") }
        Text(
            "O relatório não inclui senhas, tokens, chaves privadas, caminhos internos ou saída bruta do terminal. Quando uma correção exigir confirmação ou ação externa, o GEIVIA explicará isso antes de prosseguir.",
            color = DiagnosticMuted, fontSize = 10.sp, lineHeight = 15.sp
        )
    }
}

private fun collectGeiviaDiagnostics(context: Context): GeiviaDiagnosticReport {
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
    val prefs = context.getSharedPreferences("geivia_onboarding", Context.MODE_PRIVATE)
    val debianVerified = prefs.getBoolean("linux_debian_verified", false)
    val toolchainVerified = prefs.getBoolean("linux_toolchain_verified", false)

    findings += GeiviaDiagnosticFinding(
        code = GeiviaDiagnosticCode.DEBIAN_NOT_VERIFIED,
        area = GeiviaDiagnosticArea.DEBIAN,
        status = if (debianVerified) GeiviaDiagnosticStatus.OK else GeiviaDiagnosticStatus.ATTENTION,
        severity = if (debianVerified) GeiviaDiagnosticSeverity.INFO else GeiviaDiagnosticSeverity.WARNING,
        userMessage = if (debianVerified) "O ambiente Debian foi confirmado pela configuração inicial." else "O Debian ainda não foi confirmado neste aparelho. Conclua ou retome a configuração do ambiente.",
        recommendedAction = if (debianVerified) GeiviaDiagnosticAction.NONE else GeiviaDiagnosticAction.RUN_ENVIRONMENT_SETUP
    )
    findings += GeiviaDiagnosticFinding(
        code = GeiviaDiagnosticCode.TOOLCHAIN_NOT_VERIFIED,
        area = GeiviaDiagnosticArea.TOOLCHAIN,
        status = if (toolchainVerified) GeiviaDiagnosticStatus.OK else GeiviaDiagnosticStatus.ATTENTION,
        severity = if (toolchainVerified) GeiviaDiagnosticSeverity.INFO else GeiviaDiagnosticSeverity.WARNING,
        userMessage = if (toolchainVerified) "As ferramentas básicas foram confirmadas pela configuração inicial." else "A instalação das ferramentas ainda não foi confirmada. Retome a configuração para concluir e verificar os pacotes.",
        recommendedAction = if (toolchainVerified) GeiviaDiagnosticAction.NONE else GeiviaDiagnosticAction.RUN_ENVIRONMENT_SETUP
    )

    return GeiviaDiagnosticReport(
        checkedAtEpochMillis = runtimeReport.checkedAtEpochMillis,
        findings = findings
    )
}

private fun diagnosticActionLabel(action: GeiviaDiagnosticAction): String = when (action) {
    GeiviaDiagnosticAction.NONE -> "Nenhuma ação necessária."
    GeiviaDiagnosticAction.FREE_STORAGE -> "Libere espaço no aparelho e execute novamente."
    GeiviaDiagnosticAction.UPDATE_OR_REINSTALL_APP -> "Atualize o GEIVIA pela versão oficial."
    GeiviaDiagnosticAction.REVIEW_DEVICE_COMPATIBILITY -> "Confira a compatibilidade do aparelho."
    GeiviaDiagnosticAction.RUN_ENVIRONMENT_SETUP -> "Volte à configuração do ambiente Linux."
    GeiviaDiagnosticAction.RETRY_VERIFICATION -> "Execute a verificação novamente."
}
