package com.geivia.app

import org.json.JSONArray
import org.json.JSONObject

/** Versioned screen registry used by the Android visual editor. */
internal data class StudioComponentDefinition(val id: String, val label: String)

internal data class StudioScreenInputParameter(
    val key: String,
    val label: String,
    val type: String = "text",
    val required: Boolean = false,
    val defaultValue: String = ""
)

internal data class StudioScreenDefinition(
    val id: String,
    val name: String,
    val inputParameters: List<StudioScreenInputParameter> = emptyList()
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
            if (screen.inputParameters.size > MAX_INPUT_PARAMETERS) {
                errors += "Limite de parâmetros de entrada excedido na tela ${screen.id}"
            }
            val keys = screen.inputParameters.map { it.key }
            if (keys.distinct().size != keys.size) {
                errors += "Parâmetros de entrada duplicados na tela ${screen.id}"
            }
            screen.inputParameters.forEach { parameter ->
                if (!ID_PATTERN.matches(parameter.key)) errors += "Identificador de parâmetro inválido: ${parameter.key}"
                if (parameter.label.isBlank() || parameter.label.length > MAX_NAME_LENGTH) {
                    errors += "Nome de parâmetro inválido: ${parameter.key}"
                }
                if (parameter.type !in PARAMETER_TYPES) errors += "Tipo inválido no parâmetro de entrada ${parameter.key}"
                if (parameter.defaultValue.length > MAX_PARAMETER_VALUE_LENGTH) {
                    errors += "Valor padrão muito longo: ${parameter.key}"
                }
                if (parameter.defaultValue.isNotBlank()) {
                    when (parameter.type) {
                        "number" -> if (parameter.defaultValue.toDoubleOrNull() == null) {
                            errors += "Valor padrão numérico inválido: ${parameter.key}"
                        }
                        "boolean" -> if (parameter.defaultValue !in setOf("true", "false")) {
                            errors += "Valor padrão booleano inválido: ${parameter.key}"
                        }
                        "json" -> if (!isJsonValue(parameter.defaultValue)) {
                            errors += "Valor padrão JSON inválido: ${parameter.key}"
                        }
                    }
                }
            }
        }
        if (screens.none { it.id == startScreenId }) errors += "A tela inicial não existe"
        return errors.distinct()
    }

    fun containsScreen(id: String): Boolean = screens.any { it.id == id }

    fun screen(id: String): StudioScreenDefinition? = screens.firstOrNull { it.id == id }

    fun toJson(): String {
        val items = JSONArray()
        screens.forEach { screen ->
            val parameters = JSONArray()
            screen.inputParameters.forEach { parameter ->
                parameters.put(JSONObject()
                    .put("key", parameter.key)
                    .put("label", parameter.label)
                    .put("type", parameter.type)
                    .put("required", parameter.required)
                    .put("defaultValue", parameter.defaultValue))
            }
            items.put(JSONObject()
                .put("id", screen.id)
                .put("name", screen.name)
                .put("inputParameters", parameters))
        }
        return JSONObject().put("schemaVersion", schemaVersion)
            .put("startScreenId", startScreenId).put("screens", items).toString()
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val MAX_SCREENS = 50
        const val MAX_INPUT_PARAMETERS = 30
        const val MAX_NAME_LENGTH = 60
        const val MAX_PARAMETER_VALUE_LENGTH = 4096
        private val ID_PATTERN = Regex("[A-Za-z_][A-Za-z0-9_-]{0,63}")
        private val PARAMETER_TYPES = setOf("text", "number", "boolean", "json")

        fun fromJson(source: String): StudioScreenDocument {
            require(source.length <= 64 * 1024) { "Registro de telas muito grande" }
            val root = JSONObject(source)
            val array = root.optJSONArray("screens") ?: JSONArray()
            require(array.length() <= MAX_SCREENS) { "Limite de telas excedido" }
            val screens = (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                val parameterArray = item.optJSONArray("inputParameters") ?: JSONArray()
                require(parameterArray.length() <= MAX_INPUT_PARAMETERS) { "Limite de parâmetros de entrada excedido" }
                val parameters = (0 until parameterArray.length()).map { parameterIndex ->
                    val parameter = parameterArray.getJSONObject(parameterIndex)
                    StudioScreenInputParameter(
                        key = parameter.optString("key"),
                        label = parameter.optString("label"),
                        type = parameter.optString("type", "text"),
                        required = parameter.optBoolean("required", false),
                        defaultValue = parameter.optString("defaultValue")
                    )
                }
                StudioScreenDefinition(item.optString("id"), item.optString("name"), parameters)
            }
            val document = StudioScreenDocument(
                screens = screens,
                startScreenId = root.optString("startScreenId", screens.firstOrNull()?.id.orEmpty()),
                schemaVersion = root.optInt("schemaVersion", CURRENT_SCHEMA_VERSION)
            )
            require(document.validate().isEmpty()) { document.validate().joinToString("; ") }
            return document
        }

        private fun isJsonValue(value: String): Boolean = runCatching {
            val trimmed = value.trim()
            if (trimmed.startsWith("{")) JSONObject(trimmed)
            else if (trimmed.startsWith("[")) JSONArray(trimmed)
            else throw IllegalArgumentException("Expected object or array")
        }.isSuccess
    }
}
