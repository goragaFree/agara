package moscow.rockstar.systems.modules.modules.combat;

import lombok.Generated;
import moscow.rockstar.Rockstar;
import moscow.rockstar.mixin.accessors.ClientPlayerInteractionManagerAccessor;
import moscow.rockstar.systems.event.EventListener;
import moscow.rockstar.systems.event.impl.player.ClientPlayerTickEvent;
import moscow.rockstar.systems.localization.Localizator;
import moscow.rockstar.systems.modules.api.ModuleCategory;
import moscow.rockstar.systems.modules.api.ModuleInfo;
import moscow.rockstar.systems.modules.impl.BaseModule;
import moscow.rockstar.systems.setting.settings.BooleanSetting;
import moscow.rockstar.systems.setting.settings.ModeSetting;
import moscow.rockstar.systems.setting.settings.SelectSetting;
import moscow.rockstar.systems.setting.settings.SliderSetting;
import moscow.rockstar.systems.target.TargetComparators;
import moscow.rockstar.systems.target.TargetSettings;
import moscow.rockstar.utility.animation.base.Animation;
import moscow.rockstar.utility.animation.base.Easing;
import moscow.rockstar.utility.game.CombatUtility;
import moscow.rockstar.utility.game.EntityUtility;
import moscow.rockstar.utility.game.TextUtility;
import moscow.rockstar.utility.game.prediction.ElytraPredictionSystem;
import moscow.rockstar.utility.game.prediction.FallingPlayer;
import moscow.rockstar.utility.game.server.ServerUtility;
import moscow.rockstar.utility.math.MathUtility;
import moscow.rockstar.utility.math.PerlinNoise;
import moscow.rockstar.utility.rotations.MoveCorrection;
import moscow.rockstar.utility.rotations.Rotation;
import moscow.rockstar.utility.rotations.RotationHandler;
import moscow.rockstar.utility.rotations.RotationMath;
import moscow.rockstar.utility.rotations.RotationPriority;
import moscow.rockstar.utility.rotations.modes.FunTimeRotationMode;
import moscow.rockstar.utility.rotations.modes.HolyWorldRotationMode;
import moscow.rockstar.utility.rotations.modes.OneTickRotationMode;
import moscow.rockstar.utility.rotations.modes.ReallyWorldRotationMode;
import moscow.rockstar.utility.rotations.modes.SlimeWorldRotationMode;
import moscow.rockstar.utility.rotations.modes.SlothRotationMode;
import moscow.rockstar.utility.rotations.modes.SpookyTimeRotationMode;
import moscow.rockstar.utility.time.Timer;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.item.consume.UseAction;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

