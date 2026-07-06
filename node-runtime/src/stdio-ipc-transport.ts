import { Buffer } from "node:buffer"
import { buildBitmapFrame, type HudBitmapEntry } from "./binary-ipc-server.js"
import type { ItemDefinition, KeyBinding } from "./api.js"
import type { IpcServer } from "./ipc-server.js"
import type { MinecraftState, StateClient } from "./state-client.js"

const encode = (value: string | Buffer): string => Buffer.isBuffer(value)
  ? value.toString("base64")
  : Buffer.from(value, "utf8").toString("base64")

export class StdioIpcTransport {
  private timer: NodeJS.Timeout | undefined
  private keybindingTimer: NodeJS.Timeout | undefined
  private itemTimer: NodeJS.Timeout | undefined
  private nextQueryId = 1
  private pendingQueries = new Map<number, { resolve: (value: string) => void, reject: (error: Error) => void }>()
  private hudEnabled = true

  constructor(
    private readonly stateClient: StateClient,
    private readonly ipcServer: IpcServer,
    private readonly bitmaps: Map<string, HudBitmapEntry>,
    private readonly keybindings: Map<string, KeyBinding>,
    private readonly items: Map<string, ItemDefinition>,
  ) {
  }

  disableHud(): void {
    this.hudEnabled = false
    if (this.timer) {
      clearInterval(this.timer)
      this.timer = undefined
    }
  }

  start(): void {
    process.stdout.on("error", (error: NodeJS.ErrnoException) => {
      if (error.code !== "EPIPE") console.error("AkivCraft stdio stdout failed", error)
    })

    process.stdin.setEncoding("utf8")
    let buffered = ""
    process.stdin.on("data", (chunk) => {
      buffered += chunk
      let newline = buffered.indexOf("\n")
      while (newline >= 0) {
        const line = buffered.slice(0, newline).trim()
        buffered = buffered.slice(newline + 1)
        if (line) this.handleLine(line)
        newline = buffered.indexOf("\n")
      }
    })

    this.timer = setInterval(() => this.sendHudFrame(), 100)
    this.keybindingTimer = setInterval(() => this.sendKeybindings(), 1000)
    this.itemTimer = setInterval(() => this.sendItems(), 1000)
    this.sendKeybindings()
    this.sendItems()
    console.error("AkivCraft stdio IPC transport enabled")
  }

  private handleLine(line: string): void {
    try {
      const message = JSON.parse(line) as {
        type?: string, data?: string, action?: string | number, id?: string,
        key?: number, scancode?: number, modifiers?: number,
        player?: string, x?: number, y?: number, z?: number, event?: string,
        look?: { x: number, y: number, z: number },
        rayHit?: { x: number, y: number, z: number },
        chatType?: string, text?: string,
      }
      if (message.type === "state" && message.data) {
        this.stateClient.setState(JSON.parse(Buffer.from(message.data, "base64").toString("utf8")) as MinecraftState)
      } else if (message.type === "keyBindingEvent" && message.action && message.id) {
        this.ipcServer.handleKeyBindingEvent(`keyBindingEvent\t${message.action}\t${message.id}`)
      } else if (message.type === "keyEvent") {
        this.ipcServer.handleKeyEvent(`keyEvent\t${Number(message.action)}\t${Number(message.key)}\t${Number(message.scancode)}\t${Number(message.modifiers)}`)
      } else if (message.type === "itemUse" && message.id) {
        const look = message.look ?? { x: 0, y: 0, z: 0 }
        const hit = message.rayHit
        this.ipcServer.handleItemUse([
          "itemUse", message.id, message.player ?? "",
          message.x ?? 0, message.y ?? 0, message.z ?? 0, message.event ?? "use",
          look.x, look.y, look.z,
          hit ? 1 : 0, hit?.x ?? 0, hit?.y ?? 0, hit?.z ?? 0,
        ].join("\t"))
      } else if (message.type === "chatMessage") {
        this.ipcServer.handleChatMessage(`chatMessage\t${message.chatType ?? "system"}\t${message.text ?? ""}`)
      } else if (message.type === "blockEvent" && message.data) {
        const json = Buffer.from(message.data, "base64").toString("utf8")
        this.ipcServer.handleBlockEvent(`blockEvent\t${json}`)
      } else if (message.type === "queryResult" && message.id !== undefined && message.data) {
        const pending = this.pendingQueries.get(Number(message.id))
        if (pending) {
          this.pendingQueries.delete(Number(message.id))
          const json = Buffer.from(message.data, "base64").toString("utf8")
          pending.resolve(json)
        }
      }
    } catch (error) {
      console.error("AkivCraft stdio IPC parse failed", error)
    }
  }

  private sendHudFrame(): void {
    if (!this.hudEnabled) return
    this.send("hud", Buffer.from(this.ipcServer.hudLines().join("\n"), "utf8"))
    this.send("bitmap", buildBitmapFrame(this.bitmaps))
  }

  private sendKeybindings(): void {
    const bindings = [...this.keybindings.values()].map(({ id, category, name, defaultKey, description }) => ({
      id,
      category,
      name,
      defaultKey,
      description: description ?? "",
    }))
    this.send("keybindings", JSON.stringify({ bindings }))
  }

  private sendItems(): void {
    this.send("items", JSON.stringify({ items: [...this.items.values()] }))
  }

  async query(tsv: string): Promise<string> {
    return new Promise((resolve, reject) => {
      const id = this.nextQueryId++
      this.pendingQueries.set(id, { resolve, reject })
      const data = Buffer.from(tsv, "utf8").toString("base64")
      this.sendRaw(JSON.stringify({ type: "query", id, data }))

      // Timeout after 2 seconds
      setTimeout(() => {
        if (this.pendingQueries.has(id)) {
          this.pendingQueries.delete(id)
          reject(new Error("stdio query timeout"))
        }
      }, 2000)
    })
  }

  private sendRaw(line: string): void {
    process.stdout.write(`${line}\n`)
  }

  private send(type: string, payload: string | Buffer): void {
    this.sendRaw(JSON.stringify({ type, data: encode(payload) }))
  }
}
