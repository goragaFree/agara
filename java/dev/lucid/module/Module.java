package dev.lucid.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import dev.lucid.Lucid;
import dev.lucid.client.LucidClient;
import dev.lucid.event.Event;
import dev.lucid.event.Subscription;
import dev.lucid.setting.Setting;

/**
 * Базовый класс модуля клиента.
 *
 * <p>Модуль — это одна функция, которую можно включить и выключить. Наследники
 * переопределяют {@link #onEnable()}, {@link #onDisable()} и {@link #onTick(Minecraft)}.</p>
 *
 * <p>Важно: рендер-модули не должны регистрировать события Fabric в {@code onEnable},
 * потому что события нельзя отписать. Подписывайтесь один раз в {@link #onRegister()}
 * и внутри коллбэка проверяйте {@link #isEnabled()}.</p>
 */
public abstract class Module {

	/** Значение GLFW_KEY_UNKNOWN — клавиша не привязана. */
	public static final int KEY_UNBOUND = -1;

	/** Ключ в реестре, в конфиге и в ключе перевода, например "item_scroller". */
	private final String id;

	/** Имя для отображения в GUI и чате. */
	private final String name;

	private final Category category;
	private final String description;

	/** Клавиша по умолчанию (GLFW-код). Игрок переназначает её в настройках управления. */
	private final int defaultKey;

	/** Создаётся и регистрируется в {@link ModuleManager} на ините клиента. */
	private KeyMapping keyMapping;

	private boolean enabled;

	/** Скрыть из HUD-списка активных модулей. */
	private boolean hidden;

	/** Настройки модуля в порядке добавления — в таком же порядке их покажет GUI. */
	private final List<Setting<?>> settings = new ArrayList<>();

	/**
	 * Подписки модуля на события. Заводятся один раз в конструкторе через {@link #listen},
	 * а в шину попадают и убираются из неё вместе с включением модуля.
	 */
	private final List<Subscription<? extends Event>> subscriptions = new ArrayList<>();

	protected Module(String id, String name, Category category, String description, int defaultKey) {
		this.id = id;
		this.name = name;
		this.category = category;
		this.description = description;
		this.defaultKey = defaultKey;
	}

	protected Module(String id, String name, Category category, String description) {
		this(id, name, category, description, KEY_UNBOUND);
	}

	protected Module(String id, String name, Category category) {
		this(id, name, category, "", KEY_UNBOUND);
	}

	// ------------------------------------------------------------------ хуки

	/** Вызывается один раз при регистрации в {@link ModuleManager}. Здесь подписки на события. */
	public void onRegister() {
	}

	protected void onEnable() {
	}

	protected void onDisable() {
	}

	/** Вызывается каждый клиентский тик, только пока модуль включён и игрок в мире. */
	public void onTick(Minecraft client) {
	}

	// ---------------------------------------------------------------- состояние

	public final void toggle() {
		this.setEnabled(!this.enabled);
	}

	public final void setEnabled(boolean value) {
		if (this.enabled == value) {
			return;
		}

		this.enabled = value;

		try {
			if (value) {
				this.onEnable();

				// Подписки живут ровно пока модуль включён — поэтому в обработчиках
				// больше не нужна проверка isEnabled(), которую легко забыть.
				for (Subscription<? extends Event> subscription : this.subscriptions) {
					LucidClient.EVENTS.subscribe(subscription);
				}
			} else {
				// Сначала отключаем от шины, потом гасим: иначе onDisable может
				// разобрать состояние, которое ещё ждёт пришедшее событие.
				LucidClient.EVENTS.unsubscribeAll(this);
				this.onDisable();
			}
		} catch (Throwable t) {
			// Один сломанный модуль не должен ронять игру.
			this.enabled = false;
			LucidClient.EVENTS.unsubscribeAll(this);
			Lucid.LOGGER.error("Module '{}' threw on toggle", this.id, t);
			return;
		}

		// Без этого конфиг считал бы, что менялись только настройки, и не замечал самое частое
		// действие в клиенте — клик по плитке модуля.
		LucidClient.CONFIG.markDirty();

		Lucid.LOGGER.debug("Module '{}' -> {}", this.id, value ? "on" : "off");
		this.announce();
	}

