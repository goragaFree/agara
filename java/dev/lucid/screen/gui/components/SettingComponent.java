package dev.lucid.screen.gui.components;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import dev.lucid.setting.BooleanSetting;
import dev.lucid.setting.ModeSetting;
import dev.lucid.setting.MultiSelectSetting;
import dev.lucid.setting.Setting;
import dev.lucid.setting.SliderSetting;
import dev.lucid.util.render.Render2D;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * Как настройка выглядит и кликается в ClickGUI.
 *
 * <p>Сама {@link Setting} про пиксели ничего не знает — только значение. Виджет живёт
 * здесь, поэтому новый тип настройки не раздувает {@code ClickGuiScreen}.</p>
 */
public abstract class SettingComponent {

	public static final float HEIGHT = 13.0F;
	public static final float SLIDER_HEIGHT = 22.0F;
	public static final float OPTION_HEIGHT = 12.0F;

	protected static final float FONT_SIZE = 9.0F;
	protected static final float TEXT_PADDING = 7.0F;
	protected static final float SETTING_INDENT = 8.0F;
	protected static final float OPTION_INDENT = 16.0F;
	protected static final float BOX_SIZE = 7.0F;
	protected static final float BOX_RADIUS = 3.0F;
	protected static final float BOX_MARK_INSET = 2.0F;
	protected static final float SLIDER_TRACK_HEIGHT = 3.0F;
	protected static final float SLIDER_KNOB_WIDTH = 3.0F;
	protected static final float SLIDER_KNOB_HEIGHT = 7.0F;
	protected static final float CARD_PADDING = 3.0F;

	protected static final int COLOR_SETTING_TEXT = 0xFF9AA0AC;
	protected static final int COLOR_SETTING_VALUE = 0xFFE3E7EE;
	protected static final int COLOR_ACCENT = 0xFF3E8F6B;
	protected static final int COLOR_OFF = 0xFF2B2F37;
	protected static final int COLOR_KNOB = 0xFFD5D9E0;

	protected static final int MOUSE_LEFT = 0;
	protected static final int MOUSE_RIGHT = 1;

	protected static final float CHIP_HEIGHT = 12.0F;
	protected static final float CHIP_PAD_X = 5.0F;
	protected static final float CHIP_GAP = 3.0F;
	protected static final float CHIP_RADIUS = 4.0F;
	protected static final float CHIP_OUTLINE = 1.0F;
	protected static final float CHIP_FONT = 8.0F;

	/** Выбранный чип — нормальная обводка и текст. */
	protected static final int CHIP_ON = COLOR_SETTING_VALUE;

	/** Выключенный — та же палитра, только бледнее. */
	protected static final int CHIP_OFF = 0x558B919C;

	public static SettingComponent of(Setting<?> setting) {
		if (setting instanceof BooleanSetting value) {
			return new BooleanComponent(value);
		}

		if (setting instanceof SliderSetting value) {
			return new SliderComponent(value);
		}

		if (setting instanceof MultiSelectSetting value) {
			return new MultiSelectComponent(value);
		}

		if (setting instanceof ModeSetting value) {
			return new ModeComponent(value);
		}

		return new ValueComponent(setting);
	}

	public abstract float height();

	/** Высота с учётом ширины карточки — мультиселекту нужно, чтобы чипы переносились. */
	public float height(float width) {
		return this.height();
	}

	/** Дополнительные строки под заголовком. У мультиселекта пусто: чипы внутри виджета. */
	public List<String> options() {
		return List.of();
	}

	public float optionHeight() {
		return OPTION_HEIGHT;
	}

	public abstract void draw(GuiGraphicsExtractor graphics, float x, float y, float width);

	public void drawOption(GuiGraphicsExtractor graphics, String option, float x, float y, float width,
	                       float height) {
	}

	/**
	 * @return {@code true}, если клик захватил виджет для протаскивания (ползунок).
	 */
	public boolean click(MouseButtonEvent event, float x, float y, float width, int button) {
		return false;
	}

