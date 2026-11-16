package com.geivia.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
internal fun FirebaseAdminScreen(projectId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { LinuxRuntimeController(context) }
    val workflowStore = remember { WorkflowProgressStore(context.getSharedPreferences("geivia_onboarding", android.content.Context.MODE_PRIVATE)) }
    var log by remember { mutableStateOf("Selecione uma operação. O GEIVIA consultará o projeto usando a sessão Google autenticada no Debian.") }
    var busy by remember { mutableStateOf(false) }
    var pendingFile by remember { mutableStateOf<File?>(null) }
    var confirmAction by remember { mutableStateOf<String?>(null) }
    var firestoreRegion by rememberSaveable { mutableStateOf("southamerica-east1") }
    var workflowRefresh by rememberSaveable { mutableStateOf(0) }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val file = pendingFile
        if (uri != null && file?.isFile == true) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } }
                ?: error("Não foi possível gravar o arquivo escolhido.")
            log += "\nArquivo JSON salvo no local escolhido."
        }.onFailure { log += "\nFalha ao salvar JSON: ${it.message}" }
        pendingFile = null
    }

    fun runOperation(label: String, shell: String, timeout: Long = 180_000L, export: File? = null) {
        if (busy) return
        val operationId = UUID.randomUUID().toString()
        val stepId = "firebase_admin_" + label.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(70)
        val historyError = workflowStore.healthError()
        if (historyError != null) {
            log = "Operação bloqueada: $historyError"
            return
        }
        runCatching {
            workflowStore.appendAuditEvent(projectId, stepId, "STARTED", operationId, label, "Comando preparado: " + sanitizeTerminalLog(shell).take(8000))
        }.onFailure {
            log = "Operação bloqueada: não foi possível registrar o histórico. ${it.message}"
            return
        }
        busy = true
        log = "GEIVIA • $label\nProject ID: $projectId\nOperação: $operationId\n"
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    controller.execute(listOf("login", "debian", "--", "/bin/bash", "-lc", shell), timeout) { chunk ->
                        scope.launch { log = (log + chunk).takeLast(100000) }
                    }
                }
            }.onSuccess { code ->
                log += "\n\nCódigo de saída: $code"
                runCatching {
                    workflowStore.appendAuditEvent(
                        projectId, stepId, if (code == 0) "SUCCEEDED" else "FAILED", operationId,
                        "$label • código de saída $code",
                        "Project ID: $projectId; exitCode=$code; " + if (code == 0) "resultado do comando concluído" else "verifique o log antes de tentar novamente"
                    )
                }.onFailure { log += "\nFalha ao registrar resultado no histórico: ${it.message}" }
                if (code == 0 && export?.isFile == true) {
                    pendingFile = export
                    saveFile.launch(export.name)
                } else if (code == 0 && export != null) log += "\nO comando terminou, mas o arquivo JSON não foi criado."
            }.onFailure {
                log += "\nFalha: ${it.message}"
                runCatching {
                    workflowStore.appendAuditEvent(projectId, stepId, "FAILED", operationId, "$label • ${it.message}", "Execução interrompida; verificar estado remoto antes de repetir.")
                }.onFailure { log += "\nFalha ao registrar histórico: ${it.message}" }
            }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("FIREBASE • ADMINISTRAÇÃO", style = MaterialTheme.typography.titleLarge)
        Text("Projeto: $projectId", style = MaterialTheme.typography.labelMedium)
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Voltar") }
        listOf("Criar banco Firestore", "Ativar Firebase Authentication", "Ativar APIs necessárias").forEach { actionTitle ->
            val stepId = "firebase_admin_" + actionTitle.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(70)
            val record = remember(projectId, stepId, workflowRefresh) { workflowStore.get(projectId, stepId) }
            if (record.state == WorkflowStepState.WAITING_CONFIRMATION) {
                Card {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("$actionTitle • aguardando execução manual", style = MaterialTheme.typography.labelMedium)
                        Text("Após executar o comando no Google Cloud Shell, registre aqui. Isso é uma declaração sua, não uma confirmação automática do Google.", fontSize = 11.sp)
                        OutlinedButton(onClick = {
                            runCatching {
                                val operationId = UUID.randomUUID().toString()
                                workflowStore.markManualExecutionStarted(projectId, stepId, operationId, "Usuário informou que executou o comando no Google Cloud Shell.")
                                workflowStore.appendAuditEvent(projectId, stepId, "IN_PROGRESS", operationId, "Execução manual declarada; aguardando diagnóstico atualizado.", "Declaração do usuário; ainda não é evidência do estado remoto.")
                                workflowRefresh++
                                log = "$actionTitle registrada como executada manualmente. Aguardando diagnóstico atualizado; não marcada como concluída."
                            }.onFailure { log = "Não foi possível registrar a execução: ${it.message}" }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Executei no Cloud Shell") }
                    }
                }
            } else if (record.state == WorkflowStepState.IN_PROGRESS) {
                Text("$actionTitle: execução declarada; aguardando diagnóstico. Ainda não confirmada.", fontSize = 11.sp, color = androidx.compose.ui.graphics.Color(0xFF9A641B))
            }
        }
        Text("CONSULTAR E BAIXAR", style = MaterialTheme.typography.labelLarge)
        Button(enabled = !busy, onClick = {
            val f = File(controller.homeDir, "geivia-exports/firestore-databases.json")
            runOperation("Listando bancos Firestore", "mkdir -p '${f.parent}'; gcloud firestore databases list --project='$projectId' --format=json > '${f.absolutePath}' && cat '${f.absolutePath}'", export = f)
        }, modifier = Modifier.fillMaxWidth()) { Text("Listar bancos Firestore (JSON)") }
        Button(enabled = !busy, onClick = {
            val f = File(controller.homeDir, "geivia-exports/enabled-apis.json")
            runOperation("Listando APIs ativadas", "mkdir -p '${f.parent}'; gcloud services list --enabled --project='$projectId' --format=json > '${f.absolutePath}' && cat '${f.absolutePath}'", export = f)
        }, modifier = Modifier.fillMaxWidth()) { Text("Listar APIs ativadas (JSON)") }
        Button(enabled = !busy, onClick = {
            val f = File(controller.homeDir, "geivia-exports/firestore-rules.json")
            val script = """
                set -euo pipefail
                mkdir -p '${f.parent}'
                TOKEN="${'$'}(gcloud auth print-access-token)"
                curl -fsS -H "Authorization: Bearer ${'$'}TOKEN" "https://firebaserules.googleapis.com/v1/projects/$projectId/releases/cloud.firestore" > release.json
                NAME="${'$'}(jq -r '.rulesetName // empty' release.json)"
                test -n "${'$'}NAME" || { echo "Nenhuma release de regras Firestore foi encontrada."; exit 4; }
                curl -fsS -H "Authorization: Bearer ${'$'}TOKEN" "https://firebaserules.googleapis.com/v1/${'$'}NAME" > '${f.absolutePath}'
                cat '${f.absolutePath}'
            """.trimIndent()
            runOperation("Baixando regras Firestore ativas", script, export = f)
        }, modifier = Modifier.fillMaxWidth()) { Text("Baixar regras Firestore atuais (JSON)") }

        HorizontalDivider()
        Text("CRIAR E CONFIGURAR", style = MaterialTheme.typography.labelLarge)
        Text("As ações abaixo podem criar recursos e gerar cobrança. O GEIVIA consulta o estado atual antes de agir.", fontSize = 12.sp)
        OutlinedTextField(value = firestoreRegion, onValueChange = { firestoreRegion = it.trim() }, modifier = Modifier.fillMaxWidth(), label = { Text("Região do Firestore (ex.: southamerica-east1)") }, singleLine = true)
        OutlinedButton(enabled = !busy && firestoreRegion.matches(Regex("^[a-z0-9-]+$")), onClick = { confirmAction = "database" }, modifier = Modifier.fillMaxWidth()) { Text("Criar banco Firestore") }
        OutlinedButton(enabled = !busy, onClick = { confirmAction = "auth" }, modifier = Modifier.fillMaxWidth()) { Text("Ativar Firebase Authentication") }
        OutlinedButton(enabled = !busy, onClick = { confirmAction = "apis" }, modifier = Modifier.fillMaxWidth()) { Text("Ativar APIs necessárias") }
        Card {
            Column(Modifier.padding(12.dp)) {
                Text("CUSTOS • AVISO ANTES DA AÇÃO", style = MaterialTheme.typography.labelLarge)
                Text("Spark: uso dentro das cotas gratuitas disponíveis. Blaze: cobrança conforme consumo e serviços usados. Criar banco Firestore, publicar Functions ou ativar recursos pode exigir faturamento ou gerar custos indiretos. Ativar uma API, isoladamente, normalmente não cobra; o recurso utilizado pode cobrar. O preço depende de região, tráfego, armazenamento e operações.", fontSize = 12.sp)
                Text("O GEIVIA não promete gratuidade sem verificar plano, cotas e faturamento do projeto.", fontSize = 12.sp)
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("LOG / RESULTADO", style = MaterialTheme.typography.labelLarge)
        Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFF101820))) {
            SelectionContainer { Text(log, Modifier.padding(12.dp), color = androidx.compose.ui.graphics.Color.White, fontSize = 11.sp) }
        }
        OutlinedButton(onClick = {
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("GEIVIA", sanitizeTerminalLog(log)))
        }, modifier = Modifier.fillMaxWidth()) { Text("Copiar log") }
    }

    confirmAction?.let { action ->
        val (title, detail, command) = when (action) {
            "database" -> Triple("Criar banco Firestore", "Cria o banco Firestore Native (default) na região $firestoreRegion. A região não poderá ser alterada depois. O GEIVIA verifica antes se o banco já existe. Pode gerar cobrança conforme uso e plano.", "if gcloud firestore databases describe --database='(default)' --project='$projectId' >/dev/null 2>&1; then echo 'O banco default já existe; nenhuma alteração foi feita.'; else gcloud firestore databases create --database='(default)' --location='$firestoreRegion' --type=firestore-native --project='$projectId'; fi")
            "auth" -> Triple("Ativar Firebase Authentication", "Ativa a API Identity Toolkit. Configurar provedores (Google, e-mail, telefone) é uma etapa separada e pode exigir dados e consentimento do proprietário.", "gcloud services enable identitytoolkit.googleapis.com --project='$projectId'")
            else -> Triple("Ativar APIs básicas Firebase", "Ativa Firebase Management, Firestore, Rules e Hosting. O uso dos serviços pode gerar cobrança.", "gcloud services enable firebase.googleapis.com firebaserules.googleapis.com firestore.googleapis.com firebasehosting.googleapis.com --project='$projectId'")
        }
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text("Revisar operação • $title") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(detail)
                    Text("Project ID: $projectId", style = MaterialTheme.typography.labelMedium)
                    Text("COMANDO PARA REVISÃO", style = MaterialTheme.typography.labelMedium)
                    SelectionContainer {
                        Text(command, fontSize = 11.sp, color = androidx.compose.ui.graphics.Color(0xFF1565C0))
                    }
                    Text(
                        "O GEIVIA não executará esta alteração automaticamente. Copie o comando, revise-o e execute manualmente no Google Cloud Shell. Depois retorne ao GEIVIA e faça um novo diagnóstico para confirmar o resultado.",
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val stepId = "firebase_admin_" + title.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(70)
                    val historyError = workflowStore.healthError()
                    if (historyError != null) {
                        log = "Operação bloqueada: $historyError"
                    } else {
                        runCatching {
                            workflowStore.waitForConfirmation(projectId, stepId, "Comando revisado; aguardando execução manual no Google Cloud Shell.")
                            workflowStore.appendAuditEvent(
                                projectId, stepId, "WAITING_CONFIRMATION", UUID.randomUUID().toString(),
                                title, "Comando preparado para execução manual: " + sanitizeTerminalLog(command).take(8000)
                            )
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("GEIVIA • $title", command))
                            log = "OPERAÇÃO PREPARADA • $title\\nProject ID: $projectId\\n\\nO comando foi copiado. Revise-o e execute manualmente no Google Cloud Shell.\\n\\n$command\\n\\nApós executar, volte ao GEIVIA e rode o diagnóstico do projeto. A etapa continuará pendente até haver evidência atualizada."
                        }.onFailure { log = "Não foi possível preparar a operação: ${it.message}" }
                    }
                    confirmAction = null
                }) { Text("Copiar comando e preparar") }
            },
            dismissButton = { TextButton(onClick = { confirmAction = null }) { Text("Voltar") } }
        )
    }
}
