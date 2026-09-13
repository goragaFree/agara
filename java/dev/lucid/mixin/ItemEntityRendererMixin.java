package dev.lucid.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;

import dev.lucid.module.impl.render.ItemPhysic;

/**
 * Подмена отрисовки выброшенных предметов.
 *
 * <p>В 26.2 сущность на submit уже не приходит — только {@link ItemEntityRenderState}.
 * Углы и {@code onGround} снимаем в extract, а submit при включённом модуле
 * полностью заменяет ванильный подскок и спин.</p>
 */
@Mixin(ItemEntityRenderer.class)
public class ItemEntityRendererMixin {

	@Shadow
	@Final
	private RandomSource random;

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void lucid$extractItemPhysic(
			ItemEntity entity,
			ItemEntityRenderState state,
			float partialTicks,
			CallbackInfo ci) {
		ItemPhysic module = ItemPhysic.getInstance();

		if (module != null) {
			module.capture(entity, state, partialTicks);
		}
	}

	@Inject(method = "submit", at = @At("HEAD"), cancellable = true)
	private void lucid$submitItemPhysic(
			ItemEntityRenderState state,
			PoseStack poseStack,
			SubmitNodeCollector collector,
			CameraRenderState camera,
			CallbackInfo ci) {
		ItemPhysic module = ItemPhysic.getInstance();

		if (module == null) {
			return;
		}

		if (module.submit(state, poseStack, collector, this.random)) {
			ci.cancel();
		}
	}
}
