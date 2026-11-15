package com.geivia.app

import java.io.ByteArrayOutputStream
import java.io.InputStream

internal const val MAX_IMPORTED_JSON_BYTES = 5 * 1024 * 1024

/**
 * Reads UTF-8 input with a fixed-size buffer and rejects oversized files before
 * allocating memory proportional to the full input.
 */
internal fun readUtf8Limited(
    input: InputStream,
    maxBytes: Int = MAX_IMPORTED_JSON_BYTES
): String {
    require(maxBytes > 0) { "O limite de leitura deve ser maior que zero." }

    val output = ByteArrayOutputStream(minOf(maxBytes, 8192))
    val buffer = ByteArray(8192)
    var total = 0

    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (count == 0) continue
        if (count > maxBytes - total) {
            throw IllegalStateException("Arquivo excede o limite de $maxBytes bytes.")
        }
        output.write(buffer, 0, count)
        total += count
    }

    return output.toString(Charsets.UTF_8.name())
}
