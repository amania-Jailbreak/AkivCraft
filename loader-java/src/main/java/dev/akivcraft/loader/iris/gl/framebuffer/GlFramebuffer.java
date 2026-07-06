package dev.akivcraft.loader.iris.gl.framebuffer;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import dev.akivcraft.loader.iris.gl.GlResource;
import dev.akivcraft.loader.iris.gl.IrisRenderSystem;
import org.lwjgl.opengl.GL30C;

// Derived from Iris (LGPL-3.0), trimmed for AkivCraft.
public class GlFramebuffer extends GlResource {
    private final Int2IntMap attachments = new Int2IntArrayMap();
    private final int maxDrawBuffers;
    private final int maxColorAttachments;
    private boolean hasDepthAttachment;

    public GlFramebuffer() {
        super(IrisRenderSystem.createFramebuffer());
        this.maxDrawBuffers = GlStateManager._getInteger(GL30C.GL_MAX_DRAW_BUFFERS);
        this.maxColorAttachments = GlStateManager._getInteger(GL30C.GL_MAX_COLOR_ATTACHMENTS);
    }

    public void addDepthAttachmentBypass(int texture) {
        IrisRenderSystem.framebufferTexture2D(getGlId(), GL30C.GL_FRAMEBUFFER, GL30C.GL_DEPTH_ATTACHMENT, GL30C.GL_TEXTURE_2D, texture, 0);
        this.hasDepthAttachment = true;
    }

    public void addColorAttachment(int index, int texture) {
        IrisRenderSystem.framebufferTexture2D(getGlId(), GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0 + index, GL30C.GL_TEXTURE_2D, texture, 0);
        attachments.put(index, texture);
    }

    public void noDrawBuffers() {
        IrisRenderSystem.drawBuffers(getGlId(), new int[]{GL30C.GL_NONE});
    }

    public void drawBuffers(int[] buffers) {
        if (buffers.length > maxDrawBuffers) throw new IllegalArgumentException("Cannot write to more than " + maxDrawBuffers + " draw buffers on this GPU");
        int[] glBuffers = new int[buffers.length];
        for (int i = 0; i < buffers.length; i++) {
            if (buffers[i] >= maxColorAttachments) throw new IllegalArgumentException("Unsupported color attachment index " + buffers[i]);
            glBuffers[i] = GL30C.GL_COLOR_ATTACHMENT0 + buffers[i];
        }
        IrisRenderSystem.drawBuffers(getGlId(), glBuffers);
    }

    public void readBuffer(int buffer) {
        IrisRenderSystem.readBuffer(getGlId(), GL30C.GL_COLOR_ATTACHMENT0 + buffer);
    }

    public int getColorAttachment(int index) {
        return attachments.get(index);
    }

    public boolean hasDepthAttachment() {
        return hasDepthAttachment;
    }

    public void bind() {
        RenderSystem.assertOnRenderThread();
        GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, getGlId());
    }

    public void bindAsReadBuffer() {
        RenderSystem.assertOnRenderThread();
        GlStateManager._glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, getGlId());
    }

    public void bindAsDrawBuffer() {
        RenderSystem.assertOnRenderThread();
        GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, getGlId());
    }

    public int getStatus() {
        bind();
        return IrisRenderSystem.checkFramebufferStatus(GL30C.GL_FRAMEBUFFER);
    }

    public int getId() {
        return getGlId();
    }

    @Override
    protected void destroyInternal() {
        GlStateManager._glDeleteFramebuffers(getGlId());
    }
}
