import Foundation

/// Maps a payload file's bytes to the content bytes `WwwSchemeHandler` serves.
///
/// The contract is deliberately generic: given a file, hand back something that
/// turns the file's bytes into content bytes. A file may begin with a header
/// that is not content; the returned `FileContentDecoder` reports where content
/// begins and transforms content ranges at their offset. The opener is told
/// nothing about the request, and it must not need to be.
///
/// `IdentityContentDecoder` is the default and serves files unchanged.
protocol ContentDecoder {

    /// Open a decoder for the file at `fileURL`, whose size on disk is
    /// `fileLength`. Throws when the file was expected to carry decodable
    /// content but cannot be opened, so a mismatch surfaces as a failed request
    /// rather than as unreadable bytes reaching the web view.
    ///
    /// Must be safe to call from multiple threads: the web view issues requests
    /// concurrently.
    func open(_ fileURL: URL, fileLength: UInt64) throws -> FileContentDecoder
}

/// The decoded view of one file. Created per request and released when the
/// response is done.
protocol FileContentDecoder {

    /// Byte offset in the file where content begins; content byte N lives at
    /// file byte `contentStart + N`. Zero when the whole file is content.
    var contentStart: UInt64 { get }

    /// Transform `data`, which holds the content range beginning `contentOffset`
    /// bytes into the content, and return the result. Any range can be decoded
    /// on its own, without reading what precedes it.
    ///
    /// Must be safe to call from multiple threads.
    func decode(_ data: Data, at contentOffset: UInt64) throws -> Data

    /// Release any resources held for this file.
    func close()
}

/// Serves files unchanged: content begins at byte zero and bytes pass through.
struct IdentityContentDecoder: ContentDecoder {
    func open(_ fileURL: URL, fileLength: UInt64) throws -> FileContentDecoder {
        IdentityFileContentDecoder()
    }
}

/// The identity `FileContentDecoder`: no header, no transform.
struct IdentityFileContentDecoder: FileContentDecoder {
    var contentStart: UInt64 { 0 }
    func decode(_ data: Data, at contentOffset: UInt64) throws -> Data { data }
    func close() {}
}
