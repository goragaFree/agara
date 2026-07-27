package moscow.rockstar.systems.modules.modules.combat;

import java.util.Locale;
import java.util.Random;
import moscow.rockstar.Rockstar;
import moscow.rockstar.systems.event.EventListener;
import moscow.rockstar.systems.event.impl.player.ClientPlayerTickEvent;
import moscow.rockstar.systems.modules.api.ModuleCategory;
import moscow.rockstar.systems.modules.api.ModuleInfo;
import moscow.rockstar.systems.modules.impl.BaseModule;
import moscow.rockstar.systems.setting.settings.BooleanSetting;
import moscow.rockstar.systems.setting.settings.ModeSetting;
import moscow.rockstar.systems.setting.settings.SelectSetting;
import moscow.rockstar.systems.setting.settings.SliderSetting;
import moscow.rockstar.systems.target.TargetComparators;
import moscow.rockstar.systems.target.TargetSettings;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

@ModuleInfo(name = "Aim Assist", category = ModuleCategory.COMBAT, desc = "modules.descriptions.aim_assist")
public class AimAssist extends BaseModule {
   private static final String[] WEAPON_KEYWORDS = new String[]{"sword", "trident", "_axe", "mace", "stick", "pickaxe", "shovel"};
   private final Random random = new Random();
   private final SelectSetting targets = new SelectSetting(this, "modules.settings.aura.targets");
   private final SelectSetting.Value players = new SelectSetting.Value(this.targets, "modules.settings.aura.targets.players").select();
   private final SelectSetting.Value animals = new SelectSetting.Value(this.targets, "modules.settings.aura.targets.animals").select();
   private final SelectSetting.Value mobs = new SelectSetting.Value(this.targets, "modules.settings.aura.targets.mobs").select();
   private final SelectSetting.Value invisibles = new SelectSetting.Value(this.targets, "modules.settings.aura.targets.invisibles").select();
   private final SelectSetting.Value nakedPlayers = new SelectSetting.Value(this.targets, "modules.settings.aura.targets.nakedPlayers").select();
   private final SelectSetting.Value rockUsers = new SelectSetting.Value(this.targets, "modules.settings.aura.targets.rockUsers");
   private final SelectSetting.Value friends = new SelectSetting.Value(this.targets, "modules.settings.aura.targets.friends");
   private final ModeSetting sort = new ModeSetting(this, "aura.targets_sort");
   private final ModeSetting.Value sortDistance = new ModeSetting.Value(this.sort, "aura.ts_dist");
   private final ModeSetting.Value sortHealth = new ModeSetting.Value(this.sort, "aura.ts_health");
   private final ModeSetting.Value sortFov = new ModeSetting.Value(this.sort, "aura.ts_fov").select();
   private final ModeSetting targetLockMode = new ModeSetting(this, "aimassist.target_lock_mode");
   private final ModeSetting.Value lockOff = new ModeSetting.Value(this.targetLockMode, "aimassist.lock_off");
   private final ModeSetting.Value lockOnAttack = new ModeSetting.Value(this.targetLockMode, "aimassist.lock_on_attack");
   private final ModeSetting.Value lockAuto = new ModeSetting.Value(this.targetLockMode, "aimassist.lock_auto").select();
   private final SliderSetting lockTimeout = new SliderSetting(this, "aimassist.lock_timeout").min(0.0F).max(10.0F).step(0.1F).currentValue(5.0F);
   private final SliderSetting distance = new SliderSetting(this, "modules.settings.aura.aimDistance").min(3.0F).max(10.0F).step(0.1F).currentValue(4.5F);
   private final SliderSetting strength = new SliderSetting(this, "aimassist.strength").min(0.1F).max(2.0F).step(0.01F).currentValue(1.0F);
   private final SliderSetting aimSpeed = new SliderSetting(this, "aimassist.aim_speed").min(1.0F).max(60.0F).step(0.5F).currentValue(18.0F);
   private final SliderSetting smoothValue = new SliderSetting(this, "projectile.smooth_value").min(1.0F).max(30.0F).step(0.5F).currentValue(6.0F);
   private final BooleanSetting humanAim = new BooleanSetting(this, "aimassist.repit_aim").enabled(false);
   private final BooleanSetting verticalAim = new BooleanSetting(this, "aimassist.enable_vertical").enable();
   private final SliderSetting verticalFactor = new SliderSetting(this, "projectile.vertical_factor", () -> !this.verticalAim.isEnabled())
      .min(0.1F)
      .max(1.5F)
      .step(0.01F)
      .currentValue(0.4F);
   private final BooleanSetting verticalNoise = new BooleanSetting(this, "aimassist.vertical_noise", () -> this.verticalAim.isEnabled());
   private final SliderSetting motorNoise = new SliderSetting(this, "aimassist.motor_noise", "motornoise.desc")
      .min(0.0F)
      .max(0.4F)
      .step(0.01F)
      .currentValue(0.15F);
   private final BooleanSetting generateHesitations = new BooleanSetting(this, "aimassist.gen").enabled(false);
   private final SliderSetting hesitationFov = new SliderSetting(this, "aimassist.gen_fov_value", () -> !this.generateHesitations.isEnabled())
      .min(45.0F)
      .max(120.0F)
      .step(5.0F)
      .currentValue(90.0F);
   private final BooleanSetting onlyOnWeapon = new BooleanSetting(this, "aimassist.only_on_weapon").enable();
   private final BooleanSetting yieldToMouse = new BooleanSetting(this, "aimassist.yield_to_mouse").enabled(false);
   private final BooleanSetting inputBased = new BooleanSetting(this, "aimassist.input_based", () -> !this.yieldToMouse.isEnabled()).enabled(false);
   private final SliderSetting inputThreshold = new SliderSetting(this, "aimassist.input_threshold", () -> !this.inputBased.isEnabled())
      .min(0.1F)
      .max(3.0F)
      .step(0.1F)
      .currentValue(0.8F);
   private final SliderSetting predictionTicks = new SliderSetting(this, "aimassist.prediction_ticks").min(0.0F).max(3.0F).step(1.0F).currentValue(3.0F);
   private final SliderSetting velocityPredictionTicks = new SliderSetting(this, "aimassist.velo_pr_ticks").min(0.0F).max(3.0F).step(1.0F).currentValue(2.0F);
   private final BooleanSetting multipoint = new BooleanSetting(this, "aimassist.multipoint", () -> this.inputBased.isEnabled()).enabled(false);
   private final BooleanSetting multipointAdaptive = new BooleanSetting(this, "aimassist.mp_adaptive", () -> !this.multipoint.isEnabled()).enable();
   private final SliderSetting multipointCount = new SliderSetting(this, "aimassist.mp_count", () -> !this.shouldShowMultipointSliders())
      .min(3.0F)
      .max(50.0F)
      .step(1.0F)
      .currentValue(8.0F);
   private final SliderSetting multipointSpread = new SliderSetting(this, "aimassist.mp_spread", () -> !this.shouldShowMultipointSliders())
      .min(0.2F)
      .max(2.0F)
      .step(0.05F)
      .currentValue(0.7F);
   private final SliderSetting multipointRegen = new SliderSetting(this, "aimassist.regen", () -> !this.multipoint.isEnabled())
      .min(0.005F)
      .max(0.2F)
      .step(0.005F)
      .currentValue(0.025F);
   private LivingEntity lockedTarget;
   private long lockedAt;
   private long lastLostAt;
   private long hesitationUntil;
   private long nextPointAt;
   private Vec3d pointOffset = Vec3d.ZERO;
   private boolean appliedLastTick;
   private float lastAppliedYaw;
   private float lastAppliedPitch;
   private final EventListener<ClientPlayerTickEvent> onTick = event -> this.tickAimAssist();

