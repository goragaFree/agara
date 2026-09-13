package dev.lucid.module.impl.render;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.ModeSetting;
import dev.lucid.setting.MultiSelectSetting;
import dev.lucid.util.math.MathUtil;
import dev.lucid.util.render.ColorUtil;
import dev.lucid.util.render.Render3D;

/**
 * Подсветка сущностей. Цели общие для всех режимов, режим — только визуал.
 *
 * <p>Сейчас один режим — {@code Бокс}: 3D-коробка по хитбоксу в мире, сквозь стены.
 * Рёбра в блоках, не на экране: вдали коробка мельчает, как любая геометрия.</p>
 */
public class Esp extends Module {

	private static final String MODE_BOX = "Бокс";

	private static final String TARGET_PLAYERS = "Игроки";
	private static final String TARGET_ANIMALS = "Животные";
	private static final String TARGET_MOBS = "Мобы";
	private static final String TARGET_WARDEN = "Варден";

	private static final List<String> TARGETS = List.of(
			TARGET_PLAYERS, TARGET_ANIMALS, TARGET_MOBS, TARGET_WARDEN);

	/** RGB без альфы: игроки — голубой, животные — зелёный, мобы — красный, варден — скалк. */
	private static final int COLOR_PLAYERS = 0x55C1FF;
	private static final int COLOR_ANIMALS = 0x7CFF55;
	private static final int COLOR_MOBS = 0xFF5555;
	private static final int COLOR_WARDEN = 0x22E0C8;

	private static final float FILL_ALPHA = 0.12F;
	private static final float OUTLINE_ALPHA = 0.95F;

	/** Запас, чтобы контур не мерцал на модели. */
	private static final double GROW = 0.02;

	/** Базовая толщина ребра в блоках. Дальше чуть растёт, иначе нитка пропадает. */
	private static final double THICKNESS_NEAR = 0.018;
	private static final double THICKNESS_PER_BLOCK = 0.0016;
	private static final double THICKNESS_MAX = 0.07;

	private final ModeSetting mode = this.addSetting(new ModeSetting(
			"mode", "Режим", "Как рисовать подсветку",
			MODE_BOX, List.of(MODE_BOX)));

	private final MultiSelectSetting targets = this.addSetting(new MultiSelectSetting(
			"targets", "Цели", "Кого подсвечивать",
			TARGETS, TARGETS));

	public Esp() {
		super("esp_box", "ESP Box", Category.RENDER,
				"3D-коробки вокруг игроков, мобов, животных и вардена");
	}

	@Override
	public void onRegister() {
		Render3D.register(this::extract);
	}

	private void extract(Render3D.Frame frame) {
		if (!this.isEnabled() || this.targets.isEmpty()) {
			return;
		}

		Minecraft client = mc();

		if (client.level == null || client.player == null) {
			return;
		}

		Vec3 camera = client.gameRenderer.mainCamera().position();

		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity == client.player || entity.isRemoved()) {
				continue;
			}

			int rgb = this.colorOf(entity);

			if (rgb < 0) {
				continue;
			}

			if (this.mode.is(MODE_BOX)) {
				this.drawBox(frame, entity, camera, rgb);
			}
		}
	}

	/**
	 * Варден проверяется раньше мобов: он и враг, и отдельная категория.
	 * Хоглин тоже {@code Animal} и {@code Enemy} — его рисуем как моба.
	 */
	private int colorOf(Entity entity) {
		if (entity instanceof Player) {
			return this.targets.isSelected(TARGET_PLAYERS) ? COLOR_PLAYERS : -1;
		}

		if (entity instanceof Warden) {
			return this.targets.isSelected(TARGET_WARDEN) ? COLOR_WARDEN : -1;
		}

		if (entity instanceof Enemy) {
			return this.targets.isSelected(TARGET_MOBS) ? COLOR_MOBS : -1;
		}

		if (entity instanceof Animal) {
			return this.targets.isSelected(TARGET_ANIMALS) ? COLOR_ANIMALS : -1;
		}

		return -1;
	}

	private void drawBox(Render3D.Frame frame, Entity entity, Vec3 camera, int rgb) {
		Vec3 interpolated = MathUtil.interpolate(entity);
		AABB box = entity.getBoundingBox().move(
				interpolated.x - entity.getX(),
				interpolated.y - entity.getY(),
				interpolated.z - entity.getZ())
				.inflate(GROW);

		double distance = interpolated.distanceTo(camera);
		double thickness = Math.min(THICKNESS_NEAR + distance * THICKNESS_PER_BLOCK, THICKNESS_MAX);

		frame.filledBox(
				box.minX, box.minY, box.minZ,
				box.maxX, box.maxY, box.maxZ,
				ColorUtil.withAlpha(rgb, FILL_ALPHA),
				true);

		frame.boxOutline(
				box.minX, box.minY, box.minZ,
				box.maxX, box.maxY, box.maxZ,
				thickness,
				ColorUtil.withAlpha(rgb, OUTLINE_ALPHA),
				true);
	}
}
