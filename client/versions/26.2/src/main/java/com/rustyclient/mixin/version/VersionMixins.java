package com.rustyclient.mixin.version;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.rustyclient.module.visual.Visuals;
import net.minecraft.client.ClientClockManager;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Holder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class VersionMixins {
	private VersionMixins() {
	}

	@Mixin(ItemInHandRenderer.class)
	public abstract static class Hand {
		@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", ordinal = 0, shift = At.Shift.AFTER))
		private void rusty$hand(AbstractClientPlayer player, float partial, float pitch, InteractionHand hand, float swing, ItemStack stack, float equip, PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
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

	@Mixin(ClientClockManager.class)
	public abstract static class Clock {
		@Inject(method = "getTotalTicks", at = @At("RETURN"), cancellable = true)
		private void rusty$time(Holder<WorldClock> clock, CallbackInfoReturnable<Long> cir) {
			cir.setReturnValue(Visuals.TimeWeather.overrideTime(cir.getReturnValue()));
		}
	}

	@Mixin(GameRenderer.class)
	public abstract static class Motion {
		@Shadow
		@Final
		private Minecraft minecraft;

		@Shadow
		@Final
		private CrossFrameResourcePool resourcePool;

		@Shadow
		@Final
		private RenderTarget mainRenderTarget;

		@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V", shift = At.Shift.AFTER))
		private void rusty$motionBlur(DeltaTracker delta, boolean advance, CallbackInfo ci) {
			Identifier id = Visuals.MotionBlur.effect();
			if (id == null) {
				return;
			}
			PostChain chain = minecraft.getShaderManager().getPostChain(id, LevelTargetBundle.MAIN_TARGETS);
			if (chain != null) {
				chain.process(mainRenderTarget, resourcePool);
			}
		}
	}

	@Mixin(net.minecraft.client.renderer.SubmitNodeCollection.class)
	public abstract static class Names {
		@Inject(method = "submitNameTag", at = @At("HEAD"))
		private void rusty$nameStart(PoseStack pose, net.minecraft.world.phys.Vec3 attachment, int offset, net.minecraft.network.chat.Component name, boolean seeThrough, int light, net.minecraft.client.renderer.state.level.CameraRenderState camera, CallbackInfo ci) {
			Visuals.Nametags.begin(pose, attachment);
		}

		@Inject(method = "submitNameTag", at = @At("RETURN"))
		private void rusty$nameEnd(PoseStack pose, net.minecraft.world.phys.Vec3 attachment, int offset, net.minecraft.network.chat.Component name, boolean seeThrough, int light, net.minecraft.client.renderer.state.level.CameraRenderState camera, CallbackInfo ci) {
			Visuals.Nametags.end(pose);
		}
	}
}
