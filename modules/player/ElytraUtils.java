package moscow.rockstar.systems.modules.modules.player;

import java.util.List;
import moscow.rockstar.Rockstar;
import moscow.rockstar.mixin.accessors.ClientPlayerInteractionManagerAccessor;
import moscow.rockstar.mixin.minecraft.client.input.InputAccessor;
import moscow.rockstar.systems.event.EventListener;
import moscow.rockstar.systems.event.impl.game.FireworkEvent;
import moscow.rockstar.systems.event.impl.network.ReceivePacketEvent;
import moscow.rockstar.systems.event.impl.player.ClientPlayerTickEvent;
import moscow.rockstar.systems.event.impl.window.KeyPressEvent;
import moscow.rockstar.systems.event.impl.window.MouseEvent;
import moscow.rockstar.systems.modules.api.ModuleCategory;
import moscow.rockstar.systems.modules.api.ModuleInfo;
import moscow.rockstar.systems.modules.impl.BaseModule;
import moscow.rockstar.systems.modules.modules.combat.ElytraTarget;
import moscow.rockstar.systems.modules.modules.movement.AutoSprint;
import moscow.rockstar.systems.setting.settings.BindSetting;
import moscow.rockstar.systems.setting.settings.BooleanSetting;
import moscow.rockstar.systems.setting.settings.ModeSetting;
import moscow.rockstar.utility.game.prediction.ElytraPredictionSystem;
import moscow.rockstar.utility.inventory.InventoryUtility;
import moscow.rockstar.utility.inventory.ItemSlot;
import moscow.rockstar.utility.rotations.Rotation;
import moscow.rockstar.utility.rotations.RotationHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

