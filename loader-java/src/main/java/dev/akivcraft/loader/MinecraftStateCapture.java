package dev.akivcraft.loader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

public final class MinecraftStateCapture {
    private static volatile String currentJson = fallbackJson();
    private static String cachedSurfaceJson = "{\"radius\":0,\"blocks\":[]}";
    private static int cachedSurfaceX = Integer.MIN_VALUE;
    private static int cachedSurfaceZ = Integer.MIN_VALUE;
    private static int surfaceTick;

    private MinecraftStateCapture() {
    }

    public static void update(Minecraft minecraft) {
        if (minecraft == null) return;

        try {
            ResourcePackInjector.inject(minecraft);
            AkivCraftKeyMappings.tick(minecraft);
            PlayerActionQueue.processTick(minecraft);

            var player = minecraft.player;
            var level = minecraft.level;
            var server = minecraft.getCurrentServer();
            var dimension = level == null ? "unknown" : level.dimension().identifier().toString();
            var gameTime = level == null ? 0L : level.getLevelData().getGameTime();
            var weather = level == null ? "clear" : level.isThundering() ? "thunder" : level.isRaining() ? "rain" : "clear";
            String biome = player == null || level == null ? null : level.getBiome(player.blockPosition()).toString();

            currentJson = "{"
                + "\"client\":{" 
                + "\"minecraftVersion\":" + quote(minecraft.getLaunchedVersion()) + ","
                + "\"fps\":" + minecraft.getFps() + ","
                + "\"screen\":" + quote(minecraft.screen == null ? null : minecraft.screen.getClass().getName()) + ","
                + "\"paused\":" + minecraft.isPaused()
                + "},"
                + "\"player\":" + playerJson(player, dimension, biome) + ","
                + "\"world\":{" 
                + "\"dimension\":" + quote(dimension) + ","
                + "\"biome\":" + quote(biome) + ","
                + "\"timeOfDay\":" + (gameTime % 24000L) + ","
                + "\"day\":" + (gameTime / 24000L) + ","
                + "\"weather\":" + quote(weather) + ","
                + "\"surface\":" + surfaceJson(minecraft) + ","
                + "\"entities\":" + entitiesJson(minecraft)
                + "},"
                + "\"server\":" + serverJson(minecraft, server)
                + "}";
            StdioIpcBridge.sendState(currentJson);
        } catch (Throwable error) {
            System.err.printf("AkivCraft failed to capture Minecraft state: %s%n", error.getMessage());
        }
    }

    public static String currentJson() {
        return currentJson;
    }

    private static String playerJson(LocalPlayer player, String dimension, String biome) {
        if (player == null) return "null";

        var position = player.position();
        var blockPosition = player.blockPosition();
        var velocity = player.getDeltaMovement();
        var food = player.getFoodData();

        return "{"
            + "\"name\":" + quote(playerName(player)) + ","
            + "\"uuid\":" + quote(player.getUUID().toString()) + ","
            + "\"position\":" + vec(position) + ","
            + "\"blockPosition\":" + block(blockPosition) + ","
            + "\"velocity\":" + vec(velocity) + ","
            + "\"yaw\":" + f(player.getYRot()) + ","
            + "\"pitch\":" + f(player.getXRot()) + ","
            + "\"facing\":" + quote(player.getDirection().getName().toUpperCase(Locale.ROOT)) + ","
            + "\"health\":" + f(player.getHealth()) + ","
            + "\"maxHealth\":" + f(player.getMaxHealth()) + ","
            + "\"food\":" + food.getFoodLevel() + ","
            + "\"saturation\":" + f(food.getSaturationLevel()) + ","
            + "\"experienceLevel\":" + player.experienceLevel + ","
            + "\"dimension\":" + quote(dimension) + ","
            + "\"biome\":" + quote(biome)
            + "}";
    }

