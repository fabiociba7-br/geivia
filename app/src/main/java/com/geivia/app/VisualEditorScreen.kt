package com.geivia.app

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    val parentId: String? = null,
    val backgroundColor: String = "#FFFFFF",
    val textColor: String = "#17354D",
    val cornerRadius: Float = 10f,
    val visible: Boolean = true
)

private val EditorBlue = Color(0xFF1769AA)
private val EditorInk = Color(0xFF17354D)
private val EditorMuted = Color(0xFF718397)
private const val EDITOR_SCHEMA_KEY = "geivia_studio_schema_v1"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VisualEditorScreen(
    context: Context,
    onBack: () -> Unit,
    onFirebase: () -> Unit = {},
    onGoogle: () -> Unit = {},
    onAccounts: () -> Unit = {},
    onTools: () -> Unit = {},
    onDiagnostics: () -> Unit = {},
    onTerminal: () -> Unit = {},
    onProjects: () -> Unit = {}
) {
    val prefs = remember { context.getSharedPreferences("geivia_onboarding", Context.MODE_PRIVATE) }
    val projectIdForStorage = prefs.getString("selected_project_id", null).orEmpty().ifBlank { "local" }
    val projectKey = projectIdForStorage.replace(Regex("[^A-Za-z0-9_-]"), "_")
    val allowLegacyProjectData = !prefs.getBoolean("studio_project_migrated_$projectKey", false)
    val screenRegistryPrefKey = "geivia_studio_screens_v1_$projectKey"
    val schemaPrefix = "${EDITOR_SCHEMA_KEY}_$projectKey"
    val themePrimaryPrefKey = "studio_theme_primary_$projectKey"
    val themeBackgroundPrefKey = "studio_theme_background_$projectKey"
    val themeTextPrefKey = "studio_theme_text_$projectKey"
    val assetsPrefKey = "studio_assets_v1_$projectKey"
    val generatedPackagePrefKey = "studio_generated_package_$projectKey"
    val savedScreenDocument = prefs.getString(screenRegistryPrefKey, null)
        ?: if (allowLegacyProjectData) prefs.getString("geivia_studio_screens_v1", null) else null
    val projectTitle = prefs.getString("selected_project_name", "Meu aplicativo").orEmpty().ifBlank { "Meu aplicativo" }
    val screenRegistry = remember {
        mutableStateListOf<StudioScreenDefinition>().apply {
            if (!savedScreenDocument.isNullOrBlank()) runCatching {
                addAll(StudioScreenDocument.fromJson(savedScreenDocument).screens)
            }
            if (isEmpty()) add(StudioScreenDefinition("home", "Início"))
        }
    }
    var activeScreenId by remember { mutableStateOf(screenRegistry.first().id) }
    var startScreenId by remember {
        mutableStateOf(
            runCatching {
                StudioScreenDocument.fromJson(savedScreenDocument.orEmpty()).startScreenId
            }.getOrDefault(screenRegistry.first().id).takeIf { candidate ->
                screenRegistry.any { it.id == candidate }
            } ?: screenRegistry.first().id
        )
    }
    var renamingScreenId by remember { mutableStateOf<String?>(null) }
    var renameValue by remember { mutableStateOf("") }

    fun decodeElements(saved: String?): List<StudioElement> {
        if (saved.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(saved)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                StudioElement(
                    id = item.optString("id", UUID.randomUUID().toString()),
                    type = item.optString("type", "text"),
                    text = item.optString("text", ""),
                    fontSize = item.optDouble("fontSize", 16.0).toFloat().coerceIn(12f, 32f),
                    padding = item.optDouble("padding", 12.0).toFloat().coerceIn(4f, 28f),
                    alignment = item.optString("alignment", "start").let {
                        if (it in listOf("start", "center", "end")) it else "start"
                    },
                    parentId = item.optString("parentId").takeIf { it.isNotBlank() },
                    backgroundColor = item.optString("backgroundColor", "#FFFFFF"),
                    textColor = item.optString("textColor", "#17354D"),
                    cornerRadius = item.optDouble("cornerRadius", 10.0).toFloat().coerceIn(0f, 32f),
                    visible = item.optBoolean("visible", true)
                )
            }
        }.getOrDefault(emptyList())
    }

    val elements = remember {
        mutableStateListOf<StudioElement>().apply {
            val perScreen = prefs.getString("${schemaPrefix}_${activeScreenId}", null)
            val legacyPerScreen = if (allowLegacyProjectData) prefs.getString("${EDITOR_SCHEMA_KEY}_${activeScreenId}", null) else null
            val legacyHome = if (allowLegacyProjectData) prefs.getString(EDITOR_SCHEMA_KEY, null) else null
            addAll(decodeElements(perScreen ?: legacyPerScreen ?: legacyHome))
            if (isEmpty() && perScreen == null && legacyPerScreen == null && legacyHome == null) {
                add(StudioElement(type = "text", text = "Meu primeiro aplicativo"))
            }
        }
    }
    var selectedId by remember { mutableStateOf(elements.firstOrNull()?.id) }
    val undoStack = remember { mutableStateListOf<List<StudioElement>>() }
    val redoStack = remember { mutableStateListOf<List<StudioElement>>() }
    var message by remember { mutableStateOf("Rascunho local") }
    var previewMode by rememberSaveable { mutableStateOf(false) }
    var workspacePanel by rememberSaveable { mutableStateOf("canvas") }
    var themePrimary by remember { mutableStateOf(prefs.getString(themePrimaryPrefKey, if (allowLegacyProjectData) prefs.getString("studio_theme_primary", "#1769AA") else "#1769AA") ?: "#1769AA") }
    var themeBackground by remember { mutableStateOf(prefs.getString(themeBackgroundPrefKey, if (allowLegacyProjectData) prefs.getString("studio_theme_background", "#F3F6FA") else "#F3F6FA") ?: "#F3F6FA") }
    var themeText by remember { mutableStateOf(prefs.getString(themeTextPrefKey, if (allowLegacyProjectData) prefs.getString("studio_theme_text", "#17354D") else "#17354D") ?: "#17354D") }
    var assetUrl by remember { mutableStateOf("") }
    var generatedPackage by rememberSaveable { mutableStateOf(prefs.getString(generatedPackagePrefKey, if (allowLegacyProjectData) prefs.getString("studio_generated_package", "com.generated.geivia") else "com.generated.geivia") ?: "com.generated.geivia") }
    val savedAssets = remember { mutableStateListOf<String>().apply { prefs.getString(assetsPrefKey, if (allowLegacyProjectData) prefs.getString("studio_assets_v1", null) else null)?.let { raw -> runCatching { val a = JSONArray(raw); for (i in 0 until a.length()) add(a.getString(i)) } } } }
    val assetPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            if (uri.toString() !in savedAssets) savedAssets.add(uri.toString())
            prefs.edit().putString(assetsPrefKey, JSONArray(savedAssets).toString()).apply()
            message = "Asset adicionado ao projeto"
        }
    }
    var pendingExportFiles by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val projectExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            val result = runCatching {
                val output = context.contentResolver.openOutputStream(uri) ?: error("Não foi possível abrir o arquivo de destino")
                java.util.zip.ZipOutputStream(output).use { zip ->
                    pendingExportFiles.forEach { (name, fileContent) ->
                        zip.putNextEntry(java.util.zip.ZipEntry(name))
                        zip.write(fileContent.toByteArray(Charsets.UTF_8))
                        zip.closeEntry()
                    }
                }
            }
            message = if (result.isSuccess) "Projeto Android exportado em ZIP" else "Falha ao exportar o projeto"
        }
    }
    val editorDrawerState = rememberDrawerState(DrawerValue.Closed)
    val editorScope = rememberCoroutineScope()
    var parentMenuExpanded by remember { mutableStateOf(false) }
    var draggedElementId by remember { mutableStateOf<String?>(null) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    val dragStepPx = with(androidx.compose.ui.platform.LocalDensity.current) { 52.dp.toPx() }
    val selectedIndex = elements.indexOfFirst { it.id == selectedId }
    val selected = elements.getOrNull(selectedIndex)

    fun addElement(type: String, text: String) {
        checkpoint()
        val parent = selected?.takeIf { it.type in setOf("section", "column", "row", "card") }?.id
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
                .put("parentId", element.parentId ?: "")
                .put("backgroundColor", element.backgroundColor)
                .put("textColor", element.textColor)
                .put("cornerRadius", element.cornerRadius.toDouble())
                .put("visible", element.visible))
        }
        val registry = StudioScreenDocument(screens = screenRegistry.toList(), startScreenId = startScreenId)
        prefs.edit()
            .putString("${schemaPrefix}_${activeScreenId}", array.toString())
            .putString(screenRegistryPrefKey, registry.toJson())
            .putBoolean("studio_project_migrated_$projectKey", true)
            .putString(themePrimaryPrefKey, themePrimary)
            .putString(themeBackgroundPrefKey, themeBackground)
            .putString(themeTextPrefKey, themeText)
            .putString(assetsPrefKey, JSONArray(savedAssets).toString())
            .putString(generatedPackagePrefKey, generatedPackage)
            .apply()
        message = "Tela salva neste dispositivo"
    }

    fun buildProjectJson(): String {
        val project = JSONObject()
            .put("format", "geivia-studio-project")
            .put("schemaVersion", 2)
            .put("startScreenId", startScreenId)
        val screens = JSONArray()
        screenRegistry.forEach { screen ->
            val screenElements = if (screen.id == activeScreenId) elements.toList()
                else decodeElements(prefs.getString("${schemaPrefix}_${screen.id}", null) ?: if (allowLegacyProjectData) prefs.getString("${EDITOR_SCHEMA_KEY}_${screen.id}", null) else null)
            val items = JSONArray()
            screenElements.forEach { item ->
                items.put(JSONObject()
                    .put("id", item.id).put("type", item.type).put("text", item.text)
                    .put("fontSize", item.fontSize).put("padding", item.padding)
                    .put("alignment", item.alignment).put("parentId", item.parentId ?: JSONObject.NULL)
                    .put("backgroundColor", item.backgroundColor).put("textColor", item.textColor)
                    .put("cornerRadius", item.cornerRadius).put("visible", item.visible))
            }
            screens.put(JSONObject().put("id", screen.id).put("name", screen.name).put("components", items))
        }
        project.put("name", projectTitle)
        project.put("screens", screens)
        project.put("theme", JSONObject().put("primary", themePrimary).put("background", themeBackground).put("text", themeText))
        project.put("assets", JSONArray(savedAssets))
        val logicJson = prefs.getString("geivia_studio_logic_v1_$projectKey", null) ?: if (allowLegacyProjectData) prefs.getString("geivia_studio_logic_v1", null) else null
        project.put("logic", if (logicJson.isNullOrBlank()) JSONObject() else runCatching { JSONObject(logicJson) }.getOrDefault(JSONObject()))
        return project.toString(2)
    }

    fun switchScreen(screenId: String) {
        if (screenId == activeScreenId || screenRegistry.none { it.id == screenId }) return
        saveSchema()
        activeScreenId = screenId
        elements.clear()
        elements.addAll(decodeElements(prefs.getString("${schemaPrefix}_${screenId}", null) ?: if (allowLegacyProjectData) prefs.getString("${EDITOR_SCHEMA_KEY}_${screenId}", null) else null))
        selectedId = elements.firstOrNull()?.id
        message = "Tela alterada"
    }

    fun addScreen() {
        if (screenRegistry.size >= StudioScreenDocument.MAX_SCREENS) {
            message = "Limite de telas atingido"
            return
        }
        saveSchema()
        val number = screenRegistry.size + 1
        val id = "screen_${UUID.randomUUID().toString().replace("-", "").take(10)}"
        screenRegistry.add(StudioScreenDefinition(id, "Tela $number"))
        activeScreenId = id
        elements.clear()
        selectedId = null
        prefs.edit().putString(
            screenRegistryPrefKey,
            StudioScreenDocument(screenRegistry.toList(), startScreenId).toJson()
        ).putBoolean("studio_project_migrated_$projectKey", true).apply()
        message = "Nova tela criada"
    }

    fun setStartScreen(screenId: String) {
        if (screenRegistry.none { it.id == screenId }) return
        startScreenId = screenId
        prefs.edit().putString(
            screenRegistryPrefKey,
            StudioScreenDocument(screenRegistry.toList(), startScreenId).toJson()
        ).putBoolean("studio_project_migrated_$projectKey", true).apply()
        message = "Tela inicial definida"
    }
    fun checkpoint() {
        undoStack.add(elements.toList())
        if (undoStack.size > 30) undoStack.removeAt(0)
        redoStack.clear()
    }
    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.add(elements.toList())
        val previous = undoStack.removeAt(undoStack.lastIndex)
        elements.clear(); elements.addAll(previous)
        selectedId = selectedId?.takeIf { id -> elements.any { it.id == id } } ?: elements.firstOrNull()?.id
        message = "Ação desfeita"
    }
    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.add(elements.toList())
        val next = redoStack.removeAt(redoStack.lastIndex)
        elements.clear(); elements.addAll(next)
        selectedId = selectedId?.takeIf { id -> elements.any { it.id == id } } ?: elements.firstOrNull()?.id
        message = "Ação refeita"
    }
    fun deleteSelected() {
        val index = elements.indexOfFirst { it.id == selectedId }
        if (index < 0) return
        checkpoint()
        val removed = mutableSetOf(elements[index].id)
        var changed: Boolean
        do {
            changed = false
            elements.filter { it.parentId in removed }.forEach { if (removed.add(it.id)) changed = true }
        } while (changed)
        elements.removeAll { it.id in removed }
        selectedId = elements.getOrNull((index - 1).coerceAtLeast(0))?.id
        message = "Componente removido"
    }

    fun updateSelected(transform: (StudioElement) -> StudioElement) {
        if (selectedIndex >= 0) elements[selectedIndex] = transform(elements[selectedIndex])
    }

    fun reorderDraggedElement(id: String, steps: Int) {
        if (steps == 0) return
        checkpoint()
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

    LaunchedEffect(activeScreenId, elements.toList(), screenRegistry.toList(), startScreenId) {
        kotlinx.coroutines.delay(400)
        saveSchema()
    }

    ModalNavigationDrawer(
        drawerState = editorDrawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color.White,
                drawerContentColor = EditorInk
            ) {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)
                ) {
                    Text(projectTitle, color = EditorInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("${screenRegistry.firstOrNull { it.id == activeScreenId }?.name ?: "Home"} · Android", color = EditorMuted, fontSize = 11.sp)
                    Spacer(Modifier.height(18.dp))
                    Text("PROJETO", color = EditorMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    val projectItems = listOf("Páginas", "Componentes", "Tema e estilo", "Firestore", "Autenticação", "APIs", "Ações e lógica", "Variáveis", "Assets", "Integrações", "Configurações")
                    projectItems.forEach { item ->
                        val available = true
                        NavigationDrawerItem(
                            label = { Text(item, fontSize = 13.sp) },
                            selected = false,
                            enabled = available,
                            onClick = {
                                editorScope.launch { editorDrawerState.close() }
                                when (item) {
                                    "Firestore", "Autenticação" -> onFirebase()
                                    "APIs" -> onGoogle()
                                    "Integrações" -> onAccounts()
                                    "Configurações" -> onDiagnostics()
                                    "Páginas" -> workspacePanel = "pages"
                                    "Componentes" -> workspacePanel = "components"
                                    "Tema e estilo" -> workspacePanel = "theme"
                                    "Ações e lógica", "Variáveis" -> workspacePanel = "logic"
                                    "Assets" -> workspacePanel = "assets"
                                    else -> message = "$item selecionado"
                                }
                            },
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color(0xFFE4EAF0))
                    Text("FERRAMENTAS", color = EditorMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    val toolItems = listOf("Testar", "Deploy", "Terminal", "Versões", "Código gerado")
                    toolItems.forEach { item ->
                        val available = true
                        NavigationDrawerItem(
                            label = { Text(item, fontSize = 13.sp) },
                            selected = false,
                            enabled = available,
                            onClick = {
                                editorScope.launch { editorDrawerState.close() }
                                when (item) {
                                    "Testar" -> previewMode = true
                                    "Deploy" -> onProjects()
                                    "Terminal" -> onTerminal()
                                    "Versões" -> onTools()
                                    "Código gerado" -> workspacePanel = "code"
                                }
                            },
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }
    ) {
    Scaffold(
        containerColor = runCatching { Color(android.graphics.Color.parseColor(themeBackground)) }.getOrDefault(Color(0xFFF3F6FA)),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (previewMode) "Prévia do aplicativo" else when (workspacePanel) {
                            "canvas" -> "Editor visual"
                            "components" -> "Biblioteca de componentes"
                            "pages" -> "Páginas do aplicativo"
                            "theme" -> "Tema e estilo"
                            "assets" -> "Assets do projeto"
                            "logic" -> "Ações e lógica"
                            "code" -> "Estrutura e código"
                            else -> "Editor avançado"
                        }, fontWeight = FontWeight.Bold, color = EditorInk, fontSize = 18.sp)
                        Text("GEIVIA Studio · Android", color = EditorMuted, fontSize = 11.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (previewMode) previewMode = false
                        else if (workspacePanel != "canvas") workspacePanel = "canvas"
                        else editorScope.launch { editorDrawerState.open() }
                    }) {
                        Icon(
                            if (previewMode || workspacePanel != "canvas") Icons.Default.ArrowBack else Icons.Default.Menu,
                            contentDescription = if (previewMode || workspacePanel != "canvas") "Voltar ao editor" else "Abrir menu",
                            tint = EditorInk
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { saveSchema() }) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Salvar")
                    }
                    TextButton(onClick = { previewMode = !previewMode }) {
                        Icon(Icons.Default.Visibility, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (previewMode) "Editar" else "Prévia")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        if (previewMode) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    color = Color.White,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Color(0xFFDCE5EE))
                ) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(projectTitle, color = EditorInk, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(Modifier.weight(1f))
                            Text("PRÉVIA", color = EditorBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        elements.filter { it.parentId == null }.forEach { element ->
                            EditorCanvasNode(element, elements, null, editorColor(themePrimary, EditorBlue), editorColor(themeText, EditorInk)) {}
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { previewMode = false }, modifier = Modifier.weight(1f)) {
                        Text("Voltar ao editor")
                    }
                    Button(onClick = onProjects, modifier = Modifier.weight(1f)) {
                        Text("Deploy")
                    }
                }
            }
        } else if (workspacePanel == "canvas") {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(screenRegistry.firstOrNull { it.id == activeScreenId }?.name ?: "Início", color = EditorInk, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Editor de aplicativo · Android", color = EditorMuted, fontSize = 10.sp)
                    }
                    Surface(color = Color(0xFFEAF3FB), shape = RoundedCornerShape(18.dp)) {
                        Text("SALVAMENTO AUTOMÁTICO", modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp), color = EditorBlue, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Surface(
                        modifier = Modifier.fillMaxSize().padding(end = 48.dp),
                        color = Color.White,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color(0xFFDCE5EE)),
                        shadowElevation = 2.dp
                    ) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("9:41", color = EditorInk, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.weight(1f))
                                Text("● ● ●", color = EditorMuted, fontSize = 8.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(screenRegistry.firstOrNull { it.id == activeScreenId }?.name ?: "Meu aplicativo", color = EditorInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Default.MoreHoriz, null, tint = EditorMuted)
                            }
                            if (elements.none { it.parentId == null && it.visible }) {
                                Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.AddBox, null, tint = EditorBlue, modifier = Modifier.size(34.dp))
                                    Text("Sua tela começa aqui", color = EditorInk, fontWeight = FontWeight.SemiBold)
                                    Text("Toque em + para adicionar componentes", color = EditorMuted, fontSize = 11.sp, textAlign = TextAlign.Center)
                                }
                            } else {
                                elements.filter { it.parentId == null && it.visible }.forEach { element ->
                                    EditorCanvasNode(element, elements, selectedId, editorColor(themePrimary, EditorBlue), editorColor(themeText, EditorInk)) { selectedId = it }
                                }
                            }
                        }
                    }
                    Surface(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        color = Color.White,
                        shape = RoundedCornerShape(18.dp),
                        shadowElevation = 6.dp,
                        border = BorderStroke(1.dp, Color(0xFFE2E9F0))
                    ) {
                        Column(Modifier.padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = { workspacePanel = "advanced" }) { Icon(Icons.Default.Layers, "Hierarquia", tint = EditorInk) }
                            IconButton(onClick = { workspacePanel = "components" }) { Icon(Icons.Default.Add, "Adicionar componente", tint = EditorBlue) }
                            IconButton(onClick = { workspacePanel = "advanced" }) { Icon(Icons.Default.Tune, "Propriedades", tint = EditorInk) }
                            IconButton(onClick = { saveSchema(); message = "Alterações salvas" }) { Icon(Icons.Default.Save, "Salvar", tint = EditorInk) }
                            IconButton(onClick = { selectedId?.let { id -> elements.firstOrNull { it.id == id }?.let { item -> checkpoint(); elements.add(item.copy(id = UUID.randomUUID().toString(), text = item.text + " (cópia)")); selectedId = elements.last().id } } }) { Icon(Icons.Default.ContentCopy, "Duplicar", tint = EditorInk) }
                            IconButton(onClick = { undo() }, enabled = undoStack.isNotEmpty()) { Icon(Icons.Default.Undo, "Desfazer", tint = EditorInk) }
                            IconButton(onClick = { redo() }, enabled = redoStack.isNotEmpty()) { Icon(Icons.Default.Redo, "Refazer", tint = EditorInk) }
                            IconButton(onClick = { deleteSelected() }, enabled = selectedId != null) { Icon(Icons.Default.DeleteOutline, "Excluir", tint = Color(0xFFB33A3A)) }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { previewMode = true; saveSchema() }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(5.dp))
                        Text("Testar")
                    }
                    Button(onClick = { saveSchema(); onProjects() }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.CloudUpload, null)
                        Spacer(Modifier.width(5.dp))
                        Text("Deploy")
                    }
                }
                Text(message, color = EditorMuted, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        } else {
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (workspacePanel == "advanced") {
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
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Telas do aplicativo", color = EditorInk, fontWeight = FontWeight.Bold)
                            Text("${screenRegistry.size} tela(s) · cada tela possui seus próprios componentes", color = EditorMuted, fontSize = 11.sp)
                        }
                        Button(onClick = { addScreen() }, enabled = screenRegistry.size < StudioScreenDocument.MAX_SCREENS) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Nova tela")
                        }
                    }
                    screenRegistry.forEach { screen ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(
                                selected = activeScreenId == screen.id,
                                onClick = { switchScreen(screen.id) },
                                label = { Text(screen.name, maxLines = 1) },
                                leadingIcon = if (activeScreenId == screen.id) {
                                    { Icon(Icons.Default.PhoneAndroid, null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = {
                                renamingScreenId = screen.id
                                renameValue = screen.name
                            }) {
                                Text("Renomear", fontSize = 10.sp)
                            }
                            TextButton(onClick = { setStartScreen(screen.id) }) {
                                Text(if (startScreenId == screen.id) "Tela inicial" else "Definir início", fontSize = 10.sp)
                            }
                        }
                        Text("ID: ${screen.id}", color = EditorMuted, fontSize = 9.sp)
                    }
                    if (renamingScreenId != null) {
                        AlertDialog(
                            onDismissRequest = { renamingScreenId = null },
                            title = { Text("Renomear tela") },
                            text = {
                                OutlinedTextField(
                                    value = renameValue,
                                    onValueChange = { renameValue = it.take(StudioScreenDocument.MAX_NAME_LENGTH) },
                                    label = { Text("Nome da tela") },
                                    singleLine = true
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    val index = screenRegistry.indexOfFirst { it.id == renamingScreenId }
                                    if (index >= 0 && renameValue.isNotBlank()) {
                                        screenRegistry[index] = screenRegistry[index].copy(name = renameValue.trim())
                                        prefs.edit().putString(
                                            screenRegistryPrefKey,
                                            StudioScreenDocument(screenRegistry.toList(), startScreenId).toJson()
                                        ).putBoolean("studio_project_migrated_$projectKey", true).apply()
                                        message = "Nome da tela atualizado"
                                    }
                                    renamingScreenId = null
                                }, enabled = renameValue.isNotBlank()) { Text("Salvar") }
                            },
                            dismissButton = {
                                TextButton(onClick = { renamingScreenId = null }) { Text("Cancelar") }
                            }
                        )
                    }
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
                                EditorCanvasNode(element = element, allElements = elements, selectedId = selectedId, primaryColor = editorColor(themePrimary, EditorBlue), defaultTextColor = editorColor(themeText, EditorInk), onSelect = { selectedId = it })
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
                        OutlinedTextField(
                            value = selected.backgroundColor,
                            onValueChange = { value -> updateSelected { it.copy(backgroundColor = value.take(9)) } },
                            label = { Text("Fundo do componente (HEX)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = selected.textColor,
                            onValueChange = { value -> updateSelected { it.copy(textColor = value.take(9)) } },
                            label = { Text("Cor do texto (HEX)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Arredondamento: ${selected.cornerRadius.toInt()} dp", color = EditorInk, fontSize = 12.sp)
                        Slider(value = selected.cornerRadius, onValueChange = { value -> updateSelected { it.copy(cornerRadius = value) } }, valueRange = 0f..32f, steps = 7)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Componente visível", color = EditorInk, modifier = Modifier.weight(1f))
                            Switch(checked = selected.visible, onCheckedChange = { value -> updateSelected { it.copy(visible = value) } })
                        }
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
                            it.type in setOf("section", "column", "row", "card") && it.id != selectedId && it.id !in descendantIds
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
                                        checkpoint()
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
                                        checkpoint()
                                        val nextId = siblingIds[siblingPosition + 1]
                                        val item = elements.removeAt(elements.indexOfFirst { it.id == selected.id })
                                        val targetIndex = elements.indexOfFirst { it.id == nextId }
                                        elements.add(targetIndex + 1, item)
                                    }
                                },
                                enabled = siblingPosition >= 0 && siblingPosition < siblingIds.lastIndex
                            ) { Icon(Icons.Default.ArrowDownward, null); Spacer(Modifier.width(4.dp)); Text("Descer") }
                            OutlinedButton(
                                onClick = { deleteSelected() },
                                enabled = selectedIndex >= 0,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB33A3A))
                            ) { Icon(Icons.Default.DeleteOutline, null); Spacer(Modifier.width(4.dp)); Text("Excluir") }
                        }
                    }
                }
            }
            StudioLogicPanel(
                context = context,
                storageKeySuffix = projectKey,
                screens = screenRegistry.toList(),
                components = screenRegistry.flatMap { screen ->
                    val screenElements = if (screen.id == activeScreenId) {
                        elements.toList()
                    } else {
                        decodeElements(prefs.getString("${EDITOR_SCHEMA_KEY}_${screen.id}", null))
                    }
                    screenElements.map { element ->
                        StudioComponentDefinition(
                            id = element.id,
                            label = "${screen.name} · ${element.type}: ${element.text.ifBlank { "Sem texto" }}"
                        )
                    }
                }
            )
            } else {
                when (workspacePanel) {
                    "components" -> {
                        Text("Adicione elementos à tela. Os componentes podem ser aninhados em seções, linhas, colunas e cartões.", color = EditorMuted, fontSize = 12.sp)
                        val palette = listOf(
                            Triple("Seção", "section", Icons.Default.FolderOpen),
                            Triple("Texto", "text", Icons.Default.TextFields),
                            Triple("Botão", "button", Icons.Default.SmartButton),
                            Triple("Cartão", "card", Icons.Default.CropSquare),
                            Triple("Campo", "input", Icons.Default.Edit),
                            Triple("Imagem", "image", Icons.Default.Image),
                            Triple("Espaço", "spacer", Icons.Default.SpaceBar),
                            Triple("Divisor", "divider", Icons.Default.Add),
                            Triple("Linha", "row", Icons.Default.Add),
                            Triple("Coluna", "column", Icons.Default.Add)
                        )
                        palette.forEach { (label, type, icon) ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    val defaultText = when (type) {
                                        "button" -> "Continuar"
                                        "image" -> "Imagem do projeto"
                                        "input" -> "Digite aqui"
                                        "section" -> "Nova seção"
                                        "row" -> "Nova linha"
                                        "column" -> "Nova coluna"
                                        "divider" -> "Divisor"
                                        "spacer" -> "Espaço"
                                        else -> "Novo texto"
                                    }
                                    addElement(type, defaultText)
                                    workspacePanel = "canvas"
                                },
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(icon, null, tint = EditorBlue)
                                    Spacer(Modifier.width(12.dp))
                                    Text(label, color = EditorInk, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    Icon(Icons.Default.Add, null, tint = EditorBlue)
                                }
                            }
                        }
                    }
                    "pages" -> {
                        Text("Gerencie as telas e escolha qual será aberta primeiro.", color = EditorMuted, fontSize = 12.sp)
                        Button(onClick = { addScreen() }, enabled = screenRegistry.size < StudioScreenDocument.MAX_SCREENS, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Criar nova tela")
                        }
                        screenRegistry.forEach { screen ->
                            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(screen.name, color = EditorInk, fontWeight = FontWeight.SemiBold)
                                        Text(if (screen.id == startScreenId) "Tela inicial" else "Tela do aplicativo", color = EditorMuted, fontSize = 10.sp)
                                    }
                                    TextButton(onClick = { switchScreen(screen.id); workspacePanel = "canvas" }) { Text(if (screen.id == activeScreenId) "Aberta" else "Abrir") }
                                    if (screen.id != startScreenId) TextButton(onClick = { setStartScreen(screen.id) }) { Text("Início") }
                                }
                            }
                        }
                        TextButton(onClick = { workspacePanel = "advanced" }) { Text("Renomear telas e editar hierarquia") }
                    }
                    "theme" -> {
                        Text("Defina as cores principais do aplicativo. Use códigos hexadecimais como #1769AA.", color = EditorMuted, fontSize = 12.sp)
                        OutlinedTextField(value = themePrimary, onValueChange = { themePrimary = it.take(9) }, label = { Text("Cor principal") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = themeBackground, onValueChange = { themeBackground = it.take(9) }, label = { Text("Fundo do aplicativo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = themeText, onValueChange = { themeText = it.take(9) }, label = { Text("Cor do texto") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(Modifier.size(36.dp), color = runCatching { Color(android.graphics.Color.parseColor(themePrimary)) }.getOrDefault(EditorBlue), shape = CircleShape) {}
                            Surface(Modifier.size(36.dp), color = runCatching { Color(android.graphics.Color.parseColor(themeBackground)) }.getOrDefault(Color.White), shape = CircleShape, border = BorderStroke(1.dp, Color.LightGray)) {}
                            Surface(Modifier.size(36.dp), color = runCatching { Color(android.graphics.Color.parseColor(themeText)) }.getOrDefault(EditorInk), shape = CircleShape) {}
                        }
                        Button(onClick = {
                            val valid = listOf(themePrimary, themeBackground, themeText).all { runCatching { android.graphics.Color.parseColor(it) }.isSuccess }
                            if (valid) {
                                prefs.edit().putString(themePrimaryPrefKey, themePrimary).putString(themeBackgroundPrefKey, themeBackground).putString(themeTextPrefKey, themeText).apply()
                                message = "Tema salvo"
                            } else message = "Use cores hexadecimais válidas, por exemplo #1769AA"
                        }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(6.dp)); Text("Salvar tema") }
                    }
                    "assets" -> {
                        Text("Arquivos vinculados ao projeto. Os arquivos são mantidos no armazenamento original do Android.", color = EditorMuted, fontSize = 12.sp)
                        Button(onClick = { assetPicker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.AttachFile, null); Spacer(Modifier.width(6.dp)); Text("Selecionar arquivo do dispositivo")
                        }
                        OutlinedTextField(value = assetUrl, onValueChange = { assetUrl = it.take(2048) }, label = { Text("Ou adicionar URL https://") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedButton(onClick = {
                            if (assetUrl.startsWith("https://") || assetUrl.startsWith("http://")) {
                                if (assetUrl !in savedAssets) savedAssets.add(assetUrl)
                                prefs.edit().putString(assetsPrefKey, JSONArray(savedAssets).toString()).apply()
                                assetUrl = ""; message = "URL adicionada aos assets"
                            } else message = "Informe uma URL http ou https válida"
                        }, modifier = Modifier.fillMaxWidth()) { Text("Adicionar URL") }
                        if (savedAssets.isEmpty()) Text("Nenhum asset adicionado ainda.", color = EditorMuted, fontSize = 12.sp)
                        savedAssets.forEach { asset ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Description, null, tint = EditorBlue)
                                Spacer(Modifier.width(8.dp))
                                Text(asset, color = EditorInk, fontSize = 10.sp, modifier = Modifier.weight(1f), maxLines = 2)
                                IconButton(onClick = { savedAssets.remove(asset); prefs.edit().putString(assetsPrefKey, JSONArray(savedAssets).toString()).apply() }) { Icon(Icons.Default.DeleteOutline, "Remover asset", tint = Color(0xFFB33A3A)) }
                            }
                        }
                    }
                    "logic" -> StudioLogicPanel(
                        context = context,
                        storageKeySuffix = projectKey,
                        screens = screenRegistry.toList(),
                        components = screenRegistry.flatMap { screen ->
                            val screenElements = if (screen.id == activeScreenId) elements.toList() else decodeElements(prefs.getString("\${EDITOR_SCHEMA_KEY}_\${screen.id}", null))
                            screenElements.map { StudioComponentDefinition(it.id, "\${screen.name} · \${it.type}: \${it.text.ifBlank { "Sem texto" }}") }
                        }
                    )
                    "code" -> {
                        val projectJson = buildProjectJson()
                        Text("Exporte um projeto Android nativo com Kotlin, Jetpack Compose, Manifest e arquivos Gradle. As ações declarativas e os assets externos ainda precisam ser conectados ao projeto gerado.", color = EditorMuted, fontSize = 12.sp)
                        OutlinedTextField(
                            value = generatedPackage,
                            onValueChange = { generatedPackage = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '.' }.take(120) },
                            label = { Text("Package name do app gerado") },
                            supportingText = { Text("Ex.: com.minhaempresa.meuapp") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(onClick = {
                            if (!generatedPackage.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))) {
                                message = "Package inválido. Use o formato com.exemplo.aplicativo"
                            } else {
                            saveSchema()
                            val generatedComponents = screenRegistry.associate { screen ->
                                val sourceElements = if (screen.id == activeScreenId) elements.toList()
                                    else decodeElements(prefs.getString("${EDITOR_SCHEMA_KEY}_${screen.id}", null))
                                screen.id to sourceElements.map { item ->
                                    StudioGeneratedElement(item.id, item.type, item.text, item.fontSize, item.padding, item.alignment, item.parentId, item.backgroundColor, item.textColor, item.cornerRadius, item.visible)
                                }
                            }
                            prefs.edit().putString(generatedPackagePrefKey, generatedPackage).apply()
                            val files = StudioProjectGenerator.files(
                                packageName = generatedPackage,
                                appName = projectTitle,
                                screens = screenRegistry.toList(),
                                components = generatedComponents,
                                startScreenId = startScreenId,
                                primaryColor = themePrimary,
                                backgroundColor = themeBackground,
                                textColor = themeText
                            ).toMutableMap()
                            files["geivia-project.json"] = buildProjectJson()
                            files["assets.json"] = JSONArray(savedAssets).toString(2)
                            pendingExportFiles = files
                            val fileName = "GEIVIA_" + projectTitle.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".zip"
                            projectExportLauncher.launch(fileName)
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Archive, null); Spacer(Modifier.width(7.dp)); Text("Exportar projeto Android (.ZIP)")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("GEIVIA project JSON", projectJson))
                                message = "Estrutura copiada"
                            }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(5.dp)); Text("Copiar JSON") }
                            OutlinedButton(onClick = {
                                val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("application/json").putExtra(android.content.Intent.EXTRA_TEXT, projectJson)
                                context.startActivity(android.content.Intent.createChooser(send, "Exportar projeto GEIVIA"))
                            }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(5.dp)); Text("Exportar") }
                        }
                        Surface(color = Color(0xFF132536), shape = RoundedCornerShape(12.dp)) {
                            Text(projectJson, color = Color(0xFFE5EDF5), fontSize = 9.sp, modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()).padding(12.dp))
                        }
                    }
                    else -> Text("Selecione uma ferramenta no menu do Studio.", color = EditorMuted)
                }
            }
            Text(message, color = EditorMuted, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        }
    }
    }
}


