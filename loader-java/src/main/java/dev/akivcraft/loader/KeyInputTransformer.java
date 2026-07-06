package dev.akivcraft.loader;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public final class KeyInputTransformer implements ClassFileTransformer {
    private static volatile boolean transformed;

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (transformed || classfileBuffer == null || !"net/minecraft/client/KeyboardHandler".equals(className)) return null;

        try {
            var reader = new ClassReader(classfileBuffer);
            var writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            var visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    var methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if ("keyPress".equals(name) && "(JILnet/minecraft/client/input/KeyEvent;)V".equals(descriptor)) {
                        transformed = true;
                        return new MethodVisitor(Opcodes.ASM9, methodVisitor) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                super.visitVarInsn(Opcodes.ILOAD, 3);
                                super.visitVarInsn(Opcodes.ALOAD, 4);
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, "dev/akivcraft/loader/KeyEventBridge", "handle", "(ILnet/minecraft/client/input/KeyEvent;)V", false);
                            }
                        };
                    }
                    return methodVisitor;
                }
            };

            reader.accept(visitor, 0);
            if (transformed) {
                System.out.println("AkivCraft installed key input hook");
                return writer.toByteArray();
            }
        } catch (RuntimeException error) {
            System.err.printf("AkivCraft failed to install key input hook: %s%n", error.getMessage());
        }

        return null;
    }
}
