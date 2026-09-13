package dev.lucid.event;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import dev.lucid.Lucid;

/**
 * Шина событий клиента.
 *
 * <p>Смысл такой: миксины и коллбэки Fabric больше не вызывают модули по имени,
 * а просто кладут событие в шину. Кто его ждёт — его дело.</p>
 *
 * <p>Списки подписчиков на {@link CopyOnWriteArrayList} не ради потоков, а ради
 * реентрантности: обработчик вполне может выключить свой или соседний модуль прямо
 * во время рассылки, а это правка того самого списка, по которому идёт цикл.
 * На обычном {@code ArrayList} это {@code ConcurrentModificationException} в самом
 * неудобном месте — в рендере или в обработке клика.</p>
 *
 * <p>Тип значения в карте — именно {@code CopyOnWriteArrayList}, а не {@code List}:
 * {@code addIfAbsent} есть только у конкретного класса, в интерфейсе его нет.</p>
 */
public final class EventBus {

    /** тип события -> его подписчики в порядке подписки. */
    private final Map<Class<? extends Event>, CopyOnWriteArrayList<Subscription<? extends Event>>> listeners =
            new ConcurrentHashMap<>();

    public void subscribe(Subscription<? extends Event> subscription) {
        this.listeners
                .computeIfAbsent(subscription.getType(), type -> new CopyOnWriteArrayList<>())
                .addIfAbsent(subscription);
    }

    public void unsubscribe(Subscription<? extends Event> subscription) {
        CopyOnWriteArrayList<Subscription<? extends Event>> bucket =
                this.listeners.get(subscription.getType());

        if (bucket != null) {
            bucket.remove(subscription);
        }
    }

    /** Снять все подписки владельца разом. */
    public void unsubscribeAll(Object owner) {
        for (CopyOnWriteArrayList<Subscription<? extends Event>> bucket : this.listeners.values()) {
            bucket.removeIf(subscription -> subscription.getOwner() == owner);
        }
    }

    /**
     * Разослать событие и вернуть его же — чтобы вызывающая сторона могла тут же
     * спросить {@code isCancelled()} или забрать изменённые поля.
     *
     * <p>Обработчики вызываются в обёртке try/catch: упавший модуль не должен ронять
     * игру и не должен мешать остальным получить то же событие.</p>
     */
    @SuppressWarnings("unchecked")
    public <E extends Event> E post(E event) {
        CopyOnWriteArrayList<Subscription<? extends Event>> bucket =
                this.listeners.get(event.getClass());

        if (bucket == null || bucket.isEmpty()) {
            return event;
        }

        boolean cancellable = event instanceof CancellableEvent;

        for (Subscription<? extends Event> subscription : bucket) {
            // Отменённое событие дальше не идёт.
            if (cancellable && ((CancellableEvent) event).isCancelled()) {
                break;
            }

            try {
                ((Consumer<E>) subscription.getHandler()).accept(event);
            } catch (Throwable t) {
                Lucid.LOGGER.error("Listener of '{}' owned by '{}' threw",
                        event, subscription.getOwner(), t);
            }
        }

        return event;
    }

    /** Есть ли слушатели. Миксин пакетов без этого не должен трогать сеть. */
    public boolean hasListeners(Class<? extends Event> type) {
        CopyOnWriteArrayList<Subscription<? extends Event>> bucket = this.listeners.get(type);
        return bucket != null && !bucket.isEmpty();
    }
}
