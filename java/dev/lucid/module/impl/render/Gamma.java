package dev.lucid.module.impl.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;

import dev.lucid.mixin.OptionInstanceAccessor;
import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.SliderSetting;

/**
 * Ручная гамма: сам выбираешь яркость мира, а не только «максимум».
 *
 * <p>Ванильный ползунок ограничен диапазоном 0..1, поэтому значение
 * записывается в поле напрямую через {@link OptionInstanceAccessor}, мимо валидатора.</p>
 *
 * <p>Прежняя яркость запоминается при включении и возвращается при выключении,
 * иначе после выхода игра сохранит задранную гамму в options.txt.</p>
 *
 * <p><b>Про момент включения.</b> Конфиг клиента читается из точки входа Fabric, а она
 * вызывается <i>внутри конструктора</i> {@link Minecraft}, где поле {@code options} ещё null.
 * Значит, модуль включается раньше, чем появляются настройки игры, и {@code onEnable}
 * применить гамму физически не может. Поэтому оно только пытается, а если рано — задача
 * дожидается первого тика. Откладывать чтение конфига целиком было бы хуже: остальным
 * модулям состояние нужно сразу.</p>
 */
public class Gamma extends Module {

    /** Расхождение меньше этого считаем одним и тем же значением: double точного равенства не любит. */
    private static final double EPSILON = 0.001;

    /**
     * 0 — кромешная тьма, 1 — ванильный максимум, выше — уже фуллбрайт.
     *
     * <p>Основная разница видна в диапазоне 0..5, выше картинка почти не меняется,
     * поэтому шаг мелкий — иначе весь смысл настройки умещался бы в пару делений.</p>
     */
    private final SliderSetting brightness = this.addSetting(new SliderSetting(
            "brightness", "Яркость", "0 — тьма, 1 — ванильный максимум, выше — фуллбрайт",
            5.0, 0.0, 10.0, 0.1));

    /** Яркость игрока до включения модуля. */
    private double previousGamma = 1.0;

    /**
     * Уже ли сохранена ванильная яркость.
     *
     * <p>Одновременно играет роль флага «гамма ещё не применена». Защищает от главного
     * способа испортить игроку options.txt: запомнить свою же задранную гамму как «ванильную»
     * при повторном включении.</p>
     */
    private boolean applied;

    public Gamma() {
        super("gamma", "Gamma", Category.RENDER,
                "Своя яркость мира");

        // Крутили слайдер при включённом модуле — яркость меняется сразу, без переключения.
        // Настройки тоже грузятся из конфига до появления options, поэтому здесь тоже только попытка.
        this.brightness.onChange(value -> {
            if (this.isEnabled()) {
                this.tryApply();
            }
        });
    }

    @Override
    protected void onEnable() {
        this.tryApply();
    }

    @Override
    protected void onDisable() {
        Options options = mc().options;

        // Гамму так и не успели применить — возвращать нечего, а previousGamma сейчас мусор.
        if (options != null && this.applied) {
            setGamma(options.gamma(), this.previousGamma);
        }

        this.applied = false;
    }

    @Override
    public void onTick(Minecraft client) {
        OptionInstance<Double> gamma = client.options.gamma();

        // Первый тик после включения в обход options: добираем то, что не смог onEnable.
        if (!this.applied) {
            this.tryApply();
            return;
        }

        double target = this.brightness.get();

        // Сравниваем на неравенство, а не «меньше цели»: иначе модуль умеет только поднимать
        // яркость и любое значение ниже текущего просто не применяется.
        if (Math.abs(gamma.get() - target) > EPSILON) {
            setGamma(gamma, target);
        }
    }

    /**
     * Применяет яркость, если настройки игры уже существуют.
     *
     * <p>Молчаливый выход здесь не прячет ошибку: неприменённое состояние видно по флагу
     * {@code applied}, и первый же тик его доберёт.</p>
     */
    private void tryApply() {
        Options options = mc().options;

        if (options == null) {
            return;
        }

        OptionInstance<Double> gamma = options.gamma();

        if (!this.applied) {
            this.previousGamma = gamma.get();
            this.applied = true;
        }

        setGamma(gamma, this.brightness.get());
    }

    /** Пишет значение в обход валидатора. Каст через Object — обычный приём для миксин-аксессоров. */
    private static void setGamma(OptionInstance<Double> gamma, double value) {
        ((OptionInstanceAccessor) (Object) gamma).lucid$setValue(value);
    }
}
