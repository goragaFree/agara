package rich.util.render.batch;

import rich.Initialization;
import rich.util.render.Render2D;
import rich.util.render.font.FontRenderer;

/**
 * Rockstar-style draw batching.
 *
 * <p>Normally every {@code Render2D.rect}/{@code Font.draw} can trigger its own
 * GPU render pass (text especially — each draw flushes). Inside a batch the
 * rectangles and glyphs instead accumulate into their pipelines and are flushed
 * together, collapsing dozens of draws into ~2 passes (one for rects, one for
 * text). This is what keeps the menu/HUD cheap even with lots of elements.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // try-with-resources (auto end):
 * try (Batch b = Batch.begin()) {
 *     Render2D.rect(x, y, w, h, color, radius);
 *     Fonts.BOLD.draw("hello", x, y, 6, color);
 *     Render2D.rect(...);
 *     Fonts.BOLD.draw(...);
 * }
 *
 * // or manual:
 * Batch b = Batch.begin();
 * ... draw freely ...
 * b.end();
 * }</pre>
 *
 * <h3>Rules</h3>
 * <ul>
 *   <li>Within one batch <b>text is always drawn on top of rects</b> (rects flush
 *       first). For UI that's the normal case.</li>
 *   <li>If you need rects above some text, call {@link #flushText()} to lay that
 *       text down first, then keep drawing.</li>
 *   <li>Put each scissored region in its own batch (begin → draw → end) so the
 *       clip is active when the batch flushes.</li>
 *   <li>Don't nest batches.</li>
 * </ul>
 */
public final class Batch implements AutoCloseable {

    private boolean active;

    private Batch() {
        FontRenderer f = font();
        if (f != null) f.setBatching(true);
        this.active = true;
    }

    /** Open a batch scope. Rects + text accumulate until {@link #end()}. */
    public static Batch begin() {
        return new Batch();
    }

    /** Flush accumulated rects (underneath) then text (on top). Idempotent. */
    public void end() {
        if (!active) return;
        active = false;
        Render2D.flushRects();
        FontRenderer f = font();
        if (f != null) {
            f.flushBatch();
            f.setBatching(false);
        }
    }

    @Override
    public void close() {
        end();
    }

    /* ============================ Granular API ============================ */

    /** Start deferring text (and rects keep accumulating). Pair with {@link #endText()}. */
    public static void beginText() {
        FontRenderer f = font();
        if (f != null) f.setBatching(true);
    }

    /** Flush rects then text and stop deferring. Pair with {@link #beginText()}. */
    public static void endText() {
        Render2D.flushRects();
        FontRenderer f = font();
        if (f != null) {
            f.flushBatch();
            f.setBatching(false);
        }
    }

    /** Flush only the accumulated text mid-batch (e.g. to draw rects above it). */
    public static void flushText() {
        FontRenderer f = font();
        if (f != null) f.flushBatch();
    }

    /** Flush only the accumulated rects mid-batch. */
    public static void flushRects() {
        Render2D.flushRects();
    }

    private static FontRenderer font() {
        Initialization init = Initialization.getInstance();
        if (init == null || init.getManager() == null) return null;
        var core = init.getManager().getRenderCore();
        return core == null ? null : core.getFontRenderer();
    }
}
