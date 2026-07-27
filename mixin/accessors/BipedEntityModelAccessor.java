package moscow.rockstar.mixin.accessors;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BipedEntityModel.class)
public interface BipedEntityModelAccessor {
   @Accessor("head")
   ModelPart rockstar$getHead();

   @Accessor("hat")
   ModelPart rockstar$getHat();

   @Accessor("body")
   ModelPart rockstar$getBody();

   @Accessor("rightArm")
   ModelPart rockstar$getRightArm();

   @Accessor("leftArm")
   ModelPart rockstar$getLeftArm();

   @Accessor("rightLeg")
   ModelPart rockstar$getRightLeg();

   @Accessor("leftLeg")
   ModelPart rockstar$getLeftLeg();
}
