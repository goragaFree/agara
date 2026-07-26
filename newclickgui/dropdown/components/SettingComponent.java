package rich.screens.clickgui.dropdown.components;

import net.minecraft.client.gui.DrawContext;
import rich.modules.module.setting.Setting;
import rich.modules.module.setting.implement.*;
import rich.screens.clickgui.dropdown.theme.Theme;
import rich.screens.clickgui.dropdown.components.impl.*;

import java.awt.Color;


/**
 * Описывает базовый компонент настройки ClickGUI.
 * Используется для отображения и изменения одной настройки,
 * а также создания компонентов нужного типа через фабричный метод.
 */

/**
 * Base class for every setting renderer inside a panel. Each concrete
 * implementation lives in its own {@code *Comp} file in this package; use
 * {@link #create(Setting)} to build the right one for a given setting.
 */
public abstract class SettingComponent {

    protected final Setting setting;
    protected float x, y, width;
    protected float alpha = 1f;

    protected SettingComponent(Setting setting) {
        this.setting = setting;
    }

    public Setting getSetting() {
        return setting;
    }

    public boolean isVisible() {
        return setting.isVisible();
    }

    public void place(float x, float y, float width, float alpha) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.alpha = alpha;
    }

    public abstract float getHeight();

    public abstract void render(DrawContext context, float mouseX, float mouseY, float delta);

    public boolean mouseClicked(float mx, float my, int button) { return false; }
    public boolean mouseReleased(float mx, float my, int button) { return false; }
    public boolean mouseScrolled(float mx, float my, double amount) { return false; }
    public boolean keyPressed(int key, int scan, int mods) { return false; }
    public boolean charTyped(char chr, int mods) { return false; }

    /** True when the component captures keyboard input (bind / text field). */
    public boolean isCapturing() { return false; }

    protected boolean hovered(float mx, float my, float h) {
        return mx >= x && mx <= x + width && my >= y && my <= y + h;
    }

    protected void drawText(String text, float tx, float ty, float size, Color color) {
        Theme.FONT.draw(text, tx, ty, size, Theme.color(color, alpha));
    }

    protected float lerp(float current, float target, float speed) {
        float v = current + (target - current) * speed;
        return Math.abs(target - v) < 0.001f ? target : v;
    }

    /* ============================ Factory ============================ */
    public static SettingComponent create(Setting setting) {
        if (setting instanceof BooleanSetting s)     return new BooleanComp(s);
        if (setting instanceof SliderSettings s)     return new SliderComp(s);
        if (setting instanceof SelectSetting s)       return new SelectComp(s);
        if (setting instanceof MultiSelectSetting s)  return new MultiSelectComp(s);
        if (setting instanceof ColorSetting s)        return new ColorComp(s);
        if (setting instanceof BindSetting s)         return new BindComp(s);
        if (setting instanceof ButtonSetting s)       return new ButtonComp(s);
        if (setting instanceof TextSetting s)         return new TextComp(s);
        if (setting instanceof GroupSetting s)        return new GroupComp(s);
        return null;
    }
}

