package dev.akivcraft.loader.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL20C;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class AkivShaderRenderer {
    static final int NUM_COLORTEX = 8;

    private static final String FULLSCREEN_VS = """
        #version 330
        out vec2 vTexCoord;
        out vec2 texcoord;
        out vec2 texCoord;
        void main() {
            vec2 pos = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
            vTexCoord = pos;
            texcoord = pos;
            texCoord = pos;
            gl_Position = vec4(pos * 2.0 - 1.0, 0.0, 1.0);
        }
        """;

    private static final String COPY_FS = """
        #version 330
        in vec2 texCoord;
        uniform sampler2D colortex0;
        uniform sampler2D colortex1;
        uniform sampler2D noisetex;
        layout(location = 0) out vec4 akiv_FragData_0;
        void main() {
            vec4 c0 = texture(colortex0, texCoord);
            vec4 c1 = texture(colortex1, texCoord);
            float n = texture(noisetex, texCoord).r;
            // R = colortex0 (FBO-attached), G = colortex1 (FBO-attached), B = noisetex (standalone)
            akiv_FragData_0 = vec4(c0.r, c1.g, n, 1.0);
        }
        """;

    private static final FloatBuffer MAT4_BUF = BufferUtils.createFloatBuffer(16);

    private AkivShaderRenderer() {}

    public record CompiledPass(String name, int program, int drawBuffers, boolean isFinal) {}

    static String currentDimension(Minecraft mc) {
        try {
            var dimStr = mc.level.dimension().toString();
            if (dimStr.contains("the_nether")) return "world-1";
            if (dimStr.contains("the_end")) return "world1";
        } catch (Throwable ignored) {}
        return "world0";
    }

    static int getGpuTextureGlId(Object gpuTexture) {
        if (gpuTexture == null) return 0;
        try {
            var glIdMethod = gpuTexture.getClass().getMethod("glId");
            return (int) glIdMethod.invoke(gpuTexture);
        } catch (NoSuchMethodException ignored) {
            try {
                var textureMethod = gpuTexture.getClass().getMethod("texture");
                var inner = textureMethod.invoke(gpuTexture);
                if (inner != null) {
                    var glIdMethod = inner.getClass().getMethod("glId");
                    return (int) glIdMethod.invoke(inner);
                }
            } catch (Throwable ignored2) {}
        } catch (Throwable ignored) {}
        return 0;
    }

    @SuppressWarnings("unchecked")
    static void reloadIfNeeded(AkivShaderConfig.AkivShaderPack pack, String dimension, AkivPipeline pipeline) throws IOException {
        var timestamp = pack.timestamp();
        if (pipeline.currentPasses() != null && pack.equals(pipeline.loadedPack()) && timestamp == pipeline.loadedTimestamp() && dimension.equals(pipeline.compiledDimension())) return;

        var passList = pack.passes(dimension);
        if (passList.isEmpty()) {
            System.err.printf("AkivCraft shaderpack '%s' has no passes for dimension %s%n", pack.name(), dimension);
            pipeline.setPasses(null);
            return;
        }

        var compiled = new ArrayList<CompiledPass>();
        var copyMode = Boolean.getBoolean("akivcraft.copyShaders");
        for (var i = 0; i < passList.size(); i++) {
            var pass = passList.get(i);
            var isFinal = i == passList.size() - 1 && pass.name().equals("final");
            try {
                int program;
                int drawBuffers;
                if (copyMode && !isFinal) {
                    program = link(FULLSCREEN_VS, COPY_FS);
                    drawBuffers = 1;
                    System.out.printf("AkivCraft COPY MODE pass: %s%n", pass.name());
                } else {
                    var fragSrc = pack.readText(pass.fragmentPath());
                    var vertSrc = pass.vertexPath() != null ? pack.readText(pass.vertexPath()) : FULLSCREEN_VS;
                    var processedFrag = preprocessShader(pack, pass.fragmentPath(), fragSrc, false);
                    var processedVert = pass.vertexPath() != null
                        ? preprocessShader(pack, pass.vertexPath(), vertSrc, true)
                        : FULLSCREEN_VS;
                    try {
                        var dumpDir = java.nio.file.Path.of(System.getProperty("akivcraft.dumpdir", "/tmp/opencode/shader-dump"));
                        java.nio.file.Files.createDirectories(dumpDir);
                        java.nio.file.Files.writeString(dumpDir.resolve(pass.name() + ".frag.glsl"), processedFrag);
                        java.nio.file.Files.writeString(dumpDir.resolve(pass.name() + ".vert.glsl"), processedVert);
                    } catch (Throwable ignored) {}
                    program = link(processedVert, processedFrag);
                    drawBuffers = isFinal ? 1 : parseDrawBuffers(expandIncludes(pack, pass.fragmentPath(), fragSrc, new HashSet<>(), 0));
                }
                compiled.add(new CompiledPass(pass.name(), program, drawBuffers, isFinal));
                System.out.printf("AkivCraft compiled shader pass: %s (drawBuffers=%s)%n", pass.name(), Integer.toBinaryString(drawBuffers));
            } catch (Throwable error) {
                System.err.printf("AkivCraft failed to compile shader pass %s: %s%n", pass.name(), error.getMessage());
            }
        }

        if (compiled.isEmpty()) {
            pipeline.setPasses(null);
            return;
        }

        if (pipeline.currentPasses() != null) for (var p : pipeline.currentPasses()) GL20C.glDeleteProgram(p.program());
        pipeline.setPasses(compiled);
        pipeline.setLoadedPack(pack, timestamp, dimension);
    }

    static void setAllUniforms(int prog, Minecraft mc, int w, int h, int frameCounter, long firstRenderNanos, float[] prevCamPos, boolean prevCamInit) {
        setSampler(prog, "colortex0", 0);
        setSampler(prog, "colortex1", 1);
        setSampler(prog, "colortex2", 2);
        setSampler(prog, "colortex3", 3);
        setSampler(prog, "colortex4", 4);
        setSampler(prog, "colortex5", 5);
        setSampler(prog, "colortex6", 6);
        setSampler(prog, "colortex7", 7);
        setSampler(prog, "gcolor", 0);
        setSampler(prog, "gdepth", 1);
        setSampler(prog, "gnormal", 2);
        setSampler(prog, "composite", 3);
        setSampler(prog, "gaux1", 4);
        setSampler(prog, "gaux2", 5);
        setSampler(prog, "gaux3", 6);
        setSampler(prog, "gaux4", 7);
        setSampler(prog, "depthtex0", AkivPipeline.U_DEPTHTEX0);
        setSampler(prog, "depthtex1", AkivPipeline.U_DEPTHTEX1);
        setSampler(prog, "noisetex", AkivPipeline.U_NOISETEX);
        setSampler(prog, "shadowtex0", AkivPipeline.U_SHADOWTEX0);
        setSampler(prog, "shadow", AkivPipeline.U_SHADOWTEX0);
        setSampler(prog, "shadowtex1", AkivPipeline.U_SHADOWTEX1);
        setSampler(prog, "shadowcolor0", AkivPipeline.U_SHADOWCOLOR0);
        setSampler(prog, "shadowcolor1", AkivPipeline.U_SHADOWCOLOR1);
        setSampler(prog, "lighttex0", AkivPipeline.U_LIGHTTEX0);
        setSampler(prog, "lighttex1", AkivPipeline.U_LIGHTTEX1);

        setUniform1i(prog, "viewWidth", w);
        setUniform1i(prog, "viewHeight", h);
        setUniform1f(prog, "aspectRatio", (float) w / h);
        setUniform1f(prog, "frameTimeCounter", (System.nanoTime() - firstRenderNanos) / 1_000_000_000.0f);
        setUniform1i(prog, "frameCounter", frameCounter);

        float[] camPos = null;
        float far = 1000f;
        float near = 0.05f;
        float rainStrength = 0f;
        int isEyeInWater = 0;
        float sunAngle = 0.25f;
        int skyColorPacked = 0;
        long gameTime = 0;

        try {
            var gr = mc.gameRenderer;
            var state = gr.getGameRenderState();
            if (state != null && state.levelRenderState != null) {
                var level = state.levelRenderState;
                gameTime = level.gameTime;

                var cam = level.cameraRenderState;
                if (cam != null && cam.pos != null) {
                    camPos = new float[]{(float) cam.pos.x, (float) cam.pos.y, (float) cam.pos.z};
                }
                far = cam != null ? Math.max(cam.depthFar, 1f) : far;
                if (cam != null && cam.projectionMatrix != null) {
                    setUniformMatrix4(prog, "gbufferProjection", cam.projectionMatrix);
                    var invProj = new Matrix4f(cam.projectionMatrix).invert();
                    setUniformMatrix4(prog, "gbufferProjectionInverse", invProj);
                }
                if (cam != null && cam.fogType != null) {
                    var ft = cam.fogType;
                    isEyeInWater = switch (ft.name()) {
                        case "WATER" -> 1;
                        case "LAVA" -> 2;
                        case "POWDER_SNOW" -> 3;
                        default -> 0;
                    };
                }
                rainStrength = level.weatherRenderState != null ? level.weatherRenderState.intensity : 0f;

                var sky = level.skyRenderState;
                if (sky != null) {
                    sunAngle = sky.sunAngle;
                    skyColorPacked = sky.skyColor;
                }
            }
        } catch (Throwable ignored) {}

        setUniform1f(prog, "near", near);
        setUniform1f(prog, "far", far);
        setUniform1i(prog, "isEyeInWater", isEyeInWater);
        setUniform1f(prog, "rainStrength", rainStrength);
        setUniform1f(prog, "wetness", rainStrength);
        setUniform1f(prog, "sunAngle", sunAngle);
        setUniform1i(prog, "worldTime", (int) (gameTime % 24000));

        if (camPos != null) {
            setUniform3f(prog, "cameraPosition", camPos[0], camPos[1], camPos[2]);
            setUniform3f(prog, "previousCameraPosition",
                prevCamInit ? prevCamPos[0] : camPos[0],
                prevCamInit ? prevCamPos[1] : camPos[1],
                prevCamInit ? prevCamPos[2] : camPos[2]);
        }

        float ang = sunAngle * 2.0f * (float) Math.PI;
        setUniform3f(prog, "sunPosition", (float) Math.cos(ang) * 100f, (float) Math.sin(ang) * 100f, 0f);
        setUniform3f(prog, "moonPosition", (float) Math.cos(ang + (float) Math.PI) * 100f, (float) Math.sin(ang + (float) Math.PI) * 100f, 0f);
        setUniform3f(prog, "upVec", 0f, 1f, 0f);
        setUniform3f(prog, "shadowLightPosition", (float) Math.cos(ang) * 100f, (float) Math.sin(ang) * 100f, 0f);

        if (skyColorPacked != 0) {
            float sr = ((skyColorPacked >> 16) & 0xFF) / 255f;
            float sg = ((skyColorPacked >> 8) & 0xFF) / 255f;
            float sb = (skyColorPacked & 0xFF) / 255f;
            setUniform3f(prog, "skyColor", sr, sg, sb);
            setUniform3f(prog, "fogColor", sr, sg, sb);
        } else {
            setUniform3f(prog, "skyColor", 0.37f, 0.63f, 1.0f);
            setUniform3f(prog, "fogColor", 0.37f, 0.63f, 1.0f);
        }

        setUniform1f(prog, "nightVision", 0f);
        setUniform1f(prog, "blindness", 0f);
        setUniform1f(prog, "darknessFactor", 0f);
        setUniform1f(prog, "darknessLightFactor", 0f);
        setUniform2i(prog, "eyeBrightnessSmooth", 240, 240);

        try {
            setUniformMatrix4(prog, "gbufferModelView", RenderSystem.getModelViewMatrix());
            var invMv = new Matrix4f(RenderSystem.getModelViewMatrix()).invert();
            setUniformMatrix4(prog, "gbufferModelViewInverse", invMv);
            setUniformMatrix4(prog, "gbufferPreviousModelView", RenderSystem.getModelViewMatrix());
            if (camPos != null) {
                setUniformMatrix4(prog, "gbufferPreviousProjection", new Matrix4f().identity());
            }
        } catch (Throwable ignored) {}

        var identity = new Matrix4f().identity();
        setUniformMatrix4(prog, "shadowModelView", identity);
        setUniformMatrix4(prog, "shadowProjection", identity);

        setUniform1i(prog, "moonPhase", 0);
        setUniform1i(prog, "bedrockLevel", 0);
        setUniform1f(prog, "shadowMapResolution", 2048f);
        setUniform1f(prog, "shadowDistance", 256f);
        setUniform1f(prog, "sunPathRotation", -40f);
        setUniform1f(prog, "blindFactor", 0f);
        setUniform1f(prog, "timeAngle", (float) (gameTime % 24000) / 24000f);
        setUniform1f(prog, "timeBrightness", Math.max((float) Math.sin(sunAngle * 2.0 * Math.PI), 0f));
        setUniform1f(prog, "shadowFade", 1f);
    }

    private static final Pattern DRAWBUFFERS_RE = Pattern.compile("/\\*\\s*DRAWBUFFERS:\\s*([0-9]+)\\s*\\*/");

    private static int parseDrawBuffers(String source) {
        String lastDigits = null;
        var matcher = DRAWBUFFERS_RE.matcher(source);
        while (matcher.find()) {
            lastDigits = matcher.group(1);
        }
        int mask = 0;
        if (lastDigits != null) {
            for (char c : lastDigits.toCharArray()) {
                int idx = c - '0';
                if (idx >= 0 && idx < NUM_COLORTEX) mask |= (1 << idx);
            }
        }
        if (mask == 0) mask = 1;
        return mask;
    }

    private static String preprocessShader(AkivShaderConfig.AkivShaderPack pack, String currentPath, String source, boolean vertex) throws IOException {
        var expanded = expandIncludes(pack, currentPath, source, new HashSet<>(), 0);
        var body = new StringBuilder();
        var extensions = new java.util.LinkedHashSet<String>();
        var hasFragData = false;
        var needsPosition = false;
        var needsUv0 = false;

        for (var line : expanded.split("\\R", -1)) {
            var trimmed = line.trim();
            if (trimmed.startsWith("#version")) continue;
            if (trimmed.startsWith("#extension")) {
                if (!trimmed.contains("GL_ARB_shader_texture_lod")) extensions.add(trimmed);
                continue;
            }

            var patched = line;
            patched = patched.replaceAll("\\btexture2DGradARB\\b", "textureGrad");
            patched = patched.replaceAll("\\btexture2DGrad\\b", "textureGrad");
            patched = patched.replace("texture2DLod", "textureLod");
            patched = patched.replaceAll("\\btexture2D\\b", "texture");
            patched = patched.replaceAll("\\battribute\\b", "in");
            patched = patched.replaceAll("\\bvarying\\b", vertex ? "out" : "in");

            if (vertex) {
                if (patched.contains("gl_MultiTexCoord0")) needsUv0 = true;
                if (patched.contains("gl_Vertex") || patched.contains("ftransform()")) needsPosition = true;
                patched = patched
                    .replace("gl_TextureMatrix[0]", "mat4(1.0)")
                    .replace("gl_TextureMatrix[1]", "mat4(1.0)")
                    .replace("gl_TextureMatrix[2]", "mat4(1.0)")
                    .replace("gl_TextureMatrix[3]", "mat4(1.0)")
                    .replace("gl_TextureMatrix[4]", "mat4(1.0)")
                    .replace("gl_TextureMatrix[5]", "mat4(1.0)")
                    .replace("gl_TextureMatrix[6]", "mat4(1.0)")
                    .replace("gl_TextureMatrix[7]", "mat4(1.0)")
                    .replace("gl_MultiTexCoord0", "vec4(UV0, 0.0, 1.0)")
                    .replace("gl_MultiTexCoord1", "vec4(UV0, 0.0, 1.0)")
                    .replace("gl_MultiTexCoord2", "vec4(UV0, 0.0, 1.0)")
                    .replace("gl_MultiTexCoord3", "vec4(UV0, 0.0, 1.0)")
                    .replace("gl_MultiTexCoord4", "vec4(UV0, 0.0, 1.0)")
                    .replace("gl_MultiTexCoord5", "vec4(UV0, 0.0, 1.0)")
                    .replace("gl_MultiTexCoord6", "vec4(UV0, 0.0, 1.0)")
                    .replace("gl_MultiTexCoord7", "vec4(UV0, 0.0, 1.0)")
                    .replace("gl_Color", "vec4(1.0)")
                    .replace("gl_Normal", "vec3(0.0, 0.0, 1.0)")
                    .replace("gl_NormalMatrix", "mat3(1.0)")
                    .replace("gl_FrontMaterial", "vec4(1.0)")
                    .replace("gl_ModelViewProjectionMatrix", "mat4(2.0, 0.0, 0.0, 0.0, 0.0, 2.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, -1.0, -1.0, 0.0, 1.0)")
                    .replace("gl_ModelViewMatrix", "mat4(1.0)")
                    .replace("gl_ProjectionMatrix", "mat4(2.0, 0.0, 0.0, 0.0, 0.0, 2.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, -1.0, -1.0, 0.0, 1.0)")
                    .replace("gl_Vertex", "vec4(Position, 1.0)");
            }

            if (!vertex) {
                if (patched.contains("gl_FragData[") || patched.contains("gl_FragColor")) hasFragData = true;
                patched = patched.replaceAll("gl_FragData\\[(\\d+)\\]", "akiv_FragData_$1");
                patched = patched.replace("gl_FragColor", "akiv_FragData_0");
            }

            body.append(patched).append('\n');
        }

        var header = new StringBuilder("#version 330\n");
        for (var ext : extensions) header.append(ext).append('\n');
        header.append("#define AKIVCRAFT_SHADER 1\n");
        header.append("#define MC_VERSION 1260102\n");
        header.append("#define MC_GL_VERSION 330\n");
        header.append("#define MC_GLSL_VERSION 330\n");
        header.append("#define IRIS_VERSION 10900\n");
        if (vertex) {
            if (needsPosition) header.append("#define Position vec3(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2), 0.0)\n");
            if (needsUv0) header.append("#define UV0 vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2))\n");
            if (expanded.contains("ftransform")) header.append("vec4 ftransform() { vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2)); return vec4(p * 2.0 - 1.0, 0.0, 1.0); }\n");
        }
        if (!vertex && hasFragData) {
            for (int i = 0; i < NUM_COLORTEX; i++) {
                header.append("layout(location = ").append(i).append(") out vec4 akiv_FragData_").append(i).append(";\n");
            }
        }
        if (!vertex) {
            header.append("#define shadow2D(s, p) vec4(texture(s, p))\n");
            header.append("#define shadow2DLod(s, p, l) vec4(textureLod(s, p, l))\n");
        }
        return header.append(body).toString();
    }

    private static String expandIncludes(
        AkivShaderConfig.AkivShaderPack pack,
        String currentPath,
        String source,
        Set<String> stack,
        int depth
    ) throws IOException {
        if (depth > 32) throw new IOException("shader include depth exceeded");
        var normalizedPath = currentPath;
        if (!stack.add(normalizedPath)) throw new IOException("recursive shader include: " + currentPath);

        var expanded = new StringBuilder();
        for (var line : source.split("\\R", -1)) {
            var include = extractIncludePath(line);
            if (include == null) {
                expanded.append(line).append('\n');
                continue;
            }
            var resolved = resolveInclude(currentPath, include);
            expanded.append(expandIncludes(pack, resolved, pack.readText(resolved), stack, depth + 1));
        }

        stack.remove(normalizedPath);
        return expanded.toString();
    }

    private static String extractIncludePath(String line) {
        var trimmed = line.trim();
        if (!trimmed.startsWith("#include")) return null;
        var firstQuote = trimmed.indexOf('"');
        var lastQuote = trimmed.lastIndexOf('"');
        if (firstQuote >= 0 && lastQuote > firstQuote) return trimmed.substring(firstQuote + 1, lastQuote);
        var firstAngle = trimmed.indexOf('<');
        var lastAngle = trimmed.lastIndexOf('>');
        if (firstAngle >= 0 && lastAngle > firstAngle) return trimmed.substring(firstAngle + 1, lastAngle);
        return null;
    }

    private static String resolveInclude(String currentPath, String include) {
        var normalized = include.replace('\\', '/');
        if (normalized.startsWith("/")) return "shaders" + normalized;
        if (normalized.startsWith("shaders/")) return normalized;
        var parent = java.nio.file.Path.of(currentPath).getParent();
        var resolved = (parent == null ? java.nio.file.Path.of(normalized) : parent.resolve(normalized))
            .normalize().toString().replace('\\', '/');
        if (resolved.startsWith("../") || resolved.equals("..")) throw new IllegalArgumentException("invalid include: " + include);
        return resolved;
    }

    private static int link(String vertexSource, String fragmentSource) {
        var vertex = compile(GL20C.GL_VERTEX_SHADER, vertexSource);
        var fragment = compile(GL20C.GL_FRAGMENT_SHADER, fragmentSource);
        var program = GL20C.glCreateProgram();
        GL20C.glAttachShader(program, vertex);
        GL20C.glAttachShader(program, fragment);
        GL20C.glLinkProgram(program);
        GL20C.glDeleteShader(vertex);
        GL20C.glDeleteShader(fragment);
        if (GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) == GL11C.GL_FALSE) {
            var log = GL20C.glGetProgramInfoLog(program);
            GL20C.glDeleteProgram(program);
            throw new IllegalStateException("shader link failed: " + log);
        }
        return program;
    }

    private static int compile(int type, String source) {
        var shader = GL20C.glCreateShader(type);
        GL20C.glShaderSource(shader, source);
        GL20C.glCompileShader(shader);
        if (GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) == GL11C.GL_FALSE) {
            var log = GL20C.glGetShaderInfoLog(shader);
            GL20C.glDeleteShader(shader);
            var preview = source.lines().limit(5).reduce("", (a, b) -> a + "\n" + b);
            throw new IllegalStateException("shader compile failed: " + log + "\n--- first 5 lines ---" + preview);
        }
        return shader;
    }

    private static void setSampler(int prog, String name, int unit) {
        var loc = GL20C.glGetUniformLocation(prog, name);
        if (loc >= 0) GL20C.glUniform1i(loc, unit);
    }

    private static void setUniform1i(int prog, String name, int value) {
        var loc = GL20C.glGetUniformLocation(prog, name);
        if (loc >= 0) GL20C.glUniform1i(loc, value);
    }

    private static void setUniform1f(int prog, String name, float value) {
        var loc = GL20C.glGetUniformLocation(prog, name);
        if (loc >= 0) GL20C.glUniform1f(loc, value);
    }

    private static void setUniform3f(int prog, String name, float x, float y, float z) {
        var loc = GL20C.glGetUniformLocation(prog, name);
        if (loc >= 0) GL20C.glUniform3f(loc, x, y, z);
    }

    private static void setUniform2i(int prog, String name, int x, int y) {
        var loc = GL20C.glGetUniformLocation(prog, name);
        if (loc >= 0) GL20C.glUniform2i(loc, x, y);
    }

    private static void setUniformMatrix4(int prog, String name, Matrix4f matrix) {
        var loc = GL20C.glGetUniformLocation(prog, name);
        if (loc < 0) return;
        MAT4_BUF.clear();
        matrix.get(MAT4_BUF);
        MAT4_BUF.flip();
        GL20C.glUniformMatrix4fv(loc, false, MAT4_BUF);
    }
}
