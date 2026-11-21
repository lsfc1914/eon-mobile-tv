package com.example.eonmobiletv.data

import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

object GzipStreamHelper {

    fun openPossiblyGzipped(
        rawStream: InputStream,
        hintNameOrUrl: String? = null
    ): InputStream {
        val buffered = BufferedInputStream(rawStream, 16 * 1024)
        val hasGzHint = hintNameOrUrl
            ?.substringBefore('?')
            ?.substringBefore('#')
            ?.endsWith(".gz", ignoreCase = true) == true

        buffered.mark(2)
        val b1 = buffered.read()
        val b2 = buffered.read()
        buffered.reset()

        val hasGzipMagic = (b1 == 0x1F && b2 == 0x8B)
        return if (hasGzHint || hasGzipMagic) {
            GZIPInputStream(buffered)
        } else {
            buffered
        }
    }
}
