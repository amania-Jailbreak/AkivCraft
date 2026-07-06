package dev.akivcraft.loader.shader;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL20C;

// Built-in gbuffer prepare shader: reconstructs view-space normals from depth,
// writes linear depth, and fills auxiliary colortex targets so composite/final
// passes have meaningful data to work with even without gbuffers shader replacement.
final class GbufferPrepareShader {
    private static final String VERT = """
        #version 150
        void main() {
            vec2 pos = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
            gl_Position = vec4(pos * 2.0 - 1.0, 0.0, 1.0);
        }
        """;

    private static final String FRAG = """
        #version 330

        uniform sampler2D colortex0;
        uniform sampler2D depthtex0;
        uniform mat4 gbufferProjectionInverse;
        uniform mat4 gbufferModelViewInverse;
        uniform float viewWidth;
        uniform float viewHeight;

        layout(location = 0) out vec4 outLinearDepth;
        layout(location = 1) out vec4 outNormal;
        layout(location = 2) out vec4 outAux;

        vec3 screenToView(vec2 uv, float depth) {
            vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
            vec4 view = gbufferProjectionInverse * clip;
            return view.xyz / view.w;
        }

        vec3 getViewNormal(vec2 uv, float depth) {
            vec2 texel = 1.0 / vec2(viewWidth, viewHeight);
            float d0 = texture(depthtex0, uv).r;
            float dx = texture(depthtex0, uv + vec2(texel.x, 0.0)).r;
            float dy = texture(depthtex0, uv + vec2(0.0, texel.y)).r;

            vec3 p0 = screenToView(uv, d0);
            vec3 px = screenToView(uv + vec2(texel.x, 0.0), dx);
            vec3 py = screenToView(uv + vec2(0.0, texel.y), dy);

            vec3 dxv = px - p0;
            vec3 dyv = py - p0;
            vec3 normal = normalize(cross(dxv, dyv));
            // Ensure normals face the camera
            if (normal.z > 0.0) normal = -normal;
            return normal * 0.5 + 0.5;
        }

        void main() {
            vec2 uv = gl_FragCoord.xy / vec2(viewWidth, viewHeight);
            float depth = texture(depthtex0, uv).r;
            vec4 albedo = texture(colortex0, uv);

            float linearDepth = 1.0;
            if (depth < 1.0) {
                vec3 viewPos = screenToView(uv, depth);
                linearDepth = clamp(-viewPos.z / 1000.0, 0.0, 1.0);
            }
            outLinearDepth = vec4(vec3(linearDepth), 1.0);

            if (depth < 1.0) {
                vec3 normal = getViewNormal(uv, depth);
                outNormal = vec4(normal, 1.0);
            } else {
                outNormal = vec4(0.5, 0.5, 1.0, 1.0);
            }

            outAux = vec4(0.0, 0.0, 0.0, 1.0);
        }
        """;

    private int program;

    int getProgram() {
        if (program != 0) return program;
        var vs = compile(GL20C.GL_VERTEX_SHADER, VERT);
        var fs = compile(GL20C.GL_FRAGMENT_SHADER, FRAG);
        program = GL20C.glCreateProgram();
        GL20C.glAttachShader(program, vs);
        GL20C.glAttachShader(program, fs);
        GL20C.glLinkProgram(program);
        GL20C.glDeleteShader(vs);
        GL20C.glDeleteShader(fs);
        if (GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) == GL11C.GL_FALSE) {
            var log = GL20C.glGetProgramInfoLog(program);
            GL20C.glDeleteProgram(program);
            program = 0;
            throw new IllegalStateException("gbuffer_prepare link failed: " + log);
        }
        System.out.println("AkivCraft compiled built-in gbuffer_prepare shader");
        return program;
    }

    void destroy() {
        if (program != 0) {
            GL20C.glDeleteProgram(program);
            program = 0;
        }
    }

    private static int compile(int type, String source) {
        var shader = GL20C.glCreateShader(type);
        GL20C.glShaderSource(shader, source);
        GL20C.glCompileShader(shader);
        if (GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) == GL11C.GL_FALSE) {
            var log = GL20C.glGetShaderInfoLog(shader);
            GL20C.glDeleteShader(shader);
            throw new IllegalStateException("gbuffer_prepare compile failed: " + log);
        }
        return shader;
    }
}
