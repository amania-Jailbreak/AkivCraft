package dev.akivcraft.loader;

import java.lang.instrument.ClassFileTransformer;
import java.util.List;
import java.util.Set;

public final class AkivCraftTransformerSet {
    private AkivCraftTransformerSet() {
    }

    public static List<ClassFileTransformer> create() {
        return List.of(
            new ModStatusTransformer(),
            new BootstrapTransformer(),
            new ClientBrandTransformer(),
            new ModMenuScreenTransformer(),
            new MinecraftStateTransformer(),
            new HudTransformer(),
            new ResourcePackTransformer(),
            new DataPackTransformer(),
            new BiomeSourceParameterListTransformer(),
            new RecipeTransformer(),
            new LoadingOverlayTransformer(),
            new KeyInputTransformer(),
            new BlockEventTransformer(),
            new FreezeTransformer(),
            new WorldDimensionsTransformer(),
            new CreativeScreenTransformer(),
            new ChatTransformer(),
            new dev.akivcraft.loader.via.ViaNetworkTransformer()
        );
    }

    /**
     * Internal class names (dot-separated) that the transformers will modify.
     * Used by {@link AkivCraftTransformingClassLoader} to decide which classes
     * to load child-first — only these classes (plus dev.akivcraft.loader.* helpers)
     * are loaded child-first to avoid classloader split-brain issues.
     *
     * <p>{@code Main} is included as the anchor: since it is the entry point called
     * via {@code Class.forName(..., ourClassLoader)}, loading it child-first ensures
     * the ENTIRE Minecraft classloading chain flows through our classloader, so
     * all transform targets are actually intercepted.
     */
    public static Set<String> transformTargets() {
        return Set.of(
            "net.minecraft.client.main.Main",
            "net.minecraft.client.Minecraft",
            "net.minecraft.server.Bootstrap",
            "net.minecraft.client.ClientBrandRetriever",
            "net.minecraft.client.gui.screens.TitleScreen",
            "net.minecraft.client.gui.screens.PauseScreen",
            "net.minecraft.client.gui.Gui",
            "net.minecraft.server.WorldLoader$PackConfig",
            "net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList",
            "net.minecraft.world.item.crafting.RecipeManager",
            "net.minecraft.client.gui.screens.LoadingOverlay",
            "net.minecraft.client.KeyboardHandler",
            "net.minecraft.server.level.ServerPlayerGameMode",
            "net.minecraft.core.MappedRegistry",
            "net.minecraft.world.level.levelgen.WorldDimensions",
            "net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen",
            "net.minecraft.client.gui.components.ChatComponent",
            "net.minecraft.network.Connection",
            "net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen"
        );
    }
}
