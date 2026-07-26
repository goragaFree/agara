package rich.util.render;

import rich.util.render.font.Font;

import java.util.ArrayList;
import java.util.List;

/**
 * Очередь «позднего» текста клиентским шрифтом.
 *
 * <p>Отложенные ванильные отрисовки (context.drawItem и т.п.) композитятся в
 * конце кадра ПОВЕРХ всего, что клиент нарисовал своими пайплайнами — поэтому
 * обычный Fonts-текст из HUD-элемента предметы накрывают (каунты не видно).
 *
 * <p>Решение: элемент кладёт подписи сюда через {@link #queue} во время своего
 * рендера, а {@code GameRendererMixin#afterGuiRender} флашит очередь СРАЗУ ПОСЛЕ
 * {@code guiRenderer.render(...)} — текст рисуется позже композита предметов и
 * ложится поверх них.
 */
public final class LateText {

    private record Entry(Font font, String text, float x, float y, float size, int color) {}
    private record RectEntry(float x, float y, float w, float h, int color) {}

    private static final List<Entry> PENDING = new ArrayList<>();
    private static final List<RectEntry> RECTS = new ArrayList<>();

    private LateText() {}

    /** Добавить подпись (экранные gui-координаты, как у обычного Fonts.draw). */
    public static void queue(Font font, String text, float x, float y, float size, int color) {
        PENDING.add(new Entry(font, text, x, y, size, color));
    }

    /** Поздний прямоугольник (рисуется до позднего текста, но поверх предметов). */
    public static void queueRect(float x, float y, float w, float h, int color) {
        RECTS.add(new RectEntry(x, y, w, h, color));
    }

    /**
     * Имитация чатовой плашки ПОВЕРХ предмета: ванильный рендер предметов не
     * умеет полупрозрачность, и в полосе ввода чата иконки «горят» сквозь её
     * плашку. Затемняем часть бокса предмета, попавшую ниже bandTop, тем же
     * полупрозрачным чёрным, что и ванильная плашка.
     */
    public static void queueChatBarDim(float bx, float by, float bw, float bh,
                                       float bandTop, float af) {
        float top = Math.max(by, bandTop);
        float bottom = by + bh;
        if (bottom <= top) return;
        int a = Math.max(0, Math.min(255, (int) (128 * af)));
        if (a <= 2) return;
        RECTS.add(new RectEntry(bx, top, bw, bottom - top, a << 24));
    }

    public static boolean hasPending() {
        return !PENDING.isEmpty() || !RECTS.isEmpty();
    }

    /** Защитная очистка (начало кадра) — чтобы записи не копились при пропуске флаша. */
    public static void clear() {
        PENDING.clear();
        RECTS.clear();
    }

    /** Нарисовать всё и очистить. Вызывать внутри Batch.begin() ПОСЛЕ композита гуи. */
    public static void flush() {
        // сначала ректы (затемнение предметов), затем текст — каунты остаются читаемыми
        for (RectEntry r : RECTS) {
            Render2D.rect(r.x(), r.y(), r.w(), r.h(), r.color());
        }
        Render2D.flushRects();
        RECTS.clear();

        for (Entry e : PENDING) {
            e.font().draw(e.text(), e.x(), e.y(), e.size(), e.color());
        }
        PENDING.clear();
    }
}
