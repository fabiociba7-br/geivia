package com.geivia.app

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID

private const val STUDIO_LOGIC_PREFS_KEY = "geivia_studio_logic_v1"
private val LogicBlue = Color(0xFF1769AA)
private val LogicInk = Color(0xFF17354D)
private val LogicMuted = Color(0xFF718397)

/** Visual editor for the declarative data/action layer. Actions are stored, never executed here. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StudioLogicPanel(context: Context, screens: List<StudioScreenDefinition> = emptyList(), components: List<StudioComponentDefinition> = emptyList()) {
    val prefs = remember { context.getSharedPreferences("geivia_onboarding", Context.MODE_PRIVATE) }
    var document by remember {
        mutableStateOf(
            runCatching { StudioLogicDocument.fromJson(prefs.getString(STUDIO_LOGIC_PREFS_KEY, null).orEmpty()) }
                .getOrDefault(StudioLogicDocument())
        )
    }
    var variableKey by remember { mutableStateOf("") }
    var variableLabel by remember { mutableStateOf("") }
    var variableType by remember { mutableStateOf("text") }
    var variableInitial by remember { mutableStateOf("") }
    var actionTrigger by remember { mutableStateOf("on_click") }
    var actionKind by remember { mutableStateOf("show_message") }
    var actionComponentId by remember { mutableStateOf("") }
    var actionTarget by remember { mutableStateOf("") }
    var actionExpression by remember { mutableStateOf("") }
    var navigationParameters by remember { mutableStateOf(emptyList<StudioNavigationParameter>()) }
    var navigationParameterKey by remember { mutableStateOf("") }
    var navigationParameterValue by remember { mutableStateOf("") }
    var navigationParameterType by remember { mutableStateOf("text") }
    var feedback by remember { mutableStateOf("As ações são apenas configurações; nada é executado automaticamente.") }

    fun save(next: StudioLogicDocument) {
        document = next
        prefs.edit().putString(STUDIO_LOGIC_PREFS_KEY, next.toJson()).apply()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountTree, contentDescription = null, tint = LogicBlue)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Dados e ações", color = LogicInk, fontWeight = FontWeight.Bold)
                    Text("Configure o comportamento do aplicativo Android", color = LogicMuted, fontSize = 11.sp)
                }
                Text("${document.variables.size} vars · ${document.actions.size} ações", color = LogicBlue, fontSize = 10.sp)
            }

            Text("VARIÁVEIS DO APLICATIVO", color = LogicMuted, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            OutlinedTextField(
                value = variableLabel,
                onValueChange = { variableLabel = it.take(80) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nome exibido") },
                singleLine = true
            )
            OutlinedTextField(
                value = variableKey,
                onValueChange = { variableKey = it.filter { c -> c.isLetterOrDigit() || c == '_' }.take(64) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Identificador (ex.: userName)") },
                supportingText = { Text("Comece com uma letra ou _; sem espaços.") },
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("text" to "Texto", "number" to "Número", "boolean" to "Booleano", "json" to "JSON").forEach { (value, label) ->
                    FilterChip(
                        selected = variableType == value,
                        onClick = { variableType = value },
                        label = { Text(label, fontSize = 10.sp) }
                    )
                }
            }
            OutlinedTextField(
                value = variableInitial,
                onValueChange = { variableInitial = it.take(StudioLogicDocument.MAX_VALUE_LENGTH) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Valor inicial") },
                singleLine = true
            )
            Button(
                onClick = {
                    val item = StudioVariableDefinition(variableKey.trim(), variableLabel.trim(), variableType, variableInitial)
                    val next = document.copy(variables = document.variables + item)
                    val errors = next.validate(screens.map { it.id }.toSet())
                    if (errors.isEmpty()) {
                        save(next)
                        variableKey = ""; variableLabel = ""; variableInitial = ""
                        feedback = "Variável adicionada e salva."
                    } else feedback = errors.first()
                },
                enabled = variableKey.isNotBlank() && variableLabel.isNotBlank() && document.variables.size < StudioLogicDocument.MAX_VARIABLES,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Adicionar variável")
            }

            if (document.variables.isEmpty()) {
                Text("Nenhuma variável cadastrada.", color = LogicMuted, fontSize = 12.sp)
            } else {
                document.variables.forEach { variable ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(variable.label, color = LogicInk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("${variable.key} · ${variable.type} · ${variable.initialValue.ifBlank { "sem valor inicial" }}", color = LogicMuted, fontSize = 10.sp)
                        }
                        IconButton(onClick = {
                            save(document.copy(
                                variables = document.variables.filterNot { it.key == variable.key },
                                actions = document.actions.map { action ->
                                    if (action.kind == "set_variable" && action.target == variable.key) action.copy(enabled = false) else action
                                }
                            ))
                            feedback = "Variável removida. Ações relacionadas foram desativadas para revisão."
                        }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Remover variável", tint = LogicMuted)
                        }
                    }
                    HorizontalDivider(color = Color(0xFFE8EDF3))
                }
            }

            HorizontalDivider(color = Color(0xFFDCE5EE), thickness = 1.dp)
            Text("AÇÕES VISUAIS", color = LogicMuted, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            Text("Gatilho", color = LogicInk, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("on_click" to "Ao tocar", "on_screen_open" to "Ao abrir tela", "on_value_change" to "Ao alterar valor").forEach { (value, label) ->
                    FilterChip(selected = actionTrigger == value, onClick = { actionTrigger = value }, label = { Text(label, fontSize = 10.sp) })
                }
            }
            if (actionTrigger != "on_screen_open") {
                var componentMenuExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = componentMenuExpanded, onExpandedChange = { componentMenuExpanded = it }) {
                    OutlinedTextField(
                        value = components.firstOrNull { it.id == actionComponentId }?.label ?: "",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        label = { Text("Componente que dispara a ação") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = componentMenuExpanded) }
                    )
                    ExposedDropdownMenu(expanded = componentMenuExpanded, onDismissRequest = { componentMenuExpanded = false }) {
                        components.forEach { component ->
                            DropdownMenuItem(text = { Text(component.label) }, onClick = {
                                actionComponentId = component.id
                                componentMenuExpanded = false
                            })
                        }
                    }
                }
                if (components.isEmpty()) Text("Adicione um componente à tela para configurar este gatilho.", color = LogicMuted, fontSize = 10.sp)
            }
            Text("O que deve acontecer?", color = LogicInk, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("show_message" to "Mostrar mensagem", "navigate" to "Navegar para tela", "set_variable" to "Alterar variável").forEach { (value, label) ->
                    FilterChip(selected = actionKind == value, onClick = { actionKind = value }, label = { Text(label, fontSize = 11.sp) })
                }
            }
            if (actionKind == "navigate") {
                var screenMenuExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = screenMenuExpanded, onExpandedChange = { screenMenuExpanded = it }) {
                    OutlinedTextField(
                        value = screens.firstOrNull { it.id == actionTarget }?.let { "${it.name} (${it.id})" } ?: actionTarget,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        label = { Text("Tela de destino") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = screenMenuExpanded) }
                    )
                    ExposedDropdownMenu(expanded = screenMenuExpanded, onDismissRequest = { screenMenuExpanded = false }) {
                        screens.forEach { screen ->
                            DropdownMenuItem(
                                text = { Text("${screen.name} · ${screen.id}") },
                                onClick = { actionTarget = screen.id; screenMenuExpanded = false }
                            )
                        }
                    }
                }
                if (screens.isEmpty()) Text("Crie uma tela antes de configurar a navegação.", color = LogicMuted, fontSize = 10.sp)
                Text("PARÂMETROS DE NAVEGAÇÃO", color = LogicMuted, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                OutlinedTextField(value = navigationParameterKey, onValueChange = { navigationParameterKey = it.filter { ch -> ch.isLetterOrDigit() || ch.toString() == "_" }.take(64) }, modifier = Modifier.fillMaxWidth(), label = { Text("Nome do parâmetro") }, singleLine = true)
                OutlinedTextField(value = navigationParameterValue, onValueChange = { navigationParameterValue = it.take(StudioLogicDocument.MAX_VALUE_LENGTH) }, modifier = Modifier.fillMaxWidth(), label = { Text("Valor ou expressão") }, supportingText = { Text("Informe um valor literal ou expressão.") })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("text" to "Texto", "number" to "Número", "boolean" to "Booleano", "json" to "JSON").forEach { (value, label) -> FilterChip(selected = navigationParameterType == value, onClick = { navigationParameterType = value }, label = { Text(label, fontSize = 10.sp) }) } }
                OutlinedButton(onClick = { navigationParameters = navigationParameters + StudioNavigationParameter(navigationParameterKey.trim(), navigationParameterValue, navigationParameterType); navigationParameterKey = ""; navigationParameterValue = ""; navigationParameterType = "text" }, enabled = navigationParameterKey.isNotBlank() && navigationParameters.size < StudioLogicDocument.MAX_NAVIGATION_PARAMETERS, modifier = Modifier.fillMaxWidth()) { Text("Adicionar parâmetro") }
                navigationParameters.forEach { parameter -> Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(parameter.key + " = " + parameter.value + " (" + parameter.type + ")", color = LogicInk, fontSize = 12.sp) }; IconButton(onClick = { navigationParameters = navigationParameters.filterNot { it.key == parameter.key } }) { Icon(Icons.Default.DeleteOutline, contentDescription = "Remover parâmetro", tint = LogicMuted) } } }
            } else if (actionKind == "set_variable") {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = actionTarget,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        label = { Text("Variável de destino") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        document.variables.forEach { variable ->
                            DropdownMenuItem(text = { Text("${variable.label} (${variable.key})") }, onClick = { actionTarget = variable.key; expanded = false })
                        }
                    }
                }
            }
            OutlinedTextField(
                value = actionExpression,
                onValueChange = { actionExpression = it.take(StudioLogicDocument.MAX_VALUE_LENGTH) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (actionKind == "show_message") "Mensagem" else "Valor / expressão") },
                supportingText = { Text("Referencie variáveis com ${'$'}{identificador}.") }
            )
            Button(
                onClick = {
                    val action = StudioActionDefinition(
                        id = UUID.randomUUID().toString(),
                        trigger = actionTrigger,
                        kind = actionKind,
                        componentId = if (actionTrigger == "on_screen_open") "" else actionComponentId,
                        target = actionTarget.trim(),
                        expression = actionExpression.trim(),
                        navigationParameters = if (actionKind == "navigate") navigationParameters else emptyList()
                    )
                    val next = document.copy(actions = document.actions + action)
                    val errors = next.validate(screens.map { it.id }.toSet(), components.map { it.id }.toSet())
                    if (errors.isEmpty()) {
                        save(next)
                        actionTarget = ""; actionExpression = ""
                        navigationParameters = emptyList()
                        navigationParameterKey = ""; navigationParameterValue = ""; navigationParameterType = "text"
                        feedback = "Ação salva. Ela ainda não é executada pelo editor."
                    } else feedback = errors.first()
                },
                enabled = document.actions.size < StudioLogicDocument.MAX_ACTIONS &&
                    (actionTrigger == "on_screen_open" || (actionComponentId.isNotBlank() && components.any { it.id == actionComponentId })) &&
                    (actionKind == "show_message" && actionExpression.isNotBlank() ||
                     actionKind == "navigate" && actionTarget.isNotBlank() ||
                     actionKind == "set_variable" && actionTarget.isNotBlank() && actionExpression.isNotBlank()),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Adicionar ação")
            }

            if (document.actions.isEmpty()) {
                Text("Nenhuma ação configurada.", color = LogicMuted, fontSize = 12.sp)
            } else {
                document.actions.forEach { action ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${action.trigger} · ${components.firstOrNull { it.id == action.componentId }?.label ?: if (action.trigger == "on_screen_open") "Tela" else "Componente removido"} → ${action.kind}", color = LogicInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                (listOf(action.target, action.expression).filter { it.isNotBlank() } +
                                    action.navigationParameters.map { it.key + "=" + it.value + " (" + it.type + ")" }).joinToString(" · ").ifBlank { "Sem parâmetros" } +
                                    if (action.enabled) "" else " · DESATIVADA",
                                color = LogicMuted, fontSize = 10.sp
                            )
                        }
                        IconButton(onClick = { save(document.copy(actions = document.actions.filterNot { it.id == action.id })) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Remover ação", tint = LogicMuted)
                        }
                    }
                    HorizontalDivider(color = Color(0xFFE8EDF3))
                }
            }

            Text(feedback, color = LogicMuted, fontSize = 11.sp, lineHeight = 16.sp)
            OutlinedButton(
                onClick = {
                    val errors = document.validate(screens.map { it.id }.toSet())
                    if (errors.isEmpty()) {
                        prefs.edit().putString(STUDIO_LOGIC_PREFS_KEY, document.toJson()).apply()
                        feedback = "Configuração validada e salva neste dispositivo."
                    } else feedback = errors.first()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Save, null)
                Spacer(Modifier.width(6.dp))
                Text("Validar e salvar dados e ações")
            }
        }
    }
}
}{variavel} para enviar dados do aplicativo.") })
                OutlinedButton(onClick = { navigationParameters = navigationParameters + StudioNavigationParameter(navigationParameterKey.trim(), navigationParameterValue, navigationParameterType); navigationParameterKey = ""; navigationParameterValue = "" }, enabled = navigationParameterKey.isNotBlank() && navigationParameters.size < StudioLogicDocument.MAX_NAVIGATION_PARAMETERS, modifier = Modifier.fillMaxWidth()) { Text("Adicionar parâmetro") }
                navigationParameters.forEach { parameter -> Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("${parameter.key} = ${parameter.value}", color = LogicInk, fontSize = 12.sp) }; IconButton(onClick = { navigationParameters = navigationParameters.filterNot { it.key == parameter.key } }) { Icon(Icons.Default.DeleteOutline, contentDescription = "Remover parâmetro", tint = LogicMuted) } } }
            } else if (actionKind == "set_variable") {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = actionTarget,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        label = { Text("Variável de destino") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        document.variables.forEach { variable ->
                            DropdownMenuItem(text = { Text("${variable.label} (${variable.key})") }, onClick = { actionTarget = variable.key; expanded = false })
                        }
                    }
                }
            }
            OutlinedTextField(
                value = actionExpression,
                onValueChange = { actionExpression = it.take(StudioLogicDocument.MAX_VALUE_LENGTH) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (actionKind == "show_message") "Mensagem" else "Valor / expressão") },
                supportingText = { Text("Referencie variáveis com ${'$'}{identificador}.") }
            )
            Button(
                onClick = {
                    val action = StudioActionDefinition(
                        id = UUID.randomUUID().toString(),
                        trigger = actionTrigger,
                        kind = actionKind,
                        componentId = if (actionTrigger == "on_screen_open") "" else actionComponentId,
                        target = actionTarget.trim(),
                        expression = actionExpression.trim()
                    )
                    val next = document.copy(actions = document.actions + action)
                    val errors = next.validate(screens.map { it.id }.toSet(), components.map { it.id }.toSet())
                    if (errors.isEmpty()) {
                        save(next)
                        actionTarget = ""; actionExpression = ""
                        feedback = "Ação salva. Ela ainda não é executada pelo editor."
                    } else feedback = errors.first()
                },
                enabled = document.actions.size < StudioLogicDocument.MAX_ACTIONS &&
                    (actionTrigger == "on_screen_open" || (actionComponentId.isNotBlank() && components.any { it.id == actionComponentId })) &&
                    (actionKind == "show_message" && actionExpression.isNotBlank() ||
                     actionKind == "navigate" && actionTarget.isNotBlank() ||
                     actionKind == "set_variable" && actionTarget.isNotBlank() && actionExpression.isNotBlank()),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Adicionar ação")
            }

            if (document.actions.isEmpty()) {
                Text("Nenhuma ação configurada.", color = LogicMuted, fontSize = 12.sp)
            } else {
                document.actions.forEach { action ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${action.trigger} · ${components.firstOrNull { it.id == action.componentId }?.label ?: if (action.trigger == "on_screen_open") "Tela" else "Componente removido"} → ${action.kind}", color = LogicInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                listOf(action.target, action.expression).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Sem parâmetros" } +
                                    if (action.enabled) "" else " · DESATIVADA",
                                color = LogicMuted, fontSize = 10.sp
                            )
                        }
                        IconButton(onClick = { save(document.copy(actions = document.actions.filterNot { it.id == action.id })) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Remover ação", tint = LogicMuted)
                        }
                    }
                    HorizontalDivider(color = Color(0xFFE8EDF3))
                }
            }

            Text(feedback, color = LogicMuted, fontSize = 11.sp, lineHeight = 16.sp)
            OutlinedButton(
                onClick = {
                    val errors = document.validate(screens.map { it.id }.toSet())
                    if (errors.isEmpty()) {
                        prefs.edit().putString(STUDIO_LOGIC_PREFS_KEY, document.toJson()).apply()
                        feedback = "Configuração validada e salva neste dispositivo."
                    } else feedback = errors.first()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Save, null)
                Spacer(Modifier.width(6.dp))
                Text("Validar e salvar dados e ações")
            }
        }
    }
}
