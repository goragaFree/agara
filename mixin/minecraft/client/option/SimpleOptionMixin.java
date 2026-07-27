package moscow.rockstar.mixin.minecraft.client.option;

import moscow.rockstar.Rockstar;
import moscow.rockstar.systems.modules.modules.visuals.Ambience;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SimpleOption.class)
public class SimpleOptionMixin<T> {
   @Shadow
   @Final
   Text field_38280;
   @Shadow
   T field_37868;

   @Inject(method = "getValue()Ljava/lang/Object;", at = @At("HEAD"), cancellable = true)
   public void getGammaValue(CallbackInfoReturnable<Double> cir) {
      if (Rockstar.getInstance().getModuleManager() != null) {
         Ambience ambienceModule = Rockstar.getInstance().getModuleManager().getModule(Ambience.class);
         if (ambienceModule.shouldUseGammaBrightness() && this.field_38280.equals(Text.translatable("options.gamma"))) {
            cir.setReturnValue(1337.0);
         }
      }
   }

   @Inject(method = "setValue(Ljava/lang/Object;)V", at = @At("HEAD"), cancellable = true)
   public void setGammaValue(T value, CallbackInfo ci) {
      if (this.field_38280.equals(Text.translatable("options.gamma"))) {
         this.field_37868 = value;
         ci.cancel();
      }
   }
}
