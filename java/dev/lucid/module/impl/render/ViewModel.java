package dev.lucid.module.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.world.InteractionHand;

import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.SliderSetting;

/**
 * Положение рук от первого лица — те же шесть ползунков, что в Rich:
 * X/Y/Z основной и X/Y/Z второй руки.
 */
public class ViewModel extends Module {

	private static ViewModel instance;

	private final SliderSetting mainX = this.addSetting(new SliderSetting(
			"main_x", "Основная рука X", "Сдвиг основной руки влево / вправо",
			0.0, -1.0, 1.0, 0.05));
	private final SliderSetting mainY = this.addSetting(new SliderSetting(
			"main_y", "Основная рука Y", "Сдвиг основной руки вниз / вверх",
			0.0, -1.0, 1.0, 0.05));
	private final SliderSetting mainZ = this.addSetting(new SliderSetting(
			"main_z", "Основная рука Z", "Сдвиг основной руки дальше / ближе",
			0.0, -2.5, 2.5, 0.05));

	private final SliderSetting offX = this.addSetting(new SliderSetting(
			"off_x", "Второстепенная рука X", "Сдвиг второй руки влево / вправо",
			0.0, -1.0, 1.0, 0.05));
	private final SliderSetting offY = this.addSetting(new SliderSetting(
			"off_y", "Второстепенная рука Y", "Сдвиг второй руки вниз / вверх",
			0.0, -1.0, 1.0, 0.05));
	private final SliderSetting offZ = this.addSetting(new SliderSetting(
			"off_z", "Второстепенная рука Z", "Сдвиг второй руки дальше / ближе",
			0.0, -2.5, 2.5, 0.05));

	public ViewModel() {
		super("view_model", "ViewModel", Category.RENDER,
				"Положение рук от первого лица");
		instance = this;
	}

	public static boolean isActive() {
		return instance != null && instance.isEnabled();
	}

	/** Сдвиг стека текущей руки. Вызывать после ванильного {@code pushPose}. */
	public static void apply(InteractionHand hand, PoseStack pose) {
		if (!isActive()) {
			return;
		}

		ViewModel vm = instance;

		if (hand == InteractionHand.MAIN_HAND) {
			pose.translate(vm.mainX.getAsFloat(), vm.mainY.getAsFloat(), vm.mainZ.getAsFloat());
		} else {
			pose.translate(vm.offX.getAsFloat(), vm.offY.getAsFloat(), vm.offZ.getAsFloat());
		}
	}
}
