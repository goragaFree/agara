package moscow.rockstar.systems.modules.modules.movement;

import moscow.rockstar.systems.modules.api.ModuleCategory;
import moscow.rockstar.systems.modules.api.ModuleInfo;
import moscow.rockstar.systems.modules.impl.BaseModule;
import moscow.rockstar.systems.setting.settings.BooleanSetting;
import moscow.rockstar.systems.setting.settings.ModeSetting;
import moscow.rockstar.systems.setting.settings.SliderSetting;
import moscow.rockstar.utility.game.EntityUtility;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

@ModuleInfo(name = "Speed", category = ModuleCategory.MOVEMENT, desc = "modules.descriptions.speed")
public class Speed extends BaseModule {
   private final ModeSetting mode = new ModeSetting(this, "modules.settings.speed.mode");
   private final ModeSetting.Value entities = new ModeSetting.Value(this.mode, "modules.settings.speed.entities").select();
   private final SliderSetting speed = new SliderSetting(this, "modules.settings.speed.speed_info").min(1.0F).max(20.0F).step(0.1F).currentValue(8.0F);
   private final SliderSetting range = new SliderSetting(this, "modules.settings.speed.distance").min(0.5F).max(10.0F).step(0.1F).currentValue(3.0F);
   private final SliderSetting expand = new SliderSetting(this, "modules.settings.speed.expand").min(0.1F).max(2.0F).step(0.1F).currentValue(0.5F);
   private final BooleanSetting onlyPlayers = new BooleanSetting(this, "modules.settings.speed.only_players").enabled(true);
   private final BooleanSetting requireMoving = new BooleanSetting(this, "modules.settings.speed.require_moving").enabled(true);

   @Override
   public void tick() {
      if (mc.player != null && mc.world != null) {
         if (this.entities.isSelected()) {
            this.updateEntitySpeed();
         }

         super.tick();
      } else {
         super.tick();
      }
   }

   private void updateEntitySpeed() {
      int collisions = this.countCollisions();
      if (collisions > 0 && (!this.requireMoving.isEnabled() || EntityUtility.isPlayerMoving())) {
         double finalSpeed = this.speed.getCurrentValue() * 0.01 * collisions;
         if (!(finalSpeed <= 0.0)) {
            Entity nearest = this.findNearestEntity();
            if (nearest != null) {
               Vec3d direction = this.getDirectionToPoint(mc.player.getPos(), nearest.getPos(), finalSpeed);
               mc.player.addVelocity(direction.x, 0.0, direction.z);
            }
         }
      }
   }

   private int countCollisions() {
      int collisions = 0;
      Box expandedBox = mc.player.getBoundingBox().expand(this.expand.getCurrentValue());

      for (Entity entity : mc.world.getEntities()) {
         if (this.isValidEntity(entity) && expandedBox.intersects(entity.getBoundingBox())) {
            collisions++;
         }
      }

      return collisions;
   }

   private Entity findNearestEntity() {
      Entity nearest = null;
      double bestSq = Double.MAX_VALUE;
      double maxRangeSq = this.range.getCurrentValue() * this.range.getCurrentValue();

      for (Entity entity : mc.world.getEntities()) {
         if (this.isValidEntity(entity)) {
            double dx = entity.getX() - mc.player.getX();
            double dz = entity.getZ() - mc.player.getZ();
            double distanceSq = dx * dx + dz * dz;
            if (distanceSq <= maxRangeSq && distanceSq < bestSq) {
               bestSq = distanceSq;
               nearest = entity;
            }
         }
      }

      return nearest;
   }

   private boolean isValidEntity(Entity entity) {
      return entity != mc.player && (!this.onlyPlayers.isEnabled() || entity instanceof PlayerEntity)
         ? entity instanceof LivingEntity || entity instanceof BoatEntity
         : false;
   }

   private Vec3d getDirectionToPoint(Vec3d from, Vec3d to, double speed) {
      double dx = to.x - from.x;
      double dz = to.z - from.z;
      double length = Math.sqrt(dx * dx + dz * dz);
      return length == 0.0 ? Vec3d.ZERO : new Vec3d(dx / length * speed, 0.0, dz / length * speed);
   }
}
