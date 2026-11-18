package com.geivia.app

import android.content.Context
import java.io.File

internal data class ProjectWorkspaceInfo(
    val root: File,
    val name: String,
    val kind: Kind,
    val files: List<File>,
    val moduleNames: List<String>,
    val hasGradleWrapper: Boolean,
    val hasManifest: Boolean,
    val hasFirebaseConfig: Boolean,
    val webEntry: File?,
    val buildFiles: List<File>
) {
    enum class Kind { ANDROID, WEB, FLUTTER, NODE, UNKNOWN }

    val relativeFiles: List<String>
        get() = files.map { it.relativeTo(root).invariantSeparatorsPath }

    companion object {
        fun resolveRoot(rootDir: File): File {
            val root = rootDir.canonicalFile
            fun isProjectDirectory(dir: File): Boolean {
                val markers = listOf(
                    "settings.gradle", "settings.gradle.kts", "build.gradle", "build.gradle.kts",
                    "gradlew", "pubspec.yaml", "package.json", "index.html"
                )
                return markers.any { File(dir, it).isFile } ||
                    File(dir, "src/main/AndroidManifest.xml").isFile ||
                    File(dir, "app/src/main/AndroidManifest.xml").isFile
            }
            if (isProjectDirectory(root)) return root
            val candidates = root.walkTopDown()
                .onEnter { it == root || it.name !in setOf(".git", ".gradle", "build", "node_modules", ".idea", ".dart_tool") }
                .maxDepth(3)
                .filter { it.isDirectory && it != root && isProjectDirectory(it) }
                .toList()
            return candidates.singleOrNull() ?: root
        }

        fun load(context: Context): ProjectWorkspaceInfo? {
            val prefs = context.getSharedPreferences("geivia_onboarding", Context.MODE_PRIVATE)
            val path = prefs.getString("active_project_dir", null)
                ?.takeIf { it.isNotBlank() }
                ?: prefs.getString("imported_project_dir", null)?.takeIf { it.isNotBlank() }
                ?: return null
            val root = runCatching { File(path).canonicalFile }.getOrNull()
                ?.takeIf { it.isDirectory } ?: return null
            return scan(root)
        }

        fun scan(rootDir: File): ProjectWorkspaceInfo {
            val root = rootDir.canonicalFile
            val all = root.walkTopDown()
                .onEnter { dir ->
                    dir == root || dir.name !in setOf(".git", ".gradle", "build", "node_modules", ".idea", ".dart_tool")
                }
                .filter { it.isFile }
                .take(20_001)
                .toList()
            val relevant = all.filter { it.length() <= 2L * 1024 * 1024 }
            val rel = relevant.map { it.relativeTo(root).invariantSeparatorsPath }
            fun has(name: String) = rel.any { it == name || it.endsWith("/$name") }
            val manifest = has("AndroidManifest.xml")
            val gradle = has("settings.gradle") || has("settings.gradle.kts") ||
                has("build.gradle") || has("build.gradle.kts") || has("gradlew")
            val flutter = has("pubspec.yaml") && has("lib/main.dart")
            val webEntry = listOf("index.html", "public/index.html", "dist/index.html", "build/index.html")
                .map { File(root, it) }.firstOrNull { it.isFile }
                ?: relevant.firstOrNull { it.name == "index.html" && it.parentFile?.name !in setOf("node_modules", "build") }
            val kind = when {
                manifest || gradle -> Kind.ANDROID
                flutter -> Kind.FLUTTER
                has("package.json") -> Kind.NODE
                webEntry != null -> Kind.WEB
                else -> Kind.UNKNOWN
            }
            val moduleNames = all.asSequence()
                .filter { it.name == "build.gradle" || it.name == "build.gradle.kts" }
                .mapNotNull { it.parentFile?.relativeTo(root)?.invariantSeparatorsPath }
                .filter { it.isNotBlank() }
                .distinct().take(100).toList()
            val buildFiles = all.filter {
                it.name in setOf("settings.gradle", "settings.gradle.kts", "build.gradle", "build.gradle.kts", "gradlew", "gradlew.bat")
            }.take(100)
            return ProjectWorkspaceInfo(
                root = root,
                name = root.name,
                kind = kind,
                files = relevant.take(5000),
                moduleNames = moduleNames,
                hasGradleWrapper = File(root, "gradlew").isFile,
                hasManifest = manifest,
                hasFirebaseConfig = has("firebase.json"),
                webEntry = webEntry,
                buildFiles = buildFiles
            )
        }
    }
}
