import { readFile, readdir, writeFile, copyFile, mkdir, rm } from "node:fs/promises"
import { pathToFileURL } from "node:url"
import path from "node:path"
import type { AkivCraftApi, AkivCraftMod, BiomeDefinition, BlockEventHandler, BlockEventType, ChatMessage, GameState, ItemUseContext, PlayerState, ServerState, SettingsSchema, WorldState } from "./api.js"
import { buildBitmapFrame, type HudBitmapEntry } from "./binary-ipc-server.js"
import { BinaryIpcServer } from "./binary-ipc-server.js"
import type { HudEntry, ItemUseHandler } from "./ipc-server.js"
import { IpcServer } from "./ipc-server.js"
import { PlayerActionClient } from "./player-action-client.js"
import { StateClient } from "./state-client.js"
import { StdioIpcTransport } from "./stdio-ipc-transport.js"
import { UnixSocketClient } from "./unix-socket-client.js"
import { UdpBitmapSender } from "./udp-bitmap-sender.js"

type RuntimeOptions = {
  modsDirectory: string
  minecraftVersion: string
  statePort: number
  port: number
  binaryPort: number
  udpPort: number
  stdioIpc: boolean
  unixSocket: string
}

export class AkivCraftRuntime {
  private readonly mods = new Map<string, AkivCraftMod>()
  private readonly settings = new Map<string, string | number | boolean>()
  private readonly hud = new Map<string, HudEntry>()
  private readonly bitmaps = new Map<string, HudBitmapEntry>()
  private readonly keybindings = new Map<string, import("./api.js").KeyBinding>()
  private readonly items = new Map<string, import("./api.js").ItemDefinition>()
  private readonly creativeTabs = new Map<string, import("./api.js").CreativeTabDefinition>()
  private readonly biomes = new Map<string, BiomeDefinition>()
  private readonly biomeOwners = new Map<string, string>()
  private readonly dimensions = new Map<string, import("./api.js").DimensionDefinition>()
  private readonly recipes = new Map<string, import("./api.js").RecipeDefinition>()
  private readonly blocks = new Map<string, import("./api.js").BlockDefinition>()
  private readonly entities = new Map<string, import("./api.js").EntityDefinition>()
  private readonly features = new Map<string, import("./api.js").FeatureDefinition>()
  private readonly carvers = new Map<string, import("./api.js").CarverDefinition>()
  private readonly keyPressListeners = new Set<(key: string) => void>()
  private readonly keyReleaseListeners = new Set<(key: string) => void>()
  private readonly itemUseHandlers = new Map<string, ItemUseHandler>()
  private readonly blockEventListeners = new Map<BlockEventType, Set<BlockEventHandler>>()
  private readonly chatListeners = new Set<(message: ChatMessage) => void>()
  private readonly stateClient: StateClient
  private readonly ipcServer: IpcServer
  private readonly binaryIpcServer: BinaryIpcServer
  private readonly udpBitmapSender: UdpBitmapSender
  private readonly stdioIpcTransport: StdioIpcTransport
  private readonly unixSocketClient: UnixSocketClient | null
  private readonly playerActionClient: PlayerActionClient

  constructor(private readonly options: RuntimeOptions) {
    this.stateClient = new StateClient(options.statePort, options.minecraftVersion)
    this.ipcServer = new IpcServer(options.port, this.hud, this.keybindings, this.keyPressListeners, this.keyReleaseListeners, this.itemUseHandlers, this.blockEventListeners, this.chatListeners)
    this.binaryIpcServer = new BinaryIpcServer(options.binaryPort, this.bitmaps)
    this.udpBitmapSender = new UdpBitmapSender(options.udpPort, this.bitmaps)
    this.stdioIpcTransport = new StdioIpcTransport(this.stateClient, this.ipcServer, this.bitmaps, this.keybindings, this.items)
    this.unixSocketClient = options.unixSocket ? new UnixSocketClient(options.unixSocket) : null
    this.playerActionClient = new PlayerActionClient(options.statePort, options.stdioIpc, (tsv) => {
      if (!this.stdioIpcTransport) {
        return Promise.reject(new Error("stdio IPC transport not available"))
      }
      return this.stdioIpcTransport.query(tsv)
    })
  }

