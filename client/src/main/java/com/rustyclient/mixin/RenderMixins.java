package com.rustyclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rustyclient.core.Modules;
import com.rustyclient.module.visual.Visuals;
import com.rustyclient.platform.Compat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class RenderMixins {
	private RenderMixins() {
	}

	@Mixin(LivingEntityRenderer.class)
	public abstract static class OwnName {
		@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
		private void rusty$own(LivingEntity entity, double distance, CallbackInfoReturnable<Boolean> cir) {
			Visuals.Nametags tags = Modules.get(Visuals.Nametags.class);
			if (tags != null && tags.enabled() && tags.own.on() && entity == Minecraft.getInstance().player && !Compat.hudHidden()) {
				cir.setReturnValue(true);
			}
		}
	}

	@Mixin(Options.class)
	public abstract static class NameBackground {
		@Inject(method = "getBackgroundOpacity", at = @At("RETURN"), cancellable = true)
		private void rusty$opacity(float fallback, CallbackInfoReturnable<Float> cir) {
			if (fallback != 0.25f) {
				return;
			}
			Visuals.Nametags tags = Modules.get(Visuals.Nametags.class);
			if (tags != null && tags.enabled()) {
				cir.setReturnValue(tags.backgroundOpacity(cir.getReturnValue()));
			}
		}
	}

	@Mixin(ItemEntityRenderer.class)
	public abstract static class Physics {
		@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemEntityRenderer;submitMultipleFromCount(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/ItemClusterRenderState;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/phys/AABB;)V"))
		private void rusty$flat(ItemEntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
			Visuals.ItemPhysics physics = Modules.get(Visuals.ItemPhysics.class);
			if (physics == null || !physics.enabled()) {
				return;
			}
			float spin = ItemEntity.getSpin(state.ageInTicks, state.bobOffset);
			float bob = Mth.sin(state.ageInTicks / 10.0F + state.bobOffset) * 0.1F + 0.1F;
			Compat.rotate(pose, Axis.YP.rotation(-spin));
			pose.translate(0f, -bob, 0f);
			AABB box = state.item.getModelBoundingBox();
			boolean block = box.getYsize() > 0.3;
			if (block && !physics.flatBlocks.on()) {
				Compat.rotate(pose, Axis.YP.rotationDegrees((state.seed & 0xFFFF) % 360));
				return;
			}
			float lift = (float) -box.minY + 0.0625F;
			pose.translate(0f, -lift + (float) box.getZsize() / 2f + 0.002f, 0f);
			Compat.rotate(pose, Axis.XP.rotationDegrees(90f));
			Compat.rotate(pose, Axis.ZP.rotationDegrees((state.seed & 0xFFFF) % 360));
		}
	}
}
