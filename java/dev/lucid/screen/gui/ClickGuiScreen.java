package dev.lucid.screen.gui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.lucid.client.LucidClient;
import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.screen.gui.components.SettingComponent;
import dev.lucid.setting.Setting;
import dev.lucid.util.render.Render2D;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * ClickGUI: пять колонок-панелей по числу категорий, внутри — плитки модулей с настройками.
 *
 * <p>Левый клик по плитке разворачивает её в карточку с настройками, клик по переключателю
 * справа включает и выключает сам модуль.</p>
 *
 * <p>Как рисуется конкретная настройка — уже не здесь, а в {@link SettingComponent}.
 * Экран только раскладывает строки и спрашивает виджет: нарисуй / кликни.</p>
 */
public class ClickGuiScreen extends Screen {

	// --- геометрия колонок ---

	private static final float COLUMN_WIDTH = 128.0F;
	private static final float COLUMN_GAP = 8.0F;
	/** Минимальный отступ сверху: панель центрируется, но выше этой линии не поднимается. */
	private static final float TOP_OFFSET = 36.0F;

	/** Минимальный отступ снизу: не даёт панели налезть на хотбар на низком окне. */
	private static final float BOTTOM_MARGIN = 20.0F;
	private static final float HEADER_HEIGHT = 22.0F;
	private static final float SIDE_PADDING = 6.0F;
	private static final float CORNER_RADIUS = 34.0F;
	private static final float OUTLINE_THICKNESS = 1.0F;

	/** Радиус размытия тени вокруг колонки. */
	private static final float SHADOW_BLUR = 12.0F;

	/**
	 * Фигура тени совпадает с панелью.
	 *
	 * <p>Шейдер выбрасывает всё, что внутри фигуры, поэтому раздувание и смещение тут
	 * нулевые: любой сдвиг загнал бы часть тени под полупрозрачную подложку.</p>
	 */
	private static final float SHADOW_SPREAD = 0.0F;

	private static final float SHADOW_OFFSET_Y = 0.0F;

	/** Цвет тени. */
	private static final int COLOR_SHADOW = 0x78000000;
	private static final float DIVIDER_THICKNESS = 1.0F;
	private static final float TEXT_PADDING = 7.0F;
	private static final float FONT_SIZE = 9.0F;

	/** Желаемая высота тела панели. Урезается, если экран ниже. */
	private static final float BODY_HEIGHT = 240.0F;

	/** Ниже этого панель не сжимается даже на крошечном окне. */
	private static final float MIN_BODY_HEIGHT = 80.0F;

	// --- геометрия плиток и карточки ---

	private static final float TILE_HEIGHT = 16.0F;
	private static final float TILE_GAP = 3.0F;
	private static final float TILE_RADIUS = 8.0F;

	private static final float SETTING_GAP = 1.0F;
	private static final float CARD_PADDING = 3.0F;

	private static final float SWITCH_WIDTH = 14.0F;
	private static final float SWITCH_HEIGHT = 8.0F;
	private static final float SWITCH_KNOB = 6.0F;
	private static final float SWITCH_INSET = 1.0F;

	// --- полоса прокрутки ---

	private static final float SCROLLBAR_WIDTH = 2.0F;
	private static final float SCROLLBAR_MARGIN = 3.0F;
	private static final float SCROLLBAR_MIN_LENGTH = 12.0F;

	// --- цвета (ARGB) ---

	private static final int COLOR_PANEL_BG = 0xF014161B;
	private static final int COLOR_PANEL_OUTLINE = 0xFF2B2F37;
	private static final int COLOR_HEADER_TEXT = 0xFFFFFFFF;
	private static final int COLOR_DIVIDER = 0xFF2B2F37;
	private static final int COLOR_CARD_BG = 0x5A1E2128;
	private static final int COLOR_TILE_BG_HOVERED = 0x401E2128;
	private static final int COLOR_TILE_BG_ENABLED = 0x3C3E8F6B;
	private static final int COLOR_TILE_TEXT = 0xFF8B919C;
	private static final int COLOR_TILE_TEXT_ENABLED = 0xFFFFFFFF;
	private static final int COLOR_ACCENT = 0xFF3E8F6B;
	private static final int COLOR_OFF = 0xFF2B2F37;
	private static final int COLOR_KNOB = 0xFFD5D9E0;
	private static final int COLOR_EMPTY_TEXT = 0xFF4A4F58;
	private static final int COLOR_SCROLL_TRACK = 0xFF23262D;
	private static final int COLOR_SCROLL_THUMB = 0xFF4A5059;

