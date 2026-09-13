package dev.lucid.screen.gui.components;

import java.util.List;

import dev.lucid.setting.MultiSelectSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * Название сверху, варианты — прямоугольники в ряд.
 *
 * <p>Можно включить сколько угодно чипов сразу. Выбранный непрозрачный,
 * выключенный бледнее.</p>
 */
public final class MultiSelectComponent extends SettingComponent {

	private final MultiSelectSetting setting;

	public MultiSelectComponent(MultiSelectSetting setting) {
		this.setting = setting;
	}

	@Override
	public float height() {
		return this.height(0.0F);
	}

	@Override
	public float height(float width) {
		return chipsHeight(this.setting.getOptions(), width);
	}

	@Override
	public List<String> options() {
		return List.of();
	}

	@Override
	public void draw(GuiGraphicsExtractor graphics, float x, float y, float width) {
		drawChipRow(graphics, this.setting.getName(), this.setting.getOptions(),
				this.setting::isSelected, x, y, width);
	}

	@Override
	public boolean click(MouseButtonEvent event, float x, float y, float width, int button) {
		String option = chipAt(
				this.setting.getOptions(),
				width,
				(float) event.x() - x,
				(float) event.y() - y);

		if (option != null) {
			this.setting.toggle(option);
		}

		return false;
	}
}
