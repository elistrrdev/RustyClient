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

		@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
		private void rusty$itemScale(PlayerRenderState player, FirstPersonHandsAndItemsRenderState state, float partial, float pitch, InteractionHand hand, float swing, ItemStack stack, float equip, PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
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

	@Mixin(net.minecraft.client.renderer.SubmitNodeCollection.class)
	public abstract static class NameShadow {
		@org.spongepowered.asm.mixin.injection.ModifyArg(method = "nameTag", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/TextFeatureRenderer$Content$Text;<init>(FFLnet/minecraft/util/FormattedCharSequence;ZIII)V"), index = 3)
		private static boolean rusty$shadow(boolean shadow) {
			return Visuals.Nametags.shadow(shadow);
		}
	}

	@Mixin(net.minecraft.client.renderer.entity.EntityRenderDispatcher.class)
	public abstract static class EntityDistance {
		@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
		private void rusty$distance(net.minecraft.world.entity.Entity entity, net.minecraft.client.renderer.culling.Frustum culler, double camX, double camY, double camZ, float partial, CallbackInfoReturnable<Boolean> cir) {
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
}
