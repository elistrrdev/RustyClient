package com.rustyclient.mixin;

import com.rustyclient.core.Modules;
import com.rustyclient.core.MouseButtons;
import com.rustyclient.module.utility.Utilities;
import com.rustyclient.module.visual.Visuals;
import com.rustyclient.platform.Compat;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public final class InputMixins {
	private InputMixins() {
	}

	@Mixin(MouseHandler.class)
	public abstract static class Scroll {
		@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
		private void rusty$scroll(long window, double horizontal, double vertical, CallbackInfo ci) {
			if (Compat.screen() != null) {
				return;
			}
			Visuals.Zoom zoom = Modules.get(Visuals.Zoom.class);
			if (zoom != null && zoom.scroll(vertical)) {
				ci.cancel();
				return;
			}
			Utilities.HotbarLock lock = Modules.get(Utilities.HotbarLock.class);
			if (lock != null && lock.blocksScroll()) {
				ci.cancel();
			}
		}
	}

	@Mixin(MouseHandler.class)
	public abstract static class Buttons {
		@Inject(method = "onButton", at = @At("HEAD"))
		private void rusty$button(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
			MouseButtons.update(info.button(), action);
		}
	}
}
