package dev.lucid.util.render;

import org.joml.Matrix3x2f;
import org.joml.Vector2f;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;

/**
 * Свой элемент отрисовки: прямоугольник со скруглением и цветом в каждом углу.
 *
 * <p>Геометрия всегда одна и та же — четыре вершины. Скругление, форма угла и обводка
 * считаются в шейдере, поэтому радиус ничего не стоит по числу треугольников.</p>
 *
 * <p>В вершины кроме цвета едет три вещи: координата от центра фигуры, половинный размер
 * и пара «радиус — толщина обводки». Целые атрибуты пишутся в шестнадцатых долях пикселя:
 * целого пикселя мало для плавных анимаций, а float-атрибутов больше нет.</p>
 */
public record RectRenderState(
		RenderPipeline pipeline,
		Matrix3x2f pose,
		float x0,
		float y0,
		float x1,
		float y1,
		float radius,
		float thickness,
		int colorTopLeft,
		int colorTopRight,
		int colorBottomRight,
		int colorBottomLeft,
		ScreenRectangle scissorArea,
		ScreenRectangle bounds) implements GuiElementRenderState {

	/** Сколько долей пикселя влезает в целочисленный атрибут. */
	private static final float SUBPIXEL = 16.0F;

	/** Запас границ под сглаживание края. */
	private static final int ANTIALIAS_PADDING = 1;

	/**
	 * Обычный конструктор: границы считаются сами.
	 *
	 * <p>Границы нужны игре, чтобы понять, какие элементы перекрываются и что можно
	 * склеить в одну пачку. Если наврать — элемент может пропасть или нарисоваться
	 * не в том порядке.</p>
	 */
	public RectRenderState(
			RenderPipeline pipeline,
			Matrix3x2f pose,
			float x0,
			float y0,
			float x1,
			float y1,
			float radius,
			float thickness,
			int colorTopLeft,
			int colorTopRight,
			int colorBottomRight,
			int colorBottomLeft,
			ScreenRectangle scissorArea) {
		this(
				pipeline,
				pose,
				x0,
				y0,
				x1,
				y1,
				radius,
				thickness,
				colorTopLeft,
				colorTopRight,
				colorBottomRight,
				colorBottomLeft,
				scissorArea,
				computeBounds(x0, y0, x1, y1, pose, scissorArea));
	}

	/** Четыре вершины по часовой стрелке, как это делает ванильная заливка. */
	@Override
	public void buildVertices(VertexConsumer consumer) {
		this.emit(consumer, this.x0, this.y0, this.colorTopLeft);
		this.emit(consumer, this.x0, this.y1, this.colorBottomLeft);
		this.emit(consumer, this.x1, this.y1, this.colorBottomRight);
		this.emit(consumer, this.x1, this.y0, this.colorTopRight);
	}

	private void emit(VertexConsumer consumer, float x, float y, int color) {
		Vector2f point = this.pose.transformPosition(new Vector2f(x, y));

		float centerX = (this.x0 + this.x1) * 0.5F;
		float centerY = (this.y0 + this.y1) * 0.5F;
		float halfWidth = Math.abs(this.x1 - this.x0) * 0.5F;
		float halfHeight = Math.abs(this.y1 - this.y0) * 0.5F;

		consumer.addVertex(point.x, point.y, 0.0F)
				.setColor(color)
				.setUv(x - centerX, y - centerY)
				.setUv1(pack(halfWidth), pack(halfHeight))
				.setUv2(pack(this.radius), pack(this.thickness));
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
		// Запас по пикселю с каждой стороны: сглаженный край выходит чуть за геометрию.
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
