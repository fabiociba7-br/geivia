package com.geivia.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class StudioElement(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val text: String,
    val fontSize: Float = 16f,
    val padding: Float = 12f,
    val alignment: String = "start",
    val parentId: String? = null
)

private val EditorBlue = Color(0xFF1769AA)
private val EditorInk = Color(0xFF17354D)
private val EditorMuted = Color(0xFF718397)
private const val EDITOR_SCHEMA_KEY = "geivia_studio_schema_v1"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VisualEditorScreen(context: Context, onBack: () -> Unit) {
    val prefs = remember { context.getSharedPreferences("geivia_onboarding", Context.MODE_PRIVATE) }
    val elements = remember {
        mutableStateListOf<StudioElement>().apply {
            val saved = prefs.getString(EDITOR_SCHEMA_KEY, null)
            if (!saved.isNullOrBlank()) runCatching {
                val array = JSONArray(saved)
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(StudioElement(
                        id = item.optString("id", UUID.randomUUID().toString()),
                        type = item.optString("type", "text"),
                        text = item.optString("text", ""),
                        fontSize = item.optDouble("fontSize", 16.0).toFloat().coerceIn(12f, 32f),
                        padding = item.optDouble("padding", 12.0).toFloat().coerceIn(4f, 28f),
                        alignment = item.optString("alignment", "start").let {
                            if (it in listOf("start", "center", "end")) it else "start"
                        },
                        parentId = item.optString("parentId").takeIf { it.isNotBlank() }
                    ))
                }
            }
            if (isEmpty()) add(StudioElement(type = "text", text = "Meu primeiro aplicativo"))
        }
    }
    var selectedId by remember { mutableStateOf(elements.firstOrNull()?.id) }
    var message by remember { mutableStateOf("Rascunho local") }
    val selectedIndex = elements.indexOfFirst { it.id == selectedId }
    val selected = elements.getOrNull(selectedIndex)

    fun addElement(type: String, text: String) {
        val parent = selected?.takeIf { it.type == "section" }?.id
        val element = StudioElement(type = type, text = text, parentId = parent)
        elements.add(element)
        selectedId = element.id
        message = "Componente adicionado"
    }
    fun saveSchema() {
        val array = JSONArray()
        elements.forEach { element ->
            array.put(JSONObject()
                .put("id", element.id)
                .put("type", element.type)
                .put("text", element.text)
                .put("fontSize", element.fontSize.toDouble())
                .put("padding", element.padding.toDouble())
                .put("alignment", element.alignment)
                .put("parentId", element.parentId ?: ""))
        }
        prefs.edit().putString(EDITOR_SCHEMA_KEY, array.toString()).apply()
        message = "Projeto salvo neste dispositivo"
    }
    fun updateSelected(transform: (StudioElement) -> StudioElement) {
        if (selectedIndex >= 0) elements[selectedIndex] = transform(elements[selectedIndex])
    }

    Scaffold(
        containerColor = Color(0xFFF3F6FA),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Editor visual", fontWeight = FontWeight.Bold, color = EditorInk, fontSize = 18.sp)
                        Text("Construa a tela do seu aplicativo", color = EditorMuted, fontSize = 11.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = EditorInk)
                    }
                },
                actions = {
                    TextButton(onClick = { saveSchema() }) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Salvar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Widgets, null, tint = EditorBlue)
                        Spacer(Modifier.width(8.dp))
                        Text("Biblioteca de componentes", fontWeight = FontWeight.Bold, color = EditorInk)
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EditorPaletteButton("Seção", Icons.Default.FolderOpen) { addElement("section", "Nova seção") }
                        EditorPaletteButton("Texto", Icons.Default.TextFields) { addElement("text", "Novo texto") }
                        EditorPaletteButton("Botão", Icons.Default.SmartButton) { addElement("button", "Continuar") }
                        EditorPaletteButton("Cartão", Icons.Default.CropSquare) { addElement("card", "Novo cartão") }
                        EditorPaletteButton("Espaço", Icons.Default.SpaceBar) { addElement("spacer", "Espaço") }
                    }
                    Text("Adicione elementos e organize a estrutura da tela.", color = EditorMuted, fontSize = 11.sp)
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountTree, null, tint = EditorBlue)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Árvore de componentes", fontWeight = FontWeight.Bold, color = EditorInk)
                            Text("${elements.size} elemento(s) nesta tela", color = EditorMuted, fontSize = 11.sp)
                        }
                    }
                    if (elements.isEmpty()) {
                        Text("A tela ainda não possui componentes.", color = EditorMuted, fontSize = 12.sp)
                    } else {
                        elements.forEachIndexed { index, element ->
                            val active = element.id == selectedId
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(if (active) Color(0xFFEAF3FB) else Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                                    .clickable { selectedId = element.id }
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    when (element.type) {
                                        "button" -> Icons.Default.SmartButton
                                        "card" -> Icons.Default.CropSquare
                                        "spacer" -> Icons.Default.SpaceBar
                                        "section" -> Icons.Default.FolderOpen
                                        else -> Icons.Default.TextFields
                                    },
                                    null,
                                    tint = if (active) EditorBlue else EditorMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Text((if (element.parentId != null) "↳ " else "") + element.text.ifBlank { "Sem texto" }, color = EditorInk, fontSize = 13.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                                    Text("${index + 1}. ${element.type}", color = EditorMuted, fontSize = 10.sp)
                                }
                                if (active) Text("SELECIONADO", color = EditorBlue, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Canvas", fontWeight = FontWeight.Bold, color = EditorInk)
                            Text("Prévia da tela Android", color = EditorMuted, fontSize = 11.sp)
                        }
                        Surface(color = Color(0xFFEAF3FB), shape = RoundedCornerShape(20.dp)) {
                            Text("MOBILE", modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = EditorBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFF9FBFD),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFFDCE5EE))
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Meu aplicativo", fontWeight = FontWeight.Bold, color = EditorInk)
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Default.MoreHoriz, null, tint = EditorMuted)
                            }
                            elements.filter { it.parentId == null }.forEach { element ->
                                EditorCanvasNode(element = element, children = elements.filter { it.parentId == element.id }, selectedId = selectedId, onSelect = { selectedId = it })
                            }
                        }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tune, null, tint = EditorBlue)
                        Spacer(Modifier.width(8.dp))
                        Text("Propriedades do componente", fontWeight = FontWeight.Bold, color = EditorInk)
                    }
                    if (selected == null) {
                        Text("Selecione um componente na árvore ou no canvas.", color = EditorMuted, fontSize = 12.sp)
                    } else {
                        Text("TIPO: ${selected.type.uppercase()}", color = EditorMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = selected.text,
                            onValueChange = { value -> updateSelected { it.copy(text = value) } },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Texto do componente") },
                            singleLine = false,
                            minLines = 1,
                            maxLines = 3
                        )
                        Text("Tamanho do texto: ${selected.fontSize.toInt()} sp", color = EditorInk, fontSize = 12.sp)
                        Slider(
                            value = selected.fontSize,
                            onValueChange = { value -> updateSelected { it.copy(fontSize = value) } },
                            valueRange = 12f..32f,
                            steps = 9
                        )
                        Text("Espaçamento interno: ${selected.padding.toInt()} dp", color = EditorInk, fontSize = 12.sp)
                        Slider(
                            value = selected.padding,
                            onValueChange = { value -> updateSelected { it.copy(padding = value) } },
                            valueRange = 4f..28f,
                            steps = 11
                        )
                        Text("Alinhamento", color = EditorInk, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("start" to "Esquerda", "center" to "Centro", "end" to "Direita").forEach { (value, label) ->
                                if (selected.alignment == value) {
                                    Button(onClick = { updateSelected { it.copy(alignment = value) } }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) { Text(label, fontSize = 11.sp) }
                                } else {
                                    OutlinedButton(onClick = { updateSelected { it.copy(alignment = value) } }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) { Text(label, fontSize = 11.sp) }
                                }
                            }
                        }
                        HorizontalDivider(color = Color(0xFFE7EDF3))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    if (selectedIndex > 0) {
                                        val item = elements.removeAt(selectedIndex)
                                        elements.add(selectedIndex - 1, item)
                                    }
                                },
                                enabled = selectedIndex > 0
                            ) { Icon(Icons.Default.ArrowUpward, null); Spacer(Modifier.width(4.dp)); Text("Subir") }
                            OutlinedButton(
                                onClick = {
                                    if (selectedIndex >= 0 && selectedIndex < elements.lastIndex) {
                                        val item = elements.removeAt(selectedIndex)
                                        elements.add(selectedIndex + 1, item)
                                    }
                                },
                                enabled = selectedIndex >= 0 && selectedIndex < elements.lastIndex
                            ) { Icon(Icons.Default.ArrowDownward, null); Spacer(Modifier.width(4.dp)); Text("Descer") }
                            OutlinedButton(
                                onClick = {
                                    if (selectedIndex >= 0) {
                                        elements.removeAt(selectedIndex)
                                        selectedId = elements.getOrNull((selectedIndex - 1).coerceAtLeast(0))?.id
                                    }
                                },
                                enabled = selectedIndex >= 0,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB33A3A))
                            ) { Icon(Icons.Default.DeleteOutline, null); Spacer(Modifier.width(4.dp)); Text("Excluir") }
                        }
                    }
                }
            }
            Text(message, color = EditorMuted, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}


