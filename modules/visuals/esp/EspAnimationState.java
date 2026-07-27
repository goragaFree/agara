package moscow.rockstar.systems.modules.modules.visuals.esp;

import moscow.rockstar.utility.animation.base.Animation;
import moscow.rockstar.utility.animation.base.Easing;
import net.minecraft.util.math.MathHelper;

public final class EspAnimationState {
   private final Animation visibility = new Animation(500L, 0.0F, Easing.FIGMA_EASE_IN_OUT);
   private final Animation scale = new Animation(500L, 0.0F, Easing.BAKEK_SIZE);
   private final Animation screenX = new Animation(220L, 0.0F, Easing.CUBIC_OUT);
   private final Animation screenY = new Animation(220L, 0.0F, Easing.CUBIC_OUT);
   private final Animation rotation = new Animation(220L, 0.0F, Easing.CUBIC_OUT);
   private boolean screenInitialized;
   private boolean rotationInitialized;

   public float updateVisible(boolean visible) {
      this.visibility.update(visible);
      this.scale.update(visible);
      return this.visibility();
   }

   public void snapVisible() {
      this.visibility.setValue(1.0F);
      this.scale.setValue(1.0F);
   }

   public float visibility() {
      return this.visibility.getValue();
   }

   public float scale() {
      return this.scale.getValue();
   }

   public float popScale(float hiddenScale, float visibleScale) {
      return MathHelper.lerp(this.scale(), hiddenScale, visibleScale);
   }

   public void updateScreen(float x, float y) {
      if (!this.screenInitialized) {
         this.screenX.setValue(x);
         this.screenY.setValue(y);
         this.screenInitialized = true;
      } else {
         this.screenX.update(x);
         this.screenY.update(y);
      }
   }

   public float screenX() {
      return this.screenX.getValue();
   }

   public float screenY() {
      return this.screenY.getValue();
   }

   public float updateRotation(float targetDegrees) {
      if (!this.rotationInitialized) {
         this.rotation.setValue(targetDegrees);
         this.rotationInitialized = true;
         return targetDegrees;
      } else {
         float target = this.rotation.getTargetValue();
         this.rotation.update(target + MathHelper.wrapDegrees(targetDegrees - target));
         return MathHelper.wrapDegrees(this.rotation.getValue());
      }
   }

   public boolean hidden() {
      return this.visibility.getTargetValue() == 0.0F && this.visibility() <= 0.01F;
   }
}
