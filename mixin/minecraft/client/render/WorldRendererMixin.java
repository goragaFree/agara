package moscow.rockstar.mixin.minecraft.client.render;

import moscow.rockstar.Rockstar;
import moscow.rockstar.systems.event.impl.render.Render3DEvent;
import moscow.rockstar.systems.modules.modules.visuals.CustomFog;
import moscow.rockstar.utility.interfaces.IMinecraft;
import moscow.rockstar.utility.render.DrawUtility;
import moscow.rockstar.utility.render.DynamicLightUtility;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.profiler.Profilers;
import net.minecraft.world.BlockRenderView;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin implements IMinecraft {
   @Inject(
      method = "render(Lnet/minecraft/client/util/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
      at = @At("RETURN")
   )
   private void render(
      ObjectAllocator allocator,
      RenderTickCounter tickCounter,
      boolean renderBlockOutline,
      Camera camera,
      GameRenderer gameRenderer,
      Matrix4f positionMatrix,
      Matrix4f projectionMatrix,
      CallbackInfo ci
   ) {
      Profilers.get().swap(Rockstar.MOD_ID + "_renderWorld");
      this.applyCustomFogDepthBlur(camera);
      MatrixStack matrices = new MatrixStack();
      matrices.multiplyPositionMatrix(positionMatrix);
      Rockstar.getInstance()
         .getEventManager()
         .triggerEvent(new Render3DEvent(matrices, positionMatrix, projectionMatrix, camera, tickCounter.getTickDelta(false)));
   }

   @Inject(method = "getLightmapCoordinates(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;)I", at = @At("RETURN"), cancellable = true)
   private static void applyDynamicFullbright(BlockRenderView world, BlockState state, BlockPos pos, CallbackInfoReturnable<Integer> info) {
      info.setReturnValue(DynamicLightUtility.apply(pos, info.getReturnValueI()));
   }

   private void applyCustomFogDepthBlur(Camera camera) {
      CustomFog customFog = Rockstar.getInstance().getModuleManager().getModule(CustomFog.class);
      if (customFog != null && DrawUtility.fogBlurPost != null && customFog.shouldApplyDepthBlur(camera)) {
         float fogStart = customFog.getDistance().getFirstValue();
         float fogEnd = customFog.getDistance().getSecondValue();
         if (!(fogEnd <= fogStart)) {
            DrawUtility.fogBlurPost
               .apply(
                  fogStart,
                  fogEnd,
                  customFog.getBlurStrength().getCurrentValue(),
                  8,
                  customFog.getBlurOffset().getCurrentValue(),
                  0.0F,
                  customFog.getNoSkyBlur().isEnabled()
               );
         }
      }
   }
}
