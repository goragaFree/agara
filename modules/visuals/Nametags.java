package moscow.rockstar.systems.modules.modules.visuals;

import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import lombok.Generated;
import moscow.rockstar.Rockstar;
import moscow.rockstar.framework.base.UIContext;
import moscow.rockstar.framework.msdf.Font;
import moscow.rockstar.framework.msdf.Fonts;
import moscow.rockstar.framework.objects.MouseButton;
import moscow.rockstar.systems.event.EventListener;
import moscow.rockstar.systems.event.impl.render.ChatRenderEvent;
import moscow.rockstar.systems.event.impl.render.PreHudRenderEvent;
import moscow.rockstar.systems.event.impl.window.ChatClickEvent;
import moscow.rockstar.systems.friends.FriendManager;
import moscow.rockstar.systems.localization.Localizator;
import moscow.rockstar.systems.modules.api.ModuleCategory;
import moscow.rockstar.systems.modules.api.ModuleInfo;
import moscow.rockstar.systems.modules.impl.BaseModule;
import moscow.rockstar.systems.modules.modules.other.NameProtect;
import moscow.rockstar.systems.modules.modules.visuals.esp.EspAnimationState;
import moscow.rockstar.systems.setting.settings.BooleanSetting;
import moscow.rockstar.systems.setting.settings.ModeSetting;
import moscow.rockstar.systems.target.TargetManager;
import moscow.rockstar.ui.components.popup.Popup;
import moscow.rockstar.utility.colors.ColorRGBA;
import moscow.rockstar.utility.game.EntityUtility;
import moscow.rockstar.utility.game.ItemUtility;
import moscow.rockstar.utility.game.TextUtility;
import moscow.rockstar.utility.gui.GuiUtility;
import moscow.rockstar.utility.inventory.EnchantmentUtility;
import moscow.rockstar.utility.render.Utils;
import moscow.rockstar.utility.render.batching.Batching;
import moscow.rockstar.utility.render.batching.impl.FontBatching;
import moscow.rockstar.utility.render.batching.impl.RectBatching;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

