package com.rustyclient.module;

import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import com.rustyclient.module.hud.Saturation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rustyclient.RustyClient;
import com.rustyclient.core.Combat;
import com.rustyclient.core.Modules;
import com.rustyclient.gui.HudEditorScreen;
import com.rustyclient.module.combat.CombatHuds;
import com.rustyclient.module.hud.InfoHuds;
import com.rustyclient.module.hud.Keystrokes;
import com.rustyclient.module.hud.SimpleHuds;
import com.rustyclient.module.hud.VanillaHuds;
import com.rustyclient.module.utility.Utilities;
import com.rustyclient.module.utility.Waypoints;
import com.rustyclient.module.visual.HealthIndicator;
import com.rustyclient.module.visual.Visuals;
import com.rustyclient.platform.Compat;
import com.rustyclient.render.Draw;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class ModuleList {
	private ModuleList() {
	}

	public static void registerAll() {
		Modules.register(new ClientSettings());
		Modules.register(new Keystrokes());
		Modules.register(new SimpleHuds.Cps());
		Modules.register(new SimpleHuds.Fps());
		Modules.register(new InfoHuds.Coordinates());
		Modules.register(new InfoHuds.ArmorStatus());
		Modules.register(new InfoHuds.PotionHud());
		Modules.register(new Visuals.Crosshair());
		Modules.register(new Visuals.BlockOverlay());
		Modules.register(new Visuals.Zoom());
		Modules.register(new Visuals.MotionBlur());
		Modules.register(new Visuals.Nametags());
		Modules.register(new HealthIndicator());
		Modules.register(new Visuals.Fullbright());
		Modules.register(new Utilities.ToggleSprint());
		Modules.register(new SimpleHuds.ServerInfo());
		Modules.register(new SimpleHuds.PingDisplay());
		Modules.register(new InfoHuds.Direction());
		Modules.register(new InfoHuds.ItemCounter());
		Modules.register(new SimpleHuds.Clock());
		Modules.register(new SimpleHuds.Speed());
		Modules.register(new SimpleHuds.Memory());
		Modules.register(new CombatHuds.ComboCounter());
		Modules.register(new CombatHuds.ReachDisplay());
		Modules.register(new InfoHuds.PackDisplay());
		Modules.register(new VanillaHuds.ScoreboardHud());
		Modules.register(new VanillaHuds.BossBar());
		Modules.register(new Saturation());
		Modules.register(new Visuals.Freelook());
		Modules.register(new Visuals.HitColor());
		Modules.register(new Visuals.DamageTint());
		Modules.register(new Visuals.ItemPhysics());
		Modules.register(new Visuals.Particles());
		Modules.register(new Visuals.CustomHand());
		Modules.register(new Visuals.TimeWeather());
		Modules.register(new Visuals.ClearFluids());
		Modules.register(new Visuals.ScreenOverlays());
		Modules.register(new Visuals.Fadeless());
		Modules.register(new Visuals.LoadingScreen());
		Modules.register(new Utilities.AutoGG());
		Modules.register(new Utilities.ScreenshotHelper());
		Modules.register(new Utilities.Chat());
		Modules.register(new Waypoints());
		Modules.register(new Utilities.HotbarLock());
		Modules.register(new Utilities.MenuBlur());
		Modules.register(new CombatHuds.HitDelay());
		Modules.register(new CombatHuds.TotemPops());
	}

	public static void registerHudHooks() {
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, old -> (g, delta) -> {
			Visuals.Crosshair crosshair = Modules.get(Visuals.Crosshair.class);
			if (crosshair == null || !crosshair.enabled()) {
				old.extractRenderState(g, delta);
				return;
			}
			if (!crosshair.shouldDraw() || Compat.debugOpen()) {
				return;
			}
			Draw d = new Draw(g, Draw.guiScale());
			try {
				float cx = Draw.windowWidth() / 2f / d.s;
				float cy = Draw.windowHeight() / 2f / d.s;
				crosshair.draw(d, cx, cy);
			} finally {
				d.end();
			}
			crosshair.drawAttackIndicator(g);
		});
		HudElementRegistry.attachElementBefore(VanillaHudElements.FOOD_BAR, Identifier.fromNamespaceAndPath("rustyclient", "exhaustion"), (g, delta) -> {
			Saturation saturation = Saturation.active();
			if (saturation != null) {
				saturation.drawUnderlay(g);
			}
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.FOOD_BAR, Identifier.fromNamespaceAndPath("rustyclient", "saturation"), (g, delta) -> {
			Saturation saturation = Saturation.active();
			if (saturation != null) {
				saturation.drawFood(g);
			}
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.HEALTH_BAR, Identifier.fromNamespaceAndPath("rustyclient", "health_preview"), (g, delta) -> {
			Saturation saturation = Saturation.active();
			if (saturation != null) {
				saturation.drawHealth(g);
			}
		});
		DebugScreenEntries.register(Saturation.DEBUG_ENTRY, (displayer, level, clientChunk, serverChunk) -> {
			Saturation saturation = Saturation.active();
			if (saturation != null) {
				saturation.debugLines(displayer);
			}
		});
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			Saturation saturation = Modules.get(Saturation.class);
			if (saturation != null) {
				saturation.tooltip(stack, lines);
			}
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Identifier.fromNamespaceAndPath("rustyclient", "hand_durability"), (g, delta) -> {
			InfoHuds.ArmorStatus armor = Modules.get(InfoHuds.ArmorStatus.class);
			if (armor != null) {
				armor.drawHands(g);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.SCOREBOARD, old -> (g, delta) -> {
			if (!Modules.on(VanillaHuds.ScoreboardHud.class)) {
				old.extractRenderState(g, delta);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.MOB_EFFECTS, old -> (g, delta) -> {
			InfoHuds.PotionHud potions = Modules.get(InfoHuds.PotionHud.class);
			if (potions == null || !potions.hidesVanilla()) {
				old.extractRenderState(g, delta);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.BOSS_BAR, old -> (g, delta) -> {
			if (!Modules.on(VanillaHuds.BossBar.class)) {
				old.extractRenderState(g, delta);
			}
		});

		ClientTickEvents.START_CLIENT_TICK.register(mc -> {
			Utilities.HotbarLock lock = Modules.get(Utilities.HotbarLock.class);
			if (lock != null && lock.enabled()) {
				lock.beforeInput();
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> Combat.tick());

		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() && player == Minecraft.getInstance().player) {
				try {
					Combat.onAttack(entity);
					Saturation saturation = Saturation.active();
					if (saturation != null) {
						saturation.onAttack();
					}
					Visuals.Particles particles = Modules.get(Visuals.Particles.class);
					if (particles != null && particles.enabled()) {
						particles.onAttack(entity);
					}
				} catch (Throwable t) {
					RustyClient.LOG.error("attack hook failed", t);
				}
			}
			return InteractionResult.PASS;
		});

		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				Utilities.AutoGG gg = Modules.get(Utilities.AutoGG.class);
				if (gg != null) {
					gg.onMessage(message);
				}
			}
		});

		LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
			Waypoints waypoints = Modules.get(Waypoints.class);
			if (waypoints == null || !waypoints.enabled()) {
				return;
			}
			try {
				waypoints.submitBeams(context.poseStack(), context.submitNodeCollector());
			} catch (Throwable t) {
				RustyClient.LOG.error("waypoint beams failed", t);
			}
		});

		LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, outline) -> {
			Visuals.BlockOverlay overlay = Modules.get(Visuals.BlockOverlay.class);
			if (overlay == null || !overlay.enabled()) {
				return true;
			}
			try {
				drawOutline(context.poseStack(), context.submitNodeCollector(), outline, overlay);
			} catch (Throwable t) {
				RustyClient.LOG.error("block overlay failed", t);
				return true;
			}
			return false;
		});
	}

	private static void drawOutline(PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector collector, BlockOutlineRenderState outline, Visuals.BlockOverlay overlay) {
		Vec3 cam = Compat.camera().position();
		BlockPos pos = outline.pos();
		VoxelShape shape = outline.shape();
		double ox = pos.getX() - cam.x, oy = pos.getY() - cam.y, oz = pos.getZ() - cam.z;
		if (overlay.fill.on()) {
			int fill = overlay.fill();
			collector.submitCustomGeometry(pose, RenderTypes.debugQuads(), (p, buffer) -> {
				for (AABB box : shape.toAabbs()) {
					AABB b = box.inflate(0.002).move(ox, oy, oz);
					quads(p, buffer, b, fill);
				}
			});
		}
		if (overlay.outline.on()) {
			int color = overlay.outline();
			float width = overlay.width.f();
			collector.submitCustomGeometry(pose, RenderTypes.linesTranslucent(), (p, buffer) -> shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
				float dx = (float) (x2 - x1), dy = (float) (y2 - y1), dz = (float) (z2 - z1);
				float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
				if (len < 1e-6f) {
					return;
				}
				dx /= len;
				dy /= len;
				dz /= len;
				buffer.addVertex(p, (float) (x1 + ox), (float) (y1 + oy), (float) (z1 + oz)).setColor(color).setNormal(p, dx, dy, dz).setLineWidth(width);
				buffer.addVertex(p, (float) (x2 + ox), (float) (y2 + oy), (float) (z2 + oz)).setColor(color).setNormal(p, dx, dy, dz).setLineWidth(width);
			}));
		}
	}

	private static void quads(PoseStack.Pose p, VertexConsumer b, AABB box, int c) {
		float x0 = (float) box.minX, y0 = (float) box.minY, z0 = (float) box.minZ;
		float x1 = (float) box.maxX, y1 = (float) box.maxY, z1 = (float) box.maxZ;
		v(p, b, x0, y0, z0, c); v(p, b, x1, y0, z0, c); v(p, b, x1, y0, z1, c); v(p, b, x0, y0, z1, c);
		v(p, b, x0, y1, z0, c); v(p, b, x0, y1, z1, c); v(p, b, x1, y1, z1, c); v(p, b, x1, y1, z0, c);
		v(p, b, x0, y0, z0, c); v(p, b, x0, y1, z0, c); v(p, b, x1, y1, z0, c); v(p, b, x1, y0, z0, c);
		v(p, b, x0, y0, z1, c); v(p, b, x1, y0, z1, c); v(p, b, x1, y1, z1, c); v(p, b, x0, y1, z1, c);
		v(p, b, x0, y0, z0, c); v(p, b, x0, y0, z1, c); v(p, b, x0, y1, z1, c); v(p, b, x0, y1, z0, c);
		v(p, b, x1, y0, z0, c); v(p, b, x1, y1, z0, c); v(p, b, x1, y1, z1, c); v(p, b, x1, y0, z1, c);
	}

	private static void v(PoseStack.Pose p, VertexConsumer b, float x, float y, float z, int c) {
		b.addVertex(p, x, y, z).setColor(c);
	}

	public static void renderOverlays(GuiGraphicsExtractor g) {
		Visuals.DamageTint tint = Modules.get(Visuals.DamageTint.class);
		Waypoints waypoints = Modules.get(Waypoints.class);
		boolean tintOn = tint != null && tint.enabled();
		boolean wpOn = waypoints != null && waypoints.enabled();
		if (!tintOn && !wpOn) {
			return;
		}
		Draw d = new Draw(g, Draw.guiScale());
		try {
			int w = Draw.windowWidth(), h = Draw.windowHeight();
			if (tintOn) {
				Draw full = new Draw(g, 1f);
				try {
					tint.draw(full, w, h);
				} finally {
					full.end();
				}
			}
			if (wpOn) {
				waypoints.draw(d, w, h);
			}
		} finally {
			d.end();
		}
	}
}
