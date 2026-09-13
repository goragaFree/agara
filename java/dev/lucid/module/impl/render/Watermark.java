package dev.lucid.module.impl.render;

import java.util.Locale;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import dev.lucid.Lucid;
import dev.lucid.event.impl.TimeSyncEvent;
import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.util.render.Render2D;

/**
 * Логотип клиента со счётчиками FPS и TPS в левом верхнем углу экрана.
 *
 * <p>HUD-элемент, как и события рендера, отписать нельзя — поэтому подписка делается
 * один раз в {@link #onRegister()}, а вкл/выкл решается проверкой {@code isEnabled()}
 * внутри отрисовки.</p>
 *
 * <p>Рисуется целиком своей отрисовкой: подложка — скруглённый прямоугольник из
 * {@link Render2D}, текст — свой шрифт по полю расстояний. Ванильный {@code client.font}
 * больше не участвует ни в отрисовке, ни в замерах ширины.</p>
 *
 * <p>FPS берётся сырым из {@code Minecraft.getFps()}, без сглаживания и анимаций.
 * Спокойно смотрится оно за счёт фиксированной разметки: под каждое число всегда отведено
 * место под фиксированное число разрядов, поэтому плашка не меняет ширину ни при
 * переходе с 99 на 100, ни при падении до одной цифры.</p>
 *
 * <p>TPS считается по промежуткам между обновлениями времени от сервера — это оценка,
 * а не точное значение: настоящий TPS сервер клиенту не сообщает.</p>
 */
public class Watermark extends Module {

    private static final String NAME = "Lucid client";

    /** Разделитель между блоками. */
    private static final String SEPARATOR = " > ";

    /** Подписи перед числами. */
    private static final String FPS_LABEL = "fps ";
    private static final String TPS_LABEL = "tps ";

    /**
     * Кегль текста в пикселях интерфейса.
     *
     * <p>Можно ставить любое дробное значение: шрифт хранится как поле расстояний,
     * поэтому буквы остаются резкими на любом размере и при любом масштабе интерфейса.</p>
     */
    private static final float FONT_SIZE = 9.0f;

    /**
     * Сколько разрядов резервируется под FPS. Ширина плашки считается по ним,
     * а не по текущему значению — именно поэтому она не дышит.
     */
    private static final int RESERVED_DIGITS = 4;

    /** Шаблон самого широкого TPS: два разряда, точка и десятая. */
    private static final int TPS_DIGITS = 3;

    /** Потолок тиков в секунду у ванильного сервера. */
    private static final float MAX_TPS = 20.0f;

    /** Наносекунд в секунде. */
    private static final float NANOS_PER_SECOND = 1_000_000_000.0f;

    /** С какого молчания сервера начинаем сами ронять показание, в наносекундах. */
    private static final long STALE_AFTER_NANOS = 1_500_000_000L;

    /** Отступ плашки от краёв экрана. */
    private static final float MARGIN = 4.0f;

    /** Внутренние отступы от текста до края прямоугольника. */
    private static final float PADDING_X = 5.0f;
    private static final float PADDING_Y = 4.0f;

    /** Скругление углов подложки. Ноль вернёт обычный прямоугольник. */
    private static final float CORNER_RADIUS = 3.0f;

    private static final int COLOR_BG = 0xC81E2128;

    /** Радиус размытия тени вокруг плашки. */
    private static final float SHADOW_BLUR = 6.0f;

    /** Фигура тени совпадает с плашкой: внутреннюю часть шейдер выбрасывает. */
    private static final float SHADOW_SPREAD = 0.0f;

    private static final float SHADOW_OFFSET_Y = 0.0f;

    private static final int COLOR_SHADOW = 0x6E000000;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_SEPARATOR = 0xFF5A5F69;
    private static final int COLOR_FPS = 0xFF3E8F6B;
    private static final int COLOR_TPS = 0xFF3E8F6B;

    /**
     * Ширина самой широкой цифры. Считается один раз: атлас и кегль не меняются.
     *
     * <p>В ванильном шрифте цифры одной ширины, и хватало повторить нуль. В своём
     * шрифте это не гарантировано — ширина единицы часто меньше остальных. Поэтому
     * берём максимум по всем десяти и по нему верстаем место под число.</p>
     */
    private static float digitWidth;

    /** Момент прихода последнего обновления времени; 0 — ещё ни одного не было. */
    private long lastSyncNanos;

    /** Последнее измеренное значение. */
    private float tps = MAX_TPS;

    public Watermark() {
        super("watermark", "Watermark", Category.RENDER,
                "Название клиента, FPS и TPS в углу экрана");

        this.listen(TimeSyncEvent.class, event -> this.onTimeSync());
    }

