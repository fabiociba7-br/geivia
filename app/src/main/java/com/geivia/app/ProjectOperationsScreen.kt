package com.geivia.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import org.json.JSONObject
import java.util.UUID

@Composable
internal fun ProjectOperationsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { LinuxRuntimeController(context) }
    val workflowStore = remember { WorkflowProgressStore(context.getSharedPreferences("geivia_onboarding", android.content.Context.MODE_PRIVATE)) }
    var projectDir by rememberSaveable { mutableStateOf("") }
    var projectId by rememberSaveable { mutableStateOf(context.getSharedPreferences("geivia_onboarding", 0).getString("selected_project_id", "").orEmpty()) }
    var output by rememberSaveable { mutableStateOf("Importe um ZIP do seu projeto para começar. O GEIVIA extrairá os arquivos no Debian privado e executará os comandos no próprio aparelho.") }
    var githubRepo by rememberSaveable { mutableStateOf("") }
    var githubBranch by rememberSaveable { mutableStateOf("main") }
    var busy by rememberSaveable { mutableStateOf(false) }
    var showDeployConfirm by remember { mutableStateOf(false) }
    var deployTargets by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val prefs = remember { context.getSharedPreferences("geivia_onboarding", android.content.Context.MODE_PRIVATE) }
    LaunchedEffect(projectId) { prefs.edit().putString("selected_project_id", projectId).apply() }
    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            output = "Importando e verificando o ZIP…"
            runCatching {
                withContext(Dispatchers.IO) {
                    controller.prepareEngine()
                    ProjectZipImporter.import(context, uri, controller.homeDir)
                }
            }.onSuccess {
                projectDir = it.absolutePath
                prefs.edit().putString("imported_project_dir", it.absolutePath).apply()
                output = "Projeto importado em: " + it.absolutePath + "\nArquivos: " + it.walkTopDown().count { f -> f.isFile } +
                    "\nfirebase.json: " + if (File(it, "firebase.json").isFile) "encontrado" else "não encontrado"
            }.onFailure { output = "Falha na importação: " + (it.message ?: "erro desconhecido") }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("CENTRAL DE PROJETOS", color = Color(0xFF087DB5), fontSize = 11.sp)
        Text("Importar, enviar e publicar", color = Color(0xFF102B43), fontSize = 25.sp)
        Text("O ZIP é extraído no Debian/PRoot. O envio ao GitHub ocorre pelo aplicativo; deploy Firebase é preparado para revisão e execução manual no Google Cloud Shell.", color = Color(0xFF587083), fontSize = 13.sp)
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Voltar ao painel") }
        Button(onClick = { zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Selecionar ZIP do projeto") }
        if (projectDir.isNotBlank()) Text("Pasta importada: $projectDir", color = Color(0xFF18794E), fontSize = 11.sp)
        OutlinedTextField(value = projectId, onValueChange = { projectId = it.trim() }, modifier = Modifier.fillMaxWidth(), label = { Text("Firebase Project ID") }, singleLine = true)
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F7FB))) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Publicação Firebase", color = Color(0xFF102B43))
                Text("Prepara o comando de deploy para revisão e execução manual no Google Cloud Shell. O GEIVIA não publica automaticamente.", color = Color(0xFF587083), fontSize = 12.sp)
                Button(onClick = {
    runCatching {
        val configFile = File(projectDir, "firebase.json")
        require(configFile.isFile) { "firebase.json não encontrado na raiz do projeto importado." }
        val config = JSONObject(configFile.readText())
        val targets = mutableListOf<String>()
        if (config.has("hosting") && config.opt("hosting") != JSONObject.NULL) targets += "hosting"
        val firestore = config.optJSONObject("firestore")
        if (firestore?.has("rules") == true) targets += "firestore:rules"
        if (firestore?.has("indexes") == true) targets += "firestore:indexes"
        if (config.has("storage") && config.opt("storage") != JSONObject.NULL) targets += "storage"
        if (config.has("functions") && config.opt("functions") != JSONObject.NULL) targets += "functions"
        require(targets.isNotEmpty()) { "Nenhum alvo Hosting, Firestore, Storage ou Functions foi encontrado no firebase.json." }
        deployTargets = targets.distinct()
        showDeployConfirm = true
    }.onFailure { output = "Não foi possível preparar o deploy: ${it.message}" }
}, enabled = !busy && projectDir.isNotBlank() && projectId.matches(Regex("^[a-z][a-z0-9-]{4,28}[a-z0-9]$")), modifier = Modifier.fillMaxWidth()) { Text("Preparar comando de deploy Firebase") }
                Text("Firebase CLI precisa estar instalado e autenticado no Debian do GEIVIA.", color = Color(0xFF587083), fontSize = 11.sp)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF5E6))) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("GitHub — enviar projeto", color = Color(0xFF102B43))
                OutlinedTextField(value = githubRepo, onValueChange = { githubRepo = it.trim() }, modifier = Modifier.fillMaxWidth(), label = { Text("Repositório: usuário/nome") }, singleLine = true)
                OutlinedTextField(value = githubBranch, onValueChange = { githubBranch = it.trim() }, modifier = Modifier.fillMaxWidth(), label = { Text("Branch") }, singleLine = true)
                Text("A autorização GitHub deve incluir escrita de conteúdo e workflows. Se conectou antes desta atualização, reconecte a conta para aprovar as novas permissões. Arquivos .env, chaves e pastas geradas são excluídos automaticamente.", color = Color(0xFF77501A), fontSize = 12.sp)
                Button(onClick = {
                    val tokenStore = GitHubTokenStore(context)
                    val token = tokenStore.accessToken()
                    val repo = githubRepo
                    val branch = githubBranch
                    if (token == null) {
                        output = "Conecte sua conta GitHub primeiro na tela Conectar minhas contas."
                    } else {
                        busy = true
                        output = "Preparando envio seguro ao GitHub…\n"
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    val clientId = prefs.getString("github_client_id", "").orEmpty()
                                    val callback = prefs.getString("github_callback_url", "").orEmpty()
                                    val exchange = prefs.getString("github_exchange_url", "").orEmpty()
                                    val fresh = GitHubAuthClient.ensureFreshAccessToken(exchange, clientId, callback, tokenStore)
                                        ?: error("Conecte novamente a conta GitHub.")
                                    GitHubApiClient.pushProjectDirectory(fresh, repo, branch, File(projectDir)) { message ->
                                        scope.launch { output = (output + message + "\n").takeLast(100000) }
                                    }
                                }
                            }.onSuccess { url -> output += "\nCommit publicado: $url" }
                                .onFailure { output += "\nFalha no envio: ${it.message}. Se o GitHub indicar falta de escopo, desconecte e conecte novamente a conta para autorizar escrita." }
                            busy = false
                        }
                    }
                }, enabled = !busy && projectDir.isNotBlank() && githubRepo.matches(Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")) && githubBranch.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Enviar projeto e criar commit") }
            }
        }
        if (busy) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { CircularProgressIndicator(); Text("Operação em andamento…") }
        Text("LOG DA OPERAÇÃO", color = Color(0xFF087DB5), fontSize = 11.sp)
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF101820))) {
            SelectionContainer { Text(output, Modifier.padding(12.dp), color = Color(0xFFE2EDF5), fontSize = 11.sp, lineHeight = 16.sp) }
        }
        OutlinedButton(onClick = {
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("GEIVIA log", sanitizeTerminalLog(output)))
        }, modifier = Modifier.fillMaxWidth()) { Text("Copiar log") }
    }

    if (showDeployConfirm) {
        val deployCommand = "firebase deploy --only '${deployTargets.joinToString(",")}' --project '$projectId'"
        AlertDialog(
            onDismissRequest = { showDeployConfirm = false },
            title = { Text("Revisar deploy Firebase") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Projeto: $projectId\nPasta importada no aparelho: $projectDir")
                    Text("Alvos detectados no firebase.json: ${deployTargets.joinToString(", ")}. O deploy pode substituir conteúdo publicado e alterar regras de segurança. Nenhum comando será executado pelo GEIVIA.")
                    Text("ANTES DE EXECUTAR", style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                    Text("1. Envie o mesmo ZIP para o ambiente Google Cloud Shell.\n2. Entre na pasta que contém firebase.json.\n3. Confira os alvos e as regras.\n4. Execute o comando abaixo somente depois da revisão.")
                    SelectionContainer { Text(deployCommand, fontSize = 12.sp, color = Color(0xFF1565C0)) }
                    Text("O deploy pode gerar cobrança e afetar usuários. A confirmação nesta tela apenas prepara e copia o comando; não publica o projeto.", fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val stepId = "firebase_deploy"
                    val historyError = workflowStore.healthError()
                    if (historyError != null) {
                        output = "Deploy bloqueado: $historyError"
                    } else {
                        runCatching {
                            workflowStore.waitForConfirmation(
                                projectId, stepId,
                                "Comando de deploy revisado; aguardando execução manual no Google Cloud Shell e diagnóstico posterior."
                            )
                            workflowStore.appendAuditEvent(
                                projectId, stepId, "WAITING_CONFIRMATION", UUID.randomUUID().toString(),
                                "Deploy Firebase preparado para execução manual",
                                "Pasta importada: $projectDir; comando: $deployCommand; exige upload do mesmo projeto ao Cloud Shell e execução manual."
                            )
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("GEIVIA • Deploy Firebase", deployCommand))
                            output = "DEPLOY PREPARADO\nProject ID: $projectId\n\nComando copiado para a área de transferência:\n$deployCommand\n\nO GEIVIA não executou o deploy. Envie o mesmo ZIP ao Google Cloud Shell, entre na pasta do projeto, revise firebase.json e as regras, então execute o comando manualmente. Depois retorne ao GEIVIA e rode um novo diagnóstico. O histórico permanece aguardando confirmação até haver evidência atualizada."
                        }.onFailure { output = "Não foi possível preparar o deploy: ${it.message}" }
                    }
                    showDeployConfirm = false
                }) { Text("Copiar comando e preparar") }
            },
            dismissButton = { TextButton(onClick = { showDeployConfirm = false }) { Text("Voltar") } }
        )
    }
}
