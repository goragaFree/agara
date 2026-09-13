package dev.lucid.module.impl.render;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.ModeSetting;

/**
 * Физика выброшенных предметов: без ванильного парения, спин только вокруг
 * вертикали, на земле угол не щёлкает.
 */
public class ItemPhysic extends Module {

	private static final String MODE_NORMAL = "Обычная";

	/** Порог ванили: тоньше — «плоский» предмет (меч, еда), толще — блок/3D-модель. */
	private static final float FLAT_ITEM_DEPTH_THRESHOLD = 0.0625F;

	/** Спин вокруг вертикали в воздухе. */
	private static final float AIR_SPIN_Y = 5.0F;

	private static ItemPhysic instance;

	private final ModeSetting mode = this.addSetting(new ModeSetting(
			"physics", "Физика", "Как крутятся выброшенные предметы",
			MODE_NORMAL, java.util.List.of(MODE_NORMAL)));

	/** Последний yaw в воздухе — на земле замораживаем, а не сбрасываем. */
	private final Map<Integer, Phys> phys = new HashMap<>();

	public ItemPhysic() {
		super("item_physic", "ItemPhysic", Category.RENDER,
				"Физика выброшенных предметов");
		instance = this;
	}

	public static ItemPhysic getInstance() {
		return instance;
	}

	public static boolean isActive() {
		return instance != null && instance.isEnabled();
	}

	/**
	 * Снимает {@code onGround} и считает углы, пока сущность ещё есть.
	 */
	public void capture(ItemEntity entity, ItemEntityRenderState state, float partialTicks) {
		ItemPhysicRenderState extra = (ItemPhysicRenderState) state;
		boolean onGround = entity.onGround();
		extra.lucid$setOnGround(onGround);

		if (!this.isEnabled() || state.item.isEmpty()) {
			return;
		}

		AABB box = state.item.getModelBoundingBox();
		boolean flat = box.getZsize() <= FLAT_ITEM_DEPTH_THRESHOLD;
		float age = state.ageInTicks;
		float facing = (float) Math.toDegrees(state.bobOffset);

		Phys p = this.phys.computeIfAbsent(entity.getId(), id -> new Phys());

		// X никогда не кувыркаем: у блоков это бок + переворот при касании,
		// у плоских — скачок с age*8 на 90°. Плоский спрайт всегда плашмя.
		p.rotX = flat ? 90.0F : 0.0F;

		if (!onGround) {
			p.rotY = facing + age * AIR_SPIN_Y;
		}

		extra.lucid$setRotX(p.rotX);
		extra.lucid$setRotY(p.rotY);

		if (this.phys.size() > 512) {
			this.phys.clear();
		}
	}

	/**
	 * Подменяет ванильный submit: без подскока, один и тот же поворот в воздухе и на земле.
	 *
	 * @return {@code true}, если ванильную отрисовку нужно отменить
	 */
	public boolean submit(
			ItemEntityRenderState state,
			PoseStack poseStack,
			SubmitNodeCollector collector,
			RandomSource random) {
		if (!this.isEnabled() || state.item.isEmpty()) {
			return false;
		}

		ItemPhysicRenderState extra = (ItemPhysicRenderState) state;

		poseStack.pushPose();

		AABB box = state.item.getModelBoundingBox();
		boolean flat = box.getZsize() <= FLAT_ITEM_DEPTH_THRESHOLD;
		float minY = (float) box.minY;

		poseStack.translate(0.0F, -minY, 0.0F);

		if (this.mode.is(MODE_NORMAL)) {
			if (flat) {
				// И в воздухе, и на земле одна схема: иначе в конце падения
				// меняется пивот и предмет визуально переворачивается.
				float thickness = (float) Math.max(box.getZsize() / 2.0, 0.02);
				poseStack.translate(0.0F, thickness, 0.0F);
				poseStack.mulPose(Axis.YP.rotationDegrees(extra.lucid$rotY()));
				poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
			} else {
				float pivotX = (float) box.getCenter().x;
				float pivotY = (float) (box.getYsize() / 2.0);
				float pivotZ = (float) box.getCenter().z;
				poseStack.translate(pivotX, pivotY, pivotZ);
				poseStack.mulPose(Axis.YP.rotationDegrees(extra.lucid$rotY()));
				poseStack.translate(-pivotX, -pivotY, -pivotZ);
			}
		}

		ItemEntityRenderer.submitMultipleFromCount(
				poseStack, collector, state.lightCoords, state, random, box);

		poseStack.popPose();
		return true;
	}

	private static final class Phys {
		float rotX;
		float rotY;
	}
}
