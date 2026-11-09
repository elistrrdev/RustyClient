package com.rustyclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rustyclient.module.visual.Visuals;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Unique
	private final Matrix4f rusty$pose = new Matrix4f();
	@Unique
	private final Matrix3f rusty$normal = new Matrix3f();

	@Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
	private void rusty$hurtStart(CameraRenderState camera, PoseStack pose, CallbackInfo ci) {
		float scale = Visuals.ScreenOverlays.hurtScale();
		if (scale <= 0f) {
			ci.cancel();
			return;
		}
		rusty$pose.set(pose.last().pose());
		rusty$normal.set(pose.last().normal());
	}

	@Inject(method = "bobHurt", at = @At("RETURN"))
	private void rusty$hurtEnd(CameraRenderState camera, PoseStack pose, CallbackInfo ci) {
		float scale = Visuals.ScreenOverlays.hurtScale();
		if (scale >= 1f || scale <= 0f) {
			return;
		}
		Matrix4f after = new Matrix4f(pose.last().pose());
		Matrix3f afterNormal = new Matrix3f(pose.last().normal());
		pose.last().pose().set(new Matrix4f(rusty$pose).lerp(after, scale));
		pose.last().normal().set(new Matrix3f(rusty$normal).lerp(afterNormal, scale));
	}
}
