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

@Composable
internal fun ProjectOperationsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { LinuxRuntimeController(context) }
    var projectDir by rememberSaveable { mutableStateOf("") }
    var projectId by rememberSaveable { mutableStateOf(context.getSharedPreferences("geivia_onboarding", 0).getString("selected_project_id", "").orEmpty()) }
    var output by rememberSaveable { mutableStateOf("Importe um ZIP do seu projeto para começar. O GEIVIA extrairá os arquivos no Debian privado e executará os comandos no próprio aparelho.") }
    var githubRepo by rememberSaveable { mutableStateOf("") }
    var githubBranch by rememberSaveable { mutableStateOf("main") }
    var busy by rememberSaveable { mutableStateOf(false) }
    var showDeployConfirm by remember { mutableStateOf(false) }
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
        Text("O ZIP é extraído no Debian/PRoot. Os comandos são executados pelo GEIVIA, sem copiar comandos para outro terminal.", color = Color(0xFF587083), fontSize = 13.sp)
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Voltar ao painel") }
        Button(onClick = { zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Selecionar ZIP do projeto") }
        if (projectDir.isNotBlank()) Text("Pasta importada: $projectDir", color = Color(0xFF18794E), fontSize = 11.sp)
        OutlinedTextField(value = projectId, onValueChange = { projectId = it.trim() }, modifier = Modifier.fillMaxWidth(), label = { Text("Firebase Project ID") }, singleLine = true)
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F7FB))) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Publicação Firebase", color = Color(0xFF102B43))
                Text("Executa Firebase CLI no projeto importado e publica os alvos declarados no firebase.json.", color = Color(0xFF587083), fontSize = 12.sp)
                Button(onClick = { showDeployConfirm = true }, enabled = !busy && projectDir.isNotBlank() && projectId.matches(Regex("^[a-z][a-z0-9-]{4,28}[a-z0-9]$")), modifier = Modifier.fillMaxWidth()) { Text("Publicar Hosting, Rules, Storage e Functions") }
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

    if (showDeployConfirm) AlertDialog(
        onDismissRequest = { showDeployConfirm = false },
        title = { Text("Confirmar deploy Firebase") },
        text = { Text("Projeto: $projectId\nPasta: $projectDir\n\nO deploy pode substituir conteúdo publicado e alterar regras de segurança. Revise os arquivos antes de continuar.") },
        confirmButton = { TextButton(onClick = {
            showDeployConfirm = false
            busy = true
            output = "Verificando projeto e autenticação Firebase…\n"
            scope.launch {
                val safeDir = projectDir.replace("'", "'\\''")
                val safeId = projectId.replace("'", "")
                val command = """
                    set -euo pipefail
                    PROJECT_DIR='${safeDir}'
                    PROJECT_ID='${safeId}'
                    test -d "§PROJECT_DIR" || { echo "Pasta do projeto não encontrada."; exit 20; }
                    test -f "§PROJECT_DIR/firebase.json" || { echo "firebase.json não encontrado no ZIP."; exit 21; }
                    command -v firebase >/dev/null || { echo "Firebase CLI não está instalado no Debian do GEIVIA."; exit 22; }
                    command -v jq >/dev/null || { echo "jq não está instalado no Debian do GEIVIA."; exit 24; }
                    cd "§PROJECT_DIR"
                    firebase projects:list >/dev/null || { echo "Firebase CLI sem sessão autenticada. Conecte a conta Google no GEIVIA e repita."; exit 23; }
                    echo "Validando configuração Firebase…"
                    TARGETS=()
                    jq -e '.hosting != null' firebase.json >/dev/null && TARGETS+=("hosting")
                    jq -e '.firestore.rules != null' firebase.json >/dev/null && TARGETS+=("firestore:rules")
                    jq -e '.firestore.indexes != null' firebase.json >/dev/null && TARGETS+=("firestore:indexes")
                    jq -e '.storage != null' firebase.json >/dev/null && TARGETS+=("storage")
                    jq -e '.functions != null' firebase.json >/dev/null && TARGETS+=("functions")
                    if [ "§{#TARGETS[@]}" -eq 0 ]; then echo "Nenhum alvo compatível foi encontrado no firebase.json."; exit 25; fi
                    TARGETS_CSV="$(IFS=,; echo "§{TARGETS[*]}")"
                    echo "Alvos identificados: §TARGETS_CSV"
                    firebase deploy --only "§TARGETS_CSV" --project "§PROJECT_ID" --non-interactive
                    echo "GEIVIA_DEPLOY_SUCCESS project=§PROJECT_ID targets=§TARGETS_CSV"
                """.trimIndent().replace("§", "$")
                runCatching {
                    withContext(Dispatchers.IO) {
                        controller.execute(listOf("login", "debian", "--", "/bin/bash", "-lc", command), 60 * 60 * 1000L) { chunk ->
                            scope.launch { output = (output + chunk).takeLast(120000) }
                        }
                    }
                }.onSuccess { code ->
                    output += "\nProcesso finalizado com código $code. " + if (code == 0) "Consulte a saída de cada alvo publicado." else "A publicação não foi confirmada."
                }.onFailure { output += "\nFalha ao executar: " + (it.message ?: "erro desconhecido") }
                busy = false
            }
        }) { Text("Confirmar e executar") } },
        dismissButton = { TextButton(onClick = { showDeployConfirm = false }) { Text("Cancelar") } }
    )
}
