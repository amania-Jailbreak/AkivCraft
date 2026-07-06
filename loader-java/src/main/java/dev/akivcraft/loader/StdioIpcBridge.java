package dev.akivcraft.loader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

public final class StdioIpcBridge {
    private static final Pattern TYPE_PATTERN = Pattern.compile("\"type\":\"([^\"]+)\"");
    private static final Pattern DATA_PATTERN = Pattern.compile("\"data\":\"([^\"]*)\"");
    private static final Pattern QUERY_ID_PATTERN = Pattern.compile("\"id\":\"([^\"]+)\"");
    private static volatile PrintWriter writer;
    private static volatile String keybindingsJson = "{\"bindings\":[]}";
    private static volatile boolean loggedHud;
    private static volatile boolean loggedBitmap;
    private static volatile boolean loggedKeybindings;

    private StdioIpcBridge() {
    }

    public static boolean enabled() {
        return writer != null;
    }

    public static void attach(Process process) {
        writer = new PrintWriter(process.getOutputStream(), true, StandardCharsets.UTF_8);
        var thread = new Thread(() -> readLoop(process), "AkivCraft stdio IPC reader");
        thread.setDaemon(true);
        thread.start();
        System.out.println("AkivCraft stdio IPC enabled");
    }

    public static String keybindingsJson() {
        return keybindingsJson;
    }

    public static void sendState(String json) {
        send("{\"type\":\"state\",\"data\":\"" + encode(json) + "\"}");
    }

    public static void sendKeyBinding(String id, boolean press) {
        send("{\"type\":\"keyBindingEvent\",\"action\":\"" + (press ? "press" : "release") + "\",\"id\":\"" + escape(id) + "\"}");
    }

    public static void sendKeyEvent(int action, int key, int scancode, int modifiers) {
        send("{\"type\":\"keyEvent\",\"action\":" + action + ",\"key\":" + key + ",\"scancode\":" + scancode + ",\"modifiers\":" + modifiers + "}");
    }

    public static void sendItemUse(
        String itemId, String playerName,
        double x, double y, double z, String event,
        double lookX, double lookY, double lookZ,
        boolean rayHit, String hitX, String hitY, String hitZ
    ) {
        send("{\"type\":\"itemUse\",\"id\":\"" + escape(itemId) + "\",\"player\":\"" + escape(playerName)
            + "\",\"x\":" + x + ",\"y\":" + y + ",\"z\":" + z
            + ",\"event\":\"" + escape(event) + "\""
            + ",\"look\":{\"x\":" + lookX + ",\"y\":" + lookY + ",\"z\":" + lookZ + "}"
            + (rayHit ? ",\"rayHit\":{\"x\":" + hitX + ",\"y\":" + hitY + ",\"z\":" + hitZ + "}" : "")
            + "}");
    }

    public static void sendChatMessage(String type, String text) {
        send("{\"type\":\"chatMessage\",\"chatType\":\"" + escape(type) + "\",\"text\":\"" + escape(text) + "\"}");
    }

    public static void sendBlockEvent(String json) {
        send("{\"type\":\"blockEvent\",\"data\":\"" + encode(json) + "\"}");
    }

    private static void readLoop(Process process) {
        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) handle(line);
        } catch (IOException error) {
            System.err.printf("AkivCraft stdio IPC stopped: %s%n", error.getMessage());
        }
    }

    private static void handle(String line) {
        var type = match(TYPE_PATTERN, line);
        var data = match(DATA_PATTERN, line);
        if (type == null || data == null) return;

        try {
            var decoded = Base64.getDecoder().decode(data);
            if ("hud".equals(type)) {
                var text = new String(decoded, StandardCharsets.UTF_8);
                var items = NodeHudClient.parseLines(text.isBlank() ? List.of() : Arrays.asList(text.split("\\n", -1)));
                NodeHudClient.setItems(items);
                if (!loggedHud && !items.isEmpty()) {
                    loggedHud = true;
                    System.out.printf("AkivCraft stdio HUD received %d items%n", items.size());
                }
            } else if ("bitmap".equals(type)) {
                var bitmaps = BinaryHudClient.parse(decoded);
                BinaryHudClient.setBitmaps(bitmaps);
                if (!loggedBitmap && !bitmaps.isEmpty()) {
                    loggedBitmap = true;
                    System.out.printf("AkivCraft stdio bitmap received %d items%n", bitmaps.size());
                }
            } else if ("keybindings".equals(type)) {
                keybindingsJson = new String(decoded, StandardCharsets.UTF_8);
                if (!loggedKeybindings && keybindingsJson.contains("\"id\"")) {
                    loggedKeybindings = true;
                    System.out.println("AkivCraft stdio keybindings received");
                }
            } else if ("playerAction".equals(type)) {
                var tsv = new String(decoded, StandardCharsets.UTF_8);
                PlayerActionHandler.handle("playerAction\t" + tsv);
            } else if ("query".equals(type)) {
                var tsv = new String(decoded, StandardCharsets.UTF_8);
                var response = handleQuery(tsv);
                send("{\"type\":\"queryResult\",\"id\":\"" + escape(extractQueryId(line)) + "\",\"data\":\"" + encode(response) + "\"}");
            }
        } catch (Throwable error) {
            System.err.printf("AkivCraft failed to parse stdio IPC frame: %s%n", error.getMessage());
        }
    }

    private static void send(String line) {
        var out = writer;
        if (out == null) return;
        out.println(line);
    }

    private static String extractQueryId(String json) {
        var matcher = QUERY_ID_PATTERN.matcher(json);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String handleQuery(String tsv) {
        var parts = tsv.split("\t");
        if (parts.length < 1) return "{\"error\":\"empty query\"}";
        var action = parts[0];
        if ("getBlock".equals(action) && parts.length >= 4) {
            try {
                var x = Integer.parseInt(parts[1]);
                var y = Integer.parseInt(parts[2]);
                var z = Integer.parseInt(parts[3]);
                return StateIpcServer.queryBlockJson(x, y, z);
            } catch (NumberFormatException e) {
                return "{\"error\":\"invalid coordinates\"}";
            }
        }
        if ("getBlocks".equals(action) && parts.length >= 7) {
            try {
                var x1 = Integer.parseInt(parts[1]);
                var y1 = Integer.parseInt(parts[2]);
                var z1 = Integer.parseInt(parts[3]);
                var x2 = Integer.parseInt(parts[4]);
                var y2 = Integer.parseInt(parts[5]);
                var z2 = Integer.parseInt(parts[6]);
                return StateIpcServer.queryBlocksJson(x1, y1, z1, x2, y2, z2);
            } catch (NumberFormatException e) {
                return "{\"error\":\"invalid coordinates\"}";
            }
        }
        return "{\"error\":\"unknown query\"}";
    }

    private static String match(Pattern pattern, String value) {
        var matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
