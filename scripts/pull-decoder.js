#!/usr/bin/env node

/*
 * Fetch the prebuilt decoder libraries from the published @narraleaf/encryption
 * package and drop them where each shell's build expects them.
 *
 * They are build inputs, not source: nothing here is committed, and the shells
 * carry no decoding logic of their own — only the calls into these libraries.
 * Pinned by version so a template is always built against a known decoder; the
 * Studio side must ship a payload the same decoder can open.
 *
 * Usage: node scripts/pull-decoder.js [--version <v>]
 */

const { execFileSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const PACKAGE = '@narraleaf/encryption';
const DEFAULT_VERSION = '0.4.0';

const ANDROID_ABIS = ['arm64-v8a', 'armeabi-v7a', 'x86_64'];
const ANDROID_DEST = path.join(ROOT, 'android', 'app', 'src', 'main', 'jniLibs');
const IOS_DEST = path.join(ROOT, 'ios', 'Vendor');

function arg(name, fallback) {
    const i = process.argv.indexOf(name);
    return i > 0 && process.argv[i + 1] ? process.argv[i + 1] : fallback;
}

const version = arg('--version', DEFAULT_VERSION);
const work = fs.mkdtempSync(path.join(os.tmpdir(), 'nl-decoder-'));

console.log(`[decoder] fetching ${PACKAGE}@${version}`);
const tarball = execFileSync('npm', ['pack', `${PACKAGE}@${version}`, '--silent', '--pack-destination', work], {
    encoding: 'utf8',
}).trim();
execFileSync('tar', ['xzf', path.join(work, tarball), '-C', work], { stdio: 'inherit' });

const prebuilds = path.join(work, 'package', 'prebuilds');
if (!fs.existsSync(prebuilds)) {
    console.error(`[decoder] ${PACKAGE}@${version} has no prebuilds/ — wrong version?`);
    process.exit(1);
}

// Android: one .so per ABI, under the layout the Android Gradle plugin expects.
fs.rmSync(ANDROID_DEST, { recursive: true, force: true });
for (const abi of ANDROID_ABIS) {
    const src = path.join(prebuilds, 'android', abi, 'libnlcrypto.so');
    if (!fs.existsSync(src)) {
        console.error(`[decoder] missing ${abi} in the package — refusing a partial set`);
        process.exit(1);
    }
    fs.mkdirSync(path.join(ANDROID_DEST, abi), { recursive: true });
    fs.copyFileSync(src, path.join(ANDROID_DEST, abi, 'libnlcrypto.so'));
    console.log(`[decoder] android/${abi}`);
}

// iOS: the xcframework, linked by the Xcode project.
const xcframework = path.join(prebuilds, 'ios', 'NlCrypto.xcframework');
if (!fs.existsSync(xcframework)) {
    console.error('[decoder] the package has no NlCrypto.xcframework — wrong version?');
    process.exit(1);
}
fs.rmSync(IOS_DEST, { recursive: true, force: true });
fs.mkdirSync(IOS_DEST, { recursive: true });
fs.cpSync(xcframework, path.join(IOS_DEST, 'NlCrypto.xcframework'), { recursive: true });
console.log('[decoder] ios/NlCrypto.xcframework');

fs.rmSync(work, { recursive: true, force: true });
console.log(`[decoder] staged ${PACKAGE}@${version}`);
