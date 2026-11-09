package com.rustyclient.mixin;

import com.rustyclient.core.Combat;
import com.rustyclient.core.Modules;
import com.rustyclient.module.combat.CombatHuds;
import com.rustyclient.module.visual.Visuals;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class WorldMixins {
	private WorldMixins() {
	}

	@Mixin(Level.class)
	public abstract static class Weather {
		@Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true)
		private void rusty$rain(float partial, CallbackInfoReturnable<Float> cir) {
			if (((Level) (Object) this).isClientSide()) {
				cir.setReturnValue(Visuals.TimeWeather.rain(cir.getReturnValue()));
			}
		}

		@Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true)
		private void rusty$thunder(float partial, CallbackInfoReturnable<Float> cir) {
			if (((Level) (Object) this).isClientSide()) {
				cir.setReturnValue(Visuals.TimeWeather.thunder(cir.getReturnValue()));
			}
		}
	}

	@Mixin(LivingEntity.class)
	public abstract static class Events {
		@Inject(method = "handleEntityEvent", at = @At("HEAD"))
		private void rusty$event(byte id, CallbackInfo ci) {
			LivingEntity self = (LivingEntity) (Object) this;
			if (!self.level().isClientSide()) {
				return;
			}
			if (id == 35) {
				CombatHuds.TotemPops pops = Modules.get(CombatHuds.TotemPops.class);
				if (self == Minecraft.getInstance().player && pops != null && !pops.countSelf()) {
					return;
				}
				Combat.onTotemPop(self);
			} else if (id == 3) {
				Combat.onDeath(self);
			}
		}
	}
}
