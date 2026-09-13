package dev.lucid.event.impl;

import dev.lucid.event.Event;

/**
 * Игра собирается повернуть камеру на накопленное смещение мыши.
 *
 * <p>Это событие-заготовка, а не уведомление: подписчик правит {@link #setDeltaX(double)}
 * и {@link #setDeltaY(double)}, и игра повернётся именно на эти значения.</p>
 *
 * <p>Отмены здесь нет сознательно. Полный запрет поворота и так выражается нулями в обоих
 * полях, а отмена ещё и оборвала бы рассылку: второй модуль, которому тоже нужен поворот,
 * молча перестал бы работать.</p>
 *
 * <p>Событие рассылается каждый кадр при любом шевелении мыши — обработчик обязан быть
 * дешёвым: никаких аллокаций и обходов списков.</p>
 *
 * <p>Значения — уже готовые угловые смещения: сенса, кубическая кривая и инверсия мыши
 * в них уже учтены. Значит, модулю не надо знать ни о настройках игры, ни о DPI мыши,
 * но и менять их надо аккуратно: это градусы, а не пиксели.</p>
 *
 * <p>Тип — {@code double}, как и у игры. Округлять до {@code float} здесь нельзя: на высоком FPS
 * смещения за кадр очень маленькие, и потеря точности чувствуется рукой как зацепы.</p>
 */
public final class MouseRotationEvent extends Event {

    private double deltaX;
    private double deltaY;

    public MouseRotationEvent(double deltaX, double deltaY) {
        this.deltaX = deltaX;
        this.deltaY = deltaY;
    }

    /** Горизонталь: плюс — поворот вправо. */
    public double getDeltaX() {
        return this.deltaX;
    }

    public void setDeltaX(double deltaX) {
        this.deltaX = deltaX;
    }

    /** Вертикаль: плюс — взгляд вниз. */
    public double getDeltaY() {
        return this.deltaY;
    }

    public void setDeltaY(double deltaY) {
        this.deltaY = deltaY;
    }
}
