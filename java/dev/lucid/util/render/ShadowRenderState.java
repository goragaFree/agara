package dev.lucid.util.render;

import org.joml.Matrix3x2f;
import org.joml.Vector2f;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;

/**
 * Тень под фигурой: та же формула формы, но с широкой полосой затухания.
 *
 * <p>Главное отличие от {@link RectRenderState}: прямоугольник, который рисуется, больше
 * самой фигуры. Размытый край уходит наружу на целый радиус размытия, и если рисовать
 * его по геометрии фигуры, тень обрежется ровно по её краю — видно как чёткая рамка
 * вокруг панели.</p>
 *
 * <p>Поэтому в вершины едет два разных прямоугольника: геометрия — расширенная, а
 * половинный размер и координата от центра — от самой тени. Шейдер считает расстояние
 * до второго, а заливает первый.</p>
 *
 * <p>Целые атрибуты, как и у фигур, пишутся в шестнадцатых долях пикселя.</p>
 *
 * @param centerX  центр фигуры, которая отбрасывает тень, вместе со смещением тени
 * @param halfWidth  половина ширины тени без учёта размытия
 * @param blur       радиус размытия в пикселях интерфейса
 */
public record ShadowRenderState(
		RenderPipeline pipeline,
		Matrix3x2f pose,
		float x0,
		float y0,
		float x1,
		float y1,
		float centerX,
		float centerY,
		float halfWidth,
		float halfHeight,
		float radius,
		float blur,
		int color,
		ScreenRectangle scissorArea,
		ScreenRectangle bounds) implements GuiElementRenderState {

	/** Сколько долей пикселя влезает в целочисленный атрибут. */
	private static final float SUBPIXEL = 16.0F;

	/** Запас границ под сглаживание края. */
	private static final int ANTIALIAS_PADDING = 1;

	/**
	 * Обычный конструктор: границы считаются сами по расширенному прямоугольнику.
	 *
	 * <p>Границы нужны игре, чтобы понять, какие элементы перекрываются. У тени брать
	 * границы фигуры нельзя: она больше её со всех сторон.</p>
	 */
	public ShadowRenderState(
			RenderPipeline pipeline,
			Matrix3x2f pose,
			float x0,
			float y0,
			float x1,
			float y1,
			float centerX,
			float centerY,
			float halfWidth,
			float halfHeight,
			float radius,
			float blur,
			int color,
			ScreenRectangle scissorArea) {
		this(
				pipeline,
				pose,
				x0,
				y0,
				x1,
				y1,
				centerX,
				centerY,
				halfWidth,
				halfHeight,
				radius,
				blur,
				color,
				scissorArea,
				computeBounds(x0, y0, x1, y1, pose, scissorArea));
	}

	/** Четыре вершины по часовой стрелке, как и у остальных элементов интерфейса. */
	@Override
	public void buildVertices(VertexConsumer consumer) {
		this.emit(consumer, this.x0, this.y0);
		this.emit(consumer, this.x0, this.y1);
		this.emit(consumer, this.x1, this.y1);
		this.emit(consumer, this.x1, this.y0);
	}

	private void emit(VertexConsumer consumer, float x, float y) {
		Vector2f point = this.pose.transformPosition(new Vector2f(x, y));

		consumer.addVertex(point.x, point.y, 0.0F)
				.setColor(this.color)
				.setUv(x - this.centerX, y - this.centerY)
				.setUv1(pack(this.halfWidth), pack(this.halfHeight))
				.setUv2(pack(this.radius), pack(this.blur));
	}

	private static int pack(float value) {
		return Math.round(value * SUBPIXEL);
	}

	/** Текстура не нужна: цвет едет в самих вершинах. */
	@Override
	public TextureSetup textureSetup() {
		return TextureSetup.noTexture();
	}

	private static ScreenRectangle computeBounds(
			float x0,
			float y0,
			float x1,
			float y1,
			Matrix3x2f pose,
			ScreenRectangle scissorArea) {
		int left = (int) Math.floor(Math.min(x0, x1)) - ANTIALIAS_PADDING;
		int top = (int) Math.floor(Math.min(y0, y1)) - ANTIALIAS_PADDING;
		int width = (int) Math.ceil(Math.abs(x1 - x0)) + ANTIALIAS_PADDING * 2;
		int height = (int) Math.ceil(Math.abs(y1 - y0)) + ANTIALIAS_PADDING * 2;

		ScreenRectangle rectangle = new ScreenRectangle(left, top, width, height).transformMaxBounds(pose);

		if (scissorArea == null) {
			return rectangle;
		}

		return scissorArea.intersection(rectangle);
	}
}
