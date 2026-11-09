package com.rustyclient.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rustyclient.module.ClientSettings;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Theme {
	public static final int DEFAULT_ACCENT = 0xFF1AB0DD;
	public static final int FRAME = 0xFF0F0E0D;
	public static final int MAIN = 0xFF131211;
	public static final int SURFACE = 0xFF1A1817;
	public static final int SURFACE_2 = 0xFF211F1E;
	public static final int SURFACE_3 = 0xFF2A2827;
	public static final int LINE = 0xFF1B1A19;
	public static final int BORDER = 0xFF242322;
	public static final int TEXT = 0xFFF2EEEA;
	public static final int TEXT_2 = 0xFFE6E1DC;
	public static final int TEXT_3 = 0xFFC2BEBA;
	public static final int MUTED = 0xFF948C85;
	public static final int FAINT = 0xFF6F6862;
	public static final int INK = 0xFF0D0F10;
	public static final int DANGER = 0xFFEF5B52;
	public static final int SUCCESS = 0xFF57C88A;

	private static int launcherAccent = DEFAULT_ACCENT;
	private static long lastRead;
	private static long lastModified = -1;

	private Theme() {
	}

	public static int accent() {
		ClientSettings settings = ClientSettings.get();
		if (settings != null && !settings.followLauncher.on()) {
			return settings.accent.argb();
		}
		refresh(false);
		return launcherAccent;
	}

	public static int hudAccent(int color) {
		return (color | 0xFF000000) == DEFAULT_ACCENT ? accent() : color;
	}

	public static int accentHover() {
		return mix(accent(), 0xFFFFFFFF, 0.14f);
	}

	public static int accentText() {
		return mix(accent(), 0xFFFFFFFF, 0.28f);
	}

	public static int accentSoft() {
		return withAlpha(accent(), 0x29);
	}

	public static void refresh(boolean force) {
		long now = System.currentTimeMillis();
		if (!force && now - lastRead < 3000) {
			return;
		}
		lastRead = now;
		Path file = launcherSettings();
		if (file == null) {
			return;
		}
		try {
			long modified = Files.getLastModifiedTime(file).toMillis();
			if (modified == lastModified && !force) {
				return;
			}
			lastModified = modified;
			JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
			if (root.isJsonObject()) {
				JsonObject obj = root.getAsJsonObject();
				if (obj.has("accent")) {
					Integer parsed = parseHex(obj.get("accent").getAsString());
					if (parsed != null) {
						launcherAccent = parsed;
					}
				}
			}
		} catch (Exception ignored) {
		}
	}

	public static Path launcherSettings() {
		try {
			Path game = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
			Path instances = game.getParent();
			if (instances != null && instances.getFileName() != null && instances.getFileName().toString().equals("instances")) {
				Path candidate = instances.getParent().resolve("settings.json");
				if (Files.isRegularFile(candidate)) {
					return candidate;
				}
			}
			String appdata = System.getenv("APPDATA");
			if (appdata != null) {
				Path candidate = Path.of(appdata, ".rustyclient", "settings.json");
				if (Files.isRegularFile(candidate)) {
					return candidate;
				}
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	public static Integer parseHex(String text) {
		if (text == null) {
			return null;
		}
		String s = text.trim().replace("#", "");
		if (s.length() == 3) {
			s = "" + s.charAt(0) + s.charAt(0) + s.charAt(1) + s.charAt(1) + s.charAt(2) + s.charAt(2);
		}
		if (s.length() != 6 && s.length() != 8) {
			return null;
		}
		try {
			long v = Long.parseLong(s, 16);
			return s.length() == 6 ? (int) (0xFF000000L | v) : (int) v;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public static int withAlpha(int argb, int alpha) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0xFFFFFF);
	}

	public static int fade(int argb, float factor) {
		int a = Math.round((argb >>> 24) * Math.max(0f, Math.min(1f, factor)));
		return (a << 24) | (argb & 0xFFFFFF);
	}

	public static int mix(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
		int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
		return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16) | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
	}

	public static String hex(int argb) {
		return String.format("#%06X", argb & 0xFFFFFF);
	}
}
