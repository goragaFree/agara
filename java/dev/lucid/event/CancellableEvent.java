package dev.lucid.event;

/**
 * Событие, ванильное поведение которого модуль может погасить.
 *
 * <p>Важно: отмена также обрывает рассылку — остальные подписчики событие уже не
 * увидят. Иначе два модуля могли бы обработать один и тот же клик каждый по-своему.</p>
 */
public abstract class CancellableEvent extends Event {

    private boolean cancelled;

    /** Погасить ванильное поведение и остановить рассылку остальным подписчикам. */
    public final void cancel() {
        this.cancelled = true;
    }

    public final boolean isCancelled() {
        return this.cancelled;
    }
}
