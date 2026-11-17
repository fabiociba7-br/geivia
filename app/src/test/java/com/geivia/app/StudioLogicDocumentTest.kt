package com.geivia.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioLogicDocumentTest {
    @Test
    fun roundTripsVariablesAndActions() {
        val original = StudioLogicDocument(
            variables = listOf(
                StudioVariableDefinition("userName", "Nome do usuário", "text", "Ana"),
                StudioVariableDefinition("counter", "Contador", "number", "0")
            ),
            actions = listOf(
                StudioActionDefinition("action-1", "on_click", "set_variable", "counter", "${counter}")
            )
        )

        val restored = StudioLogicDocument.fromJson(original.toJson())

        assertEquals(original, restored)
        assertTrue(restored.validate().isEmpty())
    }

    @Test
    fun detectsDuplicateAndInvalidVariableKeys() {
        val document = StudioLogicDocument(
            variables = listOf(
                StudioVariableDefinition("user-name", "Nome", "text"),
                StudioVariableDefinition("user-name", "Outro nome", "text")
            )
        )

        val errors = document.validate()

        assertTrue(errors.any { it.contains("duplicados") })
        assertTrue(errors.any { it.contains("Identificador inválido") })
    }

    @Test
    fun detectsReferencesToMissingVariables() {
        val document = StudioLogicDocument(
            actions = listOf(
                StudioActionDefinition("a1", "on_click", "show_message", expression = "Olá ${unknown}")
            )
        )

        assertTrue(document.validate().any { it.contains("não existe: unknown") })
    }

    @Test
    fun rejectsInvalidTypedInitialValues() {
        val document = StudioLogicDocument(
            variables = listOf(
                StudioVariableDefinition("amount", "Valor", "number", "dez"),
                StudioVariableDefinition("active", "Ativo", "boolean", "sim"),
                StudioVariableDefinition("payload", "Dados", "json", "{broken")
            )
        )

        assertEquals(3, document.validate().size)
    }

    @Test
    fun disabledActionsAreNotRequiredToHaveTargets() {
        val document = StudioLogicDocument(
            actions = listOf(
                StudioActionDefinition("a1", "on_click", "navigate", enabled = false)
            )
        )

        assertFalse(document.validate().any { it.contains("sem tela de destino") })
    }

    @Test
    fun rejectsOversizedDocuments() {
        val oversized = " ".repeat(StudioLogicDocument.MAX_VALUE_LENGTH * 130)

        val error = runCatching { StudioLogicDocument.fromJson(oversized) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }
}
