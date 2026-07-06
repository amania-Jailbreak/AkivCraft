import http from "node:http"

const PORT = 28520
const clients = new Set()

const HTML = `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>AkivCraft Debug Chat</title>
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body { background: #1a1c1f; color: #dfe3e8; font-family: "Cascadia Code", "Fira Code", monospace; display: flex; flex-direction: column; height: 100vh; }
  header { background: #20242a; padding: 10px 16px; border-bottom: 1px solid #3a4048; display: flex; align-items: center; gap: 12px; }
  header h1 { font-size: 14px; font-weight: 600; color: #64ffda; }
  header .status { font-size: 12px; color: #6b7580; margin-left: auto; }
  header .status.connected { color: #64ffda; }
  #messages { flex: 1; overflow-y: auto; padding: 8px 12px; display: flex; flex-direction: column; gap: 2px; }
  .msg { padding: 3px 8px; border-radius: 4px; font-size: 13px; line-height: 1.5; word-break: break-word; white-space: pre-wrap; }
  .msg.system { color: #ffcd75; }
  .msg.player { color: #c8e6c9; }
  .msg.client { color: #90caf9; }
  .msg.command { color: #ce93d8; font-style: italic; }
  .msg.error { color: #ef5350; }
  .msg .time { color: #555; font-size: 11px; margin-right: 6px; }
  #input-bar { display: flex; gap: 8px; padding: 10px 12px; background: #20242a; border-top: 1px solid #3a4048; }
  #input { flex: 1; background: #151719; border: 1px solid #3a4048; border-radius: 4px; padding: 8px 12px; color: #dfe3e8; font-size: 13px; font-family: inherit; outline: none; }
  #input:focus { border-color: #64ffda; }
  #send { background: #1b3a4b; color: #64ffda; border: 1px solid #2a5670; border-radius: 4px; padding: 8px 20px; font-size: 13px; cursor: pointer; font-family: inherit; }
  #send:hover { background: #234a5e; }
  #messages::-webkit-scrollbar { width: 6px; }
  #messages::-webkit-scrollbar-thumb { background: #3a4048; border-radius: 3px; }
</style>
</head>
<body>
<header>
  <h1>AkivCraft Debug Chat</h1>
  <span class="status" id="status">Connecting...</span>
</header>
<div id="messages"></div>
<div id="input-bar">
  <input id="input" type="text" placeholder="Type a message or /command..." autocomplete="off" autofocus />
  <button id="send">Send</button>
</div>
<script>
  const messages = document.getElementById('messages');
  const input = document.getElementById('input');
  const sendBtn = document.getElementById('send');
  const status = document.getElementById('status');

  function addMsg(type, text) {
    const el = document.createElement('div');
    el.className = 'msg ' + type;
    const time = new Date().toLocaleTimeString('en', { hour12: false });
    el.innerHTML = '<span class="time">' + time + '</span>' + text.replace(/</g, '&lt;');
    messages.appendChild(el);
    messages.scrollTop = messages.scrollHeight;
  }

  function doSend() {
    const text = input.value.trim();
    if (!text) return;
    fetch('/send', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ text }) });
    addMsg('command', '\\u2192 ' + text);
    input.value = '';
  }

  sendBtn.addEventListener('click', doSend);
  input.addEventListener('keydown', e => { if (e.key === 'Enter') doSend(); });

  const es = new EventSource('/events');
  es.onopen = () => { status.textContent = 'Connected'; status.classList.add('connected'); };
  es.onerror = () => { status.textContent = 'Disconnected'; status.classList.remove('connected'); };
  es.onmessage = e => {
    try {
      const msg = JSON.parse(e.data);
      addMsg(msg.type || 'system', msg.text || '');
    } catch { addMsg('error', 'Parse error: ' + e.data); }
  };

  addMsg('system', 'Debug Chat ready. Type /help for commands or just chat.');
</script>
</body>
</html>`

export default {
  id: "debug-chat",
  name: "Debug Chat",
  version: "0.1.0",
  description: "External chat window for debugging. Open http://localhost:28520 in a browser.",

  onEnable(api) {
    const server = http.createServer((req, res) => {
      if (req.method === "GET" && (req.url === "/" || req.url === "/index.html")) {
        res.writeHead(200, { "Content-Type": "text/html; charset=utf-8" })
        res.end(HTML)
        return
      }

      if (req.method === "GET" && req.url === "/events") {
        res.writeHead(200, {
          "Content-Type": "text/event-stream",
          "Cache-Control": "no-cache",
          "Connection": "keep-alive",
        })
        clients.add(res)
        req.on("close", () => clients.delete(res))
        return
      }

      if (req.method === "POST" && req.url === "/send") {
        let body = ""
        req.on("data", chunk => { body += chunk })
        req.on("end", () => {
          try {
            const { text } = JSON.parse(body)
            if (typeof text === "string" && text.trim()) {
              if (text.startsWith("/")) {
                api.chat.command(text.slice(1))
              } else {
                api.chat.send(text)
              }
            }
            res.writeHead(200, { "Content-Type": "application/json" })
            res.end('{"ok":true}')
          } catch {
            res.writeHead(400)
            res.end('{"ok":false}')
          }
        })
        return
      }

      res.writeHead(404)
      res.end("Not found")
    })

    server.on("error", (error) => {
      if (error.code === "EADDRINUSE") {
        console.error(`AkivCraft Debug Chat: port ${PORT} is already in use`)
      } else {
        console.error("AkivCraft Debug Chat server error:", error.message)
      }
    })

    server.listen(PORT, "127.0.0.1", () => {
      console.log(`AkivCraft Debug Chat: open http://localhost:${PORT}`)
    })

    api.chat.onMessage((msg) => {
      const data = JSON.stringify({ type: msg.type, text: msg.text })
      for (const client of clients) {
        try {
          client.write(`data: ${data}\n\n`)
        } catch {
          clients.delete(client)
        }
      }
    })

    api.chat.send("Debug Chat connected")
  },

  onDisable() {
    for (const client of clients) {
      try { client.end() } catch {}
    }
    clients.clear()
  },
}
