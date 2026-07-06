package dev.akivcraft.loader.shader;

import com.mojang.blaze3d.opengl.GlStateManager;
import dev.akivcraft.loader.iris.gl.framebuffer.GlFramebuffer;
import dev.akivcraft.loader.iris.gl.texture.InternalTextureFormat;
import dev.akivcraft.loader.iris.mixinterface.CustomPass;
import dev.akivcraft.loader.iris.targets.RenderTargets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AkivPipeline {
    private static final int NUM_COLORTEX = 8;

    static final int U_COLORTEX0 = 0;
    static final int U_DEPTHTEX0 = 8;
    static final int U_DEPTHTEX1 = 9;
    static final int U_NOISETEX = 10;
    static final int U_SHADOWTEX0 = 11;
    static final int U_SHADOWTEX1 = 12;
    static final int U_SHADOWCOLOR0 = 13;
    static final int U_SHADOWCOLOR1 = 14;
    static final int U_LIGHTTEX0 = 15;
    static final int U_LIGHTTEX1 = 16;

    private static final InternalTextureFormat[] COLORTEX_FORMATS = {
        InternalTextureFormat.RGBA16F, InternalTextureFormat.RGBA8, InternalTextureFormat.RGBA16F, InternalTextureFormat.RGBA8,
        InternalTextureFormat.R8, InternalTextureFormat.RGBA8, InternalTextureFormat.RGBA16F, InternalTextureFormat.RGBA16F
    };

    private static AkivPipeline instance;

    private final CustomPass gbufferPass = new GbufferCustomPass();

    private RenderTargets renderTargets;
    private GlFramebuffer gbufferFbo;
    private GlFramebuffer sceneFbo;
    private GlFramebuffer gbufferPrepareFbo;
    private GlFramebuffer readAllMainFbo;
    private GlFramebuffer readAllAltFbo;
    private GbufferPrepareShader gbufferPrepareShader;
    private int originalSceneTex;
    private int originalSceneFbo;

    private int mcDepthGlId;
    private int mcColorGlId;
    private int noiseTex;
    private int shadowDepth0, shadowDepth1;
    private int shadowColor0, shadowColor1;
    private int light3D0, light3D1;
    private int depthTex;
    private int vao;

    private int pipelineW = -1, pipelineH = -1;

    private List<AkivShaderRenderer.CompiledPass> passes;
    private GlFramebuffer[] passFbosMain;
    private GlFramebuffer[] passFbosAlt;
    private String compiledDimension;
    private AkivShaderConfig.AkivShaderPack loadedPack;
    private long loadedTimestamp;

    private boolean inLevelRender;
    private int trySetupCount;
    private int trySetupRedirectCount;
    private int frameCounter;
    private long firstRenderNanos;
    private float[] prevCamPos = {0, 0, 0};
    private boolean prevCamInit;

    private long lastErrorNanos;
    private boolean loggedActive;

    private AkivPipeline() {}

    public static synchronized AkivPipeline getInstance() {
        if (instance == null) instance = new AkivPipeline();
        return instance;
    }

    public static void beginLevelRender(GameRenderer gameRenderer) {
        getInstance().beginLevelRenderInternal(gameRenderer);
    }

    public static void endLevelRender(GameRenderer gameRenderer) {
        getInstance().endLevelRenderInternal(gameRenderer);
    }

    public static void onTrySetupReturn() {
        getInstance().onTrySetupReturnInternal();
    }

    CustomPass getGbufferPass() {
        return gbufferPass;
    }

    void onTrySetupReturnInternal() {
        trySetupCount++;
        if (!inLevelRender || gbufferFbo == null) return;
        trySetupRedirectCount++;
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, gbufferFbo.getId());
    }

    void beginLevelRenderInternal(GameRenderer gameRenderer) {
        var mc = gameRenderer.getMinecraft();
        if (mc == null || mc.level == null) return;
        var pack = AkivShaderConfig.selectedPack();
        if (pack == null) return;

        inLevelRender = true;
        AkivShaderOverrides.setOverrideEnabled(true);

        try {
            var window = mc.getWindow();
            int w = window.getWidth();
            int h = window.getHeight();
            if (w <= 0 || h <= 0) return;

            resolveMcTextures(mc);
            ensureResources(w, h);
            ensureGbufferFbo();

            int prevFbo = GL11C.glGetInteger(GL30C.GL_FRAMEBUFFER_BINDING);
            GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, gbufferFbo.getId());
            GL20C.glDrawBuffer(GL30C.GL_COLOR_ATTACHMENT0);
            GL11C.glClearColor(0, 0, 0, 0);
            GL11C.glClear(GL11C.GL_COLOR_BUFFER_BIT);
            GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, prevFbo);
        } catch (Throwable ignored) {}
    }

    void endLevelRenderInternal(GameRenderer gameRenderer) {
        inLevelRender = false;
        AkivShaderOverrides.setOverrideEnabled(false);

        var mc = gameRenderer.getMinecraft();
        if (mc == null || mc.level == null) return;
        var pack = AkivShaderConfig.selectedPack();
        if (pack == null) return;

        var dimension = AkivShaderRenderer.currentDimension(mc);
        try {
            AkivShaderRenderer.reloadIfNeeded(pack, dimension, this);
            if (passes == null || passes.isEmpty()) return;

            var window = mc.getWindow();
            int w = window.getWidth();
            int h = window.getHeight();
            if (w <= 0 || h <= 0) return;

            ensureResources(w, h);

            boolean diag = frameCounter < 3 || frameCounter % 120 == 0;
            if (diag) {
                System.err.printf("AkivCraft diag [frame=%d]: [w=%d h=%d gbufferFbo=%d mcColor=%d mcDepth=%d trySetup=%d redirects=%d]%n",
                    frameCounter, w, h, gbufferFbo != null ? gbufferFbo.getId() : -1, mcColorGlId, mcDepthGlId, trySetupCount, trySetupRedirectCount);
            }

            runPipeline(mc, w, h, diag);
            frameCounter++;
        } catch (Throwable error) {
            var now = System.nanoTime();
            if (now - lastErrorNanos > 5_000_000_000L) {
                System.err.printf("AkivCraft pipeline failed: %s%n", error.getMessage());
                error.printStackTrace();
                lastErrorNanos = now;
            }
            try {
                var window = mc.getWindow();
                int ew = window.getWidth();
                int eh = window.getHeight();
                resolveMcTextures(mc);
                if (mcColorGlId != 0 && originalSceneFbo != 0) {
                    int mainFbo = GL11C.glGetInteger(GL30C.GL_FRAMEBUFFER_BINDING);
                    fallbackBlitOriginalScene(ew, eh);
                    GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, mainFbo);
                }
            } catch (Throwable ignored) {}
        }
    }

    private void resolveMcTextures(Minecraft mc) {
        mcDepthGlId = 0;
        mcColorGlId = 0;
        try {
            var rt = mc.getMainRenderTarget();
            if (rt == null) return;
            mcColorGlId = AkivShaderRenderer.getGpuTextureGlId(rt.getColorTexture());
            mcDepthGlId = AkivShaderRenderer.getGpuTextureGlId(rt.getDepthTexture());
        } catch (Throwable ignored) {}
    }

    private void ensureResources(int w, int h) {
        if (renderTargets == null || pipelineW != w || pipelineH != h) {
            createResources(w, h);
            pipelineW = w;
            pipelineH = h;
        }
        if (renderTargets != null) {
            renderTargets.resizeIfNeeded(w, h, mcDepthGlId != 0 ? mcDepthGlId : depthTex);
        }
    }

    private void createResources(int w, int h) {
        if (renderTargets != null) {
            renderTargets.destroy();
            renderTargets = null;
        }
        gbufferFbo = null;
        sceneFbo = null;
        gbufferPrepareFbo = null;
        readAllMainFbo = null;
        readAllAltFbo = null;
        if (gbufferPrepareShader != null) {
            gbufferPrepareShader.destroy();
            gbufferPrepareShader = null;
        }
        destroyPassFbos();

        int depthId = mcDepthGlId != 0 ? mcDepthGlId : createDepthTexture(w, h);
        depthTex = depthId;

        renderTargets = new RenderTargets(w, h, depthId, COLORTEX_FORMATS);

        if (noiseTex == 0) {
            noiseTex = generateNoiseTexture();
            shadowDepth0 = createShadowDepthTexture();
            shadowDepth1 = createShadowDepthTexture();
            shadowColor0 = createDummyColorTexture();
            shadowColor1 = createDummyColorTexture();
            light3D0 = createDummy3DTexture();
            light3D1 = createDummy3DTexture();
        }

        if (depthId == mcDepthGlId) {
            depthTex = createDepthTexture(w, h);
        }

        if (passes != null && !passes.isEmpty()) {
            createPassFbos();
        }
    }

    private void ensureGbufferFbo() {
        if (gbufferFbo != null) return;
        int depthId = mcDepthGlId != 0 ? mcDepthGlId : depthTex;
        renderTargets.setDepthTexture(depthId);

        gbufferFbo = renderTargets.createGbufferFramebuffer(Set.of(), new int[]{0});

        int status = gbufferFbo.getStatus();
        if (status != GL30C.GL_FRAMEBUFFER_COMPLETE) {
            System.err.printf("AkivCraft gbufferFbo incomplete: 0x%X%n", status);
        }
    }

    private void runPipeline(Minecraft mc, int w, int h, boolean diag) {
        int mainFbo = GL11C.glGetInteger(GL30C.GL_FRAMEBUFFER_BINDING);
        int prevProgram = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        int prevActiveTex = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        int prevVao = GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING);
        boolean depthEnabled = GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST);
        boolean blendEnabled = GL11C.glIsEnabled(GL11C.GL_BLEND);

        int totalUnits = 17;
        int[] prevTex2D = new int[totalUnits];
        int[] prevTex3D = new int[2];
        for (int u = 0; u < totalUnits; u++) {
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + u);
            prevTex2D[u] = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        }
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + 15);
        prevTex3D[0] = GL11C.glGetInteger(0x806A);
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + 16);
        prevTex3D[1] = GL11C.glGetInteger(0x806A);

        GL11C.glDisable(GL11C.GL_DEPTH_TEST);
        GL11C.glDisable(GL11C.GL_BLEND);
        GL11C.glViewport(0, 0, w, h);

        if (vao == 0) vao = GL30C.glGenVertexArrays();
        GL30C.glBindVertexArray(vao);

        resolveMcTextures(mc);
        ensureSceneFbo();
        captureOriginalScene(w, h);
        runGbufferPrepare(mc, w, h);

        if (diag) {
            logTextureDiagnostics(w, h);
        }

        boolean readingAlt = false;
        for (int i = 0; i < passes.size(); i++) {
            var pass = passes.get(i);

            int[] readTex = new int[NUM_COLORTEX];
            for (int t = 0; t < NUM_COLORTEX; t++) {
                var rt = renderTargets.get(t);
                if (rt != null) {
                    readTex[t] = readingAlt ? rt.getAltTexture() : rt.getMainTexture();
                }
            }

            if (pass.isFinal()) {
                GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, sceneFbo.getId());
                GL20C.glDrawBuffer(GL30C.GL_COLOR_ATTACHMENT0);
            } else {
                GlFramebuffer writeFbo = readingAlt ? passFbosMain[i] : passFbosAlt[i];
                GlFramebuffer readFbo = readingAlt ? readAllAltFbo : readAllMainFbo;

                copyUnwrittenTargets(readFbo, writeFbo, pass.drawBuffers(), w, h);

                GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, writeFbo.getId());
                setupDrawBuffers(pass.drawBuffers());

                readingAlt = !readingAlt;
            }

            GL20C.glUseProgram(pass.program());
            int depthId = pass.isFinal() ? depthTex : (mcDepthGlId != 0 ? mcDepthGlId : depthTex);
            bindAllTextures(readTex, depthId);

            if (diag && i == 0) {
                GL13C.glActiveTexture(GL13C.GL_TEXTURE0);
                int boundUnit0 = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
                int expected = readTex[0] != 0 ? readTex[0] : dummyColorCache;
                int fboStatus = GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER);
                int linkOk = GL20C.glGetProgrami(pass.program(), GL20C.GL_LINK_STATUS);
                int validOk = GL20C.glGetProgrami(pass.program(), GL20C.GL_VALIDATE_STATUS);
                System.err.printf("  pre-draw pass[0]: unit0_bound=%d expected=%d match=%b fboStatus=0x%X linkOk=%d validOk=%d%n",
                    boundUnit0, expected, boundUnit0 == expected, fboStatus, linkOk, validOk);
            }

            AkivShaderRenderer.setAllUniforms(pass.program(), mc, w, h, frameCounter, firstRenderNanos, prevCamPos, prevCamInit);

            if (diag && i == 0) {
                int samplerLoc = GL20C.glGetUniformLocation(pass.program(), "colortex0");
                int[] samplerVal = new int[1];
                if (samplerLoc >= 0) GL20C.glGetUniformiv(pass.program(), samplerLoc, samplerVal);

                GL13C.glActiveTexture(GL13C.GL_TEXTURE0);
                int boundAfterUniform = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);

                int attachedReadTex = 0;
                if (readAllMainFbo != null) {
                    int prevRead = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
                    GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, readAllMainFbo.getId());
                    attachedReadTex = GL30C.glGetFramebufferAttachmentParameteri(
                        GL30C.GL_READ_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0,
                        GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
                    GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, prevRead);
                }

                int writeFboId = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
                int attachedWriteTex = GL30C.glGetFramebufferAttachmentParameteri(
                    GL30C.GL_DRAW_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0,
                    GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);

                System.err.printf("  post-uniform pass[0]: samplerLoc=%d samplerVal=%d unit0_bound=%d readFboTex=%d writeFboTex=%d writeFbo=%d%n",
                    samplerLoc, samplerVal[0], boundAfterUniform, attachedReadTex, attachedWriteTex, writeFboId);

                for (int att = 0; att < NUM_COLORTEX; att++) {
                    int wTex = GL30C.glGetFramebufferAttachmentParameteri(
                        GL30C.GL_DRAW_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0 + att,
                        GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
                    boolean feedback = false;
                    for (int u = 0; u < NUM_COLORTEX; u++) {
                        if (readTex[u] != 0 && readTex[u] == wTex) { feedback = true; break; }
                    }
                    if (feedback || wTex == attachedReadTex) {
                        System.err.printf("  FEEDBACK: writeFbo ATT%d tex=%d matches read texture!%n", att, wTex);
                    }
                }
            }

            updatePrevCamPos(mc);

            while (GL11C.glGetError() != GL11C.GL_NO_ERROR) {}
            GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, 3);
            int err = GL11C.glGetError();
            if (diag && (i == 0 || pass.isFinal())) {
                System.err.printf("  post-draw pass[%d]: glError=0x%X%n", i, err);
            }

            if (diag && i == 0 && readAllAltFbo != null) {
                int prevRead = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
                GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, readAllAltFbo.getId());
                for (int t = 0; t < NUM_COLORTEX; t++) {
                    GL11C.glReadBuffer(GL30C.GL_COLOR_ATTACHMENT0 + t);
                    var dpx2 = BufferUtils.createByteBuffer(4);
                    GL11C.glReadPixels(w / 2, h / 2, 1, 1, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, dpx2);
                    System.err.printf("  alt colortex%d (read by next pass): center=(%d,%d,%d)%n",
                        t, dpx2.get(0) & 0xFF, dpx2.get(1) & 0xFF, dpx2.get(2) & 0xFF);
                }
                GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, prevRead);
            }

            if (diag && (i < 3 || pass.isFinal())) {
                int prevReadFbo = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
                int boundFbo = GL11C.glGetInteger(GL30C.GL_FRAMEBUFFER_BINDING);
                int checkTarget = pass.isFinal() ? -1 : Integer.numberOfTrailingZeros(pass.drawBuffers());
                if (boundFbo != 0) {
                    GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, boundFbo);
                    int attach = pass.isFinal() ? GL30C.GL_COLOR_ATTACHMENT0 : GL30C.GL_COLOR_ATTACHMENT0 + Math.max(checkTarget, 0);
                    GL11C.glReadBuffer(attach);
                    var dpx = BufferUtils.createByteBuffer(4);
                    GL11C.glReadPixels(w / 2, h / 2, 1, 1, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, dpx);
                    System.err.printf("  pass[%d]=%s after: center=(%d,%d,%d) fbo=%d attach=%d%n",
                        i, pass.name(), dpx.get(0) & 0xFF, dpx.get(1) & 0xFF, dpx.get(2) & 0xFF, boundFbo, Math.max(checkTarget, 0));
                    GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, prevReadFbo);
                }
            }
        }

        conditionalFallbackBlit(w, h, diag);

        for (int u = totalUnits - 1; u >= 0; u--) {
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + u);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, prevTex2D[u]);
        }
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + 15);
        GL11C.glBindTexture(GL12C.GL_TEXTURE_3D, prevTex3D[0]);
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + 16);
        GL11C.glBindTexture(GL12C.GL_TEXTURE_3D, prevTex3D[1]);

        GL30C.glBindVertexArray(prevVao);
        GL13C.glActiveTexture(prevActiveTex);
        GL20C.glUseProgram(prevProgram);
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, mainFbo);
        if (depthEnabled) GL11C.glEnable(GL11C.GL_DEPTH_TEST); else GL11C.glDisable(GL11C.GL_DEPTH_TEST);
        if (blendEnabled) GL11C.glEnable(GL11C.GL_BLEND); else GL11C.glDisable(GL11C.GL_BLEND);
    }

    private void logTextureDiagnostics(int w, int h) {
        int prevFbo = GL11C.glGetInteger(GL30C.GL_FRAMEBUFFER_BINDING);
        try {
            var px = BufferUtils.createByteBuffer(4);

            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, gbufferFbo != null ? gbufferFbo.getId() : 0);
            GL11C.glReadBuffer(GL30C.GL_COLOR_ATTACHMENT0);
            px.clear();
            GL11C.glReadPixels(w / 2, h / 2, 1, 1, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, px);
            int ct0r = px.get(0) & 0xFF, ct0g = px.get(1) & 0xFF, ct0b = px.get(2) & 0xFF;
            px.clear();
            GL11C.glReadPixels(0, 0, 1, 1, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, px);
            int ct0CornerR = px.get(0) & 0xFF, ct0CornerG = px.get(1) & 0xFF;

            System.err.printf("AkivCraft texdiag: colortex0 center=(%d,%d,%d) corner=(%d,%d)%n",
                ct0r, ct0g, ct0b, ct0CornerR, ct0CornerG);

            if (readAllMainFbo != null) {
                GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, readAllMainFbo.getId());
                for (int t = 0; t < NUM_COLORTEX; t++) {
                    GL11C.glReadBuffer(GL30C.GL_COLOR_ATTACHMENT0 + t);
                    px.clear();
                    GL11C.glReadPixels(w / 2, h / 2, 1, 1, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, px);
                    int r = px.get(0) & 0xFF, g = px.get(1) & 0xFF, b = px.get(2) & 0xFF;
                    boolean validTex = renderTargets.get(t) != null && GL11C.glIsTexture(renderTargets.get(t).getMainTexture());
                    System.err.printf("  colortex%d main: center=(%d,%d,%d) texValid=%b%n", t, r, g, b, validTex);
                }
            }
        } catch (Throwable ignored) {
        } finally {
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, prevFbo);
        }
    }

    private void ensureSceneFbo() {
        if (mcColorGlId == 0) return;
        if (sceneFbo == null) sceneFbo = new GlFramebuffer();
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, sceneFbo.getId());
        GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0,
            GL11C.GL_TEXTURE_2D, mcColorGlId, 0);
    }

    private void captureOriginalScene(int w, int h) {
        if (originalSceneTex == 0) {
            originalSceneTex = GL11C.glGenTextures();
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, originalSceneTex);
            GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RGBA8, w, h, 0, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
        } else if (pipelineW != w || pipelineH != h) {
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, originalSceneTex);
            GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RGBA8, w, h, 0, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        }

        if (originalSceneFbo == 0) originalSceneFbo = GL30C.glGenFramebuffers();
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, originalSceneFbo);
        GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0,
            GL11C.GL_TEXTURE_2D, originalSceneTex, 0);

        int gbufId = gbufferFbo != null ? gbufferFbo.getId() : 0;
        GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, gbufId);
        GL11C.glReadBuffer(GL30C.GL_COLOR_ATTACHMENT0);
        GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, originalSceneFbo);
        GL20C.glDrawBuffer(GL30C.GL_COLOR_ATTACHMENT0);
        GL30C.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL30C.GL_COLOR_BUFFER_BIT, GL11C.GL_NEAREST);
    }

    private void runGbufferPrepare(Minecraft mc, int w, int h) {
        try {
            if (gbufferPrepareShader == null) gbufferPrepareShader = new GbufferPrepareShader();
            int prog = gbufferPrepareShader.getProgram();
            if (prog == 0) return;

            if (gbufferPrepareFbo == null) {
                gbufferPrepareFbo = renderTargets.createColorFramebuffer(new HashSet<>(), new int[]{1, 2, 3}, false);
            }

            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, gbufferPrepareFbo.getId());
            GL11C.glViewport(0, 0, w, h);

            GL20C.glUseProgram(prog);

            GL13C.glActiveTexture(GL13C.GL_TEXTURE0);
            int albedoTex = renderTargets.getOrCreate(0).getMainTexture();
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, albedoTex);
            GL20C.glUniform1i(GL20C.glGetUniformLocation(prog, "colortex0"), 0);

            GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + U_DEPTHTEX0);
            int depthId = mcDepthGlId != 0 ? mcDepthGlId : depthTex;
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, depthId);
            GL20C.glUniform1i(GL20C.glGetUniformLocation(prog, "depthtex0"), U_DEPTHTEX0);

            GL20C.glUniform1f(GL20C.glGetUniformLocation(prog, "viewWidth"), w);
            GL20C.glUniform1f(GL20C.glGetUniformLocation(prog, "viewHeight"), h);

            float[] projMat = null;
            try {
                var gr = mc.gameRenderer;
                var state = gr.getGameRenderState();
                if (state != null && state.levelRenderState != null) {
                    var cam = state.levelRenderState.cameraRenderState;
                    if (cam != null && cam.projectionMatrix != null) {
                        projMat = new float[16];
                        cam.projectionMatrix.get(projMat);
                    }
                }
            } catch (Throwable ignored) {}

            if (projMat != null) {
                var mat = new org.joml.Matrix4f().set(projMat);
                var invProj = mat.invert();
                var buf = org.lwjgl.BufferUtils.createFloatBuffer(16);
                invProj.get(buf);
                buf.flip();
                GL20C.glUniformMatrix4fv(GL20C.glGetUniformLocation(prog, "gbufferProjectionInverse"), false, buf);
            }

            GL11C.glDisable(GL11C.GL_DEPTH_TEST);
            GL11C.glDisable(GL11C.GL_BLEND);

            if (vao == 0) vao = GL30C.glGenVertexArrays();
            GL30C.glBindVertexArray(vao);

            GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, 3);

            GL13C.glActiveTexture(GL13C.GL_TEXTURE0);
        } catch (Throwable error) {
            System.err.printf("AkivCraft gbuffer prepare failed: %s%n", error.getMessage());
        }
    }

    private void conditionalFallbackBlit(int w, int h, boolean diag) {
        if (originalSceneFbo == 0 || sceneFbo == null) {
            fallbackBlitOriginalScene(w, h);
            return;
        }

        int checkFbo = sceneFbo.getId();
        GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, checkFbo);
        GL11C.glReadBuffer(GL30C.GL_COLOR_ATTACHMENT0);
        var px = org.lwjgl.BufferUtils.createByteBuffer(4);
        GL11C.glReadPixels(w / 2, h / 2, 1, 1, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, px);
        int r = px.get(0) & 0xFF;
        int g = px.get(1) & 0xFF;
        int b = px.get(2) & 0xFF;

        if (r == 0 && g == 0 && b == 0) {
            fallbackBlitOriginalScene(w, h);
            if (diag) {
                System.err.println("AkivCraft: pipeline output black, applied fallback blit");
            }
        } else if (diag) {
            System.err.printf("AkivCraft: pipeline output ok (%d,%d,%d)%n", r, g, b);
        }
    }

    private void fallbackBlitOriginalScene(int w, int h) {
        if (originalSceneFbo == 0) return;
        int drawTarget = sceneFbo != null ? sceneFbo.getId() : GL11C.glGetInteger(GL30C.GL_FRAMEBUFFER_BINDING);
        GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, originalSceneFbo);
        GL11C.glReadBuffer(GL30C.GL_COLOR_ATTACHMENT0);
        GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, drawTarget);
        GL20C.glDrawBuffer(GL30C.GL_COLOR_ATTACHMENT0);
        GL30C.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL30C.GL_COLOR_BUFFER_BIT, GL11C.GL_NEAREST);
    }

    private void copyUnwrittenTargets(GlFramebuffer readFbo, GlFramebuffer writeFbo, int drawBuffersMask, int w, int h) {
        if (readFbo == null || writeFbo == null) return;
        GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, readFbo.getId());
        GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, writeFbo.getId());
        for (int i = 0; i < NUM_COLORTEX; i++) {
            if ((drawBuffersMask & (1 << i)) != 0) continue;
            GL11C.glReadBuffer(GL30C.GL_COLOR_ATTACHMENT0 + i);
            GL20C.glDrawBuffer(GL30C.GL_COLOR_ATTACHMENT0 + i);
            GL30C.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL30C.GL_COLOR_BUFFER_BIT, GL11C.GL_NEAREST);
        }
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

    private static int[] maskToBuffers(int mask) {
        var list = new ArrayList<Integer>();
        for (int i = 0; i < NUM_COLORTEX; i++) {
            if ((mask & (1 << i)) != 0) list.add(i);
        }
        return list.stream().mapToInt(Integer::intValue).toArray();
    }

    private void bindAllTextures(int[] readTex, int depthId) {
        for (int i = 0; i < NUM_COLORTEX; i++) {
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + i);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, readTex[i] != 0 ? readTex[i] : createDummyColorTexture());
        }
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + U_DEPTHTEX0);
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, depthId);
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + U_DEPTHTEX1);
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, depthId);

        GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + U_NOISETEX);
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, noiseTex);

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

    private void updatePrevCamPos(Minecraft mc) {
        try {
            var gr = mc.gameRenderer;
            var state = gr.getGameRenderState();
            if (state != null && state.levelRenderState != null) {
                var cam = state.levelRenderState.cameraRenderState;
                if (cam != null && cam.pos != null) {
                    prevCamPos[0] = (float) cam.pos.x;
                    prevCamPos[1] = (float) cam.pos.y;
                    prevCamPos[2] = (float) cam.pos.z;
                    prevCamInit = true;
                }
            }
        } catch (Throwable ignored) {}
    }

    void setPasses(List<AkivShaderRenderer.CompiledPass> passes) {
        if (this.passes != null) {
            destroyPassFbos();
        }
        this.passes = passes;
        if (passes != null && renderTargets != null) {
            createPassFbos();
        }
    }

    private void createPassFbos() {
        int n = passes.size();
        passFbosMain = new GlFramebuffer[n];
        passFbosAlt = new GlFramebuffer[n];
        int[] allBuffers = new int[]{0, 1, 2, 3, 4, 5, 6, 7};
        for (int i = 0; i < n; i++) {
            var pass = passes.get(i);
            if (pass.isFinal()) continue;
            passFbosMain[i] = renderTargets.createColorFramebuffer(new HashSet<>(), allBuffers, false);
            Set<Integer> allAlt = new HashSet<>();
            for (int t = 0; t < NUM_COLORTEX; t++) allAlt.add(t);
            passFbosAlt[i] = renderTargets.createColorFramebuffer(allAlt, allBuffers, false);
        }
        if (readAllMainFbo == null) {
            readAllMainFbo = renderTargets.createColorFramebuffer(new HashSet<>(), allBuffers, false);
        }
        if (readAllAltFbo == null) {
            Set<Integer> allAlt = new HashSet<>();
            for (int t = 0; t < NUM_COLORTEX; t++) allAlt.add(t);
            readAllAltFbo = renderTargets.createColorFramebuffer(allAlt, allBuffers, false);
        }
    }

    private void destroyPassFbos() {
        if (passFbosMain != null) {
            for (var fbo : passFbosMain) if (fbo != null) fbo.destroy();
            passFbosMain = null;
        }
        if (passFbosAlt != null) {
            for (var fbo : passFbosAlt) if (fbo != null) fbo.destroy();
            passFbosAlt = null;
        }
    }

    void setLoadedPack(AkivShaderConfig.AkivShaderPack pack, long timestamp, String dimension) {
        this.loadedPack = pack;
        this.loadedTimestamp = timestamp;
        this.compiledDimension = dimension;
        if (firstRenderNanos == 0) firstRenderNanos = System.nanoTime();
        if (!loggedActive) {
            System.out.printf("AkivCraft pipeline active: %d passes for %s%n", passes != null ? passes.size() : 0, dimension);
            loggedActive = true;
        }
    }

    AkivShaderConfig.AkivShaderPack loadedPack() {
        return loadedPack;
    }

    long loadedTimestamp() {
        return loadedTimestamp;
    }

    String compiledDimension() {
        return compiledDimension;
    }

    List<AkivShaderRenderer.CompiledPass> currentPasses() {
        return passes;
    }

    RenderTargets renderTargets() {
        return renderTargets;
    }

    int frameCounter() {
        return frameCounter;
    }

    long firstRenderNanos() {
        return firstRenderNanos;
    }

    float[] prevCamPos() {
        return prevCamPos;
    }

    boolean prevCamInit() {
        return prevCamInit;
    }

    private int createDepthTexture(int w, int h) {
        var tex = GL11C.glGenTextures();
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, tex);
        GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL14C.GL_DEPTH_COMPONENT24, w, h, 0, GL11C.GL_DEPTH_COMPONENT, GL11C.GL_FLOAT, (ByteBuffer) null);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL12C.GL_CLAMP_TO_EDGE);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL12C.GL_CLAMP_TO_EDGE);
        return tex;
    }

    private int generateNoiseTexture() {
        var size = 512;
        var dataSize = size * size * 4;
        var data = BufferUtils.createByteBuffer(dataSize);
        var random = new java.util.Random(0L);
        for (int i = 0; i < dataSize; i++) data.put((byte) random.nextInt(256));
        data.flip();
        var tex = GL11C.glGenTextures();
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, tex);
        GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RGBA8, size, size, 0, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, data);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL11C.GL_REPEAT);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL11C.GL_REPEAT);
        return tex;
    }

    private int createShadowDepthTexture() {
        int size = 2048;
        var tex = GL11C.glGenTextures();
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, tex);
        GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL14C.GL_DEPTH_COMPONENT16, size, size, 0, GL11C.GL_DEPTH_COMPONENT, GL11C.GL_FLOAT, (ByteBuffer) null);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL12C.GL_CLAMP_TO_EDGE);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL12C.GL_CLAMP_TO_EDGE);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL14C.GL_TEXTURE_COMPARE_MODE, 0x884E);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL14C.GL_TEXTURE_COMPARE_FUNC, GL11C.GL_LEQUAL);

        int shadowFbo = GL30C.glGenFramebuffers();
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, shadowFbo);
        GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_DEPTH_ATTACHMENT, GL11C.GL_TEXTURE_2D, tex, 0);
        GL20C.glDrawBuffer(GL11C.GL_NONE);
        GL11C.glClearDepth(1.0);
        GL11C.glClear(GL11C.GL_DEPTH_BUFFER_BIT);
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, 0);
        GL30C.glDeleteFramebuffers(shadowFbo);

        return tex;
    }

    private int dummyColorCache = 0;

    private int createDummyColorTexture() {
        if (dummyColorCache != 0) return dummyColorCache;
        int size = 2048;
        var tex = GL11C.glGenTextures();
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, tex);
        GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RGBA8, size, size, 0, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL12C.GL_CLAMP_TO_EDGE);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL12C.GL_CLAMP_TO_EDGE);

        int clearFbo = GL30C.glGenFramebuffers();
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, clearFbo);
        GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0, GL11C.GL_TEXTURE_2D, tex, 0);
        GL20C.glDrawBuffer(GL30C.GL_COLOR_ATTACHMENT0);
        GL11C.glClearColor(0, 0, 0, 1);
        GL11C.glClear(GL11C.GL_COLOR_BUFFER_BIT);
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, 0);
        GL30C.glDeleteFramebuffers(clearFbo);

        dummyColorCache = tex;
        return tex;
    }

    private int createDummy3DTexture() {
        var tex = GL11C.glGenTextures();
        GL11C.glBindTexture(GL12C.GL_TEXTURE_3D, tex);
        var data = BufferUtils.createByteBuffer(4);
        data.put((byte) 0).put((byte) 0).put((byte) 0).put((byte) 0xFF).flip();
        GL12C.glTexImage3D(GL12C.GL_TEXTURE_3D, 0, GL11C.GL_RGBA8, 1, 1, 1, 0, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, data);
        GL11C.glTexParameteri(GL12C.GL_TEXTURE_3D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
        GL11C.glTexParameteri(GL12C.GL_TEXTURE_3D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
        return tex;
    }

    private final class GbufferCustomPass implements CustomPass {
        @Override
        public void setupState() {
            if (gbufferFbo != null) {
                GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, gbufferFbo.getId());
            }
        }
    }
}
