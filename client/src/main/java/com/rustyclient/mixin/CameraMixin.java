package com.rustyclient.mixin;

import com.rustyclient.core.Modules;
import com.rustyclient.module.visual.Visuals;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private Entity entity;

	@Shadow
	protected abstract void setRotation(float yaw, float pitch);

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

	@Inject(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"))
	private void rusty$freelookThird(float partial, CallbackInfo ci) {
		Visuals.Freelook look = Visuals.Freelook.look();
		if (look != null && entity == Minecraft.getInstance().player) {
			if (look.mirrored()) {
				setRotation(look.yaw + 180f, -look.pitch);
			} else {
				setRotation(look.yaw, look.pitch);
			}
		}
	}

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void rusty$freelookFirst(float partial, CallbackInfo ci) {
		Visuals.Freelook look = Visuals.Freelook.look();
		if (look != null && look.firstPerson() && entity == Minecraft.getInstance().player) {
			setRotation(look.yaw, look.pitch);
		}
	}
}
