# HTML Drop

Right-click any `.html` file → share it as a link. Pages live for 3 days, max 3 MB.

## Architecture

Three clients, one backend, one config:

```
settings.json           single source of truth (uploadUrl, maxUploadMb)
scripts/sync-config.js  generates typed constants for each platform
  → worker/config.js
  → src/main/kotlin/htmldrop/Constants.kt
  → finder/Sources/GeneratedConstants.swift
```

**Always edit `settings.json`, never the generated files. Run `npm run sync` to propagate.**

## Backend — Cloudflare Worker (`worker/`)

- `index.js` — all routes, HTML templates, crypto, icon serving
- `config.js` — generated constants (MAX_UPLOAD_MB)
- `wrangler.toml` — KV binding, custom domain

Routes: `POST /api/upload`, `GET /{uuid}`, `GET /delete/{uuid}?token=`, `GET /terms`, `GET /favicon.png`, `GET /icon.png`, `GET /apple-touch-icon.png`

Encryption: PBKDF2-HMAC-SHA256 (100k iterations) + AES-256-GCM, server-side. Client sends raw HTML + password; server encrypts and generates the lock page. Browser decrypts via Web Crypto API.

KV storage: `page:{uuid}` → `{html, deleteToken, isProtected, createdAt}`, TTL 3 days.

## WebStorm Plugin (`src/main/kotlin/htmldrop/`)

- `ShareOnHTMLDropAction.kt` — context menu action, size check, upload, notify
- `PasswordDialog.kt` — password prompt showing filename + size
- `Constants.kt` — generated (UPLOAD_URL, MAX_UPLOAD_BYTES)

Size check happens before the dialog — files over limit show an error dialog immediately.

## macOS App (`finder/`)

- `Sources/HTMLDropCore.swift` — `htmlDropUpload()`, helpers
- `Sources/GeneratedConstants.swift` — generated (uploadURL, maxUploadBytes)
- `Sources/main.swift` — CLI entry point (used by Finder Quick Action)
- `Extension/ShareViewController.swift` — Share Extension UI (NSAlert dialogs)
- `App/main.swift` — stub host app, registers extension then quits

## npm scripts

| Script | Does |
|---|---|
| `npm run sync` | regenerate platform constants from settings.json |
| `npm run build` | sync + gradle buildPlugin |
| `npm run update:app` | build + install macOS app |
| `npm run worker:deploy` | sync + wrangler deploy |
| `npm run worker:dev` | local worker at localhost:8787 |
| `npm run worker:init` | first-time KV namespace setup |
| `npm run release` | bump version, build all, push GitHub release |

## Domain

`html-drop.studio-bonkers.nl` on Cloudflare. studio-bonkers.nl DNS managed by Cloudflare (nameservers pointed from GoDaddy). Email routing: `html-drop@studio-bonkers.nl` → Gmail via Cloudflare Email Routing.

## Deployment

```bash
npm install           # installs wrangler v4
npm run worker:init   # first time only: creates KV namespace, patches wrangler.toml
npm run worker:deploy
```

Required Cloudflare resources: one Workers KV namespace (`PAGES`). No env vars needed.
