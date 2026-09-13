package dev.lucid.util.render.font;

/**
 * Шрифты клиента.
 *
 * <p>Атлас читается один раз при первом обращении и дальше живёт всю сессию. Один атлас
 * обслуживает все размеры сразу, поэтому плодить копии под кегли не нужно.</p>
 */
public final class Fonts {

	private static FontAtlas bold;

	private Fonts() {
	}

	/** Основной шрифт интерфейса. */
	public static FontAtlas bold() {
		if (bold == null) {
			bold = FontAtlas.load("bold");
		}

		return bold;
	}
}
