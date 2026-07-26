package rich.screens.clickgui.dropdown.tab;

import net.minecraft.client.gui.DrawContext;
import rich.screens.clickgui.dropdown.theme.Theme;
import rich.screens.clickgui.dropdown.theme.ThemeManager;
import rich.screens.clickgui.dropdown.components.SettingAnimationController;
import rich.screens.clickgui.dropdown.tab.impl.ConfigTab;
import rich.screens.clickgui.dropdown.tab.impl.SettingsTab;
import rich.screens.clickgui.dropdown.tab.impl.ThemeTab;
import rich.util.render.Render2D;
import rich.util.render.batch.Batch;
import rich.util.render.shader.Scissor;

import java.awt.Color;


// представляет боковую панель ClickGUI с вкладками

/**
 * Collapsible side block (top-right). Collapsed, it is a single small tile (shows "s" for now).
 * Clicking it makes the tile itself <b>morph</b> in place into the full panel — it doesn't spawn a
 * panel beside it, it <i>is</i> the panel. While open, the category selection appears as floating
 * tiles in the air to its left (Themes / Settings / Config). Each category is a {@link PanelTab};
 * adding one is just a new tab.
 */
public class SidePanel {

    private static final float TILE = 28f, TILE_GAP = 6f, GAP = 8f, TILE_RADIUS = 7f;
    private static final float W = 124f, HEADER = 22f, MARGIN = 12f, SCISSOR_SCALE = 2f;
    /** Fixed content height for every tab — overflow scrolls instead of resizing the panel. */
    private static final float CONTENT_H = 126f;

    private final PanelTab[] tabs = { new ThemeTab(), new SettingsTab(), new ConfigTab() };
    private int active = 0;
    private int prevActive = 0;
    private float tabAnim = 1f;   // tab-switch slide: 0 = mid switch, 1 = settled

    private boolean open;
    private float anim;

    private float scroll, scrollTarget;   // content scroll (px), 0 = top
    private long lastScrollNanos;

    private float rightX, topY;      // anchored top-right corner of the block
    private float px, py;            // settled panel top-left (open)
    private float tlx;               // floating category tiles column left

    private PanelTab tab() { return tabs[active]; }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    /* ============================== Geometry ============================== */
    private float fullHeight() { return HEADER + CONTENT_H; }

    /** How far the content can scroll before its bottom reaches the panel bottom. */
    private float maxScroll() { return Math.max(0f, tab().contentHeight() - (CONTENT_H - 6f)); }

    private float catY(int i) { return py + i * (TILE + TILE_GAP); }

    private PanelTab.Region region() {
        // panelX = the floating tiles column, so popups open clear to the left of everything.
        // content y is shifted up by the scroll offset (clipped to the fixed content window).
        return new PanelTab.Region(tlx, py, W, px, py + HEADER + 4f - scroll, W);
    }

    /** Region with the content shifted vertically by {@code dy} (used for the tab-switch fade). */
    private PanelTab.Region regionShiftedY(float dy) {
        return new PanelTab.Region(tlx, py, W, px, py + HEADER + 4f - scroll + dy, W);
    }

    private boolean overLauncher(float mx, float my) {
        return mx >= rightX - TILE && mx <= rightX && my >= topY && my <= topY + TILE;
    }

    private boolean overPanel(float mx, float my) {
        return open && mx >= px && mx <= px + W && my >= py && my <= py + fullHeight();
    }

    private int catAt(float mx, float my) {
        if (!open || anim < 0.5f || mx < tlx || mx > tlx + TILE) return -1;
        for (int i = 0; i < tabs.length; i++) {
            if (my >= catY(i) && my <= catY(i) + TILE) return i;
        }
        return -1;
    }

    public boolean isHovered(float mx, float my) {
        return tab().modal() || overLauncher(mx, my) || overPanel(mx, my) || catAt(mx, my) != -1;
    }

    public boolean isTyping() { return tab().isTyping(); }

