const MAP_PIXELS = 256
const SURFACE_RADIUS = 32
const SMALL_SCALE = 0.5
const LARGE_SCALE = 1.35
const PADDING = 6
const HEADER = 24

const COLORS = {
  panel: "#b00a1018",
  panel2: "#d0142430",
  border: "#ff67ffd8",
  water: "#ff2479ff",
  lava: "#ffff6120",
  grass: "#ff43bd52",
  foliage: "#ff319d45",
  flower: "#ffff7ad9",
  crop: "#ffd6b64c",
  leaves: "#ff238f3c",
  wood: "#ff8a5a2b",
  stone: "#ff777b86",
  deepslate: "#ff4b4f5b",
  basalt: "#ff373741",
  blackstone: "#ff26242d",
  tuff: "#ff6a706f",
  sand: "#ffe0cf8a",
  red_sand: "#ffc77445",
  gravel: "#ff8d8880",
  snow: "#ffeeffff",
  ice: "#ff9fe8ff",
  dirt: "#ff7a5531",
  mud: "#ff4f3d32",
  clay: "#ff9aa0aa",
  ore: "#ffffd24a",
  nether: "#ff8f2828",
  end: "#ffd8d59a",
  structure: "#ff9b8f82",
  glass: "#ffa9f4ff",
  metal: "#ffc7cbd4",
  wool: "#ffe7e0d6",
  other: "#ff53575f",
  air: "#ff101820",
  player: "#fffff36a",
  otherPlayer: "#ff55f7ff",
  passiveMob: "#ff66ff87",
  hostileMob: "#ffff3048",
  marker: "#ff101010",
  text: "#ffdbfff5",
  muted: "#ff9db8c8",
}

function shadeColor(color, amount) {
  const alpha = color.slice(1, 3)
  const rgb = [color.slice(3, 5), color.slice(5, 7), color.slice(7, 9)].map((part) => Number.parseInt(part, 16))
  const shaded = rgb.map((channel) => {
    const next = amount >= 0
      ? channel + (255 - channel) * amount
      : channel * (1 + amount)
    return Math.max(0, Math.min(255, Math.round(next))).toString(16).padStart(2, "0")
  })
  return `#${alpha}${shaded.join("")}`
}

const SHADED_KINDS = [
  "air",
  "water",
  "lava",
  "grass",
  "foliage",
  "flower",
  "crop",
  "leaves",
  "wood",
  "stone",
  "deepslate",
  "basalt",
  "blackstone",
  "tuff",
  "sand",
  "red_sand",
  "gravel",
  "snow",
  "ice",
  "dirt",
  "mud",
  "clay",
  "ore",
  "nether",
  "end",
  "structure",
  "glass",
  "metal",
  "wool",
  "other",
]

const KIND_SHADES = Object.fromEntries(SHADED_KINDS.map((kind) => [kind, [
  shadeColor(COLORS[kind], -0.34),
  shadeColor(COLORS[kind], -0.18),
  COLORS[kind],
  shadeColor(COLORS[kind], 0.18),
  shadeColor(COLORS[kind], 0.36),
]]))

const PALETTE = [
  ...Object.values(KIND_SHADES).flat(),
  COLORS.player,
  COLORS.otherPlayer,
  COLORS.passiveMob,
  COLORS.hostileMob,
  COLORS.marker,
]

const PALETTE_INDEX = new Map(PALETTE.map((color, index) => [color, index]))

const ARROWS = {
  NORTH: [[0, -5], [-1, -4], [1, -4], [-2, -3], [2, -3], [0, -4], [0, -3], [0, -2], [0, -1], [0, 0]],
  SOUTH: [[0, 5], [-1, 4], [1, 4], [-2, 3], [2, 3], [0, 4], [0, 3], [0, 2], [0, 1], [0, 0]],
  EAST: [[5, 0], [4, -1], [4, 1], [3, -2], [3, 2], [4, 0], [3, 0], [2, 0], [1, 0], [0, 0]],
  WEST: [[-5, 0], [-4, -1], [-4, 1], [-3, -2], [-3, 2], [-4, 0], [-3, 0], [-2, 0], [-1, 0], [0, 0]],
}

