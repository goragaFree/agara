package rich.screens.clickgui.dropdown.components.impl;

import net.minecraft.client.gui.DrawContext;
import rich.screens.clickgui.dropdown.theme.Theme;
import rich.util.render.Render2D;

import java.awt.Color;
import java.util.function.Consumer;

//эта хуйня для таба в темс
/** A small floating HSB + alpha color picker used by the theme panel. */
public class ColorPicker {

    private static final float PAD = 6f, SB = 62f, BAR = 7f, GAP = 5f, SBW = 72f;
    private static final float W = PAD * 2f + SBW + GAP + BAR;

    private boolean showing;
    private float x, y;
    private float hue, sat, bri, alpha;
    /** Полоса альфы внизу; false — без неё (например, тень: сила — отдельным слайдером Sh. Power). */
    private boolean showAlpha = true;
    private int drag; // 0 none, 1 sb, 2 hue, 3 alpha
    private Consumer<Color> onChange;

    // smoothed marker positions + cursor "grab" (same feel as the module ColorComp)
    private float dHue = Float.NaN, dSat = Float.NaN, dBri = Float.NaN, dAlpha = Float.NaN;
    private long lastNanos;
    private float grab;

    // open/close reveal: fade + slide. close() animates out, then the render pass hides for real.
    private float anim;         // 0 = hidden, 1 = fully shown
    private boolean targetOpen; // desired visible state

    public float width() { return W; }
    public float height() { return PAD * 2f + SB + (showAlpha ? GAP + 6f : 0f); }
    public boolean isShowing() { return showing; }

    public void open(float px, float py, Color c, Consumer<Color> cb) {
        open(px, py, c, true, cb);
    }

    /** @param withAlpha показывать ли полосу альфы; false — альфа исходного цвета сохраняется как есть. */
    public void open(float px, float py, Color c, boolean withAlpha, Consumer<Color> cb) {
        this.showAlpha = withAlpha;
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        hue = hsb[0]; sat = hsb[1]; bri = hsb[2];
        alpha = c.getAlpha() / 255f;
        onChange = cb;
        x = px; y = py;
        showing = true;
        targetOpen = true;
        anim = 0f;          // grow + fade in from the panel edge
        drag = 0;
        // snap markers to the opened color (don't glide in from a previous one)
        dHue = dSat = dBri = dAlpha = Float.NaN;
        grab = 0f;
        lastNanos = 0L;
    }

    /** Begin the close-out; the render pass fades/slides it away, then hides for real. */
    public void close() { targetOpen = false; drag = 0; }

    public boolean contains(float mx, float my) {
        return showing && mx >= x && mx <= x + W && my >= y && my <= y + height();
    }

