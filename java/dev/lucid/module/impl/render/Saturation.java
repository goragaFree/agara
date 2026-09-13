package dev.lucid.module.impl.render;

import net.minecraft.client.Minecraft;

import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.SliderSetting;
import dev.lucid.util.render.SaturationRenderer;

/**
 * Цветокоррекция мира: насыщенность, живость и контраст.
 *
 * <p>Это пост-эффект на готовый кадр, а не смена гаммы. Ползунки живут здесь,
 * сама отрисовка — в {@link SaturationRenderer}.</p>
 */
public class Saturation extends Module {

	private static Saturation instance;

	private final SliderSetting saturation = this.addSetting(new SliderSetting(
			"saturation", "Насыщенность",
			"0 — чёрно-белый мир, 1 — как в ванили, больше — сочнее",
			1.4, 0.0, 3.0, 0.05));

	private final SliderSetting vibrance = this.addSetting(new SliderSetting(
			"vibrance", "Живость",
			"Поднимает тусклые цвета сильнее, чем уже яркие",
			0.0, 0.0, 1.0, 0.05));

	private final SliderSetting contrast = this.addSetting(new SliderSetting(
			"contrast", "Контраст",
			"1 — как в ванили",
			1.0, 0.5, 2.0, 0.05));

	public Saturation() {
		super("saturation", "Saturation", Category.RENDER,
				"Насыщенность и контраст мира");
		instance = this;
	}

	public static Saturation getInstance() {
		return instance;
	}

	public boolean shouldApply() {
		Minecraft client = mc();
		return this.isEnabled() && client.level != null;
	}

	public float getSaturation() {
		return this.saturation.getAsFloat();
	}

	public float getVibrance() {
		return this.vibrance.getAsFloat();
	}

	public float getContrast() {
		return this.contrast.getAsFloat();
	}
}
