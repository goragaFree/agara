package rich.screens.clickgui.dropdown.search;

import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;
import rich.IMinecraft;
import rich.screens.clickgui.dropdown.components.SettingAnimationController;
import rich.screens.clickgui.dropdown.theme.Theme;
import rich.util.render.Render2D;
import rich.util.render.batch.Batch;
import rich.util.render.font.Fonts;
import rich.util.render.shader.Scissor;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

// строка поиска модулей под панелями категорий

/**
 * Standalone search bar for the panel dropdown ClickGui. Written from scratch for the
 * dropdown menu (no code shared with the legacy {@code impl.background} search).
 *
 * <p>Visually it is a small glass panel: the exact same shadow / frosted-glass /
 * outline stack (and the same {@link Theme} colors) as the category panels, shaped
 * as a pill and centered below the panel row. It never paints its own colors — the
 * whole look re-skins together with the menu when the theme changes.</p>
 *
 * <p>Typing does not open a separate results popup: the query is pushed through
 * {@link #onQuery(Consumer)} and the category panels filter their module lists
 * in place.</p>
 */
public class SearchBar implements IMinecraft {

    private static final float SCISSOR_SCALE = 2f;

    /** Caret blink period, ms (one visible + one hidden phase). */
    private static final long BLINK_MS = 530L;
    /** Hard cap so the query can't grow silly long. */
    private static final int MAX_LENGTH = 64;
    /** Neutral highlight laid over the glass so the small pill doesn't read too dark. */
    private static final Color LIFT = new Color(255, 255, 255, 10);
    /** How fast typed characters fade in / deleted ones fade out (approach rate). */
    private static final float CHAR_IN_RATE = 22f, CHAR_OUT_RATE = 26f;

    /* ============================== State ============================== */
    private String text = "";
    private int cursor;                 // caret index, 0..text.length()
    private int selAnchor = -1;         // selection anchor, -1 = no selection
    private boolean focused;

    private float focusAnim;            // 0..1 — accent outline / icon brighten
    private float hoverAnim;            // 0..1 — subtle outline brighten under cursor
    private long lastEditMs;            // caret blink phase restarts on every edit

    private float viewShift;            // horizontal text scroll so the caret stays visible

    /** Per-character reveal 0..1, parallel to {@link #text} — fresh characters fade in. */
    private final List<Float> charAnim = new ArrayList<>();
    /** Just-deleted characters still fading out in place. */
    private final List<Ghost> ghosts = new ArrayList<>();

    private int resultCount = -1;       // matches across all panels (-1 = hidden)
    private Consumer<String> onQuery;

    /** A deleted glyph that keeps rendering for a moment while it dissolves. */
    private static final class Ghost {
        final String glyph;
        final float xOff;       // x of the glyph at deletion time, relative to the text start
        float anim = 1f;
        Ghost(String glyph, float xOff) { this.glyph = glyph; this.xOff = xOff; }
    }

    private float x, y, width, height;  // last laid-out geometry (for hit-testing)

    /* ============================== Access ============================== */
    public boolean isFocused() { return focused; }

    /** Lower-cased, trimmed query the panels filter by. */
    public String getQuery() { return text.trim().toLowerCase(); }

    /** Called with {@link #getQuery()} every time the text changes. */
    public void onQuery(Consumer<String> listener) { this.onQuery = listener; }

    /** Total number of matching modules (drawn small on the right); pass -1 to hide. */
    public void setResultCount(int count) { this.resultCount = count; }

    public boolean isHovered(float mx, float my) {
        return mx >= x && mx <= x + width && my >= y && my <= y + height;
    }

    /** Full reset: called when the menu opens and after the close animation finishes. */
    public void reset() {
        boolean hadText = !text.isEmpty();
        text = "";
        charAnim.clear();
        ghosts.clear();
        cursor = 0;
        selAnchor = -1;
        focused = false;
        viewShift = 0f;
        resultCount = -1;
        if (hadText) fireQuery();
    }

