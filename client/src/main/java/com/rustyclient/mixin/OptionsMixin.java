package com.rustyclient.mixin;

import com.rustyclient.module.utility.Utilities;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Options.class)
public abstract class OptionsMixin {
	@Inject(method = "getMenuBackgroundBlurriness", at = @At("RETURN"), cancellable = true)
	private void rusty$blurriness(CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(Utilities.MenuBlur.blurriness(cir.getReturnValue()));
	}
}
