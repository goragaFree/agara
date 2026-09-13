package dev.lucid.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

/**
 * Запись в поле сессии.
 *
 * <p>Поле объявлено {@code private final}. Пометка {@link Mutable} снимает {@code final}
 * на этапе загрузки класса — тот же приём, что и в {@code OptionInstanceAccessor}.</p>
 */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {

	@Mutable
	@Accessor("user")
	void lucid$setUser(User user);
}
