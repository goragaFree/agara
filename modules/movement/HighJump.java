package moscow.rockstar.systems.modules.modules.movement;

import moscow.rockstar.systems.modules.api.ModuleCategory;
import moscow.rockstar.systems.modules.api.ModuleInfo;
import moscow.rockstar.systems.modules.impl.BaseModule;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

@ModuleInfo(name = "High Jump", category = ModuleCategory.MOVEMENT)
public class HighJump extends BaseModule {
   private boolean boosting;
   private int boostTicks;

   @Override
   public void tick() {
      if (mc.player != null && mc.world != null) {
         if (this.boosting) {
            this.boostTicks++;
            Vec3d velocity = mc.player.getVelocity();
            mc.player.setVelocity(velocity.x, 1.0, velocity.z);
            if (this.boostTicks > 4) {
               this.boosting = false;
            }
         } else {
            this.scanShulkerBoost();
         }
      }
   }

   @Override
   public void onEnable() {
   }

   @Override
   public void onDisable() {
      this.boosting = false;
      this.boostTicks = 0;
   }

   private void scanShulkerBoost() {
      Vec3d playerPos = mc.player.getPos();
      int centerX = MathHelper.floor(playerPos.x);
      int centerY = MathHelper.floor(playerPos.y);
      int centerZ = MathHelper.floor(playerPos.z);
      int verticalRange = mc.player.getVelocity().y > 1.0 ? 30 : 2;

      for (int x = centerX - 2; x <= centerX + 2; x++) {
         for (int y = centerY - verticalRange; y <= centerY + verticalRange; y++) {
            for (int z = centerZ - 2; z <= centerZ + 2; z++) {
               BlockPos pos = new BlockPos(x, y, z);
               if (mc.world.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity shulkerBox && this.canBoostFrom(playerPos, pos, shulkerBox)) {
                  this.startBoost();
                  return;
               }
            }
         }
      }
   }

   private boolean canBoostFrom(Vec3d playerPos, BlockPos pos, ShulkerBoxBlockEntity shulkerBox) {
      double dx = playerPos.x - (pos.getX() + 0.5);
      double dz = playerPos.z - (pos.getZ() + 0.5);
      double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
      double verticalDistance = Math.abs(playerPos.y - (pos.getY() + 0.5));
      double maxVerticalDistance = mc.player.getVelocity().y > 1.0 ? 30.0 : 2.0;
      float progress = shulkerBox.getAnimationProgress(1.0F);
      return horizontalDistance <= 1.5 && verticalDistance <= maxVerticalDistance && mc.player.fallDistance == 0.0F && progress > 0.0F && progress != 1.0F;
   }

   private void startBoost() {
      this.boosting = true;
      this.boostTicks = 0;
      Vec3d velocity = mc.player.getVelocity();
      mc.player.setVelocity(velocity.x, 1.0, velocity.z);
      mc.setScreen(null);
   }
}
