package com.rustyclient.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rustyclient.core.setting.KeySetting;
import com.rustyclient.core.setting.Setting;
import com.rustyclient.module.ClientSettings;
import com.rustyclient.module.Performance;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;

public final class Config {
	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
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
		Profiles.noteChange();
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
				JsonObject obj = modules.getAsJsonObject(module.savedId(modules));
				if (obj == null) {
					continue;
				}
				if (obj.has("enabled") && obj.get("enabled").isJsonPrimitive()) {
					module.loadEnabled(obj.get("enabled").getAsBoolean());
				}
				if (obj.has("favorite") && obj.get("favorite").isJsonPrimitive()) {
					module.loadFavorite(obj.get("favorite").getAsBoolean());
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

	public static Profiles.Part partOf(Module module, Setting<?> setting) {
		if (setting instanceof KeySetting) {
			return Profiles.Part.KEYBINDS;
		}
		if (module instanceof ClientSettings) {
			return Profiles.Part.CLIENT;
		}
		if (module instanceof Performance) {
			return Profiles.Part.PERFORMANCE;
		}
		if (module instanceof HudModule hud && setting == hud.scale) {
			return Profiles.Part.HUD;
		}
		return Profiles.Part.SETTINGS;
	}

	public static void apply(JsonObject root, Set<Profiles.Part> parts) {
		JsonObject modules = root != null && root.has("modules") && root.get("modules").isJsonObject() ? root.getAsJsonObject("modules") : new JsonObject();
		loading = true;
		try {
			for (Module module : Modules.all()) {
				JsonElement raw = modules.get(module.savedId(modules));
				JsonObject obj = raw != null && raw.isJsonObject() ? raw.getAsJsonObject() : null;
				JsonObject settings = obj != null && obj.has("settings") && obj.get("settings").isJsonObject() ? obj.getAsJsonObject("settings") : null;
				boolean core = module instanceof ClientSettings || module instanceof Performance;
				for (Setting<?> setting : module.settings) {
					if (!parts.contains(partOf(module, setting))) {
						continue;
					}
					try {
						setting.applyJson(settings == null ? null : settings.get(setting.id));
					} catch (Exception ignored) {
					}
				}
				if (module instanceof HudModule hud && parts.contains(Profiles.Part.HUD)) {
					Double x = obj != null && obj.has("x") ? number(obj.get("x")) : null;
					Double y = obj != null && obj.has("y") ? number(obj.get("y")) : null;
					hud.applyPosition(x, y);
				}
				if (!core && parts.contains(Profiles.Part.FAVORITES)) {
					module.loadFavorite(obj != null && obj.has("favorite") && bool(obj.get("favorite")));
				}
				if (!core && parts.contains(Profiles.Part.MODULES)) {
					boolean on = obj != null && obj.has("enabled") ? bool(obj.get("enabled")) : module.defaultEnabled();
					try {
						module.applyEnabled(on);
					} catch (Exception ignored) {
					}
				}
			}
		} finally {
			loading = false;
		}
	}

	private static Double number(JsonElement element) {
		try {
			return element.getAsDouble();
		} catch (Exception e) {
			return null;
		}
	}

	private static boolean bool(JsonElement element) {
		try {
			return element.getAsBoolean();
		} catch (Exception e) {
			return false;
		}
	}

	public static JsonObject snapshot() {
		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		JsonObject modules = new JsonObject();
		for (Module module : Modules.all()) {
			JsonObject obj = new JsonObject();
			obj.addProperty("enabled", module.enabled());
			if (module.favorite()) {
				obj.addProperty("favorite", true);
			}
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
		return root;
	}

	public static void save() {
		dirty = false;
		JsonObject root = snapshot();
		write(file(), root);
		Profiles.afterSave(root);
	}

	public static boolean write(Path file, JsonObject root) {
		String text = GSON.toJson(root);
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(tmp, text, StandardCharsets.UTF_8);
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			return true;
		} catch (Exception e) {
			try {
				Files.writeString(file, text, StandardCharsets.UTF_8);
				return true;
			} catch (Exception ignored) {
				return false;
			}
		}
	}

	public static JsonObject read(Path file) {
		try {
			JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
			return root.isJsonObject() ? root.getAsJsonObject() : null;
		} catch (Exception e) {
			return null;
		}
	}

	private static double clamp(double v) {
		return Double.isNaN(v) ? 0 : Math.max(0, Math.min(1, v));
	}
}