   private void tickAimAssist() {
      if (mc.player == null || mc.world == null || mc.currentScreen != null) {
         this.resetState();
      } else if (this.onlyOnWeapon.isEnabled() && !this.isHoldingWeapon()) {
         this.resetState();
      } else {
         LivingEntity target = this.resolveTarget();
         if (target == null) {
            this.appliedLastTick = false;
         } else {
            Vec3d aimPoint = this.getAimPoint(target);
            Vec3d eyes = mc.player.getEyePos();
            double dx = aimPoint.x - eyes.x;
            double dy = aimPoint.y - eyes.y;
            double dz = aimPoint.z - eyes.z;
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            float targetYaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
            float targetPitch = (float)(-Math.toDegrees(Math.atan2(dy, horizontal)));
            if (!this.verticalAim.isEnabled()) {
               targetPitch = mc.player.getPitch();
            }

            float yawDelta = MathHelper.wrapDegrees(targetYaw - mc.player.getYaw());
            float pitchDelta = targetPitch - mc.player.getPitch();
            float angle = (float)Math.sqrt(yawDelta * yawDelta + pitchDelta * pitchDelta);
            if (!(angle < 0.01F) && !this.shouldPauseForHumanInput(yawDelta, pitchDelta, angle)) {
               float[] adjusted = this.getAdjustedDelta(yawDelta, pitchDelta, angle);
               float nextYaw = mc.player.getYaw() + adjusted[0];
               float nextPitch = MathHelper.clamp(mc.player.getPitch() + adjusted[1], -90.0F, 90.0F);
               mc.player.setYaw(nextYaw);
               mc.player.setPitch(nextPitch);
               mc.player.setHeadYaw(nextYaw);
               this.lastAppliedYaw = nextYaw;
               this.lastAppliedPitch = nextPitch;
               this.appliedLastTick = true;
            }
         }
      }
   }

