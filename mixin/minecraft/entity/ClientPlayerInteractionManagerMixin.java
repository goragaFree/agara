package moscow.rockstar.mixin.minecraft.entity;

import moscow.rockstar.Rockstar;
import moscow.rockstar.systems.event.impl.game.BlockBreakEvent;
import moscow.rockstar.systems.event.impl.game.InternalAttackEvent;
import moscow.rockstar.systems.event.impl.game.StartBreakBlockEvent;
import moscow.rockstar.systems.modules.modules.player.NoInteract;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {
   @Shadow
   @Final
   private MinecraftClient field_3712;

   @Inject(method = "attackEntity(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
   private void rockstar$critPre(PlayerEntity player, Entity target, CallbackInfo ci) {
      InternalAttackEvent event = new InternalAttackEvent(target);
      Rockstar.getInstance().getEventManager().triggerEvent(event);
      if (event.isCancelled()) {
         ci.cancel();
      }
   }

   @Inject(method = "breakBlock(Lnet/minecraft/util/math/BlockPos;)Z", at = @At("RETURN"), cancellable = true)
   public void breakBlockHook(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
      BlockBreakEvent event = new BlockBreakEvent(pos);
      Rockstar.getInstance().getEventManager().triggerEvent(event);
      if (event.isCancelled()) {
         cir.setReturnValue(false);
      }
   }

   @Inject(method = "attackBlock(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;)Z", at = @At("HEAD"), cancellable = true)
   private void onAttackBlock(BlockPos blockPos, Direction direction, CallbackInfoReturnable<Boolean> info) {
      StartBreakBlockEvent event = new StartBreakBlockEvent(blockPos);
      Rockstar.getInstance().getEventManager().triggerEvent(event);
      if (event.isCancelled()) {
         info.cancel();
      }
   }

   @Inject(
      method = "interactBlock(Lnet/minecraft/client/network/ClientPlayerEntity;Lnet/minecraft/util/Hand;Lnet/minecraft/util/hit/BlockHitResult;)Lnet/minecraft/util/ActionResult;",
      at = @At("HEAD"),
      cancellable = true
   )
   public void preventInteraction(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
      if (this.field_3712.world != null) {
         NoInteract noInteractModule = Rockstar.getInstance().getModuleManager().getModule(NoInteract.class);
         Block block = this.field_3712.world.getBlockState(hitResult.getBlockPos()).getBlock();
         if (noInteractModule.isEnabled() && noInteractModule.shouldPreventInteract(block)) {
            cir.setReturnValue(ActionResult.PASS);
         }
      }
   }
}
