package dev.lucid.util.render;

import org.joml.Matrix3x2f;

import com.mojang.blaze3d.pipeline.RenderPipeline;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;

import dev.lucid.mixin.GuiGraphicsExtractorAccessor;
import dev.lucid.util.render.font.FontAtlas;
import dev.lucid.util.render.font.Fonts;
import dev.lucid.util.render.font.TextRenderState;

/**
 * Единая точка отрисовки для всего интерфейса клиента.
 *
 * <p>Всё рисуется своим шейдером: форма считается формулой, поэтому скругление любого
 * радиуса стоит ровно столько же, сколько обычный прямоугольник. Текст устроен так же:
 * буква — это тоже расстояние до края, только взятое из атласа, а не считанное на месте.</p>
 *
 * <p>Состояния класс не хранит: экстрактор всегда приходит первым аргументом. Рисовать
 * можно только изнутри фазы сборки кадра — то есть из {@code extractRenderState} экрана
 * или из HUD-элемента, а не из произвольного места.</p>
 */
public final class Render2D {

	/** Заливка без обводки. */
	private static final float FILLED = 0.0F;

	/** Прямой угол. */
	private static final float SHARP = 0.0F;

	/** На сколько смещается тень текста по умолчанию. */
	private static final float SHADOW_OFFSET = 1.0F;

	/** Цвет тени текста: чёрный, не до конца непрозрачный. */
	private static final int SHADOW_COLOR = 0xA0000000;

	private Render2D() {
	}

	/** Заливка одним цветом. */
	public static void rect(GuiGraphicsExtractor graphics, float x, float y, float width, float height, int color) {
		gradient(graphics, x, y, width, height, color, color, color, color);
	}

	/** Заливка со скруглёнными углами. */
	public static void roundedRect(
			GuiGraphicsExtractor graphics,
			float x,
			float y,
			float width,
			float height,
			float radius,
			int color) {
		shape(graphics, ClientPipelines.ROUND, x, y, width, height, radius, FILLED,
				color, color, color, color);
	}

	/** То же скругление, но сглаженное: угол переходит в сторону мягче, чем дуга. */
	public static void squircle(
			GuiGraphicsExtractor graphics,
			float x,
			float y,
			float width,
			float height,
			float radius,
			int color) {
		shape(graphics, ClientPipelines.SQUIRCLE, x, y, width, height, radius, FILLED,
				color, color, color, color);
	}

	/** Градиент сверху вниз. */
	public static void gradientVertical(
			GuiGraphicsExtractor graphics,
			float x,
			float y,
			float width,
			float height,
			int top,
			int bottom) {
		gradient(graphics, x, y, width, height, top, top, bottom, bottom);
	}

	/** Градиент слева направо. */
	public static void gradientHorizontal(
			GuiGraphicsExtractor graphics,
			float x,
			float y,
			float width,
			float height,
			int left,
			int right) {
		gradient(graphics, x, y, width, height, left, right, right, left);
	}

	/** Свой цвет в каждом углу, по часовой стрелке от левого верхнего. */
	public static void gradient(
			GuiGraphicsExtractor graphics,
			float x,
			float y,
			float width,
			float height,
			int topLeft,
			int topRight,
			int bottomRight,
			int bottomLeft) {
		shape(graphics, ClientPipelines.ROUND, x, y, width, height, SHARP, FILLED,
				topLeft, topRight, bottomRight, bottomLeft);
	}

	/** Градиент со скруглёнными углами. */
	public static void roundedGradient(
			GuiGraphicsExtractor graphics,
			float x,
			float y,
			float width,
			float height,
			float radius,
			int topLeft,
			int topRight,
			int bottomRight,
			int bottomLeft) {
		shape(graphics, ClientPipelines.ROUND, x, y, width, height, radius, FILLED,
				topLeft, topRight, bottomRight, bottomLeft);
	}

	/** Обводка прямоугольника без скругления. */
	public static void outline(
			GuiGraphicsExtractor graphics,
			float x,
			float y,
			float width,
			float height,
			float thickness,
			int color) {
		roundedOutline(graphics, x, y, width, height, SHARP, thickness, color);
	}

	/** Обводка со скруглёнными углами. Рисуется одним элементом, а не четырьмя полосками. */
	public static void roundedOutline(
			GuiGraphicsExtractor graphics,
			float x,
			float y,
			float width,
			float height,
			float radius,
			float thickness,
			int color) {
		shape(graphics, ClientPipelines.ROUND, x, y, width, height, radius, thickness,
				color, color, color, color);
	}

