package moscow.rockstar.systems.modules.modules.movement;

import moscow.rockstar.systems.event.EventListener;
import moscow.rockstar.systems.event.impl.player.ClientPlayerTickEvent;
import moscow.rockstar.systems.modules.api.ModuleCategory;
import moscow.rockstar.systems.modules.api.ModuleInfo;
import moscow.rockstar.systems.modules.impl.BaseModule;
import moscow.rockstar.systems.setting.settings.BooleanSetting;
import moscow.rockstar.systems.setting.settings.SliderSetting;
import moscow.rockstar.utility.game.EntityUtility;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.MathHelper;

@ModuleInfo(name = "Strafe", category = ModuleCategory.MOVEMENT, desc = "modules.descriptions.strafe")
public class Strafe extends BaseModule {
   private final BooleanSetting autoJump = new BooleanSetting(this, "modules.settings.strafe.auto_jump");
   private final BooleanSetting damageBoost = new BooleanSetting(this, "modules.settings.strafe.damage_boost");
   private final SliderSetting damageSpeed = new SliderSetting(this, "modules.settings.strafe.damage_speed", () -> !this.damageBoost.isEnabled())
      .currentValue(0.4F)
      .min(0.05F)
      .max(0.8F)
      .step(0.01F);
   private double lastSpeed;
   private int airTicks;
   private int groundTicks;
   private final EventListener<ClientPlayerTickEvent> onClientTick = event -> this.updateStrafe();

   @Override
   public void onDisable() {
      this.resetSpeedState();
      super.onDisable();
   }

   private void updateStrafe() {
      if (mc.player != null && mc.world != null) {
         if (EntityUtility.isPlayerMoving()
            && !mc.player.isGliding()
            && !mc.player.isClimbing()
            && !mc.player.isTouchingWater()
            && !mc.player.isInLava()) {
            if (this.autoJump.isEnabled() && mc.player.isOnGround() && !mc.options.jumpKey.isPressed()) {
               mc.player.jump();
            }

            double speed = this.calculateSpeed();
            if (!(speed <= 0.0)) {
               this.applySpeed(speed);
               this.lastSpeed = Math.hypot(mc.player.getVelocity().x, mc.player.getVelocity().z);
            }
         } else {
            this.resetSpeedState();
         }
      }
   }

   private double calculateSpeed() {
      double baseSpeed = this.getBaseMoveSpeed();
      boolean boosted = this.damageBoost.isEnabled() && mc.player.hurtTime > 0;
      if (mc.player.isOnGround()) {
         this.groundTicks++;
         this.airTicks = 0;
         return Math.max(baseSpeed, boosted ? this.damageSpeed.getCurrentValue() : 0.42);
      } else {
         this.airTicks++;
         this.groundTicks = 0;
         double decay = this.lastSpeed - this.lastSpeed / 159.0;
         double boost = boosted ? this.damageSpeed.getCurrentValue() : 0.0;
         return Math.max(baseSpeed, Math.max(decay, baseSpeed + boost * 0.35));
      }
   }

   private double getBaseMoveSpeed() {
      double speed = 0.2873;
      if (mc.player.hasStatusEffect(StatusEffects.SPEED)) {
         int amplifier = mc.player.getStatusEffect(StatusEffects.SPEED).getAmplifier();
         speed *= 1.0 + 0.2 * (amplifier + 1);
      }

      if (mc.player.hasStatusEffect(StatusEffects.SLOWNESS)) {
         int amplifier = mc.player.getStatusEffect(StatusEffects.SLOWNESS).getAmplifier();
         speed /= 1.0 + 0.2 * (amplifier + 1);
      }

      return speed;
   }

   private void applySpeed(double speed) {
      float forward = mc.player.input.movementForward;
      float strafe = mc.player.input.movementSideways;
      float yaw = mc.player.getYaw();
      if (forward != 0.0F || strafe != 0.0F) {
         if (forward != 0.0F) {
            if (strafe > 0.0F) {
               yaw += forward > 0.0F ? -45.0F : 45.0F;
            } else if (strafe < 0.0F) {
               yaw += forward > 0.0F ? 45.0F : -45.0F;
            }

            strafe = 0.0F;
            forward = MathHelper.clamp(forward > 0.0F ? 1.0F : -1.0F, -1.0F, 1.0F);
         }

         strafe = MathHelper.clamp(strafe, -1.0F, 1.0F);
         double radians = Math.toRadians(yaw + 90.0F);
         double sin = Math.sin(radians);
         double cos = Math.cos(radians);
         double motionX = forward * speed * cos + strafe * speed * sin;
         double motionZ = forward * speed * sin - strafe * speed * cos;
         mc.player.setVelocity(motionX, mc.player.getVelocity().y, motionZ);
      }
   }

   private void resetSpeedState() {
      this.lastSpeed = 0.0;
      this.airTicks = 0;
      this.groundTicks = 0;
   }
}
