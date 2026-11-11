package com.rustyclient.mixin;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemCooldowns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(ItemCooldowns.class)
public interface CooldownsAccessor {
	@Accessor("cooldowns")
	Map<Identifier, Object> rusty$cooldowns();

	@Accessor("tickCount")
	int rusty$tickCount();
}
