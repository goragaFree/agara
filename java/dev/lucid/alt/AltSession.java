package dev.lucid.alt;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import dev.lucid.mixin.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

/**
 * Подмена сессии на оффлайн-ник.
 *
 * <p>Клиент представляется серверу тем, что лежит в поле {@code user} у {@link Minecraft}:
 * именно оттуда игра берёт имя и UUID для рукопожатия. Поле объявлено {@code private final},
 * поэтому пишем в него через аксессор.</p>
 *
 * <p>UUID считается ровно так же, как его считает сама игра в оффлайн-режиме — хэшем от
 * строки с именем. Если выдумать свой, сервер увидит другого игрока, и инвентарь с
 * прогрессом не совпадут между заходами.</p>
 *
 * <p>Токен пустой: на лицензионные сервера это не пустит и не должно. Работает на серверах
 * в оффлайн-режиме.</p>
 */
public final class AltSession {

	/** Приставка, по которой игра строит оффлайн-UUID. Менять нельзя — сервер считает так же. */
	private static final String OFFLINE_PREFIX = "OfflinePlayer:";

	private AltSession() {
	}

	/** Ник текущей сессии. */
	public static String currentName() {
		return Minecraft.getInstance().getUser().getName();
	}

	/**
	 * Переключает сессию на указанный ник.
	 *
	 * @return {@code false}, если ник не проходит проверку
	 */
	public static boolean apply(String name) {
		if (!AltManager.isValid(name)) {
			return false;
		}

		UUID uuid = offlineUuid(name);
		User user = new User(name, uuid, "", Optional.empty(), Optional.empty());

		((MinecraftAccessor) Minecraft.getInstance()).lucid$setUser(user);

		return true;
	}

	public static UUID offlineUuid(String name) {
		return UUID.nameUUIDFromBytes((OFFLINE_PREFIX + name).getBytes(StandardCharsets.UTF_8));
	}
}
