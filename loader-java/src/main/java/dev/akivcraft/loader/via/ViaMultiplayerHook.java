package dev.akivcraft.loader.via;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;

public final class ViaMultiplayerHook {
    private ViaMultiplayerHook() {
    }

    private static Method addRenderableWidgetMethod;

    private static Component label() {
        return Component.literal("Via: " + ViaConfigStore.versionName(ViaConfigStore.getVersion()));
    }

    @SuppressWarnings("unchecked")
    public static void addViaButton(JoinMultiplayerScreen screen) {
        try {
            var width = screen.width;
            var btn = Button.builder(
                label(),
                button -> Minecraft.getInstance().setScreen(new ViaConfigScreen(screen))
            ).bounds(width / 2 + 113, 8, 100, 20).build();

            if (addRenderableWidgetMethod == null) {
                addRenderableWidgetMethod = Screen.class.getDeclaredMethod("addRenderableWidget", net.minecraft.client.gui.components.events.GuiEventListener.class);
                addRenderableWidgetMethod.setAccessible(true);
            }
            addRenderableWidgetMethod.invoke(screen, btn);
        } catch (Throwable error) {
            System.err.println("AkivCraft failed to add Via button: " + error.getMessage());
        }
    }

    public static void refreshViaButton(Screen screen) {
        try {
            for (var child : screen.children()) {
                if (child instanceof AbstractWidget widget && widget.getMessage().getString().startsWith("Via:")) {
                    widget.setMessage(label());
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
