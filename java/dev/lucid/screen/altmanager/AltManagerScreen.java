package dev.lucid.screen.altmanager;

import java.util.List;

import dev.lucid.alt.AltManager;
import dev.lucid.alt.AltSession;
import dev.lucid.util.render.Render2D;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Альт-менеджер: список сохранённых ников и поле ввода для нового.
 *
 * <p>Рисуется целиком своей отрисовкой и своим шрифтом, ванильных виджетов внутри нет.</p>
 *
 * <p>Ввод собирается из кодов клавиш, а не из готовых символов. Можно так: игра разрешает
 * в нике только латинские буквы, цифры и подчёркивание, а у них у всех код клавиши совпадает с кодом
 * самого символа. Заодно это снимает вопрос раскладки: русская клавиатура введёт те же буквы,
 * что и латинская, вместо того чтобы молча ничего не делать.</p>
 */
public class AltManagerScreen extends Screen {

	// --- геометрия ---

	private static final float PANEL_WIDTH = 220.0F;
	private static final float PADDING = 8.0F;
	private static final float HEADER_HEIGHT = 22.0F;
	private static final float ROW_HEIGHT = 15.0F;
	private static final float ROW_GAP = 2.0F;
	private static final int VISIBLE_ROWS = 8;
	private static final float INPUT_HEIGHT = 16.0F;
	private static final float BUTTON_HEIGHT = 16.0F;
	private static final float BUTTON_GAP = 4.0F;
	private static final float STATUS_HEIGHT = 12.0F;
	private static final float BLOCK_GAP = 5.0F;
	private static final float CORNER_RADIUS = 3.0F;
	private static final float FONT_SIZE = 9.0F;
	private static final float TEXT_PADDING = 5.0F;

	// --- цвета (ARGB) ---

	private static final int COLOR_PANEL_BG = 0xF014161B;
	private static final int COLOR_PANEL_OUTLINE = 0xFF2B2F37;
	private static final int COLOR_HEADER_TEXT = 0xFFFFFFFF;
	private static final int COLOR_ROW_BG = 0xC81E2128;
	private static final int COLOR_ROW_BG_SELECTED = 0xC82C7A5A;
	private static final int COLOR_ROW_TEXT = 0xFFB8BDC7;
	private static final int COLOR_ROW_TEXT_SELECTED = 0xFFFFFFFF;
	private static final int COLOR_ROW_TEXT_CURRENT = 0xFF3E8F6B;
	private static final int COLOR_INPUT_BG = 0xC80F1115;
	private static final int COLOR_INPUT_OUTLINE = 0xFF3E8F6B;
	private static final int COLOR_INPUT_TEXT = 0xFFE3E7EE;
	private static final int COLOR_PLACEHOLDER = 0xFF5A5F69;
	private static final int COLOR_BUTTON_BG = 0xC82B2F37;
	private static final int COLOR_BUTTON_TEXT = 0xFFE3E7EE;
	private static final int COLOR_STATUS_TEXT = 0xFF9AA0AC;
	private static final int COLOR_ERROR_TEXT = 0xFFB4544A;

	// --- коды клавиш и кнопок мыши ---

	private static final int MOUSE_LEFT = 0;
	private static final int KEY_ENTER = 257;
	private static final int KEY_NUMPAD_ENTER = 335;
	private static final int KEY_BACKSPACE = 259;
	private static final int KEY_DELETE = 261;
	private static final int KEY_UP = 265;
	private static final int KEY_DOWN = 264;
	private static final int KEY_MINUS = 45;
	private static final int KEY_ZERO = 48;
	private static final int KEY_NINE = 57;
	private static final int KEY_A = 65;
	private static final int KEY_Z = 90;
	private static final int KEY_NUMPAD_ZERO = 320;
	private static final int KEY_NUMPAD_NINE = 329;

	/** Максимальная длина ника в игре. */
	private static final int MAX_NAME_LENGTH = 16;

	/** Половина периода мигания каретки, миллисекунды. */
	private static final long CARET_BLINK = 500L;

	/** Куда вернуться по Esc. */
	private final Screen parent;

	private String input = "";

	private int selected = -1;

	/** Номер первой видимой строки списка. */
	private int scroll;

	private String status = "";

	private boolean statusIsError;

	public AltManagerScreen(Screen parent) {
		super(Component.literal("Alt Manager"));

		this.parent = parent;
	}

