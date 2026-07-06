import net from "node:net"
import type { BlockEventContext, BlockEventType, ChatMessage, HudPrimitive, HudTextOptions, ItemUseContext, KeyBinding } from "./api.js"

export type HudEntry =
  | { kind: "text", value: () => string, options: HudTextOptions }
  | { kind: "canvas", render: () => HudPrimitive[] }

export type ItemUseHandler = (ctx: ItemUseContext) => void | Promise<void>
export type BlockEventHandler = (ctx: BlockEventContext) => void | Promise<void>

const encode = (value: string): string => Buffer.from(value, "utf8").toString("base64")

const parseColor = (value: string | undefined, fallback: number): number => {
  if (!value) return fallback
  const normalized = value.startsWith("#") ? value.slice(1) : value
  const color = Number.parseInt(normalized, 16)
  if (!Number.isFinite(color)) return fallback
  if (normalized.length <= 6) return 0xff000000 | color
  return color
}

export class IpcServer {
  private server: net.Server | undefined

  constructor(
    private readonly port: number,
    private readonly hud: Map<string, HudEntry>,
    private readonly keybindings: Map<string, KeyBinding>,
    private readonly keyPressListeners: Set<(key: string) => void>,
    private readonly keyReleaseListeners: Set<(key: string) => void>,
    private readonly itemUseHandlers: Map<string, ItemUseHandler>,
    private readonly blockEventListeners: Map<BlockEventType, Set<BlockEventHandler>>,
    private readonly chatListeners: Set<(message: ChatMessage) => void>,
  ) {
  }

  start(): void {
    this.server = net.createServer((socket) => {
      socket.setEncoding("utf8")
      socket.on("error", () => {
        socket.destroy()
      })
      socket.once("data", (data) => {
        const request = data.toString().trim()
        if (request === "getHud") {
          this.writeHud(socket)
        } else if (request === "getKeybindings") {
          this.writeKeybindings(socket)
        } else if (request.startsWith("keyEvent\t")) {
          this.handleKeyEvent(request)
          socket.end("OK\n")
        } else if (request.startsWith("keyBindingEvent\t")) {
          this.handleKeyBindingEvent(request)
          socket.end("OK\n")
        } else if (request.startsWith("itemUse\t")) {
          this.handleItemUse(request)
          socket.end("OK\n")
        } else if (request.startsWith("blockEvent\t")) {
          this.handleBlockEvent(request)
          socket.end("OK\n")
        } else if (request.startsWith("chatMessage\t")) {
          this.handleChatMessage(request)
          socket.end("OK\n")
        } else {
          socket.end("ERR unknown request\n")
        }
      })
    })
    this.server.on("error", (error: NodeJS.ErrnoException) => {
      if (error.code === "EADDRINUSE") {
        console.error(`AkivCraft Node IPC port ${this.port} is already in use; HUD IPC disabled for this runtime.`)
        return
      }
      console.error("AkivCraft Node IPC failed", error)
    })
    this.server.listen(this.port, "127.0.0.1", () => {
      console.log(`AkivCraft Node IPC listening on 127.0.0.1:${this.port}`)
    })
  }

  private writeKeybindings(socket: net.Socket): void {
    const bindings = [...this.keybindings.values()].map(({ id, category, name, defaultKey, description }) => ({
      id,
      category,
      name,
      defaultKey,
      description: description ?? "",
    }))
    socket.end(`${JSON.stringify({ bindings })}\n`)
  }

  handleKeyEvent(request: string): void {
    const [, actionText, keyText, scancodeText, modifiersText] = request.split("\t")
    const action = Number(actionText)
    const key = Number(keyText)
    const keyName = `KEY_${key}`
    const isPress = action === 1
    const isRelease = action === 0

    if (isPress) {
      for (const listener of this.keyPressListeners) listener(keyName)
    } else if (isRelease) {
      for (const listener of this.keyReleaseListeners) listener(keyName)
    }

    void scancodeText
    void modifiersText
  }

  handleKeyBindingEvent(request: string): void {
    const [, action, id] = request.split("\t")
    const binding = this.keybindings.get(id)
    if (!binding) return

    try {
      if (action === "press") {
        for (const listener of this.keyPressListeners) listener(id)
        void binding.onPress?.()
      }

      if (action === "release") {
        for (const listener of this.keyReleaseListeners) listener(id)
        void binding.onRelease?.()
      }
    } catch (error) {
      console.error(`AkivCraft keybinding '${binding.id}' failed`, error)
    }
  }

  handleChatMessage(request: string): void {
    const parts = request.split("\t")
    const type = (parts[1] ?? "system") as ChatMessage["type"]
    const text = parts[2] ?? ""

    const msg: ChatMessage = { type, text }
    for (const listener of this.chatListeners) {
      try {
        listener(msg)
      } catch (error) {
        console.error("AkivCraft chat listener failed", error)
      }
    }
  }

