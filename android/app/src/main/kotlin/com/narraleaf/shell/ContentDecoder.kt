package com.narraleaf.shell

import java.io.InputStream

/**
 * Transform hook applied to payload bytes on their way out of [WwwServer].
 *
 * The contract is deliberately generic: bytes in, bytes out. An implementation
 * sees an opaque stream and returns an opaque stream; it is told nothing about
 * the request, and it must not need to be.
 *
 * [IdentityContentDecoder] is the default and returns its input unchanged.
 */
interface ContentDecoder {

    /**
     * Wrap [source], whose content is [declaredLength] bytes long, or -1 when
     * the length is unknown. Returns the stream the server reads from.
     *
     * Implementations must be safe to call from multiple threads: WebView
     * issues intercepted requests concurrently.
     */
    fun decode(source: InputStream, declaredLength: Long): InputStream

    /**
     * The number of bytes [decode] will yield for an input of [sourceLength],
     * or -1 when that cannot be known before reading. The server uses this to
     * answer range requests, so an implementation that changes the length must
     * report it here.
     */
    fun decodedLength(sourceLength: Long): Long
}

/** Passes bytes through untouched. */
object IdentityContentDecoder : ContentDecoder {
    override fun decode(source: InputStream, declaredLength: Long): InputStream = source
    override fun decodedLength(sourceLength: Long): Long = sourceLength
}
