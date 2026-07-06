package dev.akivcraft.loader;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class FreezeTransformer implements ClassFileTransformer {
    private static volatile boolean transformed;
    private static volatile boolean itemsRegistered;
    private static volatile boolean recipesRegistered;
    private static volatile boolean creativeTabRegistered;
    private static volatile boolean blocksRegistered;
    private static volatile boolean entitiesRegistered;
    private static volatile boolean featuresRegistered;
    private static volatile boolean carversRegistered;
    private static volatile boolean dimensionTypesRegistered;
    private static final Set<Object> biomeRegistriesRegistered = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<Object> levelStemRegistriesRegistered = Collections.newSetFromMap(new WeakHashMap<>());

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (transformed || classfileBuffer == null || !"net/minecraft/core/MappedRegistry".equals(className)) return null;

        try {
            var reader = new ClassReader(classfileBuffer);
            var writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            var visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    var methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if ("freeze".equals(name) && "()Lnet/minecraft/core/Registry;".equals(descriptor)) {
                        transformed = true;
                        return new MethodVisitor(Opcodes.ASM9, methodVisitor) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                super.visitVarInsn(Opcodes.ALOAD, 0);
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, "dev/akivcraft/loader/FreezeTransformer", "beforeFreeze", "(Ljava/lang/Object;)V", false);
                            }
                        };
                    }
                    return methodVisitor;
                }
            };

            reader.accept(visitor, 0);
            if (transformed) {
                System.out.println("AkivCraft installed registry freeze hook");
                return writer.toByteArray();
            }
        } catch (RuntimeException error) {
            System.err.printf("AkivCraft failed to install freeze hook: %s%n", error.getMessage());
        }

        return null;
    }

    public static void beforeFreeze(Object registry) {
        try {
            var keyMethod = registry.getClass().getMethod("key");
            var registryKey = keyMethod.invoke(registry);
            var registryKeyString = String.valueOf(registryKey);
            if (registryKeyString.contains("dimension") || registryKeyString.contains("level_stem")) {
                System.out.printf("AkivCraft freeze hook saw registry key: %s (%s)%n", registryKeyString, registry.getClass().getName());
            }
            if (net.minecraft.core.registries.Registries.ITEM.equals(registryKey)) {
                AkivCraftBootCoordinator.awaitPreparedOrThrow();
                if (!itemsRegistered) {
                    itemsRegistered = true;
                    AkivCraftLoadingLog.stage("Registering custom items");
                    registerItems();
                }
            } else if (net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB.equals(registryKey)) {
                AkivCraftBootCoordinator.awaitPreparedOrThrow();
                if (!creativeTabRegistered) {
                    creativeTabRegistered = true;
                    AkivCraftLoadingLog.stage("Registering creative tabs");
                    var config = LoaderConfig.fromSystemProperties();
                    var tabsFile = config.modsDirectory().resolve("loaded-creative-tabs.json");
                    CustomItemRegistry.registerCreativeTabs(tabsFile);
                }
            } else if (net.minecraft.core.registries.Registries.BIOME.equals(registryKey)) {
                AkivCraftBootCoordinator.awaitPreparedOrThrow();
                synchronized (biomeRegistriesRegistered) {
                    if (biomeRegistriesRegistered.contains(registry)) return;
                    biomeRegistriesRegistered.add(registry);
                }

                {
                    AkivCraftLoadingLog.stage("Registering custom biomes");
                    var config = LoaderConfig.fromSystemProperties();
                    var biomesFile = config.modsDirectory().resolve("loaded-biomes.json");
                    CustomBiomeRegistry.registerFromFile(biomesFile, registry);
                }
            } else if (net.minecraft.core.registries.Registries.BLOCK.equals(registryKey)) {
                AkivCraftBootCoordinator.awaitPreparedOrThrow();
                if (!blocksRegistered) {
                    blocksRegistered = true;
                    AkivCraftLoadingLog.stage("Registering custom blocks");
                    var config = LoaderConfig.fromSystemProperties();
                    var blocksFile = config.modsDirectory().resolve("loaded-blocks.json");
                    CustomBlockRegistry.registerFromFile(blocksFile, registry);
                }
            } else if (net.minecraft.core.registries.Registries.ENTITY_TYPE.equals(registryKey)) {
                AkivCraftBootCoordinator.awaitPreparedOrThrow();
                if (!entitiesRegistered) {
                    entitiesRegistered = true;
                    AkivCraftLoadingLog.stage("Registering custom entities");
                    var config = LoaderConfig.fromSystemProperties();
                    var entitiesFile = config.modsDirectory().resolve("loaded-entities.json");
                    CustomEntityRegistry.registerFromFile(entitiesFile, registry);
                }
            } else if (net.minecraft.core.registries.Registries.PLACED_FEATURE.equals(registryKey)) {
                AkivCraftBootCoordinator.awaitPreparedOrThrow();
                synchronized (biomeRegistriesRegistered) {
                    if (featuresRegistered) return;
                    featuresRegistered = true;
                }
                AkivCraftLoadingLog.stage("Registering custom features");
                var config = LoaderConfig.fromSystemProperties();
                var featuresFile = config.modsDirectory().resolve("loaded-features.json");
                CustomFeatureRegistry.registerFromFile(featuresFile, registry);
            } else if (net.minecraft.core.registries.Registries.CONFIGURED_CARVER.equals(registryKey)) {
                AkivCraftBootCoordinator.awaitPreparedOrThrow();
                if (!carversRegistered) {
                    carversRegistered = true;
                    AkivCraftLoadingLog.stage("Registering custom carvers");
                    var config = LoaderConfig.fromSystemProperties();
                    var carversFile = config.modsDirectory().resolve("loaded-carvers.json");
                    CustomCarverRegistry.registerFromFile(carversFile, registry);
                }
            } else if (net.minecraft.core.registries.Registries.DIMENSION_TYPE.equals(registryKey)) {
                AkivCraftBootCoordinator.awaitPreparedOrThrow();
                if (!dimensionTypesRegistered) {
                    dimensionTypesRegistered = true;
                    AkivCraftLoadingLog.stage("Registering custom dimension types");
                    var config = LoaderConfig.fromSystemProperties();
                    var dimensionsFile = config.modsDirectory().resolve("loaded-dimensions.json");
                    CustomDimensionRegistry.registerTypesFromFile(dimensionsFile, registry);
                }
            } else if (net.minecraft.core.registries.Registries.LEVEL_STEM.equals(registryKey)) {
                AkivCraftBootCoordinator.awaitPreparedOrThrow();
                synchronized (levelStemRegistriesRegistered) {
                    if (levelStemRegistriesRegistered.contains(registry)) return;
                    levelStemRegistriesRegistered.add(registry);
                }
                AkivCraftLoadingLog.stage("Registering custom level stems");
                var config = LoaderConfig.fromSystemProperties();
                var dimensionsFile = config.modsDirectory().resolve("loaded-dimensions.json");
                CustomDimensionRegistry.registerStemsFromFile(dimensionsFile, registry);
            }
        } catch (Throwable error) {
            AkivCraftLoadingLog.error("Registry freeze hook failed: " + error.getMessage());
            if (error instanceof RuntimeException runtimeException) throw runtimeException;
            throw new AkivCraftStartupException("Registry freeze hook failed", error);
        }
    }

    private static void registerItems() {
        try {
            var config = LoaderConfig.fromSystemProperties();
            var itemsFile = config.modsDirectory().resolve("loaded-items.json");

            if (!Files.isRegularFile(itemsFile)) {
                throw new AkivCraftStartupException("loaded-items.json not found before ITEM registry freeze: " + itemsFile.toAbsolutePath());
            }

            System.out.println("AkivCraft registering custom items before ITEM registry freeze");
            CustomItemRegistry.registerFromFile(itemsFile);
        } catch (Throwable error) {
            AkivCraftLoadingLog.error("Item registration failed: " + error.getMessage());
            System.err.printf("AkivCraft item registration before freeze failed: %s%n", error.getMessage());
            error.printStackTrace();
            if (error instanceof RuntimeException runtimeException) throw runtimeException;
            throw new AkivCraftStartupException("Item registration before freeze failed", error);
        }
    }
}
