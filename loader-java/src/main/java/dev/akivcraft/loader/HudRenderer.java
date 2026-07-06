package dev.akivcraft.loader;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.Base64;

public final class HudRenderer {
    private HudRenderer() {
    }

    public static void render(Gui gui, GuiGraphicsExtractor graphics) {
        try {
            if (Minecraft.getInstance().options.hideGui) return;
            var font = gui.getFont();
            graphics.nextStratum();
            var hudScale = GuiScaleHelper.hudScale(graphics);
            var scaled = GuiScaleHelper.pushScale(graphics, hudScale);
            var fallbackScale = !scaled && (Math.abs(hudScale.x() - 1f) >= 0.001f || Math.abs(hudScale.y() - 1f) >= 0.001f);
            try {
            for (var item : NodeHudClient.items()) {
                if ("rect".equals(item.kind())) {
                    graphics.fill(x(item.x(), hudScale, fallbackScale), y(item.y(), hudScale, fallbackScale), x(item.x() + item.width(), hudScale, fallbackScale), y(item.y() + item.height(), hudScale, fallbackScale), item.color());
                    continue;
                }

                if ("bitmapRle".equals(item.kind())) {
                    renderBitmapRle(graphics, item, hudScale, fallbackScale);
                    continue;
                }

                if ("sprite".equals(item.kind())) {
                    renderSprite(graphics, item, hudScale, fallbackScale);
                    continue;
                }

                var lines = item.text().split("\\R", -1);
                var width = 0;
                for (var line : lines) width = Math.max(width, font.width(line));
                var height = lines.length * font.lineHeight;

                if ((item.background() >>> 24) != 0) {
                    graphics.fill(x(item.x() - 3, hudScale, fallbackScale), y(item.y() - 3, hudScale, fallbackScale), x(item.x() + width + 3, hudScale, fallbackScale), y(item.y() + height + 2, hudScale, fallbackScale), item.background());
                }

                for (var i = 0; i < lines.length; i++) {
                    graphics.text(font, lines[i], x(item.x(), hudScale, fallbackScale), y(item.y() + i * font.lineHeight, hudScale, fallbackScale), item.color(), item.shadow());
                }
            }

            var bitmaps = UdpHudClient.hasRecentFrame() ? UdpHudClient.bitmaps() : BinaryHudClient.bitmaps();
            for (var bitmap : bitmaps) {
                renderBitmap(graphics, bitmap, hudScale, fallbackScale);
            }
            } finally {
                if (scaled) GuiScaleHelper.popScale(graphics);
            }
        } catch (Throwable error) {
            System.err.printf("AkivCraft HUD render failed: %s%n", error.getMessage());
        }
    }

    private static void renderBitmap(GuiGraphicsExtractor graphics, HudBitmap bitmap, GuiScaleHelper.Scale hudScale, boolean fallbackScale) {
        if (!fallbackScale && BitmapTextureCache.render(graphics, bitmap)) return;

        var pixels = bitmap.pixels();
        var palette = bitmap.palette();
        var scale = bitmap.scale();
        var displayWidth = Math.max(1, Math.round(bitmap.width() * scale));
        var displayHeight = Math.max(1, Math.round(bitmap.height() * scale));
        var invScale = 1f / scale;

        for (var screenRow = 0; screenRow < displayHeight; screenRow++) {
            var srcRow = Math.min(bitmap.height() - 1, (int) (screenRow * invScale));
            var screenColumn = 0;
            while (screenColumn < displayWidth) {
                var srcColumn = Math.min(bitmap.width() - 1, (int) (screenColumn * invScale));
                var paletteIndex = pixels[srcRow * bitmap.width() + srcColumn] & 0xff;
                var runStart = screenColumn;
                screenColumn++;
                while (screenColumn < displayWidth) {
                    var nextSrcColumn = Math.min(bitmap.width() - 1, (int) (screenColumn * invScale));
                    if ((pixels[srcRow * bitmap.width() + nextSrcColumn] & 0xff) != paletteIndex) break;
                    screenColumn++;
                }
                if (paletteIndex < palette.length) {
                    graphics.fill(
                        x(bitmap.x() + runStart, hudScale, fallbackScale),
                        y(bitmap.y() + screenRow, hudScale, fallbackScale),
                        x(bitmap.x() + screenColumn, hudScale, fallbackScale),
                        y(bitmap.y() + screenRow + 1, hudScale, fallbackScale),
                        palette[paletteIndex]
                    );
                }
            }
        }
    }

