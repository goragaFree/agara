package dev.lucid.setting;

import java.util.Collections;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * Выбор ровно одного варианта из списка.
 *
 * <p>Пример: режим отрисовки — "Заливка", "Контур", "Оба". В GUI удобно кликать
 * по {@link #cycle()}, перебирая варианты по кругу.</p>
 */
public class ModeSetting extends Setting<String> {

    private final List<String> options;

    public ModeSetting(String id, String name, String description, String defaultValue, List<String> options) {
        super(id, name, description, defaultValue);

        if (options.isEmpty()) {
            throw new IllegalArgumentException("ModeSetting '" + id + "' has no options");
        }

        if (!options.contains(defaultValue)) {
            throw new IllegalArgumentException(
                    "ModeSetting '" + id + "' default '" + defaultValue + "' is not in options");
        }

        this.options = List.copyOf(options);
    }

    public ModeSetting(String id, String name, String defaultValue, String... options) {
        this(id, name, "", defaultValue, List.of(options));
    }

    /**
     * Отбрасывает значения вне списка — важно при чтении старого конфига,
     * где вариант мог быть переименован или удалён.
     */
    @Override
    protected String sanitize(String newValue) {
        // options ещё null во время вызова из конструктора базового класса.
        if (this.options == null || this.options.contains(newValue)) {
            return newValue;
        }

        return this.getValue();
    }

    /** Следующий вариант по кругу. */
    public void cycle() {
        int next = (this.getIndex() + 1) % this.options.size();
        this.setValue(this.options.get(next));
    }

    /** Предыдущий вариант — для правого клика в GUI. */
    public void cycleBack() {
        int size = this.options.size();
        int previous = (this.getIndex() - 1 + size) % size;
        this.setValue(this.options.get(previous));
    }

    public int getIndex() {
        return Math.max(0, this.options.indexOf(this.getValue()));
    }

    public void setIndex(int index) {
        if (index >= 0 && index < this.options.size()) {
            this.setValue(this.options.get(index));
        }
    }

    public boolean is(String option) {
        return this.getValue().equals(option);
    }

    public List<String> getOptions() {
        return Collections.unmodifiableList(this.options);
    }

    @Override
    public JsonElement save() {
        return new JsonPrimitive(this.getValue());
    }

    @Override
    public void load(JsonElement json) {
        // Исчезнувший вариант отсечёт sanitize, и останется текущее значение.
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            this.setValue(json.getAsString());
        }
    }

    @Override
    public String getDisplayValue() {
        return this.getValue();
    }
}
