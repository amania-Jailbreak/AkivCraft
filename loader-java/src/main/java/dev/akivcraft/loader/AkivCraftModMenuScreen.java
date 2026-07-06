package dev.akivcraft.loader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class AkivCraftModMenuScreen extends Screen {
    private static final int MARGIN = 18;
    private static final int HEADER_HEIGHT = 38;
    private static final int FOOTER_HEIGHT = 40;
    private static final int LIST_WIDTH = 230;
    private static final int ROW_HEIGHT = 34;

    private final Screen parent;
    private List<ModMetadata> mods = List.of();
    private int selectedIndex;
    private int listScroll;
    private int detailScroll;

    public AkivCraftModMenuScreen(Screen parent) {
        super(Component.literal("AkivCraft Mods"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        mods = ModMetadataStore.discover();
        selectedIndex = Math.min(selectedIndex, Math.max(0, mods.size() - 1));
        listScroll = clamp(listScroll, 0, maxListScroll());
        detailScroll = 0;

        var margin = margin();
        var buttonY = height - 30;
        var reloadWidth = Math.min(140, Math.max(100, width / 4));
        addRenderableWidget(
            Button.builder(Component.literal("Reload Node Mods"), button -> System.out.println("AkivCraft Mod Menu requested Node mod reload"))
                .bounds(margin, buttonY, reloadWidth, 20)
                .build()
        );
        addRenderableWidget(
            Button.builder(Component.literal("Settings"), button -> System.out.println("AkivCraft Mod Menu settings clicked"))
                .bounds(margin + reloadWidth + 8, buttonY, 90, 20)
                .build()
        );
        addRenderableWidget(
            Button.builder(Component.literal("Back"), button -> returnToParent())
                .bounds(width - margin - 90, buttonY, 90, 20)
                .build()
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        graphics.fill(0, 0, width, height, 0xff151719);
        super.extractRenderState(graphics, mouseX, mouseY, tickDelta);

        var margin = margin();
        var listWidth = listWidth();
        var gap = Math.max(6, margin / 2);

        graphics.text(font, "AkivCraft Mods", margin, 14, 0xfff2f4f8, true);
        graphics.text(font, mods.size() + " mods loaded", margin + 126, 16, 0xff8fa0ad, false);

        var top = HEADER_HEIGHT;
        var bottom = height - FOOTER_HEIGHT;
        var listX = margin;
        var listY = top;
        var listH = bottom - top;
        var detailX = listX + listWidth + gap;
        var detailY = top;
        var detailW = width - detailX - margin;
        var detailH = listH;

        drawPanel(graphics, listX, listY, listWidth, listH, "Mods");
        drawPanel(graphics, detailX, detailY, detailW, detailH, "Details");
        drawModList(graphics, listX, listY, listWidth, listH, mouseX, mouseY);
        drawDetails(graphics, detailX, detailY, detailW, detailH);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        var x = event.x();
        var y = event.y();
        if (event.button() == 0 && isInside(x, y, margin(), HEADER_HEIGHT + 22, listWidth(), height - FOOTER_HEIGHT - HEADER_HEIGHT - 24)) {
            var index = listScroll + (int) ((y - (HEADER_HEIGHT + 24)) / ROW_HEIGHT);
            if (index >= 0 && index < mods.size()) {
                selectedIndex = index;
                detailScroll = 0;
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        var top = HEADER_HEIGHT;
        var bottom = height - FOOTER_HEIGHT;
        var margin = margin();
        var listWidth = listWidth();
        var detailX = margin + listWidth + Math.max(6, margin / 2);

        if (isInside(mouseX, mouseY, margin, top, listWidth, bottom - top)) {
            listScroll = clamp(listScroll - (int) Math.signum(scrollY), 0, maxListScroll());
            return true;
        }

        if (isInside(mouseX, mouseY, detailX, top, width - detailX - margin, bottom - top)) {
            detailScroll = clamp(detailScroll - (int) Math.signum(scrollY) * 3, 0, maxDetailScroll(width - detailX - margin, bottom - top));
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        returnToParent();
    }

    private void drawPanel(GuiGraphicsExtractor graphics, int x, int y, int w, int h, String title) {
        graphics.fill(x, y, x + w, y + h, 0xff20242a);
        graphics.fill(x, y, x + w, y + 1, 0xff5a6470);
        graphics.fill(x, y + h - 1, x + w, y + h, 0xff0d0f12);
        graphics.fill(x, y, x + 1, y + h, 0xff5a6470);
        graphics.fill(x + w - 1, y, x + w, y + h, 0xff0d0f12);
        graphics.fill(x + 1, y + 1, x + w - 1, y + 21, 0xff2b3037);
        graphics.text(font, title, x + 8, y + 7, 0xffdbe6ef, false);
    }

    private void drawModList(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int mouseX, int mouseY) {
        var contentY = y + 24;
        var visibleRows = Math.max(1, (h - 26) / ROW_HEIGHT);
        var end = Math.min(mods.size(), listScroll + visibleRows);

        for (var index = listScroll; index < end; index++) {
            var mod = mods.get(index);
            var rowY = contentY + (index - listScroll) * ROW_HEIGHT;
            var hovered = isInside(mouseX, mouseY, x + 2, rowY, w - 4, ROW_HEIGHT - 2);
            var selected = index == selectedIndex;
            var bg = selected ? 0xff405064 : hovered ? 0xff303842 : 0xff242932;
            graphics.fill(x + 2, rowY, x + w - 2, rowY + ROW_HEIGHT - 2, bg);
            graphics.fill(x + 5, rowY + 5, x + 8, rowY + ROW_HEIGHT - 7, mod.enabled() ? 0xff64ffda : 0xff777777);
            graphics.text(font, trim(mod.name(), 24), x + 14, rowY + 6, mod.enabled() ? 0xfff4f8fb : 0xff9aa3aa, false);
            graphics.text(font, mod.version(), x + 14, rowY + 18, 0xff8fa0ad, false);
        }

        if (mods.isEmpty()) {
            graphics.text(font, "No AkivCraft mods found", x + 10, contentY + 8, 0xffaab4bd, false);
        }
    }

    private void drawDetails(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        if (mods.isEmpty()) return;

        var mod = mods.get(selectedIndex);
        var contentX = x + 12;
        var contentY = y + 30;
        var contentW = w - 24;
        var lineY = contentY - detailScroll * 10;

        lineY = drawLine(graphics, mod.name(), contentX, lineY, 0xfff4f8fb, true, 1);
        lineY = drawLine(graphics, "ID: " + mod.id(), contentX, lineY + 4, 0xff9fb0bd, false, 1);
        lineY = drawLine(graphics, "Version: " + mod.version(), contentX, lineY, 0xff9fb0bd, false, 1);
        lineY = drawLine(graphics, "Status: " + (mod.enabled() ? "Enabled" : "Disabled"), contentX, lineY, mod.enabled() ? 0xff64ffda : 0xffffd166, false, 1);
        graphics.fill(contentX, lineY + 5, contentX + contentW, lineY + 6, 0xff39414a);
        lineY += 14;

        var body = !mod.readme().isBlank() ? mod.readme() : mod.description();
        if (body == null || body.isBlank()) body = "No README.md or description provided.";
        drawMarkdown(graphics, body, contentX, lineY, contentW, y + h - 10);
    }

    private void drawMarkdown(GuiGraphicsExtractor graphics, String markdown, int x, int y, int width, int bottom) {
        var lineY = y;
        for (var rawLine : markdown.replace("\r", "").split("\n", -1)) {
            if (lineY > bottom) break;
            var line = rawLine.stripTrailing();
            if (line.isBlank()) {
                lineY += 8;
                continue;
            }

            var color = 0xffd7dde4;
            var scale = 1;
            var prefix = "";
            if (line.startsWith("### ")) {
                color = 0xffb8fff0;
                line = line.substring(4);
            } else if (line.startsWith("## ")) {
                color = 0xffd7fff7;
                line = line.substring(3);
                scale = 2;
            } else if (line.startsWith("# ")) {
                color = 0xffffffff;
                line = line.substring(2);
                scale = 2;
            } else if (line.startsWith("- ") || line.startsWith("* ")) {
                prefix = "- ";
                line = line.substring(2);
                color = 0xffc8d3dc;
            }

            for (var wrapped : wrap(prefix + stripInlineMarkdown(line), width)) {
                if (lineY > bottom) break;
                lineY = drawLine(graphics, wrapped, x, lineY, color, false, scale);
            }
            lineY += scale == 2 ? 4 : 1;
        }
    }

    private int drawLine(GuiGraphicsExtractor graphics, String text, int x, int y, int color, boolean shadow, int scale) {
        if (scale <= 1) {
            graphics.text(font, text, x, y, color, shadow);
            return y + 10;
        }
        graphics.text(font, text, x, y, color, true);
        return y + 14;
    }

    private List<String> wrap(String text, int maxWidth) {
        var result = new ArrayList<String>();
        var current = new StringBuilder();
        for (var word : text.split(" ")) {
            var next = current.isEmpty() ? word : current + " " + word;
            if (!current.isEmpty() && font.width(next) > maxWidth) {
                result.add(current.toString());
                current.setLength(0);
                current.append(word);
            } else {
                current.setLength(0);
                current.append(next);
            }
        }
        if (!current.isEmpty()) result.add(current.toString());
        return result.isEmpty() ? List.of("") : result;
    }

    private int maxListScroll() {
        var listH = height - FOOTER_HEIGHT - HEADER_HEIGHT;
        var visibleRows = Math.max(1, (listH - 26) / ROW_HEIGHT);
        return Math.max(0, mods.size() - visibleRows);
    }

    private int margin() {
        return clamp(width / 48, 8, MARGIN);
    }

    private int listWidth() {
        return clamp(width / 3, 150, Math.min(LIST_WIDTH, Math.max(150, width - margin() * 2 - 180)));
    }

    private int maxDetailScroll(int width, int height) {
        if (mods.isEmpty()) return 0;
        var mod = mods.get(selectedIndex);
        var body = !mod.readme().isBlank() ? mod.readme() : mod.description();
        var lines = 7;
        if (body != null) {
            for (var line : body.replace("\r", "").split("\n", -1)) {
                lines += Math.max(1, wrap(stripInlineMarkdown(line), Math.max(40, width - 24)).size());
            }
        }
        return Math.max(0, lines - Math.max(1, (height - 48) / 10));
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String trim(String value, int max) {
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 3)) + "...";
    }

    private static String stripInlineMarkdown(String value) {
        return value.replace("`", "").replace("**", "").replace("__", "");
    }

    private void returnToParent() {
        Minecraft.getInstance().setScreen(parent);
    }
}
