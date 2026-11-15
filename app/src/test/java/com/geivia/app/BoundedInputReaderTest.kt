package com.geivia.app

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BoundedInputReaderTest {
    @Test
    fun readsUtf8ContentWithinLimit() {
        val content = """{"projeto":"ação","status":"ativo"}"""
        val result = readUtf8Limited(ByteArrayInputStream(content.toByteArray(Charsets.UTF_8)), 128)
        assertEquals(content, result)
    }

    @Test
    fun acceptsContentExactlyAtLimit() {
        val content = "12345678"
        assertEquals(content, readUtf8Limited(ByteArrayInputStream(content.toByteArray()), 8))
    }

    @Test
    fun rejectsOversizedContent() {
        val input = ByteArrayInputStream("123456789".toByteArray())
        val error = assertThrows(IllegalStateException::class.java) {
            readUtf8Limited(input, 8)
        }
        assertEquals("Arquivo excede o limite de 8 bytes.", error.message)
    }

    @Test
    fun rejectsNonPositiveLimit() {
        assertThrows(IllegalArgumentException::class.java) {
            readUtf8Limited(ByteArrayInputStream(byteArrayOf()), 0)
        }
    }

    @Test
    fun readsEmptyInput() {
        assertEquals("", readUtf8Limited(ByteArrayInputStream(byteArrayOf()), 8))
    }
}
