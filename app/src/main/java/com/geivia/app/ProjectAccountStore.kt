package com.geivia.app

import android.content.Context
import android.content.SharedPreferences
import java.io.File

/**
 * Binds Google Cloud, Firebase and GitHub account data to a specific project
 * so switching projects never mixes credentials or project IDs.
 */
internal object ProjectAccountStore {
    private const val PREFS = "geivia_onboarding"

    private fun normalizedPath(projectPath: String): String {
        val candidate = File(projectPath)
        return runCatching { ProjectWorkspaceInfo.resolveRoot(candidate).absolutePath }
            .getOrDefault(candidate.absolutePath)
    }

    private fun key(projectPath: String, suffix: String): String {
        val hash = normalizedPath(projectPath).hashCode().toUInt().toString(16)
        return "proj_${hash}_$suffix"
    }

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun activePath(context: Context): String? {
        val p = prefs(context)
        return sequenceOf(
            p.getString("active_project_dir", null),
            p.getString("imported_project_dir", null)
        ).filterNotNull().firstOrNull { it.isNotBlank() && File(it).isDirectory }
            ?.let(::normalizedPath)
    }

    fun saveFirebase(context: Context, projectPath: String, firebaseProjectId: String?) {
        if (projectPath.isBlank()) return
        prefs(context).edit()
            .putString(key(projectPath, "firebase_id"), firebaseProjectId.orEmpty())
            .apply()
    }

    fun firebaseId(context: Context, projectPath: String): String =
        prefs(context).getString(key(projectPath, "firebase_id"), "").orEmpty()

    fun saveGoogle(context: Context, projectPath: String, googleProjectId: String?) {
        if (projectPath.isBlank()) return
        prefs(context).edit()
            .putString(key(projectPath, "google_project"), googleProjectId.orEmpty())
            .apply()
    }

    fun googleProject(context: Context, projectPath: String): String =
        prefs(context).getString(key(projectPath, "google_project"), "").orEmpty()

    fun saveGitHub(context: Context, projectPath: String, repo: String?) {
        if (projectPath.isBlank()) return
        prefs(context).edit()
            .putString(key(projectPath, "github_repo"), repo.orEmpty())
            .apply()
    }

    fun githubRepo(context: Context, projectPath: String): String =
        prefs(context).getString(key(projectPath, "github_repo"), "").orEmpty()

    fun applyActiveToGlobals(context: Context) {
        val path = activePath(context) ?: return
        val p = prefs(context)
        val editor = p.edit()
        val fb = firebaseId(context, path)
        val gh = githubRepo(context, path)
        val g = googleProject(context, path)
        editor.putString("active_firebase_project_id", fb)
            .putString("selected_project_id", fb)
            .putString("preflight_github_repo", gh)
            .putString("selected_google_project_id", g)
        editor.apply()
    }

    fun captureGlobalsToActive(context: Context) {
        val path = activePath(context) ?: return
        val p = prefs(context)
        val fb = p.getString("active_firebase_project_id", null)
            ?: p.getString("selected_project_id", null)
        val gh = p.getString("preflight_github_repo", null)
        val g = p.getString("selected_google_project_id", null)
        saveFirebase(context, path, fb)
        saveGitHub(context, path, gh)
        saveGoogle(context, path, g)
    }
}
