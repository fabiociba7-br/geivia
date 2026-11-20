package com.geivia.app

internal data class StudioGeneratedElement(
    val id: String,
    val type: String,
    val text: String,
    val fontSize: Float,
    val padding: Float,
    val alignment: String,
    val parentId: String?,
    val backgroundColor: String,
    val textColor: String,
    val cornerRadius: Float,
    val visible: Boolean,
    val weight: Float? = null,
    val fillMaxWidth: Boolean = true,
    val heightDp: Float? = null,
    val enabled: Boolean = true,
    val placeholder: String? = null,
    val contentScale: String = "fit"
)

internal object StudioProjectGenerator {
    fun files(
        packageName: String,
        appName: String,
        screens: List<StudioScreenDefinition>,
        components: Map<String, List<StudioGeneratedElement>>,
        startScreenId: String,
        primaryColor: String,
        backgroundColor: String,
        textColor: String,
        logic: StudioLogicDocument = StudioLogicDocument()
    ): Map<String, String> {
        val pkg = packageName.split(".").joinToString(".") { part ->
            part.filter { it.isLetterOrDigit() || it.code == 95 }.ifBlank { "app" }
        }
        val app = appName.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;").replace("\n", " ").replace("\r", " ")
        val projectSlug = appName.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "GEIVIAApp" }
        val start = screens.firstOrNull { it.id == startScreenId } ?: screens.firstOrNull()
        val source = buildString {
            appendLine("package " + pkg)
            appendLine()
            listOf(
                "android.os.Bundle", "android.graphics.BitmapFactory", "androidx.activity.ComponentActivity", "androidx.activity.compose.setContent",
                "androidx.compose.foundation.Image", "androidx.compose.foundation.background", "androidx.compose.foundation.clickable", "androidx.compose.ui.draw.clip", "androidx.compose.foundation.layout.*",
                "androidx.compose.foundation.rememberScrollState", "androidx.compose.foundation.shape.RoundedCornerShape",
                "androidx.compose.foundation.verticalScroll", "androidx.compose.material3.*",
                "androidx.compose.runtime.*", "androidx.compose.ui.Modifier", "androidx.compose.ui.graphics.Color",
                "androidx.compose.ui.graphics.asImageBitmap", "androidx.compose.ui.layout.ContentScale", "androidx.compose.ui.focus.onFocusChanged",
                "androidx.compose.ui.platform.LocalContext", "androidx.compose.ui.text.style.TextAlign",
                "androidx.compose.ui.unit.dp", "androidx.compose.ui.unit.sp", "kotlinx.coroutines.Dispatchers",
                "kotlinx.coroutines.withContext"
            ).forEach { appendLine("import " + it) }
            appendLine()
            appendLine("class MainActivity : ComponentActivity() {")
            appendLine("    override fun onCreate(savedInstanceState: Bundle?) {")
            appendLine("        super.onCreate(savedInstanceState)")
            appendLine("        setContent { MaterialTheme { GeneratedApp() } }")
            appendLine("    }")
            appendLine("}")
            appendLine()
            appendLine("@Composable")
            appendLine("private fun GeneratedApp() {")
            appendLine("    var current by remember { mutableStateOf(" + quote(start?.id ?: "home") + ") }")
            appendLine("    var message by remember { mutableStateOf(\"\") }")
            appendLine("    var navigationCount by remember { mutableIntStateOf(0) }")
            appendLine("    val variables = remember { mutableStateMapOf<String, String>().apply {")
            logic.variables.forEach { variable -> appendLine("        put(" + quote(variable.key) + ", " + quote(variable.initialValue) + ")") }
            start?.inputParameters?.forEach { parameter -> appendLine("        put(" + quote(parameter.key) + ", " + quote(parameter.defaultValue) + ")") }
            appendLine("    } }")
            appendLine("    fun resolve(expression: String, value: String = \"\"): String = Regex(\"\\\\$\\\\{([A-Za-z_][A-Za-z0-9_]*)\\\\}\").replace(expression) { match -> if (match.groupValues[1] == \"value\") value else variables[match.groupValues[1]].orEmpty() }")
            appendLine("    fun runAction(actionId: String, value: String = \"\") {")
            appendLine("        when (actionId) {")
            logic.actions.filter { it.enabled }.forEach { action ->
                appendLine("            " + quote(action.id) + " -> {")
                when (action.kind) {
                    "navigate" -> {
                        appendLine("                if (navigationCount < 25) {")
                        screens.firstOrNull { it.id == action.target }?.inputParameters?.forEach { parameter ->
                            appendLine("                    variables[" + quote(parameter.key) + "] = " + quote(parameter.defaultValue))
                        }
                        action.navigationParameters.forEach { parameter ->
                            appendLine("                    variables[" + quote(parameter.key) + "] = resolve(" + quote(parameter.value) + ", value)")
                        }
                        val requiredKeys = screens.firstOrNull { it.id == action.target }?.inputParameters
                            ?.filter { it.required }?.map { it.key }.orEmpty()
                        appendLine("                    val missingRequired = listOf<String>(" + requiredKeys.joinToString(", ") { quote(it) } + ").filter { variables[it].orEmpty().isBlank() }")
                        appendLine("                    if (missingRequired.isEmpty()) {")
                        appendLine("                        navigationCount += 1")
                        appendLine("                        current = " + quote(action.target))
                        appendLine("                    } else {")
                        appendLine("                        message = \"Informe os campos obrigatórios: \" + missingRequired.joinToString(\", \")")
                        appendLine("                    }")
                        appendLine("                }")
                    }
                    "set_variable" -> appendLine("                variables[" + quote(action.target) + "] = resolve(" + quote(action.expression) + ", value)")
                    "show_message" -> appendLine("                message = resolve(" + quote(action.expression) + ", value)")
                }
                appendLine("            }")
            }
            appendLine("        }")
            appendLine("    }")
            appendLine("    // Action contract shared with Studio preview: on_click, on_value_change, on_screen_open; payload is the new field value.")
            appendLine("    fun runActions(componentId: String, trigger: String, value: String = \"\") {")
            appendLine("        if (trigger != \"on_screen_open\") navigationCount = 0")
            appendLine("        when (componentId + \"|\" + trigger) {")
            logic.actions.filter { it.enabled }.groupBy { it.componentId + "|" + it.trigger }.forEach { (key, actions) ->
                appendLine("            " + quote(key) + " -> {")
                actions.forEach { action -> appendLine("                runAction(" + quote(action.id) + ", value)") }
                appendLine("            }")
            }
            appendLine("        }")
            appendLine("    }")
            appendLine("    LaunchedEffect(current) { runActions(\"\", \"on_screen_open\") }")
            appendLine("    Column(Modifier.fillMaxSize()) {")
            appendLine("        Box(Modifier.weight(1f)) {")
            appendLine("            when (current) {")
            screens.forEach { screen ->
                appendLine("                " + quote(screen.id) + " -> Screen_" + identifier(screen.id) + "({ componentId, trigger, value -> runActions(componentId, trigger, value) }, { expression, value -> resolve(expression, value) })")
            }
            appendLine("                else -> Screen_" + identifier(start?.id ?: "home") + "({ componentId, trigger, value -> runActions(componentId, trigger, value) }, { expression, value -> resolve(expression, value) })")
            appendLine("            }")
            appendLine("        }")
            appendLine("        if (message.isNotBlank()) Text(message, Modifier.fillMaxWidth().padding(12.dp))")
            appendLine("    }")
            appendLine("}")
            screens.forEach { screen ->
                appendLine()
                appendLine("@Composable")
                appendLine("private fun Screen_" + identifier(screen.id) + "(runActions: (String, String, String) -> Unit, resolve: (String, String) -> String) {")
                appendLine("    Column(Modifier.fillMaxSize().background(" + color(backgroundColor, "Color(0xFFF3F6FA)") + ").verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {")
                appendLine("        Text(resolve(" + quote(screen.name) + ", \"\"), fontSize = 22.sp, color = " + color(textColor, "Color(0xFF17354D)") + ")")
                val screenComponents = components[screen.id].orEmpty()
                screenComponents.filter { it.parentId == null && it.visible }.forEach {
                    append(render(it, screenComponents, 2, primaryColor, textColor))
                }
                appendLine("    }")
                appendLine("}")
            }
        }
        val manifest = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:theme="@android:style/Theme.Material.Light.NoActionBar" android:label="$app">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
"""
        val settings = """pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "$projectSlug"
include(":app")
"""
        val rootGradle = """plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
"""
        val appGradle = """plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "$pkg"
    compileSdk = 35
    defaultConfig {
        applicationId = "$pkg"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures { compose = true }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
}
"""
        val readme = "# " + appName + "\n\nProjeto Android Kotlin/Jetpack Compose gerado pelo GEIVIA Studio.\nAbra no Android Studio e sincronize o Gradle.\nO runtime gerado executa ações declarativas de navegação entre telas, alteração de variáveis e exibição de mensagens. Integrações remotas, chamadas de API e regras de negócio externas ainda precisam ser implementadas separadamente. Assets locais selecionados são incluídos em app/src/main/assets; URLs externas precisam ser baixadas e associadas manualmente.\n\nPackage: " + pkg + "\n"
        return linkedMapOf(
            "settings.gradle.kts" to settings,
            "gradle.properties" to "android.useAndroidX=true\norg.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8\nkotlin.code.style=official\n",
            "build.gradle.kts" to rootGradle,
            "app/build.gradle.kts" to appGradle,
            "app/src/main/AndroidManifest.xml" to manifest,
            "app/src/main/java/" + pkg.replace('.', '/') + "/MainActivity.kt" to source,
            "README.md" to readme
        )
    }

    private fun render(
        element: StudioGeneratedElement,
        all: List<StudioGeneratedElement>,
        depth: Int,
        primaryColor: String,
        defaultTextColor: String
    ): String {
        if (!element.visible) return ""
        val indent = "    ".repeat(depth)
        val text = quote(element.text)
        val size = element.fontSize.coerceIn(8f, 48f)
        val padding = element.padding.coerceIn(0f, 64f)
        val radius = element.cornerRadius.coerceIn(0f, 48f)
        val foreground = if (element.type.equals("button", true) && element.textColor == "#17354D") color("#FFFFFF", "Color.White")
            else if (element.textColor == "#17354D") color(defaultTextColor, "Color(0xFF17354D)")
            else color(element.textColor, "Color(0xFF17354D)")
        val background = if (element.type.equals("button", true) && element.backgroundColor == "#FFFFFF") color(primaryColor, "Color(0xFF1769AA)")
            else color(element.backgroundColor, "Color(0xFFFFFFFF)")
        val children = all.filter { it.parentId == element.id && it.visible }
        return buildString {
            val widthModifier = if (element.fillMaxWidth) "Modifier.fillMaxWidth()" else "Modifier"
            val heightModifier = element.heightDp?.let { ".height(" + it.coerceIn(0f, 2000f) + ".dp)" }.orEmpty()
            when (element.type.lowercase()) {
                "section", "column", "row" -> {
                    val isRow = element.type.equals("row", true)
                    val layout = if (isRow) "Row" else "Column"
                    val arrangement = if (isRow) "horizontalArrangement" else "verticalArrangement"
                    appendLine(indent + layout + "(" + widthModifier + heightModifier + ".clickable(enabled = " + element.enabled + ") { runActions(" + quote(element.id) + ", \"on_click\", \"\") }.background(" + background + ", RoundedCornerShape(" + radius + ".dp)).padding(" + padding + ".dp), " + arrangement + " = Arrangement.spacedBy(8.dp)) {")
                    if (element.text.isNotBlank()) appendLine(indent + "    Text(resolve(" + text + ", \"\"), fontSize = " + size + ".sp, color = " + foreground + ")")
                    children.forEach { child ->
                        if (isRow || child.weight != null) {
                            val childWeight = child.weight ?: 1f
                            appendLine(indent + "    Box(Modifier.weight(" + childWeight.coerceIn(0.01f, 100f) + "f)) {")
                            append(render(child, all, depth + 2, primaryColor, defaultTextColor))
                            appendLine(indent + "    }")
                        } else append(render(child, all, depth + 1, primaryColor, defaultTextColor))
                    }
                    appendLine(indent + "}")
                }
                    appendLine(indent + "Text(resolve(" + text + ", \"\\"), " + widthModifier + heightModifier + ".clickable(enabled = " + element.enabled + ") { runActions(" + quote(element.id) + ", \"on_click\", \"\") }.background(" + background + ", RoundedCornerShape(" + radius + ".dp)).padding(" + (padding + 4f) + ".dp), color = " + foreground + ", fontSize = " + size + ".sp, textAlign = " + alignment + ")")
                "outlined_button", "outlinedbutton" -> appendLine(indent + "OutlinedButton(onClick = { runActions(" + quote(element.id) + ", \"on_click\", \"\") }, enabled = " + element.enabled + ", shape = RoundedCornerShape(" + radius + ".dp), modifier = " + widthModifier + heightModifier + ".padding(" + padding + ".dp)) { Text(resolve(" + text + ", \"\"), fontSize = " + size + ".sp) }")
                "card" -> {
                    appendLine(indent + "Card(" + widthModifier + heightModifier + ".clickable(enabled = " + element.enabled + ") { runActions(" + quote(element.id) + ", \"on_click\", \"\") }, shape = RoundedCornerShape(" + radius + ".dp), colors = CardDefaults.cardColors(containerColor = " + background + ")) {")
                    appendLine(indent + "    Column(Modifier.fillMaxWidth().padding(" + padding + ".dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {")
                    if (children.isEmpty()) appendLine(indent + "        Text(resolve(" + text + ", \"\"), color = " + foreground + ", fontSize = " + size + ".sp)")
                    else children.forEach { append(render(it, all, depth + 2, primaryColor, defaultTextColor)) }
                    appendLine(indent + "    }")
                    appendLine(indent + "}")
                }
                "input", "textfield", "field", "edittext", "outlinedtextfield" -> {
                    val inputName = "input_" + identifier(element.id)
                    val focusName = "focused_" + identifier(element.id)
                    appendLine(indent + "var " + inputName + " by remember { mutableStateOf(\"\") }")
                    appendLine(indent + "var " + focusName + " by remember { mutableStateOf(false) }")
                    appendLine(indent + "OutlinedTextField(value = " + inputName + ", onValueChange = { " + inputName + " = it; runActions(" + quote(element.id) + ", \"on_value_change\", it) }, enabled = " + element.enabled + ", label = { Text(resolve(" + text + ", \"\")) }, placeholder = { Text(" + quote(element.placeholder.orEmpty()) + ") }, modifier = " + widthModifier + heightModifier + ".padding(" + padding + ".dp).onFocusChanged { focus -> if (focus.isFocused && !" + focusName + ") runActions(" + quote(element.id) + ", \"on_click\", \"\"); " + focusName + " = focus.isFocused }, singleLine = true)")
                }
                "image" -> {
                    if (element.text.startsWith("asset://")) {
                        val assetName = element.text.removePrefix("asset://")
                        val suffix = identifier(element.id)
                        appendLine(indent + "val imageContext_" + suffix + " = LocalContext.current")
                        appendLine(indent + "val imageBitmap_" + suffix + " by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = " + quote(assetName) + ") {")
                        appendLine(indent + "    value = withContext(Dispatchers.IO) { runCatching { imageContext_" + suffix + ".assets.open(" + quote(assetName) + ").use { BitmapFactory.decodeStream(it) } }.getOrNull() }")
                        appendLine(indent + "}")
                        appendLine(indent + "if (imageBitmap_" + suffix + " != null) Image(bitmap = imageBitmap_" + suffix + "!!.asImageBitmap(), contentDescription = null, contentScale = ContentScale." + (element.contentScale.equals("crop", true) ? "Crop" : "Fit") + ", modifier = " + widthModifier + heightModifier + ".clickable(enabled = " + element.enabled + ") { runActions(" + quote(element.id) + ", \"on_click\", \"\") }.padding(" + padding + ".dp).clip(RoundedCornerShape(" + radius + ".dp)) else Text(" + quote("Imagem não disponível: " + element.text) + ", color = Color.Gray)")
                    } else {
                        appendLine(indent + "Text(resolve(" + quote("[Imagem: " + element.text + "]") + ", \"\"), Modifier.clickable(enabled = " + element.enabled + ") { runActions(" + quote(element.id) + ", \"on_click\", \"\") }.padding(" + padding + ".dp), color = Color.Gray)")
                    }
                }
                "switch", "checkbox" -> {
                    val checkedName = "checked_" + identifier(element.id)
                    appendLine(indent + "var " + checkedName + " by remember { mutableStateOf(false) }")
                    appendLine(indent + "Row(" + widthModifier + ".padding(" + padding + ".dp), verticalAlignment = Alignment.CenterVertically) {")
                    appendLine(indent + "    Text(resolve(" + text + ", \"\"), Modifier.weight(1f), color = " + foreground + ")")
                    if (element.type.lowercase() == "switch") {
                        appendLine(indent + "    Switch(checked = " + checkedName + ", enabled = " + element.enabled + ", onCheckedChange = { " + checkedName + " = it; runActions(" + quote(element.id) + ", \"on_value_change\", it.toString()) })")
                    } else {
                        appendLine(indent + "    Checkbox(checked = " + checkedName + ", enabled = " + element.enabled + ", onCheckedChange = { " + checkedName + " = it; runActions(" + quote(element.id) + ", \"on_value_change\", it.toString()) })")
                    }
                    appendLine(indent + "}")
                }
                "divider" -> appendLine(indent + "HorizontalDivider(Modifier.padding(vertical = " + padding + ".dp))")
                "spacer" -> appendLine(indent + "Spacer(Modifier.height(" + (element.heightDp ?: padding.coerceAtLeast(8f)) + ".dp))")
                else -> {
                    val alignment = when (element.alignment) {
                        "center" -> "TextAlign.Center"
                        "end" -> "TextAlign.End"
                        else -> "TextAlign.Start"
                    }
appendLine(indent + "Text(resolve(" + text + ", \\"\\"), " + widthModifier + heightModifier + ".background(" + background + ", RoundedCornerShape(" + radius + ".dp)).padding(" + padding + ".dp).clickable(enabled = " + element.enabled + ") { runActions(" + quote(element.id) + ", \\"on_click\\", \\"\\") }, color = " + foreground + ", fontSize = " + size + ".sp, textAlign = " + alignment + ")")
                }
            }
        }
    }

    private fun identifier(value: String): String = value.split(Regex("[^A-Za-z0-9]+"))
        .filter { it.isNotBlank() }.joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }
        .ifBlank { "Screen" }

    private fun quote(value: String): String = "\"" + value.replace("\\", "\\\\")
        .replace("\"", "\\\"").replace("$", "\\$").replace("\n", "\\n").replace("\r", "\\r") + "\""

    private fun color(value: String, fallback: String): String {
        val hex = value.removePrefix("#")
        return when {
            hex.length == 6 && hex.all { it.digitToIntOrNull(16) != null } -> "Color(0xFF" + hex + ")"
            hex.length == 8 && hex.all { it.digitToIntOrNull(16) != null } -> "Color(0x" + hex + ")"
            else -> fallback
        }
    }
}
