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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

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
        setContent { GeiviaApp(intent?.data) }
    }
}


@Composable
private fun BrandSplashScreen() {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF7FBFD)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.geivia_splash_icon),
            contentDescription = "Símbolo GEIVIA",
            modifier = Modifier.size(196.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text("GEIVIA", fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF101820), letterSpacing = 2.sp)
        Spacer(Modifier.height(4.dp))
        Text("FIREBASE DEPLOY &", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Blue, letterSpacing = 0.5.sp)
        Text("HOSTING AUTOMATION", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink, letterSpacing = 0.5.sp)
        Spacer(Modifier.height(28.dp))
        Text("Configuração e ativação de APIs  •  Diagnóstico e regras", fontSize = 11.sp, color = Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text("Authentication  •  Registro Android/Web", fontSize = 11.sp, color = Muted, textAlign = TextAlign.Center)
    }
}

private data class Question(val id: String, val title: String, val help: String, val options: List<String>, val multi: Boolean = false)

@Composable
private fun GeiviaApp(githubCallbackUri: Uri? = null) {
    val context = LocalContext.current
    var showBrandSplash by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(1200); showBrandSplash = false }
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
    val validWorkflowPages = setOf("device_access", "workspace_setup", "cloud_setup", "questions", "cost", "summary", "dashboard", "accounts", "google_access", "github", "terminal", "toolchain_updates", "diagnostics", "project_operations", "project_preview", "firebase_admin", "legacy_dashboard")
    val savedCheckpoint = remember {
        prefs.getString("workflow_checkpoint", null)?.takeIf { it in validWorkflowPages }
            ?.let { when (it) { "cloud_setup" -> "questions"; "terminal", "summary" -> "dashboard"; else -> it } }
            ?: if (prefs.getBoolean("cloud_setup_completed", false)) "questions" else "workspace_setup"
    }
    var page by rememberSaveable {
        mutableStateOf(
            if (githubCallbackUri != null) "github"
            else if (!prefs.getBoolean("device_access_completed", false)) "device_access"
            else if (!prefs.getBoolean("environment_setup_completed", false)) "environment_setup"
            else if (!prefs.getBoolean("preflight_completed", false)) "preflight"
            // A completed analysis is a checkpoint, not the app's home screen.
            // Return an established workspace directly to GEIVIA Studio.
            else "dashboard"
        )
    }
    var returnToEditorAfterTool by rememberSaveable { mutableStateOf(false) }
    var index by rememberSaveable {
        mutableIntStateOf(prefs.getInt("workflow_question_index", 0).coerceAtLeast(0))
    }
    var costMode by rememberSaveable { mutableStateOf(prefs.getString("cost_mode", "") ?: "") }
    var preflightGoogleAccount by rememberSaveable { mutableStateOf(prefs.getString("preflight_google_account", "") ?: "") }
    var preflightGoogleEmail by rememberSaveable { mutableStateOf(prefs.getString("preflight_google_email", "") ?: "") }
    var preflightCloudProjectId by rememberSaveable { mutableStateOf(prefs.getString("preflight_cloud_project_id", "") ?: "") }
    var preflightFirebaseProjectId by rememberSaveable { mutableStateOf(prefs.getString("preflight_firebase_project_id", "") ?: "") }
    var preflightCloudProject by rememberSaveable { mutableStateOf(prefs.getString("preflight_cloud_project", "") ?: "") }
    var preflightFirebaseProject by rememberSaveable { mutableStateOf(prefs.getString("preflight_firebase_project", "") ?: "") }
    var preflightGithubRepo by rememberSaveable { mutableStateOf(prefs.getString("preflight_github_repo", "") ?: "") }
    var preflightProjectId by rememberSaveable { mutableStateOf(prefs.getString("preflight_project_id", "") ?: "") }
    val githubTokenStore = remember { GitHubTokenStore(context) }
    var githubConnected by remember { mutableStateOf(githubTokenStore.accessToken() != null) }
    var githubCallbackMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(githubCallbackUri) {
        val callback = githubCallbackUri ?: return@LaunchedEffect
        if (callback.scheme != "geivia" || callback.host != "github" || callback.path != "/callback") return@LaunchedEffect
        val pendingAuthorization = githubTokenStore.readPendingAuthorization()
        val expectedState = pendingAuthorization?.optString("state")
        val verifier = pendingAuthorization?.optString("verifier")
        val receivedState = callback.getQueryParameter("state")
        val code = callback.getQueryParameter("code")
        val startedAt = pendingAuthorization?.optLong("startedAt") ?: 0L
        val callbackError = callback.getQueryParameter("error")
        try {
            require(expectedState != null && receivedState == expectedState) { "A validação de segurança da autorização falhou. Inicie a conexão novamente." }
            require(startedAt > 0L && System.currentTimeMillis() - startedAt in 0..600_000L) { "A autorização expirou. Inicie a conexão novamente." }
            require(callbackError == null) { "O GitHub cancelou ou recusou a autorização." }
            require(!code.isNullOrBlank() && !verifier.isNullOrBlank()) { "O retorno do GitHub está incompleto. Inicie a conexão novamente." }
            val clientId = prefs.getString("github_client_id", "").orEmpty()
            val callbackUrl = prefs.getString("github_callback_url", "").orEmpty()
            val exchangeUrl = prefs.getString("github_exchange_url", "").orEmpty()
            val tokenResponse = withContext(Dispatchers.IO) {
                GitHubAuthClient.exchangeCode(exchangeUrl, clientId, callbackUrl, code, verifier)
            }
            githubTokenStore.save(tokenResponse)
            githubConnected = true
            githubCallbackMessage = "Conta GitHub conectada. Carregando seus repositórios."
        } catch (error: Exception) {
            githubCallbackMessage = error.message ?: "Não foi possível concluir a autorização GitHub."
        } finally {
            githubTokenStore.clearPendingAuthorization()
        }
        page = "github"
    }

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
            if (showBrandSplash) BrandSplashScreen() else when (page) {
                "device_access" -> DeviceAccessSetupScreen(onCompleted = {
                    prefs.edit().putBoolean("device_access_completed", true).putLong("device_access_completed_at", System.currentTimeMillis()).apply()
                    page = "environment_setup"
                })
                "welcome" -> WelcomeScreen(hasCheckpoint = prefs.contains("workflow_checkpoint")) {
                    page = if (!prefs.getBoolean("device_access_completed", false)) "device_access"
                        else if (!prefs.getBoolean("environment_setup_completed", false)) "environment_setup"
                        else if (prefs.getBoolean("preflight_completed", false)) savedCheckpoint else "preflight"
                    if (page == "questions") index = prefs.getInt("workflow_question_index", 0).coerceAtLeast(0)
                }
                "environment_setup" -> GeiviaEnvironmentSetupScreen(
                    onCompleted = {
                        prefs.edit()
                            .putBoolean("environment_setup_completed", true)
                            .putLong("environment_setup_completed_at", System.currentTimeMillis())
                            .apply()
                        page = "preflight"
                    }
                )
                "preflight" -> InitialEnvironmentScreen(
                    googleAccount = preflightGoogleAccount,
                    googleEmail = preflightGoogleEmail,
                    cloudProject = preflightCloudProject,
                    cloudProjectId = preflightCloudProjectId,
                    firebaseProjectId = preflightFirebaseProjectId,
                    firebaseProject = preflightFirebaseProject,
                    githubRepo = preflightGithubRepo,
                    projectId = preflightProjectId,
                    onGoogleAccountChange = { preflightGoogleAccount = it },
                    onGoogleEmailChange = { preflightGoogleEmail = it },
                    onCloudProjectChange = { preflightCloudProject = it },
                    onCloudProjectIdChange = { preflightCloudProjectId = it },
                    onFirebaseProjectIdChange = { preflightFirebaseProjectId = it },
                    onFirebaseProjectChange = { preflightFirebaseProject = it },
                    onGithubRepoChange = { preflightGithubRepo = it },
                    onProjectIdChange = { preflightProjectId = it },
                    onBack = { page = "welcome" },
                    onContinue = {
                        prefs.edit()
                            .putString("preflight_google_account", preflightGoogleAccount)
                            .putString("preflight_google_email", preflightGoogleEmail.trim())
                            .putString("preflight_cloud_project_id", preflightCloudProjectId.trim())
                            .putString("preflight_firebase_project_id", preflightFirebaseProjectId.trim())
                            .putString("preflight_cloud_project", preflightCloudProject)
                            .putString("preflight_firebase_project", preflightFirebaseProject)
                            .putString("preflight_github_repo", preflightGithubRepo)
                            .putString("preflight_project_id", preflightProjectId.trim())
                            .putString("selected_project_id", preflightCloudProjectId.trim().ifBlank { preflightFirebaseProjectId.trim() }.ifBlank { preflightProjectId.trim() })
                            .putBoolean("preflight_completed", true)
                            .putLong("preflight_completed_at", System.currentTimeMillis())
                            .apply()
                        val shouldLoginNow = preflightGoogleAccount == "yes"
                        prefs.edit().putBoolean("preflight_auto_cloud_login", shouldLoginNow).putBoolean("preflight_return_to_workspace", shouldLoginNow).apply()
                        page = if (shouldLoginNow) "google_access" else if (prefs.contains("workflow_checkpoint")) savedCheckpoint else "workspace_setup"
                    }
                )
                "workspace_setup" -> WorkspaceSetupScreen(
                    initialName = workspaceName,
                    workspaceId = workspaceId,
                    onBack = { page = "welcome" },
                    onContinue = { name ->
                        workspaceName = name.trim()
                        prefs.edit().putString("workspace_name", workspaceName).apply()
                        // Cloud access is optional; let users plan offline first.
                        page = "questions"
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
                "visual_editor" -> VisualEditorScreen(
                    context = context,
                    onBack = { page = "dashboard" },
                    onFirebase = { returnToEditorAfterTool = true; page = "firebase_admin" },
                    onGoogle = { returnToEditorAfterTool = true; page = "google_access" },
                    onAccounts = { returnToEditorAfterTool = true; page = "accounts" },
                    onTools = { returnToEditorAfterTool = true; page = "toolchain_updates" },
                    onDiagnostics = { returnToEditorAfterTool = true; page = "diagnostics" },
                    onTerminal = { returnToEditorAfterTool = true; page = "terminal" },
                    onProjects = { returnToEditorAfterTool = true; page = "project_operations" }
                )
                "accounts" -> AccountsConnectionsScreen(
                    googleReady = prefs.getBoolean("google_cloud_cli_ready", false),
                    githubConnected = githubConnected,
                    onGoogle = { page = "google_access" },
                    onGitHub = { page = "github" },
                    onBack = { page = if (returnToEditorAfterTool) "visual_editor" else "dashboard"; returnToEditorAfterTool = false }
                )
                "github" -> GitHubScreen(
                    connected = githubConnected,
                    callbackMessage = githubCallbackMessage,
                    onBack = { page = if (returnToEditorAfterTool) "visual_editor" else "dashboard"; returnToEditorAfterTool = false },
                    onConnectedChanged = { githubConnected = it }
                )
                "google_access" -> GoogleCloudAccessScreen(
                    accountEmail = prefs.getString("preflight_google_email", "").orEmpty(),
                    projectId = prefs.getString("preflight_cloud_project_id", "").orEmpty().ifBlank { prefs.getString("preflight_firebase_project_id", "").orEmpty() }.ifBlank { prefs.getString("preflight_project_id", "").orEmpty() },
                    autoStartLogin = prefs.getBoolean("preflight_auto_cloud_login", false),
                    onAutoStartConsumed = { prefs.edit().putBoolean("preflight_auto_cloud_login", false).apply() },
                    onBack = {
                        page = if (returnToEditorAfterTool) {
                            returnToEditorAfterTool = false
                            "visual_editor"
                        } else if (prefs.getBoolean("preflight_return_to_workspace", false)) {
                            prefs.edit().putBoolean("preflight_return_to_workspace", false).apply()
                            "workspace_setup"
                        } else "dashboard"
                    }
                )
                "terminal" -> Column(
                    Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { page = "visual_editor"; returnToEditorAfterTool = false }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Voltar ao editor")
                        }
                        Column {
                            Text("Terminal GEIVIA", fontWeight = FontWeight.Bold)
                            Text("Terminal local e ambiente Debian/PRoot", fontSize = 11.sp, color = Muted)
                        }
                    }
                    TerminalPanel()
                }
                "toolchain_updates" -> GeiviaToolchainUpdatesScreen(onBack = { page = if (returnToEditorAfterTool) "visual_editor" else "dashboard"; returnToEditorAfterTool = false })
                "diagnostics" -> GeiviaDiagnosticsScreen(onBack = { page = if (returnToEditorAfterTool) "visual_editor" else "dashboard"; returnToEditorAfterTool = false })
                "project_operations" -> ProjectOperationsScreen(onBack = { page = if (returnToEditorAfterTool) "visual_editor" else "dashboard"; returnToEditorAfterTool = false })
                "project_preview" -> ProjectPreviewScreen(prefs.getString("imported_project_dir", "").orEmpty()) { page = "project_operations" }
                "firebase_admin" -> FirebaseAdminScreen(prefs.getString("selected_project_id", preflightProjectId).orEmpty()) { page = if (returnToEditorAfterTool) "visual_editor" else "project_operations"; returnToEditorAfterTool = false }
                "legacy_dashboard" -> DashboardScreen(answers.toMap(), costMode,
                    onOperations = { page = "project_operations" },
                    onPreview = { page = "project_preview" },
                    onFirebaseAdmin = { page = "firebase_admin" },
                    onAccounts = { page = "accounts" },
                    onToolchainUpdates = { page = "toolchain_updates" },
                    onDiagnostics = { page = "diagnostics" },
                    onRestart = {
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
                    })
                else -> StudioDashboardScreen(
                    context = context,
                    onNewProject = {
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
                    },
                    onProjects = { page = "project_operations" },
                    onEditor = { page = "visual_editor" },
                    onPreview = { page = "project_preview" },
                    onFirebase = { page = "firebase_admin" },
                    onIntegrations = { page = "accounts" },
                    onTools = { page = "toolchain_updates" },
                    onDiagnostics = { page = "diagnostics" },
                    onAccounts = { page = "accounts" },
                    onLegacyDashboard = { page = "legacy_dashboard" }
                )
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
        Text("Conte o que deseja criar. Planeje seu projeto sem conectar uma conta Google. O acesso ao Cloud é opcional e só será necessário quando você decidir configurar recursos na nuvem.", color = Muted, fontSize = 15.sp, lineHeight = 23.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text(if (hasCheckpoint) "Continuar de onde parei" else "Começar", Modifier.padding(vertical = 7.dp), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(12.dp))
        Text("Seu ambiente GEIVIA é identificado neste aparelho. A conexão com o Google só acontece mediante configuração e autorização explícitas.", color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun InitialEnvironmentScreen(
    googleAccount: String,
    googleEmail: String,
    cloudProject: String,
    cloudProjectId: String,
    firebaseProjectId: String,
    firebaseProject: String,
    githubRepo: String,
    projectId: String,
    onGoogleAccountChange: (String) -> Unit,
    onGoogleEmailChange: (String) -> Unit,
    onCloudProjectChange: (String) -> Unit,
    onCloudProjectIdChange: (String) -> Unit,
    onFirebaseProjectIdChange: (String) -> Unit,
    onFirebaseProjectChange: (String) -> Unit,
    onGithubRepoChange: (String) -> Unit,
    onProjectIdChange: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val complete = googleAccount.isNotBlank() && cloudProject.isNotBlank() &&
        firebaseProject.isNotBlank() && githubRepo.isNotBlank() &&
        (googleAccount != "yes" || googleEmail.trim().matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) &&
        (cloudProject != "yes" || cloudProjectId.trim().matches(Regex("^[a-z][a-z0-9-]{4,28}[a-z0-9]$"))) &&
        (firebaseProject != "yes" || firebaseProjectId.trim().matches(Regex("^[a-z][a-z0-9-]{4,28}[a-z0-9]$")))
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Text("GEIVIA • CONFIGURAÇÃO INICIAL", color = DeepBlue, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
        LinearProgressIndicator(progress = 0.12f, modifier = Modifier.fillMaxWidth().height(5.dp))
        Text("Antes de começar, vamos aproveitar o que você já tem.", color = Ink, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
        Text(
            "Responda estas perguntas uma vez. O GEIVIA usará as respostas para evitar etapas desnecessárias e não vai criar projetos, conectar contas ou alterar serviços nesta tela.",
            color = Muted, fontSize = 14.sp, lineHeight = 21.sp
        )
        InitialStatusQuestion("Você já tem uma conta Google?", googleAccount, onGoogleAccountChange,
            "Ela é usada para acessar o Google Cloud e o Firebase.")
        if (googleAccount == "yes") OutlinedTextField(
            value = googleEmail, onValueChange = onGoogleEmailChange, modifier = Modifier.fillMaxWidth(),
            label = { Text("E-mail da conta Google") }, placeholder = { Text("voce@gmail.com") }, singleLine = true,
            supportingText = { Text("O GEIVIA usará este e-mail para iniciar o login oficial no terminal. A senha nunca será solicitada.") }
        )
        InitialStatusQuestion("Você já tem um projeto no Google Cloud?", cloudProject, onCloudProjectChange,
            "Se já tiver, vamos tentar localizar e reutilizar esse projeto.")
        if (cloudProject == "yes") OutlinedTextField(
            value = cloudProjectId, onValueChange = onCloudProjectIdChange, modifier = Modifier.fillMaxWidth(),
            label = { Text("Project ID do Google Cloud") }, placeholder = { Text("meu-projeto-123") }, singleLine = true
        )
        InitialStatusQuestion("Você já tem um projeto no Firebase?", firebaseProject, onFirebaseProjectChange,
            "Um projeto Firebase também é um projeto Google Cloud associado ao Firebase.")
        if (firebaseProject == "yes") OutlinedTextField(
            value = firebaseProjectId, onValueChange = onFirebaseProjectIdChange, modifier = Modifier.fillMaxWidth(),
            label = { Text("Project ID do Firebase") }, placeholder = { Text("meu-projeto-123") }, singleLine = true
        )
        InitialStatusQuestion("Você já tem um repositório GitHub para esse projeto?", githubRepo, onGithubRepoChange,
            "Se já existir, o GEIVIA poderá trabalhar com ele após a autorização.")
        OutlinedTextField(
            value = projectId,
            onValueChange = onProjectIdChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Project ID existente (opcional)") },
            placeholder = { Text("ex.: meu-projeto-123") },
            singleLine = true,
            supportingText = { Text("Se souber o ID do projeto Cloud/Firebase, informe aqui. Ele será apenas uma referência até a conta ser autorizada.") }
        )
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF4FA)), shape = RoundedCornerShape(13.dp)) {
            Text(
                "Privacidade e segurança: o GEIVIA não consegue confirmar contas ou projetos sem sua autorização. Quando uma etapa precisar de arquivos, conta ou acesso do Android, ele explicará o motivo e pedirá somente o acesso necessário. Não existe uma permissão comum que conceda acesso root ou controle irrestrito do sistema.",
                Modifier.padding(14.dp), color = Ink, fontSize = 12.sp, lineHeight = 18.sp
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Voltar") }
            Button(onClick = onContinue, enabled = complete, modifier = Modifier.weight(1f)) { Text("Continuar") }
        }
    }
}

@Composable
private fun InitialStatusQuestion(
    title: String,
    selected: String,
    onSelect: (String) -> Unit,
    help: String
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(13.dp)) {
            Text(title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(help, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf("yes" to "Sim", "no" to "Não", "unsure" to "Não sei").forEach { (value, label) ->
                    Row(
                        Modifier.weight(1f).clickable { onSelect(value) }.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected == value, onClick = { onSelect(value) })
                        Text(label, color = Ink, fontSize = 11.sp)
                    }
                }
            }
        }
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
private fun DashboardScreen(answers: Map<String, String>, costMode: String, onOperations: () -> Unit, onPreview: () -> Unit, onFirebaseAdmin: () -> Unit, onAccounts: () -> Unit, onToolchainUpdates: () -> Unit, onDiagnostics: () -> Unit, onRestart: () -> Unit) {
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
        val workflowStorageError = workflowStore.healthError()
        val workflowBackupLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument(AndroidAccessUseCase.EXPORT_HISTORY_BACKUP.mimeTypes.first())
        ) { uri ->
            if (uri != null) {
                runCatching {
                    val backup = workflowStore.exportBackupJson()
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(backup.toByteArray(Charsets.UTF_8))
                    } ?: error("Não foi possível gravar o arquivo de backup.")
                }.onSuccess {
                    Toast.makeText(context, "Backup do histórico salvo. Guarde o arquivo em local seguro.", Toast.LENGTH_LONG).show()
                }.onFailure { error ->
                    Toast.makeText(context, "Falha ao exportar backup: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        val workflowBackupValidationLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                val result = runCatching {
                    val raw = context.contentResolver.openInputStream(uri)?.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var total = 0
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > 5 * 1024 * 1024) {
                                throw IllegalStateException("Arquivo excede o limite de 5 MB.")
                            }
                            output.write(buffer, 0, count)
                        }
                        output.toString(Charsets.UTF_8.name())
                    } ?: throw IllegalStateException("Não foi possível abrir o arquivo selecionado.")
                    WorkflowBackupValidator.validate(raw)
                }.getOrElse { error ->
                    WorkflowBackupValidationResult(false, error.message ?: "Não foi possível verificar o arquivo.")
                }
                val message = if (result.valid) {
                    "Backup íntegro. Nenhum dado foi restaurado ou alterado."
                } else {
                    "Backup rejeitado: ${result.message} Nenhum dado foi alterado."
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
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
            "firebase_deploy" to "Deploy dos alvos do projeto",
            "storage" to "Cloud Storage"
        )
        val workflowRecords = if (workflowStorageError == null) workflowStore.allForProject(chosenProjectId).associateBy { it.stepId } else emptyMap()
        val workflowDisplayCatalog = workflowCatalog + workflowRecords.keys
            .filterNot { stepId -> workflowCatalog.any { it.first == stepId } }
            .map { stepId -> stepId to stepId.replace('_', ' ').replaceFirstChar { it.uppercase() } }
        val workflowHistory = if (workflowStorageError == null) workflowStore.historyForProject(chosenProjectId).take(30) else emptyList()
        Spacer(Modifier.height(16.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(15.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("ACOMPANHAMENTO DAS ETAPAS", color = DeepBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    AndroidAccessPolicy.guidance(AndroidAccessUseCase.EXPORT_HISTORY_BACKUP) + " " + AndroidAccessPolicy.guidance(AndroidAccessUseCase.VALIDATE_HISTORY_BACKUP) + " Não precisa liberar acesso geral ao armazenamento.",
                    color = Muted, fontSize = 11.sp, lineHeight = 16.sp
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        workflowBackupLauncher.launch("GEIVIA-historico-${System.currentTimeMillis()}.json")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Exportar backup do histórico")
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { workflowBackupValidationLauncher.launch(AndroidAccessUseCase.VALIDATE_HISTORY_BACKUP.mimeTypes.toTypedArray()) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Verificar integridade de backup")
                }
                Text("A verificação confere formato, versão e hashes SHA-256. Não restaura dados, não altera o histórico e não libera operações.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
                if (workflowStorageError != null) {
                    Spacer(Modifier.height(8.dp))
                    Text("OPERAÇÕES BLOQUEADAS PARA EVITAR DUPLICIDADE", color = Color(0xFF9A5B13), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text(workflowStorageError, color = Color(0xFF9A5B13), fontSize = 11.sp, lineHeight = 16.sp)
                    Text("O histórico não foi apagado nem reiniciado. Abra a configuração do Cloud Shell para ver as orientações de recuperação; nenhuma nova operação será iniciada.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
                }
                Spacer(Modifier.height(5.dp))
                Text(if (chosenProjectId.isBlank()) "Selecione um projeto para iniciar o acompanhamento." else "Projeto: $chosenProjectId", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                workflowDisplayCatalog.forEach { (stepId, label) ->
                    val record = workflowRecords[stepId]
                    val state = record?.state ?: WorkflowStepState.NOT_STARTED
                    val stateLabel = if (stepId == "storage" && costMode == "zero") "Bloqueada no modo custo zero" else when (state) {
                        WorkflowStepState.NOT_STARTED -> "Não iniciada"
                        WorkflowStepState.IN_PROGRESS -> "Aguardando diagnóstico"
                        WorkflowStepState.WAITING_CONFIRMATION -> if (stepId == "firebase_deploy") "Comando preparado — aguardando execução manual" else "Aguardando confirmação"
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
                Divider(color = Color(0xFFE7EEF2))
                Spacer(Modifier.height(8.dp))
                Text("HISTÓRICO DETALHADO • ÚLTIMAS 30 AÇÕES", color = DeepBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                if (chosenProjectId.isBlank()) {
                    Text("Selecione um Project ID para consultar o histórico.", color = Muted, fontSize = 10.sp)
                } else if (workflowHistory.isEmpty()) {
                    Text("Ainda não há operações registradas para este projeto.", color = Muted, fontSize = 10.sp)
                } else {
                    workflowHistory.forEach { event ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(event.stepId.replace('_', ' ').uppercase(), color = Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(android.text.format.DateFormat.format("dd/MM/yyyy HH:mm:ss", event.updatedAt).toString(), color = Muted, fontSize = 9.sp)
                            }
                            Text(event.state, color = when {
                                event.state.contains("FAIL", true) || event.state.contains("UNKNOWN", true) -> Color(0xFFB05A18)
                                event.state.contains("SUCCESS", true) || event.state.contains("COMPLETED", true) -> Color(0xFF238653)
                                else -> Muted
                            }, fontSize = 10.sp)
                            if (event.message.isNotBlank()) Text(event.message, color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
                            if (event.operationId.isNotBlank()) Text("Operação: " + event.operationId, color = Muted, fontSize = 9.sp)
                            if (event.evidence.isNotBlank()) Text("Evidência: " + event.evidence, color = Muted, fontSize = 9.sp, lineHeight = 13.sp)
                        }
                        Divider(color = Color(0xFFE7EEF2))
                    }
                }
                Text("Histórico isolado por Project ID. Operações pendentes ou desconhecidas não são repetidas automaticamente.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
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
        Button(onClick = onOperations, modifier = Modifier.fillMaxWidth()) { Text("Importar projeto ZIP e fazer deploy") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onPreview, modifier = Modifier.fillMaxWidth()) { Text("Prévia do projeto importado") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onFirebaseAdmin, modifier = Modifier.fillMaxWidth()) { Text("Administrar Firebase e baixar JSON") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onAccounts, modifier = Modifier.fillMaxWidth()) { Text("Conectar minhas contas") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onToolchainUpdates, modifier = Modifier.fillMaxWidth()) { Text("Gerenciar atualizações das ferramentas") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onDiagnostics, modifier = Modifier.fillMaxWidth()) { Text("Diagnóstico e solução de problemas") }
        Spacer(Modifier.height(8.dp))
        XiaomiCompatibilityCard()
        Spacer(Modifier.height(8.dp))
        TerminalPanel()
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
    val preflightProjectHint = prefs.getString("preflight_project_id", "").orEmpty().trim()
    val preflightHasGoogleAccount = prefs.getString("preflight_google_account", "") == "yes"
    val preflightHasFirebaseProject = prefs.getString("preflight_firebase_project", "") == "yes"
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
    val workflowStorageError = workflowProgressStore.healthError()
    if (workflowStorageError != null) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("GEIVIA", color = DeepBlue, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Spacer(Modifier.height(18.dp))
            Text("Histórico de operações protegido", color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(workflowStorageError, color = Color(0xFF9A5B13), fontSize = 14.sp, lineHeight = 21.sp)
            Spacer(Modifier.height(12.dp))
            Text("Nenhum progresso foi apagado ou reiniciado. Para evitar uma criação duplicada ou uma alteração repetida na nuvem, todas as operações ficam bloqueadas até a recuperação segura do histórico.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(22.dp))
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Voltar ao painel") }
        }
        return
    }
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
                val raw = context.contentResolver.openInputStream(uri)?.use { readUtf8Limited(it) }
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
                val raw = context.contentResolver.openInputStream(uri)?.use { readUtf8Limited(it) }
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