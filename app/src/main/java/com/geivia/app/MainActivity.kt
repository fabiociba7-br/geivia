package com.geivia.app

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.net.Uri
import org.json.JSONArray
import android.widget.Toast
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import java.util.UUID

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
    val answerKeys = setOf("platform", "purpose", "audience", "login", "data", "files", "hosting", "extras")
    val answers = remember {
        mutableStateMapOf<String, String>().apply {
            answerKeys.forEach { key -> prefs.getString(key, null)?.let { put(key, it) } }
        }
    }
    val workspaceId = remember {
        prefs.getString("workspace_id", null) ?: ("GEIVIA-" + UUID.randomUUID().toString().replace("-", "").take(10).uppercase()).also {
            prefs.edit().putString("workspace_id", it).apply()
        }
    }
    var workspaceName by rememberSaveable { mutableStateOf(prefs.getString("workspace_name", "GEIVIA-" + workspaceId.removePrefix("GEIVIA-")) ?: ("GEIVIA-" + workspaceId.removePrefix("GEIVIA-"))) }
    val validWorkflowPages = setOf("workspace_setup", "cloud_setup", "questions", "cost", "summary", "dashboard", "google_access")
    val savedCheckpoint = remember {
        prefs.getString("workflow_checkpoint", null)?.takeIf { it in validWorkflowPages }
            ?: if (prefs.getBoolean("cloud_setup_completed", false)) "questions" else "workspace_setup"
    }
    var page by rememberSaveable { mutableStateOf("welcome") }
    var index by rememberSaveable {
        mutableIntStateOf(prefs.getInt("workflow_question_index", 0).coerceAtLeast(0))
    }
    var costMode by rememberSaveable { mutableStateOf(prefs.getString("cost_mode", "") ?: "") }

    // Central checkpoint: every screen transition is persisted, so force-closing
    // the app does not send the user back to the beginning of onboarding.
    LaunchedEffect(page, index) {
        if (page != "welcome") {
            prefs.edit()
                .putString("workflow_checkpoint", page)
                .putInt("workflow_question_index", index)
                .putLong("workflow_checkpoint_updated_at", System.currentTimeMillis())
                .apply()
        }
    }

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
                "welcome" -> WelcomeScreen(hasCheckpoint = prefs.contains("workflow_checkpoint")) {
                    page = savedCheckpoint
                    if (page == "questions") index = prefs.getInt("workflow_question_index", 0).coerceAtLeast(0)
                }
                "workspace_setup" -> WorkspaceSetupScreen(
                    initialName = workspaceName,
                    workspaceId = workspaceId,
                    onBack = { page = "welcome" },
                    onContinue = { name ->
                        workspaceName = name.trim()
                        prefs.edit().putString("workspace_name", workspaceName).apply()
                        page = "cloud_setup"
                    }
                )
                "cloud_setup" -> CloudShellSetupScreen(
                    workspaceName = workspaceName,
                    workspaceId = workspaceId,
                    answers = answers.toMap(),
                    costMode = costMode,
                    onBack = { page = "workspace_setup" },
                    onContinue = {
                        prefs.edit().putBoolean("cloud_setup_completed", true).apply()
                        page = "questions"
                    }
                )
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
                "google_access" -> CloudShellSetupScreen(
                    workspaceName = workspaceName,
                    workspaceId = workspaceId,
                    answers = answers.toMap(),
                    costMode = costMode,
                    onBack = { page = "dashboard" },
                    onContinue = { page = "dashboard" }
                )
                else -> DashboardScreen(answers.toMap(), costMode, onGoogleAccess = { page = "google_access" }) {
                    answers.clear()
                    val editor = prefs.edit()
                    answerKeys.forEach { editor.remove(it) }
                    editor.remove("cost_mode")
                        .remove("workflow_checkpoint")
                        .remove("workflow_question_index")
                        .remove("workflow_checkpoint_updated_at")
                        .putBoolean("cloud_setup_completed", false)
                        .apply()
                    costMode = ""
                    index = 0
                    page = "workspace_setup"
                }
            }
        }
    }
}