    /* ============================== Render ============================== */
    public void render(DrawContext ctx, float x, float y, float width, float height,
                       float mx, float my, float alpha) {
        this.x = x; this.y = y; this.width = width; this.height = height;

        float radius = height / 2f;   // pill
        focusAnim = SettingAnimationController.approach(focusAnim, focused ? 1f : 0f, 14f);
        hoverAnim = SettingAnimationController.approach(hoverAnim, isHovered(mx, my) ? 1f : 0f,
                SettingAnimationController.HOVER_RATE);

        // same background stack as the category panels (Panel.render), but a touch
        // lighter: a small pill over dark ground reads darker than the big panels,
        // so the theme tint is applied slightly weaker + a faint neutral lift on top.
        Color tint = Theme.GLASS_TINT;
        int barTint = new Color(tint.getRed(), tint.getGreen(), tint.getBlue(),
                Math.round(tint.getAlpha() * 0.76f)).getRGB();
        Render2D.shadow(x, y, width, height, radius, Theme.PANEL_SHADOW * 0.8f,
                Theme.color(Theme.SHADOW, alpha * 0.85f));
        Render2D.glass(x, y, width, height, radius, barTint, alpha);
        Render2D.rect(x, y, width, height, Theme.color(LIFT, alpha), radius);

        // outline: panel hairline that gently blends toward the theme accent on focus
        Color focusOutline = new Color(Theme.ACCENT.getRed(), Theme.ACCENT.getGreen(), Theme.ACCENT.getBlue(), 110);
        Color outline = Theme.lerp(Theme.PANEL_OUTLINE, focusOutline, focusAnim);
        float outlineBoost = 1f + 0.5f * hoverAnim * (1f - focusAnim);   // subtle hover hint while unfocused
        Render2D.outline(x, y, width, height, 1f,
                Theme.color(outline, Math.min(1f, alpha * outlineBoost)), radius);

        float cy = y + height / 2f;

        // --- static chrome: icon + match counter (not scissored) ---
        Batch.beginText();

        float iconSize = 9f;
        Color iconColor = Theme.lerp(Theme.TEXT_OFF, Theme.TEXT_ON, Math.max(focusAnim, hoverAnim * 0.5f));
        Fonts.ICONS.draw("U", x + 6f, cy - iconSize / 2f, iconSize, Theme.color(iconColor, alpha));

        float textLeft = x + 19f;
        float textRight = x + width - 8f;
        if (resultCount >= 0 && !text.isEmpty()) {
            String count = String.valueOf(resultCount);
            float cw = Theme.FONT.getWidth(count, Theme.SMALL_SIZE);
            Theme.FONT.draw(count, x + width - cw - 9f, cy - Theme.SMALL_SIZE / 2f,
                    Theme.SMALL_SIZE, Theme.color(Theme.TEXT_OFF, alpha));
            textRight = x + width - cw - 15f;
        }

        Batch.endText();

        // --- text area: selection, query / placeholder, caret (scissored) ---
        float viewW = textRight - textLeft;
        updateViewShift(viewW);

        Scissor.enable(textLeft - 1f, y, viewW + 2f, height, SCISSOR_SCALE);
        Batch.beginText();

        float ts = Theme.MODULE_SIZE;
        float tx = textLeft - viewShift;
        float ty = cy - ts / 2f;

        if (text.isEmpty()) {
            // placeholder recedes and slides clear of the caret while focused
            Theme.FONT.draw("Поиск", textLeft + 4f * focusAnim, ty, ts,
                    Theme.color(Theme.TEXT_OFF, alpha * (1f - 0.35f * focusAnim)));
        } else {
            if (hasSelection()) {
                float sx0 = tx + caretX(selMin());
                float sx1 = tx + caretX(selMax());
                Render2D.rect(sx0, cy - (ts + 4f) / 2f, sx1 - sx0, ts + 4f,
                        Theme.color(new Color(Theme.ACCENT.getRed(), Theme.ACCENT.getGreen(),
                                Theme.ACCENT.getBlue(), 70), alpha), 2f);
            }
            // per-character reveal: freshly typed characters fade + rise into place.
            // Pure eye-candy — the text state itself updates instantly, so fast
            // typing is never slowed down or swallowed.
            while (charAnim.size() < text.length()) charAnim.add(1f);              // safety net
            while (charAnim.size() > text.length()) charAnim.remove(charAnim.size() - 1);
            for (int i = 0; i < text.length(); i++) {
                float a = charAnim.get(i);
                if (a < 1f) {
                    a = SettingAnimationController.approach(a, 1f, CHAR_IN_RATE);
                    charAnim.set(i, a);
                }
                float cxI = tx + caretX(i);
                if (a >= 0.999f) {
                    Theme.FONT.draw(text.substring(i, i + 1), cxI, ty, ts, Theme.color(Theme.TEXT_ON, alpha));
                } else {
                    Theme.FONT.draw(text.substring(i, i + 1), cxI, ty + (1f - a) * 2.5f, ts,
                            Theme.color(Theme.TEXT_ON, alpha * a));
                }
            }
        }

        // deleted characters dissolve in place (sink + fade), then get dropped
        for (Iterator<Ghost> it = ghosts.iterator(); it.hasNext(); ) {
            Ghost g = it.next();
            g.anim = SettingAnimationController.approach(g.anim, 0f, CHAR_OUT_RATE);
            if (g.anim <= 0.03f) { it.remove(); continue; }
            Theme.FONT.draw(g.glyph, tx + g.xOff, ty + (1f - g.anim) * 2.5f, ts,
                    Theme.color(Theme.TEXT_ON, alpha * g.anim * 0.9f));
        }

        Batch.endText();   // flush: rects underneath, text on top

        // caret — drawn AFTER the batch flush so it sits above the glyphs too
        // (inside a batch every rect lands under the text, whatever the call order);
        // steady while typing, blinking when idle.
        if (focused && !hasSelection() && caretVisible()) {
            Render2D.rect(tx + caretX(cursor), cy - (ts + 2f) / 2f, 0.8f, ts + 2f,
                    Theme.color(Theme.TEXT_ON, alpha * (0.55f + 0.45f * focusAnim)), 0.4f);
        }

        Scissor.disable();
    }

