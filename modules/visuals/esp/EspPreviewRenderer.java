package moscow.rockstar.systems.modules.modules.visuals.esp;

import java.util.ArrayList;
import java.util.List;
import moscow.rockstar.framework.base.UIContext;
import moscow.rockstar.framework.msdf.Font;
import moscow.rockstar.framework.msdf.Fonts;
import moscow.rockstar.framework.objects.BorderRadius;
import moscow.rockstar.utility.colors.ColorRGBA;
import moscow.rockstar.utility.colors.Colors;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public final class EspPreviewRenderer {
   public static final float ORIGINAL_PLAYER_PREVIEW_WIDTH = 165.0F;

   private EspPreviewRenderer() {
   }

   public static void drawPlayer(
      UIContext context,
      PlayerEntity player,
      float x,
      float y,
      float width,
      float height,
      ColorRGBA glowColor,
      ColorRGBA boxColor,
      ColorRGBA arrowColor,
      Identifier arrowTexture,
      boolean glowEnabled,
      boolean boxEnabled,
      boolean nametagEnabled,
      boolean arrowsEnabled
   ) {
      context.drawSquircle(x, y, width, height, 7.0F, BorderRadius.all(6.0F), new ColorRGBA(7.0F, 12.0F, 11.0F, 92.0F));
      context.drawSquircle(x + 1.0F, y + 1.0F, width - 2.0F, height - 2.0F, 7.0F, BorderRadius.all(5.0F), Colors.getAdditionalColor().withAlpha(28.0F));
      float centerX = x + width / 2.0F;
      float modelTop = y + 5.0F;
      float modelBottom = y + height - 6.0F;
      float modelHeight = modelBottom - modelTop;
      float bodyWidth = Math.min(width * 0.29F, 42.0F);
      float bodyTop = modelTop + Math.max(12.0F, modelHeight * 0.13F);
      float bodyHeight = modelHeight - Math.max(23.0F, modelHeight * 0.22F);
      if (glowEnabled) {
         context.drawShadow(centerX - bodyWidth / 2.0F, bodyTop + 2.0F, bodyWidth, bodyHeight, 20.0F, BorderRadius.all(6.0F), glowColor.withAlpha(112.0F));
      }

      context.drawSquircle(centerX - 35.0F, y + height - 13.0F, 70.0F, 7.0F, 7.0F, BorderRadius.all(4.0F), ColorRGBA.BLACK.withAlpha(68.0F));
      int entitySize = Math.round(MathHelper.clamp(height * 0.76F, 42.0F, 74.0F));
      InventoryScreen.drawEntity(
         context, (int)(x + 6.0F), (int)modelTop, (int)(x + width - 6.0F), (int)modelBottom, entitySize, 0.0F, context.getMouseX(), context.getMouseY(), player
      );
      if (boxEnabled) {
         context.drawRoundedBorder(centerX - bodyWidth / 2.0F, bodyTop, bodyWidth, bodyHeight, 0.8F, BorderRadius.all(3.0F), boxColor.withAlpha(205.0F));
      }

      if (nametagEnabled) {
         Font font = Fonts.MEDIUM.getFont(7.0F);
         String name = player.getName().getString();
         float tagWidth = Math.min(width - 18.0F, font.width(name) + 12.0F);
         context.drawSquircle(centerX - tagWidth / 2.0F, y + 6.0F, tagWidth, 13.0F, 7.0F, BorderRadius.all(4.0F), ColorRGBA.BLACK.withAlpha(132.0F));
         context.drawText(font, name, centerX - font.width(name) / 2.0F, y + 9.5F, Colors.getTextColor());
      }

      drawEquipment(context, player, centerX, y + height - 20.0F);
      if (arrowsEnabled) {
         context.drawTexture(arrowTexture, x + width - 20.0F, y + height - 20.0F, 12.0F, 12.0F, arrowColor);
      }
   }

   private static void drawEquipment(UIContext context, PlayerEntity player, float centerX, float y) {
      List<ItemStack> stacks = new ArrayList<>();
      stacks.add((ItemStack)player.getInventory().armor.get(3));
      stacks.add((ItemStack)player.getInventory().armor.get(2));
      stacks.add((ItemStack)player.getInventory().armor.get(1));
      stacks.add((ItemStack)player.getInventory().armor.get(0));
      stacks.add(player.getMainHandStack());
      stacks.add(player.getOffHandStack());
      stacks.removeIf(ItemStack::isEmpty);
      if (!stacks.isEmpty()) {
         int visibleStacks = Math.min(stacks.size(), 6);
         float totalWidth = visibleStacks * 13.0F - 1.0F;
         float startX = centerX - totalWidth / 2.0F;

         for (int i = 0; i < visibleStacks; i++) {
            context.drawItem(stacks.get(i), startX + i * 13.0F, y, 0.62F);
         }
      }
   }
}
