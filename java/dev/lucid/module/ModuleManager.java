package dev.lucid.module;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.mojang.blaze3d.platform.InputConstants;

import dev.lucid.screen.gui.ClickGuiScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import dev.lucid.Lucid;
import dev.lucid.client.LucidClient;
import dev.lucid.event.impl.TickEvent;
import dev.lucid.module.impl.combat.KillEffect;
import dev.lucid.module.impl.movement.AutoSprint;
import dev.lucid.module.impl.movement.InventoryMove;
import dev.lucid.module.impl.player.ItemScroller;
import dev.lucid.module.impl.player.NameProtect;
import dev.lucid.module.impl.render.Ambience;
import dev.lucid.module.impl.render.BlockOverlay;
import dev.lucid.module.impl.render.Esp;
import dev.lucid.module.impl.render.Removals;
import dev.lucid.module.impl.render.Gamma;
import dev.lucid.module.impl.render.ItemPhysic;
import dev.lucid.module.impl.render.Saturation;
import dev.lucid.module.impl.render.SeeInvisible;
import dev.lucid.module.impl.render.SmoothCamera;
import dev.lucid.module.impl.render.ViewModel;
import dev.lucid.module.impl.render.Watermark;

/**
 * Реестр модулей, их бинды и тик-луп.
 *
 * <p>Создаётся один раз в {@code onInitializeClient}. Клавиши регистрируются ванильным
 * {@link KeyMapping}, поэтому они сами появляются в Настройки → Управление, переназначаются
 * игроком и сохраняются в options.txt — свой конфиг для этого не нужен.</p>
 */
public final class ModuleManager {

	/** Группа в списке управления. Ключ перевода: key.category.lucid.modules */
	public static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath(Lucid.MOD_ID, "modules")
	);

	/** id -> модуль, порядок регистрации сохраняется. */
	private final Map<String, Module> modules = new LinkedHashMap<>();

	/** Открывает ClickGUI. Не привязан к модулю, поэтому живёт отдельным полем. */
	private KeyMapping clickGuiKey;

	private boolean initialized;

	/** Регистрирует модули с их биндами и вешает тик-луп. */
	public void init() {
		if (this.initialized) {
			throw new IllegalStateException("ModuleManager already initialized");
		}

		this.initialized = true;

		// --- регистрация модулей ---
		this.register(new BlockOverlay());
		this.register(new Esp());
		this.register(new AutoSprint());
		this.register(new InventoryMove());
		this.register(new Watermark());
		this.register(new Gamma());
		this.register(new ItemScroller());
		this.register(new Ambience());
		this.register(new Removals());
		this.register(new SmoothCamera());
		this.register(new ItemPhysic());
		this.register(new Saturation());
		this.register(new SeeInvisible());
		this.register(new ViewModel());
		this.register(new NameProtect());
		this.register(new KillEffect());

		// Состояние берём из файла. Его нет только при первом запуске — тогда включаем дефолты.
		if (!LucidClient.CONFIG.load(this)) {
			this.applyDefaults();

			// Сразу пишем файл: иначе непонятно, конфиг ещё не нужен или запись сломана.
			LucidClient.CONFIG.save(this);
		}

		this.clickGuiKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key." + Lucid.MOD_ID + ".clickgui",
				InputConstants.Type.KEYSYM,
				InputConstants.KEY_RSHIFT,
				KEY_CATEGORY
		));

		ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);

		Lucid.LOGGER.info("Registered {} modules", this.modules.size());
	}

	/** Что работает сразу после установки клиента, пока игрок ничего не настраивал. */
	private void applyDefaults() {
		this.get(BlockOverlay.class).setEnabled(true);
		this.get(Watermark.class).setEnabled(true);
	}

	private void register(Module module) {
		Module previous = this.modules.putIfAbsent(module.getId(), module);

		if (previous != null) {
			throw new IllegalStateException("Duplicate module id: " + module.getId());
		}

		module.setKeyMapping(KeyMappingHelper.registerKeyMapping(new KeyMapping(
				module.getKeyTranslationKey(),
				InputConstants.Type.KEYSYM,
				module.getDefaultKey(),
				KEY_CATEGORY
		)));

		module.onRegister();
	}

	private void onEndTick(Minecraft client) {
		// Бинды читаем всегда, чтобы нажатия не копились в очереди вне мира.
		this.handleClickGuiKey(client);
		this.handleKeys();

		if (client.level == null || client.player == null) {
			return;
		}

		// Новый путь: модули слушают TickEvent через шину.
		LucidClient.EVENTS.post(new TickEvent(client));

		// Старый путь: прямой вызов onTick. Оставлен специально: простому
		// модулю переопределить один метод короче, чем заводить подписку.
		for (Module module : this.modules.values()) {
			if (!module.isEnabled()) {
				continue;
			}

			try {
				module.onTick(client);
			} catch (Throwable t) {
				Lucid.LOGGER.error("Module '{}' threw on tick, disabling", module.getId(), t);
				module.setEnabled(false);
			}
		}
	}

	/** RShift открывает ClickGUI. Закрывает её уже сам экран по Esc. */
	private void handleClickGuiKey(Minecraft client) {
		if (this.clickGuiKey == null) {
			return;
		}

		boolean pressed = false;

		while (this.clickGuiKey.consumeClick()) {
			pressed = true;
		}

		// В 26.2 текущий экран и setScreen переехали с Minecraft на Gui.
		if (pressed && client.gui.screen() == null) {
			client.gui.setScreen(new ClickGuiScreen());
		}
	}

	/**
	 * Разгребает очередь нажатий каждого бинда.
	 *
	 * <p>{@code consumeClick} возвращает true по разу на каждое необработанное нажатие,
	 * поэтому его вызывают в while, а не в if: если за тик нажали дважды, оба нажатия
	 * нужно вычерпать. Непривязанные клавиши никогда не стреляют, отдельная проверка не нужна.</p>
	 */
	private void handleKeys() {
		for (Module module : this.modules.values()) {
			KeyMapping keyMapping = module.getKeyMapping();

			if (keyMapping == null) {
				continue;
			}

			boolean pressed = false;

			while (keyMapping.consumeClick()) {
				pressed = true;
			}

			// Несколько нажатий за один тик — это всё равно одно переключение, иначе модуль "мигнёт".
			if (pressed) {
				module.toggle();
			}
		}
	}

	// ------------------------------------------------------------------ доступ

	public Optional<Module> get(String id) {
		return Optional.ofNullable(this.modules.get(id.toLowerCase(Locale.ROOT)));
	}

	@SuppressWarnings("unchecked")
	public <T extends Module> T get(Class<T> type) {
		for (Module module : this.modules.values()) {
			if (type.isInstance(module)) {
				return (T) module;
			}
		}

		throw new IllegalArgumentException("Module not registered: " + type.getSimpleName());
	}

	public Collection<Module> getAll() {
		return Collections.unmodifiableCollection(this.modules.values());
	}

	public List<Module> getByCategory(Category category) {
		List<Module> result = new ArrayList<>();

		for (Module module : this.modules.values()) {
			if (module.getCategory() == category) {
				result.add(module);
			}
		}

		return result;
	}

	/** Активные и не скрытые модули — для будущего HUD-списка. */
	public List<Module> getActive() {
		List<Module> result = new ArrayList<>();

		for (Module module : this.modules.values()) {
			if (module.isEnabled() && !module.isHidden()) {
				result.add(module);
			}
		}

		return result;
	}
}