    /* ============================== Render ============================== */
    public void render(DrawContext ctx, float vw, float mx, float my, float alpha) {
        anim = SettingAnimationController.expand(anim, open);

        rightX = vw - MARGIN;
        topY = MARGIN;
        px = rightX - W;
        py = topY;
        tlx = px - GAP - TILE;

        // fixed-size panel: clamp + smooth the content scroll
        scrollTarget = Math.max(0f, Math.min(maxScroll(), scrollTarget));
        scroll = smoothScroll(scroll, scrollTarget);

        // the morphing block: tile (28) -> full panel (W x fullHeight)
        float w = lerp(TILE, W, anim);
        float h = lerp(TILE, fullHeight(), anim);
        float radius = lerp(TILE_RADIUS, Theme.PANEL_RADIUS, anim);
        float x = rightX - w, y = topY;

        Render2D.shadow(x, y, w, h, radius, Theme.PANEL_SHADOW, Theme.color(Theme.SHADOW, alpha));
        Render2D.glass(x, y, w, h, radius, Theme.GLASS_TINT.getRGB(), alpha);

        Color accent = ThemeManager.INSTANCE.current().accent;

        // "s" placeholder fades out as the block opens
        float sFade = clamp(1f - anim / 0.35f);
        if (sFade > 0.01f) {
            float a = alpha * sFade;
            float size = 9f, tw = Theme.FONT.getWidth("s", size);
            Theme.FONT.draw("s", x + (w - tw) / 2f, y + h / 2f - size / 2f, size,
                    Theme.color(Theme.TEXT_OFF, a));
        }

        // content fades in over the second half of the morph; clipped to the current (growing)
        // block rect so the full-width layout reveals inside the tile instead of poking out.
        float cFade = clamp((anim - 0.45f) / 0.55f);
        if (cFade > 0.01f) {
            float a = alpha * cFade;
            renderContent(ctx, mx, my, a, accent, x, y, w, h);
            renderCategoryTiles(ctx, mx, my, a, accent);
        }

        // same subtle border as the regular category panels (no bright accent ring when open)
        Render2D.outline(x, y, w, h, 1f, Theme.color(Theme.PANEL_OUTLINE, alpha), radius);
    }

    private void renderContent(DrawContext ctx, float mx, float my, float a, Color accent,
                               float bx, float by, float bw, float bh) {
        PanelTab.Region r = region();

        // advance the tab-switch transition (once per frame)
        tabAnim = SettingAnimationController.approach(tabAnim, 1f, 12f);
        boolean switching = tabAnim < 0.999f && prevActive != active;
        float rise = (1f - tabAnim) * 10f;   // new content lifts up into place

        // header chrome — clipped to the morphing block rect (not scrolled).
        // Batched (one rect flush + one text flush) like the category panels.
        Scissor.enable(bx, by, bw, bh, SCISSOR_SCALE);
        Batch.beginText();

        float cy = py + HEADER / 2f, ty0 = cy - Theme.TITLE_SIZE / 2f;
        Render2D.rect(px + 8f, cy - 4f, 8f, 8f, Theme.color(accent, a), 2.5f);
        if (switching) {
            // title slides + swaps in the direction of the picked tile: a lower tab (dir +1)
            // scrolls the title up, an upper tab (dir -1) scrolls it down.
            int dir = active > prevActive ? 1 : -1;
            float td = 9f;
            Theme.FONT.draw(tabs[prevActive].title(), px + 20f, ty0 - dir * td * tabAnim, Theme.TITLE_SIZE,
                    Theme.color(Theme.TITLE, a * (1f - tabAnim)));
            Theme.FONT.draw(tabs[active].title(), px + 20f, ty0 + dir * td * (1f - tabAnim), Theme.TITLE_SIZE,
                    Theme.color(Theme.TITLE, a * tabAnim));
        } else {
            Theme.FONT.draw(tab().title(), px + 20f, ty0, Theme.TITLE_SIZE, Theme.color(Theme.TITLE, a));
        }

        // collapse indicator (minus) — click anywhere on the header to fold back into the tile
        float icx = px + W - 8f, arm = 2.5f, thick = 1.1f;
        Render2D.rect(icx - arm, cy - thick / 2f, arm * 2f, thick, Theme.accent(a), thick / 2f);

        Render2D.rect(px + 4f, py + HEADER, W - 8f, 0.6f, Theme.color(Theme.SEPARATOR, a), 0f);

        Batch.endText();
        Scissor.disable();

        // scrollable tab content — clipped to the content window below the header
        // (and to the still-growing block during the open morph). The window
        // bottom rises by the rounded-corner inset so rows/sliders don't poke
        // past the bottom arc at large Radius values (rows are inset 3px).
        float top = py + HEADER + 1f;
        float bottom = Math.min(
                py + HEADER + CONTENT_H - Theme.cornerInset(Theme.PANEL_RADIUS, 3f),
                by + bh);
        if (bottom > top) {
            Scissor.enable(px, top, W, bottom - top, SCISSOR_SCALE);
            if (switching) {
                // old tab fades out in place; new tab fades in while lifting up
                Batch.beginText();
                tabs[prevActive].render(ctx, region(), mx, my, a * (1f - tabAnim));
                Batch.endText();
                Batch.beginText();
                tabs[active].render(ctx, regionShiftedY(rise), mx, my, a * tabAnim);
                Batch.endText();
            } else {
                Batch.beginText();
                tab().render(ctx, r, mx, my, a);
                Batch.endText();
            }
            Scissor.disable();
        }

        // floating popups (picker) must not be clipped to the block
        tab().renderModal(ctx, r, mx, my, a);
    }

