package dev.akivcraft.loader;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public final class ChatTransformer implements ClassFileTransformer {
    private static volatile boolean transformed;

    @Override
    public byte[] transform(
        ClassLoader loader,
        String className,
        Class<?> classBeingRedefined,
        ProtectionDomain protectionDomain,
        byte[] classfileBuffer
    ) {
        if (transformed || classfileBuffer == null || !"net/minecraft/client/gui/components/ChatComponent".equals(className)) return null;

        try {
            var reader = new ClassReader(classfileBuffer);
            var writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            var visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    var mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                    if ("addServerSystemMessage".equals(name) && "(Lnet/minecraft/network/chat/Component;)V".equals(descriptor)) {
                        transformed = true;
                        return injectHook(mv, "onSystemMessage", 1);
                    }

                    if ("addPlayerMessage".equals(name) && descriptor.startsWith("(Lnet/minecraft/network/chat/Component;")) {
                        transformed = true;
                        return injectHook(mv, "onPlayerMessage", 1);
                    }

                    if ("addClientSystemMessage".equals(name) && "(Lnet/minecraft/network/chat/Component;)V".equals(descriptor)) {
                        transformed = true;
                        return injectHook(mv, "onClientMessage", 1);
                    }

                    return mv;
                }
            };

            reader.accept(visitor, 0);
            if (transformed) {
                System.out.println("AkivCraft installed chat capture hook");
                return writer.toByteArray();
            }
        } catch (RuntimeException error) {
            System.err.printf("AkivCraft failed to install chat hook: %s%n", error.getMessage());
        }

        return null;
    }

    private static MethodVisitor injectHook(MethodVisitor mv, String hookMethod, int componentVar) {
        return new MethodVisitor(Opcodes.ASM9, mv) {
            @Override
            public void visitCode() {
                super.visitCode();
                super.visitVarInsn(Opcodes.ALOAD, componentVar);
                super.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "dev/akivcraft/loader/ChatCapture",
                    hookMethod,
                    "(Lnet/minecraft/network/chat/Component;)V",
                    false
                );
            }
        };
    }
}
