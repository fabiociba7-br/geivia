package com.geivia.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioElementSchemaTest {
    @Test
    fun legacyJsonLoadsWithSafeDefaults() {
        val decoded = StudioElementJsonCodec.decodeList(
            """[{"id":"legacy","type":"text","text":"Olá"}]"""
        ).single()

        assertEquals("legacy", decoded.id)
        assertEquals("Olá", decoded.text)
        assertEquals(16f, decoded.fontSize, 0.001f)
        assertEquals(12f, decoded.padding)
        assertTrue(decoded.fillMaxWidth)
        assertTrue(decoded.enabled)
        assertEquals("fit", decoded.contentScale)
        assertNull(decoded.weight)
        assertNull(decoded.heightDp)
        assertNull(decoded.placeholder)
    }

    @Test
    fun extendedSchemaRoundTripsAllOptionalProperties() {
        val source = StudioElement(
            id = "field_1",
            type = "textfield",
            text = "Nome",
            weight = 2.5f,
            fillMaxWidth = false,
            heightDp = 56f,
            enabled = false,
            placeholder = "Digite seu nome",
            contentScale = "crop"
        )
        val decoded = StudioElementJsonCodec.decodeList(StudioElementJsonCodec.encodeList(listOf(source))).single()

        assertEquals(source, decoded)
    }

    @Test
    fun generatedComposeRetainsMainComponentTypesAndDefaults() {
        val elements = listOf(
            StudioGeneratedElement("t", "text", "Título", 20f, 8f, "start", null, "#FFFFFF", "#17354D", 8f, true),
            StudioGeneratedElement("b", "button", "Continuar", 16f, 12f, "center", null, "#FFFFFF", "#17354D", 10f, true),
            StudioGeneratedElement("o", "outlined_button", "Cancelar", 16f, 12f, "center", null, "#FFFFFF", "#17354D", 10f, true),
            StudioGeneratedElement("f", "textfield", "E-mail", 16f, 12f, "start", null, "#FFFFFF", "#17354D", 10f, true, placeholder = "seu@email.com"),
            StudioGeneratedElement("s", "switch", "Ativar", 16f, 8f, "start", null, "#FFFFFF", "#17354D", 8f, true),
            StudioGeneratedElement("c", "checkbox", "Aceito", 16f, 8f, "start", null, "#FFFFFF", "#17354D", 8f, true)
        )
        val generated = StudioProjectGenerator.files(
            packageName = "com.example.preview",
            appName = "Preview",
            screens = listOf(StudioScreenDefinition("home", "Início")),
            components = mapOf("home" to elements),
            startScreenId = "home",
            primaryColor = "#1769AA",
            backgroundColor = "#F3F6FA",
            textColor = "#17354D"
        ).values.joinToString("\n")

        assertTrue(generated.contains("Button("))
        assertTrue(generated.contains("OutlinedButton("))
        assertTrue(generated.contains("OutlinedTextField("))
        assertTrue(generated.contains("Switch("))
        assertTrue(generated.contains("Checkbox("))
        assertTrue(generated.contains("Alignment.CenterVertically"))
        assertTrue(generated.contains("singleLine = true"))
    }
}
