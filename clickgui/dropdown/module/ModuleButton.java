package rich.screens.clickgui.dropdown.module;

import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;
import rich.modules.module.ModuleStructure;
import rich.modules.module.setting.Setting;
import rich.screens.clickgui.dropdown.components.SettingComponent;
import rich.screens.clickgui.dropdown.theme.Theme;
import rich.util.render.Render2D;
import rich.util.string.KeyHelper;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;


// Описывает кнопку модуля в панели категории и содержит другие настройки вродеб

/** A single module row inside a panel, owning its setting components. */
public class ModuleButton {

    private final ModuleStructure module;
    private final List<SettingComponent> components = new ArrayList<>();

    private float hover, state;
    private boolean binding;
    private float lastX, lastY, lastW;

    public ModuleButton(ModuleStructure module) {
        this.module = module;
        this.state = module.isState() ? 1f : 0f;
        for (Setting setting : module.settings()) {
            SettingComponent c = SettingComponent.create(setting);
            if (c != null) components.add(c);
        }
    }

    public ModuleStructure getModule() { return module; }
    public List<SettingComponent> getComponents() { return components; }
    public boolean hasSettings() { return !components.isEmpty(); }
    public boolean isBinding() { return binding; }
    public void setBinding(boolean binding) { this.binding = binding; }

    public boolean isCapturing() {
        if (binding) return true;
        for (SettingComponent c : components) if (c.isVisible() && c.isCapturing()) return true;
        return false;
    }

    public boolean contains(float mx, float my) {
        return mx >= lastX && mx <= lastX + lastW && my >= lastY && my <= lastY + Theme.MODULE_HEIGHT;
    }

    public void render(DrawContext ctx, float x, float y, float width, float mouseX, float mouseY,
                       float alpha, boolean topSeparator) {
        this.lastX = x; this.lastY = y; this.lastW = width;
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + Theme.MODULE_HEIGHT;
        hover += ((hovered ? 1f : 0f) - hover) * 0.2f;
        state += ((module.isState() ? 1f : 0f) - state) * 0.25f;

        float h = Theme.MODULE_HEIGHT;

        // thin separator line between modules (Rockstar style) – no panel block
        if (topSeparator) {
            Render2D.rect(x, y, width, 0.5f, Theme.color(new Color(255, 255, 255, 6), alpha), 0f);
        }

        // name: while binding (middle-click) the row shows a prompt instead of the
        // name; otherwise the key is not shown at all.
        float nameOffset = 2f * state;
        if (binding) {
            int key = module.getKey();
            String label = (key == GLFW.GLFW_KEY_UNKNOWN || key == -1)
                    ? "Не забинжен"
                    : "Забинжен на " + KeyHelper.getKeyName(key);
            Theme.FONT.draw(label, x + 4f + nameOffset, y + h / 2f - Theme.MODULE_SIZE / 2f,
                    Theme.MODULE_SIZE, Theme.accent(alpha));
        } else {
            Color text = Theme.lerp(Theme.TEXT_OFF, Theme.TEXT_ON, state);
            float nameAlpha = alpha * (0.75f + 0.25f * state + 0.25f * hover);
            Theme.FONT.draw(module.getName(), x + 4f + nameOffset, y + h / 2f - Theme.MODULE_SIZE / 2f,
                    Theme.MODULE_SIZE, Theme.color(text, Math.min(1f, nameAlpha)));
        }

        // enabled indicator – small accent dot on the right
        if (state > 0.02f) {
            float barW = 2f, barH = 10f * state;
            Render2D.rect(x, y + h / 2f - barH / 2f, barW, barH, Theme.accent(alpha * state), barW / 2f);
        }

        // settings indicator
        if (hasSettings() && !binding) {
            String dots = "...";
            float dw = Theme.FONT.getWidth(dots, Theme.SETTING_SIZE);
            float dx = x + width - dw - 3f;
            Theme.FONT.draw(dots, dx, y + h / 2f - Theme.SETTING_SIZE,
                    Theme.SETTING_SIZE, Theme.color(new Color(150, 150, 158, 160), alpha));
        }
    }
}
