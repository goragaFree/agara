package rich.screens.clickgui.dropdown.tab.impl;

import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;
import rich.screens.clickgui.dropdown.components.SettingAnimationController;
import rich.screens.clickgui.dropdown.tab.PanelTab;
import rich.screens.clickgui.dropdown.theme.Theme;
import rich.util.config.impl.bind.BindConfig;
import rich.util.render.Render2D;

import java.awt.Color;

/**
 * Settings tab — глобальные настройки гуи. Строка «Open GUI» позволяет
 * перебиндить клавишу открытия меню (по умолчанию RShift; хранится в
 * Bind.json через {@link BindConfig}, той же настройкой пользуется
 * команда .bind gui). Поведение капсулы — как у биндов модулей:
 * клик — режим прослушивания («...», акцентная заливка с «дыханием»),
 * следующая клавиша становится биндом, Escape — отмена, Delete — сброс
 * на RShift. Кнопки мыши намеренно НЕ биндятся: открытие меню слушает
 * только клавиатуру (KeyboardMixin), мышиный бинд молча не работал бы.
 */
public class SettingsTab implements PanelTab {

    private static final float SECTION_H = 13f;   // подпись секции
    private static final float ROW_H     = 16f;   // строка с биндом
    private static final float HINT_H    = 10f;   // подсказка под строкой
    private static final int   DEFAULT_KEY = GLFW.GLFW_KEY_RIGHT_SHIFT;

    /** Ждём следующую клавишу для бинда открытия. */
    private boolean listening;

    private float hover;    // ховер строки 0..1
    private float listenA;  // режим прослушивания 0..1
    private float flash;    // вспышка после установки бинда 1..0
    private float pulse;    // таймер «дыхания» капсулы

    @Override public String title() { return "Settings"; }

    @Override public float contentHeight() { return SECTION_H + ROW_H + HINT_H + 8f; }

    /** Пока слушаем клавишу — держим ввод внутри панели (как фокус текстового поля). */
    @Override public boolean isTyping() { return listening; }

    @Override
    public void icon(DrawContext ctx, float cx, float cy, float size, int color) {
        // three slider rows (a "settings" glyph) drawn from primitives
        float s = size, line = Math.max(1f, s / 7f);
        for (int i = 0; i < 3; i++) {
            float ly = cy - s / 2f + i * (s / 2f);
            Render2D.rect(cx - s / 2f, ly - line / 2f, s, line, color, line / 2f);
            float kx = cx - s / 2f + (i == 1 ? s * 0.15f : s * 0.65f);
            Render2D.rect(kx, ly - line * 1.4f, line * 2.2f, line * 2.8f, color, line);
        }
    }

    @Override
    public void render(DrawContext ctx, Region r, float mx, float my, float alpha) {
        float cx = r.x(), cw = r.w();
        float oy = r.y();
        float dt = SettingAnimationController.dt();

        // подпись секции
        Theme.FONT.draw("Menu", cx + 8f, oy + SECTION_H / 2f - Theme.SMALL_SIZE / 2f,
                Theme.SMALL_SIZE, Theme.color(Theme.TEXT_OFF, alpha));
        oy += SECTION_H;

        // ── строка «Open GUI» + капсула бинда (в стиле BindComp) ──────────
        boolean hov = mx >= cx && mx <= cx + cw && my >= oy && my < oy + ROW_H;
        hover   = SettingAnimationController.hover(hover, hov);
        listenA = SettingAnimationController.approach(listenA, listening ? 1f : 0f, 16f);
        flash   = SettingAnimationController.approach(flash, 0f, 9f);
        pulse   = listening ? pulse + dt : 0f;
        float breathe = 0.5f + 0.5f * (float) Math.sin(pulse * 6.5f);

        float rcy = oy + ROW_H / 2f;
        Theme.FONT.draw("Open GUI", cx + 8f, rcy - Theme.SETTING_SIZE / 2f, Theme.SETTING_SIZE,
                Theme.color(Theme.lerp(Theme.TEXT_OFF, Theme.TEXT_ON, hover), alpha));

        String txt = listening ? "..." : Theme.keyName(BindConfig.getInstance().getBindKey());
        float tw = Theme.FONT.getWidth(txt, Theme.SMALL_SIZE);
        float bw = Math.max(18f, tw + 8f), bh = 9f;
        float bx = cx + cw - 8f - bw, by = rcy - bh / 2f;

        // pop после установки + «дыхание» пока слушаем (раздувание от центра)
        float infl = flash * 1.6f + listenA * breathe * 0.8f;
        float dbx = bx - infl, dby = by - infl, dbw = bw + infl * 2f, dbh = bh + infl * 2f;

        Color fill = Theme.lerp(Theme.CONTROL_BG, Theme.ACCENT, listenA);
        Render2D.rect(dbx, dby, dbw, dbh, Theme.color(fill, alpha), Theme.CONTROL_RADIUS);

        if (listenA > 0.001f) {
            float glow = listenA * (0.35f + 0.65f * breathe);
            Render2D.outline(dbx, dby, dbw, dbh, 0.8f,
                    Theme.color(Theme.ACCENT, alpha * glow), Theme.CONTROL_RADIUS);
        }
        if (flash > 0.001f) {
            Render2D.rect(dbx, dby, dbw, dbh,
                    Theme.color(new Color(255, 255, 255, 120), alpha * flash), Theme.CONTROL_RADIUS);
        }

        // текст капсулы: в прослушивании — контрастный к акцентной заливке
        Theme.FONT.draw(txt, bx + (bw - tw) / 2f, by + (bh - Theme.SMALL_SIZE) / 2f, Theme.SMALL_SIZE,
                listening ? Theme.color(Theme.onAccent(), alpha)
                        : Theme.color(Theme.lerp(Theme.TEXT_OFF, Theme.TEXT_ON, hover), alpha));
        oy += ROW_H;

        // подсказка (появляется в режиме прослушивания)
        if (listenA > 0.01f) {
            Theme.FONT.draw("Esc — отмена, Delete — сброс", cx + 8f,
                    oy + HINT_H / 2f - Theme.SMALL_SIZE / 2f, Theme.SMALL_SIZE,
                    Theme.color(Theme.TEXT_DESC, alpha * listenA));
        }
    }

    /* ============================== Ввод ============================== */

    @Override
    public boolean mouseClicked(Region r, float mx, float my, int button) {
        // мышь не может открывать меню (KeyboardMixin слушает только
        // клавиатуру) — в режиме прослушивания клик просто отменяет его
        if (listening) {
            listening = false;
            return true;
        }

        float cx = r.x(), cw = r.w();
        float oy = r.y() + SECTION_H;
        if (button == 0 && mx >= cx && mx <= cx + cw && my >= oy && my < oy + ROW_H) {
            listening = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (!listening) return false;

        if (key == GLFW.GLFW_KEY_ESCAPE) {          // отмена — бинд не меняем
            listening = false;
            return true;
        }
        if (key == GLFW.GLFW_KEY_DELETE) {          // сброс на дефолтный RShift
            BindConfig.getInstance().setKeyAndSave(DEFAULT_KEY);
            listening = false;
            flash = 1f;
            return true;
        }
        if (key != GLFW.GLFW_KEY_UNKNOWN) {
            BindConfig.getInstance().setKeyAndSave(key);
            listening = false;
            flash = 1f;
        }
        return true;
    }
}
