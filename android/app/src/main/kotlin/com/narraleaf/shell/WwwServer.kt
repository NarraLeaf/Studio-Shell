package com.narraleaf.shell

import android.content.res.AssetManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URLDecoder

/**
 * Serves the injected `assets/www/` payload to the WebView.
 *
 * Requests are intercepted for a fixed https origin rather than served from a
 * custom scheme: an https origin is a secure context, which is what keeps
 * IndexedDB (where the game's saves live) and the rest of the modern web
 * platform available. The host never resolves — nothing leaves the device.
 *
 * Range support is not optional. Media elements seek by asking for byte
 * ranges, and a server that answers every request with the whole file makes
 * seeking in music and video impossible.
 *
 * Bytes are streamed, never read whole: a game's video can be far larger than
 * the heap.
 */
class WwwServer(
    private val assets: AssetManager,
    private val decoder: ContentDecoder = IdentityContentDecoder,
) {

    /**
     * Intercept [request] when it targets the payload origin, or return null to
     * let the WebView handle it normally (which, for anything off-origin,
     * means the request simply fails — the shell has no network policy of its
     * own to enforce).
     */
    fun intercept(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url
        if (url.scheme != SCHEME || url.host != HOST) {
            return null
        }
        // The WebView issues HEAD for some media probes; both are read-only.
        val method = request.method.uppercase()
        if (method != "GET" && method != "HEAD") {
            return methodNotAllowed()
        }

        val assetPath = resolveAssetPath(url.path) ?: return notFound()
        val rangeHeader = request.requestHeaders.entries
            .firstOrNull { it.key.equals("Range", ignoreCase = true) }
            ?.value

        return runCatching { respond(assetPath, rangeHeader) }
            .getOrElse { notFound() }
    }

    /**
     * Map a URL path onto an asset path under the payload root, or null when it
     * escapes the root or is malformed. "/" serves the entry document.
     */
    private fun resolveAssetPath(rawPath: String?): String? {
        val decoded = runCatching {
            URLDecoder.decode(rawPath ?: "/", "UTF-8")
        }.getOrNull() ?: return null

        val trimmed = decoded.trimStart('/')
        val relative = if (trimmed.isEmpty() || trimmed.endsWith("/")) "${trimmed}index.html" else trimmed

        // Reject traversal before touching the asset manager: a payload path is
        // always a plain relative path.
        if (relative.contains("..") || relative.contains('\\') || relative.startsWith("/")) {
            return null
        }
        for (segment in relative.split('/')) {
            if (segment == "." || segment == ".." || segment.isEmpty()) {
                return null
            }
        }
        return "$WWW_ROOT$relative"
    }

    private fun respond(assetPath: String, rangeHeader: String?): WebResourceResponse {
        // openFd gives the real length; assets stored uncompressed in the APK
        // (which is how the repacker writes the payload) always have one.
        val sourceLength = assets.openFd(assetPath).use { it.length }
        val length = decoder.decodedLength(sourceLength)

        val contentType = Mime.of(assetPath)
        val encoding = if (Mime.isTextual(contentType)) "utf-8" else null

        val range = rangeHeader?.let { Range.parse(it, length) }
        if (rangeHeader != null && range == null) {
            return rangeNotSatisfiable(length)
        }

        val headers = LinkedHashMap<String, String>()
        headers["Accept-Ranges"] = "bytes"
        // The payload is immutable for the lifetime of an installed build, so
        // the WebView may cache it indefinitely.
        headers["Cache-Control"] = "public, max-age=31536000, immutable"

        if (range == null) {
            headers["Content-Length"] = length.toString()
            return WebResourceResponse(contentType, encoding, 200, "OK", headers, open(assetPath, length))
        }

        headers["Content-Range"] = "bytes ${range.start}-${range.end}/$length"
        headers["Content-Length"] = range.length.toString()
        val stream = SlicedInputStream(open(assetPath, length), range.start, range.length)
        return WebResourceResponse(contentType, encoding, 206, "Partial Content", headers, stream)
    }

    private fun open(assetPath: String, decodedLength: Long): InputStream {
        // ACCESS_STREAMING: the payload is read front-to-back and can be large.
        val raw = assets.open(assetPath, AssetManager.ACCESS_STREAMING)
        return decoder.decode(raw, decodedLength)
    }

    private fun notFound() = WebResourceResponse(
        "text/plain", "utf-8", 404, "Not Found",
        emptyMap(), ByteArrayInputStream(ByteArray(0)),
    )

    private fun methodNotAllowed() = WebResourceResponse(
        "text/plain", "utf-8", 405, "Method Not Allowed",
        mapOf("Allow" to "GET, HEAD"), ByteArrayInputStream(ByteArray(0)),
    )

    private fun rangeNotSatisfiable(length: Long) = WebResourceResponse(
        "text/plain", "utf-8", 416, "Range Not Satisfiable",
        mapOf("Content-Range" to "bytes */$length"), ByteArrayInputStream(ByteArray(0)),
    )

    /** An inclusive byte range, already clamped to the entity length. */
    internal data class Range(val start: Long, val end: Long) {
        val length: Long get() = end - start + 1

        companion object {
            /**
             * Parse a single-range `bytes=` header. Multi-range requests are
             * rejected (answering them needs a multipart body, and no media
             * element the payload uses asks for one).
             */
            fun parse(header: String, entityLength: Long): Range? {
                val spec = header.trim().removePrefix("bytes=").trim()
                if (spec.isEmpty() || spec.contains(',')) {
                    return null
                }
                val dash = spec.indexOf('-')
                if (dash < 0) {
                    return null
                }
                val rawStart = spec.substring(0, dash).trim()
                val rawEnd = spec.substring(dash + 1).trim()

                if (rawStart.isEmpty()) {
                    // "-N": the final N bytes.
                    val suffix = rawEnd.toLongOrNull() ?: return null
                    if (suffix <= 0L || entityLength == 0L) {
                        return null
                    }
                    val start = (entityLength - suffix).coerceAtLeast(0L)
                    return Range(start, entityLength - 1)
                }

                val start = rawStart.toLongOrNull() ?: return null
                if (start < 0 || start >= entityLength) {
                    return null
                }
                val end = if (rawEnd.isEmpty()) {
                    entityLength - 1
                } else {
                    (rawEnd.toLongOrNull() ?: return null).coerceAtMost(entityLength - 1)
                }
                if (end < start) {
                    return null
                }
                return Range(start, end)
            }
        }
    }

    /** Skips to [start] and stops after [length] bytes, without buffering. */
    private class SlicedInputStream(
        private val source: InputStream,
        start: Long,
        private val length: Long,
    ) : InputStream() {

        private var remaining = length

        init {
            var toSkip = start
            while (toSkip > 0) {
                val skipped = source.skip(toSkip)
                if (skipped <= 0) {
                    // skip() may legitimately return 0; fall back to reading.
                    if (source.read() < 0) {
                        break
                    }
                    toSkip -= 1
                } else {
                    toSkip -= skipped
                }
            }
        }

        override fun read(): Int {
            if (remaining <= 0) {
                return -1
            }
            val value = source.read()
            if (value >= 0) {
                remaining -= 1
            }
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, count: Int): Int {
            if (remaining <= 0) {
                return -1
            }
            val toRead = minOf(count.toLong(), remaining).toInt()
            val read = source.read(buffer, offset, toRead)
            if (read > 0) {
                remaining -= read
            }
            return read
        }

        override fun available(): Int = minOf(remaining, Int.MAX_VALUE.toLong()).toInt()

        override fun close() = source.close()
    }

    companion object {
        const val SCHEME = "https"

        /**
         * A name under the reserved .localhost TLD: guaranteed never to resolve
         * (RFC 6761), so a request can only ever be answered from inside the
         * app, while the https origin keeps the page in a secure context.
         */
        const val HOST = "shell.narraleaf.localhost"

        const val WWW_ROOT = "www/"

        /** The document the WebView is pointed at on launch. */
        val ENTRY_URL = "$SCHEME://$HOST/"
    }
}
