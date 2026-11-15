package com.geivia.app

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        val result = runCatching {
            readUtf8Limited(ByteArrayInputStream(bytes), 8)
        }
        assertTrue(result.isFailure)
    }

    @Test
    fun boundedReaderRejectsNonPositiveLimit() {
        assertThrows(IllegalArgumentException::class.java) {
            readUtf8Limited(ByteArrayInputStream(byteArrayOf()), 0)
        }
    }

    @Test
    fun tokenRefreshDecisionUsesTwoMinuteSafetyWindow() {
        val savedAt = 1_000_000L
        assertFalse(shouldRefreshGitHubToken(28_800L, savedAt, savedAt + 28_800_000L - 180_000L))
        assertTrue(shouldRefreshGitHubToken(28_800L, savedAt, savedAt + 28_800_000L - 60_000L))
        assertTrue(shouldRefreshGitHubToken(28_800L, 0L, savedAt))
        assertFalse(shouldRefreshGitHubToken(0L, savedAt, savedAt + 99_999_999L))
    }
}
