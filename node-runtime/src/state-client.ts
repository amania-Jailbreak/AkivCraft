import net from "node:net"
import type { GameState, PlayerState, ServerState, WorldState } from "./api.js"

export type MinecraftState = {
  client: GameState
  player: PlayerState | null
  world: WorldState
  server: ServerState
}

const fallbackState = (minecraftVersion: string): MinecraftState => ({
  client: {
    minecraftVersion,
    fps: 0,
    screen: undefined,
    paused: false,
  },
  player: null,
  world: {
    dimension: "unknown",
    biome: undefined,
    timeOfDay: 0,
    day: 0,
    weather: "clear",
    lightLevel: undefined,
    minimap: {
      radius: 0,
      blocks: [],
    },
    surface: {
      radius: 0,
      blocks: [],
    },
    entities: [],
  },
  server: {
    connected: false,
    address: undefined,
    brand: undefined,
    pingMs: undefined,
  },
})

export class StateClient {
  private current: MinecraftState
  private timer: NodeJS.Timeout | undefined

  constructor(private readonly port: number, minecraftVersion: string) {
    this.current = fallbackState(minecraftVersion)
  }

  start(): void {
    this.timer = setInterval(() => {
      void this.refresh()
    }, 50)
    this.timer.unref()
    void this.refresh()
  }

  state(): MinecraftState {
    return this.current
  }

  setState(state: MinecraftState): void {
    this.current = state
  }

  private async refresh(): Promise<void> {
    try {
      this.current = await this.requestState()
    } catch {
      // Minecraft may not have started its state IPC yet; keep the last known state.
    }
  }

  private requestState(): Promise<MinecraftState> {
    return new Promise((resolve, reject) => {
      const socket = net.createConnection({ host: "127.0.0.1", port: this.port })
      let raw = ""

      socket.setEncoding("utf8")
      socket.setTimeout(250)
      socket.on("connect", () => {
        socket.write("getState\n")
      })
      socket.on("data", (chunk) => {
        raw += chunk
      })
      socket.on("end", () => {
        try {
          resolve(JSON.parse(raw) as MinecraftState)
        } catch (error) {
          reject(error)
        }
      })
      socket.on("timeout", () => {
        socket.destroy(new Error("state IPC timeout"))
      })
      socket.on("error", reject)
    })
  }
}
