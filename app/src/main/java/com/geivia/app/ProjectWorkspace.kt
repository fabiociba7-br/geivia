package com.geivia.app

import android.content.Context
import java.io.File
import org.json.JSONObject

internal data class ProjectWorkspaceInfo(
    val root: File,
    val name: String,
    val kind: Kind,
    val files: List<File>,
    val moduleNames: List<String>,
    val hasGradleWrapper: Boolean,
    val hasManifest: Boolean,
    val hasFirebaseConfig: Boolean,
    val firebaseProjectId: String?,
    val webEntry: File?,
    val buildFiles: List<File>
) {
    enum class Kind { ANDROID, WEB, FLUTTER, NODE, UNKNOWN }

    val relativeFiles: List<String>
        get() = files.map { it.relativeTo(root).invariantSeparatorsPath }

    companion object {
        /** Directories that never belong to the user's sources (build output, VCS, ZIP metadata). */
        internal val IGNORED_DIRS = setOf(
            ".git", ".gradle", "build", "node_modules", ".idea", ".dart_tool", "__MACOSX",
            ".aistudio", ".cg.editor", ".cg.gradle-sync", ".kotlin", ".cxx", ".externalNativeBuild"
        )

        private val PROJECT_MARKERS = listOf(
            "settings.gradle", "settings.gradle.kts", "build.gradle", "build.gradle.kts",
            "gradlew", "pubspec.yaml", "package.json", "index.html"
        )

        private fun isProjectDirectory(dir: File): Boolean =
            PROJECT_MARKERS.any { File(dir, it).isFile } ||
                File(dir, "src/main/AndroidManifest.xml").isFile ||
                File(dir, "app/src/main/AndroidManifest.xml").isFile

        /**
         * Finds the real project root. If [rootDir] is already a project, it is returned. Otherwise
         * the nearest (topmost) project directories below it are collected; only when there is exactly
         * one is it chosen. Nested modules (e.g. wrapper/project/app) must not count as separate
         * candidates, otherwise a project inside a wrapper folder is never resolved.
         */
        fun resolveRoot(rootDir: File): File {
            val root = rootDir.canonicalFile
            if (isProjectDirectory(root)) return root
            val found = mutableListOf<File>()
            fun visit(dir: File, depth: Int) {
                if (depth > 3) return
                val children = dir.listFiles { f -> f.isDirectory && f.name !in IGNORED_DIRS }
                    ?.sortedBy { it.name } ?: return
                for (child in children) {
                    if (isProjectDirectory(child)) found += child.canonicalFile else visit(child, depth + 1)
                }
            }
            visit(root, 1)
            return found.singleOrNull() ?: root
        }

        fun activeRoot(context: Context): File? {
            val prefs = context.getSharedPreferences("geivia_onboarding", Context.MODE_PRIVATE)
            return sequenceOf(
                prefs.getString("active_project_dir", null),
                prefs.getString("imported_project_dir", null)
            ).filterNotNull().filter { it.isNotBlank() }
                .mapNotNull { path -> runCatching { File(path).canonicalFile }.getOrNull() }
                .firstOrNull { it.isDirectory }
                ?.let { runCatching { resolveRoot(it) }.getOrDefault(it) }
        }

        fun load(context: Context): ProjectWorkspaceInfo? {
            val root = activeRoot(context) ?: return null
            return scan(root)
        }

        fun scan(rootDir: File): ProjectWorkspaceInfo {
            val root = resolveRoot(rootDir)
            val all = root.walkTopDown()
                .onEnter { dir -> dir == root || dir.name !in IGNORED_DIRS }
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
            val firebaseProjectId = relevant.firstOrNull { it.name == "google-services.json" }
                ?.let { file ->
                    runCatching {
                        JSONObject(file.readText()).optJSONObject("project_info")
                            ?.optString("project_id")?.takeIf { it.isNotBlank() }
                    }.getOrNull()
                } ?: relevant.firstOrNull { it.name == ".firebaserc" }?.let { file ->
                    runCatching {
                        JSONObject(file.readText()).optJSONObject("projects")
                            ?.optString("default")?.takeIf { it.isNotBlank() }
                    }.getOrNull()
                }
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
                firebaseProjectId = firebaseProjectId,
                webEntry = webEntry,
                buildFiles = buildFiles
            )
        }
    }
}
