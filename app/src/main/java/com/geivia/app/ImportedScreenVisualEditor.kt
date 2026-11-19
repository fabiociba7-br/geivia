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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
    var status by remember(screen.sourcePath, screen.name) { mutableStateOf("Selecione um elemento para editar.") }
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
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF5E8))) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Tela Jetpack Compose", color = Color(0xFF80521B), fontWeight = FontWeight.Bold)
                    Text(
                        "O editor de código Compose ainda não possui um gravador estrutural seguro. Para não corromper o Kotlin, esta tela permanece em modo de leitura nesta etapa. A edição XML já está conectada ao arquivo-fonte.",
                        color = Color(0xFF80521B), fontSize = 12.sp
                    )
                    OutlinedButton(onClick = onOpenCode) { Text("Editar código Kotlin") }
                }
            }
            ImportedScreenPreview(screen)
        } else {
            Text("Selecione um componente. Segure e arraste para mudar sua ordem no LinearLayout.", color = Color(0xFF718397), fontSize = 12.sp)
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
                                        runCatching {
                                            ImportedXmlLayoutEditor.moveSibling(File(projectRoot, screen.sourcePath), component.id, targetIndex)
                                            components = ImportedXmlLayoutEditor.inspect(File(projectRoot, screen.sourcePath))
                                            status = "Componente reposicionado e salvo no XML original."
                                            onSourceChanged()
                                        }.onFailure { status = it.message ?: "Não foi possível reposicionar." }
                                    }
                                    dragDistance = 0f
                                },
                                onDragCancel = { dragDistance = 0f },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragDistance += dragAmount.y
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
                            Text(component.tag, color = Color(0xFF17354D), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("@id/${component.id} · ${component.text.orEmpty()}", color = Color(0xFF718397), fontSize = 10.sp)
                        }
                        if (component.canReorder) Icon(Icons.Default.DragIndicator, contentDescription = "Pode reordenar", tint = Color(0xFF1769AA))
                        IconButton(onClick = { selectedComponentId = component.id }) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar componente", tint = Color(0xFF1769AA))
                        }
                    }
                }
            }
            if (selected != null) {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Propriedades · ${selected.tag}", color = Color(0xFF17354D), fontWeight = FontWeight.Bold)
                        if (selected.tag in setOf("TextView", "Button", "EditText", "CheckedTextView", "ToggleButton", "RadioButton", "CheckBox", "Switch", "AutoCompleteTextView")) {
                            OutlinedTextField(
                                value = editableText,
                                onValueChange = { editableText = it.take(4096) },
                                label = { Text("Texto") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(onClick = {
                                runCatching {
                                    ImportedXmlLayoutEditor.updateText(File(projectRoot, screen.sourcePath), selected.id, editableText)
                                    components = ImportedXmlLayoutEditor.inspect(File(projectRoot, screen.sourcePath))
                                    status = "Texto gravado no XML original."
                                    onSourceChanged()
                                }.onFailure { status = it.message ?: "Não foi possível salvar." }
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
                                    runCatching {
                                        ImportedXmlLayoutEditor.moveSibling(File(projectRoot, screen.sourcePath), selected.id, index - 1)
                                        components = ImportedXmlLayoutEditor.inspect(File(projectRoot, screen.sourcePath))
                                        status = "Ordem atualizada no XML original."
                                        onSourceChanged()
                                    }.onFailure { status = it.message ?: "Não foi possível mover." }
                                }
                            ) { Text("Mover para cima") }
                            OutlinedButton(
                                enabled = selected.canReorder && index >= 0 && index < siblings.lastIndex,
                                onClick = {
                                    runCatching {
                                        ImportedXmlLayoutEditor.moveSibling(File(projectRoot, screen.sourcePath), selected.id, index + 1)
                                        components = ImportedXmlLayoutEditor.inspect(File(projectRoot, screen.sourcePath))
                                        status = "Ordem atualizada no XML original."
                                        onSourceChanged()
                                    }.onFailure { status = it.message ?: "Não foi possível mover." }
                                }
                            ) { Text("Mover para baixo") }
                        }
                    }
                }
            }
            Text(status, color = Color(0xFF718397), fontSize = 11.sp)
            ImportedScreenPreview(screen, projectRoot)
        }
    }
}
