package rich.screens.clickgui.dropdown.components;


// контроль анимаций компачей
/**
 * Animation controller for the ClickGui setting components (Boolean / Slider / Select / Color /
 * MultiSelect / Group …).
 *
 * <p>Single source of truth for how those little in-panel animations <i>feel</i>: expand/collapse
 * of dropdowns, the slider knob "grab", the slider value glide, colour-marker glide, etc. Tweak the
 * central rate constants here and every component changes at once.</p>
 *
 * <p>Unlike whole screens / HUD elements, setting components are many, short-lived and per-instance
 * (panels are rebuilt on open), so a global keyed map is the wrong tool — each component keeps its
 * own little {@code float} animation state. This controller instead provides the shared <b>timing
 * model</b>: a single per-frame {@code dt} (so motion is frame-rate independent, the same at 60 or
 * 300 fps) plus time-based {@code approach()} helpers the components call to advance their state.</p>
 *
 * <p>{@link #beginFrame()} must be called once per render pass (done in
 * {@code ClickGui.renderOverlay}) so every {@code approach()} in that frame shares one {@code dt}.</p>
 */
public final class SettingAnimationController {

    private SettingAnimationController() {}

    /* ===================== Central tunables (the "one place") =====================
     * Rate = exponential smoothing speed. Higher = snappier, lower = more glide.       */
    public static float EXPAND_RATE = 16f;   // dropdown / group expand & collapse
    public static float GRAB_RATE   = 18f;   // slider / colour knob grab grow
    public static float SLIDER_RATE = 14f;   // slider fill / knob glide toward the value
    public static float MARKER_RATE = 14f;   // colour picker marker glide
    public static float TOGGLE_RATE = 21f;   // boolean on/off indicator
    public static float HOVER_RATE  = 16f;   // hover highlight fade

    /* ============================== Per-frame clock ============================== */
    private static long lastNanos = 0L;
    private static float dt = 0f;

    /** Call once per render pass: computes the shared {@code dt} used by all approach() calls. */
    public static void beginFrame() {
        long now = System.nanoTime();
        if (lastNanos == 0L) lastNanos = now;
        dt = Math.min(0.1f, (now - lastNanos) / 1_000_000_000f); // clamp stalls
        lastNanos = now;
    }

    /** Seconds elapsed since the previous frame (clamped). */
    public static float dt() {
        return dt;
    }

    /* ============================== Approach helpers ============================== */

    /**
     * Frame-rate independent move of {@code current} toward {@code target} at {@code rate}
     * (exponential smoothing). Snaps when close enough to avoid an endless crawl.
     */
    public static float approach(float current, float target, float rate) {
        float k = 1f - (float) Math.exp(-rate * dt);
        float v = current + (target - current) * k;
        return Math.abs(target - v) < 0.0005f ? target : v;
    }

    /** Expand/collapse 0..1 value heading toward {@code open}. */
    public static float expand(float current, boolean open) {
        return approach(current, open ? 1f : 0f, EXPAND_RATE);
    }

    /** Knob "grab" 0..1 value heading toward {@code held}. */
    public static float grab(float current, boolean held) {
        return approach(current, held ? 1f : 0f, GRAB_RATE);
    }

    /** Slider visual fraction gliding toward the real value fraction. */
    public static float slider(float current, float target) {
        return approach(current, target, SLIDER_RATE);
    }

    /** Colour-picker marker gliding toward its target position. */
    public static float marker(float current, float target) {
        return approach(current, target, MARKER_RATE);
    }

    /** Boolean on/off indicator 0..1 heading toward {@code on}. */
    public static float toggle(float current, boolean on) {
        return approach(current, on ? 1f : 0f, TOGGLE_RATE);
    }

    /** Hover highlight 0..1 heading toward {@code hovered}. */
    public static float hover(float current, boolean hovered) {
        return approach(current, hovered ? 1f : 0f, HOVER_RATE);
    }
}