@Composable
private fun WelcomeScreen(hasCheckpoint: Boolean, onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.White, Color(0xFFF0F8FC), Color(0xFFE7F3F9)))).verticalScroll(rememberScrollState()).padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        BrandMark()
        Spacer(Modifier.height(18.dp))
        Text("GEIVIA", color = Color(0xFF07131F), fontSize = 40.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
        Text("PLANEJAMENTO DE PROJETOS • MODO INDEPENDENTE", color = DeepBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(34.dp))
        Text("Seus projetos começam aqui.", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text("Conte o que deseja criar. A GEIVIA vai organizar as etapas e explicar quais recursos você precisa, inclusive as opções sem custo.", color = Muted, fontSize = 15.sp, lineHeight = 23.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text(if (hasCheckpoint) "Continuar de onde parei" else "Começar", Modifier.padding(vertical = 7.dp), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(12.dp))
        Text("Seu ambiente GEIVIA é identificado neste aparelho. A conexão com o Google só acontece mediante configuração e autorização explícitas.", color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
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

private data class ApiRequirement(
    val api: String,
    val title: String,
    val reason: String,
    val required: Boolean = true,
    val paidRisk: Boolean = false
)

@Composable
private fun SummaryScreen(answers: Map<String, String>, costMode: String, onBack: () -> Unit, onEdit: () -> Unit, onFinish: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val selectedProjectId = prefs.getString("selected_project_id", null).orEmpty()
    val enabledApis = remember(selectedProjectId) {
        runCatching {
            val report = org.json.JSONObject(prefs.getString("cloud_project_diagnostic_json", null) ?: return@runCatching emptySet<String>())
            val reportId = report.optJSONObject("project")?.optString("projectId").orEmpty()
            if (reportId.isBlank() || reportId != selectedProjectId) return@runCatching emptySet<String>()
            val services = report.optJSONArray("enabledServices") ?: return@runCatching emptySet<String>()
            (0 until services.length()).mapNotNull { index ->
                val item = services.optJSONObject(index) ?: return@mapNotNull null
                item.optJSONObject("config")?.optString("name")?.takeIf { it.isNotBlank() }
                    ?: item.optString("name").takeIf { it.isNotBlank() }
            }.toSet()
        }.getOrDefault(emptySet())
    }
    val requirements = buildList {
        add(ApiRequirement("firebase.googleapis.com", "Firebase Management API", "Necessária para operações de gerenciamento e associação do Firebase ao projeto."))
        if (answers["login"].orEmpty().let { it.isNotBlank() && !it.contains("Não precisa") }) {
            add(ApiRequirement("identitytoolkit.googleapis.com", "Firebase Authentication", "Sua resposta indica que haverá autenticação de usuários."))
        }
        if (answers["data"].orEmpty().let { it.isNotBlank() && !it.contains("Não vai guardar") }) {
            add(ApiRequirement("firestore.googleapis.com", "Cloud Firestore", "Sua resposta indica armazenamento de cadastros, movimentações ou dados."))
        }
        if (answers["files"].orEmpty().let { it.isNotBlank() && !it.startsWith("Não terá") }) {
            add(ApiRequirement("firebasestorage.googleapis.com", "Cloud Storage for Firebase", "Sua resposta indica uso de fotos, documentos ou outros arquivos.", paidRisk = true))
        }
        if (answers["platform"].orEmpty().let { it.contains("web") || it.contains("Site") || it.contains("Android e web") }) {
            add(ApiRequirement("firebasehosting.googleapis.com", "Firebase Hosting", "Sua plataforma inclui uma experiência web ou publicação de site."))
        }
        if (answers["extras"].orEmpty().let { it.contains("Processamentos automáticos") || it.contains("programadas") }) {
            add(ApiRequirement("cloudbuild.googleapis.com", "Cloud Build", "Pode ser necessário em fluxos de implantação e processamento no servidor.", required = false, paidRisk = true))
        }
    }
    val services = requirements.map { requirement ->
        Triple(requirement, requirement.api in enabledApis, costMode == "zero" && requirement.paidRisk)
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
                if (services.isEmpty()) Text("Nenhuma API específica foi identificada pelas respostas.", color = Muted, fontSize = 13.sp)
                if (selectedProjectId.isBlank()) {
                    Text("Selecione um projeto Google Cloud para comparar esta lista com as APIs já habilitadas.", color = Color(0xFF9A5B13), fontSize = 12.sp)
                } else {
                    Text("Projeto analisado: $selectedProjectId", color = Muted, fontSize = 10.sp)
                }
                services.forEach { (requirement, active, blockedByCost) ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(requirement.title, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(requirement.api, color = Muted, fontSize = 10.sp)
                            }
                            Text(
                                when {
                                    selectedProjectId.isBlank() -> "Sem projeto"
                                    active -> "Ativa"
                                    blockedByCost -> "Bloqueada no custo zero"
                                    else -> "Pendente"
                                },
                                color = when {
                                    active -> Color(0xFF238653)
                                    blockedByCost -> Color(0xFF9A5B13)
                                    else -> Color(0xFF9A641B)
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(requirement.reason, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                        if (blockedByCost) Text("Não habilitar nesta configuração. Avaliar alternativa sem faturamento antes de qualquer mudança.", color = Color(0xFF9A5B13), fontSize = 10.sp)
                        if (!requirement.required) Text("Serviço opcional: confirmar necessidade antes de considerar habilitação.", color = Muted, fontSize = 10.sp)
                    }
                    Divider(color = Color(0xFFE7EEF2))
                }
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

@Composable
private fun DashboardScreen(answers: Map<String, String>, costMode: String, onGoogleAccess: () -> Unit, onRestart: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) {
        BrandMark()
        Spacer(Modifier.height(12.dp))
        Text("Análise concluída", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Suas respostas foram salvas somente neste aparelho.", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(14.dp))
        Text("Modo escolhido: " + if (costMode == "zero") "custo zero" else "recursos pagos somente com autorização", color = DeepBlue, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("Projeto: " + (answers["purpose"] ?: "não informado"), color = Ink, fontSize = 13.sp)
        val projectPrefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val chosenProjectId = projectPrefs.getString("selected_project_id", null).orEmpty()
        val chosenProjectName = projectPrefs.getString("selected_project_name", null).orEmpty()
        Spacer(Modifier.height(10.dp))
        Card(colors = CardDefaults.cardColors(containerColor = if (chosenProjectId.isBlank()) Color(0xFFFFF5E6) else Color(0xFFEAF6EF)), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(13.dp)) {
                Text(if (chosenProjectId.isBlank()) "PROJETO GOOGLE CLOUD NÃO SELECIONADO" else "PROJETO GOOGLE CLOUD DE DESTINO", color = if (chosenProjectId.isBlank()) Color(0xFF9A5B13) else Color(0xFF236342), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(if (chosenProjectId.isBlank()) "Importe a lista e escolha um projeto existente antes de qualquer configuração na nuvem." else chosenProjectName.ifBlank { chosenProjectId }, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                if (chosenProjectId.isNotBlank()) Text("Project ID: $chosenProjectId", color = Muted, fontSize = 12.sp)
            }
        }
        val planItems = buildList {
            add("Conferir o projeto Google Cloud selecionado")
            add("Importar um diagnóstico atualizado do mesmo Project ID")
            if (answers["login"].orEmpty().let { it.isNotBlank() && !it.contains("Não precisa") }) add("Avaliar Firebase Authentication")
            if (answers["data"].orEmpty().let { it.isNotBlank() && !it.contains("Não vai guardar") }) add("Avaliar Cloud Firestore")
            if (answers["files"].orEmpty().let { it.isNotBlank() && !it.startsWith("Não terá") }) add("Avaliar Cloud Storage para arquivos")
            if (answers["platform"].orEmpty().let { it.contains("web") || it.contains("Site") || it.contains("Android e web") }) add("Avaliar Firebase Hosting")
            if (answers["extras"].orEmpty().let { it.contains("Processamentos automáticos") || it.contains("programadas") }) add("Verificar necessidade de Cloud Build e processamento no servidor")
            add("Revisar limites gratuitos e possíveis custos de cada serviço")
            add("Autorizar individualmente cada alteração antes de executá-la")
        }
        val diagnosticMatches = runCatching {
            val report = org.json.JSONObject(projectPrefs.getString("cloud_project_diagnostic_json", null) ?: return@runCatching false)
            report.optJSONObject("project")?.optString("projectId") == chosenProjectId && chosenProjectId.isNotBlank()
        }.getOrDefault(false)
        val workflowStore = remember(projectPrefs) { WorkflowProgressStore(projectPrefs) }
        val workflowCatalog = listOf(
            "project_diagnostic" to "Diagnóstico do projeto",
            "api_firebase_googleapis_com" to "Firebase Management API",
            "api_firestore_googleapis_com" to "Cloud Firestore API",
            "api_firebaserules_googleapis_com" to "Firebase Rules API",
            "api_identitytoolkit_googleapis_com" to "Identity Toolkit API",
            "api_firebasestorage_googleapis_com" to "Cloud Storage for Firebase API",
            "api_firebasehosting_googleapis_com" to "Firebase Hosting API",
            "api_cloudbuild_googleapis_com" to "Cloud Build API",
            "firestore_database" to "Banco Firestore (default)",
            "firestore_rules" to "Regras de segurança Firestore",
            "authentication" to "Firebase Authentication",
            "hosting" to "Firebase Hosting",
            "hosting_deploy" to "Publicação do site",
            "storage" to "Cloud Storage"
        )
        val workflowRecords = workflowStore.allForProject(chosenProjectId).associateBy { it.stepId }
        Spacer(Modifier.height(16.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(15.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("ACOMPANHAMENTO DAS ETAPAS", color = DeepBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(5.dp))
                Text(if (chosenProjectId.isBlank()) "Selecione um projeto para iniciar o acompanhamento." else "Projeto: $chosenProjectId", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                workflowCatalog.forEach { (stepId, label) ->
                    val record = workflowRecords[stepId]
                    val state = record?.state ?: WorkflowStepState.NOT_STARTED
                    val stateLabel = if (stepId == "storage" && costMode == "zero") "Bloqueada no modo custo zero" else when (state) {
                        WorkflowStepState.NOT_STARTED -> "Não iniciada"
                        WorkflowStepState.IN_PROGRESS -> "Aguardando diagnóstico"
                        WorkflowStepState.WAITING_CONFIRMATION -> "Aguardando confirmação"
                        WorkflowStepState.UNKNOWN -> "Resultado desconhecido — reconciliar"
                        WorkflowStepState.COMPLETED -> "Concluída por diagnóstico"
                        WorkflowStepState.USER_CONFIRMED -> "Declarada concluída pelo usuário"
                        WorkflowStepState.FAILED -> "Falhou — revisar antes de tentar"
                    }
                    val stateColor = if (stepId == "storage" && costMode == "zero") Color(0xFFB05A18) else when (state) {
                        WorkflowStepState.COMPLETED, WorkflowStepState.USER_CONFIRMED -> Color(0xFF238653)
                        WorkflowStepState.UNKNOWN, WorkflowStepState.FAILED -> Color(0xFFB05A18)
                        WorkflowStepState.IN_PROGRESS, WorkflowStepState.WAITING_CONFIRMATION -> Color(0xFF9A641B)
                        WorkflowStepState.NOT_STARTED -> Muted
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(label, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(stateLabel, color = stateColor, fontSize = 10.sp)
                        }
                        if (record != null) Text(android.text.format.DateFormat.format("dd/MM HH:mm", record.updatedAt).toString(), color = Muted, fontSize = 9.sp)
                    }
                    if (record?.message?.isNotBlank() == true) Text(record.message, color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
                    Divider(color = Color(0xFFE7EEF2))
                }
                Text("As etapas são vinculadas ao Project ID. Uma operação pendente ou desconhecida não é repetida automaticamente.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(15.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("PLANO DE EXECUÇÃO", color = DeepBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(5.dp))
                Text("Etapas organizadas pelas respostas do questionário", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(when {
                    chosenProjectId.isBlank() -> "Primeiro selecione um projeto existente. Nenhum projeto será criado automaticamente."
                    diagnosticMatches -> "O diagnóstico importado corresponde ao projeto selecionado. Revise os serviços antes de qualquer mudança."
                    else -> "O projeto está selecionado, mas falta importar um diagnóstico atualizado vinculado a ele."
                }, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(10.dp))
                planItems.forEachIndexed { index, item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                        Text("${index + 1}.", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Text(item, color = Ink, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(if (costMode == "zero") "Modo custo zero: não habilitar recursos que possam exigir faturamento. Storage e processamento avançado ficam sujeitos a verificação específica; se houver exigência de plano pago, a etapa será interrompida." else "Recursos pagos continuam dependendo de análise de custo e autorização separada. A escolha deste modo não ativa faturamento.", color = if (costMode == "zero") Color(0xFF77501A) else Muted, fontSize = 11.sp, lineHeight = 17.sp)
                Spacer(Modifier.height(8.dp))
                Text("As alterações na nuvem são assistidas: o GEIVIA prepara comandos específicos, registra a operação e exige execução manual no Cloud Shell. Diagnósticos novos confirmam o resultado; o app não executa comandos remotamente.", color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
            }
        }
        Spacer(Modifier.height(22.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF6EF)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(17.dp)) {
                Text("MODO INDEPENDENTE", color = Color(0xFF236342), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                Text("Sua conta Google fica sob seu controle.", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "O GEIVIA abre o Google Cloud no navegador e usa o Cloud Shell para executar comandos na sessão da sua própria conta. Senhas, tokens e chaves não são enviados ao aplicativo.",
                    color = Muted, fontSize = 13.sp, lineHeight = 19.sp
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("O QUE ESTA VERSÃO FAZ", color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(9.dp))
        listOf(
            "Mantém suas respostas e preferências localmente neste aparelho.",
            "Organiza os serviços que poderão ser necessários para o projeto.",
            "Respeita a escolha de custo zero ou de recursos pagos somente com autorização.",
            "Não recebe tokens nem executa comandos remotamente: você revisa e executa cada comando no Cloud Shell."
        ).forEach { item ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                Text("•", color = Blue, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(9.dp))
                Text(item, color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF5E6)), shape = RoundedCornerShape(14.dp)) {
            Text(
                "Importante: o diagnóstico inicial é executado por você no Cloud Shell. O GEIVIA não pede sua senha Google. Alterações em projetos serão apresentadas como comandos separados, com revisão e confirmação.",
                Modifier.padding(15.dp), color = Color(0xFF77501A), fontSize = 13.sp, lineHeight = 19.sp
            )
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onGoogleAccess, modifier = Modifier.fillMaxWidth()) { Text("Acessar Google Cloud com minha conta") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { shareProjectPlan(context = context, answers = answers, costMode = costMode) }, modifier = Modifier.fillMaxWidth()) { Text("Compartilhar plano do projeto") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onRestart, modifier = Modifier.fillMaxWidth()) { Text("Refazer questionário") }
    }
}

@Composable
private fun WorkspaceSetupScreen(
    initialName: String,
    workspaceId: String,
    onBack: () -> Unit,
    onContinue: (String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("GEIVIA", color = DeepBlue, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        Spacer(Modifier.height(24.dp))
        Text("Vamos criar seu ambiente.", color = Ink, fontSize = 27.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Este nome identifica seu espaço de trabalho neste aparelho. Ele não é uma conta Google nem uma credencial de acesso.", color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Nome do ambiente") },
            placeholder = { Text("Ex.: Minha empresa, Projeto pessoal...") },
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF4FA)), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("IDENTIFICADOR EXCLUSIVO", color = DeepBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(5.dp))
                Text(workspaceId, color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("Gerado localmente e salvo neste aparelho.", color = Muted, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = { onContinue(name) }, enabled = name.trim().length >= 2, modifier = Modifier.fillMaxWidth()) {
            Text("Criar meu ambiente e continuar")
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Voltar") }
    }
}

private data class CloudProject(
    val projectId: String,
    val name: String,
    val projectNumber: String,
    val lifecycleState: String
)

@Composable
private fun CloudShellSetupScreen(
    workspaceName: String,
    workspaceId: String,
    answers: Map<String, String>,
    costMode: String,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    fun readSavedProjects(): List<CloudProject> {
        return runCatching {
            val array = JSONArray(prefs.getString("cloud_projects_json", "[]") ?: "[]")
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("projectId").trim()
                if (id.isBlank()) null else CloudProject(
                    projectId = id,
                    name = item.optString("name").ifBlank { id },
                    projectNumber = item.optString("projectNumber"),
                    lifecycleState = item.optString("lifecycleState")
                )
            }
        }.getOrDefault(emptyList())
    }
    var importedProjects by remember { mutableStateOf(readSavedProjects()) }
    var importStatus by remember { mutableStateOf("") }
    var pendingAbsentConfirmation by remember { mutableStateOf("") }
    var selectedProjectId by remember { mutableStateOf(prefs.getString("selected_project_id", null).orEmpty()) }
    var selectedProjectName by remember { mutableStateOf(prefs.getString("selected_project_name", null).orEmpty()) }
    var selectedProjectNumber by remember { mutableStateOf(prefs.getString("selected_project_number", null).orEmpty()) }
    var selectedApiForConfirmation by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showCancelPendingDialog by remember { mutableStateOf(false) }
    var pendingActionToCancel by remember { mutableStateOf("api") }
    var firestoreLocation by remember { mutableStateOf(prefs.getString("firestore_location", "").orEmpty()) }
    var newProjectId by rememberSaveable { mutableStateOf(prefs.getString("new_project_id", "").orEmpty()) }
    var newProjectName by rememberSaveable { mutableStateOf(prefs.getString("new_project_name", "").orEmpty()) }
    var showProjectCreateDialog by remember { mutableStateOf(false) }
    var projectCreationStatus by remember { mutableStateOf("") }
    var showFirestoreRulesDialog by remember { mutableStateOf(false) }
    var firestoreRulesStatus by remember { mutableStateOf("") }
    var showHostingDeployDialog by remember { mutableStateOf(false) }
    var hostingDeployStatus by remember { mutableStateOf("") }
    var firestoreDatabaseIds by remember {
        mutableStateOf(
            runCatching {
                val report = org.json.JSONObject(prefs.getString("cloud_project_diagnostic_json", null) ?: "")
                if (report.optJSONObject("project")?.optString("projectId") != selectedProjectId) null
                else report.optJSONArray("firestoreDatabases")?.let { array ->
                    (0 until array.length()).mapNotNull { index ->
                        array.optJSONObject(index)?.optString("name")?.substringAfterLast("/databases/")?.takeIf { it.isNotBlank() }
                    }
                }
            }.getOrNull()
        )
    }
    var firestoreBillingEnabled by remember {
        mutableStateOf(
            runCatching {
                val report = org.json.JSONObject(prefs.getString("cloud_project_diagnostic_json", null) ?: "")
                if (report.optJSONObject("project")?.optString("projectId") != selectedProjectId) null
                else report.optJSONObject("billing")?.let { billing ->
                    if (billing.has("billingEnabled") && !billing.isNull("billingEnabled")) billing.optBoolean("billingEnabled") else null
                }
            }.getOrNull()
        )
    }
    val workflowProgressStore = remember(prefs) { WorkflowProgressStore(prefs) }
    var showFirestoreCreateDialog by remember { mutableStateOf(false) }
    var firestoreActionStatus by remember { mutableStateOf("") }
    var apiActionStatus by remember { mutableStateOf("") }
    var actionHistory by remember {
        mutableStateOf(
            runCatching {
                val saved = JSONArray(prefs.getString("api_action_history", "[]") ?: "[]")
                (0 until saved.length()).mapNotNull { saved.optString(it).takeIf { value -> value.isNotBlank() } }.takeLast(30)
            }.getOrDefault(emptyList())
        )
    }
    fun addActionHistory(entry: String) {
        val updated = (actionHistory + entry).takeLast(30)
        actionHistory = updated
        val json = JSONArray()
        updated.forEach { json.put(it) }
        prefs.edit().putString("api_action_history", json.toString()).apply()
    }
    fun apiWorkflowStepId(api: String) = "api_" + api.replace(Regex("[^a-zA-Z0-9]+"), "_")

    val cloudShellCommand = """echo 'GEIVIA - diagnóstico inicial'
gcloud auth list --filter=status:ACTIVE --format='table(account,status)'
echo
echo 'Projetos disponíveis para esta conta:'
gcloud projects list --format=json > geivia-projects.json
echo
echo 'Baixando arquivo para o dispositivo:'
cloudshell download geivia-projects.json"""


    val pendingCreationId = prefs.getString("pending_project_creation_id", null).orEmpty()
    val projectCreationCommand = remember(newProjectId, newProjectName) {
        runCatching { GoogleCloudProjectCommands.createOrReuse(newProjectId.trim(), newProjectName.trim()) }.getOrDefault("")
    }
    val projectReconcileCommand = remember(pendingCreationId) {
        if (pendingCreationId.isBlank()) "" else runCatching {
            GoogleCloudProjectCommands.reconcile(pendingCreationId)
        }.getOrDefault("")
    }

    val firestoreRulesCommand = remember(selectedProjectId) {
        if (selectedProjectId.isBlank()) "" else runCatching {
            FirebaseDeploymentCommands.firestoreRulesBootstrap(selectedProjectId)
        }.getOrDefault("")
    }
    val hostingStarterCommand = remember(selectedProjectId) {
        if (selectedProjectId.isBlank()) "" else runCatching {
            FirebaseDeploymentCommands.hostingStarterDeploy(selectedProjectId)
        }.getOrDefault("")
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalStateException("Não foi possível abrir o arquivo selecionado.")
                val array = JSONArray(raw)
                val clean = JSONArray()
                val projects = mutableListOf<CloudProject>()
                var confirmedAbsentProjectId = ""
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("projectId").trim()
                    if (id.matches(Regex("^[a-z][a-z0-9-]{4,28}[a-z0-9]$"))) {
                        val name = item.optString("name").ifBlank { id }
                        val number = item.optString("projectNumber")
                        val state = item.optString("lifecycleState")
                        if (state == "NOT_FOUND_CONFIRMED") {
                            confirmedAbsentProjectId = id
                            continue
                        }
                        val safe = org.json.JSONObject().apply {
                            put("projectId", id)
                            put("name", name)
                            put("projectNumber", number)
                            put("lifecycleState", state)
                        }
                        clean.put(safe)
                        projects.add(CloudProject(id, name, number, state))
                    }
                }
                if (projects.isEmpty() && confirmedAbsentProjectId.isBlank()) {
                    throw IllegalStateException("O JSON não contém projetos válidos nem confirmação NOT_FOUND.")
                }
                prefs.edit().putString("cloud_projects_json", clean.toString()).apply()
                projects to confirmedAbsentProjectId
            }.onSuccess { result ->
                importedProjects = result.first
                val confirmedAbsentId = result.second
                val pendingId = prefs.getString("pending_project_creation_id", null).orEmpty()
                val activeProjectFound = pendingId.isNotBlank() &&
                    result.first.any { project -> project.projectId == pendingId && project.lifecycleState.equals("ACTIVE", ignoreCase = true) }
                when {
                    activeProjectFound -> {
                        val reconciled = runCatching {
                            val current = workflowProgressStore.get(pendingId, "project_creation")
                            if (current.state == WorkflowStepState.IN_PROGRESS || current.state == WorkflowStepState.UNKNOWN) {
                                workflowProgressStore.reconcileCompleted(pendingId, "project_creation", "Project ID localizado em inventário importado com estado ACTIVE: $pendingId.")
                            } else {
                                throw IllegalStateException("A operação não está aguardando reconciliação.")
                            }
                        }
                        if (reconciled.isSuccess) {
                            prefs.edit().remove("pending_project_creation_id").remove("pending_project_creation_name").apply()
                            importStatus = "Projeto $pendingId confirmado como ACTIVE. Operação reconciliada; selecione-o explicitamente na lista."
                        } else {
                            importStatus = reconciled.exceptionOrNull()?.message ?: "Não foi possível reconciliar a criação."
                        }
                    }
                    pendingId.isNotBlank() && confirmedAbsentId == pendingId -> {
                        // Imported JSON is user-supplied and is not cryptographically authenticated.
                        // Require an explicit user acknowledgement before releasing the creation lock.
                        pendingAbsentConfirmation = pendingId
                        importStatus = "O arquivo importado declara NOT_FOUND para $pendingId. Confirme que ele foi gerado agora pelo comando de reconciliação no Cloud Shell."
                    }
                    pendingId.isNotBlank() -> {
                        importStatus = "Inventário importado, mas a operação $pendingId continua pendente. Somente o mesmo ID pode ser reconciliado; não inicie outra criação."
                    }
                    else -> {
                        importStatus = "Importação concluída: ${result.first.size} projeto(s) lido(s). A seleção anterior foi preservada."
                    }
                }
            }.onFailure {
                importStatus = it.message ?: "Não foi possível importar o JSON."
            }
        }
    }

    val firebaseApiChecks = listOf(
        "firebase.googleapis.com" to "Firebase Management API",
        "firestore.googleapis.com" to "Cloud Firestore API",
        "firebaserules.googleapis.com" to "Firebase Rules API",
        "identitytoolkit.googleapis.com" to "Identity Toolkit API (não configura provedores)",
        "firebasestorage.googleapis.com" to "Cloud Storage for Firebase",
        "firebasehosting.googleapis.com" to "Firebase Hosting",
        "cloudbuild.googleapis.com" to "Cloud Build"
    )
    val requestedApis = buildList {
        add("firebase.googleapis.com" to "Firebase Management API")
        if (answers["login"].orEmpty().isNotBlank() && !answers["login"].orEmpty().contains("Não precisa")) {
            add("identitytoolkit.googleapis.com" to "Identity Toolkit API (pré-requisito técnico; não ativa login)")
        }
        if (answers["data"].orEmpty().isNotBlank() && !answers["data"].orEmpty().contains("Não vai guardar")) {
            add("firestore.googleapis.com" to "Cloud Firestore")
            add("firebaserules.googleapis.com" to "Firebase Rules API (regras de segurança)")
        }
        if (answers["files"].orEmpty().isNotBlank() && !answers["files"].orEmpty().startsWith("Não terá")) {
            add("firebasestorage.googleapis.com" to "Cloud Storage for Firebase")
        }
        if (answers["platform"].orEmpty().let { it.contains("web") || it.contains("Site") || it.contains("Android e web") }) {
            add("firebasehosting.googleapis.com" to "Firebase Hosting")
        }
        if (answers["extras"].orEmpty().let { it.contains("Processamentos automáticos") || it.contains("programadas") }) {
            add("cloudbuild.googleapis.com" to "Cloud Build")
        }
    }.distinctBy { it.first }
    var enabledFirebaseApis by remember {
        mutableStateOf(
            runCatching {
                val report = org.json.JSONObject(prefs.getString("cloud_project_diagnostic_json", null) ?: return@runCatching emptySet<String>())
                val id = report.optJSONObject("project")?.optString("projectId").orEmpty()
                if (id != selectedProjectId) return@runCatching emptySet<String>()
                val services = report.optJSONArray("enabledServices") ?: return@runCatching emptySet<String>()
                (0 until services.length()).mapNotNull { index ->
                    val item = services.optJSONObject(index) ?: return@mapNotNull null
                    item.optJSONObject("config")?.optString("name")?.takeIf { it.isNotBlank() }
                        ?: item.optString("name").takeIf { it.isNotBlank() }
                }.toSet()
            }.getOrDefault(emptySet())
        )
    }
    var diagnosticSummary by remember {
        mutableStateOf(
            runCatching {
                val report = org.json.JSONObject(prefs.getString("cloud_project_diagnostic_json", null) ?: return@runCatching "")
                val id = report.optJSONObject("project")?.optString("projectId").orEmpty()
                val count = report.optJSONArray("enabledServices")?.length() ?: 0
                if (id == selectedProjectId && id.isNotBlank()) "Último diagnóstico: $id • $count API(s) habilitada(s)." else ""
            }.getOrDefault("")
        )
    }
    val diagnosticCommand = remember(selectedProjectId) {
        if (selectedProjectId.isBlank() || !selectedProjectId.matches(Regex("^[a-z][a-z0-9-]{4,28}[a-z0-9]$"))) "" else listOf(
            "set -e",
            "PROJECT_ID='$selectedProjectId'",
            "echo \"Verificando o projeto exato: \$PROJECT_ID\"",
            "gcloud projects describe \"\$PROJECT_ID\" --format=json > geivia-project-details.json",
            "gcloud services list --enabled --project=\"\$PROJECT_ID\" --format=json > geivia-enabled-apis.json",
            "if jq -e 'any(.[]; ((.config.name // .name) == \"firestore.googleapis.com\"))' geivia-enabled-apis.json >/dev/null; then gcloud firestore databases list --project=\"\$PROJECT_ID\" --format=json > geivia-firestore-databases.json; else printf '[]\\n' > geivia-firestore-databases.json; fi",
            "if gcloud billing projects describe \"\$PROJECT_ID\" --format=json > geivia-billing.json 2>/dev/null; then :; else printf '{\"billingEnabled\":null}\\n' > geivia-billing.json; fi",
            "jq -n --slurpfile p geivia-project-details.json --slurpfile s geivia-enabled-apis.json --slurpfile d geivia-firestore-databases.json --slurpfile b geivia-billing.json '{project:\$p[0],enabledServices:\$s[0],firestoreDatabases:\$d[0],billing:\$b[0]}' > geivia-diagnostic.json",
            "cloudshell download geivia-diagnostic.json"
        ).joinToString("\n")
    }
    val firestoreCreateCommand = remember(selectedProjectId, firestoreLocation) {
        if (selectedProjectId.isBlank() || firestoreLocation.isBlank()) "" else """
            set -e
            PROJECT_ID='$selectedProjectId'
            LOCATION='$firestoreLocation'
            gcloud billing projects describe "§PROJECT_ID" --format=json | jq -e '.billingEnabled == false' >/dev/null
            gcloud services list --enabled --project="§PROJECT_ID" --format=json | jq -e 'any(.[]; ((.config.name // .name) == "firestore.googleapis.com"))' >/dev/null
            DATABASES=§(gcloud firestore databases list --project="§PROJECT_ID" --format=json)
            if printf '%s' "§DATABASES" | jq -e --arg project "§PROJECT_ID" 'any(.[]; (.name // "") == ("projects/" + §project + "/databases/(default)"))' >/dev/null; then
              echo "O banco (default) já existe. Nenhuma criação será feita."
            else
              gcloud firestore databases create --database="(default)" --location="§LOCATION" --edition=standard --type=firestore-native --delete-protection --project="§PROJECT_ID"
            fi
        """.trimIndent().replace('§', '$')
    }
    val diagnosticLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalStateException("Não foi possível abrir o relatório.")
                val report = org.json.JSONObject(raw)
                val project = report.optJSONObject("project") ?: throw IllegalStateException("Relatório inválido: dados do projeto ausentes.")
                val reportId = project.optString("projectId")
                if (reportId != selectedProjectId || selectedProjectId.isBlank()) throw IllegalStateException("O relatório é de outro projeto. Selecione o mesmo Project ID antes de importar.")
                val services = report.optJSONArray("enabledServices") ?: throw IllegalStateException("Relatório inválido: lista de APIs ausente.")
                val databases = report.optJSONArray("firestoreDatabases")
                val importedDatabaseIds = databases?.let { array -> (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.optString("name")?.substringAfterLast("/databases/")?.takeIf { it.isNotBlank() } } }
                firestoreDatabaseIds = importedDatabaseIds
                val billing = report.optJSONObject("billing")
                firestoreBillingEnabled = if (billing != null && billing.has("billingEnabled") && !billing.isNull("billingEnabled")) billing.optBoolean("billingEnabled") else null
                val summary = "Último diagnóstico: $reportId • ${services.length()} API(s) habilitada(s)."
                val enabledNames = (0 until services.length()).mapNotNull { index ->
                    val item = services.optJSONObject(index) ?: return@mapNotNull null
                    item.optJSONObject("config")?.optString("name")?.takeIf { it.isNotBlank() }
                        ?: item.optString("name").takeIf { it.isNotBlank() }
                }.toSet()
                enabledFirebaseApis = enabledNames
                val pendingFirestoreProject = prefs.getString("pending_firestore_project_id", null)
                val hasDefaultFirestore = importedDatabaseIds?.contains("(default)") == true
                if (pendingFirestoreProject == selectedProjectId && hasDefaultFirestore) {
                    runCatching {
                        val tracked = workflowProgressStore.get(selectedProjectId, "firestore_database")
                        if (tracked.state == WorkflowStepState.IN_PROGRESS || tracked.state == WorkflowStepState.UNKNOWN) {
                            workflowProgressStore.reconcileCompleted(
                                selectedProjectId,
                                "firestore_database",
                                "Diagnóstico importado em ${System.currentTimeMillis()}: banco (default) listado para o mesmo Project ID."
                            )
                        }
                    }
                    prefs.edit().remove("pending_firestore_project_id").remove("pending_firestore_location").remove("pending_firestore_created_at").apply()
                    firestoreActionStatus = "Banco (default) localizado no diagnóstico do projeto. Nenhuma nova criação será preparada."
                    addActionHistory("Firestore (default) localizado no diagnóstico • $selectedProjectId")
                } else if (pendingFirestoreProject == selectedProjectId && importedDatabaseIds != null) {
                    firestoreActionStatus = "O diagnóstico ainda não lista o banco (default). A pendência foi mantida; não repita a criação sem reconciliar o estado no Google Cloud."
                }
                val pendingApi = prefs.getString("pending_api_service", null)
                val pendingProject = prefs.getString("pending_api_project_id", null)
                val completedApi = if (pendingApi != null && pendingProject == selectedProjectId && pendingApi in enabledNames) pendingApi else null
                if (completedApi != null) {
                    val stepId = "api_" + completedApi.replace(Regex("[^a-zA-Z0-9]+"), "_")
                    runCatching {
                        val tracked = workflowProgressStore.get(selectedProjectId, stepId)
                        if (tracked.state == WorkflowStepState.IN_PROGRESS || tracked.state == WorkflowStepState.UNKNOWN) {
                            workflowProgressStore.reconcileCompleted(
                                selectedProjectId,
                                stepId,
                                "Diagnóstico importado em ${System.currentTimeMillis()}: $completedApi consta entre os serviços habilitados para $selectedProjectId."
                            )
                        }
                    }
                }
                val editor = prefs.edit()
                    .putString("cloud_project_diagnostic_json", report.toString())
                    .putString("cloud_project_diagnostic_summary", summary)
                if (completedApi != null) {
                    editor.putString("last_completed_api_service", completedApi)
                        .putString("last_completed_api_project_id", selectedProjectId)
                        .remove("pending_api_service")
                        .remove("pending_api_project_id")
                        .remove("pending_api_created_at")
                    apiActionStatus = "Confirmado pelo novo diagnóstico: $completedApi está ativa neste projeto."
                    addActionHistory("Confirmada pelo diagnóstico • $selectedProjectId • $completedApi")
                } else if (pendingApi != null && pendingProject == selectedProjectId) {
                    apiActionStatus = "O diagnóstico novo ainda não lista $pendingApi como ativa. A pendência foi mantida; o GEIVIA não repetirá o comando."
                }
                editor.apply()
                summary
            }.onSuccess { diagnosticSummary = it; importStatus = "Diagnóstico importado e vinculado ao projeto selecionado." }
             .onFailure { importStatus = it.message ?: "Não foi possível importar o diagnóstico." }
        }
    }
    fun copyDiagnosticCommand() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Diagnóstico somente leitura GEIVIA", diagnosticCommand))
        Toast.makeText(context, "Comando copiado. Ele consulta somente o projeto selecionado.", Toast.LENGTH_LONG).show()
    }

    fun selectProject(project: CloudProject) {
        prefs.edit()
            .putString("selected_project_id", project.projectId)
            .putString("selected_project_name", project.name)
            .putString("selected_project_number", project.projectNumber)
            .apply()
        if (selectedProjectId != project.projectId) {
            diagnosticSummary = ""
            enabledFirebaseApis = emptySet()
            firestoreDatabaseIds = null
            firestoreBillingEnabled = null
            firestoreActionStatus = ""
            apiActionStatus = ""
            selectedApiForConfirmation = null
        }
        selectedProjectId = project.projectId
        selectedProjectName = project.name
        selectedProjectNumber = project.projectNumber
        importStatus = "Projeto selecionado e salvo neste aparelho."
    }

    fun openGoogleCloud(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(context, "Não foi possível abrir o navegador.", Toast.LENGTH_LONG).show()
        }
    }
    fun copyCommand() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Comando GEIVIA Cloud Shell", cloudShellCommand))
        Toast.makeText(context, "Comando copiado. Cole no terminal do Cloud Shell.", Toast.LENGTH_LONG).show()
    }

    if (showCancelPendingDialog) {
        val cancellingFirestore = pendingActionToCancel == "firestore"
        val pendingLabel = if (cancellingFirestore) "criação do Firestore (banco default)" else (prefs.getString("pending_api_service", "API") ?: "API")
        AlertDialog(
            onDismissRequest = { showCancelPendingDialog = false },
            title = { Text("Encerrar acompanhamento local?") },
            text = {
                Text("Isso remove apenas o bloqueio local de $pendingLabel. Não cancela uma operação que já esteja no Google Cloud, não apaga recursos e não confirma que o comando falhou. Depois de encerrar, será obrigatório importar um novo diagnóstico antes de preparar outra ação. Se o comando já foi executado ou ainda pode estar em andamento, não tente repeti-lo sem verificar o estado no Google Cloud.")
            },
            confirmButton = {
                TextButton(onClick = {
                    if (cancellingFirestore) {
                        val project = prefs.getString("pending_firestore_project_id", selectedProjectId) ?: selectedProjectId
                        val location = prefs.getString("pending_firestore_location", "") ?: ""
                        prefs.edit()
                            .remove("pending_firestore_project_id")
                            .remove("pending_firestore_location")
                            .remove("pending_firestore_created_at")
                            .apply()
                        runCatching {
                            val saved = org.json.JSONObject(prefs.getString("cloud_project_diagnostic_json", null) ?: "{}")
                            saved.remove("firestoreDatabases")
                            saved.remove("billing")
                            prefs.edit().putString("cloud_project_diagnostic_json", saved.toString()).apply()
                        }
                        firestoreDatabaseIds = null
                        firestoreBillingEnabled = null
                        firestoreActionStatus = "Acompanhamento encerrado somente neste aparelho. Importe um novo diagnóstico antes de qualquer nova preparação."
                        addActionHistory("Acompanhamento Firestore encerrado manualmente, sem confirmação de resultado • $project • $location")
                    } else {
                        val api = prefs.getString("pending_api_service", "API") ?: "API"
                        val project = prefs.getString("pending_api_project_id", selectedProjectId) ?: selectedProjectId
                        prefs.edit().remove("pending_api_service").remove("pending_api_project_id").remove("pending_api_created_at").apply()
                        runCatching {
                            val saved = org.json.JSONObject(prefs.getString("cloud_project_diagnostic_json", null) ?: "{}")
                            saved.put("enabledServices", JSONArray())
                            saved.remove("firestoreDatabases")
                            saved.remove("billing")
                            prefs.edit().putString("cloud_project_diagnostic_json", saved.toString()).apply()
                        }
                        diagnosticSummary = ""
                        enabledFirebaseApis = emptySet()
                        firestoreDatabaseIds = null
                        firestoreBillingEnabled = null
                        apiActionStatus = "Acompanhamento encerrado neste aparelho. Importe um novo diagnóstico antes de preparar outra ação. Nenhuma alteração foi feita no Google Cloud."
                        addActionHistory("Acompanhamento de API encerrado manualmente, sem confirmação de resultado • $project • $api")
                    }
                    showCancelPendingDialog = false
                }) { Text("Encerrar acompanhamento") }
            },
            dismissButton = { TextButton(onClick = { showCancelPendingDialog = false }) { Text("Voltar") } }
        )
    }
    if (pendingAbsentConfirmation.isNotBlank()) {
        val absentId = pendingAbsentConfirmation
        AlertDialog(
            onDismissRequest = { pendingAbsentConfirmation = "" },
            title = { Text("Confirmar relatório de ausência?") },
            text = { Text("O arquivo JSON é fornecido pelo usuário e não possui assinatura digital. Ele declara que a consulta gcloud projects describe retornou NOT_FOUND para o mesmo Project ID: $absentId. Só confirme se você acabou de gerar esse arquivo executando o comando de reconciliação exibido pelo GEIVIA no Cloud Shell, na conta Google correta. Se houver dúvida, cancele e mantenha a operação bloqueada.") },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        val current = workflowProgressStore.get(absentId, "project_creation")
                        if (current.state == WorkflowStepState.IN_PROGRESS) {
                            workflowProgressStore.markUnknown(absentId, "project_creation", "Usuário confirmou que importou o resultado da consulta de reconciliação do mesmo Project ID.")
                        }
                        workflowProgressStore.reconcileAbsent(
                            absentId,
                            "project_creation",
                            "Usuário confirmou arquivo importado que declara NOT_FOUND para o mesmo Project ID.",
                            userAcknowledged = true
                        )
                    }.onSuccess {
                        prefs.edit().remove("pending_project_creation_id").remove("pending_project_creation_name").apply()
                        importStatus = "Relatório de ausência confirmado pelo usuário. A operação anterior foi encerrada; uma nova tentativa exige outra confirmação explícita."
                    }.onFailure {
                        importStatus = it.message ?: "Não foi possível reconciliar a ausência."
                    }
                    pendingAbsentConfirmation = ""
                }) { Text("Confirmar arquivo") }
            },
            dismissButton = {
                TextButton(onClick = { pendingAbsentConfirmation = "" }) { Text("Cancelar") }
            }
        )
    }
    if (showProjectCreateDialog) {
        AlertDialog(
            onDismissRequest = { showProjectCreateDialog = false },
            title = { Text("Confirmar criação ou reutilização?") },
            text = { Text("Project ID: $newProjectId\nNome: $newProjectName\n\nO comando consulta primeiro este ID exato. Se existir e estiver acessível, reutiliza. Só uma resposta explícita de não encontrado permite uma única solicitação de criação. Erros de permissão, rede ou resposta ambígua interrompem o processo. Depois de executar, não repita o comando: use a reconciliação do mesmo ID.") },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        workflowProgressStore.waitForConfirmation(newProjectId.trim(), "project_creation", "Revisar criação ou reutilização do Project ID exato.")
                        workflowProgressStore.confirmAndStart(newProjectId.trim(), "project_creation", "project-create-${newProjectId.trim()}-${System.currentTimeMillis()}", "Comando único preparado; aguardando execução e reconciliação do mesmo Project ID.")
                    }.onFailure {
                        projectCreationStatus = it.message ?: "Não foi possível registrar a operação."
                        showProjectCreateDialog = false
                        return@TextButton
                    }
                    prefs.edit().putString("pending_project_creation_id", newProjectId.trim()).putString("pending_project_creation_name", newProjectName.trim()).apply()
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("GEIVIA criar ou reutilizar projeto", projectCreationCommand))
                    projectCreationStatus = "Comando copiado. Execute uma vez no Cloud Shell. Em caso de erro ou dúvida, não repita; reconcilie o mesmo Project ID."
                    Toast.makeText(context, "Comando copiado. Execução manual.", Toast.LENGTH_LONG).show()
                    showProjectCreateDialog = false
                }) { Text("Confirmar e copiar") }
            },
            dismissButton = { TextButton(onClick = { showProjectCreateDialog = false }) { Text("Cancelar") } }
        )
    }
    if (showFirestoreRulesDialog) {
        AlertDialog(
            onDismissRequest = { showFirestoreRulesDialog = false },
            title = { Text("Preparar regras iniciais restritivas?") },
            text = { Text("Projeto: $selectedProjectId\n\nO comando verifica se já existe uma publicação ativa de regras Firestore. Se existir, interrompe sem sobrescrever. Se não existir, publica uma regra inicial que nega todas as leituras e gravações. Isso protege os dados, mas também impede o aplicativo cliente de acessar o banco até que regras específicas sejam criadas, testadas e publicadas.") },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        workflowProgressStore.waitForConfirmation(selectedProjectId, "firestore_rules", "Revisar publicação inicial de regras deny-all; não substitui regras existentes.")
                        workflowProgressStore.confirmAndStart(selectedProjectId, "firestore_rules", "firestore-rules-$selectedProjectId-${System.currentTimeMillis()}", "Comando preparado; aguardando execução manual e confirmação.")
                    }.onFailure {
                        firestoreRulesStatus = it.message ?: "Não foi possível registrar a etapa."
                        showFirestoreRulesDialog = false
                        return@TextButton
                    }
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("GEIVIA regras Firestore", firestoreRulesCommand))
                    firestoreRulesStatus = "Comando copiado. Execute no Cloud Shell. A publicação inicial bloqueia todo acesso até personalizar as regras."
                    Toast.makeText(context, "Comando de regras copiado.", Toast.LENGTH_LONG).show()
                    showFirestoreRulesDialog = false
                }) { Text("Confirmar e copiar") }
            },
            dismissButton = { TextButton(onClick = { showFirestoreRulesDialog = false }) { Text("Cancelar") } }
        )
    }
    if (showHostingDeployDialog) {
        AlertDialog(
            onDismissRequest = { showHostingDeployDialog = false },
            title = { Text("Publicar página inicial no Hosting?") },
            text = { Text("Projeto: $selectedProjectId\n\nO comando cria ou reutiliza o site padrão do mesmo projeto e publica uma página demonstrativa simples. Ele não publica o código real do seu produto. Se a pasta local já existir e não tiver sido criada pelo GEIVIA, o comando para sem sobrescrever arquivos.") },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        workflowProgressStore.waitForConfirmation(selectedProjectId, "hosting_deploy", "Revisar criação/reutilização do site padrão e publicação da página inicial.")
                        workflowProgressStore.confirmAndStart(selectedProjectId, "hosting_deploy", "hosting-deploy-$selectedProjectId-${System.currentTimeMillis()}", "Comando preparado; aguardando deploy e teste manual da URL.")
                    }.onFailure {
                        hostingDeployStatus = it.message ?: "Não foi possível registrar a etapa."
                        showHostingDeployDialog = false
                        return@TextButton
                    }
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("GEIVIA Hosting deploy", hostingStarterCommand))
                    hostingDeployStatus = "Comando copiado. Execute no Cloud Shell e confira a URL HTTPS exibida ao final."
                    Toast.makeText(context, "Comando de Hosting copiado.", Toast.LENGTH_LONG).show()
                    showHostingDeployDialog = false
                }) { Text("Confirmar e copiar") }
            },
            dismissButton = { TextButton(onClick = { showHostingDeployDialog = false }) { Text("Cancelar") } }
        )
    }
    if (showFirestoreCreateDialog) {
        AlertDialog(
            onDismissRequest = { showFirestoreCreateDialog = false },
            title = { Text("Confirmar criação do Firestore?") },
            text = { Text("Será preparado um comando para criar somente o banco (default), edição Standard, na região $firestoreLocation, no projeto $selectedProjectId. O comando consulta os bancos existentes e não tenta criar outro se (default) já existir. A localização é uma decisão importante e não pode ser simplesmente trocada depois. O GEIVIA apenas copia o comando; você executará manualmente no Cloud Shell.") },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        workflowProgressStore.confirmAndStart(
                            selectedProjectId,
                            "firestore_database",
                            "firestore-default-${System.currentTimeMillis()}",
                            "Comando preparado e copiado; aguardando execução manual e diagnóstico do mesmo projeto."
                        )
                    }.onFailure {
                        firestoreActionStatus = it.message ?: "A etapa não foi liberada para confirmação."
                        showFirestoreCreateDialog = false
                        return@TextButton
                    }
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("GEIVIA criar Firestore", firestoreCreateCommand))
                    prefs.edit().putString("pending_firestore_project_id", selectedProjectId).putString("pending_firestore_location", firestoreLocation).putLong("pending_firestore_created_at", System.currentTimeMillis()).apply()
                    addActionHistory("Comando de criação Firestore preparado; aguardando diagnóstico • $selectedProjectId • $firestoreLocation")
                    firestoreActionStatus = "Comando copiado. Execute uma única vez no Cloud Shell e importe um novo diagnóstico para reconciliar o resultado."
                    Toast.makeText(context, "Comando copiado. A execução é manual.", Toast.LENGTH_LONG).show()
                    showFirestoreCreateDialog = false
                }) { Text("Confirmar e copiar") }
            },
            dismissButton = { TextButton(onClick = { showFirestoreCreateDialog = false }) { Text("Cancelar") } }
        )
    }
    selectedApiForConfirmation?.let { (api, label) ->
        AlertDialog(
            onDismissRequest = { selectedApiForConfirmation = null },
            title = { Text("Confirmar habilitação da API?") },
            text = {
                Column {
                    Text("Projeto de destino: $selectedProjectId", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("$label ($api)")
                    Spacer(Modifier.height(8.dp))
                    Text("O comando abaixo habilita somente esta API. O GEIVIA não executará o comando: ele será copiado para você revisar e executar no Cloud Shell. A ativação da API não significa que o uso futuro do serviço seja gratuito.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("gcloud services enable $api --project=$selectedProjectId", color = Color(0xFFDAEAF4), fontSize = 11.sp, modifier = Modifier.fillMaxWidth().background(Color(0xFF102B43), RoundedCornerShape(8.dp)).padding(10.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val command = "gcloud services enable $api --project=$selectedProjectId"
                    val stepId = apiWorkflowStepId(api)
                    val operationId = "api-$selectedProjectId-$api-${System.currentTimeMillis()}"
                    val started = runCatching {
                        workflowProgressStore.confirmAndStart(
                            selectedProjectId,
                            stepId,
                            operationId,
                            "Comando preparado e copiado; aguardando execução manual e diagnóstico."
                        )
                    }
                    if (started.isFailure) {
                        apiActionStatus = started.exceptionOrNull()?.message ?: "Não foi possível registrar a operação."
                        selectedApiForConfirmation = null
                        return@TextButton
                    }
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("GEIVIA habilitar API", command))
                    prefs.edit()
                        .putString("pending_api_service", api)
                        .putString("pending_api_project_id", selectedProjectId)
                        .putLong("pending_api_created_at", System.currentTimeMillis())
                        .apply()
                    apiActionStatus = "Comando copiado para $selectedProjectId. Execute no Cloud Shell e importe um novo diagnóstico para confirmar."
                    addActionHistory("Comando preparado; aguardando diagnóstico • $selectedProjectId • $api")
                    Toast.makeText(context, "Comando copiado. Execute manualmente no Cloud Shell.", Toast.LENGTH_LONG).show()
                    selectedApiForConfirmation = null
                }) { Text("Confirmar e copiar") }
            },
            dismissButton = { TextButton(onClick = { selectedApiForConfirmation = null }) { Text("Cancelar") } }
        )
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) {
        Text("GEIVIA", color = DeepBlue, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        Spacer(Modifier.height(18.dp))
        Text("Prepare seu Google Cloud.", color = Ink, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Ambiente: $workspaceName", color = DeepBlue, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text("Código: $workspaceId", color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(14.dp))
        Text("O GEIVIA não usa nenhuma conta do desenvolvedor. O Cloud Shell acessa os recursos com a sua conta Google, autenticada diretamente no navegador.", color = Muted, fontSize = 14.sp, lineHeight = 21.sp)

        Spacer(Modifier.height(16.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF4FA)), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(15.dp)) {
                Text("ETAPA 1 — ENTRE NA SUA CONTA", color = DeepBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Abra o Google Cloud e faça login com a conta que será proprietária dos seus projetos. Não informe sua senha dentro do GEIVIA.", color = Ink, fontSize = 13.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(10.dp))
                Button(onClick = { openGoogleCloud("https://console.cloud.google.com/") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Abrir Google Cloud")
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(15.dp)) {
                Text("ETAPA 2 — ABRA O CLOUD SHELL", color = DeepBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("O Cloud Shell executa comandos na sessão Google que você autenticou no navegador. Assim, o GEIVIA não precisa guardar senha, chave de serviço ou token da sua conta.", color = Ink, fontSize = 13.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { openGoogleCloud("https://shell.cloud.google.com/?show=terminal") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Abrir Cloud Shell")
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF5E6)), shape = RoundedCornerShape(14.dp)) {
            Text("A criação de projeto está disponível como ação opcional e manual. O comando consulta primeiro o Project ID exato; resultado ambíguo bloqueia novas tentativas e exige reconciliação do mesmo ID.", Modifier.padding(15.dp), color = Color(0xFF77501A), fontSize = 12.sp, lineHeight = 18.sp)
        }
        Spacer(Modifier.height(12.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(15.dp)) {
                Text("ETAPA 3 — DIAGNÓSTICO DA SUA CONTA", color = DeepBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("No terminal do Cloud Shell, cole o comando abaixo. Ele apenas identifica a conta ativa, lista projetos que ela já pode visualizar e baixa um JSON para o aparelho. Não cria recursos, não ativa APIs e não altera faturamento.", color = Ink, fontSize = 13.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(10.dp))
                Text(cloudShellCommand, color = Color(0xFFDAEAF4), fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.fillMaxWidth().background(Color(0xFF102B43), RoundedCornerShape(10.dp)).padding(12.dp))
                Spacer(Modifier.height(10.dp))
                Button(onClick = { copyCommand() }, modifier = Modifier.fillMaxWidth()) { Text("Copiar comando de diagnóstico") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Importar geivia-projects.json")
                }
                if (importStatus.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(importStatus, color = DeepBlue, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(15.dp)) {
                Text("ETAPA 4 — ESCOLHA O PROJETO DE DESTINO", color = DeepBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Selecione um projeto que já existe na sua conta. O GEIVIA salvará o ID exato e deverá manter esse mesmo destino nas próximas etapas. A importação de outra lista não troca sua seleção automaticamente.", color = Ink, fontSize = 13.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(10.dp))
                if (selectedProjectId.isNotBlank()) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF6EF)), shape = RoundedCornerShape(10.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("PROJETO ATUALMENTE SELECIONADO", color = Color(0xFF236342), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(selectedProjectName.ifBlank { selectedProjectId }, color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Project ID: $selectedProjectId", color = Muted, fontSize = 12.sp)
                            if (selectedProjectNumber.isNotBlank()) Text("Número: $selectedProjectNumber", color = Muted, fontSize = 11.sp)
                            if (importedProjects.none { it.projectId == selectedProjectId }) {
                                Text("Este projeto não aparece na última lista importada. A seleção foi preservada; confira o acesso antes de continuar.", color = Color(0xFF9A5B13), fontSize = 11.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                if (importedProjects.isEmpty()) {
                    Text("Nenhum projeto importado ainda. Faça o diagnóstico e importe o arquivo JSON para escolher um destino.", color = Muted, fontSize = 13.sp)
                } else {
                    Text("PROJETOS DISPONÍVEIS (${importedProjects.size})", color = DeepBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    importedProjects.forEach { project ->
                        val chosen = selectedProjectId == project.projectId
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 7.dp).clickable { selectProject(project) },
                            shape = RoundedCornerShape(11.dp),
                            colors = CardDefaults.cardColors(containerColor = if (chosen) Color(0xFFE8F4FA) else Color(0xFFF7FBFD))
                        ) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = chosen, onClick = { selectProject(project) })
                                Spacer(Modifier.width(7.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(project.name, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text("ID: ${project.projectId}", color = Muted, fontSize = 11.sp)
                                    if (project.lifecycleState.isNotBlank()) Text("Estado: ${project.lifecycleState}", color = Muted, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(15.dp)) {
                Text("ETAPA 4B — CRIAR OU REUTILIZAR PROJETO", color = DeepBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("Opcional. O Project ID é escolhido por você e não muda automaticamente.", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                if (pendingCreationId.isNotBlank()) {
                    Text("Operação aguardando reconciliação: $pendingCreationId", color = Color(0xFF9A5B13), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Não inicie outra criação. Consulte apenas este ID e importe o JSON resultante.", color = Muted, fontSize = 10.sp)
                    Button(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("GEIVIA reconciliar projeto", projectReconcileCommand))
                        Toast.makeText(context, "Consulta do mesmo ID copiada. Não cria projeto.", Toast.LENGTH_LONG).show()
                    }, enabled = projectReconcileCommand.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Reconciliar mesmo Project ID") }
                } else {
                    OutlinedTextField(value = newProjectId, onValueChange = { value ->
                        newProjectId = value.lowercase().filter { it.isLetterOrDigit() || it == '-' }.take(30)
                        prefs.edit().putString("new_project_id", newProjectId).apply()
                    }, label = { Text("Project ID escolhido por você") }, supportingText = { Text("6–30 caracteres; letras minúsculas, números e hífen.") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(value = newProjectName, onValueChange = { value ->
                        newProjectName = value.take(30)
                        prefs.edit().putString("new_project_name", newProjectName).apply()
                    }, label = { Text("Nome de exibição") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("O comando consulta primeiro o ID exato. Só cria se o Google confirmar que ele não existe. Erros de permissão ou conexão interrompem o processo.", color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
                    Button(onClick = { showProjectCreateDialog = true }, enabled = projectCreationCommand.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Revisar criação ou reutilização") }
                }
                if (projectCreationStatus.isNotBlank()) Text(projectCreationStatus, color = DeepBlue, fontSize = 11.sp, lineHeight = 15.sp)
            }
        }

        if (selectedProjectId.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(15.dp)) {
                    Text("ETAPA 5 — CONFERIR ESTE PROJETO (SOMENTE LEITURA)", color = DeepBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("O comando confirma se o projeto selecionado existe e lista as APIs habilitadas. Não cria projetos, não habilita APIs e não altera faturamento.", color = Ink, fontSize = 12.sp, lineHeight = 18.sp)
                    Spacer(Modifier.height(9.dp))
                    Text(diagnosticCommand, color = Color(0xFFDAEAF4), fontSize = 9.sp, lineHeight = 14.sp, modifier = Modifier.fillMaxWidth().background(Color(0xFF102B43), RoundedCornerShape(10.dp)).padding(11.dp))
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { copyDiagnosticCommand() }, modifier = Modifier.fillMaxWidth()) { Text("Copiar diagnóstico deste projeto") }
                    OutlinedButton(onClick = { diagnosticLauncher.launch(arrayOf("application/json", "text/*", "*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Importar geivia-diagnostic.json") }
                    if (diagnosticSummary.isNotBlank()) {
                        Text(diagnosticSummary, color = DeepBlue, fontSize = 11.sp)
                        Spacer(Modifier.height(12.dp))
                        Divider(color = Color(0xFFE1EAF0))
                        Spacer(Modifier.height(10.dp))
                        Text("ETAPA 6 — SERVIÇOS FIREBASE", color = DeepBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("Comparação informativa das APIs mais comuns. Uma API ausente não será ativada automaticamente; a necessidade depende dos recursos escolhidos no questionário.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                        Spacer(Modifier.height(7.dp))
                        firebaseApiChecks.forEach { (api, label) ->
                            val enabled = api in enabledFirebaseApis
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (enabled) "✓" else "!", color = if (enabled) Color(0xFF238653) else Color(0xFFB7791F), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(label, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text(api, color = Muted, fontSize = 10.sp)
                                }
                                Text(if (enabled) "Ativa" else "Não listada", color = if (enabled) Color(0xFF238653) else Color(0xFF9A641B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Divider(color = Color(0xFFE1EAF0))
                        Spacer(Modifier.height(10.dp))
                        Text("ETAPA 7 — HABILITAÇÃO ASSISTIDA, UMA API POR VEZ", color = DeepBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("O GEIVIA prepara um único comando para o projeto selecionado. Você revisa, confirma, copia e executa manualmente no Cloud Shell. Nenhuma alteração é executada pelo aplicativo.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        val currentPendingApi = prefs.getString("pending_api_service", null)
                        val currentPendingProject = prefs.getString("pending_api_project_id", null)
                        if (currentPendingApi != null && currentPendingProject == selectedProjectId) {
                            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF5E6)), shape = RoundedCornerShape(10.dp)) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("AÇÃO AGUARDANDO VERIFICAÇÃO", color = Color(0xFF9A5B13), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text("$currentPendingApi • projeto $currentPendingProject", color = Ink, fontSize = 11.sp)
                                    Text("Importe um diagnóstico atualizado. Se a API não aparecer, a pendência permanece e nenhum comando será repetido automaticamente.", color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
                                    TextButton(onClick = { pendingActionToCancel = "api"; showCancelPendingDialog = true }) { Text("Encerrar somente o acompanhamento local") }
                                }
                            }
                        }
                        requestedApis.forEach { (api, label) ->
                            val enabled = api in enabledFirebaseApis
                            val blockedByCost = costMode == "zero" && (api == "firebasestorage.googleapis.com" || api == "cloudbuild.googleapis.com")
                            Card(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = if (enabled) Color(0xFFEAF6EF) else if (blockedByCost) Color(0xFFFFF5E6) else Color(0xFFF4F8FA)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(Modifier.padding(11.dp)) {
                                    Text(label, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text(api, color = Muted, fontSize = 10.sp)
                                    Spacer(Modifier.height(5.dp))
                                    when {
                                        enabled -> Text("Já habilitada — nenhuma ação necessária.", color = Color(0xFF236342), fontSize = 11.sp)
                                        blockedByCost -> Text("Bloqueada no modo custo zero: exige avaliação específica de limites e possível faturamento.", color = Color(0xFF9A5B13), fontSize = 11.sp)
                                        selectedProjectId.isBlank() -> Text("Selecione um projeto antes de preparar alterações.", color = Muted, fontSize = 11.sp)
                                        prefs.getString("pending_api_service", null) != null && prefs.getString("pending_api_project_id", null) == selectedProjectId ->
                                            Text("Há uma ação aguardando verificação. Importe um novo diagnóstico antes de preparar outra API.", color = Color(0xFF9A5B13), fontSize = 11.sp, lineHeight = 15.sp)
                                        else -> Button(onClick = {
                                            runCatching {
                                                workflowProgressStore.waitForConfirmation(
                                                    selectedProjectId,
                                                    apiWorkflowStepId(api),
                                                    "Revisar habilitação de $api para o projeto selecionado."
                                                )
                                                selectedApiForConfirmation = api to label
                                            }.onFailure {
                                                apiActionStatus = it.message ?: "Não foi possível abrir a confirmação da API."
                                            }
                                        }, modifier = Modifier.fillMaxWidth()) {
                                            Text("Revisar e confirmar")
                                        }
                                    }
                                }
                            }
                        }
                        if (apiActionStatus.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(apiActionStatus, color = DeepBlue, fontSize = 11.sp, lineHeight = 16.sp)
                        }
                        Spacer(Modifier.height(14.dp))
                        Divider(color = Color(0xFFE1EAF0))
                        Spacer(Modifier.height(10.dp))
                        Text("ETAPA 8 — CLOUD FIRESTORE", color = DeepBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("A criação só fica disponível depois que a API Firestore estiver ativa e um diagnóstico atualizado listar os bancos do projeto. O GEIVIA não presume que uma lista ausente significa que não existe banco.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        when {
                            "firestore.googleapis.com" !in enabledFirebaseApis -> Text("Primeiro habilite a API Cloud Firestore pela etapa anterior e importe um novo diagnóstico.", color = Color(0xFF9A641B), fontSize = 11.sp)
                            firestoreDatabaseIds == null -> Text("Importe um diagnóstico recente que inclua a lista de bancos Firestore antes de continuar.", color = Color(0xFF9A641B), fontSize = 11.sp)
                            "(default)" in firestoreDatabaseIds.orEmpty() -> Text("O banco (default) já existe neste projeto. O GEIVIA não oferecerá outra criação.", color = Color(0xFF236342), fontSize = 11.sp)
                            prefs.getString("pending_firestore_project_id", null) == selectedProjectId -> Column {
                                Text("Há uma criação aguardando reconciliação. Importe um novo diagnóstico; não repita o comando.", color = Color(0xFF9A641B), fontSize = 11.sp)
                                TextButton(onClick = { pendingActionToCancel = "firestore"; showCancelPendingDialog = true }) { Text("Encerrar somente o acompanhamento local") }
                            }
                            firestoreBillingEnabled != false -> Text(if (firestoreBillingEnabled == true) "Faturamento está habilitado neste projeto. A criação foi bloqueada para preservar o modo custo zero." else "Não foi possível confirmar que o faturamento está desabilitado. A criação fica bloqueada até essa informação ser verificada.", color = Color(0xFF9A641B), fontSize = 11.sp, lineHeight = 15.sp)
                            else -> Text("Nenhum banco (default) foi encontrado e o diagnóstico confirmou faturamento desabilitado. Escolha uma região antes de criar.", color = Muted, fontSize = 11.sp)
                        }
                        if ("firestore.googleapis.com" in enabledFirebaseApis && firestoreDatabaseIds != null && "(default)" !in firestoreDatabaseIds.orEmpty() && prefs.getString("pending_firestore_project_id", null) != selectedProjectId && firestoreBillingEnabled == false) {
                            Spacer(Modifier.height(8.dp))
                            Text("REGIÃO DO BANCO — ESCOLHA ANTES DE CRIAR", color = DeepBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("A localização padrão do projeto pode já ter sido definida por outro recurso Google Cloud. Se o Google indicar incompatibilidade, pare e verifique a localização existente; não tente contornar criando outro projeto.", color = Color(0xFF9A641B), fontSize = 10.sp, lineHeight = 14.sp)
                            listOf("southamerica-east1" to "São Paulo, Brasil", "us-central1" to "Iowa, Estados Unidos", "nam5" to "Multirregião Estados Unidos").forEach { (region, label) ->
                                Row(Modifier.fillMaxWidth().clickable { firestoreLocation = region; prefs.edit().putString("firestore_location", region).apply() }.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = firestoreLocation == region, onClick = { firestoreLocation = region; prefs.edit().putString("firestore_location", region).apply() })
                                    Column {
                                        Text(label, color = Ink, fontSize = 12.sp)
                                        Text(region, color = Muted, fontSize = 10.sp)
                                    }
                                }
                            }
                            Button(onClick = {
                                runCatching {
                                    workflowProgressStore.waitForConfirmation(selectedProjectId, "firestore_database", "Região escolhida: $firestoreLocation. Revisar criação do banco (default).")
                                    showFirestoreCreateDialog = true
                                }.onFailure {
                                    firestoreActionStatus = it.message ?: "Não foi possível abrir a confirmação desta etapa."
                                }
                            }, enabled = firestoreLocation.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Revisar criação do banco") }
                        }
                        Text("IMPORTANTE: criar o banco não configura regras de segurança. Antes de conectar o aplicativo, publique regras restritivas adequadas aos dados e aos usuários. Não use regras abertas de teste em produção.", color = Color(0xFF9A641B), fontSize = 10.sp, lineHeight = 14.sp)
                        if ("(default)" in firestoreDatabaseIds.orEmpty() && selectedProjectId.isNotBlank()) {
                            val rulesRecord = workflowProgressStore.get(selectedProjectId, "firestore_rules")
                            Spacer(Modifier.height(8.dp))
                            Text("ETAPA 8B — REGRAS DE SEGURANÇA FIRESTORE", color = DeepBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            when (rulesRecord.state) {
                                WorkflowStepState.NOT_STARTED, WorkflowStepState.WAITING_CONFIRMATION -> Button(onClick = { showFirestoreRulesDialog = true }, enabled = firestoreRulesCommand.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Preparar regras iniciais restritivas") }
                                WorkflowStepState.IN_PROGRESS -> Button(onClick = {
                                    runCatching {
                                        workflowProgressStore.confirmManualCompletion(selectedProjectId, "firestore_rules", "O usuário declarou que executou o comando e conferiu a publicação das regras deny-all.")
                                        addActionHistory("Regras iniciais Firestore declaradas publicadas pelo usuário • $selectedProjectId")
                                    }.onFailure { firestoreRulesStatus = it.message ?: "Não foi possível registrar a confirmação." }
                                }, modifier = Modifier.fillMaxWidth()) { Text("Já publiquei — registrar confirmação") }
                                WorkflowStepState.USER_CONFIRMED -> Text("Publicação declarada pelo usuário. As regras atuais negam todo acesso até serem substituídas por regras específicas e testadas.", color = Color(0xFF9A641B), fontSize = 10.sp)
                                WorkflowStepState.COMPLETED -> Text("Regras confirmadas por diagnóstico.", color = Color(0xFF236342), fontSize = 10.sp)
                                WorkflowStepState.UNKNOWN, WorkflowStepState.FAILED -> Text("Etapa bloqueada até reconciliar a publicação anterior. Não repita automaticamente.", color = Color(0xFF9A5B13), fontSize = 10.sp)
                            }
                        }
                        if (firestoreRulesStatus.isNotBlank()) Text(firestoreRulesStatus, color = DeepBlue, fontSize = 11.sp, lineHeight = 16.sp)
                        if (firestoreActionStatus.isNotBlank()) Text(firestoreActionStatus, color = DeepBlue, fontSize = 11.sp, lineHeight = 16.sp)
                        if (answers["login"].orEmpty().isNotBlank() && !answers["login"].orEmpty().contains("Não precisa")) {
                            Spacer(Modifier.height(14.dp))
                            Divider(color = Color(0xFFE1EAF0))
                            Spacer(Modifier.height(10.dp))
                            Text("ETAPA 9 — FIREBASE AUTHENTICATION (PLANO SPARK)", color = DeepBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("A autenticação básica do Firebase oferece métodos sem custo no plano Spark. Configure os provedores diretamente no Console Firebase. Se o projeto ainda não for um projeto Firebase, adicione Firebase ao projeto Google Cloud existente — não crie outro projeto. Não faça upgrade para Identity Platform nem habilite login por telefone/SMS no fluxo custo zero.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                            Spacer(Modifier.height(8.dp))
                            if (selectedProjectId.isNotBlank()) {
                                if ("identitytoolkit.googleapis.com" in enabledFirebaseApis) {
                                    Button(onClick = { openGoogleCloud("https://console.firebase.google.com/project/$selectedProjectId/authentication/providers") }, modifier = Modifier.fillMaxWidth()) {
                                        Text("Abrir provedores de autenticação")
                                    }
                                } else {
                                    Text("Primeiro habilite identitytoolkit.googleapis.com na etapa de APIs e importe um novo diagnóstico do mesmo projeto. Depois disso, abra os provedores de login.", color = Color(0xFF9A641B), fontSize = 10.sp, lineHeight = 14.sp)
                                }
                            }
                            Text("No Console: abra Método de login e ative somente os provedores necessários, como e-mail/senha ou Google. A ativação da Identity Toolkit API, isoladamente, não configura nenhum provedor.", color = Ink, fontSize = 11.sp, lineHeight = 16.sp)
                        }
                        if (answers["login"].orEmpty().isNotBlank() && !answers["login"].orEmpty().contains("Não precisa") && selectedProjectId.isNotBlank()) {
                            val authRecord = workflowProgressStore.get(selectedProjectId, "authentication")
                            Spacer(Modifier.height(6.dp))
                            when (authRecord.state) {
                                WorkflowStepState.NOT_STARTED, WorkflowStepState.WAITING_CONFIRMATION -> Button(onClick = {
                                    runCatching {
                                        workflowProgressStore.waitForConfirmation(selectedProjectId, "authentication", "Configurar os provedores escolhidos no Console Firebase.")
                                        workflowProgressStore.confirmAndStart(selectedProjectId, "authentication", "auth-manual-$selectedProjectId-${System.currentTimeMillis()}", "Configuração manual iniciada no Console Firebase.")
                                        openGoogleCloud("https://console.firebase.google.com/project/$selectedProjectId/authentication/providers")
                                    }.onFailure { apiActionStatus = it.message ?: "Não foi possível iniciar a etapa manual." }
                                }, modifier = Modifier.fillMaxWidth()) { Text("Iniciar configuração de autenticação") }
                                WorkflowStepState.IN_PROGRESS -> Button(onClick = {
                                    runCatching {
                                        workflowProgressStore.confirmManualCompletion(selectedProjectId, "authentication", "O usuário confirmou que os provedores necessários foram configurados no Console Firebase.")
                                        addActionHistory("Authentication declarada configurada pelo usuário • $selectedProjectId")
                                    }.onFailure { apiActionStatus = it.message ?: "Não foi possível registrar a confirmação." }
                                }, modifier = Modifier.fillMaxWidth()) { Text("Já configurei — registrar confirmação") }
                                WorkflowStepState.USER_CONFIRMED -> Text("Configuração declarada pelo usuário. O GEIVIA não consegue verificar automaticamente os provedores ativos.", color = Color(0xFF236342), fontSize = 10.sp)
                                WorkflowStepState.COMPLETED -> Text("Etapa confirmada por diagnóstico.", color = Color(0xFF236342), fontSize = 10.sp)
                                WorkflowStepState.UNKNOWN, WorkflowStepState.FAILED -> Text("Etapa bloqueada até reconciliar o estado anterior. Não reinicie automaticamente.", color = Color(0xFF9A5B13), fontSize = 10.sp)
                            }
                        }
                        if (answers["platform"].orEmpty().let { it.contains("web") || it.contains("Site") || it.contains("Android e web") }) {
                            Spacer(Modifier.height(14.dp))
                            Divider(color = Color(0xFFE1EAF0))
                            Spacer(Modifier.height(10.dp))
                            Text("ETAPA 10 — FIREBASE HOSTING", color = DeepBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("O Hosting publica o site, mas não cria o conteúdo nem faz o deploy do seu código. Primeiro habilite a API Firebase Hosting pela etapa de APIs; depois configure o site no projeto já selecionado. Não crie outro projeto para contornar erros.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                            if (selectedProjectId.isNotBlank()) {
                                val hostingRecord = workflowProgressStore.get(selectedProjectId, "hosting")
                                when (hostingRecord.state) {
                                    WorkflowStepState.NOT_STARTED, WorkflowStepState.WAITING_CONFIRMATION -> {
                                        if ("firebasehosting.googleapis.com" in enabledFirebaseApis) {
                                            Button(onClick = {
                                                runCatching {
                                                    workflowProgressStore.waitForConfirmation(selectedProjectId, "hosting", "Abrir o painel de Hosting do projeto existente.")
                                                    workflowProgressStore.confirmAndStart(selectedProjectId, "hosting", "hosting-manual-$selectedProjectId-${System.currentTimeMillis()}", "Configuração manual do Hosting iniciada.")
                                                    openGoogleCloud("https://console.firebase.google.com/project/$selectedProjectId/hosting/sites")
                                                }.onFailure { apiActionStatus = it.message ?: "Não foi possível iniciar a etapa de Hosting." }
                                            }, modifier = Modifier.fillMaxWidth()) { Text("Abrir configuração do Hosting") }
                                        } else {
                                            Text("Primeiro habilite firebasehosting.googleapis.com na etapa de APIs e importe um novo diagnóstico deste projeto. A configuração do Hosting ficará liberada depois da confirmação.", color = Color(0xFF9A641B), fontSize = 10.sp, lineHeight = 14.sp)
                                        }
                                    }
                                    WorkflowStepState.IN_PROGRESS -> Button(onClick = {
                                        runCatching {
                                            workflowProgressStore.confirmManualCompletion(selectedProjectId, "hosting", "O usuário confirmou que o site Firebase Hosting foi criado/configurado no projeto selecionado.")
                                            addActionHistory("Firebase Hosting declarado configurado pelo usuário • $selectedProjectId")
                                        }.onFailure { apiActionStatus = it.message ?: "Não foi possível registrar a confirmação." }
                                    }, modifier = Modifier.fillMaxWidth()) { Text("Já configurei — registrar confirmação") }
                                    WorkflowStepState.USER_CONFIRMED -> Text("Hosting declarado configurado pelo usuário; o deploy e a URL pública ainda precisam ser testados.", color = Color(0xFF236342), fontSize = 10.sp)
                                    WorkflowStepState.COMPLETED -> Text("Etapa confirmada por diagnóstico.", color = Color(0xFF236342), fontSize = 10.sp)
                                    WorkflowStepState.UNKNOWN, WorkflowStepState.FAILED -> Text("Etapa bloqueada até reconciliar o estado anterior. Não reinicie automaticamente.", color = Color(0xFF9A5B13), fontSize = 10.sp)
                                }
                                Spacer(Modifier.height(8.dp))
                                Text("ETAPA 10B — PUBLICAR UMA PÁGINA INICIAL", color = DeepBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("O GEIVIA prepara um site estático demonstrativo. Substitua a página pelos arquivos reais do seu produto antes de divulgar o endereço.", color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
                                val deployRecord = workflowProgressStore.get(selectedProjectId, "hosting_deploy")
                                when (deployRecord.state) {
                                    WorkflowStepState.NOT_STARTED, WorkflowStepState.WAITING_CONFIRMATION -> Button(onClick = { showHostingDeployDialog = true }, enabled = "firebasehosting.googleapis.com" in enabledFirebaseApis && hostingStarterCommand.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Revisar e publicar página inicial") }
                                    WorkflowStepState.IN_PROGRESS -> Button(onClick = {
                                        runCatching {
                                            workflowProgressStore.confirmManualCompletion(selectedProjectId, "hosting_deploy", "O usuário confirmou que executou o deploy e testou a URL HTTPS.")
                                            addActionHistory("Hosting inicial declarado publicado e testado • $selectedProjectId")
                                        }.onFailure { hostingDeployStatus = it.message ?: "Não foi possível registrar a confirmação." }
                                    }, modifier = Modifier.fillMaxWidth()) { Text("Já publiquei e testei — confirmar") }
                                    WorkflowStepState.USER_CONFIRMED -> Text("Deploy e teste declarados pelo usuário. URL padrão: https://$selectedProjectId.web.app", color = Color(0xFF236342), fontSize = 10.sp)
                                    WorkflowStepState.COMPLETED -> Text("Deploy confirmado por diagnóstico.", color = Color(0xFF236342), fontSize = 10.sp)
                                    WorkflowStepState.UNKNOWN, WorkflowStepState.FAILED -> Text("Etapa bloqueada até reconciliar a publicação anterior. Não repita automaticamente.", color = Color(0xFF9A5B13), fontSize = 10.sp)
                                }
                                if (hostingDeployStatus.isNotBlank()) Text(hostingDeployStatus, color = DeepBlue, fontSize = 10.sp, lineHeight = 14.sp)
                            } else Text("Selecione um projeto existente para configurar o Hosting.", color = Color(0xFF9A641B), fontSize = 10.sp)
                        }
                        if (answers["files"].orEmpty().isNotBlank() && !answers["files"].orEmpty().startsWith("Não terá")) {
                            Spacer(Modifier.height(14.dp))
                            Divider(color = Color(0xFFE1EAF0))
                            Spacer(Modifier.height(10.dp))
                            Text("ETAPA 11 — CLOUD STORAGE", color = DeepBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(if (costMode == "zero") "Bloqueado no modo custo zero: a criação de novos buckets do Cloud Storage for Firebase pode exigir o plano Blaze e faturamento associado. O GEIVIA não habilitará, criará bucket nem solicitará upgrade nesta configuração." else "Antes de criar um bucket, confira no Console Firebase o plano exigido, a região e os preços aplicáveis. Nenhum recurso será criado automaticamente.", color = Color(0xFF9A5B13), fontSize = 11.sp, lineHeight = 16.sp)
                            Text("A API habilitada não significa que o Storage esteja pronto nem que o uso seja gratuito. Não faça upgrade nem associe faturamento sem decisão explícita.", color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
                        }
                        if (actionHistory.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Divider(color = Color(0xFFE1EAF0))
                            Spacer(Modifier.height(8.dp))
                            Text("HISTÓRICO DE AÇÕES", color = DeepBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            actionHistory.asReversed().forEach { entry ->
                                Text("• $entry", color = Muted, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(vertical = 2.dp))
                            }
                            Text("Histórico local deste aparelho; não é um registro emitido pelo Google Cloud.", color = Muted, fontSize = 9.sp, lineHeight = 13.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF5E6)), shape = RoundedCornerShape(14.dp)) {
            Text("O GEIVIA guarda apenas o identificador, nome, número e estado dos projetos. Nunca importe tokens, cookies ou chaves. A seleção fica salva neste aparelho e não será substituída por um projeto novo após um erro.", Modifier.padding(15.dp), color = Color(0xFF77501A), fontSize = 12.sp, lineHeight = 18.sp)
        }
        Spacer(Modifier.height(18.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Continuar para o questionário") }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Voltar") }
    }
}

private fun shareProjectPlan(context: Context, answers: Map<String, String>, costMode: String) {
    val labels = mapOf(
        "platform" to "Plataforma",
        "purpose" to "Finalidade",
        "audience" to "Público",
        "login" to "Acesso",
        "data" to "Dados",
        "files" to "Arquivos",
        "hosting" to "Publicação web",
        "extras" to "Recursos especiais"
    )
    val cost = if (costMode == "zero") "Custo zero: bloquear etapas que exijam faturamento." else "Recursos pagos: somente após autorização específica."
    val body = buildString {
        appendLine("PLANO INICIAL DO PROJETO — GEIVIA")
        appendLine()
        labels.forEach { (key, label) ->
            answers[key]?.takeIf { it.isNotBlank() }?.let { appendLine("$label: $it") }
        }
        appendLine()
        appendLine("Política de custos: $cost")
        appendLine()
        appendLine("Este documento é uma pré-análise. Disponibilidade, limites e preços dos serviços devem ser verificados antes da implementação.")
        appendLine("Gerado localmente pelo GEIVIA. Nenhuma conta Google foi conectada.")
    }
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Plano inicial do projeto")
        putExtra(Intent.EXTRA_TEXT, body)
    }
    context.startActivity(Intent.createChooser(sendIntent, "Compartilhar plano"))
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
