import Foundation
import WebKit

/// Serves the payload bundled beside the executable to the web view.
///
/// A custom scheme, not https: WebKit refuses to hand a `WKURLSchemeHandler`
/// any scheme it implements itself, so unlike the Android shell (which
/// intercepts an https origin) the payload has to live under a scheme of our
/// own. Nothing leaves the device either way.
///
/// Range support is not optional. AVFoundation, which backs `<video>` and
/// `<audio>`, asks for byte ranges and will not seek against a server that
/// only ever returns whole files.
///
/// Bytes are read through a file handle at an offset, never by loading the
/// whole file: a game's video can be far larger than the available memory.
final class WwwSchemeHandler: NSObject, WKURLSchemeHandler {

    static let scheme = "nlshell"
    static let host = "game"
    static let wwwRoot = "www"

    /// The document the web view is pointed at on launch.
    static var entryURL: URL {
        URL(string: "\(scheme)://\(host)/")!
    }

    private let rootDirectory: URL
    private let decoder: ContentDecoder
    private let queue = DispatchQueue(label: "com.narraleaf.shell.www", qos: .userInitiated, attributes: .concurrent)
    private let lock = NSLock()
    private var cancelled = Set<ObjectIdentifier>()

    init(rootDirectory: URL, decoder: ContentDecoder = IdentityContentDecoder()) {
        self.rootDirectory = rootDirectory
        self.decoder = decoder
    }

    func webView(_ webView: WKWebView, start urlSchemeTask: any WKURLSchemeTask) {
        let key = ObjectIdentifier(urlSchemeTask)
        lock.lock(); cancelled.remove(key); lock.unlock()

        queue.async { [weak self] in
            guard let self else { return }
            do {
                try self.respond(to: urlSchemeTask)
            } catch {
                self.finish(urlSchemeTask, with: error)
            }
        }
    }

    func webView(_ webView: WKWebView, stop urlSchemeTask: any WKURLSchemeTask) {
        let key = ObjectIdentifier(urlSchemeTask)
        lock.lock(); cancelled.insert(key); lock.unlock()
    }

    private func isCancelled(_ task: any WKURLSchemeTask) -> Bool {
        lock.lock(); defer { lock.unlock() }
        return cancelled.contains(ObjectIdentifier(task))
    }

    /// Calling back into a stopped task raises an ObjC exception that would
    /// tear the app down, so every delivery is gated on the cancel set.
    private func deliver(_ task: any WKURLSchemeTask, _ body: () -> Void) {
        guard !isCancelled(task) else { return }
        body()
    }

    private func finish(_ task: any WKURLSchemeTask, with error: Error) {
        deliver(task) { task.didFailWithError(error) }
    }

    private func respond(to task: any WKURLSchemeTask) throws {
        guard let url = task.request.url, let fileURL = resolve(url) else {
            return respondEmpty(task, status: 404, reason: "Not Found")
        }

        let attributes = try FileManager.default.attributesOfItem(atPath: fileURL.path)
        guard let sourceLength = (attributes[.size] as? NSNumber)?.uint64Value else {
            return respondEmpty(task, status: 404, reason: "Not Found")
        }

        // Open a per-file decoder up front. A protected build that cannot open a
        // file (missing header, unusable key) throws here and the request fails
        // loudly, rather than serving bytes the web view cannot use.
        let file = try decoder.open(fileURL, fileLength: sourceLength)
        defer { file.close() }
        // Content begins `contentStart` bytes into the file; the served length
        // and every range are in content space.
        let contentStart = file.contentStart
        let length = sourceLength - contentStart

        let contentType = Mime.of(fileURL.lastPathComponent)
        let mimeType = Mime.isTextual(contentType) ? "\(contentType); charset=utf-8" : contentType

        let rangeHeader = task.request.value(forHTTPHeaderField: "Range")
        let range = rangeHeader.flatMap { Range.parse($0, entityLength: length) }
        if rangeHeader != nil && range == nil {
            return respondEmpty(
                task,
                status: 416,
                reason: "Range Not Satisfiable",
                headers: ["Content-Range": "bytes */\(length)"]
            )
        }

        var headers = [
            "Content-Type": mimeType,
            "Accept-Ranges": "bytes",
            // The payload is immutable for the lifetime of an installed build.
            "Cache-Control": "public, max-age=31536000, immutable",
        ]
        let status: Int
        let start: UInt64
        let count: UInt64

        if let range {
            status = 206
            start = range.start
            count = range.length
            headers["Content-Range"] = "bytes \(range.start)-\(range.end)/\(length)"
        } else {
            status = 200
            start = 0
            count = length
        }
        headers["Content-Length"] = String(count)

        guard let response = HTTPURLResponse(
            url: url,
            statusCode: status,
            httpVersion: "HTTP/1.1",
            headerFields: headers
        ) else {
            return respondEmpty(task, status: 500, reason: "Internal Error")
        }
        deliver(task) { task.didReceive(response) }

        let handle = try FileHandle(forReadingFrom: fileURL)
        defer { try? handle.close() }
        // Read from the file at the header offset; decode in content space.
        try handle.seek(toOffset: contentStart + start)

        // Stream in chunks so a large asset never lands in memory whole.
        let chunkSize = 512 * 1024
        var remaining = count
        var offset = start
        while remaining > 0 {
            if isCancelled(task) { return }
            let want = Int(min(UInt64(chunkSize), remaining))
            guard let raw = try handle.read(upToCount: want), !raw.isEmpty else { break }
            let chunk = try file.decode(raw, at: offset)
            deliver(task) { task.didReceive(chunk) }
            remaining -= UInt64(raw.count)
            offset += UInt64(raw.count)
        }
        deliver(task) { task.didFinish() }
    }

