package dev.lucid.module.impl.render;

import net.minecraft.world.level.biome.Biome;

import dev.lucid.event.impl.TimeEvent;
import dev.lucid.event.impl.WeatherEvent;
import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.ModeSetting;
import dev.lucid.setting.SliderSetting;

/**
 * Визуальное время суток и погода.
 *
 * <p>Меняет картинку только у тебя на экране: сервер своё время и погоду считает сам,
 * поэтому мобы, посевы и костёр живут по настоящим значениям. Это ожидаемо и
 * по-другому быть не может.</p>
 *
 * <p>Шкала времени Minecraft: 0 — рассвет (6:00), 6000 — полдень, 12000 — закат (18:00),
 * 18000 — полночь.</p>
 *
 * <p>Подмена силы осадков — это только столбики дождя и цвет неба. Молния — отдельная
 * сущность, которую присылает сервер: у неё свой рендер, своя вспышка неба и свои
 * звуки, и сила дождя на них никак не влияет. Поэтому режимы без грозы гасят молнию
 * отдельно — этим занимается {@link #suppressLightning()}.</p>
 */
public class Ambience extends Module {

    private static final String WEATHER_OFF = "Off";
    private static final String WEATHER_CLEAR = "Clear";
    private static final String WEATHER_RAIN = "Rain";
    private static final String WEATHER_THUNDER = "Thunder";
    private static final String WEATHER_SNOW = "Snow";

    private static Ambience instance;

    private final SliderSetting time = this.addSetting(new SliderSetting(
            "time", "Время",
            "0 — рассвет, 6000 — полдень, 12000 — закат, 18000 — полночь",
            18000.0, 0.0, 23000.0, 250.0));

    private final ModeSetting weather = this.addSetting(new ModeSetting(
            "weather", "Погода", WEATHER_OFF,
            WEATHER_OFF, WEATHER_CLEAR, WEATHER_RAIN, WEATHER_THUNDER, WEATHER_SNOW));

    public Ambience() {
        super("ambience", "Ambience", Category.RENDER,
                "Своё время суток и погода — только визуально");

        instance = this;

        this.listen(TimeEvent.class, this::onTime);
        this.listen(WeatherEvent.class, this::onWeather);
    }

    private void onTime(TimeEvent event) {
        // Номер суток сохраняем и меняем только время внутри дня. Иначе сломается то,
        // что считается от номера дня, например фаза луны.
        long day = Math.floorDiv(event.getTime(), TimeEvent.DAY_LENGTH);

        event.setTime(day * TimeEvent.DAY_LENGTH + this.time.getAsInt());
        event.cancel();
    }

    private void onWeather(WeatherEvent event) {
        if (this.weather.is(WEATHER_OFF)) {
            return;
        }

        if (this.weather.is(WEATHER_CLEAR)) {
            event.setRainLevel(0.0F);
            event.setThunderLevel(0.0F);
            event.setPrecipitation(Biome.Precipitation.NONE);
        } else if (this.weather.is(WEATHER_RAIN)) {
            event.setRainLevel(1.0F);
            event.setThunderLevel(0.0F);
            event.setPrecipitation(Biome.Precipitation.RAIN);
        } else if (this.weather.is(WEATHER_THUNDER)) {
            // Гроза — это тот же дождь, но с затемнением неба. Ваниль считает силу
            // грозы отдельным множителем, поэтому нужны оба значения, а не одно.
            event.setRainLevel(1.0F);
            event.setThunderLevel(1.0F);
            event.setPrecipitation(Biome.Precipitation.RAIN);
        } else if (this.weather.is(WEATHER_SNOW)) {
            event.setRainLevel(1.0F);
            event.setThunderLevel(0.0F);
            event.setPrecipitation(Biome.Precipitation.SNOW);
        } else {
            return;
        }

        event.cancel();
    }

    // ------------------------------------------------------------------ молния

    /**
     * Гасить ли молнию целиком: её рендер, вспышку неба и звуки.
     *
     * <p>True для всех режимов, которые обещают погоду без грозы. В режиме
     * {@code Thunder} и при выключенной подмене молния остаётся как есть — иначе
     * гроза была бы без грозы, а выключенный модуль менял бы картинку.</p>
     */
    public static boolean suppressLightning() {
        return instance != null
                && instance.isEnabled()
                && !instance.weather.is(WEATHER_OFF)
                && !instance.weather.is(WEATHER_THUNDER);
    }
}
