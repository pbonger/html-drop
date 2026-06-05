#!/usr/bin/env node
/**
 * Generates platform-specific constants from settings.json.
 * Run via: npm run sync
 * Called automatically by worker:deploy and build.
 */

const fs   = require('fs');
const path = require('path');

const root     = path.resolve(__dirname, '..');
const settings = JSON.parse(fs.readFileSync(path.join(root, 'settings.json'), 'utf8'));
const localPath = path.join(root, 'settings.local.json');
const local    = fs.existsSync(localPath) ? JSON.parse(fs.readFileSync(localPath, 'utf8')) : {};
const merged   = { ...settings, ...local };

const { uploadUrl, maxUploadMb, uploadSecret } = merged;
const maxBytes = maxUploadMb * 1024 * 1024;

function write(relPath, content) {
  const abs = path.join(root, relPath);
  fs.mkdirSync(path.dirname(abs), { recursive: true });
  fs.writeFileSync(abs, content.trimStart());
  console.log('  wrote', relPath);
}

console.log('Syncing config from settings.json…');

// ── Kotlin (WebStorm plugin) ───────────────────────────────────────────────
write('src/main/kotlin/htmldrop/Constants.kt',
`// Generated from settings.json — do not edit manually
package htmldrop

const val UPLOAD_URL       = ${JSON.stringify(uploadUrl)}
const val MAX_UPLOAD_BYTES = ${maxBytes}L  // ${maxUploadMb} MB
const val UPLOAD_SECRET    = ${JSON.stringify(uploadSecret)}
`);

// ── Swift (macOS app) ──────────────────────────────────────────────────────
write('finder/Sources/GeneratedConstants.swift',
`// Generated from settings.json — do not edit manually
let uploadURL      = "${uploadUrl}"
let maxUploadBytes = ${maxBytes}  // ${maxUploadMb} MB
let uploadSecret   = "${uploadSecret}"
`);

console.log('Done.');
