package rich.util.render.pipeline;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryUtil;
import rich.util.render.Render3D;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Renders a layered shader effect directly onto the faces of the targeted
 * block's bounding box (in camera-relative world space): a transparent
 * frosted-glass blur of the world behind, the particle shader on top, and an
 * edge outline. Drives the "shader" variant of the BlockOverlay module.
 */
public class BlockOverlayShaderPipeline {

    private static final Identifier VERTEX_SHADER = Identifier.of("rich", "core/block_overlay");
    private static final Identifier FRAGMENT_SHADER = Identifier.of("rich", "core/block_overlay");

    // "Full block": no depth test, so every face is drawn -> the box shows through.
    private static final RenderPipeline PIPELINE_FULL = buildPipeline(
            Identifier.of("rich", "pipeline/block_overlay_full"), DepthTestFunction.NO_DEPTH_TEST);
    // Depth-tested: faces behind the (solid) block fail the depth test, so only
    // the faces actually facing the camera are drawn -> nothing shows through.
    private static final RenderPipeline PIPELINE_OCCLUDE = buildPipeline(
            Identifier.of("rich", "pipeline/block_overlay_occlude"), DepthTestFunction.LEQUAL_DEPTH_TEST);

    private static RenderPipeline buildPipeline(Identifier id, DepthTestFunction depthTest) {
        return RenderPipelines.register(
                RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                        .withLocation(id)
                        .withVertexShader(VERTEX_SHADER)
                        .withFragmentShader(FRAGMENT_SHADER)
                        .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                        .withUniform("BlockData", UniformType.UNIFORM_BUFFER)
                        .withSampler("Sampler0")
                        .withBlend(BlendFunction.TRANSLUCENT)
                        .withDepthTestFunction(depthTest)
                        .withDepthWrite(false)
                        .withCull(false)
                        .build()
        );
    }

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    // std140: mat4 (64) + 6 * vec4 (96) = 160
    private static final int BUFFER_SIZE = 160;

    private static final BlockOverlayShaderPipeline INSTANCE = new BlockOverlayShaderPipeline();

    private final Matrix4f mvp = new Matrix4f();

    private GpuBuffer uniformBuffer;
    private GpuBuffer dummyVertexBuffer;
    private ByteBuffer dataBuffer;

    // Copy of the scene framebuffer, sampled by the blur.
    private GpuTexture copyTexture;
    private GpuTextureView copyTextureView;
    private int lastWidth = 0;
    private int lastHeight = 0;

    // Accumulate animation time incrementally (dt * speed) so changing speed only
    // affects the rate going forward instead of rescaling all past time.
    private double accumulatedTime = 0.0;
    private double lastFrameTime = Double.NaN;
    private boolean initialized = false;

    public static BlockOverlayShaderPipeline getInstance() {
        return INSTANCE;
    }

    private void ensureInitialized() {
        if (initialized) return;

        this.dataBuffer = MemoryUtil.memAlloc(BUFFER_SIZE);

        ByteBuffer dummyData = MemoryUtil.memAlloc(4);
        dummyData.putInt(0);
        dummyData.flip();
        this.dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "rich:block_overlay_dummy_vertex",
                GpuBuffer.USAGE_VERTEX,
                dummyData
        );
        MemoryUtil.memFree(dummyData);

