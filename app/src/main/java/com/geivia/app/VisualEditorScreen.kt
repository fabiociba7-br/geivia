package com.geivia.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class StudioElement(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val text: String,
    val fontSize: Float = 16f,
    val padding: Float = 12f,
    val alignment: String = "start",
    val stylePreset: String = "body",
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
                        stylePreset = item.optString("stylePreset", "body").takeIf { it in listOf("body", "title", "subtitle", "emphasis", "muted") } ?: "body",
                        parentId = item.optString("parentId").takeIf { it.isNotBlank() }
                    ))
                }
            }
            if (isEmpty()) add(StudioElement(type = "text", text = "Meu primeiro aplicativo"))
        }
    }
    var selectedId by remember { mutableStateOf(elements.firstOrNull()?.id) }
    var message by remember { mutableStateOf("Rascunho local") }
    var parentMenuExpanded by remember { mutableStateOf(false) }
    var styleMenuExpanded by remember { mutableStateOf(false) }
    var draggedElementId by remember { mutableStateOf<String?>(null) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    val dragStepPx = with(androidx.compose.ui.platform.LocalDensity.current) { 52.dp.toPx() }
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
                .put("stylePreset", element.stylePreset)
                .put("parentId", element.parentId ?: ""))
        }
        prefs.edit().putString(EDITOR_SCHEMA_KEY, array.toString()).apply()
        message = "Projeto salvo neste dispositivo"
    }
    fun updateSelected(transform: (StudioElement) -> StudioElement) {
        if (selectedIndex >= 0) elements[selectedIndex] = transform(elements[selectedIndex])
    }

    fun reorderDraggedElement(id: String, steps: Int) {
        if (steps == 0) return
        repeat(kotlin.math.abs(steps)) {
            val current = elements.firstOrNull { it.id == id } ?: return
            val siblings = elements.filter { it.parentId == current.parentId }.map { it.id }
            val position = siblings.indexOf(id)
            val targetPosition = position + if (steps > 0) 1 else -1
            if (position < 0 || targetPosition !in siblings.indices) return@repeat
            val targetId = siblings[targetPosition]
            val item = elements.removeAt(elements.indexOfFirst { it.id == id })
            val targetIndex = elements.indexOfFirst { it.id == targetId }
            elements.add(if (steps > 0) targetIndex + 1 else targetIndex, item)
        }
        selectedId = id
        message = "Ordem dos componentes atualizada"
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
                    Text("Adicione elementos. Segure e arraste um item na árvore para reordenar componentes do mesmo nível.", color = EditorMuted, fontSize = 11.sp)
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountTree, null, tint = EditorBlue)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Árvore de componentes", fontWeight = FontWeight.Bold, color = EditorInk)
                            Text("${elements.size} elemento(s) nesta tela · hierarquia aninhada", color = EditorMuted, fontSize = 11.sp)
                        }
                    }
                    if (elements.isEmpty()) {
                        Text("A tela ainda não possui componentes.", color = EditorMuted, fontSize = 12.sp)
                    } else {
                        elements.forEachIndexed { index, element ->
                            var depth = 0
                            var ancestorId = element.parentId
                            val visited = mutableSetOf(element.id)
                            while (ancestorId != null && visited.add(ancestorId)) {
                                depth++
                                ancestorId = elements.firstOrNull { it.id == ancestorId }?.parentId
                            }
                            val active = element.id == selectedId
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(if (draggedElementId == element.id) Color(0xFFD8EAF9) else if (active) Color(0xFFEAF3FB) else Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                                    .clickable { selectedId = element.id }
                                    .pointerInput(element.id, elements.size) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggedElementId = element.id
                                                selectedId = element.id
                                                dragDistance = 0f
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragDistance += amount.y
                                            },
                                            onDragEnd = {
                                                val steps = (dragDistance / dragStepPx).roundToInt()
                                                reorderDraggedElement(element.id, steps)
                                                draggedElementId = null
                                                dragDistance = 0f
                                            },
                                            onDragCancel = {
                                                draggedElementId = null
                                                dragDistance = 0f
                                            }
                                        )
                                    }
                                    .padding(start = (10 + depth * 16).dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
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
                                    Text((if (depth > 0) "↳ ".repeat(depth.coerceAtMost(4)) else "") + element.text.ifBlank { "Sem texto" }, color = EditorInk, fontSize = 13.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                                    Text("${index + 1}. ${element.type}", color = EditorMuted, fontSize = 10.sp)
                                }
                                if (active) Text("SELECIONADO", color = EditorBlue, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                Icon(Icons.Default.DragIndicator, contentDescription = "Arraste para reordenar", tint = EditorMuted, modifier = Modifier.size(18.dp))
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
                                EditorCanvasNode(element = element, allElements = elements, selectedId = selectedId, onSelect = { selectedId = it })
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
                        Text("Estilo reutilizável", color = EditorInk, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Box {
                            OutlinedButton(onClick = { styleMenuExpanded = true }) {
                                Text(when (selected.stylePreset) {
                                    "title" -> "Título"
                                    "subtitle" -> "Subtítulo"
                                    "emphasis" -> "Destaque"
                                    "muted" -> "Texto secundário"
                                    else -> "Corpo"
                                })
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                            DropdownMenu(expanded = styleMenuExpanded, onDismissRequest = { styleMenuExpanded = false }) {
                                listOf("body" to "Corpo", "title" to "Título", "subtitle" to "Subtítulo", "emphasis" to "Destaque", "muted" to "Texto secundário").forEach { (key, label) ->
                                    DropdownMenuItem(text = { Text(label) }, onClick = {
                                        updateSelected { it.copy(stylePreset = key) }
                                        styleMenuExpanded = false
                                        message = "Estilo reutilizável aplicado"
                                    })
                                }
                            }
                        }
                        HorizontalDivider(color = Color(0xFFE7EDF3))
                        Text("Posição na hierarquia", color = EditorInk, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        val descendantIds = remember(selectedId, elements.size) {
                            val found = mutableSetOf<String>()
                            var changed: Boolean
                            do {
                                changed = false
                                elements.filter { it.parentId == selectedId || it.parentId in found }.forEach {
                                    if (found.add(it.id)) changed = true
                                }
                            } while (changed)
                            found
                        }
                        val availableParents = elements.filter {
                            it.type == "section" && it.id != selectedId && it.id !in descendantIds
                        }
                        Box {
                            OutlinedButton(onClick = { parentMenuExpanded = true }) {
                                Text(elements.firstOrNull { it.id == selected.parentId }?.text ?: "Raiz da tela", maxLines = 1)
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                            DropdownMenu(expanded = parentMenuExpanded, onDismissRequest = { parentMenuExpanded = false }) {
                                DropdownMenuItem(text = { Text("Raiz da tela") }, onClick = {
                                    updateSelected { it.copy(parentId = null) }
                                    parentMenuExpanded = false
                                    message = "Componente movido para a raiz"
                                })
                                availableParents.forEach { parent ->
                                    DropdownMenuItem(text = { Text(parent.text.ifBlank { "Seção sem nome" }) }, onClick = {
                                        updateSelected { it.copy(parentId = parent.id) }
                                        parentMenuExpanded = false
                                        message = "Componente movido para " + parent.text
                                    })
                                }
                            }
                        }
                        val siblingIds = elements.filter { it.parentId == selected.parentId }.map { it.id }
                        val siblingPosition = siblingIds.indexOf(selected.id)
                        HorizontalDivider(color = Color(0xFFE7EDF3))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    if (siblingPosition > 0) {
                                        val previousId = siblingIds[siblingPosition - 1]
                                        val item = elements.removeAt(elements.indexOfFirst { it.id == selected.id })
                                        val targetIndex = elements.indexOfFirst { it.id == previousId }
                                        elements.add(targetIndex, item)
                                    }
                                },
                                enabled = siblingPosition > 0
                            ) { Icon(Icons.Default.ArrowUpward, null); Spacer(Modifier.width(4.dp)); Text("Subir") }
                            OutlinedButton(
                                onClick = {
                                    if (siblingPosition >= 0 && siblingPosition < siblingIds.lastIndex) {
                                        val nextId = siblingIds[siblingPosition + 1]
                                        val item = elements.removeAt(elements.indexOfFirst { it.id == selected.id })
                                        val targetIndex = elements.indexOfFirst { it.id == nextId }
                                        elements.add(targetIndex + 1, item)
                                    }
                                },
                                enabled = siblingPosition >= 0 && siblingPosition < siblingIds.lastIndex
                            ) { Icon(Icons.Default.ArrowDownward, null); Spacer(Modifier.width(4.dp)); Text("Descer") }
                            OutlinedButton(
                                onClick = {
                                    if (selectedIndex >= 0) {
                                        val removedIds = mutableSetOf(selected!!.id)
                                        var changed: Boolean
                                        do {
                                            changed = false
                                            elements.filter { it.parentId in removedIds }.forEach {
                                                if (removedIds.add(it.id)) changed = true
                                            }
                                        } while (changed)
                                        elements.removeAll { it.id in removedIds }
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
    allElements: List<StudioElement>,
    selectedId: String?,
    onSelect: (String) -> Unit
) {
    val children = allElements.filter { it.parentId == element.id }
    val selected = element.id == selectedId
    val styleFontSize = when (element.stylePreset) { "title" -> maxOf(element.fontSize, 26f); "subtitle" -> maxOf(element.fontSize, 19f); else -> element.fontSize }
    val styleColor = when (element.stylePreset) { "emphasis" -> Color(0xFF1769AA); "muted" -> Color(0xFF718397); else -> EditorInk }
    val styleWeight = when (element.stylePreset) { "title", "emphasis" -> FontWeight.Bold; "subtitle" -> FontWeight.SemiBold; else -> FontWeight.Normal }
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
                    Text(element.text, color = styleColor, fontWeight = styleWeight, fontSize = styleFontSize.sp)
                }
                if (children.isEmpty()) Text("Adicione componentes selecionando esta seção", color = EditorMuted, fontSize = 10.sp)
                children.forEach { child ->
                    EditorCanvasNode(child, allElements, selectedId, onSelect)
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
                            Text(element.text, color = Color.White, fontWeight = styleWeight, fontSize = styleFontSize.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
                        }
                    }
                    "card" -> Surface(color = Color.White, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Color(0xFFDCE5EE))) {
                        Text(element.text, color = styleColor, modifier = Modifier.fillMaxWidth().padding(12.dp), fontWeight = styleWeight, fontSize = styleFontSize.sp, textAlign = align)
                    }
                    "spacer" -> Text("Espaçamento", color = EditorMuted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp))
                    else -> Text(element.text, color = styleColor, fontSize = styleFontSize.sp, fontWeight = styleWeight, textAlign = align, modifier = Modifier.fillMaxWidth())
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
