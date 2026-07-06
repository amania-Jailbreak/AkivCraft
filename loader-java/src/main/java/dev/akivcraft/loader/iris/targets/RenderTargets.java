package dev.akivcraft.loader.iris.targets;

import dev.akivcraft.loader.iris.gl.framebuffer.GlFramebuffer;
import dev.akivcraft.loader.iris.gl.texture.InternalTextureFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// Derived from Iris (LGPL-3.0), heavily trimmed for AkivCraft MVP.
// This keeps the same conceptual model: persistent colortex targets, alt/main ping-pong,
// and framebuffers that share Minecraft's depth texture.
public class RenderTargets {
    private final RenderTarget[] targets;
    private final InternalTextureFormat[] targetFormats;
    private final List<GlFramebuffer> ownedFramebuffers = new ArrayList<>();
    private final BufferFlipper flipper = new BufferFlipper();

    private int width;
    private int height;
    private int depthTexture;
    private boolean destroyed;

    public RenderTargets(int width, int height, int depthTexture, InternalTextureFormat[] targetFormats) {
        this.width = width;
        this.height = height;
        this.depthTexture = depthTexture;
        this.targetFormats = targetFormats.clone();
        this.targets = new RenderTarget[targetFormats.length];
    }

    public int getRenderTargetCount() {
        return targets.length;
    }

    public RenderTarget get(int index) {
        checkNotDestroyed();
        return targets[index];
    }

    public RenderTarget getOrCreate(int index) {
        checkNotDestroyed();
        if (targets[index] == null) create(index);
        return targets[index];
    }

    public int getDepthTexture() {
        return depthTexture;
    }

    public void setDepthTexture(int depthTexture) {
        this.depthTexture = depthTexture;
        for (GlFramebuffer framebuffer : ownedFramebuffers) {
            if (framebuffer.hasDepthAttachment()) framebuffer.addDepthAttachmentBypass(depthTexture);
        }
    }

    public void resizeIfNeeded(int width, int height, int depthTexture) {
        boolean sizeChanged = width != this.width || height != this.height;
        boolean depthChanged = depthTexture != this.depthTexture;
        this.depthTexture = depthTexture;
        if (!sizeChanged && !depthChanged) return;
        this.width = width;
        this.height = height;
        for (int i = 0; i < targets.length; i++) {
            if (targets[i] != null) targets[i].resize(width, height);
        }
        for (GlFramebuffer framebuffer : ownedFramebuffers) {
            if (framebuffer.hasDepthAttachment()) framebuffer.addDepthAttachmentBypass(depthTexture);
        }
    }

    public void flip(int target) {
        flipper.flip(target);
    }

    public boolean isFlipped(int target) {
        return flipper.isFlipped(target);
    }

    public Set<Integer> snapshotFlippedBuffers() {
        return flipper.snapshot();
    }

    public GlFramebuffer createFramebufferWritingToMain(int[] drawBuffers) {
        return createFullFramebuffer(false, drawBuffers);
    }

    public GlFramebuffer createFramebufferWritingToAlt(int[] drawBuffers) {
        return createFullFramebuffer(true, drawBuffers);
    }

    public GlFramebuffer createGbufferFramebuffer(Set<Integer> stageWritesToAlt, int[] drawBuffers) {
        return createColorFramebuffer(stageWritesToAlt, drawBuffers);
    }

    public GlFramebuffer createColorFramebuffer(Set<Integer> stageReadsFromAlt, int[] drawBuffers) {
        return createColorFramebuffer(stageReadsFromAlt, drawBuffers, true);
    }

    public GlFramebuffer createColorFramebuffer(Set<Integer> stageReadsFromAlt, int[] drawBuffers, boolean withDepth) {
        GlFramebuffer framebuffer = new GlFramebuffer();
        ownedFramebuffers.add(framebuffer);
        for (int target : drawBuffers) {
            RenderTarget renderTarget = getOrCreate(target);
            boolean useAlt = stageReadsFromAlt.contains(target);
            framebuffer.addColorAttachment(target, useAlt ? renderTarget.getAltTexture() : renderTarget.getMainTexture());
        }
        if (withDepth) framebuffer.addDepthAttachmentBypass(depthTexture);
        framebuffer.drawBuffers(drawBuffers);
        return framebuffer;
    }

    public void destroyFramebuffer(GlFramebuffer framebuffer) {
        ownedFramebuffers.remove(framebuffer);
        framebuffer.destroy();
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void destroy() {
        if (destroyed) return;
        destroyed = true;
        for (GlFramebuffer framebuffer : ownedFramebuffers) framebuffer.destroy();
        for (RenderTarget target : targets) {
            if (target != null) target.destroy();
        }
        ownedFramebuffers.clear();
    }

    private GlFramebuffer createFullFramebuffer(boolean alt, int[] drawBuffers) {
        GlFramebuffer framebuffer = new GlFramebuffer();
        ownedFramebuffers.add(framebuffer);
        for (int target : drawBuffers) {
            RenderTarget renderTarget = getOrCreate(target);
            framebuffer.addColorAttachment(target, alt ? renderTarget.getAltTexture() : renderTarget.getMainTexture());
        }
        framebuffer.addDepthAttachmentBypass(depthTexture);
        framebuffer.drawBuffers(drawBuffers);
        return framebuffer;
    }

    private void create(int index) {
        targets[index] = RenderTarget.builder()
            .setDimensions(width, height)
            .setName("colortex" + index)
            .setInternalFormat(targetFormats[index])
            .setPixelFormat(targetFormats[index].getPixelFormat())
            .build();
    }

    private void checkNotDestroyed() {
        if (destroyed) throw new IllegalStateException("Tried to use destroyed RenderTargets");
    }
}