	/**
	 * Заливка с обводкой одной командой: подложка ужимается на толщину обводки.
	 *
	 * <p>Если рисовать подложку во всю фигуру, а обводку поверх, то на полупрозрачных
	 * пикселях края фон смешивается дважды: сначала с подложкой, потом с обводкой.
	 * На прямых сторонах это незаметно — там пиксели попадают ровно на сетку и покрытие
	 * равно нулю или единице. А на дуге покрытие дробное почти везде, и лишнее смешение
	 * читается как выеденные куски обводки.</p>
	 *
	 * <p>Ужатая подложка ставит край ровно под внутренним краем обводки, поэтому наружная
	 * кромка смешивается с фоном ровно один раз, а внутренняя — обводка с подложкой.</p>
	 */
	public static void roundedRectOutlined(
			GuiGraphicsExtractor graphics,
			float x,
			float y,
			float width,
			float height,
			float radius,
			float thickness,
			int fill,
			int outline) {
		// Подложка заходит под обводку на половину её толщины. Если ужать её ровно на
		// толщину, то внутренний край обводки и край подложки совпадут, и в этой полосе
		// обе фигуры полупрозрачны — между ними пробивается фон.
		float inset = Math.min(thickness * 0.5F, Math.min(width, height) * 0.5F);

		roundedRect(graphics, x + inset, y + inset, width - inset * 2.0F, height - inset * 2.0F,
				Math.max(radius - inset, 0.0F), fill);
		roundedOutline(graphics, x, y, width, height, radius, thickness, outline);
	}

	/**
	 * Заливка с обводкой для сглаженного угла. Подложка ужимается так же, как в
	 * {@link #roundedRectOutlined}: на половину толщины обводки, чтобы между двумя фигурами
	 * не пробивался фон.
	 */
	public static void squircleRectOutlined(
			GuiGraphicsExtractor graphics,
			float x,
			float y,
			float width,
			float height,
			float radius,
			float thickness,
			int fill,
			int outline) {
		float inset = Math.min(thickness * 0.5F, Math.min(width, height) * 0.5F);

		squircle(graphics, x + inset, y + inset, width - inset * 2.0F, height - inset * 2.0F,
				Math.max(radius - inset, 0.0F), fill);
		squircleOutline(graphics, x, y, width, height, radius, thickness, outline);
	}

	/** Обводка вдоль сглаженного угла. */
	public static void squircleOutline(
			GuiGraphicsExtractor graphics,
			float x,
			float y,
			float width,
			float height,
			float radius,
			float thickness,
			int color) {
		shape(graphics, ClientPipelines.SQUIRCLE, x, y, width, height, radius, thickness,
				color, color, color, color);
	}

	/** Ширина строки в пикселях интерфейса. */
	public static float textWidth(String text, float size) {
		return Fonts.bold().width(text, size);
	}

	/** Высота строки в пикселях интерфейса. */
	public static float textHeight(float size) {
		return Fonts.bold().lineHeight(size);
	}

	/**
	 * Текст от левого верхнего угла.
	 *
	 * <p>Координата Y — это верх строки, а не базовая линия. Так удобнее стыковать текст
	 * с прямоугольниками, у которых точка отсчёта тоже сверху.</p>
	 */
	public static void text(
			GuiGraphicsExtractor graphics,
			String text,
			float x,
			float y,
			float size,
			int color) {
		FontAtlas atlas = Fonts.bold();

		submit(graphics, new TextRenderState(
				atlas,
				text,
				new Matrix3x2f(graphics.pose()),
				x,
				y + atlas.ascender(size),
				size,
				color,
				(ScreenRectangle) null));
	}

	/** Текст с мягкой тенью на пиксель вниз и вправо. */
	public static void textShadow(
			GuiGraphicsExtractor graphics,
			String text,
			float x,
			float y,
			float size,
			int color) {
		text(graphics, text, x + SHADOW_OFFSET, y + SHADOW_OFFSET, size, SHADOW_COLOR);
		text(graphics, text, x, y, size, color);
	}

	/** Текст, выровненный по центру относительно заданной точки. */
	public static void textCentered(
			GuiGraphicsExtractor graphics,
			String text,
			float centerX,
			float y,
			float size,
			int color) {
		text(graphics, text, centerX - textWidth(text, size) * 0.5F, y, size, color);
	}

