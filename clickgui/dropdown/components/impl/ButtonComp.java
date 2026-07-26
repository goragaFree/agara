package rich.screens.clickgui.dropdown.components.impl;

import net.minecraft.client.gui.DrawContext;
import rich.modules.module.setting.implement.ButtonSetting;
import rich.screens.clickgui.dropdown.components.SettingAnimationController;
import rich.screens.clickgui.dropdown.theme.Theme;
import rich.screens.clickgui.dropdown.components.SettingComponent;
import rich.util.render.Render2D;

import java.awt.Color;

/** Clickable button for a {@link ButtonSetting}. */
public class ButtonComp extends SettingComponent {
    private final ButtonSetting s;
    private float hover;

    public ButtonComp(ButtonSetting s) { super(s); this.s = s; }

    @Override public float getHeight() { return 16f; }

    @Override
    public void render(DrawContext ctx, float mx, float my, float delta) {
        hover = SettingAnimationController.hover(hover, hovered(mx, my, getHeight()));
        float bh = 13f, by = y + (getHeight() - bh) / 2f;
        Color bg = Theme.lerp(Theme.CONTROL_BG, Theme.ACCENT, hover * 0.85f);
        Render2D.rect(x, by, width, bh, Theme.color(bg, alpha), Theme.CONTROL_RADIUS);
        String label = s.getButtonName() != null ? s.getButtonName() : s.getName();
        float tw = Theme.FONT.getWidth(label, Theme.SETTING_SIZE);
        // под ховером кнопка заливается акцентом — подпись перетекает в
        // контрастный к акценту цвет, чтобы не пропадать на белом акценте
        drawText(label, x + (width - tw) / 2f, by + (bh - Theme.SETTING_SIZE) / 2f, Theme.SETTING_SIZE,
                Theme.lerp(Theme.TEXT_ON, Theme.onAccent(), hover * 0.85f));
    }

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        if (button == 0 && hovered(mx, my, getHeight())) {
            if (s.getRunnable() != null) s.getRunnable().run();
            return true;
        }
        return false;
    }
}
