package rich.util.render;

import net.minecraft.client.gui.DrawContext;

/**
 * Подъём ванильных баров над кастом-хотбаром — общий помощник для миксинов
 * пакета {@code hud.bar} (ContextualBarMixin, BarMixin). Кастомная панель
 * хотбара выше ванильной (28px + отступ против 22px), поэтому XP-полоса,
 * цифра уровня, полоса прыжка и локатор-бар поднимаются на разницу высот.
 *
 * <p>Счётчик вложенности защищает от двойного подъёма, если поднятый метод
 * внутри зовёт другой поднятый (например, renderAddons → drawExperienceLevel):
 * матрица сдвигается только на внешнем уровне.
 */
public final class BarsLift {

    /** Синхронно с InGameHudMixin.RICH_BARS_LIFT. */
    public static final float LIFT = 7f;

    private static int depth = 0;

    private BarsLift() {}

    public static void begin(DrawContext context) {
        if (!rich.screens.hud.impl.Hotbar.replacesVanilla()) return;
        if (depth++ == 0) {
            var m = context.getMatrices();
            m.pushMatrix();
            m.translate(0f, -LIFT);
        }
    }

    public static void end(DrawContext context) {
        if (!rich.screens.hud.impl.Hotbar.replacesVanilla()) return;
        if (depth > 0 && --depth == 0) {
            context.getMatrices().popMatrix();
        }
    }
}
