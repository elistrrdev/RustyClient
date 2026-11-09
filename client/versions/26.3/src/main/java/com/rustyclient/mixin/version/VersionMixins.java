package com.rustyclient.mixin.version;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rustyclient.module.visual.Visuals;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

public final class VersionMixins {
	private VersionMixins() {
	}

	@Mixin(FirstPersonHandsAndItemsRenderer.class)
	public abstract static class Hand {
		@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", ordinal = 0, shift = At.Shift.AFTER))
		private void rusty$hand(PlayerRenderState player, FirstPersonHandsAndItemsRenderState state, float partial, float pitch, InteractionHand hand, float swing, ItemStack stack, float equip, PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
			Visuals.CustomHand.apply(pose, hand == InteractionHand.MAIN_HAND, stack);
		}
	}

	@Mixin(ScreenEffectRenderer.class)
	public abstract static class Fire {
		@Inject(method = "submitFire", at = @At("HEAD"))
		private static void rusty$fireStart(PoseStack pose, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
			pose.pushPose();
			pose.translate(0f, -Visuals.ScreenOverlays.fireOffset(), 0f);
		}

		@Inject(method = "submitFire", at = @At("RETURN"))
		private static void rusty$fireEnd(PoseStack pose, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
			pose.popPose();
		}
	}

	@Mixin(targets = "net.minecraft.client.ClientClockManager$ClientClockInstance")
	public abstract static class Clock {
		@Inject(method = "totalTicks", at = @At("RETURN"), cancellable = true)
		private void rusty$time(CallbackInfoReturnable<Long> cir) {
			cir.setReturnValue(Visuals.TimeWeather.overrideTime(cir.getReturnValue()));
		}
	}

	@Mixin(GameRenderer.class)
	public abstract static class Motion {
		@Shadow
		@Final
		private List<Identifier> requestedPostEffects;

		@Inject(method = "update", at = @At("RETURN"))
		private void rusty$motionBlur(DeltaTracker delta, CallbackInfo ci) {
			Identifier id = Visuals.MotionBlur.effect();
			if (id != null) {
				requestedPostEffects.add(0, id);
			}
		}
	}
}