function shortDimension(dimension) {
  return dimension.replace("minecraft:", "")
}

function shortBiome(biome) {
  return (biome ?? "unknown")
    .replace(/^ResourceKey\[.* \/ /, "")
    .replace(/^minecraft:/, "")
    .replace(/\]$/, "")
    .replaceAll("_", " ")
}

function surfaceData(api) {
  const surface = api.world.surface()
  return {
    surface,
    blocks: new Map(surface.map((block) => [`${block.x},${block.z}`, block])),
  }
}

function paletteIndex(color) {
  return PALETTE_INDEX.get(color) ?? PALETTE_INDEX.get(COLORS.other) ?? 0
}

function blockAt(blocks, x, z) {
  return blocks.get(`${x},${z}`)
}

function heightShadeIndex(block, center, blocks) {
  if (!block) return paletteIndex(KIND_SHADES.air[0])

  const kind = KIND_SHADES[block.kind] ? block.kind : "other"
  const east = blockAt(blocks, block.x + 1, block.z)
  const south = blockAt(blocks, block.x, block.z + 1)
  const slope = Math.max(
    east ? Math.abs(block.y - east.y) : 0,
    south ? Math.abs(block.y - south.y) : 0,
  )
  const relative = block.y - center.y
  const shade = Math.max(-2, Math.min(2, Math.round(relative / 9) + Math.min(2, Math.floor(slope / 4))))
  return paletteIndex(KIND_SHADES[kind][shade + 2])
}

function heightRange(api) {
  let min = Infinity
  let max = -Infinity
  for (const block of api.world.surface()) {
    min = Math.min(min, block.y)
    max = Math.max(max, block.y)
  }
  return Number.isFinite(min) ? { min, max } : { min: 0, max: 0 }
}

function buildPixels(api) {
  const large = api.settings.get("minimap.large") === true
  const player = api.player.state()
  const center = player.blockPosition
  const { blocks } = surfaceData(api)
  const pixels = new Uint8Array(MAP_PIXELS * MAP_PIXELS)
  const columns = SURFACE_RADIUS * 2 + 1

  for (let gz = 0; gz < columns; gz += 1) {
    const z0 = Math.floor((gz / columns) * MAP_PIXELS)
    const z1 = Math.floor(((gz + 1) / columns) * MAP_PIXELS)
    const dz = gz - SURFACE_RADIUS

    for (let gx = 0; gx < columns; gx += 1) {
      const x0 = Math.floor((gx / columns) * MAP_PIXELS)
      const x1 = Math.floor(((gx + 1) / columns) * MAP_PIXELS)
      const dx = gx - SURFACE_RADIUS
      const block = blockAt(blocks, center.x + dx, center.z + dz)
      const color = heightShadeIndex(block, center, blocks)

      for (let py = z0; py < z1; py += 1) {
        pixels.fill(color, py * MAP_PIXELS + x0, py * MAP_PIXELS + x1)
      }
    }
  }

  drawEntityMarkers(pixels, player, api.world.entities())
  drawPlayerMarker(pixels, player.facing)
  return pixels
}

function worldToPixel(center, position) {
  const dx = position.x - center.x
  const dz = position.z - center.z
  if (Math.abs(dx) > SURFACE_RADIUS || Math.abs(dz) > SURFACE_RADIUS) return undefined
  return {
    x: Math.round(((dx + SURFACE_RADIUS) / (SURFACE_RADIUS * 2 + 1)) * (MAP_PIXELS - 1)),
    y: Math.round(((dz + SURFACE_RADIUS) / (SURFACE_RADIUS * 2 + 1)) * (MAP_PIXELS - 1)),
  }
}