	private static final int MOUSE_LEFT = 0;
	private static final int MOUSE_RIGHT = 1;

	private static final int KEY_DOWN = 264;
	private static final int KEY_UP = 265;

	/** На сколько сдвигает один щелчок колеса или одно нажатие стрелки. */
	private static final float SCROLL_STEP = TILE_HEIGHT + TILE_GAP;

	/** Смещение содержимого каждой колонки, всегда неотрицательное. */
	private final float[] scroll = new float[Category.values().length];

	/** id модулей, у которых сейчас раскрыты настройки. Сбрасывается при закрытии экрана. */
	private final Set<String> expanded = new HashSet<>();

	/** Виджет, который сейчас тащат мышью — обычно ползунок. */
	private SettingComponent dragged;

	/**
	 * Одна строка внутри раскрытой карточки.
	 *
	 * @param component виджет настройки или null для заглушки «без настроек»
	 * @param option    вариант мультиселекта или null, если это сама настройка
	 * @param y         верх строки относительно верха содержимого колонки
	 */
	private record Row(SettingComponent component, String option, float y, float height) { }

	/**
	 * Модуль вместе с его строками настроек.
	 *
	 * @param y      верх плитки относительно верха содержимого колонки
	 * @param height высота всей карточки: плитка плюс настройки, если модуль раскрыт
	 */
	private record Group(Module module, float y, float height, List<Row> rows) { }

	public ClickGuiScreen() {
		super(Component.literal("Lucid"));
	}

	// ------------------------------------------------------------------ раскладка

	/** Левый край колонки по её номеру. Вся сетка центрируется по ширине экрана. */
	private float columnX(int index) {
		int count = Category.values().length;
		float totalWidth = count * COLUMN_WIDTH + (count - 1) * COLUMN_GAP;
		float left = (this.width - totalWidth) * 0.5F;

		return left + index * (COLUMN_WIDTH + COLUMN_GAP);
	}

	/** Высота видимой части панели — одна на все колонки. */
	private float bodyHeight() {
		float available = this.height - TOP_OFFSET - BOTTOM_MARGIN - HEADER_HEIGHT;

		return Math.max(Math.min(BODY_HEIGHT, available), MIN_BODY_HEIGHT);
	}

	/**
	 * Верх панели. Блок колонок стоит по центру экрана по вертикали, а не висит у верхней
	 * кромки; на низком окне центр опустился бы под {@link #TOP_OFFSET}, поэтому там верх
	 * прижимается к этому отступу.
	 */
	private float panelTop() {
		float panelHeight = HEADER_HEIGHT + this.bodyHeight();

		return Math.max(TOP_OFFSET, (this.height - panelHeight) * 0.5F);
	}

	private float bodyTop() {
		return this.panelTop() + HEADER_HEIGHT;
	}

	private float cardWidth() {
		return COLUMN_WIDTH - SIDE_PADDING * 2.0F;
	}

