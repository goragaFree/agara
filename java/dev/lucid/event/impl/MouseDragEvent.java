package dev.lucid.event.impl;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import dev.lucid.event.CancellableEvent;

/**
 * Протаскивание мыши с зажатой кнопкой внутри контейнерного экрана.
 *
 * <p>Отмена гасит ванильную обработку — именно это нужно {@code ItemScroller},
 * иначе игра параллельно начнёт тащить предмет курсором.</p>
 */
public final class MouseDragEvent extends CancellableEvent {

    private final AbstractContainerScreen<?> screen;
    private final int button;

    public MouseDragEvent(AbstractContainerScreen<?> screen, int button) {
        this.screen = screen;
        this.button = button;
    }

    public AbstractContainerScreen<?> getScreen() {
        return this.screen;
    }

    /** Код кнопки GLFW: 0 — левая, 1 — правая. */
    public int getButton() {
        return this.button;
    }
}
