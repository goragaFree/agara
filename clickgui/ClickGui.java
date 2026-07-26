package rich.screens.clickgui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import rich.IMinecraft;
import rich.modules.module.category.ModuleCategory;
import rich.screens.clickgui.dropdown.module.Panel;
import rich.screens.clickgui.dropdown.theme.Theme;
import rich.screens.clickgui.dropdown.theme.ThemeManager;
import rich.screens.clickgui.dropdown.tab.SidePanel;
import rich.util.animations.Direction;
import rich.util.animations.GuiAnimation;
import rich.screens.clickgui.dropdown.components.SettingAnimationController;
import rich.util.render.batch.UiGlass;
import rich.util.render.gif.GifRender;
import rich.util.render.shader.Scissor;

import java.util.ArrayList;
import java.util.List;

/**
 * Panel based dropdown ClickGui. Each module category becomes a panel; a panel
 * lists its modules and swaps to a module's settings when opened. Style lives
 * entirely in {@link Theme} so the whole menu is easy to re-skin / configure.
 */
public class ClickGui extends Screen implements IMinecraft {

    public static ClickGui INSTANCE = new ClickGui();
    private static final int FIXED_GUI_SCALE = 2;

    private final List<Panel> panels = new ArrayList<>();
    private final SidePanel sidePanel = new SidePanel();
    private final GuiAnimation openAnimation = new GuiAnimation();

    private boolean closing = false;

    private int lastMouseX, lastMouseY;
    private float lastDelta;

    public ClickGui() {
        super(Text.of("ClickGui"));
    }

    public boolean isClosing() {
        return closing;
    }

    private void buildPanels() {
        panels.clear();
        for (ModuleCategory category : ModuleCategory.values()) {
            if (category == ModuleCategory.AUTOBUY) continue;
            Panel panel = new Panel(category);
            if (!panel.isEmpty()) panels.add(panel);
        }
    }

    @Override
    protected void init() {
        super.init();
        closing = false;
        openAnimation.setMs(250).setValue(1.0).setDirection(Direction.FORWARDS).reset();
        ThemeManager.INSTANCE.apply();
        buildPanels();
        centerCursor();
    }

    public void openGui() {
        if (mc.currentScreen == null) {
            closing = false;
            openAnimation.setMs(250).setValue(1.0).setDirection(Direction.FORWARDS).reset();
            // take ONE fresh frosted-glass snapshot for this menu session (static while open,
            // so the backdrop doesn't tremble with view-bob/world motion while you move).
            UiGlass.invalidate();
            mc.setScreen(this);
        }
    }

    @Override
    public void tick() {
        GifRender.tick();
        super.tick();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void centerCursor() {
        long handle = mc.getWindow().getHandle();
        GLFW.glfwSetCursorPos(handle, mc.getWindow().getWidth() / 2.0, mc.getWindow().getHeight() / 2.0);
    }

    private float currentScale() {
        int guiScale = mc.getWindow().calculateScaleFactor(mc.options.getGuiScale().getValue(), mc.forcesUnicodeFont());
        return (float) FIXED_GUI_SCALE / guiScale;
    }

    /* ============================ Bookkeeping ============================ */
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        lastDelta = delta;
    }

    /* ============================== Drawing ============================== */
    public void renderOverlay(DrawContext context, RenderTickCounter tickCounter) {
        if (mc.getWindow() == null) return;

        float delta = lastDelta;
        float animValue = openAnimation.getOutput().floatValue();

        // Per-frame clock for the setting-component animations (single shared dt this frame).
        SettingAnimationController.beginFrame();

        // Finalize close here (not in render()): this runs inside afterGuiRender,
        // AFTER the HUD was already drawn this frame via Drag.onDraw, so nulling
        // the screen now doesn't leave a one-frame gap where no HUD path renders.
        if (closing && openAnimation.isFinished(Direction.BACKWARDS)) {
            closing = false;
            for (Panel p : panels) p.resetState();
            mc.currentScreen = null;
            return;
        }

        context.createNewRootLayer();

        // Frosted-glass snapshot is captured live during the world-render pass
        // (GameRendererMixin.hookWorldRender) so it doesn't freeze. Do NOT invalidate
        // here — that would discard this frame's fresh capture before the panels sample it.

        float scale = currentScale();
        float mx = lastMouseX / scale, my = lastMouseY / scale;

        int vw = mc.getWindow().getWidth() / FIXED_GUI_SCALE;
        int vh = mc.getWindow().getHeight() / FIXED_GUI_SCALE;

        int n = panels.size();
        if (n == 0) return;

        float totalW = n * Theme.PANEL_WIDTH + (n - 1) * Theme.PANEL_GAP;
        float startX = (vw - totalW) / 2f;
        float baseY = (vh - Theme.PANEL_HEIGHT) / 2f;

        float yOffset = closing ? (1f - animValue) * 30f : (1f - animValue) * -15f;
        float panelY = baseY + yOffset;

        context.getMatrices().pushMatrix();
        context.getMatrices().scale(scale, scale);

        for (int i = 0; i < n; i++) {
            float px = startX + i * (Theme.PANEL_WIDTH + Theme.PANEL_GAP);
            panels.get(i).render(context, px, panelY, Theme.PANEL_WIDTH, Theme.PANEL_HEIGHT,
                    mx, my, delta, animValue);
        }

        sidePanel.render(context, vw, mx, my, animValue);

        Scissor.reset();
        context.getMatrices().popMatrix();
    }

    /* =============================== Input =============================== */
    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (closing) return false;
        float scale = currentScale();
        float mx = (float) (click.x() / scale), my = (float) (click.y() / scale);

        if (sidePanel.isHovered(mx, my)) {
            sidePanel.mouseClicked(mx, my, click.button());
            return true;
        }

        for (Panel p : panels) {
            if (p.isHovered(mx, my) && p.mouseClicked(mx, my, click.button())) return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (closing) return false;
        float scale = currentScale();
        float mx = (float) (click.x() / scale), my = (float) (click.y() / scale);

        sidePanel.mouseReleased(mx, my, click.button());
        for (Panel p : panels) p.mouseReleased(mx, my, click.button());
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (closing) return false;
        float scale = currentScale();
        float mx = (float) (mouseX / scale), my = (float) (mouseY / scale);

        if (sidePanel.mouseScrolled(mx, my, vertical)) return true;
        for (Panel p : panels) {
            if (p.mouseScrolled(mx, my, vertical)) return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (sidePanel.keyPressed(input.key(), input.scancode(), input.modifiers())) return true;

        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (closing) return false;

        for (Panel p : panels) {
            if (p.keyPressed(input.key(), input.scancode(), input.modifiers())) return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (closing) return false;
        if (sidePanel.charTyped((char) input.codepoint(), input.modifiers())) return true;
        for (Panel p : panels) {
            if (p.charTyped((char) input.codepoint(), input.modifiers())) return true;
        }
        return super.charTyped(input);
    }

    /* =============================== Close =============================== */
    @Override
    public void close() {
        if (closing) return;
        ThemeManager.INSTANCE.save();
        closing = true;
        openAnimation.setDirection(Direction.BACKWARDS);
        openAnimation.reset();

        long handle = mc.getWindow().getHandle();
        GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        centerCursor();
    }
}
