package dev.lucid.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * Число в заданном диапазоне, например от 1 до 10 с шагом 0.5.
 *
 * <p>Значение всегда хранится как {@code double}. Если нужны только целые — ставь шаг 1.0
 * и бери {@link #getAsInt()}. Любое входящее число обрезается по границам и прилипает
 * к ближайшему шагу, поэтому грязных значений вроде 7.3000000000000005 не будет.</p>
 */
public class SliderSetting extends Setting<Double> {

    private final double min;
    private final double max;
    private final double step;

    /** Показывать без дробной части. */
    private final boolean integer;

    public SliderSetting(String id, String name, String description,
                         double defaultValue, double min, double max, double step) {
        super(id, name, description, defaultValue);

        if (min >= max) {
            throw new IllegalArgumentException("SliderSetting '" + id + "': min must be less than max");
        }

        if (step <= 0.0) {
            throw new IllegalArgumentException("SliderSetting '" + id + "': step must be positive");
        }

        this.min = min;
        this.max = max;
        this.step = step;
        this.integer = step >= 1.0 && step % 1.0 == 0.0;

        // Базовый конструктор звал sanitize до того, как появились границы — пересчитываем.
        this.setValue(defaultValue);
    }

    public SliderSetting(String id, String name, double defaultValue, double min, double max) {
        this(id, name, "", defaultValue, min, max, 1.0);
    }

    @Override
    protected Double sanitize(Double newValue) {
        if (this.step == 0.0) {
            // Вызов из конструктора базового класса, поля ещё не выставлены.
            return newValue;
        }

        double clamped = Math.max(this.min, Math.min(this.max, newValue));

        // Прилипание к шагу считаем от min, иначе диапазон вроде 1..10 с шагом 0.3 съехал бы.
        double snapped = this.min + Math.round((clamped - this.min) / this.step) * this.step;

        // Убираем хвосты двоичной арифметики.
        snapped = Math.round(snapped * 1000.0) / 1000.0;

        return Math.max(this.min, Math.min(this.max, snapped));
    }

    public double get() {
        return this.getValue();
    }

    public int getAsInt() {
        return (int) Math.round(this.getValue());
    }

    public float getAsFloat() {
        return this.getValue().floatValue();
    }

    public void set(double value) {
        this.setValue(value);
    }

    /** Доля заполнения 0..1 — столько рисует полоска в GUI. */
    public double getFraction() {
        return (this.getValue() - this.min) / (this.max - this.min);
    }

    /** Обратное к {@link #getFraction()} — для протаскивания мышью. */
    public void setFraction(double fraction) {
        double clamped = Math.max(0.0, Math.min(1.0, fraction));
        this.setValue(this.min + clamped * (this.max - this.min));
    }

    public double getMin() {
        return this.min;
    }

    public double getMax() {
        return this.max;
    }

    public double getStep() {
        return this.step;
    }

    public boolean isInteger() {
        return this.integer;
    }

    @Override
    public JsonElement save() {
        return new JsonPrimitive(this.getValue());
    }

    @Override
    public void load(JsonElement json) {
        // Границы могли смениться с прошлой версии клиента — sanitize загонит число обратно в диапазон.
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
            this.setValue(json.getAsDouble());
        }
    }

    @Override
    public String getDisplayValue() {
        if (this.integer) {
            return String.valueOf(this.getAsInt());
        }

        return String.valueOf(this.getValue());
    }
}
