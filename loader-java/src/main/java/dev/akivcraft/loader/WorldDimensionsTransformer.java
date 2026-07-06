package dev.akivcraft.loader;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public final class WorldDimensionsTransformer implements ClassFileTransformer {
    private static volatile boolean transformed;

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (transformed || classfileBuffer == null || !"net/minecraft/world/level/levelgen/WorldDimensions".equals(className)) return null;

        try {
            var reader = new ClassReader(classfileBuffer);
            var writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            var visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    var mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if ("bake".equals(name) && "(Lnet/minecraft/core/Registry;)Lnet/minecraft/world/level/levelgen/WorldDimensions$Complete;".equals(descriptor)) {
                        transformed = true;
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                super.visitVarInsn(Opcodes.ALOAD, 0);
                                super.visitVarInsn(Opcodes.ALOAD, 1);
                                super.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    "dev/akivcraft/loader/CustomDimensionRegistry",
                                    "beforeBake",
                                    "(Ljava/lang/Object;Ljava/lang/Object;)V",
                                    false
                                );
                            }
                        };
                    }
                    return mv;
                }
            };

            reader.accept(visitor, 0);
            if (transformed) {
                System.out.println("AkivCraft installed WorldDimensions.bake hook");
                return writer.toByteArray();
            }
        } catch (RuntimeException error) {
            System.err.printf("AkivCraft failed to install WorldDimensions hook: %s%n", error.getMessage());
        }

        return null;
    }
}
