package com.geivia.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
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
    val sourceFile = File(projectRoot, screen.sourcePath)
    var previewOnly by rememberSaveable(screen.sourcePath, screen.name) { mutableStateOf(false) }
    var selectedId by rememberSaveable(screen.sourcePath, screen.name) { mutableStateOf<String?>(null) }
    var draft by remember(screen.sourcePath, screen.name, selectedId) { mutableStateOf("") }
    var status by remember(screen.sourcePath, screen.name) { mutableStateOf("Somente textos literais são editáveis nesta versão.") }
    var texts by remember(screen.sourcePath, screen.name) {
        mutableStateOf(runCatching { ImportedComposeLayoutEditor.inspect(sourceFile, screen.name) }.getOrDefault(emptyList()))
    }
    val selected = texts.firstOrNull { it.id == selectedId }
    LaunchedEffect(selected?.id, selected?.value) { draft = selected?.value.orEmpty() }

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
        if (previewOnly) {
            ImportedScreenPreview(screen)
        } else {
            Text("Elementos de texto encontrados no Composable. Expressões dinâmicas e componentes sem texto literal ficam protegidos.", color = Color(0xFF718397), fontSize = 12.sp)
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
                            value = draft,
                            onValueChange = { draft = it.take(4096) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Texto do componente") }
                        )
                        Button(onClick = {
                            runCatching {
                                ImportedComposeLayoutEditor.updateText(sourceFile, screen.name, selected.id, draft)
                                texts = ImportedComposeLayoutEditor.inspect(sourceFile, screen.name)
                                status = "Texto atualizado no arquivo Kotlin original."
                                onSourceChanged()
                            }.onFailure { status = it.message ?: "Não foi possível salvar no Kotlin." }
                        }) { Text("Salvar no arquivo-fonte") }
                    }
                }
            }
            Text(status, color = Color(0xFF718397), fontSize = 11.sp)
        }
    }
}
