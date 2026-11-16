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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun GeiviaEnvironmentSetupScreen(onCompleted: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { LinuxRuntimeController(context) }
    var busy by remember { mutableStateOf(false) }
    var complete by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Pronto para preparar o ambiente Linux integrado.") }
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
            progress = { if (complete) 1f else if (busy) 0.55f else 0.08f },
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
                Text("• Java 17 e ferramentas de compilação", color = Color(0xFF102B43), fontSize = 13.sp)
                Text("• Node.js, npm e Firebase CLI", color = Color(0xFF102B43), fontSize = 13.sp)
                Text("• Python 3, pip e ambientes virtuais", color = Color(0xFF102B43), fontSize = 13.sp)
            }
        }
        Text(
            "A instalação pode baixar algumas centenas de MB e levar vários minutos. Mantenha o aparelho conectado à internet, com espaço livre e o GEIVIA aberto. Os pacotes ficam no espaço privado do aplicativo, não no Android.",
            color = Color(0xFF587083), fontSize = 12.sp, lineHeight = 18.sp
        )
        Text(status, color = if (complete) Color(0xFF18794E) else Color(0xFF173B5E), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(
                onClick = { controller.cancel(); status = "Cancelamento solicitado. Você poderá tentar novamente." },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Cancelar configuração") }
        } else if (!complete) {
            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        status = "Verificando o aparelho e preparando o Debian…"
                        details = ""
                        try {
                            withContext(Dispatchers.IO) { controller.prepareEngine() }
                            controller.installAndVerifyDebian { chunk -> scope.launch { log(chunk) } }
                            status = "Debian pronto. Instalando Git, Java, Node.js, Python e Firebase CLI…"
                            val exitCode = controller.execute(GeiviaToolchainCommands.installDefaultProfile()) { chunk ->
                                scope.launch { log(chunk) }
                            }
                            check(exitCode == 0) { "A instalação das ferramentas terminou com código $exitCode." }
                            val verifyCode = controller.execute(GeiviaToolchainCommands.verifyDefaultProfile()) { chunk ->
                                scope.launch { log(chunk) }
                            }
                            check(verifyCode == 0) { "A verificação final das ferramentas falhou (código $verifyCode)." }
                            complete = true
                            status = "Configuração concluída. Debian e ferramentas principais foram instalados e verificados."
                        } catch (error: Exception) {
                            status = "Não foi possível concluir: ${error.message ?: "erro não identificado"}. Nenhuma repetição automática será iniciada."
                        } finally {
                            busy = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Configurar GEIVIA automaticamente", modifier = Modifier.padding(vertical = 5.dp)) }
        } else {
            Button(onClick = onCompleted, modifier = Modifier.fillMaxWidth()) {
                Text("Continuar para configurar minhas contas", modifier = Modifier.padding(vertical = 5.dp))
            }
        }
        if (details.isNotBlank()) {
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
