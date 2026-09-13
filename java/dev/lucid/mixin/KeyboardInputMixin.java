package dev.lucid.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;

import dev.lucid.module.impl.movement.InventoryMove;

/**
 * Как в Blade: после ванильного {@code tick} подставляем WASD из GLFW.
 * Бинды не трогаем — иначе ломается и одиночка, и управление.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {

	@Inject(method = "tick", at = @At("TAIL"))
	private void lucid$inventoryMove(CallbackInfo ci) {
		Input screenInput = InventoryMove.screenInput();

		if (screenInput != null) {
			this.keyPresses = screenInput;
			this.moveVector = new Vec2(
					impulse(screenInput.left(), screenInput.right()),
					impulse(screenInput.forward(), screenInput.backward()));
		}

		if (InventoryMove.shouldStopMovement()) {
			this.moveVector = Vec2.ZERO;
			this.keyPresses = new Input(
					false, false, false, false, false, this.keyPresses.shift(), false);
		}
	}

	private static float impulse(boolean positive, boolean negative) {
		if (positive == negative) {
			return 0.0F;
		}

		return positive ? 1.0F : -1.0F;
	}
}
