import fs from "node:fs";
import assert from "node:assert/strict";

const path = "app/src/main/assets/linux-toolchain-manifest.json";
const manifest = JSON.parse(fs.readFileSync(path, "utf8"));

assert.equal(manifest.schemaVersion, 1);
assert.equal(manifest.architecture, "arm64");
assert.equal(manifest.installMode, "on-demand-in-app");
const profiles = new Map(manifest.profiles.map((profile) => [profile.id, profile]));
for (const required of ["base", "java", "node", "python", "build", "firebase"]) {
  assert.ok(profiles.has(required), `Missing required profile: ${required}`);
}
assert.ok(profiles.get("java").apt.includes("default-jdk-headless"));
assert.ok(profiles.get("base").apt.includes("git"));

const firebase = profiles.get("firebase");
assert.equal(firebase.bundle.provider, "npm-registry");
assert.equal(firebase.bundle.package, "firebase-tools");
assert.equal(firebase.bundle.version, "15.36.0");
assert.equal(firebase.bundle.architecture, "arm64");
assert.ok(firebase.requires.includes("node"));
assert.ok(manifest.defaultProfiles.includes("firebase"));
assert.ok(manifest.optionalProfiles.includes("gcloud"));
assert.equal(
  profiles.get("gcloud").aptExternal[0].requiresSignedRepositoryKey,
  true
);
assert.ok(
  manifest.notes.some((note) => note.includes("public npm registry")),
  "Firebase CLI must be documented as installed from the public npm registry"
);
console.log("Linux toolchain manifest: OK");
