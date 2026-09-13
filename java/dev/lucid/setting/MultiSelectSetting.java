package dev.lucid.setting;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

/**
 * Выбор любого числа вариантов: от нуля до всех сразу.
 *
 * <p>Пример: кого подсвечивать — "Игроки", "Мобы", "Предметы". Порядок выбранных
 * совпадает с порядком списка вариантов, чтобы подпись в GUI не прыгала.</p>
 */
public class MultiSelectSetting extends Setting<Set<String>> {

    private final List<String> options;

    public MultiSelectSetting(String id, String name, String description,
                              Collection<String> defaultValue, List<String> options) {
        super(id, name, description, new LinkedHashSet<>(defaultValue));

        this.options = List.copyOf(options);

        // Базовый конструктор уже вызвал sanitize, когда options ещё не было — чистим сейчас.
        this.setValue(new LinkedHashSet<>(defaultValue));
    }

    public MultiSelectSetting(String id, String name, String... options) {
        this(id, name, "", Set.of(), List.of(options));
    }

    /** Оставляет только известные варианты и выстраивает их в порядке списка. */
    @Override
    protected Set<String> sanitize(Set<String> newValue) {
        if (this.options == null) {
            return new LinkedHashSet<>(newValue);
        }

        Set<String> cleaned = new LinkedHashSet<>();

        for (String option : this.options) {
            if (newValue.contains(option)) {
                cleaned.add(option);
            }
        }

        return cleaned;
    }

    public boolean isSelected(String option) {
        return this.getValue().contains(option);
    }

    /** Клик по варианту в GUI. */
    public void toggle(String option) {
        Set<String> updated = new LinkedHashSet<>(this.getValue());

        if (!updated.remove(option)) {
            updated.add(option);
        }

        this.setValue(updated);
    }

    public void select(String option) {
        Set<String> updated = new LinkedHashSet<>(this.getValue());
        updated.add(option);
        this.setValue(updated);
    }

    public void deselect(String option) {
        Set<String> updated = new LinkedHashSet<>(this.getValue());
        updated.remove(option);
        this.setValue(updated);
    }

    public void selectAll() {
        this.setValue(new LinkedHashSet<>(this.options));
    }

    public void clear() {
        this.setValue(new LinkedHashSet<>());
    }

    public boolean isEmpty() {
        return this.getValue().isEmpty();
    }

    public List<String> getOptions() {
        return Collections.unmodifiableList(this.options);
    }

    @Override
    public JsonElement save() {
        JsonArray array = new JsonArray();

        for (String selected : this.getValue()) {
            array.add(selected);
        }

        return array;
    }

    @Override
    public void load(JsonElement json) {
        if (!json.isJsonArray()) {
            return;
        }

        Set<String> loaded = new LinkedHashSet<>();

        for (JsonElement element : json.getAsJsonArray()) {
            // В массив могли затесаться числа или объекты, если файл правили руками.
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                loaded.add(element.getAsString());
            }
        }

        // Неизвестные варианты отсечёт sanitize.
        this.setValue(loaded);
    }

    @Override
    public String getDisplayValue() {
        // Пустой список вариантов — заглушка: в GUI только имя группы, без "Ничего"/"Всё".
        if (this.options.isEmpty()) {
            return "";
        }

        if (this.getValue().isEmpty()) {
            return "Ничего";
        }

        if (this.getValue().size() == this.options.size()) {
            return "Всё";
        }

        return String.join(", ", this.getValue());
    }
}
