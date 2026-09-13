package dev.lucid.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.ClockNetworkState;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;

import dev.lucid.client.LucidClient;
import dev.lucid.event.impl.TimeEvent;
import dev.lucid.event.impl.TimeSyncEvent;

@Mixin(ClientClockManager.class)
public class ClientClockManagerMixin {

    // В 26.2 время — не поле мира, а набор часов (WorldClock). Всё (солнце, цвет неба,
    // яркость освещения) спрашивает время именно через getTotalTicks.
    // ClientClockManager есть только на клиенте, так что встроенный сервер одиночной игры
    // считает время своим ServerClockManager и о подмене не узнает.
    @Inject(method = "getTotalTicks", at = @At("RETURN"), cancellable = true)
    private void lucid$totalTicks(Holder<WorldClock> definition, CallbackInfoReturnable<Long> cir) {
        // Часы — общий механизм, на них живут и таймлайны. Меняем только Обычный мир.
        if (!definition.is(WorldClocks.OVERWORLD)) {
            return;
        }

        // getReturnValue() возвращает Long и распаковывается сам. У вариантов с буквой на конце
        // суффикс — это дескриптор JVM, а не имя типа: для long это J, а не L.
        long vanillaTime = cir.getReturnValue();

        TimeEvent event = LucidClient.EVENTS.post(new TimeEvent(vanillaTime));

        if (event.isCancelled()) {
            cir.setReturnValue(event.getTime());
        }
    }

    // Сюда приходит обновление времени от сервера — точка замера TPS.
    @Inject(method = "handleUpdates", at = @At("HEAD"))
    private void lucid$timeSync(long gameTime, Map<Holder<WorldClock>, ClockNetworkState> updates,
                                CallbackInfo ci) {
        LucidClient.EVENTS.post(new TimeSyncEvent());
    }
}