    private static void renderSprite(GuiGraphicsExtractor graphics, HudItem item, GuiScaleHelper.Scale hudScale, boolean fallbackScale) {
        try {
            var texture = Identifier.parse(item.text());
            var u = item.color();
            var v = item.background();
            if (u == 0 && v == 0 && item.width() > 0 && item.height() > 0) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, texture, x(item.x(), hudScale, fallbackScale), y(item.y(), hudScale, fallbackScale), sizeX(item.width(), hudScale, fallbackScale), sizeY(item.height(), hudScale, fallbackScale));
            } else {
                graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x(item.x(), hudScale, fallbackScale), y(item.y(), hudScale, fallbackScale), u, v, sizeX(item.width(), hudScale, fallbackScale), sizeY(item.height(), hudScale, fallbackScale), item.width(), item.height());
            }
        } catch (Throwable error) {
            System.err.printf("AkivCraft sprite render failed: %s%n", error.getMessage());
        }
    }

    private static void renderBitmapRle(GuiGraphicsExtractor graphics, HudItem item, GuiScaleHelper.Scale hudScale, boolean fallbackScale) {
        var separator = item.text().indexOf('|');
        if (separator < 0) return;

        var paletteText = item.text().substring(0, separator);
        var runsText = item.text().substring(separator + 1);
        var paletteParts = paletteText.isEmpty() ? new String[0] : paletteText.split(",");
        var palette = new int[paletteParts.length];
        for (var i = 0; i < paletteParts.length; i++) {
            palette[i] = (int) Long.parseLong(paletteParts[i]);
        }

        var runs = Base64.getDecoder().decode(runsText);
        var pixel = 0;
        for (var i = 0; i + 3 < runs.length; i += 4) {
            var count = ((runs[i] & 0xff) << 8) | (runs[i + 1] & 0xff);
            var paletteIndex = ((runs[i + 2] & 0xff) << 8) | (runs[i + 3] & 0xff);
            if (paletteIndex >= palette.length || count <= 0) continue;

            var remaining = count;
            while (remaining > 0 && pixel < item.width() * item.height()) {
                var row = pixel / item.width();
                var column = pixel % item.width();
                var span = Math.min(remaining, item.width() - column);
                graphics.fill(
                    x(item.x() + column, hudScale, fallbackScale),
                    y(item.y() + row, hudScale, fallbackScale),
                    x(item.x() + column + span, hudScale, fallbackScale),
                    y(item.y() + row + 1, hudScale, fallbackScale),
                    palette[paletteIndex]
                );
                pixel += span;
                remaining -= span;
            }
        }
    }

    private static int x(int value, GuiScaleHelper.Scale scale, boolean fallbackScale) {
        return fallbackScale ? Math.round(value * scale.x()) : value;
    }

    private static int y(int value, GuiScaleHelper.Scale scale, boolean fallbackScale) {
        return fallbackScale ? Math.round(value * scale.y()) : value;
    }

    private static int sizeX(int value, GuiScaleHelper.Scale scale, boolean fallbackScale) {
        return fallbackScale ? Math.max(1, Math.round(value * scale.x())) : value;
    }

    private static int sizeY(int value, GuiScaleHelper.Scale scale, boolean fallbackScale) {
        return fallbackScale ? Math.max(1, Math.round(value * scale.y())) : value;
    }
}
