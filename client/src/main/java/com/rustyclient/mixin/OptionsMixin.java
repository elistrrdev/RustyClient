package com.rustyclient.mixin;

import com.rustyclient.core.RustyUsers;
import com.rustyclient.module.utility.Utilities;
import net.minecraft.client.Options;
import net.minecraft.server.level.ClientInformation;
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

	@Inject(method = "buildPlayerInformation", at = @At("RETURN"), cancellable = true)
	private void rusty$mark(CallbackInfoReturnable<ClientInformation> cir) {
		ClientInformation info = cir.getReturnValue();
		int parts = RustyUsers.mark(info.modelCustomisation());
		if (parts != info.modelCustomisation()) {
			cir.setReturnValue(new ClientInformation(info.language(), info.viewDistance(), info.chatVisibility(), info.chatColors(), parts, info.mainHand(), info.textFilteringEnabled(), info.allowsListing(), info.particleStatus()));
		}
	}
}
