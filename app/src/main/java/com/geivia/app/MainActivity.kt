package com.geivia.app

import android.content.Context
import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Blue = Color(0xFF087DB5)
private val DeepBlue = Color(0xFF07528D)
private val Orange = Color(0xFFF29A21)
private val Ink = Color(0xFF102B43)
private val Muted = Color(0xFF587083)
private const val PREFS = "geivia_onboarding"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(247, 251, 253)
        window.navigationBarColor = android.graphics.Color.rgb(247, 251, 253)
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContent { GeiviaApp() }
    }
}

private data class Question(val id: String, val title: String, val help: String, val options: List<String>, val multi: Boolean = false)

@Composable
private fun GeiviaApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val answers = remember {
        mutableStateMapOf<String, String>().apply {
            prefs.all.forEach { (k, v) -> if (v is String) put(k, v) }
        }
    }
    var page by rememberSaveable { mutableStateOf("welcome") }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var costMode by rememberSaveable { mutableStateOf(prefs.getString("cost_mode", "") ?: "") }

    val questions = buildList {
        add(Question("platform", "Onde seu projeto vai funcionar?", "Você pode escolher mais de uma opção.", listOf("Aplicativo Android", "Site ou sistema web", "Android e web"), true))
        add(Question("purpose", "O que você quer construir?", "Escolha a finalidade mais próxima.", listOf("Sistema de gestão ou administrativo", "Loja ou catálogo de produtos", "Agendamento ou reservas", "Site institucional ou páginas públicas", "Comunidade, conteúdo ou área de membros", "Outro tipo de projeto")))
        add(Question("audience", "Quem vai usar o projeto?", "Isso ajuda a estimar a estrutura necessária.", listOf("Somente eu", "Minha equipe ou empresa", "Clientes e público em geral", "Muitas pessoas, em grande escala")))
        add(Question("login", "As pessoas precisarão entrar com uma conta?", "Você poderá configurar isso depois.", listOf("Não precisa de login", "E-mail e senha", "Conta Google", "Telefone", "Ainda não sei, recomende para mim")))
        add(Question("data", "Que tipo de informação o projeto vai guardar?", "Marque tudo o que fizer sentido.", listOf("Cadastros e informações comuns", "Pedidos, vendas ou movimentações", "Dados em tempo real", "Não vai guardar dados", "Ainda não sei"), true))
        add(Question("files", "Seu projeto terá arquivos?", "Fotos, documentos e vídeos podem exigir armazenamento específico.", listOf("Não terá arquivos", "Fotos e imagens", "PDFs e documentos", "Vídeos ou arquivos grandes", "Ainda não sei")))
        if (answers["platform"].orEmpty().let { it.contains("web") || it.contains("Site") || it.contains("Android e web") }) {
            add(Question("hosting", "O que você pretende publicar na internet?", "Selecione a opção mais próxima.", listOf("Site simples", "Painel administrativo", "Site e painel administrativo", "Páginas públicas para clientes", "Ainda não sei")))
        }
        add(Question("extras", "Seu projeto precisa de recursos especiais?", "Marque os recursos que pretende usar.", listOf("Notificações", "Integração com outros sistemas", "Processamentos automáticos no servidor", "Relatórios e tarefas programadas", "Nenhum desses por enquanto", "Ainda não sei"), true))
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = Blue, secondary = Orange, background = Color(0xFFF7FBFD), surface = Color.White, onSurface = Ink)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFFF7FBFD)) {
            when (page) {
                "welcome" -> WelcomeScreen { page = "questions"; index = 0 }
                "questions" -> {
                    val i = index.coerceIn(0, questions.lastIndex)
                    val q = questions[i]
                    QuestionScreen(q, i, questions.size, answers[q.id].orEmpty(),
                        onSelect = { value -> answers[q.id] = value; prefs.edit().putString(q.id, value).apply() },
                        onBack = { if (i == 0) page = "welcome" else index = i - 1 },
                        onNext = { if (i < questions.lastIndex) index = i + 1 else page = "cost" })
                }
                "cost" -> CostScreen(costMode,
                    onSelect = { costMode = it; prefs.edit().putString("cost_mode", it).apply() },
                    onBack = { page = "questions"; index = questions.lastIndex },
                    onContinue = { page = "summary" })
                "summary" -> SummaryScreen(answers.toMap(), costMode,
                    onBack = { page = "cost" }, onEdit = { page = "questions"; index = 0 }, onFinish = { page = "dashboard" })
                else -> DashboardScreen(answers.toMap(), costMode) {
                    answers.clear(); prefs.edit().clear().apply(); costMode = ""; index = 0; page = "welcome"
                }
            }
        }
    }
}

