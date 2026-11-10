package com.rustyclient.mixin;

import com.rustyclient.module.visual.Visuals;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "handleKeybinds", at = @At("HEAD"))
	private void rusty$freelookPerspective(CallbackInfo ci) {
		Visuals.Freelook look = Visuals.Freelook.look();
		if (look == null || !look.perspectiveEnds()) {
			return;
		}
		boolean pressed = false;
		while (Minecraft.getInstance().options.keyTogglePerspective.consumeClick()) {
			pressed = true;
		}
		if (pressed) {
			look.stop();
		}
	}
}
