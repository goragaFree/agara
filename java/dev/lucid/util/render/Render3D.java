package dev.lucid.util.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import dev.lucid.Lucid;

/**
 * Единая точка отрисовки в мире: коробки и их контуры в координатах блоков.
 *
 * <p>Пара к {@link Render2D}. Разделены они не по вкусу, а потому что это две разные
 * машины. Интерфейс складывается в {@code GuiRenderState} и рисуется игрой в конце кадра;
 * мир требует своих пайплайнов, своего буфера вершин и своего прохода отрисовки, а вызвать
 * его можно только внутри фазы отрисовки уровня. Общего у них ровно ничего, кроме слова
 * «рисовать».</p>
 *
 * <p>Кадр собирается в два шага, как и у самой игры. Сначала фаза сбора: модули
 * складывают сюда фигуры в мировых координатах. Потом фаза отрисовки: всё накопленное
 * уезжает в один буфер и рисуется двумя вызовами — сквозь стены и с проверкой глубины.
 * Модуль не знает ни про буферы, ни про проходы, ни про камеру.</p>
 *
 * <p>Подписка на события Fabric делается один раз в {@link #init()} на весь клиент.
 * Отписаться от них нельзя, поэтому подписываться из модулей нельзя тем более: включив
 * и выключив модуль десять раз, игрок получил бы десять подписок.</p>
 */
public final class Render3D {

