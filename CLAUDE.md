# HTML Drop

Right-click any `.html` file → share it as a link. Pages live for 3 days, max 3 MB.

**What every Studio Bonkers project shares:** `../bonkers/docs/family/family.md`; the basics of an app: `../bonkers/docs/family/apps.md`. A lesson every project would hit goes there; one only this project hits goes here.

## Architecture

Three clients, one backend, one config:

```
settings.json           single source of truth (uploadUrl, maxUploadMb, maxPagesPerIp, pluginId)
settings.local.json     gitignored — holds uploadSecret only
scripts/sync-config.js  merges both, generates typed constants for the native clients
  → src/main/kotlin/htmldrop/Constants.kt
  → finder/Sources/GeneratedConstants.swift
worker/index.js         imports settings.json directly (and the version from package.json)
build.gradle.kts        reads pluginId from settings.json, writes it into plugin.xml at build
```

`package.json` holds the version; `npm run release` bumps it and copies it into `build.gradle.kts`, amending the version commit, then moves the tag onto the amended commit (`git tag -f`): without that the tag stayed on the commit before the amend, off master, `build.gradle.kts` a version behind (v1.0.2 to v1.0.9 were).

**Always edit `settings.json`, never the generated files. Run `npm run sync` to propagate.**

## Backend — Cloudflare Worker (`worker/`)

- `index.js` — all routes, HTML templates, crypto, icon serving
- `wrangler.toml` — KV binding, custom domain

Routes: `POST /api/upload`, `GET /{uuid}`, `GET /delete/{uuid}?token=`, `GET /terms`, `GET /updatePlugins.xml`, `GET /favicon.png`, `GET /icon.png`, `GET /apple-touch-icon.png`

### Authentication

Every upload request must include `X-Upload-Token: <secret>` matching the `UPLOAD_SECRET` Wrangler secret. Requests without it get a 401. The secret is:
- Stored in `settings.local.json` (gitignored)
- Pushed to Cloudflare automatically on `npm run worker:deploy`
- Injected into the browser app at request time from `env.UPLOAD_SECRET`
- Generated into native client constants via `npm run sync`

### Encryption

Every page is encrypted with AES-256-GCM (PBKDF2-HMAC-SHA256, 100k iterations). Decryption happens entirely in the browser via Web Crypto API.

- **With password** — user-supplied password; shared URL has no key. Visitor enters password on the lock page.
- **Without password** — random 32-byte key generated server-side; appended to the URL as `#key=<base64>`. Lock page auto-decrypts on load. Key is stored in `sessionStorage` so reloads work without re-entering anything. `history.replaceState` strips the key from the URL after first decrypt; sessionStorage takes over for subsequent loads.

### KV storage

`page:{uuid}` → `{html, deleteToken, isProtected, createdAt, ip}`, TTL 3 days.

`ip:{ip}:{uuid}` → `1`, TTL 3 days. Secondary index for fast per-IP lookups and FIFO eviction.

`block:{ip}` → strike counter or permanent block:
- `1` = blocked (admin or exhausted strikes)
- `3`, `2` = strikes remaining; bad uploads decrement the counter

### Upload validation

Before encrypting, uploads are checked for:
1. Valid `X-Upload-Token` header — 401 if missing or wrong
2. IP block check — 403 if blocked
3. Valid HTML content (`/<[a-zA-Z]/`)
4. Known obfuscation patterns (`eval(atob(`, `eval(unescape(`, etc.)
5. Large encoded blocks (256+ consecutive base64-alphabet chars, after stripping data URIs)

Violations (4 and 5) trigger a strike against the uploader's IP. After 3 violations the IP is blocked.

### Per-IP page limit

A maximum of `maxPagesPerIp` (default 10) pages are kept per IP. When the limit is reached the oldest page is automatically deleted (FIFO) before the new one is stored. Uses the `ip:` secondary index for O(1) lookup.

### IP blocking

