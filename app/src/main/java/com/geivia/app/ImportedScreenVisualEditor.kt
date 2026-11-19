package com.geivia.app

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
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
    var previewOnly by rememberSaveable(screen.sourcePath, screen.name) { mutableStateOf(false) }
    var selectedComponentId by rememberSaveable(screen.sourcePath, screen.name) { mutableStateOf<String?>(null) }
    var editableText by remember(screen.sourcePath, screen.name, selectedComponentId) { mutableStateOf("") }
    var widthDraft by remember(screen.sourcePath, screen.name, selectedComponentId) { mutableStateOf("") }
    var heightDraft by remember(screen.sourcePath, screen.name, selectedComponentId) { mutableStateOf("") }
    var status by remember(screen.sourcePath, screen.name) { mutableStateOf("Selecione um elemento para editar.") }
    var latestBackupAvailable by remember(screen.sourcePath) { mutableStateOf(ImportedSourceRecovery.available(File(projectRoot, screen.sourcePath), projectRoot, ImportedSourceRecovery.Version.LATEST)) }
    var previousBackupAvailable by remember(screen.sourcePath) { mutableStateOf(ImportedSourceRecovery.available(File(projectRoot, screen.sourcePath), projectRoot, ImportedSourceRecovery.Version.PREVIOUS)) }
    var pendingChange by remember { mutableStateOf<(() -> Unit)?>(null) }
    var backupPreview by remember { mutableStateOf<String?>(null) }
    fun requestChange(change: () -> Unit) { pendingChange = change }
    var dragDistance by remember(screen.sourcePath, screen.name) { mutableFloatStateOf(0f) }
    var components by remember(screen.sourcePath, screen.name) {
        mutableStateOf(
            if (screen.format == ImportedScreen.Format.ANDROID_XML) {
                runCatching { ImportedXmlLayoutEditor.inspect(File(projectRoot, screen.sourcePath)) }.getOrDefault(emptyList())
            } else emptyList()
        )
    }

    val selected = components.firstOrNull { it.id == selectedComponentId }
    LaunchedEffect(selected?.id, selected?.text) { editableText = selected?.text.orEmpty() }
    LaunchedEffect(selected?.id, selected?.width, selected?.height) {
        widthDraft = selected?.width.orEmpty()
        heightDraft = selected?.height.orEmpty()
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
        if (previewOnly) {
            ImportedScreenPreview(screen)
        } else if (screen.format != ImportedScreen.Format.ANDROID_XML) {
            ImportedComposeVisualEditor(
                screen = screen,
                projectRoot = projectRoot,
                onBack = onBack,
                onOpenCode = onOpenCode,
                onSourceChanged = onSourceChanged
            )
        } else {
            Text("Selecione um componente e arraste na direção do LinearLayout para reposicionar. A alteração é gravada no XML original.", color = Color(0xFF718397), fontSize = 12.sp)
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE8EDF3)), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("PRÉVIA EDITÁVEL · TOQUE E ARRASTE", color = Color(0xFF1769AA), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    Surface(color = Color.White, shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            components.forEach { component ->
                val active = component.id == selectedComponentId
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(component.id, components) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    dragDistance = 0f
                                    selectedComponentId = component.id
                                },
                                onDragEnd = {
                                    val siblings = components.filter { it.parentId == component.parentId }
                                    val currentIndex = siblings.indexOfFirst { it.id == component.id }
                                    val direction = if (dragDistance < -24f) -1 else if (dragDistance > 24f) 1 else 0
                                    val steps = (abs(dragDistance) / 64f).toInt().coerceAtLeast(1)
                                    val targetIndex = (currentIndex + direction * steps).coerceIn(0, (siblings.size - 1).coerceAtLeast(0))
                                    if (direction != 0 && component.canReorder && currentIndex >= 0) {
                                        requestChange {
                                            runCatching {
                                                ImportedXmlLayoutEditor.moveSibling(File(projectRoot, screen.sourcePath), component.id, targetIndex)
                                                components = ImportedXmlLayoutEditor.inspect(File(projectRoot, screen.sourcePath))
                                                latestBackupAvailable = ImportedSourceRecovery.available(File(projectRoot, screen.sourcePath), projectRoot, ImportedSourceRecovery.Version.LATEST)
                                                previousBackupAvailable = ImportedSourceRecovery.available(File(projectRoot, screen.sourcePath), projectRoot, ImportedSourceRecovery.Version.PREVIOUS)
                                                status = "Componente reposicionado e salvo no XML original."
                                                onSourceChanged()
                                            }.onFailure { status = it.message ?: "Não foi possível reposicionar." }
                                        }
                                    }
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
                    Row(
                        Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                            requestChange {
                                runCatching {
                                    val file = File(projectRoot, screen.sourcePath)
                                    ImportedXmlLayoutEditor.updateDimensions(file, selected.id, widthDraft, heightDraft)
                                    components = ImportedXmlLayoutEditor.inspect(file)
                                    latestBackupAvailable = ImportedSourceRecovery.available(file, projectRoot, ImportedSourceRecovery.Version.LATEST)
                                    previousBackupAvailable = ImportedSourceRecovery.available(file, projectRoot, ImportedSourceRecovery.Version.PREVIOUS)
                                    status = "Dimensões salvas no XML original."
                                    onSourceChanged()
                                }.onFailure { status = it.message ?: "Dimensões inválidas." }
                            }
                        }) { Text("Salvar dimensões") }
                        if (selected.tag in setOf("TextView", "Button", "EditText", "CheckedTextView", "ToggleButton", "RadioButton", "CheckBox", "Switch", "AutoCompleteTextView")) {
                            OutlinedTextField(
                                value = editableText,
                                onValueChange = { editableText = it.take(4096) },
                                label = { Text("Texto") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(onClick = {
                                requestChange {
                                    runCatching {
                                        ImportedXmlLayoutEditor.updateText(File(projectRoot, screen.sourcePath), selected.id, editableText)
                                        components = ImportedXmlLayoutEditor.inspect(File(projectRoot, screen.sourcePath))
                                                latestBackupAvailable = ImportedSourceRecovery.available(File(projectRoot, screen.sourcePath), projectRoot, ImportedSourceRecovery.Version.LATEST)
                                                previousBackupAvailable = ImportedSourceRecovery.available(File(projectRoot, screen.sourcePath), projectRoot, ImportedSourceRecovery.Version.PREVIOUS)
                                        status = "Texto gravado no XML original."
                                        onSourceChanged()
                                    }.onFailure { status = it.message ?: "Não foi possível salvar." }
                                }
                            }) { Text("Salvar no arquivo-fonte") }
                        } else {
                            Text("Este tipo de componente não possui edição de texto disponível.", color = Color(0xFF718397), fontSize = 11.sp)
                        }
                        val siblings = components.filter { it.parentId == selected.parentId }
                        val index = siblings.indexOfFirst { it.id == selected.id }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                enabled = selected.canReorder && index > 0,
                                onClick = {
                                    requestChange {
                                        runCatching {
                                            ImportedXmlLayoutEditor.moveSibling(File(projectRoot, screen.sourcePath), selected.id, index - 1)
                                            components = ImportedXmlLayoutEditor.inspect(File(projectRoot, screen.sourcePath))
                                                latestBackupAvailable = ImportedSourceRecovery.available(File(projectRoot, screen.sourcePath), projectRoot, ImportedSourceRecovery.Version.LATEST)
                                                previousBackupAvailable = ImportedSourceRecovery.available(File(projectRoot, screen.sourcePath), projectRoot, ImportedSourceRecovery.Version.PREVIOUS)
                                            status = "Ordem atualizada no XML original."
                                            onSourceChanged()
                                        }.onFailure { status = it.message ?: "Não foi possível mover." }
                                    }
                                }
                            ) { Text("Mover para cima") }
                            OutlinedButton(
                                enabled = selected.canReorder && index >= 0 && index < siblings.lastIndex,
                                onClick = {
                                    requestChange {
                                        runCatching {
                                            ImportedXmlLayoutEditor.moveSibling(File(projectRoot, screen.sourcePath), selected.id, index + 1)
                                            components = ImportedXmlLayoutEditor.inspect(File(projectRoot, screen.sourcePath))
                                                latestBackupAvailable = ImportedSourceRecovery.available(File(projectRoot, screen.sourcePath), projectRoot, ImportedSourceRecovery.Version.LATEST)
                                                previousBackupAvailable = ImportedSourceRecovery.available(File(projectRoot, screen.sourcePath), projectRoot, ImportedSourceRecovery.Version.PREVIOUS)
                                            status = "Ordem atualizada no XML original."
                                            onSourceChanged()
                                        }.onFailure { status = it.message ?: "Não foi possível mover." }
                                    }
                                }
                            ) { Text("Mover para baixo") }
                        }
                    }
                }
            }
            if (latestBackupAvailable || previousBackupAvailable) {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("RECUPERAÇÃO DO ARQUIVO-FONTE", color = Color(0xFF1769AA), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        Text("Restaure uma cópia de segurança sem apagar os arquivos de backup.", color = Color(0xFF718397), fontSize = 11.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (latestBackupAvailable) OutlinedButton(onClick = {
                                backupPreview = runCatching {
                                    ImportedSourceRecovery.preview(File(projectRoot, screen.sourcePath), projectRoot, ImportedSourceRecovery.Version.LATEST)
                                }.fold({ "Última versão · ${File(projectRoot, screen.sourcePath).name}\n\n" + it }, { "Não foi possível abrir a prévia: ${it.message}" })
                            }) { Text("Prévia da última") }
                            if (previousBackupAvailable) OutlinedButton(onClick = {
                                backupPreview = runCatching {
                                    ImportedSourceRecovery.preview(File(projectRoot, screen.sourcePath), projectRoot, ImportedSourceRecovery.Version.PREVIOUS)
                                }.fold({ "Versão anterior · ${File(projectRoot, screen.sourcePath).name}\n\n" + it }, { "Não foi possível abrir a prévia: ${it.message}" })
                            }) { Text("Prévia da anterior") }
                            if (latestBackupAvailable) OutlinedButton(onClick = {
                                requestChange {
                                    runCatching {
                                        ImportedSourceRecovery.restore(File(projectRoot, screen.sourcePath), projectRoot, ImportedSourceRecovery.Version.LATEST)
                                        components = ImportedXmlLayoutEditor.inspect(File(projectRoot, screen.sourcePath))
                                                latestBackupAvailable = ImportedSourceRecovery.available(File(projectRoot, screen.sourcePath), projectRoot, ImportedSourceRecovery.Version.LATEST)
                                                previousBackupAvailable = ImportedSourceRecovery.available(File(projectRoot, screen.sourcePath), projectRoot, ImportedSourceRecovery.Version.PREVIOUS)
                                        status = "Última versão de segurança restaurada."
                                        onSourceChanged()
                                    }.onFailure { status = it.message ?: "Não foi possível restaurar a versão." }
                                }
                            }) { Text("Restaurar última") }
                            if (previousBackupAvailable) OutlinedButton(onClick = {
                                requestChange {
                                    runCatching {
                                        ImportedSourceRecovery.restore(File(projectRoot, screen.sourcePath), projectRoot, ImportedSourceRecovery.Version.PREVIOUS)
                                        components = ImportedXmlLayoutEditor.inspect(File(projectRoot, screen.sourcePath))
                                                latestBackupAvailable = ImportedSourceRecovery.available(File(projectRoot, screen.sourcePath), projectRoot, ImportedSourceRecovery.Version.LATEST)
                                                previousBackupAvailable = ImportedSourceRecovery.available(File(projectRoot, screen.sourcePath), projectRoot, ImportedSourceRecovery.Version.PREVIOUS)
                                        status = "Versão anterior restaurada."
                                        onSourceChanged()
                                    }.onFailure { status = it.message ?: "Não foi possível restaurar a versão." }
                                }
                            }) { Text("Restaurar anterior") }
                        }
                    }
                }
            }
            Text(status, color = Color(0xFF718397), fontSize = 11.sp)
        }
    }
    backupPreview?.let { content ->
        AlertDialog(
            onDismissRequest = { backupPreview = null },
            title = { Text("Prévia da cópia de segurança") },
            text = {
                Text(
                    content,
                    color = Color(0xFF17354D),
                    fontSize = 10.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            },
            confirmButton = {
                Button(onClick = { backupPreview = null }) { Text("Fechar prévia") }
            }
        )
    }
    pendingChange?.let { change ->
        AlertDialog(
            onDismissRequest = { pendingChange = null },
            title = { Text("Confirmar alteração no projeto?") },
            text = { Text("Esta ação vai modificar o arquivo XML original. O código permanecerá intacto se você cancelar.") },
            confirmButton = {
                Button(onClick = {
                    pendingChange = null
                    change()
                }) { Text("Confirmar e aplicar") }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingChange = null }) { Text("Cancelar") }
            }
        )
    }
}
