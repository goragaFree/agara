package dev.lucid.module.impl.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

import dev.lucid.event.impl.MouseDragEvent;
import dev.lucid.event.impl.MouseReleaseEvent;
import dev.lucid.event.impl.ScreenClosedEvent;
import dev.lucid.mixin.ContainerScreenInvoker;
import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.SliderSetting;

/**
 * Перенос предметов протаскиванием левой кнопки мыши.
 *
 * <p>Работает поверх шины событий: проверки {@code isEnabled()} здесь больше нет,
 * потому что выключенный модуль от шины отключён и событий не получает.</p>
 */
public class ItemScroller extends Module {

    /** Левая кнопка мыши в нумерации GLFW. */
    private static final int BUTTON_LEFT = 0;

    private static final int NO_SLOT = -1;

    private final SliderSetting delay = this.addSetting(new SliderSetting(
            "delay", "Задержка", "Пауза между переносами в миллисекундах",
            50.0, 0.0, 200.0, 5.0));

    private long lastTransfer;

    /** Последний обработанный слот — чтобы один слот не щёлкался дважды за одно движение. */
    private int lastSlot = NO_SLOT;

    public ItemScroller() {
        super("item_scroller", "ItemScroller", Category.PLAYER,
                "Перенос предметов протаскиванием левой кнопки");

        this.listen(MouseDragEvent.class, this::onDrag);
        this.listen(MouseReleaseEvent.class, event -> this.reset());

        // Экран могли закрыть прямо во время протаскивания — отпускания мы тогда не увидим.
        this.listen(ScreenClosedEvent.class, event -> this.reset());
    }

    @Override
    protected void onEnable() {
        this.reset();
    }

    @Override
    protected void onDisable() {
        this.reset();
    }

    private void onDrag(MouseDragEvent event) {
        if (event.getButton() != BUTTON_LEFT) {
            return;
        }

        Minecraft client = mc();
        LocalPlayer player = client.player;

        if (player == null || client.gameMode == null) {
            return;
        }

        AbstractContainerScreen<?> screen = event.getScreen();
        AbstractContainerMenu menu = screen.getMenu();

        // На курсоре уже висит стак — это ванильное раскладывание, его ломать не надо.
        if (!menu.getCarried().isEmpty()) {
            return;
        }

        Slot hovered = ((ContainerScreenInvoker) screen).lucid$hoveredSlot();

        if (hovered == null || hovered.index == this.lastSlot) {
            return;
        }

        if (!hovered.hasItem() || hovered.isFake() || !hovered.mayPickup(player)) {
            this.lastSlot = hovered.index;
            return;
        }

        // Задержка ещё не вышла. Слот специально НЕ запоминаем: вернёмся к нему
        // следующим движением, иначе предмет потеряется из-за тайминга.
        // Событие всё равно гасим, чтобы ваниль не подхватила предмет на курсор.
        if (!this.ready()) {
            event.cancel();
            return;
        }

        ((ContainerScreenInvoker) screen).lucid$slotClicked(
                hovered, hovered.index, BUTTON_LEFT, ContainerInput.QUICK_MOVE);

        this.lastSlot = hovered.index;
        this.lastTransfer = System.nanoTime();

        event.cancel();
    }

    private void reset() {
        this.lastSlot = NO_SLOT;
        this.lastTransfer = 0L;
    }

    private boolean ready() {
        int millis = this.delay.getAsInt();

        return millis <= 0 || System.nanoTime() - this.lastTransfer >= millis * 1_000_000L;
    }
}
