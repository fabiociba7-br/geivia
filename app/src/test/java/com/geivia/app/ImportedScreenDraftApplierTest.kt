package com.geivia.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImportedScreenDraftApplierTest {
    @get:Rule val temp = TemporaryFolder()

    private fun xml(): File = File(temp.root, "screen.xml").apply {
        writeText(
            """<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" android:orientation="vertical">
                <TextView android:id="@+id/title" android:text="Antes" android:layout_width="wrap_content" android:layout_height="wrap_content" />
                <Button android:id="@+id/save" android:text="Salvar" android:layout_width="wrap_content" android:layout_height="wrap_content" />
            </LinearLayout>""".trimIndent()
        )
    }

    @Test fun appliesTextDimensionsAndReorderAsOneReviewedXmlBatch() {
        val file = xml()
        val fingerprint = ImportedScreenDraft.fingerprint(file)
        val changes = listOf(
            ImportedScreenChange.Text("title", "Antes", "Depois"),
            ImportedScreenChange.Dimensions("title", "wrap_content", "wrap_content", "240dp", "48dp"),
            ImportedScreenChange.Move("save", "__root__", 1, 0)
        )

        ImportedScreenDraftApplier.applyXml(file, changes, fingerprint)

        val components = ImportedXmlLayoutEditor.inspect(file)
        assertEquals(listOf("save", "title"), components.map { it.id })
        val title = components.first { it.id == "title" }
        assertEquals("Depois", title.text)
        assertEquals("240dp", title.width)
        assertEquals("48dp", title.height)
        assertFalse(file.parentFile!!.listFiles().orEmpty().any { it.name.contains("geivia.draft") })
    }

    @Test fun refusesXmlBatchWhenSourceChangedAfterDraftStarted() {
        val file = xml()
        val fingerprint = ImportedScreenDraft.fingerprint(file)
        file.appendText("\n<!-- changed outside GEIVIA -->")

        assertThrows(IllegalArgumentException::class.java) {
            ImportedScreenDraftApplier.applyXml(
                file,
                listOf(ImportedScreenChange.Text("title", "Antes", "Depois")),
                fingerprint
            )
        }
        assertEquals("Antes", ImportedXmlLayoutEditor.inspect(file).first().text)
    }

    @Test fun appliesMultipleComposeLiteralEditsTogether() {
        val file = File(temp.root, "Screen.kt").apply {
            writeText(
                """@Composable
                    fun SampleScreen() {
                        Column {
                            Text("Título")
                            Text(text = "Salvar")
                        }
                    }""".trimIndent()
            )
        }
        val fingerprint = ImportedScreenDraft.fingerprint(file)

        ImportedScreenDraftApplier.applyCompose(
            file,
            "SampleScreen",
            listOf(
                ImportedScreenChange.Text("text_0", "Título", "Painel"),
                ImportedScreenChange.Text("text_1", "Salvar", "Confirmar")
            ),
            fingerprint
        )

        assertEquals(
            listOf("Painel", "Confirmar"),
            ImportedComposeLayoutEditor.inspect(file, "SampleScreen").map { it.value }
        )
    }

    @Test fun coalescesRepeatedEditsAndRemovesChangesRevertedToOriginal() {
        val first = ImportedScreenDraft.stage(
            emptyList(),
            ImportedScreenChange.Text("title", "Antes", "Meio")
        )
        val second = ImportedScreenDraft.stage(
            first,
            ImportedScreenChange.Text("title", "Meio", "Depois")
        )
        assertEquals(1, second.size)
        assertEquals("Antes", (second.single() as ImportedScreenChange.Text).originalValue)
        assertEquals("Depois", (second.single() as ImportedScreenChange.Text).newValue)

        val reverted = ImportedScreenDraft.stage(
            second,
            ImportedScreenChange.Text("title", "Depois", "Antes")
        )
        assertTrue(reverted.isEmpty())
    }

    @Test fun resolvesMultipleXmlMovesAgainstTheSameOriginalSiblingOrder() {
        val file = File(temp.root, "three.xml").apply {
            writeText(
                """<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android">
                    <TextView android:id="@+id/first" android:text="1" />
                    <TextView android:id="@+id/second" android:text="2" />
                    <TextView android:id="@+id/third" android:text="3" />
                </LinearLayout>""".trimIndent()
            )
        }
        val fingerprint = ImportedScreenDraft.fingerprint(file)

        ImportedScreenDraftApplier.applyXml(
            file,
            listOf(
                ImportedScreenChange.Move("third", "__root__", 2, 0),
                ImportedScreenChange.Move("first", "__root__", 0, 2)
            ),
            fingerprint
        )

        assertEquals(
            listOf("third", "second", "first"),
            ImportedXmlLayoutEditor.inspect(file).map { it.id }
        )
    }

    @Test fun rejectsConflictingXmlMoveDestinationsWithoutChangingOriginal() {
        val file = xml()
        val original = file.readText()
        val fingerprint = ImportedScreenDraft.fingerprint(file)

        assertThrows(IllegalArgumentException::class.java) {
            ImportedScreenDraftApplier.applyXml(
                file,
                listOf(
                    ImportedScreenChange.Move("title", "__root__", 0, 0),
                    ImportedScreenChange.Move("save", "__root__", 1, 0)
                ),
                fingerprint
            )
        }
        assertEquals(original, file.readText())
    }

}
