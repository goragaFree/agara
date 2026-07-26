package rich.screens.clickgui.dropdown.components.impl;

import net.minecraft.client.gui.DrawContext;
import rich.modules.module.setting.implement.SliderSettings;
import rich.screens.clickgui.dropdown.components.SettingAnimationController;
import rich.screens.clickgui.dropdown.theme.Theme;
import rich.screens.clickgui.dropdown.components.SettingComponent;
import rich.util.render.Render2D;

import java.awt.Color;

/** Draggable bar for a {@link SliderSettings}. */
public class SliderComp extends SettingComponent {
    private final SliderSettings s;
    private boolean dragging;

    private float displayFrac = Float.NaN; // smoothed visual position
    private float grab; // knob grows while held

    public SliderComp(SliderSettings s) { super(s); this.s = s; }

    @Override public float getHeight() { return 21f; }

    private String valueText() {
        if (s.isInteger()) return String.valueOf(s.getInt());
        return String.format("%.2f", s.getValue());
    }

    @Override
    public void render(DrawContext ctx, float mx, float my, float delta) {
        drawText(s.getName(), x, y, Theme.SETTING_SIZE, Theme.TEXT_OFF);
        String vt = valueText();
        float vw = Theme.FONT.getWidth(vt, Theme.SETTING_SIZE);
        drawText(vt, x + width - vw, y, Theme.SETTING_SIZE, new Color(Theme.ACCENT.getRGB()));

        float barX = x, barY = y + 13f, barW = width, barH = 3f;

        if (dragging) {
            float t = (mx - barX) / barW;
            setFromFraction(t);
        }

        float frac = (s.getValue() - s.getMin()) / Math.max(0.0001f, (s.getMax() - s.getMin()));
        frac = Math.max(0f, Math.min(1f, frac));

        // smoothed visual position: the knob/fill glide toward the real value
        // instead of snapping, so dragging and clicks feel fluid.
        if (Float.isNaN(displayFrac)) displayFrac = frac;
        else displayFrac = SettingAnimationController.slider(displayFrac, frac);
        float disp = displayFrac;

        Render2D.rect(barX, barY, barW, barH, Theme.color(Theme.CONTROL_BG, alpha), barH / 2f);
        Render2D.rect(barX, barY, barW * disp, barH, Theme.accent(alpha), barH / 2f);

        // knob enlarges while grabbed, like you're holding it
        grab = SettingAnimationController.grab(grab, dragging);
        float kw = 5f + grab * 3f, kh = 6f + grab * 3f;
        float kcx = barX + barW * disp, kcy = barY + barH / 2f;
        Render2D.rect(kcx - kw / 2f, kcy - kh / 2f, kw, kh,
                Theme.color(new Color(255, 255, 255, 240), alpha), Math.min(kw, kh) / 2f);
        // тонкое тёмное кольцо вокруг шайбы: на белом/светлом акценте белая
        // шайба сливалась с заливкой трека — кольцо отделяет её на любом фоне
        // (на тёмном треке кольцо само незаметно, мешать не будет)
        Render2D.outline(kcx - kw / 2f, kcy - kh / 2f, kw, kh, 0.6f,
                Theme.color(new Color(22, 22, 26, 150), alpha), Math.min(kw, kh) / 2f);
    }

    private void setFromFraction(float t) {
        t = Math.max(0f, Math.min(1f, t));
        float value = s.getMin() + t * (s.getMax() - s.getMin());
        if (s.isInteger()) value = Math.round(value);
        s.setValue(value);
    }

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        if (button == 0 && mx >= x && mx <= x + width && my >= y + 9f && my <= y + getHeight()) {
            dragging = true;
            setFromFraction((mx - x) / width);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(float mx, float my, int button) {
        if (dragging) { dragging = false; return true; }
        return false;
    }
}
