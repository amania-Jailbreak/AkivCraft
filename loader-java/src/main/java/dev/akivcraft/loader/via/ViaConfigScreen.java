package dev.akivcraft.loader.via;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class ViaConfigScreen extends Screen {
    private final Screen parent;
    private final List<VersionButton> versionButtons = new ArrayList<>();
    private static Method addRenderableWidgetMethod;
    private int pendingVersion;

    private static final int COLS = 4;
    private static final int BTN_W = 130;
    private static final int BTN_H = 20;
    private static final int GAP = 4;

    public ViaConfigScreen(Screen parent) {
        super(Component.literal("ViaVersion Settings"));
        this.parent = parent;
        this.pendingVersion = ViaConfigStore.getVersion();
    }

    @Override
    protected void init() {
        versionButtons.clear();

        var startY = 40;
        var startX = (this.width - (COLS * BTN_W + (COLS - 1) * GAP)) / 2;
        var i = 0;

        addVersionButton(-2, "AUTO (auto-detect)", startX, startY, i++);
        for (var v : ViaConfigStore.availableVersions()) {
            if (v.getVersion() == -2) continue;
            addVersionButton(v.getVersion(), v.getName(), startX, startY, i++);
        }

        var doneBtn = Button.builder(
            Component.literal("Done"),
            button -> {
                ViaConfigStore.setVersion(pendingVersion);
                ViaMultiplayerHook.refreshViaButton(parent);
                Minecraft.getInstance().setScreen(parent);
            }
        ).bounds(this.width / 2 - 104, this.height - 28, 100, 20).build();
        addWidgetReflective(doneBtn);

        var cancelBtn = Button.builder(
            Component.literal("Cancel"),
            button -> Minecraft.getInstance().setScreen(parent)
        ).bounds(this.width / 2 + 4, this.height - 28, 100, 20).build();
        addWidgetReflective(cancelBtn);
    }

    private void addVersionButton(int id, String name, int startX, int startY, int index) {
        var col = index % COLS;
        var row = index / COLS;
        var x = startX + col * (BTN_W + GAP);
        var y = startY + row * (BTN_H + GAP);
        if (y + BTN_H > this.height - 58) return;

        var btn = Button.builder(
            versionLabel(id, name),
            button -> {
                pendingVersion = id;
                refreshVersionLabels();
            }
        ).bounds(x, y, BTN_W, BTN_H).build();

        versionButtons.add(new VersionButton(id, name, btn));
        addWidgetReflective(btn);
    }

    private Component versionLabel(int id, String name) {
        return Component.literal((pendingVersion == id ? "> " : "  ") + name);
    }

    private void refreshVersionLabels() {
        for (var entry : versionButtons) {
            entry.button().setMessage(versionLabel(entry.id(), entry.name()));
        }
    }

    private void addWidgetReflective(AbstractWidget widget) {
        try {
            if (addRenderableWidgetMethod == null) {
                addRenderableWidgetMethod = Screen.class.getDeclaredMethod("addRenderableWidget", net.minecraft.client.gui.components.events.GuiEventListener.class);
                addRenderableWidgetMethod.setAccessible(true);
            }
            addRenderableWidgetMethod.invoke(this, widget);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        graphics.fill(0, 0, this.width, this.height, 0xff02060b);

        var font = Minecraft.getInstance().font;
        graphics.text(font, "ViaVersion - Select Protocol Version", this.width / 2, 16, 0xff76efff, true);

        for (var child : this.children()) {
            if (child instanceof AbstractWidget w && w.visible) {
                w.extractRenderState(graphics, mouseX, mouseY, tickDelta);
            }
        }

        graphics.text(font, "Selected: " + ViaConfigStore.versionName(pendingVersion),
            this.width / 2, this.height - 44, 0xffb8c9d5, true);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    private record VersionButton(int id, String name, Button button) {
    }
}
