package dev.lucid.event;

import java.util.function.Consumer;

/**
 * Одна подписка: какое событие слушаем, чем обрабатываем и кто владелец.
 *
 * <p>Владелец нужен ровно для двух вещей: внятного сообщения в логе, если обработчик
 * упал, и возможности снять все его подписки разом.</p>
 *
 * @param <E> тип события
 */
public final class Subscription<E extends Event> {

    private final Class<E> type;
    private final Consumer<E> handler;
    private final Object owner;

    public Subscription(Class<E> type, Consumer<E> handler, Object owner) {
        this.type = type;
        this.handler = handler;
        this.owner = owner;
    }

    public Class<E> getType() {
        return this.type;
    }

    public Consumer<E> getHandler() {
        return this.handler;
    }

    public Object getOwner() {
        return this.owner;
    }
}
