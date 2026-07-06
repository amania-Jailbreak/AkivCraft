export default {
  id: "fps-hud",
  name: "FPS HUD",
  onEnable(api) {
    api.hud.addText("fps", () => `FPS: ${api.client.fps()}`, {
      x: 8,
      y: 8,
      color: "#ffffff"
    })

    api.chat.send(`AkivCraft FPS HUD enabled on ${api.client.minecraftVersion()}`)
  }
}
