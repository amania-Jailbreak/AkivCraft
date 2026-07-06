package dev.akivcraft.loader;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public final class BiomeSourceParameterListTransformer implements ClassFileTransformer {
    private static volatile boolean transformed;

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (transformed || classfileBuffer == null || !"net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList".equals(className)) return null;

        try {
            var reader = new ClassReader(classfileBuffer);
            var writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            var visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    var methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if ("<init>".equals(name) && "(Lnet/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList$Preset;Lnet/minecraft/core/HolderGetter;)V".equals(descriptor)) {
                        transformed = true;
                        return new MethodVisitor(Opcodes.ASM9, methodVisitor) {
                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == Opcodes.RETURN) {
                                    super.visitVarInsn(Opcodes.ALOAD, 0);
                                    super.visitVarInsn(Opcodes.ALOAD, 1);
                                    super.visitVarInsn(Opcodes.ALOAD, 2);
                                    super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        "dev/akivcraft/loader/CustomBiomeRegistry",
                                        "remember",
                                        "(Lnet/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList;Lnet/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList$Preset;Lnet/minecraft/core/HolderGetter;)V",
                                        false
                                    );
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    } else if ("parameters".equals(name) && "()Lnet/minecraft/world/level/biome/Climate$ParameterList;".equals(descriptor)) {
                        transformed = true;
                        return new MethodVisitor(Opcodes.ASM9, methodVisitor) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                super.visitVarInsn(Opcodes.ALOAD, 0);
                                super.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    "dev/akivcraft/loader/CustomBiomeRegistry",
                                    "injectIfReady",
                                    "(Lnet/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList;)V",
                                    false
                                );
                            }
                        };
                    }
                    return methodVisitor;
                }
            };

            reader.accept(visitor, 0);
            if (transformed) {
                System.out.println("AkivCraft installed biome source parameter hook");
                return writer.toByteArray();
            }
        } catch (RuntimeException error) {
            System.err.printf("AkivCraft failed to install biome source parameter hook: %s%n", error.getMessage());
        }

        return null;
    }
}
