package dev.lucid.event.impl;

import net.minecraft.world.inventory.ContainerInput;

import dev.lucid.event.CancellableEvent;

/** Клик по слоту. Отмена — клик не уходит. */
public final class ContainerClickEvent extends CancellableEvent {

	private final int button;
	private final ContainerInput input;

	public ContainerClickEvent(int button, ContainerInput input) {
		this.button = button;
		this.input = input;
	}

	public int getButton() {
		return this.button;
	}

	public ContainerInput getInput() {
		return this.input;
	}
}
