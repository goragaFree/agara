package rich.util.render.batch.impl;

import rich.Initialization;
import rich.util.render.pipeline.BlurPipeline;

/**
 * Object-style blur batch (Rockstar's {@code BlurBatching}).
 *
 * <p>Blur cannot be collapsed into a single GPU pass like rects/text: every blur
 * samples the live framebuffer, so each needs its own pass. The expensive part is
 * that each {@code Render2D.blur} call copies the <b>whole framebuffer</b> into a
 * scratch texture. This batch copies the framebuffer <b>once</b> and lets every
 * blur in the scope sample that single snapshot — so N blurs cost 1 framebuffer
 * copy instead of N.
 *
 * <pre>{@code
 * try (BlurBatch blurs = new BlurBatch()) {
 *     Render2D.blur(panel1X, panel1Y, w, h, radius, corner, tint);
 *     Render2D.blur(panel2X, panel2Y, w, h, radius, corner, tint);
 * }
 * }</pre>
 *
 * <p>Trade-off: blurs inside the batch all sample the framebuffer as it was at the
 * first blur, so they do <b>not</b> blur each other or anything drawn during the
 * batch. That's exactly what you want for separate glass panels over the same
 * background; avoid batching blurs that are meant to stack on top of one another.
 */
public final class BlurBatch implements AutoCloseable {

    private boolean drawn;

    public BlurBatch() {
        BlurPipeline p = blur();
        if (p != null) p.beginBatch();
    }

    public static BlurBatch begin() {
        return new BlurBatch();
    }

    /** Ends the shared-snapshot scope (next blur outside will re-copy). Idempotent. */
    public void draw() {
        if (drawn) return;
        drawn = true;
        BlurPipeline p = blur();
        if (p != null) p.endBatch();
    }

    @Override
    public void close() {
        draw();
    }

    private static BlurPipeline blur() {
        Initialization init = Initialization.getInstance();
        if (init == null || init.getManager() == null) return null;
        var core = init.getManager().getRenderCore();
        return core == null ? null : core.getBlurPipeline();
    }
}