    private static String serverJson(Minecraft minecraft, ServerData server) {
        return "{"
            + "\"connected\":" + (minecraft.getConnection() != null) + ","
            + "\"address\":" + quote(server == null ? null : server.ip) + ","
            + "\"brand\":null,"
            + "\"pingMs\":" + (server == null ? "null" : Long.toString(server.ping))
            + "}";
    }

    private static String surfaceJson(Minecraft minecraft) {
        var player = minecraft.player;
        var level = minecraft.level;
        if (player == null || level == null) return "{\"radius\":0,\"blocks\":[]}";

        var center = player.blockPosition();
        var radius = 32;
        if (cachedSurfaceX == center.getX() && cachedSurfaceZ == center.getZ() && (surfaceTick++ % 4) != 0) {
            return cachedSurfaceJson;
        }

        cachedSurfaceX = center.getX();
        cachedSurfaceZ = center.getZ();
        var maxY = Math.min(level.getMaxY() - 1, center.getY() + 48);
        var minY = level.getMinY();
        var pos = new BlockPos.MutableBlockPos();
        var json = new StringBuilder("{\"radius\":").append(radius).append(",\"blocks\":[");
        var first = true;

        for (var dz = -radius; dz <= radius; dz++) {
            for (var dx = -radius; dx <= radius; dx++) {
                var x = center.getX() + dx;
                var z = center.getZ() + dz;
                BlockState surface = null;
                var surfaceY = minY;

                for (var y = maxY; y >= minY; y--) {
                    pos.set(x, y, z);
                    var state = level.getBlockState(pos);
                    if (!state.isAir()) {
                        surface = state;
                        surfaceY = y;
                        break;
                    }
                }

                if (surface == null) continue;
                if (!first) json.append(',');
                first = false;
                var id = blockId(surface);
                json.append('{')
                    .append("\"x\":").append(x).append(',')
                    .append("\"z\":").append(z).append(',')
                    .append("\"y\":").append(surfaceY).append(',')
                    .append("\"id\":").append(quote(id)).append(',')
                    .append("\"kind\":").append(quote(blockKind(id, surface)))
                    .append('}');
            }
        }

        cachedSurfaceJson = json.append("]}").toString();
        return cachedSurfaceJson;
    }

    private static String entitiesJson(Minecraft minecraft) {
        var player = minecraft.player;
        var level = minecraft.level;
        if (player == null || level == null) return "[]";

        var center = player.position();
        var radiusSqr = 64.0 * 64.0;
        var json = new StringBuilder("[");
        var first = true;
        var count = 0;

        for (var entity : level.entitiesForRendering()) {
            if (entity == null || entity == player || !entity.isAlive()) continue;
            var position = entity.position();
            if (position.distanceToSqr(center) > radiusSqr) continue;

            var kind = entityKind(entity);
            if (kind == null) continue;

            if (!first) json.append(',');
            first = false;
            json.append('{')
                .append("\"id\":").append(entity.getId()).append(',')
                .append("\"uuid\":").append(quote(entity.getUUID().toString())).append(',')
                .append("\"name\":").append(quote(entity.getName().getString())).append(',')
                .append("\"type\":").append(quote(entity.getType().toString())).append(',')
                .append("\"kind\":").append(quote(kind)).append(',')
                .append("\"position\":").append(vec(position))
                .append('}');

            if (++count >= 128) break;
        }

        return json.append(']').toString();
    }

    private static String entityKind(Entity entity) {
        if (entity instanceof Player) return "player";
        if (entity instanceof Enemy) return "hostile_mob";
        if (entity instanceof Mob) return "passive_mob";
        return null;
    }

    private static String blockId(BlockState state) {
        var value = state.getBlock().toString();
        if (value.startsWith("Block{")) value = value.substring(6, value.length() - 1);
        return value;
    }

