package com.geivia.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeToStudioParserTest {
    @Test
    fun parsesComposableScreensAndVisibleComponents() {
        val root = createTempDir(prefix = "geivia-compose-")
        try {
            val source = File(root, "app/src/main/java/SampleScreen.kt")
            source.parentFile?.mkdirs()
            source.writeText(
                """
                @Composable
                fun HomeScreen() {
                    Column {
                        Text("Olá")
                        Button(onClick = {}) { Text("Entrar") }
                    }
                }
                """.trimIndent()
            )

            val parsed = ComposeToStudioParser.parse(root)
            assertNotNull(parsed)
            assertEquals("Home Screen", parsed!!.screens.first().name)
            val components = parsed.elementsByScreen.values.flatten()
            assertTrue(components.any { it.type == "text" && it.text == "Olá" })
            assertTrue(components.any { it.type == "button" && it.text == "Entrar" })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun ignoresAiStudioAndEditorMetadataWhenImportingScreens() {
        val root = createTempDir(prefix = "geivia-compose-metadata-")
        try {
            val metadata = File(root, ".aistudio/artifacts/brain/RulesScreen.kt")
            metadata.parentFile?.mkdirs()
            metadata.writeText(
                """
                @Composable
                fun RulesScreen() { Text("Metadata que não deve ser importado") }
                """.trimIndent()
            )
            val real = File(root, "app/src/main/java/RealScreen.kt")
            real.parentFile?.mkdirs()
            real.writeText(
                """
                @Composable
                fun RealHomeScreen() { Text("Tela real") }
                """.trimIndent()
            )

            val parsed = ComposeToStudioParser.parse(root)
            assertNotNull(parsed)
            assertTrue(parsed!!.screens.any { it.name == "Real Home Screen" })
            assertTrue(parsed.screens.none { it.name == "Rules Screen" })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun ignoresBuildAndDependencyDirectories() {
        val root = createTempDir(prefix = "geivia-compose-ignore-")
        try {
            val ignored = File(root, "build/generated/GeneratedScreen.kt")
            ignored.parentFile?.mkdirs()
            ignored.writeText(
                """
                @Composable
                fun GeneratedScreen() { Text("Não importar") }
                """.trimIndent()
            )
            assertEquals(null, ComposeToStudioParser.parse(root))
        } finally {
            root.deleteRecursively()
        }
    }
}
