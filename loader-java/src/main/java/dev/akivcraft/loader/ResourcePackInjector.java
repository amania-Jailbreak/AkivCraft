package dev.akivcraft.loader;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.level.validation.DirectoryValidator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.stream.Stream;

public final class ResourcePackInjector {
    private static volatile boolean injected;
    private static final Set<PackRepository> injectedDataPackRepositories = Collections.newSetFromMap(new WeakHashMap<>());

    private ResourcePackInjector() {
    }

    public static void inject(Minecraft minecraft) {
        if (injected) return;

        try {
            AkivCraftBootCoordinator.awaitPreparedOrThrow();
            var config = LoaderConfig.fromSystemProperties();
            var generatedDir = config.akivcraftHome().resolve("generated-resourcepacks");
            if (!Files.isDirectory(generatedDir)) return;
            AkivCraftLoadingLog.stage("Installing generated resource packs");

            var packsDir = minecraft.getResourcePackDirectory();
            var repository = minecraft.getResourcePackRepository();
            var addedAny = false;

            try (Stream<Path> entries = Files.list(generatedDir)) {
                var sorted = entries.filter(Files::isDirectory).sorted().toList();

                for (var packDir : sorted) {
                    var packId = packDir.getFileName().toString();
                    var targetDir = packsDir.resolve(packId);

                    copyTree(packDir, targetDir);
                    addedAny = true;
                    AkivCraftLoadingLog.info("Installed resource pack " + packId);
                    System.out.printf("AkivCraft installed generated resource pack: %s%n", packId);
                }
            }

            if (!addedAny) return;

            repository.reload();
            for (var available : repository.getAvailablePacks()) {
                var id = available.getId();
                if (id.contains("akivcraft.") && !repository.getSelectedIds().contains(id)) {
                    repository.addPack(id);
                    AkivCraftLoadingLog.info("Selected resource pack " + id);
                    System.out.printf("AkivCraft selected resource pack: %s%n", id);
                }
            }
            injected = true;

            if (minecraft.isRunning()) {
                minecraft.reloadResourcePacks();
            }
        } catch (Throwable error) {
            AkivCraftLoadingLog.error("Resource pack injection failed: " + error.getMessage());
            System.err.printf("AkivCraft failed to inject generated resource packs: %s%n", error.getMessage());
        }
    }

    public static void injectDataPacks(PackRepository repository) {
        if (repository == null) return;

        try {
            AkivCraftBootCoordinator.awaitPreparedOrThrow();
            var config = LoaderConfig.fromSystemProperties();
            var generatedDir = config.akivcraftHome().resolve("generated-resourcepacks");
            if (!Files.isDirectory(generatedDir)) return;
            AkivCraftLoadingLog.stage("Adding generated data packs");

            synchronized (injectedDataPackRepositories) {
                if (!injectedDataPackRepositories.add(repository)) return;
            }

            var source = new FolderRepositorySource(
                generatedDir,
                PackType.SERVER_DATA,
                PackSource.create(component -> Component.literal("AkivCraft").append("/ ").append(component), true),
                new DirectoryValidator(path -> true)
            );

            var sourcesField = PackRepository.class.getDeclaredField("sources");
            sourcesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            var sources = (Set<Object>) sourcesField.get(repository);
            sources.add(source);

            AkivCraftLoadingLog.info("Added generated data packs");
            System.out.printf("AkivCraft added generated data packs from %s%n", generatedDir.toAbsolutePath());
        } catch (Throwable error) {
            AkivCraftLoadingLog.error("Data pack injection failed: " + error.getMessage());
            System.err.printf("AkivCraft failed to inject generated data packs: %s%n", error.getMessage());
        }
    }

    private static void copyTree(Path source, Path target) throws java.io.IOException {
        try (Stream<Path> stream = Files.walk(source)) {
            for (var entry : (Iterable<Path>) stream::iterator) {
                var relative = source.relativize(entry);
                var dest = target.resolve(relative);
                if (Files.isDirectory(entry)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(entry, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
