package dev.akivcraft.loader.iris.gl;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL32C;

import java.nio.ByteBuffer;

// Derived from Iris (LGPL-3.0), heavily trimmed for AkivCraft MVP.
public final class IrisRenderSystem {
    private IrisRenderSystem() {}

    public static int createFramebuffer() {
        RenderSystem.assertOnRenderThread();
        return GlStateManager.glGenFramebuffers();
    }

    public static void framebufferTexture2D(int framebuffer, int target, int attachment, int textureTarget, int texture, int level) {
        RenderSystem.assertOnRenderThread();
        int prevDraw = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        int prevRead = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        GlStateManager._glBindFramebuffer(target, framebuffer);
        GlStateManager._glFramebufferTexture2D(target, attachment, textureTarget, texture, level);
        GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, prevDraw);
        GlStateManager._glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, prevRead);
    }

    public static void texImage2D(int texture, int target, int level, int internalFormat, int width, int height, int border, int format, int type, ByteBuffer pixels) {
        RenderSystem.assertOnRenderThread();
        int prevTex = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        GlStateManager._bindTexture(texture);
        GL32C.glTexImage2D(target, level, internalFormat, width, height, border, format, type, pixels);
        GlStateManager._bindTexture(prevTex);
    }

    public static void texParameteri(int texture, int target, int pname, int param) {
        RenderSystem.assertOnRenderThread();
        int prevTex = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        GlStateManager._bindTexture(texture);
        GL11C.glTexParameteri(target, pname, param);
        GlStateManager._bindTexture(prevTex);
    }

    public static void drawBuffers(int framebuffer, int[] buffers) {
        RenderSystem.assertOnRenderThread();
        int prevDraw = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, framebuffer);
        GL20Compat.drawBuffers(buffers);
        GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, prevDraw);
    }

    public static void readBuffer(int framebuffer, int buffer) {
        RenderSystem.assertOnRenderThread();
        int prevRead = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        GlStateManager._glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, framebuffer);
        GL11C.glReadBuffer(buffer);
        GlStateManager._glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, prevRead);
    }

    public static int checkFramebufferStatus(int target) {
        RenderSystem.assertOnRenderThread();
        return GL30C.glCheckFramebufferStatus(target);
    }

    private static final class GL20Compat {
        private GL20Compat() {}

        private static void drawBuffers(int[] buffers) {
            if (buffers.length == 0) {
                org.lwjgl.opengl.GL20C.glDrawBuffer(GL11C.GL_NONE);
            } else if (buffers.length == 1) {
                org.lwjgl.opengl.GL20C.glDrawBuffer(buffers[0]);
            } else {
                org.lwjgl.opengl.GL20C.glDrawBuffers(buffers);
            }
        }
    }
}
