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
- `api.world.setBlock(x, y, z, blockId)` places a block at the given coordinates. In singleplayer this edits the server level directly; in multiplayer it sends a vanilla `/setblock` command. `blockId` is a full id like `minecraft:stone` or `akivcraft.mymod:custom_block`.
- `api.world.removeBlock(x, y, z)` removes the block at the given coordinates (sets it to air). Same singleplayer/multiplayer behavior as `setBlock`.
- `api.world.getBlock(x, y, z)` returns a Promise resolving to the block id at the given coordinates (e.g. `"minecraft:stone"`). Works in both stdio IPC mode and TCP IPC mode.
- `api.world.getBlocks(x1, y1, z1, x2, y2, z2)` returns a Promise resolving to an array of `{ x, y, z, id }` objects for all blocks in the bounding box. Max 4096 blocks per query. Works in both stdio IPC mode and TCP IPC mode.

Block edits run on the game thread and are asynchronous from the mod's perspective. Mods should not assume a block is present immediately after calling `setBlock`. Use block events (`api.events.on("place_block", ...)`) to confirm placement when needed.

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

Generated resources can include item textures and item model JSON. Runtime item registration is implemented in Java, so `example-mods/item-sample` demonstrates direct item registration plus resource-pack-backed client rendering.

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

## Recipes

- `api.recipes.register(recipe)` registers a custom recipe directly into Minecraft's `RecipeManager` at startup. No data pack JSON files are needed.

Supported recipe types: `crafting_shapeless`, `crafting_shaped`, `smelting`, `blasting`, `smoking`, `campfire_cooking`, `stonecutting`, `smithing_transform`, `smithing_trim`.

For `crafting_shapeless`, `ingredients` is an array. For `crafting_shaped`, `pattern` is an array of strings and `ingredients` is a key map like `{ A: { item: "minecraft:stone" } }`.

Example:

```js
api.recipes.register({
  id: "akivcraft.mymod:ruby_from_diamonds",
  type: "crafting_shapeless",
  ingredients: [{ item: "minecraft:diamond" }, { item: "minecraft:emerald" }],
  result: { id: "akivcraft.mymod:ruby", count: 1 },
})
```

## Blocks

- `api.blocks.register(block)` registers a custom block directly in Minecraft's `BLOCK` registry at freeze time.

Supported fields: `id`, `material`, `hardness`, `explosionResistance`, `lightLevel`, `requiresTool`, `instabreak`, `noCollision`, `noOcclusion`, `friction`, `speedFactor`, `jumpFactor`, `pushReaction`, and `mapColor`.

Example:

```js
api.blocks.register({
  id: "akivcraft.mymod:ruby_block",
  material: "stone",
  hardness: 3.0,
  explosionResistance: 3.0,
  requiresTool: true,
})
```

## Entities

- `api.entities.register(entity)` registers a custom entity type directly in Minecraft's `ENTITY_TYPE` registry at freeze time.

Supported fields: `id`, `width`, `height`, `fireImmune`, `summonable`, `trackingRange`, `updateInterval`, and `clientTrackingRange`.

Example:

```js
api.entities.register({
  id: "akivcraft.mymod:ruby_golem",
  width: 1.4,
  height: 2.7,
  fireImmune: true,
  summonable: true,
})
```

Custom entities use a vanilla pig factory as placeholder until a custom entity class and renderer are implemented.

## Features And Carvers

- `api.features.register(feature)` registers a custom placed feature directly in Java registries.
- `api.carvers.register(carver)` registers a custom configured carver directly in Java registries.

These APIs are intentionally low-level and mirror Minecraft's own configured JSON shape. The current implementation wraps feature definitions as a placed feature with empty placement modifiers.

Example:

```js
api.features.register({
  id: "akivcraft.mymod:noop_feature",
  type: "minecraft:no_op",
  config: {},
})

api.carvers.register({
  id: "akivcraft.mymod:cave_copy",
  type: "minecraft:cave",
  config: {
    probability: 0.0,
  },
})
```

## Block Events

- `api.events.on("use_block", callback)` listens after a server-side block right click.
- `api.events.on("place_block", callback)` listens after a likely block placement caused by right click.
- `api.events.on("break_block", callback)` listens after server-side block destruction.
- `api.blocks.onUse(blockId, callback)`, `api.blocks.onPlace(blockId, callback)`, and `api.blocks.onBreak(blockId, callback)` are convenience filters by block id.

Block event context includes player, dimension, item id, hand, target block/position, clicked face, click position, placement position, broken block, current block, and consumed result when available.

Example:

```js
export default {
  id: "portal-sample",
  name: "Portal Sample",
  onEnable(api) {
    api.events.on("use_block", (ctx) => {
      if (ctx.itemId === "minecraft:flint_and_steel" && ctx.targetBlock === "minecraft:obsidian") {
        api.chat.send(`Portal trigger at ${ctx.targetPos.x}, ${ctx.targetPos.y}, ${ctx.targetPos.z}`)
      }
    })

    api.blocks.onPlace("minecraft:diamond_block", (ctx) => {
      api.chat.send(`Diamond block placed in ${ctx.dimension}`)
    })
  }
}
```

The first implementation is notification-only. It does not cancel vanilla placement/use. Use it for triggers, logging, portal detection, and multiblock rescans around the changed position.

## Multiblock Detection

- `api.blocks.detectPattern(anchor, pattern)` returns a Promise resolving to `true` if all blocks in the pattern match at the given anchor position. `pattern` is a map of `"dx,dy,dz"` offset strings to expected block ids.

Example — detect a 2x2 nether portal frame:

```js
const PORTAL_PATTERN = {
  "0,0,0": "minecraft:obsidian",
  "1,0,0": "minecraft:obsidian",
  "0,1,0": "minecraft:obsidian",
  "1,1,0": "minecraft:obsidian",
  "0,2,0": "minecraft:obsidian",
  "1,2,0": "minecraft:obsidian",
}

api.blocks.onUse("minecraft:obsidian", async (ctx) => {
  const match = await api.blocks.detectPattern(ctx.targetPos, PORTAL_PATTERN)
  if (match) {
    api.chat.send("Nether portal frame detected!")
    api.world.setBlock(ctx.targetPos.x, ctx.targetPos.y + 1, ctx.targetPos.z, "minecraft:fire")
  }
})
```

`detectPattern` uses `api.world.getBlocks` internally and works in both stdio IPC mode and TCP IPC mode.

## Custom Dimensions

- `api.dimensions.register(dimension)` registers a custom dimension. The Node runtime generates dimension and dimension_type data pack JSON files in `generated-resourcepacks/akivcraft.dimensions/`, which Minecraft loads automatically.

Supported fields: `id` (required, e.g. `"akivcraft.mymod:void_world"`), `name`, `type` (dimension type properties), and `generator` (noise/biome source settings).

Example:

```js
api.dimensions.register({
  id: "akivcraft.mymod:void_world",
  name: "Void World",
  type: {
    height: 256,
    minY: 0,
    hasSkylight: false,
    hasCeiling: false,
    ultrawarm: false,
    natural: false,
    bedWorks: false,
    respawnAnchorWorks: false,
    effects: "minecraft:the_end",
  },
  generator: {
    template: "end",
  },
})
```

Dimensions are registered directly in Minecraft's `DIMENSION_TYPE` and `LEVEL_STEM` registries at registry freeze time — no data packs are used. The chunk generator is cloned from an existing dimension (`overworld`, `nether`, or `end` via `generator.template`). New worlds will automatically include custom dimensions.

## Biomes

- `api.biomes.register(biome)` registers a custom biome with noise placement, spawn settings, and visual properties. Biomes are injected into Minecraft's biome registry and noise parameter lists at startup.

Supported fields include `id`, `temperature`, `downfall`, `hasPrecipitation`, `noise` (temperature/humidity/continentalness/erosion/depth/weirdness ranges and offset), `source` (`"overworld"`, `"nether"`, or `"all"`), `skyColor`, `fogColor`, `waterColor`, `grassColor`, `foliageColor`, `spawners`, `spawnCosts`, `backgroundMusic`, `ambientSound`, and various boolean attributes.

`features` and `carvers` fields are applied through `PLACED_FEATURE` and `CONFIGURED_CARVER` registry injection. Register custom features via `api.features.register(...)` and custom carvers via `api.carvers.register(...)`, then reference them by id in biome definitions.

Example:

```js
api.biomes.register({
  id: "akivcraft.mymod:crystal_plains",
  temperature: 0.5,
  downfall: 0.3,
  hasPrecipitation: true,
  source: "overworld",
  noise: {
    temperature: [0.3, 0.7],
    humidity: [0.2, 0.6],
    continentalness: [-0.3, 0.3],
    erosion: [-0.5, 0.5],
    depth: [-1, 1],
    weirdness: [-0.5, 0.5],
    offset: 0,
  },
  skyColor: "#78a7ff",
  fogColor: "#c0d8ff",
  waterColor: "#3f76e4",
  grassColor: "#7ec850",
  foliageColor: "#5da83a",
  spawners: {
    creature: [{ type: "minecraft:sheep", weight: 12, minCount: 4, maxCount: 4 }],
  },
})
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
