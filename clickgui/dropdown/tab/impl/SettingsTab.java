package rich.screens.clickgui.dropdown.tab.impl;

import net.minecraft.client.gui.DrawContext;
import rich.screens.clickgui.dropdown.theme.Theme;
import rich.screens.clickgui.dropdown.tab.PanelTab;
import rich.util.render.Render2D;

/**
 * Settings tab — placeholder for now. Future home for global GUI settings. Its own class so the
 * container does not need to know what lives here.
 */
public class SettingsTab implements PanelTab {

    @Override public String title() { return "Settings"; }

    @Override public float contentHeight() { return 40f; }

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
        String msg = "Coming soon";
        float tw = Theme.FONT.getWidth(msg, Theme.SETTING_SIZE);
        Theme.FONT.draw(msg, r.x() + (r.w() - tw) / 2f, r.y() + 14f, Theme.SETTING_SIZE,
                Theme.color(Theme.TEXT_DESC, alpha));
    }
}
