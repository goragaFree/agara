package rich.screens.clickgui.dropdown.tab;

import net.minecraft.client.gui.DrawContext;

//  Описывает функциональность вкладки боковой панели

/**
 * One section of the side panel (Themes / Settings / Config …). The {@link SidePanel} container
 * owns the chrome — glass background, header, the left sidebar of tab buttons and the expand
 * animation — while each tab only knows how to draw and handle <b>its own content</b>. This keeps
 * the blocks out of a single god-class: add a new tab by writing a new {@code PanelTab}.
 */
public interface PanelTab {

    /**
     * Geometry handed to a tab each frame. {@code panel*} is the whole panel box (used e.g. to
     * place a popup to its left); {@code x/y/w} is the content area to the right of the sidebar.
     */
    record Region(float panelX, float panelY, float panelW, float x, float y, float w) {}

    /** Title shown in the panel header while this tab is active. */
    String title();

    /** Draw this tab's sidebar-button icon, centred at (cx, cy). Kept here so each tab owns its look. */
    void icon(DrawContext ctx, float cx, float cy, float size, int color);

    /** Height of the content block when fully expanded (drives the panel height). */
    float contentHeight();

    /** Draw the content inside {@code r} (already clipped to the expand animation by the container). */
    void render(DrawContext ctx, Region r, float mx, float my, float alpha);

    /** Floating overlay drawn outside the content clip (e.g. a colour picker). No-op by default. */
    default void renderModal(DrawContext ctx, Region r, float mx, float my, float alpha) {}

    default boolean mouseClicked(Region r, float mx, float my, int button) { return false; }
    default boolean mouseReleased(float mx, float my, int button) { return false; }
    default boolean keyPressed(int key, int scan, int mods) { return false; }
    default boolean charTyped(char chr, int mods) { return false; }

    /** A text field is focused — used to keep keystrokes inside the panel. */
    default boolean isTyping() { return false; }

    /** Modal element open (e.g. picker) — captures all clicks while true. */
    default boolean modal() { return false; }
}
