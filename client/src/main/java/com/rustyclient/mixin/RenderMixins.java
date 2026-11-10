package com.rustyclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rustyclient.core.Modules;
import com.rustyclient.module.visual.HealthIndicator;
import com.rustyclient.module.visual.Visuals;
import com.rustyclient.platform.Compat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.OptionsRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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

	@Mixin(OptionsRenderState.class)
	public abstract static class NameBackground {
		@Inject(method = "getBackgroundOpacity", at = @At("RETURN"), cancellable = true)
		private void rusty$opacity(float fallback, CallbackInfoReturnable<Float> cir) {
			cir.setReturnValue(Visuals.Nametags.opacity(cir.getReturnValue()));
		}
	}

	@Mixin(LivingEntityRenderer.class)
	public abstract static class NameHealth {
		@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("RETURN"))
		private void rusty$health(LivingEntity entity, LivingEntityRenderState state, float partial, CallbackInfo ci) {
			((HealthIndicator.Holder) state).rusty$hearts(HealthIndicator.capture(entity, state));
		}
	}

	@Mixin(LivingEntityRenderState.class)
	public abstract static class HealthState implements HealthIndicator.Holder {
		@Unique
		private float[] rusty$data;

		@Override
		public float[] rusty$hearts() {
			return rusty$data;
		}

		@Override
		public void rusty$hearts(float[] hearts) {
			rusty$data = hearts;
		}
	}

	@Mixin(EntityRenderer.class)
	public abstract static class HealthName {
		@Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"))
		private void rusty$liftStart(EntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
			HealthIndicator.beginName(state);
		}

		@Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("RETURN"))
		private void rusty$liftEnd(EntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
			HealthIndicator.endName();
		}
	}

	@Mixin(EntityRenderer.class)
	public abstract static class HealthSubmit {
		@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("RETURN"))
		private void rusty$hearts(EntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
			if (state instanceof HealthIndicator.Holder holder && holder.rusty$hearts() != null) {
				HealthIndicator.submit(state, holder.rusty$hearts(), pose, collector, camera);
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