    /** Pixel offset of the caret at {@code index} from the start of the text. */
    private float caretX(int index) {
        if (index <= 0) return 0f;
        return Theme.FONT.getWidth(text.substring(0, Math.min(index, text.length())), Theme.MODULE_SIZE);
    }

    /** Keeps the caret inside the visible window by sliding the text horizontally. */
    private void updateViewShift(float viewW) {
        float caret = caretX(cursor);
        if (caret - viewShift > viewW - 4f) viewShift = caret - viewW + 4f;
        if (caret - viewShift < 0f) viewShift = caret;
        float total = caretX(text.length());
        viewShift = Math.max(0f, Math.min(viewShift, Math.max(0f, total - viewW + 4f)));
    }

    private boolean caretVisible() {
        long since = System.currentTimeMillis() - lastEditMs;
        return (since / BLINK_MS) % 2L == 0L;
    }

    /* ============================== Selection ============================== */
    private boolean hasSelection() { return selAnchor != -1 && selAnchor != cursor; }
    private int selMin() { return Math.min(selAnchor, cursor); }
    private int selMax() { return Math.max(selAnchor, cursor); }

    private void clearSelection() { selAnchor = -1; }

    /* ============================== Editing ============================== */

    /**
     * Removes {@code [from, to)} keeping the per-character animations in sync.
     * A single-character removal leaves a fading {@link Ghost} behind; bulk
     * removals (selection, paste-over) vanish silently to avoid glyph mush.
     */
    private void deleteRange(int from, int to, boolean ghost) {
        from = Math.max(0, from);
        to = Math.min(text.length(), to);
        if (from >= to) return;
        if (ghost) {
            for (int i = from; i < to; i++) {
                ghosts.add(new Ghost(text.substring(i, i + 1), caretX(i)));
            }
            while (ghosts.size() > 8) ghosts.remove(0);   // держим хвост коротким
        }
        text = text.substring(0, from) + text.substring(to);
        for (int i = to - 1; i >= from; i--) charAnim.remove(i);
        cursor = from;
        clearSelection();
        touch();
        fireQuery();
    }

    private void insert(String s) {
        if (s == null || s.isEmpty()) return;
        if (hasSelection()) deleteRange(selMin(), selMax(), false);
        int room = MAX_LENGTH - text.length();
        if (room <= 0) return;
        if (s.length() > room) s = s.substring(0, room);
        text = text.substring(0, cursor) + s + text.substring(cursor);
        for (int i = 0; i < s.length(); i++) charAnim.add(cursor + i, 0f);   // fade in from 0
        cursor += s.length();
        clearSelection();
        touch();
        fireQuery();
    }

