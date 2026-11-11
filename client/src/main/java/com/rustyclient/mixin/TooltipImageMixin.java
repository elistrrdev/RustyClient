package com.rustyclient.mixin;

import com.rustyclient.module.utility.ItemPreviews;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(ItemStack.class)
public abstract class TooltipImageMixin {
	@Inject(method = "getTooltipImage", at = @At("RETURN"), cancellable = true)
	private void rusty$preview(CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
		if (cir.getReturnValue().isPresent()) {
			return;
		}
		Optional<TooltipComponent> preview = ItemPreviews.image((ItemStack) (Object) this);
		if (preview.isPresent()) {
			cir.setReturnValue(preview);
		}
	}
}
