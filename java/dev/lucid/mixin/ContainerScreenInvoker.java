package dev.lucid.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

/**
 * Доступ к закрытым частям контейнерного экрана.
 *
 * <p>{@code slotClicked} дёргаем именно у экрана, а не у {@code MultiPlayerGameMode} напрямую:
 * экран креатива переопределяет этот метод и обрабатывает свои слоты по-своему.</p>
 *
 * <p>Слот под курсором берём из поля {@code hoveredSlot}. Метода-геттера в ванили нет:
 * {@code getSlotUnderMouse()} существует только в NeoForge и там уже помечен к удалению.</p>
 */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenInvoker {

    @Accessor("hoveredSlot")
    Slot lucid$hoveredSlot();

    @Invoker("slotClicked")
    void lucid$slotClicked(Slot slot, int slotId, int buttonNum, ContainerInput containerInput);
}
