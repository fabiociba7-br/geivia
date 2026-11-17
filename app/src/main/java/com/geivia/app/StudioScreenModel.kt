package com.geivia.app

import org.json.JSONArray
import org.json.JSONObject

/** Versioned screen registry used by the Android visual editor. */
internal data class StudioScreenDefinition(
    val id: String,
    val name: String
)

internal data class StudioScreenDocument(
    val screens: List<StudioScreenDefinition> = listOf(StudioScreenDefinition("home", "Início")),
    val startScreenId: String = screens.firstOrNull()?.id.orEmpty(),
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION
) {
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (schemaVersion != CURRENT_SCHEMA_VERSION) errors += "Versão de telas não suportada"
        if (screens.isEmpty()) errors += "O projeto precisa ter ao menos uma tela"
        if (screens.size > MAX_SCREENS) errors += "Limite de $MAX_SCREENS telas excedido"
        if (screens.map { it.id }.distinct().size != screens.size) errors += "Existem IDs de tela duplicados"
        screens.forEach { screen ->
            if (!ID_PATTERN.matches(screen.id)) errors += "ID de tela inválido: ${screen.id}"
            if (screen.name.isBlank() || screen.name.length > MAX_NAME_LENGTH) errors += "Nome de tela inválido: ${screen.id}"
        }
        if (screens.none { it.id == startScreenId }) errors += "A tela inicial não existe"
        return errors.distinct()
    }

    fun containsScreen(id: String): Boolean = screens.any { it.id == id }

    fun toJson(): String {
        val items = JSONArray()
        screens.forEach { items.put(JSONObject().put("id", it.id).put("name", it.name)) }
        return JSONObject().put("schemaVersion", schemaVersion)
            .put("startScreenId", startScreenId).put("screens", items).toString()
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val MAX_SCREENS = 50
        const val MAX_NAME_LENGTH = 60
        private val ID_PATTERN = Regex("[A-Za-z_][A-Za-z0-9_-]{0,63}")

        fun fromJson(source: String): StudioScreenDocument {
            require(source.length <= 64 * 1024) { "Registro de telas muito grande" }
            val root = JSONObject(source)
            val array = root.optJSONArray("screens") ?: JSONArray()
            require(array.length() <= MAX_SCREENS) { "Limite de telas excedido" }
            val screens = (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                StudioScreenDefinition(item.optString("id"), item.optString("name"))
            }
            return StudioScreenDocument(
                screens = screens,
                startScreenId = root.optString("startScreenId", screens.firstOrNull()?.id.orEmpty()),
                schemaVersion = root.optInt("schemaVersion", CURRENT_SCHEMA_VERSION)
            )
        }
    }
}