   private LivingEntity resolveTarget() {
      TargetSettings settings = this.buildTargetSettings();
      LivingEntity candidate = Rockstar.getInstance().getTargetManager().getBestTarget(settings) instanceof LivingEntity living ? living : null;
      long now = System.currentTimeMillis();
      this.invalidateLock(settings, now);
      if (this.targetLockMode.is(this.lockOff)) {
         this.lockedTarget = null;
         return candidate;
      }

      if (this.targetLockMode.is(this.lockOnAttack)) {
         if (mc.options.attackKey.isPressed() && candidate != null) {
            this.lockTarget(candidate, now);
         }

         return this.lockedTarget != null ? this.lockedTarget : candidate;
      } else if (this.targetLockMode.is(this.lockAuto)) {
         if (this.lockedTarget == null && candidate != null) {
            this.lockTarget(candidate, now);
         }

         return this.lockedTarget != null ? this.lockedTarget : candidate;
      } else {
         return candidate;
      }
   }

   private TargetSettings buildTargetSettings() {
      TargetSettings.Builder builder = new TargetSettings.Builder()
         .targetPlayers(this.players.isSelected())
         .targetAnimals(this.animals.isSelected())
         .targetMobs(this.mobs.isSelected())
         .targetInvisibles(this.invisibles.isSelected())
         .targetNakedPlayers(this.nakedPlayers.isSelected())
         .targetFriends(this.friends.isSelected())
         .requiredRange(this.distance.getCurrentValue());
      if (this.sort.is(this.sortDistance)) {
         builder.sortBy(TargetComparators.DISTANCE);
      } else if (this.sort.is(this.sortHealth)) {
         builder.sortBy(TargetComparators.HEALTH);
      } else if (this.sort.is(this.sortFov)) {
         builder.sortBy(TargetComparators.FOV);
      }

      return builder.build();
   }

   private void invalidateLock(TargetSettings settings, long now) {
      if (this.lockedTarget != null) {
         boolean expired = this.lockTimeout.getCurrentValue() > 0.0F && now - this.lockedAt > (long)(this.lockTimeout.getCurrentValue() * 1000.0F);
         boolean invalid = !settings.isEntityValid(this.lockedTarget)
            || !this.lockedTarget.isAlive()
            || mc.world.getEntityById(this.lockedTarget.getId()) != this.lockedTarget;
         if (expired || invalid) {
            this.lockedTarget = null;
            this.lastLostAt = now;
            this.nextPointAt = 0L;
            this.pointOffset = Vec3d.ZERO;
         }
      }
   }

   private void lockTarget(LivingEntity target, long now) {
      if (target != this.lockedTarget) {
         this.lockedTarget = target;
         this.lockedAt = now;
         this.nextPointAt = 0L;
         this.pointOffset = Vec3d.ZERO;
      }
   }

   private Vec3d getAimPoint(LivingEntity target) {
      Vec3d velocity = target.getVelocity().multiply(this.predictionTicks.getCurrentValue());
      Vec3d tickVelocity = new Vec3d(
            target.getX() - target.prevX, target.getY() - target.prevY, target.getZ() - target.prevZ
         )
         .multiply(this.velocityPredictionTicks.getCurrentValue());
      Box box = target.getBoundingBox();
      Vec3d center = box.getCenter().add(velocity).add(tickVelocity);
      double y = target.getY() + target.getHeight() * (this.verticalAim.isEnabled() ? 0.55F * this.verticalFactor.getCurrentValue() + 0.25F : 0.5F);
      Vec3d base = new Vec3d(center.x, y + velocity.y + tickVelocity.y, center.z);
      if (!this.multipoint.isEnabled()) {
         return base;
      }

      long now = System.nanoTime();
      long interval = Math.max(1L, (long)(this.multipointRegen.getCurrentValue() * 1.0E9F));
      if (now >= this.nextPointAt) {
         this.pointOffset = this.createMultipointOffset(target);
         this.nextPointAt = now + interval;
      }

      return base.add(this.pointOffset);
   }

