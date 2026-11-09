package com.rustyclient;

import com.rustyclient.core.Clicks;
import com.rustyclient.core.Config;
import com.rustyclient.core.HudModule;
import com.rustyclient.core.Module;
import com.rustyclient.core.Modules;
import com.rustyclient.core.Theme;
import com.rustyclient.gui.HudEditorScreen;
import com.rustyclient.gui.RustyScreen;
import com.rustyclient.module.ClientSettings;
import com.rustyclient.module.ModuleList;
import com.rustyclient.platform.Compat;
import com.rustyclient.render.Draw;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

public final class RustyClient implements ClientModInitializer {
	public static final Logger LOG = LoggerFactory.getLogger("rustyclient");
	public static final long START = System.currentTimeMillis();
	private static boolean menuKeyDown;

	@Override
	public void onInitializeClient() {
		ModuleList.registerAll();
		Config.load();
		Theme.refresh(true);
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("rustyclient", "hud"), (g, delta) -> renderHud(g));
		ModuleList.registerHudHooks();
		ClientTickEvents.END_CLIENT_TICK.register(mc -> tick());
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> Config.save());
		LOG.info("rusty client {} ready with {} modules", version(), Modules.all().size());
	}

	public static String version() {
		return FabricLoader.getInstance().getModContainer("rustyclient").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
	}

	public static void openFolder(Path path) {
		try {
			Files.createDirectories(path);
			new ProcessBuilder("explorer.exe", path.toAbsolutePath().toString()).start();
		} catch (Exception e) {
			LOG.warn("could not open {}", path, e);
		}
	}

	private static void tick() {
		Minecraft mc = Minecraft.getInstance();
		pollKeys();
		for (Module module : Modules.all()) {
			try {
				module.alwaysTick();
				if (module.enabled() && mc.player != null) {
					module.tick();
				}
			} catch (Throwable t) {
				LOG.error("module {} failed to tick", module.id, t);
			}
		}
		Config.tick();
	}

	public static void pollKeys() {
		Minecraft mc = Minecraft.getInstance();
		Clicks.poll();
		if (Compat.screen() != null) {
			menuKeyDown = true;
			for (Module module : Modules.all()) {
				module.holdKey();
			}
			return;
		}
		ClientSettings settings = ClientSettings.get();
		boolean down = settings != null && Compat.isKeyDown(settings.menuKey.get());
		if (down && !menuKeyDown && mc.player != null) {
			RustyScreen.open();
		}
		menuKeyDown = down;
		for (Module module : Modules.all()) {
			if (module.toggleKey.bound()) {
				module.pollKey(Compat.isKeyDown(module.toggleKey.get()));
			}
		}
	}

	public static void drawHudModules(GuiGraphicsExtractor g, boolean editing) {
		for (HudModule hud : Modules.hud()) {
			if (!hud.enabled() || (!editing && !hud.visibleNow())) {
				continue;
			}
			Draw d = new Draw(g, hud.unitPx());
			try {
				d.hudText(hud.vanillaFont());
				d.origin(Math.round(hud.pixelX()), Math.round(hud.pixelY()));
				hud.render(d, editing);
			} catch (Throwable t) {
				LOG.error("hud module {} failed to render", hud.id, t);
			} finally {
				d.end();
			}
		}
	}

	public static HudModule hudAt(float pixelX, float pixelY) {
		for (int i = Modules.hud().size() - 1; i >= 0; i--) {
			HudModule hud = Modules.hud().get(i);
			if (!hud.enabled() || !hud.visibleNow()) {
				continue;
			}
			float x = hud.pixelX(), y = hud.pixelY();
			if (pixelX >= x && pixelY >= y && pixelX < x + hud.pixelWidth() && pixelY < y + hud.pixelHeight()) {
				return hud;
			}
		}
		return null;
	}

	private static void renderHud(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		pollKeys();
		ClientSettings settings = ClientSettings.get();
		if (settings != null && settings.hideInF3.on() && Compat.debugOpen()) {
			return;
		}
		if (!(Compat.screen() instanceof RustyScreen)) {
			drawHudModules(g, HudEditorScreen.editing());
		}
		if (mc.player != null) {
			ModuleList.renderOverlays(g);
		}
	}
}
