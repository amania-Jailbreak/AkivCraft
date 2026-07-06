import { cp, mkdir, rm, writeFile } from "node:fs/promises"
import { existsSync } from "node:fs"
import { createHash } from "node:crypto"
import { createReadStream } from "node:fs"
import { readFile, stat } from "node:fs/promises"
import { request } from "node:https"
import path from "node:path"

const root = process.cwd()
const out = path.join(root, "dist", "external-launcher", ".akivcraft")
const packageRoot = path.join(root, "dist", "external-launcher")
const loaderJar = path.join(root, "loader-java", "build", "libs", "akivcraft-loader-0.1.0.jar")
const nodeRuntimeDist = path.join(root, "node-runtime", "dist")
const exampleMods = path.join(root, "example-mods")
const binId = `akivcraft-${new Date().toISOString().replace(/[-:]/g, "").replace(/\..+$/, "").replace("T", "-")}`
const buildVersion = `0.1.0-${binId.replace("akivcraft-", "")}`
const loaderFilename = `akivcraft-loader-${buildVersion}.jar`
const loaderMavenPath = `dev/akivcraft/akivcraft-loader/${buildVersion}/${loaderFilename}`
const loaderMavenFile = path.join(packageRoot, "libraries", loaderMavenPath)
const filebinJarUrl = `https://filebin.net/${binId}/${loaderFilename}`

if (!existsSync(loaderJar)) {
  throw new Error(`Missing loader jar: ${loaderJar}`)
}

if (!existsSync(nodeRuntimeDist)) {
  throw new Error(`Missing Node runtime build: ${nodeRuntimeDist}`)
}

await rm(packageRoot, { recursive: true, force: true })
await mkdir(path.join(out, "node-runtime"), { recursive: true })
await mkdir(path.join(out, "assets"), { recursive: true })
await mkdir(path.join(packageRoot, "patches"), { recursive: true })
await mkdir(path.join(packageRoot, "libraries", path.dirname(loaderMavenPath)), { recursive: true })

await cp(loaderJar, path.join(out, "akivcraft-loader.jar"))
await cp(loaderJar, loaderMavenFile)
await cp(nodeRuntimeDist, path.join(out, "node-runtime", "dist"), { recursive: true })
await writeFile(path.join(out, "node-runtime", "package.json"), '{"type":"module"}\n')
await writeFile(path.join(out, "assets", "README.txt"), "Optional fallback: place akivcraft-logo.png here if the loader jar does not bundle assets/akivcraft/logo.png.\n")
await cp(exampleMods, path.join(out, "mods"), { recursive: true })

const jvmArgs = [
  "-Dakivcraft.home=.akivcraft",
  "-Dakivcraft.minecraftVersion=26.1.2",
].join("\n")
const mainClass = "dev.akivcraft.loader.AkivCraftMain"
const classpath = ".akivcraft/akivcraft-loader.jar"
const loaderBytes = await readFile(loaderJar)
const loaderSha1 = createHash("sha1").update(loaderBytes).digest("hex")
const loaderSize = (await stat(loaderJar)).size
const library = {
  name: `dev.akivcraft:akivcraft-loader:${buildVersion}`,
  downloads: {
    artifact: {
      path: loaderMavenPath,
      sha1: loaderSha1,
      size: loaderSize,
      url: filebinJarUrl,
    },
  },
}
const patch = {
  formatVersion: 1,
  uid: "dev.akivcraft.loader",
  name: "AkivCraft Loader",
  version: buildVersion,
  order: 10,
  mainClass,
  libraries: [library],
  "+libraries": [library],
  jvmArgs: jvmArgs.split("\n"),
  "+jvmArgs": jvmArgs.split("\n"),
}
const mmcPackComponent = {
  cachedName: "AkivCraft Loader",
  cachedVersion: buildVersion,
  important: true,
  uid: "dev.akivcraft.loader",
  version: buildVersion,
}
const mmcPack = {
  components: [
    {
      cachedName: "LWJGL 3",
      cachedVersion: "3.4.1",
      cachedVolatile: true,
      dependencyOnly: true,
      uid: "org.lwjgl3",
      version: "3.4.1",
    },
    {
      cachedName: "Minecraft",
      cachedRequires: [
        {
          suggests: "3.4.1",
          uid: "org.lwjgl3",
        },
      ],
      cachedVersion: "26.1.2",
      important: true,
      uid: "net.minecraft",
      version: "26.1.2",
    },
    mmcPackComponent,
  ],
  formatVersion: 1,
}

