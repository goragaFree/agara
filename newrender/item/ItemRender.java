package rich.util.render.item;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.client.texture.Sprite;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix3x2fStack;
import rich.util.render.Render2D;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ItemRender {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Map<String, CachedSprite> SPRITE_CACHE = new ConcurrentHashMap<>();
    private static final Random RANDOM = Random.create();
    private static final int FORCED_GUI_SCALE = 2;

    private static int getCurrentGuiScale() {
        int scale = mc.options.getGuiScale().getValue();
        if (scale == 0) {
            scale = mc.getWindow().calculateScaleFactor(0, mc.forcesUnicodeFont());
        }
        return scale;
    }

    private static float getScaleCompensation() {
        return (float) FORCED_GUI_SCALE / (float) getCurrentGuiScale();
    }

    public static boolean isBlockItem(ItemStack stack) {
        return stack.getItem() instanceof BlockItem;
    }

    public static boolean isPotionItem(ItemStack stack) {
        return stack.getItem() == Items.POTION ||
                stack.getItem() == Items.SPLASH_POTION ||
                stack.getItem() == Items.LINGERING_POTION ||
                stack.getItem() == Items.TIPPED_ARROW;
    }

    public static boolean hasGlint(ItemStack stack) {
        return stack.hasGlint();
    }

    /** Dyed items (leather armor etc.) get their color from a tint layer the sprite path can't apply. */
    public static boolean isDyed(ItemStack stack) {
        return stack.contains(DataComponentTypes.DYED_COLOR);
    }

    public static boolean needsContextRender(ItemStack stack) {
        return isBlockItem(stack) || isPotionItem(stack) || hasGlint(stack) || isDyed(stack);
    }

    public static void drawItem(ItemStack stack, float x, float y, float scale, float alpha) {
        drawItem(stack, x, y, scale, alpha, 0xFFFFFFFF);
    }

    public static void drawItem(ItemStack stack, float x, float y, float scale, float alpha, int tintColor) {
        if (stack.isEmpty() || alpha <= 0.01f) return;

        if (needsContextRender(stack)) {
            return;
        }

        Sprite sprite = getSpriteForStack(stack);
        if (sprite != null) {
            int color = applyAlpha(tintColor, alpha);
            float size = 16 * scale;
            Render2D.drawSprite(sprite, x, y, size, size, color, true);
        }
    }

    public static void drawBlockItem(DrawContext context, ItemStack stack, float x, float y, float scale, float alpha) {
        drawItemWithContext(context, stack, x, y, scale, alpha);
    }

    /**
     * Отрисовка предмета через ванильный context.drawItem с ЧЕСТНЫМ fade.
     * <p>
     * Ванильный GUI-рендер предметов не умеет полупрозрачность, поэтому alpha
     * (0..1) реализуется как плавное сжатие предмета к центру его слота:
     * центр вычисляется от НОМИНАЛЬНОГО размера (16 * scale) и не двигается,
     * а масштаб умножается на fade. При alpha = 1 поведение прежнее (без
     * сжатия), при alpha -> 0 предмет плавно "растворяется" в точку — синхронно
     * с альфой панели, а не резким морганием в конце.
     * <p>
     * Значения alpha вне 0..1 клампятся (защита от вызовов со старой семантикой
     * 0..255).
     */
    public static void drawItemWithContext(DrawContext context, ItemStack stack, float x, float y, float scale, float alpha) {
        drawItemWithContext(context, stack, x, y, scale, alpha, false);
    }

    /**
     * То же + опциональный ВАНИЛЬНЫЙ оверлей стека (количество, полоска
     * прочности, кулдаун). Оверлей рисуется внутри той же матрицы и в том же
     * отложенном пайплайне, что и сам предмет — поэтому ложится ПОВЕРХ иконки
     * (текст клиента, нарисованный через Fonts, предметы наоборот накрывают)
     * и масштабируется/сжимается вместе с ней.
     */
    public static void drawItemWithContext(DrawContext context, ItemStack stack, float x, float y, float scale, float alpha, boolean withOverlay) {
        if (stack.isEmpty()) return;

        float fade = Math.max(0f, Math.min(1f, alpha));
        if (fade <= 0.01f) return;

        float compensation = getScaleCompensation();
        // fade сжимает предмет вокруг неподвижного центра слота
        float finalScale = scale * compensation * fade;

        float size = 16 * scale;
        float centerX = x + size / 2f;
        float centerY = y + size / 2f;

        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();

        matrices.translate(centerX, centerY);
        matrices.scale(finalScale, finalScale);
        matrices.translate(-8, -8);

        context.drawItem(stack, 0, 0);
        if (withOverlay) {
            // пустой override гасит ванильную цифру количества: остаются полоска
            // прочности и кулдаун; каунт элемент рисует сам клиентским шрифтом
            // через LateText (поверх композита предметов)
            context.drawStackOverlay(mc.textRenderer, stack, 0, 0, "");
        }

        matrices.popMatrix();
    }

    public static void drawItemCentered(ItemStack stack, float centerX, float centerY, float scale, float alpha) {
        float size = 16 * scale;
        float x = centerX - size / 2f;
        float y = centerY - size / 2f;
        drawItem(stack, x, y, scale, alpha);
    }

    public static void drawItemCenteredWithContext(DrawContext context, ItemStack stack, float centerX, float centerY, float scale, float alpha) {
        float size = 16 * scale;
        float x = centerX - size / 2f;
        float y = centerY - size / 2f;
        drawItemWithContext(context, stack, x, y, scale, alpha);
    }

    private static Sprite getSpriteForStack(ItemStack stack) {
        String cacheKey = getCacheKey(stack);

        CachedSprite cached = SPRITE_CACHE.get(cacheKey);
        if (cached != null) {
            return cached.sprite;
        }

        try {
            ItemRenderState state = new ItemRenderState();
            mc.getItemModelManager().clearAndUpdate(state, stack, ItemDisplayContext.GUI, mc.world, null, 0);

            Sprite sprite = state.getParticleSprite(RANDOM);
            if (sprite != null) {
                SPRITE_CACHE.put(cacheKey, new CachedSprite(sprite));
                return sprite;
            }
        } catch (Exception ignored) {}

        return null;
    }

    private static String getCacheKey(ItemStack stack) {
        return stack.getItem().toString() + "_" + stack.getComponents().hashCode();
    }

    private static int applyAlpha(int color, float alpha) {
        int a = (int) (((color >> 24) & 0xFF) * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    public static void clearCache() {
        SPRITE_CACHE.clear();
    }

    private record CachedSprite(Sprite sprite) {}
}