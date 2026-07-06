package dev.akivcraft.loader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;

public final class AkivCraftTitleStatus {
    private static volatile long lastRefreshMillis;
    private static volatile int cachedModCount = -1;

    private AkivCraftTitleStatus() {
    }

    public static void render(TitleScreen screen, GuiGraphicsExtractor graphics) {
        try {
            var count = modCount();
            if (count <= 0) return;

            var font = Minecraft.getInstance().font;
            var text = count + " mods";
            graphics.text(font, text, 2, graphics.guiHeight() - 20, 0xffe6e6e6, true);
        } catch (Throwable ignored) {
        }
    }

    private static int modCount() {
        var now = System.currentTimeMillis();
        var cached = cachedModCount;
        if (cached >= 0 && now - lastRefreshMillis < 2_000L) return cached;

        try {
            cached = ModMetadataStore.discover().size();
        } catch (Throwable ignored) {
            cached = 0;
        }
        cachedModCount = cached;
        lastRefreshMillis = now;
        return cached;
    }
}
