package moscow.rockstar.systems.modules.modules.player;

import com.mojang.blaze3d.platform.GlStateManager.DstFactor;
import com.mojang.blaze3d.platform.GlStateManager.SrcFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import lombok.Generated;
import moscow.rockstar.Rockstar;
import moscow.rockstar.systems.event.EventListener;
import moscow.rockstar.systems.event.impl.player.InputEvent;
import moscow.rockstar.systems.event.impl.render.Render3DEvent;
import moscow.rockstar.systems.localization.Localizator;
import moscow.rockstar.systems.modules.api.ModuleCategory;
import moscow.rockstar.systems.modules.api.ModuleInfo;
import moscow.rockstar.systems.modules.impl.BaseModule;
import moscow.rockstar.systems.notifications.NotificationType;
import moscow.rockstar.systems.setting.settings.BooleanSetting;
import moscow.rockstar.systems.setting.settings.ModeSetting;
import moscow.rockstar.systems.setting.settings.SliderSetting;
import moscow.rockstar.systems.setting.settings.StringSetting;
import moscow.rockstar.utility.colors.ColorRGBA;
import moscow.rockstar.utility.game.EntityUtility;
import moscow.rockstar.utility.inventory.InventoryUtility;
import moscow.rockstar.utility.inventory.group.SlotGroups;
import moscow.rockstar.utility.inventory.slots.HotbarSlot;
import moscow.rockstar.utility.inventory.slots.InventorySlot;
import moscow.rockstar.utility.render.Draw3DUtility;
import moscow.rockstar.utility.rotations.MoveCorrection;
import moscow.rockstar.utility.rotations.Rotation;
import moscow.rockstar.utility.rotations.RotationMath;
import moscow.rockstar.utility.rotations.RotationPriority;
import moscow.rockstar.utility.time.Timer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.NetherWartBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BrewingStandBlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.screen.ingame.BrewingStandScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.ItemEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SwordItem;
import net.minecraft.potion.Potion;
import net.minecraft.potion.Potions;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.screen.BrewingStandScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Direction.Type;
import net.minecraft.util.math.Vec3d;

