import net from "node:net"

const encode = (value: string): string => Buffer.from(value, "utf8").toString("base64")

export class PlayerActionClient {
  constructor(
    private readonly statePort: number,
    private readonly stdio: boolean,
  ) {
  }

  setVelocity(x: number, y: number, z: number): void {
    this.send(`setVelocity\t${x}\t${y}\t${z}`)
  }

  teleport(x: number, y: number, z: number): void {
    this.send(`teleport\t${x}\t${y}\t${z}`)
  }

  heal(amount: number): void {
    this.send(`heal\t${amount}`)
  }

  addEffect(effect: string, duration: number, amplifier: number): void {
    this.send(`addEffect\t${effect}\t${duration}\t${amplifier}`)
  }

  sendChat(message: string): void {
    this.send(`sendChat\t${message}`)
  }

  sendCommand(command: string): void {
    this.send(`sendCommand\t${command}`)
  }

  setBlock(x: number, y: number, z: number, blockId: string): void {
    this.send(`setBlock\t${x}\t${y}\t${z}\t${blockId}`)
  }

  removeBlock(x: number, y: number, z: number): void {
    this.send(`removeBlock\t${x}\t${y}\t${z}`)
  }

  private query(request: string): Promise<string> {
    if (this.stdio) {
      return Promise.reject(new Error("getBlock/getBlocks not supported in stdio mode"))
    }
    return new Promise((resolve, reject) => {
      const socket = new net.Socket()
      let raw = ""
      socket.setEncoding("utf8")
      socket.setTimeout(1000)
      socket.connect(this.statePort, "127.0.0.1", () => {
        socket.write(`${request}\n`)
      })
      socket.on("data", (chunk: string) => { raw += chunk })
      socket.on("end", () => { resolve(raw.trim()) })
      socket.on("timeout", () => { socket.destroy(new Error("block query timeout")) })
      socket.on("error", reject)
    })
  }

  async getBlock(x: number, y: number, z: number): Promise<string> {
    const response = await this.query(`getBlock ${x} ${y} ${z}`)
    const parsed = JSON.parse(response) as { error?: string, blockId?: string }
    if (parsed.error) throw new Error(parsed.error)
    return parsed.blockId ?? "minecraft:air"
  }

  async getBlocks(x1: number, y1: number, z1: number, x2: number, y2: number, z2: number): Promise<Array<{ x: number, y: number, z: number, id: string }>> {
    const response = await this.query(`getBlocks ${x1} ${y1} ${z1} ${x2} ${y2} ${z2}`)
    const parsed = JSON.parse(response) as { error?: string, blocks?: Array<{ x: number, y: number, z: number, id: string }> }
    if (parsed.error) throw new Error(parsed.error)
    return parsed.blocks ?? []
  }

  private send(tsv: string): void {
    if (this.stdio) {
      process.stdout.write(`${JSON.stringify({ type: "playerAction", data: encode(tsv) })}\n`)
      return
    }

    const socket = new net.Socket()
    socket.connect(this.statePort, "127.0.0.1", () => {
      socket.write(`playerAction\t${tsv}\n`)
      socket.end()
    })
    socket.on("error", () => socket.destroy())
    socket.setTimeout(500, () => socket.destroy())
  }
}
