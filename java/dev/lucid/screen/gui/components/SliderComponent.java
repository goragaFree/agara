package dev.lucid.screen.gui.components;

import dev.lucid.setting.SliderSetting;
import dev.lucid.util.render.Render2D;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

/** Ползунок: имя и число сверху, дорожка снизу. */
public final class SliderComponent extends SettingComponent {

	private final SliderSetting setting;

	/** Границы дорожки на момент захвата — пока тащат, раскладка может уехать. */
	private float trackLeft;
	private float trackWidth;

	public SliderComponent(SliderSetting setting) {
		this.setting = setting;
	}

	@Override
	public float height() {
		return SLIDER_HEIGHT;
	}

	@Override
	public void draw(GuiGraphicsExtractor graphics, float x, float y, float width) {
		float labelHeight = SLIDER_HEIGHT - SLIDER_KNOB_HEIGHT - CARD_PADDING;

		Render2D.textInRow(graphics, this.setting.getName(), x + SETTING_INDENT, y, labelHeight, FONT_SIZE,
				COLOR_SETTING_TEXT);

		String value = this.setting.getDisplayValue();
		float valueX = x + width - TEXT_PADDING - Render2D.textWidth(value, FONT_SIZE);

		Render2D.textInRow(graphics, value, valueX, y, labelHeight, FONT_SIZE, COLOR_SETTING_VALUE);

		float trackLeft = x + SETTING_INDENT;
		float trackWidth = width - SETTING_INDENT - TEXT_PADDING;
		float trackY = y + labelHeight + (SLIDER_KNOB_HEIGHT - SLIDER_TRACK_HEIGHT) * 0.5F;
		float radius = SLIDER_TRACK_HEIGHT * 0.5F;
		float fraction = (float) this.setting.getFraction();

		Render2D.roundedRect(graphics, trackLeft, trackY, trackWidth, SLIDER_TRACK_HEIGHT, radius,
				COLOR_OFF);

		if (fraction > 0.0F) {
			Render2D.roundedRect(graphics, trackLeft, trackY, trackWidth * fraction,
					SLIDER_TRACK_HEIGHT, radius, COLOR_ACCENT);
		}

		// Кружок держим внутри дорожки, иначе на краях он наполовину висит в воздухе.
		float knobX = trackLeft + (trackWidth - SLIDER_KNOB_WIDTH) * fraction;

		Render2D.roundedRect(graphics, knobX, y + labelHeight, SLIDER_KNOB_WIDTH, SLIDER_KNOB_HEIGHT,
				SLIDER_KNOB_WIDTH * 0.5F, COLOR_KNOB);
	}

	@Override
	public boolean click(MouseButtonEvent event, float x, float y, float width, int button) {
		if (button != MOUSE_LEFT) {
			return false;
		}

		this.trackLeft = x + SETTING_INDENT;
		this.trackWidth = width - SETTING_INDENT - TEXT_PADDING;
		this.drag(event.x());
		return true;
	}

	@Override
	public void drag(double mouseX) {
		double fraction = (mouseX - this.trackLeft) / this.trackWidth;

		this.setting.setFraction(Math.max(0.0, Math.min(1.0, fraction)));
	}
}
