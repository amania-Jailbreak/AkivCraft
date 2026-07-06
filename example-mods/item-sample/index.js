let activeHookshot = null

export default {
  id: "item-sample",
  name: "Item Sample",
  version: "0.1.0",
  description: "Sample generated resources for a future custom item.",
  resources: {
    "assets/akivcraft.item_sample/textures/item/akiv_gem.png": "./assets/textures/item/akiv_gem.png",
    "assets/akivcraft.item_sample/textures/item/akiv_sword.png": "./assets/textures/item/akiv_sword.png",
    "assets/akivcraft.item_sample/textures/item/akiv_pickaxe.png": "./assets/textures/item/akiv_pickaxe.png",
    "assets/akivcraft.item_sample/models/item/akiv_gem.json": "./assets/models/item/akiv_gem.json",
    "assets/akivcraft.item_sample/models/item/akiv_sword.json": "./assets/models/item/akiv_sword.json",
    "assets/akivcraft.item_sample/models/item/akiv_pickaxe.json": "./assets/models/item/akiv_pickaxe.json",
    "assets/akivcraft.item_sample/items/akiv_gem.json": "./assets/items/akiv_gem.json",
    "assets/akivcraft.item_sample/items/akiv_sword.json": "./assets/items/akiv_sword.json",
    "assets/akivcraft.item_sample/items/akiv_pickaxe.json": "./assets/items/akiv_pickaxe.json",
    "assets/akivcraft.item_sample/items/grappling_hook.json": "./assets/items/grappling_hook.json",
    "assets/akivcraft.item_sample/models/item/grappling_hook.json": "./assets/models/item/grappling_hook.json",
    "assets/akivcraft.item_sample/lang/en_us.json": "./assets/lang/en_us.json",
    "assets/akivcraft.item_sample/lang/ja_jp.json": "./assets/lang/ja_jp.json",
    "data/akivcraft.item_sample/recipe/akiv_gem.json": "./data/akivcraft.item_sample/recipe/akiv_gem.json",
    "data/akivcraft.item_sample/recipe/akiv_sword.json": "./data/akivcraft.item_sample/recipe/akiv_sword.json",
    "data/akivcraft.item_sample/recipe/akiv_pickaxe.json": "./data/akivcraft.item_sample/recipe/akiv_pickaxe.json"
  },
  onEnable(api) {
    api.creative.registerTab({
      id: "akivcraft.item_sample:main",
      name: "AkivCraft Items",
      icon: "akivcraft.item_sample:akiv_gem",
      row: "top",
    })

    api.items.register({
      id: "akivcraft.item_sample:akiv_gem",
      name: "Akiv Gem",
      maxStackSize: 64,
      rarity: "rare",
      tab: "akivcraft.item_sample:main",
      onUse: [
        { type: "potion_effect", effect: "minecraft:speed", duration: 200, amplifier: 1 },
        { type: "sound", sound: "minecraft:block.amethyst_block.chime", volume: 1.0, pitch: 1.5 },
        { type: "node_callback", event: "gem_use" },
        { type: "cooldown", ticks: 60 },
      ],
    })

    api.items.onUse("akivcraft.item_sample:akiv_gem", (ctx) => {
      api.chat.send(`Gem used by ${ctx.player} at (${ctx.x.toFixed(1)}, ${ctx.y.toFixed(1)}, ${ctx.z.toFixed(1)})`)
    })

    api.items.register({
      id: "akivcraft.item_sample:akiv_sword",
      name: "Akiv Sword",
      type: "sword",
      material: "diamond",
      attackDamage: 4,
      attackSpeed: -2.2,
      rarity: "epic",
      fireResistant: true,
      tab: "akivcraft.item_sample:main",
      onUse: [
        { type: "lightning" },
        { type: "particle", particle: "minecraft:electric_spark", count: 20 },
        { type: "sound", sound: "minecraft:entity.lightning_bolt.thunder", volume: 1.0, pitch: 0.8 },
        { type: "cooldown", ticks: 100 },
      ],
    })

    api.items.register({
      id: "akivcraft.item_sample:akiv_pickaxe",
      name: "Akiv Pickaxe",
      type: "pickaxe",
      material: "diamond",
      attackDamage: 1,
      attackSpeed: -2.8,
      miningSpeed: 1,
      rarity: "rare",
      tab: "akivcraft.item_sample:main",
    })

    api.items.register({
      id: "akivcraft.item_sample:grappling_hook",
      name: "Grappling Hook",
      maxStackSize: 1,
      durability: 0,
      rarity: "uncommon",
      tab: "akivcraft.item_sample:main",
      onUse: [
        { type: "sound", sound: "minecraft:item.crossbow.shoot", volume: 0.8, pitch: 1.5 },
        { type: "node_callback", event: "hookshot" },
        { type: "cooldown", ticks: 10 },
      ],
    })

    api.items.onUse("akivcraft.item_sample:grappling_hook", (ctx) => {
      if (!ctx.rayHit) return

      if (activeHookshot) clearInterval(activeHookshot)

      const target = ctx.rayHit
      let elapsed = 0
      const tickMs = 100
      const maxMs = 5000

      activeHookshot = setInterval(() => {
        elapsed += tickMs
        if (elapsed > maxMs) {
          clearInterval(activeHookshot)
          activeHookshot = null
          return
        }

        const pos = api.player.position()
        const dx = target.x - pos.x
        const dy = target.y - pos.y
        const dz = target.z - pos.z
        const dist = Math.sqrt(dx * dx + dy * dy + dz * dz)

        if (dist < 1.5) {
          clearInterval(activeHookshot)
          activeHookshot = null
          return
        }

        const speed = Math.max(0.3, Math.min(dist * 0.25, 1.8))
        const nx = dx / dist
        const ny = dy / dist
        const nz = dz / dist
        const gravityComp = 0.2

        api.player.setVelocity(nx * speed, ny * speed + gravityComp, nz * speed)
      }, tickMs)
    })

    api.settings.define({
      "itemSample.preview": true,
    })

    api.keys.register({
      id: "item-sample.preview.toggle",
      category: "Item Sample",
      name: "Toggle item sample preview",
      description: "Show or hide the generated item resource preview.",
      defaultKey: 73,
      onPress() {
        const next = api.settings.get("itemSample.preview") === false
        api.settings.set("itemSample.preview", next)
        api.chat.send(`Item Sample preview ${next ? "enabled" : "disabled"}`)
      },
    })

    api.hud.addCanvas("item-sample.preview", () => {
      if (api.settings.get("itemSample.preview") === false) return []

      return [
        { kind: "rect", x: 8, y: 168, width: 156, height: 34, color: "#aa101820" },
        { kind: "sprite", texture: "akivcraft.item_sample:textures/item/akiv_gem.png", x: 14, y: 173, width: 24, height: 24 },
        { kind: "text", x: 44, y: 173, text: "Item resource sample", color: "#ffdbfff5" },
        { kind: "sprite", texture: "akivcraft.item_sample:textures/item/akiv_sword.png", x: 132, y: 173, width: 24, height: 24 },
        { kind: "sprite", texture: "akivcraft.item_sample:textures/item/akiv_pickaxe.png", x: 158, y: 173, width: 24, height: 24 },
        { kind: "text", x: 44, y: 184, text: "gem, sword, pickaxe", color: "#ff80ffd9" },
      ]
    })

    api.chat.send("Item Sample registered Akiv Gem, Sword, and Pickaxe")
  },
  onDisable(api) {
    if (activeHookshot) clearInterval(activeHookshot)
    api.hud.remove("item-sample.preview")
  },
}
