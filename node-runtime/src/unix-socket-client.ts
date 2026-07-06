import net from "node:net"

export class UnixSocketClient {
  private socket: net.Socket | null = null

  constructor(private readonly socketPath: string) {}

  connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      const socket = net.createConnection(this.socketPath)
      socket.on("connect", () => {
        this.socket = socket
        console.error(`AkivCraft Unix socket client connected to ${this.socketPath}`)
        resolve()
      })
      socket.on("error", (error) => {
        socket.destroy()
        reject(error)
      })
      socket.on("close", () => {
        this.socket = null
      })
    })
  }

  sendHud(lines: string, bitmaps: Buffer): void {
    const socket = this.socket
    if (!socket || socket.destroyed) return

    if (lines) {
      const data = Buffer.from(lines, "utf8").toString("base64")
      socket.write(JSON.stringify({ type: "hud", data }) + "\n")
    }
    if (bitmaps && bitmaps.length > 0) {
      const data = bitmaps.toString("base64")
      socket.write(JSON.stringify({ type: "bitmap", data }) + "\n")
    }
  }
}