	/** Короткое сообщение в чат при переключении. */
	private void announce() {
		Minecraft client = mc();

		if (client.player == null) {
			return;
		}

		client.player.sendSystemMessage(
				Component.literal(this.name + (this.enabled ? " \u00a7aON" : " \u00a7cOFF"))
		);
	}

	// ------------------------------------------------------------------ события

	/**
	 * Подписать модуль на событие. Вызывать из конструктора:
	 *
	 * <pre>{@code
	 * this.listen(MouseDragEvent.class, this::onDrag);
	 * }</pre>
	 *
	 * <p>Сама подписка в шину происходит позже, при включении модуля, и снимается при
	 * выключении. Выключенный модуль событий просто не видит.</p>
	 *
	 * <p>Исключение — события рендера и HUD самого Fabric: от них отписаться нельзя,
	 * там по-прежнему нужна ручная проверка {@link #isEnabled()}.</p>
	 */
	protected final <E extends Event> void listen(Class<E> type, Consumer<E> handler) {
		this.subscriptions.add(new Subscription<>(type, handler, this));
	}

	// ------------------------------------------------------------------ настройки

	/**
	 * Добавляет настройку и возвращает её же — удобно сразу сохранить в поле:
	 *
	 * <pre>{@code
	 * private final SliderSetting gamma = this.addSetting(
	 *         new SliderSetting("gamma", "Яркость", 15.0, 1.0, 20.0, 0.5));
	 * }</pre>
	 */
	protected final <S extends Setting<?>> S addSetting(S setting) {
		for (Setting<?> existing : this.settings) {
			if (existing.getId().equals(setting.getId())) {
				throw new IllegalStateException(
						"Duplicate setting id '" + setting.getId() + "' in module '" + this.id + "'");
			}
		}

		this.settings.add(setting);
		return setting;
	}

	public final List<Setting<?>> getSettings() {
		return Collections.unmodifiableList(this.settings);
	}

	/** Только те, что сейчас имеют смысл — именно их рисует GUI. */
	public final List<Setting<?>> getVisibleSettings() {
		List<Setting<?>> result = new ArrayList<>();

		for (Setting<?> setting : this.settings) {
			if (setting.isVisible()) {
				result.add(setting);
			}
		}

		return result;
	}

	public final Setting<?> getSetting(String settingId) {
		for (Setting<?> setting : this.settings) {
			if (setting.getId().equals(settingId)) {
				return setting;
			}
		}

		return null;
	}

	public final boolean hasSettings() {
		return !this.settings.isEmpty();
	}

	// ------------------------------------------------------------------ геттеры

	protected static Minecraft mc() {
		return Minecraft.getInstance();
	}

	public final String getId() {
		return this.id;
	}

	public final String getName() {
		return this.name;
	}

	public final Category getCategory() {
		return this.category;
	}

	public final String getDescription() {
		return this.description;
	}

	public final int getDefaultKey() {
		return this.defaultKey;
	}

	/** Ключ перевода бинда, он же его имя в настройках управления. */
	public final String getKeyTranslationKey() {
		return "key." + Lucid.MOD_ID + "." + this.id;
	}

	public final KeyMapping getKeyMapping() {
		return this.keyMapping;
	}

	void setKeyMapping(KeyMapping keyMapping) {
		this.keyMapping = keyMapping;
	}

	public final boolean isEnabled() {
		return this.enabled;
	}

	public final boolean isHidden() {
		return this.hidden;
	}

	protected final void setHidden(boolean hidden) {
		this.hidden = hidden;
	}

	@Override
	public String toString() {
		return this.name + "[" + this.category + ", " + (this.enabled ? "on" : "off") + "]";
	}
}
