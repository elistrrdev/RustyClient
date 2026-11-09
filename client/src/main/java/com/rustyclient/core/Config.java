package com.rustyclient.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rustyclient.core.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static boolean dirty;
	private static long dirtySince;
	private static boolean loading;

	private Config() {
	}

	public static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("rustyclient.json");
	}

	public static void markDirty() {
		if (loading) {
			return;
		}
		if (!dirty) {
			dirtySince = System.currentTimeMillis();
		}
		dirty = true;
	}

	public static void tick() {
		if (dirty && System.currentTimeMillis() - dirtySince > 750) {
			save();
		}
	}

	public static void load() {
		Path file = file();
		if (!Files.isRegularFile(file)) {
			return;
		}
		loading = true;
		try {
			JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
			if (!root.isJsonObject()) {
				return;
			}
			JsonObject modules = root.getAsJsonObject().getAsJsonObject("modules");
			if (modules == null) {
				return;
			}
			for (Module module : Modules.all()) {
				JsonObject obj = modules.getAsJsonObject(module.id);
				if (obj == null) {
					continue;
				}
				if (obj.has("enabled") && obj.get("enabled").isJsonPrimitive()) {
					module.loadEnabled(obj.get("enabled").getAsBoolean());
				}
				JsonObject settings = obj.getAsJsonObject("settings");
				if (settings != null) {
					for (Setting<?> setting : module.settings) {
						if (settings.has(setting.id)) {
							setting.load(settings.get(setting.id));
						}
					}
				}
				if (module instanceof HudModule hud && obj.has("x") && obj.has("y")) {
					hud.fx = clamp(obj.get("x").getAsDouble());
					hud.fy = clamp(obj.get("y").getAsDouble());
				}
			}
		} catch (Exception ignored) {
		} finally {
			loading = false;
		}
	}

	public static void save() {
		dirty = false;
		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		JsonObject modules = new JsonObject();
		for (Module module : Modules.all()) {
			JsonObject obj = new JsonObject();
			obj.addProperty("enabled", module.enabled());
			JsonObject settings = new JsonObject();
			for (Setting<?> setting : module.settings) {
				settings.add(setting.id, setting.save());
			}
			obj.add("settings", settings);
			if (module instanceof HudModule hud) {
				obj.addProperty("x", hud.fx);
				obj.addProperty("y", hud.fy);
			}
			modules.add(module.id, obj);
		}
		root.add("modules", modules);
		Path file = file();
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling("rustyclient.json.tmp");
			Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (Exception e) {
			try {
				Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
			} catch (Exception ignored) {
			}
		}
	}

	private static double clamp(double v) {
		return Double.isNaN(v) ? 0 : Math.max(0, Math.min(1, v));
	}
}
