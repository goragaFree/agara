package dev.lucid.module.impl.player;

import net.minecraft.client.Minecraft;

import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.ModeSetting;

/**
 * Прячет свой ник на экране: чат, табличка, список игроков.
 *
 * <p>Только свой ник, без списка друзей. Подмена идёт в разборе форматированной
 * строки — туда попадает почти весь видимый текст.</p>
 */
public class NameProtect extends Module {

	private static final String NAME_PROTECTED = "Protected";
	private static final String NAME_HIDDEN = "Hidden";
	private static final String NAME_LUCID = "Lucid";

	private static NameProtect instance;

	private final ModeSetting alias = this.addSetting(new ModeSetting(
			"alias", "Имя", "Чем заменить свой ник на экране",
			NAME_PROTECTED, java.util.List.of(NAME_PROTECTED, NAME_HIDDEN, NAME_LUCID)));

	public NameProtect() {
		super("name_protect", "NameProtect", Category.PLAYER,
				"Прячет свой ник на экране");
		instance = this;
	}

	public static String protect(String text) {
		if (instance == null || !instance.isEnabled() || text == null || text.isEmpty()) {
			return text;
		}

		Minecraft client = Minecraft.getInstance();

		if (client.getUser() == null) {
			return text;
		}

		String alias = instance.alias.getValue();
		String result = text;
		String account = client.getUser().getName();

		if (account != null && !account.isEmpty() && result.contains(account)) {
			result = result.replace(account, alias);
		}

		// В мире ник может отличаться от аккаунта (оффлайн / ник на сервере).
		if (client.player != null) {
			String inGame = client.player.getName().getString();

			if (inGame != null && !inGame.isEmpty() && !inGame.equals(account) && result.contains(inGame)) {
				result = result.replace(inGame, alias);
			}
		}

		return result;
	}
}