  async start(): Promise<void> {
    if (this.options.stdioIpc) {
      this.stdioIpcTransport.start()
    } else {
      this.stateClient.start()
      this.ipcServer.start()
      this.binaryIpcServer.start()
      this.udpBitmapSender.start()
    }
    if (this.unixSocketClient) {
      this.stdioIpcTransport.disableHud()
      this.unixSocketClient.connect().catch((error: unknown) => {
        console.error("AkivCraft Unix socket client failed to connect", error)
      })
      setInterval(() => {
        try {
          const lines = this.ipcServer.hudLines()
          const bitmaps = buildBitmapFrame(this.bitmaps)
          this.unixSocketClient?.sendHud(lines.join("\n"), bitmaps)
        } catch (error) {
          console.error("AkivCraft UDS HUD send failed", error)
        }
      }, 100)
    }
    await this.loadMods()
    await this.writeLoadedModsManifest()
    for (const mod of this.mods.values()) {
      await mod.onEnable?.(this.createApi(mod))
    }
    await this.writeLoadedItemsManifest()
    await this.writeLoadedRecipesManifest()
    await this.writeLoadedCreativeTabsManifest()
    await this.writeLoadedBiomesManifest()
    await this.writeLoadedBlocksManifest()
    await this.writeLoadedEntitiesManifest()
    await this.writeLoadedFeaturesManifest()
    await this.writeLoadedCarversManifest()
    await this.writeLoadedDimensionsManifest()
    await this.generateResourcePacks()
  }

  private async writeLoadedItemsManifest(): Promise<void> {
    const items = [...this.items.values()]
    await writeFile(path.join(this.options.modsDirectory, "loaded-items.json"), `${JSON.stringify({ items }, null, 2)}\n`).catch(() => undefined)
  }

  private async writeLoadedCreativeTabsManifest(): Promise<void> {
    const tabs = [...this.creativeTabs.values()]
    await writeFile(path.join(this.options.modsDirectory, "loaded-creative-tabs.json"), `${JSON.stringify({ tabs }, null, 2)}\n`).catch(() => undefined)
  }

  private async writeLoadedBiomesManifest(): Promise<void> {
    const biomes = [...this.biomes.values()].map((biome) => ({ ...biome, source: biome.source ?? "overworld", noise: normalizeBiomeNoise(biome) }))
    await writeFile(path.join(this.options.modsDirectory, "loaded-biomes.json"), `${JSON.stringify({ biomes }, null, 2)}\n`).catch(() => undefined)
  }

  private async writeLoadedDimensionsManifest(): Promise<void> {
    const dimensions = [...this.dimensions.values()]
    if (dimensions.length === 0) return
    await writeFile(
      path.join(this.options.modsDirectory, "loaded-dimensions.json"),
      `${JSON.stringify({ dimensions }, null, 2)}\n`,
    ).catch(() => undefined)
  }

  private async loadMods(): Promise<void> {
    const entries = await readdir(this.options.modsDirectory, { withFileTypes: true }).catch(() => [])

    for (const entry of entries) {
      if (!entry.isDirectory()) continue

      const modEntry = path.join(this.options.modsDirectory, entry.name, "index.js")
      const metadata = await this.readModMetadata(path.join(this.options.modsDirectory, entry.name, "mod.json"))
      const module = await import(pathToFileURL(modEntry).href).catch((error: unknown) => {
        console.error(`Failed to load mod ${entry.name}`, error)
        return undefined
      })

      const mod = module?.default as AkivCraftMod | undefined
      if (!mod?.id || !mod.name) continue

      const merged = { ...metadata, ...mod }
      this.mods.set(merged.id, merged)
      console.log(`Loaded AkivCraft mod: ${merged.name} (${merged.id})`)
    }
  }

  private async readModMetadata(filePath: string): Promise<Partial<AkivCraftMod>> {
    const raw = await readFile(filePath, "utf8").catch(() => undefined)
    if (!raw) return {}

    try {
      return JSON.parse(raw) as Partial<AkivCraftMod>
    } catch (error) {
      console.error(`Failed to parse ${filePath}`, error)
      return {}
    }
  }

