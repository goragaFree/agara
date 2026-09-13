package dev.lucid.module.impl.render;

import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.SliderSetting;

/**
 * Показывает невидимых существ полупрозрачными.
 *
 * <p>Ваниль просто не вызывает отрисовку, если сущность невидима для тебя.
 * Модуль ничего не рисует сам: миксин просит полупрозрачный слой и подставляет
 * альфу из ползунка.</p>
 */
public class SeeInvisible extends Module {

	private static SeeInvisible instance;

	/** Ванильный «призрак» союзника — почти прозрачный белый. */
	private static final int VANILLA_GHOST = 0x26FFFFFF;

	private final SliderSetting alpha = this.addSetting(new SliderSetting(
			"alpha", "Прозрачность",
			"Насколько плотно виден невидимый: 0.1 — едва, 1 — как обычная модель",
			0.5, 0.1, 1.0, 0.05));

	public SeeInvisible() {
		super("see_invisible", "SeeInvisible", Category.RENDER,
				"Показывает невидимых полупрозрачными");
		instance = this;
	}

	public static boolean isActive() {
		return instance != null && instance.isEnabled();
	}

	/**
	 * Базовый цвет модели. {@code -1} — обычная непрозрачная отрисовка, её не трогаем.
	 * Иначе это путь «призрак»: подставляем альфу из настройки.
	 */
	public static int tintGhost(int vanillaBase) {
		if (!isActive() || vanillaBase == -1) {
			return vanillaBase;
		}

		int a = Math.round(instance.alpha.getAsFloat() * 255.0F);
		return (a << 24) | 0x00FFFFFF;
	}

	/** Ваниль уже собралась рисовать призрака этим цветом. */
	public static boolean isGhostColor(int color) {
		return color == VANILLA_GHOST;
	}
}
