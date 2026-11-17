package com.geivia.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds a conservative Firebase CLI partial-deploy plan from firebase.json.
 * Broad selectors are used only when the config has no deploy target metadata.
 */
internal object FirebaseDeployPlanner {
    fun targets(config: JSONObject): List<String> {
        val result = linkedSetOf<String>()
        addScopedTargets(config.opt("hosting"), "hosting", result)
        addScopedTargets(config.opt("storage"), "storage", result)

        if (isConfigured(config, "firestore")) result += "firestore"
        if (isConfigured(config, "database")) result += "database"
        if (isConfigured(config, "functions")) result += "functions"
        if (isConfigured(config, "auth")) result += "auth"
        if (isConfigured(config, "dataconnect")) result += "dataconnect"
        return result.toList()
    }

    private fun isConfigured(config: JSONObject, key: String): Boolean =
        config.has(key) && config.opt(key) != null && config.opt(key) != JSONObject.NULL

    private fun addScopedTargets(value: Any?, type: String, result: MutableSet<String>) {
        if (value == null || value == JSONObject.NULL) return
        val entries = when (value) {
            is JSONObject -> listOf(value)
            is JSONArray -> (0 until value.length()).mapNotNull { value.optJSONObject(it) }
            else -> emptyList()
        }
        if (entries.isEmpty()) {
            result += type
            return
        }

        val names = entries.map { it.optString("target").trim() }
        if (names.all { it.isNotEmpty() }) {
            names.forEach { name ->
                require(name.matches(Regex("^[A-Za-z0-9_-]+$"))) {
                    "Nome de deploy target inválido em $type: $name"
                }
                result += "$type:$name"
            }
        } else {
            // A generic selector is explicit and may cover every configured site/bucket.
            result += type
        }
    }
}