   private Vec3d createMultipointOffset(LivingEntity target) {
      Box box = target.getBoundingBox();
      double spread = this.multipointSpread.getCurrentValue();
      if (this.multipointAdaptive.isEnabled()) {
         float dist = mc.player == null ? 0.0F : mc.player.distanceTo(target);
         spread *= MathHelper.clamp(dist / Math.max(this.distance.getCurrentValue(), 0.1F), 0.35F, 1.0F);
      }

      double width = Math.max(0.05, box.getLengthX() * 0.5 * spread);
      double depth = Math.max(0.05, box.getLengthZ() * 0.5 * spread);
      double height = Math.max(0.05, target.getHeight() * 0.35 * spread);
      int count = Math.max(1, (int)this.multipointCount.getCurrentValue());
      int slot = this.random.nextInt(count);
      double angle = (Math.PI * 2) * slot / count + this.random.nextDouble() * 0.35;
      double x = Math.cos(angle) * width * this.random.nextDouble();
      double z = Math.sin(angle) * depth * this.random.nextDouble();
      double y = (this.random.nextDouble() - 0.5) * height;
      return new Vec3d(x, y, z);
   }

   private boolean shouldPauseForHumanInput(float yawDelta, float pitchDelta, float angle) {
      if (this.appliedLastTick) {
         float manualYaw = MathHelper.wrapDegrees(mc.player.getYaw() - this.lastAppliedYaw);
         float manualPitch = mc.player.getPitch() - this.lastAppliedPitch;
         float manualAmount = Math.abs(manualYaw) + Math.abs(manualPitch);
         if (this.inputBased.isEnabled() && manualAmount < this.inputThreshold.getCurrentValue()) {
            return true;
         }

         if (this.yieldToMouse.isEnabled()
            && (Math.signum(manualYaw) == -Math.signum(yawDelta) || Math.signum(manualPitch) == -Math.signum(pitchDelta))
            && manualAmount > this.inputThreshold.getCurrentValue()) {
            return true;
         }
      }

      long now = System.currentTimeMillis();
      if (this.generateHesitations.isEnabled() && angle < this.hesitationFov.getCurrentValue()) {
         if (now < this.hesitationUntil) {
            return true;
         }

         if (this.random.nextFloat() < 0.018F) {
            this.hesitationUntil = now + 45L + this.random.nextInt(115);
            return true;
         }
      }

      return false;
   }

   private float[] getAdjustedDelta(float yawDelta, float pitchDelta, float angle) {
      float speed = this.aimSpeed.getCurrentValue() * 0.35F * this.strength.getCurrentValue();
      float smoothing = MathHelper.clamp(20.0F / Math.max(this.smoothValue.getCurrentValue(), 1.0F), 0.1F, 1.0F);
      float factor = MathHelper.clamp(angle / 45.0F, 0.08F, 1.0F) * smoothing;
      if (this.humanAim.isEnabled()) {
         speed *= 0.7F + 0.3F * factor;
         factor *= 0.75F + this.random.nextFloat() * 0.2F;
      }

      float yawStep = MathHelper.clamp(yawDelta * factor, -speed, speed);
      float pitchStep = this.verticalAim.isEnabled() ? MathHelper.clamp(pitchDelta * factor, -speed, speed) : 0.0F;
      float noise = this.motorNoise.getCurrentValue();
      if (noise > 0.0F) {
         yawStep += (this.random.nextFloat() - 0.5F) * noise;
         pitchStep += (this.random.nextFloat() - 0.5F) * noise * 0.65F;
      }

      if (this.verticalNoise.isEnabled() && Math.abs(yawStep) > 0.01F) {
         pitchStep += (this.random.nextFloat() - 0.5F) * Math.abs(yawStep) * 0.05F * Math.max(noise, 0.1F);
      }

      return new float[]{yawStep, pitchStep};
   }

   private boolean isHoldingWeapon() {
      if (mc.player == null) {
         return false;
      }

      String key = mc.player.getMainHandStack().getItem().getTranslationKey().toLowerCase(Locale.ROOT);

      for (String keyword : WEAPON_KEYWORDS) {
         if (key.contains(keyword)) {
            return true;
         }
      }

      return false;
   }

   private boolean shouldShowMultipointSliders() {
      return this.multipoint.isEnabled() && !this.multipointAdaptive.isEnabled();
   }

   private void resetState() {
      this.lockedTarget = null;
      this.lastLostAt = 0L;
      this.hesitationUntil = 0L;
      this.nextPointAt = 0L;
      this.pointOffset = Vec3d.ZERO;
      this.appliedLastTick = false;
   }

   @Override
   public void onDisable() {
      this.resetState();
   }

   public SelectSetting.Value getRockUsers() {
      return this.rockUsers;
   }
}