@ModuleInfo(name = "Aura", category = ModuleCategory.COMBAT, desc = "Бьёт женщин и детей")
public class Aura extends BaseModule {
   private static final float AUTO_MACE_MIN_FALL_DISTANCE = 5.0F;
   private static final double AUTO_MACE_MIN_DOWNWARD_SPEED = -0.35;
   private SliderSetting attackDistance;
   private SliderSetting aimDistance;
   private SliderSetting minCps;
   private SliderSetting maxCps;
   private SelectSetting targets;
   private SelectSetting.Value players;
   private SelectSetting.Value animals;
   private SelectSetting.Value mobs;
   private SelectSetting.Value invisibles;
   private SelectSetting.Value nakedPlayers;
   private SelectSetting.Value friends;
   private SelectSetting.Value rockUsers;
   private ModeSetting sortingMode;
   private ModeSetting.Value distanceSorting;
   private ModeSetting.Value healthSorting;
   private ModeSetting.Value fovSorting;
   private ModeSetting rotationMode;
   private ModeSetting.Value noRotation;
   private ModeSetting.Value simpleRotation;
   private ModeSetting.Value funTimeRotation;
   private ModeSetting.Value spookyTimeRotation;
   private ModeSetting.Value holyWorldRotation;
   private ModeSetting.Value intaveRotation;
   private ModeSetting.Value oneTickRotation;
   private ModeSetting.Value reallyWorldRotation;
   private ModeSetting.Value slimeWorldRotation;
   private ModeSetting.Value slothRotation;
   private ModeSetting moveCorrectionMode;
   private ModeSetting.Value noMoveCorrection;
   private ModeSetting.Value directMoveCorrection;
   private ModeSetting.Value silentMoveCorrection;
   private ModeSetting styleAttack;
   private ModeSetting.Value fastPvp;
   private ModeSetting.Value slowPvp;
   private ModeSetting criticalMode;
   private ModeSetting.Value newCriticals;
   private ModeSetting.Value oldCriticals;
   private SelectSetting targetingOptions;
   private SelectSetting.Value hitVectorMode;
   private SelectSetting.Value offhandHit;
   private SelectSetting.Value protectedPlayerCheck;
   private SelectSetting.Value strictTargeting;
   private SelectSetting.Value elytraTarget;
   private BooleanSetting onlyCriticals;
   private BooleanSetting walls;
   private BooleanSetting rayTrace;
   private BooleanSetting smartCriticals;
   private BooleanSetting noHitInv;
   private BooleanSetting onlyWeapon;
   private BooleanSetting autoMace;
   private SliderSetting autoMaceFallDistance;
   private BooleanSetting targeting;
   private BooleanSetting noTeammates18;
   private final Animation nononoYaw = new Animation(300L, Easing.LINEAR);
   private final Animation nononoPitch = new Animation(1000L, Easing.LINEAR);
   private Timer attackTimer;
   boolean shield;
   private PerlinNoise noise = new PerlinNoise();
   private long rotationStartTime = 0L;
   private float noiseFactor = 0.0F;
   private int attacks;
   private Rotation additional;
   private float lastAttackCooldown;
   private boolean wasCriticalReady;
   private boolean wallBypassArmed;
   private boolean queuedUseHit;
   private final HolyWorldRotationMode holyWorldRotationMode = new HolyWorldRotationMode();
   private final FunTimeRotationMode funTimeRotationModeImpl = new FunTimeRotationMode();
   private final SpookyTimeRotationMode spookyTimeRotationMode = new SpookyTimeRotationMode();
   private final OneTickRotationMode oneTickRotationMode = new OneTickRotationMode();
   private final ReallyWorldRotationMode reallyWorldRotationMode = new ReallyWorldRotationMode();
   private final SlimeWorldRotationMode slimeWorldRotationMode = new SlimeWorldRotationMode();
   private final SlothRotationMode slothRotationMode = new SlothRotationMode();
   private final EventListener<ClientPlayerTickEvent> onPlayerTick = event -> {
      if (mc.player != null) {
         float requiredAimDistance = Rockstar.getInstance().getModuleManager().getModule(ElytraTarget.class).isEnabled()
            ? 50.0F
            : this.aimDistance.getCurrentValue();
         TargetSettings.Builder builder = new TargetSettings.Builder()
            .targetPlayers(this.players.isSelected())
            .targetAnimals(this.animals.isSelected())
            .targetMobs(this.mobs.isSelected())
            .targetInvisibles(this.invisibles.isSelected())
            .targetNakedPlayers(this.nakedPlayers.isSelected())
            .targetFriends(this.friends.isSelected())
            .requiredRange(requiredAimDistance);
         if (this.sortingMode.is(this.distanceSorting)) {
            builder.sortBy(TargetComparators.DISTANCE);
         } else if (this.sortingMode.is(this.healthSorting)) {
            builder.sortBy(TargetComparators.HEALTH);
         } else if (this.sortingMode.is(this.fovSorting)) {
            builder.sortBy(TargetComparators.FOV);
         }

         TargetSettings settings = builder.build();
         LivingEntity target = Rockstar.getInstance().getTargetManager().getCurrentTarget() instanceof LivingEntity living ? living : null;
         boolean keepForcedTarget = this.targeting.isEnabled()
            && target != null
            && settings.isEntityValid(target)
            && MathHelper.sqrt((float)mc.player.squaredDistanceTo(RotationMath.getNearestPoint(target))) <= requiredAimDistance
            && mc.world.hasEntity(target)
            && target.isAlive();
         if (!keepForcedTarget) {
            Rockstar.getInstance().getTargetManager().update(settings);
            target = Rockstar.getInstance().getTargetManager().getCurrentTarget() instanceof LivingEntity living ? living : null;
         }

         if (target != null) {
            this.rotateHead(target);
            if (this.shouldAttackEntity(target)) {
               this.attack(target);
            }
         } else {
            this.rotationStartTime = System.currentTimeMillis();
            this.noise = new PerlinNoise();
            this.noiseFactor = 1.0F;
            this.resetRotationModes();
         }
      }
   };

   public Aura() {
      this.initialize();
   }