  private addFilteredBlockListener(type: BlockEventType, listener: BlockEventHandler): void {
    let listeners = this.blockEventListeners.get(type)
    if (!listeners) {
      listeners = new Set()
      this.blockEventListeners.set(type, listeners)
    }
    listeners.add(listener)
  }

  private async writeLoadedModsManifest(): Promise<void> {
    const mods = [...this.mods.values()].map((mod) => ({
      id: mod.id,
      name: mod.name,
      version: mod.version ?? "0.1.0",
      description: mod.description ?? "",
      enabled: true,
    }))

    await writeFile(path.join(this.options.modsDirectory, "loaded-mods.json"), `${JSON.stringify({ mods }, null, 2)}\n`).catch(() => undefined)
  }

  private async writeLoadedRecipesManifest(): Promise<void> {
    const recipes = [...this.recipes.values()]
    if (recipes.length === 0) return
    await writeFile(
      path.join(this.options.modsDirectory, "loaded-recipes.json"),
      `${JSON.stringify({ recipes }, null, 2)}\n`,
    ).catch(() => undefined)
  }

  private async writeLoadedBlocksManifest(): Promise<void> {
    const blocks = [...this.blocks.values()]
    if (blocks.length === 0) return
    await writeFile(
      path.join(this.options.modsDirectory, "loaded-blocks.json"),
      `${JSON.stringify({ blocks }, null, 2)}\n`,
    ).catch(() => undefined)
  }

  private async writeLoadedEntitiesManifest(): Promise<void> {
    const entities = [...this.entities.values()]
    if (entities.length === 0) return
    await writeFile(
      path.join(this.options.modsDirectory, "loaded-entities.json"),
      `${JSON.stringify({ entities }, null, 2)}\n`,
    ).catch(() => undefined)
  }

  private async writeLoadedFeaturesManifest(): Promise<void> {
    const features = [...this.features.values()]
    if (features.length === 0) return
    await writeFile(
      path.join(this.options.modsDirectory, "loaded-features.json"),
      `${JSON.stringify({ features }, null, 2)}\n`,
    ).catch(() => undefined)
  }

  private async writeLoadedCarversManifest(): Promise<void> {
    const carvers = [...this.carvers.values()]
    if (carvers.length === 0) return
    await writeFile(
      path.join(this.options.modsDirectory, "loaded-carvers.json"),
      `${JSON.stringify({ carvers }, null, 2)}\n`,
    ).catch(() => undefined)
  }

  private async generateResourcePacks(): Promise<void> {
    const outputDir = path.resolve(this.options.modsDirectory, "..", "generated-resourcepacks")
    await rm(outputDir, { recursive: true, force: true }).catch(() => undefined)
    await mkdir(outputDir, { recursive: true })

    for (const mod of this.mods.values()) {
      if (!mod.resources) continue

      const packDir = path.join(outputDir, `akivcraft.${mod.id}`)
      const modDir = path.join(this.options.modsDirectory, mod.id)

      await mkdir(packDir, { recursive: true })
      let hasPackMcmeta = false

      for (const [targetPath, sourcePath] of Object.entries(mod.resources ?? {})) {
        const dest = path.join(packDir, targetPath)
        await mkdir(path.dirname(dest), { recursive: true })

        if (targetPath === "pack.mcmeta") {
          hasPackMcmeta = true
        }

        const resolved = sourcePath.startsWith(".")
          ? path.resolve(modDir, sourcePath)
          : path.resolve(modDir, sourcePath)

        await copyFile(resolved, dest).catch((error: unknown) => {
          console.error(`AkivCraft resource '${targetPath}' for mod '${mod.id}' not found: ${resolved}`, error)
        })
      }

      if (!hasPackMcmeta) {
        const mcmeta = { pack: { min_format: [84, 0], max_format: 101, description: `${mod.name} resources` } }
        await writeFile(path.join(packDir, "pack.mcmeta"), `${JSON.stringify(mcmeta, null, 2)}\n`)
      }

      console.log(`AkivCraft generated resource pack for ${mod.id} at ${packDir}`)
    }

    if (this.dimensions.size > 0) {
      console.warn("AkivCraft: dimensions are now registered directly in Java registries; generated dimension data packs are no longer used")
    }
  }

