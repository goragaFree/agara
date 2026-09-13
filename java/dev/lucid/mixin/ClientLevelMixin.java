package dev.lucid.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

import dev.lucid.module.impl.combat.KillEffect;

/**
 * Задержка удаления трупа на время анимации добивания.
 *
 * <p>Сервер убирает тело через двадцать тиков после смерти и присылает пакет на
 * удаление. Сама анимация живёт дольше, и мечи оставались торчать в пустоте.
 * Здесь удаление откладывается: модуль сам уберёт тело, когда эффект догорит.
 * Задержка чисто клиентская и на сервер никак не влияет: там трупа уже нет,
 * лут выпал, опыт начислен.</p>
 *
 * <p>{@code require = 0} потому, что без этого крючка модуль просто работает как
 * раньше — тело исчезает по-ванильному. Падать из-за косметики хуже.</p>
 */
@Mixin(ClientLevel.class)
public class ClientLevelMixin {

	@Inject(method = "removeEntity", at = @At("HEAD"), cancellable = true, require = 0)
	private void lucid$holdCorpse(int entityId, Entity.RemovalReason reason, CallbackInfo ci) {
		if (KillEffect.holdRemoval(entityId)) {
			ci.cancel();
		}
	}
}
