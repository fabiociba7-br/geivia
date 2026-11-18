package com.geivia.app

import java.io.File

/**
 * Read-only visual index of existing screens. This is intentionally a preview model,
 * not a Kotlin/XML source rewriter: original project files are never modified.
 */
internal data class ImportedScreenComponent(
    val type: String,
    val id: String? = null,
    val text: String? = null
)

internal data class ImportedScreen(
    val name: String,
    val sourcePath: String,
    val format: Format,
    val components: List<ImportedScreenComponent>
) {
    enum class Format { ANDROID_XML, JETPACK_COMPOSE }
}

internal object ImportedScreenDiscovery {
    private val composeAnnotation = Regex(
        """@Composable(?:\s*\([^)]*\))?\s+(?:(?:private|internal|public|protected|inline|tailrec|operator|suspend|override)\s+)*fun\s+([A-Za-z_][A-Za-z0-9_]*)\s*\("""
    )
    private val composeCalls = listOf(
        "Column", "Row", "Box", "LazyColumn", "LazyRow", "Text", "Button",
        "OutlinedButton", "IconButton", "TextField", "OutlinedTextField",
        "Image", "Card", "Surface", "Scaffold", "TopAppBar", "NavigationBar",
        "Checkbox", "RadioButton", "Switch", "Slider", "Spacer", "HorizontalDivider"
    )
    private val xmlTag = Regex("""<([A-Za-z_][A-Za-z0-9_.]*)\b([^<>]*?)(?:/?>)""")
    private val xmlId = Regex("""(?:android:)?id\s*=\s*["']@\+?id/([^"']+)["']""")
    private val xmlText = Regex("""(?:android:)?text\s*=\s*["']([^"']*)["']""")
    private val composeText = Regex("""\bText\s*\(\s*(?:text\s*=\s*)?"([^"]{1,160})"""")
    private val composeButton = Regex("""\b(?:Button|OutlinedButton)\s*\([^)]*\)\s*\{\s*Text\s*\(\s*(?:text\s*=\s*)?"([^"]{1,100})"""")
    private val composeField = Regex("""\b(?:OutlinedTextField|TextField)\s*\(""")
    private val composeImage = Regex("""\bImage\s*\(""")

    fun scan(projectRoot: File, maxFiles: Int = 5000): List<ImportedScreen> {
        val root = projectRoot.canonicalFile
        require(root.isDirectory) { "A pasta do projeto não existe." }
        val sourceFiles = root.walkTopDown()
            .onEnter { dir -> dir == root || dir.name !in ProjectWorkspaceInfo.IGNORED_DIRS }
            .filter { it.isFile }
            .filter { file ->
                val path = file.relativeTo(root).invariantSeparatorsPath
                (file.extension.equals("xml", true) && Regex("""(^|/)res/layout[^/]*/""").containsMatchIn(path)) ||
                    (file.extension.equals("kt", true) && (path.startsWith("src/main/") || path.contains("/src/main/")))
            }
            .take(maxFiles.coerceIn(1, 10000))
            .toList()

        return sourceFiles.flatMap { file ->
            val relative = file.relativeTo(root).invariantSeparatorsPath
            if (file.length() > MAX_SOURCE_BYTES) return@flatMap emptyList()
            val source = runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
                ?.takeIf { !it.contains('\u0000') } ?: return@flatMap emptyList()
            when {
                file.extension.equals("xml", true) -> parseXml(relative, source)
                file.extension.equals("kt", true) -> parseCompose(relative, source)
                else -> emptyList()
            }
        }.distinctBy { it.sourcePath to it.name }
            .sortedWith(compareBy<ImportedScreen>({ it.format.ordinal }, { it.sourcePath }, { it.name }))
    }

    private fun parseXml(path: String, source: String): List<ImportedScreen> {
        val components = xmlTag.findAll(source).mapNotNull { match ->
            val tag = match.groupValues[1]
            if (tag in setOf("resources", "item", "style", "string", "color", "dimen", "declare-styleable")) {
                null
            } else {
                val attrs = match.groupValues[2]
                ImportedScreenComponent(
                    type = tag.substringAfterLast('.'),
                    id = xmlId.find(attrs)?.groupValues?.getOrNull(1),
                    text = xmlText.find(attrs)?.groupValues?.getOrNull(1)?.replace("&amp;", "&")?.replace("&lt;", "<")?.replace("&gt;", ">")
                )
            }
        }.toList()
        val name = path.substringAfterLast('/').substringBeforeLast('.')
        return listOf(ImportedScreen(name, path, ImportedScreen.Format.ANDROID_XML, components))
    }

    private fun parseCompose(path: String, source: String): List<ImportedScreen> {
        val matches = composeAnnotation.findAll(source).toList()
        if (matches.isEmpty()) return emptyList()
        return matches.mapIndexed { index, match ->
            val nextStart = matches.getOrNull(index + 1)?.range?.first ?: source.length
            val block = source.substring(match.range.first, nextStart)
            val visualItems = mutableListOf<Pair<Int, ImportedScreenComponent>>()
            composeButton.findAll(block).forEach {
                visualItems += it.range.first to ImportedScreenComponent("Button", text = unescape(it.groupValues[1]))
            }
            composeText.findAll(block).forEach {
                // Button labels are already represented by a button preview.
                val insideButton = composeButton.findAll(block).any { button -> it.range.first in button.range }
                if (!insideButton) visualItems += it.range.first to ImportedScreenComponent("Text", text = unescape(it.groupValues[1]))
            }
            composeField.findAll(block).forEach {
                visualItems += it.range.first to ImportedScreenComponent("TextField", text = "Campo de entrada")
            }
            composeImage.findAll(block).forEach {
                visualItems += it.range.first to ImportedScreenComponent("Image", text = "Imagem")
            }
            composeCalls.filter { it !in setOf("Text", "Button", "OutlinedButton", "TextField", "OutlinedTextField", "Image") }
                .forEach { type ->
                    Regex("""\b${Regex.escape(type)}\s*(?:\(|\{)""").findAll(block).forEach {
                        visualItems += it.range.first to ImportedScreenComponent(type, text = null)
                    }
                }
            val components = visualItems.sortedBy { it.first }.map { it.second }.distinct()
            ImportedScreen(
                name = match.groupValues[1],
                sourcePath = path,
                format = ImportedScreen.Format.JETPACK_COMPOSE,
                components = components
            )
        }
    }

    private fun unescape(value: String): String =
        value.replace("\\n", " ").replace("\\\"", "\"").replace("\\\\", "\\")

    private const val MAX_SOURCE_BYTES = 2L * 1024L * 1024L
}
