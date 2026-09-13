package dev.lucid.util.math;

import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Общая математика клиента.
 *
 * <p>Цвета сюда не входят — они живут в {@code ColorUtil}. Здесь только числа:
 * зажимы, интерполяция, углы, случайность и то, что нужно каждый кадр при
 * отрисовке и движении.</p>
 */
public final class MathUtil {

	public static final double PI2 = Math.PI * 2.0;

	private static final double DEG_TO_RAD = Math.PI / 180.0;
	private static final double RAD_TO_DEG = 180.0 / Math.PI;

	private MathUtil() {
	}

	// ------------------------------------------------------------------ зажим

	public static int clamp(int value, int min, int max) {
		return value < min ? min : Math.min(value, max);
	}

	public static float clamp(float value, float min, float max) {
		return value < min ? min : Math.min(value, max);
	}

	public static double clamp(double value, double min, double max) {
		return value < min ? min : Math.min(value, max);
	}

	public static float clamp01(float value) {
		return clamp(value, 0.0F, 1.0F);
	}

	public static double clamp01(double value) {
		return clamp(value, 0.0, 1.0);
	}

	// ------------------------------------------------------------------ линейное

	public static float lerp(float from, float to, float t) {
		return from + (to - from) * t;
	}

	public static double lerp(double from, double to, double t) {
		return from + (to - from) * t;
	}

	/** Доля пути от {@code from} к {@code to}. Если отрезок нулевой — 0. */
	public static float inverseLerp(float from, float to, float value) {
		float span = to - from;

		if (span == 0.0F) {
			return 0.0F;
		}

		return (value - from) / span;
	}

	public static float remap(float value, float inMin, float inMax, float outMin, float outMax) {
		return lerp(outMin, outMax, inverseLerp(inMin, inMax, value));
	}

	/** Сглаженный шаг 0..1. На краях производная нулевая — меньше дёрганья анимаций. */
	public static float smoothStep(float t) {
		float x = clamp01(t);
		return x * x * (3.0F - 2.0F * x);
	}

	/** Сдвинуть {@code current} к {@code target} не больше чем на {@code maxDelta}. */
	public static float approach(float current, float target, float maxDelta) {
		float delta = target - current;

		if (Math.abs(delta) <= maxDelta) {
			return target;
		}

		return current + Math.copySign(maxDelta, delta);
	}

	public static double approach(double current, double target, double maxDelta) {
		double delta = target - current;

		if (Math.abs(delta) <= maxDelta) {
			return target;
		}

		return current + Math.copySign(maxDelta, delta);
	}

	// ------------------------------------------------------------------ углы

	public static float wrapDegrees(float degrees) {
		return Mth.wrapDegrees(degrees);
	}

	public static double wrapDegrees(double degrees) {
		return Mth.wrapDegrees(degrees);
	}

	public static float toRadians(float degrees) {
		return (float) (degrees * DEG_TO_RAD);
	}

	public static float toDegrees(float radians) {
		return (float) (radians * RAD_TO_DEG);
	}

	/**
	 * Интерполяция углов кратчайшим путём. Обычный lerp на yaw даёт рывок
	 * при переходе через 180/−180.
	 */
	public static float lerpAngle(float from, float to, float t) {
		return from + wrapDegrees(to - from) * t;
	}

	// ------------------------------------------------------------------ кадр

	/** Доля кадра между тиками. Для позиций и поворотов при отрисовке. */
	public static float partialTick() {
		return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
	}
	
	public static float interpolate(float previous, float current) {
		return lerp(previous, current, partialTick());
	}

	public static double interpolate(double previous, double current) {
		return lerp(previous, current, partialTick());
	}

	public static Vec3 interpolate(Vec3 previous, Vec3 current) {
		float t = partialTick();
		return new Vec3(
				lerp(previous.x, current.x, t),
				lerp(previous.y, current.y, t),
				lerp(previous.z, current.z, t));
	}

	/** Экранная позиция сущности в этом кадре: между прошлым тиком и текущим. */
	public static Vec3 interpolate(Entity entity) {
		if (entity == null) {
			return Vec3.ZERO;
		}

		float t = partialTick();

		return new Vec3(
				lerp(entity.xo, entity.getX(), t),
				lerp(entity.yo, entity.getY(), t),
				lerp(entity.zo, entity.getZ(), t));
	}

	// ------------------------------------------------------------------ прочее

	public static double hypot(double x, double z) {
		return Math.hypot(x, z);
	}

	public static boolean approximately(float a, float b, float epsilon) {
		return Math.abs(a - b) <= epsilon;
	}

	public static boolean approximately(double a, double b, double epsilon) {
		return Math.abs(a - b) <= epsilon;
	}

	public static double roundTo(double value, double step) {
		if (step == 0.0) {
			return value;
		}

		return Math.round(value / step) * step;
	}

	public static int random(int min, int maxInclusive) {
		if (min == maxInclusive) {
			return min;
		}

		int from = Math.min(min, maxInclusive);
		int to = Math.max(min, maxInclusive);

		return ThreadLocalRandom.current().nextInt(from, to + 1);
	}

	public static float random(float min, float max) {
		return (float) random((double) min, (double) max);
	}

	public static double random(double min, double max) {
		if (min == max) {
			return min;
		}

		double from = Math.min(min, max);
		double to = Math.max(min, max);

		return ThreadLocalRandom.current().nextDouble(from, to);
	}

	public static boolean isHovered(double mouseX, double mouseY, double x, double y, double width, double height) {
		return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
	}

	/**
	 * Шаг мыши в градусах, как у ванили: {@code (sens * 0.6 + 0.2)^3 * 8}.
	 * Нужен, когда поворот надо класть на ту же сетку, что и курсор.
	 */
	public static double mouseGcd() {
		double sensitivity = Minecraft.getInstance().options.sensitivity().get();
		double f = sensitivity * 0.6 + 0.2;
		return f * f * f * 8.0;
	}
}
