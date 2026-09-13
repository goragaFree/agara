package dev.lucid.module.impl.render;

import dev.lucid.event.impl.MouseRotationEvent;
import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.SliderSetting;

/**
 * Сглаживание поворота камеры мышью.
 *
 * <p>Суть: каждый кадр применяется лишь доля накопленного смещения, остаток переносится
 * на следующие кадры. Ни один градус не теряется — итоговый угол всегда тот, что дала мышь,
 * меняется только темп прибытия. Это отличает сглаживание от уменьшения сенсы.</p>
 *
 * <p><b>Почему время, а не просто деление на коэффициент.</b> Наивная версия
 * ({@code applied = total / smooth}) зависит от FPS: на 240 кадрах остаток рассасывается вчетверо
 * быстрее, чем на 60, и одна и та же цифра в настройке ощущается по-разному. Здесь доля
 * считается от реального шага времени по формуле {@code 1 - exp(-dt / tau)}, поэтому
 * ощущение одинаково на любом железе и не меняется при просадках кадров.</p>
 *
 * <p><b>Про ванильную «Плавную камеру».</b> Если в настройках игры включён
 * {@code smoothCamera}, сглаживания будет два подряд, и камера станет вялой. Это не ошибка
 * модуля: он работает после ванили и намеренно её не отключает — настройки игры не наше дело.
 * Для чистого результата ванильную галочку стоит снять.</p>
 */
public final class SmoothCamera extends Module {

    /**
     * Предел накопленного остатка в градусах. Страховка от зависания окна или рывка
     * мыши: без него камера могла бы ещё секунды докручиваться без участия игрока.
     */
    private static final double RESIDUAL_LIMIT_DEGREES = 720.0;

    /** Ниже этого остаток считается нулём, иначе он вечно делится надвое. */
    private static final double RESIDUAL_EPSILON = 1.0E-4;

    /**
     * Перевод деления на постоянную времени: {@code tau = (smooth - 1) * это}.
     * 0.01 выбрано так, чтобы середина ползунка ощущалась примерно как деление на это
     * же число при 60 кадрах в секунду — привычно тем, кто видел другие клиенты.
     */
    private static final double TAU_PER_STEP_SECONDS = 0.01;

    /** Шаг времени обрезается сверху: после паузы или загрузки чанков dt огромен. */
    private static final double MAX_STEP_SECONDS = 0.1;

    private final SliderSetting smoothness = this.addSetting(new SliderSetting(
            "smoothness",
            "Плавность",
            "1 — ванильное поведение, больше — длиннее доворот камеры",
            5.0,
            1.0,
            50.0,
            0.5));

    private double residualX;
    private double residualY;

    /** Время прошлого события. {@code 0} означает «шаг ещё не известен». */
    private long lastEventNanos;

    public SmoothCamera() {
        super(
                "smooth_camera",
                "SmoothCamera",
                Category.RENDER,
                "Сглаживает поворот камеры мышью, не теряя ни одного градуса ввода",
                Module.KEY_UNBOUND);

        this.listen(MouseRotationEvent.class, this::onMouseRotation);
    }

    @Override
    protected void onEnable() {
        this.reset();
    }

    @Override
    protected void onDisable() {
        // Сброс и на выключении тоже: иначе старый остаток выстрелит дёрганьем камеры
        // при следующем включении.
        this.reset();
    }

    private void reset() {
        this.residualX = 0.0;
        this.residualY = 0.0;
        this.lastEventNanos = 0L;
    }

    private void onMouseRotation(MouseRotationEvent event) {
        double smooth = this.smoothness.get();

        if (smooth <= 1.0) {
            // Ровно ваниль: смещение не трогаем, но остаток гасим, чтобы он не всплыл
            // при возврате ползунка вверх.
            this.reset();
            return;
        }

        long now = System.nanoTime();
        double step = this.lastEventNanos == 0L
                ? 1.0 / 60.0
                : (double) (now - this.lastEventNanos) / 1.0E9;
        this.lastEventNanos = now;

        if (step <= 0.0) {
            // Таймер может вернуть то же значение дважды. Откладываем всё в остаток.
            this.residualX = clampResidual(this.residualX + event.getDeltaX());
            this.residualY = clampResidual(this.residualY + event.getDeltaY());
            event.setDeltaX(0.0);
            event.setDeltaY(0.0);
            return;
        }

        double tau = (smooth - 1.0) * TAU_PER_STEP_SECONDS;
        double alpha = 1.0 - Math.exp(-Math.min(step, MAX_STEP_SECONDS) / tau);

        double totalX = clampResidual(this.residualX + event.getDeltaX());
        double totalY = clampResidual(this.residualY + event.getDeltaY());

        double appliedX = totalX * alpha;
        double appliedY = totalY * alpha;

        this.residualX = quantize(totalX - appliedX);
        this.residualY = quantize(totalY - appliedY);

        event.setDeltaX(appliedX);
        event.setDeltaY(appliedY);
    }

    private static double clampResidual(double value) {
        if (value > RESIDUAL_LIMIT_DEGREES) {
            return RESIDUAL_LIMIT_DEGREES;
        }
        if (value < -RESIDUAL_LIMIT_DEGREES) {
            return -RESIDUAL_LIMIT_DEGREES;
        }
        return value;
    }

    private static double quantize(double residual) {
        return Math.abs(residual) < RESIDUAL_EPSILON ? 0.0 : residual;
    }
}
