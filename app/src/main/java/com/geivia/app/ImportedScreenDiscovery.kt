package com.geivia.app

import java.io.File

/**
 * Read-only index of screens that already exist in an imported source project.
 * It deliberately does not rewrite Kotlin: safe Compose round-tripping requires a Kotlin PSI model.
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

    fun scan(projectRoot: File, maxFiles: Int = 5000): List<ImportedScreen> {
        val root = projectRoot.canonicalFile
        require(root.isDirectory) { "A pasta do projeto não existe." }
        val sourceFiles = root.walkTopDown()
            .onEnter { dir -> dir == root || dir.name !in ProjectWorkspaceInfo.IGNORED_DIRS }
            .filter { it.isFile }
            .filter { file ->
                val path = file.relativeTo(root).invariantSeparatorsPath
                (file.extension.equals("xml", true) && Regex("""(^|/)res/layout[^/]*/""").containsMatchIn(path)) ||
                    (file.extension.equals("kt", true) && (path.startsWith("src/main/") || path.contains("/src/main/")) )
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
                    text = xmlText.find(attrs)?.groupValues?.getOrNull(1)
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
            val componentList = composeCalls.mapNotNull { type ->
                val count = Regex("""\b${Regex.escape(type)}\s*\(""").findAll(block).count()
                if (count == 0) null else ImportedScreenComponent(type = type).copy(text = "×$count")
            }
            ImportedScreen(
                name = match.groupValues[1],
                sourcePath = path,
                format = ImportedScreen.Format.JETPACK_COMPOSE,
                components = componentList
            )
        }
    }

    private const val MAX_SOURCE_BYTES = 2L * 1024L * 1024L
}
