package com.rustyclient.platform;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.SharedConstants;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import org.joml.Quaternionfc;

public final class Compat {
	private Compat() {
	}

	public static Screen screen() {
		return Minecraft.getInstance().gui.screen();
	}

	public static void setScreen(Screen screen) {
		Minecraft.getInstance().gui.setScreen(screen);
	}

	public static boolean isKeyDown(String name) {
		try {
			InputConstants.Key key = InputConstants.getKey(name);
			if (key == InputConstants.UNKNOWN || key.getType() == InputConstants.Type.MOUSE) {
				return false;
			}
			return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key.getValue());
		} catch (Exception e) {
			return false;
		}
	}

	public static boolean debugOpen() {
		return Minecraft.getInstance().gui.hud.getDebugOverlay().showDebugScreen();
	}

	public static String gameVersion() {
		return SharedConstants.getCurrentVersion().name();
	}

	public static void rotate(PoseStack pose, Quaternionfc rotation) {
		pose.mulPose(rotation);
	}

	public static Camera camera() {
		return Minecraft.getInstance().gameRenderer.mainCamera();
	}

	public static ChatComponent chat() {
		return Minecraft.getInstance().gui.hud.getChat();
	}

	public static BossHealthOverlay bossOverlay() {
		return Minecraft.getInstance().gui.hud.getBossOverlay();
	}

	public static boolean hudHidden() {
		return Minecraft.getInstance().gui.hud.isHidden();
	}

	public static Identifier effectSprite(Holder<MobEffect> effect) {
		return net.minecraft.client.gui.Hud.getMobEffectSprite(effect);
	}

	public static net.minecraft.world.scores.DisplaySlot teamSlot(net.minecraft.world.scores.PlayerTeam team) {
		return team.getColor().map(net.minecraft.world.scores.TeamColor::displaySlot).orElse(null);
	}

	public static void textInput(Object owner, boolean on) {
	}

	public static void setOverlay(net.minecraft.client.gui.screens.Overlay overlay) {
		Minecraft.getInstance().gui.setOverlay(overlay);
	}

	public static byte[] packIcon(net.minecraft.server.packs.repository.Pack pack) {
		try (net.minecraft.server.packs.PackResources resources = pack.open()) {
			net.minecraft.server.packs.resources.IoSupplier<java.io.InputStream> supplier = resources.getRootResource("pack.png");
			if (supplier == null) {
				return null;
			}
			try (java.io.InputStream in = supplier.get()) {
				return in.readAllBytes();
			}
		} catch (Exception e) {
			return null;
		}
	}

	public static boolean tabHeads() {
		Minecraft mc = Minecraft.getInstance();
		return mc.getConnection() != null && mc.getConnection().onlineMode();
	}
}
