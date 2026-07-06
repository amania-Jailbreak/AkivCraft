package dev.akivcraft.loader;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public final class ModStatusTransformer implements ClassFileTransformer {
    private static volatile boolean transformed;

    @Override
    public byte[] transform(
        ClassLoader loader,
        String className,
        Class<?> classBeingRedefined,
        ProtectionDomain protectionDomain,
        byte[] classfileBuffer
    ) {
        if (transformed || classfileBuffer == null || !"net/minecraft/client/Minecraft".equals(className)) {
            return null;
        }

        try {
            var reader = new ClassReader(classfileBuffer);
            var writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            var visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    var methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if ("checkModStatus".equals(name) && "()Lnet/minecraft/util/ModCheck;".equals(descriptor)) {
                        transformed = true;
                        methodVisitor.visitCode();
                        methodVisitor.visitTypeInsn(Opcodes.NEW, "net/minecraft/util/ModCheck");
                        methodVisitor.visitInsn(Opcodes.DUP);
                        methodVisitor.visitFieldInsn(
                            Opcodes.GETSTATIC,
                            "net/minecraft/util/ModCheck$Confidence",
                            "DEFINITELY",
                            "Lnet/minecraft/util/ModCheck$Confidence;"
                        );
                        methodVisitor.visitLdcInsn("akivcraft");
                        methodVisitor.visitMethodInsn(
                            Opcodes.INVOKESPECIAL,
                            "net/minecraft/util/ModCheck",
                            "<init>",
                            "(Lnet/minecraft/util/ModCheck$Confidence;Ljava/lang/String;)V",
                            false
                        );
                        methodVisitor.visitInsn(Opcodes.ARETURN);

                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override
                            public void visitEnd() {
                                methodVisitor.visitMaxs(0, 0);
                                methodVisitor.visitEnd();
                            }
                        };
                    }

                    return methodVisitor;
                }
            };

            reader.accept(visitor, 0);
            if (transformed) {
                System.out.println("AkivCraft installed Minecraft mod status hook");
                return writer.toByteArray();
            }
        } catch (RuntimeException error) {
            System.err.printf("AkivCraft failed to patch Minecraft mod status: %s%n", error.getMessage());
        }

        return null;
    }
}
