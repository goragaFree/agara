package dev.lucid.event.impl;

import net.minecraft.network.protocol.Packet;

import dev.lucid.event.CancellableEvent;

/** Исходящий пакет. Слушателей нет — миксин даже не создаёт это событие. */
public final class PacketEvent extends CancellableEvent {

	private final Packet<?> packet;

	public PacketEvent(Packet<?> packet) {
		this.packet = packet;
	}

	public Packet<?> getPacket() {
		return this.packet;
	}
}
