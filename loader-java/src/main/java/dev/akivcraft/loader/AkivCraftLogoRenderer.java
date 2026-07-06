package dev.akivcraft.loader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderPipelines;

public final class AkivCraftLogoRenderer {
    private AkivCraftLogoRenderer() {
    }

    public static void renderTitle(TitleScreen screen, GuiGraphicsExtractor graphics) {
        try {
            var maxWidth = Math.max(220, Math.round(graphics.guiWidth() * 0.58f));
            var maxHeight = Math.max(62, Math.round(graphics.guiHeight() * 0.20f));
            var y = Math.max(14, Math.round(graphics.guiHeight() * 0.045f));
            renderTitleHero(graphics, (graphics.guiWidth() - maxWidth) / 2, y, maxWidth, maxHeight);
        } catch (Throwable ignored) {
        }
    }

    public static void renderTitleHero(GuiGraphicsExtractor graphics, int x, int y, int maxWidth, int maxHeight) {
        try {
            var logo = AkivCraftLogoTexture.get();
            if (logo == null) {
                var font = Minecraft.getInstance().font;
                graphics.text(font, "AkivCraft", x, y + 18, 0xff66e7ff, true);
                return;
            }

            var scale = Math.min(maxWidth / (float) logo.contentWidth(), maxHeight / (float) logo.contentHeight());
            var width = Math.max(1, Math.round(logo.contentWidth() * scale));
            var height = Math.max(1, Math.round(logo.contentHeight() * scale));
            draw(graphics, logo, x, y, width, height);
        } catch (Throwable ignored) {
        }
    }

    public static void renderLoading(GuiGraphicsExtractor graphics) {
        try {
            var logo = AkivCraftLogoTexture.get();
            if (logo == null) {
                var font = Minecraft.getInstance().font;
                graphics.centeredText(font, "AkivCraft", graphics.guiWidth() / 2, graphics.guiHeight() / 2 - 5, 0xff66e7ff);
                return;
            }

            var maxWidth = Math.max(280, Math.round(graphics.guiWidth() * 0.74f));
            var maxHeight = Math.max(105, Math.round(graphics.guiHeight() * 0.34f));
            var scale = Math.min(maxWidth / (float) logo.contentWidth(), maxHeight / (float) logo.contentHeight());
            var width = Math.max(1, Math.round(logo.contentWidth() * scale));
            var height = Math.max(1, Math.round(logo.contentHeight() * scale));
            var x = (graphics.guiWidth() - width) / 2;
            var y = Math.max(18, (graphics.guiHeight() - height) / 2 - Math.round(graphics.guiHeight() * 0.08f));
            draw(graphics, logo, x, y, width, height);
        } catch (Throwable ignored) {
        }
    }

    private static void draw(GuiGraphicsExtractor graphics, AkivCraftLogoTexture.Entry logo, int x, int y, int width, int height) {
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            logo.identifier(),
            x,
            y,
            logo.contentX(),
            logo.contentY(),
            width,
            height,
            logo.contentWidth(),
            logo.contentHeight(),
            logo.width(),
            logo.height()
        );
    }
}
