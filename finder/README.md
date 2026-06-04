# HTML Drop — Finder Quick Action

Right-click any `.html` file in Finder → **Share** → **HTML Drop**.  
Uploads to [html-drop.studio-bonkers.nl](https://html-drop.studio-bonkers.nl), copies the URL to clipboard, shows a macOS notification.

## Install

```bash
npm run update:app
```

Or install from the release DMG — double-click `HTMLDrop.pkg` and follow the installer.

## Build from source

```bash
git clone <this repo>
cd html-drop-plugin
npm install
npm run build:app    # compiles Swift, bundles HTMLDrop.app
npm run update:app   # build + install to /Applications + re-register extension
```

## Architecture

```
HTMLDrop.app/
  Contents/
    MacOS/HTMLDrop                  ← stub host app (quits immediately after registering extension)
    PlugIns/
      HTMLDropExtension.appex/      ← Share Extension (shown in Finder Share menu)
```

**`finder/Extension/ShareViewController.swift`** — handles the share sheet, size check, password dialog, upload  
**`finder/Sources/HTMLDropCore.swift`** — upload function, helpers  
**`finder/Sources/GeneratedConstants.swift`** — generated from `settings.json` (upload URL, max size)  
**`finder/App/main.swift`** — stub host, registers extension then quits  

## Uninstall

Delete `/Applications/HTMLDrop.app` or open **System Settings → Privacy & Security → Extensions → Finder Extensions** and remove HTMLDrop.