@ModuleInfo(name = "Name Tags", category = ModuleCategory.VISUALS, enabledByDefault = true, desc = "Теги, отображающие информацию о сущностях")
public class Nametags extends BaseModule {
   private static final ColorRGBA FRIEND_COLOR = new ColorRGBA(52.0F, 199.0F, 89.0F);
   private static final ColorRGBA HEALTH_GOOD = new ColorRGBA(94.0F, 252.0F, 132.0F);
   private static final ColorRGBA HEALTH_MEDIUM = new ColorRGBA(255.0F, 214.0F, 92.0F);
   private static final ColorRGBA HEALTH_LOW = new ColorRGBA(255.0F, 85.0F, 85.0F);
   private static final Map<RegistryKey<Enchantment>, String> ENCHANT_NAMES = Map.of(
      Enchantments.BLAST_PROTECTION,
      "B",
      Enchantments.PROTECTION,
      "P",
      Enchantments.SHARPNESS,
      "S",
      Enchantments.EFFICIENCY,
      "E",
      Enchantments.UNBREAKING,
      "U",
      Enchantments.POWER,
      "PO",
      Enchantments.THORNS,
      "T"
   );
   private static final Map<String, String> PRIVILEGE_MAPPING = new HashMap<>();
   private static final Map<String, ColorRGBA> PRIVILEGE_COLORS = new HashMap<>();
   private final List<Entity> entityList = new ArrayList<>();
   private final Map<Integer, EspAnimationState> playerAnimations = new HashMap<>();
   private final ModeSetting mode = new ModeSetting(this, "modules.settings.name_tags.mode");
   private final ModeSetting.Value clientMode = new ModeSetting.Value(this.mode, "modules.settings.name_tags.mode.client").select();
   private final ModeSetting.Value minecraftMode = new ModeSetting.Value(this.mode, "modules.settings.name_tags.mode.minecraft");
   private final BooleanSetting armor = new BooleanSetting(this, "modules.settings.name_tags.armor");
   private final BooleanSetting enchants = new BooleanSetting(this, "modules.settings.name_tags.enchants", () -> !this.armor.isEnabled()).enable();
   private final BooleanSetting offFriends = new BooleanSetting(this, "modules.settings.name_tags.offFriends");
   private final BooleanSetting items = new BooleanSetting(this, "modules.settings.name_tags.items");
   private final BooleanSetting playerBackground = new BooleanSetting(this, "modules.settings.name_tags.player_background").enable();
   private final BooleanSetting handItems = new BooleanSetting(this, "modules.settings.name_tags.hand_items").enable();
   private final BooleanSetting backItems = new BooleanSetting(this, "modules.settings.name_tags.background", () -> !this.items.isEnabled());
   private final EventListener<PreHudRenderEvent> onHudRenderEvent = event -> {
      if (mc.player != null && mc.world != null) {
         MatrixStack matrices = event.getContext().getMatrices();
         float tickDelta = event.getTickDelta();
         this.entityList.clear();

         for (Entity entity : mc.world.getEntities()) {
            if (entity != mc.player
               && (entity.getType() == EntityType.PLAYER || this.items.isEnabled() && entity.getType() == EntityType.ITEM)
               && !(
                  entity instanceof PlayerEntity player
                     && Rockstar.getInstance().getFriendManager().isFriend(player.getName().getString())
                     && this.offFriends.isEnabled()
               )) {
               this.entityList.add(entity);
            }
         }

         List<List<ItemEntity>> itemGroups = new LinkedList<>();
         Set<ItemEntity> processedItems = new HashSet<>();

         for (Entity e : this.entityList) {
            if (e instanceof ItemEntity item && !processedItems.contains(item)) {
               List<ItemEntity> group = new LinkedList<>();

               for (Entity other : this.entityList) {
                  if (other instanceof ItemEntity otherItem && !processedItems.contains(otherItem) && item.squaredDistanceTo(otherItem) < 1.0) {
                     group.add(otherItem);
                     processedItems.add(otherItem);
                  }
               }

               itemGroups.add(group);
            }
         }

         Batching rect = new RectBatching(VertexFormats.POSITION_COLOR, event.getContext().getMatrices());
         this.drawBack(event, itemGroups, tickDelta);
         rect.draw();
         Set<Integer> visiblePlayerIds = new HashSet<>();

         for (Entity entityx : this.entityList) {
            Vec3d pos = Utils.getInterpolatedPos(entityx, tickDelta).add(0.0, entityx.getBoundingBox().getLengthY() + 0.5, 0.0);
            Vec2f screenPos = Utils.worldToScreen(pos);
            if (screenPos != null && entityx.getType() == EntityType.PLAYER && this.armor.isEnabled()) {
               this.renderArmorPlayer(event, matrices, (PlayerEntity)entityx, screenPos);
            }
         }

         for (Entity entityxx : this.entityList) {
            Vec3d pos = Utils.getInterpolatedPos(entityxx, tickDelta).add(0.0, entityxx.getBoundingBox().getLengthY() + 0.5, 0.0);
            Vec2f screenPos = Utils.worldToScreen(pos);
            if (screenPos != null && this.items.isEnabled() && entityxx.getType() == EntityType.ITEM) {
               this.renderShulkerDisplay(event, matrices, (ItemEntity)entityxx, screenPos);
            }
         }

         DiffuseLighting.disableGuiDepthLighting();
         event.getContext().draw();
         FontBatching fontBatching = new FontBatching(VertexFormats.POSITION_TEXTURE_COLOR, Fonts.MEDIUM);

         for (Entity entityxxx : this.entityList) {
            Vec3d pos = Utils.getInterpolatedPos(entityxxx, tickDelta).add(0.0, entityxxx.getBoundingBox().getLengthY() + 0.5, 0.0);
            Vec2f screenPos = Utils.worldToScreen(pos);
            if (screenPos != null && entityxxx.getType() == EntityType.PLAYER) {
               visiblePlayerIds.add(entityxxx.getId());
               this.renderNametagPlayer(event, matrices, entityxxx, screenPos);
            }
         }

         for (List<ItemEntity> group : itemGroups) {
            ItemEntity first = (ItemEntity)group.getFirst();
            Vec3d pos = Utils.getInterpolatedPos(first, tickDelta).add(0.0, first.getBoundingBox().getLengthY() + 0.5, 0.0);
            Vec2f screenPos = Utils.worldToScreen(pos);
            if (screenPos != null && this.items.isEnabled()) {
               if (group.size() > 1) {
                  this.renderItemsText(event, matrices, group, screenPos);
               } else {
                  this.renderItemText(event, matrices, first, screenPos);
               }
            }
         }

         for (Entity entityxxxx : this.entityList) {
            Vec3d pos = Utils.getInterpolatedPos(entityxxxx, tickDelta).add(0.0, entityxxxx.getBoundingBox().getLengthY() + 0.5, 0.0);
            Vec2f screenPos = Utils.worldToScreen(pos);
            if (screenPos != null && this.items.isEnabled() && entityxxxx.getType() == EntityType.ITEM) {
               this.renderShulkerText(event, matrices, (ItemEntity)entityxxxx, screenPos);
            }
         }

         fontBatching.draw();
         this.fadeMissingPlayerAnimations(visiblePlayerIds);
         if (!(mc.currentScreen instanceof ChatScreen)) {
            this.active = null;
         }
      } else {
         this.entityList.clear();
         this.playerAnimations.clear();
         this.active = null;
      }
   };
   private Popup active;
   private final EventListener<ChatRenderEvent> onRender = event -> {
      UIContext context = UIContext.of(
         event.getContext(),
         mc.currentScreen == null ? -1 : (int)GuiUtility.getMouse().getX(),
         mc.currentScreen == null ? -1 : (int)GuiUtility.getMouse().getY(),
         MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false)
      );
      if (this.active != null) {
         this.active.render(context);
      }
   };
   private final EventListener<ChatClickEvent> onClick = event -> {
      if (this.active != null) {
         this.active.onMouseClicked(event.getX(), event.getY(), MouseButton.fromButtonIndex(event.getButton()));
         if (this.active.isHovered(event.getX(), event.getY())) {
            return;
         }

         this.active.setShowing(false);
      }

      for (Entity entity : this.entityList) {
         Vec3d pos = Utils.getInterpolatedPos(entity, 1.0F).add(0.0, entity.getBoundingBox().getLengthY() + 0.5, 0.0);
         Vec2f screenPos = Utils.worldToScreen(pos);
         if (screenPos != null && entity.getType() == EntityType.PLAYER) {
            this.handleClick(event, entity, screenPos);
         }
      }
   };

   private void drawBack(PreHudRenderEvent event, List<List<ItemEntity>> itemGroups, float tickDelta) {
      MatrixStack matrices = event.getContext().getMatrices();

      for (Entity entity : this.entityList) {
         Vec3d pos = Utils.getInterpolatedPos(entity, tickDelta).add(0.0, entity.getBoundingBox().getLengthY() + 0.5, 0.0);
         Vec2f screenPos = Utils.worldToScreen(pos);
         if (screenPos != null && entity.getType() == EntityType.PLAYER) {
            this.renderBack(event, matrices, entity, screenPos);
         }
      }

      for (List<ItemEntity> group : itemGroups) {
         ItemEntity first = (ItemEntity)group.getFirst();
         Vec3d pos = Utils.getInterpolatedPos(first, tickDelta).add(0.0, first.getBoundingBox().getLengthY() + 0.5, 0.0);
         Vec2f screenPos = Utils.worldToScreen(pos);
         if (screenPos != null && this.backItems.isEnabled() && this.items.isEnabled()) {
            if (group.size() > 1) {
               this.renderItemsBack(event, matrices, group, screenPos);
            } else {
               this.renderItemBack(event, matrices, first, screenPos);
            }
         }
      }

      for (Entity entityx : this.entityList) {
         Vec3d pos = Utils.getInterpolatedPos(entityx, tickDelta).add(0.0, entityx.getBoundingBox().getLengthY() + 0.5, 0.0);
         Vec2f screenPos = Utils.worldToScreen(pos);
         if (screenPos != null && this.items.isEnabled() && this.backItems.isEnabled() && entityx.getType() == EntityType.ITEM) {
            this.renderShulkerBack(event, matrices, (ItemEntity)entityx, screenPos);
         }
      }
   }

   private void renderItemBack(PreHudRenderEvent event, MatrixStack matrices, ItemEntity entity, Vec2f screenPos) {
      float distance = entity.distanceTo(mc.player);
      float scale = MathHelper.clamp(1.0F - distance / 20.0F, 0.5F, 1.0F);
      matrices.push();
      matrices.translate(screenPos.x, screenPos.y, 0.0F);
      matrices.scale(scale, scale, 1.0F);
      String text = entity.getStack().getName().getString() + " " + entity.getStack().getCount() + "x";
      int textWidth = (int)Fonts.MEDIUM.getFont(11.0F).width(text);
      int x = -textWidth / 2;
      int y = 5;
      event.getContext().drawRect(x - 3, y - 3, textWidth + 6, Fonts.MEDIUM.getFont(11.0F).height() + 6.0F, new ColorRGBA(0.0F, 0.0F, 0.0F, 100.0F));
      matrices.pop();
   }

   private void renderItemText(PreHudRenderEvent event, MatrixStack matrices, ItemEntity entity, Vec2f screenPos) {
      float distance = entity.distanceTo(mc.player);
      float scale = MathHelper.clamp(1.0F - distance / 20.0F, 0.5F, 1.0F);
      matrices.push();
      matrices.translate(screenPos.x, screenPos.y, 0.0F);
      matrices.scale(scale, scale, 1.0F);
      Text text = entity.getStack().getName().copy().append(" " + entity.getStack().getCount() + "x");
      int textWidth = (int)Fonts.MEDIUM.getFont(11.0F).width(text);
      int x = -textWidth / 2;
      int y = 5;
      event.getContext().drawText(Fonts.MEDIUM.getFont(11.0F), text, x, y);
      matrices.pop();
   }

   private void renderItemsBack(PreHudRenderEvent event, MatrixStack matrices, List<ItemEntity> items, Vec2f screenPos) {
      if (!items.isEmpty()) {
         float distance = ((ItemEntity)items.getFirst()).distanceTo(mc.player);
         float scale = MathHelper.clamp(1.0F - distance / 20.0F, 0.5F, 1.0F);
         matrices.push();
         matrices.translate(screenPos.x, screenPos.y, 0.0F);
         matrices.scale(scale, scale, 1.0F);
         int maxWidth = 0;
         int textHeight = (int)Fonts.MEDIUM.getFont(11.0F).height();

         for (ItemEntity item : items) {
            String text = item.getStack().getName().getString() + " " + item.getStack().getCount() + "x";
            int w = (int)Fonts.MEDIUM.getFont(11.0F).width(text);
            if (w > maxWidth) {
               maxWidth = w;
            }
         }

         int boxWidth = maxWidth + 6;
         int boxHeight = items.size() * textHeight + (items.size() - 1) * 2 + 6;
         event.getContext().drawRect(-maxWidth / 2.0F - 3.0F, 2.0F, boxWidth, boxHeight, new ColorRGBA(0.0F, 0.0F, 0.0F, 100.0F));
         matrices.pop();
      }
   }

   private void renderItemsText(PreHudRenderEvent event, MatrixStack matrices, List<ItemEntity> items, Vec2f screenPos) {
      if (!items.isEmpty()) {
         float distance = ((ItemEntity)items.getFirst()).distanceTo(mc.player);
         float scale = MathHelper.clamp(1.0F - distance / 20.0F, 0.5F, 1.0F);
         matrices.push();
         matrices.translate(screenPos.x, screenPos.y, 0.0F);
         matrices.scale(scale, scale, 1.0F);
         int textHeight = (int)Fonts.MEDIUM.getFont(11.0F).height();
         int maxWidth = 0;
         List<Text> lines = new LinkedList<>();

         for (ItemEntity item : items) {
            Text text = item.getStack().getName().copy().append(" " + item.getStack().getCount() + "x");
            lines.add(text);
            int w = (int)Fonts.MEDIUM.getFont(11.0F).width(text);
            if (w > maxWidth) {
               maxWidth = w;
            }
         }

         int startX = -maxWidth / 2;

         for (int i = 0; i < lines.size(); i++) {
            Text line = lines.get(i);
            int lineWidth = (int)Fonts.MEDIUM.getFont(11.0F).width(line);
            int x = startX + (maxWidth - lineWidth) / 2;
            int y = 5 + i * (textHeight + 2);
            event.getContext().drawText(Fonts.MEDIUM.getFont(11.0F), Text.of(line), x, y);
         }

         matrices.pop();
      }
   }

   private void renderBack(PreHudRenderEvent event, MatrixStack matrices, Entity entity, Vec2f screenPos) {
   }

   private void renderNametagPlayer(PreHudRenderEvent event, MatrixStack matrices, Entity entity, Vec2f screenPos) {
      if (entity instanceof PlayerEntity player) {
         EspAnimationState animation = this.playerAnimations.computeIfAbsent(player.getId(), id -> new EspAnimationState());
         float visibility = animation.updateVisible(true);
         animation.updateScreen(screenPos.x, screenPos.y);
         float scale = this.tagScale(player) * animation.popScale(0.72F, 1.0F);
         matrices.push();
         matrices.translate(animation.screenX(), animation.screenY() - (1.0F - visibility) * 6.0F, 0.0F);
         matrices.scale(scale, scale, 1.0F);
         if (this.minecraftMode.isSelected()) {
            this.renderMinecraftPlayerTag(event, player, visibility);
         } else {
            this.renderClientPlayerTag(event, matrices, player, visibility);
         }

         matrices.pop();
      }
   }

   private void renderClientPlayerTag(PreHudRenderEvent event, MatrixStack matrices, PlayerEntity player, float visibility) {
      Font font = Fonts.MEDIUM.getFont(11.0F);
      String privilege = this.getPlayerPrivilege(player);
      String privilegeText = PRIVILEGE_MAPPING.getOrDefault(privilege.toLowerCase(), "");
      ColorRGBA privilegeColor = PRIVILEGE_COLORS.getOrDefault(privilege.toLowerCase(), ColorRGBA.WHITE);
      String playerName = this.protectedName(player);
      int health = Math.round(EntityUtility.getHealth(player));
      int absorption = Math.round(player.getAbsorptionAmount());
      String healthText = "♥ " + (health == 1000 ? "?" : health);
      String absorptionText = absorption > 0 ? " + " + absorption : "";
      float padding = 3.0F;
      float spacing = 2.0F;
      float privilegeWidth = privilegeText.isEmpty() ? 0.0F : font.width(privilegeText);
      float nameWidth = font.width(playerName);
      float healthWidth = font.width(healthText);
      float absorptionWidth = absorptionText.isEmpty() ? 0.0F : font.width(absorptionText);
      float totalWidth = privilegeWidth + (privilegeText.isEmpty() ? 0.0F : spacing) + nameWidth + spacing + healthWidth + absorptionWidth + padding * 2.0F;
      float totalHeight = font.height() + padding * 2.0F;
      float tagX = -totalWidth / 2.0F;
      float tagY = 5.0F;
      if (this.playerBackground.isEnabled()) {
         ColorRGBA background = this.isFriend(player) ? FRIEND_COLOR.withAlpha(105.0F * visibility) : ColorRGBA.BLACK.withAlpha(125.0F * visibility);
         event.getContext().drawRect(tagX, tagY, totalWidth, totalHeight, background);
      }

      float currentX = tagX + padding;
      float textY = tagY + padding;
      if (!privilegeText.isEmpty()) {
         event.getContext().drawText(font, privilegeText, currentX, textY, privilegeColor.withAlpha(255.0F * visibility));
         currentX += privilegeWidth + spacing;
      }

      ColorRGBA nameColor = this.isFriend(player) ? new ColorRGBA(255.0F, 230.0F, 90.0F, 255.0F * visibility) : ColorRGBA.WHITE.withAlpha(255.0F * visibility);
      event.getContext().drawText(font, playerName, currentX, textY, nameColor);
      currentX += nameWidth + spacing;
      event.getContext().drawText(font, healthText, currentX, textY, this.healthColor(health).withAlpha(255.0F * visibility));
      currentX += healthWidth;
      if (!absorptionText.isEmpty()) {
         event.getContext().drawText(font, absorptionText, currentX, textY, new ColorRGBA(255.0F, 215.0F, 0.0F, 255.0F * visibility));
      }

      if (this.handItems.isEnabled()) {
         this.renderHandItemLabels(event, matrices, player, tagY + totalHeight + 3.0F, visibility);
      }
   }

   private void renderMinecraftPlayerTag(PreHudRenderEvent event, PlayerEntity player, float visibility) {
      Font font = Fonts.MEDIUM.getFont(11.0F);
      Text displayName = displayName(player);
      float textWidth = font.width(displayName);
      float textHeight = font.height();
      float x = -textWidth / 2.0F;
      float y = 5.0F;
      if (this.playerBackground.isEnabled()) {
         event.getContext().drawRect(x - 4.0F, y - 3.0F, textWidth + 8.0F, textHeight + 6.0F, ColorRGBA.BLACK.withAlpha(115.0F * visibility));
      }

      event.getContext().drawText(font, displayName, x, y);
   }

   private void renderHandItemLabels(PreHudRenderEvent event, MatrixStack matrices, PlayerEntity player, float startY, float visibility) {
      float y = startY;
      y = this.renderHandItemLabel(event, matrices, player.getMainHandStack(), y, visibility);
      this.renderHandItemLabel(event, matrices, player.getOffHandStack(), y, visibility);
   }

   private float renderHandItemLabel(PreHudRenderEvent event, MatrixStack matrices, ItemStack stack, float y, float visibility) {
      if (stack.isEmpty()) {
         return y;
      }

      Font font = Fonts.MEDIUM.getFont(9.0F);
      String name = this.cleanItemName(stack);
      float padding = 3.0F;
      float width = font.width(name) + padding * 2.0F;
      float height = font.height() + padding * 2.0F;
      float x = -width / 2.0F;
      event.getContext().drawRect(x, y, width, height, ColorRGBA.BLACK.withAlpha(105.0F * visibility));
      event.getContext().drawText(font, name, x + padding, y + padding, ColorRGBA.WHITE.withAlpha(255.0F * visibility));
      return y + height + 2.0F;
   }

   private void renderArmorPlayer(PreHudRenderEvent event, MatrixStack matrices, PlayerEntity entity, Vec2f screenPos) {
      float distance = entity.distanceTo(mc.player);
      float scale = MathHelper.clamp(1.0F - distance / 20.0F, 0.5F, 1.0F);
      matrices.push();
      matrices.translate(screenPos.x, screenPos.y, 0.0F);
      matrices.scale(scale, scale, 1.0F);
      List<ItemStack> items = new LinkedList<>();
      items.add((ItemStack)entity.getInventory().armor.get(3));
      items.add((ItemStack)entity.getInventory().armor.get(2));
      items.add((ItemStack)entity.getInventory().armor.get(1));
      items.add((ItemStack)entity.getInventory().armor.get(0));
      items.add(entity.getMainHandStack());
      items.add(entity.getOffHandStack());
      items.removeIf(ItemStack::isEmpty);
      int count = items.size();
      if (count > 0) {
         float totalWidth = (count - 1) * 18.0F + 16.0F;
         float startX = -totalWidth / 2.0F;

         for (int i = 0; i < count; i++) {
            ItemStack item = items.get(i);
            int x = (int)(startX + i * 18);
            event.getContext().drawBatchItem(item, x, -20);
            if (this.enchants.isEnabled()) {
               this.renderEnchantments(event, item, x + 8.0F, -26.0F);
            }
         }
      }

      matrices.pop();
   }

   private void renderEnchantments(PreHudRenderEvent event, ItemStack stack, float centerX, float startY) {
      Object2IntMap<RegistryEntry<Enchantment>> enchantments = new Object2IntArrayMap();
      EnchantmentUtility.getEnchantments(stack, enchantments);
      if (!enchantments.isEmpty()) {
         Font font = Fonts.MEDIUM.getFont(7.0F);
         float y = startY;

         for (Entry<RegistryKey<Enchantment>, String> entry : ENCHANT_NAMES.entrySet()) {
            int level = EnchantmentUtility.getEnchantmentLevel(enchantments, entry.getKey());
            if (level > 0) {
               String text = entry.getValue() + level;
               event.getContext().drawText(font, text, centerX - font.width(text) / 2.0F, y, ColorRGBA.WHITE);
               y -= 6.0F;
            }
         }
      }
   }

   private void renderShulkerBack(PreHudRenderEvent event, MatrixStack matrices, ItemEntity entity, Vec2f screenPos) {
      List<ItemStack> items = ItemUtility.getItemsInShulker(entity.getStack());
      if (!items.isEmpty()) {
         float distance = entity.distanceTo(mc.player);
         float scale = MathHelper.clamp(1.0F - distance / 20.0F, 0.5F, 1.0F);
         matrices.push();
         matrices.translate(screenPos.x, screenPos.y, 0.0F);
         matrices.scale(scale, scale, 1.0F);
         int columns = Math.min(items.size(), 9);
         int rows = (int)Math.ceil(items.size() / 9.0F);
         int boxWidth = columns * 18 + 4;
         int boxHeight = rows * 18 + 4;
         event.getContext().drawRect(-boxWidth / 2, -boxHeight / 2, boxWidth, boxHeight, new ColorRGBA(0.0F, 0.0F, 0.0F, 150.0F));
         matrices.pop();
      }
   }

   private void renderShulkerDisplay(PreHudRenderEvent event, MatrixStack matrices, ItemEntity entity, Vec2f screenPos) {
      List<ItemStack> items = ItemUtility.getItemsInShulker(entity.getStack());
      if (!items.isEmpty()) {
         float distance = entity.distanceTo(mc.player);
         float scale = MathHelper.clamp(1.0F - distance / 20.0F, 0.5F, 1.0F);
         matrices.push();
         matrices.translate(screenPos.x, screenPos.y, 0.0F);
         matrices.scale(scale, scale, 1.0F);
         int columns = Math.min(items.size(), 9);
         int rows = (int)Math.ceil(items.size() / 9.0F);
         int boxWidth = columns * 18 + 4;
         int boxHeight = rows * 18 + 4;

         for (int i = 0; i < items.size(); i++) {
            ItemStack item = items.get(i);
            int x = i % 9 * 18 - boxWidth / 2 + 3;
            int y = i / 9 * 18 - boxHeight / 2 + 3;
            event.getContext().drawBatchItem(item, x, y);
         }

         matrices.pop();
      }
   }

   private void renderShulkerText(PreHudRenderEvent event, MatrixStack matrices, ItemEntity entity, Vec2f screenPos) {
      List<ItemStack> items = ItemUtility.getItemsInShulker(entity.getStack());
      if (!items.isEmpty()) {
         float distance = entity.distanceTo(mc.player);
         float scale = MathHelper.clamp(1.0F - distance / 20.0F, 0.5F, 1.0F);
         matrices.push();
         matrices.translate(screenPos.x, screenPos.y, 0.0F);
         matrices.scale(scale, scale, 1.0F);
         int columns = Math.min(items.size(), 9);
         int rows = (int)Math.ceil(items.size() / 9.0F);
         int boxWidth = columns * 18 + 4;
         int boxHeight = rows * 18 + 4;
         event.getContext().drawText(Fonts.MEDIUM.getFont(11.0F), entity.getDisplayName().getString(), -boxWidth / 2 + 0.5F, -boxHeight / 2 - 11, ColorRGBA.WHITE);

         for (int i = 0; i < items.size(); i++) {
            ItemStack item = items.get(i);
            int x = i % 9 * 18 - boxWidth / 2 + 3;
            int y = i / 9 * 18 - boxHeight / 2 + 3;
            if (item.getCount() > 1) {
               String count = String.valueOf(item.getCount());
               event.getContext().drawText(Fonts.MEDIUM.getFont(11.0F), count, x + 16 - Fonts.MEDIUM.getFont(11.0F).width(count), y + 9, ColorRGBA.WHITE);
            }
         }

         matrices.pop();
      }
   }

   private float tagScale(Entity entity) {
      float distance = entity.distanceTo(mc.player);
      return MathHelper.clamp(1.0F - distance / 20.0F, 0.5F, 1.0F);
   }

   private void fadeMissingPlayerAnimations(Set<Integer> visibleIds) {
      this.playerAnimations.entrySet().removeIf(entry -> {
         if (visibleIds.contains(entry.getKey())) {
            return false;
         }

         entry.getValue().updateVisible(false);
         return entry.getValue().hidden();
      });
   }

   private boolean isFriend(PlayerEntity player) {
      return Rockstar.getInstance().getFriendManager().isFriend(player.getName().getString());
   }

   private String protectedName(PlayerEntity player) {
      NameProtect nameProtectModule = Rockstar.getInstance().getModuleManager().getModule(NameProtect.class);
      String name = player.getDisplayName() == null ? player.getName().getString() : player.getDisplayName().getString();
      return nameProtectModule.isEnabled() ? nameProtectModule.patchName(name) : name;
   }

   private ColorRGBA healthColor(float health) {
      if (health <= 7.0F) {
         return HEALTH_LOW;
      } else {
         return health <= 15.0F ? HEALTH_MEDIUM : HEALTH_GOOD;
      }
   }

   private String cleanItemName(ItemStack stack) {
      String itemName = stack.getName().getString();
      String privilege = this.getItemPrivilege(stack);
      if (!privilege.equals("default")) {
         itemName = itemName.replace(privilege, "").replace(privilege.toUpperCase(), "").trim();
      }

      return itemName.isEmpty() ? stack.getItem().getName().getString() : itemName;
   }

   private String getPlayerPrivilege(PlayerEntity player) {
      Set<String> validPrivileges = new HashSet<>(PRIVILEGE_MAPPING.keySet());
      if (mc.player != null && mc.player.networkHandler != null) {
         PlayerListEntry entry = mc.player.networkHandler.getPlayerListEntry(player.getUuid());
         if (entry != null) {
            Text displayName = entry.getDisplayName();
            String privilege = this.parsePrivilegeFromText(displayName, validPrivileges);
            if (privilege != null) {
               return privilege;
            }
         }
      }

      String privilege = this.parsePrivilegeFromTeam(player, validPrivileges);
      return privilege != null ? privilege : "default";
   }

   private String getItemPrivilege(ItemStack itemStack) {
      if (itemStack != null && !itemStack.isEmpty()) {
         String privilege = this.parsePrivilegeFromText(itemStack.getName(), new HashSet<>(PRIVILEGE_MAPPING.keySet()));
         return privilege != null ? privilege : "default";
      } else {
         return "default";
      }
   }

   private String parsePrivilegeFromText(Text text, Set<String> validPrivileges) {
      if (text == null) {
         return null;
      }

      Style style = text.getStyle();
      if (style != null && style.getFont() != null) {
         String fontId = style.getFont().toString();
         if (fontId.contains("custom:groups/")) {
            String privilege = fontId.substring(fontId.lastIndexOf("/") + 1).toLowerCase();
            if (validPrivileges.contains(privilege)) {
               return privilege;
            }
         }
      }

      String textString = text.getString();
      if (textString != null && !textString.isEmpty()) {
         String lower = textString.toLowerCase();
         Optional<String> found = validPrivileges.stream().filter(lower::contains).findFirst();
         if (found.isPresent()) {
            return found.get();
         }
      }

      for (Text sibling : text.getSiblings()) {
         String result = this.parsePrivilegeFromText(sibling, validPrivileges);
         if (result != null) {
            return result;
         }
      }

      return null;
   }

   private String parsePrivilegeFromTeam(PlayerEntity player, Set<String> validPrivileges) {
      Team team = player.getScoreboardTeam();
      if (team != null && team.getPrefix() != null) {
         String fromPrefix = this.parsePrivilegeFromText(team.getPrefix(), validPrivileges);
         if (fromPrefix != null) {
            return fromPrefix;
         }

         String prefix = team.getPrefix().getString().toLowerCase();
         return validPrivileges.stream().filter(prefix::contains).findFirst().orElse(null);
      } else {
         return null;
      }
   }

   public static Text displayName(Entity entity) {
      if (entity.getDisplayName() == null) {
         return Text.empty();
      }

      NameProtect nameProtectModule = Rockstar.getInstance().getModuleManager().getModule(NameProtect.class);
      String displayName = nameProtectModule.isEnabled() ? nameProtectModule.patchName(entity.getDisplayName().getString()) : entity.getDisplayName().getString();
      MutableText text = Text.of(displayName).copy();
      if (entity instanceof LivingEntity living) {
         int health = (int)(entity instanceof PlayerEntity player ? EntityUtility.getHealth(player) : living.getHealth());
         if (!text.getString().endsWith(" ")) {
            text.append(" ");
         }

         return text.append(Text.of("[" + (health == 1000 ? "?" : health) + "]").copy().withColor(-2142128));
      } else {
         return text;
      }
   }

   private void handleClick(ChatClickEvent event, Entity entity, Vec2f screenPos) {
      float distance = entity.distanceTo(mc.player);
      float scale = MathHelper.clamp(1.0F - distance / 20.0F, 0.5F, 1.0F);
      Text displayName = displayName(entity);
      float textWidth = Fonts.MEDIUM.getFont(11.0F).width(displayName);
      float textHeight = Fonts.MEDIUM.getFont(11.0F).height();
      float rectWidth = textWidth + 5.0F;
      float rectHeight = textHeight + 6.0F;
      float rectOffsetX = -textWidth / 2.0F - 3.0F;
      float rectOffsetY = 2.0F;
      float scaledRectWidth = rectWidth * scale;
      float scaledRectHeight = rectHeight * scale;
      float scaledRectX = screenPos.x + rectOffsetX * scale;
      float scaledRectY = screenPos.y + rectOffsetY * scale;
      if (GuiUtility.isHovered(scaledRectX, scaledRectY, scaledRectWidth, scaledRectHeight, event.getX(), event.getY())) {
         FriendManager friendManager = Rockstar.getInstance().getFriendManager();
         TargetManager targetManager = Rockstar.getInstance().getTargetManager();
         String name = entity.getName().getString();
         this.active = new Popup(event.getX(), event.getY(), 100.0F, 6.0F)
            .title(name)
            .separator()
            .checkbox(Localizator.translate("friend"), friendManager.isFriend(name), toggled -> {
               if (toggled) {
                  friendManager.add(name);
               } else {
                  friendManager.remove(name);
               }
            })
            .checkbox(Localizator.translate("enemy"), targetManager.isTarget(name), toggled -> {
               if (toggled) {
                  targetManager.addTarget(name);
               } else {
                  targetManager.removeTarget(name);
               }
            })
            .button(Localizator.translate("copy"), "icons/hud/copy.png", popup -> {
               TextUtility.copyText(name);
               popup.setShowing(false);
            });
      }
   }

   @Override
   public void onDisable() {
      this.entityList.clear();
      this.playerAnimations.clear();
   }

   @Generated
   public List<Entity> getEntityList() {
      return this.entityList;
   }

   @Generated
   public BooleanSetting getArmor() {
      return this.armor;
   }

   @Generated
   public BooleanSetting getOffFriends() {
      return this.offFriends;
   }

   @Generated
   public BooleanSetting getItems() {
      return this.items;
   }

   @Generated
   public BooleanSetting getBackItems() {
      return this.backItems;
   }

   @Generated
   public EventListener<PreHudRenderEvent> getOnHudRenderEvent() {
      return this.onHudRenderEvent;
   }

   @Generated
   public Popup getActive() {
      return this.active;
   }

   @Generated
   public EventListener<ChatRenderEvent> getOnRender() {
      return this.onRender;
   }

   @Generated
   public EventListener<ChatClickEvent> getOnClick() {
      return this.onClick;
   }

   static {
      PRIVILEGE_MAPPING.put("hydra", "Hydra");
      PRIVILEGE_MAPPING.put("cerberus", "Cerberus");
      PRIVILEGE_MAPPING.put("triton", "Triton");
      PRIVILEGE_MAPPING.put("phoenix", "Phoenix");
      PRIVILEGE_MAPPING.put("pandar", "Pandar");
      PRIVILEGE_MAPPING.put("heat", "Heat");
      PRIVILEGE_MAPPING.put("cold", "Cold");
      PRIVILEGE_MAPPING.put("kronos", "Kronos");
      PRIVILEGE_MAPPING.put("summer", "Summer");
      PRIVILEGE_MAPPING.put("winter", "Winter");
      PRIVILEGE_MAPPING.put("phobos", "Phobos");
      PRIVILEGE_MAPPING.put("ares", "Ares");
      PRIVILEGE_MAPPING.put("aristocrat", "Aristocrat");
      PRIVILEGE_MAPPING.put("youtuber", "YouTube");
      PRIVILEGE_MAPPING.put("helper", "Helper");
      PRIVILEGE_MAPPING.put("shelper", "Sr.Helper");
      PRIVILEGE_MAPPING.put("moder", "Moder");
      PRIVILEGE_MAPPING.put("smoder", "Sr.Moder");
      PRIVILEGE_MAPPING.put("admin", "Admin");
      PRIVILEGE_COLORS.put("hydra", new ColorRGBA(94.0F, 252.0F, 133.0F));
      PRIVILEGE_COLORS.put("cerberus", new ColorRGBA(66.0F, 136.0F, 86.0F));
      PRIVILEGE_COLORS.put("triton", new ColorRGBA(65.0F, 111.0F, 225.0F));
      PRIVILEGE_COLORS.put("phoenix", new ColorRGBA(255.0F, 68.0F, 64.0F));
      PRIVILEGE_COLORS.put("pandar", new ColorRGBA(220.0F, 20.0F, 220.0F));
      PRIVILEGE_COLORS.put("heat", new ColorRGBA(255.0F, 100.0F, 87.0F));
      PRIVILEGE_COLORS.put("cold", new ColorRGBA(135.0F, 209.0F, 235.0F));
      PRIVILEGE_COLORS.put("kronos", new ColorRGBA(100.0F, 100.0F, 255.0F));
      PRIVILEGE_COLORS.put("summer", new ColorRGBA(255.0F, 165.0F, 0.0F));
      PRIVILEGE_COLORS.put("winter", new ColorRGBA(173.0F, 216.0F, 230.0F));
      PRIVILEGE_COLORS.put("phobos", new ColorRGBA(245.0F, 101.0F, 199.0F));
      PRIVILEGE_COLORS.put("ares", new ColorRGBA(135.0F, 245.0F, 231.0F));
      PRIVILEGE_COLORS.put("aristocrat", new ColorRGBA(157.0F, 0.0F, 255.0F));
      PRIVILEGE_COLORS.put("youtuber", new ColorRGBA(255.0F, 100.0F, 100.0F));
      PRIVILEGE_COLORS.put("helper", ColorRGBA.GREEN);
      PRIVILEGE_COLORS.put("shelper", ColorRGBA.YELLOW);
      PRIVILEGE_COLORS.put("moder", new ColorRGBA(128.0F, 0.0F, 128.0F));
      PRIVILEGE_COLORS.put("smoder", ColorRGBA.BLUE);
      PRIVILEGE_COLORS.put("admin", ColorRGBA.RED);
   }
}
