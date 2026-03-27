# Scriptica

Scriptica is a **client-side** Minecraft Fabric mod that adds an in-game scripting environment (“Scriptica”) for automating common tasks.

Target Minecraft version: **1.21.11** (see `gradle.properties`).

## Features
- In-game GUI (hotkey) to edit, save, load, and run scripts.
- A small custom language with functions, loops, structs/enums, events, and a standard library.
- Automation helpers (keys, look/turn, attack/use, inventory helpers).
- World interaction helpers (raycast, block/entity queries, simple mining/placing helpers).

## Quick start (dev)
Prereqs: **JDK 21**

Run the dev client:
- Windows: `.\gradlew.bat runClient`
- macOS/Linux: `./gradlew runClient`

Open the GUI in-game:
- Default hotkey: **`** (backtick / grave accent), changeable in **Options → Controls**

Scripts are saved to:
- `.minecraft/config/scriptica/scripts/*.sca`

## Docs
- Mod usage: `docs/mod-usage.md`
- Language reference: `docs/scriptica-language.md`
- Build notes: `docs/build.md`

## GitHub Pages (docs website + homepage)
This repo includes a simple docs website under `docs/` (static, no build step).

To publish it on GitHub:
1. Go to **Settings → Pages**
2. **Build and deployment**: “Deploy from a branch”
3. Select branch `main` (or `master`), folder `/docs`

Then your site will be available at your repo’s GitHub Pages URL.

