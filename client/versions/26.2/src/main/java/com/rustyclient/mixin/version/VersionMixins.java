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

		@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"))
		private void rusty$itemScale(AbstractClientPlayer player, float partial, float pitch, InteractionHand hand, float swing, ItemStack stack, float equip, PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
			Visuals.CustomHand.scaleItem(pose, hand == InteractionHand.MAIN_HAND);
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

	@Mixin(net.minecraft.client.renderer.feature.NameTagFeatureRenderer.class)
	public abstract static class NameShadow {
		@org.spongepowered.asm.mixin.injection.ModifyArg(method = "prepareText", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;"), index = 4)
		private static boolean rusty$shadow(boolean shadow) {
			return Visuals.Nametags.shadow(shadow);
		}
	}

	@Mixin(net.minecraft.client.renderer.entity.EntityRenderDispatcher.class)
	public abstract static class EntityDistance {
		@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
		private void rusty$distance(net.minecraft.world.entity.Entity entity, net.minecraft.client.renderer.culling.Frustum culler, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
			if (com.rustyclient.module.Performance.hideEntity(entity, camX, camY, camZ)) {
				cir.setReturnValue(false);
			}
		}
	}

	@Mixin(net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher.class)
	public abstract static class BlockEntityDistance {
		@Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
		private void rusty$distance(net.minecraft.world.level.block.entity.BlockEntity blockEntity, float partial, net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay progress, boolean global, CallbackInfoReturnable<net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState> cir) {
			if (com.rustyclient.module.Performance.hideBlockEntity(blockEntity.getBlockPos())) {
				cir.setReturnValue(null);
			}
		}
	}

	@Mixin(com.mojang.blaze3d.platform.Window.class)
	public interface WindowAccess {
		@org.spongepowered.asm.mixin.gen.Invoker("setMode")
		void rusty$setMode();
	}

	@Mixin(com.mojang.blaze3d.platform.Window.class)
	public abstract static class Borderless {
		@Shadow
		private int x;
		@Shadow
		private int y;
		@Shadow
		private int width;
		@Shadow
		private int height;

		@org.spongepowered.asm.mixin.injection.Redirect(method = "setMode", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwGetWindowMonitor(J)J"))
		private long rusty$monitor(long window) {
			return com.rustyclient.platform.Compat.borderless ? 1L : org.lwjgl.glfw.GLFW.glfwGetWindowMonitor(window);
		}

		@org.spongepowered.asm.mixin.injection.Redirect(method = "setMode", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwSetWindowMonitor(JJIIIII)V", ordinal = 0))
		private void rusty$fullscreen(long window, long monitor, int px, int py, int w, int h, int rate) {
			org.lwjgl.glfw.GLFWVidMode mode = com.rustyclient.module.Performance.borderlessFullscreen() ? org.lwjgl.glfw.GLFW.glfwGetVideoMode(monitor) : null;
			if (mode == null) {
				org.lwjgl.glfw.GLFW.glfwSetWindowAttrib(window, org.lwjgl.glfw.GLFW.GLFW_DECORATED, org.lwjgl.glfw.GLFW.GLFW_TRUE);
				com.rustyclient.platform.Compat.borderless = false;
				org.lwjgl.glfw.GLFW.glfwSetWindowMonitor(window, monitor, px, py, w, h, rate);
				return;
			}
			int[] mx = new int[1];
			int[] my = new int[1];
			org.lwjgl.glfw.GLFW.glfwGetMonitorPos(monitor, mx, my);
			org.lwjgl.glfw.GLFW.glfwSetWindowAttrib(window, org.lwjgl.glfw.GLFW.GLFW_DECORATED, org.lwjgl.glfw.GLFW.GLFW_FALSE);
			org.lwjgl.glfw.GLFW.glfwSetWindowMonitor(window, 0L, mx[0], my[0], mode.width(), mode.height(), org.lwjgl.glfw.GLFW.GLFW_DONT_CARE);
			x = mx[0];
			y = my[0];
			width = mode.width();
			height = mode.height();
			com.rustyclient.platform.Compat.borderless = true;
		}

		@org.spongepowered.asm.mixin.injection.Redirect(method = "setMode", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwSetWindowMonitor(JJIIIII)V", ordinal = 1))
		private void rusty$windowed(long window, long monitor, int px, int py, int w, int h, int rate) {
			if (com.rustyclient.platform.Compat.borderless) {
				org.lwjgl.glfw.GLFW.glfwSetWindowAttrib(window, org.lwjgl.glfw.GLFW.GLFW_DECORATED, org.lwjgl.glfw.GLFW.GLFW_TRUE);
				com.rustyclient.platform.Compat.borderless = false;
			}
			org.lwjgl.glfw.GLFW.glfwSetWindowMonitor(window, monitor, px, py, w, h, rate);
		}

		@Inject(method = "isSoftScreen", at = @At("HEAD"), cancellable = true)
		private void rusty$soft(CallbackInfoReturnable<Boolean> cir) {
			if (com.rustyclient.platform.Compat.borderless) {
				cir.setReturnValue(false);
			}
		}
	}
}
