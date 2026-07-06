package dev.akivcraft.loader.shader;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public final class GlCommandEncoderTransformer implements ClassFileTransformer {
    private static volatile boolean transformed;

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (transformed || classfileBuffer == null || !"com/mojang/blaze3d/opengl/GlCommandEncoder".equals(className)) return null;

        try {
            var reader = new ClassReader(classfileBuffer);
            var writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            var visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    var mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if ("trySetup".equals(name) && "(Lcom/mojang/blaze3d/opengl/GlRenderPass;Ljava/util/Collection;)Z".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == Opcodes.IRETURN) {
                                super.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    "dev/akivcraft/loader/shader/AkivPipeline",
                                    "onTrySetupReturn",
                                    "()V",
                                    false
                                );
                                    transformed = true;
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    }
                    return mv;
                }
            };

            reader.accept(visitor, 0);
            if (transformed) {
                System.out.println("AkivCraft installed framebuffer redirect hook on GlCommandEncoder.trySetup");
                return writer.toByteArray();
            }
        } catch (RuntimeException error) {
            System.err.printf("AkivCraft failed to install trySetup hook: %s%n", error.getMessage());
        }

        return null;
    }
}
