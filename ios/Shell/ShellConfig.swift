import UIKit

/// The per-game settings the repacker injects as `shell-config.json` beside the
/// payload.
///
/// Decoded with the platform's own JSON reader — the shell carries no
/// third-party libraries. Every field falls back to a default, so an older
/// template paired with a newer writer (or the reverse) degrades instead of
/// crashing.
struct ShellConfig {

    enum Orientation: String {
        case landscape
        case portrait
        case auto

        var mask: UIInterfaceOrientationMask {
            switch self {
            case .landscape: return .landscape
            case .portrait: return [.portrait, .portraitUpsideDown]
            case .auto: return .all
            }
        }

        static func parse(_ value: String?) -> Orientation {
            guard let value, let parsed = Orientation(rawValue: value.lowercased()) else {
                return .landscape
            }
            return parsed
        }
    }

    let schemaVersion: Int
    let orientation: Orientation
    let backgroundColor: UIColor
    /// Opaque token the payload decoder needs, injected only when the packer
    /// encoded this build's payload. Nil means the payload is plain and is
    /// served as-is — one shell runs both kinds of game.
    let contentKey: String?

    static let path = "shell-config.json"

    /// Matches the pre-boot background the web export paints.
    static let defaultConfig = ShellConfig(
        schemaVersion: 1,
        orientation: .landscape,
        backgroundColor: .black,
        contentKey: nil
    )

    /// Read the injected config, falling back to `defaultConfig` when it is
    /// absent or unreadable — a template running without a repack (the CI smoke
    /// test) still has to boot.
    static func load(bundle: Bundle = .main) -> ShellConfig {
        guard
            let url = bundle.url(forResource: "shell-config", withExtension: "json"),
            let data = try? Data(contentsOf: url),
            let object = try? JSONSerialization.jsonObject(with: data),
            let json = object as? [String: Any]
        else {
            return defaultConfig
        }
        let contentKey = (json["contentKey"] as? String).flatMap { $0.isEmpty ? nil : $0 }
        return ShellConfig(
            schemaVersion: json["schemaVersion"] as? Int ?? defaultConfig.schemaVersion,
            orientation: Orientation.parse(json["orientation"] as? String),
            backgroundColor: parseColor(json["backgroundColor"] as? String) ?? defaultConfig.backgroundColor,
            contentKey: contentKey
        )
    }

    /// #rgb / #rrggbb / #aarrggbb, or nil when unparseable.
    private static func parseColor(_ value: String?) -> UIColor? {
        guard var hex = value?.trimmingCharacters(in: .whitespacesAndNewlines) else { return nil }
        if hex.hasPrefix("#") { hex.removeFirst() }
        if hex.count == 3 { hex = hex.map { "\($0)\($0)" }.joined() }
        guard hex.count == 6 || hex.count == 8, let parsed = UInt32(hex, radix: 16) else { return nil }

        let hasAlpha = hex.count == 8
        let a = hasAlpha ? CGFloat((parsed >> 24) & 0xff) / 255 : 1
        let r = CGFloat((parsed >> 16) & 0xff) / 255
        let g = CGFloat((parsed >> 8) & 0xff) / 255
        let b = CGFloat(parsed & 0xff) / 255
        return UIColor(red: r, green: g, blue: b, alpha: a)
    }
}
