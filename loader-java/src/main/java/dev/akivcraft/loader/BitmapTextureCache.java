package dev.akivcraft.loader;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class BitmapTextureCache {
    private static final int MAX_TEXTURES = 32;
    private static final Map<String, Entry> textures = new LinkedHashMap<>();

    private BitmapTextureCache() {
    }

    public static boolean render(GuiGraphicsExtractor graphics, HudBitmap bitmap) {
        try {
            var entry = textureFor(bitmap);
            var displayWidth = Math.max(1, Math.round(bitmap.width() * bitmap.scale()));
            var displayHeight = Math.max(1, Math.round(bitmap.height() * bitmap.scale()));
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                entry.identifier,
                bitmap.x(),
                bitmap.y(),
                0,
                0,
                displayWidth,
                displayHeight,
                bitmap.width(),
                bitmap.height(),
                bitmap.width(),
                bitmap.height()
            );
            return true;
        } catch (Throwable error) {
            System.err.printf("AkivCraft bitmap texture render failed: %s%n", error.getMessage());
            return false;
        }
    }

    private static Entry textureFor(HudBitmap bitmap) {
        var id = bitmap.id();
        var hash = hash(bitmap);
        var entry = textures.get(id);
        if (entry != null && entry.width == bitmap.width() && entry.height == bitmap.height()) {
            if (entry.hash != hash) {
                upload(entry.texture, bitmap);
                entry.hash = hash;
            }
            return entry;
        }

        if (entry != null) {
            Minecraft.getInstance().getTextureManager().release(entry.identifier);
            textures.remove(id);
        }

        pruneOldestIfNeeded();

        var identifier = Identifier.fromNamespaceAndPath("akivcraft", "dynamic_hud/" + safePath(id));
        var texture = new DynamicTexture(() -> "AkivCraft HUD bitmap " + id, bitmap.width(), bitmap.height(), false);
        upload(texture, bitmap);
        Minecraft.getInstance().getTextureManager().register(identifier, texture);

        entry = new Entry(identifier, texture, bitmap.width(), bitmap.height(), hash);
        textures.put(id, entry);
        return entry;
    }

    private static void upload(DynamicTexture texture, HudBitmap bitmap) {
        var image = texture.getPixels();
        if (image == null || image.getWidth() != bitmap.width() || image.getHeight() != bitmap.height()) {
            image = new NativeImage(bitmap.width(), bitmap.height(), false);
            texture.setPixels(image);
        }

        var pixels = bitmap.pixels();
        var palette = bitmap.palette();
        for (var y = 0; y < bitmap.height(); y++) {
            var row = y * bitmap.width();
            for (var x = 0; x < bitmap.width(); x++) {
                var paletteIndex = pixels[row + x] & 0xff;
                image.setPixel(x, y, paletteIndex < palette.length ? palette[paletteIndex] : 0x00000000);
            }
        }
        texture.upload();
    }

    private static int hash(HudBitmap bitmap) {
        var result = 17;
        result = 31 * result + bitmap.width();
        result = 31 * result + bitmap.height();
        result = 31 * result + Arrays.hashCode(bitmap.palette());
        result = 31 * result + Arrays.hashCode(bitmap.pixels());
        return result;
    }

    private static void pruneOldestIfNeeded() {
        while (textures.size() >= MAX_TEXTURES) {
            var iterator = textures.entrySet().iterator();
            if (!iterator.hasNext()) return;
            var entry = iterator.next().getValue();
            Minecraft.getInstance().getTextureManager().release(entry.identifier);
            iterator.remove();
        }
    }

    private static String safePath(String id) {
        var normalized = id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
        return normalized.isBlank() ? "bitmap" : normalized;
    }

    private static final class Entry {
        final Identifier identifier;
        final DynamicTexture texture;
        final int width;
        final int height;
        int hash;

        Entry(Identifier identifier, DynamicTexture texture, int width, int height, int hash) {
            this.identifier = identifier;
            this.texture = texture;
            this.width = width;
            this.height = height;
            this.hash = hash;
        }
    }
}