	/** Заливка с проверкой глубины: фигура прячется за блоками, как обычная геометрия. */
	private static final RenderPipeline FILLED = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath(Lucid.MOD_ID, "pipeline/filled"))
					.build()
	);

	/**
	 * Та же заливка, но без работы с глубиной: видно сквозь стены.
	 *
	 * <p>Пустое состояние глубины отключает и проверку, и запись. Запись выключить важно
	 * не меньше: иначе фигура оставила бы в буфере глубины след, за которым пропадали бы
	 * блоки и частицы, нарисованные позже.</p>
	 */
	private static final RenderPipeline FILLED_THROUGH_WALLS = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath(Lucid.MOD_ID, "pipeline/filled_through_walls"))
					.withDepthStencilState(Optional.empty())
					.build()
	);

	private static final Vector4f COLOR_MODULATOR = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
	private static final Vector3f MODEL_OFFSET = new Vector3f();
	private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

	private static final StagedVertexBuffer STAGED_BUFFER =
			new StagedVertexBuffer(() -> "Lucid World Buffer", RenderType.SMALL_BUFFER_SIZE);

	/** Кто рисует в мире. Список фиксируется на ините клиента и дальше не меняется. */
	private static final List<Extractor> EXTRACTORS = new ArrayList<>();

	/**
	 * Фигуры текущего кадра, разложенные по проходам сразу при сборе.
	 *
	 * <p>Разложить потом дороже: пришлось бы либо второй раз обходить весь список, либо
	 * дважды переключать пайплайн на каждой фигуре и получить по проходу отрисовки на
	 * каждую коробку.</p>
	 */
	private static final List<Shape> THROUGH_WALLS = new ArrayList<>();
	private static final List<Shape> DEPTH_TESTED = new ArrayList<>();

	/**
	 * Восемь углов повёрнутой коробки. Один массив на весь клиент: отрисовка идёт в один
	 * поток и по одной фигуре за раз, а иначе на каждый меч приходилось бы по восемь
	 * короткоживущих векторов в кадре.
	 */
	private static final Vector3f[] CORNERS = new Vector3f[8];

	static {
		for (int index = 0; index < CORNERS.length; index++) {
			CORNERS[index] = new Vector3f();
		}
	}

	private static final Frame FRAME = new Frame();

	private static boolean initialized;

	private Render3D() {
	}

	/** Что-то, что рисует в мире: модуль или его часть. */
	public interface Extractor {

		/**
		 * Сложить фигуры кадра. Вызывается каждый кадр, в том числе когда модуль выключен,
		 * поэтому проверка состояния — на стороне модуля.
		 */
		void extract(Frame frame);
	}

	/**
	 * Подписывает клиент на отрисовку мира. Вызывается один раз на ините, до первого кадра.
	 */
	public static void init() {
		if (initialized) {
			return;
		}

		initialized = true;

		LevelExtractionEvents.END_EXTRACTION.register(Render3D::extract);
		LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(Render3D::renderAndDraw);
	}

	/**
	 * Добавляет рисующего. Порядок вызова совпадает с порядком регистрации, поэтому
	 * при наложении полупрозрачных фигур поверх окажется зарегистрированный позже.
	 */
	public static void register(Extractor extractor) {
		EXTRACTORS.add(extractor);
	}

	/** Освобождает буфер вершин. Зовётся при закрытии игры. */
	public static void close() {
		STAGED_BUFFER.close();
	}

	// ------------------------------------------------------------------ сбор кадра

	private static void extract(LevelExtractionContext context) {
		THROUGH_WALLS.clear();
		DEPTH_TESTED.clear();

		if (Minecraft.getInstance().level == null) {
			return;
		}

		for (Extractor extractor : EXTRACTORS) {
			try {
				extractor.extract(FRAME);
			} catch (Throwable t) {
				// Сломанный модуль не должен уносить с собой кадр целиком.
				Lucid.LOGGER.error("World renderer threw during extraction", t);
			}
		}
	}

	/**
	 * Приёмник фигур на время сбора кадра.
	 *
	 * <p>Координаты — мировые и в блоках, ровно те, что у блока или сущности. Про камеру,
	 * пайплайны и формат вершин модулю знать не нужно, поэтому наружу этого нет.</p>
	 *
	 * <p>Цвет — обычный ARGB, как и во всём интерфейсе: одна запись цвета на весь клиент
	 * дешевле, чем помнить, где четыре float, а где одно число.</p>
	 */
	public static final class Frame {

		private Frame() {
		}

		/** Залитая коробка по границам блока, раздутая на {@code grow} во все стороны. */
		public void filledBox(BlockPos pos, double grow, int color, boolean throughWalls) {
			this.filledBox(
					pos.getX() - grow, pos.getY() - grow, pos.getZ() - grow,
					pos.getX() + 1.0 + grow, pos.getY() + 1.0 + grow, pos.getZ() + 1.0 + grow,
					color, throughWalls);
		}

		/** Залитая коробка по двум углам. */
		public void filledBox(
				double minX,
				double minY,
				double minZ,
				double maxX,
				double maxY,
				double maxZ,
				int color,
				boolean throughWalls) {
			if ((color >>> 24) == 0) {
				return;
			}

			list(throughWalls).add(new Box(minX, minY, minZ, maxX, maxY, maxZ, color, throughWalls));
		}

		/** Контур вокруг границ блока: двенадцать рёбер заданной толщины. */
		public void boxOutline(BlockPos pos, double grow, double thickness, int color, boolean throughWalls) {
			this.boxOutline(
					pos.getX() - grow, pos.getY() - grow, pos.getZ() - grow,
					pos.getX() + 1.0 + grow, pos.getY() + 1.0 + grow, pos.getZ() + 1.0 + grow,
					thickness, color, throughWalls);
		}

		/**
		 * Контур коробки.
		 *
		 * <p>Рёбра — тонкие коробки, а не линии. Линия шириной больше пикселя рисуется
		 * по-разному у разных драйверов, толщина у неё экранная, поэтому вблизи она остаётся
		 * ниткой, а издалека закрывает блок целиком. У коробок толщина в блоках, и контур
		 * ведёт себя как часть мира.</p>
		 *
		 * <p>Рёбра не пересекаются: вдоль X ребро идёт во всю длину, а вдоль Y и Z ребро
		 * начинается там, где кончается угол. Иначе на восьми углах фигуры лежали бы по три
		 * коробки друг на друге, и на полупрозрачном контуре углы вышли бы втрое темнее.</p>
		 */
		public void boxOutline(
				double minX,
				double minY,
				double minZ,
				double maxX,
				double maxY,
				double maxZ,
				double thickness,
				int color,
				boolean throughWalls) {
			if ((color >>> 24) == 0 || thickness <= 0.0) {
				return;
			}

			// Половина толщины по обе стороны от ребра: контур сидит на границе фигуры,
			// а не внутри или снаружи неё.
			double half = thickness * 0.5;

			// Слишком толстый контур на маленькой фигуре превратился бы в заливку
			// с вывернутыми рёбрами.
			double limit = Math.min(maxX - minX, Math.min(maxY - minY, maxZ - minZ)) * 0.5;
			half = Math.min(half, Math.max(limit, 0.0));

			double[] xs = { minX, maxX };
			double[] ys = { minY, maxY };
			double[] zs = { minZ, maxZ };

			for (double y : ys) {
				for (double z : zs) {
					this.filledBox(minX - half, y - half, z - half,
							maxX + half, y + half, z + half, color, throughWalls);
				}
			}

			for (double x : xs) {
				for (double z : zs) {
					this.filledBox(x - half, minY + half, z - half,
							x + half, maxY - half, z + half, color, throughWalls);
				}
			}

			for (double x : xs) {
				for (double y : ys) {
					this.filledBox(x - half, y - half, minZ + half,
							x + half, y + half, maxZ - half, color, throughWalls);
				}
			}
		}

		/**
		 * Коробка, повёрнутая как угодно: задаётся центром, размерами по своим осям и
		 * поворотом. Нужна всему, что не лежит по сетке мира — оружию, лезвиям, стрелкам.
		 *
		 * <p>Размеры — полная длина стороны, а не половина: так их проще прикидывать
		 * от глаза, как размеры модели.</p>
		 */
		public void orientedBox(
				double centerX,
				double centerY,
				double centerZ,
				double sizeX,
				double sizeY,
				double sizeZ,
				Quaternionfc rotation,
				int color,
				boolean throughWalls) {
			if ((color >>> 24) == 0) {
				return;
			}

			// Поворот копируем: вызывающая сторона крутит один и тот же кватернион в цикле,
			// а фигура доживает до фазы отрисовки и должна помнить своё состояние.
			list(throughWalls).add(new OrientedBox(
					centerX, centerY, centerZ,
					(float) (sizeX * 0.5), (float) (sizeY * 0.5), (float) (sizeZ * 0.5),
					new Quaternionf(rotation), color, throughWalls));
		}

		private static List<Shape> list(boolean throughWalls) {
			return throughWalls ? THROUGH_WALLS : DEPTH_TESTED;
		}
	}

	// ------------------------------------------------------------------ отрисовка

	private static void renderAndDraw(LevelRenderContext context) {
		if (THROUGH_WALLS.isEmpty() && DEPTH_TESTED.isEmpty()) {
			return;
		}

		// Мировые координаты переводим в координаты относительно камеры здесь, а не
		// сдвигом матрицы. Матрица сдвигается уже во float, а координаты в мире бывают
		// в миллионах блоков: у float там шаг больше метра, и фигура начинает дрожать
		// и разъезжаться с блоком. Вычитание в double убирает разницу до отправки на GPU.
		Vec3 camera = context.levelState().cameraRenderState.pos;

		// Порядок важен: сначала то, что прячется за геометрией, потом то, что рисуется
		// поверх всего. Внутри одного буфера порядок отрисовки задаётся только этим.
		StagedVertexBuffer.Draw depthTested = append(FILLED, DEPTH_TESTED, camera, context);
		StagedVertexBuffer.Draw throughWalls = append(FILLED_THROUGH_WALLS, THROUGH_WALLS, camera, context);

		if (depthTested == null && throughWalls == null) {
			return;
		}

		// Одна выгрузка на кадр, а не на фигуру: обращение к GPU дороже самой геометрии.
		STAGED_BUFFER.upload();

		draw(depthTested, FILLED);
		draw(throughWalls, FILLED_THROUGH_WALLS);

		STAGED_BUFFER.endFrame();
	}

	/** Складывает фигуры одного прохода в буфер. Возвращает null, если складывать нечего. */
	private static StagedVertexBuffer.Draw append(
			RenderPipeline pipeline,
			List<Shape> shapes,
			Vec3 camera,
			LevelRenderContext context) {
		if (shapes.isEmpty()) {
			return null;
		}

		VertexFormat format = pipeline.getVertexFormatBinding(0);

		if (format == null) {
			return null;
		}

		PrimitiveTopology primitive = pipeline.getPrimitiveTopology();

		// Сортировка нужна только четырёхугольникам: полупрозрачные грани должны лечь
		// от дальней к ближней, иначе смешивание даст неверный цвет.
		StagedVertexBuffer.Draw draw = STAGED_BUFFER.appendDraw(
				format,
				primitive,
				primitive == PrimitiveTopology.QUADS ? RenderSystem.getProjectionType().vertexSorting() : null
		);

		Matrix4fc pose = context.poseStack().last().pose();
		VertexConsumer buffer = STAGED_BUFFER.getVertexBuilder(draw);

		for (Shape shape : shapes) {
			shape.emit(pose, buffer, camera);
		}

		return draw;
	}

	private static void draw(StagedVertexBuffer.Draw draw, RenderPipeline pipeline) {
		if (draw == null) {
			return;
		}

		StagedVertexBuffer.ExecuteInfo info = STAGED_BUFFER.getExecuteInfo(draw);

		if (info == null) {
			return;
		}

		Minecraft client = Minecraft.getInstance();

		GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
				.writeTransform(RenderSystem.getModelViewMatrixCopy(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

		RenderTarget mainTarget = client.gameRenderer.mainRenderTarget();
		GpuTextureView colorTexture = mainTarget.getColorTextureView();

		if (colorTexture == null) {
			return;
		}

		try (RenderPass renderPass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(() -> Lucid.MOD_ID + " world rendering",
						colorTexture, Optional.empty(),
						mainTarget.getDepthTextureView(), OptionalDouble.empty())) {

			renderPass.setPipeline(pipeline);

			RenderSystem.bindDefaultUniforms(renderPass);
			renderPass.setUniform("DynamicTransforms", dynamicTransforms);

			renderPass.setVertexBuffer(0, info.vertexBuffer().slice());
			renderPass.setIndexBuffer(info.indexBuffer(), info.indexType());
			renderPass.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0);
		}
	}

	/**
	 * Одна фигура кадра. Собирается в фазе сбора, а вершины отдаёт в фазе отрисовки —
	 * поэтому фигура умеет ровно одно: положить себя в буфер.
	 */
	private interface Shape {

		void emit(Matrix4fc pose, VertexConsumer buffer, Vec3 camera);
	}

	/**
	 * Одна коробка кадра.
	 *
	 * <p>Запись, а не поля класса: состояние кадра собирается в одной фазе, а читается
	 * в другой, и между ними его нельзя менять.</p>
	 */
	private record Box(
			double minX,
			double minY,
			double minZ,
			double maxX,
			double maxY,
			double maxZ,
			int color,
			boolean throughWalls) implements Shape {

		@Override
		public void emit(Matrix4fc pose, VertexConsumer buffer, Vec3 camera) {
			float x0 = (float) (this.minX - camera.x);
			float y0 = (float) (this.minY - camera.y);
			float z0 = (float) (this.minZ - camera.z);
			float x1 = (float) (this.maxX - camera.x);
			float y1 = (float) (this.maxY - camera.y);
			float z1 = (float) (this.maxZ - camera.z);

			float a = (this.color >>> 24) / 255.0F;
			float r = ((this.color >> 16) & 0xFF) / 255.0F;
			float g = ((this.color >> 8) & 0xFF) / 255.0F;
			float b = (this.color & 0xFF) / 255.0F;

			// Юг
			buffer.addVertex(pose, x0, y0, z1).setColor(r, g, b, a);
			buffer.addVertex(pose, x1, y0, z1).setColor(r, g, b, a);
			buffer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a);
			buffer.addVertex(pose, x0, y1, z1).setColor(r, g, b, a);
			// Север
			buffer.addVertex(pose, x1, y0, z0).setColor(r, g, b, a);
			buffer.addVertex(pose, x0, y0, z0).setColor(r, g, b, a);
			buffer.addVertex(pose, x0, y1, z0).setColor(r, g, b, a);
			buffer.addVertex(pose, x1, y1, z0).setColor(r, g, b, a);
			// Запад
			buffer.addVertex(pose, x0, y0, z0).setColor(r, g, b, a);
			buffer.addVertex(pose, x0, y0, z1).setColor(r, g, b, a);
			buffer.addVertex(pose, x0, y1, z1).setColor(r, g, b, a);
			buffer.addVertex(pose, x0, y1, z0).setColor(r, g, b, a);
			// Восток
			buffer.addVertex(pose, x1, y0, z1).setColor(r, g, b, a);
			buffer.addVertex(pose, x1, y0, z0).setColor(r, g, b, a);
			buffer.addVertex(pose, x1, y1, z0).setColor(r, g, b, a);
			buffer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a);
			// Верх
			buffer.addVertex(pose, x0, y1, z1).setColor(r, g, b, a);
			buffer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a);
			buffer.addVertex(pose, x1, y1, z0).setColor(r, g, b, a);
			buffer.addVertex(pose, x0, y1, z0).setColor(r, g, b, a);
			// Низ
			buffer.addVertex(pose, x0, y0, z0).setColor(r, g, b, a);
			buffer.addVertex(pose, x1, y0, z0).setColor(r, g, b, a);
			buffer.addVertex(pose, x1, y0, z1).setColor(r, g, b, a);
			buffer.addVertex(pose, x0, y0, z1).setColor(r, g, b, a);
		}
	}

	/**
	 * Коробка с произвольным поворотом: центр в мире, половинные размеры по своим осям
	 * и кватернион.
	 *
	 * <p>Поворот считается здесь, а не матрицей стека: в мировом проходе у нас одна
	 * матрица на весь буфер, а фигур с разными поворотами в кадре могут быть десятки:
	 * три меча по пять частей дают пятнадцать разных поворотов на одно убийство. Сдвиг до
	 * камеры, как и у {@link Box}, остаётся в double — иначе вдали от нуля всё дрожит.</p>
	 */
	private record OrientedBox(
			double centerX,
			double centerY,
			double centerZ,
			float halfX,
			float halfY,
			float halfZ,
			Quaternionf rotation,
			int color,
			boolean throughWalls) implements Shape {

		@Override
		public void emit(Matrix4fc pose, VertexConsumer buffer, Vec3 camera) {
			float offsetX = (float) (this.centerX - camera.x);
			float offsetY = (float) (this.centerY - camera.y);
			float offsetZ = (float) (this.centerZ - camera.z);

			// Углы считаем раз и кладём в общий массив: каждый из них используется трижды,
			// по три примыкающие к нему грани, и поворачивать его три раза незачем.
			for (int index = 0; index < 8; index++) {
				float localX = (index & 4) == 0 ? -this.halfX : this.halfX;
				float localY = (index & 2) == 0 ? -this.halfY : this.halfY;
				float localZ = (index & 1) == 0 ? -this.halfZ : this.halfZ;

				Vector3f corner = CORNERS[index].set(localX, localY, localZ);
				this.rotation.transform(corner);
				corner.add(offsetX, offsetY, offsetZ);
			}

			float a = (this.color >>> 24) / 255.0F;
			float r = ((this.color >> 16) & 0xFF) / 255.0F;
			float g = ((this.color >> 8) & 0xFF) / 255.0F;
			float b = (this.color & 0xFF) / 255.0F;

			// Порядок вершин — как у {@link Box}, чтобы обе фигуры вели себя одинаково
			// при отсечении граней.
			// Юг
			quad(pose, buffer, 1, 5, 7, 3, r, g, b, a);
			// Север
			quad(pose, buffer, 4, 0, 2, 6, r, g, b, a);
			// Запад
			quad(pose, buffer, 0, 1, 3, 2, r, g, b, a);
			// Восток
			quad(pose, buffer, 5, 4, 6, 7, r, g, b, a);
			// Верх
			quad(pose, buffer, 3, 7, 6, 2, r, g, b, a);
			// Низ
			quad(pose, buffer, 0, 4, 5, 1, r, g, b, a);
		}

		/** Индекс угла — три бита: 4 — плюс по X, 2 — по Y, 1 — по Z. */
		private static void quad(
				Matrix4fc pose,
				VertexConsumer buffer,
				int first,
				int second,
				int third,
				int fourth,
				float r,
				float g,
				float b,
				float a) {
			vertex(pose, buffer, first, r, g, b, a);
			vertex(pose, buffer, second, r, g, b, a);
			vertex(pose, buffer, third, r, g, b, a);
			vertex(pose, buffer, fourth, r, g, b, a);
		}

		private static void vertex(
				Matrix4fc pose,
				VertexConsumer buffer,
				int corner,
				float r,
				float g,
				float b,
				float a) {
			Vector3f point = CORNERS[corner];
			buffer.addVertex(pose, point.x, point.y, point.z).setColor(r, g, b, a);
		}
	}
}
