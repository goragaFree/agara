package dev.lucid.event.impl;

import net.minecraft.world.level.biome.Biome;

import dev.lucid.event.CancellableEvent;

/**
 * Игра спрашивает клиентскую погоду: силу осадков и чем они падают.
 *
 * <p>Это событие-вопрос, как {@link TimeEvent}: подписчик может подменить значения
 * и отменить событие. Отмена значит «ответ подменён, используй его вместо ванильного».
 * Сервер свою погоду считает сам и о подмене не узнаёт.</p>
 *
 * <p>Событие рассылается часто — по несколько раз за кадр. Обработчик должен быть
 * дешёвым: никаких созданий объектов и обходов списков.</p>
 */
public final class WeatherEvent extends CancellableEvent {

    private float rainLevel;
    private float thunderLevel;
    private Biome.Precipitation precipitation;

    public WeatherEvent(float rainLevel, float thunderLevel, Biome.Precipitation precipitation) {
        this.rainLevel = rainLevel;
        this.thunderLevel = thunderLevel;
        this.precipitation = precipitation;
    }

    public WeatherEvent() {
        this(0.0F, 0.0F, Biome.Precipitation.NONE);
    }

    public float getRainLevel() {
        return this.rainLevel;
    }

    public void setRainLevel(float rainLevel) {
        this.rainLevel = rainLevel;
    }

    public float getThunderLevel() {
        return this.thunderLevel;
    }

    public void setThunderLevel(float thunderLevel) {
        this.thunderLevel = thunderLevel;
    }

    public Biome.Precipitation getPrecipitation() {
        return this.precipitation;
    }

    public void setPrecipitation(Biome.Precipitation precipitation) {
        this.precipitation = precipitation;
    }
}
