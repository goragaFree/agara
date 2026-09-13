package dev.lucid.setting;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import com.google.gson.JsonElement;

import dev.lucid.client.LucidClient;
import dev.lucid.util.config.ConfigManager;

/**
 * Одна настройка модуля: имя, текущее значение и правила его изменения.
 *
 * <p>Это только данные — никакого рендера здесь нет. GUI потом просто смотрит на тип
 * настройки и рисует нужный виджет, а конфиг — сохраняет значение по {@link #getId()}.
 * Благодаря этому настройки работают даже тогда, когда GUI закрыто.</p>
 *
 * <p>За превращение значения в JSON и обратно отвечает сама настройка ({@link #save()} и
 * {@link #load(JsonElement)}), а не конфиг. Иначе конфигу пришлось бы держать список
 * всех типов настроек через instanceof, и каждый новый тип требовал бы правки в двух местах.</p>
 *
 * @param <T> тип хранимого значения
 */
public abstract class Setting<T> {

    /** Ключ в конфиге, например "gamma". Уникален в пределах модуля. */
    private final String id;

    /** Подпись в GUI. */
    private final String name;

    private final String description;

    private final T defaultValue;

    private T value;

    /** Вызывается после реального изменения значения. */
    private Consumer<T> onChange = ignored -> { };

    /** Позволяет прятать настройку, пока она не имеет смысла при текущих значениях других. */
    private BooleanSupplier visible = () -> true;

    protected Setting(String id, String name, String description, T defaultValue) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.defaultValue = defaultValue;
        this.value = this.sanitize(defaultValue);
    }

    // ------------------------------------------------------------------ значение

    public final T getValue() {
        return this.value;
    }

    /**
     * Ставит значение, прогнав его через {@link #sanitize(Object)}.
     *
     * <p>Коллбэк стреляет только при фактическом изменении, чтобы протаскивание
     * слайдера не дёргало логику модуля на каждом кадре.</p>
     */
    public final void setValue(T newValue) {
        T sanitized = this.sanitize(newValue);

        if (Objects.equals(this.value, sanitized)) {
            return;
        }

        this.value = sanitized;
        this.onChange.accept(sanitized);

        // Значение разошлось с тем, что лежит на диске — конфиг перезапишется при ближайшей возможности.
        // null возможен только если настройку создали раньше клиента — падать из-за этого глупо.
        ConfigManager config = LucidClient.CONFIG;

        if (config != null) {
            config.markDirty();
        }
    }

    public final void reset() {
        this.setValue(this.defaultValue);
    }

    /** Приводит входящее значение к допустимому: обрезает диапазон, отбрасывает лишние варианты. */
    protected T sanitize(T newValue) {
        return newValue;
    }

    /** Текст справа от названия в GUI, например "15.0" или "Fade, Glow". */
    public abstract String getDisplayValue();

    // ------------------------------------------------------------------ конфиг

    /** Значение в виде, пригодном для записи в файл. */
    public abstract JsonElement save();

    /**
     * Читает значение из файла.
     *
     * <p>Реализация обязана молча игнорировать мусор. Конфиг — обычный текстовый файл,
     * его могли править руками или он остался от старой версии, где тип настройки был другим.
     * Падать из-за этого клиент не должен.</p>
     */
    public abstract void load(JsonElement json);

    // ------------------------------------------------------------------ настройка поведения

    /** Цепочка: {@code addSetting(new SliderSetting(...)).onChange(v -> ...)}. */
    @SuppressWarnings("unchecked")
    public final <S extends Setting<T>> S onChange(Consumer<T> listener) {
        this.onChange = listener;
        return (S) this;
    }

    @SuppressWarnings("unchecked")
    public final <S extends Setting<T>> S visibleWhen(BooleanSupplier condition) {
        this.visible = condition;
        return (S) this;
    }

    public final boolean isVisible() {
        return this.visible.getAsBoolean();
    }

    // ------------------------------------------------------------------ геттеры

    public final String getId() {
        return this.id;
    }

    public final String getName() {
        return this.name;
    }

    public final String getDescription() {
        return this.description;
    }

    public final T getDefaultValue() {
        return this.defaultValue;
    }

    @Override
    public String toString() {
        return this.id + "=" + this.getDisplayValue();
    }
}
