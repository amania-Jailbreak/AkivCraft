package dev.akivcraft.loader;

import com.google.gson.JsonParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

public final class CustomRecipeRegistry {
    private static Method fromJsonMethod;

    private CustomRecipeRegistry() {
    }

    public static void inject(RecipeManager manager) {
        try {
            var recipesField = RecipeManager.class.getDeclaredField("recipes");
            recipesField.setAccessible(true);
            var existing = (RecipeMap) recipesField.get(manager);

            var registriesField = RecipeManager.class.getDeclaredField("registries");
            registriesField.setAccessible(true);
            var registries = (HolderLookup.Provider) registriesField.get(manager);

            var recipes = new LinkedHashMap<ResourceKey<Recipe<?>>, RecipeHolder<?>>();
            for (var holder : existing.values()) {
                recipes.put(holder.id(), holder);
            }

            var added = collectRecipes(registries, recipes);
            if (added == 0) return;

            recipesField.set(manager, RecipeMap.create(recipes.values()));
            AkivCraftLoadingLog.info("Injected " + added + " custom recipes");
            System.out.printf("AkivCraft injected %d custom recipes into RecipeManager%n", added);
        } catch (Throwable error) {
            AkivCraftLoadingLog.error("Recipe injection failed: " + error.getMessage());
            System.err.printf("AkivCraft failed to inject custom recipes: %s%n", error.getMessage());
            error.printStackTrace();
        }
    }

    private static int collectRecipes(HolderLookup.Provider registries, Map<ResourceKey<Recipe<?>>, RecipeHolder<?>> recipes) {
        var config = LoaderConfig.fromSystemProperties();
        var added = 0;
        added += collectRecipesFromModDirectories(config.modsDirectory(), registries, recipes);
        added += collectRecipesFromPackDirectories(config.akivcraftHome().resolve("generated-resourcepacks"), registries, recipes);
        return added;
    }

    private static int collectRecipesFromModDirectories(Path modsDirectory, HolderLookup.Provider registries, Map<ResourceKey<Recipe<?>>, RecipeHolder<?>> recipes) {
        if (!Files.isDirectory(modsDirectory)) return 0;

        var added = 0;
        try (Stream<Path> entries = Files.list(modsDirectory)) {
            for (var modDir : entries.filter(Files::isDirectory).toList()) {
                added += collectRecipesFromDataDirectory(modDir.resolve("data"), registries, recipes);
            }
        } catch (Exception error) {
            System.err.printf("AkivCraft failed to scan recipe mods in %s: %s%n", modsDirectory, error.getMessage());
        }
        return added;
    }

    private static int collectRecipesFromPackDirectories(Path packsDirectory, HolderLookup.Provider registries, Map<ResourceKey<Recipe<?>>, RecipeHolder<?>> recipes) {
        if (!Files.isDirectory(packsDirectory)) return 0;

        var added = 0;
        try (Stream<Path> entries = Files.list(packsDirectory)) {
            for (var packDir : entries.filter(Files::isDirectory).toList()) {
                added += collectRecipesFromDataDirectory(packDir.resolve("data"), registries, recipes);
            }
        } catch (Exception error) {
            System.err.printf("AkivCraft failed to scan generated recipe packs in %s: %s%n", packsDirectory, error.getMessage());
        }
        return added;
    }

    private static int collectRecipesFromDataDirectory(Path dataDirectory, HolderLookup.Provider registries, Map<ResourceKey<Recipe<?>>, RecipeHolder<?>> recipes) {
        if (!Files.isDirectory(dataDirectory)) return 0;

        var added = 0;
        try (Stream<Path> namespaces = Files.list(dataDirectory)) {
            for (var namespaceDir : namespaces.filter(Files::isDirectory).toList()) {
                var namespace = namespaceDir.getFileName().toString();
                var recipeDir = namespaceDir.resolve("recipe");
                if (!Files.isDirectory(recipeDir)) continue;

                try (Stream<Path> recipeFiles = Files.walk(recipeDir)) {
                    for (var recipeFile : recipeFiles.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".json")).toList()) {
                        var holder = parseRecipe(namespace, recipeDir, recipeFile, registries);
                        if (holder != null) {
                            recipes.put(holder.id(), holder);
                            added++;
                        }
                    }
                }
            }
        } catch (Exception error) {
            System.err.printf("AkivCraft failed to scan recipes in %s: %s%n", dataDirectory, error.getMessage());
        }
        return added;
    }

    private static RecipeHolder<?> parseRecipe(String namespace, Path recipeDir, Path recipeFile, HolderLookup.Provider registries) {
        try {
            AkivCraftLoadingLog.stage("Injecting custom recipes");
            var relative = recipeDir.relativize(recipeFile).toString().replace('\\', '/');
            var path = relative.substring(0, relative.length() - ".json".length());
            var id = Identifier.parse(namespace + ":" + path);
            var key = ResourceKey.create(Registries.RECIPE, id);
            var json = JsonParser.parseString(Files.readString(recipeFile)).getAsJsonObject();

            return (RecipeHolder<?>) fromJson().invoke(null, key, json, registries);
        } catch (Throwable error) {
            AkivCraftLoadingLog.error("Failed recipe " + recipeFile.getFileName() + ": " + error.getMessage());
            System.err.printf("AkivCraft failed to parse recipe %s: %s%n", recipeFile, error.getMessage());
            return null;
        }
    }

    private static Method fromJson() throws NoSuchMethodException {
        if (fromJsonMethod == null) {
            fromJsonMethod = RecipeManager.class.getDeclaredMethod("fromJson", ResourceKey.class, com.google.gson.JsonObject.class, HolderLookup.Provider.class);
            fromJsonMethod.setAccessible(true);
        }
        return fromJsonMethod;
    }
}
