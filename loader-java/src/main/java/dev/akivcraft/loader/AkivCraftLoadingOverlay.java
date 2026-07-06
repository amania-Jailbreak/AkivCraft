package dev.akivcraft.loader;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;

public final class AkivCraftLoadingOverlay {
    private AkivCraftLoadingOverlay() {
    }

    public static void render(LoadingOverlay overlay, GuiGraphicsExtractor graphics) {
        try {
            graphics.nextStratum();
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0xff000000);
            AkivCraftLogoRenderer.renderLoading(graphics);
            renderProgress(overlay, graphics);
        } catch (Throwable ignored) {
        }
    }

    private static void renderProgress(LoadingOverlay overlay, GuiGraphicsExtractor graphics) {
        var width = Math.max(160, Math.min(360, Math.round(graphics.guiWidth() * 0.42f)));
        var height = 4;
        var x = (graphics.guiWidth() - width) / 2;
        var y = Math.min(graphics.guiHeight() - 42, Math.round(graphics.guiHeight() * 0.64f));
        var progress = Math.max(0.0f, Math.min(1.0f, progress(overlay)));
        var fill = Math.round((width - 2) * progress);

        graphics.fill(x, y, x + width, y + height, 0xffffffff);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xff000000);
        graphics.fill(x + 1, y + 1, x + 1 + fill, y + height - 1, 0xffffffff);
    }

    private static float progress(LoadingOverlay overlay) {
        try {
            var field = LoadingOverlay.class.getDeclaredField("currentProgress");
            field.setAccessible(true);
            return field.getFloat(overlay);
        } catch (Throwable ignored) {
            return AkivCraftBootCoordinator.isPrepared() ? 1.0f : 0.35f;
        }
    }
}
