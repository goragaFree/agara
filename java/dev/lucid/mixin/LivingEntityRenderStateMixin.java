package dev.lucid.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

import dev.lucid.module.impl.combat.CorpseRenderState;

/**
 * Кладёт в состояние отрисовки цвет тела, задержанного KillEffect.
 */
@Mixin(LivingEntityRenderState.class)
public abstract class LivingEntityRenderStateMixin implements CorpseRenderState {

	@Unique
	private int lucid$corpseTint;

	@Override
	public int lucid$corpseTint() {
		return this.lucid$corpseTint;
	}

	@Override
	public void lucid$setCorpseTint(int tint) {
		this.lucid$corpseTint = tint;
	}
}
