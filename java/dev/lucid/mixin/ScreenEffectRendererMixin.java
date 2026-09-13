package dev.lucid.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import dev.lucid.module.impl.render.Removals;

/**
 * Оверлей огня от первого лица.
 *
 * <p>В 26.2 ваниль рисует оранжевые квады через статический {@code submitFire},
 * а не {@code renderFire}. Глушим его целиком — урон и пламя на модели остаются.</p>
 */
@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {

	@Inject(method = "submitFire", at = @At("HEAD"), cancellable = true)
	private static void lucid$hideFire(
			PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			TextureAtlasSprite sprite,
			CallbackInfo ci) {
		if (Removals.hideFire()) {
			ci.cancel();
		}
	}
}
