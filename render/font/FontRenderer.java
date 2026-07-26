package rich.util.render.font;

import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rich.util.render.Render2D;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FontRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger("rich/FontRenderer");

    /** Диагностика: не спамим лог — одна и та же причина не чаще раза в секунду. */
    private static final Map<String, Long> LOG_THROTTLE = new ConcurrentHashMap<>();

    static boolean shouldLog(String key) {
        long now = System.currentTimeMillis();
        Long last = LOG_THROTTLE.get(key);
        if (last != null && now - last < 1000L) return false;
        LOG_THROTTLE.put(key, now);
        return true;
    }

    private final FontPipeline pipeline;
    private final Map<String, FontAtlas> fonts;
    private boolean initialized = false;
    private boolean batching = false;

    /** Enter/exit a batched text pass. While batching, glyphs accumulate and the
     *  rect batch is NOT flushed per call, so many draws collapse into one GPU pass. */
    public void setBatching(boolean batching) {
        this.batching = batching;
        pipeline.setDeferFlush(batching);
    }

    /** Flush any accumulated batched text (call at the end of a batched pass). */
    public void flushBatch() {
        pipeline.flush();
    }

    /**
     * Called before every text draw. While batching, glyphs of every font accumulate
     * as ordered runs inside {@link FontPipeline} (a font change just opens a new run),
     * so no per-call flush is needed and nothing interleaves with the rect batch.
     * Outside a batch, commit any pending rects first so this text lands on top of them.
     */
    private void beforeDraw(String fontName) {
        if (!batching) {
            Render2D.flushRects();
        }
    }

    public FontRenderer() {
        this.pipeline = new FontPipeline();
        this.fonts = new HashMap<>();
    }

    public void loadFont(String name, String path) {
        Identifier jsonId = Identifier.of("rich", "fonts/" + path + ".json");
        Identifier textureId = Identifier.of("rich", "fonts/" + path + ".png");
        FontAtlas atlas = new FontAtlas(jsonId, textureId);
        fonts.put(name, atlas);
        LOGGER.info("Registered font: {} -> {}", name, path);
    }

    public void loadAllFonts(Map<String, String> registry) {
        for (Map.Entry<String, String> entry : registry.entrySet()) {
            loadFont(entry.getKey(), entry.getValue());
        }
    }

    public void initialize() {
        if (initialized) return;
        LOGGER.info("Initializing {} fonts...", fonts.size());
        long startTime = System.currentTimeMillis();
        for (Map.Entry<String, FontAtlas> entry : fonts.entrySet()) {
            entry.getValue().forceLoad();
        }
        initialized = true;
        LOGGER.info("All fonts initialized in {}ms", System.currentTimeMillis() - startTime);
    }

    public boolean isInitialized() {
        return initialized;
    }

    public FontAtlas getFont(String name) {
        return fonts.get(name);
    }

    public void drawText(String fontName, String text, float x, float y, float size, int color) {
        FontAtlas atlas = fonts.get(fontName);
        if (atlas == null) {
            if (shouldLog("missing:" + fontName)) {
                LOGGER.warn("drawText: font '{}' is not registered (registered: {})", fontName, fonts.keySet());
            }
            return;
        }
        beforeDraw(fontName);
        pipeline.drawText(atlas, text, x, y, size, color, 0, 0, 0);
    }

    public void drawText(String fontName, String text, float x, float y, float size, int color, float rotation) {
        FontAtlas atlas = fonts.get(fontName);
        if (atlas == null) return;
        beforeDraw(fontName);
        pipeline.drawText(atlas, text, x, y, size, color, 0, 0, rotation);
    }

    public void drawTextWithOutline(String fontName, String text, float x, float y, float size,
                                    int color, float outlineWidth, int outlineColor) {
        FontAtlas atlas = fonts.get(fontName);
        if (atlas == null) return;
        beforeDraw(fontName);
        pipeline.drawText(atlas, text, x, y, size, color, outlineWidth, outlineColor, 0);
    }

    public void drawTextWithOutline(String fontName, String text, float x, float y, float size,
                                    int color, float outlineWidth, int outlineColor, float rotation) {
        FontAtlas atlas = fonts.get(fontName);
        if (atlas == null) return;
        beforeDraw(fontName);
        pipeline.drawText(atlas, text, x, y, size, color, outlineWidth, outlineColor, rotation);
    }

    public void drawCenteredText(String fontName, String text, float x, float y, float size, int color) {
        FontAtlas atlas = fonts.get(fontName);
        if (atlas == null) return;
        float width = pipeline.getTextWidth(atlas, text, size);
        beforeDraw(fontName);
        pipeline.drawText(atlas, text, x - width / 2, y, size, color, 0, 0, 0);
    }

    public void drawCenteredText(String fontName, String text, float x, float y, float size, int color, float rotation) {
        FontAtlas atlas = fonts.get(fontName);
        if (atlas == null) return;
        float width = pipeline.getTextWidth(atlas, text, size);
        float height = pipeline.getTextHeight(atlas, text, size);
        float centerX = x;
        float centerY = y + height / 2;
        beforeDraw(fontName);
        pipeline.drawTextRotatedAroundPoint(atlas, text, x - width / 2, y, size, color, 0, 0, rotation, centerX, centerY);
    }

    public float getTextWidth(String fontName, String text, float size) {
        FontAtlas atlas = fonts.get(fontName);
        if (atlas == null) return 0;
        return pipeline.getTextWidth(atlas, text, size);
    }

    public float getLineHeight(String fontName, float size) {
        FontAtlas atlas = fonts.get(fontName);
        if (atlas == null) return size;
        return (atlas.getLineHeight() / atlas.getFontSize()) * size;
    }

    public void close() {
        pipeline.close();
        fonts.clear();
        initialized = false;
    }
}