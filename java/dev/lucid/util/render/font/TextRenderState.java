package dev.lucid.util.render.font;

import org.joml.Matrix3x2f;
import org.joml.Vector2f;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;

import dev.lucid.util.render.ClientPipelines;

/**
 * Целая строка одним элементом отрисовки.
 *
 * <p>На каждую букву идёт четыре вершины, но вся строка — один элемент. Переключений
 * пайплайна и текстуры внутри строки нет, а соседние строки игра склеивает сама,
 * потому что атлас у них общий.</p>
 *
 * <p>Раскладка считается в момент сборки вершин, а не заранее: так элемент остаётся
 * лёгким и не держит массивы на каждый кадр.</p>
 */
public final class TextRenderState implements GuiElementRenderState {

	/** Сколько долей пикселя влезает в целочисленный атрибут. */
	private static final float SUBPIXEL = 16.0F;

	/** Запас границ под сглаживание края. */
	private static final int ANTIALIAS_PADDING = 1;

	private final FontAtlas atlas;
	private final String text;
	private final Matrix3x2f pose;
	private final float x;
	private final float baseline;
	private final float size;
	private final int color;
	private final ScreenRectangle scissorArea;
	private final ScreenRectangle bounds;

	/**
	 * @param x        левый край строки
	 * @param baseline линия, на которой стоят буквы
	 * @param size     размер шрифта в пикселях интерфейса
	 */
	public TextRenderState(
			FontAtlas atlas,
			String text,
			Matrix3x2f pose,
			float x,
			float baseline,
			float size,
			int color,
			ScreenRectangle scissorArea) {
		this.atlas = atlas;
		this.text = text;
		this.pose = pose;
		this.x = x;
		this.baseline = baseline;
		this.size = size;
		this.color = color;
		this.scissorArea = scissorArea;
		this.bounds = computeBounds(atlas, text, x, baseline, size, pose, scissorArea);
	}

	@Override
	public RenderPipeline pipeline() {
		return ClientPipelines.TEXT;
	}

	@Override
	public TextureSetup textureSetup() {
		return FontTexture.of(this.atlas.texture());
	}

	@Override
	public ScreenRectangle scissorArea() {
		return this.scissorArea;
	}

	@Override
	public ScreenRectangle bounds() {
		return this.bounds;
	}

	@Override
	public void buildVertices(VertexConsumer consumer) {
		float pen = this.x;
		int previous = -1;
		int range = Math.round(this.atlas.distanceRange() * SUBPIXEL);

		for (int index = 0; index < this.text.length(); ) {
			int code = this.text.codePointAt(index);
			index += Character.charCount(code);

			Glyph glyph = this.atlas.glyph(code);

			if (glyph == null) {
				continue;
			}

			pen += this.atlas.kerning(previous, code) * this.size;
			previous = code;

			if (glyph.drawable()) {
				float left = pen + glyph.left() * this.size;
				float right = pen + glyph.right() * this.size;
				float top = this.baseline + glyph.top() * this.size;
				float bottom = this.baseline + glyph.bottom() * this.size;

				// По часовой стрелке, как и везде в интерфейсе.
				this.emit(consumer, left, top, glyph.u0(), glyph.v0(), range);
				this.emit(consumer, left, bottom, glyph.u0(), glyph.v1(), range);
				this.emit(consumer, right, bottom, glyph.u1(), glyph.v1(), range);
				this.emit(consumer, right, top, glyph.u1(), glyph.v0(), range);
			}

			pen += glyph.advance() * this.size;
		}
	}

	private void emit(VertexConsumer consumer, float x, float y, float u, float v, int range) {
		Vector2f point = this.pose.transformPosition(new Vector2f(x, y));

		consumer.addVertex(point.x, point.y, 0.0F)
				.setColor(this.color)
				.setUv(u, v)
				.setUv1(range, 0);
	}

	private static ScreenRectangle computeBounds(
			FontAtlas atlas,
			String text,
			float x,
			float baseline,
			float size,
			Matrix3x2f pose,
			ScreenRectangle scissorArea) {
		// Границы берутся по всей высоте строки, а не по фактическим буквам: иначе строка
		// без хвостов и строка с хвостами попадали бы в разные пачки по порядку.
		float width = atlas.width(text, size);
		float top = baseline - atlas.ascender(size);
		float bottom = baseline - atlas.descender(size);

		int left = (int) Math.floor(x) - ANTIALIAS_PADDING;
		int boxTop = (int) Math.floor(top) - ANTIALIAS_PADDING;
		int boxWidth = (int) Math.ceil(width) + ANTIALIAS_PADDING * 2;
		int boxHeight = (int) Math.ceil(bottom - top) + ANTIALIAS_PADDING * 2;

		ScreenRectangle rectangle = new ScreenRectangle(left, boxTop, boxWidth, boxHeight)
				.transformMaxBounds(pose);

		if (scissorArea == null) {
			return rectangle;
		}

		return scissorArea.intersection(rectangle);
	}
}