   private void initialize() {
      this.rotationMode = new ModeSetting(this, "modules.settings.aura.rotationMode");
      this.noRotation = new ModeSetting.Value(this.rotationMode, "modules.settings.aura.noRotation");
      this.simpleRotation = new ModeSetting.Value(this.rotationMode, "modules.settings.aura.simpleRotation").select();
      this.funTimeRotation = new ModeSetting.Value(this.rotationMode, "FunTime");
      this.spookyTimeRotation = new ModeSetting.Value(this.rotationMode, "SpookyTime");
      this.holyWorldRotation = new ModeSetting.Value(this.rotationMode, "HolyWorld");
      this.intaveRotation = new ModeSetting.Value(this.rotationMode, "Intave");
      this.oneTickRotation = new ModeSetting.Value(this.rotationMode, "OneTick");
      this.reallyWorldRotation = new ModeSetting.Value(this.rotationMode, "ReallyWorld");
      this.slimeWorldRotation = new ModeSetting.Value(this.rotationMode, "SlimeWorld");
      this.slothRotation = new ModeSetting.Value(this.rotationMode, "Sloth");
      this.attackDistance = new SliderSetting(this, "modules.settings.aura.attackDistance")
         .min(0.1F)
         .max(6.0F)
         .step(0.1F)
         .currentValue(3.0F)
         .suffix(number -> " %s".formatted(Localizator.translate("block")) + TextUtility.makeCountTranslated(number));
      this.aimDistance = new SliderSetting(this, "modules.settings.aura.aimDistance")
         .min(0.1F)
         .max(50.0F)
         .step(0.1F)
         .currentValue(3.0F)
         .suffix(number -> " %s".formatted(Localizator.translate("block")) + TextUtility.makeCountTranslated(number));
      this.minCps = new SliderSetting(this, "modules.settings.aura.minCps").min(1.0F).max(20.0F).step(1.0F).currentValue(8.0F);
      this.maxCps = new SliderSetting(this, "modules.settings.aura.maxCps").min(1.0F).max(20.0F).step(1.0F).currentValue(12.0F);
      this.onlyCriticals = new BooleanSetting(this, "only_crits");
      this.walls = new BooleanSetting(this, "modules.settings.aura.walls").enable();
      this.rayTrace = new BooleanSetting(this, "modules.settings.aura.rayTrace").enable();
      this.smartCriticals = new BooleanSetting(this, "modules.settings.aura.smartCriticals").enable();
      this.noHitInv = new BooleanSetting(this, "modules.settings.aura.noHitInv").enable();
      this.targeting = new BooleanSetting(this, "modules.settings.aura.targeting").enable();
      this.onlyWeapon = new BooleanSetting(this, "modules.settings.aura.onlyWeapon");
      this.autoMace = new BooleanSetting(this, "modules.settings.aura.auto_mace").enabled(true);
      this.autoMaceFallDistance = new SliderSetting(this, "modules.settings.aura.auto_mace_fall_distance", () -> !this.autoMace.isEnabled())
         .min(5.0F)
         .max(30.0F)
         .step(0.1F)
         .currentValue(5.0F);
      this.noTeammates18 = new BooleanSetting(this, "modules.settings.aura.noTeammates18").enable();
      this.targets = new SelectSetting(this, "targets");
      this.players = new SelectSetting.Value(this.targets, "players").select();
      this.animals = new SelectSetting.Value(this.targets, "animals").select();
      this.mobs = new SelectSetting.Value(this.targets, "mobs").select();
      this.invisibles = new SelectSetting.Value(this.targets, "invisibles").select();
      this.nakedPlayers = new SelectSetting.Value(this.targets, "nakedPlayers").select();
      this.friends = new SelectSetting.Value(this.targets, "friends");
      this.rockUsers = new SelectSetting.Value(this.targets, "rockUsers").select();
      this.sortingMode = new ModeSetting(this, "sorting");
      this.distanceSorting = new ModeSetting.Value(this.sortingMode, "modules.settings.aura.distanceSorting").select();
      this.healthSorting = new ModeSetting.Value(this.sortingMode, "modules.settings.aura.healthSorting");
      this.fovSorting = new ModeSetting.Value(this.sortingMode, "modules.settings.aura.fovSorting");
      this.moveCorrectionMode = new ModeSetting(this, "modules.settings.aura.moveCorrectionMode");
      this.noMoveCorrection = new ModeSetting.Value(this.moveCorrectionMode, "modules.settings.aura.noMoveCorrection");
      this.directMoveCorrection = new ModeSetting.Value(this.moveCorrectionMode, "modules.settings.aura.directMoveCorrection");
      this.silentMoveCorrection = new ModeSetting.Value(this.moveCorrectionMode, "modules.settings.aura.silentMoveCorrection").select();
      this.styleAttack = new ModeSetting(this, "modules.settings.aura.styleAttack");
      this.fastPvp = new ModeSetting.Value(this.styleAttack, "1.8");
      this.slowPvp = new ModeSetting.Value(this.styleAttack, "1.9").select();
      this.criticalMode = new ModeSetting(this, "modules.settings.aura.critCalc");
      this.newCriticals = new ModeSetting.Value(this.criticalMode, "New").select();
      this.oldCriticals = new ModeSetting.Value(this.criticalMode, "Old");
      this.targetingOptions = new SelectSetting(this, "modules.settings.aura.targetingOptions");
      this.hitVectorMode = new SelectSetting.Value(this.targetingOptions, "Hit Vector").select();
      this.offhandHit = new SelectSetting.Value(this.targetingOptions, "Offhand Hit").select();
      this.protectedPlayerCheck = new SelectSetting.Value(this.targetingOptions, "Protected Player Check").select();
      this.strictTargeting = new SelectSetting.Value(this.targetingOptions, "Strict Targeting").select();
      this.elytraTarget = new SelectSetting.Value(this.targetingOptions, "Elytra Target").select();
      this.attackTimer = new Timer();
      this.lastAttackCooldown = 0.0F;
      this.wasCriticalReady = false;
      this.wallBypassArmed = false;
      this.queuedUseHit = false;
   }

