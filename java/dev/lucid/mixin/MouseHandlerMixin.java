package dev.lucid.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import net.minecraft.client.MouseHandler;

import dev.lucid.client.LucidClient;
import dev.lucid.event.impl.MouseRotationEvent;

/**
 * Перехват поворота камеры мышью.
 *
 * <p>Цель — не начало {@code turnPlayer}, а сам вызов {@code turn} в его конце. К этому моменту
 * игра уже учла всё: чувствительность в кубе, ванильную плавную камеру, режим прицеливания
 * и инверсию осей. Значит, подписчику приходят готовые градусы, а не сырые пиксели курсора,
 * и плавность не зависит ни от настроек игры, ни от DPI мыши.</p>
 *
 * <p>Владелец вызова — именно {@code LocalPlayer}, а не {@code Entity}: в байткоде владелец берётся
 * от типа выражения на компиляции, а игра зовёт поворот как {@code minecraft.player.turn(...)},
 * где поле {@code player} объявлено как {@code LocalPlayer}. Метод объявлен выше, в {@code Entity},
 * но миксину важно то, что написано в инструкции {@code INVOKEVIRTUAL}, а не то, где метод
 * реально живёт. Именно на этом прошлая версия и упала.</p>
 *
 * <p>{@code ModifyArgs} вместо {@code Redirect} выбран сознательно: редирект забирает вызов себе
 * целиком и конфликтует с любым другим модом, трогающим мышь. Модификаторов аргументов
 * может быть сколько угодно, они складываются в цепочку.</p>
 *
 * <p>Вызов {@code getTutorial().onMouse(...)} остаётся видеть ванильные значения — и так правильно:
 * обучалка считает, смотрел ли игрок по сторонам, а не куда его в итоге довернули.</p>
 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @ModifyArgs(
            method = "turnPlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"
            )
    )
    private void lucid$turnPlayer(Args args) {
        MouseRotationEvent event = LucidClient.EVENTS.post(
                new MouseRotationEvent(args.get(0), args.get(1)));

        // Аргументы перезаписываются всегда, а не только при изменении: сравнивать
        // double на равенство смысла нет, а запись того же значения ничего не ломает.
        args.set(0, event.getDeltaX());
        args.set(1, event.getDeltaY());
    }
}
