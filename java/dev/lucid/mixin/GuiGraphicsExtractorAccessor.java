package dev.lucid.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;

/**
 * Доступ к приватному {@link GuiRenderState} внутри {@link GuiGraphicsExtractor}.
 *
 * <p>В 26.2 GUI рисуется в два прохода: сначала игра собирает список элементов
 * ({@code extract}), потом рисует их пачками ({@code render}). Список живёт в
 * {@code GuiRenderState}, и метод {@code addGuiElement} у него публичный — туда можно
 * подать свой элемент отрисовки. Но само поле в экстракторе закрыто, поэтому нужен
 * этот аксессор.</p>
 */
@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsExtractorAccessor {

    @Accessor("guiRenderState")
    GuiRenderState lucid$guiRenderState();
}
