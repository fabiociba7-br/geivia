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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun GeiviaEnvironmentSetupScreen(onCompleted: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("geivia_onboarding", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val controller = remember { LinuxRuntimeController(context) }
    var busy by remember { mutableStateOf(false) }
    // Persist each completed stage. Android may recreate the Activity while apt/npm
    // are running; without checkpoints the UI starts over and reinstalls Debian.
    var debianReady by remember { mutableStateOf(prefs.getBoolean("linux_debian_verified", false)) }
    var complete by remember { mutableStateOf(prefs.getBoolean("linux_toolchain_verified", false)) }
    var showDetails by remember { mutableStateOf(false) }
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

    DisposableEffect(controller) {
        onDispose { controller.cancel() }
    }

    fun log(chunk: String) {
        details = (details + chunk).takeLast(12000)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Text("PRIMEIRO PASSO • CONFIGURAÇÃO DO GEIVIA", color = Color(0xFF07528D), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        LinearProgressIndicator(
            progress = if (complete) 1f else if (busy) 0.72f else if (debianReady) 0.48f else 0.08f,
            modifier = Modifier.fillMaxWidth().height(5.dp)
        )
        Text("Vamos preparar tudo antes de perguntar sobre suas contas.", color = Color(0xFF102B43), fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
        Text(
            "O GEIVIA instala o Linux integrado e configura automaticamente as ferramentas básicas para desenvolvimento e publicação. Você não precisa abrir Termux, Shell nem digitar comandos.",
            color = Color(0xFF587083), fontSize = 14.sp, lineHeight = 21.sp
        )
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF4FA)), shape = RoundedCornerShape(15.dp)) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("O QUE SERÁ INSTALADO", color = Color(0xFF07528D), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("• Debian ARM64 dentro do GEIVIA", color = Color(0xFF102B43), fontSize = 13.sp)
                Text("• Git, SSH, curl, wget, zip, unzip e utilitários", color = Color(0xFF102B43), fontSize = 13.sp)
                Text("• Java (versão estável do Debian) e ferramentas de compilação", color = Color(0xFF102B43), fontSize = 13.sp)
                Text("• Node.js, npm e Firebase CLI", color = Color(0xFF102B43), fontSize = 13.sp)
                Text("• Python 3, pip e ambientes virtuais", color = Color(0xFF102B43), fontSize = 13.sp)
            }
        }
        Text(
            "A instalação pode baixar algumas centenas de MB e levar vários minutos. Mantenha o aparelho conectado à internet, com espaço livre e o GEIVIA aberto. Os pacotes ficam no espaço privado do aplicativo, não no Android.",
            color = Color(0xFF587083), fontSize = 12.sp, lineHeight = 18.sp
        )
        Text(status, color = if (complete) Color(0xFF18794E) else Color(0xFF173B5E), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        if (debianReady && !complete) {
            Text("DEBIAN VERIFICADO • esta etapa será preservada mesmo se o aplicativo reiniciar.", color = Color(0xFF18794E), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(
                onClick = { controller.cancel(); status = "Cancelamento solicitado. O progresso confirmado será preservado." },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Cancelar configuração") }
        } else if (!complete) {
            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        details = ""
                        try {
                            withContext(Dispatchers.IO) { controller.prepareEngine() }

                            if (!debianReady) {
                                // Recover safely if Android killed the app after Debian
                                // installation but before the checkpoint was committed.
                                status = "Verificando se o Debian já está instalado…"
                                val existingCheck = controller.execute(LinuxRuntimeCommands.testDebianBase(), 60_000L) { chunk ->
                                    scope.launch { log(chunk) }
                                }
                                if (existingCheck == 0) {
                                    debianReady = true
                                    check(prefs.edit().putBoolean("linux_debian_verified", true).commit()) {
                                        "Não foi possível salvar o progresso da instalação do Debian."
                                    }
                                    log("\\n[GEIVIA] Debian existente validado; instalação ignorada.\\n")
                                } else {
                                    status = "Preparando o Debian ARM64…"
                                    controller.installAndVerifyDebian { chunk -> scope.launch { log(chunk) } }
                                    debianReady = true
                                    check(prefs.edit().putBoolean("linux_debian_verified", true).commit()) {
                                        "Debian foi verificado, mas não foi possível salvar o progresso."
                                    }
                                }
                            }

                            status = "Debian preservado. Instalando ou retomando Git, Java, Node.js, Python e Firebase CLI…"
                            val exitCode = controller.execute(
                                GeiviaToolchainCommands.installDefaultProfile(),
                                45 * 60 * 1000L
                            ) { chunk -> scope.launch { log(chunk) } }
                            check(exitCode == 0) { "A instalação das ferramentas terminou com código $exitCode." }

                            val verifyCode = controller.execute(
                                GeiviaToolchainCommands.verifyDefaultProfile(),
                                3 * 60 * 1000L
                            ) { chunk -> scope.launch { log(chunk) } }
                            check(verifyCode == 0) { "A verificação final das ferramentas falhou (código $verifyCode)." }

                            check(prefs.edit().putBoolean("linux_toolchain_verified", true).commit()) {
                                "As ferramentas foram verificadas, mas não foi possível salvar a conclusão."
                            }
                            complete = true
                            status = "Configuração concluída. Debian e ferramentas principais foram instalados e verificados."
                        } catch (error: Exception) {
                            status = "Não foi possível concluir: ${error.message ?: "erro não identificado"}. O progresso confirmado foi preservado; toque novamente para retomar."
                        } finally {
                            busy = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (debianReady) "Continuar configuração das ferramentas" else "Configurar GEIVIA automaticamente", modifier = Modifier.padding(vertical = 5.dp)) }
        } else {
            Button(onClick = onCompleted, modifier = Modifier.fillMaxWidth()) {
                Text("Continuar para configurar minhas contas", modifier = Modifier.padding(vertical = 5.dp))
            }
        }
        if (details.isNotBlank()) {
            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Diagnóstico GEIVIA", status + "\\n\\n" + details))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Copiar erro e detalhes técnicos") }
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
            "Depois desta etapa, o GEIVIA perguntará se você já tem conta Google, projeto Cloud, Firebase ou repositório GitHub. Assim ele poderá reutilizar o que já existe.",
            color = Color(0xFF587083), fontSize = 12.sp, lineHeight = 18.sp
        )
    }
}
