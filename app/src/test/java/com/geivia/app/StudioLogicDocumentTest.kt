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
                StudioActionDefinition("action-1", "on_click", "set_variable", "counter", "\${counter}", componentId = "saveButton")
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
                StudioActionDefinition("a1", "on_click", "show_message", expression = "Olá \${unknown}", componentId = "helloButton")
            )
        )

        assertTrue(document.validate().any { it.contains("não existe: unknown") })
    }

    @Test
    fun rejectsNavigationToUnknownScreenWhenRegistryIsProvided() {
        val document = StudioLogicDocument(
            actions = listOf(
                StudioActionDefinition("go", "on_click", "navigate", target = "missing", componentId = "goButton")
            )
        )
        assertTrue(document.validate(setOf("home", "profile")).any {
            it.contains("Tela de destino não existe: missing")
        })
        assertTrue(document.validate().isEmpty())
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
                StudioActionDefinition("a1", "on_click", "navigate", enabled = false, componentId = "disabledButton")
            )
        )

        assertFalse(document.validate().any { it.contains("sem tela de destino") })
    }


    @Test
    fun rejectsActionsLinkedToMissingComponents() {
        val document = StudioLogicDocument(
            actions = listOf(
                StudioActionDefinition("tap", "on_click", "show_message", expression = "Oi", componentId = "oldButton")
            )
        )
        assertTrue(document.validate(availableComponentIds = setOf("currentButton")).any {
            it.contains("Componente não existe: oldButton")
        })
    }

    @Test
    fun roundTripsTypedNavigationParameters() {
        val original = StudioLogicDocument(actions = listOf(StudioActionDefinition(
            "go-profile", "on_click", "navigate", target = "profile",
            navigationParameters = listOf(StudioNavigationParameter("userId", "42", "number"), StudioNavigationParameter("origin", "dashboard"))
        )))
        val restored = StudioLogicDocument.fromJson(original.toJson())
        assertEquals(original, restored)
        assertTrue(restored.validate(setOf("home", "profile")).isEmpty())
    }

    @Test
    fun acceptsTypedVariableReferencesAsNavigationParameters() {
        val document = StudioLogicDocument(
            variables = listOf(
                StudioVariableDefinition("amount", "Valor", "number", "12"),
                StudioVariableDefinition("active", "Ativo", "boolean", "true")
            ),
            actions = listOf(StudioActionDefinition(
                "go", "on_click", "navigate", target = "checkout",
                navigationParameters = listOf(
                    StudioNavigationParameter("total", "${'
        val document = StudioLogicDocument(actions = listOf(StudioActionDefinition(
            "go", "on_click", "navigate", target = "profile",
            navigationParameters = listOf(StudioNavigationParameter("bad-name", "x"), StudioNavigationParameter("amount", "not-a-number", "number"))
        )))
        val errors = document.validate(setOf("profile"))
        assertTrue(errors.any { it.contains("Nome de parâmetro inválido") })
        assertTrue(errors.any { it.contains("Parâmetro numérico inválido") })
    }

    @Test
    fun rejectsDuplicateNavigationParameterKeys() {
        val document = StudioLogicDocument(actions = listOf(StudioActionDefinition(
            "go", "on_click", "navigate", target = "profile",
            navigationParameters = listOf(StudioNavigationParameter("id", "1"), StudioNavigationParameter("id", "2"))
        )))
        assertTrue(document.validate().any { it.contains("Parâmetros duplicados") })
    }

    @Test
    fun readsLegacyActionsWithoutNavigationParameters() {
        val legacy = """{"schemaVersion":1,"variables":[],"actions":[{"id":"old","trigger":"on_click","kind":"navigate","target":"home","enabled":true}]}"""
        val restored = StudioLogicDocument.fromJson(legacy)
        assertTrue(restored.actions.single().navigationParameters.isEmpty())
    }
    @Test
    fun rejectsOversizedDocuments() {
        val oversized = " ".repeat(StudioLogicDocument.MAX_VALUE_LENGTH * 130)

        val error = runCatching { StudioLogicDocument.fromJson(oversized) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }
}
}{amount}", "number"),
                    StudioNavigationParameter("enabled", "${'
        val document = StudioLogicDocument(actions = listOf(StudioActionDefinition(
            "go", "on_click", "navigate", target = "profile",
            navigationParameters = listOf(StudioNavigationParameter("bad-name", "x"), StudioNavigationParameter("amount", "not-a-number", "number"))
        )))
        val errors = document.validate(setOf("profile"))
        assertTrue(errors.any { it.contains("Nome de parâmetro inválido") })
        assertTrue(errors.any { it.contains("Parâmetro numérico inválido") })
    }

    @Test
    fun rejectsDuplicateNavigationParameterKeys() {
        val document = StudioLogicDocument(actions = listOf(StudioActionDefinition(
            "go", "on_click", "navigate", target = "profile",
            navigationParameters = listOf(StudioNavigationParameter("id", "1"), StudioNavigationParameter("id", "2"))
        )))
        assertTrue(document.validate().any { it.contains("Parâmetros duplicados") })
    }

    @Test
    fun readsLegacyActionsWithoutNavigationParameters() {
        val legacy = """{"schemaVersion":1,"variables":[],"actions":[{"id":"old","trigger":"on_click","kind":"navigate","target":"home","enabled":true}]}"""
        val restored = StudioLogicDocument.fromJson(legacy)
        assertTrue(restored.actions.single().navigationParameters.isEmpty())
    }
    @Test
    fun rejectsOversizedDocuments() {
        val oversized = " ".repeat(StudioLogicDocument.MAX_VALUE_LENGTH * 130)

        val error = runCatching { StudioLogicDocument.fromJson(oversized) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }
}
}{active}", "boolean")
                )
            ))
        )
        assertTrue(document.validate(setOf("checkout")).isEmpty())
    }

    @Test
    fun rejectsNavigationParameterReferencesWithMismatchedTypes() {
        val document = StudioLogicDocument(
            variables = listOf(StudioVariableDefinition("amount", "Valor", "number", "12")),
            actions = listOf(StudioActionDefinition(
                "go", "on_click", "navigate", target = "checkout",
                navigationParameters = listOf(StudioNavigationParameter("total", "${'
        val document = StudioLogicDocument(actions = listOf(StudioActionDefinition(
            "go", "on_click", "navigate", target = "profile",
            navigationParameters = listOf(StudioNavigationParameter("bad-name", "x"), StudioNavigationParameter("amount", "not-a-number", "number"))
        )))
        val errors = document.validate(setOf("profile"))
        assertTrue(errors.any { it.contains("Nome de parâmetro inválido") })
        assertTrue(errors.any { it.contains("Parâmetro numérico inválido") })
    }

    @Test
    fun rejectsDuplicateNavigationParameterKeys() {
        val document = StudioLogicDocument(actions = listOf(StudioActionDefinition(
            "go", "on_click", "navigate", target = "profile",
            navigationParameters = listOf(StudioNavigationParameter("id", "1"), StudioNavigationParameter("id", "2"))
        )))
        assertTrue(document.validate().any { it.contains("Parâmetros duplicados") })
    }

    @Test
    fun readsLegacyActionsWithoutNavigationParameters() {
        val legacy = """{"schemaVersion":1,"variables":[],"actions":[{"id":"old","trigger":"on_click","kind":"navigate","target":"home","enabled":true}]}"""
        val restored = StudioLogicDocument.fromJson(legacy)
        assertTrue(restored.actions.single().navigationParameters.isEmpty())
    }
    @Test
    fun rejectsOversizedDocuments() {
        val oversized = " ".repeat(StudioLogicDocument.MAX_VALUE_LENGTH * 130)

        val error = runCatching { StudioLogicDocument.fromJson(oversized) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }
}
}{amount}", "text"))
            ))
        )
        assertTrue(document.validate(setOf("checkout")).any {
            it.contains("Tipo incompatível na referência do parâmetro total")
        })
    }

    @Test
    fun rejectsInvalidNavigationParameterNamesAndTypes() {
        val document = StudioLogicDocument(actions = listOf(StudioActionDefinition(
            "go", "on_click", "navigate", target = "profile",
            navigationParameters = listOf(StudioNavigationParameter("bad-name", "x"), StudioNavigationParameter("amount", "not-a-number", "number"))
        )))
        val errors = document.validate(setOf("profile"))
        assertTrue(errors.any { it.contains("Nome de parâmetro inválido") })
        assertTrue(errors.any { it.contains("Parâmetro numérico inválido") })
    }

    @Test
    fun rejectsDuplicateNavigationParameterKeys() {
        val document = StudioLogicDocument(actions = listOf(StudioActionDefinition(
            "go", "on_click", "navigate", target = "profile",
            navigationParameters = listOf(StudioNavigationParameter("id", "1"), StudioNavigationParameter("id", "2"))
        )))
        assertTrue(document.validate().any { it.contains("Parâmetros duplicados") })
    }

    @Test
    fun readsLegacyActionsWithoutNavigationParameters() {
        val legacy = """{"schemaVersion":1,"variables":[],"actions":[{"id":"old","trigger":"on_click","kind":"navigate","target":"home","enabled":true}]}"""
        val restored = StudioLogicDocument.fromJson(legacy)
        assertTrue(restored.actions.single().navigationParameters.isEmpty())
    }
    @Test
    fun rejectsOversizedDocuments() {
        val oversized = " ".repeat(StudioLogicDocument.MAX_VALUE_LENGTH * 130)

        val error = runCatching { StudioLogicDocument.fromJson(oversized) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }
}
