package dev.akivcraft.loader;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public final class ClientBrandTransformer implements ClassFileTransformer {
    private static volatile boolean transformed;

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (transformed || classfileBuffer == null || !"net/minecraft/client/ClientBrandRetriever".equals(className)) return null;

        try {
            var reader = new ClassReader(classfileBuffer);
            var writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            var visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    var methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if ("getClientModName".equals(name) && "()Ljava/lang/String;".equals(descriptor)) {
                        transformed = true;
                        return new MethodVisitor(Opcodes.ASM9) {
                            private boolean emitted;

                            @Override
                            public void visitCode() {
                                if (emitted) return;
                                emitted = true;
                                methodVisitor.visitCode();
                                methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "dev/akivcraft/loader/AkivCraftClientBrand", "getClientModName", "()Ljava/lang/String;", false);
                                methodVisitor.visitInsn(Opcodes.ARETURN);
                            }

                            @Override
                            public void visitMaxs(int maxStack, int maxLocals) {
                                methodVisitor.visitMaxs(0, 0);
                            }

                            @Override
                            public void visitEnd() {
                                methodVisitor.visitEnd();
                            }
                        };
                    }
                    return methodVisitor;
                }
            };

            reader.accept(visitor, 0);
            if (transformed) {
                System.out.println("AkivCraft installed client brand hook");
                return writer.toByteArray();
            }
        } catch (RuntimeException error) {
            System.err.printf("AkivCraft failed to install client brand hook: %s%n", error.getMessage());
        }

        return null;
    }
}
