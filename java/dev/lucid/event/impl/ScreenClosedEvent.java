package dev.lucid.event.impl;

import net.minecraft.client.gui.screens.Screen;

import dev.lucid.event.Event;

/**
 * Экран закрывается. Нужен модулям, которые копят состояние между кадрами.
 *
 * <p>Причина существования: если экран закрыли прямо во время протаскивания,
 * отпускания кнопки модуль так и не увидит.</p>
 */
public final class ScreenClosedEvent extends Event {

    private final Screen screen;

    public ScreenClosedEvent(Screen screen) {
        this.screen = screen;
    }

    public Screen getScreen() {
        return this.screen;
    }
}