function drawEntityMarkers(pixels, player, entities) {
  const center = player.blockPosition
  for (const entity of entities) {
    const point = worldToPixel(center, entity.position)
    if (!point) continue

    const color = entity.kind === "player"
      ? COLORS.otherPlayer
      : entity.kind === "hostile_mob"
        ? COLORS.hostileMob
        : COLORS.passiveMob
    const radius = entity.kind === "player" ? 5 : entity.kind === "hostile_mob" ? 6 : 5
    drawDot(pixels, point.x, point.y, paletteIndex(COLORS.marker), radius + 2)
    drawDot(pixels, point.x, point.y, paletteIndex(color), radius)
  }
}

function drawDot(pixels, cx, cy, color, radius) {
  for (let y = -radius; y <= radius; y += 1) {
    for (let x = -radius; x <= radius; x += 1) {
      if (x * x + y * y > radius * radius) continue
      const px = cx + x
      const py = cy + y
      if (px < 0 || py < 0 || px >= MAP_PIXELS || py >= MAP_PIXELS) continue
      pixels[py * MAP_PIXELS + px] = color
    }
  }
}

function drawPlayerMarker(pixels, facing) {
  const cx = Math.floor(MAP_PIXELS / 2)
  const cy = Math.floor(MAP_PIXELS / 2)
  const player = paletteIndex(COLORS.player)
  const marker = paletteIndex(COLORS.marker)
  for (let y = -3; y <= 3; y += 1) {
    for (let x = -3; x <= 3; x += 1) {
      if (x * x + y * y <= 9) pixels[(cy + y) * MAP_PIXELS + cx + x] = player
    }
  }
  for (const [x, y] of ARROWS[facing] ?? ARROWS.NORTH) {
    pixels[(cy + y) * MAP_PIXELS + cx + x] = marker
  }
}

function bitmap(api) {
  const large = api.settings.get("minimap.large") === true
  const x = Number(api.settings.get(large ? "minimap.large.x" : "minimap.x") ?? (large ? 48 : 8))
  const y = Number(api.settings.get(large ? "minimap.large.y" : "minimap.y") ?? (large ? 36 : 28))
  const enabled = api.settings.get("minimap.enabled") !== false
  const scale = large ? LARGE_SCALE : SMALL_SCALE
  if (!enabled) {
    return {
      x: 0,
      y: 0,
      width: 1,
      height: 1,
      scale: 1,
      fps: 2,
      palette: ["#00000000"],
      pixels: new Uint8Array(1),
    }
  }

  return {
    x: x + PADDING,
    y: y + HEADER,
    width: MAP_PIXELS,
    height: MAP_PIXELS,
    scale,
    fps: large ? 8 : 10,
    palette: PALETTE,
    pixels: buildPixels(api),
  }
}

function displayPixels(api) {
  return Math.round(MAP_PIXELS * (api.settings.get("minimap.large") === true ? LARGE_SCALE : SMALL_SCALE))
}

