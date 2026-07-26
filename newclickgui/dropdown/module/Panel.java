package rich.screens.clickgui.dropdown.module;

import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;
import rich.Initialization;
import rich.modules.module.ModuleStructure;
import rich.modules.module.category.ModuleCategory;
import rich.screens.clickgui.dropdown.components.SettingAnimationController;
import rich.screens.clickgui.dropdown.components.SettingComponent;
import rich.screens.clickgui.dropdown.theme.Theme;
import rich.util.animations.SmoothAnimation;
import rich.util.render.batch.Batch;
import rich.util.render.Render2D;
import rich.util.render.shader.Scissor;

import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;


// категории списое модулей и переключение между списком и
// * настройками выбранного модуля, плюс анимки и прокрутка
/** A category panel: lists modules and swaps to a module's settings on demand. */
public class Panel {

    private static final float SCISSOR_SCALE = 2f;

    // back-arrow geometry (settings view) — shared by render + click hit-testing
    private static final float ARROW_INSET = 7f;   // apex distance from panel left edge
    private static final float ARROW_W = 4f;       // arrow width  (apex -> base)
    private static final float ARROW_H = 7f;       // arrow height (full)

    private final ModuleCategory category;
    private final List<ModuleButton> buttons = new ArrayList<>();
    private final SmoothAnimation swap = new SmoothAnimation();

    private float x, y, width, height;
    private float moduleScroll, settingsScroll;          // smoothed (rendered) value
    private float moduleTarget, settingsTarget;          // wheel target
    private float moduleMax, settingsMax;
    private long lastScrollNanos;

    private float backArrowHover;          // 0..1 smoothed hover highlight for the back arrow
    private long lastHeaderNanos;

    private ModuleButton selected;
    private ModuleButton binding;

    /** Lower-cased search query from the {@code SearchBar}; empty = no filtering. */
    private String filter = "";
    /** Rows that are actually visible this frame — the only ones that take clicks. */
    private final List<ModuleButton> hitButtons = new ArrayList<>();
    private float emptyFade;   // 0..1 — "ничего не найдено" hint fade

    public Panel(ModuleCategory category) {
        this.category = category;
        try {
            var repo = Initialization.getInstance().getManager().getModuleRepository();
            if (repo != null) {
                List<ModuleStructure> mods = new ArrayList<>();
                for (ModuleStructure m : repo.modules()) if (m.getCategory() == category) mods.add(m);
                mods.sort(Comparator.comparing(ModuleStructure::getName));
                for (ModuleStructure m : mods) buttons.add(new ModuleButton(m));
            }
        } catch (Exception ignored) {}
    }

    public ModuleCategory getCategory() { return category; }
    public boolean isEmpty() { return buttons.isEmpty(); }

    public boolean isHovered(float mx, float my) {
        return mx >= x && mx <= x + width && my >= y && my <= y + height;
    }

    public boolean isCapturing() {
        if (binding != null) return true;
        return selected != null && selected.isCapturing();
    }

    public void resetState() {
        selected = null;
        if (binding != null) binding.setBinding(false);
        binding = null;
        swap.set(0);
        // Прокрутка СПИСКА модулей намеренно НЕ сбрасывается: закрыл меню,
        // открыл снова — список на том же месте. Панели пересоздаются при
        // каждом открытии (ClickGui.buildPanels), но позиция переносится со
        // старых панелей на новые (см. restoreModuleScroll). При перезапуске
        // клиента переносить неоткуда — меню естественно стартует сверху.
        // Скролл НАСТРОЕК сбрасываем: вид настроек при закрытии сворачивается.
        settingsScroll = settingsTarget = 0;
        backArrowHover = 0;
        filter = "";
        emptyFade = 0;
        hitButtons.clear();
        for (ModuleButton b : buttons) b.setReveal(1f);
    }

    /** Целевая прокрутка списка модулей — для переноса между пересозданиями панелей. */
    public float getModuleScrollTarget() {
        return moduleTarget;
    }

    /**
     * Восстановить прокрутку списка снапом (без «доезда» с самого верха) —
     * вызывается из ClickGui.buildPanels при пересоздании панелей, чтобы
     * меню открывалось на том же месте, где его закрыли. Выход за пределы
     * не страшен: цель клампится по moduleMax каждый кадр.
     */
    public void restoreModuleScroll(float value) {
        this.moduleTarget = value;
        this.moduleScroll = value;
    }

    /* ============================== Search ============================== */

    /** Live filter from the search bar: matching rows stay, the rest fold away. */
    public void setFilter(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        if (q.equals(filter)) return;
        filter = q;
        moduleTarget = 0f;                                   // fresh results start from the top
        if (!filter.isEmpty() && selected != null) back();   // searching always shows the list view
    }

