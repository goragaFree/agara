package dev.lucid.mixin;

import org.spongepowered.asm.mixin.Mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import io.netty.channel.ChannelFutureListener;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;

import dev.lucid.client.LucidClient;
import dev.lucid.event.impl.PacketEvent;

/**
 * Копия схемы Blade: {@code WrapMethod} на трёхаргументный send.
 * Нет слушателей — сразу original.call, сеть как у ванили.
 * Incoming не трогаем.
 */
@Mixin(Connection.class)
public abstract class ConnectionMixin {

	@WrapMethod(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V")
	private void lucid$send(Packet<?> packet, ChannelFutureListener listener, boolean flush, Operation<Void> original) {
		if (!LucidClient.EVENTS.hasListeners(PacketEvent.class)) {
			original.call(packet, listener, flush);
			return;
		}

		PacketEvent event = LucidClient.EVENTS.post(new PacketEvent(packet));

		if (event.isCancelled()) {
			return;
		}

		original.call(event.getPacket(), listener, flush);
	}
}
