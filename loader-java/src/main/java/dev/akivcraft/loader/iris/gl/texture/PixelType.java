package dev.akivcraft.loader.iris.gl.texture;

import dev.akivcraft.loader.iris.gl.GlVersion;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL30C;

// Derived from Iris (LGPL-3.0), adapted for AkivCraft.
public enum PixelType {
    BYTE(1, GL11C.GL_BYTE, GlVersion.GL_11),
    SHORT(2, GL11C.GL_SHORT, GlVersion.GL_11),
    INT(4, GL11C.GL_INT, GlVersion.GL_11),
    HALF_FLOAT(2, GL30C.GL_HALF_FLOAT, GlVersion.GL_30),
    FLOAT(4, GL11C.GL_FLOAT, GlVersion.GL_11),
    UNSIGNED_BYTE(1, GL11C.GL_UNSIGNED_BYTE, GlVersion.GL_11),
    UNSIGNED_SHORT(2, GL11C.GL_UNSIGNED_SHORT, GlVersion.GL_11),
    UNSIGNED_INT(4, GL11C.GL_UNSIGNED_INT, GlVersion.GL_11),
    UNSIGNED_INT_10F_11F_11F_REV(4, GL30C.GL_UNSIGNED_INT_10F_11F_11F_REV, GlVersion.GL_30),
    UNSIGNED_INT_5_9_9_9_REV(4, GL30C.GL_UNSIGNED_INT_5_9_9_9_REV, GlVersion.GL_30),
    UNSIGNED_SHORT_5_6_5(2, GL12C.GL_UNSIGNED_SHORT_5_6_5, GlVersion.GL_12),
    UNSIGNED_SHORT_4_4_4_4(2, GL12C.GL_UNSIGNED_SHORT_4_4_4_4, GlVersion.GL_12),
    UNSIGNED_SHORT_5_5_5_1(2, GL12C.GL_UNSIGNED_SHORT_5_5_5_1, GlVersion.GL_12),
    UNSIGNED_INT_8_8_8_8(4, GL12C.GL_UNSIGNED_INT_8_8_8_8, GlVersion.GL_12),
    UNSIGNED_INT_10_10_10_2(4, GL12C.GL_UNSIGNED_INT_10_10_10_2, GlVersion.GL_12);

    private final int byteSize;
    private final int glFormat;
    private final GlVersion minimumGlVersion;

    PixelType(int byteSize, int glFormat, GlVersion minimumGlVersion) {
        this.byteSize = byteSize;
        this.glFormat = glFormat;
        this.minimumGlVersion = minimumGlVersion;
    }

    public int getByteSize() { return byteSize; }
    public int getGlFormat() { return glFormat; }
    public GlVersion getMinimumGlVersion() { return minimumGlVersion; }
}
