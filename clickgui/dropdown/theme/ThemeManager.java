package rich.screens.clickgui.dropdown.theme;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;


//настройки темы плюс сохранение
/**
 * Holds the live GUI colors (always persisted, so the menu keeps its look between
 * sessions) and a list of explicitly saved themes (named, starts empty). Editing
 * colors only changes the live set; a theme is added to the list only when the
 * user saves one.
 */
public final class ThemeManager {

    public static final ThemeManager INSTANCE = new ThemeManager();

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path path;

    private final GuiTheme current = new GuiTheme();
    private final List<SavedTheme> saved = new ArrayList<>();

    private ThemeManager() {
        Path dir = Paths.get("Rich", "configs");
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
        path = dir.resolve("Theme.json");
        load();
        apply();
    }

    public GuiTheme current() { return current; }
    public List<SavedTheme> getSaved() { return saved; }

    /** Push the live colors into {@link Theme}. */
    public void apply() {
        // Panel transparency comes from the dedicated opacity setting, NOT the
        // background colour's own alpha — the colour picker only changes the tint.
        int glassAlpha = Math.round(clamp01(current.opacity) * 255f);
        Theme.ACCENT = current.accent;
        Theme.GLASS_TINT = withAlpha(current.background, glassAlpha);
        Theme.PANEL_FILL = withAlpha(current.background, glassAlpha);
        Theme.TITLE = current.text;
        Theme.TEXT_ON = current.text;
        Theme.TEXT_OFF = dim(current.text, 0.62f);
        Theme.TEXT_DESC = dim(current.text, 0.46f);
        // Cap the outline alpha: the theme picks the tint, but the border must
        // stay a subtle edge. Without this a theme set to opaque white draws a
        // glaring 1px line that "outlines" every panel and makes the rounded
        // corners stand out. RGB is kept; only the alpha is clamped down.
        Theme.PANEL_OUTLINE = capAlpha(current.outline, 24);
        Theme.CONTROL_LINE = capAlpha(current.outline, 40);
        Theme.SEPARATOR = current.extra;
        Theme.CONTROL_BG = current.extra;

        // liquid-glass knobs
        Theme.GLASS_STRENGTH = clamp01(current.glassStrength);
        Theme.GLASS_DISTORTION = current.glassDistortion;
        // Rockstar-style: keep the per-pass offset ~1px so the downsample passes sample adjacent
        // texels (correct 2x2 footprint). A large offset undersamples -> aliasing that shimmers
        // /trembles under motion. Drive blur WIDTH by the number of Kawase steps (iterations).
        rich.util.render.batch.UiGlass.offset = 1.0f;
        rich.util.render.batch.UiGlass.iterations =
                Math.max(2, Math.min(7, Math.round(2f + clamp01(current.glassBlur / 8f) * 5f)));
    }

    /** Save the current colors as a new named theme. */
    public void addCurrentAsTheme(String name) {
        if (name == null || name.isBlank()) return;
        saved.add(new SavedTheme(name.trim(), current.copy()));
        save();
    }

    /** Load a saved theme's colors into the live set. */
    public void applyTheme(int i) {
        if (i >= 0 && i < saved.size()) {
            current.copyFrom(saved.get(i).colors);
            apply();
            save();
        }
    }

    public void removeTheme(int i) {
        if (i >= 0 && i < saved.size()) {
            saved.remove(i);
            save();
        }
    }

    /* ============================ Persistence ============================ */
    public void save() {
        try {
            JsonObject root = new JsonObject();
            root.add("current", colorsToJson(current));
            JsonArray arr = new JsonArray();
            for (SavedTheme t : saved) {
                JsonObject o = colorsToJson(t.colors);
                o.addProperty("name", t.name);
                arr.add(o);
            }
            root.add("themes", arr);
            Files.writeString(path, gson.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }

    public void load() {
        try {
            if (!Files.exists(path)) return;
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.has("current")) colorsFromJson(root.getAsJsonObject("current"), current);
            saved.clear();
            if (root.has("themes")) {
                for (var el : root.getAsJsonArray("themes")) {
                    JsonObject o = el.getAsJsonObject();
                    GuiTheme g = new GuiTheme();
                    colorsFromJson(o, g);
                    String name = o.has("name") ? o.get("name").getAsString() : "Theme";
                    saved.add(new SavedTheme(name, g));
                }
            }
        } catch (Exception ignored) {}
    }

    private static JsonObject colorsToJson(GuiTheme g) {
        JsonObject o = new JsonObject();
        o.addProperty("accent", g.accent.getRGB());
        o.addProperty("background", g.background.getRGB());
        o.addProperty("text", g.text.getRGB());
        o.addProperty("outline", g.outline.getRGB());
        o.addProperty("extra", g.extra.getRGB());
        o.addProperty("opacity", g.opacity);
        o.addProperty("glassStrength", g.glassStrength);
        o.addProperty("glassDistortion", g.glassDistortion);
        o.addProperty("glassBlur", g.glassBlur);
        return o;
    }

    private static void colorsFromJson(JsonObject o, GuiTheme g) {
        if (o.has("accent"))     g.accent     = new Color(o.get("accent").getAsInt(), true);
        if (o.has("background")) g.background = new Color(o.get("background").getAsInt(), true);
        if (o.has("text"))       g.text       = new Color(o.get("text").getAsInt(), true);
        if (o.has("outline"))    g.outline    = new Color(o.get("outline").getAsInt(), true);
        if (o.has("extra"))      g.extra      = new Color(o.get("extra").getAsInt(), true);
        // backward compat: old configs had no opacity -> derive from the background alpha
        g.opacity = o.has("opacity") ? o.get("opacity").getAsFloat() : g.background.getAlpha() / 255f;
        if (o.has("glassStrength"))   g.glassStrength   = o.get("glassStrength").getAsFloat();
        if (o.has("glassDistortion")) g.glassDistortion = o.get("glassDistortion").getAsFloat();
        if (o.has("glassBlur"))       g.glassBlur       = o.get("glassBlur").getAsFloat();
    }

    private static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    private static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }

    /** Returns the color with its alpha clamped to at most {@code maxAlpha}. */
    private static Color capAlpha(Color c, int maxAlpha) {
        if (c.getAlpha() <= maxAlpha) return c;
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), maxAlpha);
    }

    private static Color dim(Color c, float f) {
        return new Color(
                Math.round(c.getRed() * f),
                Math.round(c.getGreen() * f),
                Math.round(c.getBlue() * f),
                c.getAlpha());
    }
}
