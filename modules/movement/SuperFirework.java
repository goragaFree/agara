package moscow.rockstar.systems.modules.modules.movement;

import moscow.rockstar.Rockstar;
import moscow.rockstar.systems.event.EventListener;
import moscow.rockstar.systems.event.impl.game.FireworkEvent;
import moscow.rockstar.systems.modules.api.ModuleCategory;
import moscow.rockstar.systems.modules.api.ModuleInfo;
import moscow.rockstar.systems.modules.impl.BaseModule;
import moscow.rockstar.systems.setting.settings.ModeSetting;
import moscow.rockstar.systems.setting.settings.SliderSetting;
import moscow.rockstar.utility.rotations.Rotation;
import moscow.rockstar.utility.rotations.RotationHandler;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

@ModuleInfo(name = "Super Firework", category = ModuleCategory.MOVEMENT, desc = "modules.descriptions.elytra_motion")
public class SuperFirework extends BaseModule {
   private final ModeSetting algorithm = new ModeSetting(this, "modules.settings.elytra_motion.algorithm");
   private final ModeSetting.Value defaultAlgorithm = new ModeSetting.Value(this.algorithm, "modules.settings.elytra_motion.algorithm.default").select();
   private final ModeSetting.Value advancedAlgorithm = new ModeSetting.Value(this.algorithm, "modules.settings.elytra_motion.algorithm.advanced");
   private final ModeSetting.Value advancedStableAlgorithm = new ModeSetting.Value(this.algorithm, "modules.settings.elytra_motion.algorithm.advanced-stable");
   private final SliderSetting boostStrength = new SliderSetting(
         this, "modules.settings.elytra_motion.boost_strength", () -> this.algorithm.is(this.defaultAlgorithm)
      )
      .currentValue(0.28F)
      .min(0.0F)
      .max(1.0F)
      .step(0.02F);
   private final EventListener<FireworkEvent> onFirework = event -> {
      if (event.getEntity() == mc.player && mc.player != null) {
         Rotation rotation = this.getRotation();
         Vec3d direction = rotation.getRotationVector();
         double horizontalBoost;
         double verticalBoost;
         if (this.algorithm.is(this.defaultAlgorithm)) {
            horizontalBoost = this.getDefaultBoost(rotation);
            verticalBoost = horizontalBoost;
         } else {
            float normalizedYaw = this.normalizeDiagonalYaw(rotation.getYaw());
            float pitch = Math.abs(rotation.getPitch());
            ModeSetting.Value selectedAlgorithm = this.algorithm.getValue();
            horizontalBoost = this.getHorizontalAdvancedBoost(normalizedYaw, pitch, selectedAlgorithm);
            verticalBoost = this.getVerticalAdvancedBoost(pitch, normalizedYaw, selectedAlgorithm);
            horizontalBoost *= 1.0 + this.boostStrength.getCurrentValue() * 0.25;
            verticalBoost *= 1.0 + this.boostStrength.getCurrentValue() * 0.25;
         }

         event.setVelocity(this.applyFireworkBoost(event.getVelocity(), direction, horizontalBoost, verticalBoost));
      }
   };

   private Rotation getRotation() {
      RotationHandler rotationHandler = Rockstar.getInstance().getRotationHandler();
      return rotationHandler.isIdling() ? rotationHandler.getPlayerRotation() : rotationHandler.getCurrentRotation();
   }

   private Vec3d applyFireworkBoost(Vec3d currentVelocity, Vec3d direction, double horizontalBoost, double verticalBoost) {
      return currentVelocity.add(
         direction.x * 0.1 + (direction.x * horizontalBoost - currentVelocity.x) * 0.5,
         direction.y * 0.1 + (direction.y * verticalBoost - currentVelocity.y) * 0.5,
         direction.z * 0.1 + (direction.z * horizontalBoost - currentVelocity.z) * 0.5
      );
   }

