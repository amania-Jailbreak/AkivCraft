package dev.akivcraft.loader;

public record HudBitmap(String id, int x, int y, int width, int height, float scale, int[] palette, byte[] pixels) {
}
