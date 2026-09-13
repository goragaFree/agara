package dev.lucid.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import dev.lucid.client.LucidClient;
import dev.lucid.event.impl.WeatherEvent;

/**
 * Столбики дождя и снега спрашивают тип осадков у рендерера, а не только у мира.
 *
 * <p>Без этой точки в тёплом биоме «Снегопад» всё равно рисовал бы дождь:
 * ваниль смотрит температуру биома. {@code require = 0} — если в патче метод
 * переименуют, клиент не упадёт, останется подмена через {@code Level.precipitationAt}.</p>
 */
@Mixin(WeatherEffectRenderer.class)
public class WeatherEffectRendererMixin {

    @Inject(
            method = "getPrecipitationAt",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void lucid$precipitation(Level level, BlockPos pos,
                                    CallbackInfoReturnable<Biome.Precipitation> cir) {
        if (!LucidClient.EVENTS.hasListeners(WeatherEvent.class)) {
            return;
        }

        WeatherEvent event = LucidClient.EVENTS.post(new WeatherEvent());

        if (event.isCancelled()) {
            cir.setReturnValue(event.getPrecipitation());
        }
    }
}
