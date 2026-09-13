package dev.lucid.util.render;

/**
 * Работа с цветом.
 *
 * <p>Цвет везде — обычный {@code int} в порядке ARGB, а не объект. Так его ждёт
 * вершинный буфер, и так он не создаёт мусора: цвет считается каждый кадр и для
 * каждого элемента, а класс-обёртка на каждый такой счёт давала бы тысячи короткоживущих
 * объектов в секунду.</p>
 *
 * <p>Здесь только действия над цветом. Сама палитра клиента сюда не входит — это
 * разные вещи: действия не меняются никогда, а палитра будет настраиваться.</p>
 */
public final class ColorUtil {

    /** Полностью прозрачный цвет. Шейдер такие точки выбрасывает. */
    public static final int TRANSPARENT = 0x00000000;

    private static final int CHANNEL_MASK = 0xFF;
    private static final int ALPHA_SHIFT = 24;
    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;
    private static final int BLUE_SHIFT = 0;
    private static final float MAX_CHANNEL = 255.0F;
    private static final int HUE_SECTORS = 6;

    private ColorUtil() {
    }

    /** Собрать цвет из каналов 0..255. */
    public static int rgba(int red, int green, int blue, int alpha) {
        return (clampChannel(alpha) << ALPHA_SHIFT)
                | (clampChannel(red) << RED_SHIFT)
                | (clampChannel(green) << GREEN_SHIFT)
                | clampChannel(blue);
    }

    /** Непрозрачный цвет из каналов 0..255. */
    public static int rgb(int red, int green, int blue) {
        return rgba(red, green, blue, CHANNEL_MASK);
    }

    public static int alpha(int color) {
        return (color >> ALPHA_SHIFT) & CHANNEL_MASK;
    }

    public static int red(int color) {
        return (color >> RED_SHIFT) & CHANNEL_MASK;
    }

    public static int green(int color) {
        return (color >> GREEN_SHIFT) & CHANNEL_MASK;
    }

    public static int blue(int color) {
        return (color >> BLUE_SHIFT) & CHANNEL_MASK;
    }

    /** Тот же цвет с другой прозрачностью: {@code alpha} от 0 до 1. */
    public static int withAlpha(int color, float alpha) {
        int value = Math.round(clamp01(alpha) * MAX_CHANNEL);
        return (color & 0x00FFFFFF) | (value << ALPHA_SHIFT);
    }

    /** Умножить текущую прозрачность. Удобно для плавного появления целого окна. */
    public static int fade(int color, float factor) {
        return withAlpha(color, alpha(color) / MAX_CHANNEL * clamp01(factor));
    }

    /**
     * Плавный переход между двумя цветами, канал за каналом.
     *
     * <p>{@code progress} — от 0 (первый цвет) до 1 (второй).</p>
     */
    public static int mix(int from, int to, float progress) {
        float t = clamp01(progress);

        return rgba(
                lerpChannel(red(from), red(to), t),
                lerpChannel(green(from), green(to), t),
                lerpChannel(blue(from), blue(to), t),
                lerpChannel(alpha(from), alpha(to), t));
    }

    /** Светлее на долю {@code amount}, прозрачность не трогается. */
    public static int lighten(int color, float amount) {
        return mix(color, withAlpha(0xFFFFFFFF, alpha(color) / MAX_CHANNEL), amount);
    }

    /** Темнее на долю {@code amount}, прозрачность не трогается. */
    public static int darken(int color, float amount) {
        return mix(color, withAlpha(0xFF000000, alpha(color) / MAX_CHANNEL), amount);
    }

    /**
     * Цвет по тону, насыщенности и яркости, всё от 0 до 1.
     *
     * <p>Тон зациклен: 1.2 даст то же, что 0.2. Это удобно для анимаций: можно просто
     * гнать время вверх, не следя за переполнением.</p>
     */
    public static int hsb(float hue, float saturation, float brightness, float alpha) {
        float h = hue - (float) Math.floor(hue);
        float s = clamp01(saturation);
        float v = clamp01(brightness);

        float sector = h * HUE_SECTORS;
        int index = (int) sector;
        float offset = sector - index;

        float p = v * (1.0F - s);
        float q = v * (1.0F - s * offset);
        float t = v * (1.0F - s * (1.0F - offset));

        float red;
        float green;
        float blue;

        switch (index % HUE_SECTORS) {
            case 0 -> {
                red = v;
                green = t;
                blue = p;
            }
            case 1 -> {
                red = q;
                green = v;
                blue = p;
            }
            case 2 -> {
                red = p;
                green = v;
                blue = t;
            }
            case 3 -> {
                red = p;
                green = q;
                blue = v;
            }
            case 4 -> {
                red = t;
                green = p;
                blue = v;
            }
            default -> {
                red = v;
                green = p;
                blue = q;
            }
        }

        return rgba(
                Math.round(red * MAX_CHANNEL),
                Math.round(green * MAX_CHANNEL),
                Math.round(blue * MAX_CHANNEL),
                Math.round(clamp01(alpha) * MAX_CHANNEL));
    }

    /**
     * Перелив по радуге, привязанный к часам системы.
     *
     * @param secondsPerCycle сколько секунд занимает полный круг
     * @param offset сдвиг тона; разный у соседних элементов даёт бегущую волну
     */
    public static int rainbow(float secondsPerCycle, float offset, float saturation, float brightness) {
        float period = Math.max(secondsPerCycle, 0.001F) * 1000.0F;
        float hue = (System.currentTimeMillis() % (long) period) / period;

        return hsb(hue + offset, saturation, brightness, 1.0F);
    }

    private static int lerpChannel(int from, int to, float progress) {
        return Math.round(from + (to - from) * progress);
    }

    private static int clampChannel(int value) {
        return Math.max(0, Math.min(CHANNEL_MASK, value));
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
