package dev.akivcraft.loader.shader;

import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Standalone pipeline test — no Minecraft dependency.
 * Creates a GLFW window, fills test textures, runs Complementary shader passes, and reads back results.
 */
public final class PipelineStandaloneTest {
    private static final int NUM_COLORTEX = 8;
    private static final int W = 854;
    private static final int H = 480;

    // Texture unit assignments (same as AkivPipeline)
    private static final int U_DEPTHTEX0 = 8;
    private static final int U_DEPTHTEX1 = 9;
    private static final int U_NOISETEX = 10;
    private static final int U_SHADOWTEX0 = 11;
    private static final int U_SHADOWTEX1 = 12;
    private static final int U_SHADOWCOLOR0 = 13;
    private static final int U_SHADOWCOLOR1 = 14;
    private static final int U_LIGHTTEX0 = 15;
    private static final int U_LIGHTTEX1 = 16;

    // Internal formats (matching AkivPipeline.COLORTEX_FORMATS)
    private static final int[] COLORTEX_INTERNAL = {
        GL30C.GL_RGBA16F, GL11C.GL_RGBA8, GL30C.GL_RGBA16F, GL11C.GL_RGBA8,
        GL30C.GL_R8,      GL11C.GL_RGBA8, GL30C.GL_RGBA16F, GL30C.GL_RGBA16F
    };

    // Fullscreen vertex shader
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

    // Shaderpack root
    private final Path shaderpackPath;

    // GL resources
    private int[] mainTextures = new int[NUM_COLORTEX];
    private int[] altTextures = new int[NUM_COLORTEX];
    private int depthTexture;
    private int noiseTexture;
    private int shadowDepth0, shadowDepth1;
    private int shadowColor0, shadowColor1;
    private int light3D0, light3D1;
    private int vao;
    private int windowFbo;
    private int dummyColorTex;

    // Compiled passes
    record Pass(String name, int program, int drawBuffers, boolean isFinal) {}
    private List<Pass> passes;

    public PipelineStandaloneTest(Path shaderpackPath) {
        this.shaderpackPath = shaderpackPath;
    }
    private boolean preprocessOnly = false;

    public PipelineStandaloneTest(Path shaderpackPath, boolean preprocessOnly) {
        this.shaderpackPath = shaderpackPath;
        this.preprocessOnly = preprocessOnly;
    }

    public void run() throws Exception {
        System.out.println("=== AkivCraft Pipeline Standalone Test ===");
        System.out.println("Shaderpack: " + shaderpackPath);

        if (preprocessOnly) {
            compileShaders();
            System.out.println("\nPreprocess-only mode complete. Dumps at /tmp/opencode/shader-dump-standalone/");
            return;
        }

        initGlfw();
        createResources();
        fillTestData();
        compileShaders();
        runPipeline();
        presentAndLoop();
    }

    // ─── GLFW ────────────────────────────────────────────────────────────

    private long window;

    private void initGlfw() {
        if (!GLFW.glfwInit()) throw new RuntimeException("GLFW init failed");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);

        window = GLFW.glfwCreateWindow(W, H, "AkivCraft Pipeline Test", 0, 0);
        if (window == 0) throw new RuntimeException("Window creation failed");
        GLFW.glfwMakeContextCurrent(window);
        GLFW.glfwSwapInterval(0);
        GL.createCapabilities();

