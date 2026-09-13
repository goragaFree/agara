package dev.lucid.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;

import dev.lucid.client.LucidClient;
import dev.lucid.event.impl.MouseDragEvent;
import dev.lucid.event.impl.MouseReleaseEvent;
import dev.lucid.event.impl.ScreenClosedEvent;

/**
 * Перехват мыши в контейнерных экранах.
 *
 * <p>Публичного события на мышь внутри экрана в Fabric нет, поэтому здесь миксин —
 * тот случай, ради которого они и нужны.</p>
 *
 * <p>Миксин больше не знает ни одного модуля по имени — он только кладёт событие
 * в шину. Новый модуль, которому нужна мышь в сундуке, правки этого файла уже не требует.</p>
 *
 * <p>Методы указаны без дескрипторов специально: перегрузок у них нет, а одна
 * опечатка в дескрипторе даёт ровно такую же тишину, как была с колесом.</p>
 */
@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin {

    /**
     * Протаскивание мыши с зажатой кнопкой.
     */
    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void lucid$mouseDragged(MouseButtonEvent event, double dx, double dy,
                                    CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;

        MouseDragEvent posted = LucidClient.EVENTS.post(
                new MouseDragEvent(screen, event.button()));

        if (posted.isCancelled()) {
            cir.setReturnValue(true);
        }
    }

    /**
     * Отпускание кнопки. Не отменяется: ванили оно тоже нужно.
     */
    @Inject(method = "mouseReleased", at = @At("HEAD"))
    private void lucid$mouseReleased(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;

        LucidClient.EVENTS.post(new MouseReleaseEvent(screen, event.button()));
    }

    /**
     * Экран закрывается.
     */
    @Inject(method = "removed", at = @At("HEAD"))
    private void lucid$removed(CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;

        LucidClient.EVENTS.post(new ScreenClosedEvent(screen));
    }
}
