package rich.util.render.pipeline;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import rich.util.render.Render3D;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Fullscreen "lava pool" sky shader pass. Built on the same GPU pipeline API as
 * {@link RectPipeline}; drawn from inside the frame-graph "sky" pass so it fully
 * replaces the vanilla sky in the main framebuffer.
 */
public class SkyShaderPipeline {

    private static final Identifier PIPELINE_ID = Identifier.of("rich", "pipeline/sky");
    private static final Identifier VERTEX_SHADER = Identifier.of("rich", "core/sky");
    private static final Identifier FRAGMENT_SHADER = Identifier.of("rich", "core/sky");

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(PIPELINE_ID)
                    .withVertexShader(VERTEX_SHADER)
                    .withFragmentShader(FRAGMENT_SHADER)
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("SkyData", UniformType.UNIFORM_BUFFER)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    // std140: vec4 (16) + mat4 invProj (64) + mat4 invView (64) + vec4 iParams (16) = 160
    private static final int BUFFER_SIZE = 160;

    private static final SkyShaderPipeline INSTANCE = new SkyShaderPipeline();

    private final Matrix4f invProj = new Matrix4f();
    private final Matrix4f invView = new Matrix4f();

    private GpuBuffer uniformBuffer;
    private GpuBuffer dummyVertexBuffer;
    private ByteBuffer dataBuffer;
    // Accumulate animation time incrementally (dt * speed) instead of
    // elapsed * speed, so changing the speed slider only affects the rate going
    // forward and never teleports the pattern by rescaling all past time.
    private double accumulatedTime = 0.0;
    private double lastFrameTime = Double.NaN;
    private boolean initialized = false;

    public static SkyShaderPipeline getInstance() {
        return INSTANCE;
    }

    private void ensureInitialized() {
        if (initialized) return;

        this.dataBuffer = MemoryUtil.memAlloc(BUFFER_SIZE);

        ByteBuffer dummyData = MemoryUtil.memAlloc(4);
        dummyData.putInt(0);
        dummyData.flip();
        this.dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "rich:sky_dummy_vertex",
                GpuBuffer.USAGE_VERTEX,
                dummyData
        );
        MemoryUtil.memFree(dummyData);

        this.lastFrameTime = GLFW.glfwGetTime();
        this.initialized = true;
    }

    /**
     * Draws the fullscreen sky shader. Must be called on the render thread while
     * the main framebuffer is the active render target (i.e. from the frame-graph
     * sky pass renderer).
     *
     * @param speed  time multiplier (1.0 = original speed)
     * @param mode   shader mode (0 = lava pool, 1 = eyes, 2 = cosmo, 3 = cosmo2)
     * @param wobble cosmo2 turbulence amplitude
     * @param holes  cosmo2 dark-lobe count (1..2)
     */
    public void draw(float speed, float mode, float wobble, float holes) {
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer framebuffer = client.getFramebuffer();
        if (framebuffer == null) return;

        ensureInitialized();

        int width = framebuffer.textureWidth;
        int height = framebuffer.textureHeight;
        double now = GLFW.glfwGetTime();
        accumulatedTime += (now - lastFrameTime) * speed;
        lastFrameTime = now;
        float time = (float) accumulatedTime;

        // Inverse view/projection so the fragment shader can reconstruct a
        // world-space ray per pixel -> the sky stays fixed while the camera turns.
        invProj.set(Render3D.lastProjMat).invert();
        invView.set(RenderSystem.getModelViewMatrix()).invert();

        dataBuffer.clear();
        dataBuffer.putFloat(width);
        dataBuffer.putFloat(height);
        dataBuffer.putFloat(time);
        dataBuffer.putFloat(mode);
        invProj.get(16, dataBuffer);
        invView.get(80, dataBuffer);
        dataBuffer.position(144);
        dataBuffer.putFloat(wobble);
        dataBuffer.putFloat(holes);
        dataBuffer.putFloat(0.0f);
        dataBuffer.putFloat(0.0f);
        dataBuffer.flip();

        int size = dataBuffer.remaining();
        if (uniformBuffer == null || uniformBuffer.size() < size) {
            if (uniformBuffer != null) {
                uniformBuffer.close();
            }
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "rich:sky_uniform",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    size
            );
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToBuffer(uniformBuffer.slice(), dataBuffer);

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .write(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        try (RenderPass renderPass = encoder.createRenderPass(
                () -> "rich:sky_pass",
                framebuffer.getColorAttachmentView(),
                OptionalInt.empty(),
                framebuffer.getDepthAttachmentView(),
                OptionalDouble.empty())) {

            renderPass.setPipeline(PIPELINE);
            renderPass.setVertexBuffer(0, dummyVertexBuffer);

            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setUniform("SkyData", uniformBuffer);

            renderPass.draw(0, 3);
        }
    }

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
        initialized = false;
    }
}
