package rich.screens.clickgui.dropdown.components.impl;

import net.minecraft.client.gui.DrawContext;
import rich.modules.module.setting.implement.SelectSetting;
import rich.screens.clickgui.dropdown.components.SettingAnimationController;
import rich.screens.clickgui.dropdown.theme.Theme;
import rich.screens.clickgui.dropdown.components.SettingComponent;
import rich.util.render.Render2D;
import rich.util.render.shader.Scissor;

import java.awt.Color;

/** Dropdown list for a {@link SelectSetting}: click to expand, click an option to pick. */
public class SelectComp extends SettingComponent {
    private final SelectSetting s;
    private boolean expanded;
    private float anim;
    private static final float ROW = 11f;
    private static final float SCISSOR_SCALE = 2f;

    public SelectComp(SelectSetting s) { super(s); this.s = s; }

    private float listExtra() {
        int n = s.getList() == null ? 0 : s.getList().size();
        return n * ROW + 2f;
    }

    @Override
    public float getHeight() {
        return 14f + anim * listExtra();
    }

    @Override
    public void render(DrawContext ctx, float mx, float my, float delta) {
        anim = SettingAnimationController.expand(anim, expanded);

        float hy = y + 7f;
        boolean hover = mx >= x && mx <= x + width && my >= y && my <= y + 14f;
        drawText(s.getName(), x, hy - Theme.SETTING_SIZE / 2f, Theme.SETTING_SIZE,
                hover ? Theme.TEXT_ON : Theme.TEXT_OFF);

        // drawn plus / minus indicator (collapsed = "+", expanded = "-")
        float icx = x + width - 4f, icy = hy;
        float arm = 2.5f, thick = 1.1f;
        int icol = Theme.accent(alpha);
        Render2D.rect(icx - arm, icy - thick / 2f, arm * 2f, thick, icol, thick / 2f);
        // vertical bar shrinks away as it opens (plus -> minus)
        float vBar = arm * 2f * (1f - anim);
        if (vBar > 0.05f) {
            Render2D.rect(icx - thick / 2f, icy - vBar / 2f, thick, vBar, icol, thick / 2f);
        }

        String sel = s.getSelected();
        float vw = Theme.FONT.getWidth(sel, Theme.SETTING_SIZE);
        drawText(sel, x + width - 9f - vw, hy - Theme.SETTING_SIZE / 2f, Theme.SETTING_SIZE, Theme.ACCENT);

        if (anim > 0.001f && s.getList() != null) {
            float clipH = anim * listExtra();
            Scissor.enable(x, y + 14f, width, clipH, SCISSOR_SCALE);
            float oy = y + 14f;
            for (String opt : s.getList()) {
                boolean on = s.isSelected(opt);
                boolean oh = expanded && mx >= x && mx <= x + width && my >= oy && my <= oy + ROW;
                if (on) Render2D.rect(x + 1f, oy + ROW / 2f - 1.5f, 3f, 3f, Theme.accent(alpha), 1.5f);
                Color c = on ? Theme.ACCENT : (oh ? Theme.TEXT_ON : Theme.TEXT_OFF);
                drawText(opt, x + 8f, oy + (ROW - Theme.SMALL_SIZE) / 2f, Theme.SMALL_SIZE, c);
                oy += ROW;
            }
            Scissor.disable();
        }
    }

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        if (button != 0) return false;
        if (my >= y && my <= y + 14f && mx >= x && mx <= x + width) {
            // clicking the name does nothing; only the value / plus toggles it
            float nameW = Theme.FONT.getWidth(s.getName(), Theme.SETTING_SIZE);
            if (mx > x + nameW + 2f) expanded = !expanded;
            return true;
        }
        if (expanded && s.getList() != null) {
            float oy = y + 14f;
            for (String opt : s.getList()) {
                if (mx >= x && mx <= x + width && my >= oy && my <= oy + ROW) {
                    s.setSelected(opt);
                    expanded = false;
                    return true;
                }
                oy += ROW;
            }
        }
        return false;
    }
}
