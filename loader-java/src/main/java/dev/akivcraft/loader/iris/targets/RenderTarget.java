package dev.akivcraft.loader.iris.targets;

import com.mojang.blaze3d.opengl.GlStateManager;
import dev.akivcraft.loader.iris.gl.IrisRenderSystem;
import dev.akivcraft.loader.iris.gl.texture.InternalTextureFormat;
import dev.akivcraft.loader.iris.gl.texture.PixelFormat;
import dev.akivcraft.loader.iris.gl.texture.PixelType;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;

import java.nio.ByteBuffer;

// Derived from Iris (LGPL-3.0), trimmed for AkivCraft.
public class RenderTarget {
    private static final ByteBuffer NULL_BUFFER = null;

    private final InternalTextureFormat internalFormat;
    private final PixelFormat format;
    private final PixelType type;
    private final int mainTexture;
    private final int altTexture;
    private int width;
    private int height;
    private boolean valid;
    private String name;
    private boolean allowsLinear;
    private boolean mipmapsOnAlt;
    private boolean mipmapsOnMain;

    public RenderTarget(Builder builder) {
        this.valid = true;
        this.name = builder.name;
        this.internalFormat = builder.internalFormat;
        this.format = builder.format;
        this.type = builder.type;
        this.width = builder.width;
        this.height = builder.height;
        this.mainTexture = GlStateManager._genTexture();
        this.altTexture = GlStateManager._genTexture();
        boolean isPixelFormatInteger = builder.internalFormat.getPixelFormat().isInteger();
        this.allowsLinear = !isPixelFormatInteger;
        setupTexture(mainTexture, width, height, allowsLinear, false);
        setupTexture(altTexture, width, height, allowsLinear, true);
        GlStateManager._bindTexture(0);
    }

    public static Builder builder() {
        return new Builder();
    }

    private void setupTexture(int texture, int width, int height, boolean allowsLinear, boolean alt) {
        resizeTexture(texture, width, height, alt);
        IrisRenderSystem.texParameteri(texture, GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, allowsLinear ? GL11C.GL_LINEAR : GL11C.GL_NEAREST);
        IrisRenderSystem.texParameteri(texture, GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, allowsLinear ? GL11C.GL_LINEAR : GL11C.GL_NEAREST);
        IrisRenderSystem.texParameteri(texture, GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL12C.GL_CLAMP_TO_EDGE);
        IrisRenderSystem.texParameteri(texture, GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL12C.GL_CLAMP_TO_EDGE);
    }

    private void resizeTexture(int texture, int width, int height, boolean alt) {
        IrisRenderSystem.texImage2D(texture, GL11C.GL_TEXTURE_2D, 0, internalFormat.getGlFormat(), width, height, 0, format.getGlFormat(), type.getGlFormat(), NULL_BUFFER);
    }

    void resize(int width, int height) {
        requireValid();
        this.width = width;
        this.height = height;
        resizeTexture(mainTexture, width, height, false);
        resizeTexture(altTexture, width, height, true);
    }

    public InternalTextureFormat getInternalFormat() { return internalFormat; }
    public int getMainTexture() { requireValid(); return mainTexture; }
    public int getAltTexture() { requireValid(); return altTexture; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public void destroy() {
        requireValid();
        valid = false;
        GlStateManager._deleteTexture(mainTexture);
        GlStateManager._deleteTexture(altTexture);
    }

    private void requireValid() {
        if (!valid) throw new IllegalStateException("Attempted to use a deleted composite render target");
    }

    public void turnOnMips(boolean alt) {
        if (alt) mipmapsOnAlt = true; else mipmapsOnMain = true;
    }

    public void turnOffMips(boolean alt) {
        if (alt) mipmapsOnAlt = false; else mipmapsOnMain = false;
    }

    public static class Builder {
        private InternalTextureFormat internalFormat = InternalTextureFormat.RGBA8;
        private int width;
        private int height;
        private PixelFormat format = PixelFormat.RGBA;
        private PixelType type = PixelType.UNSIGNED_BYTE;
        private String name;

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setInternalFormat(InternalTextureFormat format) {
            this.internalFormat = format;
            return this;
        }

        public Builder setDimensions(int width, int height) {
            if (width <= 0 || height <= 0) throw new IllegalArgumentException("Width and height must be greater than zero");
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder setPixelFormat(PixelFormat pixelFormat) {
            this.format = pixelFormat;
            return this;
        }

        public Builder setPixelType(PixelType pixelType) {
            this.type = pixelType;
            return this;
        }

        public RenderTarget build() {
            return new RenderTarget(this);
        }
    }
}
