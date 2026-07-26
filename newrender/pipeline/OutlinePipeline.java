package rich.util.render.pipeline;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * БАТЧ-пайплайн контуров (по образцу {@link RectPipeline}): вызовы
 * {@code drawOutline} аккумулируются и рисуются ОДНИМ рендер-пассом на
 * {@link #flush()}. Раньше каждый контур был отдельным пассом (setPipeline +
 * uniform upload + draw) — с панелями, кейкапами и свотчами это десятки
 * пассов на кадр; теперь — один. Флаш дёргается из Render2D.flushRects()
 * на тех же z-границах, что и батч ректов.
 */
public class OutlinePipeline {

    private static final Identifier PIPELINE_ID = Identifier.of("rich", "pipeline/outline");
    private static final Identifier VERTEX_SHADER = Identifier.of("rich", "core/outline");
    private static final Identifier FRAGMENT_SHADER = Identifier.of("rich", "core/outline");

    private static final Vector3f MODEL_OFFSET = new Vector3f(0, 0, 0);
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static final float FIXED_GUI_SCALE = 2.0f;

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(PIPELINE_ID)
                    .withVertexShader(VERTEX_SHADER)
                    .withFragmentShader(FRAGMENT_SHADER)
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("OutlineData", UniformType.UNIFORM_BUFFER)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);

    /**
     * Максимум контуров в одном батче. Раскладка на контур = 13 vec4 (208 байт):
     * rect + radii + params + 8 цветов + 2 vec4 толщин. Заголовок 32 байта.
     * 72 * 208 + 32 = 15008 — укладывается в гарантированный минимум UBO 16384.
     * Должно совпадать с размером data[] в outline.vsh (72 * 13 = 936).
     */
    private static final int MAX_OUTLINES = 72;
    private static final int BYTES_PER_OUTLINE = 13 * 16;
    private static final int BUFFER_SIZE = 32 + MAX_OUTLINES * BYTES_PER_OUTLINE;

    private GpuBuffer uniformBuffer;
    private GpuBuffer dummyVertexBuffer;
    private ByteBuffer dataBuffer;
    private boolean initialized = false;

    // Batch storage: reusable per-outline entries, filled until flush().
    private OutlineEntry[] batch;
    private int outlineCount = 0;

    private static final class OutlineEntry {
        float x, y, width, height;
        float smoothness;
        final float[] radii = new float[4];
        final int[] colors8 = new int[8];
        final float[] thick8 = new float[8];
    }

    public OutlinePipeline() {
    }

    private void ensureInitialized() {
        if (initialized) return;

        this.dataBuffer = MemoryUtil.memAlloc(BUFFER_SIZE);

        this.batch = new OutlineEntry[MAX_OUTLINES];
        for (int i = 0; i < MAX_OUTLINES; i++) {
            this.batch[i] = new OutlineEntry();
        }

        ByteBuffer dummyData = MemoryUtil.memAlloc(4);
        dummyData.putInt(0);
        dummyData.flip();
        this.dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "minecraft:outline_dummy_vertex",
                GpuBuffer.USAGE_VERTEX,
                dummyData
        );
        MemoryUtil.memFree(dummyData);

        initialized = true;
    }

    /** Кладёт контур в батч (данные копируются сразу — входные массивы можно переиспользовать). */
    public void drawOutline(float x, float y, float width, float height,
                            int[] colors, float[] thicknesses, float[] radii, float smoothness) {

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getFramebuffer() == null) return;

        ensureInitialized();

        OutlineEntry e = batch[outlineCount];
        e.x = x;
        e.y = y;
        e.width = width;
        e.height = height;
        e.smoothness = smoothness;
        e.radii[0] = radii[0];
        e.radii[1] = radii[1];
        e.radii[2] = radii[2];
        e.radii[3] = radii[3];
        for (int i = 0; i < 8; i++) {
            e.colors8[i] = i < colors.length ? colors[i] : colors[colors.length - 1];
            e.thick8[i] = i < thicknesses.length ? thicknesses[i] : thicknesses[thicknesses.length - 1];
        }

        outlineCount++;
        if (outlineCount >= MAX_OUTLINES) {
            flush();
        }
    }

    /** Рисует накопленные контуры одним рендер-пассом. Пустой батч — бесплатный выход. */
    public void flush() {
        if (outlineCount == 0) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getFramebuffer() == null) {
            outlineCount = 0;
            return;
        }

        int framebufferWidth = client.getWindow().getFramebufferWidth();
        int framebufferHeight = client.getWindow().getFramebufferHeight();
        float fixedScreenWidth = framebufferWidth / FIXED_GUI_SCALE;
        float fixedScreenHeight = framebufferHeight / FIXED_GUI_SCALE;

        prepareUniformData(fixedScreenWidth, fixedScreenHeight, FIXED_GUI_SCALE);

        int size = dataBuffer.remaining();
        if (uniformBuffer == null || uniformBuffer.size() < size) {
            if (uniformBuffer != null) {
                uniformBuffer.close();
            }
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "minecraft:outline_uniform",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    size
            );
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToBuffer(uniformBuffer.slice(), dataBuffer);

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .write(RenderSystem.getModelViewMatrix(),
                        COLOR_MODULATOR,
                        MODEL_OFFSET,
                        TEXTURE_MATRIX);

        int drawCount = outlineCount;

        try (RenderPass renderPass = encoder.createRenderPass(
                () -> "minecraft:outline_pass",
                client.getFramebuffer().getColorAttachmentView(),
                OptionalInt.empty(),
                client.getFramebuffer().getDepthAttachmentView(),
                OptionalDouble.empty())) {

            renderPass.setPipeline(PIPELINE);
            renderPass.setVertexBuffer(0, dummyVertexBuffer);

            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setUniform("OutlineData", uniformBuffer);

            renderPass.draw(0, drawCount * 6);
        }

        outlineCount = 0;
    }

    private void prepareUniformData(float screenWidth, float screenHeight, float guiScale) {
        dataBuffer.clear();

        // Header: screen vec4 + meta ivec4 (как у RectPipeline)
        dataBuffer.putFloat(screenWidth);
        dataBuffer.putFloat(screenHeight);
        dataBuffer.putFloat(guiScale);
        dataBuffer.putFloat(0f);

        dataBuffer.putInt(outlineCount);
        dataBuffer.putInt(0);
        dataBuffer.putInt(0);
        dataBuffer.putInt(0);

        for (int i = 0; i < outlineCount; i++) {
            OutlineEntry e = batch[i];

            dataBuffer.putFloat(e.x);
            dataBuffer.putFloat(e.y);
            dataBuffer.putFloat(e.width);
            dataBuffer.putFloat(e.height);

            dataBuffer.putFloat(e.radii[0]);
            dataBuffer.putFloat(e.radii[1]);
            dataBuffer.putFloat(e.radii[2]);
            dataBuffer.putFloat(e.radii[3]);

            dataBuffer.putFloat(e.smoothness);
            dataBuffer.putFloat(0f);
            dataBuffer.putFloat(0f);
            dataBuffer.putFloat(0f);

            for (int c = 0; c < 8; c++) {
                int color = e.colors8[c];
                float a = ((color >> 24) & 0xFF) / 255.0f;
                float r = ((color >> 16) & 0xFF) / 255.0f;
                float g = ((color >> 8) & 0xFF) / 255.0f;
                float b = (color & 0xFF) / 255.0f;

                dataBuffer.putFloat(r);
                dataBuffer.putFloat(g);
                dataBuffer.putFloat(b);
                dataBuffer.putFloat(a);
            }

            for (int t = 0; t < 8; t++) {
                dataBuffer.putFloat(e.thick8[t]);
            }
        }

        dataBuffer.flip();
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
