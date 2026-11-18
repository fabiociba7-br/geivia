package com.geivia.app

import android.content.Context
import java.io.File
import java.util.UUID

/**
 * Best-effort parser that imports existing Kotlin/Compose screens into GEIVIA Studio's
 * editable visual model. It is a source scanner, not a complete Kotlin compiler parser;
 * original source files remain untouched.
 */
internal object ComposeToStudioParser {
    data class ParsedProject(
        val screens: List<StudioScreenDefinition>,
        val elementsByScreen: Map<String, List<StudioElement>>,
        val startScreenId: String
    )

    fun parse(projectRoot: File): ParsedProject? {
        if (!projectRoot.isDirectory) return null
        val ktFiles = projectRoot.walkTopDown()
            .onEnter { it == projectRoot.canonicalFile || it.name !in ProjectWorkspaceInfo.IGNORED_DIRS }
            .filter { it.isFile && it.extension == "kt" && it.length() <= 2L * 1024 * 1024 }
            .take(200)
            .toList()
        if (ktFiles.isEmpty()) return null

        val screens = mutableListOf<StudioScreenDefinition>()
        val elements = mutableMapOf<String, MutableList<StudioElement>>()
        ktFiles.forEach { file ->
            val source = runCatching { file.readText() }.getOrNull() ?: return@forEach
            extractComposableFunctions(source).forEach functionLoop@{ (name, body) ->
                if (screens.size >= StudioScreenDocument.MAX_SCREENS) return@functionLoop
                val screenId = nameToId(name)
                if (screens.any { it.id == screenId }) return@functionLoop
                val components = extractComponents(body)
                if (components.isEmpty() && !name.startsWith("Screen_")) return@functionLoop
                screens += StudioScreenDefinition(id = screenId, name = nameToTitle(name))
                elements[screenId] = components.toMutableList()
            }
        }

        if (screens.isEmpty()) {
            val main = ktFiles.firstOrNull { it.name.contains("MainActivity", true) || it.name.contains("Screen", true) }
                ?: ktFiles.first()
            val source = runCatching { main.readText() }.getOrNull() ?: return null
            val components = extractComponents(source)
            if (components.isEmpty()) return null
            val id = "imported"
            screens += StudioScreenDefinition(id = id, name = main.nameWithoutExtension)
            elements[id] = components.toMutableList()
        }

        val start = screens.firstOrNull { it.id == "home" || it.name.equals("Início", true) }?.id
            ?: screens.first().id
        return ParsedProject(screens, elements, start)
    }

    fun persistToStudio(context: Context, projectRoot: File): ParsedProject? {
        val resolvedRoot = ProjectWorkspaceInfo.resolveRoot(projectRoot)
        val parsed = parse(resolvedRoot) ?: return null
        val projectKey = ("local_" + resolvedRoot.absolutePath.hashCode().toUInt().toString(16))
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
        val prefs = context.getSharedPreferences("geivia_onboarding", Context.MODE_PRIVATE)
        prefs.edit().putString(
            "geivia_studio_screens_v1_$projectKey",
            StudioScreenDocument(parsed.screens, parsed.startScreenId).toJson()
        ).apply()
        parsed.elementsByScreen.forEach { (screenId, items) ->
            val array = org.json.JSONArray()
            items.forEach { item ->
                array.put(org.json.JSONObject()
                    .put("id", item.id).put("type", item.type).put("text", item.text)
                    .put("fontSize", item.fontSize).put("padding", item.padding)
                    .put("alignment", item.alignment).put("visible", item.visible))
            }
            prefs.edit().putString("geivia_studio_schema_v1_${projectKey}_$screenId", array.toString()).apply()
        }
        return parsed
    }

    private fun extractComposableFunctions(source: String): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()
        val regex = Regex("""@Composable\s+(?:private\s+|internal\s+|public\s+)?fun\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(""")
        var searchFrom = 0
        while (true) {
            val match = regex.find(source, searchFrom) ?: break
            val name = match.groupValues[1]
            val startBrace = source.indexOf('{', match.range.last)
            if (startBrace < 0) break
            val endBrace = findMatchingBrace(source, startBrace)
            if (endBrace < 0) break
            results += name to source.substring(startBrace, endBrace + 1)
            searchFrom = endBrace + 1
        }
        return results
    }

    private fun findMatchingBrace(source: String, openIndex: Int): Int {
        var depth = 0
        for (i in openIndex until source.length) {
            when (source[i]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return i
            }
        }
        return -1
    }

    private fun extractComponents(body: String): List<StudioElement> {
        val list = mutableListOf<StudioElement>()
        Regex("""Text\s*\(\s*(?:resolve\s*\(\s*)?"([^"]{1,120})"""").findAll(body).forEach { match ->
            list += StudioElement(type = "text", text = unescape(match.groupValues[1]))
        }
        Regex("""Button\s*\([^)]*\)\s*\{\s*Text\s*\(\s*(?:resolve\s*\(\s*)?"([^"]{1,80})"""").findAll(body).forEach { match ->
            list += StudioElement(type = "button", text = unescape(match.groupValues[1]))
        }
        Regex("""Card\s*\([^)]*\)\s*\{[^}]*Text\s*\(\s*(?:resolve\s*\(\s*)?"([^"]{1,80})"""").findAll(body).forEach { match ->
            list += StudioElement(type = "card", text = unescape(match.groupValues[1]))
        }
        Regex("""(?:Outlined)?TextField\s*\(""").findAll(body).forEach { list += StudioElement(type = "field", text = "") }
        Regex("""Image\s*\(""").findAll(body).forEach { list += StudioElement(type = "image", text = "") }
        if (body.contains("Spacer(")) list += StudioElement(type = "spacer", text = "")
        if (body.contains("HorizontalDivider(") || body.contains("Divider(")) list += StudioElement(type = "divider", text = "")
        return list.distinctBy { it.type + "|" + it.text }.take(40)
    }

    private fun unescape(value: String): String =
        value.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")

    private fun nameToId(name: String): String =
        name.removePrefix("Screen_").replace(Regex("[^A-Za-z0-9]"), "_").lowercase()
            .take(64).ifBlank { "screen_" + UUID.randomUUID().toString().take(6) }

    private fun nameToTitle(name: String): String =
        name.removePrefix("Screen_").replace(Regex("([a-z])([A-Z])"), "$1 $2").replace('_', ' ')
            .trim().take(StudioScreenDocument.MAX_NAME_LENGTH).ifBlank { "Tela" }
}
