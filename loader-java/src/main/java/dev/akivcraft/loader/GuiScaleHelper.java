package dev.akivcraft.loader;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.lang.reflect.Method;

public final class GuiScaleHelper {
    public static final int BASE_WIDTH = 854;
    public static final int BASE_HEIGHT = 480;

    private static Method pushMatrixMethod;
    private static Method popMatrixMethod;
    private static Method scaleMethod;
    private static Method poseMethod;

    private GuiScaleHelper() {
    }

    public static Scale hudScale(GuiGraphicsExtractor graphics) {
        var x = Math.max(0.5f, graphics.guiWidth() / (float) BASE_WIDTH);
        var y = Math.max(0.5f, graphics.guiHeight() / (float) BASE_HEIGHT);
        if (!Boolean.getBoolean("akivcraft.hud.nonUniformScale")) {
            var uniform = Math.min(x, y);
            x = uniform;
            y = uniform;
        }
        return new Scale(x, y);
    }

    public static boolean pushScale(GuiGraphicsExtractor graphics, Scale scale) {
        if (Math.abs(scale.x() - 1f) < 0.001f && Math.abs(scale.y() - 1f) < 0.001f) return false;

        try {
            var pose = pose(graphics);
            if (pushMatrixMethod == null) {
                pushMatrixMethod = pose.getClass().getMethod("pushMatrix");
                popMatrixMethod = pose.getClass().getMethod("popMatrix");
                scaleMethod = pose.getClass().getMethod("scale", float.class, float.class);
            }
            pushMatrixMethod.invoke(pose);
            scaleMethod.invoke(pose, scale.x(), scale.y());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void popScale(GuiGraphicsExtractor graphics) {
        try {
            if (popMatrixMethod != null) popMatrixMethod.invoke(pose(graphics));
        } catch (Throwable ignored) {
        }
    }

    private static Object pose(GuiGraphicsExtractor graphics) throws ReflectiveOperationException {
        if (poseMethod == null) poseMethod = GuiGraphicsExtractor.class.getMethod("pose");
        return poseMethod.invoke(graphics);
    }

    public static int sx(GuiGraphicsExtractor graphics, int value) {
        return Math.round(value * hudScale(graphics).x());
    }

    public static int sy(GuiGraphicsExtractor graphics, int value) {
        return Math.round(value * hudScale(graphics).y());
    }

    public record Scale(float x, float y) {
    }
}