    /** How many modules of this panel match the current filter. */
    public int matchCount() {
        int n = 0;
        for (ModuleButton b : buttons) if (matchesFilter(b)) n++;
        return n;
    }

    private boolean matchesFilter(ModuleButton b) {
        if (filter.isEmpty()) return true;
        if (b.getModule().getName().toLowerCase().contains(filter)) return true;
        String desc = b.getModule().getDescription();
        return desc != null && !desc.isEmpty() && desc.toLowerCase().contains(filter);
    }

    /* ============================== Render ============================== */
    public void render(DrawContext ctx, float x, float y, float width, float height,
                       float mx, float my, float delta, float alpha) {
        this.x = x; this.y = y; this.width = width; this.height = height;
        swap.update();
        float s = swap.get();

        Render2D.shadow(x, y, width, height, Theme.COLUMN_RADIUS, Theme.PANEL_SHADOW, Theme.color(Theme.SHADOW, alpha));
        Render2D.glass(x, y, width, height, Theme.COLUMN_RADIUS, Theme.GLASS_TINT.getRGB(), alpha);
        Render2D.outline(x, y, width, height, 1f, Theme.color(Theme.PANEL_OUTLINE, alpha), Theme.COLUMN_RADIUS);

        float sepY = y + Theme.HEADER_HEIGHT;
        float contentY = sepY + 1f;
        float pad = 5f;
        // keep the content clear of the panel's rounded bottom corners so the
        // accent bar / rows can't poke out past the rounded edge. The inset is
        // derived from the corner geometry (rows are inset `pad` from the
        // sides, so only the part of the arc deeper than `pad` matters): at
        // the stock radius that's ~1px instead of a fixed dead band, and big
        // radii no longer cut the list high above the bottom edge.
        float bottomInset = Theme.cornerInset(Theme.COLUMN_RADIUS, pad);
        float contentH = height - Theme.HEADER_HEIGHT - 1f - bottomInset;

        // --- header pass (not scissored) : one rect flush + one text flush ---
        Batch.beginText();
        renderHeader(ctx, s, alpha, mx, my);
        Render2D.rect(x + 4f, sepY, width - 8f, 0.6f, Theme.color(Theme.SEPARATOR, alpha), 0f);
        Batch.endText();

        // --- content pass : batched inside the scissor ---
        Scissor.enable(x, contentY, width, contentH, SCISSOR_SCALE);
        Batch.beginText();
        if (s < 0.999f) {
            renderModuleList(ctx, x - width * s, contentY, width, contentH, mx, my, alpha * (1f - s), pad);
        }
        if (s > 0.001f && selected != null) {
            renderSettings(ctx, x + width * (1f - s), contentY, width, contentH, mx, my, delta, alpha * s, pad);
        }
        Batch.endText();
        Scissor.disable();
    }

    private void renderHeader(DrawContext ctx, float s, float alpha, float mx, float my) {
        float cy = y + Theme.HEADER_HEIGHT / 2f;
        // category title (fades out when entering settings)
        if (s < 0.999f) {
            Theme.FONT.draw(category.getReadableName(), x + 8f, cy - Theme.TITLE_SIZE / 2f,
                    Theme.TITLE_SIZE, Theme.color(Theme.TITLE, alpha * (1f - s)));

            String icon = Theme.categoryIcon(category);
            rich.util.render.font.Font iconFont = Theme.categoryIconFont(category);
            float iconSize = 9f;
            float iw = iconFont.getWidth(icon, iconSize);
            iconFont.draw(icon, x + width - iw - 7f, cy - iconSize / 2f, iconSize,
                    Theme.accent(alpha * (1f - s)));
        }
        // back header (fades in)
        if (s > 0.001f && (selected != null)) {
            // smooth hover highlight: dim by default, brightens + slides left under the cursor
            boolean hovered = s > 0.5f && isOverBackArrow(mx, my);
            backArrowHover = smoothHover(backArrowHover, hovered ? 1f : 0f);

            float a = alpha * s;
            float dim = 0.55f + 0.45f * backArrowHover;        // 0.55 idle -> 1.0 hovered
            float ax = x + ARROW_INSET - 1.5f * backArrowHover; // nudge left on hover
            drawBackArrow(ax, cy, ARROW_W, ARROW_H, Theme.color(Theme.ACCENT, a * dim));
            Theme.FONT.draw(selected.getModule().getName(), x + ARROW_INSET + ARROW_W + 5f,
                    cy - Theme.TITLE_SIZE / 2f, Theme.TITLE_SIZE, Theme.color(Theme.TITLE, a));
        }
    }

