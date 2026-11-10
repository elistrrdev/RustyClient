package com.rustyclient.mixin;

import com.rustyclient.module.visual.Visuals;
import com.rustyclient.platform.Compat;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.IntSupplier;

@Mixin(LoadingOverlay.class)
public abstract class LoadingMixin {
	@Shadow
	private long fadeOutStart;

	@Shadow
	private long fadeInStart;

	@Shadow
	@Final
	private boolean fadeIn;

	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void rusty$skipFadeIn(CallbackInfo ci) {
		if (fadeIn && fadeInStart == -1L && Visuals.Fadeless.skipsFadeIn()) {
			fadeInStart = Util.getMillis() - 2000L;
		}
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void rusty$skipFadeOut(CallbackInfo ci) {
		if (fadeOutStart > -1L && Visuals.Fadeless.skipsFadeOut()) {
			Compat.setOverlay(null);
		}
	}

	@Redirect(method = "extractRenderState", at = @At(value = "INVOKE", target = "Ljava/util/function/IntSupplier;getAsInt()I"))
	private int rusty$background(IntSupplier supplier) {
		Integer color = Visuals.LoadingScreen.color();
		return color == null ? supplier.getAsInt() : color;
	}
}
