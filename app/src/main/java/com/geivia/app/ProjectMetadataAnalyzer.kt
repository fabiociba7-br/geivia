package com.geivia.app

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Read-only interpretation of project identity, Firebase deploy config and folder roles. */
internal data class ProjectMetadata(
    val appName: String?, val packageName: String?, val appTitle: String?,
    val firebaseProjectId: String?, val hostingSiteIds: List<String>, val deployTargets: List<String>,
    val functionsSources: List<String>, val firestoreRules: List<String>, val storageRules: List<String>, val databaseRules: List<String>,
    val hostingPublicDirs: List<String>, val configFiles: List<String>,
    val structure: List<ProjectFolderRole>, val warnings: List<String>, val googleAiStudioProject: Boolean
) {
    companion object {
        fun analyze(root: File, files: List<File>): ProjectMetadata {
            val base = root.canonicalFile
            fun rel(f: File) = runCatching { f.canonicalFile.relativeTo(base).invariantSeparatorsPath }.getOrDefault(f.name)
            fun read(f: File?) = f?.takeIf { it.isFile && it.length() <= 2L * 1024 * 1024 }?.let { runCatching { it.readText(Charsets.UTF_8) }.getOrNull() }
            fun json(f: File?) = read(f)?.let { runCatching { JSONObject(it) }.getOrNull() }
            val grouped = files.groupBy { it.name }
            fun first(name: String): File? {
                val rootLevel = File(base, name)
                if (rootLevel.isFile) return rootLevel
                return grouped[name]?.sortedBy { rel(it).count { c -> c == '/' } }?.firstOrNull()
            }
            val packageFile = files.firstOrNull { it.name == "package.json" && it.parentFile?.canonicalFile == base } ?: first("package.json")
            val pkg = json(packageFile)
            val packageName = pkg?.optString("name")?.takeIf { it.isNotBlank() && it != "null" }
            val packageDisplay = listOf("displayName", "productName", "appName").firstNotNullOfOrNull { key ->
                pkg?.optString(key)?.takeIf { it.isNotBlank() && it != "null" }
            }
            val metadataJson = json(first("metadata.json"))
            val appConfig = listOf("app.json", "app.config.json", "capacitor.config.json").firstNotNullOfOrNull(::first).let(::json)
            val appConfigName = sequenceOf(
                metadataJson?.optString("appName")?.takeIf { it.isNotBlank() && it != "null" },
                metadataJson?.optString("displayName")?.takeIf { it.isNotBlank() && it != "null" },
                metadataJson?.optString("title")?.takeIf { it.isNotBlank() && it != "null" },
                appConfig?.optString("name")?.takeIf { it.isNotBlank() && it != "null" },
                appConfig?.optJSONObject("expo")?.optString("name")?.takeIf { it.isNotBlank() && it != "null" }
            ).firstOrNull { !it.isNullOrBlank() }
            val manifestText = read(first("AndroidManifest.xml")).orEmpty()
            val manifestLabel = Regex("""android:label\s*=\s*["']([^"']+)["']""").find(manifestText)?.groupValues?.getOrNull(1)
            val manifestResource = manifestLabel?.takeIf { it.startsWith("@string/") }?.removePrefix("@string/")
            val resolvedManifestLabel = manifestResource?.let { resource ->
                files.filter { it.name == "strings.xml" }.mapNotNull(::read).firstNotNullOfOrNull { xml ->
                    Regex("""<string\s+name=["']$resource["'][^>]*>(.*?)</string>""", setOf(RegexOption.DOT_MATCHES_ALL))
                        .find(xml)?.groupValues?.getOrNull(1)?.trim()?.takeIf(String::isNotBlank)
                }
            } ?: manifestLabel?.takeUnless { it.startsWith("@") }
            val gradleText = files.filter { it.name == "build.gradle" || it.name == "build.gradle.kts" }
                .sortedBy(::rel).mapNotNull(::read).joinToString("\n")
            val appId = sequenceOf(
                Regex("""applicationId\s*(?:=\s*)?["']([^"']+)["']""").find(gradleText)?.groupValues?.getOrNull(1),
                Regex("""namespace\s*(?:=\s*)?["']([^"']+)["']""").find(gradleText)?.groupValues?.getOrNull(1),
                Regex("""<manifest[^>]*\bpackage=["']([^"']+)["']""").find(manifestText)?.groupValues?.getOrNull(1)
            ).firstOrNull { !it.isNullOrBlank() }
            val html = read(first("index.html")).orEmpty()
            val htmlTitle = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(html)?.groupValues?.getOrNull(1)?.replace(Regex("""\s+"""), " ")?.trim()?.takeIf { it.isNotBlank() }

            val firebaseFile = first("firebase.json")
            val firebase = json(firebaseFile)
            val rc = json(first(".firebaserc"))
            val google = json(first("google-services.json"))
            val projectIds = linkedSetOf<String>()
            rc?.optJSONObject("projects")?.optString("default")?.takeIf { it.isNotBlank() }?.let(projectIds::add)
            firebase?.optString("project")?.takeIf { it.isNotBlank() && it != "null" }?.let(projectIds::add)
            google?.optJSONObject("project_info")?.optString("project_id")?.takeIf { it.isNotBlank() }?.let(projectIds::add)

            val targets = linkedSetOf<String>()
            val sites = linkedSetOf<String>()
            val targetProject = projectIds.firstOrNull()
            val hostingTargetMap = targetProject?.let { rc?.optJSONObject("targets")?.optJSONObject(it)?.optJSONObject("hosting") }
            hostingTargetMap?.keys()?.forEach { targetName ->
                val siteList = hostingTargetMap.optJSONArray(targetName)
                if (siteList != null) {
                    for (i in 0 until siteList.length()) siteList.optString(i).takeIf { it.isNotBlank() }?.let(sites::add)
                    if (siteList.length() > 0) targets += "hosting:$targetName"
                }
            }
            val hosting = firebase?.opt("hosting")
            val hostingEntries = when (hosting) {
                is JSONObject -> listOf(hosting)
                is JSONArray -> (0 until hosting.length()).mapNotNull { hosting.optJSONObject(it) }
                else -> emptyList()
            }
            hostingEntries.forEach { e ->
                val target = e.optString("target").takeIf { it.isNotBlank() }
                val site = e.optString("site").takeIf { it.isNotBlank() }
                if (target != null) targets += "hosting:$target"
                else if (site == null) targets += "hosting"
                site?.let(sites::add)
            }
            if (hosting != null && hosting != JSONObject.NULL && hostingEntries.isEmpty()) targets += "hosting"
            listOf("storage", "database").forEach { key ->
                val value = firebase?.opt(key)
                val entries = when (value) {
                    is JSONObject -> listOf(value)
                    is JSONArray -> (0 until value.length()).mapNotNull { value.optJSONObject(it) }
                    else -> emptyList()
                }
                entries.forEach { e -> targets += e.optString("target").takeIf { it.isNotBlank() }?.let { "$key:$it" } ?: key }
                if (value != null && value != JSONObject.NULL && entries.isEmpty()) targets += key
            }
            listOf("firestore", "functions", "auth", "dataconnect").forEach { key ->
                if (firebase?.has(key) == true && firebase.opt(key) != JSONObject.NULL) targets += key
            }
            val functionValue = firebase?.opt("functions")
            val functionEntries = when (functionValue) {
                is JSONObject -> listOf(functionValue)
                is JSONArray -> (0 until functionValue.length()).mapNotNull { functionValue.optJSONObject(it) }
                else -> emptyList()
            }
            val functionSources = functionEntries.mapNotNull { it.optString("source").takeIf(String::isNotBlank) }.ifEmpty {
                if (functionValue != null && functionValue != JSONObject.NULL) listOf("functions") else emptyList()
            }
            val firestoreValue = firebase?.opt("firestore")
            val firestoreEntries = when (firestoreValue) {
                is JSONObject -> listOf(firestoreValue)
                is JSONArray -> (0 until firestoreValue.length()).mapNotNull { firestoreValue.optJSONObject(it) }
                else -> emptyList()
            }
            val firestoreRules = firestoreEntries.mapNotNull { it.optString("rules").takeIf(String::isNotBlank) }
            val storageValue = firebase?.opt("storage")
            val storageEntries = when (storageValue) {
                is JSONObject -> listOf(storageValue)
                is JSONArray -> (0 until storageValue.length()).mapNotNull { storageValue.optJSONObject(it) }
                else -> emptyList()
            }
            val storageRules = storageEntries.mapNotNull { it.optString("rules").takeIf(String::isNotBlank) }
            val databaseValue = firebase?.opt("database")
            val databaseEntries = when (databaseValue) {
                is JSONObject -> listOf(databaseValue)
                is JSONArray -> (0 until databaseValue.length()).mapNotNull { databaseValue.optJSONObject(it) }
                else -> emptyList()
            }
            val databaseRules = databaseEntries.mapNotNull { it.optString("rules").takeIf(String::isNotBlank) }
            val publicDirs = hostingEntries.mapNotNull { it.optString("public").takeIf(String::isNotBlank) }
            val configNames = setOf("firebase.json", ".firebaserc", "google-services.json", "package.json", "metadata.json",
                "app.json", "app.config.json", "capacitor.config.json", "pubspec.yaml", "AndroidManifest.xml",
                "vite.config.js", "vite.config.ts", "next.config.js", "next.config.ts", "angular.json")
            val configs = files.filter { it.name in configNames }.map(::rel).distinct().sorted()

            val roles = linkedMapOf<String, String>()
            files.forEach { f ->
                val path = rel(f)
                val segments = path.split('/')
                val folder = path.substringBeforeLast('/', "")
                val role = when {
                    "functions" in segments -> "Firebase Cloud Functions"
                    "firestore" in segments || f.name.contains("firestore", true) -> "Firestore rules / indexes"
                    f.name == "storage.rules" -> "Firebase Storage rules"
                    "hosting" in segments || "public" in segments -> "Hosting / arquivos publicados"
                    "src" in segments -> "Código-fonte"
                    "lib" in segments -> "Bibliotecas / código principal"
                    segments.any { it in setOf("tests", "test", "__tests__") } -> "Testes"
                    "android" in segments -> "Subprojeto Android"
                    "ios" in segments -> "Subprojeto iOS"
                    else -> null
                }
                if (role != null && folder.isNotBlank()) roles.putIfAbsent(folder, role)
            }
            val warnings = mutableListOf<String>()
            if (projectIds.size > 1) warnings += "Project IDs divergentes encontrados: ${projectIds.joinToString()}. Confirme o destino antes do deploy."
            if (firebaseFile == null) warnings += "firebase.json não encontrado; não há configuração de deploy Firebase identificada."
            if (firebaseFile != null && projectIds.isEmpty()) warnings += "firebase.json encontrado, mas o Firebase Project ID não foi identificado."
            if (firebase != null && targets.isEmpty()) warnings += "firebase.json não declarou serviços de deploy reconhecidos."
            if (manifestResource != null && resolvedManifestLabel == null) warnings += "O nome Android aponta para recurso ${manifestLabel}, mas o valor não foi encontrado em strings.xml."
            val dependencies = pkg?.optJSONObject("dependencies")
            val devDependencies = pkg?.optJSONObject("devDependencies")
            val aiStudio = files.any { it.name == ".aistudio" || rel(it).contains("/.aistudio/") } ||
                (pkg?.optString("description")?.contains("Google AI Studio", true) == true) ||
                (dependencies?.has("@google/genai") == true) || (dependencies?.has("@google/generative-ai") == true) ||
                (devDependencies?.has("@google/genai") == true) || (devDependencies?.has("@google/generative-ai") == true)
            val appName = packageDisplay ?: appConfigName ?: htmlTitle ?: resolvedManifestLabel ?: packageName
            return ProjectMetadata(appName, appId ?: packageName, htmlTitle, projectIds.firstOrNull(), sites.toList(),
                targets.toList(), functionSources.distinct(), firestoreRules.distinct(), storageRules.distinct(), databaseRules.distinct(),
                publicDirs.distinct(), configs, roles.map { ProjectFolderRole(it.key, it.value) }.sortedBy { it.path },
                warnings, aiStudio)
        }
    }
}

internal data class ProjectFolderRole(val path: String, val role: String)
