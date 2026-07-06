package dev.akivcraft.loader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class AkivCraftTitleScreenUi {
    private AkivCraftTitleScreenUi() {
    }

    private static final List<AkivCraftButton> buttons = new ArrayList<>();
    private static Field onPressField;
    private static Method clearWidgetsMethod;
    private static Method addRenderableWidgetMethod;

    private static String translationKey(AbstractWidget widget) {
        ComponentContents contents = widget.getMessage().getContents();
        if (contents instanceof TranslatableContents tc) return tc.getKey();
        return "";
    }

    @SuppressWarnings("unchecked")
    private static Button.OnPress extractOnPress(Button button) {
        try {
            if (onPressField == null) {
                onPressField = Button.class.getDeclaredField("onPress");
                onPressField.setAccessible(true);
            }
            return (Button.OnPress) onPressField.get(button);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void clearWidgets(Screen screen) {
        try {
            if (clearWidgetsMethod == null) {
                clearWidgetsMethod = Screen.class.getDeclaredMethod("clearWidgets");
                clearWidgetsMethod.setAccessible(true);
            }
            clearWidgetsMethod.invoke(screen);
        } catch (Throwable ignored) {
        }
    }

    private static void addRenderableWidget(Screen screen, AbstractWidget widget) {
        try {
            if (addRenderableWidgetMethod == null) {
                addRenderableWidgetMethod = Screen.class.getDeclaredMethod("addRenderableWidget", GuiEventListener.class);
                addRenderableWidgetMethod.setAccessible(true);
            }
            addRenderableWidgetMethod.invoke(screen, widget);
        } catch (Throwable ignored) {
        }
    }

    public static void setup(TitleScreen screen) {
        try {
            Button.OnPress singlePress = null, multiPress = null, realmsPress = null;
            Button.OnPress optionsPress = null, quitPress = null;
            Button.OnPress langPress = null, accessPress = null;
            List<Button.OnPress> extraPresses = new ArrayList<>();
            List<Component> extraLabels = new ArrayList<>();

            for (var child : screen.children()) {
                if (!(child instanceof AbstractWidget w) || !w.visible) continue;
                var key = translationKey(w);
                if (!(w instanceof Button btn)) continue;
                if ("title.credits".equals(key)) continue;
                var onPress = extractOnPress(btn);
                switch (key) {
                    case "menu.singleplayer" -> singlePress = onPress;
                    case "menu.multiplayer" -> multiPress = onPress;
                    case "menu.online", "menu.realms" -> realmsPress = onPress;
                    case "menu.options" -> optionsPress = onPress;
                    case "menu.quit" -> quitPress = onPress;
                    case "options.language" -> langPress = onPress;
                    case "options.accessibility", "accessibility.onboarding.accessibility.button" -> accessPress = onPress;
                    default -> {
                        extraPresses.add(onPress);
                        extraLabels.add(w.getMessage());
                    }
                }
            }

            clearWidgets(screen);
            buttons.clear();

            var panelWidth = Math.max(220, Math.min(280, Math.round(screen.width * 0.28f)));
            var buttonWidth = Math.min(230, panelWidth - 32);
            var buttonHeight = 22;
            var gap = 8;
            var halfWidth = (buttonWidth - gap) / 2;
            var x = screen.width - panelWidth + (panelWidth - buttonWidth) / 2;

            var rowCount = 4 + extraPresses.size() + 1 + 1;
            var totalHeight = rowCount * buttonHeight + (rowCount - 1) * gap;
            var y = Math.max(62, (screen.height - totalHeight) / 2 + 28);

            if (singlePress != null) {
                var btn = AkivCraftButton.full(x, y, buttonWidth, buttonHeight, Component.translatable("menu.singleplayer"), singlePress);
                buttons.add(btn); addRenderableWidget(screen, btn); y += buttonHeight + gap;
            }
            if (multiPress != null) {
                var btn = AkivCraftButton.full(x, y, buttonWidth, buttonHeight, Component.translatable("menu.multiplayer"), multiPress);
                buttons.add(btn); addRenderableWidget(screen, btn); y += buttonHeight + gap;
            }
            if (realmsPress != null) {
                var btn = AkivCraftButton.full(x, y, buttonWidth, buttonHeight, Component.translatable("menu.online"), realmsPress);
                buttons.add(btn); addRenderableWidget(screen, btn); y += buttonHeight + gap;
            }
            if (optionsPress != null) {
                var btn = AkivCraftButton.full(x, y, buttonWidth, buttonHeight, Component.translatable("menu.options"), optionsPress);
                buttons.add(btn); addRenderableWidget(screen, btn); y += buttonHeight + gap;
            }
            for (var i = 0; i < extraPresses.size(); i++) {
                var btn = AkivCraftButton.full(x, y, buttonWidth, buttonHeight, extraLabels.get(i), extraPresses.get(i));
                buttons.add(btn); addRenderableWidget(screen, btn); y += buttonHeight + gap;
            }
            if (langPress != null) {
                var btn = AkivCraftButton.half(x, y, halfWidth, buttonHeight, Component.translatable("options.language"), langPress);
                buttons.add(btn); addRenderableWidget(screen, btn);
            }
            if (accessPress != null) {
                var btn = AkivCraftButton.half(x + halfWidth + gap, y, halfWidth, buttonHeight, Component.translatable("options.accessibility"), accessPress);
                buttons.add(btn); addRenderableWidget(screen, btn);
            }
            if (langPress != null || accessPress != null) y += buttonHeight + gap;
            if (quitPress != null) {
                var btn = AkivCraftButton.full(x, y, buttonWidth, buttonHeight, Component.translatable("menu.quit"), quitPress);
                buttons.add(btn); addRenderableWidget(screen, btn);
            }
        } catch (Throwable error) {
            System.err.printf("AkivCraft failed to setup title UI: %s%n", error.getMessage());
            error.printStackTrace();
        }
    }

    public static void layout(TitleScreen screen) {
        var panelWidth = Math.max(220, Math.min(280, Math.round(screen.width * 0.28f)));
        var buttonWidth = Math.min(230, panelWidth - 32);
        var buttonHeight = 22;
        var gap = 8;
        var halfWidth = (buttonWidth - gap) / 2;
        var x = screen.width - panelWidth + (panelWidth - buttonWidth) / 2;

        var rowCount = buttons.size();
        var totalHeight = rowCount * buttonHeight + Math.max(0, rowCount - 1) * gap;
        var y = Math.max(62, (screen.height - totalHeight) / 2 + 28);

        for (var btn : buttons) {
            if (btn.isHalf()) {
                btn.setRectangle(halfWidth, buttonHeight, x, y);
            } else {
                btn.setRectangle(buttonWidth, buttonHeight, x, y);
                y += buttonHeight + gap;
            }
        }
    }

    public static void renderBackground(TitleScreen screen, GuiGraphicsExtractor graphics) {
        try {
            var width = graphics.guiWidth();
            var height = graphics.guiHeight();
            graphics.fill(0, 0, width, height, 0xff02060b);

            var panelWidth = Math.max(240, Math.min(310, Math.round(width * 0.32f)));
            var panelX = width - panelWidth;

            graphics.fillGradient(0, 0, panelX, height, 0x6600273f, 0x00000000);
            graphics.fillGradient(0, 0, panelX, Math.round(height * 0.42f), 0x3314d9ff, 0x00000000);

            graphics.fill(panelX, 0, width, height, 0xcc050912);
            graphics.fill(panelX, 0, panelX + 1, height, 0xff20dfff);
            graphics.fill(panelX + 3, 0, panelX + 4, height, 0x5520dfff);

            graphics.fill(26, height - 48, Math.min(width - panelWidth - 28, 460), height - 47, 0xff13d9ff);
            graphics.fill(26, height - 42, Math.min(width - panelWidth - 92, 330), height - 41, 0x8848f6ff);

            AkivCraftLogoRenderer.renderTitleHero(graphics, Math.max(28, Math.round(width * 0.055f)), Math.max(28, Math.round(height * 0.11f)), Math.max(260, Math.round(width * 0.42f)), Math.max(90, Math.round(height * 0.22f)));
        } catch (Throwable ignored) {
        }
    }

    public static void renderForeground(TitleScreen screen, GuiGraphicsExtractor graphics) {
        try {
            var font = Minecraft.getInstance().font;
            var width = graphics.guiWidth();
            var height = graphics.guiHeight();
            var panelWidth = Math.max(240, Math.min(310, Math.round(width * 0.32f)));
            var panelX = width - panelWidth;
            var count = ModMetadataStore.discover().size();

            graphics.text(font, "AKIVCRAFT", panelX + 18, 24, 0xff76efff, true);
            graphics.text(font, count + " mods loaded", panelX + 18, 38, 0xffb8c9d5, false);
            graphics.text(font, "Minecraft 26.1.2", 28, height - 32, 0xffa7b6c0, false);
        } catch (Throwable ignored) {
        }
    }

    public static void renderWidgets(TitleScreen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        try {
            for (var btn : buttons) {
                if (btn.visible) {
                    btn.extractRenderState(graphics, mouseX, mouseY, tickDelta);
                }
            }
        } catch (Throwable error) {
            System.err.printf("AkivCraft failed to render title widgets: %s%n", error.getMessage());
        }
    }
}
