package rich.screens.clickgui.dropdown.tab.impl;

import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;
import rich.screens.clickgui.dropdown.components.SettingAnimationController;
import rich.screens.clickgui.dropdown.components.impl.ColorPicker;
import rich.screens.clickgui.dropdown.tab.PanelTab;
import rich.screens.clickgui.dropdown.tab.SidePanel;
import rich.screens.clickgui.dropdown.theme.GuiTheme;
import rich.screens.clickgui.dropdown.theme.SavedTheme;
import rich.screens.clickgui.dropdown.theme.Theme;
import rich.screens.clickgui.dropdown.theme.ThemeManager;
import rich.util.render.Render2D;

import java.awt.Color;
import java.util.List;

/**
 * Theme tab: edit the live GUI colours (Accent / Background / Text / Outline / Other) with a
 * floating picker, and save the current colours as a named theme. Lives in its own class so the
 * {@link SidePanel} container stays free of theme-specific blocks.
 */
public class ThemeTab implements PanelTab {

    private static final float NAME_H = 16f, SAVED_ROW = 14f, CLABEL_H = 13f, CROW = 14f;
    private static final float ADD_W = 16f;
    private static final float OPA_LABEL = 13f, OPA_ROW = 12f, TRACK_H = 4f;

    private static final String[] COLOR_LABELS = {"Accent", "Background", "Text", "Outline", "Other", "Shadow"};

    private final ColorPicker picker = new ColorPicker();
    private String nameText = "";
    private boolean nameFocused;
    private int draggingSlider = -1;   // -1 none, else index into SLIDER_*

    // 0 Opacity, 1 Strength, 2 Distortion, 3 Blur (liquid-glass knobs),
    // 4 Sh. Size (размер/растушёвка тени, 0 = выкл),
    // 5 Sh. Power (плотность тени = альфа цвета Shadow, синхронизирована с пикером),
    // 6 Radius (закругление гуи: окна напрямую, модули/контролы пропорционально)
    private static final String[] SLIDER_NAMES = {"Opacity", "Strength", "Distortion", "Blur", "Sh. Size", "Sh. Power", "Radius"};
    private static final float[]  SLIDER_MIN   = {0f, 0f, -0.2f, 0f, 0f, 0f, 0f};
    private static final float[]  SLIDER_MAX   = {1f, 1f,  0.2f, 8f, 20f, 1f, 16f};

    // per-element animation state (driven by SettingAnimationController)
    private final float[] sliderDisp = new float[SLIDER_NAMES.length];   // glided fill fraction
    private final float[] sliderGrab = new float[SLIDER_NAMES.length];   // knob grab 0..1
    private final float[] colorHover = new float[COLOR_LABELS.length];   // color row hover 0..1
    private boolean animPrimed = false;

    // save-feedback animation: plus-button pop + newest saved-row intro
    private float addPop;        // plus-button press bounce 1..0 (decays)
    private float rowIntro;      // newest saved-row reveal 0..1 (glides to 1)
    private int introIndex = -1; // index of the row currently animating in (-1 none)

    // delete animation: the row collapses (height + fade + slide-out) then is removed for real
    private float removeAnim = 1f; // 1 = full height, glides to 0 then the row is dropped
    private int removingIndex = -1; // index of the row animating out (-1 none)

    private float emptyFade;       // "No saved themes" fade-in (replays each time the list empties)

    @Override public String title() { return "Theme"; }

    @Override public boolean isTyping() { return nameFocused; }

    @Override public boolean modal() { return picker.isShowing(); }

    @Override
    public void icon(DrawContext ctx, float cx, float cy, float size, int color) {
        // accent swatch — same motif as the header in the mock-up
        float s = size;
        Render2D.rect(cx - s / 2f, cy - s / 2f, s, s, color, s / 3f);
    }

    private int savedCount() { return ThemeManager.INSTANCE.getSaved().size(); }

    /** Height of one saved row (SAVED_ROW, or the collapsing height while it animates out). */
    private float rowHeight(int i) { return SAVED_ROW * (i == removingIndex ? removeAnim : 1f); }

    private float savedHeight() {
        int n = savedCount();
        if (n == 0) return 12f * emptyFade;   // grows in with the placeholder so nothing below jumps
        float h = 0f;
        for (int i = 0; i < n; i++) h += rowHeight(i);
        return h;
    }