   private boolean shouldAttackEntity(LivingEntity targetedEntity) {
      if (!this.isCooledDown()) {
         return false;
      }

      if (this.onlyWeapon.isEnabled() && !EntityUtility.isHoldingWeapon()) {
         return false;
      }

      if (this.shouldSwapHandForHit()) {
         return false;
      }

      if (this.inRange(targetedEntity)) {
         return false;
      }

      if (this.walls.isEnabled()
         && this.spookyTimeRotation.isSelected()
         && mc.world
               .raycast(
                  new RaycastContext(
                     mc.player.getEyePos(),
                     mc.player
                        .getEyePos()
                        .add(
                           mc.player
                              .getRotationVector(-90.0F, Rockstar.getInstance().getRotationHandler().getCurrentRotation().getYaw())
                              .multiply(this.attackDistance.getCurrentValue())
                        ),
                     ShapeType.COLLIDER,
                     FluidHandling.NONE,
                     mc.player
                  )
               )
               .getType()
            == Type.BLOCK) {
         return false;
      }

      boolean canSeeTarget = this.canSeeTarget(targetedEntity);
      return !canSeeTarget && this.rayTrace.isEnabled() && !this.wallBypassArmed ? false : this.canAttackWithCriticalSettings(targetedEntity);
   }

   private boolean canAttackWithCriticalSettings(LivingEntity targetedEntity) {
      return !this.shouldWaitForCritical(targetedEntity) || CombatUtility.canPerformCriticalHit(targetedEntity, true);
   }

   private boolean shouldWaitForCritical(LivingEntity targetedEntity) {
      if (!this.onlyCriticals.isEnabled() && !this.smartCriticals.isEnabled()) {
         return false;
      } else {
         return this.onlyCriticals.isEnabled() && !this.smartCriticals.isEnabled() ? true : this.shouldHoldForSmartCritical(targetedEntity);
      }
   }

   private boolean shouldHoldForSmartCritical(LivingEntity targetedEntity) {
      return this.canPrepareSmartCritical() && this.isCriticalRequired(targetedEntity);
   }

