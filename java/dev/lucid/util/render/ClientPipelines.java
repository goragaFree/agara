package dev.lucid.util.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import dev.lucid.Lucid;

/**
 * Свои пайплайны отрисовки интерфейса.
 *
 * <p>Форма считается в шейдере формулой, а не набором треугольников. Поэтому любой
 * прямоугольник — всегда четыре вершины, сколь бы гладким ни было скругление.</p>
 *
 * <p>Форма задана через {@code SHAPE} — это константа времени сборки шейдера,
 * а не переменная. За счёт этого круглый вариант обходится без возведения в степень.
 * Плата за это — два пайплайна вместо одного, то есть круглые и squircle-элементы рисуются
 * разными пачками.</p>
 */
public final class ClientPipelines {

	/**
	 * Формат вершины.
	 *
	 * <p>Кроме позиции и цвета везём три вещи: координату внутри фигуры, её половинный
	 * размер и пару «радиус плюс толщина обводки». Без размера шейдер не знает, где
	 * у фигуры край, а значит не может посчитать расстояние до него.</p>
	 *
	 * <p>Имена атрибутов не произвольные: через них игра понимает, куда класть то, что
	 * записывают {@code setColor}, {@code setUv} и соседние методы.</p>
	 */
	public static final VertexFormat SHAPE_FORMAT = VertexFormat.builder(0)
			.addAttribute("Position", GpuFormat.RGB32_FLOAT)
			.addAttribute("Color", GpuFormat.RGBA8_UNORM)
			.addAttribute("UV0", GpuFormat.RG32_FLOAT)
			.addAttribute("UV1", GpuFormat.RG16_SINT)
			.addAttribute("UV2", GpuFormat.RG16_SINT)
			.build();

	/**
	 * Формат вершины для текста.
	 *
	 * <p>Здесь вместо размеров фигуры едет место буквы в атласе и ширина полосы
	 * расстояний. Последняя зависит от того, как пекли атлас, поэтому передаётся
	 * данными, а не вбита в шейдер числом: иначе смена шрифта тихо ломала бы сглаживание.</p>
	 */
	public static final VertexFormat TEXT_FORMAT = VertexFormat.builder(0)
			.addAttribute("Position", GpuFormat.RGB32_FLOAT)
			.addAttribute("Color", GpuFormat.RGBA8_UNORM)
			.addAttribute("UV0", GpuFormat.RG32_FLOAT)
			.addAttribute("UV1", GpuFormat.RG16_SINT)
			.build();

	/** Обычное круглое скругление. Радиус ноль даёт обычный прямоугольник. */
	public static final RenderPipeline ROUND = create("round", 2);

	/**
	 * Сглаженный угол: переход в сторону тянется дальше, чем у дуги.
	 *
	 * <p>Показатель пять, а не четыре: именно около пяти лежит непрерывный угол Apple, где
	 * кривизна нарастает плавно, а не вскакивает в месте стыка дуги со стороной. При том же
	 * радиусе фигура выглядит полнее, поэтому радиусы в интерфейсе заданы чуть больше.</p>
	 */
	public static final RenderPipeline SQUIRCLE = create("squircle", 5);

	/**
	 * Тень под круглым скруглением.
	 *
	 * <p>Вершинная программа та же, что и у фигур: тени нужны ровно те же атрибуты.
	 * Отличается только пиксельная часть — вместо резкого края широкая полоса затухания.</p>
	 */
	public static final RenderPipeline SHADOW_ROUND = create("shadow_round", "shadow", 2);

	/** Тень под сглаженным углом. */
	public static final RenderPipeline SHADOW_SQUIRCLE = create("shadow_squircle", "shadow", 5);

	/**
	 * Текст по полю расстояний.
	 *
	 * <p>Единственный пайплайн клиента с текстурой, поэтому только здесь добавлена
	 * группа привязок с сэмплером. Порядок групп имеет значение, поэтому сэмплер идёт
	 * последним — ровно как в ванильной текстурной заготовке интерфейса.</p>
	 *
	 * <p>Остальное берётся из той же заготовки, что и у фигур, чтобы смешивание и работа
	 * с глубиной совпадали с остальным интерфейсом.</p>
	 */
	public static final RenderPipeline TEXT = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath(Lucid.MOD_ID, "pipeline/text"))
					.withVertexShader(Identifier.fromNamespaceAndPath(Lucid.MOD_ID, "core/text"))
					.withFragmentShader(Identifier.fromNamespaceAndPath(Lucid.MOD_ID, "core/text"))
					.withBindGroupLayout(BindGroupLayouts.SAMPLER0)
					.withVertexBinding(0, TEXT_FORMAT)
					.build());

	private ClientPipelines() {
	}

	/**
	 * Прикосновение к классу, чтобы пайплайны зарегистрировались заранее.
	 *
	 * <p>Поля создаются при первом обращении к классу. Если оставить это на случай,
	 * порядок регистрации будет плавать от запуска к запуску.</p>
	 */
	public static void bootstrap() {
	}

	private static RenderPipeline create(String name, int shape) {
		return create(name, "rect", shape);
	}

	private static RenderPipeline create(String name, String fragment, int shape) {
		return RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
				.withLocation(Identifier.fromNamespaceAndPath(Lucid.MOD_ID, "pipeline/" + name))
				.withVertexShader(Identifier.fromNamespaceAndPath(Lucid.MOD_ID, "core/rect"))
				.withFragmentShader(Identifier.fromNamespaceAndPath(Lucid.MOD_ID, "core/" + fragment))
				.withVertexBinding(0, SHAPE_FORMAT)
				.withShaderDefine("SHAPE", shape)
				.build());
	}
}
