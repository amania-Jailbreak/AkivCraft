package dev.akivcraft.loader.via;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public final class ViaNetworkTransformer implements ClassFileTransformer {
    @Override
    public byte[] transform(
        ClassLoader loader,
        String className,
        Class<?> classBeingRedefined,
        ProtectionDomain protectionDomain,
        byte[] classfileBuffer
    ) {
        if (classfileBuffer == null || !"net/minecraft/network/Connection".equals(className)) {
            return null;
        }

        try {
            var reader = new ClassReader(classfileBuffer);
            var writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            var visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    var mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                    if ("configureSerialization".equals(name)
                        && "(Lio/netty/channel/ChannelPipeline;Lnet/minecraft/network/protocol/PacketFlow;ZLnet/minecraft/network/BandwidthDebugMonitor;)V".equals(descriptor)) {
                        return injectHook(mv);
                    }

                    if ("setupCompression".equals(name)
                        && "(IZ)V".equals(descriptor)) {
                        return injectCompressionHook(mv);
                    }

                    return mv;
                }

                private MethodVisitor injectHook(MethodVisitor mv) {
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        @Override
                        public void visitInsn(int opcode) {
                            if (opcode == Opcodes.RETURN) {
                                super.visitVarInsn(Opcodes.ALOAD, 0);
                                super.visitVarInsn(Opcodes.ALOAD, 1);
                                super.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    "dev/akivcraft/loader/via/ViaNetworkHook",
                                    "onConfigureSerialization",
                                    "(Lio/netty/channel/ChannelPipeline;Lnet/minecraft/network/protocol/PacketFlow;)V",
                                    false
                                );
                            }
                            super.visitInsn(opcode);
                        }
                    };
                }

                private MethodVisitor injectCompressionHook(MethodVisitor mv) {
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        @Override
                        public void visitInsn(int opcode) {
                            if (opcode == Opcodes.RETURN) {
                                super.visitVarInsn(Opcodes.ALOAD, 0);
                                super.visitFieldInsn(Opcodes.GETFIELD, "net/minecraft/network/Connection", "channel", "Lio/netty/channel/Channel;");
                                super.visitMethodInsn(
                                    Opcodes.INVOKEINTERFACE, "io/netty/channel/Channel", "pipeline", "()Lio/netty/channel/ChannelPipeline;", true
                                );
                                super.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    "dev/akivcraft/loader/via/ViaNetworkHook",
                                    "onSetupCompression",
                                    "(Lio/netty/channel/ChannelPipeline;)V",
                                    false
                                );
                            }
                            super.visitInsn(opcode);
                        }
                    };
                }
            };

            reader.accept(visitor, 0);
            System.out.println("AkivCraft installed Via network hook into Connection");
            return writer.toByteArray();
        } catch (RuntimeException error) {
            System.err.printf("AkivCraft failed to install Via network hook: %s%n", error.getMessage());
            return null;
        }
    }
}
