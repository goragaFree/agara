package rich.screens.clickgui.dropdown.components.impl;

import net.minecraft.client.gui.DrawContext;
import rich.modules.module.setting.implement.ColorSetting;
import rich.screens.clickgui.dropdown.components.SettingAnimationController;
import rich.screens.clickgui.dropdown.theme.Theme;
import rich.screens.clickgui.dropdown.components.SettingComponent;
import rich.util.render.Render2D;
import rich.util.render.shader.Scissor;

import java.awt.Color;

/** Expandable HSB + alpha picker for a {@link ColorSetting}. */
public class ColorComp extends SettingComponent {
    private final ColorSetting s;
    private boolean expanded;
    private float anim;
    private int drag; // 0 none, 1 sb, 2 hue, 3 alpha
    private static final float SB = 46f, BAR = 6f, GAP = 4f;
    private static final float SCISSOR_SCALE = 2f;

    // smoothed marker positions so the picker doesn't snap
    private float dHue = Float.NaN, dSat = Float.NaN, dBri = Float.NaN, dAlpha = Float.NaN;
    private float grab;

    public ColorComp(ColorSetting s) { super(s); this.s = s; }

    private float pickerExtra() {
        return SB + GAP + 8f + GAP;
    }

    @Override
    public float getHeight() {
        return 14f + anim * pickerExtra();
    }

    @Override
    public void render(DrawContext ctx, float mx, float my, float delta) {
        anim = SettingAnimationController.expand(anim, expanded);

        float cy = y + 7f;
        drawText(s.getName(), x, cy - Theme.SETTING_SIZE / 2f, Theme.SETTING_SIZE, Theme.TEXT_OFF);

        float pw = 14f, ph = 8f, pxx = x + width - pw, pyy = cy - ph / 2f;
        Render2D.rect(pxx, pyy, pw, ph, Theme.color(s.getAwtColor(), alpha), 2f);
        Render2D.outline(pxx, pyy, pw, ph, 0.5f, Theme.color(Theme.CONTROL_LINE, alpha), 2f);

        if (anim <= 0.001f) return;

        Scissor.enable(x, y + 14f, width, anim * pickerExtra(), SCISSOR_SCALE);

        float sbX = x, sbY = y + 14f, sbW = width - BAR - GAP;
        updateDrag(mx, my, sbX, sbY, sbW);

        // smooth the displayed markers (and the square hue) toward the real values
        dHue = Float.isNaN(dHue) ? s.getHue() : SettingAnimationController.marker(dHue, s.getHue());
        dSat = Float.isNaN(dSat) ? s.getSaturation() : SettingAnimationController.marker(dSat, s.getSaturation());
        dBri = Float.isNaN(dBri) ? s.getBrightness() : SettingAnimationController.marker(dBri, s.getBrightness());
        dAlpha = Float.isNaN(dAlpha) ? s.getAlpha() : SettingAnimationController.marker(dAlpha, s.getAlpha());
        grab = SettingAnimationController.grab(grab, drag != 0);

        // saturation / brightness square
        int hueColor = Color.HSBtoRGB(dHue, 1f, 1f) | 0xFF000000;
        int white = 0xFFFFFFFF, black = 0xFF000000;
        Render2D.gradientRect(sbX, sbY, sbW, SB, new int[]{withA(white), withA(hueColor), withA(black), withA(black)}, 3f);
        float sx = sbX + dSat * sbW;
        float sy = sbY + (1f - dBri) * SB;
        float cur = 2f + grab * 1.5f;
        Render2D.outline(sx - cur, sy - cur, cur * 2f, cur * 2f, 0.8f, Theme.color(Color.WHITE, alpha), cur);

        // hue bar (vertical)
        float hueX = sbX + sbW + GAP;
        int steps = (int) SB;
        for (int i = 0; i < steps; i++) {
            int c = Color.HSBtoRGB(i / (float) steps, 1f, 1f) | 0xFF000000;
            Render2D.rect(hueX, sbY + i, BAR, 1.2f, withA(c), 0f);
        }
        Render2D.rect(hueX - 1f, sbY + dHue * SB - 1f, BAR + 2f, 2f, Theme.color(Color.WHITE, alpha), 1f);

        // alpha bar (horizontal)
        float aY = sbY + SB + GAP;
        int base = s.getColorNoAlpha();
        int c0 = base & 0x00FFFFFF;
        int c1 = base | 0xFF000000;
        Render2D.gradientRect(sbX, aY, width, 6f, new int[]{withA(c0), withA(c1), withA(c1), withA(c0)}, 3f);
        Render2D.rect(sbX + dAlpha * width - 1f, aY - 1f, 2f, 8f, Theme.color(Color.WHITE, alpha), 1f);

        Scissor.disable();
    }

    private int withA(int rgb) { return Theme.color(new Color(rgb, true), alpha); }

    private void updateDrag(float mx, float my, float sbX, float sbY, float sbW) {
        if (drag == 1) {
            s.setSaturation((mx - sbX) / sbW);
            s.setBrightness(1f - (my - sbY) / SB);
        } else if (drag == 2) {
            s.setHue((my - sbY) / SB);
        } else if (drag == 3) {
            s.setAlpha((mx - sbX) / width);
        }
    }

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        float cy = y + 7f, pw = 14f, ph = 8f, pxx = x + width - pw, pyy = cy - ph / 2f;
        if (button == 0 && mx >= pxx && mx <= pxx + pw && my >= pyy && my <= pyy + ph) {
            expanded = !expanded;
            return true;
        }
        if (!expanded || button != 0) return false;

        float sbX = x, sbY = y + 14f, sbW = width - BAR - GAP;
        if (mx >= sbX && mx <= sbX + sbW && my >= sbY && my <= sbY + SB) { drag = 1; updateDrag(mx, my, sbX, sbY, sbW); return true; }
        float hueX = sbX + sbW + GAP;
        if (mx >= hueX && mx <= hueX + BAR && my >= sbY && my <= sbY + SB) { drag = 2; updateDrag(mx, my, sbX, sbY, sbW); return true; }
        float aY = sbY + SB + GAP;
        if (mx >= sbX && mx <= sbX + width && my >= aY && my <= aY + 6f) { drag = 3; updateDrag(mx, my, sbX, sbY, sbW); return true; }
        return false;
    }

    @Override
    public boolean mouseReleased(float mx, float my, int button) {
        if (drag != 0) { drag = 0; return true; }
        return false;
    }
}
