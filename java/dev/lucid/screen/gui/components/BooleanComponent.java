package dev.lucid.screen.gui.components;

import dev.lucid.setting.BooleanSetting;
import dev.lucid.util.render.Render2D;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

/** Галочка: имя слева, квадратик справа. */
public final class BooleanComponent extends SettingComponent {

	private final BooleanSetting setting;

	public BooleanComponent(BooleanSetting setting) {
		this.setting = setting;
	}

	@Override
	public float height() {
		return HEIGHT;
	}

	@Override
	public void draw(GuiGraphicsExtractor graphics, float x, float y, float width) {
		Render2D.textInRow(graphics, this.setting.getName(), x + SETTING_INDENT, y, HEIGHT, FONT_SIZE,
				COLOR_SETTING_TEXT);
		drawBox(graphics, boxX(x, width), y + (HEIGHT - BOX_SIZE) * 0.5F, this.setting.get());
	}

	@Override
	public boolean click(MouseButtonEvent event, float x, float y, float width, int button) {
		this.setting.toggle();
		return false;
	}
}