	/**
	 * Текст, выровненный по середине полосы по высоте.
	 *
	 * <p>Центрируется не коробка строки, а сами буквы между верхом и базовой линией:
	 * иначе строка всегда кажется смещённой вверх из-за места под хвосты букв.</p>
	 */
	public static void textInRow(
			GuiGraphicsExtractor graphics,
			String text,
			float x,
			float rowY,
			float rowHeight,
			float size,
			int color) {
		FontAtlas atlas = Fonts.bold();
		float visible = atlas.ascender(size);

		submit(graphics, new TextRenderState(
				atlas,
				text,
				new Matrix3x2f(graphics.pose()),
				x,
				rowY + (rowHeight + visible) * 0.5F,
				size,
				color,
				(ScreenRectangle) null));
	}

	/**
	 * Тень под фигурой со скруглёнными углами.
	 *
	 * <p>Рисуется до самой фигуры и в тех же координатах, что и она: смещение и раздувание
	 * задаются отдельными числами, а не ручным подбором прямоугольника.</p>
	 *
	 * @param blur    радиус размытия в пикселях интерфейса
	 * @param spread  насколько тень раздута относительно фигуры; отрицательное её ужимает
	 * @param offsetX смещение тени вправо
	 * @param offsetY смещение тени вниз
	 */
	public static void shadow(
			GuiGraphicsExtractor graphics,
			float x,
			float y,
			float width,
			float height,
			float radius,
			float blur,
			float spread,
			float offsetX,
			float offsetY,
			int color) {
		shadowShape(graphics, ClientPipelines.SHADOW_ROUND, x, y, width, height, radius,
				blur, spread, offsetX, offsetY, color);
	}

	/** Тень под фигурой со сглаженным углом. */
	public static void squircleShadow(
			GuiGraphicsExtractor graphics,
			float x,
			float y,
			float width,
			float height,
			float radius,
			float blur,
			float spread,
			float offsetX,
			float offsetY,
			int color) {
		shadowShape(graphics, ClientPipelines.SHADOW_SQUIRCLE, x, y, width, height, radius,
				blur, spread, offsetX, offsetY, color);
	}

	private static void shadowShape(
			GuiGraphicsExtractor graphics,
			RenderPipeline pipeline,
			float x,
			float y,
			float width,
			float height,
			float radius,
			float blur,
			float spread,
			float offsetX,
			float offsetY,
			int color) {
		// Совсем пустая фигура тени не даёт: рисовать нечего, а шейдеру пришлось бы
		// делить на ноль при поиске размера пикселя.
		if (width <= 0.0F || height <= 0.0F) {
			return;
		}

		float centerX = x + width * 0.5F + offsetX;
		float centerY = y + height * 0.5F + offsetY;

		// Раздувание меняет и радиус: иначе раздутая тень окажется угловатее фигуры,
		// а ужатая — круглее, и край тени перестанет идти вдоль края панели.
		float halfWidth = Math.max(width * 0.5F + spread, 0.0F);
		float halfHeight = Math.max(height * 0.5F + spread, 0.0F);
		float shadowRadius = Math.max(radius + spread, 0.0F);

		if (halfWidth <= 0.0F || halfHeight <= 0.0F) {
			return;
		}

		// Геометрия шире самой тени на радиус размытия и ещё на пиксель запаса.
		// Без этого размытый хвост обрежется по краю прямоугольника и вокруг панели
		// появится чёткая рамка — самый частый баг теней.
		float margin = Math.max(blur, 0.0F) + 1.0F;

		submit(graphics, new ShadowRenderState(
				pipeline,
				new Matrix3x2f(graphics.pose()),
				centerX - halfWidth - margin,
				centerY - halfHeight - margin,
				centerX + halfWidth + margin,
				centerY + halfHeight + margin,
				centerX,
				centerY,
				halfWidth,
				halfHeight,
				shadowRadius,
				Math.max(blur, 0.0F),
				color,
				(ScreenRectangle) null));
	}

	private static void shape(
			GuiGraphicsExtractor graphics,
			RenderPipeline pipeline,
			float x,
			float y,
			float width,
			float height,
			float radius,
			float thickness,
			int topLeft,
			int topRight,
			int bottomRight,
			int bottomLeft) {
		submit(graphics, new RectRenderState(
				pipeline,
				new Matrix3x2f(graphics.pose()),
				x,
				y,
				x + width,
				y + height,
				radius,
				thickness,
				topLeft,
				topRight,
				bottomRight,
				bottomLeft,
				(ScreenRectangle) null));
	}

	private static void submit(GuiGraphicsExtractor graphics, GuiElementRenderState state) {
		((GuiGraphicsExtractorAccessor) (Object) graphics).lucid$guiRenderState().addGuiElement(state);
	}
}
