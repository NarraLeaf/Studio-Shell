package com.narraleaf.shell

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/**
 * [ContentDecoder] backed by the prebuilt decoder library.
 *
 * The library is a build input, not source: it arrives as a prebuilt `.so` per
 * ABI and exports the entry points declared below. This class is deliberately
 * the whole of the shell's knowledge on the subject — a header of
 * [nativeHeaderLen] bytes precedes the content, and content bytes are passed
 * through [nativeDecode] at their offset. What that does is not the shell's
 * business.
 *
 * The class and method names below are an ABI contract with the library:
 * renaming any of them breaks the JNI lookup, and it breaks it at runtime on a
 * device rather than at build time here.
 */
class NativeContentDecoder(private val key: String) : ContentDecoder {

    override fun decodedLength(sourceLength: Long): Long {
        // This is asked without the file in hand, so it leans on the packer
        // encoding the whole payload or none of it: a build with a key has a
        // header on every payload file. decode() checks each file individually
        // and fails loudly if that ever stops being true.
        val header = PREFIX_LEN.toLong()
        return if (sourceLength >= header) sourceLength - header else 0
    }

    override fun decode(source: InputStream, declaredLength: Long): InputStream {
        val head = ByteArray(PROBE_SIZE)
        var read = 0
        while (read < head.size) {
            val n = source.read(head, read, head.size - read)
            if (n < 0) break
            read += n
        }
        val headerLen = if (read < head.size) 0 else nativeHeaderLen(head)
        if (headerLen == 0) {
            source.close()
            // Serving the bytes as-is would hand the WebView ciphertext, which
            // fails far away from here (a blank game, a broken asset). This is
            // the packer and the shell disagreeing; say so.
            throw IOException("payload file is not encoded content")
        }
        if (headerLen != PREFIX_LEN) {
            // decodedLength() subtracts PREFIX_LEN without seeing the file. If
            // the library ever reports a different prefix than it asks to
            // probe, every Content-Length is silently wrong — refuse instead.
            source.close()
            throw IOException("decoder library reports an unexpected prefix ($headerLen != $PREFIX_LEN)")
        }
        val ctx = nativeOpen(head, key)
        if (ctx == 0L) {
            source.close()
            throw IOException("this build's key does not open its payload")
        }
        return DecodedStream(source, ctx)
    }

    /**
     * The decoded view of one file. Decodes lazily as it is read, and — the
     * load-bearing part — skips in O(1) by moving the offset instead of
     * decoding the bytes it passes over. Range requests seek by skipping, so
     * without this every seek into a long track would decode the whole track up
     * to that point.
     */
    private class DecodedStream(source: InputStream, private var ctx: Long) : FilterInputStream(source) {

        private var offset = 0L

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xff
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = `in`.read(b, off, len)
            if (n <= 0) return n
            if (!nativeDecode(ctx, offset, b, off, n)) {
                throw IOException("decode failed")
            }
            offset += n.toLong()
            return n
        }

        override fun skip(n: Long): Long {
            val skipped = `in`.skip(n)
            if (skipped > 0) offset += skipped
            return skipped
        }

        override fun markSupported(): Boolean = false

        override fun close() {
            try {
                super.close()
            } finally {
                if (ctx != 0L) {
                    nativeFree(ctx)
                    ctx = 0L
                }
            }
        }
    }

    companion object {
        /**
         * The fixed number of leading bytes the library asks to see, which is
         * also the prefix it strips. decode() asserts the two agree per file
         * rather than trusting that they always will.
         */
        private val PROBE_SIZE: Int by lazy { nativeProbeSize() }
        private val PREFIX_LEN: Int get() = PROBE_SIZE

        /** Whether the library is present and loadable in this build. */
        val isAvailable: Boolean = try {
            System.loadLibrary("nlcrypto")
            true
        } catch (error: UnsatisfiedLinkError) {
            // A build with no payload key never links it, which is the normal
            // case for an unprotected game.
            false
        }

        @JvmStatic
        private external fun nativeProbeSize(): Int

        @JvmStatic
        private external fun nativeHeaderLen(head: ByteArray): Int

        @JvmStatic
        private external fun nativeOpen(head: ByteArray, obfKey: String): Long

        @JvmStatic
        private external fun nativeDecode(ctx: Long, contentOffset: Long, data: ByteArray, off: Int, len: Int): Boolean

        @JvmStatic
        private external fun nativeFree(ctx: Long)
    }
}
