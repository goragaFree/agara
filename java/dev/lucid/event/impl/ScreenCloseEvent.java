package dev.lucid.event.impl;

import net.minecraft.client.gui.screens.Screen;

import dev.lucid.event.CancellableEvent;

/** Закрытие контейнера. Можно отменить и закрыть позже, когда игрок остановится. */
public final class ScreenCloseEvent extends CancellableEvent {

	private final Screen screen;

	public ScreenCloseEvent(Screen screen) {
		this.screen = screen;
	}

	public Screen getScreen() {
		return this.screen;
	}
}
