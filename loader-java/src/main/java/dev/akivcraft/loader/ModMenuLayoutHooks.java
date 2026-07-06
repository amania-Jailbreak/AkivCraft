package dev.akivcraft.loader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;

public final class ModMenuLayoutHooks {
    private ModMenuLayoutHooks() {
    }

    public static int addTitleModMenuButton(TitleScreen screen, int y, int spacing) {
        var nextY = y + spacing;
        addRenderableWidget(
            screen,
            Button.builder(Component.literal("Mod Menu"), button -> open(screen))
                .bounds(screen.width / 2 - 100, nextY, 200, 20)
                .build()
        );
        System.out.printf("AkivCraft added Mod Menu to TitleScreen layout at y=%d%n", nextY);
        return nextY;
    }

    public static void addPauseModMenuButton(PauseScreen screen, GridLayout.RowHelper rowHelper) {
        rowHelper.addChild(
            Button.builder(Component.literal("Mod Menu"), button -> open(screen))
                .width(98)
                .build()
        );
        System.out.println("AkivCraft added Mod Menu to PauseScreen layout");
    }

    private static void open(Screen parent) {
        Minecraft.getInstance().setScreen(new AkivCraftModMenuScreen(parent));
        System.out.println("AkivCraft Mod Menu opened");
    }

    private static void addRenderableWidget(Screen screen, Button button) {
        try {
            Method method = null;
            for (Class<?> type = screen.getClass(); type != null && method == null; type = type.getSuperclass()) {
                for (var candidate : type.getDeclaredMethods()) {
                    if ("addRenderableWidget".equals(candidate.getName()) && candidate.getParameterCount() == 1) {
                        method = candidate;
                        break;
                    }
                }
            }

            if (method == null) {
                throw new NoSuchMethodException("addRenderableWidget");
            }

            method.setAccessible(true);
            method.invoke(screen, button);
        } catch (ReflectiveOperationException | RuntimeException error) {
            throw new IllegalStateException("Failed to add AkivCraft Mod Menu button", error);
        }
    }
}
