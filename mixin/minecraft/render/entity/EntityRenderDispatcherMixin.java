package moscow.rockstar.mixin.minecraft.render.entity;

import moscow.rockstar.utility.render.DynamicLightUtility;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
   @Inject(method = "getLight(Lnet/minecraft/entity/Entity;F)I", at = @At("RETURN"), cancellable = true)
   private <E extends Entity> void rockstar$applyDynamicFullbright(E entity, float tickDelta, CallbackInfoReturnable<Integer> info) {
      BlockPos pos = BlockPos.ofFloored(entity.getClientCameraPosVec(tickDelta));
      info.setReturnValue(DynamicLightUtility.apply(pos, info.getReturnValueI()));
   }
}