    private func respondEmpty(
        _ task: any WKURLSchemeTask,
        status: Int,
        reason: String,
        headers: [String: String] = [:]
    ) {
        guard
            let url = task.request.url,
            let response = HTTPURLResponse(
                url: url,
                statusCode: status,
                httpVersion: "HTTP/1.1",
                headerFields: headers
            )
        else { return }
        deliver(task) {
            task.didReceive(response)
            task.didReceive(Data())
            task.didFinish()
        }
    }

    /// Map a URL onto a file under the payload root, or nil when it escapes the
    /// root or is malformed. "/" serves the entry document.
    private func resolve(_ url: URL) -> URL? {
        guard url.scheme == Self.scheme, url.host == Self.host else { return nil }

        let path = url.path.isEmpty ? "/" : url.path
        var relative = path.hasPrefix("/") ? String(path.dropFirst()) : path
        if relative.isEmpty || relative.hasSuffix("/") {
            relative += "index.html"
        }
        // Reject traversal before touching the file system.
        guard !relative.contains("..") && !relative.contains("\\") else { return nil }
        for segment in relative.split(separator: "/", omittingEmptySubsequences: false) {
            if segment.isEmpty || segment == "." || segment == ".." { return nil }
        }

        let candidate = rootDirectory.appendingPathComponent(relative).standardizedFileURL
        // Belt and braces: the resolved path must still sit under the root.
        guard candidate.path.hasPrefix(rootDirectory.standardizedFileURL.path) else { return nil }
        return FileManager.default.fileExists(atPath: candidate.path) ? candidate : nil
    }

    /// An inclusive byte range, already clamped to the entity length.
    struct Range {
        let start: UInt64
        let end: UInt64
        var length: UInt64 { end - start + 1 }

        /// Parse a single-range `bytes=` header. Multi-range requests are
        /// rejected: answering them needs a multipart body, and no media
        /// element the payload uses asks for one.
        static func parse(_ header: String, entityLength: UInt64) -> Range? {
            let spec = header
                .trimmingCharacters(in: .whitespaces)
                .replacingOccurrences(of: "bytes=", with: "")
                .trimmingCharacters(in: .whitespaces)
            guard !spec.isEmpty, !spec.contains(","), let dash = spec.firstIndex(of: "-") else { return nil }

            let rawStart = String(spec[spec.startIndex..<dash]).trimmingCharacters(in: .whitespaces)
            let rawEnd = String(spec[spec.index(after: dash)...]).trimmingCharacters(in: .whitespaces)

            if rawStart.isEmpty {
                // "-N": the final N bytes.
                guard let suffix = UInt64(rawEnd), suffix > 0, entityLength > 0 else { return nil }
                let start = entityLength > suffix ? entityLength - suffix : 0
                return Range(start: start, end: entityLength - 1)
            }
            guard let start = UInt64(rawStart), start < entityLength else { return nil }
            let end: UInt64
            if rawEnd.isEmpty {
                end = entityLength - 1
            } else {
                guard let parsed = UInt64(rawEnd) else { return nil }
                end = min(parsed, entityLength - 1)
            }
            guard end >= start else { return nil }
            return Range(start: start, end: end)
        }
    }
}
