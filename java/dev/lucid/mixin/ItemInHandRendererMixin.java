package dev.lucid.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import dev.lucid.module.impl.render.ViewModel;

/**
 * Сдвиг рук от первого лица.
 *
 * <p>В 26.2 метод называется {@code submitArmWithItem}. Трансформ вешаем сразу после
 * {@code pushPose}: стек уже свой на эту руку, ванильные анимации экипа и удара
 * ещё впереди и остаются на месте.</p>
 */
@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {

	@Inject(
			method = "submitArmWithItem",
			at = @At(
					value = "INVOKE",
					target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
					shift = At.Shift.AFTER
			)
	)
	private void lucid$viewModel(
			AbstractClientPlayer player,
			float frameInterp,
			float xRot,
			InteractionHand hand,
			float attack,
			ItemStack itemStack,
			float inverseArmHeight,
			PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			int lightCoords,
			CallbackInfo ci) {
		ViewModel.apply(hand, poseStack);
	}
}
