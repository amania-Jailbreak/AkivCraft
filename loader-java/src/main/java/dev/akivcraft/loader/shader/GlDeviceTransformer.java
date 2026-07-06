package dev.akivcraft.loader.shader;

import net.minecraft.resources.Identifier;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public final class GlDeviceTransformer implements ClassFileTransformer {
    private static volatile boolean transformed;

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (transformed || classfileBuffer == null || !"com/mojang/blaze3d/opengl/GlDevice".equals(className)) return null;

        try {
            var reader = new ClassReader(classfileBuffer);
            var writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            var visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    var mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if ("compileShader".equals(name)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                transformed = true;
                            }

                            @Override
                            public void visitVarInsn(int opcode, int varIndex) {
                                super.visitVarInsn(opcode, varIndex);
                                if (opcode == Opcodes.ASTORE && varIndex == 4) {
                                    super.visitVarInsn(Opcodes.ALOAD, 1);
                                    super.visitFieldInsn(Opcodes.GETFIELD,
                                        "com/mojang/blaze3d/opengl/GlDevice$ShaderCompilationKey",
                                        "id",
                                        "Lnet/minecraft/resources/Identifier;");
                                    super.visitVarInsn(Opcodes.ALOAD, 1);
                                    super.visitFieldInsn(Opcodes.GETFIELD,
                                        "com/mojang/blaze3d/opengl/GlDevice$ShaderCompilationKey",
                                        "type",
                                        "Lcom/mojang/blaze3d/shaders/ShaderType;");
                                    super.visitVarInsn(Opcodes.ALOAD, 4);
                                    super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        "dev/akivcraft/loader/shader/AkivShaderOverrides",
                                        "modifySource",
                                        "(Lnet/minecraft/resources/Identifier;Lcom/mojang/blaze3d/shaders/ShaderType;Ljava/lang/String;)Ljava/lang/String;",
                                        false
                                    );
                                    super.visitVarInsn(Opcodes.ASTORE, 4);
                                }
                            }
                        };
                    }
                    return mv;
                }
            };

            reader.accept(visitor, 0);
            if (transformed) {
                System.out.println("AkivCraft installed shader source interception on GlDevice.compileShader");
                return writer.toByteArray();
            }
        } catch (RuntimeException error) {
            System.err.printf("AkivCraft failed to install GlDevice hook: %s%n", error.getMessage());
        }

        return null;
    }
}
