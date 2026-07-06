# Architecture

AkivCraft is split into four layers:

- `launcher-gpui`: Rust and GPUI desktop launcher.
- `launcher-core`: shared launcher model and launch profile logic.
- `loader-java`: Java loader main class and class transformer pipeline.
- `node-runtime`: NodeJS runtime that loads JavaScript and TypeScript mods.

The first implementation keeps Minecraft rendering unchanged. Metal and Vulkan support are intentionally out of scope for this phase.

```text
GPUI Launcher
  -> AkivCraftMain
  -> AkivCraft transforming classloader
  -> Minecraft Main
  -> Node Runtime
  -> JavaScript/TypeScript Mods
```

The Java loader main class prepares Node mods before vanilla Minecraft starts, then invokes `net.minecraft.client.main.Main` through AkivCraft's transforming classloader. The Node runtime is responsible for loading user mods and exposing the stable API.
