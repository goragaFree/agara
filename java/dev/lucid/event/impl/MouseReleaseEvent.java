package dev.lucid.event.impl;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import dev.lucid.event.Event;

/**
 * Кнопку мыши отпустили внутри контейнерного экрана.
 *
 * <p>Отмене не подлежит сознательно: отпускание нужно и ванили тоже, иначе
 * игра останется думать, что кнопка всё ещё зажата.</p>
 */
public final class MouseReleaseEvent extends Event {

    private final AbstractContainerScreen<?> screen;
    private final int button;

    public MouseReleaseEvent(AbstractContainerScreen<?> screen, int button) {
        this.screen = screen;
        this.button = button;
    }

    public AbstractContainerScreen<?> getScreen() {
        return this.screen;
    }

    public int getButton() {
        return this.button;
    }
}
