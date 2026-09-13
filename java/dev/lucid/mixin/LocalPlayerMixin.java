package dev.lucid.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import dev.lucid.client.LucidClient;
import dev.lucid.event.impl.ScreenCloseEvent;

/**
 * Закрытие контейнера. Событие можно отменить — тогда экран не закроется сразу.
 */
@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {

	@Inject(method = "closeContainer", at = @At("HEAD"), cancellable = true)
	private void lucid$closeContainer(CallbackInfo ci) {
		ScreenCloseEvent event = LucidClient.EVENTS.post(
				new ScreenCloseEvent(Minecraft.getInstance().gui.screen()));

		if (event.isCancelled()) {
			ci.cancel();
		}
	}
}
