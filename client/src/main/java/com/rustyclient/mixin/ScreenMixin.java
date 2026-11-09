package com.rustyclient.mixin;

import com.rustyclient.core.Modules;
import com.rustyclient.module.utility.Utilities;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {
	@Shadow
	public int width;
	@Shadow
	public int height;

	@Shadow
	protected abstract void extractBlurredBackground(GuiGraphicsExtractor g);

	@Inject(method = "extractTransparentBackground", at = @At("HEAD"), cancellable = true)
	private void rusty$blur(GuiGraphicsExtractor g, CallbackInfo ci) {
		Utilities.MenuBlur blur = Modules.get(Utilities.MenuBlur.class);
		if (blur == null || !blur.enabled()) {
			return;
		}
		Object self = this;
		boolean container = self instanceof AbstractContainerScreen<?> && blur.containers.on();
		boolean chat = self instanceof ChatScreen && blur.chat.on();
		if (!container && !chat) {
			return;
		}
		extractBlurredBackground(g);
		int alpha = Math.round(blur.dim.f() * 255);
		if (alpha > 0) {
			g.fill(0, 0, width, height, alpha << 24);
		}
		ci.cancel();
	}
}
