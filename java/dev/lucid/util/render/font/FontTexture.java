package dev.lucid.util.render.font;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

/**
 * Единственное место в клиенте, где мы трогаем текстуры.
 *
 * <p>Всё остальное рисуется вообще без них, поэтому все вызовы собраны сюда и нигде
 * больше не встречаются.</p>
 *
 * <p>Картинка грузится менеджером текстур игры как обычный ресурс — своё создание
 * текстуры и заливка пикселей руками нам не нужны.</p>
 */
public final class FontTexture {

	private FontTexture() {
	}

	/**
	 * Привязка атласа к элементу отрисовки.
	 *
	 * <p>Менеджер сам подгружает картинку при первом обращении, поэтому отдельной
	 * регистрации на старте клиента не требуется.</p>
	 */
	public static TextureSetup of(Identifier texture) {
		AbstractTexture loaded = Minecraft.getInstance().getTextureManager().getTexture(texture);

		return TextureSetup.singleTexture(loaded.getTextureView(), sampler());
	}

	/**
	 * Плавная выборка с прижатием к краю.
	 *
	 * <p>Поле расстояний имеет смысл только при плавной выборке: именно промежуточные
	 * значения между соседними пикселями и дают гладкий край буквы. Прижатие к краю нужно,
	 * чтобы соседние буквы в атласе не подтекали друг в друга по краям.</p>
	 *
	 * <p>Кэш отдаёт один и тот же объект на одинаковые настройки, поэтому звать его
	 * каждый кадр дешёво, а склеивание строк в одну пачку от этого не ломается.</p>
	 */
	private static GpuSampler sampler() {
		return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
	}
}