    public void render(DrawContext ctx, float mx, float my, float a) {
        if (!showing) return;
        updateDrag(mx, my);

        // smooth the displayed markers (and the square hue) toward the real values
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 1f / 60f : Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
        lastNanos = now;
        float k = 1f - (float) Math.exp(-14f * dt);
        dHue = sm(dHue, hue, k);
        dSat = sm(dSat, sat, k);
        dBri = sm(dBri, bri, k);
        dAlpha = sm(dAlpha, alpha, k);
        grab = lerp(grab, drag != 0 ? 1f : 0f, 0.25f);

        // advance the open/close reveal; drop the picker once it has fully closed out
        anim = sm(anim, targetOpen ? 1f : 0f, 1f - (float) Math.exp(-18f * dt));
        if (!targetOpen && anim <= 0.02f) { showing = false; return; }
        float e = anim * anim * (3f - 2f * anim);   // smoothstep for the fade + slide
        float pa = a * e;                            // faded picker alpha
        float savedX = x;
        x += (1f - e) * 10f;                         // emerges from the panel edge, settling into place

        Render2D.rect(x, y, W, height(), Theme.color(new Color(18, 18, 22, 246), pa), 5f);
        Render2D.outline(x, y, W, height(), 0.5f, Theme.color(new Color(255, 255, 255, 30), pa), 5f);

        float sbX = x + PAD, sbY = y + PAD;

        int hueColor = Color.HSBtoRGB(dHue, 1f, 1f) | 0xFF000000;
        Render2D.gradientRect(sbX, sbY, SBW, SB,
                new int[]{wa(0xFFFFFFFF, pa), wa(hueColor, pa), wa(0xFF000000, pa), wa(0xFF000000, pa)}, 3f);
        float cur = 2f + grab * 1.5f;
        Render2D.outline(sbX + dSat * SBW - cur, sbY + (1f - dBri) * SB - cur, cur * 2f, cur * 2f, 0.8f,
                Theme.color(Color.WHITE, pa), cur);

        float hueX = sbX + SBW + GAP;
        int steps = (int) SB;
        for (int i = 0; i < steps; i++) {
            int c = Color.HSBtoRGB(i / (float) steps, 1f, 1f) | 0xFF000000;
            Render2D.rect(hueX, sbY + i, BAR, 1.2f, wa(c, pa), 0f);
        }
        Render2D.rect(hueX - 1f, sbY + dHue * SB - 1f, BAR + 2f, 2f, Theme.color(Color.WHITE, pa), 1f);

        if (showAlpha) {
            float aY = sbY + SB + GAP, aW = W - PAD * 2f;
            int base = Color.HSBtoRGB(dHue, dSat, dBri) | 0xFF000000;
            Render2D.gradientRect(sbX, aY, aW, 6f,
                    new int[]{wa(base & 0x00FFFFFF, pa), wa(base, pa), wa(base, pa), wa(base & 0x00FFFFFF, pa)}, 3f);
            Render2D.rect(sbX + dAlpha * aW - 1f, aY - 1f, 2f, 8f, Theme.color(Color.WHITE, pa), 1f);
        }

        x = savedX;   // restore the rest position so hit-testing stays aligned
    }

    private float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    private float sm(float cur, float target, float k) {
        if (Float.isNaN(cur)) return target;
        float v = cur + (target - cur) * k;
        return Math.abs(target - v) < 0.0005f ? target : v;
    }

    public boolean mouseClicked(float mx, float my, int button) {
        if (!showing) return false;
        if (button != 0) return contains(mx, my);

        float sbX = x + PAD, sbY = y + PAD, aW = W - PAD * 2f;
        if (in(mx, my, sbX, sbY, SBW, SB)) { drag = 1; updateDrag(mx, my); return true; }
        float hueX = sbX + SBW + GAP;
        if (in(mx, my, hueX, sbY, BAR, SB)) { drag = 2; updateDrag(mx, my); return true; }
        if (showAlpha) {
            float aY = sbY + SB + GAP;
            if (in(mx, my, sbX, aY, aW, 6f)) { drag = 3; updateDrag(mx, my); return true; }
        }
        if (!contains(mx, my)) { close(); return false; }
        return true;
    }

    public boolean mouseReleased(float mx, float my, int button) {
        if (drag != 0) { drag = 0; return true; }
        return false;
    }

    private void updateDrag(float mx, float my) {
        if (drag == 0) return;
        float sbX = x + PAD, sbY = y + PAD, aW = W - PAD * 2f;
        if (drag == 1) {
            sat = clamp((mx - sbX) / SBW);
            bri = clamp(1f - (my - sbY) / SB);
        } else if (drag == 2) {
            hue = clamp((my - sbY) / SB);
        } else if (drag == 3) {
            alpha = clamp((mx - sbX) / aW);
        }
        fire();
    }

    private void fire() {
        if (onChange != null) onChange.accept(current());
    }

    private Color current() {
        int rgb = Color.HSBtoRGB(hue, sat, bri) & 0x00FFFFFF;
        int al = Math.round(alpha * 255f);
        return new Color((al << 24) | rgb, true);
    }

    private int wa(int argb, float a) { return Theme.color(new Color(argb, true), a); }

    private boolean in(float mx, float my, float bx, float by, float bw, float bh) {
        return mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
    }

    private float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }
}
