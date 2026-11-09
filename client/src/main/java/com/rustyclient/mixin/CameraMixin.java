package com.rustyclient.mixin;

import com.rustyclient.core.Modules;
import com.rustyclient.module.visual.Visuals;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
	private void rusty$zoom(float partial, CallbackInfoReturnable<Float> cir) {
		Visuals.Zoom zoom = Modules.get(Visuals.Zoom.class);
		if (zoom != null && zoom.enabled()) {
			float divisor = zoom.fovDivisor();
			if (divisor > 1.0001f) {
				cir.setReturnValue(cir.getReturnValue() / divisor);
			}
		}
	}

	@ModifyVariable(method = "setRotation", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float rusty$freelookYaw(float yaw) {
		Visuals.Freelook look = Modules.get(Visuals.Freelook.class);
		return look != null && look.active() ? look.yaw : yaw;
	}

	@ModifyVariable(method = "setRotation", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private float rusty$freelookPitch(float pitch) {
		Visuals.Freelook look = Modules.get(Visuals.Freelook.class);
		return look != null && look.active() ? look.pitch : pitch;
	}
}
