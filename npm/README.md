# @narraleaf/studio-shell

Prebuilt, **unsigned** WebView shell templates for
[NarraLeaf Studio](https://github.com/NarraLeaf/NarraLeaf-Studio).

This package is data, not code: it ships two Android APK templates and two iOS
`.app` archives, plus a `manifest.json` describing where a game's payload and
identity go. Studio's repacker reads the manifest and rewrites the templates
entirely in TypeScript — building a mobile game needs no Android SDK, Gradle,
Xcode or JDK on the author's machine.

| File | What it is |
| --- | --- |
| `android/template.apk` | Release shell, unsigned, placeholder identity |
| `android/template-debug.apk` | Same, debuggable (Studio uses it only in development) |
| `ios/template.app.zip` | Release shell, unsigned |
| `ios/template-debug.app.zip` | Same, debuggable |
| `manifest.json` | The versioned contract: placeholders, icon slots, injection roots |

`manifest.json`'s `schemaVersion` is checked by Studio before every repack; a
mismatch reports that Studio and the template are incompatible rather than
producing a broken package.

Built and published from
[NarraLeaf/Studio-Shell](https://github.com/NarraLeaf/Studio-Shell).
