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
