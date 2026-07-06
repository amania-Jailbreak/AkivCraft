package dev.akivcraft.loader;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class ItemUseHandler {
    private ItemUseHandler() {
    }

    public static InteractionResult dispatch(
        List<ItemUseAction> actions,
        String itemId,
        ItemStack stack,
        Level level,
        Player player,
        InteractionHand hand
    ) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }

        boolean didSomething = false;
        for (var action : actions) {
            try {
                didSomething |= execute(action, itemId, stack, serverLevel, player);
            } catch (Throwable error) {
                System.err.printf("AkivCraft item use action failed for %s: %s%n", itemId, error.getMessage());
            }
        }

        return didSomething ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS;
    }

    private static boolean execute(ItemUseAction action, String itemId, ItemStack stack, ServerLevel level, Player player) {
        return switch (action) {
            case ItemUseAction.PotionEffect e -> applyPotion(e, player);
            case ItemUseAction.Heal h -> {
                player.heal(h.amount());
                yield true;
            }
            case ItemUseAction.Damage d -> {
                var item = stack.getItem();
                var source = item.getItemDamageSource(player);
                player.hurtServer(level, source, d.amount());
                yield true;
            }
            case ItemUseAction.Teleport t -> {
                var range = t.range();
                var random = level.getRandom();
                var dx = (random.nextDouble() - 0.5) * 2 * range;
                var dz = (random.nextDouble() - 0.5) * 2 * range;
                player.teleportTo(player.getX() + dx, player.getY(), player.getZ() + dz);
                yield true;
            }
            case ItemUseAction.Lightning l -> {
                var bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
                bolt.setPos(player.getX(), player.getY(), player.getZ());
                if (player instanceof ServerPlayer sp) bolt.setCause(sp);
                level.addFreshEntity(bolt);
                yield true;
            }
            case ItemUseAction.Explosion e -> {
                level.explode(player, player.getX(), player.getY(), player.getZ(), e.power(), Level.ExplosionInteraction.TNT);
                yield true;
            }
            case ItemUseAction.FireProjectile p -> spawnProjectile(p, level, player, stack);
            case ItemUseAction.Sound s -> playSound(s, level, player);
            case ItemUseAction.Particle p -> spawnParticles(p, level, player);
            case ItemUseAction.Consume c -> {
                var amount = c.amount() <= 0 ? 1 : c.amount();
                stack.shrink(amount);
                yield true;
            }
            case ItemUseAction.Cooldown c -> {
                player.getCooldowns().addCooldown(stack, c.ticks());
                yield true;
            }
            case ItemUseAction.Command c -> executeCommand(c.command(), level, player);
            case ItemUseAction.NodeCallback n -> {
                var look = player.getLookAngle();
                var eyePos = player.getEyePosition();
                var endPos = eyePos.add(look.scale(64.0));
                var clipContext = new ClipContext(eyePos, endPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);
                var hitResult = level.clip(clipContext);
                String rayHitX = "0", rayHitY = "0", rayHitZ = "0";
                boolean rayHit = false;
                if (hitResult.getType() == HitResult.Type.BLOCK) {
                    var loc = hitResult.getLocation();
                    rayHitX = String.format(java.util.Locale.ROOT, "%.3f", loc.x);
                    rayHitY = String.format(java.util.Locale.ROOT, "%.3f", loc.y);
                    rayHitZ = String.format(java.util.Locale.ROOT, "%.3f", loc.z);
                    rayHit = true;
                }
                ItemUseIpc.sendItemUse(
                    itemId, player.getName().getString(),
                    player.getX(), player.getY(), player.getZ(), n.event(),
                    look.x, look.y, look.z,
                    rayHit, rayHitX, rayHitY, rayHitZ
                );
                yield true;
            }
        };
    }

    private static boolean applyPotion(ItemUseAction.PotionEffect effect, Player player) {        var id = Identifier.tryParse(effect.effect());
        if (id == null) return false;
        var holder = BuiltInRegistries.MOB_EFFECT.get(id).orElse(null);
        if (holder == null) {
            System.err.printf("AkivCraft unknown potion effect: %s%n", effect.effect());
            return false;
        }
        player.addEffect(new MobEffectInstance(holder, effect.duration(), effect.amplifier()));
        return true;
    }

    private static boolean spawnProjectile(ItemUseAction.FireProjectile p, ServerLevel level, Player player, ItemStack weaponStack) {
        var look = player.getLookAngle();
        var type = p.projectile() == null ? "arrow" : p.projectile().toLowerCase();
        Projectile projectile = switch (type) {
            case "snowball" -> new Snowball(level, player, new ItemStack(Items.SNOWBALL));
            case "fireball", "large_fireball" -> new LargeFireball(level, player, look, 1);
            case "small_fireball" -> new SmallFireball(level, player, look);
            default -> new Arrow(level, player, new ItemStack(Items.ARROW), weaponStack);
        };
        projectile.shoot(look.x, look.y, look.z, p.speed(), 1.0f);
        level.addFreshEntity(projectile);
        return true;
    }

    private static boolean playSound(ItemUseAction.Sound s, ServerLevel level, Player player) {
        var id = Identifier.tryParse(s.sound());
        if (id == null) return false;
        var soundHolder = BuiltInRegistries.SOUND_EVENT.get(id).orElse(null);
        if (soundHolder == null) {
            System.err.printf("AkivCraft unknown sound: %s%n", s.sound());
            return false;
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), soundHolder, SoundSource.PLAYERS, s.volume(), s.pitch());
        return true;
    }

    @SuppressWarnings("unchecked")
    private static boolean spawnParticles(ItemUseAction.Particle p, ServerLevel level, Player player) {
        var id = Identifier.tryParse(p.particle());
        if (id == null) return false;
        var particleType = BuiltInRegistries.PARTICLE_TYPE.get(id).orElse(null);
        if (particleType == null) {
            System.err.printf("AkivCraft unknown particle: %s%n", p.particle());
            return false;
        }
        if (!(particleType instanceof ParticleOptions particleOptions)) {
            System.err.printf("AkivCraft particle type %s does not support spawning%n", p.particle());
            return false;
        }
        level.sendParticles(
            particleOptions,
            player.getX(), player.getY() + 1.0, player.getZ(),
            p.count(),
            1.0, 1.0, 1.0, 0.5
        );
        return true;
    }

    private static boolean executeCommand(String command, ServerLevel level, Player player) {
        var server = level.getServer();
        if (server == null) return false;
        CommandSourceStack source;
        if (player instanceof ServerPlayer sp) {
            source = sp.createCommandSourceStack();
        } else {
            return false;
        }
        server.getCommands().performPrefixedCommand(source, command);
        return true;
    }
}
