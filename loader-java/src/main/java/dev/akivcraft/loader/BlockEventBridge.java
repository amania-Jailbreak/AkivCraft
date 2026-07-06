package dev.akivcraft.loader;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class BlockEventBridge {
    private static final ThreadLocal<BreakSnapshot> BREAK_SNAPSHOT = new ThreadLocal<>();

    private BlockEventBridge() {
    }

    public static void afterUseItemOn(
        ServerPlayer player,
        Level level,
        ItemStack stack,
        InteractionHand hand,
        BlockHitResult hit,
        InteractionResult result
    ) {
        if (player == null || level == null || stack == null || hit == null) return;

        try {
            var targetPos = hit.getBlockPos();
            var face = hit.getDirection();
            var placePos = targetPos.relative(face);
            var targetState = level.getBlockState(targetPos);
            var placeState = level.getBlockState(placePos);
            var itemId = id(BuiltInRegistries.ITEM.getKey(stack.getItem()));
            var used = result != null && result.consumesAction();

            sendUseBlock(player, level, itemId, hand, targetPos, targetState, face, hit.getLocation(), placePos, placeState, used);
            if (used && stack.getItem() instanceof BlockItem && !placeState.isAir()) {
                sendPlaceBlock(player, level, itemId, hand, targetPos, targetState, face, hit.getLocation(), placePos, placeState);
            }
        } catch (Throwable error) {
            System.err.printf("AkivCraft block use hook failed: %s%n", error.getMessage());
        }
    }

    public static void beforeDestroyBlock(Object gameMode, BlockPos pos) {
        BREAK_SNAPSHOT.remove();
        if (pos == null) return;
        try {
            var player = player(gameMode);
            if (player == null) return;
            var level = player.level();
            BREAK_SNAPSHOT.set(new BreakSnapshot(pos, level.getBlockState(pos)));
        } catch (Throwable error) {
            System.err.printf("AkivCraft block break snapshot failed: %s%n", error.getMessage());
        }
    }

    public static void afterDestroyBlock(Object gameMode, BlockPos pos, boolean result) {
        try {
            var snapshot = BREAK_SNAPSHOT.get();
            BREAK_SNAPSHOT.remove();
            if (!result || pos == null) return;
            var player = player(gameMode);
            if (player == null) return;
            var level = player.level();
            if (snapshot == null || !snapshot.pos().equals(pos)) return;
            sendBreakBlock(player, level, pos, snapshot.state(), level.getBlockState(pos));
        } catch (Throwable error) {
            System.err.printf("AkivCraft block break hook failed: %s%n", error.getMessage());
        }
    }

    private static ServerPlayer player(Object gameMode) throws ReflectiveOperationException {
        if (!(gameMode instanceof net.minecraft.server.level.ServerPlayerGameMode mode)) return null;
        var playerField = net.minecraft.server.level.ServerPlayerGameMode.class.getDeclaredField("player");
        playerField.setAccessible(true);
        return (ServerPlayer) playerField.get(mode);
    }

    private static void sendUseBlock(
        ServerPlayer player,
        Level level,
        String itemId,
        InteractionHand hand,
        BlockPos targetPos,
        BlockState targetState,
        Direction face,
        Vec3 click,
        BlockPos placePos,
        BlockState placeState,
        boolean consumed
    ) {
        var json = common("use_block", player, level)
            + ",\"phase\":\"after\""
            + ",\"itemId\":\"" + escape(itemId) + "\""
            + ",\"hand\":\"" + escape(handName(hand)) + "\""
            + ",\"targetBlock\":\"" + escape(blockId(targetState)) + "\""
            + ",\"targetPos\":" + pos(targetPos)
            + ",\"face\":\"" + escape(faceName(face)) + "\""
            + ",\"click\":" + vec(click)
            + ",\"placePos\":" + pos(placePos)
            + ",\"placeBlock\":\"" + escape(blockId(placeState)) + "\""
            + ",\"consumed\":" + consumed
            + "}";
        BlockEventIpc.send(json);
    }

    private static void sendPlaceBlock(
        ServerPlayer player,
        Level level,
        String itemId,
        InteractionHand hand,
        BlockPos targetPos,
        BlockState targetState,
        Direction face,
        Vec3 click,
        BlockPos placePos,
        BlockState placeState
    ) {
        var json = common("place_block", player, level)
            + ",\"phase\":\"after\""
            + ",\"itemId\":\"" + escape(itemId) + "\""
            + ",\"hand\":\"" + escape(handName(hand)) + "\""
            + ",\"targetBlock\":\"" + escape(blockId(targetState)) + "\""
            + ",\"targetPos\":" + pos(targetPos)
            + ",\"face\":\"" + escape(faceName(face)) + "\""
            + ",\"click\":" + vec(click)
            + ",\"placePos\":" + pos(placePos)
            + ",\"placedBlock\":\"" + escape(blockId(placeState)) + "\""
            + "}";
        BlockEventIpc.send(json);
    }

    private static void sendBreakBlock(ServerPlayer player, Level level, BlockPos pos, BlockState brokenState, BlockState currentState) {
        var json = common("break_block", player, level)
            + ",\"phase\":\"after\""
            + ",\"breakPos\":" + pos(pos)
            + ",\"brokenBlock\":\"" + escape(blockId(brokenState)) + "\""
            + ",\"currentBlock\":\"" + escape(blockId(currentState)) + "\""
            + "}";
        BlockEventIpc.send(json);
    }

    private static String common(String type, ServerPlayer player, Level level) {
        var p = player.position();
        return "{\"type\":\"" + type + "\""
            + ",\"player\":\"" + escape(player.getGameProfile().getName()) + "\""
            + ",\"uuid\":\"" + escape(player.getUUID().toString()) + "\""
            + ",\"dimension\":\"" + escape(id(level.dimension().identifier())) + "\""
            + ",\"playerPos\":" + vec(p);
    }

    private static String blockId(BlockState state) {
        if (state == null) return "minecraft:air";
        return id(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    private static String id(Identifier identifier) {
        return identifier != null ? identifier.toString() : "unknown";
    }

    private static String handName(InteractionHand hand) {
        if (hand == null) return "unknown";
        return hand == InteractionHand.MAIN_HAND ? "main_hand" : "off_hand";
    }

    private static String faceName(Direction face) {
        return face != null ? face.getName() : "unknown";
    }

    private static String pos(BlockPos pos) {
        if (pos == null) return "null";
        return "{\"x\":" + pos.getX() + ",\"y\":" + pos.getY() + ",\"z\":" + pos.getZ() + "}";
    }

    private static String vec(Vec3 vec) {
        if (vec == null) return "null";
        return "{\"x\":" + vec.x + ",\"y\":" + vec.y + ",\"z\":" + vec.z + "}";
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record BreakSnapshot(BlockPos pos, BlockState state) {
    }
}