    /** Frame-rate independent exponential smoothing for the back-arrow hover highlight. */
    private float smoothHover(float current, float target) {
        long now = System.nanoTime();
        float dt = lastHeaderNanos == 0L ? 1f / 60f : (now - lastHeaderNanos) / 1_000_000_000f;
        lastHeaderNanos = now;
        dt = Math.min(dt, 0.1f);
        float factor = 1f - (float) Math.exp(-14f * dt);
        float v = current + (target - current) * factor;
        return Math.abs(target - v) < 0.002f ? target : v;
    }

    /** Small left-pointing triangle ("back"), drawn as vertical scanlines (no rotation primitive). */
    private void drawBackArrow(float apexX, float cy, float w, float h, int color) {
        int steps = Math.max(1, (int) (w * 3f));   // ~0.33px columns -> smooth edge
        float colW = w / steps;
        for (int i = 0; i < steps; i++) {
            float dx = i * colW;
            float halfH = (dx / w) * (h / 2f);     // 0 at apex, full at base
            if (halfH <= 0f) continue;
            Render2D.rect(apexX + dx, cy - halfH, colW + 0.5f, halfH * 2f, color, 0f);
        }
    }

    /** Hit-area for the back arrow: only the arrow (plus a little padding), not the whole header. */
    private boolean isOverBackArrow(float mx, float my) {
        float x0 = x + ARROW_INSET - 4f;
        float x1 = x + ARROW_INSET + ARROW_W + 4f;
        return mx >= x0 && mx <= x1 && my >= y && my <= y + Theme.HEADER_HEIGHT;
    }

    private void renderModuleList(DrawContext ctx, float lx, float ly, float lw, float lh,
                                  float mx, float my, float alpha, float pad) {
        float rowH = Theme.MODULE_HEIGHT + Theme.MODULE_GAP;
        hitButtons.clear();

        // each row owns an animated 0..1 reveal driven by the search filter; the layout
        // advances by (rowH * reveal), so rows fold shut / slide open and the list
        // reflows smoothly instead of jumping when the query changes.
        float total = 0f;
        int matched = 0;
        for (ModuleButton b : buttons) {
            boolean match = matchesFilter(b);
            if (match) matched++;
            b.setReveal(SettingAnimationController.approach(b.getReveal(), match ? 1f : 0f, 14f));
            total += rowH * b.getReveal();
        }

        moduleMax = Math.min(0f, lh - total - 4f);
        moduleTarget = Math.max(moduleMax, Math.min(0f, moduleTarget));
        moduleScroll = smoothScroll(moduleScroll, moduleTarget);

        float ry = ly + 3f + moduleScroll;
        boolean firstVisible = true;
        for (ModuleButton b : buttons) {
            float reveal = b.getReveal();
            if (reveal <= 0.01f) continue;
            // content fades over the top part of the slot growth, so a folding row's
            // text is long gone before the neighbours slide across its slot.
            float fade = Math.max(0f, (reveal - 0.55f) / 0.45f);
            if (fade > 0.01f) {
                b.render(ctx, lx + pad, ry, lw - pad * 2f, mx, my, alpha * fade, !firstVisible);
                if (fade > 0.5f) hitButtons.add(b);
                firstVisible = false;
            }
            ry += rowH * reveal;
        }

        // dim hint when the search filtered the whole panel away
        emptyFade = SettingAnimationController.approach(emptyFade,
                !filter.isEmpty() && matched == 0 ? 1f : 0f, 14f);
        if (emptyFade > 0.01f) {
            Theme.FONT.drawCentered("Ничего не найдено", lx + lw / 2f, ly + lh / 2f - Theme.SMALL_SIZE,
                    Theme.SMALL_SIZE, Theme.color(Theme.TEXT_DESC, alpha * emptyFade));
        }
    }

    private void renderSettings(DrawContext ctx, float lx, float ly, float lw, float lh,
                                float mx, float my, float delta, float alpha, float pad) {
        float total = 0f;
        for (SettingComponent c : selected.getComponents()) {
            if (c.isVisible()) total += c.getHeight() + 3f;
        }
        settingsMax = Math.min(0f, lh - total - 4f);
        settingsTarget = Math.max(settingsMax, Math.min(0f, settingsTarget));
        settingsScroll = smoothScroll(settingsScroll, settingsTarget);

        float oy = ly + 4f + settingsScroll;
        for (SettingComponent c : selected.getComponents()) {
            if (!c.isVisible()) continue;
            c.place(lx + pad, oy, lw - pad * 2f, alpha);
            if (oy + c.getHeight() >= ly && oy <= ly + lh) {
                c.render(ctx, mx, my, delta);
            }
            oy += c.getHeight() + 3f;
        }
    }

