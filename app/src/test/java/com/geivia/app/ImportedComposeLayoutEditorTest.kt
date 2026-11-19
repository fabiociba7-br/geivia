package com.geivia.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImportedComposeLayoutEditorTest {
    @get:Rule val temp = TemporaryFolder()

    private fun source(): File = File(temp.root, "Screen.kt").apply {
        writeText(
            """package sample
                @Composable
                fun SampleScreen() {
                    Column {
                        Text("Título")
                        Text(text = "Salvar")
                        Text(text = dynamicLabel)
                        Text("prefix" + dynamicSuffix)
                    }
                }
            """.trimIndent()
        )
    }

    @Test fun inspectsOnlyLiteralTextArguments() {
        val file = source()

        val result = ImportedComposeLayoutEditor.inspect(file, "SampleScreen")

        assertEquals(listOf("Título", "Salvar"), result.map { it.value })
        assertEquals(listOf("text_0", "text_1"), result.map { it.id })
    }

    @Test fun updatesOnlySelectedLiteralAndPreservesOtherKotlin() {
        val file = source()

        ImportedComposeLayoutEditor.updateText(file, "SampleScreen", "text_1", "Confirmar")

        val updated = file.readText()
        assertTrue(updated.contains("""Text(text = "Confirmar")"""))
        assertTrue(updated.contains("""Text("Título")"""))
        assertTrue(updated.contains("Text(text = dynamicLabel)"))
        assertTrue(updated.contains("""Text("prefix" + dynamicSuffix)"""))
        assertEquals("Confirmar", ImportedComposeLayoutEditor.inspect(file, "SampleScreen")[1].value)
    }

    @Test fun refusesComputedTextExpressions() {
        val file = source()

        assertThrows(IllegalArgumentException::class.java) {
            ImportedComposeLayoutEditor.updateText(file, "SampleScreen", "text_2", "Novo")
        }
    }

    @Test fun refusesUnknownComposableFunction() {
        val file = source()

        assertThrows(IllegalArgumentException::class.java) {
            ImportedComposeLayoutEditor.inspect(file, "MissingScreen")
        }
    }
}
