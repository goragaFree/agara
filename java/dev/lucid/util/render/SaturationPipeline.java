package dev.lucid.util.render;

import java.nio.ByteBuffer;
import java.util.Optional;

import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import dev.lucid.Lucid;

/**
 * Полноэкранный проход цветокоррекции: saturation / vibrance / contrast.
 */
public final class SaturationPipeline {

	private static final Identifier PIPELINE_ID =
			Identifier.fromNamespaceAndPath(Lucid.MOD_ID, "pipeline/saturation");
	private static final Identifier SHADER =
			Identifier.fromNamespaceAndPath(Lucid.MOD_ID, "core/saturation");

	private static final VertexFormat EMPTY_FORMAT = VertexFormat.builder(0).build();

	private static final BindGroupLayout LAYOUT = BindGroupLayout.builder()
			.withSampler("SceneSampler")
			.withUniform("SaturationData", UniformType.UNIFORM_BUFFER)
			.build();

	private static final RenderPipeline PIPELINE = RenderPipelines.register(
			RenderPipeline.builder()
					.withLocation(PIPELINE_ID)
					.withVertexShader(SHADER)
					.withFragmentShader(SHADER)
					.withBindGroupLayout(LAYOUT)
					.withVertexBinding(0, EMPTY_FORMAT)
					.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
					.withDepthStencilState(Optional.empty())
					.withCull(false)
					.build());

	private static final int BUFFER_SIZE = 16;

	private GpuBuffer uniformBuffer;
	private GpuBuffer dummyVertexBuffer;
	private ByteBuffer dataBuffer;
	private boolean initialized;

	private void ensureInitialized() {
		if (this.initialized) {
			return;
		}

		this.dataBuffer = MemoryUtil.memAlloc(BUFFER_SIZE);

		ByteBuffer dummyData = MemoryUtil.memAlloc(4);
		dummyData.putInt(0);
		dummyData.flip();
		this.dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
				() -> Lucid.MOD_ID + ":saturation_dummy_vertex",
				GpuBuffer.USAGE_VERTEX,
				dummyData);
		MemoryUtil.memFree(dummyData);

		this.initialized = true;
	}

	public void grade(GpuTextureView targetView, GpuTextureView sceneView,
			float saturation, float vibrance, float contrast) {
		if (targetView == null || sceneView == null) {
			return;
		}

		this.ensureInitialized();

		this.dataBuffer.clear();
		this.dataBuffer.putFloat(saturation);
		this.dataBuffer.putFloat(vibrance);
		this.dataBuffer.putFloat(contrast);
		this.dataBuffer.putFloat(0.0F);
		this.dataBuffer.flip();

		int size = this.dataBuffer.remaining();

		if (this.uniformBuffer == null || this.uniformBuffer.size() < size) {
			if (this.uniformBuffer != null) {
				this.uniformBuffer.close();
			}

			this.uniformBuffer = RenderSystem.getDevice().createBuffer(
					() -> Lucid.MOD_ID + ":saturation_uniform",
					GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
					size);
		}

		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		encoder.writeToBuffer(this.uniformBuffer.slice(), this.dataBuffer);

		try (RenderPass renderPass = encoder.createRenderPass(
				() -> Lucid.MOD_ID + " saturation",
				targetView,
				Optional.empty())) {
			renderPass.setPipeline(PIPELINE);
			renderPass.setVertexBuffer(0, this.dummyVertexBuffer.slice());
			renderPass.bindTexture(
					"SceneSampler",
					sceneView,
					RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
			renderPass.setUniform("SaturationData", this.uniformBuffer);
			renderPass.draw(6, 1, 0, 0);
		}
	}

	public void close() {
		if (this.uniformBuffer != null) {
			this.uniformBuffer.close();
			this.uniformBuffer = null;
		}

		if (this.dummyVertexBuffer != null) {
			this.dummyVertexBuffer.close();
			this.dummyVertexBuffer = null;
		}

		if (this.dataBuffer != null) {
			MemoryUtil.memFree(this.dataBuffer);
			this.dataBuffer = null;
		}

		this.initialized = false;
	}
}
