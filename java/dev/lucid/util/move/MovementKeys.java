package dev.lucid.util.move;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

import dev.lucid.mixin.KeyMappingAccessor;

/**
 * Физическое состояние клавиш движения.
 *
 * <p>Пока открыт экран, ваниль не обновляет {@code KeyMapping}. GLFW клавишу
 * всё равно видит — читаем её и пишем в бинд.</p>
 */
public final class MovementKeys {

	private MovementKeys() {
	}

	public static KeyMapping[] all() {
		Options options = Minecraft.getInstance().options;

		return new KeyMapping[] {
			options.keyUp,
			options.keyDown,
			options.keyLeft,
			options.keyRight,
			options.keyJump,
			options.keySprint
		};
	}

	public static boolean physicalDown(KeyMapping mapping) {
		InputConstants.Key key = ((KeyMappingAccessor) mapping).lucid$key();

		if (key == InputConstants.UNKNOWN) {
			return false;
		}

		var window = Minecraft.getInstance().getWindow();

		if (key.getType() == InputConstants.Type.MOUSE) {
			return GLFW.glfwGetMouseButton(window.handle(), key.getValue()) == GLFW.GLFW_PRESS;
		}

		return InputConstants.isKeyDown(window, key.getValue());
	}

	public static void applyPhysical() {
		for (KeyMapping mapping : all()) {
			mapping.setDown(physicalDown(mapping));
		}
	}

	public static void release() {
		for (KeyMapping mapping : all()) {
			mapping.setDown(false);
		}
	}
}
