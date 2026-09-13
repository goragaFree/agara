package dev.lucid.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import dev.lucid.module.impl.render.Removals;

/**
 * Блюр тыквы, если оверлей ещё рисует {@code Gui}, а не {@code Hud}.
 */
@Mixin(Gui.class)
public class GuiMixin {

	@Inject(
			method = {"extractTextureOverlay", "renderTextureOverlay"},
			at = @At("HEAD"),
			cancellable = true,
			require = 0
	)
	private void lucid$hidePumpkinTexture(
			GuiGraphicsExtractor graphics,
			Identifier texture,
			float alpha,
			CallbackInfo ci) {
		if (Removals.isPumpkinBlur(texture)) {
			ci.cancel();
		}
	}

	@WrapOperation(
			method = {"extractCameraOverlays", "extractMiscOverlays", "renderCameraOverlays", "renderMiscOverlays"},
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"
			),
			require = 0
	)
	private boolean lucid$hidePumpkinItem(ItemStack stack, Item item, Operation<Boolean> original) {
		return Removals.hideIfPumpkinItem(item, original.call(stack, item));
	}

	@WrapOperation(
			method = {"extractCameraOverlays", "extractMiscOverlays", "renderCameraOverlays", "renderMiscOverlays"},
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/level/ItemLike;)Z"
			),
			require = 0
	)
	private boolean lucid$hidePumpkinItemLike(ItemStack stack, ItemLike item, Operation<Boolean> original) {
		return Removals.hideIfPumpkinItemLike(item, original.call(stack, item));
	}

	@WrapOperation(
			method = {"extractCameraOverlays", "extractMiscOverlays", "renderCameraOverlays", "renderMiscOverlays"},
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/LivingEntity;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"
			),
			require = 0
	)
	private ItemStack lucid$hidePumpkinHelmet(
			LivingEntity entity,
			EquipmentSlot slot,
			Operation<ItemStack> original) {
		return Removals.hidePumpkinHelmet(slot, original.call(entity, slot));
	}
}
