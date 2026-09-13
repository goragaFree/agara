package dev.lucid.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import dev.lucid.client.LucidClient;
import dev.lucid.event.impl.WeatherEvent;
import dev.lucid.module.impl.render.Ambience;

/**
 * Клиентская подмена погоды.
 *
 * <p>{@link Level} общий для клиента и встроенного сервера одиночки, поэтому
 * трогаем только {@code isClientSide}: иначе дождь стал бы настоящим — мобы,
 * посевы и костёр на серверной стороне.</p>
 */
@Mixin(Level.class)
public class LevelMixin {

    @Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
    private void lucid$rainLevel(float partialTick, CallbackInfoReturnable<Float> cir) {
        Float value = this.lucid$weatherRain();

        if (value != null) {
            cir.setReturnValue(value);
        }
    }

    @Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true)
    private void lucid$thunderLevel(float partialTick, CallbackInfoReturnable<Float> cir) {
        Float value = this.lucid$weatherThunder();

        if (value != null) {
            cir.setReturnValue(value);
        }
    }

    /**
     * Вспышка неба от молнии. Её ставит сама сущность разряда, поэтому обнулённый
     * {@code thunderLevel} её не гасит. Здесь гаснет и та вспышка, что успела
     * выставиться до включения модуля — иначе экран мигнёт уже после «Ясно».
     *
     * <p>{@code require = 0} — без этого крючка остаётся вырез самой сущности,
     * падать из-за косметики хуже.</p>
     */
    @Inject(method = "getSkyFlashTime", at = @At("HEAD"), cancellable = true, require = 0)
    private void lucid$skyFlashTime(CallbackInfoReturnable<Integer> cir) {
        Level self = (Level) (Object) this;

        if (self.isClientSide() && Ambience.suppressLightning()) {
            cir.setReturnValue(0);
        }
    }

    @Inject(method = "precipitationAt", at = @At("HEAD"), cancellable = true)
    private void lucid$precipitation(BlockPos pos, CallbackInfoReturnable<Biome.Precipitation> cir) {
        Biome.Precipitation value = this.lucid$weatherPrecipitation();

        if (value != null) {
            cir.setReturnValue(value);
        }
    }

    private Float lucid$weatherRain() {
        WeatherEvent event = this.lucid$weather();

        return event != null && event.isCancelled() ? event.getRainLevel() : null;
    }

    private Float lucid$weatherThunder() {
        WeatherEvent event = this.lucid$weather();

        return event != null && event.isCancelled() ? event.getThunderLevel() : null;
    }

    private Biome.Precipitation lucid$weatherPrecipitation() {
        WeatherEvent event = this.lucid$weather();

        return event != null && event.isCancelled() ? event.getPrecipitation() : null;
    }

    private WeatherEvent lucid$weather() {
        Level self = (Level) (Object) this;

        // Встроенный сервер одиночки живёт в том же процессе. Без этой проверки
        // визуальный дождь стал бы серверным.
        if (!self.isClientSide() || !self.canHaveWeather()) {
            return null;
        }

        if (!LucidClient.EVENTS.hasListeners(WeatherEvent.class)) {
            return null;
        }

        return LucidClient.EVENTS.post(new WeatherEvent());
    }
}
