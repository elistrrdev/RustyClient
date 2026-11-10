package com.rustyclient.mixin;

import com.rustyclient.core.RustyUsers;
import com.rustyclient.module.hud.Tablist;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class TabMixins {
	private TabMixins() {
	}

	@Mixin(PlayerTabOverlay.class)
	public interface Fields {
		@Accessor("header")
		Component rusty$header();

		@Accessor("footer")
		Component rusty$footer();
	}

	@Mixin(PlayerTabOverlay.class)
	public abstract static class Overlay {
		@Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
		private void rusty$icon(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
			if (RustyUsers.tablistIcon() && RustyUsers.uses(info.getProfile().id())) {
				cir.setReturnValue(RustyUsers.prefixed(cir.getReturnValue()));
			}
		}

		@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
		private void rusty$render(GuiGraphicsExtractor g, int width, Scoreboard scoreboard, Objective objective, CallbackInfo ci) {
			if (Tablist.render(g, (PlayerTabOverlay) (Object) this, scoreboard, objective)) {
				ci.cancel();
			}
		}
	}
}