   private boolean canPrepareSmartCritical() {
      return mc.player != null
         && mc.world != null
         && !mc.player.isOnGround()
         && !mc.player.isGliding()
         && !mc.player.isClimbing()
         && !mc.player.isTouchingWater()
         && !mc.player.isSwimming()
         && !mc.player.isInLava()
         && !mc.player.hasVehicle()
         && !mc.player.hasStatusEffect(StatusEffects.BLINDNESS)
         && !mc.player.hasStatusEffect(StatusEffects.LEVITATION)
         && !mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING)
         && !mc.world.getBlockState(mc.player.getBlockPos()).isOf(Blocks.COBWEB);
   }

   private boolean isCriticalRequired(LivingEntity targetedEntity) {
      float damage = this.calculateDamage(targetedEntity, false);
      return damage + 0.25F < this.getComparableHealth(targetedEntity);
   }

   private float getComparableHealth(LivingEntity targetedEntity) {
      return targetedEntity instanceof PlayerEntity player ? EntityUtility.getHealth(player) : targetedEntity.getHealth() + targetedEntity.getAbsorptionAmount();
   }

   public boolean isCooledDown() {
      if (mc.player == null) {
         return false;
      }

      long fastDelay = this.getAttackDelayMs();
      float cooldownThreshold = this.newCriticals.isSelected() ? 0.8F : 0.93F;
      return mc.player.getAttackCooldownProgress(1.5F) > cooldownThreshold && this.attackTimer.finished(500L)
         || this.fastPvp.isSelected() && this.attackTimer.finished(fastDelay);
   }

   public float calculateDamage(LivingEntity targetedEntity) {
      return this.calculateDamage(targetedEntity, CombatUtility.canDealCriticalDamage(targetedEntity, true));
   }

   private float calculateDamage(LivingEntity targetedEntity, boolean includeCritical) {
      if (mc.player != null && targetedEntity != null) {
         float baseDamage = (float)mc.player.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);
         float cooldown = mc.player.getAttackCooldownProgress(0.0F);
         float damage = baseDamage * (0.2F + cooldown * cooldown * 0.8F);
         if (includeCritical) {
            damage *= 1.5F;
         }

         float armor = targetedEntity.getArmor();
         float toughness = (float)targetedEntity.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
         float armorPart = MathHelper.clamp(armor - damage / (2.0F + toughness / 4.0F), armor * 0.2F, 20.0F);
         damage *= 1.0F - armorPart / 25.0F;
         return Math.max(0.0F, damage);
      } else {
         return 0.0F;
      }
   }

   private void attack(LivingEntity targetedEntity) {
      this.attackTargetWithAutoMace(targetedEntity);
   }

   private void attackTargetWithAutoMace(LivingEntity targetedEntity) {
      if (!this.shouldUseAutoMace(targetedEntity)) {
         this.attackTarget(targetedEntity);
      } else if (mc.player.getMainHandStack().isOf(Items.MACE)) {
         this.attackTarget(targetedEntity);
      } else {
         int previousSlot = mc.player.getInventory().selectedSlot;
         int maceHotbarSlot = this.findMaceHotbarSlot();
         if (maceHotbarSlot != -1) {
            this.attackWithMaceSlot(targetedEntity, maceHotbarSlot, previousSlot);
         } else {
            int maceInventorySlot = this.findMaceInventorySlot();
            if (maceInventorySlot == -1) {
               this.attackTarget(targetedEntity);
            } else {
               int swapHotbarSlot = this.findTemporaryHotbarSlot(previousSlot);
               this.swapInventorySlotWithHotbar(maceInventorySlot, swapHotbarSlot);
               this.attackWithMaceSlot(targetedEntity, swapHotbarSlot, previousSlot);
               this.swapInventorySlotWithHotbar(maceInventorySlot, swapHotbarSlot);
            }
         }
      }
   }

   private void attackWithMaceSlot(LivingEntity targetedEntity, int maceSlot, int previousSlot) {
      if (maceSlot == previousSlot) {
         this.attackTarget(targetedEntity);
      } else {
         mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(maceSlot));
         this.attackTarget(targetedEntity);
         mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
      }
   }

   private boolean shouldUseAutoMace(LivingEntity targetedEntity) {
      float requiredFallDistance = Math.max(5.0F, this.autoMaceFallDistance.getCurrentValue());
      return mc.player != null
         && targetedEntity != null
         && this.autoMace.isEnabled()
         && !mc.player.isOnGround()
         && !mc.player.isGliding()
         && !mc.player.isTouchingWater()
         && !mc.player.isInLava()
         && mc.player.getVelocity().y <= -0.35
         && mc.player.fallDistance >= requiredFallDistance;
   }

   private int findMaceHotbarSlot() {
      for (int slot = 0; slot < 9; slot++) {
         if (mc.player.getInventory().getStack(slot).isOf(Items.MACE)) {
            return slot;
         }
      }

      return -1;
   }

   private int findMaceInventorySlot() {
      for (int slot = 9; slot < 36; slot++) {
         if (mc.player.getInventory().getStack(slot).isOf(Items.MACE)) {
            return slot;
         }
      }

      return -1;
   }

   private int findTemporaryHotbarSlot(int previousSlot) {
      for (int slot = 0; slot < 9; slot++) {
         if (slot != previousSlot && mc.player.getInventory().getStack(slot).isEmpty()) {
            return slot;
         }
      }

      return previousSlot == 8 ? 7 : 8;
   }

   private void swapInventorySlotWithHotbar(int inventorySlot, int hotbarSlot) {
      if (mc.interactionManager != null && mc.getNetworkHandler() != null) {
         int syncId = mc.player.currentScreenHandler.syncId;
         mc.interactionManager.clickSlot(syncId, inventorySlot, hotbarSlot, SlotActionType.SWAP, mc.player);
         mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(syncId));
      }
   }

   private void attackTarget(LivingEntity targetedEntity) {
      if (mc.interactionManager != null && mc.player != null) {
         this.shield = mc.player.isUsingItem()
            && mc.player.getActiveItem().getItem().getUseAction(mc.player.getActiveItem()) == UseAction.BLOCK;
         if (this.shield) {
            mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN));
         }

         if (CombatUtility.shouldBreakShield(targetedEntity) && CombatUtility.canBreakShield(targetedEntity)) {
            CombatUtility.tryBreakShield(targetedEntity);
         }

         this.lastAttackCooldown = mc.player.getAttackCooldownProgress(0.0F);
         this.wasCriticalReady = this.shouldWaitForCritical(targetedEntity) && CombatUtility.canPerformCriticalHit(targetedEntity, true);
         mc.interactionManager.attackEntity(mc.player, targetedEntity);
         mc.player.swingHand(Hand.MAIN_HAND);
         if (this.shield) {
             ((ClientPlayerInteractionManagerAccessor)mc.interactionManager)
                .invokeSendSequencedPacket(
                   mc.world,
                   sequence -> new PlayerInteractItemC2SPacket(
                     mc.player.getActiveHand(),
                     sequence,
                     Rockstar.getInstance().getRotationHandler().getCurrentRotation().getYaw(),
                     Rockstar.getInstance().getRotationHandler().getCurrentRotation().getPitch()
                  )
               );
         }

         this.additional = new Rotation(MathUtility.random(5.0, 20.0), MathUtility.random(5.0, 10.0));
         this.attackTimer.reset();
         this.attacks++;
         this.wallBypassArmed = false;
         this.queuedUseHit = false;
      }
   }

   private void rotateHead(LivingEntity targetedEntity) {
      if ((!this.onlyWeapon.isEnabled() || EntityUtility.isHoldingWeapon()) && !this.rotationMode.is(this.noRotation)) {
         MoveCorrection moveCorrection;
         if (this.moveCorrectionMode.is(this.silentMoveCorrection)) {
            moveCorrection = MoveCorrection.SILENT;
         } else if (this.moveCorrectionMode.is(this.directMoveCorrection)) {
            moveCorrection = MoveCorrection.DIRECT;
         } else {
            moveCorrection = MoveCorrection.NONE;
         }

         RotationHandler handler = Rockstar.getInstance().getRotationHandler();
         if (this.rotationMode.is(this.simpleRotation)) {
            Rotation rot = RotationMath.getRotationTo(
               RotationMath.getNearestPoint(
                  targetedEntity,
                  Rockstar.getInstance().getModuleManager().getModule(ElytraTarget.class).isEnabled() && targetedEntity instanceof PlayerEntity player
                     ? ElytraPredictionSystem.predictPlayerPosition(player)
                     : targetedEntity.getPos()
               )
            );
            if (mc.player.getEyePos().distanceTo(targetedEntity.getEyePos()) > 3.0) {
               rot.setYaw(
                  RotationMath.getRotationTo(
                        (Rockstar.getInstance().getModuleManager().getModule(ElytraTarget.class).isEnabled() && targetedEntity instanceof PlayerEntity playerx
                              ? ElytraPredictionSystem.predictPlayerPosition(playerx)
                              : targetedEntity.getPos())
                           .add(0.0, targetedEntity.getEyeHeight(targetedEntity.getPose()), 0.0)
                     )
                     .getYaw()
               );
            }

            handler.rotate(rot, moveCorrection, 180.0F, 180.0F, 180.0F, RotationPriority.TO_TARGET);
         }

         if (this.rotationMode.is(this.holyWorldRotation)) {
            this.holyWorldRotationMode.rotate(this, handler, targetedEntity, moveCorrection);
         }

         if (this.rotationMode.is(this.funTimeRotation)) {
            this.funTimeRotationModeImpl.rotate(this, handler, targetedEntity, moveCorrection);
         }

         if (this.rotationMode.is(this.intaveRotation)) {
            if (mc.player.age % 500 == 0) {
               this.noise = new PerlinNoise();
               this.noiseFactor = 1.0F;
            }

            Vec3d nearY = RotationMath.getNearestPoint(targetedEntity);
            Rotation targetRot = RotationMath.getRotationTo(
               new Vec3d(
                  nearY.x,
                  MathHelper.clamp(
                     MathUtility.interpolate(mc.player.getY(), targetedEntity.getEyeY(), 0.5),
                     targetedEntity.getBoundingBox().minY,
                     targetedEntity.getBoundingBox().maxY
                  ),
                  nearY.z
               )
            );
            Rotation multipoint = RotationMath.getRotationTo(RotationMath.getNearestPoint(targetedEntity));
            boolean idle = this.attackTimer.finished(300L);
            if (this.additional == null) {
               this.additional = new Rotation(0.0F, 0.0F);
            }

            float targetYaw = targetRot.getYaw();
            float targetPitch = targetRot.getPitch();
            Rotation currentRot = handler.getCurrentRotation();
            float currentYaw = currentRot.getYaw();
            float currentPitch = currentRot.getPitch();
            float yawDiff = RotationMath.getAngleDifference(currentYaw, targetYaw);
            float pitchDiff = RotationMath.getAngleDifference(currentPitch, targetPitch);
            if (idle) {
               if (this.shouldPreventSprinting()) {
                  targetYaw += 5.0F;
                  targetPitch -= 10.0F;
               } else {
                  targetYaw -= 5.0F;
               }
            }

            if (!this.rotationMode.is(this.intaveRotation) && !idle) {
               targetYaw += this.additional.getYaw();
               targetPitch += this.additional.getPitch();
            }

            float yawSpeed = Math.max(
                  (90.0F - Math.abs(yawDiff)) / (idle ? (mc.player.fallDistance > 0.0F ? 20.0F : 60.0F) : 40.0F), MathUtility.random(1.0, 5.0)
               )
               * MathUtility.random(0.9, 1.1);
            float pitchSpeed = Math.abs(pitchDiff) / (idle ? (mc.player.fallDistance > 0.0F ? 60.0F : 100.0F) : 30.0F) * MathUtility.random(0.9, 1.1);
            long timeElapsed = System.currentTimeMillis() - this.rotationStartTime;
            float yawNoise = (float)this.noise.noise(timeElapsed * 5.0E-4);
            float pitchNoise = (float)this.noise.noise(timeElapsed * 5.0E-4, 10.0);
            float yawOffset = yawNoise * 25.0F * this.noiseFactor;
            float pitchOffset = pitchNoise * 25.0F * this.noiseFactor;
            float finalTargetYaw = targetYaw + yawOffset;
            float finalTargetPitch = targetPitch + pitchOffset;
            float totalDiff = Math.abs(yawDiff) + Math.abs(pitchDiff);
            if (totalDiff < 10.0F) {
               this.noiseFactor = Math.max(0.0F, this.noiseFactor - 0.05F);
            }

            handler.rotate(
               new Rotation(targetYaw, Math.clamp(targetPitch, -90.0F, 90.0F)),
               moveCorrection,
               yawSpeed * 25.0F,
               pitchSpeed * 25.0F,
               MathUtility.random(5.0, 50.0),
               RotationPriority.TO_TARGET
            );
         }

         if (this.rotationMode.is(this.spookyTimeRotation)) {
            this.spookyTimeRotationMode.rotate(this, handler, targetedEntity, moveCorrection);
         }

         if (this.rotationMode.is(this.oneTickRotation)) {
            this.oneTickRotationMode.rotate(this, handler, targetedEntity, moveCorrection);
         }

         if (this.rotationMode.is(this.reallyWorldRotation)) {
            this.reallyWorldRotationMode.rotate(this, handler, targetedEntity, moveCorrection);
         }

         if (this.rotationMode.is(this.slimeWorldRotation)) {
            this.slimeWorldRotationMode.rotate(this, handler, targetedEntity, moveCorrection);
         }

         if (this.rotationMode.is(this.slothRotation)) {
            this.slothRotationMode.rotate(this, handler, targetedEntity, moveCorrection);
         }
      }
   }

   private boolean canSeeTarget(LivingEntity target) {
      Rotation currentRotation = Rockstar.getInstance().getRotationHandler().getCurrentRotation();
      return MathUtility.canTraceWithBlock(
         this.attackDistance.getCurrentValue(), currentRotation.getYaw(), currentRotation.getPitch(), mc.player, target, !this.noHitInv.isEnabled()
      );
   }

   private boolean shouldSwapHandForHit() {
      if (mc.player == null || !mc.player.isUsingItem()) {
         return false;
      } else {
         return !this.offhandHit.isSelected() ? mc.player.getActiveHand() == Hand.MAIN_HAND : mc.player.getActiveHand() == Hand.MAIN_HAND;
      }
   }

   private long getAttackDelayMs() {
      int min = Math.round(this.minCps.getCurrentValue());
      int max = Math.round(this.maxCps.getCurrentValue());
      if (min > max) {
         int swap = min;
         min = max;
         max = swap;
      }

      int cps = (int)Math.floor(MathUtility.random(min, max + 1));
      return Math.max(1L, 1000L / Math.max(1, cps));
   }

   public float getGCDValue() {
      double sensitivity = (Double)mc.options.getMouseSensitivity().getValue();
      double value = sensitivity * 0.6 + 0.2;
      double result = Math.pow(value, 3.0) * 0.8;
      return (float)result * 0.15F;
   }

   public float getSensitivity(float rot) {
      return this.getDeltaMouse(rot) * this.getGCDValue();
   }

   public float getDeltaMouse(float delta) {
      return Math.round(delta / this.getGCDValue());
   }

   public boolean shouldPreventSprinting() {
      LivingEntity target = Rockstar.getInstance().getTargetManager().getCurrentTarget() instanceof LivingEntity living ? living : null;
      if (target == null || mc.player == null) {
         return false;
      }

      if (this.styleAttack.is(this.fastPvp)) {
         return false;
      }

      Criticals criticals = Rockstar.getInstance().getModuleManager().getModule(Criticals.class);
      boolean predict = criticals.isEnabled() && (criticals.canCritical() || mc.player.isOnGround())
         || !mc.player.isOnGround() && FallingPlayer.fromPlayer(mc.player).findFall(CombatUtility.getFallDistance(target));
      return this.shouldWaitForCritical(target)
         && (
            predict
               || CombatUtility.canPerformCriticalHit(target, true)
               || !this.attackTimer.finished(!ServerUtility.isHW() && !ServerUtility.isST() ? 50L : (long)MathUtility.random(50.0, 150.0))
         );
   }

   private boolean inRange(LivingEntity target) {
      return MathHelper.sqrt((float)mc.player.squaredDistanceTo(RotationMath.getNearestPoint(target))) > this.attackDistance.getCurrentValue();
   }

   public boolean isRotationTargetValid(LivingEntity target) {
      return mc.player != null
         && mc.world != null
         && target != null
         && target.isAlive()
         && mc.world.hasEntity(target)
         && !this.inRange(target);
   }

   public boolean isReadyToAttackNow() {
      return this.isCooledDown();
   }

   public float getAttackDistanceValue() {
      return this.attackDistance.getCurrentValue();
   }

   public boolean isWallsEnabled() {
      return this.walls.isEnabled();
   }

   public boolean isNoHitInvEnabled() {
      return this.noHitInv.isEnabled();
   }

   public boolean isHitVectorModeEnabled() {
      return this.hitVectorMode.isSelected();
   }

   public boolean isHolyWorldRotationSelected() {
      return this.rotationMode.is(this.holyWorldRotation);
   }

   public boolean isSpookyTimeRotationSelected() {
      return this.rotationMode.is(this.spookyTimeRotation);
   }

   public boolean isFunTimeRotationSelected() {
      return this.rotationMode.is(this.funTimeRotation);
   }

   private void resetRotationModes() {
      this.holyWorldRotationMode.reset();
      this.funTimeRotationModeImpl.reset();
      this.spookyTimeRotationMode.reset();
      this.oneTickRotationMode.reset();
      this.reallyWorldRotationMode.reset();
      this.slimeWorldRotationMode.reset();
      this.slothRotationMode.reset();
   }

   @Override
   public void onEnable() {
      this.rotationStartTime = System.currentTimeMillis();
      this.noise = new PerlinNoise();
      this.noiseFactor = 1.0F;
      this.resetRotationModes();
      super.onEnable();
   }

   @Override
   public void onDisable() {
      Rockstar.getInstance().getTargetManager().reset();
      this.resetRotationModes();
      super.onDisable();
   }

   @Generated
   public ModeSetting.Value getFastPvp() {
      return this.fastPvp;
   }

   @Generated
   public ModeSetting.Value getSlowPvp() {
      return this.slowPvp;
   }

   @Generated
   public Timer getAttackTimer() {
      return this.attackTimer;
   }

   @Generated
   public int getAttacks() {
      return this.attacks;
   }
}
