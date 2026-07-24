import Foundation
import NlCrypto

/// `ContentDecoder` backed by the prebuilt decoder library linked into the
/// shell binary.
///
/// The library is a build input, not source: it arrives as the prebuilt static
/// archive `NlCrypto.xcframework` and exports the `nlc_*` entry points its
/// header declares. This type is deliberately the whole of the shell's
/// knowledge on the subject — a file begins with a header of `nlc_header_len`
/// bytes, and content bytes are passed through `nlc_decode` at their offset.
/// What that does is not the shell's business.
///
/// The `nlc_*` names below are an ABI contract with the library; the header
/// ships inside the xcframework and the module map exposes it as `NlCrypto`.
final class NativeContentDecoder: ContentDecoder {

    private let key: String

    /// The fixed number of leading bytes the library asks to see, which is also
    /// the prefix it strips. `open` asserts the two agree per file rather than
    /// trusting that they always will.
    private static let probeSize: Int = nlc_probe_size()

    init(key: String) {
        self.key = key
    }

    func open(_ fileURL: URL, fileLength: UInt64) throws -> FileContentDecoder {
        let handle = try FileHandle(forReadingFrom: fileURL)
        defer { try? handle.close() }
        let head = (try handle.read(upToCount: Self.probeSize)) ?? Data()

        let headerLen: Int
        if head.count < Self.probeSize {
            headerLen = 0
        } else {
            headerLen = head.withUnsafeBytes { raw -> Int in
                guard let base = raw.baseAddress else { return 0 }
                return Int(nlc_header_len(base.assumingMemoryBound(to: UInt8.self), head.count))
            }
        }

        if headerLen == 0 {
            // A build with a key expects every payload file to carry a header.
            // Serving the bytes as-is would hand the web view content it cannot
            // use and fail far from here (a blank game, a broken asset); the
            // packer and the shell disagree, so say so.
            throw DecoderError.notEncoded
        }
        if headerLen != Self.probeSize {
            // `contentStart` leans on the prefix equalling the probe size. If
            // the library ever reports a different prefix than it probes, every
            // Content-Length would be silently wrong — refuse instead.
            throw DecoderError.unexpectedPrefix(headerLen)
        }

        let ctx: OpaquePointer? = head.withUnsafeBytes { raw in
            guard let base = raw.baseAddress else { return nil }
            return nlc_open(base.assumingMemoryBound(to: UInt8.self), head.count, key)
        }
        guard let ctx else {
            // A present but unusable key opens nothing. Refusing here keeps a
            // wrong build from serving content it cannot decode.
            throw DecoderError.keyRejected
        }
        return NativeFileContentDecoder(ctx: ctx, contentStart: UInt64(headerLen))
    }

    enum DecoderError: Error {
        case notEncoded
        case unexpectedPrefix(Int)
        case keyRejected
        case decodeFailed
        case closed
    }

    /// The decoded view of one file. Holds a native decoder for its lifetime and
    /// frees it on `close`. `nlc_decode` is safe to call concurrently on one
    /// decoder, so `decode` needs no lock of its own.
    private final class NativeFileContentDecoder: FileContentDecoder {

        let contentStart: UInt64
        private var ctx: OpaquePointer?
        private let lock = NSLock()

        init(ctx: OpaquePointer, contentStart: UInt64) {
            self.ctx = ctx
            self.contentStart = contentStart
        }

        func decode(_ data: Data, at contentOffset: UInt64) throws -> Data {
            guard let ctx else { throw DecoderError.closed }
            if data.isEmpty { return data }
            var out = data
            let ok = out.withUnsafeMutableBytes { raw -> Bool in
                guard let base = raw.baseAddress else { return false }
                return nlc_decode(ctx, contentOffset, base.assumingMemoryBound(to: UInt8.self), raw.count) == 1
            }
            guard ok else { throw DecoderError.decodeFailed }
            return out
        }

        func close() {
            lock.lock(); defer { lock.unlock() }
            if let ctx {
                nlc_free(ctx)
                self.ctx = nil
            }
        }
    }
}
