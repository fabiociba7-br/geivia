package com.geivia.app

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlin.math.abs

@Composable
internal fun ImportedScreenVisualEditor(
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
    var selectedComponentId by rememberSaveable(screen.sourcePath, screen.name) { mutableStateOf<String?>(null) }
    var editableText by remember(screen.sourcePath, screen.name, selectedComponentId) { mutableStateOf("") }
    var widthDraft by remember(screen.sourcePath, screen.name, selectedComponentId) { mutableStateOf("") }
    var heightDraft by remember(screen.sourcePath, screen.name, selectedComponentId) { mutableStateOf("") }
    var status by remember(screen.sourcePath, screen.name) { mutableStateOf("Selecione um elemento para editar.") }
    var dragDistance by remember(screen.sourcePath, screen.name) { mutableFloatStateOf(0f) }
    var reviewOpen by rememberSaveable(screen.sourcePath, screen.name) { mutableStateOf(false) }
    var changes by remember(screen.sourcePath, screen.name) { mutableStateOf(emptyList<ImportedScreenChange>()) }
    var components by remember(screen.sourcePath, screen.name) {
        mutableStateOf(
            if (screen.format == ImportedScreen.Format.ANDROID_XML) {
                runCatching { ImportedXmlLayoutEditor.inspect(sourceFile) }.getOrDefault(emptyList())
            } else emptyList()
        )
    }
    var sourceFingerprint by remember(screen.sourcePath, screen.name) {
        mutableStateOf(runCatching { ImportedScreenDraft.fingerprint(sourceFile) }.getOrDefault(""))
    }

    val selected = components.firstOrNull { it.id == selectedComponentId }
    LaunchedEffect(selected?.id, selected?.text) { editableText = selected?.text.orEmpty() }
    LaunchedEffect(selected?.id, selected?.width, selected?.height) {
        widthDraft = selected?.width.orEmpty()
        heightDraft = selected?.height.orEmpty()
    }

    fun stage(change: ImportedScreenChange) {
        runCatching { changes = ImportedScreenDraft.stage(changes, change) }
            .onSuccess { status = "Alteração adicionada ao rascunho. O arquivo original ainda não foi modificado." }
            .onFailure { status = it.message ?: "Não foi possível adicionar a alteração." }
    }

    fun stageMove(component: ImportedXmlLayoutEditor.Component, targetIndex: Int) {
        val baselineSiblings = components.filter { it.parentId == component.parentId }
        val originalIndex = baselineSiblings.indexOfFirst { it.id == component.id }
        if (originalIndex < 0) return
        val currentIndex = (changes.filterIsInstance<ImportedScreenChange.Move>()
            .firstOrNull { it.componentId == component.id }?.newIndex ?: originalIndex)
        val safeTarget = targetIndex.coerceIn(0, (baselineSiblings.size - 1).coerceAtLeast(0))
        if (safeTarget == currentIndex) return
        stage(ImportedScreenChange.Move(component.id, component.parentId, originalIndex, safeTarget))
    }

    fun discardDraft() {
        changes = emptyList()
        components = runCatching { ImportedXmlLayoutEditor.inspect(sourceFile) }.getOrDefault(components)
        sourceFingerprint = runCatching { ImportedScreenDraft.fingerprint(sourceFile) }.getOrDefault(sourceFingerprint)
        status = "Rascunho descartado. O arquivo original foi preservado."
        reviewOpen = false
    }

    fun applyDraft() {
        runCatching {
            ImportedScreenDraftApplier.applyXml(sourceFile, changes, sourceFingerprint)
            components = ImportedXmlLayoutEditor.inspect(sourceFile)
            sourceFingerprint = ImportedScreenDraft.fingerprint(sourceFile)
            changes = emptyList()
            reviewOpen = false
            status = "Todas as alterações foram aplicadas e validadas no XML."
            onSourceChanged()
        }.onFailure {
            status = it.message ?: "Não foi possível aplicar o rascunho. O arquivo original foi preservado."
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
                    Text("O arquivo original permanece intacto até a confirmação do lote.", color = Color(0xFF17354D), fontSize = 11.sp)
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
            Text("Edite textos, dimensões e ordem. As mudanças ficam em rascunho e só são gravadas após revisão e confirmação do lote.", color = Color(0xFF718397), fontSize = 12.sp)
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE8EDF3)), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("PRÉVIA EDITÁVEL · ALTERAÇÕES PENDENTES", color = Color(0xFF1769AA), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    Surface(color = Color.White, shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            components.forEach { component ->
                                val active = component.id == selectedComponentId
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .pointerInput(component.id, components, changes) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    dragDistance = 0f
                                                    selectedComponentId = component.id
                                                },
                                                onDragEnd = {
                                                    val siblings = components.filter { it.parentId == component.parentId }
                                                    val baselineIndex = siblings.indexOfFirst { it.id == component.id }
                                                    val stagedIndex = changes.filterIsInstance<ImportedScreenChange.Move>()
                                                        .firstOrNull { it.componentId == component.id }?.newIndex ?: baselineIndex
                                                    val direction = if (dragDistance < -24f) -1 else if (dragDistance > 24f) 1 else 0
                                                    val steps = (abs(dragDistance) / 64f).toInt().coerceAtLeast(1)
                                                    val targetIndex = (stagedIndex + direction * steps).coerceIn(0, (siblings.size - 1).coerceAtLeast(0))
                                                    if (direction != 0 && component.canReorder) stageMove(component, targetIndex)
                                                    dragDistance = 0f
                                                },
                                                onDragCancel = { dragDistance = 0f },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragDistance += if (component.orientation == "horizontal") dragAmount.x else dragAmount.y
                                                }
                                            )
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (active) Color(0xFFEAF3FB) else Color(0xFFF5F8FB)
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                component.text?.takeIf { it.isNotBlank() } ?: component.tag,
                                                color = Color(0xFF17354D),
                                                fontWeight = if (component.tag in setOf("Button", "TextView")) FontWeight.SemiBold else FontWeight.Normal,
                                                fontSize = if (component.tag == "Button") 13.sp else 12.sp
                                            )
                                            Text("${component.tag} · @id/${component.id}", color = Color(0xFF718397), fontSize = 9.sp)
                                        }
                                        if (component.canReorder) Icon(Icons.Default.DragIndicator, contentDescription = "Pode reordenar", tint = Color(0xFF1769AA))
                                        IconButton(onClick = { selectedComponentId = component.id }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Editar componente", tint = Color(0xFF1769AA))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (selected != null) {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Propriedades · ${selected.tag}", color = Color(0xFF17354D), fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = widthDraft,
                                onValueChange = { widthDraft = it.take(12) },
                                label = { Text("Largura") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = heightDraft,
                                onValueChange = { heightDraft = it.take(12) },
                                label = { Text("Altura") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                        Text("Use wrap_content, match_parent ou valores em dp.", color = Color(0xFF718397), fontSize = 10.sp)
                        OutlinedButton(onClick = {
                            stage(ImportedScreenChange.Dimensions(
                                selected.id,
                                selected.width.orEmpty(),
                                selected.height.orEmpty(),
                                widthDraft.trim(),
                                heightDraft.trim()
                            ))
                        }) { Text("Adicionar dimensões ao rascunho") }
                        if (selected.tag in setOf("TextView", "Button", "EditText", "CheckedTextView", "ToggleButton", "RadioButton", "CheckBox", "Switch", "AutoCompleteTextView")) {
                            OutlinedTextField(
                                value = editableText,
                                onValueChange = { editableText = it.take(4096) },
                                label = { Text("Texto") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(onClick = {
                                stage(ImportedScreenChange.Text(selected.id, selected.text.orEmpty(), editableText))
                            }) { Text("Adicionar texto ao rascunho") }
                        } else {
                            Text("Este tipo de componente não possui edição de texto disponível.", color = Color(0xFF718397), fontSize = 11.sp)
                        }
                        val siblings = components.filter { it.parentId == selected.parentId }
                        val baselineIndex = siblings.indexOfFirst { it.id == selected.id }
                        val currentIndex = changes.filterIsInstance<ImportedScreenChange.Move>()
                            .firstOrNull { it.componentId == selected.id }?.newIndex ?: baselineIndex
                        HorizontalRule()
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                enabled = selected.canReorder && currentIndex > 0,
                                onClick = { stageMove(selected, currentIndex - 1) }
                            ) { Text("Mover para cima") }
                            OutlinedButton(
                                enabled = selected.canReorder && currentIndex >= 0 && currentIndex < siblings.lastIndex,
                                onClick = { stageMove(selected, currentIndex + 1) }
                            ) { Text("Mover para baixo") }
                        }
                    }
                }
            }
        }
        Text(status, color = Color(0xFF718397), fontSize = 11.sp)
    }

    if (reviewOpen) {
        AlertDialog(
            onDismissRequest = { reviewOpen = false },
            title = { Text("Revisar alterações do projeto") },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Confira o lote. Nada será gravado se você cancelar.", color = Color(0xFF718397), fontSize = 12.sp)
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

@Composable
private fun HorizontalRule() {
    androidx.compose.material3.HorizontalDivider(color = Color(0xFFE7EDF3))
}
