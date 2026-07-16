import Foundation

/// Content types for the payload's file extensions.
///
/// Hardcoded rather than resolved through UniformTypeIdentifiers: the mapping
/// must be identical to the Android shell's, and a wrong type on a module
/// script or a media file breaks the game in ways that are painful to diagnose
/// on a user's device. The set of extensions a build can contain is known.
enum Mime {

    private static let byExtension: [String: String] = [
        // Documents and code
        "html": "text/html",
        "htm": "text/html",
        "js": "text/javascript",
        "mjs": "text/javascript",
        "css": "text/css",
        "json": "application/json",
        "map": "application/json",
        "txt": "text/plain",
        "xml": "text/xml",
        "svg": "image/svg+xml",
        "wasm": "application/wasm",

        // Images
        "png": "image/png",
        "jpg": "image/jpeg",
        "jpeg": "image/jpeg",
        "gif": "image/gif",
        "webp": "image/webp",
        "avif": "image/avif",
        "bmp": "image/bmp",
        "ico": "image/x-icon",

        // Audio
        "mp3": "audio/mpeg",
        "m4a": "audio/mp4",
        "aac": "audio/aac",
        "ogg": "audio/ogg",
        "oga": "audio/ogg",
        "opus": "audio/ogg",
        "wav": "audio/wav",
        "flac": "audio/flac",

        // Video
        "mp4": "video/mp4",
        "m4v": "video/mp4",
        "webm": "video/webm",
        "mkv": "video/x-matroska",
        "mov": "video/quicktime",

        // Fonts
        "woff": "font/woff",
        "woff2": "font/woff2",
        "ttf": "font/ttf",
        "otf": "font/otf",
    ]

    /// The content type for `path`, defaulting to a safe opaque type.
    static func of(_ path: String) -> String {
        let ext = (path as NSString).pathExtension.lowercased()
        return byExtension[ext] ?? "application/octet-stream"
    }

    /// Whether a type should be served with an explicit UTF-8 charset.
    static func isTextual(_ contentType: String) -> Bool {
        contentType.hasPrefix("text/")
            || contentType == "application/json"
            || contentType == "image/svg+xml"
    }
}
