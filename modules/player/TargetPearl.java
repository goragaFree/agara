package moscow.rockstar.systems.modules.modules.player;

import moscow.rockstar.Rockstar;
import moscow.rockstar.systems.event.EventListener;
import moscow.rockstar.systems.event.impl.player.ClientPlayerTickEvent;
import moscow.rockstar.systems.modules.api.ModuleCategory;
import moscow.rockstar.systems.modules.api.ModuleInfo;
import moscow.rockstar.systems.modules.impl.BaseModule;
import moscow.rockstar.systems.setting.settings.BooleanSetting;
import moscow.rockstar.systems.setting.settings.SliderSetting;
import moscow.rockstar.utility.inventory.InventoryUtility;
import moscow.rockstar.utility.inventory.ItemSlot;
import moscow.rockstar.utility.inventory.group.SlotGroups;
import moscow.rockstar.utility.inventory.slots.HotbarSlot;
import moscow.rockstar.utility.rotations.MoveCorrection;
import moscow.rockstar.utility.rotations.Rotation;
import moscow.rockstar.utility.rotations.RotationHandler;
import moscow.rockstar.utility.rotations.RotationPriority;
import moscow.rockstar.utility.time.Timer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

@ModuleInfo(name = "Target Pearl", category = ModuleCategory.PLAYER, desc = "modules.descriptions.target_pearl")
public class TargetPearl extends BaseModule {
   private final SliderSetting trackRange = new SliderSetting(this, "modules.settings.target_pearl.track_range")
      .min(10.0F)
      .max(160.0F)
      .step(1.0F)
      .currentValue(80.0F)
      .suffix(value -> " m");
   private final SliderSetting minLanding = new SliderSetting(this, "modules.settings.target_pearl.min_landing")
      .min(50.0F)
      .max(120.0F)
      .step(1.0F)
      .currentValue(50.0F)
      .suffix(value -> " m");
   private final SliderSetting maxLanding = new SliderSetting(this, "modules.settings.target_pearl.max_landing")
      .min(50.0F)
      .max(160.0F)
      .step(1.0F)
      .currentValue(100.0F)
      .suffix(value -> " m");
   private final SliderSetting aimSpeed = new SliderSetting(this, "modules.settings.target_pearl.aim_speed")
      .min(40.0F)
      .max(180.0F)
      .step(5.0F)
      .currentValue(180.0F);
   private final SliderSetting maxAngle = new SliderSetting(this, "modules.settings.target_pearl.max_angle")
      .min(0.5F)
      .max(30.0F)
      .step(0.5F)
      .currentValue(8.0F)
      .suffix(value -> "°");
   private final SliderSetting cooldown = new SliderSetting(this, "modules.settings.target_pearl.cooldown")
      .min(0.0F)
      .max(1000.0F)
      .step(25.0F)
      .currentValue(50.0F)
      .suffix(value -> " ms");
   private final BooleanSetting onlyAuraTarget = new BooleanSetting(this, "modules.settings.target_pearl.only_aura_target").enable();
   private final BooleanSetting ownPearls = new BooleanSetting(this, "modules.settings.target_pearl.own_pearls");
   private final BooleanSetting onlyHolding = new BooleanSetting(this, "modules.settings.target_pearl.only_holding");
   private final Timer cooldownTimer = new Timer();
   private int handledPearlId = -1;
   private final EventListener<ClientPlayerTickEvent> onUpdateEvent = event -> {
      if (mc.player != null && mc.world != null && mc.interactionManager != null && mc.getNetworkHandler() != null) {
         if (mc.currentScreen == null && !mc.player.isUsingItem() && !(mc.player.getHealth() < 5.0F)) {
            if (!this.onlyHolding.isEnabled()
               || mc.player.getMainHandStack().isOf(Items.ENDER_PEARL)
               || mc.player.getOffHandStack().isOf(Items.ENDER_PEARL)) {
               if (this.hasPearl()) {
                  EnderPearlEntity pearl = this.findPearlToFollow();
                  if (pearl != null) {
                     Vec3d landing = this.predictPearlLanding(pearl);
                     if (landing != null) {
                        double landingDistance = mc.player.getEyePos().distanceTo(landing);
                        float minDistance = this.minLanding.getCurrentValue();
                        float maxDistance = Math.max(minDistance, this.maxLanding.getCurrentValue());
                        if (!(landingDistance < minDistance) && !(landingDistance > maxDistance)) {
                           Rotation rotation = this.calculateThrowRotation(landing);
                           if (rotation != null) {
                              RotationHandler rotationHandler = Rockstar.getInstance().getRotationHandler();
                              float speed = this.aimSpeed.getCurrentValue();
                              rotationHandler.rotate(rotation, MoveCorrection.SILENT, speed, speed, speed, RotationPriority.USE_ITEM);
                              if (this.cooldownTimer.finished((long)this.cooldown.getCurrentValue())) {
                                 Rotation currentRotation = rotationHandler.isIdling()
                                    ? rotationHandler.getPlayerRotation()
                                    : rotationHandler.getCurrentRotation();
                                 if (!(currentRotation.differenceValue(rotation) > this.maxAngle.getCurrentValue())) {
                                    if (this.throwPearl(rotation)) {
                                       this.handledPearlId = pearl.getId();
                                       this.cooldownTimer.reset();
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   };

   @Override
   public void onEnable() {
      this.cooldownTimer.reset();
      this.handledPearlId = -1;
   }

   private boolean hasPearl() {
      return SlotGroups.hotbar().and(SlotGroups.inventory()).findItem(Items.ENDER_PEARL) != null
         || mc.player.getOffHandStack().isOf(Items.ENDER_PEARL);
   }

   private EnderPearlEntity findPearlToFollow() {
      EnderPearlEntity bestPearl = null;
      double bestDistance = Double.MAX_VALUE;
      double maxDistanceSq = this.trackRange.getCurrentValue() * this.trackRange.getCurrentValue();
      Entity auraTarget = Rockstar.getInstance().getTargetManager().getCurrentTarget();

      for (Entity entity : mc.world.getEntities()) {
         if (entity instanceof EnderPearlEntity pearl && pearl.getId() != this.handledPearlId && !pearl.isRemoved()) {
            Entity owner = this.resolvePearlOwner(pearl);
            if ((this.ownPearls.isEnabled() || owner != mc.player) && (!this.onlyAuraTarget.isEnabled() || auraTarget != null && owner == auraTarget)) {
               double distanceSq = pearl.squaredDistanceTo(mc.player);
               if (!(distanceSq > maxDistanceSq) && !(distanceSq >= bestDistance)) {
                  bestDistance = distanceSq;
                  bestPearl = pearl;
               }
            }
         }
      }

      return bestPearl;
   }

   private Entity resolvePearlOwner(EnderPearlEntity pearl) {
      Entity owner = pearl.getOwner();
      if (owner != null) {
         return owner;
      }

      PlayerEntity nearest = null;
      double bestDistance = Double.MAX_VALUE;

      for (PlayerEntity player : mc.world.getPlayers()) {
         double distance = player.squaredDistanceTo(pearl);
         if (distance < bestDistance) {
            bestDistance = distance;
            nearest = player;
         }
      }

      return nearest;
   }

   private Vec3d predictPearlLanding(EnderPearlEntity pearl) {
      Vec3d pos = pearl.getPos();
      Vec3d velocity = pearl.getVelocity();

      for (int i = 0; i < 200; i++) {
         Vec3d nextPos = pos.add(velocity);
         BlockHitResult hit = mc.world.raycast(new RaycastContext(pos, nextPos, ShapeType.COLLIDER, FluidHandling.NONE, pearl));
         if (hit != null && hit.getType() == Type.BLOCK) {
            return hit.getPos();
         }

         velocity = velocity.multiply(0.99).add(0.0, -0.03, 0.0);
         pos = nextPos;
         if (pos.y < mc.world.getBottomY() - 16.0) {
            return null;
         }
      }

      return null;
   }

   private Rotation calculateThrowRotation(Vec3d landing) {
      Vec3d eyes = mc.player.getEyePos();
      double dx = landing.x - eyes.x;
      double dy = landing.y - eyes.y;
      double dz = landing.z - eyes.z;
      double horizontal = Math.hypot(dx, dz);
      if (horizontal < 0.001) {
         return null;
      }

      Float pitch = this.solvePitch(horizontal, dy);
      if (pitch == null) {
         return null;
      }

      float yaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
      return new Rotation(yaw, pitch);
   }

   private Float solvePitch(double horizontalDistance, double verticalDistance) {
      float bestPitch = Float.NaN;
      double bestError = Double.MAX_VALUE;

      for (float pitch = -89.0F; pitch <= 60.0F; pitch++) {
         Double y = this.heightAtDistance(horizontalDistance, pitch);
         if (y != null) {
            double error = Math.abs(y - verticalDistance);
            if (error < bestError) {
               bestError = error;
               bestPitch = pitch;
            }
         }
      }

      if (Float.isNaN(bestPitch)) {
         return null;
      }

      for (float pitch = bestPitch - 1.0F; pitch <= bestPitch + 1.0F; pitch += 0.05F) {
         Double y = this.heightAtDistance(horizontalDistance, pitch);
         if (y != null) {
            double error = Math.abs(y - verticalDistance);
            if (error < bestError) {
               bestError = error;
               bestPitch = pitch;
            }
         }
      }

      return bestError > 1.5 ? null : MathHelper.clamp(bestPitch, -90.0F, 90.0F);
   }

   private Double heightAtDistance(double horizontalDistance, float pitchDegrees) {
      double pitch = Math.toRadians(pitchDegrees);
      double velocityX = Math.cos(pitch) * 1.5;
      double velocityY = -Math.sin(pitch) * 1.5;
      if (velocityX <= 1.0E-4) {
         return null;
      }

      double travelled = 0.0;
      double y = 0.0;

      for (int i = 0; i < 200; i++) {
         double prevTravelled = travelled;
         double prevY = y;
         travelled += velocityX;
         y += velocityY;
         if (travelled >= horizontalDistance) {
            double partial = (horizontalDistance - prevTravelled) / velocityX;
            return prevY + velocityY * partial;
         }

         velocityX *= 0.99;
         velocityY = velocityY * 0.99 - 0.03;
      }

      return null;
   }

   private boolean throwPearl(Rotation rotation) {
      if (mc.player.getOffHandStack().isOf(Items.ENDER_PEARL)) {
         this.sendUsePearlPacket(Hand.OFF_HAND, rotation);
         return true;
      }

      if (mc.player.getMainHandStack().isOf(Items.ENDER_PEARL)) {
         this.sendUsePearlPacket(Hand.MAIN_HAND, rotation);
         return true;
      }

      int previousSlot = mc.player.getInventory().selectedSlot;
      HotbarSlot hotbarSlot = SlotGroups.hotbar().findItem(Items.ENDER_PEARL);
      if (hotbarSlot != null) {
         this.usePearlHotbarSlot(hotbarSlot.getSlotId(), previousSlot, rotation);
         return true;
      }

      ItemSlot inventorySlot = SlotGroups.inventory().findItem(Items.ENDER_PEARL);
      if (inventorySlot == null) {
         return false;
      }

      int temporarySlot = this.findTemporaryHotbarSlot(previousSlot);
      int syncId = mc.player.currentScreenHandler.syncId;
      mc.interactionManager.clickSlot(syncId, inventorySlot.getIdForServer(), temporarySlot, SlotActionType.SWAP, mc.player);
      this.usePearlHotbarSlot(temporarySlot, previousSlot, rotation);
      mc.interactionManager.clickSlot(syncId, inventorySlot.getIdForServer(), temporarySlot, SlotActionType.SWAP, mc.player);
      return true;
   }

   private void usePearlHotbarSlot(int pearlSlot, int previousSlot, Rotation rotation) {
      if (pearlSlot != previousSlot) {
         InventoryUtility.selectHotbarSlot(pearlSlot);
      }

      this.sendUsePearlPacket(Hand.MAIN_HAND, rotation);
      if (pearlSlot != previousSlot) {
         InventoryUtility.selectHotbarSlot(previousSlot);
      }
   }

   private void sendUsePearlPacket(Hand hand, Rotation rotation) {
      mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(hand, 0, rotation.getYaw(), rotation.getPitch()));
      mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));
   }

   private int findTemporaryHotbarSlot(int previousSlot) {
      for (int slot = 0; slot < 9; slot++) {
         if (slot != previousSlot && mc.player.getInventory().getStack(slot).isEmpty()) {
            return slot;
         }
      }

      return previousSlot == 8 ? 7 : 8;
   }
}