	/**
	 * Собирает содержимое колонки сверху вниз.
	 *
	 * <p>Координаты здесь относительные, без прокрутки: она добавляется одним вычитанием при
	 * отрисовке и при клике. Иначе смещение пришлось бы помнить в двух местах.</p>
	 */
	private List<Group> layout(Category category) {
		List<Group> groups = new ArrayList<>();
		float y = TILE_GAP;

		for (Module module : LucidClient.MODULES.getByCategory(category)) {
			List<Row> rows = new ArrayList<>();
			float height = TILE_HEIGHT;

			if (this.expanded.contains(module.getId())) {
				List<Setting<?>> settings = module.getVisibleSettings();

				if (settings.isEmpty()) {
					// Пустая карточка выглядела бы как поломка: строку-заглушку рисуем вместо неё.
					rows.add(new Row(null, null, height + CARD_PADDING, SettingComponent.HEIGHT));
					height += CARD_PADDING + SettingComponent.HEIGHT + CARD_PADDING;
				} else {
					height += CARD_PADDING;

					float cardWidth = this.cardWidth();

					for (Setting<?> setting : settings) {
						SettingComponent component = SettingComponent.of(setting);
						float rowHeight = component.height(cardWidth);

						rows.add(new Row(component, null, height, rowHeight));
						height += rowHeight + SETTING_GAP;

						for (String option : component.options()) {
							rows.add(new Row(component, option, height, component.optionHeight()));
							height += component.optionHeight() + SETTING_GAP;
						}
					}

					height += CARD_PADDING - SETTING_GAP;
				}
			}

			groups.add(new Group(module, y, height, rows));
			y += height + TILE_GAP;
		}

		return groups;
	}

	/** Полная высота содержимого колонки со всеми раскрытыми карточками. */
	private float contentHeight(List<Group> groups) {
		if (groups.isEmpty()) {
			return 0.0F;
		}

		Group last = groups.get(groups.size() - 1);

		return last.y() + last.height() + TILE_GAP;
	}

	private float maxScroll(List<Group> groups) {
		return Math.max(0.0F, this.contentHeight(groups) - this.bodyHeight());
	}

	/**
	 * Возвращает смещение колонки, по дороге загоняя его в допустимые пределы.
	 *
	 * <p>Проверка именно здесь, а не в месте прокрутки: карточку можно свернуть после того, как
	 * колонка была уведена вниз, и тогда старое смещение оставило бы пустоту под последней плиткой.</p>
	 */
	private float offsetOf(int columnIndex, List<Group> groups) {
		float clamped = Math.max(0.0F, Math.min(this.scroll[columnIndex], this.maxScroll(groups)));

		this.scroll[columnIndex] = clamped;

		return clamped;
	}

	// ------------------------------------------------------------------ отрисовка

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		Category[] categories = Category.values();

