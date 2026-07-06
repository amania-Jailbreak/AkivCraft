package dev.akivcraft.loader.iris.gl.texture;

import dev.akivcraft.loader.iris.gl.GlVersion;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL31C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL41C;

// Derived from Iris (LGPL-3.0), trimmed for AkivCraft.
public enum InternalTextureFormat {
    RGBA(GL11C.GL_RGBA8, GlVersion.GL_11, PixelFormat.RGBA, ShaderDataType.FLOAT),
    R8(GL30C.GL_R8, GlVersion.GL_30, PixelFormat.RED, ShaderDataType.FLOAT),
    RG8(GL30C.GL_RG8, GlVersion.GL_30, PixelFormat.RG, ShaderDataType.FLOAT),
    RGB8(GL11C.GL_RGB8, GlVersion.GL_11, PixelFormat.RGB, ShaderDataType.FLOAT),
    RGBA8(GL11C.GL_RGBA8, GlVersion.GL_11, PixelFormat.RGBA, ShaderDataType.FLOAT),
    RGBA16(GL11C.GL_RGBA16, GlVersion.GL_11, PixelFormat.RGBA, ShaderDataType.FLOAT),
    R16F(GL30C.GL_R16F, GlVersion.GL_30, PixelFormat.RED, ShaderDataType.FLOAT),
    RG16F(GL30C.GL_RG16F, GlVersion.GL_30, PixelFormat.RG, ShaderDataType.FLOAT),
    RGB16F(GL30C.GL_RGB16F, GlVersion.GL_30, PixelFormat.RGB, ShaderDataType.FLOAT),
    RGBA16F(GL30C.GL_RGBA16F, GlVersion.GL_30, PixelFormat.RGBA, ShaderDataType.FLOAT),
    RGB10_A2(GL11C.GL_RGB10_A2, GlVersion.GL_11, PixelFormat.RGBA, ShaderDataType.FLOAT),
    R11F_G11F_B10F(GL30C.GL_R11F_G11F_B10F, GlVersion.GL_30, PixelFormat.RGB, ShaderDataType.FLOAT),
    R8I(GL30C.GL_R8I, GlVersion.GL_30, PixelFormat.RED_INTEGER, ShaderDataType.INT),
    RG8I(GL30C.GL_RG8I, GlVersion.GL_30, PixelFormat.RG_INTEGER, ShaderDataType.INT),
    RGB8I(GL30C.GL_RGB8I, GlVersion.GL_30, PixelFormat.RGB_INTEGER, ShaderDataType.INT),
    RGBA8I(GL30C.GL_RGBA8I, GlVersion.GL_30, PixelFormat.RGBA_INTEGER, ShaderDataType.INT),
    R8UI(GL30C.GL_R8UI, GlVersion.GL_30, PixelFormat.RED_INTEGER, ShaderDataType.UINT),
    RG8UI(GL30C.GL_RG8UI, GlVersion.GL_30, PixelFormat.RG_INTEGER, ShaderDataType.UINT),
    RGB8UI(GL30C.GL_RGB8UI, GlVersion.GL_30, PixelFormat.RGB_INTEGER, ShaderDataType.UINT),
    RGBA8UI(GL30C.GL_RGBA8UI, GlVersion.GL_30, PixelFormat.RGBA_INTEGER, ShaderDataType.UINT),
    R16_SNORM(GL31C.GL_R16_SNORM, GlVersion.GL_31, PixelFormat.RED, ShaderDataType.FLOAT),
    RGB565(GL41C.GL_RGB565, GlVersion.GL_41, PixelFormat.RGB, ShaderDataType.FLOAT),
    RGB10_A2UI(GL33C.GL_RGB10_A2UI, GlVersion.GL_33, PixelFormat.RGBA_INTEGER, ShaderDataType.UINT);

    private final int glFormat;
    private final GlVersion minimumGlVersion;
    private final PixelFormat pixelFormat;
    private final ShaderDataType shaderDataType;

    InternalTextureFormat(int glFormat, GlVersion minimumGlVersion, PixelFormat pixelFormat, ShaderDataType shaderDataType) {
        this.glFormat = glFormat;
        this.minimumGlVersion = minimumGlVersion;
        this.pixelFormat = pixelFormat;
        this.shaderDataType = shaderDataType;
    }

    public int getGlFormat() { return glFormat; }
    public GlVersion getMinimumGlVersion() { return minimumGlVersion; }
    public PixelFormat getPixelFormat() { return pixelFormat; }
    public ShaderDataType getShaderDataType() { return shaderDataType; }
}
