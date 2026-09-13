package dev.lucid.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.OptionInstance;

/**
 * Прямой доступ к приватному полю {@code value} настройки.
 *
 * <p>Публичный {@code OptionInstance#set} прогоняет значение через валидатор из {@code ValueSet},
 * а гамма ограничена диапазоном 0..1 — значение вроде 15.0 просто отбрасывается.
 * Запись в поле напрямую обходит проверку. Публичного API для этого нет, поэтому — миксин.</p>
 *
 * <p>Поле типизировано как {@code T}, после стирания типов это {@code Object} — поэтому
 * аксессор принимает {@code Object}, а не {@code Double}.</p>
 */
@Mixin(OptionInstance.class)
public interface OptionInstanceAccessor {

    @Accessor("value")
    void lucid$setValue(Object value);
}
