package dev.akivcraft.loader;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public final class BlockEventTransformer implements ClassFileTransformer {
    private static volatile boolean transformedUse;
    private static volatile boolean transformedBreak;

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (classfileBuffer == null || !"net/minecraft/server/level/ServerPlayerGameMode".equals(className)) return null;

        try {
            var reader = new ClassReader(classfileBuffer);
            var writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            var visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    var mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if ("useItemOn".equals(name) && "(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;".equals(descriptor)) {
                        transformedUse = true;
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == Opcodes.ARETURN) {
                                    super.visitVarInsn(Opcodes.ASTORE, 6);
                                    super.visitVarInsn(Opcodes.ALOAD, 1);
                                    super.visitVarInsn(Opcodes.ALOAD, 2);
                                    super.visitVarInsn(Opcodes.ALOAD, 3);
                                    super.visitVarInsn(Opcodes.ALOAD, 4);
                                    super.visitVarInsn(Opcodes.ALOAD, 5);
                                    super.visitVarInsn(Opcodes.ALOAD, 6);
                                    super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        "dev/akivcraft/loader/BlockEventBridge",
                                        "afterUseItemOn",
                                        "(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/world/InteractionResult;)V",
                                        false
                                    );
                                    super.visitVarInsn(Opcodes.ALOAD, 6);
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    }

                    if ("destroyBlock".equals(name) && "(Lnet/minecraft/core/BlockPos;)Z".equals(descriptor)) {
                        transformedBreak = true;
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                super.visitVarInsn(Opcodes.ALOAD, 0);
                                super.visitVarInsn(Opcodes.ALOAD, 1);
                                super.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    "dev/akivcraft/loader/BlockEventBridge",
                                    "beforeDestroyBlock",
                                    "(Ljava/lang/Object;Lnet/minecraft/core/BlockPos;)V",
                                    false
                                );
                            }

                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == Opcodes.IRETURN) {
                                    super.visitVarInsn(Opcodes.ISTORE, 2);
                                    super.visitVarInsn(Opcodes.ALOAD, 0);
                                    super.visitVarInsn(Opcodes.ALOAD, 1);
                                    super.visitVarInsn(Opcodes.ILOAD, 2);
                                    super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        "dev/akivcraft/loader/BlockEventBridge",
                                        "afterDestroyBlock",
                                        "(Ljava/lang/Object;Lnet/minecraft/core/BlockPos;Z)V",
                                        false
                                    );
                                    super.visitVarInsn(Opcodes.ILOAD, 2);
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    }

                    return mv;
                }
            };

            reader.accept(visitor, 0);
            if (transformedUse || transformedBreak) {
                System.out.printf("AkivCraft installed block event hooks: use=%b break=%b%n", transformedUse, transformedBreak);
                return writer.toByteArray();
            }
        } catch (RuntimeException error) {
            System.err.printf("AkivCraft failed to install block event hooks: %s%n", error.getMessage());
        }

        return null;
    }
}
