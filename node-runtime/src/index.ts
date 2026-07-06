import path from "node:path"
import { AkivCraftRuntime } from "./runtime.js"

function argValue(name: string, fallback: string): string {
  const index = process.argv.indexOf(name)
  if (index === -1) return fallback
  return process.argv[index + 1] ?? fallback
}

const minecraftVersion = argValue("--minecraft-version", "26.1.2")
const modsDirectory = path.resolve(argValue("--mods", "example-mods"))
const port = Number.parseInt(argValue("--port", "28512"), 10)
const statePort = Number.parseInt(argValue("--state-port", "28513"), 10)
const binaryPort = Number.parseInt(argValue("--binary-port", "28514"), 10)
const udpPort = Number.parseInt(argValue("--udp-port", "28515"), 10)
const stdioIpc = process.argv.includes("--stdio-ipc")

if (stdioIpc) {
  console.log = (...args: unknown[]) => console.error(...args)
  console.info = (...args: unknown[]) => console.error(...args)
}

const runtime = new AkivCraftRuntime({ modsDirectory, minecraftVersion, port, statePort, binaryPort, udpPort, stdioIpc })

runtime.start().catch((error: unknown) => {
  console.error("AkivCraft Node runtime failed", error)
  process.exitCode = 1
})
