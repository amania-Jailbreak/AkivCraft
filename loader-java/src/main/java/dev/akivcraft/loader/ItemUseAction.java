package dev.akivcraft.loader;

public sealed interface ItemUseAction {
    record PotionEffect(String effect, int duration, int amplifier) implements ItemUseAction {}
    record Heal(float amount) implements ItemUseAction {}
    record Damage(float amount) implements ItemUseAction {}
    record Teleport(double range) implements ItemUseAction {}
    record Lightning() implements ItemUseAction {}
    record Explosion(float power) implements ItemUseAction {}
    record FireProjectile(String projectile, float speed, float damage) implements ItemUseAction {}
    record Sound(String sound, float volume, float pitch) implements ItemUseAction {}
    record Particle(String particle, int count) implements ItemUseAction {}
    record Consume(int amount) implements ItemUseAction {}
    record Cooldown(int ticks) implements ItemUseAction {}
    record Command(String command) implements ItemUseAction {}
    record NodeCallback(String event) implements ItemUseAction {}
}
