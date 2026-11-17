package com.geivia.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class GarciaChatLine(val role: String, val content: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GarciaIntegrationScreen(context: Context, onBack: () -> Unit) {
    val prefs = remember { context.getSharedPreferences("geivia_onboarding", Context.MODE_PRIVATE) }
    val vault = remember { GarciaTokenVault(context) }
    val api = remember { GarciaApi(vault) }
    var connected by remember { mutableStateOf(vault.read() != null) }
    var registrationCode by remember { mutableStateOf("") }
    var pairingCode by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Verificando serviço Ciba AI…") }
    var error by remember { mutableStateOf<String?>(null) }
    val conversation = remember { mutableStateListOf<GarciaChatLine>() }
    val scroll = rememberScrollState()

    val projectName = prefs.getString("selected_project_name", "").orEmpty()
        .ifBlank { prefs.getString("selected_project_id", "").orEmpty() }
        .ifBlank { "Nenhum projeto selecionado" }
    val projectId = prefs.getString("selected_project_id", "").orEmpty()
    val schema = prefs.getString("geivia_studio_schema_v1", "[]").orEmpty()
    val platform = prefs.getString("platform", "").orEmpty()
    val projectContext = remember(projectName, projectId, schema, platform) {
        JSONObject()
            .put("project_name", projectName)
            .put("project_id", projectId)
            .put("target_platforms", platform)
            .put("visual_editor_schema", runCatching { JSONArray(schema) }.getOrElse { JSONArray() })
            .toString()
            .take(8_000)
    }

    LaunchedEffect(Unit) {
        status = withContext(Dispatchers.IO) {
            runCatching {
                val result = api.status()
                "Ciba AI: " + result.optString("status", "indisponível") +
                    if (result.optBoolean("ai_enabled")) " · IA ativa" else " · IA indisponível"
            }.getOrElse { "Não foi possível consultar o Ciba AI." }
        }
    }

    Scaffold(
        containerColor = Color(0xFFF3F6FA),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Garcia", fontWeight = FontWeight.Bold, color = Color(0xFF17354D), fontSize = 18.sp)
                        Text("Inteligência do GEIVIA · Ciba AI", color = Color(0xFF718397), fontSize = 11.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Voltar") }
                },
                actions = {
                    if (connected) {
                        TextButton(onClick = {
                            vault.clear()
                            connected = false
                            conversation.clear()
                            error = null
                        }) { Text("Desconectar") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(scroll).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF102B43)), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFFF29A21))
                        Spacer(Modifier.width(9.dp))
                        Text("GARCIA AI", color = Color.White, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                        Spacer(Modifier.weight(1f))
                        Surface(color = Color(0x2239C58A), shape = RoundedCornerShape(20.dp)) {
                            Text(if (connected) "CONECTADA" else "AGUARDANDO", color = Color(0xFF9BE4C2), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
                        }
                    }
                    Text("A inteligência que ajuda a planejar, construir e analisar seus aplicativos.", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(status, color = Color(0xFFB9C9D8), fontSize = 11.sp)
                }
            }

            if (!connected) {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Conectar à Garcia", color = Color(0xFF17354D), fontWeight = FontWeight.Bold)
                        Text("A conexão usa convites privados emitidos pelo Ciba AI. O código de registro e o código de pareamento são de uso único e não substituem a autorização do proprietário.", color = Color(0xFF718397), fontSize = 12.sp)
                        OutlinedTextField(
                            value = registrationCode,
                            onValueChange = { registrationCode = it.trim() },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Código de registro") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = pairingCode,
                            onValueChange = { pairingCode = it.trim() },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Código de pareamento") },
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                busy = true
                                error = null
                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                    try {
                                        withContext(Dispatchers.IO) { api.activate(registrationCode, pairingCode) }
                                        connected = true
                                        registrationCode = ""
                                        pairingCode = ""
                                        status = "GEIVIA pareado com a identidade privada da Garcia."
                                    } catch (e: Exception) {
                                        error = e.message ?: "Não foi possível conectar à Garcia."
                                    } finally { busy = false }
                                }
                            },
                            enabled = !busy && registrationCode.length >= 20 && pairingCode.length >= 20,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Link, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Conectar com segurança")
                        }
                    }
                }
            } else {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Conversa com Garcia", color = Color(0xFF17354D), fontWeight = FontWeight.Bold)
                        Text("Projeto em contexto: $projectName", color = Color(0xFF718397), fontSize = 11.sp)
                        if (conversation.isEmpty()) {
                            Text("Você pode pedir para a Garcia analisar a estrutura do projeto, sugerir telas, revisar erros ou planejar novas funcionalidades.", color = Color(0xFF718397), fontSize = 13.sp)
                        }
                        conversation.forEach { line ->
                            val user = line.role == "user"
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
                                Surface(
                                    color = if (user) Color(0xFFE8F2FC) else Color(0xFFF3F6FA),
                                    shape = RoundedCornerShape(13.dp)
                                ) {
                                    Text(line.content, Modifier.widthIn(max = 300.dp).padding(12.dp), color = Color(0xFF17354D), fontSize = 13.sp)
                                }
                            }
                        }
                        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        OutlinedTextField(
                            value = prompt,
                            onValueChange = { prompt = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Fale com a Garcia") },
                            placeholder = { Text("Ex.: analise meu projeto e sugira melhorias…") },
                            minLines = 2,
                            maxLines = 5
                        )
                        Button(
                            onClick = {
                                val question = prompt.trim()
                                if (question.isNotEmpty()) {
                                    prompt = ""
                                    conversation.add(GarciaChatLine("user", question))
                                    busy = true
                                    error = null
                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                        try {
                                            val answer = withContext(Dispatchers.IO) {
                                                val messages = JSONArray()
                                                messages.put(JSONObject().put("role", "user").put("content",
                                                    "Você está integrada ao GEIVIA Studio. Responda em português. " +
                                                    "O contexto abaixo é somente para análise; não execute nem afirme que alterou arquivos. " +
                                                    "Para qualquer alteração, apresente uma proposta clara para aprovação. Contexto do projeto: $projectContext"))
                                                conversation.takeLast(12).forEach { line ->
                                                    messages.put(JSONObject().put("role", line.role).put("content", line.content))
                                                }
                                                api.chat(messages)
                                            }
                                            conversation.add(GarciaChatLine("assistant", answer))
                                        } catch (e: Exception) {
                                            error = e.message ?: "Falha ao conversar com a Garcia."
                                        } finally { busy = false }
                                    }
                                }
                            },
                            enabled = !busy && prompt.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Send, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Enviar para Garcia")
                        }
                    }
                }
            }

            error?.let {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0EE))) {
                    Text(it, Modifier.padding(13.dp), color = Color(0xFF9B3028), fontSize = 12.sp)
                }
            }
            Text(
                "Segurança: o token do dispositivo é cifrado com AES-GCM usando uma chave protegida pelo Android Keystore. O contexto do projeto só é enviado quando você conversa com a Garcia. A IA sugere; o GEIVIA não aplica mudanças automaticamente.",
                color = Color(0xFF718397), fontSize = 10.sp, lineHeight = 15.sp
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}
