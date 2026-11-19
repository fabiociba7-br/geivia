package com.geivia.app

import java.io.File

/**
 * Conservative Kotlin source editor for literal Text(...) arguments inside a
 * single @Composable function. It deliberately refuses interpolated or computed
 * expressions; no broad regex rewrite is performed.
 */
internal object ImportedComposeLayoutEditor {
    data class LiteralText(val id: String, val value: String, val start: Int, val end: Int)

    fun inspect(file: File, functionName: String): List<LiteralText> {
        val source = readSource(file)
        val block = composableBody(source, functionName)
        val result = mutableListOf<LiteralText>()
        var cursor = block.first
        while (cursor < block.second) {
            val call = findIdentifierCall(source, "Text", cursor, block.second) ?: break
            val open = source.indexOf('(', call)
            val close = matching(source, open, '(', ')', block.second) ?: break
            val argumentStart = skipTrivia(source, open + 1, close)
            val literal = stringLiteral(source, argumentStart, close)
            if (literal != null) {
                val before = source.substring(argumentStart, literal.first)
                if (before.isBlank() || before.trim() == "text =") {
                    val ordinal = result.size
                    result += LiteralText("text_$ordinal", decode(source.substring(literal.first, literal.second)), literal.first, literal.second)
                }
            }
            cursor = close + 1
        }
        return result
    }