    @Override
    public float contentHeight() {
        return NAME_H + 4f + savedHeight() + 4f + CLABEL_H + COLOR_LABELS.length * CROW
                + 4f + SLIDER_NAMES.length * (OPA_LABEL + OPA_ROW) + 6f;
    }

    /* ============================== Colours ============================== */
    private static Color getColor(GuiTheme t, int i) {
        switch (i) {
            case 0:  return t.accent;
            case 1:  return t.background;
            case 2:  return t.text;
            case 3:  return t.outline;
            case 4:  return t.extra;
            default: return t.shadow;
        }
    }

    private static void setColor(GuiTheme t, int i, Color c) {
        switch (i) {
            case 0:  t.accent = c; break;
            case 1:  t.background = c; break;
            case 2:  t.text = c; break;
            case 3:  t.outline = c; break;
            case 4:  t.extra = c; break;
            default: t.shadow = c;
        }
    }

    private void addTheme() {
        if (!nameText.isBlank()) {
            ThemeManager.INSTANCE.addCurrentAsTheme(nameText);
            nameText = "";
            nameFocused = false;
            // kick off the save feedback: pop the plus, reveal the new (last) row
            addPop = 1f;
            rowIntro = 0f;
            introIndex = ThemeManager.INSTANCE.getSaved().size() - 1;
        }
    }

