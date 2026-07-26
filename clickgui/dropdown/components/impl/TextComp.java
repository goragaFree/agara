package rich.screens.clickgui.dropdown.components.impl;

import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;
import rich.modules.module.setting.implement.TextSetting;
import rich.screens.clickgui.dropdown.theme.Theme;
import rich.screens.clickgui.dropdown.components.SettingComponent;
import rich.util.render.Render2D;

/** Editable text field for a {@link TextSetting}. */
public class TextComp extends SettingComponent {
    private final TextSetting s;
    private boolean focused;

    public TextComp(TextSetting s) { super(s); this.s = s; if (s.getText() == null) s.setText(""); }

    @Override public float getHeight() { return 22f; }
    @Override public boolean isCapturing() { return focused; }

    @Override
    public void render(DrawContext ctx, float mx, float my, float delta) {
        drawText(s.getName(), x, y, Theme.SETTING_SIZE, Theme.TEXT_OFF);
        float bh = 11f, by = y + 9f;
        Render2D.rect(x, by, width, bh, Theme.color(Theme.CONTROL_BG, alpha), Theme.CONTROL_RADIUS);
        if (focused) Render2D.outline(x, by, width, bh, 0.5f, Theme.accent(alpha), Theme.CONTROL_RADIUS);

        String text = s.getText() == null ? "" : s.getText();
        String shown = focused ? text + "_" : text;
        drawText(shown, x + 4f, by + (bh - Theme.SMALL_SIZE) / 2f, Theme.SMALL_SIZE,
                text.isEmpty() && !focused ? Theme.TEXT_DESC : Theme.TEXT_ON);
    }

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        if (button == 0) {
            focused = mx >= x && mx <= x + width && my >= y + 9f && my <= y + getHeight();
            return focused;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (!focused) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) { focused = false; return true; }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            String t = s.getText();
            if (t != null && !t.isEmpty()) s.setText(t.substring(0, t.length() - 1));
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(char chr, int mods) {
        if (!focused) return false;
        if (chr < 32) return false;
        int max = s.getMax();
        String t = s.getText() == null ? "" : s.getText();
        if (max > 0 && t.length() >= max) return true;
        s.setText(t + chr);
        return true;
    }
}
