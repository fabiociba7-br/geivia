package com.geivia.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun GeiviaEnvironmentSetupScreen(onCompleted: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("geivia_onboarding", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val controller = remember { LinuxRuntimeController(context) }
    val stages = remember { GeiviaToolchainCommands.installStages() }
    var busy by remember { mutableStateOf(false) }
    var debianReady by remember { mutableStateOf(prefs.getBoolean("linux_debian_verified", false)) }
    var complete by remember { mutableStateOf(prefs.getBoolean("linux_toolchain_verified", false)) }
    var showDetails by remember { mutableStateOf(false) }
    var currentStage by remember { mutableStateOf("") }
    var status by remember {
        mutableStateOf(
            when {
                prefs.getBoolean("linux_toolchain_verified", false) -> "Configuração concluída. Debian e ferramentas principais já foram verificados."
                prefs.getBoolean("linux_debian_verified", false) -> "Debian já está instalado. Pronto para continuar a configuração das ferramentas."
                else -> "Pronto para preparar o ambiente Linux integrado."
            }
        )
    }
    var details by remember { mutableStateOf("") }
    var stageStates by remember {
        mutableStateOf(stages.associate { stage ->
            stage.id to if (prefs.getBoolean("toolchain_stage_${stage.id}", false)) "Concluída" else "Aguardando"
        })
    }

    DisposableEffect(controller) { onDispose { controller.cancel() } }
    fun log(chunk: String) { details = (details + chunk).takeLast(16000) }

    fun runSetup() {
        if (busy || complete) return
        scope.launch {
            busy = true
            details = ""
            try {
                withContext(Dispatchers.IO) { controller.prepareEngine() }
                if (!debianReady) {
                    currentStage = "Debian ARM64"
                    status = "Verificando se o Debian já está instalado…"
                    val existingCheck = controller.execute(LinuxRuntimeCommands.testDebianBase(), 60_000L) { chunk -> log(chunk) }
                    if (existingCheck == 0) {
                        log("\n[GEIVIA] Debian existente validado; instalação ignorada.\n")
                    } else {
                        status = "Preparando o Debian ARM64…"
                        controller.installAndVerifyDebian { chunk -> log(chunk) }
                    }
                    check(prefs.edit().putBoolean("linux_debian_verified", true).commit()) {
                        "Debian foi verificado, mas não foi possível salvar o progresso."
                    }
                    debianReady = true
                }

                for (stage in stages) {
                    if (prefs.getBoolean("toolchain_stage_${stage.id}", false)) {
                        stageStates = stageStates + (stage.id to "Concluída")
                        continue
                    }
                    currentStage = stage.title
                    stageStates = stageStates + (stage.id to "Em andamento")
                    status = "Em andamento: ${stage.title}"
                    log("\n[GEIVIA] ETAPA: ${stage.title}\n")
                    val exitCode = controller.execute(stage.command, stage.timeoutMs) { chunk -> log(chunk) }
                    if (exitCode != 0) {
                        stageStates = stageStates + (stage.id to "Falhou")
                        status = "A etapa '${stage.title}' falhou (código $exitCode). As etapas concluídas foram preservadas."
                        error("Código de saída $exitCode")
                    }
                    check(prefs.edit().putBoolean("toolchain_stage_${stage.id}", true).commit()) {
                        "A etapa terminou, mas não foi possível salvar seu checkpoint."
                    }
                    stageStates = stageStates + (stage.id to "Concluída")
                    log("[GEIVIA] ETAPA CONCLUÍDA: ${stage.title}\n")
                }

                check(prefs.edit().putBoolean("linux_toolchain_verified", true).commit()) {
                    "As ferramentas foram verificadas, mas não foi possível salvar a conclusão."
                }
                complete = true
                currentStage = ""
                status = "Configuração concluída. Debian e ferramentas principais foram instalados e verificados."
            } catch (error: Exception) {
                if (status.startsWith("Em andamento:")) {
                    val failed = stages.firstOrNull { it.title == currentStage }
                    if (failed != null && stageStates[failed.id] == "Em andamento") {
                        stageStates = stageStates + (failed.id to "Falhou")
                    }
                }
                if (!status.contains("falhou")) {
                    status = "Não foi possível concluir: ${error.message ?: "erro não identificado"}. O progresso confirmado foi preservado."
                }
                log("\n[GEIVIA] ERRO: ${error.message ?: "erro não identificado"}\n")
            } finally {
                busy = false
            }
        }
    }

    val failedStage = stages.firstOrNull { stageStates[it.id] == "Falhou" }
    val nextStage = stages.firstOrNull { stageStates[it.id] != "Concluída" }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Text("PRIMEIRO PASSO • CONFIGURAÇÃO DO GEIVIA", color = Color(0xFF07528D), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        LinearProgressIndicator(
            progress = if (complete) 1f else {
                val done = stageStates.values.count { it == "Concluída" }
                (if (debianReady) 1f else 0f) * 0.15f + (done.toFloat() / stages.size) * 0.85f
            },
            modifier = Modifier.fillMaxWidth().height(5.dp)
        )
        Text("Vamos preparar tudo antes de perguntar sobre suas contas.", color = Color(0xFF102B43), fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
        Text(
            "O GEIVIA instala o Linux integrado e configura automaticamente as ferramentas básicas. Você não precisa abrir Termux nem digitar comandos.",
            color = Color(0xFF587083), fontSize = 14.sp, lineHeight = 21.sp
        )
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF4FA)), shape = RoundedCornerShape(15.dp)) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("O QUE SERÁ INSTALADO", color = Color(0xFF07528D), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("• Debian ARM64, Git, SSH e utilitários", color = Color(0xFF102B43), fontSize = 13.sp)
                Text("• Java e ferramentas de compilação", color = Color(0xFF102B43), fontSize = 13.sp)
                Text("• Node.js, npm e Firebase CLI", color = Color(0xFF102B43), fontSize = 13.sp)
                Text("• Python 3, pip e ambientes virtuais", color = Color(0xFF102B43), fontSize = 13.sp)
            }
        }
        if (debianReady) {
            Text("✓ DEBIAN VERIFICADO", color = Color(0xFF18794E), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        } else {
            Text("○ Debian ARM64 — Aguardando verificação", color = Color(0xFF587083), fontSize = 12.sp)
        }

        Text("ETAPAS DA INSTALAÇÃO", color = Color(0xFF07528D), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        stages.forEachIndexed { index, stage ->
            val stageStatus = stageStates[stage.id] ?: "Aguardando"
            val statusColor = when (stageStatus) {
                "Concluída" -> Color(0xFF18794E)
                "Em andamento" -> Color(0xFF07528D)
                "Falhou" -> Color(0xFFB3261E)
                else -> Color(0xFF587083)
            }
            Card(colors = CardDefaults.cardColors(containerColor = if (stageStatus == "Falhou") Color(0xFFFFF0EF) else Color(0xFFF5F8FA))) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("${index + 1}. ${stage.title}", color = Color(0xFF102B43), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        if (stage.id == stages.firstOrNull { it.title == currentStage }?.id && busy) {
                            Text("Executando agora…", color = Color(0xFF07528D), fontSize = 10.sp)
                        }
                    }
                    Text(stageStatus, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Text(status, color = if (complete) Color(0xFF18794E) else if (failedStage != null) Color(0xFFB3261E) else Color(0xFF173B5E), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(onClick = {
                controller.cancel()
                status = "Cancelamento solicitado. As etapas concluídas foram preservadas; a etapa interrompida poderá ser repetida."
            }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar configuração") }
        } else if (!complete) {
            Button(onClick = { runSetup() }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    when {
                        failedStage != null -> "Tentar novamente: ${failedStage.title}"
                        debianReady -> "Continuar configuração"
                        else -> "Configurar GEIVIA automaticamente"
                    },
                    modifier = Modifier.padding(vertical = 5.dp)
                )
            }
            if (failedStage != null || nextStage != null) {
                Text(
                    "Ao tentar novamente, o GEIVIA pula as etapas já concluídas e retoma pela primeira etapa pendente. Uma etapa parcialmente executada é repetida com segurança.",
                    color = Color(0xFF587083), fontSize = 11.sp, lineHeight = 16.sp
                )
            }
        } else {
            Button(onClick = onCompleted, modifier = Modifier.fillMaxWidth()) {
                Text("Continuar para configurar minhas contas", modifier = Modifier.padding(vertical = 5.dp))
            }
        }

        if (details.isNotBlank()) {
            OutlinedButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Diagnóstico GEIVIA", status + "\n\n" + details))
            }, modifier = Modifier.fillMaxWidth()) { Text("Copiar erro e detalhes técnicos") }
            TextButton(onClick = { showDetails = !showDetails }) {
                Text(if (showDetails) "Ocultar detalhes técnicos" else "Ver detalhes técnicos")
            }
            if (showDetails) {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 280.dp)
                        .background(Color(0xFF101D2B), RoundedCornerShape(12.dp))
                        .verticalScroll(rememberScrollState()).padding(11.dp)
                ) {
                    Text(details, color = Color(0xFFE2E8F0), fontSize = 10.sp, lineHeight = 15.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
        Text(
            "O progresso de cada etapa é salvo no aparelho. Se o GEIVIA for fechado ou a conexão cair, abra novamente esta tela para continuar.",
            color = Color(0xFF587083), fontSize = 11.sp, lineHeight = 16.sp
        )
    }
}