IPv4 addresses are used as-is. IPv6 is normalized to its `/64` prefix (first 4 groups) — one ISP allocation = one block key. Only IPv4 and IPv6 `/64` prefixes are stored; the raw IPv6 address is never the block key.

The uploader's (normalized) IP is also stored in the page record for abuse lookups.

## WebStorm Plugin (`src/main/kotlin/htmldrop/`)

- `ShareOnHTMLDropAction.kt` — context menu action, size check, upload, notify
- `PasswordDialog.kt` — password prompt showing filename + size
- `Constants.kt` — generated (UPLOAD_URL, MAX_UPLOAD_BYTES, UPLOAD_SECRET)

Size check happens before the dialog — files over limit show an error dialog immediately.

### Updates — plugin repository

WebStorm 2026.3+ requires every third-party plugin to have an *update source* (Marketplace or a custom plugin repository). The `.pkg` postinstall unzips the plugin straight into WebStorm's plugins folder, so WebStorm has no source and shows "Cannot update plugin — Unable to identify where HTML Drop was installed from" on every project open. There is no "installed from disk" source.

The fix is our own plugin repository: `GET /updatePlugins.xml` lists `pluginId` (settings.json) at the `package.json` version, pointing at `html-drop-plugin-{version}.zip` on the GitHub release. `npm run release` attaches that zip and deploys the worker after the release exists, so the listed version always has a download. Users add the URL once (Settings → Plugins → ⚙ → Manage Plugin Repositories) and pick it on the notice; WebStorm then updates the plugin itself. Once the source is set, WebStorm ignores Marketplace for this ID, so the bare `html-drop` ID can't be hijacked by a same-named Marketplace plugin.

## macOS App (`finder/`)

- `Sources/HTMLDropCore.swift` — `htmlDropUpload()`, helpers
- `Sources/GeneratedConstants.swift` — generated (uploadURL, maxUploadBytes, uploadSecret)
- `Sources/main.swift` — CLI entry point (used by Finder Quick Action)
- `Extension/ShareViewController.swift` — Share Extension UI (NSAlert dialogs)
- `App/main.swift` — stub host app, registers extension then quits

## npm scripts

| Script | Does |
|---|---|
| `npm run sync` | regenerate platform constants from settings.json + settings.local.json |
| `npm run build` | sync + gradle buildPlugin |
| `npm run update:app` | build + install macOS app |
| `npm run worker:deploy` | push UPLOAD_SECRET + wrangler deploy |
| `npm run worker:dev` | local worker at localhost:8787 |
| `npm run worker:init` | first-time KV namespace setup |
| `npm run worker:pages` | list all live pages with time remaining |
| `npm run worker:purge` | delete all KV entries |
| `npm run worker:inspect` | look up a page record by UUID (shows ip, createdAt — no html) |
| `npm run worker:block` | block an IP and delete all their existing uploads |
| `npm run worker:unblock` | remove an IP block |
| `npm run release` | bump version, build all, push GitHub release |

## Scripts (`scripts/`)

- `sync-config.js` — merges settings.json + settings.local.json, generates platform constants
- `kv-ip.js` — powers `worker:block` and `worker:unblock`; normalizes IPv6 to /64 prefix; on block uses the `ip:` index to find and delete all uploads from that IP
- `kv-inspect.js` — powers `worker:inspect`; fetches a page record and prints metadata without the html field

## Domain

`html-drop.studio-bonkers.nl` on Cloudflare. studio-bonkers.nl DNS managed by Cloudflare (nameservers pointed from GoDaddy). Email routing: `html-drop@studio-bonkers.nl` → Gmail via Cloudflare Email Routing.

## Deployment

```bash
npm install           # installs wrangler v4
npm run worker:init   # first time only: creates KV namespace, patches wrangler.toml
npm run worker:deploy # pushes UPLOAD_SECRET secret + deploys worker
```

Required Cloudflare resources: one Workers KV namespace (`PAGES`), one Wrangler secret (`UPLOAD_SECRET`).
