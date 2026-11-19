package com.geivia.app

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Safe source writer for imported Android XML layouts.
 *
 * Only edits views with stable android:id values and only reorders siblings
 * inside LinearLayout containers. Unsupported layout types are rejected rather
 * than approximated with coordinates that would corrupt ConstraintLayout rules.
 */
internal object ImportedXmlLayoutEditor {
    data class Component(
        val id: String,
        val tag: String,
        val text: String?,
        val parentId: String?,
        val siblingIndex: Int,
        val canReorder: Boolean
    )

    fun inspect(layoutFile: File): List<Component> {
        val document = parse(layoutFile)
        val result = mutableListOf<Component>()
        fun visit(parent: Element, parentId: String?) {
            val children = elementChildren(parent)
            children.forEachIndexed { index, child ->
                val id = viewId(child)
                if (id != null) {
                    val parentTag = parent.tagName.substringAfterLast('.')
                    result += Component(
                        id = id,
                        tag = child.tagName.substringAfterLast('.'),
                        text = child.getAttributeNS(ANDROID_NS, "text").takeIf { it.isNotBlank() },
                        parentId = parentId,
                        siblingIndex = index,
                        canReorder = parentTag == "LinearLayout" && parentId != null
                    )
                }
                visit(child, id ?: parentId)
            }
        }
        visit(document.documentElement, viewId(document.documentElement) ?: "__root__")
        return result
    }

    fun updateText(layoutFile: File, componentId: String, text: String) {
        require(text.length <= MAX_TEXT_LENGTH) { "Texto excede o limite permitido." }
        edit(layoutFile) { document ->
            val target = findById(document, componentId)
                ?: throw IllegalArgumentException("Componente @id/$componentId não encontrado.")
            val tag = target.tagName.substringAfterLast('.')
            require(tag in TEXT_CAPABLE_TAGS) { "Este componente não possui atributo android:text editável." }
            target.setAttributeNS(ANDROID_NS, "android:text", text)
        }
    }

    fun moveSibling(layoutFile: File, componentId: String, newIndex: Int) {
        edit(layoutFile) { document ->
            val target = findById(document, componentId)
                ?: throw IllegalArgumentException("Componente @id/$componentId não encontrado.")
            val parent = target.parentNode as? Element
                ?: throw IllegalArgumentException("Componente sem contêiner válido.")
            require(parent.tagName.substringAfterLast('.') == "LinearLayout") {
                "Movimentação por arraste só é segura dentro de LinearLayout nesta versão."
            }
            val siblings = elementChildren(parent)
            require(siblings.all { viewId(it) != null }) { "Reordenação exige IDs estáveis em todos os elementos irmãos." }
            require(newIndex in siblings.indices) { "Posição de destino inválida." }
            val oldIndex = siblings.indexOfFirst { it === target }
            require(oldIndex >= 0) { "Componente não pertence ao contêiner informado." }
            if (oldIndex == newIndex) return@edit
            val reordered = siblings.toMutableList().apply {
                removeAt(oldIndex)
                add(newIndex, target)
            }
            reordered.forEach { parent.removeChild(it) }
            val anchor = reordered.lastOrNull()?.nextSibling
            reordered.forEach { child -> parent.insertBefore(child, anchor) }
        }
    }

    private fun edit(file: File, mutation: (Document) -> Unit) {
        require(file.isFile && file.extension.equals("xml", true)) {
            "Selecione um arquivo de layout XML existente."
        }
        val document = parse(file)
        mutation(document)
        // Validate the resulting document before replacing the original.
        require(document.documentElement != null) { "Layout XML inválido." }
        val temp = File(file.parentFile, file.name + ".geivia.tmp")
        try {
            val factory = TransformerFactory.newInstance()
            runCatching { factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            val transformer = factory.newTransformer()
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            transformer.setOutputProperty(OutputKeys.INDENT, "yes")
            FileOutputStream(temp).use { output ->
                transformer.transform(DOMSource(document), StreamResult(output))
                output.fd.sync()
            }
            // Reparse the serialized result before committing it.
            parse(temp)
            if (!temp.renameTo(file)) {
                FileOutputStream(file).use { output ->
                    FileInputStream(temp).use { input -> input.copyTo(output) }
                    output.fd.sync()
                }
                temp.delete()
            }
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun parse(file: File): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        }
        return FileInputStream(file).use { factory.newDocumentBuilder().parse(it) }
    }

    private fun findById(document: Document, id: String): Element? {
        require(ID_PATTERN.matches(id)) { "Identificador de componente inválido." }
        val nodes = document.getElementsByTagName("*")
        for (index in 0 until nodes.length) {
            val element = nodes.item(index) as? Element ?: continue
            if (viewId(element) == id) return element
        }
        return null
    }

    private fun viewId(element: Element): String? {
        val value = element.getAttributeNS(ANDROID_NS, "id")
            .ifBlank { element.getAttribute("android:id") }
        return value.substringAfterLast("/").takeIf { value.contains("@id/") || value.contains("@+id/") }
    }

    private fun elementChildren(parent: Element): List<Element> {
        val result = mutableListOf<Element>()
        val nodes = parent.childNodes
        for (index in 0 until nodes.length) {
            (nodes.item(index) as? Element)?.let(result::add)
        }
        return result
    }

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private const val MAX_TEXT_LENGTH = 4096
    private val ID_PATTERN = Regex("[A-Za-z_][A-Za-z0-9_]{0,127}")
    private val TEXT_CAPABLE_TAGS = setOf(
        "TextView", "Button", "EditText", "CheckedTextView", "ToggleButton",
        "RadioButton", "CheckBox", "Switch", "AutoCompleteTextView"
    )
}