@Composable
private fun WelcomeScreen(onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.White, Color(0xFFF0F8FC), Color(0xFFE7F3F9)))).verticalScroll(rememberScrollState()).padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        BrandMark()
        Spacer(Modifier.height(18.dp))
        Text("GEIVIA", color = Color(0xFF07131F), fontSize = 40.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
        Text("FIREBASE DEPLOY & HOSTING AUTOMATION", color = DeepBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(34.dp))
        Text("Seus projetos começam aqui.", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text("Conte o que deseja criar. A GEIVIA vai organizar as etapas e explicar quais recursos você precisa, inclusive as opções sem custo.", color = Muted, fontSize = 15.sp, lineHeight = 23.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text("Começar", Modifier.padding(vertical = 7.dp), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(12.dp))
        Text("Você poderá revisar suas respostas antes de conectar sua conta Google.", color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun QuestionScreen(q: Question, index: Int, total: Int, selected: String, onSelect: (String) -> Unit, onBack: () -> Unit, onNext: () -> Unit) {
    val values = selected.split("|").filter { it.isNotBlank() }.toSet()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("GEIVIA", color = DeepBlue, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text((index + 1).toString() + " de " + total.toString(), color = Muted, fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(progress = (index + 1f) / total, modifier = Modifier.fillMaxWidth().height(6.dp), color = Blue, trackColor = Color(0xFFDCEAF1))
        Spacer(Modifier.height(30.dp))
        Text("VAMOS ENTENDER SEU PROJETO", color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(10.dp))
        Text(q.title, color = Ink, fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(q.help, color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(22.dp))
        q.options.forEach { option ->
            val checked = option in values
            Card(Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable {
                onSelect(if (q.multi) (if (checked) values - option else values + option).joinToString("|") else option)
            }, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = if (checked) Color(0xFFE8F4FA) else Color.White)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (q.multi) Checkbox(checked, onCheckedChange = { onSelect((if (it) values + option else values - option).joinToString("|")) })
                    else RadioButton(checked, onClick = { onSelect(option) })
                    Spacer(Modifier.width(8.dp))
                    Text(option, color = Ink, fontSize = 14.sp, fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Voltar") }
            Button(onClick = onNext, enabled = values.isNotEmpty(), modifier = Modifier.weight(1f)) { Text(if (index == total - 1) "Continuar" else "Próxima") }
        }
    }
}

@Composable
private fun CostScreen(selected: String, onSelect: (String) -> Unit, onBack: () -> Unit, onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) {
        Text("GEIVIA", color = DeepBlue, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        Spacer(Modifier.height(28.dp))
        Text("Como você quer controlar os custos?", color = Ink, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Essa escolha define os limites que a GEIVIA deverá respeitar ao configurar seu projeto.", color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
        Spacer(Modifier.height(22.dp))
        CostOption("zero", "Quero manter custo zero", "Use somente recursos que possam ser configurados sem ativar faturamento. Se algo exigir plano pago, a GEIVIA vai avisar e oferecer alternativas quando existirem.", selected == "zero", onSelect)
        Spacer(Modifier.height(12.dp))
        CostOption("paid_allowed", "Posso autorizar recursos pagos", "A GEIVIA deverá explicar o motivo, o serviço envolvido e os possíveis custos. Nada será ativado nem terá faturamento associado sem minha autorização explícita.", selected == "paid_allowed", onSelect)
        Spacer(Modifier.height(18.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF5E6)), shape = RoundedCornerShape(13.dp)) {
            Text("Escolher recursos pagos permitidos não ativa o plano Blaze nem associa uma forma de pagamento. Qualquer mudança de faturamento exigirá confirmação separada.", Modifier.padding(15.dp), color = Color(0xFF77501A), fontSize = 13.sp, lineHeight = 19.sp)
        }
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Voltar") }
            Button(onClick = onContinue, enabled = selected.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Ver análise") }
        }
    }
}

@Composable
private fun CostOption(id: String, title: String, description: String, selected: Boolean, onSelect: (String) -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onSelect(id) }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFFE8F4FA) else Color.White)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            RadioButton(selected, onClick = { onSelect(id) })
            Spacer(Modifier.width(8.dp))
            Column {
                Text(title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(description, color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun SummaryScreen(answers: Map<String, String>, costMode: String, onBack: () -> Unit, onEdit: () -> Unit, onFinish: () -> Unit) {
    val services = buildList {
        if (answers["login"]?.contains("Não precisa") == false) add("Firebase Authentication — método de acesso a avaliar")
        if (answers["data"].orEmpty().isNotBlank() && !answers["data"].orEmpty().contains("Não vai guardar")) add("Cloud Firestore — estrutura de dados a avaliar")
        if (answers["files"].orEmpty().let { it.isNotBlank() && !it.startsWith("Não terá") }) add("Armazenamento de arquivos — verificar limites e requisitos atuais")
        if (answers["platform"].orEmpty().let { it.contains("web") || it.contains("Site") || it.contains("Android e web") }) add("Firebase Hosting — verificar limites e requisitos atuais")
        if (answers["extras"].orEmpty().let { it.contains("Processamentos") || it.contains("programadas") }) add("Recursos de servidor — podem exigir plano com faturamento")
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) {
        Text("ANÁLISE INICIAL", color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(8.dp))
        Text("Seu projeto está mapeado.", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Pré-análise baseada nas suas respostas. A disponibilidade, os limites e eventuais exigências de faturamento precisam ser conferidos antes de configurar qualquer serviço.", color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
        Spacer(Modifier.height(18.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(15.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("RECURSOS A AVALIAR", color = DeepBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                if (services.isEmpty()) Text("A configuração ainda será definida com base nos detalhes do projeto.", color = Muted, fontSize = 13.sp)
                services.forEach { Text("• " + it, color = Ink, fontSize = 13.sp, lineHeight = 20.sp); Spacer(Modifier.height(7.dp)) }
            }
        }
        Spacer(Modifier.height(14.dp))
        Card(colors = CardDefaults.cardColors(containerColor = if (costMode == "zero") Color(0xFFEAF6EF) else Color(0xFFFFF5E6)), shape = RoundedCornerShape(15.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(if (costMode == "zero") "MODO SEM CUSTO" else "RECURSOS PAGOS SOMENTE COM AUTORIZAÇÃO", color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Text(if (costMode == "zero") "A GEIVIA deverá bloquear qualquer etapa que exija faturamento e explicar as limitações ou alternativas." else "A GEIVIA poderá apresentar recursos pagos, mas deverá pedir autorização separada antes de associar faturamento ou ativar qualquer serviço pago.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text("Concluir análise") }
        OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Revisar respostas") }
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Voltar à escolha de custos") }
    }
}

private data class FirebaseProjectItem(val id: String, val name: String, val number: String)

@Composable
private fun DashboardScreen(answers: Map<String, String>, costMode: String, onRestart: () -> Unit) {
    val activity = LocalContext.current as android.app.Activity
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var accessToken by remember { mutableStateOf<String?>(null) }
    var projects by remember { mutableStateOf<List<FirebaseProjectItem>>(emptyList()) }
    var status by remember { mutableStateOf("Sua conta Google ainda não foi conectada.") }
    var loading by remember { mutableStateOf(false) }
    var selectedProject by remember { mutableStateOf<FirebaseProjectItem?>(null) }
    val requestedScopes = remember { listOf(com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/firebase.readonly")) }
    val authLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        try {
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val authResult = com.google.android.gms.auth.api.identity.Identity.getAuthorizationClient(activity)
                    .getAuthorizationResultFromIntent(result.data)
                accessToken = authResult.accessToken
                status = "Acesso de leitura autorizado. Buscando seus projetos Firebase..."
                scope.launch { loadFirebaseProjects(authResult.accessToken, { projects = it; status = if (it.isEmpty()) "Nenhum projeto Firebase foi encontrado nesta conta." else "Projetos carregados. Selecione um para continuar." }, { status = it }, { loading = it }) }
            } else status = "A autorização foi cancelada. Nenhum projeto foi alterado."
        } catch (e: Exception) {
            status = "Não foi possível concluir a autorização: " + (e.message ?: "erro desconhecido")
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) {
        BrandMark()
        Spacer(Modifier.height(12.dp))
        Text("Análise concluída", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Suas respostas foram salvas neste aparelho.", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(14.dp))
        Text("Modo escolhido: " + if (costMode == "zero") "custo zero" else "recursos pagos somente com autorização", color = DeepBlue, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("Projeto: " + (answers["purpose"] ?: "não informado"), color = Ink, fontSize = 13.sp)
        Spacer(Modifier.height(22.dp))
        Text("CONECTAR AO FIREBASE", color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        Text("A GEIVIA solicitará somente permissão de leitura dos projetos Firebase nesta etapa. Nenhum projeto será criado ou modificado.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                loading = true
                status = "Aguardando autorização do Google..."
                val request = com.google.android.gms.auth.api.identity.AuthorizationRequest.builder()
                    .setRequestedScopes(requestedScopes).build()
                com.google.android.gms.auth.api.identity.Identity.getAuthorizationClient(activity)
                    .authorize(request)
                    .addOnSuccessListener { auth ->
                        if (auth.hasResolution()) {
                            loading = false
                            val pending = auth.pendingIntent
                            if (pending != null) authLauncher.launch(
                                androidx.activity.result.IntentSenderRequest.Builder(pending.intentSender).build()
                            ) else status = "O Google não retornou a etapa de autorização."
                        } else {
                            accessToken = auth.accessToken
                            scope.launch { loadFirebaseProjects(auth.accessToken, { projects = it; status = if (it.isEmpty()) "Nenhum projeto Firebase foi encontrado nesta conta." else "Projetos carregados. Selecione um para continuar." }, { status = it }, { loading = it }) }
                        }
                    }
                    .addOnFailureListener { error ->
                        loading = false
                        status = "Falha na autorização Google: " + (error.message ?: "verifique a configuração OAuth do GEIVIA.")
                    }
            },
            enabled = !loading && accessToken == null,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (loading) "Conectando..." else "Conectar conta Google") }
        if (loading) { Spacer(Modifier.height(10.dp)); LinearProgressIndicator(Modifier.fillMaxWidth()) }
        Spacer(Modifier.height(10.dp))
        Text(status, color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
        projects.forEach { project ->
            Card(Modifier.fillMaxWidth().padding(top = 9.dp).clickable { selectedProject = project }, colors = CardDefaults.cardColors(containerColor = if (selectedProject?.id == project.id) Color(0xFFE8F4FA) else Color.White), shape = RoundedCornerShape(13.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text(project.name, color = Ink, fontWeight = FontWeight.Bold)
                    Text(project.id + " • " + project.number, color = Muted, fontSize = 11.sp)
                }
            }
        }
        if (selectedProject != null) {
            Spacer(Modifier.height(10.dp))
            Text("Projeto selecionado: " + selectedProject!!.name, color = DeepBlue, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text("A próxima etapa será analisar as APIs e permissões deste projeto antes de qualquer alteração.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        }
        Spacer(Modifier.height(18.dp))
        OutlinedButton(onClick = onRestart, modifier = Modifier.fillMaxWidth()) { Text("Refazer questionário") }
        Text("A criação e a configuração de projetos ainda não são executadas nesta etapa.", color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}

private suspend fun loadFirebaseProjects(
    token: String?,
    onLoaded: (List<FirebaseProjectItem>) -> Unit,
    onError: (String) -> Unit,
    onLoading: (Boolean) -> Unit
) {
    onLoading(true)
    try {
        val items = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val connection = java.net.URL("https://firebase.googleapis.com/v1beta1/projects?pageSize=100").openConnection() as java.net.HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer " + token.orEmpty())
                connection.setRequestProperty("Accept", "application/json")
                connection.connectTimeout = 20000
                connection.readTimeout = 20000
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream.bufferedReader().use { it.readText() }
                if (code !in 200..299) throw IllegalStateException("HTTP " + code + ": " + org.json.JSONObject(body).optJSONObject("error")?.optString("message", "erro da API"))
                val results = org.json.JSONObject(body).optJSONArray("results") ?: org.json.JSONArray()
                buildList {
                    for (i in 0 until results.length()) {
                        val item = results.optJSONObject(i) ?: continue
                        add(FirebaseProjectItem(
                            item.optString("projectId"),
                            item.optString("displayName").ifBlank { item.optString("projectId") },
                            item.optString("projectNumber")
                        ))
                    }
                }
            } finally { connection.disconnect() }
        }
        onLoaded(items)
    } catch (e: Exception) {
        onError("Não foi possível listar projetos Firebase: " + (e.message ?: "erro de conexão"))
    } finally { onLoading(false) }
}

@Composable
private fun BrandMark() {
    Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.height(148.dp).fillMaxWidth(0.72f).background(Brush.linearGradient(listOf(Color(0xFF20B7D2), Blue, DeepBlue)), CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth(0.83f).height(118.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("☁", color = Blue, fontSize = 66.sp, lineHeight = 66.sp)
                    Text("◆", color = Orange, fontSize = 23.sp, lineHeight = 23.sp)
                }
            }
        }
        Text("↗", color = Orange, fontSize = 58.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 38.dp, bottom = 8.dp))
    }
}
