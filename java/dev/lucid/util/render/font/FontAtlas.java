package dev.lucid.util.render.font;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.resources.Identifier;

import dev.lucid.Lucid;

/**
 * Шрифт, испечённый в поле расстояний.
 *
 * <p>В атласе лежат не картинки букв, а расстояние до края контура. Поэтому буква
 * остаётся резкой на любом размере: шейдер каждый раз заново решает, где проходит
 * граница, вместо того чтобы растягивать готовые пиксели.</p>
 *
 * <p>Метрики хранятся в долях кегля, ровно как их отдаёт msdf-atlas-gen. Умножение на
 * размер шрифта делается в самый последний момент, при раскладке строки.</p>
 */
public final class FontAtlas {

	/** Что подставить вместо буквы, которой нет в атласе. */
	private static final int MISSING = '?';

	private final Identifier texture;
	private final float distanceRange;
	private final float lineHeight;
	private final float ascender;
	private final float descender;
	private final Map<Integer, Glyph> glyphs;
	private final Map<Long, Float> kerning;

	private FontAtlas(Identifier texture, JsonObject root) {
		this.texture = texture;

		JsonObject atlas = root.getAsJsonObject("atlas");
		JsonObject metrics = root.getAsJsonObject("metrics");

		this.distanceRange = atlas.get("distanceRange").getAsFloat();
		this.lineHeight = metrics.get("lineHeight").getAsFloat();
		this.ascender = metrics.get("ascender").getAsFloat();
		this.descender = metrics.get("descender").getAsFloat();

		float atlasWidth = atlas.get("width").getAsFloat();
		float atlasHeight = atlas.get("height").getAsFloat();

		// У msdf-atlas-gen начало координат обычно снизу, но бывает и сверху.
		boolean flip = !atlas.has("yOrigin") || "bottom".equals(atlas.get("yOrigin").getAsString());

		this.glyphs = new HashMap<>();

		for (JsonElement element : root.getAsJsonArray("glyphs")) {
			JsonObject entry = element.getAsJsonObject();
			int code = entry.get("unicode").getAsInt();
			float advance = entry.get("advance").getAsFloat();

			if (!entry.has("planeBounds") || !entry.has("atlasBounds")) {
				this.glyphs.put(code, Glyph.blank(advance));
				continue;
			}

			JsonObject plane = entry.getAsJsonObject("planeBounds");
			JsonObject bounds = entry.getAsJsonObject("atlasBounds");

			// В файле ось Y смотрит вверх, на экране — вниз, отсюда смена знака.
			float left = plane.get("left").getAsFloat();
			float right = plane.get("right").getAsFloat();
			float top = -plane.get("top").getAsFloat();
			float bottom = -plane.get("bottom").getAsFloat();

			float u0 = bounds.get("left").getAsFloat() / atlasWidth;
			float u1 = bounds.get("right").getAsFloat() / atlasWidth;
			float rawTop = bounds.get("top").getAsFloat() / atlasHeight;
			float rawBottom = bounds.get("bottom").getAsFloat() / atlasHeight;

			float v0 = flip ? 1.0F - rawTop : rawTop;
			float v1 = flip ? 1.0F - rawBottom : rawBottom;

			this.glyphs.put(code, new Glyph(advance, left, top, right, bottom, u0, v0, u1, v1, true));
		}

		this.kerning = new HashMap<>();

		if (root.has("kerning")) {
			for (JsonElement element : root.getAsJsonArray("kerning")) {
				JsonObject entry = element.getAsJsonObject();
				long key = key(entry.get("unicode1").getAsInt(), entry.get("unicode2").getAsInt());
				this.kerning.put(key, entry.get("advance").getAsFloat());
			}
		}
	}

	/**
	 * Читает шрифт из ресурсов мода.
	 *
	 * <p>Метрики берутся прямо из jar, а не через менеджер ресурсов: атлас клиента не
	 * должен зависеть от ресурспаков и от момента их перезагрузки. Картинка при этом
	 * остаётся обычной текстурой игры, иначе её пришлось бы грузить на видеокарту руками.</p>
	 *
	 * @param name имя без расширения, например {@code bold}
	 */
	public static FontAtlas load(String name) {
		String path = "/assets/" + Lucid.MOD_ID + "/font/" + name + ".json";

		try (InputStream stream = FontAtlas.class.getResourceAsStream(path)) {
			if (stream == null) {
				throw new IOException("файл не найден: " + path);
			}

			JsonObject root = JsonParser
					.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
					.getAsJsonObject();

			Identifier texture = Identifier.fromNamespaceAndPath(Lucid.MOD_ID, "textures/font/" + name + ".png");

			return new FontAtlas(texture, root);
		} catch (IOException exception) {
			throw new IllegalStateException("Не удалось прочитать шрифт " + name, exception);
		}
	}

	/** Картинка атласа. Лежит в {@code assets/lucid/textures/font}. */
	public Identifier texture() {
		return this.texture;
	}

	/** Ширина полосы расстояний в пикселях атласа. Нужна шейдеру для сглаживания. */
	public float distanceRange() {
		return this.distanceRange;
	}

	/** Высота строки в пикселях для заданного размера шрифта. */
	public float lineHeight(float size) {
		return this.lineHeight * size;
	}

	/** Насколько буквы поднимаются над базовой линией. */
	public float ascender(float size) {
		return this.ascender * size;
	}

	/** Насколько хвосты букв опускаются под базовую линию. Значение отрицательное. */
	public float descender(float size) {
		return this.descender * size;
	}

	/** Буква или заменитель, если такой в атласе нет. */
	public Glyph glyph(int code) {
		Glyph glyph = this.glyphs.get(code);

		if (glyph != null) {
			return glyph;
		}

		return this.glyphs.get(MISSING);
	}

	/** Поправка к расстоянию между конкретной парой букв. */
	public float kerning(int previous, int current) {
		if (this.kerning.isEmpty() || previous < 0) {
			return 0.0F;
		}

		return this.kerning.getOrDefault(key(previous, current), 0.0F);
	}

	/** Ширина строки в пикселях. Считается ровно так же, как её потом рисуют. */
	public float width(String text, float size) {
		float width = 0.0F;
		int previous = -1;

		for (int index = 0; index < text.length(); ) {
			int code = text.codePointAt(index);
			index += Character.charCount(code);

			Glyph glyph = this.glyph(code);

			if (glyph == null) {
				continue;
			}

			width += this.kerning(previous, code) * size;
			width += glyph.advance() * size;
			previous = code;
		}

		return width;
	}

	private static long key(int previous, int current) {
		return ((long) previous << 32) | (current & 0xFFFFFFFFL);
	}
}
