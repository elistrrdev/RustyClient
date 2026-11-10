package com.rustyclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rustyclient.core.Modules;
import com.rustyclient.core.RustyUsers;
import com.rustyclient.module.visual.HealthIndicator;
import com.rustyclient.module.visual.ItemPhysics;
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
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
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
		@Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V", at = @At("HEAD"))
		private void rusty$liftStart(EntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, int offset, CallbackInfo ci) {
			HealthIndicator.beginName(state, pose);
		}

		@Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V", at = @At("RETURN"))
		private void rusty$liftEnd(EntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, int offset, CallbackInfo ci) {
			HealthIndicator.endName(pose);
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
			ItemPhysics.apply(state, pose);
		}

		@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V", at = @At("RETURN"))
		private void rusty$motion(ItemEntity entity, ItemEntityRenderState state, float partial, CallbackInfo ci) {
			ItemPhysics.capture(entity, state);
		}
	}

	@Mixin(ItemEntityRenderState.class)
	public abstract static class PhysicsState implements ItemPhysics.Holder {
		@Unique
		private int rusty$id;
		@Unique
		private boolean rusty$ground;
		@Unique
		private boolean rusty$water;
		@Unique
		private float rusty$speed;

		@Override
		public void rusty$motion(int id, boolean ground, boolean water, float speed) {
			rusty$id = id;
			rusty$ground = ground;
			rusty$water = water;
			rusty$speed = speed;
		}

		@Override
		public int rusty$id() {
			return rusty$id;
		}

		@Override
		public boolean rusty$ground() {
			return rusty$ground;
		}

		@Override
		public boolean rusty$water() {
			return rusty$water;
		}

		@Override
		public float rusty$speed() {
			return rusty$speed;
		}
	}

	@Mixin(EntityRenderer.class)
	public abstract static class NameIcon {
		@Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true)
		private void rusty$icon(Entity entity, CallbackInfoReturnable<Component> cir) {
			Component name = cir.getReturnValue();
			if (name != null && entity instanceof Player && RustyUsers.nametagIcon() && RustyUsers.uses(entity.getUUID())) {
				cir.setReturnValue(RustyUsers.prefixed(name));
			}
		}
	}
}
