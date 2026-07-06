package dev.akivcraft.loader;

import net.minecraft.core.Registry;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CustomItemRegistry {
    private static final Map<String, String> itemTabMap = new LinkedHashMap<>();
    private static final Map<String, TabDef> customTabs = new LinkedHashMap<>();
    private static volatile boolean tabsLoaded;
    private static int itemRegistrationFailures;

    private CustomItemRegistry() {
    }

    public static void registerFromFile(Path itemsFile) {
        if (itemsFile == null || !Files.isRegularFile(itemsFile)) {
            System.err.println("AkivCraft items file not found: " + itemsFile);
            return;
        }

        try {
            var json = Files.readString(itemsFile);
            System.out.printf("AkivCraft read %d bytes from %s%n", json.length(), itemsFile.getFileName());
            itemRegistrationFailures = 0;
            registerItemsFromJson(json);
            if (itemRegistrationFailures > 0) {
                throw new AkivCraftStartupException("Failed to register " + itemRegistrationFailures + " custom items");
            }
        } catch (Exception error) {
            if (error instanceof AkivCraftStartupException startupError) throw startupError;
            System.err.printf("AkivCraft failed to read %s: %s%n", itemsFile, error.getMessage());
            throw new AkivCraftStartupException("Failed to read custom items from " + itemsFile, error);
        }
    }

    public static void registerCreativeTabs(Path tabsFile) {
        if (tabsLoaded) return;
        tabsLoaded = true;

        if (tabsFile != null && Files.isRegularFile(tabsFile)) {
            try {
                var json = Files.readString(tabsFile);
                parseTabDefs(json);
            } catch (Exception error) {
                System.err.printf("AkivCraft failed to read %s: %s%n", tabsFile, error.getMessage());
            }
        }

        registerAllTabs();
    }

    static void registerCreativeTab() {
        registerCreativeTabs(null);
    }

    private static void parseTabDefs(String json) {
        var idMarker = "\"id\"";
        var searchIndex = 0;

        while (true) {
            var start = json.indexOf(idMarker, searchIndex);
            if (start < 0) break;
            start += idMarker.length();
            while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == ':')) start++;
            if (start >= json.length() || json.charAt(start) != '"') {
                searchIndex = start;
                continue;
            }
            start++;
            var end = json.indexOf("\"", start);
            if (end < 0) break;
            searchIndex = end + 1;

            var id = json.substring(start, end);
            var blockEnd = json.indexOf("}", end);
            if (blockEnd < 0) break;
            var block = json.substring(end, blockEnd);

            var name = stringField(block, "\"name\":", "\"");
            var icon = stringField(block, "\"icon\":", "\"");
            var row = stringField(block, "\"row\":", "\"");

            if (id != null && !id.isBlank()) {
                customTabs.put(id, new TabDef(id, name != null ? name : "AkivCraft", icon, row != null ? row : "top"));
            }
        }
    }

    private static void registerItemsFromJson(String json) {
        var itemsMarker = "\"items\"";
        var markerIdx = json.indexOf(itemsMarker);
        if (markerIdx < 0) return;

        var arrStart = json.indexOf('[', markerIdx);
        if (arrStart < 0) return;
        var arrEnd = findMatching(json, arrStart, '[', ']');
        if (arrEnd < 0) return;

        var itemsArray = json.substring(arrStart + 1, arrEnd);
        for (var block : extractObjects(itemsArray)) {
            var id = stringField(block, "\"id\":", "\"");
            if (id != null && !id.isBlank()) registerItem(id, block);
        }
    }

    private static void registerItem(String id, String block) {
        try {
            var identifier = Identifier.parse(id);
            if (BuiltInRegistries.ITEM.containsKey(identifier)) return;

            var name = stringField(block, "\"name\":", "\"");
            var type = stringField(block, "\"type\":", "\"");
            var material = stringField(block, "\"material\":", "\"");
            var rarity = stringField(block, "\"rarity\":", "\"");
            var tab = stringField(block, "\"tab\":", "\"");
            var maxStackSize = intField(block, "\"maxStackSize\":", 64);
            var durability = intField(block, "\"durability\":", 0);
            var attackDamage = floatField(block, "\"attackDamage\":", 1f);
            var attackSpeed = floatField(block, "\"attackSpeed\":", -2.4f);
            var miningSpeed = floatField(block, "\"miningSpeed\":", 1f);
            var fireResistant = block.contains("\"fireResistant\":") && block.substring(block.indexOf("\"fireResistant\":")).contains("true");

            var actions = parseActions(block);
            var useAnimation = parseUseAnimation(stringField(block, "\"useAnimation\":", "\""));
            var useDuration = intField(block, "\"useDuration\":", 0);

            var key = ResourceKey.create(Registries.ITEM, identifier);
            var properties = new Item.Properties()
                .setId(key)
                .rarity(rarity(rarity));
            if (!isTool(type) && maxStackSize > 0) properties.stacksTo(Math.max(1, Math.min(99, maxStackSize)));
            if (durability > 0) properties.durability(durability);
            properties = applyItemType(properties, type, material, attackDamage, attackSpeed, miningSpeed);
            if (name != null && !name.isBlank()) {
                properties.overrideDescription("item." + identifier.getNamespace() + "." + identifier.getPath().replace('/', '.'));
            }
            if (fireResistant) properties.fireResistant();

            var hasBehavior = !actions.isEmpty() || useDuration > 0;
            var item = hasBehavior
                ? new AkivCraftItem(properties, id, actions, useAnimation, useDuration)
                : new Item(properties);
            Registry.register(BuiltInRegistries.ITEM, key, item);
            if (tab != null && !tab.isBlank()) itemTabMap.put(id, tab);
            AkivCraftLoadingLog.info("Registered item " + id + (hasBehavior ? " (with " + actions.size() + " use actions)" : ""));
            System.out.printf("AkivCraft registered custom item: %s (type=%s, material=%s, behaviors=%d, inRegistry=%b)%n", id, type, material, actions.size(), BuiltInRegistries.ITEM.containsKey(identifier));
        } catch (Throwable error) {
            itemRegistrationFailures++;
            AkivCraftLoadingLog.error("Failed item " + id + ": " + error.getMessage());
            System.err.printf("AkivCraft failed to register custom item %s: %s%n", id, error);
            error.printStackTrace();
        }
    }

    private static void registerAllTabs() {
        int topColumn = 7;
        int bottomColumn = 7;

        for (var tabDef : customTabs.values()) {
            try {
                var identifier = Identifier.parse(tabDef.id());
                if (BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(identifier)) continue;

                var isTop = !"bottom".equalsIgnoreCase(tabDef.row());
                int column = isTop ? topColumn++ : bottomColumn++;

                var key = ResourceKey.create(Registries.CREATIVE_MODE_TAB, identifier);
                var displayItems = itemsForTab(tabDef.id());

                var tab = CreativeModeTab.builder(
                    isTop ? CreativeModeTab.Row.TOP : CreativeModeTab.Row.BOTTOM,
                    column
                )
                    .title(Component.literal(tabDef.name()))
                    .icon(() -> iconForTab(tabDef))
                    .displayItems(displayItemsGenerator(tabDef.id()))
                    .build();

                Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, key, tab);
                AkivCraftLoadingLog.info("Registered creative tab " + tabDef.id());
                System.out.printf("AkivCraft registered creative tab: %s (column %d, %d items)%n", tabDef.id(), column, displayItems.size());
            } catch (Throwable error) {
                AkivCraftLoadingLog.error("Failed creative tab " + tabDef.id() + ": " + error.getMessage());
                System.err.printf("AkivCraft failed to register creative tab %s: %s%n", tabDef.id(), error.getMessage());
            }
        }

        if (customTabs.isEmpty() && !itemTabMap.isEmpty()) {
            for (var entry : itemTabMap.entrySet()) {
                if (!customTabs.containsKey(entry.getValue())) {
                    registerFallbackTab(entry.getValue());
                }
            }
        }
    }

    private static void registerFallbackTab(String tabId) {
        try {
            var identifier = Identifier.parse(tabId);
            if (BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(identifier)) return;

            int column = 7;
            var key = ResourceKey.create(Registries.CREATIVE_MODE_TAB, identifier);
            var tab = CreativeModeTab.builder(CreativeModeTab.Row.TOP, column++)
                .title(Component.literal("AkivCraft"))
                .icon(() -> iconForTab(new TabDef(tabId, "AkivCraft", null, "top")))
                .displayItems(displayItemsGenerator(tabId))
                .build();
            Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, key, tab);
            AkivCraftLoadingLog.info("Registered fallback creative tab " + tabId);
            System.out.printf("AkivCraft registered fallback creative tab: %s%n", tabId);
        } catch (Throwable error) {
            AkivCraftLoadingLog.error("Failed fallback tab " + tabId + ": " + error.getMessage());
            System.err.printf("AkivCraft failed to register fallback tab %s: %s%n", tabId, error.getMessage());
        }
    }

    private static List<String> itemsForTab(String tabId) {
        var result = new ArrayList<String>();
        for (var entry : itemTabMap.entrySet()) {
            if (tabId.equals(entry.getValue())) result.add(entry.getKey());
        }
        return result;
    }

    private static CreativeModeTab.DisplayItemsGenerator displayItemsGenerator(String tabId) {
        return (CreativeModeTab.DisplayItemsGenerator) Proxy.newProxyInstance(
            CreativeModeTab.DisplayItemsGenerator.class.getClassLoader(),
            new Class<?>[] { CreativeModeTab.DisplayItemsGenerator.class },
            (proxy, method, args) -> {
                if (!"accept".equals(method.getName()) || args == null || args.length < 2) return null;
                var output = args[1];
                var accept = output.getClass().getMethod("accept", ItemStack.class);
                for (var itemId : itemsForTab(tabId)) {
                    var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
                    if (item != null) accept.invoke(output, new ItemStack(item));
                }
                return null;
            }
        );
    }

    private static ItemStack iconForTab(TabDef tabDef) {
        if (tabDef.icon() != null && !tabDef.icon().isBlank()) {
            var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(tabDef.icon()));
            if (item != null) return new ItemStack(item);
        }
        for (var itemId : itemsForTab(tabDef.id())) {
            var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
            if (item != null) return new ItemStack(item);
        }
        return ItemStack.EMPTY;
    }

    private static Item.Properties applyItemType(Item.Properties properties, String type, String material, float attackDamage, float attackSpeed, float miningSpeed) {
        var mat = material(material);
        return switch ((type == null ? "item" : type).toLowerCase(Locale.ROOT)) {
            case "sword" -> applySwordProperties(properties, mat, attackDamage, attackSpeed);
            case "pickaxe" -> applyToolProperties(properties, mat, BlockTags.MINEABLE_WITH_PICKAXE, attackDamage, attackSpeed, miningSpeed);
            case "axe" -> applyToolProperties(properties, mat, BlockTags.MINEABLE_WITH_AXE, attackDamage, attackSpeed, miningSpeed);
            case "shovel" -> applyToolProperties(properties, mat, BlockTags.MINEABLE_WITH_SHOVEL, attackDamage, attackSpeed, miningSpeed);
            case "hoe" -> applyToolProperties(properties, mat, BlockTags.MINEABLE_WITH_HOE, attackDamage, attackSpeed, miningSpeed);
            default -> properties;
        };
    }

    private static Item.Properties applyCommonToolProperties(Item.Properties properties, ToolMaterial material) {
        return properties
            .durability(material.durability())
            .repairable(material.repairItems())
            .enchantable(material.enchantmentValue());
    }

    private static Item.Properties applyToolProperties(Item.Properties properties, ToolMaterial material, TagKey<Block> mineableBlocks, float attackDamage, float attackSpeed, float miningSpeed) {
        return applyCommonToolProperties(properties, material)
            .component(DataComponents.TOOL, new Tool(List.of(
                Tool.Rule.deniesDrops(blockTag(material.incorrectBlocksForDrops())),
                Tool.Rule.minesAndDrops(blockTag(mineableBlocks), material.speed())
            ), 1f, 1, true))
            .attributes(toolAttributes(material, attackDamage, attackSpeed))
            .component(DataComponents.WEAPON, new Weapon(2, miningSpeed));
    }

    private static Item.Properties applySwordProperties(Item.Properties properties, ToolMaterial material, float attackDamage, float attackSpeed) {
        return applyCommonToolProperties(properties, material)
            .component(DataComponents.TOOL, new Tool(List.of(
                Tool.Rule.minesAndDrops(HolderSet.direct(Blocks.COBWEB.builtInRegistryHolder()), 15f),
                Tool.Rule.overrideSpeed(blockTag(BlockTags.SWORD_INSTANTLY_MINES), Float.MAX_VALUE),
                Tool.Rule.overrideSpeed(blockTag(BlockTags.SWORD_EFFICIENT), 1.5f)
            ), 1f, 2, false))
            .attributes(toolAttributes(material, attackDamage, attackSpeed))
            .component(DataComponents.WEAPON, new Weapon(1));
    }

    private static HolderSet<Block> blockTag(TagKey<Block> tag) {
        return BuiltInRegistries.BLOCK.get(tag)
            .<HolderSet<Block>>map(named -> named)
            .orElseGet(() -> HolderSet.emptyNamed(BuiltInRegistries.BLOCK, tag));
    }

    private static ItemAttributeModifiers toolAttributes(ToolMaterial material, float attackDamage, float attackSpeed) {
        return ItemAttributeModifiers.builder()
            .add(
                Attributes.ATTACK_DAMAGE,
                new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, attackDamage + material.attackDamageBonus(), AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND
            )
            .add(
                Attributes.ATTACK_SPEED,
                new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, attackSpeed, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND
            )
            .build();
    }

    private static boolean isTool(String type) {
        if (type == null) return false;
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "sword", "pickaxe", "axe", "shovel", "hoe" -> true;
            default -> false;
        };
    }

    private static ToolMaterial material(String value) {
        return switch ((value == null ? "iron" : value).toLowerCase(Locale.ROOT)) {
            case "wood" -> ToolMaterial.WOOD;
            case "stone" -> ToolMaterial.STONE;
            case "copper" -> ToolMaterial.COPPER;
            case "diamond" -> ToolMaterial.DIAMOND;
            case "gold" -> ToolMaterial.GOLD;
            case "netherite" -> ToolMaterial.NETHERITE;
            default -> ToolMaterial.IRON;
        };
    }

    private static Rarity rarity(String value) {
        return switch ((value == null ? "common" : value).toLowerCase(Locale.ROOT)) {
            case "uncommon" -> Rarity.UNCOMMON;
            case "rare" -> Rarity.RARE;
            case "epic" -> Rarity.EPIC;
            default -> Rarity.COMMON;
        };
    }

    private static String stringField(String source, String prefix, String suffix) {
        var start = source.indexOf(prefix);
        if (start < 0) return null;
        start += prefix.length();
        while (start < source.length() && source.charAt(start) == ' ') start++;
        if (start >= source.length() || source.charAt(start) != '"') return null;
        start++;
        var end = source.indexOf(suffix, start);
        if (end < 0) return null;
        return source.substring(start, end);
    }

    private static int intField(String source, String prefix, int fallback) {
        var start = source.indexOf(prefix);
        if (start < 0) return fallback;
        start += prefix.length();
        while (start < source.length() && source.charAt(start) == ' ') start++;
        var end = start;
        while (end < source.length() && (Character.isDigit(source.charAt(end)) || source.charAt(end) == '-')) end++;
        try {
            return Integer.parseInt(source.substring(start, end).trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static float floatField(String source, String prefix, float fallback) {
        var start = source.indexOf(prefix);
        if (start < 0) return fallback;
        start += prefix.length();
        while (start < source.length() && source.charAt(start) == ' ') start++;
        var end = start;
        while (end < source.length() && (Character.isDigit(source.charAt(end)) || source.charAt(end) == '-' || source.charAt(end) == '.')) end++;
        try {
            return Float.parseFloat(source.substring(start, end).trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static List<ItemUseAction> parseActions(String block) {
        var onUseMarker = "\"onUse\"";
        var idx = block.indexOf(onUseMarker);
        if (idx < 0) return List.of();

        var arrStart = block.indexOf('[', idx);
        if (arrStart < 0) return List.of();
        var arrEnd = findMatching(block, arrStart, '[', ']');
        if (arrEnd < 0) return List.of();

        var actionObjects = extractObjects(block.substring(arrStart + 1, arrEnd));
        var actions = new ArrayList<ItemUseAction>();
        for (var obj : actionObjects) {
            var action = parseAction(obj);
            if (action != null) actions.add(action);
        }
        return actions;
    }

    private static ItemUseAction parseAction(String obj) {
        var type = stringField(obj, "\"type\":", "\"");
        if (type == null) return null;

        return switch (type.toLowerCase(Locale.ROOT)) {
            case "potion_effect" -> new ItemUseAction.PotionEffect(
                stringField(obj, "\"effect\":", "\""),
                intField(obj, "\"duration\":", 200),
                intField(obj, "\"amplifier\":", 0)
            );
            case "heal" -> new ItemUseAction.Heal(floatField(obj, "\"amount\":", 1f));
            case "damage" -> new ItemUseAction.Damage(floatField(obj, "\"amount\":", 1f));
            case "teleport" -> new ItemUseAction.Teleport(doubleField(obj, "\"range\":", 10.0));
            case "lightning" -> new ItemUseAction.Lightning();
            case "explosion" -> new ItemUseAction.Explosion(floatField(obj, "\"power\":", 2f));
            case "fire_projectile" -> new ItemUseAction.FireProjectile(
                stringField(obj, "\"projectile\":", "\""),
                floatField(obj, "\"speed\":", 1.5f),
                floatField(obj, "\"damage\":", 2f)
            );
            case "sound" -> new ItemUseAction.Sound(
                stringField(obj, "\"sound\":", "\""),
                floatField(obj, "\"volume\":", 1f),
                floatField(obj, "\"pitch\":", 1f)
            );
            case "particle" -> new ItemUseAction.Particle(
                stringField(obj, "\"particle\":", "\""),
                intField(obj, "\"count\":", 10)
            );
            case "consume" -> new ItemUseAction.Consume(intField(obj, "\"amount\":", 1));
            case "cooldown" -> new ItemUseAction.Cooldown(intField(obj, "\"ticks\":", 20));
            case "command" -> new ItemUseAction.Command(stringField(obj, "\"command\":", "\""));
            case "node_callback" -> {
                var event = stringField(obj, "\"event\":", "\"");
                yield new ItemUseAction.NodeCallback(event != null ? event : "use");
            }
            default -> null;
        };
    }

    private static ItemUseAnimation parseUseAnimation(String value) {
        if (value == null) return ItemUseAnimation.NONE;
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "eat" -> ItemUseAnimation.EAT;
            case "drink" -> ItemUseAnimation.DRINK;
            case "block" -> ItemUseAnimation.BLOCK;
            case "bow" -> ItemUseAnimation.BOW;
            case "spear" -> ItemUseAnimation.SPEAR;
            case "crossbow" -> ItemUseAnimation.CROSSBOW;
            default -> ItemUseAnimation.NONE;
        };
    }

    private static List<String> extractObjects(String source) {
        var objects = new ArrayList<String>();
        var depth = 0;
        var start = -1;
        var inString = false;

        for (var i = 0; i < source.length(); i++) {
            var c = source.charAt(i);
            if (c == '"' && (i == 0 || source.charAt(i - 1) != '\\')) {
                inString = !inString;
            } else if (!inString) {
                if (c == '{') {
                    if (depth == 0) start = i;
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0 && start >= 0) {
                        objects.add(source.substring(start, i + 1));
                        start = -1;
                    }
                }
            }
        }
        return objects;
    }

    private static int findMatching(String json, int openIdx, char open, char close) {
        var depth = 0;
        var inString = false;
        for (var i = openIdx; i < json.length(); i++) {
            var c = json.charAt(i);
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                inString = !inString;
            } else if (!inString) {
                if (c == open) depth++;
                else if (c == close) {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    private static double doubleField(String source, String prefix, double fallback) {
        var start = source.indexOf(prefix);
        if (start < 0) return fallback;
        start += prefix.length();
        while (start < source.length() && source.charAt(start) == ' ') start++;
        var end = start;
        while (end < source.length() && (Character.isDigit(source.charAt(end)) || source.charAt(end) == '-' || source.charAt(end) == '.')) end++;
        try {
            return Double.parseDouble(source.substring(start, end).trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private record TabDef(String id, String name, String icon, String row) {
    }
}
