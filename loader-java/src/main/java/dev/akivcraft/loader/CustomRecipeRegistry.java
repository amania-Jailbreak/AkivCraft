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

public final class CustomRecipeRegistry {
    private static Method fromJsonMethod;

    private CustomRecipeRegistry() {
    }

    public static void inject(RecipeManager manager) {
        try {
            var registriesField = RecipeManager.class.getDeclaredField("registries");
            registriesField.setAccessible(true);
            var registries = (HolderLookup.Provider) registriesField.get(manager);

            var added = injectFromLoadedRecipes(manager, registries);
            if (added > 0) {
                AkivCraftLoadingLog.info("Injected " + added + " custom recipes");
                System.out.printf("AkivCraft injected %d custom recipes into RecipeManager%n", added);
            }
        } catch (Throwable error) {
            AkivCraftLoadingLog.error("Recipe injection failed: " + error.getMessage());
            System.err.printf("AkivCraft failed to inject custom recipes: %s%n", error.getMessage());
            error.printStackTrace();
        }
    }

    public static int injectFromLoadedRecipes(RecipeManager manager, HolderLookup.Provider registries) {
        var config = LoaderConfig.fromSystemProperties();
        var file = config.modsDirectory().resolve("loaded-recipes.json");
        if (!Files.isRegularFile(file)) {
            return 0;
        }

        try {
            var root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            var recipesArray = root.get("recipes");
            if (recipesArray == null || !recipesArray.isJsonArray()) {
                return 0;
            }

            var recipesField = RecipeManager.class.getDeclaredField("recipes");
            recipesField.setAccessible(true);
            var existing = (RecipeMap) recipesField.get(manager);

            var recipes = new LinkedHashMap<ResourceKey<Recipe<?>>, RecipeHolder<?>>();
            if (existing != null) {
                for (var holder : existing.values()) {
                    recipes.put(holder.id(), holder);
                }
            }

            var added = 0;
            for (var element : recipesArray.getAsJsonArray()) {
                if (!element.isJsonObject()) continue;
                var recipeObject = element.getAsJsonObject();

                var idElement = recipeObject.get("id");
                if (idElement == null || !idElement.isJsonPrimitive()) continue;
                var id = Identifier.parse(idElement.getAsString());
                var key = ResourceKey.create(Registries.RECIPE, id);

                var json = new com.google.gson.JsonObject();
                for (var entry : recipeObject.entrySet()) {
                    if (!"id".equals(entry.getKey())) {
                        json.add(entry.getKey(), entry.getValue());
                    }
                }

                try {
                    var holder = (RecipeHolder<?>) fromJson().invoke(null, key, json, registries);
                    if (holder != null) {
                        recipes.put(holder.id(), holder);
                        added++;
                    }
                } catch (Throwable error) {
                    AkivCraftLoadingLog.error("Failed loaded recipe " + id + ": " + error.getMessage());
                    System.err.printf("AkivCraft failed to parse loaded recipe %s: %s%n", id, error.getMessage());
                }
            }

            recipesField.set(manager, RecipeMap.create(recipes.values()));
            return added;
        } catch (Throwable error) {
            AkivCraftLoadingLog.error("Failed to read loaded-recipes.json: " + error.getMessage());
            System.err.printf("AkivCraft failed to read loaded-recipes.json: %s%n", error.getMessage());
            return 0;
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
