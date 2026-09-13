package dev.lucid.module.impl.render;

/**
 * Данные физики, которых нет в ванильном {@code ItemEntityRenderState}.
 * К моменту {@code submit} самой сущности уже нет, поэтому всё нужное
 * записываем на extract.
 *
 * <p>Нельзя класть это в {@code dev.lucid.mixin}: пакет миксинов Mixin считает своим
 * и обычные классы оттуда грузить запрещает.</p>
 */
public interface ItemPhysicRenderState {

	boolean lucid$isOnGround();

	void lucid$setOnGround(boolean onGround);

	float lucid$rotX();

	void lucid$setRotX(float rotX);

	float lucid$rotY();

	void lucid$setRotY(float rotY);
}
