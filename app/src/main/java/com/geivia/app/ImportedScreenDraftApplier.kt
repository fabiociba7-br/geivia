package com.geivia.app

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Applies a reviewed batch to one imported source file.
 * The source fingerprint prevents overwriting edits made outside GEIVIA
 * after the draft was created. A single validated temporary file is committed.
 */
internal object ImportedScreenDraftApplier {
    fun applyXml(
        file: File,
        changes: List<ImportedScreenChange>,
        expectedFingerprint: String
    ) {
        require(changes.isNotEmpty()) { "Não há alterações para aplicar." }
        require(changes.all { it is ImportedScreenChange.Text || it is ImportedScreenChange.Dimensions || it is ImportedScreenChange.Move }) {
            "O rascunho contém uma operação incompatível com XML."
        }
        require(ImportedScreenDraft.fingerprint(file) == expectedFingerprint) {
            "O arquivo original foi alterado fora do GEIVIA. Descarte o rascunho e carregue a versão atual antes de continuar."
        }
        val document = parseXml(file)
        changes.forEach { change ->
            when (change) {
                is ImportedScreenChange.Text -> {
                    require(change.newValue.length <= MAX_TEXT_LENGTH) { "Texto excede o limite permitido." }
                    val target = findById(document, change.componentId)
                        ?: error("Componente @id/${change.componentId} não encontrado.")
                    require(target.tagName.substringAfterLast('.') in TEXT_CAPABLE_TAGS) {
                        "O componente @id/${change.componentId} não aceita edição de texto."
                    }
                    val current = target.getAttributeNS(ANDROID_NS, "text")
                        .ifBlank { target.getAttribute("android:text") }
                    require(current == change.originalValue) {
                        "O texto de @id/${change.componentId} mudou desde a criação do rascunho."
                    }
                    target.setAttributeNS(ANDROID_NS, "android:text", change.newValue)
                }
                is ImportedScreenChange.Dimensions -> {
                    require(DIMENSION_PATTERN.matches(change.newWidth) && DIMENSION_PATTERN.matches(change.newHeight)) {
                        "Dimensões inválidas para @id/${change.componentId}."
                    }
                    val target = findById(document, change.componentId)
                        ?: error("Componente @id/${change.componentId} não encontrado.")
                    val currentWidth = target.getAttributeNS(ANDROID_NS, "layout_width")
                        .ifBlank { target.getAttribute("android:layout_width") }
                    val currentHeight = target.getAttributeNS(ANDROID_NS, "layout_height")
                        .ifBlank { target.getAttribute("android:layout_height") }
                    require(currentWidth == change.originalWidth && currentHeight == change.originalHeight) {
                        "As dimensões de @id/${change.componentId} mudaram desde a criação do rascunho."
                    }
                    target.setAttributeNS(ANDROID_NS, "android:layout_width", change.newWidth)
                    target.setAttributeNS(ANDROID_NS, "android:layout_height", change.newHeight)
                }
                is ImportedScreenChange.Move -> {
                    val target = findById(document, change.componentId)
                        ?: error("Componente @id/${change.componentId} não encontrado.")
                    val parent = target.parentNode as? Element
                        ?: error("Componente sem contêiner válido.")
                    require(parent.tagName.substringAfterLast('.') == "LinearLayout") {
                        "Movimentação só é segura dentro de LinearLayout."
                    }
                    val actualParentId = viewId(parent) ?: "__root__"
                    require(actualParentId == (change.parentId ?: "__root__")) {
                        "O contêiner de @id/${change.componentId} mudou desde a criação do rascunho."
                    }
                    val siblings = elementChildren(parent)
                    require(siblings.all { viewId(it) != null }) {
                        "Reordenação exige IDs estáveis em todos os elementos irmãos."
                    }
                    require(change.newIndex in siblings.indices) { "Posição de destino inválida." }
                    val oldIndex = siblings.indexOfFirst { it === target }
                    require(oldIndex >= 0) { "Componente não pertence ao contêiner informado." }
                    if (oldIndex != change.newIndex) {
                        val reordered = siblings.toMutableList().apply {
                            removeAt(oldIndex)
                            add(change.newIndex, target)
                        }
                        reordered.forEach { parent.removeChild(it) }
                        val anchor = reordered.lastOrNull()?.nextSibling
                        reordered.forEach { child -> parent.insertBefore(child, anchor) }
                    }
                }
            }
        }
        require(document.documentElement != null) { "Layout XML inválido." }
        commitXml(file, document)
    }

