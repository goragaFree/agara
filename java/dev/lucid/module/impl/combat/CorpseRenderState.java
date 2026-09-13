package dev.lucid.module.impl.combat;

/**
 * Цвет задержанного тела — поле, которого нет в ванильном
 * {@code LivingEntityRenderState}.
 *
 * <p>В 26.2 сущность до {@code submit} не доезжает: там есть только состояние
 * отрисовки. Поэтому альфа тела считается на {@code extractRenderState}, где сущность
 * ещё видна, и кладётся сюда.</p>
 *
 * <p>Нельзя класть это в пакет {@code dev.lucid.mixin}: его Mixin считает своим и
 * обычные классы оттуда грузить запрещает.</p>
 */
public interface CorpseRenderState {

	/** ARGB для модели или {@code 0}, если тело рисуется как обычно. */
	int lucid$corpseTint();

	void lucid$setCorpseTint(int tint);
}