private fun editorColor(value: String, fallback: Color): Color =
    runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrDefault(fallback)

@Composable
private fun EditorCanvasNode(
    element: StudioElement,
    allElements: List<StudioElement>,
    selectedId: String?,
    primaryColor: Color = EditorBlue,
    defaultTextColor: Color = EditorInk,
    onSelect: (String) -> Unit
) {
    if (!element.visible) return
    val children = allElements.filter { it.parentId == element.id && it.visible }
    val selected = element.id == selectedId
    val defaultBackground = if (element.type == "button") primaryColor else Color.White
    val defaultText = if (element.type == "button") Color.White else defaultTextColor
    val componentBackground = if (element.type == "button" && element.backgroundColor == "#FFFFFF") primaryColor else editorColor(element.backgroundColor, defaultBackground)
    val componentText = if (element.type == "button" && element.textColor == "#17354D") Color.White else editorColor(element.textColor, defaultText)
    val container = element.type in setOf("section", "column", "row") || (element.type == "card" && children.isNotEmpty())
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onSelect(element.id) },
        color = if (selected) Color(0xFFEAF3FB) else componentBackground,
        shape = RoundedCornerShape(element.cornerRadius.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) EditorBlue else Color(0xFFE3EAF1))
    ) {
        Column(Modifier.fillMaxWidth().padding(element.padding.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (container) {
                if (element.type != "row" || element.text.isNotBlank()) {
                    Text(element.text.ifBlank { if (element.type == "row") "Linha" else "Contêiner" }, color = componentText, fontWeight = FontWeight.SemiBold, fontSize = element.fontSize.sp)
                }
                if (children.isEmpty()) {
                    Text("Adicione componentes a este contêiner", color = EditorMuted, fontSize = 10.sp)
                } else if (element.type == "row") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        children.forEach { child ->
                            Box(Modifier.weight(1f)) { EditorCanvasNode(child, allElements, selectedId, primaryColor, defaultTextColor, onSelect) }
                        }
                    }
                } else {
                    children.forEach { child -> EditorCanvasNode(child, allElements, selectedId, primaryColor, defaultTextColor, onSelect) }
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
                        Surface(color = componentBackground, shape = RoundedCornerShape(element.cornerRadius.dp)) {
                            Text(element.text, color = componentText, fontWeight = FontWeight.SemiBold, fontSize = element.fontSize.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
                        }
                    }
                    "card" -> Surface(color = componentBackground, shape = RoundedCornerShape(element.cornerRadius.dp), border = BorderStroke(1.dp, Color(0xFFDCE5EE))) {
                        Text(element.text, color = componentText, modifier = Modifier.fillMaxWidth().padding(12.dp), fontWeight = FontWeight.Medium, fontSize = element.fontSize.sp, textAlign = align)
                    }
                    "input" -> OutlinedTextField(value = "", onValueChange = {}, readOnly = true, enabled = false, label = { Text(element.text) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    "image" -> Surface(color = Color(0xFFF0F4F8), shape = RoundedCornerShape(element.cornerRadius.dp), border = BorderStroke(1.dp, Color(0xFFDCE5EE))) {
                        Column(Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.Image, null, tint = EditorMuted, modifier = Modifier.size(28.dp))
                            Text(element.text, color = EditorMuted, fontSize = 10.sp)
                        }
                    }
                    "divider" -> HorizontalDivider(color = Color(0xFFDCE5EE))
                    "spacer" -> Spacer(Modifier.fillMaxWidth().height(element.padding.coerceAtLeast(8f).dp))
                    else -> Text(element.text, color = componentText, fontSize = element.fontSize.sp, textAlign = align, modifier = Modifier.fillMaxWidth())
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
