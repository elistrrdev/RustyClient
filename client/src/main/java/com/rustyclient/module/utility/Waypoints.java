package com.rustyclient.module.utility;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rustyclient.core.Category;
import com.rustyclient.core.Module;
import com.rustyclient.core.Theme;
import com.rustyclient.core.setting.BoolSetting;
import com.rustyclient.core.setting.KeySetting;
import com.rustyclient.core.setting.NumberSetting;
import com.rustyclient.gui.RustyScreen;
import com.rustyclient.module.ClientSettings;
import com.rustyclient.platform.Compat;
import com.rustyclient.render.Draw;
import com.rustyclient.render.Fonts;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class Waypoints extends Module {
	public static final class Point {
		public final String world;
		public String name;
		public final double x;
		public final double y;
		public final double z;
		public int color;
		public boolean visible = true;
		public final boolean death;

		Point(String world, String name, double x, double y, double z, int color, boolean visible, boolean death) {
			this.world = world;
			this.name = name;
			this.x = x;
			this.y = y;
			this.z = z;
			this.color = color;
			this.visible = visible;
			this.death = death;
		}
	}

	public static final int[] COLORS = {0xFF1AB0DD, 0xFFF2C14E, 0xFF6CCB8C, 0xFFF07EA8, 0xFFEF5B52, 0xFFA78BFA, 0xFFFF9F43, 0xFFF2EEEA};
	private static final int DEATH_COLOR = 0xFFEF5B52;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final KeySetting addKey = key("Add waypoint key", KEYBIND, "key.keyboard.n");
	private final KeySetting menuKey = key("Waypoints menu key", KEYBIND, "key.keyboard.m");
	private final KeySetting removeKey = key("Remove nearest key", KEYBIND, KeySetting.NONE);
	private final BoolSetting beam = bool("Show beam", GENERAL, true);
	private final BoolSetting labels = bool("Show labels", GENERAL, true);
	private final BoolSetting distance = bool("Show distance", GENERAL, true);
	private final BoolSetting deathPoint = bool("Waypoint on death", GENERAL, false);
	private final NumberSetting beamWidth = number("Beam width", APPEARANCE, 0.35, 0.1, 1, 0.05, NumberSetting.decimal(""));
	private final NumberSetting beamOpacity = number("Beam opacity", APPEARANCE, 0.45, 0.1, 1, 0.05, NumberSetting.percent());
	private final NumberSetting maxDistance = number("Max distance", APPEARANCE, 5000, 100, 20000, 100, NumberSetting.integer(" blocks"));
	private final List<Point> points = new ArrayList<>();
	private boolean loaded;
	private boolean addWas;
	private boolean removeWas;
	private boolean menuWas;
	private boolean wasDead;

	public Waypoints() {
		super("Waypoints", "map-pin", Category.UTILITY, false);
	}

	private Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("rustyclient-waypoints.json");
	}

	public String world() {
		if (mc.level == null) {
			return "";
		}
		ServerData server = mc.getCurrentServer();
		String base = server != null ? server.ip : "local";
		return base + "|" + mc.level.dimension().identifier();
	}

	public static String worldLabel(String world) {
		int split = world.indexOf('|');
		if (split < 0) {
			return world;
		}
		String server = world.substring(0, split);
		String dimension = world.substring(split + 1);
		int colon = dimension.indexOf(':');
		dimension = colon >= 0 ? dimension.substring(colon + 1) : dimension;
		dimension = dimension.replace('_', ' ');
		return (server.equals("local") ? "Singleplayer" : server) + ", " + dimension;
	}

	private void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		try {
			if (!Files.isRegularFile(file())) {
				return;
			}
			JsonElement root = JsonParser.parseString(Files.readString(file(), StandardCharsets.UTF_8));
			for (JsonElement e : root.getAsJsonArray()) {
				JsonObject o = e.getAsJsonObject();
				boolean visible = !o.has("visible") || o.get("visible").getAsBoolean();
				boolean death = o.has("death") && o.get("death").getAsBoolean();
				points.add(new Point(o.get("world").getAsString(), o.get("name").getAsString(), o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble(), o.get("color").getAsInt(), visible, death));
			}
		} catch (Exception ignored) {
		}
	}

	public void save() {
		JsonArray arr = new JsonArray();
		for (Point p : points) {
			JsonObject o = new JsonObject();
			o.addProperty("world", p.world);
			o.addProperty("name", p.name);
			o.addProperty("x", p.x);
			o.addProperty("y", p.y);
			o.addProperty("z", p.z);
			o.addProperty("color", p.color);
			o.addProperty("visible", p.visible);
			o.addProperty("death", p.death);
			arr.add(o);
		}
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(arr), StandardCharsets.UTF_8);
		} catch (Exception ignored) {
		}
	}

	public List<Point> points() {
		load();
		return Collections.unmodifiableList(points);
	}

	public Point addHere() {
		load();
		if (mc.player == null || mc.level == null) {
			return null;
		}
		int count = 0;
		for (Point p : points) {
			if (p.world.equals(world()) && !p.death) {
				count++;
			}
		}
		Vec3 pos = mc.player.position();
		Point point = new Point(world(), "Waypoint " + (count + 1), Math.floor(pos.x) + 0.5, Math.floor(pos.y), Math.floor(pos.z) + 0.5, COLORS[count % COLORS.length], true, false);
		points.add(point);
		save();
		return point;
	}

	private void addDeath() {
		Vec3 pos = mc.player.position();
		int count = 0;
		for (Point p : points) {
			if (p.world.equals(world()) && p.death) {
				count++;
			}
		}
		String name = count == 0 ? "Death" : "Death " + (count + 1);
		points.add(new Point(world(), name, Math.floor(pos.x) + 0.5, Math.floor(pos.y), Math.floor(pos.z) + 0.5, DEATH_COLOR, true, true));
		save();
		Compat.chat().addClientSystemMessage(Component.literal("Saved " + name + " at " + (int) Math.floor(pos.x) + " " + (int) Math.floor(pos.y) + " " + (int) Math.floor(pos.z)));
	}

	public void remove(Point point) {
		load();
		points.remove(point);
		save();
	}

	public double distanceTo(Point p) {
		if (mc.player == null || !p.world.equals(world())) {
			return -1;
		}
		return Math.sqrt(mc.player.position().distanceToSqr(p.x, p.y, p.z));
	}

	@Override
	public void tick() {
		load();
		boolean free = Compat.screen() == null;
		boolean add = free && addKey.bound() && Compat.isKeyDown(addKey.get());
		boolean remove = free && removeKey.bound() && Compat.isKeyDown(removeKey.get());
		boolean menu = free && menuKey.bound() && Compat.isKeyDown(menuKey.get());
		if (add && !addWas && mc.player != null) {
			Point p = addHere();
			if (p != null) {
				Compat.chat().addClientSystemMessage(Component.literal("Added " + p.name + " at " + (int) Math.floor(p.x) + " " + (int) Math.floor(p.y) + " " + (int) Math.floor(p.z)));
			}
		}
		if (remove && !removeWas && mc.player != null) {
			Point nearest = null;
			double best = Double.MAX_VALUE;
			for (Point p : points) {
				if (!p.world.equals(world())) {
					continue;
				}
				double dist = mc.player.position().distanceToSqr(p.x, p.y, p.z);
				if (dist < best) {
					best = dist;
					nearest = p;
				}
			}
			if (nearest != null) {
				remove(nearest);
				Compat.chat().addClientSystemMessage(Component.literal("Removed " + nearest.name));
			}
		}
		if (menu && !menuWas) {
			RustyScreen.open(RustyScreen.Page.WAYPOINTS);
		}
		addWas = add;
		removeWas = remove;
		menuWas = menu;
		boolean dead = mc.player != null && mc.player.isDeadOrDying();
		if (dead && !wasDead && deathPoint.on()) {
			addDeath();
		}
		wasDead = dead;
	}

	public void submitBeams(PoseStack pose, SubmitNodeCollector collector) {
		load();
		if (!beam.on() || mc.player == null || mc.level == null) {
			return;
		}
		Vec3 cam = Compat.camera().position();
		String here = world();
		List<Point> shown = new ArrayList<>();
		for (Point p : points) {
			if (p.visible && p.world.equals(here) && mc.player.position().distanceTo(new Vec3(p.x, p.y, p.z)) <= maxDistance.v()) {
				shown.add(p);
			}
		}
		if (shown.isEmpty()) {
			return;
		}
		float half = beamWidth.f() / 2f;
		int coreAlpha = Math.round(beamOpacity.f() * 255);
		int glowAlpha = Math.round(beamOpacity.f() * 90);
		collector.submitCustomGeometry(pose, RenderTypes.debugQuads(), (p, buffer) -> {
			for (Point point : shown) {
				float x = (float) (point.x - cam.x), y = (float) (point.y - cam.y), z = (float) (point.z - cam.z);
				float top = y + 384f;
				column(p, buffer, x, y, z, top, half, Theme.withAlpha(point.color, coreAlpha));
				column(p, buffer, x, y, z, top, half * 2.2f, Theme.withAlpha(point.color, glowAlpha));
			}
		});
	}

	private static void column(PoseStack.Pose p, VertexConsumer b, float cx, float y0, float cz, float y1, float r, int c) {
		float x0 = cx - r, x1 = cx + r, z0 = cz - r, z1 = cz + r;
		face(p, b, x0, z0, x1, z0, y0, y1, c);
		face(p, b, x1, z0, x1, z1, y0, y1, c);
		face(p, b, x1, z1, x0, z1, y0, y1, c);
		face(p, b, x0, z1, x0, z0, y0, y1, c);
	}

	private static void face(PoseStack.Pose p, VertexConsumer b, float ax, float az, float bx, float bz, float y0, float y1, int c) {
		b.addVertex(p, ax, y0, az).setColor(c);
		b.addVertex(p, bx, y0, bz).setColor(c);
		b.addVertex(p, bx, y1, bz).setColor(c);
		b.addVertex(p, ax, y1, az).setColor(c);
		b.addVertex(p, ax, y0, az).setColor(c);
		b.addVertex(p, ax, y1, az).setColor(c);
		b.addVertex(p, bx, y1, bz).setColor(c);
		b.addVertex(p, bx, y0, bz).setColor(c);
	}

	public void draw(Draw d, int screenW, int screenH) {
		load();
		if (!labels.on() || mc.player == null || mc.level == null) {
			return;
		}
		ClientSettings settings = ClientSettings.get();
		d.hudText(settings == null || settings.hudFont.is("Vanilla"));
		Camera camera = Compat.camera();
		Vec3 cam = camera.position();
		double yaw = Math.toRadians(camera.yRot());
		double pitch = Math.toRadians(camera.xRot());
		double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
		double rx = -Math.cos(yaw), ry = 0, rz = -Math.sin(yaw);
		double ux = ry * fz - rz * fy, uy = rz * fx - rx * fz, uz = rx * fy - ry * fx;
		double focal = (screenH / 2.0) / Math.tan(Math.toRadians(camera.getFov()) / 2.0);
		String here = world();
		for (Point p : points) {
			if (!p.visible || !p.world.equals(here)) {
				continue;
			}
			double dx = p.x - cam.x, dy = p.y + 1.6 - cam.y, dz = p.z - cam.z;
			double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (dist > maxDistance.v()) {
				continue;
			}
			double depth = dx * fx + dy * fy + dz * fz;
			if (depth < 0.1) {
				continue;
			}
			double sx = screenW / 2.0 + (dx * rx + dy * ry + dz * rz) / depth * focal;
			double sy = screenH / 2.0 - (dx * ux + dy * uy + dz * uz) / depth * focal;
			if (sx < -100 || sx > screenW + 100 || sy < -50 || sy > screenH + 50) {
				continue;
			}
			float x = (float) sx / d.s, y = (float) sy / d.s;
			String meters = distance.on() ? String.format(Locale.ROOT, "%.0fm", dist) : "";
			float nameW = d.width(p.name, Fonts.Style.SEMIBOLD, 8);
			float distW = meters.isEmpty() ? 0 : d.width(meters, Fonts.Style.MEDIUM, 7.5f) + 5;
			float w = nameW + distW + 18, h = 13;
			float left = x - w / 2f, topY = y - h / 2f;
			d.round(left, topY, w, h, 4, 0xC00F0E0D);
			d.circle(left + 7, y, 2.5f, p.color);
			d.textMid(p.name, left + 12, y, Fonts.Style.SEMIBOLD, 8, Theme.TEXT);
			if (!meters.isEmpty()) {
				d.textMid(meters, left + 12 + nameW + 5, y, Fonts.Style.MEDIUM, 7.5f, Theme.MUTED);
			}
		}
	}
}