    private float smoothScroll(float current, float target) {
        // Frame-rate independent exponential smoothing so the feel is the same
        // at any FPS (fixed per-frame lerp felt jumpy/inconsistent).
        long now = System.nanoTime();
        float dt = lastScrollNanos == 0L ? 1f / 60f : (now - lastScrollNanos) / 1_000_000_000f;
        lastScrollNanos = now;
        dt = Math.min(dt, 0.1f);

        float rate = 11f;                 // lower = smoother / slower settle
        float factor = 1f - (float) Math.exp(-rate * dt);
        float v = current + (target - current) * factor;
        return Math.abs(target - v) < 0.05f ? target : v;
    }

    /* ============================== Input ============================== */
    public boolean mouseClicked(float mx, float my, int button) {
        // capture a MOUSE-button bind while in binding mode (keyboard is handled in keyPressed).
        // mouse buttons are stored as their GLFW index (0..7); the key system treats key<8 as MOUSE.
        if (binding != null) {
            binding.getModule().setKey(button);
            binding.setBinding(false);
            binding = null;
            return true;
        }
        if (!isHovered(mx, my)) return false;

        if (selected == null) {
            // header has no action in list view
            if (my <= y + Theme.HEADER_HEIGHT) return true;

            for (ModuleButton b : hitButtons) {
                if (!b.contains(mx, my)) continue;
                if (button == 0) {
                    b.getModule().switchState();
                } else if (button == 1) {
                    if (b.hasSettings()) { open(b); }
                } else if (button == 2) {
                    setBinding(b);
                }
                return true;
            }
            return true;
        } else {
            // settings view
            if (my <= y + Theme.HEADER_HEIGHT) {
                // only the back arrow goes back, not the whole header
                if (button == 0 && isOverBackArrow(mx, my)) back();
                return true;
            }
            // a capturing component (e.g. a BindComp listening for a bind) gets the click first,
            // so it can bind ANY mouse button — including middle — before the module-bind shortcut.
            for (SettingComponent c : selected.getComponents()) {
                if (c.isVisible() && c.isCapturing() && c.mouseClicked(mx, my, button)) return true;
            }
            if (button == 2) { setBinding(selected); return true; }
            for (SettingComponent c : selected.getComponents()) {
                if (c.isVisible() && c.mouseClicked(mx, my, button)) return true;
            }
            return true;
        }
    }

    public boolean mouseReleased(float mx, float my, int button) {
        if (selected != null) {
            boolean handled = false;
            for (SettingComponent c : selected.getComponents()) {
                if (c.isVisible() && c.mouseReleased(mx, my, button)) handled = true;
            }
            return handled;
        }
        return false;
    }

    public boolean mouseScrolled(float mx, float my, double amount) {
        if (!isHovered(mx, my)) return false;
        if (selected != null) {
            settingsTarget = Math.max(settingsMax, Math.min(0f, settingsTarget + (float) amount * 15f));
        } else {
            moduleTarget = Math.max(moduleMax, Math.min(0f, moduleTarget + (float) amount * 15f));
        }
        return true;
    }

    public boolean keyPressed(int key, int scan, int mods) {
        if (binding != null) {
            int k = (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_ESCAPE) ? GLFW.GLFW_KEY_UNKNOWN : key;
            binding.getModule().setKey(k);
            binding.setBinding(false);
            binding = null;
            return true;
        }
        if (selected != null) {
            for (SettingComponent c : selected.getComponents()) {
                if (c.isVisible() && c.keyPressed(key, scan, mods)) return true;
            }
        }
        return false;
    }

    public boolean charTyped(char chr, int mods) {
        if (selected != null) {
            for (SettingComponent c : selected.getComponents()) {
                if (c.isVisible() && c.charTyped(chr, mods)) return true;
            }
        }
        return false;
    }

    private void open(ModuleButton b) {
        selected = b;
        settingsScroll = settingsTarget = 0;
        swap.run(1, 0.35);
    }

    private void back() {
        swap.run(0, 0.35);
        selected = null;
    }

    private void setBinding(ModuleButton b) {
        if (binding != null) binding.setBinding(false);
        binding = b;
        b.setBinding(true);
    }

    public void openModuleFromSearch(ModuleStructure module) {
        for (ModuleButton b : buttons) {
            if (b.getModule() == module && b.hasSettings()) { open(b); return; }
        }
    }
}
