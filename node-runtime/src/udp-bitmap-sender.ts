import dgram from "node:dgram"
import type { HudBitmapEntry } from "./binary-ipc-server.js"
import { buildBitmapFrame } from "./binary-ipc-server.js"

const MAX_PAYLOAD = 1150

const u16 = (value: number): Buffer => {
  const buffer = Buffer.allocUnsafe(2)
  buffer.writeUInt16BE(value, 0)
  return buffer
}

const u32 = (value: number): Buffer => {
  const buffer = Buffer.allocUnsafe(4)
  buffer.writeUInt32BE(value >>> 0, 0)
  return buffer
}

export class UdpBitmapSender {
  private socket: dgram.Socket | undefined
  private timer: NodeJS.Timeout | undefined
  private frameId = 0

  constructor(private readonly port: number, private readonly bitmaps: Map<string, HudBitmapEntry>) {
  }

  start(): void {
    this.socket = dgram.createSocket("udp4")
    this.socket.on("error", (error) => {
      console.error("AkivCraft UDP bitmap sender failed", error)
      this.socket?.close()
      this.socket = undefined
    })
    this.timer = setInterval(() => this.sendFrame(), 100)
    this.timer.unref()
    console.log(`AkivCraft UDP bitmap sender targeting 127.0.0.1:${this.port}`)
  }

  private sendFrame(): void {
    if (!this.socket || this.bitmaps.size === 0) return

    let frame: Buffer
    try {
      frame = buildBitmapFrame(this.bitmaps)
    } catch (error) {
      console.error("AkivCraft failed to build UDP bitmap frame", error)
      return
    }

    const frameId = this.frameId = (this.frameId + 1) >>> 0
    const chunkCount = Math.ceil(frame.byteLength / MAX_PAYLOAD)
    if (chunkCount > 65535) return

    for (let chunkIndex = 0; chunkIndex < chunkCount; chunkIndex += 1) {
      const start = chunkIndex * MAX_PAYLOAD
      const payload = frame.subarray(start, Math.min(start + MAX_PAYLOAD, frame.byteLength))
      const packet = Buffer.concat([
        Buffer.from("AKUD", "ascii"),
        u32(frameId),
        u16(chunkIndex),
        u16(chunkCount),
        u32(frame.byteLength),
        payload,
      ])
      this.socket.send(packet, this.port, "127.0.0.1")
    }
  }
}
