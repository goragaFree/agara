package dev.lucid.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;

import dev.lucid.module.impl.combat.CorpseRenderState;
import dev.lucid.module.impl.combat.KillEffect;
import dev.lucid.module.impl.render.SeeInvisible;

/**
 * Ваниль прячет сущность флагом {@code isInvisibleToPlayer}: тогда
 * {@code forceTransparent} ложный и слой не выбирается.
 *
 * <p>Игроков рисует {@code AvatarRenderer}, он может обойти {@code submit}
 * родителя. Флаг снимаем на {@code extractRenderState} — его зовут все
 * наследники через super, и дальше ваниль сама идёт путём призрака.</p>
 *
 * <p>Тем же путём тает тело убитого в KillEffect: другого способа дать ванильной
 * модели альфу в 26.2 нет — полупрозрачный слой ваниль выбирает только для
 * «призрака», то есть для невидимого, который виден лично тебе.</p>
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void lucid$seeInvisible(
			LivingEntity entity,
			LivingEntityRenderState state,
			float partialTicks,
			CallbackInfo ci) {
		if (SeeInvisible.isActive() && entity.isInvisible()) {
			state.isInvisibleToPlayer = false;
		}
	}

	/**
	 * Считает альфу задержанного тела, пока сущность ещё видна.
	 *
	 * <p>Пока мечи машут и торчат, тело обычное и непрозрачное: {@code tint} равен
	 * нулю, отрисовка ванильная. На исчезновении тело уходит на полупрозрачный
	 * слой и тает вместе с клинками.</p>
	 */
	@Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
	private void lucid$corpseFade(
			LivingEntity entity,
			LivingEntityRenderState state,
			float partialTicks,
			CallbackInfo ci) {
		if (KillEffect.isCorpse(entity)) {
			// Ваниль держит алый свет всё время, пока счётчик смерти больше нуля.
			state.hasRedOverlay = false;
		}

		int tint = KillEffect.corpseTint(entity);

		((CorpseRenderState) state).lucid$setCorpseTint(tint);

		if (tint != 0) {
			state.isInvisible = true;
			state.isInvisibleToPlayer = false;
		}
	}

	@Inject(method = "submit", at = @At("HEAD"), require = 0)
	private void lucid$corpseBegin(
			LivingEntityRenderState state,
			PoseStack poseStack,
			SubmitNodeCollector collector,
			CameraRenderState camera,
			CallbackInfo ci) {
		KillEffect.beginSubmit(((CorpseRenderState) state).lucid$corpseTint());
	}

	@Inject(method = "submit", at = @At("RETURN"), require = 0)
	private void lucid$corpseEnd(
			LivingEntityRenderState state,
			PoseStack poseStack,
			SubmitNodeCollector collector,
			CameraRenderState camera,
			CallbackInfo ci) {
		KillEffect.endSubmit();
	}

	@ModifyArg(
			method = "submit",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/util/ARGB;multiply(II)I"
			),
			index = 0
	)
	private int lucid$seeInvisibleAlpha(int vanillaBase) {
		// Порядок важен: тело убитого сильнее, его альфа ведёт анимацию.
		return KillEffect.tintCorpse(SeeInvisible.tintGhost(vanillaBase));
	}
}
