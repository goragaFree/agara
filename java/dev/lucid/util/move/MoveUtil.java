package dev.lucid.util.move;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** Горизонтальное движение локального игрока. */
public final class MoveUtil {

	private MoveUtil() {
	}

	public static boolean isMoving() {
		Vec2 input = moveVector();

		return input != null && (input.x != 0.0F || input.y != 0.0F);
	}

	public static double horizontalSpeed() {
		LocalPlayer player = Minecraft.getInstance().player;

		if (player == null) {
			return 0.0;
		}

		Vec3 motion = player.getDeltaMovement();
		return Math.hypot(motion.x, motion.z);
	}

	public static boolean isStopped(double threshold) {
		return horizontalSpeed() < threshold;
	}

	private static Vec2 moveVector() {
		LocalPlayer player = Minecraft.getInstance().player;

		if (player == null || player.input == null) {
			return null;
		}

		ClientInput input = player.input;

		return input.getMoveVector();
	}
}
