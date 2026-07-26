package rich.screens.clickgui.dropdown.components.impl;

import net.minecraft.client.gui.DrawContext;
import rich.modules.module.setting.implement.BooleanSetting;
import rich.screens.clickgui.dropdown.components.SettingAnimationController;
import rich.screens.clickgui.dropdown.theme.Theme;
import rich.screens.clickgui.dropdown.components.SettingComponent;
import rich.util.render.Render2D;

import java.awt.Color;

/** Toggle switch for a {@link BooleanSetting}. */
public class BooleanComp extends SettingComponent {
    private final BooleanSetting s;
    private float anim, hover;

    public BooleanComp(BooleanSetting s) { super(s); this.s = s; this.anim = s.isValue() ? 1f : 0f; }

    @Override public float getHeight() { return 14f; }

    @Override
    public void render(DrawContext ctx, float mx, float my, float delta) {
        hover = SettingAnimationController.hover(hover, hovered(mx, my, getHeight()));
        anim = SettingAnimationController.toggle(anim, s.isValue());

        float cy = y + getHeight() / 2f;
        drawText(s.getName(), x, cy - Theme.SETTING_SIZE / 2f, Theme.SETTING_SIZE,
                Theme.lerp(Theme.TEXT_OFF, Theme.TEXT_ON, anim));

        float pw = 15f, ph = 8f;
        float px = x + width - pw, py = cy - ph / 2f;
        Color bg = Theme.lerp(new Color(255, 255, 255, 28), Theme.ACCENT, anim);
        Render2D.rect(px, py, pw, ph, Theme.color(bg, alpha), ph / 2f);

        float kr = ph - 3f;
        float kx = px + 1.5f + (pw - kr - 3f) * anim;
        Render2D.rect(kx, py + 1.5f, kr, kr, Theme.color(new Color(255, 255, 255, 235), alpha), kr / 2f);
    }

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        if (button == 0 && hovered(mx, my, getHeight())) {
            s.setValue(!s.isValue());
            return true;
        }
        return false;
    }
}
