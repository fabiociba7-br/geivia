package com.geivia.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.or.oo.pr.engine.ProotLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun GoogleCloudAccessScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { LinuxRuntimeController(context) }
    val prefs = remember { context.getSharedPreferences("geivia_onboarding", Context.MODE_PRIVATE) }
    var busy by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(prefs.getBoolean("google_cloud_cli_ready", false)) }
    var session by remember { mutableStateOf<ProotLauncher.Session?>(null) }
    var input by rememberSaveable { mutableStateOf("") }
    var output by remember {
        mutableStateOf(
            "GEIVIA • conexão Google pelo terminal Linux integrado\n" +
            "Nenhum Cloud Shell ou Termux será aberto.\n" +
            "O GEIVIA prepara Debian e Google Cloud CLI dentro do próprio aplicativo.\n"
        )
    }
    val scroll = rememberScrollState()

    DisposableEffect(controller) {
        onDispose { controller.cancel() }
    }

    fun append(message: String) { output = (output + message).takeLast(24000) }

    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp)) {
        Text("ACESSO AO GOOGLE CLOUD", color = DeepBlue, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        Text("Tudo dentro do GEIVIA", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "O aplicativo prepara o Linux e instala as ferramentas automaticamente. Você não precisa abrir Shell, Termux ou digitar comandos de instalação.",
            color = Muted, fontSize = 14.sp, lineHeight = 21.sp
        )
        Spacer(Modifier.height(14.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF4FA)), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("O QUE SERÁ FEITO", color = DeepBlue, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Text("1. Verificar e preparar Debian ARM64", color = Ink, fontSize = 13.sp)
                Text("2. Instalar Google Cloud CLI dentro do Debian", color = Ink, fontSize = 13.sp)
                Text("3. Iniciar a autorização oficial da conta Google", color = Ink, fontSize = 13.sp)
                Text("4. Consultar os projetos disponíveis sem criar ou alterar nada", color = Ink, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = {
                scope.launch {
                    busy = true
                    append("\n[GEIVIA] Preparando o ambiente Linux… mantenha o aplicativo aberto.\n")
                    try {
                        withContext(Dispatchers.IO) { controller.prepareEngine() }
                        controller.installAndVerifyDebian { chunk -> scope.launch { append(chunk) } }
                        append("\n[GEIVIA] Instalando Google Cloud CLI. Esta etapa pode levar alguns minutos.\n")
                        val code = controller.execute(GoogleCloudCliCommands.install()) { chunk -> scope.launch { append(chunk) } }
                        check(code == 0) { "A instalação do Google Cloud CLI terminou com código $code." }
                        ready = true
                        prefs.edit().putBoolean("google_cloud_cli_ready", true).apply()
                        append("\n[GEIVIA] Google Cloud CLI instalado e verificado.\n")
                    } catch (error: Exception) {
                        append("\n[GEIVIA] Não foi possível concluir: ${error.message ?: "erro não identificado"}. A etapa foi interrompida; nenhuma tentativa ficará repetindo em segundo plano.\n")
                    } finally { busy = false }
                }
            },
            enabled = !busy && session == null,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (ready) "Verificar / reparar ambiente" else "Preparar Linux e ferramentas automaticamente") }
        if (busy) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(onClick = { controller.cancel(); append("\n[GEIVIA] Cancelamento solicitado.\n") }, modifier = Modifier.fillMaxWidth()) {
                Text("Cancelar operação")
            }
        }
        Spacer(Modifier.height(14.dp))
        if (ready) {
            Text("CONTA GOOGLE", color = DeepBlue, fontWeight = FontWeight.Black, fontSize = 11.sp)
            Text(
                "A autorização é feita pelo fluxo oficial do Google. O GEIVIA inicia o login no terminal integrado; o Google poderá pedir uma confirmação no navegador. Não informe senha ou código a ninguém.",
                color = Muted, fontSize = 12.sp, lineHeight = 18.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                val started = withContext(Dispatchers.IO) { controller.startDebianShell() }
                                session = started
                                append("\n[GEIVIA] Iniciando autorização Google…\n")
                                controller.send(started, "gcloud auth login --no-launch-browser\n")
                                scope.launch(Dispatchers.IO) {
                                    runCatching { controller.readSession(started) { chunk -> scope.launch { append(chunk) } } }
                                    withContext(Dispatchers.Main) { session = null; append("\n[GEIVIA] Sessão encerrada.\n") }
                                }
                            } catch (error: Exception) {
                                append("\n[GEIVIA] Falha ao iniciar login: ${error.message}\n")
                            }
                        }
                    },
                    enabled = session == null && !busy,
                    modifier = Modifier.weight(1f)
                ) { Text("Conectar conta Google") }
                OutlinedButton(
                    onClick = {
                        val current = session ?: return@OutlinedButton
                        controller.send(current, "gcloud projects list --format='table(projectId,name,projectNumber)'\n")
                    },
                    enabled = session != null,
                    modifier = Modifier.weight(1f)
                ) { Text("Listar projetos") }
            }
            if (session != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(1024) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Código de verificação, se solicitado") },
                    singleLine = true
                )
                Button(
                    onClick = {
                        val value = input.trim()
                        if (value.isNotEmpty()) {
                            controller.send(session!!, value + "\n")
                            input = ""
                        }
                    },
                    enabled = input.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Enviar confirmação") }
                OutlinedButton(
                    onClick = { controller.cancel(); session = null; append("\n[GEIVIA] Sessão encerrada pelo usuário.\n") },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Encerrar conexão") }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("SAÍDA DO PROCESSO", color = DeepBlue, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Column(
            Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 360.dp)
                .background(Color(0xFF101D2B), RoundedCornerShape(12.dp))
                .verticalScroll(rememberScrollState()).padding(11.dp)
        ) {
            Text(output, color = Color(0xFFE2E8F0), fontSize = 10.sp, lineHeight = 15.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Importante: o primeiro login do Google pode exigir abrir um endereço oficial e confirmar um código. Isso é uma proteção do Google. O GEIVIA não armazena sua senha. Nenhum projeto será criado ou modificado nesta etapa.",
            color = Muted, fontSize = 11.sp, lineHeight = 16.sp
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Voltar") }
    }
}
