package dev.lucid.util.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

import dev.lucid.Lucid;
import dev.lucid.module.Module;
import dev.lucid.module.ModuleManager;
import dev.lucid.setting.Setting;

/**
 * Сохранение и загрузка состояния клиента — какие модули включены и что выставлено в их настройках.
 *
 * <p>Файл лежит в {@code .minecraft/config/lucid.json} и выглядит так:</p>
 *
 * <pre>{@code
 * {
 *   "modules": {
 *     "gamma": { "enabled": true, "settings": { "brightness": 10.0 } },
 *     "item_scroller": { "enabled": true, "settings": { "delay": 50.0 } }
 *   }
 * }
 * }</pre>
 *
 * <p>Бинды сюда не попадают специально: клавиши зарегистрированы ванильным KeyMapping,
 * а значит сама игра уже хранит их в options.txt. Дублировать было бы хуже: два источника
 * правды рано или поздно расходятся.</p>
 *
 * <p>Класс намеренно ничего не знает о типах настроек. Превращение значения в JSON
 * живёт в самой настройке, поэтому новый тип настройки не требует правок здесь.</p>
 */
public final class ConfigManager {

	private static final String FILE_NAME = "lucid.json";

	/** Временный файл для безопасной записи. */
	private static final String TEMP_FILE_NAME = FILE_NAME + ".tmp";

	private static final String KEY_MODULES = "modules";
	private static final String KEY_ENABLED = "enabled";
	private static final String KEY_SETTINGS = "settings";

	/** Форматирование включено: файл рассчитан на то, что его будут открывать руками. */
	private static final Gson GSON = new GsonBuilder()
			.setPrettyPrinting()
			.disableHtmlEscaping()
			.create();

	private final Path file;

	/** Состояние в памяти разошлось с файлом. */
	private boolean dirty;

	/**
	 * Идёт чтение файла. Во время него настройки меняются и дёргают {@link #markDirty()},
	 * хотя причины перезаписывать файл нет — значения и так взяты из него.
	 */
	private boolean loading;

	public ConfigManager() {
		this.file = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	// ------------------------------------------------------------------ загрузка

	/**
	 * Читает файл и раскладывает значения по модулям.
	 *
	 * @return {@code false}, если читать было нечего — вызывающий сам решит, что включить по умолчанию
	 */
	public boolean load(ModuleManager modules) {
		if (!Files.isRegularFile(this.file)) {
			Lucid.LOGGER.info("Config not found, starting with defaults");
			return false;
		}

		JsonObject root;

		try (Reader reader = Files.newBufferedReader(this.file, StandardCharsets.UTF_8)) {
			JsonElement parsed = JsonParser.parseReader(reader);

			if (!parsed.isJsonObject()) {
				Lucid.LOGGER.warn("Config is not a JSON object, ignoring it");
				return false;
			}

			root = parsed.getAsJsonObject();
		} catch (Throwable t) {
			// Битый файл не повод не запустить игру. Стартуем на дефолтах.
			Lucid.LOGGER.error("Failed to read config, starting with defaults", t);
			return false;
		}

		this.loading = true;

		try {
			this.applyModules(root, modules);
		} finally {
			this.loading = false;
			this.dirty = false;
		}

		Lucid.LOGGER.info("Config loaded from {}", this.file);
		return true;
	}

	private void applyModules(JsonObject root, ModuleManager modules) {
		if (!root.has(KEY_MODULES) || !root.get(KEY_MODULES).isJsonObject()) {
			return;
		}

		JsonObject savedModules = root.getAsJsonObject(KEY_MODULES);

		for (Module module : modules.getAll()) {
			JsonElement saved = savedModules.get(module.getId());

			// Модуля не было в файле — значит он появился в новой версии клиента, оставляем его дефолты.
			if (saved == null || !saved.isJsonObject()) {
				continue;
			}

			try {
				this.applyModule(saved.getAsJsonObject(), module);
			} catch (Throwable t) {
				// Один сломанный модуль не должен уводить за собой весь конфиг.
				Lucid.LOGGER.error("Failed to load config for module '{}'", module.getId(), t);
			}
		}
	}

	private void applyModule(JsonObject saved, Module module) {
		// Сначала настройки, потом включение: onEnable у модуля должен увидеть уже свои значения,
		// а не дефолтные — иначе, например, гамма на мгновение выставится не та.
		if (saved.has(KEY_SETTINGS) && saved.get(KEY_SETTINGS).isJsonObject()) {
			JsonObject savedSettings = saved.getAsJsonObject(KEY_SETTINGS);

			for (Setting<?> setting : module.getSettings()) {
				JsonElement value = savedSettings.get(setting.getId());

				if (value != null && !value.isJsonNull()) {
					setting.load(value);
				}
			}
		}

		JsonElement enabled = saved.get(KEY_ENABLED);

		if (enabled != null && enabled.isJsonPrimitive() && enabled.getAsJsonPrimitive().isBoolean()) {
			module.setEnabled(enabled.getAsBoolean());
		}
	}

	// ------------------------------------------------------------------ сохранение

	/** Записывает файл, только если с прошлого раза что-то менялось. */
	public void saveIfDirty(ModuleManager modules) {
		if (this.dirty) {
			this.save(modules);
		}
	}

	public void save(ModuleManager modules) {
		JsonObject savedModules = new JsonObject();

		for (Module module : modules.getAll()) {
			JsonObject saved = new JsonObject();
			saved.addProperty(KEY_ENABLED, module.isEnabled());

			if (module.hasSettings()) {
				JsonObject savedSettings = new JsonObject();

				// Именно getSettings, а не getVisibleSettings: скрытая сейчас настройка всё равно
				// хранит значение, и терять его из-за положения другого переключателя нельзя.
				for (Setting<?> setting : module.getSettings()) {
					savedSettings.add(setting.getId(), setting.save());
				}

				saved.add(KEY_SETTINGS, savedSettings);
			}

			savedModules.add(module.getId(), saved);
		}

		JsonObject root = new JsonObject();
		root.add(KEY_MODULES, savedModules);

		try {
			this.write(root);
			this.dirty = false;
			Lucid.LOGGER.info("Config saved to {}", this.file);
		} catch (Throwable t) {
			Lucid.LOGGER.error("Failed to write config to {}", this.file, t);
		}
	}

	/**
	 * Пишет через временный файл.
	 *
	 * <p>Если игра упадёт прямо во время записи, при прямой записи в lucid.json остался бы
	 * обрезанный JSON — то есть потеря всех настроек. Подмена готового файла такого не допускает.</p>
	 */
	private void write(JsonObject root) throws IOException {
		Path directory = this.file.getParent();

		if (directory != null) {
			Files.createDirectories(directory);
		}

		Path temp = this.file.resolveSibling(TEMP_FILE_NAME);

		try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
			GSON.toJson(root, writer);
		}

		try {
			Files.move(temp, this.file,
					StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			// Некоторые файловые системы так не умеют — переезжаем обычным способом.
			Files.move(temp, this.file, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	// ------------------------------------------------------------------ состояние

	/**
	 * Помечает, что есть несохранённые изменения.
	 *
	 * <p>Зовётся из модулей и настроек. Сам файл пишется позже и один раз, а не на каждое
	 * движение слайдера: протаскивание мышью дало бы десятки записей на диск в секунду.</p>
	 */
	public void markDirty() {
		if (!this.loading) {
			this.dirty = true;
		}
	}

	public boolean isDirty() {
		return this.dirty;
	}

	public Path getFile() {
		return this.file;
	}
}
