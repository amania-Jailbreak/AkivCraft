package dev.akivcraft.loader;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class AkivCraftButton extends Button {
    private static final int BG = 0xff040810;
    private static final int BG_HOVER = 0xff0a1828;
    private static final int BORDER = 0xff13d9ff;
    private static final int BORDER_DIM = 0x5513d9ff;
    private static final int TEXT_COLOR = 0xffb8c9d5;
    private static final int TEXT_HOVER = 0xff76efff;

    private final boolean half;

    public boolean isHalf() { return half; }

    private AkivCraftButton(int x, int y, int width, int height, Component message, OnPress onPress, boolean half) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.half = half;
    }

    public static AkivCraftButton full(int x, int y, int width, int height, Component message, OnPress onPress) {
        return new AkivCraftButton(x, y, width, height, message, onPress, false);
    }

    public static AkivCraftButton half(int x, int y, int width, int height, Component message, OnPress onPress) {
        return new AkivCraftButton(x, y, width, height, message, onPress, true);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float tickDelta) {
        var x = getX();
        var y = getY();
        var w = getWidth();
        var h = getHeight();
        var hover = isHovered();

        g.fill(x, y, x + w, y + h, hover ? BG_HOVER : BG);
        g.fill(x, y, x + w, y + 1, hover ? BORDER : BORDER_DIM);
        g.fill(x, y + h - 1, x + w, y + h, hover ? BORDER : BORDER_DIM);
        g.fill(x, y, x + 1, y + h, hover ? BORDER : BORDER_DIM);
        g.fill(x + w - 1, y, x + w, y + h, hover ? BORDER : BORDER_DIM);

        var font = net.minecraft.client.Minecraft.getInstance().font;
        var msg = getMessage();
        var tx = x + (w - font.width(msg)) / 2;
        var ty = y + (h - font.lineHeight) / 2;
        g.text(font, msg, tx, ty, hover ? TEXT_HOVER : TEXT_COLOR, false);
    }
}
