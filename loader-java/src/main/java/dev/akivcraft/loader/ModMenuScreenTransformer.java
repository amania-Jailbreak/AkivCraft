package dev.akivcraft.loader;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public final class ModMenuScreenTransformer implements ClassFileTransformer {
    @Override
    public byte[] transform(
        ClassLoader loader,
        String className,
        Class<?> classBeingRedefined,
        ProtectionDomain protectionDomain,
        byte[] classfileBuffer
    ) {
        if (classfileBuffer == null || !isTargetClass(className)) {
            return null;
        }

        try {
            var reader = new ClassReader(classfileBuffer);
            var writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            var visitor = new ScreenClassVisitor(writer, className);
            reader.accept(visitor, 0);

            if (!visitor.injected) {
                return null;
            }

            System.out.printf("AkivCraft installed layout Mod Menu hook into %s%n", className);
            return writer.toByteArray();
        } catch (RuntimeException error) {
            System.err.printf("AkivCraft failed to install layout Mod Menu hook into %s: %s%n", className, error.getMessage());
            return null;
        }
    }

    private static boolean isTargetClass(String className) {
        return "net/minecraft/client/gui/screens/TitleScreen".equals(className)
            || "net/minecraft/client/gui/screens/PauseScreen".equals(className)
            || "net/minecraft/client/gui/screens/multiplayer/JoinMultiplayerScreen".equals(className);
    }

    private static final class ScreenClassVisitor extends ClassVisitor {
        private final String className;
        private boolean injected;

        private ScreenClassVisitor(ClassVisitor classVisitor, String className) {
            super(Opcodes.ASM9, classVisitor);
            this.className = className;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            var methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);

            if ("net/minecraft/client/gui/screens/TitleScreen".equals(className)
                && "init".equals(name)
                && "()V".equals(descriptor)) {
                return injectTitleLayout(methodVisitor);
            }

            if ("net/minecraft/client/gui/screens/TitleScreen".equals(className)
                && "createNormalMenuOptions".equals(name)
                && "(II)I".equals(descriptor)) {
                return injectTitleMenu(methodVisitor);
            }

            if ("net/minecraft/client/gui/screens/TitleScreen".equals(className)
                && "extractRenderState".equals(name)
                && "(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V".equals(descriptor)) {
                return replaceTitleRender(methodVisitor);
            }

            if ("net/minecraft/client/gui/screens/TitleScreen".equals(className)
                && "extractPanorama".equals(name)
                && "(Lnet/minecraft/client/gui/GuiGraphicsExtractor;F)V".equals(descriptor)) {
                return replaceTitlePanorama(methodVisitor);
            }

            if ("net/minecraft/client/gui/screens/TitleScreen".equals(className)
                && "extractBackground".equals(name)
                && "(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V".equals(descriptor)) {
                return replaceTitleBackground(methodVisitor);
            }

            if ("net/minecraft/client/gui/screens/multiplayer/JoinMultiplayerScreen".equals(className)
                && "init".equals(name)
                && "()V".equals(descriptor)) {
                return injectMultiplayerViaButton(methodVisitor);
            }

            if ("net/minecraft/client/gui/screens/PauseScreen".equals(className)
                && "createPauseMenu".equals(name)
                && "()V".equals(descriptor)) {
                return injectPauseMenu(methodVisitor);
            }

            return methodVisitor;
        }

        private MethodVisitor injectTitleMenu(MethodVisitor methodVisitor) {
            return new MethodVisitor(Opcodes.ASM9, methodVisitor) {
                @Override
                public void visitInsn(int opcode) {
                    if (opcode == Opcodes.IRETURN) {
                        super.visitVarInsn(Opcodes.ALOAD, 0);
                        super.visitVarInsn(Opcodes.ILOAD, 1);
                        super.visitVarInsn(Opcodes.ILOAD, 2);
                        super.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            "dev/akivcraft/loader/ModMenuLayoutHooks",
                            "addTitleModMenuButton",
                            "(Lnet/minecraft/client/gui/screens/TitleScreen;II)I",
                            false
                        );
                        injected = true;
                    }

                    super.visitInsn(opcode);
                }
            };
        }

        private MethodVisitor injectTitleLayout(MethodVisitor methodVisitor) {
            return new MethodVisitor(Opcodes.ASM9, methodVisitor) {
                @Override
                public void visitInsn(int opcode) {
                    if (opcode == Opcodes.RETURN) {
                        super.visitVarInsn(Opcodes.ALOAD, 0);
                        super.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            "dev/akivcraft/loader/AkivCraftTitleScreenUi",
                            "setup",
                            "(Lnet/minecraft/client/gui/screens/TitleScreen;)V",
                            false
                        );
                        injected = true;
                    }

                    super.visitInsn(opcode);
                }
            };
        }

        private MethodVisitor replaceTitleRender(MethodVisitor methodVisitor) {
            methodVisitor.visitCode();

            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "dev/akivcraft/loader/AkivCraftTitleScreenUi",
                "renderBackground",
                "(Lnet/minecraft/client/gui/screens/TitleScreen;Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",
                false
            );

            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);
            methodVisitor.visitVarInsn(Opcodes.ILOAD, 2);
            methodVisitor.visitVarInsn(Opcodes.ILOAD, 3);
            methodVisitor.visitVarInsn(Opcodes.FLOAD, 4);
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "dev/akivcraft/loader/AkivCraftTitleScreenUi",
                "renderWidgets",
                "(Lnet/minecraft/client/gui/screens/TitleScreen;Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
                false
            );

            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "dev/akivcraft/loader/AkivCraftTitleScreenUi",
                "renderForeground",
                "(Lnet/minecraft/client/gui/screens/TitleScreen;Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",
                false
            );

            methodVisitor.visitInsn(Opcodes.RETURN);
            injected = true;

            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitEnd() {
                    methodVisitor.visitMaxs(0, 0);
                    methodVisitor.visitEnd();
                }
            };
        }

        private MethodVisitor replaceTitlePanorama(MethodVisitor methodVisitor) {
            methodVisitor.visitCode();
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "dev/akivcraft/loader/AkivCraftTitleScreenUi",
                "renderBackground",
                "(Lnet/minecraft/client/gui/screens/TitleScreen;Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",
                false
            );
            methodVisitor.visitInsn(Opcodes.RETURN);
            injected = true;

            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitEnd() {
                    methodVisitor.visitMaxs(0, 0);
                    methodVisitor.visitEnd();
                }
            };
        }

        private MethodVisitor replaceTitleBackground(MethodVisitor methodVisitor) {
            methodVisitor.visitCode();
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);
            methodVisitor.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "dev/akivcraft/loader/AkivCraftTitleScreenUi",
                "renderBackground",
                "(Lnet/minecraft/client/gui/screens/TitleScreen;Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",
                false
            );
            methodVisitor.visitInsn(Opcodes.RETURN);
            injected = true;

            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitEnd() {
                    methodVisitor.visitMaxs(0, 0);
                    methodVisitor.visitEnd();
                }
            };
        }

        private MethodVisitor injectPauseMenu(MethodVisitor methodVisitor) {
            return new MethodVisitor(Opcodes.ASM9, methodVisitor) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                    if (opcode == Opcodes.INVOKEVIRTUAL
                        && "net/minecraft/client/gui/layouts/GridLayout$RowHelper".equals(owner)
                        && "addChild".equals(name)
                        && "(Lnet/minecraft/client/gui/layouts/LayoutElement;I)Lnet/minecraft/client/gui/layouts/LayoutElement;".equals(descriptor)) {
                        super.visitVarInsn(Opcodes.ALOAD, 0);
                        super.visitVarInsn(Opcodes.ALOAD, 2);
                        super.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            "dev/akivcraft/loader/ModMenuLayoutHooks",
                            "addPauseModMenuButton",
                            "(Lnet/minecraft/client/gui/screens/PauseScreen;Lnet/minecraft/client/gui/layouts/GridLayout$RowHelper;)V",
                            false
                        );
                        injected = true;
                    }

                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                }
            };
        }

        private MethodVisitor injectMultiplayerViaButton(MethodVisitor methodVisitor) {
            return new MethodVisitor(Opcodes.ASM9, methodVisitor) {
                @Override
                public void visitInsn(int opcode) {
                    if (opcode == Opcodes.RETURN) {
                        super.visitVarInsn(Opcodes.ALOAD, 0);
                        super.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            "dev/akivcraft/loader/via/ViaMultiplayerHook",
                            "addViaButton",
                            "(Lnet/minecraft/client/gui/screens/multiplayer/JoinMultiplayerScreen;)V",
                            false
                        );
                        injected = true;
                    }
                    super.visitInsn(opcode);
                }
            };
        }

    }
}