    fun applyCompose(
        file: File,
        functionName: String,
        changes: List<ImportedScreenChange>,
        expectedFingerprint: String
    ) {
        val textChanges = changes.filterIsInstance<ImportedScreenChange.Text>()
        require(textChanges.isNotEmpty() && textChanges.size == changes.size) {
            "O rascunho contém operações não suportadas para Jetpack Compose."
        }
        require(ImportedScreenDraft.fingerprint(file) == expectedFingerprint) {
            "O arquivo original foi alterado fora do GEIVIA. Descarte o rascunho e carregue a versão atual antes de continuar."
        }
        val source = file.readText(Charsets.UTF_8)
        val inspected = ImportedComposeLayoutEditor.inspect(file, functionName)
        val replacements = textChanges.map { change ->
            val target = inspected.firstOrNull { it.id == change.componentId }
                ?: error("Texto ${change.componentId} não encontrado na função selecionada.")
            require(target.value == change.originalValue) {
                "O texto ${change.componentId} mudou desde a criação do rascunho."
            }
            val encoded = "\"" + change.newValue
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\""
            Triple(target.start, target.end, encoded)
        }
        var updated = source
        replacements.sortedByDescending { it.first }.forEach { (start, end, value) ->
            updated = updated.substring(0, start) + value + updated.substring(end)
        }
        require(updated.length <= MAX_SOURCE_LENGTH) { "Arquivo Kotlin excede o limite permitido." }
        val temp = File(file.parentFile, file.nameWithoutExtension + ".geivia.draft.tmp.kt")
        try {
            temp.writeText(updated, Charsets.UTF_8)
            val verified = ImportedComposeLayoutEditor.inspect(temp, functionName)
            textChanges.forEach { change ->
                require(verified.any { it.id == change.componentId && it.value == change.newValue }) {
                    "A validação do Kotlin modificado falhou para ${change.componentId}."
                }
            }
            replaceAtomically(file, temp)
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun commitXml(file: File, document: Document) {
        val temp = File(file.parentFile, file.name + ".geivia.draft.tmp")
        try {
            val factory = TransformerFactory.newInstance()
            runCatching { factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            val transformer = factory.newTransformer().apply {
                setOutputProperty(OutputKeys.ENCODING, "UTF-8")
                setOutputProperty(OutputKeys.INDENT, "yes")
            }
            FileOutputStream(temp).use { output ->
                transformer.transform(DOMSource(document), StreamResult(output))
                output.fd.sync()
            }
            parseXml(temp)
            ImportedXmlLayoutEditor.inspect(temp)
            replaceAtomically(file, temp)
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun replaceAtomically(original: File, temp: File) {
        try {
            Files.move(temp.toPath(), original.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            val backup = File(original.parentFile, original.name + ".geivia.draft.bak")
            if (backup.exists()) backup.delete()
            require(original.renameTo(backup)) { "Não foi possível proteger o arquivo original." }
            if (!temp.renameTo(original)) {
                backup.renameTo(original)
                throw IllegalStateException("Não foi possível aplicar o rascunho; o original foi restaurado.")
            }
            backup.delete()
        }
    }

    private fun parseXml(file: File): Document {
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
        for (index in 0 until nodes.length) (nodes.item(index) as? Element)?.let(result::add)
        return result
    }

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private const val MAX_TEXT_LENGTH = 4096
    private const val MAX_SOURCE_LENGTH = 2L * 1024L * 1024L
    private val ID_PATTERN = Regex("[A-Za-z_][A-Za-z0-9_]{0,127}")
    private val DIMENSION_PATTERN = Regex("(?:wrap_content|match_parent|0dp|[1-9][0-9]{0,4}dp)")
    private val TEXT_CAPABLE_TAGS = setOf(
        "TextView", "Button", "EditText", "CheckedTextView", "ToggleButton",
        "RadioButton", "CheckBox", "Switch", "AutoCompleteTextView"
    )
}
