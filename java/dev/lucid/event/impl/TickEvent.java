package dev.lucid.event.impl;

import net.minecraft.client.Minecraft;

import dev.lucid.event.Event;

/**
 * Конец клиентского тика. Рассылается только когда игрок в мире,
 * то есть {@code client.level} и {@code client.player} гарантированно не null.
 *
 * <p>Отмене не подлежит: тик игры уже произошёл, отменять тут нечего.</p>
 */
public final class TickEvent extends Event {

    private final Minecraft client;

    public TickEvent(Minecraft client) {
        this.client = client;
    }

    public Minecraft getClient() {
        return this.client;
    }
}
