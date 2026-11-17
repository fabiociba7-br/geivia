package com.geivia.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioScreenDocumentTest {
    @Test
    fun roundTripsScreenRegistry() {
        val document = StudioScreenDocument(
            screens = listOf(
                StudioScreenDefinition("home", "Início"),
                StudioScreenDefinition("profile", "Perfil")
            ),
            startScreenId = "home"
        )
        assertEquals(document, StudioScreenDocument.fromJson(document.toJson()))
        assertTrue(document.validate().isEmpty())
    }

    @Test
    fun rejectsDuplicateIdsAndMissingStartScreen() {
        val document = StudioScreenDocument(
            screens = listOf(
                StudioScreenDefinition("home", "Início"),
                StudioScreenDefinition("home", "Outra tela")
            ),
            startScreenId = "missing"
        )
        val errors = document.validate()
        assertTrue(errors.any { it.contains("duplicados") })
        assertTrue(errors.any { it.contains("tela inicial não existe") })
    }

    @Test
    fun rejectsInvalidIdsAndEmptyNames() {
        val document = StudioScreenDocument(
            screens = listOf(StudioScreenDefinition("tela inválida", " "))
        )
        assertTrue(document.validate().any { it.contains("ID de tela inválido") })
        assertTrue(document.validate().any { it.contains("Nome de tela inválido") })
    }

    @Test
    fun roundTripsTypedScreenInputParameters() {
        val document = StudioScreenDocument(
            screens = listOf(
                StudioScreenDefinition("home", "Início"),
                StudioScreenDefinition(
                    "checkout", "Pagamento",
                    listOf(
                        StudioScreenInputParameter("total", "Total", "number", required = true),
                        StudioScreenInputParameter("coupon", "Cupom"),
                        StudioScreenInputParameter("metadata", "Metadados", "json", defaultValue = """{"source":"cart"}""")
                    )
                )
            ),
            startScreenId = "home"
        )
        assertEquals(document, StudioScreenDocument.fromJson(document.toJson()))
        assertTrue(document.validate().isEmpty())
    }

    @Test
    fun readsLegacyScreenDocumentsWithoutInputParameters() {
        val legacy = """{"schemaVersion":1,"startScreenId":"home","screens":[{"id":"home","name":"Início"}]}"""
        val restored = StudioScreenDocument.fromJson(legacy)
        assertTrue(restored.screens.single().inputParameters.isEmpty())
    }

    @Test
    fun rejectsInvalidScreenInputParameterDefinitions() {
        val document = StudioScreenDocument(
            screens = listOf(
                StudioScreenDefinition(
                    "checkout", "Pagamento",
                    listOf(
                        StudioScreenInputParameter("bad-key", "Inválido"),
                        StudioScreenInputParameter("total", "Total", "number", defaultValue = "muito"),
                        StudioScreenInputParameter("active", "Ativo", "boolean", defaultValue = "sim"),
                        StudioScreenInputParameter("payload", "Dados", "json", defaultValue = "{broken")
                    )
                )
            ),
            startScreenId = "checkout"
        )
        val errors = document.validate()
        assertTrue(errors.any { it.contains("Identificador de parâmetro inválido") })
        assertTrue(errors.any { it.contains("Valor padrão numérico inválido") })
        assertTrue(errors.any { it.contains("Valor padrão booleano inválido") })
        assertTrue(errors.any { it.contains("Valor padrão JSON inválido") })
    }

    @Test
    fun rejectsDuplicateInputParameterKeys() {
        val document = StudioScreenDocument(
            screens = listOf(
                StudioScreenDefinition(
                    "profile", "Perfil",
                    listOf(
                        StudioScreenInputParameter("userId", "Usuário"),
                        StudioScreenInputParameter("userId", "Outro usuário")
                    )
                )
            ),
            startScreenId = "profile"
        )
        assertTrue(document.validate().any { it.contains("Parâmetros de entrada duplicados") })
    }
}