   private double getDefaultBoost(Rotation rotation) {
      double yawMultiplier = this.getDiagonalMultiplier(rotation.getYaw());
      double pitch = rotation.getPitch();
      double pitchMultiplier;
      if (pitch >= -45.0 && pitch <= 20.0) {
         double shiftedPitch = pitch + 10.0;
         pitchMultiplier = 1.0 + 0.05 * Math.exp(-(shiftedPitch * shiftedPitch) / 800.0);
      } else if (pitch > 20.0) {
         pitchMultiplier = 1.0 - Math.min(0.35, (pitch - 20.0) / 70.0 * 0.35);
      } else {
         pitchMultiplier = 1.0 - Math.min(0.3, (Math.abs(pitch) - 45.0) / 45.0 * 0.3);
      }

      return MathHelper.clamp(yawMultiplier * pitchMultiplier, 0.65, 1.49);
   }

   private double getDiagonalMultiplier(float yaw) {
      double angle = Math.abs(yaw % 360.0F);
      if (angle > 180.0) {
         angle = 360.0 - angle;
      }

      double distanceToFirstDiagonal = Math.abs(angle - 45.0);
      double distanceToSecondDiagonal = Math.abs(angle - 135.0);
      double distance = Math.min(distanceToFirstDiagonal, distanceToSecondDiagonal);
      return 1.0 + 0.47 * Math.exp(-(distance * distance) / 288.0);
   }

   private float normalizeDiagonalYaw(float yaw) {
      float normalized = yaw % 180.0F;
      if (normalized > 90.0F) {
         normalized -= 180.0F;
      } else if (normalized < -90.0F) {
         normalized += 180.0F;
      }

      return Math.abs(normalized);
   }

   private double getHorizontalAdvancedBoost(float yaw, float pitch, ModeSetting.Value mode) {
      int roundedYaw = (int)Math.ceil(yaw);
      double value;
      if (pitch >= 40.0F && pitch <= 50.0F) {
         value = 2.0;
      } else if (pitch >= 38.0F && pitch <= 52.0F) {
         value = 1.98;
      } else if (pitch >= 32.0F && pitch <= 58.0F) {
         value = 1.97;
      } else if ((roundedYaw < 40 || roundedYaw > 50) && (!(pitch >= 40.0F) || !(pitch <= 50.0F))) {
         if ((roundedYaw < 29 || roundedYaw > 61) && (!(pitch >= 29.0F) || !(pitch <= 61.0F))) {
            if ((roundedYaw < 27 || roundedYaw > 63) && (!(pitch >= 27.0F) || !(pitch <= 63.0F))) {
               if ((roundedYaw < 26 || roundedYaw > 64) && (!(pitch >= 26.0F) || !(pitch <= 64.0F))) {
                  if ((roundedYaw < 15 || roundedYaw > 75) && (!(pitch >= 15.0F) || !(pitch <= 75.0F))) {
                     if ((roundedYaw < 13 || roundedYaw > 77) && (!(pitch >= 13.0F) || !(pitch <= 77.0F))) {
                        value = (roundedYaw < 12 || roundedYaw > 78) && (!(pitch >= 12.0F) || !(pitch <= 78.0F)) ? 1.626 : 1.671;
                     } else {
                        value = 1.7;
                     }
                  } else {
                     value = 1.74;
                  }
               } else {
                  value = 1.8;
               }
            } else {
               value = 1.84;
            }
         } else {
            value = 1.963;
         }
      } else {
         value = 1.967;
      }

      if (value < 1.9 && pitch > 10.0F) {
         value += 0.05;
      }

      return value * (mode == this.advancedStableAlgorithm ? 0.98F : 1.0F);
   }

   private double getVerticalAdvancedBoost(float pitch, float yaw, ModeSetting.Value mode) {
      double stable = mode == this.advancedStableAlgorithm ? 0.95F : 1.0;
      if (pitch >= 30.0F && pitch <= 40.0F) {
         return 2.0 * stable;
      } else if (pitch >= 35.0F && pitch <= 45.0F) {
         return 1.99 * stable;
      } else if (pitch >= 40.0F && pitch <= 50.0F) {
         return 1.97 * stable;
      } else if (pitch >= 50.0F && pitch <= 60.0F) {
         return 1.96 * stable;
      } else if (pitch >= 51.0F && pitch <= 61.0F) {
         return 1.89 * (mode == this.advancedStableAlgorithm ? 0.98F : 1.0F);
      } else if (pitch >= 52.0F && pitch <= 65.0F) {
         return 1.7;
      } else {
         return yaw >= 40.0F && yaw <= 50.0F ? 1.67 : 1.6;
      }
   }
}