	public void clickOption(String option, int button) {
	}

	public void drag(double mouseX) {
	}

	protected static void drawNameValue(
			GuiGraphicsExtractor graphics,
			Setting<?> setting,
			float x,
			float y,
			float width,
			float height) {
		Render2D.textInRow(graphics, setting.getName(), x + SETTING_INDENT, y, height, FONT_SIZE,
				COLOR_SETTING_TEXT);

		String value = setting.getDisplayValue();
		float valueX = x + width - TEXT_PADDING - Render2D.textWidth(value, FONT_SIZE);

		Render2D.textInRow(graphics, value, valueX, y, height, FONT_SIZE, COLOR_SETTING_VALUE);
	}

	/** Квадратик галки. Включённый — залит цветом, выключенный — только тёмная рамка. */
	protected static void drawBox(GuiGraphicsExtractor graphics, float x, float y, boolean checked) {
		Render2D.squircle(graphics, x, y, BOX_SIZE, BOX_SIZE, BOX_RADIUS, COLOR_OFF);

		if (checked) {
			float inset = BOX_MARK_INSET;

			Render2D.squircle(graphics, x + inset * 0.5F, y + inset * 0.5F, BOX_SIZE - inset,
					BOX_SIZE - inset, BOX_RADIUS, COLOR_ACCENT);
		}
	}

	protected static float boxX(float x, float width) {
		return x + width - TEXT_PADDING - BOX_SIZE;
	}

	protected static float chipsHeight(List<String> options, float width) {
		if (options.isEmpty()) {
			return HEIGHT;
		}

		List<Chip> chips = layoutChips(options, Math.max(width, 1.0F));
		Chip last = chips.get(chips.size() - 1);

		return last.y + last.height + 1.0F;
	}

	protected static void drawChipRow(
			GuiGraphicsExtractor graphics,
			String title,
			List<String> options,
			Predicate<String> selected,
			float x,
			float y,
			float width) {
		Render2D.textInRow(graphics, title, x + SETTING_INDENT, y, HEIGHT, FONT_SIZE, COLOR_SETTING_VALUE);

		for (Chip chip : layoutChips(options, width)) {
			int color = selected.test(chip.option) ? CHIP_ON : CHIP_OFF;

			Render2D.roundedRectOutlined(
					graphics,
					x + chip.x,
					y + chip.y,
					chip.width,
					chip.height,
					CHIP_RADIUS,
					CHIP_OUTLINE,
					0x00000000,
					color);

			Render2D.textInRow(
					graphics,
					chip.option,
					x + chip.x + CHIP_PAD_X,
					y + chip.y,
					chip.height,
					CHIP_FONT,
					color);
		}
	}

	protected static String chipAt(List<String> options, float width, float localX, float localY) {
		for (Chip chip : layoutChips(options, width)) {
			if (localX >= chip.x && localX <= chip.x + chip.width
					&& localY >= chip.y && localY <= chip.y + chip.height) {
				return chip.option;
			}
		}

		return null;
	}

	protected static List<Chip> layoutChips(List<String> options, float width) {
		List<Chip> chips = new ArrayList<>();
		float left = SETTING_INDENT;
		float right = Math.max(left + 8.0F, width - TEXT_PADDING);
		float maxWidth = right - left;
		float x = left;
		float y = HEIGHT;

		for (String option : options) {
			float chipWidth = Render2D.textWidth(option, CHIP_FONT) + CHIP_PAD_X * 2.0F;
			chipWidth = Math.min(Math.max(chipWidth, CHIP_HEIGHT), maxWidth);

			if (x > left && x + chipWidth > right) {
				x = left;
				y += CHIP_HEIGHT + CHIP_GAP;
			}

			chips.add(new Chip(option, x, y, chipWidth, CHIP_HEIGHT));
			x += chipWidth + CHIP_GAP;
		}

		return chips;
	}

	protected record Chip(String option, float x, float y, float width, float height) {
	}
}