    fun updateText(file: File, functionName: String, componentId: String, value: String) {
        require(value.length <= MAX_TEXT_LENGTH) { "Texto excede o limite permitido." }
        val source = readSource(file)
        val target = inspect(file, functionName).firstOrNull { it.id == componentId }
            ?: throw IllegalArgumentException("Texto literal não encontrado na função selecionada.")
        val encoded = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""
        val updated = source.substring(0, target.start) + encoded + source.substring(target.end)
        require(updated.length <= MAX_SOURCE_LENGTH) { "Arquivo Kotlin excede o limite permitido." }
        val temp = File(file.parentFile, file.name + ".geivia.tmp")
        val backup = File(file.parentFile, file.name + ".geivia.bak")
        try {
            temp.writeText(updated, Charsets.UTF_8)
            // Ensure the modified file still contains the same composable and can be inspected.
            require(inspectText(temp, functionName).any { it.id == componentId && it.value == value }) {
                "A validação do Kotlin modificado falhou."
            }
            if (backup.exists()) backup.delete()
            require(file.renameTo(backup)) { "Não foi possível criar backup do arquivo Kotlin." }
            if (!temp.renameTo(file)) {
                backup.renameTo(file)
                throw IllegalStateException("Não foi possível salvar o Kotlin; o original foi restaurado.")
            }
            backup.delete()
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun inspectText(file: File, functionName: String): List<LiteralText> {
        val source = readSource(file)
        val block = composableBody(source, functionName)
        val result = mutableListOf<LiteralText>()
        var cursor = block.first
        while (cursor < block.second) {
            val call = findIdentifierCall(source, "Text", cursor, block.second) ?: break
            val open = source.indexOf('(', call)
            val close = matching(source, open, '(', ')', block.second) ?: break
            val argumentStart = skipTrivia(source, open + 1, close)
            val literal = stringLiteral(source, argumentStart, close)
            if (literal != null) {
                val before = source.substring(argumentStart, literal.first)
                if (before.isBlank() || before.trim() == "text =") {
                    val ordinal = result.size
                    result += LiteralText("text_$ordinal", decode(source.substring(literal.first, literal.second)), literal.first, literal.second)
                }
            }
            cursor = close + 1
        }
        return result
    }

    private fun readSource(file: File): String {
        require(file.isFile && file.extension.equals("kt", true)) { "Selecione um arquivo Kotlin existente." }
        require(file.length() <= MAX_SOURCE_LENGTH) { "Arquivo Kotlin excede o limite permitido." }
        return file.readText(Charsets.UTF_8).also {
            require(!it.contains('\u0000')) { "Arquivo Kotlin inválido." }
        }
    }

    private fun composableBody(source: String, name: String): Pair<Int, Int> {
        require(IDENTIFIER.matches(name)) { "Nome de função inválido." }
        val declaration = Regex("""@Composable(?:\\s*\\([^)]*\\))?\\s+(?:(?:private|internal|public|protected|inline|suspend|override)\\s+)*fun\\s+${Regex.escape(name)}\\s*\\(""")
            .find(source) ?: throw IllegalArgumentException("Função @Composable não encontrada.")
        val paramsOpen = source.indexOf('(', declaration.range.first)
        val paramsClose = matching(source, paramsOpen, '(', ')', source.length)
            ?: throw IllegalArgumentException("Parâmetros Kotlin não balanceados.")
        val bodyOpen = source.indexOf('{', paramsClose)
        require(bodyOpen >= 0) { "Corpo da função não encontrado." }
        val bodyClose = matching(source, bodyOpen, '{', '}', source.length)
            ?: throw IllegalArgumentException("Corpo Kotlin não balanceado.")
        return (bodyOpen + 1) to bodyClose
    }

    private fun findIdentifierCall(source: String, name: String, from: Int, limit: Int): Int? {
        var i = from
        while (i < limit) {
            i = source.indexOf(name, i)
            if (i < 0 || i >= limit) return null
            val beforeOk = i == 0 || !source[i - 1].isLetterOrDigit() && source[i - 1] != '_'
            var next = i + name.length
            while (next < limit && source[next].isWhitespace()) next++
            if (beforeOk && next < limit && source[next] == '(') return i
            i += name.length
        }
        return null
    }

    private fun stringLiteral(source: String, start: Int, limit: Int): Pair<Int, Int>? {
        var i = start
        while (i < limit && source[i].isWhitespace()) i++
        if (source.startsWith("text", i)) {
            i += 4
            while (i < limit && source[i].isWhitespace()) i++
            if (i >= limit || source[i++] != '=') return null
            while (i < limit && source[i].isWhitespace()) i++
        }
        if (i >= limit || source[i] != '"') return null
        val begin = i++
        while (i < limit) {
            if (source[i] == '\\') { i += 2; continue }
            if (source[i] == '"') return begin to (i + 1)
            i++
        }
        return null
    }

    private fun skipTrivia(source: String, from: Int, limit: Int): Int {
        var i = from
        while (i < limit && source[i].isWhitespace()) i++
        return i
    }

    private fun matching(source: String, start: Int, open: Char, close: Char, limit: Int): Int? {
        if (start !in source.indices || source[start] != open) return null
        var depth = 0
        var i = start
        var inString = false
        var triple = false
        var escaped = false
        while (i < limit) {
            if (!inString && source.startsWith("//", i)) {
                i = (source.indexOf('\n', i).takeIf { it >= 0 } ?: limit)
                continue
            }
            if (!inString && source.startsWith("/*", i)) {
                val end = source.indexOf("*/", i + 2)
                i = if (end < 0) limit else end + 2
                continue
            }
            if (source.startsWith("\"\"\"", i)) {
                triple = !triple
                i += 3
                continue
            }
            val c = source[i]
            if (!triple && c == '"' && !escaped) inString = !inString
            if (!inString && !triple) {
                if (c == open) depth++
                if (c == close) {
                    depth--
                    if (depth == 0) return i
                }
            }
            escaped = !escaped && c == '\\'
            if (c != '\\') escaped = false
            i++
        }
        return null
    }

    private fun decode(value: String): String = value.removeSurrounding("\"")
        .replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t")
        .replace("\\\"", "\"").replace("\\\\", "\\")

    private val IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]{0,127}")
    private const val MAX_TEXT_LENGTH = 4096
    private const val MAX_SOURCE_LENGTH = 2L * 1024L * 1024L
}