  private createApi(mod: AkivCraftMod): AkivCraftApi {
    const gameState = (): GameState => this.stateClient.state().client
    const playerState = (): PlayerState => this.stateClient.state().player ?? {
      name: "Player",
      position: { x: 0, y: 64, z: 0 },
      blockPosition: { x: 0, y: 64, z: 0 },
      velocity: { x: 0, y: 0, z: 0 },
      yaw: 0,
      pitch: 0,
      facing: "N",
      health: 20,
      maxHealth: 20,
      food: 20,
      saturation: 5,
      experienceLevel: 0,
      dimension: "unknown",
      biome: undefined,
    }
    const worldState = (): WorldState => this.stateClient.state().world
    const serverState = (): ServerState => this.stateClient.state().server

    return {
      hud: {
        addText: (id, value, options) => {
          this.hud.set(id, { kind: "text", value, options })
        },
        addCanvas: (id, render) => {
          this.hud.set(id, { kind: "canvas", render })
        },
        addBitmap: (id, render) => {
          this.bitmaps.set(id, { render })
        },
        remove: (id) => {
          this.hud.delete(id)
          this.bitmaps.delete(id)
        },
      },
      keys: {
        register: (binding) => {
          this.keybindings.set(binding.id, binding)
          console.log(`Registered AkivCraft keybinding: ${binding.category} / ${binding.name} (${binding.defaultKey})`)
        },
        onPress: (listener) => {
          this.keyPressListeners.add(listener)
        },
        onRelease: (listener) => {
          this.keyReleaseListeners.add(listener)
        },
      },
      items: {
        register: (item) => {
          this.items.set(item.id, item)
          console.log(`Registered AkivCraft item definition: ${item.id}`)
        },
        onUse: (itemId, callback) => {
          this.itemUseHandlers.set(itemId, callback)
          console.log(`Registered AkivCraft item use handler: ${itemId}`)
        },
      },
      recipes: {
        register: (recipe) => {
          this.recipes.set(recipe.id, recipe)
          console.log(`Registered AkivCraft recipe definition: ${recipe.id}`)
        },
      },
      events: {
        on: (type, callback) => {
          let listeners = this.blockEventListeners.get(type)
          if (!listeners) {
            listeners = new Set()
            this.blockEventListeners.set(type, listeners)
          }
          listeners.add(callback)
        },
      },
      blocks: {
        register: (block) => {
          this.blocks.set(block.id, block)
          console.log(`Registered AkivCraft block definition: ${block.id}`)
        },
        onUse: (blockId, callback) => this.addFilteredBlockListener("use_block", (ctx) => {
          if (ctx.targetBlock === blockId) return callback(ctx)
        }),
        onPlace: (blockId, callback) => this.addFilteredBlockListener("place_block", (ctx) => {
          if (ctx.placedBlock === blockId) return callback(ctx)
        }),
        onBreak: (blockId, callback) => this.addFilteredBlockListener("break_block", (ctx) => {
          if (ctx.brokenBlock === blockId) return callback(ctx)
        }),
        detectPattern: async (anchor, pattern) => {
          const offsets = Object.entries(pattern).map(([key, expectedId]) => {
            const [dx, dy, dz] = key.split(",").map(Number)
            return { dx, dy, dz, expectedId }
          })
          if (offsets.length === 0) return true
          const minX = Math.min(...offsets.map((o) => o.dx))
          const maxX = Math.max(...offsets.map((o) => o.dx))
          const minY = Math.min(...offsets.map((o) => o.dy))
          const maxY = Math.max(...offsets.map((o) => o.dy))
          const minZ = Math.min(...offsets.map((o) => o.dz))
          const maxZ = Math.max(...offsets.map((o) => o.dz))
          const blocks = await this.playerActionClient.getBlocks(
            anchor.x + minX, anchor.y + minY, anchor.z + minZ,
            anchor.x + maxX, anchor.y + maxY, anchor.z + maxZ,
          )
          const blockMap = new Map<string, string>()
          for (const b of blocks) {
            blockMap.set(`${b.x},${b.y},${b.z}`, b.id)
          }
          for (const { dx, dy, dz, expectedId } of offsets) {
            const key = `${anchor.x + dx},${anchor.y + dy},${anchor.z + dz}`
            const actualId = blockMap.get(key) ?? "minecraft:air"
            if (actualId !== expectedId) return false
          }
          return true
        },
      },
      creative: {
        registerTab: (tab) => {
          this.creativeTabs.set(tab.id, tab)
          console.log(`Registered AkivCraft creative tab: ${tab.name} (${tab.id})`)
        },
      },
      biomes: {
        register: (biome) => {
          this.biomes.set(biome.id, biome)
          this.biomeOwners.set(biome.id, mod.id)
          console.log(`Registered AkivCraft biome definition: ${biome.id}`)
        },
      },
      features: {
        register: (feature) => {
          this.features.set(feature.id, feature)
          console.log(`Registered AkivCraft feature definition: ${feature.id}`)
        },
      },
      carvers: {
        register: (carver) => {
          this.carvers.set(carver.id, carver)
          console.log(`Registered AkivCraft carver definition: ${carver.id}`)
        },
      },
      dimensions: {
        register: (dimension) => {
          this.dimensions.set(dimension.id, dimension)
          console.log(`Registered AkivCraft dimension: ${dimension.id}`)
        },
      },
      entities: {
        register: (entity) => {
          this.entities.set(entity.id, entity)
          console.log(`Registered AkivCraft entity definition: ${entity.id}`)
        },
      },
      chat: {
        send: (message) => this.playerActionClient.sendChat(message),
        command: (cmd) => this.playerActionClient.sendCommand(cmd),
        onMessage: (listener) => {
          this.chatListeners.add(listener)
        },
      },
      settings: {
        define: (schema: SettingsSchema) => {
          for (const [key, value] of Object.entries(schema)) {
            if (!this.settings.has(key)) this.settings.set(key, value)
          }
        },
        get: (key) => this.settings.get(key) as never,
        set: (key, value) => {
          this.settings.set(key, value)
        },
      },
      client: {
        fps: () => gameState().fps,
        minecraftVersion: () => gameState().minecraftVersion,
        state: gameState,
        position: () => playerState().position,
        facing: () => playerState().facing,
      },
      player: {
        state: playerState,
        position: () => playerState().position,
        blockPosition: () => playerState().blockPosition,
        facing: () => playerState().facing,
        health: () => playerState().health,
        dimension: () => playerState().dimension,
        setVelocity: (x, y, z) => this.playerActionClient.setVelocity(x, y, z),
        teleport: (x, y, z) => this.playerActionClient.teleport(x, y, z),
        heal: (amount) => this.playerActionClient.heal(amount),
        addEffect: (effect, duration, amplifier = 0) => this.playerActionClient.addEffect(effect, duration, amplifier),
      },
      world: {
        state: worldState,
        dimension: () => worldState().dimension,
        biome: () => worldState().biome,
        timeOfDay: () => worldState().timeOfDay,
        surface: () => worldState().surface?.blocks ?? worldState().minimap?.blocks ?? [],
        minimap: () => worldState().surface?.blocks ?? worldState().minimap?.blocks ?? [],
        entities: () => worldState().entities ?? [],
        setBlock: (x, y, z, blockId) => this.playerActionClient.setBlock(x, y, z, blockId),
        removeBlock: (x, y, z) => this.playerActionClient.removeBlock(x, y, z),
        getBlock: (x, y, z) => this.playerActionClient.getBlock(x, y, z),
        getBlocks: (x1, y1, z1, x2, y2, z2) => this.playerActionClient.getBlocks(x1, y1, z1, x2, y2, z2),
      },
      server: {
        state: serverState,
        connected: () => serverState().connected,
        address: () => serverState().address,
      },
    }
  }
}

function normalizeBiomeNoise(biome: BiomeDefinition): Record<string, [number, number] | number> {
  return {
    temperature: range(biome.noise.temperature),
    humidity: range(biome.noise.humidity),
    continentalness: range(biome.noise.continentalness),
    erosion: range(biome.noise.erosion),
    depth: range(biome.noise.depth ?? [-1, 1]),
    weirdness: range(biome.noise.weirdness),
    offset: biome.noise.offset ?? 0,
  }
}

function range(value: number | [number, number]): [number, number] {
  return Array.isArray(value) ? value : [value, value]
}
