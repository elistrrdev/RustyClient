package com.rustyclient.mixin;

import com.rustyclient.module.utility.Utilities;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public final class PlayerMixins {
	private PlayerMixins() {
	}

	@Mixin(LocalPlayer.class)
	public abstract static class Sprint {
		@Shadow
		private boolean canStartSprinting() {
			return false;
		}

		@Inject(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;tick()V", shift = At.Shift.AFTER))
		private void rusty$sprint(CallbackInfo ci) {
			Utilities.ToggleSprint sprint = Utilities.ToggleSprint.active();
			if (sprint == null) {
				return;
			}
			LocalPlayer player = (LocalPlayer) (Object) this;
			boolean run = sprint.shouldSprint();
			boolean sneak = sprint.sneaking();
			if (player.input != null && player.input.keyPresses != null) {
				Input old = player.input.keyPresses;
				player.input.keyPresses = new Input(old.forward(), old.backward(), old.left(), old.right(), old.jump(), old.shift() || sneak, run);
			}
			if (run) {
				if (!player.isSprinting() && canStartSprinting()) {
					player.setSprinting(true);
				}
			} else if (!sprint.allowsVanilla() && player.isSprinting() && !player.isSwimming()) {
				player.setSprinting(false);
			}
		}
	}
}
