package rich.util.render.batch.impl;

import rich.Initialization;
import rich.util.render.font.FontRenderer;

/**
 * Object-style text batch, in the spirit of Rockstar's {@code FontBatching}.
 *
 * <p>Every {@code Font.draw(...)} between construction and {@link #draw()} is
 * accumulated and emitted as a single GPU pass instead of one pass per string
 * (which is what tanks FPS when there is a lot of text).
 *
 * <pre>{@code
 * FontBatch font = new FontBatch();
 * Fonts.BOLD.draw("Combat", x, y, 7, color);
 * Fonts.BOLD.draw("Movement", x, y2, 7, color);
 * font.draw();         // one pass for all the text above
 * }</pre>
 *
 * <p>Or with try-with-resources:
 * <pre>{@code
 * try (FontBatch font = new FontBatch()) {
 *     Fonts.BOLD.draw(...);
 * }
 * }</pre>
 *
 * <p>Mixing different fonts in one batch still works, but switching font atlas
 * forces an intermediate flush, so for best results group draws by font (exactly
 * like Rockstar passes a single {@code Fonts.X} into each {@code FontBatching}).
 */
public final class FontBatch implements AutoCloseable {

    private boolean drawn;

    public FontBatch() {
        FontRenderer f = font();
        if (f != null) f.setBatching(true);
    }

    public static FontBatch begin() {
        return new FontBatch();
    }

    /** Emit every glyph drawn since construction as one pass. Idempotent. */
    public void draw() {
        if (drawn) return;
        drawn = true;
        FontRenderer f = font();
        if (f != null) {
            f.flushBatch();
            f.setBatching(false);
        }
    }

    @Override
    public void close() {
        draw();
    }

    private static FontRenderer font() {
        Initialization init = Initialization.getInstance();
        if (init == null || init.getManager() == null) return null;
        var core = init.getManager().getRenderCore();
        return core == null ? null : core.getFontRenderer();
    }
}
