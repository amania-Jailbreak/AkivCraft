# External Launcher Usage

AkivCraft can run from another Minecraft launcher by using AkivCraft's loader main class instead of vanilla `net.minecraft.client.main.Main`. Prism Launcher, MultiMC, ATLauncher, or another launcher can still handle Microsoft login.

## Build Package

```sh
npm install
npm run package:external
```

The package is created at:

```text
dist/external-launcher/
```

## Install Into An Instance

Copy this folder into the Minecraft instance or game directory used by your launcher:

```text
dist/external-launcher/.akivcraft
```

Do not copy only `akivcraft-loader.jar`. The full `.akivcraft` folder is required because it contains the Node runtime and mods.

The result should look like this:

```text
<instance game directory>/.akivcraft/akivcraft-loader.jar
<instance game directory>/.akivcraft/node-runtime/dist/index.js
<instance game directory>/.akivcraft/mods/fps-hud/index.js
<instance game directory>/.akivcraft/mods/minimap/index.js
```

## Prism/MultiMC Component Patch

Copy the generated patch into the instance patch directory:

```text
dist/external-launcher/patches/dev.akivcraft.loader.json
```

to:

```text
<Prism instance>/patches/dev.akivcraft.loader.json
```

Add this generated component entry to the `components` array in the instance `mmc-pack.json`:

```text
dist/external-launcher/mmc-pack-component.json
```

The entry is:

```json
{
  "cachedName": "AkivCraft Loader",
  "cachedVersion": "0.1.0",
  "important": true,
  "uid": "dev.akivcraft.loader",
  "version": "0.1.0"
}
```

Copy the generated local library path into the launcher libraries directory, preserving paths:

```text
dist/external-launcher/libraries/dev/akivcraft/akivcraft-loader/0.1.0/akivcraft-loader-0.1.0.jar
```

to:

```text
<Prism libraries>/dev/akivcraft/akivcraft-loader/0.1.0/akivcraft-loader-0.1.0.jar
```

The patch sets this main class automatically:

```text
dev.akivcraft.loader.AkivCraftMain
```

## Manual Fallback

If your launcher cannot use Prism/MultiMC component patches, add `.akivcraft/akivcraft-loader.jar` to the Minecraft classpath/libraries, then set the launch main class to:

```text
dev.akivcraft.loader.AkivCraftMain
```

Add these JVM arguments:

```text
-Dakivcraft.home=.akivcraft
-Dakivcraft.minecraftVersion=26.1.2
```

If your launcher resolves relative paths from a different directory, use absolute paths instead:

```text
-Dakivcraft.home=C:\Users\<user>\AppData\Roaming\PrismLauncher\instances\26.1.2\minecraft\.akivcraft
-Dakivcraft.minecraftVersion=26.1.2
```

NodeJS 20 or newer must be available as `node` on PATH.
Java 25 or newer is required for Minecraft `26.1.2`.

AkivCraft uses stdio IPC by default, so it does not need localhost TCP or UDP ports for control messages. Bitmap and HUD data is sent over a Unix domain socket (`.akivcraft/ipc.sock`) when stdio mode is active, which avoids head-of-line blocking on the main stdio pipe and keeps latency low for state updates and events. To force the older port-based transport for debugging, add:

```text
-Dakivcraft.ipcTransport=tcp
```

AkivCraft automatically looks for Node.js in common locations on macOS:

```text
/opt/homebrew/bin/node        # Apple Silicon Homebrew
/usr/local/bin/node           # Intel Homebrew
/usr/bin/node                 # system
~/.nvm/current/bin/node       # nvm
~/.volta/bin/node             # Volta
~/.fnm/current/bin/node       # fnm
```

If none of these work and `node` is not on PATH, add this JVM argument with the full path:

```text
-Dakivcraft.node=/path/to/node
```

On macOS, GUI launchers often do not inherit your shell PATH. If auto-detection fails and Node was installed with Homebrew, this is commonly:

```text
-Dakivcraft.node=/opt/homebrew/bin/node
```

or on Intel Macs:

```text
-Dakivcraft.node=/usr/local/bin/node
```

AkivCraft writes a PID file to `.akivcraft/node.pid` to prevent multiple Node runtimes from running under the same `.akivcraft` home. If a stale PID file is found, it is replaced automatically.

## Current Limitations

AkivCraft sets the launcher brand property and patches Minecraft's official mod status check. The log should include:

```text
AkivCraft installed Minecraft mod status hook
```

AkivCraft also tries to add a `Mod Menu` button to the title screen and pause screen. The log should include one or more of these lines when the screens load:

```text
AkivCraft installed layout Mod Menu hook into net/minecraft/client/gui/screens/TitleScreen
AkivCraft installed layout Mod Menu hook into net/minecraft/client/gui/screens/PauseScreen
AkivCraft added Mod Menu to TitleScreen layout at y=...
AkivCraft added Mod Menu to PauseScreen layout
AkivCraft Mod Menu opened
```

The Mod Menu reads installed mods dynamically from `.akivcraft/mods/loaded-mods.json`, falling back to each mod's `mod.json`.

If these lines do not appear, the target Minecraft build likely changed its GUI class names or button API and needs a version-specific hook update.

AkivCraft currently hooks Minecraft state capture, HUD rendering, generated resource packs, and key mappings. Registered mod keybindings are added to Minecraft's normal Controls screen.
