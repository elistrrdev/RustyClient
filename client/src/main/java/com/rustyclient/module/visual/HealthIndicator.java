package com.rustyclient.module.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rustyclient.core.Category;
import com.rustyclient.core.Module;
import com.rustyclient.core.Modules;
import com.rustyclient.core.setting.BoolSetting;
import com.rustyclient.core.setting.ChoiceSetting;
import com.rustyclient.core.setting.NumberSetting;
import com.rustyclient.platform.Compat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class HealthIndicator extends Module {
	public interface Holder {
		float[] rusty$hearts();

		void rusty$hearts(float[] hearts);
	}

	private static final int NORMAL = 0;
	private static final int POISONED = 1;
	private static final int WITHERED = 2;
	private static final int FROZEN = 3;
	private static final float LINE = 9f * 1.15f;

	private final BoolSetting players = bool("Show on players", GENERAL, true);
	private final BoolSetting mobs = bool("Show on other mobs", GENERAL, false);
	private final BoolSetting self = bool("Show on yourself", GENERAL, false);
	private final BoolSetting stacking = bool("Heart stacking", GENERAL, true);
	private final BoolSetting absorption = bool("Show absorption", GENERAL, true);
	private final BoolSetting empty = bool("Show empty hearts", GENERAL, true);
	private final BoolSetting effects = bool("Status effect colors", GENERAL, true);
	private final BoolSetting hideFull = bool("Hide at full health", GENERAL, false);
	private final ChoiceSetting position = choice("Position", APPEARANCE, "Below name", "Below name", "Above name");
	private final ChoiceSetting style = choice("Heart style", APPEARANCE, "Follow world", "Follow world", "Normal", "Hardcore");
	private final NumberSetting offset = number("Heart offset", APPEARANCE, 0, -30, 60, 1, NumberSetting.integer(" px"));
	private final NumberSetting scale = number("Heart scale", APPEARANCE, 1, 0.5, 2, 0.05, NumberSetting.times());
	private final NumberSetting perRow = number("Hearts per row", APPEARANCE, 10, 5, 20, 1, NumberSetting.integer(""));
	private final NumberSetting distance = number("Max distance", APPEARANCE, 48, 4, 128, 1, NumberSetting.integer(" blocks"));

	public HealthIndicator() {
		super("Health Indicator", "heart", Category.VISUAL, false);
		perRow.visibleWhen(stacking::on);
	}

	private static HealthIndicator active() {
		HealthIndicator h = Modules.get(HealthIndicator.class);
		return h != null && h.enabled() ? h : null;
	}

	public static float[] capture(LivingEntity entity, EntityRenderState state) {
		HealthIndicator h = active();
		if (h == null || entity == null) {
			return null;
		}
		Minecraft mc = Minecraft.getInstance();
		boolean isPlayer = entity instanceof Player;
		if (entity == mc.player) {
			if (!h.self.on()) {
				return null;
			}
		} else if (isPlayer ? !h.players.on() : !h.mobs.on()) {
			return null;
		}
		if (mc.player != null && entity != mc.player && entity.isInvisibleTo(mc.player)) {
			return null;
		}
		double max = h.distance.v();
		if (state.distanceToCameraSq > max * max) {
			return null;
		}
		float health = entity.getHealth();
		float maxHealth = entity.getMaxHealth();
		float extra = h.absorption.on() ? entity.getAbsorptionAmount() : 0f;
		if (h.hideFull.on() && health >= maxHealth && extra <= 0f) {
			return null;
		}
		int type = NORMAL;
		if (h.effects.on()) {
			if (entity.hasEffect(MobEffects.WITHER)) {
				type = WITHERED;
			} else if (entity.hasEffect(MobEffects.POISON)) {
				type = POISONED;
			} else if (entity.isFullyFrozen()) {
				type = FROZEN;
			}
		}
		boolean hardcore = switch (h.style.get()) {
			case "Hardcore" -> true;
			case "Normal" -> false;
			default -> mc.level != null && mc.level.getLevelData().isHardcore();
		};
		return new float[]{health, maxHealth, extra, type, hardcore ? 1 : 0};
	}

	private static String prefix(int type) {
		return switch (type) {
			case POISONED -> "poisoned_";
			case WITHERED -> "withered_";
			case FROZEN -> "frozen_";
			default -> "";
		};
	}

	public static void submit(EntityRenderState state, float[] data, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		HealthIndicator h = active();
		Vec3 attachment = state.nameTagAttachment;
		if (h == null || data == null || attachment == null || camera == null) {
			return;
		}
		TextureAtlas atlas;
		try {
			atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.GUI);
		} catch (Exception e) {
			return;
		}
		int healthRed = (int) Math.ceil(data[0]);
		int maxHealth = (int) Math.ceil(data[1]);
		int healthYellow = (int) Math.ceil(data[2]);
		int type = (int) data[3];
		boolean hardcore = data[4] > 0;
		int heartsRed = (int) Math.ceil(healthRed / 2f);
		boolean lastRedHalf = (healthRed & 1) == 1;
		int heartsNormal = (int) Math.ceil(maxHealth / 2f);
		int heartsYellow = (int) Math.ceil(healthYellow / 2f);
		boolean lastYellowHalf = (healthYellow & 1) == 1;
		int total = heartsNormal + heartsYellow;
		if (total <= 0) {
			return;
		}
		int row = h.stacking.on() ? h.perRow.i() : total;
		int rows = (total + row - 1) / row;
		int rowOffset = Math.max(10 - (rows - 2), 3);
		int pixels = Math.min(total, row) * 8 + 1;
		float startX = -pixels / 2f;
		boolean label = state.nameTag != null && state.distanceToCameraSq <= 4096.0;
		float bottom;
		if (!label) {
			bottom = 8f;
		} else if (h.position.is("Above name")) {
			bottom = -2f;
			if (state.scoreText != null && state.distanceToCameraSq < 100.0) {
				bottom -= LINE;
			}
		} else {
			bottom = 19f;
		}
		bottom -= h.offset.f();
		String hc = hardcore ? "hardcore_" : "";
		String fill = prefix(type);
		TextureAtlasSprite container = atlas.getSprite(Identifier.withDefaultNamespace(hardcore ? "hud/heart/container_hardcore" : "hud/heart/container"));
		TextureAtlasSprite redFull = atlas.getSprite(Identifier.withDefaultNamespace("hud/heart/" + fill + hc + "full"));
		TextureAtlasSprite redHalf = atlas.getSprite(Identifier.withDefaultNamespace("hud/heart/" + fill + hc + "half"));
		TextureAtlasSprite yellowFull = atlas.getSprite(Identifier.withDefaultNamespace("hud/heart/absorbing_" + hc + "full"));
		TextureAtlasSprite yellowHalf = atlas.getSprite(Identifier.withDefaultNamespace("hud/heart/absorbing_" + hc + "half"));
		boolean showEmpty = h.empty.on();
		float k = 0.025f * h.scale.f();
		pose.pushPose();
		pose.translate(attachment.x, attachment.y + 0.5, attachment.z);
		Compat.rotate(pose, camera.orientation);
		pose.scale(k, -k, k);
		int dir = label && h.position.is("Below name") ? 1 : -1;
		float base = dir > 0 ? (10f - h.offset.f()) / h.scale.f() + 9f : bottom / h.scale.f();
		Identifier location = container.atlasLocation();
		collector.submitCustomGeometry(pose, RenderTypes.text(location), (p, buffer) -> {
			for (int heart = 0; heart < total; heart++) {
				TextureAtlasSprite fillSprite = fill(heart, heartsRed, lastRedHalf, heartsNormal, total, lastYellowHalf, redFull, redHalf, yellowFull, yellowHalf);
				if (showEmpty || fillSprite != null) {
					quad(p, buffer, startX + (heart % row) * 8, base + dir * (heart / row) * rowOffset, container);
				}
			}
		});
		collector.submitCustomGeometry(pose, RenderTypes.textPolygonOffset(location), (p, buffer) -> {
			for (int heart = 0; heart < total; heart++) {
				TextureAtlasSprite fillSprite = fill(heart, heartsRed, lastRedHalf, heartsNormal, total, lastYellowHalf, redFull, redHalf, yellowFull, yellowHalf);
				if (fillSprite != null) {
					quad(p, buffer, startX + (heart % row) * 8, base + dir * (heart / row) * rowOffset, fillSprite);
				}
			}
		});
		pose.popPose();
	}

	private static TextureAtlasSprite fill(int heart, int heartsRed, boolean lastRedHalf, int heartsNormal, int total, boolean lastYellowHalf, TextureAtlasSprite redFull, TextureAtlasSprite redHalf, TextureAtlasSprite yellowFull, TextureAtlasSprite yellowHalf) {
		if (heart < heartsRed) {
			return heart == heartsRed - 1 && lastRedHalf ? redHalf : redFull;
		}
		if (heart >= heartsNormal) {
			return heart == total - 1 && lastYellowHalf ? yellowHalf : yellowFull;
		}
		return null;
	}

	private static void quad(PoseStack.Pose p, VertexConsumer b, float x, float bottom, TextureAtlasSprite s) {
		float z = 0f;
		int light = 0xF000F0;
		float top = bottom - 9f;
		b.addVertex(p, x, top, z).setColor(-1).setUv(s.getU0(), s.getV0()).setLight(light);
		b.addVertex(p, x, bottom, z).setColor(-1).setUv(s.getU0(), s.getV1()).setLight(light);
		b.addVertex(p, x + 9f, bottom, z).setColor(-1).setUv(s.getU1(), s.getV1()).setLight(light);
		b.addVertex(p, x + 9f, top, z).setColor(-1).setUv(s.getU1(), s.getV0()).setLight(light);
	}
}