await writeFile(path.join(root, "dist", "external-launcher", "jvm-args.txt"), `${jvmArgs}\n`)
await writeFile(path.join(root, "dist", "external-launcher", "main-class.txt"), `${mainClass}\n`)
await writeFile(path.join(root, "dist", "external-launcher", "classpath.txt"), `${classpath}\n`)
await writeFile(path.join(root, "dist", "external-launcher", "patches", "dev.akivcraft.loader.json"), `${JSON.stringify(patch, null, 2)}\n`)
await writeFile(path.join(root, "dist", "external-launcher", "mmc-pack-component.json"), `${JSON.stringify(mmcPackComponent, null, 2)}\n`)
await writeFile(path.join(root, "dist", "external-launcher", "mmc-pack.json"), `${JSON.stringify(mmcPack, null, 2)}\n`)
await writeFile(path.join(root, "dist", "external-launcher", "launch-info.json"), `${JSON.stringify({
  mainClass,
  classpath: [classpath],
  patch: "patches/dev.akivcraft.loader.json",
  mmcPackComponent,
  mmcPack: "mmc-pack.json",
  libraries: [`libraries/${loaderMavenPath}`],
  filebinJarUrl,
  buildVersion,
  jvmArgs: jvmArgs.split("\n"),
}, null, 2)}\n`)
await writeFile(
  path.join(root, "dist", "external-launcher", "README.txt"),
  [
    "AkivCraft external launcher package",
    "",
    "Prism Launcher / MultiMC install:",
    "1. Copy .akivcraft into your instance's minecraft/game directory.",
    "2. Copy patches/dev.akivcraft.loader.json into your instance's patches directory.",
    "3. Replace or merge your instance mmc-pack.json with generated mmc-pack.json.",
    "4. Copy libraries/dev/akivcraft into the launcher libraries directory, preserving paths, or let Prism download the jar from Filebin.",
    "5. Launch the instance. The patch sets the AkivCraft main class automatically.",
    "",
    `Filebin jar URL embedded in patch: ${filebinJarUrl}`,
    "",
    "Manual fallback:",
    "- Add .akivcraft/akivcraft-loader.jar to the Minecraft classpath/libraries.",
    `- Set the launch main class to ${mainClass}.`,
    "- Add these JVM arguments:",
    "",
    jvmArgs,
    "",
    "NodeJS 20+ must be available as `node` on PATH.",
  ].join("\n"),
)

console.log(`Created ${path.join(root, "dist", "external-launcher")}`)

console.log(`Uploading loader jar to Filebin: ${filebinJarUrl}`)
await uploadFilebin(binId, loaderFilename, loaderMavenFile)
console.log(`Filebin jar URL embedded in patch: ${filebinJarUrl}`)

async function uploadFilebin(bin, filename, file) {
  const { size } = await stat(file)
  const url = new URL(`https://filebin.net/${encodeURIComponent(bin)}/${encodeURIComponent(filename)}`)
  await new Promise((resolve, reject) => {
    const req = request(url, {
      method: "POST",
      headers: {
        "Content-Type": contentType(filename),
        "Content-Length": size,
      },
    }, res => {
      let body = ""
      res.setEncoding("utf8")
      res.on("data", chunk => body += chunk)
      res.on("end", () => {
        if (res.statusCode && res.statusCode >= 200 && res.statusCode < 300) resolve()
        else reject(new Error(`Filebin upload failed for ${filename}: HTTP ${res.statusCode} ${body}`))
      })
    })
    req.on("error", reject)
    createReadStream(file).pipe(req)
  })
}

function contentType(filename) {
  if (filename.endsWith(".jar")) return "application/java-archive"
  return "application/octet-stream"
}
