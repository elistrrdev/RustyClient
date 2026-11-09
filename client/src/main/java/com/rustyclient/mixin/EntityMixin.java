package com.rustyclient.mixin;

import com.rustyclient.core.Modules;
import com.rustyclient.module.visual.Visuals;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {
	@Inject(method = "turn", at = @At("HEAD"), cancellable = true)
	private void rusty$freelook(double dx, double dy, CallbackInfo ci) {
		if ((Object) this != Minecraft.getInstance().player) {
			return;
		}
		Visuals.Freelook look = Modules.get(Visuals.Freelook.class);
		if (look != null && look.active()) {
			look.turn(dx, dy);
			ci.cancel();
		}
	}

	@ModifyVariable(method = "turn", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private double rusty$zoomYaw(double dx) {
		return scale(dx);
	}

	@ModifyVariable(method = "turn", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private double rusty$zoomPitch(double dy) {
		return scale(dy);
	}

	private double scale(double value) {
		if ((Object) this != Minecraft.getInstance().player) {
			return value;
		}
		Visuals.Zoom zoom = Modules.get(Visuals.Zoom.class);
		return zoom == null ? value : value * zoom.sensitivityScale();
	}
}
