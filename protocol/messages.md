# AkivCraft Protocol

AkivCraft initially uses JSON messages between the Java loader and the Node runtime. The transport can be WebSocket or stdio; the message envelope stays the same.

```json
{
  "id": "request-id",
  "type": "hud.addText",
  "payload": {}
}
```

## Initial Message Types

- `runtime.ready`
- `hud.addText`
- `hud.remove`
- `key.press`
- `key.release`
- `chat.send`
- `chat.message`
- `settings.get`
- `settings.set`
