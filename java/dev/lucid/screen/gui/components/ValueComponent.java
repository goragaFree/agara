package dev.lucid.screen.gui.components;

import dev.lucid.setting.Setting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Запасной виджет: имя и текст значения, без клика.
 *
 * <p>Сюда попадает тип настройки, для которого ещё нет своего компонента.
 * Экран из-за этого не обязан знать все классы настроек.</p>
 */
public final class ValueComponent extends SettingComponent {

	private final Setting<?> setting;

	public ValueComponent(Setting<?> setting) {
		this.setting = setting;
	}

	@Override
	public float height() {
		return HEIGHT;
	}

	@Override
	public void draw(GuiGraphicsExtractor graphics, float x, float y, float width) {
		drawNameValue(graphics, this.setting, x, y, width, HEIGHT);
	}
}
