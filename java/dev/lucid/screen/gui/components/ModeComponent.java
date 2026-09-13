package dev.lucid.screen.gui.components;

import java.util.List;

import dev.lucid.setting.ModeSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * Как мультиселект, но выбран ровно один чип.
 *
 * <p>Клик по варианту ставит его текущим. Снять выбор нельзя — пустого режима не бывает.</p>
 */
public final class ModeComponent extends SettingComponent {

	private final ModeSetting setting;

	public ModeComponent(ModeSetting setting) {
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
				this.setting::is, x, y, width);
	}

	@Override
	public boolean click(MouseButtonEvent event, float x, float y, float width, int button) {
		String option = chipAt(
				this.setting.getOptions(),
				width,
				(float) event.x() - x,
				(float) event.y() - y);

		if (option != null) {
			this.setting.setValue(option);
		}

		return false;
	}
}
