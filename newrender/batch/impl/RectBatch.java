package rich.util.render.batch.impl;

import rich.util.render.Render2D;

/**
 * Object-style rectangle batch, in the spirit of Rockstar's {@code RectBatching}.
 *
 * <p>Every {@link Render2D} rect/gradient call between construction and
 * {@link #draw()} is accumulated into one buffer and emitted as a single GPU
 * pass. (The underlying {@code RectPipeline} already accumulates; this object
 * just scopes the flush so you get an explicit batch handle like Rockstar.)
 *
 * <pre>{@code
 * RectBatch rects = new RectBatch();
 * Render2D.rect(x, y, w, h, color, radius);
 * Render2D.gradientRect(...);
 * rects.draw();        // one pass for everything above
 * }</pre>
 *
 * <p>Also works with try-with-resources:
 * <pre>{@code
 * try (RectBatch rects = new RectBatch()) {
 *     Render2D.rect(...);
 * }
 * }</pre>
 *
 * <p>Note: {@code Render2D.outline}/{@code texture}/{@code Font.draw} flush rects
 * internally, so keep those out of a rect batch (use {@link FontBatch} for text).
 */
public final class RectBatch implements AutoCloseable {

    private boolean drawn;

    /** Opens a rect batch. Any pending rects are flushed first so this starts clean. */
    public RectBatch() {
        Render2D.flushRects();
    }

    public static RectBatch begin() {
        return new RectBatch();
    }

    /** Emit every rect drawn since construction as one pass. Idempotent. */
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