		for (int index = 0; index < categories.length; index++) {
			this.drawColumn(graphics, categories[index], index, mouseX, mouseY);
		}
	}

	private void drawColumn(
			GuiGraphicsExtractor graphics,
			Category category,
			int columnIndex,
			int mouseX,
			int mouseY) {
		float left = this.columnX(columnIndex);
		float bodyHeight = this.bodyHeight();
		float panelHeight = HEADER_HEIGHT + bodyHeight;
		float panelTop = this.panelTop();
		float bodyTop = panelTop + HEADER_HEIGHT;
		float bodyBottom = bodyTop + bodyHeight;

		// Подложка и обводка всей колонки: подложка ужата под обводку, чтобы край
		// не смешивался с фоном дважды.
		// Тень идёт первой: элементы рисуются в порядке подачи.
		Render2D.squircleShadow(graphics, left, panelTop, COLUMN_WIDTH, panelHeight,
				CORNER_RADIUS, SHADOW_BLUR, SHADOW_SPREAD, 0.0F, SHADOW_OFFSET_Y, COLOR_SHADOW);

		Render2D.squircleRectOutlined(graphics, left, panelTop, COLUMN_WIDTH, panelHeight,
				CORNER_RADIUS, OUTLINE_THICKNESS, COLOR_PANEL_BG, COLOR_PANEL_OUTLINE);

		// Шапка: название по центру и черта под ним.
		Render2D.textInRow(graphics, category.displayName(),
				left + (COLUMN_WIDTH - Render2D.textWidth(category.displayName(), FONT_SIZE)) * 0.5F,
				panelTop, HEADER_HEIGHT, FONT_SIZE, COLOR_HEADER_TEXT);

		Render2D.rect(graphics, left, bodyTop, COLUMN_WIDTH, DIVIDER_THICKNESS, COLOR_DIVIDER);

		List<Group> groups = this.layout(category);

		if (groups.isEmpty()) {
			Render2D.textInRow(graphics, "пусто", left + TEXT_PADDING, bodyTop + TILE_GAP, TILE_HEIGHT,
					FONT_SIZE, COLOR_EMPTY_TEXT);

			return;
		}

		float offset = this.offsetOf(columnIndex, groups);
		float x = left + SIDE_PADDING;
		float cardWidth = this.cardWidth();

		for (Group group : groups) {
			float groupTop = bodyTop + group.y() - offset;

			// Карточка целиком за пределами панели — пропускаем вместе со всеми её строками.
			if (groupTop > bodyBottom || groupTop + group.height() < bodyTop) {
				continue;
			}

			this.drawGroup(graphics, group, x, groupTop, cardWidth, bodyTop, bodyBottom, mouseX, mouseY);
		}

		this.drawScrollbar(graphics, left, bodyTop, bodyHeight, groups, offset);
	}

	private void drawGroup(
			GuiGraphicsExtractor graphics,
			Group group,
			float x,
			float groupTop,
			float cardWidth,
			float bodyTop,
			float bodyBottom,
			int mouseX,
			int mouseY) {
		Module module = group.module();
		boolean open = !group.rows().isEmpty();

		// Подложка раскрытой карточки обрезается по краю панели вручную: обрезки по краю
		// у своей отрисовки пока нет, а карточка выше панели встречается запросто.
		if (open) {
			float top = Math.max(groupTop, bodyTop);
			float bottom = Math.min(groupTop + group.height(), bodyBottom);

			if (bottom > top) {
				Render2D.squircle(graphics, x, top, cardWidth, bottom - top, TILE_RADIUS,
						COLOR_CARD_BG);
			}
		}

		if (this.fits(groupTop, TILE_HEIGHT, bodyTop, bodyBottom)) {
			this.drawTile(graphics, module, x, groupTop, cardWidth, mouseX, mouseY);
		}

		for (Row row : group.rows()) {
			float rowTop = groupTop + row.y();

			// Строка рисуется только целиком: половина строки читается как поломка.
			if (!this.fits(rowTop, row.height(), bodyTop, bodyBottom)) {
				continue;
			}

			this.drawRow(graphics, row, x, rowTop, cardWidth);
		}
	}

	private void drawTile(
			GuiGraphicsExtractor graphics,
			Module module,
			float x,
			float y,
			float cardWidth,
			int mouseX,
			int mouseY) {
		boolean enabled = module.isEnabled();
		boolean hovered = this.inside(mouseX, mouseY, x, y, cardWidth, TILE_HEIGHT);

		if (enabled) {
			Render2D.squircle(graphics, x, y, cardWidth, TILE_HEIGHT, TILE_RADIUS,
					COLOR_TILE_BG_ENABLED);
		} else if (hovered) {
			Render2D.squircle(graphics, x, y, cardWidth, TILE_HEIGHT, TILE_RADIUS,
					COLOR_TILE_BG_HOVERED);
		}

		Render2D.textInRow(graphics, module.getName(), x + TEXT_PADDING, y, TILE_HEIGHT, FONT_SIZE,
				enabled ? COLOR_TILE_TEXT_ENABLED : COLOR_TILE_TEXT);

		this.drawSwitch(graphics, this.switchX(x, cardWidth), y + (TILE_HEIGHT - SWITCH_HEIGHT) * 0.5F,
				enabled);
	}

	/** Переключатель модуля: скруглённая дорожка и кружок, который переезжает вправо. */
	private void drawSwitch(GuiGraphicsExtractor graphics, float x, float y, boolean enabled) {
		Render2D.roundedRect(graphics, x, y, SWITCH_WIDTH, SWITCH_HEIGHT, SWITCH_HEIGHT * 0.5F,
				enabled ? COLOR_ACCENT : COLOR_OFF);

		float knobX = enabled ? x + SWITCH_WIDTH - SWITCH_KNOB - SWITCH_INSET : x + SWITCH_INSET;

		Render2D.roundedRect(graphics, knobX, y + SWITCH_INSET, SWITCH_KNOB, SWITCH_KNOB,
				SWITCH_KNOB * 0.5F, COLOR_KNOB);
	}

	private void drawRow(GuiGraphicsExtractor graphics, Row row, float x, float y, float cardWidth) {
		SettingComponent component = row.component();

		if (component == null) {
			Render2D.textInRow(graphics, "без настроек", x + 8.0F, y, row.height(), FONT_SIZE,
					COLOR_EMPTY_TEXT);

			return;
		}

		if (row.option() != null) {
			component.drawOption(graphics, row.option(), x, y, cardWidth, row.height());
			return;
		}

		component.draw(graphics, x, y, cardWidth);
	}

	/** Полоса справа внутри панели. Появляется, только если крутить действительно есть куда. */
	private void drawScrollbar(
			GuiGraphicsExtractor graphics,
			float left,
			float bodyTop,
			float bodyHeight,
			List<Group> groups,
			float offset) {
		float maxScroll = this.maxScroll(groups);

		if (maxScroll <= 0.0F) {
			return;
		}

		float trackX = left + COLUMN_WIDTH - SCROLLBAR_MARGIN - SCROLLBAR_WIDTH;
		float trackY = bodyTop + SCROLLBAR_MARGIN;
		float trackHeight = bodyHeight - SCROLLBAR_MARGIN * 2.0F;
		float radius = SCROLLBAR_WIDTH * 0.5F;

		Render2D.roundedRect(graphics, trackX, trackY, SCROLLBAR_WIDTH, trackHeight, radius,
				COLOR_SCROLL_TRACK);

		float thumbHeight = Math.max(trackHeight * (bodyHeight / this.contentHeight(groups)),
				SCROLLBAR_MIN_LENGTH);
		float thumbY = trackY + (trackHeight - thumbHeight) * (offset / maxScroll);

		Render2D.roundedRect(graphics, trackX, thumbY, SCROLLBAR_WIDTH, thumbHeight, radius,
				COLOR_SCROLL_THUMB);
	}

	// ------------------------------------------------------------------ мышь

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int button = event.button();

		if (button != MOUSE_LEFT && button != MOUSE_RIGHT) {
			return super.mouseClicked(event, doubleClick);
		}

		Category[] categories = Category.values();
		float bodyTop = this.bodyTop();
		float bodyBottom = bodyTop + this.bodyHeight();
		float cardWidth = this.cardWidth();

		for (int column = 0; column < categories.length; column++) {
			float x = this.columnX(column) + SIDE_PADDING;
			List<Group> groups = this.layout(categories[column]);
			float offset = this.offsetOf(column, groups);

			for (Group group : groups) {
				float groupTop = bodyTop + group.y() - offset;

				// Тот же отбор, что и при отрисовке: невидимое не нажимается.
				if (this.fits(groupTop, TILE_HEIGHT, bodyTop, bodyBottom)
						&& this.inside(event.x(), event.y(), x, groupTop, cardWidth, TILE_HEIGHT)) {
					this.clickTile(group.module(), event, x, cardWidth, button);

					return true;
				}

				for (Row row : group.rows()) {
					float rowTop = groupTop + row.y();

					if (!this.fits(rowTop, row.height(), bodyTop, bodyBottom)) {
						continue;
					}

					if (this.inside(event.x(), event.y(), x, rowTop, cardWidth, row.height())) {
						this.clickRow(row, event, x, rowTop, cardWidth, button);

						return true;
					}
				}
			}
		}

		return super.mouseClicked(event, doubleClick);
	}

	/**
	 * Клик по плитке модуля.
	 *
	 * <p>Переключатель справа включает модуль, всё остальное поле плитки разворачивает настройки.
	 * Правая кнопка тоже переключает — привычка из других клиентов, ломать её незачем.</p>
	 */
	private void clickTile(Module module, MouseButtonEvent event, float x, float cardWidth, int button) {
		boolean onSwitch = event.x() >= this.switchX(x, cardWidth) - SWITCH_INSET;

		if (button == MOUSE_RIGHT || onSwitch) {
			module.toggle();

			return;
		}

		if (this.expanded.contains(module.getId())) {
			this.expanded.remove(module.getId());
		} else {
			this.expanded.add(module.getId());
		}
	}

	private void clickRow(Row row, MouseButtonEvent event, float x, float rowTop, float cardWidth,
	                      int button) {
		SettingComponent component = row.component();

		if (component == null) {
			return;
		}

		if (row.option() != null) {
			component.clickOption(row.option(), button);
			return;
		}

		if (component.click(event, x, rowTop, cardWidth, button)) {
			this.dragged = component;
		}
	}

	/**
	 * Протаскивание ползунка.
	 *
	 * <p>Границы дорожки запомнены в виджете в момент захвата и заново не считаются: пока тащат,
	 * колонка может уехать прокруткой, и пересчёт увёл бы значение вслед за ней.</p>
	 */
	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (this.dragged == null) {
			return super.mouseDragged(event, dragX, dragY);
		}

		this.dragged.drag(event.x());

		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		this.dragged = null;

		return super.mouseReleased(event);
	}

	/**
	 * Колесо крутит ту колонку, над которой курсор.
	 *
	 * <p>Прокрутка считается по всей панели, а не только по телу: если курсор зацепил шапку,
	 * человек всё равно имеет в виду эту колонку.</p>
	 */
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY == 0.0) {
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		}

		Category[] categories = Category.values();
		float panelHeight = HEADER_HEIGHT + this.bodyHeight();
		float panelTop = this.panelTop();

		for (int column = 0; column < categories.length; column++) {
			if (this.inside(mouseX, mouseY, this.columnX(column), panelTop, COLUMN_WIDTH, panelHeight)) {
				this.scrollBy(column, categories[column], (float) -scrollY * SCROLL_STEP);

				return true;
			}
		}

		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	/** Стрелки крутят все колонки сразу — запасной путь на случай мыши без колеса. */
	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();

		if (key != KEY_DOWN && key != KEY_UP) {
			return super.keyPressed(event);
		}

		Category[] categories = Category.values();
		float delta = key == KEY_DOWN ? SCROLL_STEP : -SCROLL_STEP;

		for (int column = 0; column < categories.length; column++) {
			this.scrollBy(column, categories[column], delta);
		}

		return true;
	}

	private void scrollBy(int columnIndex, Category category, float delta) {
		float target = this.scroll[columnIndex] + delta;
		float max = this.maxScroll(this.layout(category));

		this.scroll[columnIndex] = Math.max(0.0F, Math.min(target, max));
	}

	// ------------------------------------------------------------------ мелочи

	private float switchX(float x, float cardWidth) {
		return x + cardWidth - TEXT_PADDING - SWITCH_WIDTH;
	}

	/** Помещается ли элемент в тело панели целиком. */
	private boolean fits(float y, float height, float bodyTop, float bodyBottom) {
		return y >= bodyTop && y + height <= bodyBottom;
	}

	private boolean inside(double mouseX, double mouseY, float x, float y, float width, float height) {
		return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
	}

	/** В одиночной игре не ставим игру на паузу, как и остальные клиентские GUI. */
	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/**
	 * Меню закрыли — записываем то, что нащёлкали.
	 *
	 * <p>Ждать выхода из игры нельзя: краш или закрытие окна крестиком унесли бы все настройки
	 * за сессию. Запись идёт только если что-то действительно менялось.</p>
	 */
	@Override
	public void removed() {
		super.removed();
		this.expanded.clear();
		LucidClient.CONFIG.saveIfDirty(LucidClient.MODULES);
	}
}
