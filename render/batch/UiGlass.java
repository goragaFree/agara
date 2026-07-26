package rich.util.render.batch;

import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.MinecraftClient;
import rich.Initialization;
import rich.util.render.Render2D;
import rich.util.render.pipeline.KawaseBlurPipeline;

/**
 * Full-screen frosted-glass source for UI. Once per render pass it runs the
 * dual-Kawase blur (the high quality one already used by GlassHands) straight
 * off the framebuffer into a half-res texture; every {@link Render2D#glass}
 * panel then samples that texture by UV. Capture is lazy — nothing happens
 * unless a glass panel is actually drawn — and must be invalidated at the start
 * of each UI pass (HUD / ClickGui) so the snapshot is fresh.
 */
public final class UiGlass {

    private UiGlass() {}

    /** Kawase quality knobs (more iterations / bigger offset = softer, wider). */
    public static int iterations = 4;
    public static float offset = 3f;
    /**
     * Blur at 1/downscale resolution. NOTE: keep at 1. Values > 1 are currently WRONG with this
     * Kawase pipeline — it computes the first downsample's tap offsets from the (smaller) target
     * size while still sampling the full-res source, so it undersamples → aliasing that shimmers
     * ("wobbles") when the scene moves. The chain already returns its result at half resolution
     * natively (no full-res final pass), so there's nothing left to win here anyway.
     */
    public static int downscale = 1;

    /**
     * Re-blur only every Nth frame (1 = every frame). NOTE: any value > 1 makes the blurred
     * backdrop update in steps, which JUDDERS/shimmers ("wobbles") whenever the world behind the
     * menu is moving (e.g. on a server). Keep at 1 for smooth motion — half-res ({@link #downscale})
     * is what keeps it cheap. Raise only if the world is static (singleplayer pause) and you need FPS.
     */
    public static int captureInterval = 1;
    private static int frameCounter = 0;

    private static GpuTextureView blurredView;
    private static boolean ready = false;

    /**
     * Freeze the frosted backdrop on a single snapshot per menu session. The blurred world is
     * captured once (when {@code ready} is false — set by {@code invalidate()} on menu open) and
     * then held still, so it never trembles while the player moves (view-bob / world motion).
     * Set false for a live (every-frame) backdrop instead.
     */
    public static boolean staticSnapshot = false;

    /** Capture for this frame: once-per-session when {@link #staticSnapshot}, else throttled live. */
    public static void captureThrottled() {
        if (staticSnapshot) {
            if (!ready) capture();   // one snapshot, then frozen until next invalidate()
            return;
        }
        if (!ready || captureInterval <= 1 || (frameCounter++ % captureInterval) == 0) {
            capture();
        }
    }

    /** Drop the cached snapshot. Call at the start of every UI render pass. */
    public static void invalidate() {
        ready = false;
        blurredView = null;
    }

    /** Blurred screen texture for this pass. Captures lazily if not already done. May be null. */
    public static GpuTextureView blurred() {
        if (!ready) capture();
        return blurredView;
    }

    /**
     * Run the Kawase blur straight off the framebuffer's color attachment (no
     * intermediate copy — the first down pass reads it while rendering into the
     * blur chain's own target, which is legal and saves a full-res copy).
     * Call this while the framebuffer holds the freshly-rendered world (during the
     * world-render pass) so the glass stays live instead of freezing on a stale frame.
     */
    public static void capture() {
        ready = true;
        blurredView = null;

        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null) return;
            var fb = mc.getFramebuffer();
            if (fb == null || fb.getColorAttachment() == null) return;

            Initialization init = Initialization.getInstance();
            if (init == null || init.getManager() == null) return;
            var core = init.getManager().getRenderCore();
            if (core == null) return;
            KawaseBlurPipeline kawase = core.getKawaseBlurPipeline();
            if (kawase == null) return;

            int w = fb.textureWidth;
            int h = fb.textureHeight;
            if (w <= 0 || h <= 0) return;

            int ds = Math.max(1, downscale);
            int bw = Math.max(1, w / ds), bh = Math.max(1, h / ds);
            blurredView = kawase.blur(fb.getColorAttachment(), fb.getColorAttachmentView(),
                    bw, bh, iterations, offset);
        } catch (Exception ignored) {
            blurredView = null;
        }
    }

    public static void close() {
        ready = false;
        blurredView = null;
    }
}
