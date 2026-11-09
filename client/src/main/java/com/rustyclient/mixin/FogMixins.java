package com.rustyclient.mixin;

import com.rustyclient.module.visual.Visuals;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.LavaFogEnvironment;
import net.minecraft.client.renderer.fog.environment.WaterFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public final class FogMixins {
	private FogMixins() {
	}

	@Mixin(WaterFogEnvironment.class)
	public abstract static class Water {
		@Inject(method = "setupFog", at = @At("TAIL"))
		private void rusty$clear(FogData fog, Camera camera, ClientLevel level, float distance, DeltaTracker delta, CallbackInfo ci) {
			Visuals.ClearFluids.apply(fog, false);
		}
	}

	@Mixin(LavaFogEnvironment.class)
	public abstract static class Lava {
		@Inject(method = "setupFog", at = @At("TAIL"))
		private void rusty$clear(FogData fog, Camera camera, ClientLevel level, float distance, DeltaTracker delta, CallbackInfo ci) {
			Visuals.ClearFluids.apply(fog, true);
		}
	}
}