    @Override
    public void onRegister() {
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(Lucid.MOD_ID, "watermark"),
                this::render
        );
    }

    @Override
    protected void onEnable() {
        // Старые замеры после паузы бессмысленны: промежуток будет огромный и даст ноль.
        this.lastSyncNanos = 0L;
        this.tps = MAX_TPS;
    }

    /**
     * Пришло обновление времени от сервера.
     *
     * <p>Здоровый сервер шлёт его раз в 20 тиков. Значит, если между двумя пакетами
     * прошла ровно секунда — сервер успел свои 20 тиков, если две — только половину.</p>
     */
    private void onTimeSync() {
        long now = System.nanoTime();

        if (this.lastSyncNanos != 0L) {
            long elapsed = now - this.lastSyncNanos;

            if (elapsed > 0L) {
                this.tps = clamp(MAX_TPS * (NANOS_PER_SECOND / elapsed));
            }
        }

        this.lastSyncNanos = now;
    }

    /**
     * Текущее показание TPS.
     *
     * <p>Одного замера мало: если сервер замёр, пакеты просто перестанут приходить,
     * и счётчик застынет на бодром значении. Поэтому молчание тоже считается: чем
     * дольше нет обновлений, тем ниже потолок возможного TPS.</p>
     */
    private float currentTps() {
        if (this.lastSyncNanos == 0L) {
            return MAX_TPS;
        }

        long silence = System.nanoTime() - this.lastSyncNanos;

        if (silence <= STALE_AFTER_NANOS) {
            return this.tps;
        }

        return Math.min(this.tps, clamp(MAX_TPS * (NANOS_PER_SECOND / silence)));
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(MAX_TPS, value));
    }

    /**
     * Ширина самой широкой цифры в текущем шрифте.
     *
     * <p>Считается при первой отрисовке, а не в статическом блоке: на момент загрузки
     * класса атлас ещё может быть не прочитан.</p>
     */
    private static float digitWidth() {
        if (digitWidth == 0.0f) {
            float widest = 0.0f;

            for (char digit = '0'; digit <= '9'; digit++) {
                widest = Math.max(widest, Render2D.textWidth(String.valueOf(digit), FONT_SIZE));
            }

            digitWidth = widest;
        }

        return digitWidth;
    }

    private void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        if (!this.isEnabled()) {
            return;
        }

        Minecraft client = mc();

        // Не рисуем при скрытом интерфейсе (F1).
        // В 26.2 флаг уехал из Options в Hud и стал методом.
        if (client.gui.hud.isHidden()) {
            return;
        }

        String fps = FPS_LABEL + client.getFps();

        // Округляем до половины тика: замер шумный, и лишние доли только мелькают.
        // Locale.ROOT обязателен: в русской локали разделителем стала бы запятая.
        float rounded = Math.round(this.currentTps() * 2.0f) / 2.0f;
        String tps = TPS_LABEL + String.format(Locale.ROOT, "%.1f", rounded);

        float digit = digitWidth();

        float nameWidth = Render2D.textWidth(NAME, FONT_SIZE);
        float separatorWidth = Render2D.textWidth(SEPARATOR, FONT_SIZE);

        // Место под самый широкий вариант числа, а не под текущий.
        float fpsWidth = Render2D.textWidth(FPS_LABEL, FONT_SIZE) + digit * RESERVED_DIGITS;
        float tpsWidth = Render2D.textWidth(TPS_LABEL + ".", FONT_SIZE) + digit * TPS_DIGITS;

        float boxWidth = nameWidth + separatorWidth + fpsWidth
                + separatorWidth + tpsWidth + PADDING_X * 2.0f;
        float boxHeight = Render2D.textHeight(FONT_SIZE) + PADDING_Y * 2.0f;

        // Своя отрисовка берёт левый верхний угол и размер, а не две точки.
        // Тень подаётся до плашки: элементы рисуются в порядке подачи.
        Render2D.shadow(graphics, MARGIN, MARGIN, boxWidth, boxHeight,
                CORNER_RADIUS, SHADOW_BLUR, SHADOW_SPREAD, 0.0f, SHADOW_OFFSET_Y, COLOR_SHADOW);

        Render2D.roundedRect(graphics, MARGIN, MARGIN, boxWidth, boxHeight,
                CORNER_RADIUS, COLOR_BG);

        // Текст центруется по высоте плашки: равнять по верху строки нельзя — место
        // под хвосты букв всегда сдвигает его вверх на вид.
        float textX = MARGIN + PADDING_X;

        Render2D.textInRow(graphics, NAME, textX, MARGIN, boxHeight, FONT_SIZE, COLOR_TEXT);
        textX += nameWidth;

        Render2D.textInRow(graphics, SEPARATOR, textX, MARGIN, boxHeight, FONT_SIZE, COLOR_SEPARATOR);
        textX += separatorWidth;

        Render2D.textInRow(graphics, fps, textX, MARGIN, boxHeight, FONT_SIZE, COLOR_FPS);
        textX += fpsWidth;

        Render2D.textInRow(graphics, SEPARATOR, textX, MARGIN, boxHeight, FONT_SIZE, COLOR_SEPARATOR);
        textX += separatorWidth;

        Render2D.textInRow(graphics, tps, textX, MARGIN, boxHeight, FONT_SIZE, COLOR_TPS);
    }
}