    private void renderCategoryTiles(DrawContext ctx, float mx, float my, float a, Color accent) {
        for (int i = 0; i < tabs.length; i++) {
            float ty = catY(i);
            boolean act = i == active;
            boolean hov = mx >= tlx && mx <= tlx + TILE && my >= ty && my <= ty + TILE;

            Render2D.shadow(tlx, ty, TILE, TILE, TILE_RADIUS, Theme.PANEL_SHADOW, Theme.color(Theme.SHADOW, a));
            Render2D.glass(tlx, ty, TILE, TILE, TILE_RADIUS, Theme.GLASS_TINT.getRGB(), a);
            if (act) {
                Render2D.rect(tlx, ty, TILE, TILE,
                        Theme.color(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 40), a), TILE_RADIUS);
            }
            Render2D.outline(tlx, ty, TILE, TILE, 1f, Theme.color(act ? accent : Theme.PANEL_OUTLINE, a), TILE_RADIUS);

            int iconCol = Theme.color(act ? Theme.TEXT_ON : (hov ? Theme.TEXT_OFF : Theme.TEXT_DESC), a);
            tabs[i].icon(ctx, tlx + TILE / 2f, ty + TILE / 2f, 10f, iconCol);
        }
    }

    private static float clamp(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }

    /** Frame-rate independent exponential smoothing for the content scroll. */
    private float smoothScroll(float current, float target) {
        long now = System.nanoTime();
        float dt = lastScrollNanos == 0L ? 1f / 60f : (now - lastScrollNanos) / 1_000_000_000f;
        lastScrollNanos = now;
        dt = Math.min(dt, 0.1f);
        float factor = 1f - (float) Math.exp(-11f * dt);
        float v = current + (target - current) * factor;
        return Math.abs(target - v) < 0.05f ? target : v;
    }

    /* ============================== Input ============================== */
    public boolean mouseClicked(float mx, float my, int button) {
        if (tab().modal()) {
            tab().mouseClicked(region(), mx, my, button);
            return true;
        }

        if (!open) {
            if (button == 0 && overLauncher(mx, my)) { open = true; scroll = scrollTarget = 0f; return true; }
            return false;
        }

        // open: header collapses back into the tile
        if (button == 0 && my >= py && my <= py + HEADER && mx >= px && mx <= px + W) {
            open = false;
            return true;
        }

        // floating category tiles switch category (animate + reset scroll for the new tab)
        if (button == 0) {
            int c = catAt(mx, my);
            if (c != -1) {
                if (c != active) { prevActive = active; tabAnim = 0f; }
                active = c;
                scroll = scrollTarget = 0f;
                return true;
            }
        }

        // route clicks to the tab only inside the visible content window (below the header)
        float top = py + HEADER + 1f, bottom = py + HEADER + CONTENT_H;
        if (mx >= px && mx <= px + W && my >= top && my <= bottom) {
            if (tab().mouseClicked(region(), mx, my, button)) return true;
        }
        return overPanel(mx, my);
    }

    public boolean mouseReleased(float mx, float my, int button) {
        return tab().mouseReleased(mx, my, button);
    }

    public boolean mouseScrolled(float mx, float my, double amount) {
        if (!open || !overPanel(mx, my)) return false;
        if (maxScroll() <= 0f) return true;            // nothing to scroll, but consume over the panel
        scrollTarget = Math.max(0f, Math.min(maxScroll(), scrollTarget - (float) amount * 15f));
        return true;
    }

    public boolean keyPressed(int key, int scan, int mods) {
        return tab().keyPressed(key, scan, mods);
    }

    public boolean charTyped(char chr, int mods) {
        return tab().charTyped(chr, mods);
    }
}