@Composable
private fun EditorCanvasNode(
    element: StudioElement,
    children: List<StudioElement>,
    selectedId: String?,
    onSelect: (String) -> Unit
) {
    val selected = element.id == selectedId
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onSelect(element.id) },
        color = if (selected) Color(0xFFEAF3FB) else Color.White,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) EditorBlue else Color(0xFFE3EAF1))
    ) {
        Column(Modifier.fillMaxWidth().padding(element.padding.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (element.type == "section") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FolderOpen, null, tint = EditorBlue, modifier = Modifier.size(16.dp))
                    Text(element.text, color = EditorInk, fontWeight = FontWeight.SemiBold, fontSize = element.fontSize.sp)
                }
                if (children.isEmpty()) Text("Adicione componentes selecionando esta seção", color = EditorMuted, fontSize = 10.sp)
                children.forEach { child ->
                    EditorCanvasNode(child, emptyList(), selectedId, onSelect)
                }
            } else {
                val align = when (element.alignment) {
                    "center" -> TextAlign.Center
                    "end" -> TextAlign.End
                    else -> TextAlign.Start
                }
                when (element.type) {
                    "button" -> Box(Modifier.fillMaxWidth(), contentAlignment = when (element.alignment) {
                        "center" -> Alignment.Center
                        "end" -> Alignment.CenterEnd
                        else -> Alignment.CenterStart
                    }) {
                        Surface(color = EditorBlue, shape = RoundedCornerShape(9.dp)) {
                            Text(element.text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = element.fontSize.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
                        }
                    }
                    "card" -> Surface(color = Color.White, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Color(0xFFDCE5EE))) {
                        Text(element.text, color = EditorInk, modifier = Modifier.fillMaxWidth().padding(12.dp), fontWeight = FontWeight.Medium, fontSize = element.fontSize.sp, textAlign = align)
                    }
                    "spacer" -> Text("Espaçamento", color = EditorMuted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp))
                    else -> Text(element.text, color = EditorInk, fontSize = element.fontSize.sp, textAlign = align, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun EditorPaletteButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = RoundedCornerShape(12.dp)) {
        Icon(icon, null, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(5.dp))
        Text(label)
    }
}
