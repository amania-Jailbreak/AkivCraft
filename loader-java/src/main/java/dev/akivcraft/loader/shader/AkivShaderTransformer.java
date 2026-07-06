package dev.akivcraft.loader.shader;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public final class AkivShaderTransformer implements ClassFileTransformer {
    private static volatile boolean transformed;

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (transformed || classfileBuffer == null || !"net/minecraft/client/renderer/GameRenderer".equals(className)) return null;

        try {
            var reader = new ClassReader(classfileBuffer);
            var writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            var visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    var mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                    if ("renderLevel".equals(name) && "(Lnet/minecraft/client/DeltaTracker;)V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitVarInsn(Opcodes.ALOAD, 0);
                                super.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    "dev/akivcraft/loader/shader/AkivPipeline",
                                    "beginLevelRender",
                                    "(Lnet/minecraft/client/renderer/GameRenderer;)V",
                                    false
                                );
                                transformed = true;
                                super.visitCode();
                            }

                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == Opcodes.RETURN) {
                                    super.visitVarInsn(Opcodes.ALOAD, 0);
                                    super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        "dev/akivcraft/loader/shader/AkivPipeline",
                                        "endLevelRender",
                                        "(Lnet/minecraft/client/renderer/GameRenderer;)V",
                                        false
                                    );
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
                System.out.println("AkivCraft installed Iris-style level render hooks on GameRenderer.renderLevel");
                return writer.toByteArray();
            }
        } catch (RuntimeException error) {
            System.err.printf("AkivCraft failed to install shader hook: %s%n", error.getMessage());
        }

        return null;
    }
}
