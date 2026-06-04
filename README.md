# HTML Drop

Right-click any `.html` file → **Share on HTML Drop**.  
Uploads to [html-drop.studio-bonkers.nl](https://html-drop.studio-bonkers.nl), copies the URL to clipboard, and shows a notification. Optionally password-protects the page with server-side AES-256-GCM encryption. Pages expire after 3 days.

Available as a **WebStorm plugin** and a **macOS Finder Share Extension**.

---

## What's in the repo

| Path | What it is |
|---|---|
| `worker/` | Cloudflare Worker — the backend (upload, serve, delete, terms) |
| `src/main/kotlin/htmldrop/` | WebStorm plugin (Kotlin) |
| `finder/Sources/` | Swift CLI used by the Finder Quick Action |
| `finder/Extension/` | macOS Share Extension (Finder Share button) |
| `finder/App/` | Minimal host app carrying the Share Extension |
| `finder/HTMLDrop.workflow/` | Automator Quick Action → `~/Library/Services/` |
| `finder/build-app.sh` | Compiles + bundles + signs `HTMLDrop.app` |
| `scripts/sync-config.js` | Generates platform constants from `settings.json` |
| `scripts/package-dmg.sh` | Builds `.pkg`, assembles DMG |
| `settings.json` | Shared config — upload URL and size cap |

---

## Shared config (`settings.json`)

Single source of truth for all clients and the worker:

```json
{
  "uploadUrl": "https://html-drop.studio-bonkers.nl/api/upload",
  "maxUploadMb": 3
}
```

Running `npm run sync` generates typed constants for each platform:
- `worker/config.js` (JS)
- `src/main/kotlin/htmldrop/Constants.kt` (Kotlin)
- `finder/Sources/GeneratedConstants.swift` (Swift)

---

## npm scripts

```bash
npm run sync           # regenerate platform constants from settings.json
npm run build          # sync + compile WebStorm plugin → build/distributions/
npm run build:app      # compile + bundle + sign HTMLDrop.app
npm run update:app     # build:app, install to /Applications, re-register extension
npm run icon           # regenerate all icons from source
npm run package:dmg    # build .pkg and assemble final DMG
npm run release        # bump version, build everything, push to GitHub Releases

npm run worker:dev     # run worker locally at localhost:8787
npm run worker:deploy  # sync + deploy worker to Cloudflare
npm run worker:logs    # tail live worker logs
npm run worker:init    # create KV namespaces (first-time setup)
```

---

## Worker (Cloudflare)

Deployed at `html-drop.studio-bonkers.nl`. Handles all upload, serve, and delete logic.

- **POST `/api/upload`** — accepts `{html, password?, ttl?}`, max 3 MB
- **GET `/{uuid}`** — serves the page; injects delete/abuse/terms footer
- **GET `/delete/{uuid}?token=`** — permanent delete with single confirm
- **GET `/terms`** — terms of use
- **GET `/favicon.png`**, `/icon.png`**, `/apple-touch-icon.png`** — icons

Password-protected pages are encrypted server-side (PBKDF2-HMAC-SHA256 + AES-256-GCM). The server never stores the password; decryption happens in the browser.

### First-time deploy

```bash
npm install
npm run worker:init   # creates KV namespace, patches wrangler.toml
npm run worker:deploy
```

---

## WebStorm Plugin

Right-click `.html` → **Share on HTML Drop** → optional password → uploads, copies URL, notification with **Open in Browser**.

File size is validated before the dialog opens — files over 3 MB are rejected immediately.

### Build and install

```bash
npm run build
# WebStorm → Settings → Plugins → ⚙️ → Install Plugin from Disk → build/distributions/*.zip
```

---

## macOS Share Extension

Right-click `.html` in Finder → **Share** → **HTML Drop**.

### Build and install

```bash
npm run update:app
```

### Entitlements (sandboxed)
- `com.apple.security.network.client` — upload to worker
- `com.apple.security.files.user-selected.read-only` — read the `.html` file

---

## Release

```bash
npm run release
```

Bumps patch version, builds plugin and app, creates a GitHub release with the DMG attached.
