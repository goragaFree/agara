package dev.lucid.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.LightningBolt;

import dev.lucid.module.impl.render.Ambience;

/**
 * Гасит молнию, когда погода подменена на режим без грозы.
 *
 * <p>Подмена {@code rainLevel} и {@code thunderLevel} меняет только столбики осадков
 * и цвет неба. Сам разряд — отдельная сущность, присланная сервером: она сама
 * рисует себя, сама ставит вспышку неба и сама играет раскат с ударом. Именно
 * поэтому при «Ясно» небо продолжало мигать и громыхать.</p>
 *
 * <p>На клиенте сущность убираем сразу и тик не даём выполнить: без {@code discard}
 * отмена тика оставила бы её висеть в мире навсегда, потому что её собственный
 * счётчик жизни крутится именно в тике.</p>
 *
 * <p>На сервере не трогаем ничего: удар по-прежнему жжёт, бьёт и превращает свиней
 * в зомби-пиглинов — вырез чисто визуальный, как и вся остальная подмена погоды.</p>
 *
 * <p>{@code require = 0} — если метод в патче переименуют, клиент не упадёт,
 * просто молния снова будет видна.</p>
 */
@Mixin(LightningBolt.class)
public class LightningBoltMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 0)
    private void lucid$hideLightning(CallbackInfo ci) {
        LightningBolt self = (LightningBolt) (Object) this;

        if (!self.level().isClientSide() || !Ambience.suppressLightning()) {
            return;
        }

        self.discard();
        ci.cancel();
    }
}