	// --- раскладка ---

	private float panelHeight() {
		return HEADER_HEIGHT
				+ VISIBLE_ROWS * (ROW_HEIGHT + ROW_GAP)
				+ BLOCK_GAP + INPUT_HEIGHT
				+ BLOCK_GAP + BUTTON_HEIGHT
				+ BLOCK_GAP + STATUS_HEIGHT
				+ PADDING;
	}

	private float panelX() {
		return (this.width - PANEL_WIDTH) * 0.5F;
	}

	private float panelY() {
		return (this.height - this.panelHeight()) * 0.5F;
	}

	private float listY() {
		return this.panelY() + HEADER_HEIGHT;
	}

	private float inputY() {
		return this.listY() + VISIBLE_ROWS * (ROW_HEIGHT + ROW_GAP) + BLOCK_GAP;
	}

	private float buttonsY() {
		return this.inputY() + INPUT_HEIGHT + BLOCK_GAP;
	}

	private float statusY() {
		return this.buttonsY() + BUTTON_HEIGHT + BLOCK_GAP;
	}

	private float contentX() {
		return this.panelX() + PADDING;
	}

	private float contentWidth() {
		return PANEL_WIDTH - PADDING * 2.0F;
	}

	private float buttonWidth() {
		return (this.contentWidth() - BUTTON_GAP * 2.0F) / 3.0F;
	}

	private float buttonX(int index) {
		return this.contentX() + index * (this.buttonWidth() + BUTTON_GAP);
	}

	// --- отрисовка ---

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		List<String> names = AltManager.get().names();

		this.clampScroll(names.size());

		float panelX = this.panelX();
		float panelY = this.panelY();
		float panelHeight = this.panelHeight();

		Render2D.roundedRect(graphics, panelX, panelY, PANEL_WIDTH, panelHeight, CORNER_RADIUS, COLOR_PANEL_BG);
		Render2D.roundedOutline(graphics, panelX, panelY, PANEL_WIDTH, panelHeight, CORNER_RADIUS, 1.0F,
				COLOR_PANEL_OUTLINE);

		Render2D.textInRow(graphics, "Alt Manager", this.contentX(), panelY, HEADER_HEIGHT, FONT_SIZE,
				COLOR_HEADER_TEXT);

		String current = AltSession.currentName();
		float currentWidth = Render2D.textWidth(current, FONT_SIZE);

		Render2D.textInRow(graphics, current, panelX + PANEL_WIDTH - PADDING - currentWidth, panelY,
				HEADER_HEIGHT, FONT_SIZE, COLOR_ROW_TEXT_CURRENT);

		this.extractList(graphics, names, mouseX, mouseY);
		this.extractInput(graphics);
		this.extractButtons(graphics, mouseX, mouseY);

		if (!this.status.isEmpty()) {
			Render2D.textInRow(graphics, this.status, this.contentX(), this.statusY(), STATUS_HEIGHT, FONT_SIZE,
					this.statusIsError ? COLOR_ERROR_TEXT : COLOR_STATUS_TEXT);
		}

		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	private void extractList(GuiGraphicsExtractor graphics, List<String> names, int mouseX, int mouseY) {
		float x = this.contentX();
		float width = this.contentWidth();
		String current = AltSession.currentName();

		for (int slot = 0; slot < VISIBLE_ROWS; slot++) {
			int index = this.scroll + slot;
			float y = this.listY() + slot * (ROW_HEIGHT + ROW_GAP);

			if (index >= names.size()) {
				if (names.isEmpty() && slot == 0) {
					Render2D.textInRow(graphics, "Список пуст — впишите ник ниже", x + TEXT_PADDING, y,
							ROW_HEIGHT, FONT_SIZE, COLOR_PLACEHOLDER);
				}

				continue;
			}

			String name = names.get(index);
			boolean isSelected = index == this.selected;
			boolean hovered = this.inside(mouseX, mouseY, x, y, width, ROW_HEIGHT);

			Render2D.roundedRect(graphics, x, y, width, ROW_HEIGHT, CORNER_RADIUS,
					isSelected ? COLOR_ROW_BG_SELECTED : COLOR_ROW_BG);

			if (hovered && !isSelected) {
				Render2D.roundedOutline(graphics, x, y, width, ROW_HEIGHT, CORNER_RADIUS, 1.0F, COLOR_PANEL_OUTLINE);
			}

			int color = isSelected ? COLOR_ROW_TEXT_SELECTED : COLOR_ROW_TEXT;

			Render2D.textInRow(graphics, name, x + TEXT_PADDING, y, ROW_HEIGHT, FONT_SIZE, color);

			if (name.equals(current)) {
				float markWidth = Render2D.textWidth("сейчас", FONT_SIZE);

				Render2D.textInRow(graphics, "сейчас", x + width - TEXT_PADDING - markWidth, y, ROW_HEIGHT,
						FONT_SIZE, COLOR_ROW_TEXT_CURRENT);
			}
		}
	}

