# AkivCraft

AkivCraft is an experimental Minecraft Java custom launcher and custom mod-client scaffold for Minecraft `26.1.2`.

## Components

- `launcher-gpui`: Rust GPUI launcher.
- `launcher-core`: shared launcher profile logic.
- `loader-java`: Java loader main class and Minecraft class transformer pipeline.
- `node-runtime`: NodeJS/TypeScript mod runtime.
- `example-mods`: sample JavaScript mods.
- `protocol`: Java loader <-> Node runtime protocol notes.
- `docs/mod-api.md`: Node mod API reference.

## Current Scope

- GPUI launcher shell.
- AkivCraft main-class bootstrap.
- NodeJS mod runtime skeleton.
- HUD, key, chat, and settings API shape.
- Example FPS HUD mod.
- Example MiniMap prototype mod.

Metal and Vulkan rendering are not implemented in this phase.

## Requirements

- Rust toolchain.
- Java 25 or newer for Minecraft `26.1.2` support.
- NodeJS 20 or newer.
- Gradle is not required globally. Use the checked-in Gradle Wrapper.

Minecraft `26.1.2` uses Java 25 class files, so the Java loader is built with Java 25.

On Linux, GPUI also needs native development packages. At minimum, install `fontconfig` development files if `cargo check -p akivcraft-launcher-gpui` fails in `yeslogic-fontconfig-sys`.

## Useful Commands

```sh
cargo check -p akivcraft-launcher-core
cargo check -p akivcraft-launcher-gpui
npm install
npm run typecheck
./gradlew :loader-java:build
javac --release 21 -d /tmp/opencode/akivcraft-loader-classes loader-java/src/main/java/dev/akivcraft/loader/*.java
```

The GPUI launcher depends on the upstream GPUI crate from the Zed repository and may require platform-specific native dependencies.

## Use With Another Launcher

Build an external launcher package:

```sh
npm run package:external
```

Then install the generated Prism/MultiMC component patch:

```text
dist/external-launcher/.akivcraft/                         -> instance minecraft/.akivcraft/
dist/external-launcher/patches/dev.akivcraft.loader.json   -> instance patches/dev.akivcraft.loader.json
dist/external-launcher/libraries/dev/akivcraft/...         -> launcher libraries/dev/akivcraft/...
```

Also add `dist/external-launcher/mmc-pack-component.json` to the instance `mmc-pack.json` `components` array.

The patch sets the launch main class automatically:

```text
dev.akivcraft.loader.AkivCraftMain
```

Manual fallback, if your launcher cannot load component patches: add `.akivcraft/akivcraft-loader.jar` to classpath/libraries, set the main class above, and add these JVM arguments:

Copy the full `.akivcraft` folder, not only `akivcraft-loader.jar`.

```text
-Dakivcraft.home=.akivcraft
-Dakivcraft.minecraftVersion=26.1.2
```

See `docs/external-launcher.md` for details.

When the brand patch is applied, the game log should contain `AkivCraft installed Minecraft mod status hook`.

When the Mod Menu hook is applied, the game log should contain `AkivCraft installed layout Mod Menu hook` and `AkivCraft added Mod Menu to ... layout` after opening the title screen or pause screen.
Clicking `Mod Menu` opens the in-game `AkivCraft Mod Menu` screen with a `Back` button.
