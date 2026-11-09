package com.rustyclient.module.utility;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rustyclient.core.Category;
import com.rustyclient.core.Module;
import com.rustyclient.core.Theme;
import com.rustyclient.core.setting.BoolSetting;
import com.rustyclient.core.setting.KeySetting;
import com.rustyclient.core.setting.NumberSetting;
import com.rustyclient.platform.Compat;
import com.rustyclient.render.Draw;
import com.rustyclient.render.Fonts;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Waypoints extends Module {
	private record Point(String world, String name, double x, double y, double z, int color) {
	}

	private static final int[] COLORS = {0xFF1AB0DD, 0xFFF2C14E, 0xFF6CCB8C, 0xFFF07EA8, 0xFFEF5B52, 0xFFA78BFA};
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final KeySetting addKey = key("Add waypoint key", KEYBIND, "key.keyboard.b");
	private final KeySetting removeKey = key("Remove nearest key", KEYBIND, KeySetting.NONE);
	private final BoolSetting distance = bool("Show distance", GENERAL, true);
	private final BoolSetting beacon = bool("Show marker beam", GENERAL, true);
	private final NumberSetting maxDistance = number("Max distance", APPEARANCE, 5000, 100, 20000, 100, NumberSetting.integer(" blocks"));
	private final List<Point> points = new ArrayList<>();
	private boolean loaded;
	private boolean addWas;
	private boolean removeWas;

	public Waypoints() {
		super("Waypoints", "map-pin", Category.UTILITY, false);
	}

	private Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("rustyclient-waypoints.json");
	}

	private String world() {
		if (mc.level == null) {
			return "";
		}
		ServerData server = mc.getCurrentServer();
		String base = server != null ? server.ip : "local";
		return base + "|" + mc.level.dimension().identifier();
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
				points.add(new Point(o.get("world").getAsString(), o.get("name").getAsString(), o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble(), o.get("color").getAsInt()));
			}
		} catch (Exception ignored) {
		}
	}

	private void save() {
		JsonArray arr = new JsonArray();
		for (Point p : points) {
			JsonObject o = new JsonObject();
			o.addProperty("world", p.world());
			o.addProperty("name", p.name());
			o.addProperty("x", p.x());
			o.addProperty("y", p.y());
			o.addProperty("z", p.z());
			o.addProperty("color", p.color());
			arr.add(o);
		}
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(arr), StandardCharsets.UTF_8);
		} catch (Exception ignored) {
		}
	}

	@Override
	public void tick() {
		load();
		boolean free = Compat.screen() == null;
		boolean add = free && Compat.isKeyDown(addKey.get());
		boolean remove = free && removeKey.bound() && Compat.isKeyDown(removeKey.get());
		if (add && !addWas && mc.player != null) {
			int count = 0;
			for (Point p : points) {
				if (p.world().equals(world())) {
					count++;
				}
			}
			Vec3 pos = mc.player.position();
			String name = "Waypoint " + (count + 1);
			points.add(new Point(world(), name, Math.floor(pos.x) + 0.5, Math.floor(pos.y), Math.floor(pos.z) + 0.5, COLORS[count % COLORS.length]));
			save();
			Compat.chat().addClientSystemMessage(Component.literal("Added " + name + " at " + (int) Math.floor(pos.x) + " " + (int) Math.floor(pos.y) + " " + (int) Math.floor(pos.z)));
		}
		if (remove && !removeWas && mc.player != null) {
			Point nearest = null;
			double best = Double.MAX_VALUE;
			for (Point p : points) {
				if (!p.world().equals(world())) {
					continue;
				}
				double dist = mc.player.position().distanceToSqr(p.x(), p.y(), p.z());
				if (dist < best) {
					best = dist;
					nearest = p;
				}
			}
			if (nearest != null) {
				points.remove(nearest);
				save();
				Compat.chat().addClientSystemMessage(Component.literal("Removed " + nearest.name()));
			}
		}
		addWas = add;
		removeWas = remove;
	}

	public int count() {
		return points.size();
	}

	public void draw(Draw d, int screenW, int screenH) {
		load();
		if (mc.player == null || mc.level == null) {
			return;
		}
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
			if (!p.world().equals(here)) {
				continue;
			}
			double dx = p.x() - cam.x, dy = p.y() + 1.0 - cam.y, dz = p.z() - cam.z;
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
			if (sx < -50 || sx > screenW + 50 || sy < -50 || sy > screenH + 50) {
				continue;
			}
			float x = (float) sx / d.s, y = (float) sy / d.s;
			int color = p.color();
			if (beacon.on()) {
				d.rect(x - 0.5f, y - 60, 1, 54, Theme.withAlpha(color, 0x70));
			}
			d.circle(x, y, 4, 0xC0000000);
			d.circle(x, y, 3, color);
			String label = p.name() + (distance.on() ? "  " + String.format(Locale.ROOT, "%.0fm", dist) : "");
			float w = d.width(label, Fonts.Style.SEMIBOLD, 8) + 10;
			d.round(x - w / 2f, y - 19, w, 12, 4, 0xB00F0E0D);
			d.textCenter(label, x, y - 13, Fonts.Style.SEMIBOLD, 8, Theme.TEXT);
		}
	}
}
