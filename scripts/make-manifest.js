"use strict";
// Generate npm/manifest.json — the versioned contract Studio's repacker reads.
//
// Icon slots are enumerated from the BUILT templates, never from source-tree
// constants: the Android build rewrites density directories (res/mipmap-mdpi/
// becomes res/mipmap-mdpi-v4/), so a hand-written path would name an entry the
// APK does not contain, and Studio rejects an icon slot it cannot find.
//
// Run after scripts/pull-prebuilds.js has staged npm/android and npm/ios.
const { spawnSync } = require("child_process");
const fs = require("fs");
const path = require("path");

const root = path.join(__dirname, "..");
const npmDir = path.join(root, "npm");

/** The schema version Studio validates before repacking. Bump on any break. */
const SCHEMA_VERSION = 1;
const SHELL_CONFIG_SCHEMA_VERSION = 1;

function fail(message) {
    console.error(`[manifest] ${message}`);
    process.exit(1);
}

/** List an archive's entries. Uses unzip, which every dev machine has. */
function listEntries(archive) {
    const res = spawnSync("unzip", ["-Z1", archive], { encoding: "utf-8" });
    if (res.error && res.error.code === "ENOENT") {
        fail("the `unzip` command is required but was not found.");
    }
    if (res.status !== 0) {
        fail(`unzip -Z1 ${archive} failed:\n${(res.stderr || "").trim()}`);
    }
    return res.stdout.split("\n").map(line => line.trim()).filter(Boolean);
}

function requireFile(relative) {
    const file = path.join(npmDir, relative);
    if (!fs.existsSync(file)) {
        fail(`${relative} is missing — run scripts/pull-prebuilds.js first.`);
    }
    return file;
}

const androidApk = requireFile("android/template.apk");
requireFile("android/template-debug.apk");
const iosZip = requireFile("ios/template.app.zip");
requireFile("ios/template-debug.app.zip");

// ---- Android -------------------------------------------------------------

const apkEntries = listEntries(androidApk);

const androidIconSlots = apkEntries
    .filter(entry => /^res\/mipmap-[a-z]+(-v\d+)?\/ic_launcher\.png$/.test(entry))
    .sort();
if (androidIconSlots.length === 0) {
    fail("no launcher-icon entries found in the Android template.");
}

for (const required of ["AndroidManifest.xml", "resources.arsc"]) {
    if (!apkEntries.includes(required)) {
        fail(`the Android template has no ${required}.`);
    }
}

// ---- iOS -----------------------------------------------------------------

const iosEntries = listEntries(iosZip);

// `ditto --keepParent` prefixes every entry with the .app directory.
const appDirName = (() => {
    const first = iosEntries.find(entry => entry.includes(".app/"));
    if (!first) {
        fail("the iOS template does not look like a `ditto --keepParent` archive of a .app.");
    }
    return first.slice(0, first.indexOf(".app/") + ".app".length);
})();

const intraApp = iosEntries
    .filter(entry => entry.startsWith(`${appDirName}/`))
    .map(entry => entry.slice(appDirName.length + 1))
    .filter(Boolean);

const iosIconSlots = intraApp.filter(entry => /^AppIcon[\w@~.-]*\.png$/.test(entry)).sort();
if (iosIconSlots.length === 0) {
    fail("no app-icon entries found in the iOS template.");
}
if (!intraApp.includes("Info.plist")) {
    fail("the iOS template has no Info.plist at the .app root.");
}

const executableName = "Shell";
if (!intraApp.includes(executableName)) {
    fail(`the iOS template has no executable named "${executableName}".`);
}

// ---- Emit ----------------------------------------------------------------

const manifest = {
    schemaVersion: SCHEMA_VERSION,
    android: {
        template: "android/template.apk",
        templateDebug: "android/template-debug.apk",
        minSdk: 26,
        placeholders: {
            applicationId: "com.narraleaf.shell.placeholder",
            label: "NarraLeaf Shell",
            versionCode: 1,
            versionName: "0.0.0",
        },
        iconSlots: androidIconSlots,
        wwwRoot: "assets/www/",
        shellConfigPath: "assets/shell-config.json",
    },
    ios: {
        template: "ios/template.app.zip",
        templateDebug: "ios/template-debug.app.zip",
        appDirName,
        executableName,
        placeholders: {
            bundleId: "com.narraleaf.shell.placeholder",
        },
        iconSlots: iosIconSlots,
        wwwRoot: "www/",
        shellConfigPath: "shell-config.json",
    },
    shellConfigSchemaVersion: SHELL_CONFIG_SCHEMA_VERSION,
};

const out = path.join(npmDir, "manifest.json");
fs.writeFileSync(out, `${JSON.stringify(manifest, null, 4)}\n`);
console.log(`[manifest] wrote ${path.relative(root, out)}`);
console.log(`[manifest]   android iconSlots: ${androidIconSlots.length}`);
for (const slot of androidIconSlots) {
    console.log(`[manifest]     ${slot}`);
}
console.log(`[manifest]   ios appDirName: ${appDirName}, iconSlots: ${iosIconSlots.length}`);
for (const slot of iosIconSlots) {
    console.log(`[manifest]     ${slot}`);
}
console.log("[manifest] Next: cd npm && npm publish --access public");
