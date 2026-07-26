package rich.screens.clickgui.dropdown.components.impl;

import net.minecraft.client.gui.DrawContext;
import rich.modules.module.setting.Setting;
import rich.modules.module.setting.implement.GroupSetting;
import rich.screens.clickgui.dropdown.components.SettingAnimationController;
import rich.screens.clickgui.dropdown.theme.Theme;
import rich.screens.clickgui.dropdown.components.SettingComponent;
import rich.util.render.Render2D;
import rich.util.render.shader.Scissor;

import java.util.ArrayList;
import java.util.List;

/** Collapsible group of sub-settings for a {@link GroupSetting}. */
public class GroupComp extends SettingComponent {
    private final GroupSetting s;
    private final List<SettingComponent> subs = new ArrayList<>();
    private float anim;
    private static final float SCISSOR_SCALE = 2f;

    public GroupComp(GroupSetting s) {
        super(s);
        this.s = s;
        for (Setting sub : s.getSubSettings()) {
            SettingComponent c = SettingComponent.create(sub);
            if (c != null) subs.add(c);
        }
        this.anim = s.isValue() ? 1f : 0f;
    }

    private float subsExtra() {
        float h = 0f;
        for (SettingComponent c : subs) if (c.isVisible()) h += c.getHeight() + 2f;
        return h;
    }

    @Override
    public float getHeight() {
        return 14f + anim * subsExtra();
    }

    @Override
    public void render(DrawContext ctx, float mx, float my, float delta) {
        anim = SettingAnimationController.expand(anim, s.isValue());

        float hy = y + 7f;
        drawText(s.getName(), x, hy - Theme.SETTING_SIZE / 2f, Theme.SETTING_SIZE,
                Theme.lerp(Theme.TEXT_OFF, Theme.ACCENT, anim));

        // drawn plus / minus indicator
        float icx = x + width - 4f, icy = hy;
        float arm = 2.5f, thick = 1.1f;
        int icol = Theme.accent(alpha);
        Render2D.rect(icx - arm, icy - thick / 2f, arm * 2f, thick, icol, thick / 2f);
        float vBar = arm * 2f * (1f - anim);
        if (vBar > 0.05f) {
            Render2D.rect(icx - thick / 2f, icy - vBar / 2f, thick, vBar, icol, thick / 2f);
        }

        if (anim > 0.001f) {
            Scissor.enable(x, y + 14f, width, anim * subsExtra(), SCISSOR_SCALE);
            float oy = y + 14f;
            for (SettingComponent c : subs) {
                if (!c.isVisible()) continue;
                c.place(x + 6f, oy, width - 6f, alpha);
                c.render(ctx, mx, my, delta);
                oy += c.getHeight() + 2f;
            }
            Scissor.disable();
        }
    }

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        if (button == 0 && my >= y && my <= y + 14f && mx >= x && mx <= x + width) {
            // clicking the name does nothing; only the plus toggles it
            float nameW = Theme.FONT.getWidth(s.getName(), Theme.SETTING_SIZE);
            if (mx > x + nameW + 2f) s.setValue(!s.isValue());
            return true;
        }
        if (!s.isValue()) return false;
        for (SettingComponent c : subs) if (c.isVisible() && c.mouseClicked(mx, my, button)) return true;
        return false;
    }

    @Override
    public boolean mouseReleased(float mx, float my, int button) {
        for (SettingComponent c : subs) if (c.isVisible() && c.mouseReleased(mx, my, button)) return true;
        return false;
    }

    @Override
    public boolean mouseScrolled(float mx, float my, double amount) {
        for (SettingComponent c : subs) if (c.isVisible() && c.mouseScrolled(mx, my, amount)) return true;
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        for (SettingComponent c : subs) if (c.isVisible() && c.keyPressed(key, scan, mods)) return true;
        return false;
    }

    @Override
    public boolean charTyped(char chr, int mods) {
        for (SettingComponent c : subs) if (c.isVisible() && c.charTyped(chr, mods)) return true;
        return false;
    }

    @Override
    public boolean isCapturing() {
        for (SettingComponent c : subs) if (c.isCapturing()) return true;
        return false;
    }
}