    private static String blockKind(String id, BlockState state) {
        if (state.liquid()) return id.contains("lava") ? "lava" : "water";
        if (id.contains("ore")) return "ore";
        if (id.contains("nether") || id.contains("netherrack") || id.contains("soul_") || id.contains("crimson") || id.contains("warped")) return "nether";
        if (id.contains("end_stone") || id.contains("purpur") || id.contains("chorus")) return "end";
        if (id.contains("flower") || id.contains("tulip") || id.contains("dandelion") || id.contains("poppy") || id.contains("orchid") || id.contains("allium") || id.contains("azure_bluet")) return "flower";
        if (id.contains("crop") || id.contains("wheat") || id.contains("carrot") || id.contains("potato") || id.contains("beetroot") || id.contains("melon") || id.contains("pumpkin") || id.contains("cactus") || id.contains("sugar_cane")) return "crop";
        if (id.contains("grass") || id.contains("moss")) return "grass";
        if (id.contains("fern") || id.contains("bush") || id.contains("vine") || id.contains("kelp") || id.contains("seagrass")) return "foliage";
        if (id.contains("leaves")) return "leaves";
        if (id.contains("log") || id.contains("wood") || id.contains("stem") || id.contains("hyphae")) return "wood";
        if (id.contains("deepslate")) return "deepslate";
        if (id.contains("basalt")) return "basalt";
        if (id.contains("blackstone")) return "blackstone";
        if (id.contains("tuff")) return "tuff";
        if (id.contains("stone") || id.contains("andesite") || id.contains("diorite") || id.contains("granite") || id.contains("calcite")) return "stone";
        if (id.contains("red_sand")) return "red_sand";
        if (id.contains("sand")) return "sand";
        if (id.contains("gravel")) return "gravel";
        if (id.contains("snow")) return "snow";
        if (id.contains("ice")) return "ice";
        if (id.contains("mud")) return "mud";
        if (id.contains("clay")) return "clay";
        if (id.contains("dirt") || id.contains("podzol") || id.contains("farmland")) return "dirt";
        if (id.contains("glass")) return "glass";
        if (id.contains("iron") || id.contains("gold") || id.contains("copper") || id.contains("anvil") || id.contains("chain")) return "metal";
        if (id.contains("wool") || id.contains("carpet")) return "wool";
        if (id.contains("brick") || id.contains("plank") || id.contains("concrete") || id.contains("terracotta") || id.contains("tile") || id.contains("slab") || id.contains("stairs")) return "structure";
        return "other";
    }

    private static String playerName(LocalPlayer player) {
        try {
            return (String) player.getGameProfile().getClass().getMethod("name").invoke(player.getGameProfile());
        } catch (ReflectiveOperationException | ClassCastException ignored) {
        }

        try {
            return (String) player.getGameProfile().getClass().getMethod("getName").invoke(player.getGameProfile());
        } catch (ReflectiveOperationException | ClassCastException ignored) {
        }

        return player.getName().getString();
    }

    private static String vec(Vec3 vec) {
        return "{\"x\":" + f(vec.x) + ",\"y\":" + f(vec.y) + ",\"z\":" + f(vec.z) + "}";
    }

    private static String block(BlockPos pos) {
        return "{\"x\":" + pos.getX() + ",\"y\":" + pos.getY() + ",\"z\":" + pos.getZ() + "}";
    }

    private static String f(double value) {
        if (!Double.isFinite(value)) return "0";
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static String quote(String value) {
        if (value == null) return "null";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private static String fallbackJson() {
        return "{\"client\":{\"minecraftVersion\":\"26.1.2\",\"fps\":0,\"screen\":null,\"paused\":false},\"player\":null,\"world\":{\"dimension\":\"unknown\",\"biome\":null,\"timeOfDay\":0,\"day\":0,\"weather\":\"clear\",\"surface\":{\"radius\":0,\"blocks\":[]},\"entities\":[]},\"server\":{\"connected\":false,\"address\":null,\"brand\":null,\"pingMs\":null}}";
    }
}
