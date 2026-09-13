package dev.lucid.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;

import dev.lucid.client.LucidClient;
import dev.lucid.event.impl.ContainerClickEvent;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {

	@Inject(method = "handleInventoryMouseClick", at = @At("HEAD"), cancellable = true)
	private void lucid$click(
			int containerId,
			int slotId,
			int button,
			ContainerInput input,
			Player player,
			CallbackInfo ci) {
		ContainerClickEvent event = LucidClient.EVENTS.post(new ContainerClickEvent(button, input));

		if (event.isCancelled()) {
			ci.cancel();
		}
	}
}
