package com.geivia.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializable, Android-native model for the Studio data and interaction layer.
 * This is deliberately a definition model only: it never executes user actions.
 */
internal data class StudioVariableDefinition(
    val key: String,
    val label: String,
    val type: String,
    val initialValue: String = ""
)

internal data class StudioActionDefinition(
    val id: String,
    val trigger: String,
    val kind: String,
    val componentId: String = "",
    val target: String = "",
    val expression: String = "",
    val enabled: Boolean = true
)

internal data class StudioLogicDocument(
    val variables: List<StudioVariableDefinition> = emptyList(),
    val actions: List<StudioActionDefinition> = emptyList(),
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION
) {
    fun validate(availableScreenIds: Set<String>? = null, availableComponentIds: Set<String>? = null): List<String> {
        val errors = mutableListOf<String>()
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            errors += "Versão de esquema não suportada: $schemaVersion"
        }
        if (variables.size > MAX_VARIABLES) errors += "Limite de $MAX_VARIABLES variáveis excedido"
        if (actions.size > MAX_ACTIONS) errors += "Limite de $MAX_ACTIONS ações excedido"

        val keys = variables.map { it.key }
        if (keys.distinct().size != keys.size) errors += "Existem variáveis com identificadores duplicados"
        val variableMap = variables.associateBy { it.key }
        variables.forEach { variable ->
            if (!KEY_PATTERN.matches(variable.key)) errors += "Identificador inválido: ${variable.key}"
            if (variable.label.isBlank()) errors += "A variável ${variable.key} precisa de um nome"
            if (variable.type !in VARIABLE_TYPES) errors += "Tipo inválido na variável ${variable.key}"
            if (variable.initialValue.length > MAX_VALUE_LENGTH) errors += "Valor inicial muito longo: ${variable.key}"
            when (variable.type) {
                "number" -> if (variable.initialValue.isNotBlank() && variable.initialValue.toDoubleOrNull() == null) {
                    errors += "Valor inicial numérico inválido: ${variable.key}"
                }
                "boolean" -> if (variable.initialValue !in setOf("true", "false")) {
                    errors += "Valor booleano inválido: ${variable.key}"
                }
                "json" -> if (variable.initialValue.isNotBlank() && !isJsonValue(variable.initialValue)) {
                    errors += "JSON inicial inválido: ${variable.key}"
                }
            }
        }

        val actionIds = actions.map { it.id }
        if (actionIds.distinct().size != actionIds.size) errors += "Existem ações com identificadores duplicados"
        actions.forEach { action ->
            if (action.id.isBlank()) errors += "Toda ação precisa de um identificador"
            if (action.trigger !in ACTION_TRIGGERS) errors += "Gatilho inválido na ação ${action.id}"
            if (action.kind !in ACTION_KINDS) errors += "Tipo de ação inválido: ${action.kind}"
            if (action.trigger != "on_screen_open" && action.componentId.isBlank()) errors += "Ação ${action.id} precisa estar vinculada a um componente"
            if (action.componentId.length > MAX_TARGET_LENGTH) errors += "Identificador de componente muito longo: ${action.id}"
            if (action.componentId.isNotBlank() && availableComponentIds != null && action.componentId !in availableComponentIds) errors += "Componente não existe: ${action.componentId}"
            if (action.target.length > MAX_TARGET_LENGTH) errors += "Destino muito longo na ação ${action.id}"
            if (action.expression.length > MAX_VALUE_LENGTH) errors += "Expressão muito longa na ação ${action.id}"
            if (action.enabled) {
                when (action.kind) {
                    "navigate" -> {
                        if (action.target.isBlank()) errors += "Ação de navegação sem tela de destino"
                        else if (availableScreenIds != null && action.target !in availableScreenIds) {
                            errors += "Tela de destino não existe: ${action.target}"
                        }
                    }
                    "set_variable" -> if (action.target !in variableMap) errors += "Variável de destino inexistente: ${action.target}"
                    "show_message" -> if (action.expression.isBlank()) errors += "Mensagem da ação está vazia"
                }
            }
            VARIABLE_REFERENCE.findAll(action.expression).forEach { match ->
                val referencedKey = match.groupValues[1]
                if (referencedKey !in variableMap) errors += "Variável referenciada não existe: $referencedKey"
            }
        }
        return errors.distinct()
    }

    fun toJson(): String {
        val variableArray = JSONArray()
        variables.forEach { variable ->
            variableArray.put(JSONObject()
                .put("key", variable.key)
                .put("label", variable.label)
                .put("type", variable.type)
                .put("initialValue", variable.initialValue))
        }
        val actionArray = JSONArray()
        actions.forEach { action ->
            actionArray.put(JSONObject()
                .put("id", action.id)
                 .put("trigger", action.trigger)
                .put("kind", action.kind)
                .put("componentId", action.componentId)
                .put("target", action.target)
                .put("expression", action.expression)
                .put("enabled", action.enabled))
        }
        return JSONObject()
            .put("schemaVersion", schemaVersion)
            .put("variables", variableArray)
            .put("actions", actionArray)
            .toString()
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val MAX_VARIABLES = 200
        const val MAX_ACTIONS = 500
        const val MAX_VALUE_LENGTH = 4096
        const val MAX_TARGET_LENGTH = 256

        private val KEY_PATTERN = Regex("[A-Za-z_][A-Za-z0-9_]{0,63}")
        private val VARIABLE_REFERENCE = Regex("\\$\\{([A-Za-z_][A-Za-z0-9_]{0,63})}")
        private val VARIABLE_TYPES = setOf("text", "number", "boolean", "json")
        private val ACTION_TRIGGERS = setOf("on_click", "on_screen_open", "on_value_change")
        private val ACTION_KINDS = setOf("navigate", "set_variable", "show_message")

        fun fromJson(source: String): StudioLogicDocument {
            require(source.length <= MAX_DOCUMENT_LENGTH) { "Documento de lógica muito grande" }
            val root = JSONObject(source)
            val variablesJson = root.optJSONArray("variables") ?: JSONArray()
            val actionsJson = root.optJSONArray("actions") ?: JSONArray()
            require(variablesJson.length() <= MAX_VARIABLES) { "Limite de variáveis excedido" }
            require(actionsJson.length() <= MAX_ACTIONS) { "Limite de ações excedido" }

            val variables = (0 until variablesJson.length()).map { index ->
                val item = variablesJson.getJSONObject(index)
                StudioVariableDefinition(
                    key = item.optString("key"),
                    label = item.optString("label"),
                    type = item.optString("type", "text"),
                    initialValue = item.optString("initialValue")
                )
            }
            val actions = (0 until actionsJson.length()).map { index ->
                val item = actionsJson.getJSONObject(index)
                StudioActionDefinition(
                    id = item.optString("id"),
                    trigger = item.optString("trigger", "on_click"),
                    kind = item.optString("kind"),
                    componentId = item.optString("componentId"),
                    target = item.optString("target"),
                    expression = item.optString("expression"),
                    enabled = item.optBoolean("enabled", true)
                )
            }
            return StudioLogicDocument(
                variables = variables,
                actions = actions,
                schemaVersion = root.optInt("schemaVersion", CURRENT_SCHEMA_VERSION)
            )
        }

        private const val MAX_DOCUMENT_LENGTH = 512 * 1024

        private fun isJsonValue(value: String): Boolean = runCatching {
            val trimmed = value.trim()
            if (trimmed.startsWith("{")) JSONObject(trimmed)
            else if (trimmed.startsWith("[")) JSONArray(trimmed)
            else throw IllegalArgumentException("Expected object or array")
        }.isSuccess
    }
}
