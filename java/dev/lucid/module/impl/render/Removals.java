package dev.lucid.module.impl.render;

import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.MultiSelectSetting;

/**
 * Скрывает визуальный мусор: худ, эффекты, частицы, объекты.
 *
 * <p>Пункты в группах включают конкретные вырезы. Сам урон и серверные эффекты
 * не трогаем — только то, что рисуется у тебя на экране.</p>
 */
public class Removals extends Module {

	public static final String SCREEN_SHAKE = "тряска";
	public static final String SCREEN_FIRE = "огонь";
	public static final String SCREEN_EFFECT_ICONS = "иконки эффектов";
	public static final String SCREEN_PUMPKIN = "тыква";

	public static final String PARTICLES_EFFECTS = "эффекты";
	public static final String PARTICLES_FIRE = "огонь и дым";
	public static final String PARTICLES_DROPS = "капли";
	public static final String PARTICLES_EXPLOSION = "взрыв";

	/** Обычная трава в один блок. */
	public static final String OBJECTS_GRASS = "трава";

	/** Двублочная трава и большой папоротник. */
	public static final String OBJECTS_TALL_GRASS = "высокая трава";

	/** Цветы — одиночные и двублочные — и мелкая растительность вроде папоротника. */
	public static final String OBJECTS_PLANTS = "растения и цветы";

	/**
	 * Огонь и дым одним куском: пламя (обычное, свечное, адское), искры лавы,
	 * души и весь дым — от огня, лавы, костра, печки, потухшей свечи.
	 *
	 * <p>Делить по источнику смысла нет: ваниль шлёт один и тот же {@code FLAME}
	 * и от костра, и от спавнера, а {@code SMOKE} — и от огня, и от факела.
	 * Различать пришлось бы по блоку под частицей, то есть лазить в мир на каждую
	 * частицу и угадывать.</p>
	 */
	private static final Set<ParticleType<?>> FIRE_TYPES = Set.of(
			ParticleTypes.FLAME,
			ParticleTypes.SMALL_FLAME,
			ParticleTypes.SOUL_FIRE_FLAME,
			ParticleTypes.SOUL,
			ParticleTypes.LAVA,
			ParticleTypes.SMOKE,
			ParticleTypes.LARGE_SMOKE,
			ParticleTypes.WHITE_SMOKE,
			ParticleTypes.CAMPFIRE_COSY_SMOKE,
			ParticleTypes.CAMPFIRE_SIGNAL_SMOKE);

	/** Любые капли: вода, лава, мёд, нектар, сталактиты, плачущий обсидиан. */
	private static final Set<ParticleType<?>> DROP_TYPES = Set.of(
			ParticleTypes.DRIPPING_WATER,
			ParticleTypes.FALLING_WATER,
			ParticleTypes.DRIPPING_LAVA,
			ParticleTypes.FALLING_LAVA,
			ParticleTypes.LANDING_LAVA,
			ParticleTypes.DRIPPING_HONEY,
			ParticleTypes.FALLING_HONEY,
			ParticleTypes.LANDING_HONEY,
			ParticleTypes.FALLING_NECTAR,
			ParticleTypes.DRIPPING_DRIPSTONE_WATER,
			ParticleTypes.FALLING_DRIPSTONE_WATER,
			ParticleTypes.DRIPPING_DRIPSTONE_LAVA,
			ParticleTypes.FALLING_DRIPSTONE_LAVA,
			ParticleTypes.DRIPPING_OBSIDIAN_TEAR,
			ParticleTypes.FALLING_OBSIDIAN_TEAR,
			ParticleTypes.LANDING_OBSIDIAN_TEAR,
			ParticleTypes.FALLING_SPORE_BLOSSOM);

