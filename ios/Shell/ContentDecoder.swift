import Foundation

/// Transform hook applied to payload bytes on their way out of `WwwSchemeHandler`.
///
/// The contract is deliberately generic: bytes in, bytes out. An implementation
/// sees an opaque chunk and returns an opaque chunk; it is told nothing about
/// the request, and it must not need to be.
///
/// `IdentityContentDecoder` is the default and returns its input unchanged.
protocol ContentDecoder {

    /// Transform `data`, which starts at `offset` bytes into the whole content.
    ///
    /// Must be safe to call from multiple threads: the web view issues
    /// requests concurrently.
    func decode(_ data: Data, at offset: UInt64) throws -> Data

    /// The number of bytes `decode` yields for a whole input of `sourceLength`.
    /// The handler answers range requests against this, so an implementation
    /// that changes the length must report it here.
    func decodedLength(_ sourceLength: UInt64) -> UInt64
}

/// Passes bytes through untouched.
struct IdentityContentDecoder: ContentDecoder {
    func decode(_ data: Data, at offset: UInt64) throws -> Data { data }
    func decodedLength(_ sourceLength: UInt64) -> UInt64 { sourceLength }
}
