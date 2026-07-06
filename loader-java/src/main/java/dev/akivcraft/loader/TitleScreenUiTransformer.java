package dev.akivcraft.loader;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public final class TitleScreenUiTransformer implements ClassFileTransformer {
    private static volatile boolean transformed;

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (transformed || classfileBuffer == null || !"net/minecraft/client/gui/screens/TitleScreen".equals(className)) return null;

        try {
            var reader = new ClassReader(classfileBuffer);
            var writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            var visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                private boolean renderHook;
                private boolean layoutHook;

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    var methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if ("init".equals(name) && "()V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, methodVisitor) {
                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == Opcodes.RETURN) {
                                    super.visitVarInsn(Opcodes.ALOAD, 0);
                                    super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        "dev/akivcraft/loader/AkivCraftTitleScreenUi",
                                        "layout",
                                        "(Lnet/minecraft/client/gui/screens/TitleScreen;)V",
                                        false
                                    );
                                    layoutHook = true;
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    }

                    if ("extractRenderState".equals(name) && "(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, methodVisitor) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                super.visitVarInsn(Opcodes.ALOAD, 0);
                                super.visitVarInsn(Opcodes.ALOAD, 1);
                                super.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    "dev/akivcraft/loader/AkivCraftTitleScreenUi",
                                    "renderBackground",
                                    "(Lnet/minecraft/client/gui/screens/TitleScreen;Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",
                                    false
                                );
                            }

                            @Override
                            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                                if (opcode == Opcodes.INVOKEVIRTUAL
                                    && "net/minecraft/client/gui/screens/TitleScreen".equals(owner)
                                    && "extractPanorama".equals(name)
                                    && "(Lnet/minecraft/client/gui/GuiGraphicsExtractor;F)V".equals(descriptor)) {
                                    super.visitInsn(Opcodes.POP);
                                    super.visitInsn(Opcodes.POP);
                                    super.visitInsn(Opcodes.POP);
                                    renderHook = true;
                                    return;
                                }

                                if (opcode == Opcodes.INVOKEVIRTUAL
                                    && "net/minecraft/client/gui/components/LogoRenderer".equals(owner)
                                    && "extractRenderState".equals(name)
                                    && "(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IF)V".equals(descriptor)) {
                                    super.visitInsn(Opcodes.POP);
                                    super.visitInsn(Opcodes.POP);
                                    super.visitInsn(Opcodes.POP);
                                    super.visitInsn(Opcodes.POP);
                                    renderHook = true;
                                    return;
                                }

                                if (opcode == Opcodes.INVOKEVIRTUAL
                                    && "net/minecraft/client/gui/components/SplashRenderer".equals(owner)
                                    && "extractRenderState".equals(name)
                                    && "(Lnet/minecraft/client/gui/GuiGraphicsExtractor;ILnet/minecraft/client/gui/Font;F)V".equals(descriptor)) {
                                    super.visitInsn(Opcodes.POP);
                                    super.visitInsn(Opcodes.POP);
                                    super.visitInsn(Opcodes.POP);
                                    super.visitInsn(Opcodes.POP);
                                    super.visitInsn(Opcodes.POP);
                                    renderHook = true;
                                    return;
                                }

                                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                            }

                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == Opcodes.RETURN) {
                                    super.visitVarInsn(Opcodes.ALOAD, 0);
                                    super.visitVarInsn(Opcodes.ALOAD, 1);
                                    super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        "dev/akivcraft/loader/AkivCraftTitleScreenUi",
                                        "renderForeground",
                                        "(Lnet/minecraft/client/gui/screens/TitleScreen;Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",
                                        false
                                    );
                                    renderHook = true;
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    }

                    return methodVisitor;
                }

                @Override
                public void visitEnd() {
                    transformed = renderHook || layoutHook;
                    super.visitEnd();
                }
            };

            reader.accept(visitor, 0);
            if (transformed) {
                System.out.println("AkivCraft installed TitleScreen UI replacement hook");
                return writer.toByteArray();
            }
        } catch (RuntimeException error) {
            System.err.printf("AkivCraft failed to install TitleScreen UI replacement hook: %s%n", error.getMessage());
        }

        return null;
    }
}
