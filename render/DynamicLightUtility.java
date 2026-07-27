package moscow.rockstar.utility.render;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

public final class DynamicLightUtility {
   private static volatile boolean active;
   private static volatile double x;
   private static volatile double y;
   private static volatile double z;
   private static volatile float radius;
   private static volatile float light;

   public static void set(double x, double y, double z, float radius, float light) {
      DynamicLightUtility.x = x;
      DynamicLightUtility.y = y;
      DynamicLightUtility.z = z;
      DynamicLightUtility.radius = radius;
      DynamicLightUtility.light = light;
      active = true;
   }

   public static void clear() {
      active = false;
   }

   public static int apply(BlockPos pos, int packedLight) {
      if (active && pos != null && !(radius <= 0.0F) && !(light <= 0.0F)) {
         int sky = packedLight >> 20 & 15;
         int block = packedLight >> 4 & 15;
         int dynamic = computeLight(pos);
         return dynamic <= block ? packedLight : sky << 20 | dynamic << 4;
      } else {
         return packedLight;
      }
   }

   private static int computeLight(BlockPos pos) {
      double dx = pos.getX() + 0.5 - x;
      double dy = pos.getY() + 0.5 - y;
      double dz = pos.getZ() + 0.5 - z;
      double distanceSq = dx * dx + dz * dz;
      double radiusSq = radius * radius;
      if (!(distanceSq > radiusSq) && !(Math.abs(dy) > radius)) {
         double falloff = 1.0 - Math.sqrt(distanceSq) / radius;
         return MathHelper.clamp((int)Math.round(falloff * light), 0, 15);
      } else {
         return 0;
      }
   }

   private DynamicLightUtility() {
   }
}
