export default {
  id: "block-events",
  name: "Block Events Sample",
  version: "0.1.0",
  description: "Logs block use/place/break events and demonstrates world edit via setBlock.",

  onEnable(api) {
    api.events.on("use_block", (ctx) => {
      if (ctx.itemId === "minecraft:flint_and_steel" && ctx.targetBlock === "minecraft:obsidian") {
        api.chat.send(`AkivCraft: obsidian ignited at ${ctx.targetPos.x}, ${ctx.targetPos.y}, ${ctx.targetPos.z}`)
      }
    })

    api.blocks.onPlace("minecraft:diamond_block", (ctx) => {
      api.chat.send(`AkivCraft: diamond block placed in ${ctx.dimension}`)
    })

    api.blocks.onBreak("minecraft:stone", (ctx) => {
      api.chat.send(`AkivCraft: stone broken near ${ctx.breakPos.x}, ${ctx.breakPos.y}, ${ctx.breakPos.z}`)
    })

    api.blocks.onUse("minecraft:gold_block", (ctx) => {
      const { x, y, z } = ctx.targetPos
      api.world.setBlock(x, y + 1, z, "minecraft:beacon")
      api.chat.send(`AkivCraft: placed beacon above gold block at ${x}, ${y + 1}, ${z}`)
    })
  }
}
