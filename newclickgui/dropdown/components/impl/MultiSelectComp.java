package rich.screens.clickgui.dropdown.components.impl;

import net.minecraft.client.gui.DrawContext;
import rich.modules.module.setting.implement.MultiSelectSetting;
import rich.screens.clickgui.dropdown.components.SettingAnimationController;
import rich.screens.clickgui.dropdown.theme.Theme;
import rich.screens.clickgui.dropdown.components.SettingComponent;
import rich.util.render.Render2D;
import rich.util.render.shader.Scissor;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Expandable multi-toggle list for a {@link MultiSelectSetting}. */
public class MultiSelectComp extends SettingComponent {
    private final MultiSelectSetting s;
    private boolean expanded;
    private float anim;
    private final Map<String, Float> selAnim = new HashMap<>();
    private final Map<String, Float> hoverAnim = new HashMap<>();
    private static final float ROW = 11f;
    private static final float SCISSOR_SCALE = 2f;

    public MultiSelectComp(MultiSelectSetting s) { super(s); this.s = s; }

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
        drawText(s.getName(), x, hy - Theme.SETTING_SIZE / 2f, Theme.SETTING_SIZE, Theme.TEXT_OFF);

        // drawn plus / minus indicator
        float icx = x + width - 4f, icy = hy;
        float arm = 2.5f, thick = 1.1f;
        int icol = Theme.accent(alpha);

        // selected / total counter to the left of the indicator
        int total = s.getList() == null ? 0 : s.getList().size();
        int sel = s.getSelected() == null ? 0 : s.getSelected().size();
        String count = sel + "/" + total;
        float countW = Theme.FONT.getWidth(count, Theme.SMALL_SIZE);
        drawText(count, icx - arm - 3f - countW, hy - Theme.SMALL_SIZE / 2f, Theme.SMALL_SIZE, Theme.TEXT_OFF);

        Render2D.rect(icx - arm, icy - thick / 2f, arm * 2f, thick, icol, thick / 2f);
        float vBar = arm * 2f * (1f - anim);
        if (vBar > 0.05f) {
            Render2D.rect(icx - thick / 2f, icy - vBar / 2f, thick, vBar, icol, thick / 2f);
        }

        if (anim > 0.001f && s.getList() != null) {
            Scissor.enable(x, y + 14f, width, anim * listExtra(), SCISSOR_SCALE);
            float oy = y + 14f;
            boolean rowsActive = anim > 0.85f;
            for (String opt : s.getList()) {
                boolean on = s.isSelected(opt);
                boolean hovered = rowsActive && mx >= x && mx <= x + width && my >= oy && my <= oy + ROW;

                float sa = SettingAnimationController.toggle(selAnim.getOrDefault(opt, on ? 1f : 0f), on);
                selAnim.put(opt, sa);
                float ha = SettingAnimationController.hover(hoverAnim.getOrDefault(opt, 0f), hovered);
                hoverAnim.put(opt, ha);

                if (ha > 0.001f) {
                    Render2D.rect(x, oy, width, ROW, Theme.color(Theme.HOVER, alpha * ha), Theme.CONTROL_RADIUS);
                }

                // checkbox: empty box fades into the accent fill as it gets selected
                Render2D.rect(x + 1f, oy + 2f, 5f, 5f, Theme.color(new Color(255, 255, 255, 30), alpha), 1.5f);
                if (sa > 0.001f) {
                    float box = 5f * sa, off = (5f - box) / 2f;
                    Render2D.rect(x + 1f + off, oy + 2f + off, box, box, Theme.color(Theme.ACCENT, alpha * sa), 1.5f);
                }

                Color c = Theme.lerp(Theme.TEXT_OFF, Theme.ACCENT, sa);
                drawText(opt, x + 10f, oy + 1.5f, Theme.SMALL_SIZE, c);
                oy += ROW;
            }
            Scissor.disable();
        }
    }

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        if (button != 0) return false;
        if (my >= y && my <= y + 14f && mx >= x && mx <= x + width) {
            // clicking the name does nothing; only the plus toggles it
            float nameW = Theme.FONT.getWidth(s.getName(), Theme.SETTING_SIZE);
            if (mx > x + nameW + 2f) expanded = !expanded;
            return true;
        }
        if (expanded && s.getList() != null) {
            float oy = y + 14f;
            for (String opt : s.getList()) {
                if (my >= oy && my <= oy + ROW && mx >= x && mx <= x + width) {
                    List<String> sel = new ArrayList<>(s.getSelected());
                    if (sel.contains(opt)) sel.remove(opt); else sel.add(opt);
                    s.setSelected(sel);
                    return true;
                }
                oy += ROW;
            }
        }
        return false;
    }
}
