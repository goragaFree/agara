package moscow.rockstar.mixin.minecraft.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import moscow.rockstar.Rockstar;
import moscow.rockstar.systems.modules.modules.visuals.Ambience;
import moscow.rockstar.utility.colors.ColorRGBA;
import moscow.rockstar.utility.render.SkyboxRenderer;
import net.minecraft.client.render.SkyRendering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRendering.class)
public class SkyRenderingMixin {
   @Inject(method = "renderSky(FFF)V", at = @At("HEAD"), cancellable = true)
   private void renderCustomSkybox(float red, float green, float blue, CallbackInfo info) {
      Ambience ambience = this.getAmbience();
      if (ambience != null && ambience.shouldRenderCustomSkybox()) {
         ColorRGBA color = ambience.shouldTintSky() ? ambience.getResolvedSkyColor() : ColorRGBA.WHITE;
         if (ambience.hasCustomSkybox()) {
            SkyboxRenderer.render(ambience.getSkyboxTexture(), color);
         }

         if (ambience.hasShaderSkybox()) {
            float time = (float)(System.currentTimeMillis() % 100000000L) / 1000.0F;
            float opacity = ambience.hasCustomSkybox() ? ambience.getShaderOpacity() : 1.0F;
            SkyboxRenderer.render(ambience.getSkyShaderProgram(), color, time, opacity);
         }

         info.cancel();
      }
   }

   @Inject(method = "close()V", at = @At("HEAD"))
   private void closeCustomSkybox(CallbackInfo info) {
      SkyboxRenderer.close();
   }

   @Redirect(
      method = "renderStars(Lnet/minecraft/client/render/Fog;FLnet/minecraft/client/util/math/MatrixStack;)V",
      at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderColor(FFFF)V", ordinal = 0)
   )
   private void redirectStarColor(float red, float green, float blue, float alpha) {
      Ambience ambience = this.getAmbience();
      if (ambience != null && ambience.shouldTintStars()) {
         ColorRGBA color = ambience.getResolvedStarsColor();
         RenderSystem.setShaderColor(color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F, color.getAlpha() / 255.0F);
      } else {
         RenderSystem.setShaderColor(red, green, blue, alpha);
      }
   }

   private Ambience getAmbience() {
      return Rockstar.getInstance().getModuleManager() == null ? null : Rockstar.getInstance().getModuleManager().getModule(Ambience.class);
   }
}
