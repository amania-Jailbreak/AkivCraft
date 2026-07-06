package dev.akivcraft.loader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.WeakHashMap;

public final class ModMenuInjector {
    private static final Map<Object, ButtonPlacement> INJECTED_SCREENS = new WeakHashMap<>();
    private static volatile boolean loggedFailure;

    private ModMenuInjector() {
    }

    public static void inject(Object screen) {
        if (screen == null) {
            return;
        }

        try {
            var component = literalComponent("Mod Menu");
            var placement = calculatePlacement(screen);
            if (placement == null) {
                return;
            }

            synchronized (INJECTED_SCREENS) {
                if (placement.equals(INJECTED_SCREENS.get(screen))) {
                    return;
                }
            }

            var button = createButton(screen, component, placement);
            var addRenderableWidget = findAddRenderableWidget(screen.getClass(), button.getClass());
            addRenderableWidget.setAccessible(true);
            addRenderableWidget.invoke(screen, button);

            synchronized (INJECTED_SCREENS) {
                INJECTED_SCREENS.put(screen, placement);
            }

            System.out.printf(
                "AkivCraft added Mod Menu button to %s via %s#%s at x=%d y=%d%n",
                screen.getClass().getName(),
                addRenderableWidget.getDeclaringClass().getName(),
                addRenderableWidget.getName(),
                placement.x(),
                placement.y()
            );
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            if (!loggedFailure) {
                loggedFailure = true;
                System.err.printf("AkivCraft could not add Mod Menu button: %s%n", error.getMessage());
            }
        }
    }

    static void forget(Object screen) {
        if (screen == null) {
            return;
        }

        synchronized (INJECTED_SCREENS) {
            INJECTED_SCREENS.remove(screen);
        }
    }

    private static Object createButton(Object screen, Object component, ButtonPlacement placement) throws ReflectiveOperationException {
        var buttonClass = Class.forName("net.minecraft.client.gui.components.Button");
        var onPressClass = findNestedInterface(buttonClass, "OnPress");
        var onPress = Proxy.newProxyInstance(
            buttonClass.getClassLoader(),
            new Class<?>[] { onPressClass },
            (proxy, method, args) -> {
                if ("onPress".equals(method.getName())) {
                    openModMenu(screen);
                }
                return null;
            }
        );

        var builderMethod = findStaticMethod(buttonClass, "builder", 2);
        var builder = builderMethod.invoke(null, component, onPress);
        var boundsMethod = findMethod(builder.getClass(), "bounds", int.class, int.class, int.class, int.class);
        var buildMethod = findMethod(builder.getClass(), "build");

        boundsMethod.invoke(builder, placement.x(), placement.y(), placement.width(), placement.height());
        return buildMethod.invoke(builder);
    }

    private static ButtonPlacement calculatePlacement(Object screen) {
        var width = readIntField(screen, "width", 854);
        var height = readIntField(screen, "height", 480);
        if (width < 200 || height < 120) {
            return null;
        }

        var buttonWidth = 98;
        var buttonHeight = 20;
        var margin = 8;

        return new ButtonPlacement(
            Math.max(margin, width - buttonWidth - margin),
            Math.max(margin, height - buttonHeight - margin),
            buttonWidth,
            buttonHeight
        );
    }

    private static Object literalComponent(String text) throws ReflectiveOperationException {
        var componentClass = Class.forName("net.minecraft.network.chat.Component");
        var literal = findStaticMethod(componentClass, "literal", String.class);
        return literal.invoke(null, text);
    }

    private static void openModMenu(Object parentScreen) {
        try {
            var minecraftClass = Class.forName("net.minecraft.client.Minecraft");
            var screenClass = Class.forName("net.minecraft.client.gui.screens.Screen");
            var modMenuClass = Class.forName("dev.akivcraft.loader.AkivCraftModMenuScreen");
            var minecraft = minecraftClass.getMethod("getInstance").invoke(null);
            var modMenu = modMenuClass.getConstructor(screenClass).newInstance(parentScreen);
            minecraftClass.getMethod("setScreen", screenClass).invoke(minecraft, modMenu);

            System.out.println("AkivCraft Mod Menu opened");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            System.err.printf("AkivCraft could not open Mod Menu: %s%n", error.getMessage());
        }
    }

    private static Method findAddRenderableWidget(Class<?> screenClass, Class<?> buttonClass) throws NoSuchMethodException {
        var named = findCompatibleMethod(screenClass, buttonClass, "addRenderableWidget");
        if (named != null) {
            return named;
        }

        var fallback = findCompatibleMethod(screenClass, buttonClass, null);
        if (fallback != null) {
            return fallback;
        }

        throw new NoSuchMethodException("addRenderableWidget-compatible method not found");
    }

    private static Method findCompatibleMethod(Class<?> screenClass, Class<?> buttonClass, String preferredName) {
        for (var type = screenClass; type != null; type = type.getSuperclass()) {
            for (var method : type.getDeclaredMethods()) {
                if (preferredName != null && !preferredName.equals(method.getName())) {
                    continue;
                }

                if (method.getParameterCount() == 1 && method.getParameterTypes()[0].isAssignableFrom(buttonClass)) {
                    return method;
                }
            }
        }

        return null;
    }

    private static Class<?> findNestedInterface(Class<?> owner, String simpleName) throws ClassNotFoundException {
        for (var nested : owner.getDeclaredClasses()) {
            if (nested.getSimpleName().equals(simpleName) && nested.isInterface()) {
                return nested;
            }
        }

        throw new ClassNotFoundException(owner.getName() + "$" + simpleName);
    }

    private static Method findStaticMethod(Class<?> owner, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        var method = findMethod(owner, name, parameterTypes);
        if (!Modifier.isStatic(method.getModifiers())) {
            throw new NoSuchMethodException(name + " is not static");
        }
        return method;
    }

    private static Method findStaticMethod(Class<?> owner, String name, int parameterCount) throws NoSuchMethodException {
        for (var method : owner.getMethods()) {
            if (method.getName().equals(name) && Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == parameterCount) {
                return method;
            }
        }

        throw new NoSuchMethodException(owner.getName() + "." + name);
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        var method = owner.getMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private static int readIntField(Object target, String name, int fallback) {
        try {
            Field field = null;
            for (var type = target.getClass(); type != null && field == null; type = type.getSuperclass()) {
                try {
                    field = type.getDeclaredField(name);
                } catch (NoSuchFieldException ignored) {
                    // Continue through the superclass chain.
                }
            }

            if (field == null) {
                return fallback;
            }

            field.setAccessible(true);
            return field.getInt(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return fallback;
        }
    }

    private record ButtonPlacement(int x, int y, int width, int height) {
    }
}
