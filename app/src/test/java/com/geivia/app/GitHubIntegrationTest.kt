package com.geivia.app

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GitHubIntegrationTest {
    @Test
    fun boundedReaderPreservesUtf8() {
        val value = "GEIVIA integração ✓"
        val result = readUtf8Limited(ByteArrayInputStream(value.toByteArray(Charsets.UTF_8)), 128)
        assertEquals(value, result)
    }

    @Test
    fun boundedReaderAcceptsExactLimit() {
        val bytes = "12345678".toByteArray()
        assertEquals("12345678", readUtf8Limited(ByteArrayInputStream(bytes), bytes.size))
    }

    @Test
    fun boundedReaderRejectsOversizedResponse() {
        val bytes = "123456789".toByteArray()
        assertThrows(IllegalArgumentException::class.java) {
            readUtf8Limited(ByteArrayInputStream(bytes), 8)
        }
    }

    @Test
    fun boundedReaderRejectsNonPositiveLimit() {
        assertThrows(IllegalArgumentException::class.java) {
            readUtf8Limited(ByteArrayInputStream(byteArrayOf()), 0)
        }
    }
}
