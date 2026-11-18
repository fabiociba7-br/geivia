package com.geivia.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImportedScreenDiscoveryTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun discoversXmlLayoutAndExtractsViewIdsAndText() {
        val root = temp.newFolder("android")
        val layout = File(root, "app/src/main/res/layout/activity_main.xml")
        layout.parentFile!!.mkdirs()
        layout.writeText(
            """<LinearLayout>
                <TextView android:id="@+id/title" android:text="Olá" />
                <Button android:id="@+id/save" android:text="Salvar" />
            </LinearLayout>"""
        )

        val screens = ImportedScreenDiscovery.scan(root)

        assertEquals(1, screens.size)
        assertEquals("activity_main", screens.single().name)
        assertEquals(ImportedScreen.Format.ANDROID_XML, screens.single().format)
        assertTrue(screens.single().components.any { it.type == "TextView" && it.id == "title" && it.text == "Olá" })
        assertTrue(screens.single().components.any { it.type == "Button" && it.id == "save" })
    }

    @Test fun discoversComposableFunctionsAndCommonComposeComponents() {
        val root = temp.newFolder("compose")
        val source = File(root, "app/src/main/java/example/MainScreen.kt")
        source.parentFile!!.mkdirs()
        source.writeText(
            """package example
                @Composable
                fun HomeScreen() {
                    Column {
                        Text("Olá")
                        Button(onClick = {}) { Text("Salvar") }
                    }
                }
                @Composable
                fun SettingsScreen() {
                    Row { Text("Configurações") }
                }
            """.trimIndent()
        )

        val screens = ImportedScreenDiscovery.scan(root)

        assertEquals(listOf("HomeScreen", "SettingsScreen"), screens.map { it.name })
        assertTrue(screens.first().components.any { it.type == "Column" })
        assertTrue(screens.first().components.any { it.type == "Button" })
        assertTrue(screens.first().components.any { it.type == "Text" && it.text == "×2" })
        assertTrue(screens.last().components.any { it.type == "Row" })
    }

    @Test fun ignoresBuildAndDependencyDirectories() {
        val root = temp.newFolder("project")
        File(root, "app/src/main/res/layout/main.xml").apply {
            parentFile!!.mkdirs()
            writeText("<TextView />")
        }
        File(root, "build/generated/res/layout/fake.xml").apply {
            parentFile!!.mkdirs()
            writeText("<TextView />")
        }
        File(root, "node_modules/pkg/src/main/Fake.kt").apply {
            parentFile!!.mkdirs()
            writeText("@Composable fun Fake() { Text(\"fake\") }")
        }

        val screens = ImportedScreenDiscovery.scan(root)

        assertEquals(1, screens.size)
        assertEquals("main", screens.single().name)
    }
}
