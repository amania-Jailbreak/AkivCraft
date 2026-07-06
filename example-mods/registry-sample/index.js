export default {
  id: "registry-sample",
  name: "Registry Sample",
  version: "0.1.0",
  description: "Direct Java registry injection sample for blocks, entities, dimensions, and worldgen references.",

  onEnable(api) {
    api.blocks.register({
      id: "akivcraft.registry_sample:ruby_block",
      material: "stone",
      hardness: 3.0,
      explosionResistance: 6.0,
      requiresTool: true,
      lightLevel: 0,
    })

    api.entities.register({
      id: "akivcraft.registry_sample:ruby_golem",
      width: 1.4,
      height: 2.7,
      fireImmune: true,
      summonable: true,
      trackingRange: 10,
      updateInterval: 3,
      clientTrackingRange: 10,
    })

    api.dimensions.register({
      id: "akivcraft.registry_sample:void_world",
      type: {
        height: 256,
        minY: 0,
        logicalHeight: 256,
        ambientLight: 0.0,
        hasSkylight: false,
        hasCeiling: false,
        ultrawarm: false,
        natural: false,
        bedWorks: false,
        respawnAnchorWorks: false,
        hasRaids: false,
        piglinSafe: false,
        effects: "minecraft:the_end",
      },
      generator: {
        template: "end",
      },
    })

    api.chat.send("Registry Sample registered a block, entity, and dimension through Java registries")
  },
}