        System.out.println("OpenGL: " + GL11C.glGetString(GL11C.GL_VERSION));
        System.out.println("GLSL: " + GL11C.glGetString(GL20C.GL_SHADING_LANGUAGE_VERSION));
    }

    // ─── GL Resources ────────────────────────────────────────────────────

    private void createResources() {
        // Create color textures (main + alt for ping-pong)
        for (int i = 0; i < NUM_COLORTEX; i++) {
            mainTextures[i] = createColorTexture(COLORTEX_INTERNAL[i], W, H);
            altTextures[i] = createColorTexture(COLORTEX_INTERNAL[i], W, H);
        }

        // Depth texture
        depthTexture = GL11C.glGenTextures();
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, depthTexture);
        GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL14C.GL_DEPTH_COMPONENT24, W, H, 0,
            GL11C.GL_DEPTH_COMPONENT, GL11C.GL_FLOAT, (ByteBuffer) null);
        setNearestClamp();

        // Noise texture
        noiseTexture = generateNoiseTexture();

        // Shadow textures
        shadowDepth0 = createShadowDepth();
        shadowDepth1 = createShadowDepth();
        shadowColor0 = createDummyColor();
        shadowColor1 = createDummyColor();
        light3D0 = createDummy3D();
        light3D1 = createDummy3D();

        // VAO
        vao = GL30C.glGenVertexArrays();

        // Window FBO (for final pass output)
        windowFbo = GL30C.glGenFramebuffers();
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, windowFbo);
        GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0,
            GL11C.GL_TEXTURE_2D, mainTextures[0], 0);
        int status = GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER);
        System.out.println("windowFbo status: 0x" + Integer.toHexString(status));
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, 0);

        System.out.println("Resources created OK");
    }

    private int createColorTexture(int internalFormat, int w, int h) {
        var tex = GL11C.glGenTextures();
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, tex);
        GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, internalFormat, w, h, 0,
            GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        setNearestClamp();
        return tex;
    }

    private void setNearestClamp() {
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL12C.GL_CLAMP_TO_EDGE);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL12C.GL_CLAMP_TO_EDGE);
    }

    private int generateNoiseTexture() {
        int size = 64;
        var data = BufferUtils.createByteBuffer(size * size * 4);
        var rng = new java.util.Random(0L);
        while (data.hasRemaining()) data.put((byte) rng.nextInt(256));
        data.flip();
        var tex = GL11C.glGenTextures();
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, tex);
        GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RGBA8, size, size, 0,
            GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, data);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL11C.GL_REPEAT);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL11C.GL_REPEAT);
        return tex;
    }

    private int createShadowDepth() {
        int size = 2048;
        var tex = GL11C.glGenTextures();
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, tex);
        GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL14C.GL_DEPTH_COMPONENT16, size, size, 0,
            GL11C.GL_DEPTH_COMPONENT, GL11C.GL_FLOAT, (ByteBuffer) null);
        setNearestClamp();
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL14C.GL_TEXTURE_COMPARE_MODE, 0x884E);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL14C.GL_TEXTURE_COMPARE_FUNC, GL11C.GL_LEQUAL);
        var fbo = GL30C.glGenFramebuffers();
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fbo);
        GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_DEPTH_ATTACHMENT,
            GL11C.GL_TEXTURE_2D, tex, 0);
        GL20C.glDrawBuffer(GL11C.GL_NONE);
        GL11C.glClearDepth(1.0);
        GL11C.glClear(GL11C.GL_DEPTH_BUFFER_BIT);
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, 0);
        GL30C.glDeleteFramebuffers(fbo);
        return tex;
    }

    private int createDummyColor() {
        if (dummyColorTex != 0) return dummyColorTex;
        int size = 4;
        var tex = GL11C.glGenTextures();
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, tex);
        GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RGBA8, size, size, 0,
            GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        setNearestClamp();
        var fbo = GL30C.glGenFramebuffers();
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fbo);
        GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0,
            GL11C.GL_TEXTURE_2D, tex, 0);
        GL20C.glDrawBuffer(GL30C.GL_COLOR_ATTACHMENT0);
        GL11C.glClearColor(0, 0, 0, 1);
        GL11C.glClear(GL11C.GL_COLOR_BUFFER_BIT);
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, 0);
        GL30C.glDeleteFramebuffers(fbo);
        dummyColorTex = tex;
        return tex;
    }

    private int createDummy3D() {
        var tex = GL11C.glGenTextures();
        GL11C.glBindTexture(GL12C.GL_TEXTURE_3D, tex);
        var data = BufferUtils.createByteBuffer(4);
        data.put((byte) 0).put((byte) 0).put((byte) 0).put((byte) 0xFF).flip();
        GL12C.glTexImage3D(GL12C.GL_TEXTURE_3D, 0, GL11C.GL_RGBA8, 1, 1, 1, 0,
            GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, data);
        GL11C.glTexParameteri(GL12C.GL_TEXTURE_3D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
        GL11C.glTexParameteri(GL12C.GL_TEXTURE_3D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
        return tex;
    }

    // ─── Test Data ───────────────────────────────────────────────────────

    private void fillTestData() {
        // Fill colortex0 (RGBA8) with a blue-green gradient so shaders can read it
        var data = BufferUtils.createByteBuffer(W * H * 4);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int idx = (y * W + x) * 4;
                data.put(idx, (byte) 68);        // R = 68
                data.put(idx + 1, (byte) 67);    // G = 67
                data.put(idx + 2, (byte) 30);    // B = 30
                data.put(idx + 3, (byte) 255);   // A = 255
            }
        }
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, mainTextures[0]);
        GL11C.glTexSubImage2D(GL11C.GL_TEXTURE_2D, 0, 0, 0, W, H,
            GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, data);

        // Fill colortex1 (RGBA8) with white normals (pointing up)
        var normData = BufferUtils.createByteBuffer(W * H * 4);
        for (int i = 0; i < W * H * 4; i += 4) {
            normData.put(i, (byte) 128);     // X normal = 0
            normData.put(i + 1, (byte) 128); // Y normal = 0
            normData.put(i + 2, (byte) 255); // Z normal = 1
            normData.put(i + 3, (byte) 255);
        }
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, mainTextures[1]);
        GL11C.glTexSubImage2D(GL11C.GL_TEXTURE_2D, 0, 0, 0, W, H,
            GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, normData);

        // Fill depth with mid-distance (0.5)
        var depthData = BufferUtils.createFloatBuffer(W * H);
        for (int i = 0; i < W * H; i++) depthData.put(0.5f);
        depthData.flip();
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, depthTexture);
        GL11C.glTexSubImage2D(GL11C.GL_TEXTURE_2D, 0, 0, 0, W, H,
            GL11C.GL_DEPTH_COMPONENT, GL11C.GL_FLOAT, depthData);

        // Verify
        System.out.println("Test data filled:");
        System.out.println("  colortex0 center = (68, 67, 30)");
        System.out.println("  colortex1 center = (128, 128, 255)");
        System.out.println("  depthtex0 center = 0.5");

        // Also fill alt textures with same data
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, altTextures[0]);
        GL11C.glTexSubImage2D(GL11C.GL_TEXTURE_2D, 0, 0, 0, W, H,
            GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, data);
    }

    // ─── Shaderpack Loading & Compilation ────────────────────────────────

    private record ShaderPassInfo(String name, String fragmentPath, String vertexPath) {}

    private List<ShaderPassInfo> discoverPasses() throws IOException {
        var dimension = "world0";
        var passOrder = new String[]{"deferred", "deferred1", "deferred2", "deferred3",
            "composite", "composite1", "composite2", "composite3", "composite4",
            "composite5", "composite6", "composite7", "final"};
        var result = new ArrayList<ShaderPassInfo>();
        for (var passName : passOrder) {
            var fsh = findPassFile(dimension, passName);
            if (fsh == null) continue;
            var vsh = fsh.substring(0, fsh.length() - ".fsh".length()) + ".vsh";
            boolean hasVsh = Files.isRegularFile(shaderpackPath.resolve(vsh));
            result.add(new ShaderPassInfo(passName, fsh, hasVsh ? vsh : null));
        }
        return result;
    }

    private String findPassFile(String dimension, String passName) {
        var dimPath = "shaders/" + dimension + "/" + passName + ".fsh";
        if (Files.isRegularFile(shaderpackPath.resolve(dimPath))) return dimPath;
        var rootPath = "shaders/" + passName + ".fsh";
        if (Files.isRegularFile(shaderpackPath.resolve(rootPath))) return rootPath;
        return null;
    }

    private String readShaderFile(String relativePath) throws IOException {
        var normalized = Path.of(relativePath).normalize().toString().replace('\\', '/');
        return Files.readString(shaderpackPath.resolve(normalized));
    }

    private void compileShaders() throws IOException {
        var passInfos = discoverPasses();
        System.out.println("Found " + passInfos.size() + " passes:");
        for (var p : passInfos) System.out.println("  " + p.name());

        passes = new ArrayList<>();
        for (int i = 0; i < passInfos.size(); i++) {
            var info = passInfos.get(i);
            var isFinal = i == passInfos.size() - 1 && info.name().equals("final");
            try {
                var fragSrc = readShaderFile(info.fragmentPath);
                var vertSrc = info.vertexPath != null ? readShaderFile(info.vertexPath) : FULLSCREEN_VS;
                var processedFrag = preprocessShader(info.fragmentPath, fragSrc, false);
                var processedVert = info.vertexPath != null
                    ? preprocessShader(info.vertexPath, vertSrc, true)
                    : FULLSCREEN_VS;

                // Dump
                try {
                    var dumpDir = Path.of("/tmp/opencode/shader-dump-standalone");
                    Files.createDirectories(dumpDir);
                    Files.writeString(dumpDir.resolve(info.name() + ".frag.glsl"), processedFrag);
                    Files.writeString(dumpDir.resolve(info.name() + ".vert.glsl"), processedVert);
                } catch (Throwable ignored) {}

                var drawBuffers = isFinal ? 1 : parseDrawBuffers(expandIncludes(info.fragmentPath, fragSrc, new HashSet<>(), 0));

                if (preprocessOnly) {
                    // Just count lines and show a preview
                    System.out.printf("  preprocessed %s (%d frag lines, %d vert lines, drawBuffers=%s)%n",
                        info.name(),
                        processedFrag.split("\\R", -1).length,
                        processedVert.split("\\R", -1).length,
                        Integer.toBinaryString(drawBuffers));
                    passes.add(new Pass(info.name(), 0, drawBuffers, isFinal));
                } else {
                    var program = link(processedVert, processedFrag);
                    passes.add(new Pass(info.name(), program, drawBuffers, isFinal));
                    System.out.printf("  compiled %s (drawBuffers=%s, program=%d)%n",
                        info.name(), Integer.toBinaryString(drawBuffers), program);
                }
            } catch (Throwable error) {
                System.err.printf("  FAILED %s: %s%n", info.name(), error.getMessage());
                error.printStackTrace();
            }
        }
    }

    // ─── Shader Preprocessing (copied from AkivShaderRenderer) ───────────

    private static final Pattern DRAWBUFFERS_RE = Pattern.compile("/\\*\\s*DRAWBUFFERS:\\s*([0-9]+)\\s*\\*/");

    private int parseDrawBuffers(String source) {
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

    private String preprocessShader(String currentPath, String source, boolean vertex) throws IOException {
        var expanded = expandIncludes(currentPath, source, new HashSet<>(), 0);
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

    private String expandIncludes(String currentPath, String source, Set<String> stack, int depth) throws IOException {
        if (depth > 32) throw new IOException("shader include depth exceeded");
        if (!stack.add(currentPath)) throw new IOException("recursive shader include: " + currentPath);

        var expanded = new StringBuilder();
        for (var line : source.split("\\R", -1)) {
            var include = extractIncludePath(line);
            if (include == null) {
                expanded.append(line).append('\n');
                continue;
            }
            var resolved = resolveInclude(currentPath, include);
            expanded.append(expandIncludes(resolved, readShaderFile(resolved), stack, depth + 1));
        }

        stack.remove(currentPath);
        return expanded.toString();
    }

    private String extractIncludePath(String line) {
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

    private String resolveInclude(String currentPath, String include) {
        var normalized = include.replace('\\', '/');
        if (normalized.startsWith("/")) return "shaders" + normalized;
        if (normalized.startsWith("shaders/")) return normalized;
        var parent = Path.of(currentPath).getParent();
        var resolved = (parent == null ? Path.of(normalized) : parent.resolve(normalized))
            .normalize().toString().replace('\\', '/');
        return resolved;
    }

    // ─── Shader Compile & Link ───────────────────────────────────────────

    private int link(String vertexSource, String fragmentSource) {
        var vert = compile(GL20C.GL_VERTEX_SHADER, vertexSource);
        var frag = compile(GL20C.GL_FRAGMENT_SHADER, fragmentSource);
        var program = GL20C.glCreateProgram();
        GL20C.glAttachShader(program, vert);
        GL20C.glAttachShader(program, frag);
        GL20C.glLinkProgram(program);
        GL20C.glDeleteShader(vert);
        GL20C.glDeleteShader(frag);
        if (GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) == GL11C.GL_FALSE) {
            var log = GL20C.glGetProgramInfoLog(program);
            GL20C.glDeleteProgram(program);
            throw new IllegalStateException("shader link failed: " + log);
        }
        return program;
    }

    private int compile(int type, String source) {
        var shader = GL20C.glCreateShader(type);
        GL20C.glShaderSource(shader, source);
        GL20C.glCompileShader(shader);
        if (GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) == GL11C.GL_FALSE) {
            var log = GL20C.glGetShaderInfoLog(shader);
            GL20C.glDeleteShader(shader);
            throw new IllegalStateException("shader compile failed: " + log);
        }
        return shader;
    }

    // ─── Uniforms ────────────────────────────────────────────────────────

    private void setSampler(int prog, String name, int unit) {
        var loc = GL20C.glGetUniformLocation(prog, name);
        if (loc >= 0) GL20C.glUniform1i(loc, unit);
    }

    private void setUniform1i(int prog, String name, int value) {
        var loc = GL20C.glGetUniformLocation(prog, name);
        if (loc >= 0) GL20C.glUniform1i(loc, value);
    }

    private void setUniform1f(int prog, String name, float value) {
        var loc = GL20C.glGetUniformLocation(prog, name);
        if (loc >= 0) GL20C.glUniform1f(loc, value);
    }

    private void setUniform3f(int prog, String name, float x, float y, float z) {
        var loc = GL20C.glGetUniformLocation(prog, name);
        if (loc >= 0) GL20C.glUniform3f(loc, x, y, z);
    }

    private void setUniform2i(int prog, String name, int x, int y) {
        var loc = GL20C.glGetUniformLocation(prog, name);
        if (loc >= 0) GL20C.glUniform2i(loc, x, y);
    }

    private void setUniformMatrix4(int prog, String name, float[] mat) {
        var loc = GL20C.glGetUniformLocation(prog, name);
        if (loc < 0) return;
        var buf = BufferUtils.createFloatBuffer(16);
        buf.put(mat).flip();
        GL20C.glUniformMatrix4fv(loc, false, buf);
    }

    private void setAllUniforms(int prog) {
        // Samplers
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
        setSampler(prog, "depthtex0", U_DEPTHTEX0);
        setSampler(prog, "depthtex1", U_DEPTHTEX1);
        setSampler(prog, "noisetex", U_NOISETEX);
        setSampler(prog, "shadowtex0", U_SHADOWTEX0);
        setSampler(prog, "shadow", U_SHADOWTEX0);
        setSampler(prog, "shadowtex1", U_SHADOWTEX1);
        setSampler(prog, "shadowcolor0", U_SHADOWCOLOR0);
        setSampler(prog, "shadowcolor1", U_SHADOWCOLOR1);
        setSampler(prog, "lighttex0", U_LIGHTTEX0);
        setSampler(prog, "lighttex1", U_LIGHTTEX1);

        // Dimensions
        setUniform1i(prog, "viewWidth", W);
        setUniform1i(prog, "viewHeight", H);
        setUniform1f(prog, "aspectRatio", (float) W / H);
        setUniform1f(prog, "frameTimeCounter", 0.0f);
        setUniform1i(prog, "frameCounter", 0);

        // Camera
        setUniform3f(prog, "cameraPosition", 0, 64, 0);
        setUniform3f(prog, "previousCameraPosition", 0, 64, 0);

        // Projection
        float near = 0.05f;
        float far = 1000f;
        setUniform1f(prog, "near", near);
        setUniform1f(prog, "far", far);

        // Environment
        setUniform1i(prog, "isEyeInWater", 0);
        setUniform1f(prog, "rainStrength", 0f);
        setUniform1f(prog, "wetness", 0f);
        setUniform1f(prog, "sunAngle", 0.25f);
        setUniform1i(prog, "worldTime", 6000);

        float ang = 0.25f * 2.0f * (float) Math.PI;
        setUniform3f(prog, "sunPosition", (float) Math.cos(ang) * 100f, (float) Math.sin(ang) * 100f, 0f);
        setUniform3f(prog, "moonPosition", (float) Math.cos(ang + (float) Math.PI) * 100f, (float) Math.sin(ang + (float) Math.PI) * 100f, 0f);
        setUniform3f(prog, "upVec", 0f, 1f, 0f);
        setUniform3f(prog, "shadowLightPosition", (float) Math.cos(ang) * 100f, (float) Math.sin(ang) * 100f, 0f);

        setUniform3f(prog, "skyColor", 0.5f, 0.7f, 1.0f);
        setUniform3f(prog, "fogColor", 0.5f, 0.7f, 1.0f);

        setUniform1f(prog, "nightVision", 0f);
        setUniform1f(prog, "blindness", 0f);
        setUniform1f(prog, "darknessFactor", 0f);
        setUniform1f(prog, "darknessLightFactor", 0f);
        setUniform2i(prog, "eyeBrightnessSmooth", 240, 240);

        // Matrices (identity modelview, simple perspective projection)
        var identity = new float[]{
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1
        };
        setUniformMatrix4(prog, "gbufferModelView", identity);
        setUniformMatrix4(prog, "gbufferModelViewInverse", identity);
        setUniformMatrix4(prog, "gbufferPreviousModelView", identity);
        setUniformMatrix4(prog, "gbufferPreviousProjection", identity);
        setUniformMatrix4(prog, "shadowModelView", identity);
        setUniformMatrix4(prog, "shadowProjection", identity);

        // Simple perspective projection matrix
        float fov = 70f * (float) Math.PI / 180f;
        float aspect = (float) W / H;
        float f = (float) (1.0 / Math.tan(fov / 2));
        float range = near - far;
        float[] projMat = {
            f / aspect, 0, 0, 0,
            0, f, 0, 0,
            0, 0, (far + near) / range, -1,
            0, 0, 2 * far * near / range, 0
        };
        setUniformMatrix4(prog, "gbufferProjection", projMat);

        // Inverse projection
        var mat = new org.joml.Matrix4f().set(projMat);
        var invProj = mat.invert();
        float[] invArr = new float[16];
        invProj.get(invArr);
        setUniformMatrix4(prog, "gbufferProjectionInverse", invArr);

        // Misc
        setUniform1i(prog, "moonPhase", 0);
        setUniform1i(prog, "bedrockLevel", 0);
        setUniform1f(prog, "shadowMapResolution", 2048f);
        setUniform1f(prog, "shadowDistance", 256f);
        setUniform1f(prog, "sunPathRotation", -40f);
        setUniform1f(prog, "blindFactor", 0f);
        setUniform1f(prog, "timeAngle", 0.25f);
        setUniform1f(prog, "timeBrightness", 0.5f);
        setUniform1f(prog, "shadowFade", 1f);
    }

    // ─── Pipeline Execution ──────────────────────────────────────────────

    private void runPipeline() {
        if (passes == null || passes.isEmpty()) {
            System.err.println("No passes compiled, skipping pipeline");
            return;
        }

        System.out.println("\n=== Running Pipeline (" + passes.size() + " passes) ===");

        GL11C.glDisable(GL11C.GL_DEPTH_TEST);
        GL11C.glDisable(GL11C.GL_BLEND);
        GL11C.glViewport(0, 0, W, H);
        GL30C.glBindVertexArray(vao);

        boolean readingAlt = false;

        for (int i = 0; i < passes.size(); i++) {
            var pass = passes.get(i);

            // Determine read textures
            int[] readTex = new int[NUM_COLORTEX];
            for (int t = 0; t < NUM_COLORTEX; t++) {
                readTex[t] = readingAlt ? altTextures[t] : mainTextures[t];
            }

            // Determine write FBO
            int writeFbo;
            if (pass.isFinal()) {
                // Final pass writes to the default framebuffer (window)
                writeFbo = 0;
                GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, 0);
                GL20C.glDrawBuffer(GL30C.GL_BACK);
            } else {
                // Create FBO with all color attachments from write target
                writeFbo = createPassFbo(readingAlt ? mainTextures : altTextures);
                GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, writeFbo);
                setupDrawBuffers(pass.drawBuffers());
                readingAlt = !readingAlt;
            }

            // Clear the write targets to (0,0,0,0)
            GL11C.glClearColor(0, 0, 0, 0);
            // Only clear the buffers we're going to write to
            // Actually, let's not clear — copyUnwrittenTargets handles preservation

            // Copy unwritten targets from read FBO to write FBO
            if (!pass.isFinal()) {
                int readAllFbo = createReadAllFbo(readingAlt ? altTextures : mainTextures);
                copyUnwrittenTargets(readAllFbo, writeFbo, pass.drawBuffers());
            }

            // Use program
            GL20C.glUseProgram(pass.program());

            // Bind textures
            bindAllTextures(readTex);

            // Set uniforms
            setAllUniforms(pass.program());

            // Draw
            System.out.printf("  pass[%d] %s: drawBuffers=%s fbo=%d readingAlt=%s%n",
                i, pass.name(), Integer.toBinaryString(pass.drawBuffers()), writeFbo, !readingAlt);

            GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, 3);

            // Read back center pixel
            int[] centerPx = readCenterPixel(pass, writeFbo);
            System.out.printf("    -> center = (%d, %d, %d, %d)%n",
                centerPx[0], centerPx[1], centerPx[2], centerPx[3]);

            // Also read all 8 color attachments after this pass
            if (!pass.isFinal()) {
                for (int t = 0; t < NUM_COLORTEX; t++) {
                    int[] px = readAttachmentPixel(writeFbo, t);
                    if (px[0] != 0 || px[1] != 0 || px[2] != 0) {
                        System.out.printf("    colortex%d = (%d,%d,%d)%n", t, px[0], px[1], px[2]);
                    }
                }
            }
        }

        GL20C.glUseProgram(0);
        System.out.println("=== Pipeline Complete ===\n");
    }

    private void bindAllTextures(int[] readTex) {
        for (int i = 0; i < NUM_COLORTEX; i++) {
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + i);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, readTex[i] != 0 ? readTex[i] : createDummyColor());
        }
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + U_DEPTHTEX0);
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, depthTexture);
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + U_DEPTHTEX1);
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, depthTexture);
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + U_NOISETEX);
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, noiseTexture);
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + U_SHADOWTEX0);
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, shadowDepth0);
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + U_SHADOWTEX1);
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, shadowDepth1);
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + U_SHADOWCOLOR0);
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, shadowColor0);
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + U_SHADOWCOLOR1);
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, shadowColor1);
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + U_LIGHTTEX0);
        GL11C.glBindTexture(GL12C.GL_TEXTURE_3D, light3D0);
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + U_LIGHTTEX1);
        GL11C.glBindTexture(GL12C.GL_TEXTURE_3D, light3D1);
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0);
    }

    private void setupDrawBuffers(int mask) {
        int[] bufs = new int[NUM_COLORTEX];
        int slot = 0;
        for (int i = 0; i < NUM_COLORTEX; i++) {
            if ((mask & (1 << i)) != 0) {
                bufs[slot++] = GL30C.GL_COLOR_ATTACHMENT0 + i;
            }
        }
        while (slot < NUM_COLORTEX) {
            bufs[slot++] = GL11C.GL_NONE;
        }
        GL20C.glDrawBuffers(bufs);
    }

    private int createPassFbo(int[] textures) {
        var fbo = GL30C.glGenFramebuffers();
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fbo);
        for (int i = 0; i < NUM_COLORTEX; i++) {
            GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0 + i,
                GL11C.GL_TEXTURE_2D, textures[i], 0);
        }
        GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_DEPTH_ATTACHMENT,
            GL11C.GL_TEXTURE_2D, depthTexture, 0);
        int status = GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER);
        if (status != GL30C.GL_FRAMEBUFFER_COMPLETE) {
            System.err.printf("WARNING: passFbo incomplete: 0x%X%n", status);
        }
        return fbo;
    }

    private int createReadAllFbo(int[] textures) {
        var fbo = GL30C.glGenFramebuffers();
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fbo);
        for (int i = 0; i < NUM_COLORTEX; i++) {
            GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0 + i,
                GL11C.GL_TEXTURE_2D, textures[i], 0);
        }
        return fbo;
    }

    private void copyUnwrittenTargets(int readFbo, int writeFbo, int drawMask) {
        GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, readFbo);
        GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, writeFbo);
        for (int i = 0; i < NUM_COLORTEX; i++) {
            if ((drawMask & (1 << i)) != 0) continue;
            GL11C.glReadBuffer(GL30C.GL_COLOR_ATTACHMENT0 + i);
            GL20C.glDrawBuffer(GL30C.GL_COLOR_ATTACHMENT0 + i);
            GL30C.glBlitFramebuffer(0, 0, W, H, 0, 0, W, H, GL30C.GL_COLOR_BUFFER_BIT, GL11C.GL_NEAREST);
        }
    }

    private int[] readCenterPixel(Pass pass, int fbo) {
        var px = BufferUtils.createByteBuffer(4);
        int targetAttach = pass.isFinal() ? GL11C.GL_BACK : GL30C.GL_COLOR_ATTACHMENT0;
        if (!pass.isFinal()) {
            int lowestBit = Integer.numberOfTrailingZeros(pass.drawBuffers());
            targetAttach = GL30C.GL_COLOR_ATTACHMENT0 + lowestBit;
        }

        int prevRead = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        if (pass.isFinal()) {
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, 0);
            GL11C.glReadBuffer(GL11C.GL_BACK);
        } else {
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, fbo);
            GL11C.glReadBuffer(targetAttach);
        }
        GL11C.glReadPixels(W / 2, H / 2, 1, 1, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, px);
        GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, prevRead);
        return new int[]{px.get(0) & 0xFF, px.get(1) & 0xFF, px.get(2) & 0xFF, px.get(3) & 0xFF};
    }

    private int[] readAttachmentPixel(int fbo, int attachment) {
        var px = BufferUtils.createByteBuffer(4);
        int prevRead = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, fbo);
        GL11C.glReadBuffer(GL30C.GL_COLOR_ATTACHMENT0 + attachment);
        GL11C.glReadPixels(W / 2, H / 2, 1, 1, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, px);
        GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, prevRead);
        return new int[]{px.get(0) & 0xFF, px.get(1) & 0xFF, px.get(2) & 0xFF, px.get(3) & 0xFF};
    }

    // ─── Display Loop ────────────────────────────────────────────────────

    private void presentAndLoop() {
        System.out.println("Pipeline complete. Displaying result. Press ESC to exit.");

        // Copy final result to window framebuffer
        // The final pass wrote to GL_BACK, so it should be visible after swap

        while (!GLFW.glfwWindowShouldClose(window)) {
            GLFW.glfwPollEvents();

            // Check for ESC
            if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS) {
                GLFW.glfwSetWindowShouldClose(window, true);
            }

            GLFW.glfwSwapBuffers(window);
        }

        GLFW.glfwTerminate();
    }

    // ─── Entry ───────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        var preprocessOnly = false;
        var argList = new ArrayList<String>();
        for (var arg : args) {
            if (arg.equals("--preprocess-only")) preprocessOnly = true;
            else argList.add(arg);
        }

        var shaderpackPath = argList.isEmpty()
            ? Path.of("../shaderpacks/Complementary").toAbsolutePath().normalize()
            : Path.of(argList.get(0)).toAbsolutePath().normalize();

        new PipelineStandaloneTest(shaderpackPath, preprocessOnly).run();
    }
}
