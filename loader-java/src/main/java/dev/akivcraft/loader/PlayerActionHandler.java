package dev.akivcraft.loader;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

public final class PlayerActionHandler {
    private PlayerActionHandler() {
    }

    public static void handle(String request) {
        var parts = request.split("\t");
        if (parts.length < 2) return;

        var action = parts[1];

        switch (action) {
            case "setVelocity" -> {
                if (parts.length < 5) return;
                var x = Double.parseDouble(parts[2]);
                var y = Double.parseDouble(parts[3]);
                var z = Double.parseDouble(parts[4]);
                enqueueVelocity(x, y, z);
            }
            case "teleport" -> {
                if (parts.length < 5) return;
                var x = Double.parseDouble(parts[2]);
                var y = Double.parseDouble(parts[3]);
                var z = Double.parseDouble(parts[4]);
                PlayerActionQueue.enqueue(() -> applyTeleport(x, y, z));
            }
            case "heal" -> {
                if (parts.length < 3) return;
                var amount = Float.parseFloat(parts[2]);
                PlayerActionQueue.enqueue(() -> applyHeal(amount));
            }
            case "addEffect" -> {
                if (parts.length < 5) return;
                var effectId = parts[2];
                var duration = Integer.parseInt(parts[3]);
                var amplifier = Integer.parseInt(parts[4]);
                PlayerActionQueue.enqueue(() -> applyEffect(effectId, duration, amplifier));
            }
            case "sendChat" -> {
                if (parts.length < 3) return;
                var message = parts[2];
                PlayerActionQueue.enqueue(() -> applySendChat(message));
            }
            case "sendCommand" -> {
                if (parts.length < 3) return;
                var command = parts[2];
                PlayerActionQueue.enqueue(() -> applySendCommand(command));
            }
            case "setBlock" -> {
                if (parts.length < 6) return;
                var x = Integer.parseInt(parts[2]);
                var y = Integer.parseInt(parts[3]);
                var z = Integer.parseInt(parts[4]);
                var blockId = parts[5];
                PlayerActionQueue.enqueue(() -> applySetBlock(x, y, z, blockId));
            }
            case "removeBlock" -> {
                if (parts.length < 5) return;
                var x = Integer.parseInt(parts[2]);
                var y = Integer.parseInt(parts[3]);
                var z = Integer.parseInt(parts[4]);
                PlayerActionQueue.enqueue(() -> applyRemoveBlock(x, y, z));
            }
        }
    }

    private static void enqueueVelocity(double x, double y, double z) {
        var velocity = new Vec3(x, y, z);
        PlayerActionQueue.enqueue(() -> applyVelocity(velocity));
    }

    private static void applyVelocity(Vec3 velocity) {
        var mc = Minecraft.getInstance();
        var local = mc.player;
        if (local == null) return;

        local.setDeltaMovement(velocity);
        local.hurtMarked = true;
        local.fallDistance = 0;

        var server = mc.getSingleplayerServer();
        if (server != null) {
            var sp = server.getPlayerList().getPlayer(local.getUUID());
            if (sp != null) {
                sp.setDeltaMovement(velocity);
                sp.hurtMarked = true;
                sp.fallDistance = 0;
            }
        }
    }

    private static void applyTeleport(double x, double y, double z) {
        var mc = Minecraft.getInstance();
        var local = mc.player;
        if (local == null) return;

        local.teleportTo(x, y, z);

        var server = mc.getSingleplayerServer();
        if (server != null) {
            var sp = server.getPlayerList().getPlayer(local.getUUID());
            if (sp != null) {
                sp.teleportTo(x, y, z);
            }
        }
    }

    private static void applyHeal(float amount) {
        var mc = Minecraft.getInstance();
        var local = mc.player;
        if (local == null) return;

        local.heal(amount);

        var server = mc.getSingleplayerServer();
        if (server != null) {
            var sp = server.getPlayerList().getPlayer(local.getUUID());
            if (sp != null) sp.heal(amount);
        }
    }

    private static void applyEffect(String effectId, int duration, int amplifier) {
        var id = Identifier.tryParse(effectId);
        if (id == null) return;
        var holder = BuiltInRegistries.MOB_EFFECT.get(id).orElse(null);
        if (holder == null) return;

        var instance = new MobEffectInstance(holder, duration, amplifier);
        var mc = Minecraft.getInstance();
        var local = mc.player;
        if (local == null) return;

        local.addEffect(instance);

        var server = mc.getSingleplayerServer();
        if (server != null) {
            var sp = server.getPlayerList().getPlayer(local.getUUID());
            if (sp != null) sp.addEffect(instance);
        }
    }

    private static void applySendChat(String message) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;
        mc.getConnection().sendChat(message);
    }

    private static void applySendCommand(String command) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;
        mc.getConnection().sendCommand(command);
    }

    private static void applySetBlock(int x, int y, int z, String blockId) {
        var mc = Minecraft.getInstance();
        var local = mc.player;
        if (local == null) return;

        var id = Identifier.tryParse(blockId);
        if (id == null) {
            System.err.printf("AkivCraft setBlock unknown block id: %s%n", blockId);
            return;
        }
        var block = BuiltInRegistries.BLOCK.get(id).orElse(null);
        if (block == null) {
            System.err.printf("AkivCraft setBlock block not registered: %s%n", blockId);
            return;
        }

        var pos = new BlockPos(x, y, z);

        var server = mc.getSingleplayerServer();
        if (server != null) {
            for (var serverLevel : server.getAllLevels()) {
                if (serverLevel.dimension().identifier().toString().equals(local.level().dimension().identifier().toString())) {
                    serverLevel.setBlock(pos, block.value().defaultBlockState(), 3);
                    return;
                }
            }
        }

        if (mc.getConnection() != null) {
            mc.getConnection().sendCommand(String.format(java.util.Locale.ROOT, "setblock %d %d %d %s", x, y, z, blockId));
        }
    }

    private static void applyRemoveBlock(int x, int y, int z) {
        var mc = Minecraft.getInstance();
        var local = mc.player;
        if (local == null) return;

        var pos = new BlockPos(x, y, z);

        var server = mc.getSingleplayerServer();
        if (server != null) {
            for (var serverLevel : server.getAllLevels()) {
                if (serverLevel.dimension().identifier().toString().equals(local.level().dimension().identifier().toString())) {
                    serverLevel.removeBlock(pos, false);
                    return;
                }
            }
        }

        if (mc.getConnection() != null) {
            mc.getConnection().sendCommand(String.format(java.util.Locale.ROOT, "setblock %d %d %d air", x, y, z));
        }
    }
}
