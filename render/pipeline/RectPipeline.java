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

public class RectPipeline {

    private static final Identifier PIPELINE_ID = Identifier.of("rich", "pipeline/rect");
    private static final Identifier VERTEX_SHADER = Identifier.of("rich", "core/rect");
    private static final Identifier FRAGMENT_SHADER = Identifier.of("rich", "core/rect");

    private static final Vector3f MODEL_OFFSET = new Vector3f(0, 0, 0);
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static final float FIXED_GUI_SCALE = 2.0f;

    // Must match data[] size in rect.vsh: MAX_RECTS * 12 vec4. Header = 32 bytes.
    // 32 + 85*192 = 16352 bytes, under the 16384 UBO guaranteed minimum.
    private static final int MAX_RECTS = 85;
    private static final int HEADER_BYTES = 32;
    private static final int RECT_BYTES = 192;
    private static final int BUFFER_SIZE = HEADER_BYTES + MAX_RECTS * RECT_BYTES;

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(PIPELINE_ID)
                    .withVertexShader(VERTEX_SHADER)
                    .withFragmentShader(FRAGMENT_SHADER)
                    .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                    .withUniform("RectData", UniformType.UNIFORM_BUFFER)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);

    private GpuBuffer uniformBuffer;
    private GpuBuffer dummyVertexBuffer;
    private ByteBuffer dataBuffer;
    private boolean initialized = false;

    // Batch storage: reusable per-rect entries, filled until flush().
    private RectEntry[] batch;
    private int rectCount = 0;

    private static final class RectEntry {
        float x, y, width, height;
        final float[] radii = new float[4];
        float innerBlur;
        float softness;
        /** 1 = режим тени: рисуется только внешняя растушёвка, внутренность гасится. */
        float cutInner;
        final int[] colors9 = new int[9];
    }

    public RectPipeline() {
    }

    private void ensureInitialized() {
        if (initialized) return;

        this.dataBuffer = MemoryUtil.memAlloc(BUFFER_SIZE);

        this.batch = new RectEntry[MAX_RECTS];
        for (int i = 0; i < MAX_RECTS; i++) {
            this.batch[i] = new RectEntry();
        }

        ByteBuffer dummyData = MemoryUtil.memAlloc(4);
        dummyData.putInt(0);
        dummyData.flip();
        this.dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "minecraft:dummy_vertex",
                GpuBuffer.USAGE_VERTEX,
                dummyData
        );
        MemoryUtil.memFree(dummyData);

        initialized = true;
    }

    public void drawRect(float x, float y, float width, float height,
                         int[] colors, float[] radii) {
        drawRect(x, y, width, height, colors, radii, 0f);
    }

    public void drawRect(float x, float y, float width, float height,
                         int[] colors, float[] radii, float innerBlur) {
        drawRect(x, y, width, height, colors, radii, innerBlur, 0f);
    }

    /**
     * @param softness when > 0, the rounded-rect edge fades out softly across
     *                 this many fixed-scaled pixels (a drop-shadow / glow). The
     *                 quad is expanded outward by {@code softness} px in the
     *                 vertex shader so the falloff isn't clipped. 0 = crisp 1px AA.
     */
    public void drawRect(float x, float y, float width, float height,
                         int[] colors, float[] radii, float innerBlur, float softness) {
        drawRect(x, y, width, height, colors, radii, innerBlur, softness, false);
    }

    /**
     * @param cutInner true — режим тени: внутренность фигуры гасится, рисуется
     *                 только внешняя растушёвка. Тень под ПОЛУПРОЗРАЧНОЙ панелью
     *                 перестаёт просвечивать сквозь неё и менять её плотность.
     */
    public void drawRect(float x, float y, float width, float height,
                         int[] colors, float[] radii, float innerBlur, float softness, boolean cutInner) {

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getFramebuffer() == null) return;

        ensureInitialized();

        RectEntry e = batch[rectCount];
        e.x = x;
        e.y = y;
        e.width = width;
        e.height = height;
        e.radii[0] = radii[0];
        e.radii[1] = radii[1];
        e.radii[2] = radii[2];
        e.radii[3] = radii[3];
        e.innerBlur = innerBlur;
        e.softness = softness;
        e.cutInner = cutInner ? 1f : 0f;
        fill9Colors(colors, e.colors9);

        rectCount++;
        if (rectCount >= MAX_RECTS) {
            flush();
        }
    }

    public void flush() {
        if (rectCount == 0) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getFramebuffer() == null) {
            rectCount = 0;
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
                    () -> "minecraft:rect_uniform",
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

        int drawCount = rectCount;

        try (RenderPass renderPass = encoder.createRenderPass(
                () -> "minecraft:rect_pass",
                client.getFramebuffer().getColorAttachmentView(),
                OptionalInt.empty(),
                client.getFramebuffer().getDepthAttachmentView(),
                OptionalDouble.empty())) {

            renderPass.setPipeline(PIPELINE);
            renderPass.setVertexBuffer(0, dummyVertexBuffer);

            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setUniform("RectData", uniformBuffer);

            renderPass.draw(0, drawCount * 6);
        }

        rectCount = 0;
    }

    private void fill9Colors(int[] colors, int[] result) {
        if (colors.length == 1) {
            for (int i = 0; i < 9; i++) {
                result[i] = colors[0];
            }
        } else if (colors.length == 4) {
            result[0] = colors[0];
            result[1] = blendColors(colors[0], colors[1]);
            result[2] = colors[1];
            result[3] = blendColors(colors[0], colors[3]);
            result[4] = blendColors(colors[0], colors[1], colors[2], colors[3]);
            result[5] = blendColors(colors[1], colors[2]);
            result[6] = colors[3];
            result[7] = blendColors(colors[3], colors[2]);
            result[8] = colors[2];
        } else if (colors.length >= 9) {
            System.arraycopy(colors, 0, result, 0, 9);
        } else {
            for (int i = 0; i < 9; i++) {
                result[i] = colors[i % colors.length];
            }
        }
    }

    private int blendColors(int... colors) {
        int r = 0, g = 0, b = 0, a = 0;
        for (int color : colors) {
            a += (color >> 24) & 0xFF;
            r += (color >> 16) & 0xFF;
            g += (color >> 8) & 0xFF;
            b += color & 0xFF;
        }
        int count = colors.length;
        return ((a / count) << 24) | ((r / count) << 16) | ((g / count) << 8) | (b / count);
    }

    private void prepareUniformData(float screenWidth, float screenHeight, float guiScale) {
        dataBuffer.clear();

        // Header: screen vec4 + meta ivec4
        dataBuffer.putFloat(screenWidth);
        dataBuffer.putFloat(screenHeight);
        dataBuffer.putFloat(guiScale);
        dataBuffer.putFloat(0f);

        dataBuffer.putInt(rectCount);
        dataBuffer.putInt(0);
        dataBuffer.putInt(0);
        dataBuffer.putInt(0);

        for (int i = 0; i < rectCount; i++) {
            RectEntry e = batch[i];

            dataBuffer.putFloat(e.x);
            dataBuffer.putFloat(e.y);
            dataBuffer.putFloat(e.width);
            dataBuffer.putFloat(e.height);

            dataBuffer.putFloat(e.radii[0]);
            dataBuffer.putFloat(e.radii[1]);
            dataBuffer.putFloat(e.radii[2]);
            dataBuffer.putFloat(e.radii[3]);

            dataBuffer.putFloat(e.innerBlur);
            dataBuffer.putFloat(e.softness);
            dataBuffer.putFloat(e.cutInner);
            dataBuffer.putFloat(0f);

            for (int c = 0; c < 9; c++) {
                int color = e.colors9[c];
                float a = ((color >> 24) & 0xFF) / 255.0f;
                float r = ((color >> 16) & 0xFF) / 255.0f;
                float g = ((color >> 8) & 0xFF) / 255.0f;
                float bl = (color & 0xFF) / 255.0f;

                dataBuffer.putFloat(r);
                dataBuffer.putFloat(g);
                dataBuffer.putFloat(bl);
                dataBuffer.putFloat(a);
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
        batch = null;
        rectCount = 0;
        initialized = false;
    }
}
