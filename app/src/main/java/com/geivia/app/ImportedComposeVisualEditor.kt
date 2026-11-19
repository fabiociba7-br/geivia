package com.geivia.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

@Composable
internal fun ImportedComposeVisualEditor(
    screen: ImportedScreen,
    projectRoot: File,
    onBack: () -> Unit,
    onOpenCode: () -> Unit,
    onSourceChanged: () -> Unit
) {
    val sourceFile = remember(projectRoot.absolutePath, screen.sourcePath) {
        File(projectRoot, screen.sourcePath).canonicalFile
    }
    var previewOnly by rememberSaveable(screen.sourcePath, screen.name) { mutableStateOf(false) }
    var selectedId by rememberSaveable(screen.sourcePath, screen.name) { mutableStateOf<String?>(null) }
    var draftText by remember(screen.sourcePath, screen.name, selectedId) { mutableStateOf("") }
    var status by remember(screen.sourcePath, screen.name) { mutableStateOf("Somente textos literais são editáveis nesta versão.") }
    var reviewOpen by rememberSaveable(screen.sourcePath, screen.name) { mutableStateOf(false) }
    var changes by remember(screen.sourcePath, screen.name) { mutableStateOf(emptyList<ImportedScreenChange>()) }
    var texts by remember(screen.sourcePath, screen.name) {
        mutableStateOf(runCatching { ImportedComposeLayoutEditor.inspect(sourceFile, screen.name) }.getOrDefault(emptyList()))
    }
    var sourceFingerprint by remember(screen.sourcePath, screen.name) {
        mutableStateOf(runCatching { ImportedScreenDraft.fingerprint(sourceFile) }.getOrDefault(""))
    }
    val selected = texts.firstOrNull { it.id == selectedId }
    LaunchedEffect(selected?.id, selected?.value) { draftText = selected?.value.orEmpty() }

    fun stageText() {
        val original = texts.firstOrNull { it.id == selectedId } ?: return
        val baseline = changes.filterIsInstance<ImportedScreenChange.Text>()
            .firstOrNull { it.componentId == original.id }?.originalValue ?: original.value
        runCatching {
            changes = ImportedScreenDraft.stage(
                changes,
                ImportedScreenChange.Text(original.id, baseline, draftText)
            )
            texts = texts.map { if (it.id == original.id) it.copy(value = draftText) else it }
            status = "Texto adicionado ao rascunho. O arquivo Kotlin original ainda não foi modificado."
        }.onFailure { status = it.message ?: "Não foi possível adicionar o texto ao rascunho." }
    }

    fun discardDraft() {
        changes = emptyList()
        texts = runCatching { ImportedComposeLayoutEditor.inspect(sourceFile, screen.name) }.getOrDefault(texts)
        sourceFingerprint = runCatching { ImportedScreenDraft.fingerprint(sourceFile) }.getOrDefault(sourceFingerprint)
        status = "Rascunho descartado. O arquivo Kotlin original foi preservado."
        reviewOpen = false
    }

    fun applyDraft() {
        runCatching {
            ImportedScreenDraftApplier.applyCompose(sourceFile, screen.name, changes, sourceFingerprint)
            texts = ImportedComposeLayoutEditor.inspect(sourceFile, screen.name)
            sourceFingerprint = ImportedScreenDraft.fingerprint(sourceFile)
            changes = emptyList()
            reviewOpen = false
            status = "Todas as alterações foram aplicadas e validadas no Kotlin."
            onSourceChanged()
        }.onFailure {
            status = it.message ?: "Não foi possível aplicar o rascunho. O arquivo original foi preservado."
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Voltar às telas") }
            Column(Modifier.weight(1f)) {
                Text(screen.name, color = Color(0xFF17354D), fontWeight = FontWeight.Bold)
                Text(screen.sourcePath, color = Color(0xFF718397), fontSize = 10.sp)
            }
            IconButton(onClick = onOpenCode) { Icon(Icons.Default.Code, contentDescription = "Abrir código") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { previewOnly = false }) { Text("Editar") }
            Button(onClick = { previewOnly = true }) {
                Icon(Icons.Default.Visibility, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text("Prévia")
            }
        }
        if (changes.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF3FB))) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("RASCUNHO · ${changes.size} alteração(ões)", color = Color(0xFF1769AA), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("O arquivo Kotlin permanece intacto até a confirmação do lote.", color = Color(0xFF17354D), fontSize = 11.sp)
                    changes.forEach { Text("• " + ImportedScreenDraft.describe(it), color = Color(0xFF17354D), fontSize = 10.sp) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { reviewOpen = true }, modifier = Modifier.weight(1f)) { Text("Revisar e aplicar") }
                        OutlinedButton(onClick = { discardDraft() }, modifier = Modifier.weight(1f)) { Text("Descartar") }
                    }
                }
            }
        }
        if (previewOnly) {
            ImportedScreenPreview(screen)
        } else {
            Text("Elementos de texto encontrados no Composable. Expressões dinâmicas e componentes sem texto literal ficam protegidos. As alterações são agrupadas em rascunho.", color = Color(0xFF718397), fontSize = 12.sp)
            texts.forEach { item ->
                Card(
                    onClick = { selectedId = item.id },
                    colors = CardDefaults.cardColors(
                        containerColor = if (item.id == selectedId) Color(0xFFEAF3FB) else Color.White
                    )
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(item.value, color = Color(0xFF17354D), fontWeight = FontWeight.SemiBold)
                        Text("Texto literal · ${item.id}", color = Color(0xFF718397), fontSize = 10.sp)
                    }
                }
            }
            if (selected != null) {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Editar texto", color = Color(0xFF17354D), fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = draftText,
                            onValueChange = { draftText = it.take(4096) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Texto do componente") }
                        )
                        Button(onClick = { stageText() }) { Text("Adicionar ao rascunho") }
                    }
                }
            }
            Text(status, color = Color(0xFF718397), fontSize = 11.sp)
        }
    }

    if (reviewOpen) {
        AlertDialog(
            onDismissRequest = { reviewOpen = false },
            title = { Text("Revisar alterações Kotlin") },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Confira o lote. Nenhuma alteração será gravada se você cancelar.", color = Color(0xFF718397), fontSize = 12.sp)
                    changes.forEachIndexed { index, change ->
                        Text("${index + 1}. " + ImportedScreenDraft.describe(change), color = Color(0xFF17354D), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { applyDraft() }, enabled = changes.isNotEmpty()) { Text("Aplicar todas") }
            },
            dismissButton = {
                TextButton(onClick = { reviewOpen = false }) { Text("Voltar") }
            }
        )
    }
}
