package com.rustyclient.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import com.rustyclient.module.Performance;
import com.rustyclient.module.visual.Visuals;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class PerformanceMixins {
	private PerformanceMixins() {
	}

	@Mixin(FramerateLimitTracker.class)
	public abstract static class Framerate {
		@Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
		private void rusty$limit(CallbackInfoReturnable<Integer> cir) {
			cir.setReturnValue(Performance.framerate(cir.getReturnValue()));
		}
	}

	@Mixin(ParticleEngine.class)
	public abstract static class Particles {
		@Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
		private void rusty$type(ParticleOptions options, double x, double y, double z, double xa, double ya, double za, CallbackInfoReturnable<Particle> cir) {
			if (Performance.hideParticle(options) || Visuals.Particles.hides(options)) {
				cir.setReturnValue(null);
			}
		}

		@Inject(method = "createParticle", at = @At("RETURN"))
		private void rusty$style(ParticleOptions options, double x, double y, double z, double xa, double ya, double za, CallbackInfoReturnable<Particle> cir) {
			Visuals.Particles.style(options, cir.getReturnValue());
		}

		@Inject(method = "add", at = @At("HEAD"), cancellable = true)
		private void rusty$terrain(Particle particle, CallbackInfo ci) {
			if (particle instanceof TerrainParticle && Performance.hideBlockParticles()) {
				ci.cancel();
			}
		}
	}
}
