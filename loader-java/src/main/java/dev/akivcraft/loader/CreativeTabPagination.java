package dev.akivcraft.loader;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

public final class CreativeTabPagination {
    private static final int TABS_PER_ROW = 7;
    private static volatile int currentPage = 0;
    private static volatile boolean texturesExpanded;

    private CreativeTabPagination() {
    }

    public static void init() {
    }

    private static void ensureTexturesExpanded() {
        if (texturesExpanded) return;
        texturesExpanded = true;
        try {
            expandTextureArrays();
        } catch (Throwable error) {
            System.err.printf("AkivCraft failed to expand creative tab textures: %s%n", error.getMessage());
        }
    }

    public static int computeTabX(Object screen, CreativeModeTab tab) {
        ensureTexturesExpanded();
        int column = tab.column();
        int page = column / TABS_PER_ROW;

        if (page != currentPage) return -10000;

        int displayColumn = column % TABS_PER_ROW;
        int imageWidth = getImageWidth(screen);
        int x = 27 * displayColumn;
        if (tab.isAlignedRight()) {
            x = imageWidth - 27 * (TABS_PER_ROW - displayColumn) + 1;
        }
        return x;
    }

    public static boolean shouldRenderTab(CreativeModeTab tab) {
        int page = tab.column() / TABS_PER_ROW;
        return page == currentPage;
    }

    public static boolean handleKeyPress(int key) {
        if (key == 266) {
            prevPage();
            return true;
        }
        if (key == 267) {
            nextPage();
            return true;
        }
        return false;
    }

    public static void nextPage() {
        currentPage++;
        System.out.println("AkivCraft creative tab page: " + currentPage);
    }

    public static void prevPage() {
        if (currentPage > 0) currentPage--;
        System.out.println("AkivCraft creative tab page: " + currentPage);
    }

    public static int getPageCount() {
        try {
            int maxColumn = 0;
            for (var tab : net.minecraft.world.item.CreativeModeTabs.tabs()) {
                maxColumn = Math.max(maxColumn, tab.column());
            }
            return maxColumn / TABS_PER_ROW + 1;
        } catch (Throwable ignored) {
            return 1;
        }
    }

    public static int getCurrentPage() {
        return currentPage;
    }

    public static void renderPageButtons(Object screenObj, GuiGraphicsExtractor graphics, int leftPos, int topPos) {
    }

    public static void renderPageButtons(Object screenObj, Object graphicsObj) {
        ensureTexturesExpanded();
        try {
            var screen = (CreativeModeInventoryScreen) screenObj;
            var graphics = (GuiGraphicsExtractor) graphicsObj;
            int pages = getPageCount();
            if (pages <= 1) return;

            var font = getMinecraftFont(screen);
            if (font == null) return;
            int lp = leftPos(screen);
            int tp = topPos(screen);
            int imgW = imageWidth(screen);

            int centerX = lp + imgW / 2;
            int btnY = tp - 44;

            int nextColor = getCurrentPage() < pages - 1 ? 0xFFFFFFFF : 0x66888888;
            int prevColor = getCurrentPage() > 0 ? 0xFFFFFFFF : 0x66888888;
            String pageText = (getCurrentPage() + 1) + "/" + pages;

            int textWidth = font.width(pageText);
            graphics.text(font, "<", centerX - textWidth / 2 - 12, btnY, prevColor, false);
            graphics.text(font, pageText, centerX - textWidth / 2, btnY, 0xFFCCCCCC, false);
            graphics.text(font, ">", centerX + textWidth / 2 + 6, btnY, nextColor, false);
        } catch (Throwable ignored) {
        }
    }

    public static boolean handleMouseClick(Object screenObj, Object eventObj) {
        ensureTexturesExpanded();
        try {
            var screen = (CreativeModeInventoryScreen) screenObj;
            var event = (net.minecraft.client.input.MouseButtonEvent) eventObj;
            if (event.button() != 0) return false;

            int lp = leftPos(screen);
            int tp = topPos(screen);
            int imgW = imageWidth(screen);
            double mx = event.x();
            double my = event.y();

            int pages = getPageCount();
            if (pages <= 1) return false;

            var font = getMinecraftFont(screen);
            if (font == null) return false;

            int centerX = lp + imgW / 2;
            int btnY = tp - 44;
            String pageText = (getCurrentPage() + 1) + "/" + pages;
            int textWidth = font.width(pageText);

            int prevX = centerX - textWidth / 2 - 12;
            int nextX = centerX + textWidth / 2 + 6;

            if (getCurrentPage() < pages - 1 && mx >= nextX - 2 && mx <= nextX + 10 && my >= btnY - 2 && my <= btnY + 10) {
                nextPage();
                return true;
            }
            if (getCurrentPage() > 0 && mx >= prevX - 2 && mx <= prevX + 10 && my >= btnY - 2 && my <= btnY + 10) {
                prevPage();
                return true;
            }
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int leftPos(Object screen) {
        return getIntField(screen, "leftPos");
    }

    private static int topPos(Object screen) {
        return getIntField(screen, "topPos");
    }

    private static int imageWidth(Object screen) {
        return getIntField(screen, "imageWidth");
    }

    private static net.minecraft.client.gui.Font getMinecraftFont(Object screen) {
        try {
            var mcField = findField(screen.getClass(), "minecraft");
            if (mcField == null) return null;
            mcField.setAccessible(true);
            var mc = mcField.get(screen);
            if (mc == null) return null;
            var fontField = mc.getClass().getField("font");
            fontField.setAccessible(true);
            return (net.minecraft.client.gui.Font) fontField.get(mc);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int getIntField(Object owner, String fieldName) {
        try {
            var field = findField(owner.getClass(), fieldName);
            if (field == null) return 0;
            field.setAccessible(true);
            return field.getInt(owner);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static java.lang.reflect.Field findField(Class<?> type, String name) {
        while (type != null) {
            try {
                return type.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    private static void expandTextureArrays() {
        int newSize = 21;
        expandArray("SELECTED_TOP_TABS", newSize);
        expandArray("UNSELECTED_TOP_TABS", newSize);
        expandArray("SELECTED_BOTTOM_TABS", newSize);
        expandArray("UNSELECTED_BOTTOM_TABS", newSize);
    }

    private static void expandArray(String fieldName, int newSize) {
        try {
            var field = CreativeModeInventoryScreen.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            var original = (Identifier[]) field.get(null);
            if (original == null || original.length >= newSize) return;

            var expanded = new Identifier[newSize];
            for (var i = 0; i < newSize; i++) {
                expanded[i] = i < original.length ? original[i] : original[original.length - 1];
            }

            var unsafe = getUnsafe();
            if (unsafe != null) {
                unsafe.putObject(unsafe.staticFieldBase(field), unsafe.staticFieldOffset(field), expanded);
            }
            System.out.printf("AkivCraft expanded %s from %d to %d entries%n", fieldName, original.length, newSize);
        } catch (Exception e) {
            System.err.printf("AkivCraft failed to expand %s: %s%n", fieldName, e.getMessage());
        }
    }

    private static int getImageWidth(Object screen) {
        try {
            Field field = null;
            Class<?> type = screen.getClass();
            while (type != null) {
                try {
                    field = type.getDeclaredField("imageWidth");
                    break;
                } catch (NoSuchFieldException ignored) {
                    type = type.getSuperclass();
                }
            }
            if (field == null) return 176;
            field.setAccessible(true);
            return field.getInt(screen);
        } catch (Exception e) {
            return 176;
        }
    }

    private static Unsafe getUnsafe() {
        try {
            var field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (Exception e) {
            return null;
        }
    }
}