@ModuleInfo(name = "Auto Farm", category = ModuleCategory.PLAYER, desc = "Unified auto farm module")
public class AutoFarm extends BaseModule {
   private static final double CROP_ACTION_RANGE_SQUARED = 6.25;
   private static final double CROP_DROP_RANGE_SQUARED = 2.25;
   private static final double CROP_WALK_STOP_RANGE_SQUARED = 1.0;
   private static final long CROP_BONE_MEAL_DELAY_MS = 35L;
   private static final int CROP_BONE_MEAL_BURST = 4;
   private static final int CROP_REPLANT_RESERVE = 16;
   private final ModeSetting mode = new ModeSetting(this, "modules.settings.auto_farm.mode");
   private final ModeSetting.Value apple = new ModeSetting.Value(this.mode, "modules.settings.auto_farm.modes.apple").select();
   private final ModeSetting.Value sword = new ModeSetting.Value(this.mode, "modules.settings.auto_farm.modes.sword");
   private final ModeSetting.Value potion = new ModeSetting.Value(this.mode, "modules.settings.auto_farm.modes.potion");
   private final ModeSetting.Value potionCombiner = new ModeSetting.Value(this.mode, "modules.settings.auto_farm.modes.potion_combiner");
   private final ModeSetting.Value autoSell = new ModeSetting.Value(this.mode, "modules.settings.auto_farm.modes.auto_sell");
   private final ModeSetting.Value crop = new ModeSetting.Value(this.mode, "modules.settings.auto_farm.modes.crop");
   private final SliderSetting appleBonemealDelay = new SliderSetting(this, "modules.settings.auto_farm.apple.bonemeal_delay", () -> !this.isApple())
      .step(10.0F)
      .min(0.0F)
      .max(1000.0F)
      .currentValue(150.0F)
      .suffix("ms");
   private final StringSetting swordPrice = new StringSetting(this, "modules.settings.auto_farm.sword.price", () -> !this.isSword()).text("15000");
   private final SliderSetting swordRelistCooldown = new SliderSetting(this, "modules.settings.auto_farm.sword.relist_cooldown", () -> !this.isSword())
      .step(5.0F)
      .min(5.0F)
      .max(300.0F)
      .currentValue(60.0F)
      .suffix("sec");
   private final BooleanSetting swordCraftAll = new BooleanSetting(this, "modules.settings.auto_farm.sword.craft_all", () -> !this.isSword());
   private final ModeSetting potionBrew = new ModeSetting(this, "modules.settings.potion_farm.brew", () -> !this.isPotion());
   private final ModeSetting.Value potionStrength = new ModeSetting.Value(this.potionBrew, "modules.settings.potion_farm.potion.strength").select();
   private final ModeSetting.Value potionSpeed = new ModeSetting.Value(this.potionBrew, "modules.settings.potion_farm.potion.speed");
   private final ModeSetting.Value potionFireResistance = new ModeSetting.Value(this.potionBrew, "modules.settings.potion_farm.potion.fire_resistance");
   private final ModeSetting.Value potionInvisibility = new ModeSetting.Value(this.potionBrew, "modules.settings.potion_farm.potion.invisibility");
   private final ModeSetting.Value potionRegen = new ModeSetting.Value(this.potionBrew, "modules.settings.potion_farm.potion.regen");
   private final ModeSetting.Value potionHealing = new ModeSetting.Value(this.potionBrew, "modules.settings.potion_farm.potion.healing");
   private final ModeSetting.Value potionStrongHealing = new ModeSetting.Value(this.potionBrew, "modules.settings.potion_farm.potion.strong_healing");
   private final ModeSetting potionStackMode = new ModeSetting(this, "modules.settings.potion_farm.stack_mode", () -> !this.isPotion());
   private final ModeSetting.Value potionStackMultiple = new ModeSetting.Value(this.potionStackMode, "modules.settings.potion_farm.stack_mode.multiple")
      .select();
   private final ModeSetting.Value potionStackSingle = new ModeSetting.Value(this.potionStackMode, "modules.settings.potion_farm.stack_mode.single");
   private final BooleanSetting potionEnhance = new BooleanSetting(
      this, "modules.settings.potion_farm.enhance", () -> !this.isPotion() || this.potionInvisibility.isSelected()
   );
   private final BooleanSetting potionUseChests = new BooleanSetting(this, "modules.settings.potion_farm.use_chests", () -> !this.isPotion()).enabled(true);
   private final BooleanSetting potionTargetEsp = new BooleanSetting(this, "modules.settings.potion_farm.target_esp", () -> !this.isPotion()).enabled(true);
   private final SliderSetting potionDelay = new SliderSetting(
         this, "modules.settings.potion_farm.delay", "modules.settings.potion_farm.delay.description", () -> !this.isPotion()
      )
      .step(10.0F)
      .min(0.0F)
      .max(1500.0F)
      .currentValue(250.0F)
      .suffix("ms");
   private final SliderSetting potionSingleStackDelay = new SliderSetting(
         this,
         "modules.settings.potion_farm.single_stack_delay",
         "modules.settings.potion_farm.single_stack_delay.description",
         () -> !this.isPotion() || !this.potionStackSingle.isSelected()
      )
      .step(10.0F)
      .min(50.0F)
      .max(2000.0F)
      .currentValue(250.0F)
      .suffix("ms");
   private final ModeSetting combinerPotions = new ModeSetting(this, "modules.settings.potion_combiner.potions", () -> !this.isPotionCombiner());
   private final ModeSetting.Value combinerStrength = new ModeSetting.Value(this.combinerPotions, "modules.settings.potion_combiner.potion.strength").select();
   private final ModeSetting.Value combinerSpeed = new ModeSetting.Value(this.combinerPotions, "modules.settings.potion_combiner.potion.speed");
   private final ModeSetting.Value combinerStrengthSpeed = new ModeSetting.Value(this.combinerPotions, "modules.settings.potion_combiner.potion.strength_speed");
   private final BooleanSetting combinerAutoOpen = new BooleanSetting(this, "modules.settings.potion_combiner.auto_open", () -> !this.isPotionCombiner())
      .enabled(true);
   private final BooleanSetting combinerAutoExp = new BooleanSetting(this, "modules.settings.potion_combiner.auto_exp", () -> !this.isPotionCombiner());
   private final SliderSetting combinerRefillTo = new SliderSetting(
         this, "modules.settings.potion_combiner.refill_to", () -> !this.isPotionCombiner() || !this.combinerAutoExp.isEnabled()
      )
      .step(1.0F)
      .min(5.0F)
      .max(100.0F)
      .currentValue(40.0F);
   private final StringSetting autoSellQuantity = new StringSetting(this, "modules.settings.auto_sell.quantity", () -> !this.isAutoSell()).text("64");
   private final StringSetting autoSellPrice = new StringSetting(this, "modules.settings.auto_sell.price", () -> !this.isAutoSell()).text("10000");
   private final SliderSetting autoSellRelistCooldown = new SliderSetting(this, "modules.settings.auto_sell.relist_cooldown", () -> !this.isAutoSell())
      .step(5.0F)
      .min(5.0F)
      .max(300.0F)
      .currentValue(60.0F)
      .suffix("sec");
   private final BooleanSetting autoSellConfirm = new BooleanSetting(this, "modules.settings.auto_sell.confirm", () -> !this.isAutoSell());
   private final ModeSetting cropType = new ModeSetting(this, "modules.settings.crop_farm.crop", () -> !this.isCrop());
   private final ModeSetting.Value cropNetherWart = new ModeSetting.Value(this.cropType, "modules.settings.crop_farm.crop.nether_wart").select();
   private final ModeSetting.Value cropWheat = new ModeSetting.Value(this.cropType, "modules.settings.crop_farm.crop.wheat");
   private final ModeSetting.Value cropCarrots = new ModeSetting.Value(this.cropType, "modules.settings.crop_farm.crop.carrots");
   private final ModeSetting.Value cropPotatoes = new ModeSetting.Value(this.cropType, "modules.settings.crop_farm.crop.potatoes");
   private final ModeSetting.Value cropBeetroots = new ModeSetting.Value(this.cropType, "modules.settings.crop_farm.crop.beetroots");
   private final ModeSetting.Value cropSugarCane = new ModeSetting.Value(this.cropType, "modules.settings.crop_farm.crop.sugar_cane");
   private final SliderSetting cropScanRadius = new SliderSetting(this, "modules.settings.crop_farm.scan_radius", () -> !this.isCrop())
      .step(2.0F)
      .min(8.0F)
      .max(64.0F)
      .currentValue(24.0F);
   private final SliderSetting cropVerticalRange = new SliderSetting(this, "modules.settings.crop_farm.vertical_range", () -> !this.isCrop())
      .step(1.0F)
      .min(1.0F)
      .max(8.0F)
      .currentValue(3.0F);
   private final BooleanSetting cropReplant = new BooleanSetting(this, "modules.settings.crop_farm.replant", () -> !this.isCrop()).enabled(true);
   private final BooleanSetting cropUseHoe = new BooleanSetting(this, "modules.settings.crop_farm.use_hoe", () -> !this.isCrop()).enabled(true);
   private final BooleanSetting cropPickup = new BooleanSetting(this, "modules.settings.crop_farm.pickup", () -> !this.isCrop()).enabled(true);
   private final BooleanSetting cropAutoDeposit = new BooleanSetting(this, "modules.settings.crop_farm.auto_deposit", () -> !this.isCrop()).enabled(true);
   private final BooleanSetting cropTargetEsp = new BooleanSetting(this, "modules.settings.crop_farm.target_esp", () -> !this.isCrop()).enabled(true);
   private final SliderSetting cropActionDelay = new SliderSetting(this, "modules.settings.crop_farm.action_delay", () -> !this.isCrop())
      .step(10.0F)
      .min(0.0F)
      .max(500.0F)
      .currentValue(80.0F)
      .suffix("ms");
   private final Timer actionTimer = new Timer();
   private final Timer sellTimer = new Timer();
   private final Timer warnTimer = new Timer();
   private BlockPos currentTarget;
   private BlockPos currentChest;
   private BlockPos appleDirt;
   private BlockPos pendingReplant;
   private AutoFarm.PotionDepositState potionDepositState = AutoFarm.PotionDepositState.NONE;
   private boolean cropWalking;
   private Vec3d cropWalkTarget;
   private double cropWalkStopRangeSquared;
   private final EventListener<Render3DEvent> onRender3D = event -> {
      if (EntityUtility.isInGame() && this.currentTarget != null) {
         if (this.isCrop() && this.cropTargetEsp.isEnabled() || this.isPotion() && this.potionTargetEsp.isEnabled()) {
            this.drawTarget(event, this.currentTarget, this.isPotion() ? new ColorRGBA(255.0F, 196.0F, 64.0F) : new ColorRGBA(120.0F, 220.0F, 96.0F));
         }
      }
   };
   private final EventListener<InputEvent> onInput = event -> {
      if (this.cropWalking && this.cropWalkTarget != null && mc.player != null && mc.currentScreen == null) {
         if (mc.player.squaredDistanceTo(this.cropWalkTarget) <= this.cropWalkStopRangeSquared) {
            this.stopCropWalking();
         } else {
            event.setForward(1.0F);
            event.setStrafe(0.0F);
            event.setSprint(true);
            event.setJump(mc.player.horizontalCollision && mc.player.isOnGround());
         }
      }
   };

   @Override
   public void tick() {
      if (mc.player != null && mc.world != null && mc.interactionManager != null && mc.player.networkHandler != null) {
         if (!this.isCrop()) {
            this.stopCropWalking();
         }

         if (this.isApple()) {
            this.handleAppleFarm();
         } else if (this.isSword()) {
            this.handleSwordFarmSafely();
         } else if (this.isPotion()) {
            this.handlePotionFarm();
         } else if (this.isPotionCombiner()) {
            this.handlePotionCombiner();
         } else if (this.isAutoSell()) {
            this.handleAutoSell();
         } else if (this.isCrop()) {
            this.handleCropFarm();
         }
      }
   }

   @Override
   public void onDisable() {
      this.currentTarget = null;
      this.currentChest = null;
      this.appleDirt = null;
      this.pendingReplant = null;
      this.potionDepositState = AutoFarm.PotionDepositState.NONE;
      this.stopCropWalking();
   }