@ModuleInfo(name = "Elytra Utils", category = ModuleCategory.PLAYER, desc = "Помощник с элитрами")
public class ElytraUtils extends BaseModule {
   private static final int GUI_MOVE_SWAP_HOLD_TICKS = 2;
   private static final List<Item> CHESTPLATES = List.of(
      Items.LEATHER_CHESTPLATE, Items.CHAINMAIL_CHESTPLATE, Items.GOLDEN_CHESTPLATE, Items.IRON_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.NETHERITE_CHESTPLATE
   );
   private final BindSetting swapKey = new BindSetting(this, "Клавиша свапа");
   private final BindSetting fireworkKey = new BindSetting(this, "Клавиша фейерверка");
   private final ModeSetting fireworkMode = new ModeSetting(this, "modules.settings.elytra_utils.firework_mode");
   private final ModeSetting.Value fireworkHvh = new ModeSetting.Value(this.fireworkMode, "modules.settings.elytra_utils.firework_mode.hvh").select();
   private final ModeSetting.Value fireworkLegit = new ModeSetting.Value(this.fireworkMode, "modules.settings.elytra_utils.firework_mode.legit");
   private final BooleanSetting automat = new BooleanSetting(this, "Авто взлёт", "Автоматически взлетает на элитрах").enable();
   private final BooleanSetting withUse = new BooleanSetting(
         this, "Авто использование", "Автоматически использует фейерверк для взлета", () -> !this.automat.isEnabled()
      )
      .enable();
   private final BooleanSetting unEquip = new BooleanSetting(this, "Грудак на земле", "Автоматически надевает нагрудник или снимает элитры при приземлении")
      .enable();
   private final BooleanSetting boost = new BooleanSetting(this, "Ускорять", "Ускоряет движение на элитре");
   private boolean wasFlying;
   private boolean swap;
   private boolean useFirework;
   private int guiMoveHoldTicks;
   private final EventListener<ClientPlayerTickEvent> onUpdate = event -> {
      if (mc.player != null && mc.world != null && mc.interactionManager != null && mc.getNetworkHandler() != null) {
         this.tickGuiMoveHold();
         if (mc.player.isGliding()) {
            this.wasFlying = true;
         }

         ItemSlot chestplateSlot = InventoryUtility.getChestplateSlot();
         boolean isElytraEquipped = chestplateSlot.item() == Items.ELYTRA;
         if (this.swap) {
            this.processElytraSwap();
         } else if (this.useFirework) {
            this.processFireworkUse();
         } else {
            if (this.automat.isEnabled() && isElytraEquipped && !mc.player.isGliding() && !mc.player.isOnGround() && !mc.player.isInFluid()) {
               mc.player.startGliding();
               mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, Mode.START_FALL_FLYING));
               if (this.withUse.isEnabled() && this.hasItem(Items.FIREWORK_ROCKET)) {
                  this.useFirework = true;
               }
            } else if (mc.player.isOnGround() && this.automat.isEnabled() && isElytraEquipped && !mc.player.isInFluid()) {
               mc.player.jump();
            }

            if (this.unEquip.isEnabled() && mc.player.isOnGround() && isElytraEquipped && this.wasFlying && mc.player.getGlidingTicks() > 18) {
               if (!this.waitGuiMoveSync()) {
                  return;
               }

               this.beginGuiMoveSwap();

               try {
                  if (this.findItem(CHESTPLATES, 0, 35) != -1) {
                     this.swapChestSlot(CHESTPLATES);
                  } else {
                     mc.interactionManager.clickSlot(this.currentSyncId(), 6, 0, SlotActionType.QUICK_MOVE, mc.player);
                     mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(this.currentSyncId()));
                  }
               } finally {
                  this.endGuiMoveSwap();
               }

               this.wasFlying = false;
            }
         }
      }
   };
   private final EventListener<KeyPressEvent> onKeyPressEvent = event -> {
      if (this.swapKey.isKey(event.getKey()) && event.getAction() == 1 && mc.currentScreen == null) {
         this.swap = true;
      }

      if (this.fireworkKey.isKey(event.getKey()) && event.getAction() == 1 && mc.currentScreen == null && mc.player != null && mc.player.isGliding()) {
         this.useFirework = true;
      }
   };
   private final EventListener<MouseEvent> onMouseButtonPress = event -> {
      if (this.swapKey.isKey(event.getButton()) && event.getAction() == 1 && mc.currentScreen == null) {
         this.swap = true;
      }

      if (this.fireworkKey.isKey(event.getButton()) && event.getAction() == 1 && mc.currentScreen == null && mc.player != null && mc.player.isGliding()) {
         this.useFirework = true;
      }
   };
   private final EventListener<FireworkEvent> onFirework = event -> {
      if (this.boost.isEnabled() && event.getEntity() == mc.player) {
         if (Rockstar.getInstance().getTargetManager().getLivingTarget() instanceof PlayerEntity player && !ElytraPredictionSystem.isLeaving(player)) {
         }

         double boostPower = 1.5 * this.getAdvancedBoost();
         RotationHandler rotationHandler = Rockstar.getInstance().getRotationHandler();
         Vec3d rotationVector = rotationHandler.isIdling()
            ? rotationHandler.getPlayerRotation().getRotationVector()
            : rotationHandler.getCurrentRotation().getRotationVector();
         Vec3d currentVelocity = event.getVelocity();
         Vec3d newVelocity = currentVelocity.add(
            rotationVector.x * 0.1 + (rotationVector.x * boostPower - currentVelocity.x) * 0.5,
            rotationVector.y * 0.1 + (rotationVector.y * boostPower - currentVelocity.y) * 0.5,
            rotationVector.z * 0.1 + (rotationVector.z * boostPower - currentVelocity.z) * 0.5
         );
         event.setVelocity(newVelocity);
      }
   };
   private final EventListener<ReceivePacketEvent> onReceivePacketEvent = event -> {
      if (event.getPacket() instanceof PlayerPositionLookS2CPacket) {
         RotationHandler rotationHandler = Rockstar.getInstance().getRotationHandler();
         Rotation rot = rotationHandler.isIdling() ? rotationHandler.getPlayerRotation() : rotationHandler.getCurrentRotation();
         System.out.println(String.format("ELYTRA BOOSTER HUETA. ANGLES: yaw(%s) pitch(%s) speed(%s)", rot.getYaw(), rot.getPitch(), this.getAdvancedBoost()));
      }
   };

   private boolean processElytraSwap() {
      if (!this.waitGuiMoveSync()) {
         return false;
      }

      this.beginGuiMoveSwap();

      try {
         if (mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
            this.swapChestSlotByMainHand(CHESTPLATES);
         } else {
            this.swapChestSlotByMainHand(List.of(Items.ELYTRA));
         }
      } finally {
         this.endGuiMoveSwap();
         this.swap = false;
      }

      return true;
   }

   private boolean processFireworkUse() {
      if (!this.waitGuiMoveSync()) {
         return false;
      }

      this.beginGuiMoveSwap();

      try {
         this.useFirework();
      } finally {
         this.endGuiMoveSwap();
         this.useFirework = false;
      }

      return true;
   }

   private void swapChestSlotByMainHand(List<Item> targetItems) {
      int previousSlot = mc.player.getInventory().selectedSlot;
      int hotbarSlot = this.findItem(targetItems, 0, 8);
      if (hotbarSlot != -1) {
         this.useChestItemFromHotbar(hotbarSlot, previousSlot);
      } else {
         int inventorySlot = this.findItem(targetItems, 9, 35);
         if (inventorySlot != -1) {
            int swapHotbarSlot = this.findTemporaryHotbarSlot(previousSlot);
            this.swapInventorySlotWithHotbar(inventorySlot, swapHotbarSlot);
            this.useChestItemFromHotbar(swapHotbarSlot, previousSlot);
            this.swapInventorySlotWithHotbar(inventorySlot, swapHotbarSlot);
         }
      }
   }

   private void useChestItemFromHotbar(int itemSlot, int previousSlot) {
      if (!mc.player.getInventory().getStack(itemSlot).isEmpty()) {
         boolean wasSprinting = this.stopSprintForSwap();
         InventoryUtility.selectHotbarSlot(itemSlot);
         mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
         InventoryUtility.selectHotbarSlot(previousSlot);
         this.restoreInputAfterSwap(wasSprinting);
      }
   }

   private void swapChestSlot(List<Item> targetItems) {
      int hotbarSlot = this.findItem(targetItems, 0, 8);
      if (hotbarSlot != -1) {
         boolean wasSprinting = this.stopSprintForSwap();
         mc.interactionManager.clickSlot(this.currentSyncId(), 6, hotbarSlot, SlotActionType.SWAP, mc.player);
         mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(this.currentSyncId()));
         this.restoreInputAfterSwap(wasSprinting);
      } else {
         int inventorySlot = this.findItem(targetItems, 9, 35);
         if (inventorySlot != -1) {
            boolean wasSprinting = this.stopSprintForSwap();
            int syncId = this.currentSyncId();
            mc.interactionManager.clickSlot(syncId, inventorySlot, 8, SlotActionType.SWAP, mc.player);
            mc.interactionManager.clickSlot(syncId, 6, 8, SlotActionType.SWAP, mc.player);
            mc.interactionManager.clickSlot(syncId, inventorySlot, 8, SlotActionType.SWAP, mc.player);
            mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(syncId));
            this.restoreInputAfterSwap(wasSprinting);
         }
      }
   }

   private void useFirework() {
      if (InventoryUtility.hasItemInOffHand(Items.FIREWORK_ROCKET)) {
         mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
      } else {
         if (this.fireworkLegit.isSelected()) {
            this.useFireworkLegit();
         } else {
            this.useFireworkHvh();
         }
      }
   }

   private void useFireworkHvh() {
      int previousSlot = mc.player.getInventory().selectedSlot;
      int hotbarSlot = this.findItem(Items.FIREWORK_ROCKET, 0, 8);
      if (hotbarSlot != -1) {
         this.useFireworkSlotPacket(hotbarSlot, previousSlot);
      } else {
         int inventorySlot = this.findItem(Items.FIREWORK_ROCKET, 9, 35);
         if (inventorySlot != -1) {
            int swapHotbarSlot = this.findTemporaryHotbarSlot(previousSlot);
            this.swapInventorySlotWithHotbar(inventorySlot, swapHotbarSlot);
            this.useFireworkSlotPacket(swapHotbarSlot, previousSlot);
            this.swapInventorySlotWithHotbar(inventorySlot, swapHotbarSlot);
         }
      }
   }

   private void useFireworkLegit() {
      int previousSlot = mc.player.getInventory().selectedSlot;
      int hotbarSlot = this.findItem(Items.FIREWORK_ROCKET, 0, 8);
      if (hotbarSlot != -1) {
         this.useFireworkSlotLegit(hotbarSlot, previousSlot);
      } else {
         int inventorySlot = this.findItem(Items.FIREWORK_ROCKET, 9, 35);
         if (inventorySlot != -1) {
            int swapHotbarSlot = this.findTemporaryHotbarSlot(previousSlot);
            this.swapInventorySlotWithHotbar(inventorySlot, swapHotbarSlot);
            this.useFireworkSlotLegit(swapHotbarSlot, previousSlot);
            this.swapInventorySlotWithHotbar(inventorySlot, swapHotbarSlot);
         }
      }
   }

   private void useFireworkSlotPacket(int fireworkSlot, int previousSlot) {
      if (!mc.player.getInventory().getStack(fireworkSlot).isEmpty()) {
         if (fireworkSlot != previousSlot) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(fireworkSlot));
         }

         ((ClientPlayerInteractionManagerAccessor)mc.interactionManager)
            .invokeSendSequencedPacket(
               mc.world, sequence -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, sequence, mc.player.getYaw(), mc.player.getPitch())
            );
         if (fireworkSlot != previousSlot) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
         }
      }
   }

   private void useFireworkSlotLegit(int fireworkSlot, int previousSlot) {
      if (!mc.player.getInventory().getStack(fireworkSlot).isEmpty()) {
         InventoryUtility.selectHotbarSlot(fireworkSlot);
         mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
         InventoryUtility.selectHotbarSlot(previousSlot);
      }
   }

   private void swapInventorySlotWithHotbar(int inventorySlot, int hotbarSlot) {
      boolean wasSprinting = this.stopSprintForSwap();
      int syncId = this.currentSyncId();
      mc.interactionManager.clickSlot(syncId, inventorySlot, hotbarSlot, SlotActionType.SWAP, mc.player);
      mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(syncId));
      this.restoreInputAfterSwap(wasSprinting);
   }

   private int findTemporaryHotbarSlot(int previousSlot) {
      for (int slot = 0; slot < 9; slot++) {
         if (slot != previousSlot && mc.player.getInventory().getStack(slot).isEmpty()) {
            return slot;
         }
      }

      return previousSlot == 8 ? 7 : 8;
   }

   private int findItem(Item item, int from, int to) {
      for (int slot = from; slot <= to; slot++) {
         if (mc.player.getInventory().getStack(slot).isOf(item)) {
            return slot;
         }
      }

      return -1;
   }

   private int findItem(List<Item> items, int from, int to) {
      for (int slot = from; slot <= to; slot++) {
         ItemStack stack = mc.player.getInventory().getStack(slot);
         if (!stack.isEmpty() && items.contains(stack.getItem())) {
            return slot;
         }
      }

      return -1;
   }

   private boolean hasItem(Item item) {
      return this.findItem(item, 0, 35) != -1;
   }

   private boolean stopSprintForSwap() {
      if (!mc.player.isSprinting()) {
         return false;
      }

      mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(new PlayerInput(false, false, false, false, false, false, false)));
      mc.player.setSprinting(false);
      mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, Mode.STOP_SPRINTING));
      if (!Rockstar.getInstance().getModuleManager().getModule(AutoSprint.class).isEnabled()) {
         mc.options.sprintKey.setPressed(false);
      }

      return true;
   }

   private void restoreInputAfterSwap(boolean wasSprinting) {
      if (wasSprinting) {
         mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(((InputAccessor)mc.player.input).getInput()));
      }
   }

   private boolean waitGuiMoveSync() {
      GuiMove guiMove = Rockstar.getInstance().getModuleManager().getModule(GuiMove.class);
      if (guiMove.isEnabled() && guiMove.slowing()) {
         this.holdGuiMoveInput(guiMove);
      }

      return true;
   }

   private void beginGuiMoveSwap() {
      GuiMove guiMove = Rockstar.getInstance().getModuleManager().getModule(GuiMove.class);
      if (guiMove.isEnabled() && guiMove.slowing()) {
         this.holdGuiMoveInput(guiMove);
         guiMove.setSending(true);
      }
   }

   private void endGuiMoveSwap() {
      GuiMove guiMove = Rockstar.getInstance().getModuleManager().getModule(GuiMove.class);
      if (guiMove.isEnabled() && guiMove.slowing()) {
         guiMove.setSending(false);
      }
   }

   private void tickGuiMoveHold() {
      if (this.guiMoveHoldTicks > 0) {
         GuiMove guiMove = Rockstar.getInstance().getModuleManager().getModule(GuiMove.class);
         if (guiMove.isEnabled() && guiMove.slowing()) {
            this.guiMoveHoldTicks--;
            if (this.guiMoveHoldTicks <= 0) {
               guiMove.setStay(false);
            }
         } else {
            this.guiMoveHoldTicks = 0;
            guiMove.setSending(false);
         }
      }
   }

   private void holdGuiMoveInput(GuiMove guiMove) {
      guiMove.setStay(true);
      this.guiMoveHoldTicks = 2;
      if (mc.player != null) {
         InputAccessor input = (InputAccessor)mc.player.input;
         input.setMovementForward(0.0F);
         input.setMovementSideways(0.0F);
         input.setInput(new PlayerInput(false, false, false, false, false, false, false));
      }
   }

   private int currentSyncId() {
      return 0;
   }

   private double getAdvancedBoost() {
      if (Rockstar.getInstance().getModuleManager().getModule(ElytraTarget.class).isDefensiveActive()) {
      }

      RotationHandler rotationHandler = Rockstar.getInstance().getRotationHandler();
      Rotation rot = rotationHandler.isIdling() ? rotationHandler.getPlayerRotation() : rotationHandler.getCurrentRotation();
      float playerYaw = rot.getYaw();
      float playerPitch = rot.getPitch();
      double A = 0.239037;
      double B = 4.489648;
      double C = 1.236087;
      double MAX_ACCELERATION_YAW = 1.47;
      double YAW_TOLERANCE = 7.9;
      double MAX_PITCH_BOOST = 1.01;
      double MAX_PITCH = -45.0;
      double MIN_PITCH = 10.0;
      double effectiveYaw = Math.abs(playerYaw) % 90.0;
      double yawAcceleration;
      if (Math.abs(effectiveYaw - 45.0) <= 7.9) {
         yawAcceleration = 1.47;
      } else {
         double argument = 4.489648 * (effectiveYaw - 45.0);
         yawAcceleration = 0.239037 * Math.cos(Math.toRadians(argument)) + 1.236087;
      }

      if (playerPitch >= 10.0F) {
         return Math.abs(effectiveYaw - 45.0) <= 5.0 ? 1.8 : yawAcceleration;
      }

      if (playerPitch >= 0.0F) {
         return 1.0;
      }

      if (playerPitch < -80.0F) {
         return 1.0;
      }

      double pitchRatio = Math.min(1.0, Math.abs(playerPitch) / Math.abs(-45.0));
      double pitchMultiplier = 1.0 + 0.010000000000000009 * pitchRatio;
      double totalAcceleration = yawAcceleration * pitchMultiplier;
      return Math.min(totalAcceleration, 1.49);
   }

   private static int findClosestVector(float lastYaw, int[] vectors) {
      int index = 0;
      int minDistIndex = -1;
      float minDist = Float.MAX_VALUE;

      for (int vector : vectors) {
         float dist = Math.abs(MathHelper.wrapDegrees(lastYaw) - vector);
         if (dist < minDist) {
            minDist = dist;
            minDistIndex = index;
         }

         index++;
      }

      return minDistIndex;
   }

   private double calculateDynamicBoostPower(LivingEntity player) {
      float yaw = player.getYaw();
      float pitch = player.getPitch();
      double minSpeed = 1.4;
      double maxSpeed = 1.9;
      double yawFactor = this.calculateYawFactor(yaw);
      double pitchFactor = this.calculatePitchFactor(pitch);
      double combinedFactor = yawFactor * pitchFactor;
      double boostPower = minSpeed + (maxSpeed - minSpeed) * combinedFactor;
      return Math.max(minSpeed, Math.min(maxSpeed, boostPower));
   }

   private double calculateYawFactor(float yaw) {
      yaw = (yaw % 360.0F + 360.0F) % 360.0F;
      double[] diagonalAngles = new double[]{45.0, 135.0, 225.0, 315.0};
      double minDistanceToDiagonal = Double.MAX_VALUE;

      for (double diagonal : diagonalAngles) {
         double distance = Math.min(Math.abs(yaw - diagonal), Math.min(Math.abs(yaw - diagonal + 360.0), Math.abs(yaw - diagonal - 360.0)));
         minDistanceToDiagonal = Math.min(minDistanceToDiagonal, distance);
      }

      return minDistanceToDiagonal <= 45.0 ? 1.0 - minDistanceToDiagonal / 45.0 * 0.85 : 0.15;
   }

   private double calculatePitchFactor(float pitch) {
      float absPitch = Math.abs(pitch);
      if (absPitch <= 10.0F) {
         return 1.0;
      } else if (absPitch <= 30.0F) {
         return 1.0 - (absPitch - 10.0F) / 20.0 * 0.3;
      } else {
         return absPitch <= 60.0F ? 0.7 - (absPitch - 30.0F) / 30.0 * 0.4 : 0.3;
      }
   }

   @Override
   public void onDisable() {
      this.wasFlying = false;
      this.swap = false;
      this.useFirework = false;
      this.resetGuiMoveSync();
   }

   @Override
   public void onEnable() {
      this.wasFlying = false;
      this.swap = false;
      this.useFirework = false;
      this.guiMoveHoldTicks = 0;
   }

   private void resetGuiMoveSync() {
      this.guiMoveHoldTicks = 0;
      GuiMove guiMove = Rockstar.getInstance().getModuleManager().getModule(GuiMove.class);
      guiMove.setSending(false);
      guiMove.setStay(false);
   }
}
