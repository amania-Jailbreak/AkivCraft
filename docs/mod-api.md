# AkivCraft Mod API

AkivCraft Node mods receive one `api` object in `onEnable(api)`.

## Player

- `api.player.state()` returns name, position, velocity, yaw, pitch, facing, health, food, level, dimension, and biome.
- `api.player.position()` returns `{ x, y, z }`.
- `api.player.blockPosition()` returns block coordinates.
- `api.player.facing()` returns `N`, `E`, `S`, or `W` style direction text.
- `api.player.health()` returns current health.
- `api.player.dimension()` returns the current dimension id.

## World

- `api.world.state()` returns dimension, biome, time, day, weather, and light level.
- `api.world.dimension()` returns the current dimension id.
- `api.world.biome()` returns the current biome when available.
- `api.world.timeOfDay()` returns world time.
- `api.world.surface()` returns nearby surface blocks for map-style HUDs.
- `api.world.minimap()` is an alias for `api.world.surface()`.
- `api.world.entities()` returns nearby renderable players and mobs with id, uuid, name, type, kind, and position. `kind` is `player`, `passive_mob`, or `hostile_mob`.
- Surface blocks include `y`, so map mods can render height shading, contour lines, and altitude ranges without extra API calls.

## Server

- `api.server.state()` returns connection, address, brand, and ping info.
- `api.server.connected()` returns whether a server is connected.
- `api.server.address()` returns the current server address when available.

## Client

- `api.client.state()` returns Minecraft version, FPS, current screen, and paused state.
- `api.client.fps()` returns current FPS.
- `api.client.minecraftVersion()` returns the target Minecraft version.

## HUD

- `api.hud.addText(id, callback, options)` registers text HUD content. Options: `x`, `y`, `color`, `background`, and `shadow`.
- `api.hud.addCanvas(id, callback)` registers generic HUD primitives. The callback returns `rect`, `text`, `sprite`, and `bitmapRle` primitives; mods own layout and rendering composition.
- `api.hud.addBitmap(id, callback)` registers a binary `palette8` HUD bitmap. This avoids text/base64 transport and is intended for dense high-resolution HUD images.
- `api.hud.remove(id)` removes HUD content.

`bitmapRle` is retained as a text IPC fallback. For higher resolutions, prefer `addBitmap`, which sends raw binary palette indices over the binary IPC channel.

`addBitmap` callbacks may return `fps` to cap bitmap regeneration and transport frequency for that item. Dense HUDs should prefer `fps: 8` to `fps: 10` unless they need smooth animation.

`addBitmap` is transported over UDP when available. The loader receives chunked UDP frames and adopts only complete frames; TCP binary remains as a fallback if UDP frames are missing.

`sprite` primitives draw textures from Minecraft resource packs. Mods can generate resource packs by declaring a `resources` map on the mod export. Resource files are copied into `.akivcraft/generated-resourcepacks/<pack-id>` and loaded by the Java loader during startup or shortly after generation.

Generated resources can include item textures and item model JSON. Full runtime item registry injection is not implemented yet, so `example-mods/item-sample` demonstrates the asset/data-pack side of a custom item and a HUD preview rather than a new registered Minecraft item id.

Example:

```js
export default {
  id: "my-hud",
  name: "My HUD",
  resources: {
    "assets/my_hud/textures/gui/icon.png": "./assets/icon.png"
  },
  onEnable(api) {
    api.hud.addCanvas("my-hud", () => [
      {
        kind: "sprite",
        texture: "my_hud:textures/gui/icon.png",
        x: 8,
        y: 8,
        width: 16,
        height: 16
      }
    ])
  }
}
```

## Keybindings

- `api.keys.register(binding)` registers a mod keybinding with category and feature metadata.
- `api.keys.onPress(listener)` listens to raw key press events as `KEY_<code>`.
- `api.keys.onRelease(listener)` listens to raw key release events as `KEY_<code>`.

Registered keybindings are also registered as Minecraft `KeyMapping` entries, so players can change them from Minecraft's normal Controls settings. Binding callbacks use Minecraft's resolved key mapping, not only the `defaultKey` declared by the mod.

Example:

```js
api.keys.register({
  id: "minimap.toggle",
  category: "AkivMap",
  name: "Toggle minimap",
  description: "Show or hide the minimap.",
  defaultKey: 77, // GLFW M
  onPress() {
    const enabled = api.settings.get("minimap.enabled") !== false
    api.settings.set("minimap.enabled", !enabled)
  }
})
```

## Items

- `api.items.register(item)` registers a simple runtime item id with Minecraft's built-in item registry.
- Supported fields: `id`, `name`, `category`, `type`, `material`, `maxStackSize`, `durability`, `attackDamage`, `attackSpeed`, `miningSpeed`, `rarity`, `fireResistant`, and `tab`.
- `tab` links an item to a creative tab id registered via `api.creative.registerTab`.
- Supported `type` values: `item`, `sword`, `pickaxe`, `axe`, `shovel`, and `hoe`.
- Supported `material` values: `wood`, `stone`, `copper`, `iron`, `diamond`, `gold`, and `netherite`.
- Mods should also provide generated resources for item texture/model files so the item renders correctly.

## Creative Tabs

- `api.creative.registerTab(tab)` registers a custom creative tab.
- Supported fields: `id`, `name`, `icon` (item id), and `row` (`"top"` or `"bottom"`).
- Tabs auto-assign columns starting from 7 (page 1 in the creative inventory pagination).
- Items with a matching `tab` field appear in the registered tab.

Example:

```js
api.creative.registerTab({
  id: "akivcraft.mymod:main",
  name: "My Mod Items",
  icon: "akivcraft.mymod:custom_item",
  row: "top"
})

api.items.register({
  id: "akivcraft.mymod:custom_item",
  name: "Custom Item",
  tab: "akivcraft.mymod:main"
})
```

Example:

```js
api.items.register({
  id: "akivcraft.item_sample:akiv_gem",
  name: "Akiv Gem",
  maxStackSize: 64,
  rarity: "rare"
})

api.items.register({
  id: "akivcraft.item_sample:akiv_sword",
  name: "Akiv Sword",
  type: "sword",
  material: "diamond",
  attackDamage: 4,
  attackSpeed: -2.2,
  rarity: "epic",
  fireResistant: true
})
```

The item sample mod registers `akivcraft.item_sample:akiv_gem`, `akivcraft.item_sample:akiv_sword`, and `akivcraft.item_sample:akiv_pickaxe`. After startup, test them with:

```text
/give @s akivcraft.item_sample:akiv_gem
/give @s akivcraft.item_sample:akiv_sword
/give @s akivcraft.item_sample:akiv_pickaxe
```

## Metadata

Each mod can include `mod.json`:

```json
{
  "id": "minimap",
  "name": "AkivMap",
  "version": "0.6.0",
  "description": "256x256 palette8 binary minimap with resource pack sprites."
}
```

The Java Mod Menu reads `loaded-mods.json` generated by the Node runtime, then falls back to each mod's `mod.json`.
