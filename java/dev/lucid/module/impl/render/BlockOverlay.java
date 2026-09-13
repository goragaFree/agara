package dev.lucid.module.impl.render;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.BooleanSetting;
import dev.lucid.setting.ModeSetting;
import dev.lucid.setting.SliderSetting;
import dev.lucid.util.render.Render3D;

/**
 * Подсвечивает блок, на который смотрит игрок.
 *
 * <p>Сам рендер живёт в {@link Render3D}: модуль только говорит, какую фигуру и каким цветом
 * нарисовать, и ничего не знает ни про буферы вершин, ни про камеру, ни про проходы
 * отрисовки.</p>
 *
 * <p>Подписка делается один раз в {@link #onRegister()}, а вкл/выкл решается проверкой
 * {@code isEnabled()} внутри сбора кадра: события рендера отписать нельзя.</p>
 */
public class BlockOverlay extends Module {

	private static final String MODE_FILL = "Заливка";
	private static final String MODE_OUTLINE = "Контур";
	private static final String MODE_BOTH = "Оба";

	/** Цвет без прозрачности: альфа считается каждый кадр из настроек и пульсации. */
	private static final int RGB = 0x4DFF99;

	/** Контур всегда плотнее заливки, иначе он теряется на её фоне. */
	private static final float OUTLINE_ALPHA_BOOST = 2.2F;

	/**
	 * Запас вокруг блока в блоках.
	 *
	 * <p>Без него грани фигуры лежат ровно на гранях блока, и какая из них окажется ближе,
	 * решает ошибка округления глубины — подсветка начинает мерцать пятнами.</p>
	 */
	private static final double GROW = 0.002;

	/** Полный цикл пульсации в миллисекундах. */
	private static final long PULSE_PERIOD_MILLIS = 2000L;

	/** Глубина пульсации: доля прозрачности, которая дышит. Остальное горит ровно. */
	private static final float PULSE_DEPTH = 0.35F;

	private final ModeSetting mode = this.addSetting(new ModeSetting(
			"mode", "Режим", "Что рисовать: заливку, контур или и то и другое",
			MODE_BOTH, java.util.List.of(MODE_FILL, MODE_OUTLINE, MODE_BOTH)));

	private final SliderSetting opacity = this.addSetting(new SliderSetting(
			"opacity", "Прозрачность", "Плотность заливки в процентах",
			35.0, 5.0, 100.0, 5.0));

	private final SliderSetting thickness = this.addSetting(new SliderSetting(
			"thickness", "Толщина контура", "В сотых блока: контур живёт в мире, а не на экране",
			2.0, 0.5, 6.0, 0.5))
			.visibleWhen(() -> !this.mode.is(MODE_FILL));

	private final BooleanSetting throughWalls = this.addSetting(new BooleanSetting(
			"through_walls", "Сквозь стены", "Видно даже если блок закрыт другими", true));

	private final BooleanSetting pulse = this.addSetting(new BooleanSetting(
			"pulse", "Пульсация", "Плавное дыхание прозрачности", true));

	public BlockOverlay() {
		super("block_overlay", "BlockOverlay", Category.RENDER,
				"Подсвечивает блок под прицелом",
				InputConstants.KEY_B);
	}

	@Override
	public void onRegister() {
		Render3D.register(this::extract);
	}

	/**
	 * Собирает фигуры кадра.
	 *
	 * <p>Цель берётся из готового {@code hitResult} игры, а не своим поиском: иначе
	 * подсветка расходилась бы с тем, по чему игрок реально ударит, на границах дальности
	 * и на неполных блоках.</p>
	 */
	private void extract(Render3D.Frame frame) {
		if (!this.isEnabled()) {
			return;
		}

		Minecraft client = mc();

		if (client.level == null || client.player == null) {
			return;
		}

		HitResult hit = client.hitResult;

		if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
			return;
		}

		BlockPos pos = ((BlockHitResult) hit).getBlockPos();

		// Курсор может стоять на жидкости или на только что сломанном блоке:
		// подсветить пустоту выглядит как баг.
		if (client.level.getBlockState(pos).isAir()) {
			return;
		}

		boolean through = this.throughWalls.get();
		float alpha = this.alpha();

		if (!this.mode.is(MODE_OUTLINE)) {
			frame.filledBox(pos, GROW, color(alpha), through);
		}

		if (!this.mode.is(MODE_FILL)) {
			// Шаг слайдера — сотые блока: целые числа в GUI читаются лучше долей.
			double width = this.thickness.get() / 100.0;

			frame.boxOutline(pos, GROW, width,
					color(Math.min(alpha * OUTLINE_ALPHA_BOOST, 1.0F)), through);
		}
	}

	/** Текущая плотность заливки с учётом пульсации. */
	private float alpha() {
		float base = (float) (this.opacity.get() / 100.0);

		if (!this.pulse.get()) {
			return base;
		}

		// Колебание считается от системного времени, а не от тиков: при просадках TPS
		// тики идут рывками, и пульсация заикалась бы вместе с ними.
		float phase = (System.currentTimeMillis() % PULSE_PERIOD_MILLIS) / (float) PULSE_PERIOD_MILLIS;
		float wave = (float) Math.sin(phase * Math.PI * 2.0);

		// Множитель, а не слагаемое: иначе на малой прозрачности пульс уводил бы её в ноль,
		// а на полной — упирался в потолок и был бы не виден.
		return base * (1.0F - PULSE_DEPTH + PULSE_DEPTH * wave);
	}

	private static int color(float alpha) {
		int a = Math.round(Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F);
		return (a << 24) | RGB;
	}
}
