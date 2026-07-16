package com.narraleaf.shell

/**
 * Content types for the payload's file extensions.
 *
 * Hardcoded rather than read from the platform's MimeTypeMap: the table there
 * varies by OEM and Android version, and a wrong type on a module script or a
 * media file breaks the game in ways that are painful to diagnose on a user's
 * device. The set of extensions a build can contain is known and small.
 */
object Mime {

    private val BY_EXTENSION = mapOf(
        // Documents and code
        "html" to "text/html",
        "htm" to "text/html",
        "js" to "text/javascript",
        "mjs" to "text/javascript",
        "css" to "text/css",
        "json" to "application/json",
        "map" to "application/json",
        "txt" to "text/plain",
        "xml" to "text/xml",
        "svg" to "image/svg+xml",
        "wasm" to "application/wasm",

        // Images
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "gif" to "image/gif",
        "webp" to "image/webp",
        "avif" to "image/avif",
        "bmp" to "image/bmp",
        "ico" to "image/x-icon",

        // Audio
        "mp3" to "audio/mpeg",
        "m4a" to "audio/mp4",
        "aac" to "audio/aac",
        "ogg" to "audio/ogg",
        "oga" to "audio/ogg",
        "opus" to "audio/ogg",
        "wav" to "audio/wav",
        "flac" to "audio/flac",

        // Video
        "mp4" to "video/mp4",
        "m4v" to "video/mp4",
        "webm" to "video/webm",
        "mkv" to "video/x-matroska",
        "mov" to "video/quicktime",

        // Fonts
        "woff" to "font/woff",
        "woff2" to "font/woff2",
        "ttf" to "font/ttf",
        "otf" to "font/otf",
    )

    /** The content type for [path], defaulting to a safe opaque type. */
    fun of(path: String): String {
        val dot = path.lastIndexOf('.')
        val extension = if (dot >= 0) path.substring(dot + 1).lowercase() else ""
        return BY_EXTENSION[extension] ?: "application/octet-stream"
    }

    /** Whether a type should be served with an explicit UTF-8 charset. */
    fun isTextual(contentType: String): Boolean =
        contentType.startsWith("text/")
            || contentType == "application/json"
            || contentType == "image/svg+xml"
}
