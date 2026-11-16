package com.geivia.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun FirebaseAdminScreen(projectId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { LinuxRuntimeController(context) }
    var log by remember { mutableStateOf("Selecione uma operação. O GEIVIA consultará o projeto usando a sessão Google autenticada no Debian.") }
    var busy by remember { mutableStateOf(false) }
    var pendingFile by remember { mutableStateOf<File?>(null) }
    var confirmAction by remember { mutableStateOf<String?>(null) }
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
        busy = true
        log = "GEIVIA • $label\n"
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    controller.execute(listOf("login", "debian", "--", "/bin/bash", "-lc", shell), timeout) { chunk ->
                        scope.launch { log = (log + chunk).takeLast(100000) }
                    }
                }
            }.onSuccess { code ->
                log += "\n\nCódigo de saída: $code"
                if (code == 0 && export?.isFile == true) {
                    pendingFile = export
                    saveFile.launch(export.name)
                } else if (code == 0 && export != null) log += "\nO comando terminou, mas o arquivo JSON não foi criado."
            }.onFailure { log += "\nFalha: ${it.message}" }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("FIREBASE • ADMINISTRAÇÃO", style = MaterialTheme.typography.titleLarge)
        Text("Projeto: $projectId", style = MaterialTheme.typography.labelMedium)
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Voltar") }
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
                TOKEN="§(gcloud auth print-access-token)"
                curl -fsS -H "Authorization: Bearer §TOKEN" "https://firebaserules.googleapis.com/v1/projects/$projectId/releases/cloud.firestore" > release.json
                NAME="§(jq -r '.rulesetName // empty' release.json)"
                test -n "$NAME" || { echo "Nenhuma release de regras Firestore foi encontrada."; exit 4; }
                curl -fsS -H "Authorization: Bearer $TOKEN" "https://firebaserules.googleapis.com/v1/§NAME" > '${f.absolutePath}'
                cat '${f.absolutePath}'
            """.trimIndent().replace("§", "$")
            runOperation("Baixando regras Firestore ativas", script, export = f)
        }, modifier = Modifier.fillMaxWidth()) { Text("Baixar regras Firestore atuais (JSON)") }

        HorizontalDivider()
        Text("CRIAR E CONFIGURAR", style = MaterialTheme.typography.labelLarge)
        Text("As ações abaixo podem criar recursos e gerar cobrança. O GEIVIA consulta o estado atual antes de agir.", fontSize = 12.sp)
        OutlinedButton(enabled = !busy, onClick = { confirmAction = "database" }, modifier = Modifier.fillMaxWidth()) { Text("Criar banco Firestore") }
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
            "database" -> Triple("Criar banco Firestore", "Cria um banco Firestore Native (default) na região nam5. A região não poderá ser alterada depois. Pode gerar cobrança conforme uso e plano.", "gcloud firestore databases create --database='(default)' --location=nam5 --type=firestore-native --project='$projectId'")
            "auth" -> Triple("Ativar Firebase Authentication", "Ativa a API Identity Toolkit. Configurar provedores (Google, e-mail, telefone) é uma etapa separada e pode exigir dados e consentimento do proprietário.", "gcloud services enable identitytoolkit.googleapis.com --project='$projectId'")
            else -> Triple("Ativar APIs básicas Firebase", "Ativa Firebase Management, Firestore, Rules e Hosting. O uso dos serviços pode gerar cobrança.", "gcloud services enable firebase.googleapis.com firebaserules.googleapis.com firestore.googleapis.com firebasehosting.googleapis.com --project='$projectId'")
        }
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text(title) }, text = { Text(detail) },
            confirmButton = { TextButton(onClick = { confirmAction = null; runOperation(title, command) }) { Text("Confirmar e executar") } },
            dismissButton = { TextButton(onClick = { confirmAction = null }) { Text("Cancelar") } }
        )
    }
}
