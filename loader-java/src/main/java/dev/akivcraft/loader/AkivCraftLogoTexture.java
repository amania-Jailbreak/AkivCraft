package dev.akivcraft.loader;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AkivCraftLogoTexture {
    private static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath("akivcraft", "gui/logo");
    private static volatile Entry entry;
    private static volatile boolean attempted;

    private AkivCraftLogoTexture() {
    }

    public static Entry get() {
        var current = entry;
        if (current != null || attempted) return current;

        attempted = true;
        try {
            try (var input = openLogo()) {
                if (input == null) return null;
                return register(input);
            }
        } catch (Throwable error) {
            System.err.printf("AkivCraft failed to load logo texture: %s%n", error.getMessage());
            return null;
        }
    }

    private static Entry register(InputStream input) throws java.io.IOException {
        var image = NativeImage.read(input);
        var bounds = contentBounds(image);
        var texture = new DynamicTexture(() -> "AkivCraft logo", image);
        texture.upload();
        Minecraft.getInstance().getTextureManager().register(IDENTIFIER, texture);
        var current = new Entry(IDENTIFIER, image.getWidth(), image.getHeight(), bounds.x(), bounds.y(), bounds.width(), bounds.height());
        entry = current;
        return current;
    }

    private static Bounds contentBounds(NativeImage image) {
        var minX = image.getWidth();
        var minY = image.getHeight();
        var maxX = -1;
        var maxY = -1;

        for (var y = 0; y < image.getHeight(); y++) {
            for (var x = 0; x < image.getWidth(); x++) {
                if (((image.getPixel(x, y) >>> 24) & 0xff) <= 4) continue;
                if (x < minX) minX = x;
                if (y < minY) minY = y;
                if (x > maxX) maxX = x;
                if (y > maxY) maxY = y;
            }
        }

        if (maxX < minX || maxY < minY) return new Bounds(0, 0, image.getWidth(), image.getHeight());
        return new Bounds(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private static InputStream openLogo() throws java.io.IOException {
        var resource = AkivCraftLogoTexture.class.getResourceAsStream("/assets/akivcraft/logo.png");
        if (resource != null) return resource;

        var file = logoFile();
        if (Files.isRegularFile(file)) return Files.newInputStream(file);

        System.err.println("AkivCraft logo not found in jar resource /assets/akivcraft/logo.png or file " + file.toAbsolutePath());
        return null;
    }

    private static Path logoFile() {
        return LoaderConfig.fromSystemProperties().akivcraftHome().resolve("assets").resolve("akivcraft-logo.png");
    }

    public record Entry(Identifier identifier, int width, int height, int contentX, int contentY, int contentWidth, int contentHeight) {
    }

    private record Bounds(int x, int y, int width, int height) {
    }
}
