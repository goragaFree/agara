package dev.lucid.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * Галочка: включено / выключено.
 *
 * <p>Не путать с включением самого модуля — это подопция внутри него,
 * например "рисовать сквозь стены".</p>
 */
public class BooleanSetting extends Setting<Boolean> {

    public BooleanSetting(String id, String name, String description, boolean defaultValue) {
        super(id, name, description, defaultValue);
    }

    public BooleanSetting(String id, String name, boolean defaultValue) {
        this(id, name, "", defaultValue);
    }

    public boolean get() {
        return this.getValue();
    }

    public void toggle() {
        this.setValue(!this.getValue());
    }

    @Override
    public JsonElement save() {
        return new JsonPrimitive(this.getValue());
    }

    @Override
    public void load(JsonElement json) {
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isBoolean()) {
            this.setValue(json.getAsBoolean());
        }
    }

    @Override
    public String getDisplayValue() {
        return this.getValue() ? "Да" : "Нет";
    }
}
