package dev.lucid.util.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;

import net.minecraft.client.Minecraft;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import dev.lucid.Lucid;
import dev.lucid.module.impl.render.Saturation;

/**
 * Копирует цвет кадра, гоняет через {@link SaturationPipeline} и пишет обратно.
 */
public final class SaturationRenderer {

	private static final SaturationRenderer INSTANCE = new SaturationRenderer();

	private SaturationPipeline pipeline;
	private GpuTexture sceneTexture;
	private GpuTextureView sceneTextureView;
	private int lastWidth;
	private int lastHeight;
	private boolean hooked;

	private SaturationRenderer() {
	}

	public static SaturationRenderer getInstance() {
		return INSTANCE;
	}

	/** Подписка один раз на ините клиента: от событий Fabric не отписаться. */
	public void init() {
		if (this.hooked) {
			return;
		}

		this.hooked = true;
		LevelRenderEvents.END_MAIN.register(context -> this.apply());
	}

	public void apply() {
		Saturation module = Saturation.getInstance();

		if (module == null || !module.shouldApply()) {
			return;
		}

		float saturation = module.getSaturation();
		float vibrance = module.getVibrance();
		float contrast = module.getContrast();

		boolean active = Math.abs(saturation - 1.0F) > 0.001F
				|| Math.abs(vibrance) > 0.001F
				|| Math.abs(contrast - 1.0F) > 0.001F;

		if (!active) {
			return;
		}

		try {
			Minecraft client = Minecraft.getInstance();
			RenderTarget target = client.gameRenderer.mainRenderTarget();

			if (target == null || target.getColorTextureView() == null || target.getColorTexture() == null) {
				return;
			}

			GpuTexture color = target.getColorTexture();
			int width = color.getWidth(0);
			int height = color.getHeight(0);

			if (width <= 0 || height <= 0) {
				return;
			}

			this.ensureTextures(width, height);

			CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
			encoder.copyTextureToTexture(color, this.sceneTexture, 0, 0, 0, 0, 0, width, height);

			this.pipeline.grade(
					target.getColorTextureView(),
					this.sceneTextureView,
					Math.max(0.0F, saturation),
					vibrance,
					Math.max(0.0F, contrast));
		} catch (Throwable t) {
			Lucid.LOGGER.error("Saturation pass failed", t);
		}
	}

	private void ensureTextures(int width, int height) {
		if (this.pipeline == null) {
			this.pipeline = new SaturationPipeline();
		}

		if (width == this.lastWidth && height == this.lastHeight && this.sceneTexture != null) {
			return;
		}

		this.cleanupTextures();

		this.sceneTexture = RenderSystem.getDevice().createTexture(
				() -> Lucid.MOD_ID + ":saturation_scene",
				GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
				GpuFormat.RGBA8_UNORM,
				width, height, 1, 1);
		this.sceneTextureView = RenderSystem.getDevice().createTextureView(this.sceneTexture);
		this.lastWidth = width;
		this.lastHeight = height;
	}

	private void cleanupTextures() {
		if (this.sceneTextureView != null) {
			this.sceneTextureView.close();
			this.sceneTextureView = null;
		}

		if (this.sceneTexture != null) {
			this.sceneTexture.close();
			this.sceneTexture = null;
		}

		this.lastWidth = 0;
		this.lastHeight = 0;
	}

	public void close() {
		this.cleanupTextures();

		if (this.pipeline != null) {
			this.pipeline.close();
			this.pipeline = null;
		}
	}
}
