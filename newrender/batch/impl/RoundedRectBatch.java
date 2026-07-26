package rich.util.render.batch.impl;

import rich.util.render.Render2D;

/**
 * Object-style rounded-rectangle batch (Rockstar's {@code RoundedRectBatching}).
 *
 * <p>In this engine sharp and rounded rectangles go through the very same
 * {@code RectPipeline} — the corner radius is just a parameter — so this is
 * functionally identical to {@link RectBatch} and the two can be mixed freely.
 * It exists as a separate named handle for readability / parity with Rockstar.
 *
 * <pre>{@code
 * RoundedRectBatch rects = new RoundedRectBatch();
 * Render2D.rect(x, y, w, h, color, 8f);
 * Render2D.gradientRect(x, y, w, h, colors, 8f);
 * rects.draw();        // one pass for everything above
 * }</pre>
 *
 * <p>Note: {@code Render2D.outline}/{@code texture}/{@code Font.draw} flush rects
 * internally, so keep those out of the batch (use {@link FontBatch}/raw calls).
 */
public final class RoundedRectBatch implements AutoCloseable {

    private boolean drawn;

    public RoundedRectBatch() {
        Render2D.flushRects();
    }

    public static RoundedRectBatch begin() {
        return new RoundedRectBatch();
    }

    /** Emit every (rounded) rect drawn since construction as one pass. Idempotent. */
    public void draw() {
        if (drawn) return;
        drawn = true;
        Render2D.flushRects();
    }

    @Override
    public void close() {
        draw();
    }
}