    /* ============================== Render ============================== */
    @Override
    public void render(DrawContext ctx, Region r, float mx, float my, float alpha) {
        float cx = r.x(), cw = r.w();
        GuiTheme cur = ThemeManager.INSTANCE.current();
        float oy = r.y();

        // name field + add button
        float boxW = cw - 16f - ADD_W - 4f;
        float bx = cx + 8f, byy = oy;
        Render2D.rect(bx, byy, boxW, 14f, Theme.color(Theme.CONTROL_BG, alpha), 4f);
        if (nameFocused) Render2D.outline(bx, byy, boxW, 14f, 0.5f, Theme.accent(alpha), 4f);
        String shown = nameText.isEmpty() && !nameFocused ? "Name" : (nameFocused ? nameText + "_" : nameText);
        Theme.FONT.draw(shown, bx + 4f, byy + 7f - Theme.SMALL_SIZE / 2f, Theme.SMALL_SIZE,
                Theme.color(nameText.isEmpty() && !nameFocused ? Theme.TEXT_DESC : Theme.TEXT_ON, alpha));
        float addX = bx + boxW + 4f;
        // decay the click bounce toward rest; button inflates & the glyph pops on save
        addPop = SettingAnimationController.approach(addPop, 0f, 11f);
        float infl = addPop * 1.5f;                 // button grows a touch
        float glyph = 5f * (1f + addPop * 0.35f);   // plus arms stretch then settle
        Render2D.rect(addX - infl, byy - infl, ADD_W + infl * 2f, 14f + infl * 2f, Theme.accent(alpha), 4f);
        float pcx = addX + ADD_W / 2f, pcy = byy + 7f;
        Render2D.rect(pcx - glyph / 2f, pcy - 0.55f, glyph, 1.1f, Theme.color(Color.WHITE, alpha), 0.5f);
        Render2D.rect(pcx - 0.55f, pcy - glyph / 2f, 1.1f, glyph, Theme.color(Color.WHITE, alpha), 0.5f);
        oy += NAME_H;

        // saved themes
        List<SavedTheme> saved = ThemeManager.INSTANCE.getSaved();
        if (saved.isEmpty()) {
            // grow the placeholder height in sync with the fade so the rows below ease down (no jump)
            emptyFade = SettingAnimationController.approach(emptyFade, 1f, 8f);
            float eh = 12f * emptyFade;
            Theme.FONT.draw("No saved themes", cx + 8f, oy + eh / 2f - Theme.SMALL_SIZE / 2f, Theme.SMALL_SIZE,
                    Theme.color(Theme.TEXT_DESC, alpha * emptyFade));
            oy += eh;
        } else {
            emptyFade = 0f;   // armed to re-fade next time the list empties
            if (removingIndex >= saved.size()) removingIndex = -1;   // stale guard
            int pendingRemove = -1;
            for (int i = 0; i < saved.size(); i++) {
                SavedTheme t = saved.get(i);
                float rowH = SAVED_ROW, f = 1f, ox = cx;
                boolean intro = false;

                if (i == removingIndex) {
                    // collapse: height + fade + slide out to the right, then drop the row
                    removeAnim = SettingAnimationController.approach(removeAnim, 0f, 14f);
                    rowH = SAVED_ROW * removeAnim;
                    f = removeAnim;
                    ox = cx + (1f - removeAnim) * 14f;
                    if (removeAnim <= 0.01f) pendingRemove = i;
                } else if (i == introIndex) {
                    // newest row reveals: glides 0->1, fade + slide-in + accent flash
                    rowIntro = SettingAnimationController.approach(rowIntro, 1f, 14f);
                    f = rowIntro;
                    ox = cx + (1f - rowIntro) * 12f;
                    intro = true;
                    if (rowIntro >= 0.999f) introIndex = -1;
                }

                float ra = alpha * f;                     // fade
                float midY = oy + rowH / 2f;              // stay centred as the row collapses
                // полуоткрытый диапазон (< вместо <=): строки идут вплотную, и общая
                // граница иначе засчитывается ОБЕИМ строкам — двойная подсветка
                boolean hov = i != removingIndex && mx >= cx && mx <= cx + cw && my >= oy && my < oy + rowH;
                if (hov) Render2D.rect(cx + 3f, oy + 1f, cw - 6f, Math.max(0f, rowH - 2f), Theme.color(new Color(255, 255, 255, 12), ra), 4f);
                // brief accent flash behind the freshly saved row, fading as it settles
                if (intro && f < 1f) Render2D.rect(cx + 3f, oy + 1f, cw - 6f, SAVED_ROW - 2f, Theme.accent(alpha * (1f - f) * 0.45f), 4f);
                Render2D.rect(ox + 8f, midY - 4f, 8f, 8f, Theme.color(t.colors.accent, ra), 2.5f);
                Theme.FONT.draw(t.name, ox + 20f, midY - Theme.SETTING_SIZE / 2f, Theme.SETTING_SIZE,
                        Theme.color(hov ? Theme.TEXT_ON : Theme.TEXT_OFF, ra));
                Theme.FONT.draw("x", cx + cw - 11f, midY - Theme.SETTING_SIZE / 2f, Theme.SETTING_SIZE,
                        Theme.color(Theme.TEXT_DESC, ra));
                oy += rowH;
            }
            // finalise the delete once its collapse finished (after the loop to avoid a mid-iteration edit)
            if (pendingRemove >= 0) {
                ThemeManager.INSTANCE.removeTheme(pendingRemove);
                if (introIndex == pendingRemove) introIndex = -1;
                else if (introIndex > pendingRemove) introIndex--;
                removingIndex = -1;
                removeAnim = 1f;
            }
        }
        oy += 4f;

        // "Colors" label
        Theme.FONT.draw("Colors", cx + 8f, oy + CLABEL_H / 2f - Theme.SMALL_SIZE / 2f, Theme.SMALL_SIZE,
                Theme.color(Theme.TEXT_OFF, alpha));
        oy += CLABEL_H;

        // color rows
        for (int i = 0; i < COLOR_LABELS.length; i++) {
            // полуоткрытый диапазон: строки цветов идут вплотную (шаг = CROW),
            // «<=» на общей границе подсвечивал бы сразу две соседние строки
            boolean hov = mx >= cx && mx <= cx + cw && my >= oy && my < oy + CROW;
            float ch = colorHover[i] = SettingAnimationController.hover(colorHover[i], hov);
            if (ch > 0.001f)
                Render2D.rect(cx + 3f, oy + 1f, cw - 6f, CROW - 2f, Theme.color(new Color(255, 255, 255, 12), alpha * ch), 4f);
            Theme.FONT.draw(COLOR_LABELS[i], cx + 8f, oy + CROW / 2f - Theme.SETTING_SIZE / 2f - 1f, Theme.SETTING_SIZE,
                    Theme.color(Theme.lerp(Theme.TEXT_OFF, Theme.TEXT_ON, ch), alpha));
            // swatch grows slightly on hover (kept centred)
            float base = 9f, sw = base + ch * 1.6f;
            float scx = cx + cw - 8f - base / 2f, scy = oy + CROW / 2f;
            float sx = scx - sw / 2f, sy = scy - sw / 2f;
            Render2D.rect(sx, sy, sw, sw, Theme.color(getColor(cur, i), alpha), 2.5f);
            Render2D.outline(sx, sy, sw, sw, 0.5f, Theme.color(new Color(255, 255, 255, 40), alpha), 2.5f);
            oy += CROW;
        }

        // liquid-glass sliders: Opacity / Strength / Distortion / Blur
        oy += 4f;
        float tx = cx + 8f, tw = cw - 16f;
        // prime the glide state to the current values on first render (no intro sweep)
        if (!animPrimed) {
            for (int i = 0; i < SLIDER_NAMES.length; i++) {
                sliderDisp[i] = clamp01((getSliderValue(cur, i) - SLIDER_MIN[i]) / (SLIDER_MAX[i] - SLIDER_MIN[i]));
            }
            animPrimed = true;
        }
        for (int i = 0; i < SLIDER_NAMES.length; i++) {
            float mn = SLIDER_MIN[i], mxv = SLIDER_MAX[i];
            float val = getSliderValue(cur, i);
            if (draggingSlider == i) {
                val = mn + (mxv - mn) * clamp01((mx - tx) / tw);
                setSliderValue(cur, i, val);
                ThemeManager.INSTANCE.apply();
            }
            float target = clamp01((val - mn) / (mxv - mn));
            // animated: fill glides toward the value, knob "grabs" (grows) while dragged
            sliderDisp[i] = SettingAnimationController.slider(sliderDisp[i], target);
            sliderGrab[i] = SettingAnimationController.grab(sliderGrab[i], draggingSlider == i);
            float frac = sliderDisp[i];
            float g = sliderGrab[i];

            Theme.FONT.draw(SLIDER_NAMES[i], cx + 8f, oy + OPA_LABEL / 2f - Theme.SMALL_SIZE / 2f, Theme.SMALL_SIZE,
                    Theme.color(Theme.TEXT_OFF, alpha));
            String vs = sliderText(i, val);
            float pw = Theme.FONT.getWidth(vs, Theme.SMALL_SIZE);
            Theme.FONT.draw(vs, cx + cw - 8f - pw, oy + OPA_LABEL / 2f - Theme.SMALL_SIZE / 2f, Theme.SMALL_SIZE,
                    Theme.color(Theme.TEXT_DESC, alpha));
            oy += OPA_LABEL;

            float ty = oy + (OPA_ROW - TRACK_H) / 2f;
            Render2D.rect(tx, ty, tw, TRACK_H, Theme.color(Theme.CONTROL_BG, alpha), TRACK_H / 2f);
            Render2D.rect(tx, ty, tw * frac, TRACK_H, Theme.accent(alpha), TRACK_H / 2f);
            float knobX = tx + tw * frac;
            float kw = 5f + g * 2.5f, khh = 4f + g * 1.5f;     // grab grow
            Render2D.rect(knobX - kw / 2f, ty + TRACK_H / 2f - khh, kw, khh * 2f, Theme.color(Color.WHITE, alpha), kw / 2f);
            // контрастное кольцо: белая шайба не сливается со светлой заливкой
            Render2D.outline(knobX - kw / 2f, ty + TRACK_H / 2f - khh, kw, khh * 2f, 0.6f,
                    Theme.color(new Color(22, 22, 26, 150), alpha), kw / 2f);
            oy += OPA_ROW;
        }
    }