   private void handleAppleFarm() {
      if (this.appleDirt == null || !this.isAppleDirt(this.appleDirt)) {
         List<BlockPos> dirt = this.findAppleDirtBlocks();
         if (dirt.isEmpty()) {
            this.warn("modules.apple_farm.no_dirt");
            return;
         }

         if (dirt.size() > 1) {
            this.warn("modules.apple_farm.multiple_dirt");
            return;
         }

         this.appleDirt = (BlockPos)dirt.getFirst();
      }

      BlockPos saplingPos = this.appleDirt.up();
      BlockState saplingState = mc.world.getBlockState(saplingPos);
      this.currentTarget = saplingPos;
      if (saplingState.isAir()) {
         if (!this.ensureHotbarItem(stack -> stack.getItem() == Items.OAK_SAPLING)) {
            this.warn("modules.apple_farm.no_sapling");
         } else {
            this.useBlock(this.appleDirt, Direction.UP, Hand.MAIN_HAND);
         }
      } else if (saplingState.getBlock() == Blocks.OAK_SAPLING) {
         if (!this.ensureHotbarItem(stack -> stack.getItem() == Items.BONE_MEAL)) {
            this.warn("modules.apple_farm.no_bonemeal");
         } else {
            if (this.actionTimer.finished((long)this.appleBonemealDelay.getCurrentValue())) {
               this.useBlock(saplingPos, Direction.UP, Hand.MAIN_HAND);
               this.actionTimer.reset();
            }
         }
      } else {
         BlockPos treeBlock = this.findTreeBlock(saplingPos);
         if (treeBlock != null) {
            this.currentTarget = treeBlock;
            if (this.ensureHotbarItem(this::isAxeOrHoe) && this.actionTimer.finished(120L)) {
               this.breakBlock(treeBlock);
               this.actionTimer.reset();
            }
         }
      }
   }

   private void handleCropFarm() {
      if (!this.cropAutoDeposit.isEnabled() || !(mc.currentScreen instanceof GenericContainerScreen) && (!this.isInventoryFull() || !this.hasDepositableSelectedCropItems())) {
         if (!this.cropPickup.isEnabled() || !this.moveToNearestDrop()) {
            if (!this.tryReplantPending()) {
               if (!this.tryBoneMealSelectedCrop()) {
                  BlockPos target = this.findCropTarget();
                  this.currentTarget = target;
                  if (target == null) {
                     if (!this.tryReplantNearPlayer()) {
                        if (this.cropAutoDeposit.isEnabled() && this.hasDepositableSelectedCropItems()) {
                           this.depositSelectedCrops();
                        } else {
                           this.stopCropWalking();
                        }
                     }
                  } else if (!this.isInCropActionRange(target)) {
                     this.walkToCropTarget(target);
                  } else {
                     this.stopCropWalking();
                     if (this.actionTimer.finished((long)this.cropActionDelay.getCurrentValue())) {
                        if (this.cropUseHoe.isEnabled()) {
                           this.ensureHotbarItem(this::isHoe);
                        }

                        this.pendingReplant = target.toImmutable();
                        this.breakBlock(target);
                        this.actionTimer.reset();
                     }
                  }
               }
            }
         }
      } else {
         this.depositSelectedCrops();
      }
   }

   private void handlePotionFarm() {
      if (this.potionDepositState != AutoFarm.PotionDepositState.OPEN_CHEST && this.potionDepositState != AutoFarm.PotionDepositState.DEPOSIT) {
         if (mc.currentScreen instanceof BrewingStandScreen && mc.player.currentScreenHandler instanceof BrewingStandScreenHandler brew) {
            this.currentTarget = ((Slot)((BrewingStandScreenHandler)mc.player.currentScreenHandler).slots.getFirst()).inventory instanceof BrewingStandBlockEntity stand
               ? stand.getPos()
               : this.currentTarget;
            if (this.actionTimer.finished((long)this.potionDelay.getCurrentValue())) {
               if (brew.getFuel() <= 0 && brew.getSlot(4).getStack().isEmpty()) {
                  this.moveOneIngredient(Items.BLAZE_POWDER, 4);
               } else if (!this.fillPotionBottles(brew)) {
                  Item ingredient = this.nextPotionIngredient(brew);
                  if (ingredient != null) {
                     this.moveOneIngredient(ingredient, 3);
                  } else {
                     if (this.isPotionComplete(brew)) {
                        this.lootPotions(brew);
                        if (this.potionUseChests.isEnabled()) {
                           this.potionDepositState = AutoFarm.PotionDepositState.OPEN_CHEST;
                        }
                     }
                  }
               }
            }
         } else {
            BrewingStandBlockEntity stand = this.findBlockEntity(BrewingStandBlockEntity.class, 5.0);
            if (stand != null && this.actionTimer.finished(300L)) {
               this.currentTarget = stand.getPos();
               this.useBlock(stand.getPos(), Direction.UP, Hand.MAIN_HAND);
               this.actionTimer.reset();
            }
         }
      } else {
         this.depositPotionsToChest();
      }
   }

   private void handlePotionCombiner() {
      if (this.combinerAutoOpen.isEnabled() && mc.currentScreen == null) {
         ChestBlockEntity chest = this.findBlockEntity(ChestBlockEntity.class, 5.0);
         if (chest != null && this.actionTimer.finished(350L)) {
            this.currentTarget = chest.getPos();
            this.useBlock(chest.getPos(), Direction.UP, Hand.MAIN_HAND);
            this.actionTimer.reset();
         }
      } else if (mc.currentScreen instanceof GenericContainerScreen && mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler) {
         if (this.actionTimer.finished(150L)) {
            Potion wanted = this.selectedCombinerPotion();
            int moved = 0;

            for (int i = handler.getInventory().size(); i < handler.slots.size(); i++) {
               ItemStack stack = handler.getSlot(i).getStack();
               if (this.isPotionStack(stack) && this.matchesPotion(stack, wanted)) {
                  mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                  if (!this.combinerStrengthSpeed.isSelected() || ++moved >= 2) {
                     break;
                  }
               }
            }

            if (this.combinerAutoExp.isEnabled() && this.countItem(Items.EXPERIENCE_BOTTLE) < this.combinerRefillTo.getCurrentValue()) {
               this.moveFirstContainerItem(handler, stackx -> stackx.getItem() == Items.EXPERIENCE_BOTTLE);
            }

            this.actionTimer.reset();
         }
      }
   }

   private void handleAutoSell() {
      if (!(mc.currentScreen instanceof GenericContainerScreen && mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler)) {
         ItemStack hand = mc.player.getMainHandStack();
         if (hand.isEmpty()) {
            this.warn("modules.auto_sell.no_item_in_hand");
         } else {
            if (this.sellTimer.finished((long)(this.autoSellRelistCooldown.getCurrentValue() * 1000.0F))) {
               mc.player.networkHandler.sendChatCommand("ah sell " + this.sanitized(this.autoSellPrice.getText()));
               this.sellTimer.reset();
            }
         }
      } else if (!this.autoSellConfirm.isEnabled() || !this.clickFirstContainerItem(handler, stack -> stack.getItem() == Items.LIME_DYE)) {
         this.clickFirstContainerItem(handler, stack -> stack.getName().getString().toLowerCase().contains("storage"));
      }
   }

