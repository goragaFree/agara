package dev.lucid.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import dev.lucid.module.impl.render.Removals;

/**
 * Вырез блоков из меша чанка.
 *
 * <p>Сборщик геометрии пропускает блок, если его {@code RenderShape} —
 * {@code INVISIBLE}. Это самая дешёвая точка: геометрия вообще не строится,
 * а не рисуется каждый кадр впустую.</p>
 *
 * <p>Коллизия, луч взгляда и серверная сторона остаются как были: блок
 * невидим, но всё ещё существует.</p>
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public class BlockStateBaseMixin {

	@Inject(method = "getRenderShape", at = @At("HEAD"), cancellable = true)
	private void lucid$hideBlocks(CallbackInfoReturnable<RenderShape> cir) {
		if (Removals.hideBlock((BlockState) (Object) this)) {
			cir.setReturnValue(RenderShape.INVISIBLE);
		}
	}
}