  handleItemUse(request: string): void {    const parts = request.split("\t")
    const itemId = parts[1] ?? ""
    const player = parts[2] ?? ""
    const x = Number(parts[3]) || 0
    const y = Number(parts[4]) || 0
    const z = Number(parts[5]) || 0
    const event = parts[6] ?? "use"
    const lookX = Number(parts[7]) || 0
    const lookY = Number(parts[8]) || 0
    const lookZ = Number(parts[9]) || 0
    const hitFlag = parts[10] === "1"
    const hitX = Number(parts[11]) || 0
    const hitY = Number(parts[12]) || 0
    const hitZ = Number(parts[13]) || 0

    const handler = this.itemUseHandlers.get(itemId)
    if (!handler) return

    const ctx: ItemUseContext = {
      itemId,
      player,
      x,
      y,
      z,
      event,
      look: { x: lookX, y: lookY, z: lookZ },
      rayHit: hitFlag ? { x: hitX, y: hitY, z: hitZ } : null,
    }

    try {
      void handler(ctx)
    } catch (error) {
      console.error(`AkivCraft item use handler for '${itemId}' failed`, error)
    }
  }

  handleBlockEvent(request: string): void {
    const json = request.slice("blockEvent\t".length)
    let ctx: BlockEventContext
    try {
      ctx = JSON.parse(json) as BlockEventContext
    } catch (error) {
      console.error("AkivCraft block event parse failed", error)
      return
    }

    const listeners = this.blockEventListeners.get(ctx.type)
    if (!listeners) return

    for (const listener of listeners) {
      try {
        void listener(ctx)
      } catch (error) {
        console.error(`AkivCraft block event handler for '${ctx.type}' failed`, error)
      }
    }
  }

  private writeHud(socket: net.Socket): void {
    try {
      for (const [id, entry] of this.hud.entries()) {
        for (const primitive of this.primitives(id, entry)) {
          if (socket.destroyed || !socket.writable) return
          socket.write(primitive.join("\t"))
          socket.write("\n")
        }
      }

      if (!socket.destroyed && socket.writable) socket.end(".\n")
    } catch {
      socket.destroy()
    }
  }

  hudLines(): string[] {
    const lines: string[] = []
    for (const [id, entry] of this.hud.entries()) {
      for (const primitive of this.primitives(id, entry)) {
        lines.push(primitive.join("\t"))
      }
    }
    return lines
  }

  private primitives(id: string, entry: HudEntry): Array<Array<string | number>> {
    if (entry.kind === "text") {
      let text = ""
      try {
        text = entry.value()
      } catch (error) {
        text = `[${id} failed: ${error instanceof Error ? error.message : "unknown error"}]`
      }

      const options = entry.options
      return [[
        "text",
        encode(id),
        encode(text),
        Math.round(options.x),
        Math.round(options.y),
        0,
        0,
        parseColor(options.color, 0xffffffff),
        parseColor(options.background, 0x00000000),
        options.shadow === false ? 0 : 1,
      ]]
    }

    try {
      return entry.render().map((primitive, index) => {
        if (primitive.kind === "rect") {
          return [
            "rect",
            encode(`${id}:${index}`),
            "",
            Math.round(primitive.x),
            Math.round(primitive.y),
            Math.round(primitive.width),
            Math.round(primitive.height),
            parseColor(primitive.color, 0xffffffff),
            0,
            0,
          ]
        }

        if (primitive.kind === "bitmapRle") {
          return [
            "bitmapRle",
            encode(`${id}:${index}`),
            encode(`${primitive.palette.map((color) => parseColor(color, 0xffffffff)).join(",")}|${primitive.runs}`),
            Math.round(primitive.x),
            Math.round(primitive.y),
            Math.round(primitive.width),
            Math.round(primitive.height),
            0,
            0,
            0,
          ]
        }

        if (primitive.kind === "sprite") {
          return [
            "sprite",
            encode(`${id}:${index}`),
            encode(primitive.texture),
            Math.round(primitive.x),
            Math.round(primitive.y),
            Math.round(primitive.width),
            Math.round(primitive.height),
            Math.round(primitive.u ?? 0),
            Math.round(primitive.v ?? 0),
          ]
        }

        if (primitive.kind === "text") {
          return [
            "text",
            encode(`${id}:${index}`),
            encode(primitive.text),
            Math.round(primitive.x),
            Math.round(primitive.y),
            0,
            0,
            parseColor(primitive.color, 0xffffffff),
            0,
            primitive.shadow === false ? 0 : 1,
          ]
        }

        return [
          "rect",
          encode(`${id}:${index}`),
          "",
          Math.round((primitive as { x: number }).x),
          Math.round((primitive as { y: number }).y),
          Math.round((primitive as { width: number }).width),
          Math.round((primitive as { height: number }).height),
          parseColor((primitive as { color: string }).color, 0xffffffff),
          0,
          0,
        ]
      })
    } catch (error) {
      return [["text", encode(id), encode(`[${id} failed: ${error instanceof Error ? error.message : "unknown error"}]`), 8, 8, 0, 0, 0xffff5555, 0x99000000, 1]]
    }
  }
}
