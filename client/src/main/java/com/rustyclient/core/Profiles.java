package com.rustyclient.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rustyclient.RustyClient;
import com.rustyclient.platform.Compat;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class Profiles {
	public static final String DEFAULT_ID = "default";
	public static final String[] VERSIONS = {"26.1", "26.2", "26.3"};
	public static final String[] ICONS = {"folder-cog", "sword", "shield", "zap", "gauge", "star", "flame", "eye", "map-pin", "camera", "users", "layers", "monitor", "heart", "server", "globe", "keyboard", "sparkles", "target", "clock"};
	public static final int[] COLORS = {0xFF1AB0DD, 0xFF6CCB8C, 0xFFF2C14E, 0xFFF0864E, 0xFFEF5B52, 0xFFF07EA8, 0xFFA98BF0, 0xFFF2EEEA};
	private static final String EXPORT_PREFIX = "rustyclient-profile:";
	private static final String STATE = "state.json";

	public enum Scope {
		VERSION, GLOBAL
	}

	public enum Part {
		MODULES("modules", "Modules on and off"),
		SETTINGS("settings", "Module settings"),
		HUD("hud", "HUD layout and scale"),
		KEYBINDS("keybinds", "Keybinds"),
		FAVORITES("favorites", "Favorite modules"),
		CLIENT("client", "Client settings"),
		PERFORMANCE("performance", "Performance settings");

		public final String key;
		public final String label;

		Part(String key, String label) {
			this.key = key;
			this.label = label;
		}
	}

	public static final class Profile {
		public String id;
		public Scope scope;
		public String name = "Profile";
		public String description = "";
		public String icon = "folder-cog";
		public int color = Theme.DEFAULT_ACCENT;
		public String createdFor = version();
		public long created = System.currentTimeMillis();
		public long updated = System.currentTimeMillis();
		public boolean locked;
		public boolean autoSave = true;
		public String servers = "";
		public boolean singleplayer;
		public boolean switchBack = true;
		public final EnumSet<Part> parts = EnumSet.allOf(Part.class);
		public JsonObject config = new JsonObject();
		public String sourceVersion;

		public String key() {
			return (scope == Scope.GLOBAL ? "global:" : "version:") + id;
		}

		public boolean isDefault() {
			return scope == Scope.VERSION && DEFAULT_ID.equals(id);
		}

		public Path file() {
			return (scope == Scope.GLOBAL ? globalDir() : versionDir()).resolve(id + ".json");
		}

		public int enabledModules() {
			JsonObject modules = config.has("modules") && config.get("modules").isJsonObject() ? config.getAsJsonObject("modules") : null;
			if (modules == null) {
				return 0;
			}
			int count = 0;
			for (Module module : Modules.all()) {
				if (module.hidden()) {
					continue;
				}
				try {
					JsonObject obj = modules.getAsJsonObject(module.savedId(modules));
					if (obj != null && obj.has("enabled") && obj.get("enabled").getAsBoolean()) {
						count++;
					}
				} catch (Exception ignored) {
				}
			}
			return count;
		}

		JsonObject toJson() {
			JsonObject root = new JsonObject();
			root.addProperty("format", 1);
			root.addProperty("name", name);
			root.addProperty("description", description);
			root.addProperty("icon", icon);
			root.addProperty("color", Theme.hex(color));
			root.addProperty("createdFor", createdFor);
			root.addProperty("created", created);
			root.addProperty("updated", updated);
			root.addProperty("locked", locked);
			root.addProperty("autoSave", autoSave);
			root.addProperty("servers", servers);
			root.addProperty("singleplayer", singleplayer);
			root.addProperty("switchBack", switchBack);
			JsonArray included = new JsonArray();
			for (Part part : parts) {
				included.add(part.key);
			}
			root.add("parts", included);
			root.add("config", config);
			return root;
		}

		static Profile fromJson(JsonObject root, String id, Scope scope) {
			Profile p = new Profile();
			p.id = id;
			p.scope = scope;
			p.name = string(root, "name", "Profile");
			p.description = string(root, "description", "");
			p.icon = string(root, "icon", "folder-cog");
			Integer color = Theme.parseHex(string(root, "color", ""));
			p.color = color == null ? Theme.DEFAULT_ACCENT : (color | 0xFF000000);
			p.createdFor = string(root, "createdFor", version());
			p.created = number(root, "created", System.currentTimeMillis());
			p.updated = number(root, "updated", p.created);
			p.locked = flag(root, "locked", false);
			p.autoSave = flag(root, "autoSave", true);
			p.servers = string(root, "servers", "");
			p.singleplayer = flag(root, "singleplayer", false);
			p.switchBack = flag(root, "switchBack", true);
			if (root.has("parts") && root.get("parts").isJsonArray()) {
				p.parts.clear();
				for (JsonElement e : root.getAsJsonArray("parts")) {
					for (Part part : Part.values()) {
						if (part.key.equals(e.getAsString())) {
							p.parts.add(part);
						}
					}
				}
			}
			JsonObject config = root.getAsJsonObject("config");
			p.config = config == null ? new JsonObject() : config;
			return p;
		}

		Profile copy() {
			Profile p = fromJson(toJson().deepCopy(), id, scope);
			p.sourceVersion = sourceVersion;
			return p;
		}
	}

	public record VersionProfiles(String version, List<Profile> profiles) {
	}

	private static final List<Profile> PROFILES = new ArrayList<>();
	private static Profile active;
	private static boolean unsaved;
	private static boolean ready;
	private static String place = "";
	private static Profile beforeAuto;
	private static Profile autoProfile;
	private static int timer;
	private static String message;
	private static long messageUntil;

	private Profiles() {
	}

	public static String version() {
		Matcher m = Pattern.compile("^(\\d+\\.\\d+)").matcher(Compat.gameVersion());
		return m.find() ? m.group(1) : Compat.gameVersion();
	}

	public static String versionLabel(String version) {
		return version.equals("26.1") ? "26.1, 26.1.1 and 26.1.2" : version;
	}

	public static Path versionDir() {
		return FabricLoader.getInstance().getConfigDir().resolve("rustyclient-profiles");
	}

	private static Path root() {
		Path game = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
		Path instances = game.getParent();
		if (instances != null && instances.getFileName() != null && instances.getFileName().toString().equals("instances") && instances.getParent() != null) {
			return instances.getParent();
		}
		return null;
	}

	public static Path globalDir() {
		Path root = root();
		return root == null ? versionDir().resolve("global") : root.resolve("profiles");
	}

	public static void init() {
		PROFILES.clear();
		load(versionDir(), Scope.VERSION, PROFILES);
		load(globalDir(), Scope.GLOBAL, PROFILES);
		Profile def = find(Scope.VERSION, DEFAULT_ID);
		if (def == null) {
			def = new Profile();
			def.id = DEFAULT_ID;
			def.scope = Scope.VERSION;
			def.name = version();
			def.description = "Default profile for " + versionLabel(version());
			def.config = Config.snapshot();
			write(def);
			PROFILES.add(0, def);
		}
		JsonObject state = Config.read(versionDir().resolve(STATE));
		String key = state == null ? null : string(state, "active", null);
		active = key == null ? null : byKey(key);
		if (active == null) {
			active = def;
		}
		ready = true;
		if (active.scope == Scope.GLOBAL) {
			Config.apply(active.config, active.parts);
			Config.save();
		}
		writeState();
		sort();
	}

	private static void load(Path dir, Scope scope, List<Profile> into) {
		if (!Files.isDirectory(dir)) {
			return;
		}
		try (Stream<Path> files = Files.list(dir)) {
			for (Path file : files.toList()) {
				String name = file.getFileName().toString();
				if (!name.endsWith(".json") || name.equals(STATE)) {
					continue;
				}
				try {
					JsonObject root = Config.read(file);
					if (root == null || !root.has("config") || !root.get("config").isJsonObject()) {
						continue;
					}
					into.add(Profile.fromJson(root, name.substring(0, name.length() - 5), scope));
				} catch (Exception e) {
					RustyClient.LOG.warn("skipped broken profile {}", file, e);
				}
			}
		} catch (Exception e) {
			RustyClient.LOG.warn("could not read profiles in {}", dir, e);
		}
	}

	private static void sort() {
		PROFILES.sort((a, b) -> {
			if (a.isDefault() != b.isDefault()) {
				return a.isDefault() ? -1 : 1;
			}
			if (a.scope != b.scope) {
				return a.scope == Scope.VERSION ? -1 : 1;
			}
			return Long.compare(a.created, b.created);
		});
	}

	public static List<Profile> all() {
		return PROFILES;
	}

	public static Profile active() {
		return active;
	}

	public static boolean unsaved() {
		return unsaved;
	}

	public static Profile find(Scope scope, String id) {
		for (Profile p : PROFILES) {
			if (p.scope == scope && p.id.equals(id)) {
				return p;
			}
		}
		return null;
	}

	private static Profile byKey(String key) {
		for (Profile p : PROFILES) {
			if (p.key().equals(key)) {
				return p;
			}
		}
		return null;
	}

	public static Profile defaultProfile() {
		return find(Scope.VERSION, DEFAULT_ID);
	}

	public static void noteChange() {
		if (ready && active != null && (!active.autoSave || active.locked)) {
			unsaved = true;
		}
	}

	public static void afterSave(JsonObject root) {
		if (!ready || active == null || !active.autoSave || active.locked) {
			return;
		}
		active.config = root.deepCopy();
		active.updated = System.currentTimeMillis();
		write(active);
		unsaved = false;
	}

	public static boolean write(Profile p) {
		return Config.write(p.file(), p.toJson());
	}

	private static void writeState() {
		JsonObject state = new JsonObject();
		state.addProperty("active", active == null ? DEFAULT_ID : active.key());
		Config.write(versionDir().resolve(STATE), state);
	}

	public static void activate(Profile p) {
		if (p == null) {
			return;
		}
		if (active != null && active != p && active.autoSave && !active.locked) {
			active.config = Config.snapshot();
			active.updated = System.currentTimeMillis();
			write(active);
		}
		Config.apply(p.config, p.parts);
		active = p;
		unsaved = false;
		writeState();
		Config.save();
		Theme.refresh(true);
		notice("Profile: " + p.name);
	}

	public static void revert() {
		if (active != null) {
			Config.apply(active.config, active.parts);
			unsaved = false;
			Config.save();
			Theme.refresh(true);
		}
	}

	public static void saveCurrent(Profile p) {
		p.config = Config.snapshot();
		p.updated = System.currentTimeMillis();
		write(p);
		if (p == active) {
			unsaved = false;
		}
	}

	public static void resetToDefaults(Profile p) {
		p.config = new JsonObject();
		p.updated = System.currentTimeMillis();
		write(p);
		if (p == active) {
			Config.apply(p.config, p.parts);
			unsaved = false;
			Config.save();
			Theme.refresh(true);
		}
	}

	public static Profile create(String name, Scope scope, boolean fromDefaults) {
		Profile p = new Profile();
		p.id = newId(scope);
		p.scope = scope;
		p.name = name == null || name.isBlank() ? "New profile" : name.trim();
		p.config = fromDefaults ? new JsonObject() : Config.snapshot();
		write(p);
		PROFILES.add(p);
		sort();
		return p;
	}

	public static Profile duplicate(Profile source) {
		Profile p = source.copy();
		p.id = newId(source.scope);
		p.name = trim(source.name + " copy", 32);
		p.locked = false;
		p.created = System.currentTimeMillis();
		p.updated = p.created;
		if (source == active) {
			p.config = Config.snapshot();
		}
		write(p);
		PROFILES.add(p);
		sort();
		return p;
	}

	public static void delete(Profile p) {
		if (p == null || p.isDefault()) {
			return;
		}
		if (p == active) {
			activate(defaultProfile());
		}
		try {
			Files.deleteIfExists(p.file());
		} catch (Exception e) {
			RustyClient.LOG.warn("could not delete profile {}", p.name, e);
		}
		PROFILES.remove(p);
	}

	public static void setScope(Profile p, Scope scope) {
		if (p == null || p.isDefault() || p.scope == scope) {
			return;
		}
		Path old = p.file();
		p.scope = scope;
		if (Files.exists(p.file())) {
			p.id = newId(scope);
		}
		if (write(p)) {
			try {
				Files.deleteIfExists(old);
			} catch (Exception ignored) {
			}
		}
		if (p == active) {
			writeState();
		}
		sort();
	}

	private static String newId(Scope scope) {
		Path dir = scope == Scope.GLOBAL ? globalDir() : versionDir();
		while (true) {
			String id = UUID.randomUUID().toString().substring(0, 8);
			if (!Files.exists(dir.resolve(id + ".json"))) {
				return id;
			}
		}
	}

	public static List<VersionProfiles> otherVersions() {
		List<VersionProfiles> out = new ArrayList<>();
		Path root = root();
		if (root == null) {
			return out;
		}
		for (String v : VERSIONS) {
			if (v.equals(version())) {
				continue;
			}
			Path config = root.resolve("instances").resolve(v).resolve("config");
			List<Profile> list = new ArrayList<>();
			load(config.resolve("rustyclient-profiles"), Scope.VERSION, list);
			if (list.isEmpty()) {
				JsonObject live = Config.read(config.resolve("rustyclient.json"));
				if (live != null) {
					Profile p = new Profile();
					p.id = DEFAULT_ID;
					p.scope = Scope.VERSION;
					p.name = v;
					p.description = "Settings saved on " + v;
					p.config = live;
					list.add(p);
				}
			}
			list.sort((a, b) -> a.isDefault() != b.isDefault() ? (a.isDefault() ? -1 : 1) : Long.compare(a.created, b.created));
			for (Profile p : list) {
				p.sourceVersion = v;
			}
			out.add(new VersionProfiles(v, list));
		}
		return out;
	}

	public static Profile importProfile(Profile source, String from) {
		Profile p = source.copy();
		p.id = newId(Scope.VERSION);
		p.scope = Scope.VERSION;
		p.locked = false;
		String suffix = from == null || from.isEmpty() || source.name.equals(from) ? "" : " (" + from + ")";
		p.name = trim(source.name, 32 - suffix.length()) + suffix;
		p.created = System.currentTimeMillis();
		p.updated = p.created;
		write(p);
		PROFILES.add(p);
		sort();
		return p;
	}

	public static String export(Profile p) {
		try {
			JsonObject root = p.toJson();
			if (p == active) {
				root.add("config", Config.snapshot());
			}
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
				gzip.write(Config.GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
			}
			return EXPORT_PREFIX + Base64.getEncoder().encodeToString(bytes.toByteArray());
		} catch (Exception e) {
			return "";
		}
	}

	public static Profile importText(String text) {
		if (text == null) {
			return null;
		}
		String clean = text.trim();
		try {
			String json;
			if (clean.startsWith(EXPORT_PREFIX)) {
				byte[] raw = Base64.getDecoder().decode(clean.substring(EXPORT_PREFIX.length()).trim());
				try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(raw))) {
					json = new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
				}
			} else if (clean.startsWith("{")) {
				json = clean;
			} else {
				return null;
			}
			JsonElement root = JsonParser.parseString(json);
			if (!root.isJsonObject()) {
				return null;
			}
			JsonObject obj = root.getAsJsonObject();
			if (!obj.has("config") && obj.has("modules")) {
				JsonObject wrapped = new JsonObject();
				wrapped.addProperty("name", "Imported");
				wrapped.add("config", obj);
				obj = wrapped;
			}
			if (!obj.has("config")) {
				return null;
			}
			Profile source = Profile.fromJson(obj, "import", Scope.VERSION);
			return importProfile(source, source.createdFor.equals(version()) ? "" : source.createdFor);
		} catch (Exception e) {
			return null;
		}
	}

	public static void tick(Minecraft mc) {
		if (!ready || timer-- > 0) {
			return;
		}
		timer = 10;
		String now = currentPlace(mc);
		if (now.equals(place)) {
			return;
		}
		String before = place;
		place = now;
		if (now.isEmpty()) {
			if (autoProfile != null && autoProfile == active && autoProfile.switchBack && beforeAuto != null && PROFILES.contains(beforeAuto)) {
				activate(beforeAuto);
			}
			autoProfile = null;
			beforeAuto = null;
			return;
		}
		Profile match = match(now);
		if (match == null || match == active) {
			return;
		}
		if (before.isEmpty() || autoProfile == null) {
			beforeAuto = active;
		}
		autoProfile = match;
		activate(match);
	}

	private static String currentPlace(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			return "";
		}
		if (mc.hasSingleplayerServer()) {
			return "singleplayer";
		}
		ServerData server = mc.getCurrentServer();
		if (server == null || server.ip == null) {
			return "server";
		}
		String ip = server.ip.trim().toLowerCase(Locale.ROOT);
		int colon = ip.lastIndexOf(':');
		return colon > 0 && ip.indexOf(':') == colon ? ip.substring(0, colon) : ip;
	}

	private static Profile match(String where) {
		for (Profile p : PROFILES) {
			if (where.equals("singleplayer")) {
				if (p.singleplayer) {
					return p;
				}
				continue;
			}
			for (String entry : servers(p)) {
				if (where.equals(entry) || where.endsWith("." + entry)) {
					return p;
				}
			}
		}
		return null;
	}

	public static List<String> servers(Profile p) {
		List<String> out = new ArrayList<>();
		for (String part : p.servers.split("[,; ]+")) {
			String clean = part.trim().toLowerCase(Locale.ROOT);
			int colon = clean.lastIndexOf(':');
			if (colon > 0 && clean.indexOf(':') == colon) {
				clean = clean.substring(0, colon);
			}
			if (!clean.isEmpty()) {
				out.add(clean);
			}
		}
		return out;
	}

	private static void notice(String text) {
		message = text;
		messageUntil = System.currentTimeMillis() + 2500;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && Compat.screen() == null) {
			mc.player.sendOverlayMessage(Component.literal(text));
		}
	}

	public static String message() {
		return System.currentTimeMillis() < messageUntil ? message : null;
	}

	private static String trim(String text, int max) {
		return text.length() > max ? text.substring(0, Math.max(1, max)) : text;
	}

	private static String string(JsonObject obj, String key, String def) {
		try {
			return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : def;
		} catch (Exception e) {
			return def;
		}
	}

	private static long number(JsonObject obj, String key, long def) {
		try {
			return obj.has(key) ? obj.get(key).getAsLong() : def;
		} catch (Exception e) {
			return def;
		}
	}

	private static boolean flag(JsonObject obj, String key, boolean def) {
		try {
			return obj.has(key) ? obj.get(key).getAsBoolean() : def;
		} catch (Exception e) {
			return def;
		}
	}
}
