package dev.lucid.event.impl;

import dev.lucid.event.CancellableEvent;

/**
 * Игра спрашивает время мира в тиках.
 *
 * <p>Это событие-вопрос, а не уведомление: подписчик может заменить значение
 * через {@link #setTime(long)} и отменить событие. Отмена здесь значит не «ничего не делай»,
 * а «ответ подменён, используй его вместо ванильного».</p>
 *
 * <p>Время только клиентское и только визуальное: сервер своё время считает сам и
 * о подмене не узнаёт. Мобы, сон и рост посевов живут по серверному времени.</p>
 *
 * <p>Событие рассылается очень часто — по несколько раз за кадр. Обработчик должен
 * быть дешёвым: никаких созданий объектов и обходов списков.</p>
 */
public final class TimeEvent extends CancellableEvent {

    /** Длина суток в тиках. */
    public static final long DAY_LENGTH = 24_000L;

    private long time;

    public TimeEvent(long time) {
        this.time = time;
    }

    public long getTime() {
        return this.time;
    }

    public void setTime(long time) {
        this.time = time;
    }
}
