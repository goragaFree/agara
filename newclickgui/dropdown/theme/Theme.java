package rich.screens.clickgui.dropdown.theme;

import org.lwjgl.glfw.GLFW;
import rich.modules.module.category.ModuleCategory;
import rich.util.render.font.Font;
import rich.util.render.font.Fonts;

import java.awt.Color;

// хранит параметры оформления

/**
 * Centralised, easily tweakable style for the panel dropdown ClickGui.
 * Everything visual (colors, sizes, fonts) is configurable from one place.
 */


public final class Theme {

    private Theme() {}

    /* ============================ Layout ============================ */
    public static float PANEL_WIDTH   = 112f;
    public static float PANEL_HEIGHT  = 232f;
    public static float PANEL_GAP     = 8f;
    public static float PANEL_RADIUS  = 8f;
    public static float COLUMN_RADIUS = 8f;   // category columns
    public static float MODULE_RADIUS = 5f;
    public static float CONTROL_RADIUS= 4f;

    public static float HEADER_HEIGHT = 22f;
    public static float MODULE_HEIGHT = 18f;
    public static float MODULE_GAP    = 2f;

    /* ============================ Search ============================ */
    public static float SEARCH_WIDTH  = 200f;
    public static float SEARCH_HEIGHT = 18f;
    /** Vertical gap between the panel row and the search bar below it. */
    public static float SEARCH_GAP    = 12f;

    /* ============================ Fonts ============================ */
    public static final Font FONT  = Fonts.BOLD;
    public static final Font ICONS = Fonts.GUI_ICONS;
    public static final Font CATEGORY_ICONS = Fonts.CATEGORY_ICONS;

    /** Category glyph in the {@link #CATEGORY_ICONS} font (same as the old menu). */
    public static String categoryIcon(ModuleCategory category) {
        switch (category) {
            case COMBAT:   return "a";
            case MOVEMENT: return "b";
            case RENDER:   return "c";
            case PLAYER:   return "d";
            case MISC:     return "e";
            case AUTOBUY:  return "g";
            default:       return "";
        }
    }

    /** Font that holds the glyph returned by {@link #categoryIcon(ModuleCategory)}. */
    public static Font categoryIconFont(ModuleCategory category) {
        switch (category) {
            case MOVEMENT: return Fonts.NEWICON;
            default:       return CATEGORY_ICONS;
        }
    }

    public static float TITLE_SIZE   = 7.5f;
    public static float MODULE_SIZE  = 6f;
    public static float SETTING_SIZE = 6f;
    public static float SMALL_SIZE   = 5f;

    /* ============================ Colors ============================ */
    /** Accent color – change this single field to re-skin the whole menu. */
    public static Color ACCENT = new Color(123, 112, 255);

    public static Color DIM          = new Color(0, 0, 0, 130);
    public static Color PANEL_FILL   = new Color(20, 20, 24, 240);
    /** Tint for the frosted-glass panel background: rgb = color, alpha = strength. */
    public static Color GLASS_TINT   = new Color(16, 16, 20, 168);
    /** Liquid-glass edge refraction amount (Rockstar "distortion"), UV units. */
    public static float GLASS_DISTORTION = 0.08f;
    /** Liquid-glass edge highlight/fresnel amount (Rockstar "strength"), 0..1. */
    public static float GLASS_STRENGTH   = 0.25f;
    public static Color PANEL_OUTLINE= new Color(255, 255, 255, 8);
    public static Color SEPARATOR    = new Color(255, 255, 255, 16);

    /** Soft drop-shadow under panels (Rockstar-style). */
    public static Color SHADOW       = new Color(0, 0, 0, 110);
    /** Shadow feather radius, in fixed-scaled px. */
    public static float PANEL_SHADOW = 9f;

    public static Color TITLE        = new Color(236, 236, 242);
    public static Color TEXT_ON      = new Color(244, 244, 248);
    public static Color TEXT_OFF     = new Color(150, 150, 158);
    public static Color TEXT_DESC    = new Color(110, 110, 118);

    public static Color HOVER        = new Color(255, 255, 255, 14);
    public static Color SELECT_BG    = new Color(255, 255, 255, 20);
    public static Color CONTROL_BG   = new Color(255, 255, 255, 22);
    public static Color CONTROL_LINE = new Color(255, 255, 255, 30);

    /* ============================ Helpers ============================ */

    /**
     * Насколько контент, отступающий от боковых краёв панели на {@code pad},
     * должен подняться над её низом, чтобы не вылезать за дугу скруглённого
     * угла. Считается из геометрии угла (0 — дуга до контента не достаёт):
     * при штатном радиусе это ~1px вместо линейной «мёртвой зоны», а при
     * больших радиусах клип не режет контент высоко над краем панели.
     */
    public static float cornerInset(float radius, float pad) {
        float reach = radius - pad;   // насколько дуга «глубже» бокового отступа
        if (reach <= 0f) return 0f;
        return radius - (float) Math.sqrt(radius * radius - reach * reach) + 0.5f;
    }

    public static int color(Color c, float alpha) {
        int a = clampByte((int) (c.getAlpha() * alpha));
        return (a << 24) | (c.getRGB() & 0xFFFFFF);
    }

    public static int color(int r, int g, int b, int a, float alpha) {
        int na = clampByte((int) (a * alpha));
        return (na << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    public static int accent(float alpha) {
        return color(ACCENT, alpha);
    }

    /**
     * Цвет контента ПОВЕРХ акцентной заливки (шайба тумблера, «...» на
     * бинд-капсуле, подпись кнопки под ховером): на тёмном акценте — белый,
     * на светлом/белом — тёмный. Выбирается по перцептивной яркости акцента,
     * поэтому контент читается при любом цвете темы.
     */
    public static Color onAccent() {
        float lum = (0.299f * ACCENT.getRed() + 0.587f * ACCENT.getGreen()
                + 0.114f * ACCENT.getBlue()) / 255f;
        return lum > 0.6f ? new Color(22, 22, 26, 235) : new Color(255, 255, 255, 235);
    }

    public static Color lerp(Color a, Color b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return new Color(
                clampByte((int) (a.getRed()   + (b.getRed()   - a.getRed())   * t)),
                clampByte((int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t)),
                clampByte((int) (a.getBlue()  + (b.getBlue()  - a.getBlue())  * t)),
                clampByte((int) (a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t)));
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static int clampByte(int v) {
        return Math.max(0, Math.min(255, v));
    }

    public static String keyName(int key) {
        if (key == GLFW.GLFW_KEY_UNKNOWN || key == -1) return "None";
        // Единый источник имён клавиш — KeyHelper: фиксированные английские
        // имена, НЕ зависящие от раскладки. Прежний путь через glfwGetKeyName
        // на русской раскладке возвращал кириллицу («Ё» вместо Grave), и
        // подпись бинда менялась вместе с языком системы.
        return rich.util.string.KeyHelper.getKeyName(key);
    }
}