	/**
	 * Визуал взрыва: серые клубы и сам излучатель большого взрыва.
	 *
	 * <p>{@code EXPLOSION_EMITTER} сам порождает клубы по ходу жизни, поэтому гасим
	 * и его, и одиночные {@code EXPLOSION}. Звук, урон и отброс остаются.</p>
	 */
	private static final Set<ParticleType<?>> EXPLOSION_TYPES = Set.of(
			ParticleTypes.EXPLOSION,
			ParticleTypes.EXPLOSION_EMITTER);

	/**
	 * Двублочные цветы. Одиночные берём тегом {@code #minecraft:small_flowers},
	 * а для высоких тега в 26.2 нет — пришлось списком.
	 */
	private static final Set<Block> TALL_FLOWERS = Set.of(
			Blocks.SUNFLOWER,
			Blocks.LILAC,
			Blocks.ROSE_BUSH,
			Blocks.PEONY,
			Blocks.PITCHER_PLANT);

	/**
	 * Кусты и прочая мелкая растительность, которая не входит ни в тег
	 * {@code #minecraft:small_flowers}, ни в траву. Сюда же ушли ковровые блоки
	 * вроде опавших листьев и лепестков.
	 *
	 * <p>Сладкие ягоды специально не в списке: куст наносит урон и тормозит,
	 * поэтому его лучше видеть.</p>
	 */
	private static final Set<Block> PLANTS = Set.of(
			Blocks.FERN,
			Blocks.DEAD_BUSH,
			Blocks.BUSH,
			Blocks.FIREFLY_BUSH,
			Blocks.LEAF_LITTER,
			Blocks.WILDFLOWERS,
			Blocks.PINK_PETALS,
			Blocks.CACTUS_FLOWER);

	private static Removals instance;

	private final MultiSelectSetting screen = this.addSetting(new MultiSelectSetting(
			"screen", "Экран", SCREEN_SHAKE, SCREEN_FIRE, SCREEN_EFFECT_ICONS, SCREEN_PUMPKIN));
	private final MultiSelectSetting effects = this.addSetting(new MultiSelectSetting(
			"effects", "Эффекты"));
	private final MultiSelectSetting particles = this.addSetting(new MultiSelectSetting(
			"particles", "Частицы",
			PARTICLES_EFFECTS, PARTICLES_FIRE, PARTICLES_DROPS, PARTICLES_EXPLOSION));

	/**
	 * Вырез работает на этапе сборки меша чанка, поэтому при смене
	 * набора чанки пересобираются сразу: тот же вызов, что и у F3 + A.
	 */
	private final MultiSelectSetting objects = this.addSetting(new MultiSelectSetting(
			"objects", "Объекты", OBJECTS_GRASS, OBJECTS_TALL_GRASS, OBJECTS_PLANTS)
			.onChange(selected -> rebuildChunks()));

	public Removals() {
		super("removals", "Removals", Category.RENDER,
				"Убирает визуальные объекты и эффекты");
		instance = this;
	}

	@Override
	protected void onEnable() {
		rebuildChunks();
	}

	@Override
	protected void onDisable() {
		rebuildChunks();
	}

	/**
	 * Ровно то же, что делает F3 + A: просит клиент заново собрать
	 * геометрию всех чанков. Без этого уже собранный меш висит на экране
	 * до ближайшего изменения блока рядом.
	 */
	private static void rebuildChunks() {
		Minecraft client = mc();

		if (client == null || client.level == null || client.levelExtractor == null) {
			return;
		}

		client.levelExtractor.allChanged();
	}

	// ------------------------------------------------------------------ экран

	public static boolean hideFire() {
		return isHiding(instance == null ? null : instance.screen, SCREEN_FIRE);
	}

	public static boolean hideEffectIcons() {
		return isHiding(instance == null ? null : instance.screen, SCREEN_EFFECT_ICONS);
	}

	public static boolean hidePumpkin() {
		return isHiding(instance == null ? null : instance.screen, SCREEN_PUMPKIN);
	}

	// ------------------------------------------------------------------ частицы

