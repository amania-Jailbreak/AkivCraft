package dev.akivcraft.loader;

import net.minecraft.client.Minecraft;

import java.util.concurrent.ConcurrentLinkedQueue;

public final class PlayerActionQueue {
    private static final ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<>();

    private PlayerActionQueue() {
    }

    public static void enqueue(Runnable action) {
        queue.add(action);
    }

    public static void processTick(Minecraft minecraft) {
        Runnable action;
        while ((action = queue.poll()) != null) {
            try {
                action.run();
            } catch (Throwable error) {
                System.err.printf("AkivCraft player action failed: %s%n", error.getMessage());
            }
        }
    }
}