	private void extractInput(GuiGraphicsExtractor graphics) {
		float x = this.contentX();
		float y = this.inputY();
		float width = this.contentWidth();

		Render2D.roundedRect(graphics, x, y, width, INPUT_HEIGHT, CORNER_RADIUS, COLOR_INPUT_BG);
		Render2D.roundedOutline(graphics, x, y, width, INPUT_HEIGHT, CORNER_RADIUS, 1.0F, COLOR_INPUT_OUTLINE);

		if (this.input.isEmpty()) {
			Render2D.textInRow(graphics, "Ник", x + TEXT_PADDING, y, INPUT_HEIGHT, FONT_SIZE, COLOR_PLACEHOLDER);
		} else {
			Render2D.textInRow(graphics, this.input, x + TEXT_PADDING, y, INPUT_HEIGHT, FONT_SIZE, COLOR_INPUT_TEXT);
		}

		boolean caretVisible = (System.currentTimeMillis() / CARET_BLINK) % 2L == 0L;

		if (caretVisible) {
			float caretX = x + TEXT_PADDING + Render2D.textWidth(this.input, FONT_SIZE) + 1.0F;
			float caretHeight = Render2D.textHeight(FONT_SIZE) * 0.7F;
			float caretY = y + (INPUT_HEIGHT - caretHeight) * 0.5F;

			Render2D.rect(graphics, caretX, caretY, 1.0F, caretHeight, COLOR_INPUT_TEXT);
		}
	}

	private void extractButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		String[] labels = {"Войти", "Добавить", "Удалить"};
		float y = this.buttonsY();
		float width = this.buttonWidth();

