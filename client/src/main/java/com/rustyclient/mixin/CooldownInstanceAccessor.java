package com.rustyclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.item.ItemCooldowns$CooldownInstance")
public interface CooldownInstanceAccessor {
	@Accessor("startTime")
	int rusty$startTime();

	@Accessor("endTime")
	int rusty$endTime();
}