        this.lastFrameTime = GLFW.glfwGetTime();
        this.initialized = true;
    }

    private void ensureCopyTexture(int width, int height) {
        if (copyTexture != null && lastWidth == width && lastHeight == height) return;

        if (copyTextureView != null) {
            copyTextureView.close();
            copyTextureView = null;
        }
        if (copyTexture != null) {
            copyTexture.close();
            copyTexture = null;
        }
        copyTexture = RenderSystem.getDevice().createTexture(
                () -> "rich:block_overlay_copy",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                TextureFormat.RGBA8,
                width, height, 1, 1
        );
        copyTextureView = RenderSystem.getDevice().createTextureView(copyTexture);
        lastWidth = width;
        lastHeight = height;
    }

    /**
     * Advances the shared animation clock once per frame. Call once before the
     * per-box {@link #draw} calls so all boxes in a frame share the same time.
     *
     * @param speed time multiplier (1.0 = original speed)
     */
    public void tick(float speed) {
        ensureInitialized();
        double now = GLFW.glfwGetTime();
        accumulatedTime += (now - lastFrameTime) * speed;
        lastFrameTime = now;
    }

    /**
     * Draws the layered overlay onto one axis-aligned box. Must be called on the
     * render thread during world rendering (e.g. from {@code WorldRenderEvent}).
     *
     * @param box           box in absolute world coordinates
     * @param cameraPos     camera position to make the box camera-relative
     * @param fullBlock     true = draw all faces (shows through); false = depth
     *                      test so only the visible faces are drawn
     * @param particlesOn        whether to draw the particle shader on top
     * @param particleAlpha      particle transparency (0..1)
     * @param particleBrightness particle glow brightness (1 = original)
     * @param count              particle count
     * @param blur               frosted-glass blur radius (0 = transparent, no glass)
     * @param outlineColor       ARGB outline colour for the block edges
     * @param thickness          outline thickness in face-UV units (0 = no outline)
     */
    public void draw(Box box, Vec3d cameraPos, boolean fullBlock,
                     boolean particlesOn, float particleAlpha, float particleBrightness, float count,
                     float blur,
                     int outlineColor, float thickness) {
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer framebuffer = client.getFramebuffer();
        if (framebuffer == null || framebuffer.getColorAttachment() == null) return;

        ensureInitialized();

        int fbWidth = framebuffer.textureWidth;
        int fbHeight = framebuffer.textureHeight;
        ensureCopyTexture(fbWidth, fbHeight);

        boolean useBlur = blur > 0.0f;

        // clip = projection * modelView * (worldPos - cameraPos)
        mvp.set(Render3D.lastProjMat).mul(RenderSystem.getModelViewMatrix());

        // In depth-tested mode, nudge the faces slightly outwards so the visible
        // faces sit just in front of the block surface and don't z-fight with it.
        double pad = fullBlock ? 0.0 : 0.002;
        float minX = (float) (box.minX - pad - cameraPos.x);
        float minY = (float) (box.minY - pad - cameraPos.y);
        float minZ = (float) (box.minZ - pad - cameraPos.z);
        float maxX = (float) (box.maxX + pad - cameraPos.x);
        float maxY = (float) (box.maxY + pad - cameraPos.y);
        float maxZ = (float) (box.maxZ + pad - cameraPos.z);

        dataBuffer.clear();
        mvp.get(0, dataBuffer);
        dataBuffer.position(64);
        // boxMin
        dataBuffer.putFloat(minX).putFloat(minY).putFloat(minZ).putFloat(0f);
        // boxMax
        dataBuffer.putFloat(maxX).putFloat(maxY).putFloat(maxZ).putFloat(0f);
        // params: time, particleAlpha, count, particlesOn
        dataBuffer.putFloat((float) accumulatedTime)
                .putFloat(particleAlpha)
                .putFloat(count)
                .putFloat(particlesOn ? 1f : 0f);
        // outline: rgb, thickness
        dataBuffer.putFloat(r(outlineColor)).putFloat(g(outlineColor)).putFloat(b(outlineColor)).putFloat(thickness);
        // glassParams: blur, particleBrightness, 0, 0
        dataBuffer.putFloat(blur).putFloat(particleBrightness).putFloat(0f).putFloat(0f);
        // res: fbWidth, fbHeight
        dataBuffer.putFloat(fbWidth).putFloat(fbHeight).putFloat(0f).putFloat(0f);
        dataBuffer.flip();

        int size = dataBuffer.remaining();
        if (uniformBuffer == null || uniformBuffer.size() < size) {
            if (uniformBuffer != null) {
                uniformBuffer.close();
            }
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "rich:block_overlay_uniform",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    size
            );
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

        // Snapshot the scene for the frosted-glass blur (only when actually used).
        if (useBlur) {
            encoder.copyTextureToTexture(
                    framebuffer.getColorAttachment(), copyTexture,
                    0, 0, 0, 0, 0, fbWidth, fbHeight);
        }

        encoder.writeToBuffer(uniformBuffer.slice(), dataBuffer);

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .write(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);

        try (RenderPass renderPass = encoder.createRenderPass(
                () -> "rich:block_overlay_pass",
                framebuffer.getColorAttachmentView(),
                OptionalInt.empty(),
                framebuffer.getDepthAttachmentView(),
                OptionalDouble.empty())) {

            renderPass.setPipeline(fullBlock ? PIPELINE_FULL : PIPELINE_OCCLUDE);
            renderPass.setVertexBuffer(0, dummyVertexBuffer);
            renderPass.bindTexture("Sampler0", copyTextureView, sampler);

            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setUniform("BlockData", uniformBuffer);

            renderPass.draw(0, 36);
        }
    }

    private static float r(int argb) { return ((argb >> 16) & 0xFF) / 255.0f; }
    private static float g(int argb) { return ((argb >> 8) & 0xFF) / 255.0f; }
    private static float b(int argb) { return (argb & 0xFF) / 255.0f; }

    public void close() {
        if (uniformBuffer != null) {
            uniformBuffer.close();
            uniformBuffer = null;
        }
        if (dummyVertexBuffer != null) {
            dummyVertexBuffer.close();
            dummyVertexBuffer = null;
        }
        if (dataBuffer != null) {
            MemoryUtil.memFree(dataBuffer);
            dataBuffer = null;
        }
        if (copyTextureView != null) {
            copyTextureView.close();
            copyTextureView = null;
        }
        if (copyTexture != null) {
            copyTexture.close();
            copyTexture = null;
        }
        lastWidth = 0;
        lastHeight = 0;
        initialized = false;
    }
}
