package dev.lucid.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.lucid.screen.altmanager.AltManagerScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/**
 * Кнопка альт-менеджера в правом верхнем углу главного меню.
 *
 * <p>Класс объявлен наследником {@link Screen} не ради наследования, а чтобы быть видны
 * защищённые члены целевого экрана: добавление виджета, ширина и ссылка на игру.</p>
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

	private static final int LUCID_BUTTON_WIDTH = 56;

	private static final int LUCID_BUTTON_HEIGHT = 20;

	private static final int LUCID_BUTTON_MARGIN = 4;

	protected TitleScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void lucid$addAltButton(CallbackInfo callback) {
		Button button = Button
				.builder(Component.literal("Alts"), pressed -> this.minecraft.gui
						.setScreen(new AltManagerScreen((Screen) (Object) this)))
				.bounds(this.width - LUCID_BUTTON_WIDTH - LUCID_BUTTON_MARGIN, LUCID_BUTTON_MARGIN,
						LUCID_BUTTON_WIDTH, LUCID_BUTTON_HEIGHT)
				.build();

		this.addRenderableWidget(button);
	}
}