		for (int index = 0; index < labels.length; index++) {
			float x = this.buttonX(index);
			boolean hovered = this.inside(mouseX, mouseY, x, y, width, BUTTON_HEIGHT);

			Render2D.roundedRect(graphics, x, y, width, BUTTON_HEIGHT, CORNER_RADIUS, COLOR_BUTTON_BG);

			if (hovered) {
				Render2D.roundedOutline(graphics, x, y, width, BUTTON_HEIGHT, CORNER_RADIUS, 1.0F,
						COLOR_INPUT_OUTLINE);
			}

			Render2D.textInRow(graphics, labels[index],
					x + (width - Render2D.textWidth(labels[index], FONT_SIZE)) * 0.5F, y, BUTTON_HEIGHT, FONT_SIZE,
					COLOR_BUTTON_TEXT);
		}
	}

	// --- мышь ---

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != MOUSE_LEFT) {
			return super.mouseClicked(event, doubleClick);
		}

		double mouseX = event.x();
		double mouseY = event.y();
		List<String> names = AltManager.get().names();

		for (int slot = 0; slot < VISIBLE_ROWS; slot++) {
			int index = this.scroll + slot;

			if (index >= names.size()) {
				break;
			}

			float rowY = this.listY() + slot * (ROW_HEIGHT + ROW_GAP);

			if (this.inside(mouseX, mouseY, this.contentX(), rowY, this.contentWidth(), ROW_HEIGHT)) {
				this.selected = index;
				this.input = names.get(index);
				this.status = "";

				if (doubleClick) {
					this.login();
				}

				return true;
			}
		}

		float buttonY = this.buttonsY();
		float buttonWidth = this.buttonWidth();

		for (int index = 0; index < 3; index++) {
			if (this.inside(mouseX, mouseY, this.buttonX(index), buttonY, buttonWidth, BUTTON_HEIGHT)) {
				switch (index) {
					case 0 -> this.login();
					case 1 -> this.addCurrentInput();
					default -> this.removeSelected();
				}

				return true;
			}
		}

		return super.mouseClicked(event, doubleClick);
	}

	private boolean inside(double mouseX, double mouseY, float x, float y, float width, float height) {
		return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
	}

	// --- клавиатура ---

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();

		if (key == KEY_ENTER || key == KEY_NUMPAD_ENTER) {
			this.login();

			return true;
		}

		if (key == KEY_BACKSPACE) {
			if (!this.input.isEmpty()) {
				this.input = this.input.substring(0, this.input.length() - 1);
				this.status = "";
			}

			return true;
		}

		if (key == KEY_DELETE) {
			this.removeSelected();

			return true;
		}

		if (key == KEY_UP || key == KEY_DOWN) {
			this.moveSelection(key == KEY_DOWN ? 1 : -1);

			return true;
		}

		char typed = character(key, event.hasShiftDown());

		if (typed != 0 && this.input.length() < MAX_NAME_LENGTH) {
			this.input += typed;
			this.status = "";

			return true;
		}

		return super.keyPressed(event);
	}

	/**
	 * Символ по коду клавиши, или ноль, если такой символ в нике не разрешён.
	 *
	 * <p>Подчёркивание отдано клавише дефиса без оглядки на шифт: сам дефис в никах всё равно
	 * запрещён, а требовать шифт значит заставлять целиться туда, где промах ничего не даёт.</p>
	 */
	private static char character(int key, boolean shift) {
		if (key >= KEY_A && key <= KEY_Z) {
			return shift ? (char) key : Character.toLowerCase((char) key);
		}

		if (key >= KEY_ZERO && key <= KEY_NINE && !shift) {
			return (char) key;
		}

		if (key >= KEY_NUMPAD_ZERO && key <= KEY_NUMPAD_NINE) {
			return (char) ('0' + key - KEY_NUMPAD_ZERO);
		}

		if (key == KEY_MINUS) {
			return '_';
		}

		return 0;
	}

	// --- действия ---

	private void login() {
		String name = this.chosenName();

		if (!AltManager.isValid(name)) {
			this.setStatus("Ник: от 3 до 16 символов, буквы, цифры и подчёркивание", true);

			return;
		}

		AltSession.apply(name);
		this.setStatus("Сейчас вы — " + name, false);
	}

	private void addCurrentInput() {
		String name = this.input.trim();

		if (!AltManager.isValid(name)) {
			this.setStatus("Ник: от 3 до 16 символов, буквы, цифры и подчёркивание", true);

			return;
		}

		if (!AltManager.get().add(name)) {
			this.setStatus("Такой ник уже в списке", true);

			return;
		}

		this.selected = AltManager.get().names().indexOf(name);
		this.setStatus("Добавлен " + name, false);
	}

	private void removeSelected() {
		List<String> names = AltManager.get().names();

		if (this.selected < 0 || this.selected >= names.size()) {
			this.setStatus("Сначала выберите ник в списке", true);

			return;
		}

		String name = names.get(this.selected);

		AltManager.get().remove(name);

		this.selected = -1;
		this.setStatus("Удалён " + name, false);
	}

	/** Ник из поля ввода, а если оно пустое — выделенный в списке. */
	private String chosenName() {
		String typed = this.input.trim();

		if (!typed.isEmpty()) {
			return typed;
		}

		List<String> names = AltManager.get().names();

		if (this.selected >= 0 && this.selected < names.size()) {
			return names.get(this.selected);
		}

		return "";
	}

	private void moveSelection(int step) {
		List<String> names = AltManager.get().names();

		if (names.isEmpty()) {
			return;
		}

		int next = this.selected + step;

		if (next < 0) {
			next = 0;
		}

		if (next >= names.size()) {
			next = names.size() - 1;
		}

		this.selected = next;
		this.input = names.get(next);

		if (next < this.scroll) {
			this.scroll = next;
		} else if (next >= this.scroll + VISIBLE_ROWS) {
			this.scroll = next - VISIBLE_ROWS + 1;
		}
	}

	private void clampScroll(int size) {
		int maxScroll = Math.max(0, size - VISIBLE_ROWS);

		if (this.scroll > maxScroll) {
			this.scroll = maxScroll;
		}

		if (this.scroll < 0) {
			this.scroll = 0;
		}
	}

	private void setStatus(String text, boolean error) {
		this.status = text;
		this.statusIsError = error;
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
