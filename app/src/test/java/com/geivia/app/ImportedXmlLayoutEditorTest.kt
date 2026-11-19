package com.geivia.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImportedXmlLayoutEditorTest {
    @get:Rule val temp = TemporaryFolder()

    private fun layout(source: String): File = File(temp.root, "screen.xml").apply {
        writeText(
            """<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:orientation="vertical">
                $source
            </LinearLayout>""".trimIndent()
        )
    }

    @Test fun inspectsStableIdsAndReportsLinearLayoutReorderingSupport() {
        val file = layout(
            """<TextView android:id="@+id/title" android:text="Título" />
               <Button android:id="@+id/save" android:text="Salvar" />"""
        )

        val components = ImportedXmlLayoutEditor.inspect(file)

        assertEquals(listOf("title", "save"), components.map { it.id })
        assertEquals("Título", components.first().text)
        assertTrue(components.all { it.canReorder })
    }

    @Test fun updatesTextInTheOriginalXmlFile() {
        val file = layout("""<TextView android:id="@+id/title" android:text="Antes" />""")

        ImportedXmlLayoutEditor.updateText(file, "title", "Depois")

        assertTrue(file.readText().contains("android:text=\"Depois\""))
        assertEquals("Depois", ImportedXmlLayoutEditor.inspect(file).single().text)
    }

    @Test fun reordersSiblingsWithoutChangingTheirIds() {
        val file = layout(
            """<TextView android:id="@+id/first" android:text="1" />
               <Button android:id="@+id/second" android:text="2" />
               <TextView android:id="@+id/third" android:text="3" />"""
        )

        ImportedXmlLayoutEditor.moveSibling(file, "third", 0)

        assertEquals(listOf("third", "first", "second"), ImportedXmlLayoutEditor.inspect(file).map { it.id })
    }

    @Test fun refusesUnsafeReorderingOutsideLinearLayout() {
        val file = File(temp.root, "constraint.xml")
        file.writeText(
            """<androidx.constraintlayout.widget.ConstraintLayout
                xmlns:android="http://schemas.android.com/apk/res/android">
                <TextView android:id="@+id/title" />
            </androidx.constraintlayout.widget.ConstraintLayout>"""
        )

        assertThrows(IllegalArgumentException::class.java) {
            ImportedXmlLayoutEditor.moveSibling(file, "title", 0)
        }
        assertFalse(file.readText().isBlank())
    }

    @Test fun refusesTextUpdatesForComponentsWithoutTextAttribute() {
        val file = layout("""<ImageView android:id="@+id/logo" />""")

        assertThrows(IllegalArgumentException::class.java) {
            ImportedXmlLayoutEditor.updateText(file, "logo", "Não suportado")
        }
    }
}
