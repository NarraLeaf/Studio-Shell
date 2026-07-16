# NarraLeaf Studio-Shell

The native WebView shells that [NarraLeaf Studio](https://github.com/NarraLeaf/NarraLeaf-Studio)
repacks into Android and iOS builds of a game.

A NarraLeaf game has no native code: its renderer is the same bundle on desktop,
web and mobile, talking only to a small JavaScript bridge. So a mobile build
does not need compiling — it needs a shell that can *serve* the game's web
export to a WebView, and that shell is identical for every game apart from a
name, an icon, a version and the payload itself.

That is what lives here. CI builds each shell once into an **unsigned template**;
the templates are published as [`@narraleaf/studio-shell`](https://www.npmjs.com/package/@narraleaf/studio-shell);
and Studio rewrites a template into a finished app entirely in TypeScript. An
author never installs the Android SDK, Gradle, Xcode, CocoaPods or a JDK, and
never goes online to build.

## Layout

```
android/          Kotlin shell — single Activity + WebView
ios/              Swift shell — single view controller + WKWebView
docs/CONTRACT.md  The rules the templates must hold to, and why
test-payload/     A self-check payload that proves a shell can run a game
scripts/          Release plumbing (pull CI artifacts, generate the manifest)
npm/              The published package's contents
```

## What the shell actually does

1. Serves the payload bundled inside it to the WebView — with HTTP Range
   (without which no audio or video can seek), streaming reads, correct content
   types, and a secure context so the game's saves work.
2. Reads `shell-config.json` for the game's orientation and pre-boot colour.
3. Goes fullscreen and unlocks media playback, so the opening track sounds and
   video stays in the page.

Everything else — the game itself — is the payload.

**Zero third-party dependencies, deliberately.** The Android shell links no
libraries at all and the iOS shell only system frameworks. Beyond size and
supply chain, this is load-bearing: a library that contributes a `<provider>`
to the merged manifest would make two games repacked from one template collide
at install. See [docs/CONTRACT.md](docs/CONTRACT.md).

## Building

CI (`.github/workflows/build-shells.yml`) is the source of truth and runs on
every push: an Ubuntu runner builds the Android templates, a macOS runner the
iOS ones, and both assert the contract before uploading.

The **Android SDK exists only on the CI runner**. It is licensed such that its
components must not be redistributed, so no artifact and no npm package here
may ever contain them. Building Android locally means installing the SDK
yourself, under Google's terms; nothing in this repo does it for you.

Locally, if you want to:

```sh
# Android — needs JDK 17, Gradle 8.7 and your own Android SDK
cd android && gradle assembleRelease

# iOS — needs Xcode (Command Line Tools alone are not enough)
xcodebuild -project ios/Shell.xcodeproj -target Shell \
  -configuration Release -sdk iphoneos CODE_SIGNING_ALLOWED=NO build
```

## Releasing

```sh
cd npm && npm publish --access public
```

That is the whole thing. `npm/`'s `prepublishOnly` fetches the latest green
build's artifacts (via the `gh` CLI) and regenerates `manifest.json` from them
before the tarball is packed, so a publish cannot ship stale, partial or
hand-edited templates — and it fails closed if the artifacts are not there. The
templates are data; nothing is compiled at publish time.

`make-manifest.js` derives `manifest.json` from the built artifacts rather than
from constants, because the Android build renames resource directories — a
hand-written path would point at an entry that does not exist.

The staged files (`npm/android`, `npm/ios`, `npm/manifest.json`) are generated,
and git-ignored for that reason. To stage them without publishing —
to inspect a template, or to run Studio against one:

```sh
npm run stage    # pull + manifest, no publish
```

Studio pins this package exactly and consumes it as a development dependency;
the templates reach a released Studio through its packaged resources.

## License

[MPL-2.0](LICENSE), the same as Studio.
