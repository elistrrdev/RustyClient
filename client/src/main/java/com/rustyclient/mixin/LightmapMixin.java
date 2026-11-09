package com.rustyclient.mixin;

import com.rustyclient.module.visual.Visuals;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapMixin {
	private float rusty$last = -1f;

	@Inject(method = "extract", at = @At("TAIL"))
	private void rusty$fullbright(LightmapRenderState state, float partial, CallbackInfo ci) {
		float strength = Visuals.Fullbright.strength();
		if (strength != rusty$last) {
			rusty$last = strength;
			state.needsUpdate = true;
		}
		if (strength <= 0f) {
			return;
		}
		state.nightVisionEffectIntensity = Math.max(state.nightVisionEffectIntensity, strength);
		state.nightVisionColor = new Vector3f(1f, 1f, 1f);
		state.brightness = Math.max(state.brightness, strength);
		state.needsUpdate = true;
	}
}