   private void handleSwordFarmSafely() {
      try {
         this.handleSwordFarm();
      } catch (RuntimeException exception) {
         Rockstar.LOGGER.warn("AutoFarm sword mode failed", exception);
         this.warn("modules.sword_farm.error");
      }
   }

   private void handleSwordFarm() {
      boolean hasSword = this.hasItem(this::isSwordStack);
      boolean hasStick = this.hasItem(stack -> stack.getItem() == Items.STICK);
      boolean hasDiamonds = this.countItem(Items.DIAMOND) >= 2;
      if (!hasSword && !hasStick) {
         this.warn("modules.sword_farm.no_sticks");
      } else if (!hasSword && !hasDiamonds) {
         this.warn("modules.sword_farm.no_diamonds");
      } else if (!this.ensureHotbarItem(this::isSwordStack)) {
         if (this.swordCraftAll.isEnabled() && mc.currentScreen instanceof GenericContainerScreen && mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler) {
            if (!this.moveFirstContainerItem(handler, this::isSwordStack)) {
               this.warn("modules.sword_farm.no_sword");
            }
         } else {
            this.warn("modules.sword_farm.no_sword");
         }
      } else {
         if (this.sellTimer.finished((long)(this.swordRelistCooldown.getCurrentValue() * 1000.0F))) {
            mc.player.networkHandler.sendChatCommand("ah sell " + this.sanitized(this.swordPrice.getText()));
            this.sellTimer.reset();
         }
      }
   }

   private BlockPos findCropTarget() {
      int radius = (int)this.cropScanRadius.getCurrentValue();
      int vertical = (int)this.cropVerticalRange.getCurrentValue();
      BlockPos origin = mc.player.getBlockPos();
      BlockPos best = null;
      double bestDistance = Double.MAX_VALUE;

      for (int x = -radius; x <= radius; x++) {
         for (int y = -vertical; y <= vertical; y++) {
            for (int z = -radius; z <= radius; z++) {
               BlockPos pos = origin.add(x, y, z);
               if (this.isReadyCrop(pos)) {
                  double distance = mc.player.squaredDistanceTo(pos.toCenterPos());
                  if (distance < bestDistance) {
                     best = pos;
                     bestDistance = distance;
                  }
               }
            }
         }
      }

      return best;
   }

   private boolean isReadyCrop(BlockPos pos) {
      BlockState state = mc.world.getBlockState(pos);
      Block block = state.getBlock();
      if (this.cropNetherWart.isSelected()) {
         return block == Blocks.NETHER_WART && (Integer)state.get(NetherWartBlock.AGE) >= 3;
      } else if (!this.cropSugarCane.isSelected()) {
         return block instanceof CropBlock cropBlock && this.isSelectedCropBlock(block) ? cropBlock.getAge(state) >= cropBlock.getMaxAge() : false;
      } else {
         return block == Blocks.SUGAR_CANE && mc.world.getBlockState(pos.down()).getBlock() == Blocks.SUGAR_CANE;
      }
   }

   private boolean tryBoneMealSelectedCrop() {
      if (!this.hasItem(stack -> stack.getItem() == Items.BONE_MEAL)) {
         return false;
      }

      BlockPos target = this.findBoneMealTarget();
      if (target == null) {
         return false;
      }

      this.currentTarget = target;
      if (!this.isInCropActionRange(target)) {
         this.walkToCropTarget(target);
         return true;
      }

      this.stopCropWalking();
      long boneMealDelay = Math.min((long)this.cropActionDelay.getCurrentValue(), 35L);
      if (!this.actionTimer.finished(boneMealDelay)) {
         return true;
      }

      if (!this.ensureHotbarItem(stack -> stack.getItem() == Items.BONE_MEAL)) {
         return false;
      }

      int used = 0;

      for (int i = 0; i < 4; i++) {
         BlockPos inRangeTarget = this.findBoneMealTarget(this::isInCropActionRange);
         if (inRangeTarget == null) {
            break;
         }

         this.currentTarget = inRangeTarget;
         this.useBlock(inRangeTarget, Direction.UP, Hand.MAIN_HAND);
         used++;
      }

      if (used > 0) {
         this.actionTimer.reset();
         return true;
      } else {
         return false;
      }
   }

   private BlockPos findBoneMealTarget() {
      return this.findBoneMealTarget(pos -> true);
   }

   private BlockPos findBoneMealTarget(Predicate<BlockPos> filter) {
      int radius = (int)this.cropScanRadius.getCurrentValue();
      int vertical = (int)this.cropVerticalRange.getCurrentValue();
      BlockPos origin = mc.player.getBlockPos();
      BlockPos best = null;
      double bestDistance = Double.MAX_VALUE;

      for (int x = -radius; x <= radius; x++) {
         for (int y = -vertical; y <= vertical; y++) {
            for (int z = -radius; z <= radius; z++) {
               BlockPos pos = origin.add(x, y, z);
               if (this.isBoneMealableSelectedCrop(pos) && filter.test(pos)) {
                  double distance = mc.player.squaredDistanceTo(pos.toCenterPos());
                  if (distance < bestDistance) {
                     best = pos;
                     bestDistance = distance;
                  }
               }
            }
         }
      }

      return best;
   }

   private boolean isBoneMealableSelectedCrop(BlockPos pos) {
      BlockState state = mc.world.getBlockState(pos);
      Block block = state.getBlock();
      return block instanceof CropBlock cropBlock && this.isSelectedCropBlock(block) ? cropBlock.getAge(state) < cropBlock.getMaxAge() : false;
   }

   private boolean isSelectedCropBlock(Block block) {
      return this.cropWheat.isSelected() && block == Blocks.WHEAT
         || this.cropCarrots.isSelected() && block == Blocks.CARROTS
         || this.cropPotatoes.isSelected() && block == Blocks.POTATOES
         || this.cropBeetroots.isSelected() && block == Blocks.BEETROOTS;
   }

