package dev.lucid.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;

import dev.lucid.module.impl.render.Removals;

/**
 * Единая точка отсечения клиентских частиц.
 *
 * <p>Все партиклы проходят через {@code createParticle}. Если {@link Removals}
 * говорит, что тип скрыт, возвращаем null: частица не создаётся, а сами эффекты,
 * огонь и урон остаются как были — вырез чисто визуальный.</p>
 */
@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {

	@Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
	private void lucid$hideParticles(
			ParticleOptions options,
			double x,
			double y,
			double z,
			double xd,
			double yd,
			double zd,
			CallbackInfoReturnable<Particle> cir) {
		if (Removals.hideParticle(options)) {
			cir.setReturnValue(null);
		}
	}
}
