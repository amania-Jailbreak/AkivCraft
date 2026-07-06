package dev.akivcraft.loader;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public final class DataPackTransformer implements ClassFileTransformer {
    private static volatile boolean transformed;

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (transformed || classfileBuffer == null || !"net/minecraft/server/WorldLoader$PackConfig".equals(className)) return null;

        try {
            var reader = new ClassReader(classfileBuffer);
            var writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            var visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    var methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if ("createResourceManager".equals(name) && "()Lcom/mojang/datafixers/util/Pair;".equals(descriptor)) {
                        transformed = true;
                        return new MethodVisitor(Opcodes.ASM9, methodVisitor) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                super.visitVarInsn(Opcodes.ALOAD, 0);
                                super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/server/WorldLoader$PackConfig", "packRepository", "()Lnet/minecraft/server/packs/repository/PackRepository;", false);
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, "dev/akivcraft/loader/ResourcePackInjector", "injectDataPacks", "(Lnet/minecraft/server/packs/repository/PackRepository;)V", false);
                            }
                        };
                    }
                    return methodVisitor;
                }
            };

            reader.accept(visitor, 0);
            if (transformed) {
                System.out.println("AkivCraft installed data pack injection hook");
                return writer.toByteArray();
            }
        } catch (RuntimeException error) {
            System.err.printf("AkivCraft failed to install data pack injection hook: %s%n", error.getMessage());
        }

        return null;
    }
}
