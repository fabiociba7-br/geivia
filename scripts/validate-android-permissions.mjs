import fs from "node:fs";

const manifestPath = "app/src/main/AndroidManifest.xml";
const auditPath = "docs/GEIVIA_ANDROID_PERMISSIONS_AUDIT.md";
const manifest = fs.readFileSync(manifestPath, "utf8");
const audit = fs.readFileSync(auditPath, "utf8");

const declared = [...manifest.matchAll(/<uses-permission(?:-sdk-\d+)?\s+android:name="([^"]+)"/g)]
  .map((match) => match[1]);

const forbidden = new Set([
  "android.permission.MANAGE_EXTERNAL_STORAGE",
  "android.permission.READ_EXTERNAL_STORAGE",
  "android.permission.WRITE_EXTERNAL_STORAGE",
  "android.permission.QUERY_ALL_PACKAGES",
  "android.permission.REQUEST_INSTALL_PACKAGES",
  "android.permission.SYSTEM_ALERT_WINDOW",
  "android.permission.BIND_ACCESSIBILITY_SERVICE",
  "android.permission.RECEIVE_BOOT_COMPLETED"
]);

const violations = [];
for (const permission of declared) {
  if (forbidden.has(permission)) {
    violations.push(`Permissão ampla/restrita não autorizada: ${permission}`);
  }
  if (!audit.includes(permission)) {
    violations.push(`Permissão declarada sem justificativa na auditoria: ${permission}`);
  }
}

if (!declared.includes("android.permission.INTERNET")) {
  violations.push("A permissão INTERNET deve permanecer declarada para os recursos de rede.");
}

if (violations.length) {
  console.error("Falha na auditoria automática de permissões Android:");
  for (const violation of violations) console.error(`- ${violation}`);
  process.exit(1);
}

console.log(`Auditoria Android OK. Permissões declaradas: ${declared.join(", ")}`);