	public static boolean hideEffectParticles() {
		return isHiding(instance == null ? null : instance.particles, PARTICLES_EFFECTS);
	}

	public static boolean hideFireParticles() {
		return isHiding(instance == null ? null : instance.particles, PARTICLES_FIRE);
	}

	public static boolean hideDropParticles() {
		return isHiding(instance == null ? null : instance.particles, PARTICLES_DROPS);
	}

	public static boolean hideExplosionParticles() {
		return isHiding(instance == null ? null : instance.particles, PARTICLES_EXPLOSION);
	}

	/**
	 * Решает, создавать ли частицу вообще. Тип — единственный признак: он же
	 * определяет, как частица выглядит, а значит и группу вырезов.
	 */
	public static boolean hideParticle(ParticleOptions options) {
		ParticleType<?> type = options.getType();

		if (type == ParticleTypes.ENTITY_EFFECT) {
			return hideEffectParticles();
		}

		if (FIRE_TYPES.contains(type)) {
			return hideFireParticles();
		}

		if (DROP_TYPES.contains(type)) {
			return hideDropParticles();
		}

		if (EXPLOSION_TYPES.contains(type)) {
			return hideExplosionParticles();
		}

		return false;
	}

	// ------------------------------------------------------------------ объекты

	public static boolean hideGrass() {
		return isHiding(instance == null ? null : instance.objects, OBJECTS_GRASS);
	}

	public static boolean hideTallGrass() {
		return isHiding(instance == null ? null : instance.objects, OBJECTS_TALL_GRASS);
	}

	public static boolean hidePlants() {
		return isHiding(instance == null ? null : instance.objects, OBJECTS_PLANTS);
	}

	/**
	 * Не рисовать этот блок вообще.
	 *
	 * <p>Сам блок остаётся на месте: по нему можно кликнуть, он ломается,
	 * сервер о нём знает — просто не попадает в геометрию чанка.</p>
	 *
	 * <p>Одиночные цветы берём тегом: каждое обновление добавляет новые, а тег
	 * {@code #minecraft:small_flowers} ваниль пополняет сама.</p>
	 */
	public static boolean hideBlock(BlockState state) {
		if (instance == null || !instance.isEnabled() || instance.objects.isEmpty()) {
			return false;
		}

		if (state.is(Blocks.SHORT_GRASS) || state.is(Blocks.SHORT_DRY_GRASS)) {
			return hideGrass();
		}

		if (state.is(Blocks.TALL_GRASS)
				|| state.is(Blocks.TALL_DRY_GRASS)
				|| state.is(Blocks.LARGE_FERN)) {
			return hideTallGrass();
		}

		if (state.is(BlockTags.SMALL_FLOWERS)
				|| TALL_FLOWERS.contains(state.getBlock())
				|| PLANTS.contains(state.getBlock())) {
			return hidePlants();
		}

		return false;
	}

	// ------------------------------------------------------------------ тыква

	public static boolean isPumpkinBlur(Identifier texture) {
		return hidePumpkin() && texture.getPath().contains("pumpkin");
	}

	public static boolean hideIfPumpkinItem(Item item, boolean original) {
		if (hidePumpkin() && item == Items.CARVED_PUMPKIN) {
			return false;
		}

		return original;
	}

	public static boolean hideIfPumpkinItemLike(ItemLike item, boolean original) {
		if (hidePumpkin() && item.asItem() == Items.CARVED_PUMPKIN) {
			return false;
		}

		return original;
	}

	public static ItemStack hidePumpkinHelmet(EquipmentSlot slot, ItemStack original) {
		if (hidePumpkin() && slot == EquipmentSlot.HEAD) {
			return ItemStack.EMPTY;
		}

		return original;
	}

	private static boolean isHiding(MultiSelectSetting group, String option) {
		return instance != null && instance.isEnabled() && group != null && group.isSelected(option);
	}
}
