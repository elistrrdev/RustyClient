package com.rustyclient.mixin;

import net.minecraft.client.particle.SingleQuadParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SingleQuadParticle.class)
public interface ParticleColorAccessor {
	@Accessor("rCol")
	float rusty$red();

	@Accessor("gCol")
	float rusty$green();

	@Accessor("bCol")
	float rusty$blue();
}