   private boolean tryReplantPending() {
      if (this.cropReplant.isEnabled() && this.pendingReplant != null) {
         Item seed = this.seedForCurrentCrop();
         if (seed == null) {
            this.pendingReplant = null;
            return false;
         }

         BlockPos target = this.pendingReplant;
         if (!this.isInCropActionRange(target)) {
            this.currentTarget = target;
            this.walkToCropTarget(target);
            return true;
         }

         BlockState state = mc.world.getBlockState(target);
         if (!state.isAir()) {
            if (this.isSelectedPlantedCrop(state.getBlock())) {
               this.pendingReplant = null;
            }

            return false;
         } else if (!this.actionTimer.finished((long)this.cropActionDelay.getCurrentValue())) {
            this.currentTarget = target;
            this.stopCropWalking();
            return true;
         } else if (this.tryPlantCropAt(target, seed)) {
            this.pendingReplant = null;
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean isSelectedPlantedCrop(Block block) {
      return this.isSelectedCropBlock(block)
         || this.cropNetherWart.isSelected() && block == Blocks.NETHER_WART
         || this.cropSugarCane.isSelected() && block == Blocks.SUGAR_CANE;
   }

   private boolean tryReplantNearPlayer() {
      if (this.cropReplant.isEnabled() && this.actionTimer.finished((long)this.cropActionDelay.getCurrentValue())) {
         Item seed = this.seedForCurrentCrop();
         if (seed == null) {
            return false;
         }

         BlockPos origin = mc.player.getBlockPos();
         int radius = (int)this.cropScanRadius.getCurrentValue();
         int vertical = (int)this.cropVerticalRange.getCurrentValue();
         BlockPos best = null;
         double bestDistance = Double.MAX_VALUE;

         for (int x = -radius; x <= radius; x++) {
            for (int y = -vertical; y <= vertical; y++) {
               for (int z = -radius; z <= radius; z++) {
                  BlockPos pos = origin.add(x, y, z);
                  double distance = mc.player.squaredDistanceTo(pos.toCenterPos());
                  if (this.isPlantableCropSpot(pos, seed) && distance < bestDistance) {
                     best = pos;
                     bestDistance = distance;
                  }
               }
            }
         }

         if (best != null) {
            this.currentTarget = best;
            if (!this.isInCropActionRange(best)) {
               this.walkToCropTarget(best);
               return true;
            } else {
               this.stopCropWalking();
               return this.tryPlantCropAt(best, seed);
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean tryPlantCropAt(BlockPos pos, Item seed) {
      if (this.isPlantableCropSpot(pos, seed) && this.ensureHotbarItem(stack -> stack.getItem() == seed)) {
         this.currentTarget = pos;
         this.stopCropWalking();
         this.useBlock(pos.down(), Direction.UP, Hand.MAIN_HAND);
         this.actionTimer.reset();
         return true;
      } else {
         return false;
      }
   }

   private boolean isPlantableCropSpot(BlockPos pos, Item seed) {
      if (!mc.world.getBlockState(pos).isAir()) {
         return false;
      }

      Block support = mc.world.getBlockState(pos.down()).getBlock();
      return this.canPlantOn(support, seed) && (seed != Items.SUGAR_CANE || this.hasAdjacentWater(pos.down()));
   }

   private boolean canPlantOn(Block blockBelow, Item seed) {
      if (seed == Items.NETHER_WART) {
         return blockBelow == Blocks.SOUL_SAND;
      } else {
         return seed != Items.SUGAR_CANE
            ? blockBelow == Blocks.FARMLAND
            : blockBelow == Blocks.SAND
               || blockBelow == Blocks.DIRT
               || blockBelow == Blocks.GRASS_BLOCK
               || blockBelow == Blocks.MUD;
      }
   }

   private boolean hasAdjacentWater(BlockPos pos) {
      for (Direction direction : Type.HORIZONTAL) {
         if (mc.world.getFluidState(pos.offset(direction)).isOf(Fluids.WATER)) {
            return true;
         }
      }

      return false;
   }

   private Item seedForCurrentCrop() {
      if (this.cropNetherWart.isSelected()) {
         return Items.NETHER_WART;
      } else if (this.cropWheat.isSelected()) {
         return Items.WHEAT_SEEDS;
      } else if (this.cropCarrots.isSelected()) {
         return Items.CARROT;
      } else if (this.cropPotatoes.isSelected()) {
         return Items.POTATO;
      } else if (this.cropBeetroots.isSelected()) {
         return Items.BEETROOT_SEEDS;
      } else {
         return this.cropSugarCane.isSelected() ? Items.SUGAR_CANE : null;
      }
   }

   private void depositSelectedCrops() {
      if (mc.currentScreen instanceof GenericContainerScreen && mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler) {
         if (!this.depositOneSelectedCropStack(handler)) {
            mc.player.closeHandledScreen();
         }
      } else if (!this.hasDepositableSelectedCropItems()) {
         this.currentChest = null;
         this.stopCropWalking();
      } else {
         ChestBlockEntity chest = this.findCropDepositChest();
         if (chest == null) {
            this.stopCropWalking();
            this.warn("modules.crop_farm.no_chest");
         } else {
            this.currentChest = chest.getPos();
            this.currentTarget = this.currentChest;
            if (!this.isInCropActionRange(this.currentChest)) {
               this.walkToCropTarget(this.currentChest);
            } else {
               this.stopCropWalking();
               if (this.actionTimer.finished(300L)) {
                  this.useBlock(this.currentChest, Direction.UP, Hand.MAIN_HAND);
                  this.actionTimer.reset();
               }
            }
         }
      }
   }

   private boolean depositOneSelectedCropStack(GenericContainerScreenHandler handler) {
      int containerSize = handler.getInventory().size();
      int emptyContainerSlot = this.findEmptyContainerSlot(handler, containerSize);

      for (int i = containerSize; i < handler.slots.size(); i++) {
         ItemStack stack = handler.getSlot(i).getStack();
         if (this.isSelectedCropItem(stack) && this.canDepositCropStack(stack, emptyContainerSlot != -1)) {
            int reserveInThisStack = this.getReserveInStack(stack);
            if (reserveInThisStack > 0) {
               mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, mc.player);

               for (int reserve = 0; reserve < reserveInThisStack; reserve++) {
                  mc.interactionManager.clickSlot(handler.syncId, i, 1, SlotActionType.PICKUP, mc.player);
               }

               mc.interactionManager.clickSlot(handler.syncId, emptyContainerSlot, 0, SlotActionType.PICKUP, mc.player);
            } else {
               mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
            }

            this.actionTimer.reset();
            return true;
         }
      }

      return false;
   }

   private boolean canDepositCropStack(ItemStack stack, boolean hasEmptyContainerSlot) {
      if (!this.shouldReserveCropItem(stack.getItem())) {
         return true;
      }

      int total = this.countItem(stack.getItem());
      return total - stack.getCount() >= 16 ? true : hasEmptyContainerSlot && total > 16;
   }

   private int getReserveInStack(ItemStack stack) {
      return !this.shouldReserveCropItem(stack.getItem()) ? 0 : Math.max(0, 16 - (this.countItem(stack.getItem()) - stack.getCount()));
   }

   private int findEmptyContainerSlot(GenericContainerScreenHandler handler, int containerSize) {
      for (int i = 0; i < containerSize; i++) {
         if (handler.getSlot(i).getStack().isEmpty()) {
            return i;
         }
      }

      return -1;
   }

   private boolean shouldReserveCropItem(Item item) {
      return item == Items.CARROT || item == Items.POTATO || item == Items.NETHER_WART || item == Items.SUGAR_CANE;
   }

   private boolean isSelectedCropItem(ItemStack stack) {
      Item item = stack.getItem();
      return this.cropNetherWart.isSelected() && item == Items.NETHER_WART
         || this.cropWheat.isSelected() && item == Items.WHEAT
         || this.cropCarrots.isSelected() && item == Items.CARROT
         || this.cropPotatoes.isSelected() && item == Items.POTATO
         || this.cropBeetroots.isSelected() && item == Items.BEETROOT
         || this.cropSugarCane.isSelected() && item == Items.SUGAR_CANE;
   }

   private boolean hasSelectedCropItems() {
      return this.hasItem(this::isSelectedCropItem);
   }

   private boolean hasDepositableSelectedCropItems() {
      Item selected = this.selectedCropDepositItem();
      if (selected == null) {
         return false;
      }

      int count = this.countItem(selected);
      return this.shouldReserveCropItem(selected) ? count > 16 : count > 0;
   }

   private Item selectedCropDepositItem() {
      if (this.cropNetherWart.isSelected()) {
         return Items.NETHER_WART;
      } else if (this.cropWheat.isSelected()) {
         return Items.WHEAT;
      } else if (this.cropCarrots.isSelected()) {
         return Items.CARROT;
      } else if (this.cropPotatoes.isSelected()) {
         return Items.POTATO;
      } else if (this.cropBeetroots.isSelected()) {
         return Items.BEETROOT;
      } else {
         return this.cropSugarCane.isSelected() ? Items.SUGAR_CANE : null;
      }
   }

   private boolean isInCropActionRange(BlockPos pos) {
      return pos != null && mc.player.squaredDistanceTo(pos.toCenterPos()) <= 6.25;
   }

   private void walkToCropTarget(BlockPos pos) {
      this.walkTo(this.getCropWalkTarget(pos), 1.0);
   }

   private boolean walkTo(Vec3d target, double stopRangeSquared) {
      if (target != null && mc.currentScreen == null) {
         if (mc.player.squaredDistanceTo(target) <= stopRangeSquared) {
            this.stopCropWalking();
            return false;
         } else {
            Vec3d delta = target.subtract(mc.player.getPos());
            float yaw = (float)Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
            Rockstar.getInstance()
               .getRotationHandler()
               .rotate(new Rotation(yaw, mc.player.getPitch()), MoveCorrection.DIRECT, 85.0F, 85.0F, 120.0F, RotationPriority.NORMAL);
            mc.options.forwardKey.setPressed(true);
            mc.options.sprintKey.setPressed(true);
            mc.options.jumpKey.setPressed(mc.player.horizontalCollision && mc.player.isOnGround());
            this.cropWalkTarget = target;
            this.cropWalkStopRangeSquared = stopRangeSquared;
            this.cropWalking = true;
            return true;
         }
      } else {
         this.stopCropWalking();
         return false;
      }
   }

   private void stopCropWalking() {
      if (!this.cropWalking) {
         this.cropWalkTarget = null;
         this.cropWalkStopRangeSquared = 0.0;
      } else {
         mc.options.forwardKey.setPressed(false);
         mc.options.sprintKey.setPressed(false);
         mc.options.jumpKey.setPressed(false);
         this.cropWalkTarget = null;
         this.cropWalkStopRangeSquared = 0.0;
         this.cropWalking = false;
      }
   }

   private Vec3d getCropWalkTarget(BlockPos pos) {
      BlockPos standPos = this.findCropStandPos(pos);
      return standPos == null ? pos.toCenterPos() : Vec3d.ofBottomCenter(standPos);
   }

   private BlockPos findCropStandPos(BlockPos pos) {
      BlockPos best = null;
      double bestDistance = Double.MAX_VALUE;

      for (Direction direction : Type.HORIZONTAL) {
         BlockPos candidate = pos.offset(direction);
         if (this.canStandAt(candidate)) {
            double distance = mc.player.squaredDistanceTo(Vec3d.ofBottomCenter(candidate));
            if (distance < bestDistance) {
               best = candidate;
               bestDistance = distance;
            }
         }
      }

      return best;
   }

   private boolean canStandAt(BlockPos pos) {
      return mc.world.getBlockState(pos).getCollisionShape(mc.world, pos).isEmpty()
         && mc.world.getBlockState(pos.up()).getCollisionShape(mc.world, pos.up()).isEmpty()
         && !mc.world.getBlockState(pos.down()).getCollisionShape(mc.world, pos.down()).isEmpty();
   }

   private ChestBlockEntity findCropDepositChest() {
      BlockPos origin = mc.player.getBlockPos();
      int radius = (int)Math.ceil(this.cropScanRadius.getCurrentValue());
      int vertical = Math.max(4, (int)this.cropVerticalRange.getCurrentValue() + 2);
      ChestBlockEntity best = null;
      double bestDistance = Double.MAX_VALUE;

      for (int x = -radius; x <= radius; x++) {
         for (int y = -vertical; y <= vertical; y++) {
            for (int z = -radius; z <= radius; z++) {
               BlockPos pos = origin.add(x, y, z);
               if (mc.world.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
                  double distance = mc.player.squaredDistanceTo(pos.toCenterPos());
                  if (distance < bestDistance) {
                     best = chest;
                     bestDistance = distance;
                  }
               }
            }
         }
      }

      return best;
   }

   private boolean moveToNearestDrop() {
      double pickupRange = Math.max(6.0, this.cropScanRadius.getCurrentValue());
      ItemEntity drop = mc.world
         .getEntitiesByClass(ItemEntity.class, mc.player.getBoundingBox().expand(pickupRange), entity -> this.isSelectedCropItem(entity.getStack()))
         .stream()
         .min(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(mc.player)))
         .orElse(null);
      if (drop == null) {
         return false;
      } else {
         Vec3d dropPos = drop.getPos().add(0.0, 0.25, 0.0);
         Rockstar.getInstance()
            .getRotationHandler()
            .rotate(RotationMath.getRotationTo(dropPos), MoveCorrection.SILENT, 60.0F, 60.0F, 60.0F, RotationPriority.NORMAL);
         if (drop.squaredDistanceTo(mc.player) > 2.25) {
            this.currentTarget = drop.getBlockPos();
            this.walkTo(dropPos, 2.25);
            return true;
         } else {
            this.stopCropWalking();
            return false;
         }
      }
   }

   private Item nextPotionIngredient(BrewingStandScreenHandler brew) {
      if (this.allPotionSlotsAre(brew, (Potion)Potions.WATER.value())) {
         return Items.NETHER_WART;
      }

      if (this.allPotionSlotsAre(brew, (Potion)Potions.AWKWARD.value())) {
         if (this.potionStrength.isSelected()) {
            return Items.BLAZE_POWDER;
         }

         if (this.potionSpeed.isSelected()) {
            return Items.SUGAR;
         }

         if (this.potionFireResistance.isSelected()) {
            return Items.MAGMA_CREAM;
         }

         if (this.potionInvisibility.isSelected()) {
            return Items.GOLDEN_CARROT;
         }

         if (this.potionRegen.isSelected()) {
            return Items.GHAST_TEAR;
         }

         if (this.potionHealing.isSelected() || this.potionStrongHealing.isSelected()) {
            return Items.GLISTERING_MELON_SLICE;
         }
      }

      if (this.potionInvisibility.isSelected() && this.allPotionSlotsAre(brew, (Potion)Potions.NIGHT_VISION.value())) {
         return Items.FERMENTED_SPIDER_EYE;
      }

      if (this.potionEnhance.isEnabled()) {
         if (this.allPotionSlotsAre(brew, (Potion)Potions.STRENGTH.value())
            || this.allPotionSlotsAre(brew, (Potion)Potions.SWIFTNESS.value())
            || this.allPotionSlotsAre(brew, (Potion)Potions.HEALING.value())) {
            return Items.GLOWSTONE_DUST;
         }

         if (this.allPotionSlotsAre(brew, (Potion)Potions.FIRE_RESISTANCE.value())
            || this.allPotionSlotsAre(brew, (Potion)Potions.REGENERATION.value())) {
            return Items.REDSTONE;
         }
      }

      return this.potionStrongHealing.isSelected() && this.allPotionSlotsAre(brew, (Potion)Potions.HEALING.value()) ? Items.GLOWSTONE_DUST : null;
   }

   private boolean isPotionComplete(BrewingStandScreenHandler brew) {
      if (this.potionStrength.isSelected()) {
         return this.allPotionSlotsAre(
            brew, this.potionEnhance.isEnabled() ? (Potion)Potions.STRONG_STRENGTH.value() : (Potion)Potions.STRENGTH.value()
         );
      } else if (this.potionSpeed.isSelected()) {
         return this.allPotionSlotsAre(
            brew, this.potionEnhance.isEnabled() ? (Potion)Potions.STRONG_SWIFTNESS.value() : (Potion)Potions.SWIFTNESS.value()
         );
      } else if (this.potionFireResistance.isSelected()) {
         return this.allPotionSlotsAre(
            brew, this.potionEnhance.isEnabled() ? (Potion)Potions.LONG_FIRE_RESISTANCE.value() : (Potion)Potions.FIRE_RESISTANCE.value()
         );
      } else if (this.potionInvisibility.isSelected()) {
         return this.allPotionSlotsAre(brew, (Potion)Potions.INVISIBILITY.value());
      } else if (this.potionRegen.isSelected()) {
         return this.allPotionSlotsAre(
            brew, this.potionEnhance.isEnabled() ? (Potion)Potions.LONG_REGENERATION.value() : (Potion)Potions.REGENERATION.value()
         );
      } else if (this.potionHealing.isSelected()) {
         return this.allPotionSlotsAre(
            brew, this.potionEnhance.isEnabled() ? (Potion)Potions.STRONG_HEALING.value() : (Potion)Potions.HEALING.value()
         );
      } else {
         return this.potionStrongHealing.isSelected() ? this.allPotionSlotsAre(brew, (Potion)Potions.STRONG_HEALING.value()) : false;
      }
   }

   private boolean fillPotionBottles(BrewingStandScreenHandler brew) {
      int limit = this.potionStackSingle.isSelected() ? 1 : 3;

      for (int i = 0; i < limit; i++) {
         if (brew.getSlot(i).getStack().isEmpty()) {
            int bottle = this.findWaterBottleSlot(brew);
            if (bottle != -1) {
               InventoryUtility.quickMove(bottle);
               this.actionTimer.reset();
               return true;
            }
         }
      }

      return false;
   }

   private int findWaterBottleSlot(BrewingStandScreenHandler brew) {
      for (int i = 5; i < brew.slots.size(); i++) {
         ItemStack stack = brew.getSlot(i).getStack();
         if (stack.getItem() == Items.POTION && this.matchesPotion(stack, (Potion)Potions.WATER.value())) {
            return i;
         }
      }

      return -1;
   }

   private void moveOneIngredient(Item item, int slot) {
      int found = InventoryUtility.findItemInContainer(item);
      if (found == -1) {
         this.warn("potion_farm.no_item_title");
      } else {
         InventoryUtility.swapOneItem(found, slot);
         this.actionTimer.reset();
      }
   }

   private boolean allPotionSlotsAre(BrewingStandScreenHandler brew, Potion potion) {
      int limit = this.potionStackSingle.isSelected() ? 1 : 3;

      for (int i = 0; i < limit; i++) {
         ItemStack stack = brew.getSlot(i).getStack();
         if (!this.isPotionStack(stack) || !this.matchesPotion(stack, potion)) {
            return false;
         }
      }

      return true;
   }

   private boolean matchesPotion(ItemStack stack, Potion potion) {
      Potion stackPotion = this.getPotion(stack);
      return stackPotion != null && stackPotion == potion;
   }

   private Potion getPotion(ItemStack stack) {
      PotionContentsComponent component = (PotionContentsComponent)stack.get(DataComponentTypes.POTION_CONTENTS);
      if (component != null && !component.potion().isEmpty()) {
         RegistryEntry<Potion> entry = (RegistryEntry<Potion>)component.potion().get();
         return (Potion)entry.value();
      } else {
         return null;
      }
   }

   private boolean isPotionStack(ItemStack stack) {
      return stack.getItem() == Items.POTION || stack.getItem() == Items.SPLASH_POTION || stack.getItem() == Items.LINGERING_POTION;
   }

   private void lootPotions(BrewingStandScreenHandler brew) {
      int limit = this.potionStackSingle.isSelected() ? 1 : 3;

      for (int i = 0; i < limit; i++) {
         if (!brew.getSlot(i).getStack().isEmpty()) {
            InventoryUtility.quickMove(i);
         }
      }

      this.actionTimer.reset();
   }

   private void depositPotionsToChest() {
      if (this.potionDepositState == AutoFarm.PotionDepositState.OPEN_CHEST) {
         if (mc.currentScreen != null) {
            mc.player.closeHandledScreen();
         } else {
            ChestBlockEntity chest = this.findBlockEntity(ChestBlockEntity.class, 5.0);
            if (chest != null && this.actionTimer.finished(300L)) {
               this.currentTarget = chest.getPos();
               this.useBlock(chest.getPos(), Direction.UP, Hand.MAIN_HAND);
               this.potionDepositState = AutoFarm.PotionDepositState.DEPOSIT;
               this.actionTimer.reset();
            } else if (chest == null) {
               this.warn("modules.crop_farm.no_chest");
               this.potionDepositState = AutoFarm.PotionDepositState.NONE;
            }
         }
      } else {
         if (mc.currentScreen instanceof GenericContainerScreen && mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler) {
            boolean moved = false;

            for (int i = handler.getInventory().size(); i < handler.slots.size(); i++) {
               ItemStack stack = handler.getSlot(i).getStack();
               if (this.isPotionStack(stack)) {
                  mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                  moved = true;
                  break;
               }
            }

            if (!moved) {
               mc.player.closeHandledScreen();
               this.potionDepositState = AutoFarm.PotionDepositState.NONE;
            }
         }
      }
   }

   private Potion selectedCombinerPotion() {
      return this.combinerSpeed.isSelected() ? (Potion)Potions.SWIFTNESS.value() : (Potion)Potions.STRENGTH.value();
   }

   private boolean moveFirstContainerItem(GenericContainerScreenHandler handler, Predicate<ItemStack> predicate) {
      for (int i = 0; i < handler.getInventory().size(); i++) {
         if (predicate.test(handler.getSlot(i).getStack())) {
            mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
            this.actionTimer.reset();
            return true;
         }
      }

      return false;
   }

   private boolean clickFirstContainerItem(GenericContainerScreenHandler handler, Predicate<ItemStack> predicate) {
      for (int i = 0; i < handler.getInventory().size(); i++) {
         if (predicate.test(handler.getSlot(i).getStack())) {
            mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, mc.player);
            this.actionTimer.reset();
            return true;
         }
      }

      return false;
   }

   private List<BlockPos> findAppleDirtBlocks() {
      BlockPos origin = mc.player.getBlockPos();
      return BlockPos.stream(origin.add(-4, -2, -4), origin.add(4, 2, 4))
         .<BlockPos>map(BlockPos::toImmutable)
         .filter(this::isAppleDirt)
         .filter(pos -> mc.player.squaredDistanceTo(pos.toCenterPos()) <= 16.0)
         .toList();
   }

   private boolean isAppleDirt(BlockPos pos) {
      Block block = mc.world.getBlockState(pos).getBlock();
      return (block == Blocks.DIRT || block == Blocks.GRASS_BLOCK) && mc.world.getBlockState(pos.up()).isAir();
   }

   private BlockPos findTreeBlock(BlockPos root) {
      return BlockPos.stream(root.add(-4, 0, -4), root.add(4, 8, 4)).<BlockPos>map(BlockPos::toImmutable).filter(pos -> {
         BlockState state = mc.world.getBlockState(pos);
         return state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES);
      }).min(Comparator.comparingDouble(pos -> mc.player.squaredDistanceTo(pos.toCenterPos()))).orElse(null);
   }

   private boolean ensureHotbarItem(Predicate<ItemStack> predicate) {
      if (predicate.test(mc.player.getMainHandStack())) {
         return true;
      } else {
         HotbarSlot hotbar = SlotGroups.hotbar().findItem(predicate);
         if (hotbar != null) {
            InventoryUtility.selectHotbarSlot(hotbar);
            return true;
         } else {
            InventorySlot inventory = SlotGroups.inventory().findItem(predicate);
            if (inventory != null) {
               inventory.swapTo(InventoryUtility.getCurrentHotbarSlot());
               return true;
            } else {
               return false;
            }
         }
      }
   }

   private boolean hasItem(Predicate<ItemStack> predicate) {
      return SlotGroups.inventory().and(SlotGroups.hotbar()).and(SlotGroups.offhand()).findItem(predicate) != null;
   }

   private int countItem(Item item) {
      return SlotGroups.inventory().and(SlotGroups.hotbar()).and(SlotGroups.offhand()).countItems(item);
   }

   private boolean isHoe(ItemStack stack) {
      return stack.getItem() == Items.WOODEN_HOE
         || stack.getItem() == Items.STONE_HOE
         || stack.getItem() == Items.IRON_HOE
         || stack.getItem() == Items.GOLDEN_HOE
         || stack.getItem() == Items.DIAMOND_HOE
         || stack.getItem() == Items.NETHERITE_HOE;
   }

   private boolean isAxeOrHoe(ItemStack stack) {
      Item item = stack.getItem();
      return this.isHoe(stack)
         || item == Items.WOODEN_AXE
         || item == Items.STONE_AXE
         || item == Items.IRON_AXE
         || item == Items.GOLDEN_AXE
         || item == Items.DIAMOND_AXE
         || item == Items.NETHERITE_AXE;
   }

   private boolean isSwordStack(ItemStack stack) {
      return stack != null && !stack.isEmpty() && stack.getItem() instanceof SwordItem;
   }

   private void useBlock(BlockPos pos, Direction direction, Hand hand) {
      Vec3d hitVec = Vec3d.ofCenter(pos)
         .add(direction.getOffsetX() * 0.5, direction.getOffsetY() * 0.5, direction.getOffsetZ() * 0.5);
      Rockstar.getInstance()
         .getRotationHandler()
         .rotate(RotationMath.getRotationTo(hitVec), MoveCorrection.SILENT, 180.0F, 180.0F, 180.0F, RotationPriority.USE_ITEM);
      mc.interactionManager.interactBlock(mc.player, hand, new BlockHitResult(hitVec, direction, pos, false));
      mc.player.swingHand(hand);
   }

   private void breakBlock(BlockPos pos) {
      Vec3d hitVec = Vec3d.ofCenter(pos);
      Direction direction = this.getHitDirection(pos);
      Rockstar.getInstance()
         .getRotationHandler()
         .rotate(RotationMath.getRotationTo(hitVec), MoveCorrection.SILENT, 180.0F, 120.0F, 180.0F, RotationPriority.NORMAL);
      mc.interactionManager.attackBlock(pos, direction);
      mc.interactionManager.updateBlockBreakingProgress(pos, direction);
      mc.player.swingHand(Hand.MAIN_HAND);
   }

   private Direction getHitDirection(BlockPos pos) {
      Vec3d eyes = mc.player.getEyePos();
      return pos.getY() > eyes.y ? Direction.DOWN : Direction.UP;
   }

   private boolean isInventoryFull() {
      return mc.player.getInventory().getEmptySlot() == -1;
   }

   private <T extends BlockEntity> T findBlockEntity(Class<T> clazz, double range) {
      BlockPos origin = mc.player.getBlockPos();
      int radius = (int)Math.ceil(range);
      T best = null;
      double bestDistance = Double.MAX_VALUE;

      for (int x = -radius; x <= radius; x++) {
         for (int y = -radius; y <= radius; y++) {
            for (int z = -radius; z <= radius; z++) {
               BlockPos pos = origin.add(x, y, z);
               BlockEntity entity = mc.world.getBlockEntity(pos);
               if (clazz.isInstance(entity)) {
                  double distance = mc.player.squaredDistanceTo(pos.toCenterPos());
                  if (distance <= range * range && distance < bestDistance) {
                     best = (T)clazz.cast(entity);
                     bestDistance = distance;
                  }
               }
            }
         }
      }

      return best;
   }

   private String sanitized(String value) {
      if (value == null) {
         return "1";
      }

      String digits = value.replaceAll("[^0-9]", "");
      return digits.isBlank() ? "1" : digits;
   }

   private void warn(String key) {
      if (this.warnTimer.finished(3000L)) {
         Rockstar.getInstance().getNotificationManager().addNotificationOther(NotificationType.ERROR, "Auto Farm", Localizator.translate(key));
         this.warnTimer.reset();
      }
   }

   private void drawTarget(Render3DEvent event, BlockPos pos, ColorRGBA color) {
      MatrixStack matrices = event.getMatrices();
      Camera camera = mc.gameRenderer.getCamera();
      Vec3d cameraPos = camera.getPos();
      matrices.push();
      matrices.translate(-cameraPos.getX(), -cameraPos.getY(), -cameraPos.getZ());
      RenderSystem.enableBlend();
      RenderSystem.disableDepthTest();
      RenderSystem.disableCull();
      RenderSystem.blendFunc(SrcFactor.SRC_ALPHA, DstFactor.ONE);
      RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
      BufferBuilder buffer = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);
      Box box = new Box(pos).expand(0.02);
      Draw3DUtility.renderFilledBox(matrices, buffer, box, color.withAlpha(35.0F));
      BuiltBuffer built = buffer.endNullable();
      if (built != null) {
         BufferRenderer.drawWithGlobalProgram(built);
      }

      RenderSystem.enableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.disableBlend();
      matrices.pop();
   }

   private boolean isApple() {
      return this.mode.is(this.apple);
   }

   private boolean isSword() {
      return this.mode.is(this.sword);
   }

   private boolean isPotion() {
      return this.mode.is(this.potion);
   }

   private boolean isPotionCombiner() {
      return this.mode.is(this.potionCombiner);
   }

   private boolean isAutoSell() {
      return this.mode.is(this.autoSell);
   }

   private boolean isCrop() {
      return this.mode.is(this.crop);
   }

   @Generated
   public ModeSetting getMode() {
      return this.mode;
   }

   private enum PotionDepositState {
      NONE,
      OPEN_CHEST,
      DEPOSIT;
   }
}