function renderChrome(api) {
  const large = api.settings.get("minimap.large") === true
  const x = Number(api.settings.get(large ? "minimap.large.x" : "minimap.x") ?? (large ? 48 : 8))
  const y = Number(api.settings.get(large ? "minimap.large.y" : "minimap.y") ?? (large ? 36 : 28))
  const player = api.player.state()
  const world = api.world.state()
  const center = player.blockPosition
  const width = displayPixels(api) + PADDING * 2
  const height = HEADER + displayPixels(api) + PADDING + (large ? 34 : 20)
  const entities = api.world.entities()
  const players = entities.filter((entity) => entity.kind === "player").length
  const passive = entities.filter((entity) => entity.kind === "passive_mob").length
  const hostile = entities.filter((entity) => entity.kind === "hostile_mob").length
  const range = heightRange(api)

  const chrome = [
    { kind: "rect", x, y, width, height, color: COLORS.panel },
    { kind: "rect", x: x + 1, y: y + 1, width: width - 2, height: 18, color: COLORS.panel2 },
    { kind: "rect", x, y, width, height: 1, color: COLORS.border },
    { kind: "rect", x, y: y + height - 1, width, height: 1, color: COLORS.border },
    { kind: "rect", x, y, width: 1, height, color: COLORS.border },
    { kind: "rect", x: x + width - 1, y, width: 1, height, color: COLORS.border },
    { kind: "sprite", texture: "akivcraft.minimap:textures/gui/frame_corner.png", x: x + 2, y: y + 2, width: 16, height: 16 },
    { kind: "sprite", texture: "akivcraft.minimap:textures/gui/frame_corner.png", x: x + width - 18, y: y + 2, width: 16, height: 16 },
    { kind: "text", x: x + 6, y: y + 6, text: `AkivMap${large ? " Large" : ""}  ${shortDimension(world.dimension)}  256x256`, color: COLORS.text },
    { kind: "text", x: x + 6, y: y + height - 14, text: `${center.x}, ${center.y}, ${center.z}  ${shortBiome(world.biome)}`, color: COLORS.muted },
  ]

  if (large) {
    chrome.push(
      { kind: "text", x: x + 8, y: y + height - 28, text: `Players ${players}`, color: COLORS.otherPlayer },
      { kind: "text", x: x + 74, y: y + height - 28, text: `Passive ${passive}`, color: COLORS.passiveMob },
      { kind: "text", x: x + 146, y: y + height - 28, text: `Hostile ${hostile}`, color: COLORS.hostileMob },
      { kind: "text", x: x + width - 132, y: y + height - 28, text: `Y ${center.y}  ${range.min}-${range.max}`, color: COLORS.text },
    )
  }

  return chrome
}

export default {
  id: "minimap",
  name: "AkivMap",
  version: "0.6.0",
  description: "256x256 palette8 binary minimap with resource pack sprites.",
  resources: {
    "assets/akivcraft.minimap/textures/gui/frame_corner.png": "./assets/textures/gui/frame_corner.png",
    "assets/akivcraft.minimap/textures/gui/player_marker.png": "./assets/textures/gui/player_marker.png",
  },
  onEnable(api) {
    api.settings.define({
      "minimap.enabled": true,
      "minimap.large": false,
      "minimap.x": 8,
      "minimap.y": 28,
      "minimap.large.x": 48,
      "minimap.large.y": 36,
    })

    api.keys.register({
      id: "minimap.toggle",
      category: "AkivMap",
      name: "Toggle minimap",
      description: "Show or hide the AkivMap HUD.",
      defaultKey: 77,
      onPress() {
        const next = api.settings.get("minimap.enabled") === false
        api.settings.set("minimap.enabled", next)
        api.chat.send(`AkivMap ${next ? "enabled" : "disabled"}`)
      },
    })

    api.keys.register({
      id: "minimap.large.toggle",
      category: "AkivMap",
      name: "Toggle large map",
      description: "Switch between compact minimap and large AkivMap overlay.",
      defaultKey: 78,
      onPress() {
        const next = api.settings.get("minimap.large") !== true
        api.settings.set("minimap.large", next)
        if (next) api.settings.set("minimap.enabled", true)
        api.chat.send(`AkivMap large map ${next ? "enabled" : "disabled"}`)
      },
    })

    api.hud.addCanvas("minimap.chrome", () => {
      if (api.settings.get("minimap.enabled") === false) return []
      return renderChrome(api)
    })

    api.hud.addBitmap("minimap.surface", () => bitmap(api))

    api.chat.send("AkivMap 256x256 binary minimap enabled. Press N for large map.")
  },
  onDisable(api) {
    api.hud.remove("minimap.chrome")
    api.hud.remove("minimap.surface")
  },
}
