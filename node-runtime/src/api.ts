export type HudTextOptions = {
  x: number
  y: number
  color?: string
  background?: string
  shadow?: boolean
}

export type HudPrimitive =
  | { kind: "rect", x: number, y: number, width: number, height: number, color: string }
  | { kind: "text", x: number, y: number, text: string, color?: string, shadow?: boolean }
  | { kind: "bitmapRle", x: number, y: number, width: number, height: number, palette: string[], runs: string }
  | { kind: "sprite", texture: string, x: number, y: number, width: number, height: number, u?: number, v?: number, regionWidth?: number, regionHeight?: number, textureWidth?: number, textureHeight?: number }

export type HudBitmap = {
  x: number
  y: number
  width: number
  height: number
  scale?: number
  fps?: number
  palette: string[]
  pixels: Uint8Array
}

export type MinimapBlock = {
  x: number
  z: number
  y: number
  id: string
  kind: "air" | "water" | "lava" | "grass" | "foliage" | "flower" | "crop" | "leaves" | "wood" | "stone" | "deepslate" | "basalt" | "blackstone" | "tuff" | "sand" | "red_sand" | "gravel" | "snow" | "ice" | "dirt" | "mud" | "clay" | "ore" | "nether" | "end" | "structure" | "glass" | "metal" | "wool" | "other"
}

export type ChatMessage = {
  type: "system" | "player" | "client"
  text: string
}

export type SettingsSchema = Record<string, string | number | boolean>

export type KeyBinding = {
  id: string
  category: string
  name: string
  defaultKey: number
  description?: string
  onPress?: () => void | Promise<void>
  onRelease?: () => void | Promise<void>
}

export type ItemUseAction =
  | { type: "potion_effect", effect: string, duration: number, amplifier?: number }
  | { type: "heal", amount: number }
  | { type: "damage", amount: number }
  | { type: "teleport", range: number }
  | { type: "lightning" }
  | { type: "explosion", power: number }
  | { type: "fire_projectile", projectile: string, speed?: number, damage?: number }
  | { type: "sound", sound: string, volume?: number, pitch?: number }
  | { type: "particle", particle: string, count?: number }
  | { type: "consume", amount?: number }
  | { type: "cooldown", ticks: number }
  | { type: "command", command: string }
  | { type: "node_callback", event?: string }

export type ItemUseAnimation = "none" | "eat" | "drink" | "block" | "bow" | "spear" | "crossbow"

export type ItemDefinition = {
  id: string
  name?: string
  category?: string
  type?: "item" | "sword" | "pickaxe" | "axe" | "shovel" | "hoe"
  material?: "wood" | "stone" | "copper" | "iron" | "diamond" | "gold" | "netherite"
  maxStackSize?: number
  durability?: number
  attackDamage?: number
  attackSpeed?: number
  miningSpeed?: number
  rarity?: "common" | "uncommon" | "rare" | "epic"
  fireResistant?: boolean
  tab?: string
  onUse?: ItemUseAction[]
  useAnimation?: ItemUseAnimation
  useDuration?: number
}

export type ItemUseContext = {
  itemId: string
  player: string
  x: number
  y: number
  z: number
  event: string
  look: { x: number, y: number, z: number }
  rayHit: { x: number, y: number, z: number } | null
}

export type BlockPos = { x: number, y: number, z: number }

export type BlockEventType = "use_block" | "place_block" | "break_block"

export type BlockEventContext = {
  type: BlockEventType
  phase: "before" | "after"
  player: string
  uuid?: string
  dimension: string
  playerPos?: ClientPosition
  itemId?: string
  hand?: "main_hand" | "off_hand" | "unknown"
  targetBlock?: string
  targetPos?: BlockPos
  face?: "up" | "down" | "north" | "south" | "west" | "east" | "unknown"
  click?: ClientPosition
  placePos?: BlockPos
  placeBlock?: string
  placedBlock?: string
  breakPos?: BlockPos
  brokenBlock?: string
  currentBlock?: string
  consumed?: boolean
}

export type BlockEventHandler = (ctx: BlockEventContext) => void | Promise<void>

export type CreativeTabDefinition = {
  id: string
  name: string
  icon?: string
  row?: "top" | "bottom"
}

export type BiomeNoiseRange = number | [number, number]

export type BiomeSpawn = {
  type: string
  weight: number
  minCount: number
  maxCount: number
}

export type BiomeSpawnCost = {
  energyBudget: number
  charge: number
}

export type BiomeBackgroundMusic = {
  sound: string
  minDelay?: number
  maxDelay?: number
}

export type BiomeBedRule = {
  canSetSpawn?: "always" | "never"
  canSleep?: "always" | "never" | "when_dark"
  errorMessage?: string
}

export type BiomeDefinition = {
  id: string
  temperature: number
  downfall: number
  hasPrecipitation?: boolean
  temperatureModifier?: "none" | "frozen"
  source?: "overworld" | "nether" | "all"
  noise: {
    temperature: BiomeNoiseRange
    humidity: BiomeNoiseRange
    continentalness: BiomeNoiseRange
    erosion: BiomeNoiseRange
    depth?: BiomeNoiseRange
    weirdness: BiomeNoiseRange
    offset?: number
  }
  attributes?: Record<string, unknown>
  skyColor?: string
  fogColor?: string
  waterFogColor?: string
  cloudColor?: string
  ambientLightColor?: string
  waterColor?: string
  grassColor?: string
  foliageColor?: string
  dryFoliageColor?: string
  grassColorModifier?: "none" | "dark_forest" | "swamp"
  backgroundMusic?: string | BiomeBackgroundMusic
  ambientSound?: string
  canStartRaid?: boolean
  waterEvaporates?: boolean
  bedRule?: BiomeBedRule
  respawnAnchorWorks?: boolean
  netherPortalSpawnsPiglins?: boolean
  fastLava?: boolean
  increasedFireBurnout?: boolean
  snowGolemMelts?: boolean
  monstersBurn?: boolean
  carvers?: string[]
  features?: string[][]
  spawners?: Record<string, BiomeSpawn[]>
  spawnCosts?: Record<string, BiomeSpawnCost>
}

