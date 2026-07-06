import net from "node:net"
import type { HudBitmap } from "./api.js"

export type HudBitmapEntry = {
  render: () => HudBitmap
  lastBitmap?: HudBitmap
  lastRenderedAt?: number
}

const parseColor = (value: string | undefined, fallback: number): number => {
  if (!value) return fallback
  const normalized = value.startsWith("#") ? value.slice(1) : value
  const color = Number.parseInt(normalized, 16)
  if (!Number.isFinite(color)) return fallback
  if (normalized.length <= 6) return 0xff000000 | color
  return color >>> 0
}

const u8 = (value: number): Buffer => {
  const buffer = Buffer.allocUnsafe(1)
  buffer.writeUInt8(value, 0)
  return buffer
}

const u16 = (value: number): Buffer => {
  const buffer = Buffer.allocUnsafe(2)
  buffer.writeUInt16BE(value, 0)
  return buffer
}

const i16 = (value: number): Buffer => {
  const buffer = Buffer.allocUnsafe(2)
  buffer.writeInt16BE(value, 0)
  return buffer
}

const u32 = (value: number): Buffer => {
  const buffer = Buffer.allocUnsafe(4)
  buffer.writeUInt32BE(value >>> 0, 0)
  return buffer
}

export function buildBitmapFrame(bitmaps: Map<string, HudBitmapEntry>): Buffer {
  const now = Date.now()
  const entries = [...bitmaps.entries()].flatMap(([id, entry]) => {
    try {
      if (entry.lastBitmap && entry.lastRenderedAt !== undefined) {
        const fps = Math.max(1, Math.min(60, entry.lastBitmap.fps ?? 10))
        if (now - entry.lastRenderedAt < 1000 / fps) return [[id, entry.lastBitmap] as const]
      }

      const bitmap = entry.render()
      entry.lastBitmap = bitmap
      entry.lastRenderedAt = now
      return [[id, bitmap] as const]
    } catch (error) {
      console.error(`AkivCraft bitmap '${id}' failed`, error)
      return entry.lastBitmap ? [[id, entry.lastBitmap] as const] : []
    }
  })

  const chunks: Buffer[] = [Buffer.from("AKBM", "ascii"), u8(1), u16(entries.length)]
  for (const [id, bitmap] of entries) {
    const idBuffer = Buffer.from(id, "utf8")
    const pixels = Buffer.from(bitmap.pixels.buffer, bitmap.pixels.byteOffset, bitmap.pixels.byteLength)
    chunks.push(u16(idBuffer.byteLength), idBuffer)
    chunks.push(i16(Math.round(bitmap.x)), i16(Math.round(bitmap.y)))
    chunks.push(u16(Math.round(bitmap.width)), u16(Math.round(bitmap.height)))
    chunks.push(u8(Math.round((bitmap.scale ?? 1) * 10)))
    chunks.push(u16(bitmap.palette.length))
    for (const color of bitmap.palette) chunks.push(u32(parseColor(color, 0xffffffff)))
    chunks.push(u32(pixels.byteLength), pixels)
  }

  return Buffer.concat(chunks)
}

export class BinaryIpcServer {
  private server: net.Server | undefined

  constructor(private readonly port: number, private readonly bitmaps: Map<string, HudBitmapEntry>) {
  }

  start(): void {
    this.server = net.createServer((socket) => {
      socket.on("error", () => socket.destroy())
      socket.once("data", () => this.writeBitmaps(socket))
    })
    this.server.on("error", (error: NodeJS.ErrnoException) => {
      if (error.code === "EADDRINUSE") {
        console.error(`AkivCraft binary IPC port ${this.port} is already in use; binary HUD disabled for this runtime.`)
        return
      }
      console.error("AkivCraft binary IPC failed", error)
    })
    this.server.listen(this.port, "127.0.0.1", () => {
      console.log(`AkivCraft binary IPC listening on 127.0.0.1:${this.port}`)
    })
  }

  private writeBitmaps(socket: net.Socket): void {
    try {
      if (!socket.destroyed && socket.writable) socket.end(buildBitmapFrame(this.bitmaps))
    } catch {
      socket.destroy()
    }
  }
}
