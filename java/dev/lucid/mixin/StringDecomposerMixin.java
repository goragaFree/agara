package dev.lucid.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.util.StringDecomposer;

import dev.lucid.module.impl.player.NameProtect;

/**
 * Клиентская подмена своего ника во всём видимом тексте.
 *
 * <p>На сервер ничего не уходит: меняется только строка перед отрисовкой
 * (чат, табличка, tab-лист). Цепляем длинную {@code iterateFormatted} — туда
 * сходятся короткие перегрузки и {@code FormattedText}. Плюс {@code iterate}
 * для сырого текста без §. {@code @ModifyVariable} на HEAD не зависит от того,
 * кто кого вызывает внутри ванили.</p>
 */
@Mixin(StringDecomposer.class)
public class StringDecomposerMixin {

	@ModifyVariable(
			method = "iterateFormatted(Ljava/lang/String;ILnet/minecraft/network/chat/Style;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z",
			at = @At("HEAD"),
			argsOnly = true,
			ordinal = 0
	)
	private static String lucid$nameProtectFormatted(String text) {
		return NameProtect.protect(text);
	}

	@ModifyVariable(
			method = "iterate(Ljava/lang/String;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z",
			at = @At("HEAD"),
			argsOnly = true,
			ordinal = 0
	)
	private static String lucid$nameProtectPlain(String text) {
		return NameProtect.protect(text);
	}
}
