package dev.lucid.alt;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Список сохранённых ников.
 *
 * <p>Лежит отдельным файлом {@code .minecraft/config/lucid_alts.json}, а не внутри общего
 * конфига: ники не имеют отношения ни к модулям, ни к настройкам, и мешать их в один файл
 * значит связывать две несвязанные вещи.</p>
 *
 * <pre>{@code
 * { "alts": ["Notch", "Herobrine"] }
 * }</pre>
 */
public final class AltManager {

	private static final String FILE_NAME = "lucid_alts.json";

	private static final String TEMP_FILE_NAME = FILE_NAME + ".tmp";

	private static final String KEY_ALTS = "alts";

	/** Ровно те символы, которые разрешает сама игра. */
	private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

	private static final Gson GSON = new GsonBuilder()
			.setPrettyPrinting()
			.disableHtmlEscaping()
			.create();

	private static final AltManager INSTANCE = new AltManager();

	private final Path file;

	private final List<String> names = new ArrayList<>();

	private boolean loaded;

	private AltManager() {
		this.file = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	public static AltManager get() {
		return INSTANCE;
	}

	/** Проходит ли ник по правилам игры: три-шестнадцать букв, цифр и подчёркиваний. */
	public static boolean isValid(String name) {
		return name != null && VALID_NAME.matcher(name).matches();
	}

	/** Список только для чтения. Читается с диска при первом обращении. */
	public List<String> names() {
		this.ensureLoaded();

		return Collections.unmodifiableList(this.names);
	}

	/**
	 * Добавляет ник в конец списка.
	 *
	 * @return {@code false}, если ник не проходит проверку или уже есть в списке
	 */
	public boolean add(String name) {
		this.ensureLoaded();

		if (!isValid(name) || this.names.contains(name)) {
			return false;
		}

		this.names.add(name);
		this.save();

		return true;
	}

	public void remove(String name) {
		this.ensureLoaded();

		if (this.names.remove(name)) {
			this.save();
		}
	}

	private void ensureLoaded() {
		if (this.loaded) {
			return;
		}

		this.loaded = true;

		if (!Files.isRegularFile(this.file)) {
			return;
		}

		try (Reader reader = Files.newBufferedReader(this.file, StandardCharsets.UTF_8)) {
			JsonElement parsed = JsonParser.parseReader(reader);

			if (!parsed.isJsonObject()) {
				return;
			}

			JsonObject root = parsed.getAsJsonObject();

			if (!root.has(KEY_ALTS) || !root.get(KEY_ALTS).isJsonArray()) {
				return;
			}

			for (JsonElement element : root.getAsJsonArray(KEY_ALTS)) {
				String name = element.getAsString();

				if (isValid(name) && !this.names.contains(name)) {
					this.names.add(name);
				}
			}
		} catch (IOException | RuntimeException exception) {
			// Битый файл не должен ронять игру: начинаем с пустого списка.
			this.names.clear();
		}
	}

	private void save() {
		JsonArray array = new JsonArray();

		for (String name : this.names) {
			array.add(name);
		}

		JsonObject root = new JsonObject();
		root.add(KEY_ALTS, array);

		try {
			Path directory = this.file.getParent();

			if (directory != null) {
				Files.createDirectories(directory);
			}

			Path temp = this.file.resolveSibling(TEMP_FILE_NAME);

			try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
				GSON.toJson(root, writer);
			}

			Files.move(temp, this.file, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException exception) {
			// Не сохранилось — не повод падать.
		}
	}
}
