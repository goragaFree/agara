package dev.lucid.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;

import dev.lucid.module.impl.render.ItemPhysicRenderState;

@Mixin(ItemEntityRenderState.class)
public abstract class ItemEntityRenderStateMixin implements ItemPhysicRenderState {

	@Unique
	private boolean lucid$onGround;

	@Unique
	private float lucid$rotX;

	@Unique
	private float lucid$rotY;

	@Override
	public boolean lucid$isOnGround() {
		return this.lucid$onGround;
	}

	@Override
	public void lucid$setOnGround(boolean onGround) {
		this.lucid$onGround = onGround;
	}

	@Override
	public float lucid$rotX() {
		return this.lucid$rotX;
	}

	@Override
	public void lucid$setRotX(float rotX) {
		this.lucid$rotX = rotX;
	}

	@Override
	public float lucid$rotY() {
		return this.lucid$rotY;
	}

	@Override
	public void lucid$setRotY(float rotY) {
		this.lucid$rotY = rotY;
	}
}
