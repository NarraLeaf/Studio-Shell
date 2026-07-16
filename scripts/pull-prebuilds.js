"use strict";
// Pull the CI-built shell templates into npm/ so the package can be published
// from a machine with no Android or Apple toolchain. Downloads the artifacts of
// the latest successful "build-shells" run through the gh CLI (which handles
// auth and unzipping). The repo is inferred from the current git remote.
//
// Options:
//   --run=<id>   pull a specific run instead of the latest successful one
const { spawnSync } = require("child_process");
const fs = require("fs");
const os = require("os");
const path = require("path");

const root = path.join(__dirname, "..");
const WORKFLOW = "build-shells.yml";

// One artifact per platform, each holding the files listed.
const TARGETS = [
    { artifact: "shell-android", dir: "android", files: ["template.apk", "template-debug.apk"] },
    { artifact: "shell-ios", dir: "ios", files: ["template.app.zip", "template-debug.app.zip"] },
];

function gh(args) {
    const res = spawnSync("gh", args, { encoding: "utf-8" });
    if (res.error && res.error.code === "ENOENT") {
        fail(
            "the GitHub CLI (gh) is required but was not found.\n" +
            "  Install it and sign in, then re-run:\n" +
            "    brew install gh      # macOS\n" +
            "    gh auth login",
        );
    }
    if (res.status !== 0) {
        fail(`gh ${args.join(" ")} failed:\n${(res.stderr || res.stdout || "").trim()}`);
    }
    return (res.stdout || "").trim();
}

function fail(message) {
    console.error(`[pull] ${message}`);
    process.exit(1);
}

function resolveRunId() {
    const flag = process.argv.find(a => a.startsWith("--run="));
    if (flag) {
        return flag.slice("--run=".length);
    }
    const id = gh([
        "run", "list",
        "--workflow", WORKFLOW,
        "--status", "success",
        "--limit", "1",
        "--json", "databaseId",
        "--jq", ".[0].databaseId",
    ]);
    if (!id) {
        fail(`no successful "${WORKFLOW}" run found — trigger the workflow first (push, or run it from the Actions tab).`);
    }
    return id;
}

const runId = resolveRunId();
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "shell-prebuilds-"));
console.log(`[pull] downloading artifacts from run ${runId}`);
gh(["run", "download", runId, "--dir", tmp]);

let staged = 0;
const missing = [];
for (const target of TARGETS) {
    const destDir = path.join(root, "npm", target.dir);
    fs.mkdirSync(destDir, { recursive: true });
    for (const file of target.files) {
        const src = path.join(tmp, target.artifact, file);
        if (!fs.existsSync(src)) {
            missing.push(`${target.artifact}/${file}`);
            continue;
        }
        fs.copyFileSync(src, path.join(destDir, file));
        console.log(`[pull] staged ${target.dir}/${file}`);
        staged++;
    }
}
fs.rmSync(tmp, { recursive: true, force: true });

if (staged === 0) {
    fail("no templates were staged from that run — aborting.");
}
if (missing.length) {
    fail(
        `missing artifacts: ${missing.join(", ")}\n` +
        "  Publishing now would ship a package that cannot build one of the platforms.",
    );
}
console.log(`[pull] all ${staged} template files staged. Next: node scripts/make-manifest.js`);