    private static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }

    private static float getSliderValue(GuiTheme t, int i) {
        switch (i) {
            case 0:  return t.opacity;
            case 1:  return t.glassStrength;
            case 2:  return t.glassDistortion;
            case 3:  return t.glassBlur;
            case 4:  return t.shadowSize;
            case 5:  return t.shadow.getAlpha() / 255f;
            default: return t.radius;
        }
    }

    private static void setSliderValue(GuiTheme t, int i, float v) {
        switch (i) {
            case 0:  t.opacity = v; break;
            case 1:  t.glassStrength = v; break;
            case 2:  t.glassDistortion = v; break;
            case 3:  t.glassBlur = v; break;
            case 4:  t.shadowSize = v; break;
            case 5:  t.shadow = new Color(t.shadow.getRed(), t.shadow.getGreen(),
                    t.shadow.getBlue(), Math.round(clamp01(v) * 255f)); break;
            default: t.radius = v;
        }
    }

    private static String sliderText(int i, float v) {
        switch (i) {
            case 2:  return String.format("%.2f", v);
            case 3:  return String.format("%.1f", v);
            case 4:
            case 6:  return Math.round(v) + "px";
            default: return Math.round(v * 100f) + "%";
        }
    }

    @Override
    public void renderModal(DrawContext ctx, Region r, float mx, float my, float alpha) {
        picker.render(ctx, mx, my, alpha);
    }

    /* ============================== Input ============================== */
    @Override
    public boolean mouseClicked(Region r, float mx, float my, int button) {
        if (picker.isShowing()) {
            picker.mouseClicked(mx, my, button);
            return true;
        }

        float cx = r.x(), cw = r.w();
        float oy = r.y();

        // name field / add button
        float boxW = cw - 16f - ADD_W - 4f;
        float bx = cx + 8f, addX = bx + boxW + 4f;
        if (button == 0 && my >= oy && my <= oy + 14f) {
            if (mx >= addX && mx <= addX + ADD_W) { addTheme(); return true; }
            nameFocused = mx >= bx && mx <= bx + boxW;
            return true;
        }
        nameFocused = false;
        oy += NAME_H;

        // saved themes
        List<SavedTheme> saved = ThemeManager.INSTANCE.getSaved();
        if (saved.isEmpty()) {
            oy += 12f * emptyFade;
        } else {
            for (int i = 0; i < saved.size(); i++) {
                float rowH = rowHeight(i);
                if (mx >= cx && mx <= cx + cw && my >= oy && my < oy + rowH && button == 0) {
                    if (mx >= cx + cw - 16f) {
                        // start the collapse; the render pass removes it once it finishes
                        if (removingIndex == -1) { removingIndex = i; removeAnim = 1f; }
                    } else {
                        ThemeManager.INSTANCE.applyTheme(i);
                    }
                    return true;
                }
                oy += rowH;
            }
        }
        oy += 4f + CLABEL_H;

        // color rows -> open picker to the left of the whole panel
        GuiTheme cur = ThemeManager.INSTANCE.current();
        for (int i = 0; i < COLOR_LABELS.length; i++) {
            if (mx >= cx && mx <= cx + cw && my >= oy && my < oy + CROW && button == 0) {
                final int ci = i;
                // у тени (Shadow, индекс 5) сила задаётся отдельным слайдером
                // Sh. Power — полоса альфы в пикере дублировала бы его, поэтому
                // для тени пикер открывается без неё (альфа сохраняется как есть)
                boolean withAlpha = ci != 5;
                picker.open(r.panelX() - picker.width() - 4f, oy, getColor(cur, ci), withAlpha, c -> {
                    setColor(cur, ci, c);
                    ThemeManager.INSTANCE.apply();
                });
                return true;
            }
            oy += CROW;
        }

        // liquid-glass slider tracks (label + track row each clickable)
        oy += 4f;
        float tx = cx + 8f, tw = cw - 16f;
        for (int i = 0; i < SLIDER_NAMES.length; i++) {
            if (button == 0 && my >= oy && my < oy + OPA_LABEL + OPA_ROW) {
                draggingSlider = i;
                setSliderValue(cur, i, SLIDER_MIN[i] + (SLIDER_MAX[i] - SLIDER_MIN[i]) * clamp01((mx - tx) / tw));
                ThemeManager.INSTANCE.apply();
                return true;
            }
            oy += OPA_LABEL + OPA_ROW;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(float mx, float my, int button) {
        if (draggingSlider != -1) {
            draggingSlider = -1;
            ThemeManager.INSTANCE.save();
            return true;
        }
        if (picker.mouseReleased(mx, my, button)) {
            ThemeManager.INSTANCE.save();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (picker.isShowing() && key == GLFW.GLFW_KEY_ESCAPE) { picker.close(); return true; }
        if (!nameFocused) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE) { nameFocused = false; return true; }
        if (key == GLFW.GLFW_KEY_ENTER) { addTheme(); return true; }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (!nameText.isEmpty()) nameText = nameText.substring(0, nameText.length() - 1);
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(char chr, int mods) {
        if (!nameFocused) return false;
        if (chr >= 32 && nameText.length() < 18) nameText += chr;
        return true;
    }
}