    private void clearText() {
        if (text.isEmpty()) return;
        text = "";
        charAnim.clear();
        ghosts.clear();
        cursor = 0;
        clearSelection();
        viewShift = 0f;
        touch();
        fireQuery();
    }

    private void touch() { lastEditMs = System.currentTimeMillis(); }

    private void fireQuery() {
        if (onQuery != null) onQuery.accept(getQuery());
    }

    /* ============================== Input ============================== */

    /**
     * @return true if the click was consumed. A click outside only drops focus
     * and is left for the panels to handle.
     */
    public boolean mouseClicked(float mx, float my, int button) {
        if (!isHovered(mx, my)) {
            if (focused) { focused = false; clearSelection(); }
            return false;
        }
        if (button == 0) {
            focused = true;
            cursor = indexAt(mx);
            clearSelection();
            touch();
        } else if (button == 1) {
            focused = true;
            clearText();   // quick clear on right-click
        }
        return true;
    }

    /** Nearest caret index for a click at screen-x {@code mx}. */
    private int indexAt(float mx) {
        float local = mx - (x + 19f - viewShift);
        if (local <= 0f) return 0;
        for (int i = 1; i <= text.length(); i++) {
            float w = caretX(i);
            float prev = caretX(i - 1);
            if (local < (prev + w) / 2f) return i - 1;
        }
        return text.length();
    }

    public boolean charTyped(char chr, int mods) {
        if (!focused) return false;
        if (chr < 32 || chr == 127) return false;
        insert(String.valueOf(chr));
        return true;
    }

    /** Handles keys while focused; consumes everything so panels underneath stay quiet. */
    public boolean keyPressed(int key, int scan, int mods) {
        if (!focused) return false;
        boolean shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0;

        if (ctrl) {
            switch (key) {
                case GLFW.GLFW_KEY_A -> { selAnchor = 0; cursor = text.length(); touch(); return true; }
                case GLFW.GLFW_KEY_C -> { copySelection(); return true; }
                case GLFW.GLFW_KEY_X -> { copySelection(); deleteRange(selMin(), selMax(), false); return true; }
                case GLFW.GLFW_KEY_V -> { insert(clipboard()); return true; }
            }
        }

        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> {          // first Esc: clear + drop focus; next one closes the menu
                clearText();
                focused = false;
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> { focused = false; return true; }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (hasSelection()) deleteRange(selMin(), selMax(), false);
                else if (cursor > 0) deleteRange(cursor - 1, cursor, true);
                else touch();
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (hasSelection()) deleteRange(selMin(), selMax(), false);
                else if (cursor < text.length()) deleteRange(cursor, cursor + 1, true);
                else touch();
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> { moveCaret(cursor - 1, shift); return true; }
            case GLFW.GLFW_KEY_RIGHT -> { moveCaret(cursor + 1, shift); return true; }
            case GLFW.GLFW_KEY_HOME -> { moveCaret(0, shift); return true; }
            case GLFW.GLFW_KEY_END -> { moveCaret(text.length(), shift); return true; }
        }
        return true;   // swallow the rest while typing (don't trigger binds below)
    }

    private void moveCaret(int to, boolean extendSelection) {
        int target = Math.max(0, Math.min(text.length(), to));
        if (extendSelection) {
            if (selAnchor == -1) selAnchor = cursor;
        } else if (hasSelection()) {
            // collapse onto the matching selection edge, like every native text field
            target = to < cursor ? selMin() : (to > cursor ? selMax() : target);
            clearSelection();
        } else {
            clearSelection();
        }
        cursor = target;
        touch();
    }

    private void copySelection() {
        if (!hasSelection()) return;
        GLFW.glfwSetClipboardString(mc.getWindow().getHandle(), text.substring(selMin(), selMax()));
    }

    private String clipboard() {
        String s = GLFW.glfwGetClipboardString(mc.getWindow().getHandle());
        return s == null ? "" : s.replaceAll("[\\n\\r\\t]", "");
    }
}