export type ClientPosition = {
  x: number
  y: number
  z: number
}

export type PlayerState = {
  name: string
  uuid?: string
  position: ClientPosition
  blockPosition: ClientPosition
  velocity: ClientPosition
  yaw: number
  pitch: number
  facing: string
  health: number
  maxHealth: number
  food: number
  saturation: number
  experienceLevel: number
  dimension: string
  biome?: string
}

export type EntityState = {
  id: number
  uuid: string
  name: string
  type: string
  kind: "player" | "passive_mob" | "hostile_mob"
  position: ClientPosition
}

export type WorldState = {
  dimension: string
  biome?: string
  timeOfDay: number
  day: number
  weather: "clear" | "rain" | "thunder"
  lightLevel?: number
  minimap?: {
    radius: number
    blocks: MinimapBlock[]
  }
  surface?: {
    radius: number
    blocks: MinimapBlock[]
  }
  entities?: EntityState[]
}

export type ServerState = {
  connected: boolean
  address?: string
  brand?: string
  pingMs?: number
}

export type GameState = {
  minecraftVersion: string
  fps: number
  screen?: string
  paused: boolean
}

export type DimensionTypeDefinition = {
  height?: number
  minY?: number
  logicalHeight?: number
  ambientLight?: number
  hasSkylight?: boolean
  hasCeiling?: boolean
  ultrawarm?: boolean
  natural?: boolean
  coordinateScale?: number
  bedWorks?: boolean
  respawnAnchorWorks?: boolean
  hasRaids?: boolean
  piglinSafe?: boolean
  effects?: string
  infiniburn?: string
  fixedTime?: number | null
  monsterSpawnLightLevel?: number
  monsterSpawnBlockLightLimit?: number
}

export type DimensionGeneratorDefinition = {
  type?: string
  settings?: string
  biomeSource?: {
    type?: string
    preset?: string
  }
  seed?: number
}

export type DimensionDefinition = {
  id: string
  name?: string
  type?: DimensionTypeDefinition
  generator?: DimensionGeneratorDefinition
}

export type AkivCraftApi = {
  hud: {
    addText(id: string, value: () => string, options: HudTextOptions): void
    addCanvas(id: string, render: () => HudPrimitive[]): void
    addBitmap(id: string, render: () => HudBitmap): void
    remove(id: string): void
  }
  keys: {
    register(binding: KeyBinding): void
    onPress(listener: (key: string) => void): void
    onRelease(listener: (key: string) => void): void
  }
  items: {
    register(item: ItemDefinition): void
    onUse(itemId: string, callback: (ctx: ItemUseContext) => void | Promise<void>): void
  }
  events: {
    on(type: BlockEventType, callback: BlockEventHandler): void
  }
  blocks: {
    onUse(blockId: string, callback: BlockEventHandler): void
    onPlace(blockId: string, callback: BlockEventHandler): void
    onBreak(blockId: string, callback: BlockEventHandler): void
    detectPattern(anchor: BlockPos, pattern: Record<string, string>): Promise<boolean>
  }
  creative: {
    registerTab(tab: CreativeTabDefinition): void
  }
  biomes: {
    register(biome: BiomeDefinition): void
  }
  dimensions: {
    register(dimension: DimensionDefinition): void
  }
  chat: {
    send(message: string): void
    command(command: string): void
    onMessage(listener: (message: ChatMessage) => void): void
  }
  settings: {
    define(schema: SettingsSchema): void
    get<T extends string | number | boolean>(key: string): T | undefined
    set<T extends string | number | boolean>(key: string, value: T): void
  }
  client: {
    fps(): number
    minecraftVersion(): string
    state(): GameState
    position(): ClientPosition
    facing(): string
  }
  player: {
    state(): PlayerState
    position(): ClientPosition
    blockPosition(): ClientPosition
    facing(): string
    health(): number
    dimension(): string
    setVelocity(x: number, y: number, z: number): void
    teleport(x: number, y: number, z: number): void
    heal(amount: number): void
    addEffect(effect: string, duration: number, amplifier?: number): void
  }
  world: {
    state(): WorldState
    dimension(): string
    biome(): string | undefined
    timeOfDay(): number
    surface(): MinimapBlock[]
    minimap(): MinimapBlock[]
    entities(): EntityState[]
    setBlock(x: number, y: number, z: number, blockId: string): void
    removeBlock(x: number, y: number, z: number): void
    getBlock(x: number, y: number, z: number): Promise<string>
    getBlocks(x1: number, y1: number, z1: number, x2: number, y2: number, z2: number): Promise<Array<{ x: number, y: number, z: number, id: string }>>
  }
  server: {
    state(): ServerState
    connected(): boolean
    address(): string | undefined
  }
}

export type ModResources = Record<string, string>

export type AkivCraftMod = {
  id: string
  name: string
  version?: string
  description?: string
  resources?: ModResources
  onEnable?(api: AkivCraftApi): void | Promise<void>
  onDisable?(api: AkivCraftApi): void | Promise<void>
  onKeyPress?(api: AkivCraftApi, key: string): void | Promise<void>
}
