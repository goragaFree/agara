package rich.screens.clickgui.dropdown.components.impl;

import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;
import rich.modules.module.setting.implement.BindSetting;
import rich.screens.clickgui.dropdown.components.SettingAnimationController;
import rich.screens.clickgui.dropdown.theme.Theme;
import rich.screens.clickgui.dropdown.components.SettingComponent;
import rich.util.render.Render2D;

import java.awt.Color;

/** Key-capture box for a {@link BindSetting}. */
public class BindComp extends SettingComponent {
    private final BindSetting s;
    private boolean listening;

    // animation state: hover fade, listen glide, breathing pulse while listening, set-confirm flash
    private float hover, listenA, flash, pulse;

    public BindComp(BindSetting s) { super(s); this.s = s; }

    @Override public float getHeight() { return 14f; }
    @Override public boolean isCapturing() { return listening; }

    @Override
    public void render(DrawContext ctx, float mx, float my, float delta) {
        float dt = SettingAnimationController.dt();
        hover   = SettingAnimationController.hover(hover, hovered(mx, my, getHeight()));
        listenA = SettingAnimationController.approach(listenA, listening ? 1f : 0f, 16f);
        flash   = SettingAnimationController.approach(flash, 0f, 9f);   // decays after a bind is set
        pulse   = listening ? pulse + dt : 0f;
        float breathe = 0.5f + 0.5f * (float) Math.sin(pulse * 6.5f);   // 0..1 while listening

        float cy = y + getHeight() / 2f;
        drawText(s.getName(), x, cy - Theme.SETTING_SIZE / 2f, Theme.SETTING_SIZE,
                Theme.lerp(Theme.TEXT_OFF, Theme.TEXT_ON, hover));

        String txt = listening ? "..." : Theme.keyName(s.getKey());
        float tw = Theme.FONT.getWidth(txt, Theme.SMALL_SIZE);
        float bw = Math.max(18f, tw + 8f), bh = 9f;
        float bx = x + width - bw, by = cy - bh / 2f;

        // pop on set + a gentle breathe while listening, inflated around the box centre
        float infl = flash * 1.6f + listenA * breathe * 0.8f;
        float dbx = bx - infl, dby = by - infl, dbw = bw + infl * 2f, dbh = bh + infl * 2f;

        // fill glides from the idle control colour to accent while listening
        Color fill = Theme.lerp(Theme.CONTROL_BG, Theme.ACCENT, listenA);
        Render2D.rect(dbx, dby, dbw, dbh, Theme.color(fill, alpha), Theme.CONTROL_RADIUS);

        // breathing accent halo while waiting for a key
        if (listenA > 0.001f) {
            float glow = listenA * (0.35f + 0.65f * breathe);
            Render2D.outline(dbx, dby, dbw, dbh, 0.8f, Theme.color(Theme.ACCENT, alpha * glow), Theme.CONTROL_RADIUS);
        }
        // white flash the moment a bind is captured, fading out
        if (flash > 0.001f)
            Render2D.rect(dbx, dby, dbw, dbh, Theme.color(new Color(255, 255, 255, 120), alpha * flash), Theme.CONTROL_RADIUS);

        // в режиме прослушивания капсула залита акцентом — «...» рисуются
        // контрастным к акценту цветом (Theme.onAccent), иначе при белом
        // акценте белые точки сливались с заливкой
        drawText(txt, bx + (bw - tw) / 2f, by + (bh - Theme.SMALL_SIZE) / 2f, Theme.SMALL_SIZE,
                listening ? Theme.onAccent() : Theme.lerp(Theme.TEXT_OFF, Theme.TEXT_ON, hover));
    }

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        // while listening, any mouse click binds that mouse button (0..7 -> treated as a mouse bind)
        if (listening) {
            s.setKey(button);
            listening = false;
            flash = 1f;          // confirm pop
            return true;
        }
        if (button == 0 && hovered(mx, my, getHeight())) {
            listening = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (!listening) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_DELETE) s.setKey(GLFW.GLFW_KEY_UNKNOWN);
        else s.setKey(key);
        listening = false;
        flash = 1f;              // confirm pop
        return true;
    }
}
