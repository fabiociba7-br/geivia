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
}
