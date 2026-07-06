package dev.akivcraft.loader;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public final class CreativeScreenTransformer implements ClassFileTransformer {
    private static volatile boolean transformed;

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (transformed || classfileBuffer == null || !"net/minecraft/client/gui/screens/inventory/CreativeModeInventoryScreen".equals(className)) return null;

        try {
            var reader = new ClassReader(classfileBuffer);
            var writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES) {
                @Override
                protected String getCommonSuperClass(String type1, String type2) {
                    try {
                        return super.getCommonSuperClass(type1, type2);
                    } catch (Throwable ignored) {
                        return "java/lang/Object";
                    }
                }
            };
            var visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                private boolean replaceGetTabX;

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if ("getTabX".equals(name) && "(Lnet/minecraft/world/item/CreativeModeTab;)I".equals(descriptor)) {
                        transformed = true;
                        replaceGetTabX = true;
                        return null;
                    }

                    var original = super.visitMethod(access, name, descriptor, signature, exceptions);

                    if ("extractTabButton".equals(name) && descriptor.contains("CreativeModeTab")) {
                        return new MethodVisitor(Opcodes.ASM9, original) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                super.visitVarInsn(Opcodes.ALOAD, 4);
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, "dev/akivcraft/loader/CreativeTabPagination", "shouldRenderTab", "(Lnet/minecraft/world/item/CreativeModeTab;)Z", false);
                                var label = new Label();
                                super.visitJumpInsn(Opcodes.IFNE, label);
                                super.visitInsn(Opcodes.RETURN);
                                super.visitLabel(label);
                            }
                        };
                    }

                    if ("checkTabClicked".equals(name)) {
                        return new MethodVisitor(Opcodes.ASM9, original) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                super.visitVarInsn(Opcodes.ALOAD, 1);
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, "dev/akivcraft/loader/CreativeTabPagination", "shouldRenderTab", "(Lnet/minecraft/world/item/CreativeModeTab;)Z", false);
                                var label = new Label();
                                super.visitJumpInsn(Opcodes.IFNE, label);
                                super.visitInsn(Opcodes.ICONST_0);
                                super.visitInsn(Opcodes.IRETURN);
                                super.visitLabel(label);
                            }
                        };
                    }

                    if ("keyPressed".equals(name) && descriptor.contains("KeyEvent")) {
                        return new MethodVisitor(Opcodes.ASM9, original) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                super.visitVarInsn(Opcodes.ALOAD, 1);
                                super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/client/input/KeyEvent", "key", "()I", false);
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, "dev/akivcraft/loader/CreativeTabPagination", "handleKeyPress", "(I)Z", false);
                                var label = new Label();
                                super.visitJumpInsn(Opcodes.IFEQ, label);
                                super.visitInsn(Opcodes.ICONST_1);
                                super.visitInsn(Opcodes.IRETURN);
                                super.visitLabel(label);
                            }
                        };
                    }

                    if ("mouseClicked".equals(name) && descriptor.contains("MouseButtonEvent")) {
                        return new MethodVisitor(Opcodes.ASM9, original) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                super.visitVarInsn(Opcodes.ALOAD, 0);
                                super.visitVarInsn(Opcodes.ALOAD, 1);
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, "dev/akivcraft/loader/CreativeTabPagination", "handleMouseClick", "(Ljava/lang/Object;Ljava/lang/Object;)Z", false);
                                var label = new Label();
                                super.visitJumpInsn(Opcodes.IFEQ, label);
                                super.visitInsn(Opcodes.ICONST_1);
                                super.visitInsn(Opcodes.IRETURN);
                                super.visitLabel(label);
                            }
                        };
                    }

                    if ("extractBackground".equals(name) && descriptor.contains("GuiGraphicsExtractor")) {
                        return new MethodVisitor(Opcodes.ASM9, original) {
                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == Opcodes.RETURN) {
                                    super.visitVarInsn(Opcodes.ALOAD, 0);
                                    super.visitVarInsn(Opcodes.ALOAD, 1);
                                    super.visitMethodInsn(Opcodes.INVOKESTATIC, "dev/akivcraft/loader/CreativeTabPagination", "renderPageButtons", "(Ljava/lang/Object;Ljava/lang/Object;)V", false);
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    }

                    return original;
                }

                @Override
                public void visitEnd() {
                    if (replaceGetTabX) {
                        var mv = super.visitMethod(Opcodes.ACC_PRIVATE, "getTabX", "(Lnet/minecraft/world/item/CreativeModeTab;)I", null, null);
                        mv.visitCode();
                        mv.visitVarInsn(Opcodes.ALOAD, 0);
                        mv.visitVarInsn(Opcodes.ALOAD, 1);
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "dev/akivcraft/loader/CreativeTabPagination", "computeTabX", "(Ljava/lang/Object;Lnet/minecraft/world/item/CreativeModeTab;)I", false);
                        mv.visitInsn(Opcodes.IRETURN);
                        mv.visitMaxs(2, 2);
                        mv.visitEnd();
                    }
                    super.visitEnd();
                }
            };

            reader.accept(visitor, 0);
            if (transformed) {
                System.out.println("AkivCraft installed creative screen pagination hook");
                return writer.toByteArray();
            }
        } catch (RuntimeException error) {
            System.err.printf("AkivCraft failed to install creative screen hook: %s%n", error.getMessage());
        }

        return null;
    }
}
