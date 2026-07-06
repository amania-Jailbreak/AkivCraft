package dev.akivcraft.loader.shader;

import com.mojang.blaze3d.shaders.ShaderType;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.Set;

// Shader source modification hook called by GlDeviceTransformer.
// During level rendering, this can replace MC's shader source with shaderpack
// gbuffers shaders to provide proper per-pixel normals, specular, etc.
//
// Currently in "logging" mode: tracks which shaders are compiled during level
// rendering for future mapping. Actual gbuffers replacement is deferred until
// the MC 26.1 varying format is fully understood.
public final class AkivShaderOverrides {
    private static volatile boolean overrideEnabled;
    private static final Set<String> loggedShaders = new HashSet<>();

    private AkivShaderOverrides() {}

    public static void setOverrideEnabled(boolean enabled) {
        overrideEnabled = enabled;
    }

    public static String modifySource(Identifier id, ShaderType type, String originalSource) {
        if (!overrideEnabled || originalSource == null) return originalSource;

        var key = id.toString() + "/" + type.getName();
        if (loggedShaders.add(key)) {
            System.out.printf("AkivCraft shader trace: %s/%s%n", id, type.getName());
        }

        return originalSource;
    }
}
