package com.rustyclient.module.hud;

import com.rustyclient.core.Category;
import com.rustyclient.core.Clicks;
import com.rustyclient.core.TextHud;
import com.rustyclient.core.setting.BoolSetting;
import com.rustyclient.core.setting.ChoiceSetting;
import com.rustyclient.core.setting.TextSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.phys.Vec3;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class SimpleHuds {
	private SimpleHuds() {
	}

	public static final class Cps extends TextHud {
		private final ChoiceSetting buttons = choice("Buttons", GENERAL, "Left", "Left", "Right", "Both");
		private final BoolSetting brackets = bool("Brackets", GENERAL, false);
		private final BoolSetting suffix = bool("CPS after value", GENERAL, false);
		private final BoolSetting hideZero = bool("Hide when zero", GENERAL, false);
		private final BoolSetting rightFirst = bool("Right button first", GENERAL, false);
		private final ChoiceSetting separator = choice("Separator", APPEARANCE, "Bar", "Bar", "Slash", "Dash", "Colon", "Space");
		private final TextSetting labelText = text("Label text", APPEARANCE, "CPS", 16);
		private final BoolSetting colored = bool("Color by speed", GENERAL, false);

		public Cps() {
			super("CPS", "mouse", Category.HUD, false, 0.01, 0.52);
			separator.visibleWhen(() -> buttons.is("Both"));
			rightFirst.visibleWhen(() -> buttons.is("Both"));
		}

		@Override
		public boolean visibleNow() {
			return !hideZero.on() || Clicks.left() + Clicks.right() > 0;
		}

		@Override
		protected String label() {
			return labelText.get().isBlank() ? "CPS" : labelText.get();
		}

		@Override
		protected int valueColor(int fallback) {
			if (!colored.on()) {
				return fallback;
			}
			int c = Math.max(Clicks.left(), Clicks.right());
			if (c >= 12) {
				return 0xFFEF5B52;
			}
			return c >= 7 ? 0xFFF2C14E : 0xFF6CCB8C;
		}

		private String sep() {
			return switch (separator.get()) {
				case "Slash" -> " / ";
				case "Dash" -> " - ";
				case "Colon" -> " : ";
				case "Space" -> "  ";
				default -> " | ";
			};
		}

		@Override
		protected String value() {
			String text = switch (buttons.get()) {
				case "Right" -> String.valueOf(Clicks.right());
				case "Both" -> rightFirst.on() ? Clicks.right() + sep() + Clicks.left() : Clicks.left() + sep() + Clicks.right();
				default -> String.valueOf(Clicks.left());
			};
			if (brackets.on()) {
				text = "[" + text + "]";
			}
			return suffix.on() ? text + " CPS" : text;
		}
	}

	public static final class Fps extends TextHud {
		public Fps() {
			super("FPS", "gauge", Category.HUD, false, 0.01, 0.01);
		}

		@Override
		protected String label() {
			return "FPS";
		}

		@Override
		protected String value() {
			return String.valueOf(Minecraft.getInstance().getFps());
		}
	}

	public static final class ServerInfo extends TextHud {
		private final ChoiceSetting show = choice("Show", APPEARANCE, "Address", "Address", "Server name", "Both");
		private final BoolSetting hidePort = bool("Hide port", GENERAL, true);
		private final BoolSetting lowercase = bool("Lowercase address", GENERAL, false);

		public ServerInfo() {
			super("Server Info", "server", Category.HUD, false, 0.01, 0.07);
		}

		@Override
		protected String label() {
			return show.is("Server name") ? "Server" : "IP";
		}

		private String address() {
			ServerData server = Minecraft.getInstance().getCurrentServer();
			if (server == null) {
				return Minecraft.getInstance().hasSingleplayerServer() ? "Singleplayer" : "Offline";
			}
			String ip = server.ip;
			if (hidePort.on() && ip.matches(".*:\\d+$") && !ip.startsWith("[")) {
				ip = ip.substring(0, ip.lastIndexOf(':'));
			}
			return lowercase.on() ? ip.toLowerCase(Locale.ROOT) : ip;
		}

		private String name() {
			ServerData server = Minecraft.getInstance().getCurrentServer();
			if (server == null) {
				return Minecraft.getInstance().hasSingleplayerServer() ? "Singleplayer" : "Offline";
			}
			return server.name;
		}

		@Override
		protected String value() {
			return switch (show.get()) {
				case "Server name" -> name();
				case "Both" -> name() + "  " + address();
				default -> address();
			};
		}
	}

	public static final class PingDisplay extends TextHud {
		private final BoolSetting colored = bool("Color by ping", GENERAL, true);
		private final BoolSetting suffix = bool("Show ms", GENERAL, true);
		private final BoolSetting hideLocal = bool("Hide in singleplayer", GENERAL, false);

		public PingDisplay() {
			super("Ping Display", "activity", Category.HUD, false, 0.01, 0.13);
		}

		@Override
		public boolean visibleNow() {
			return !hideLocal.on() || Minecraft.getInstance().getCurrentServer() != null;
		}

		private int ping() {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null || mc.getConnection() == null) {
				return -1;
			}
			PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
			return info == null ? -1 : info.getLatency();
		}

		@Override
		protected String label() {
			return "Ping";
		}

		@Override
		protected int valueColor(int fallback) {
			if (!colored.on()) {
				return fallback;
			}
			int p = Math.max(0, ping());
			if (p < 80) {
				return 0xFF6CCB8C;
			}
			if (p < 150) {
				return 0xFFF2C14E;
			}
			return p < 250 ? 0xFFF09A4E : 0xFFEF5B52;
		}

		@Override
		protected String value() {
			int p = Math.max(0, ping());
			return suffix.on() ? p + " ms" : String.valueOf(p);
		}
	}

	public static final class Clock extends TextHud {
		private final ChoiceSetting format = choice("Format", APPEARANCE, "24 hour", "24 hour", "12 hour");
		private final ChoiceSetting source = choice("Time", APPEARANCE, "Real time", "Real time", "Game time");
		private final BoolSetting day = bool("Show day counter", GENERAL, true);

		public Clock() {
			super("Clock", "clock", Category.HUD, false, 0.99, 0.01);
		}

		@Override
		protected String label() {
			return "";
		}

		@Override
		protected String value() {
			Minecraft mc = Minecraft.getInstance();
			LocalTime time;
			long dayCount = 0;
			if (mc.level != null) {
				long ticks = mc.level.getOverworldClockTime();
				dayCount = ticks / 24000L;
				if (source.is("Game time")) {
					long t = (ticks + 6000L) % 24000L;
					time = LocalTime.of((int) (t / 1000L), (int) ((t % 1000L) * 60L / 1000L));
				} else {
					time = LocalTime.now();
				}
			} else {
				time = LocalTime.now();
			}
			String text = time.format(DateTimeFormatter.ofPattern(format.is("12 hour") ? "h:mm a" : "HH:mm", Locale.ROOT));
			return day.on() ? text + "  Day " + dayCount : text;
		}
	}

	public static final class Speed extends TextHud {
		private final ChoiceSetting unit = choice("Unit", APPEARANCE, "Blocks/s", "Blocks/s", "km/h");
		private final BoolSetting vertical = bool("Include vertical speed", GENERAL, false);
		private double smoothed;

		public Speed() {
			super("Speed", "zap", Category.HUD, false, 0.01, 0.58);
		}

		@Override
		public void tick() {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null) {
				return;
			}
			Vec3 now = mc.player.position();
			double dx = now.x - mc.player.xo, dy = now.y - mc.player.yo, dz = now.z - mc.player.zo;
			double v = Math.sqrt(dx * dx + dz * dz + (vertical.on() ? dy * dy : 0)) * 20.0;
			smoothed = smoothed * 0.6 + v * 0.4;
		}

		@Override
		protected String label() {
			return "Speed";
		}

		@Override
		protected String value() {
			double v = unit.is("km/h") ? smoothed * 3.6 : smoothed;
			return String.format(Locale.ROOT, "%.2f %s", v, unit.is("km/h") ? "km/h" : "b/s");
		}
	}

	public static final class Memory extends TextHud {
		private final ChoiceSetting mode = choice("Display", APPEARANCE, "Percent", "Percent", "Megabytes");

		public Memory() {
			super("Memory", "memory-stick", Category.HUD, false, 0.99, 0.07);
		}

		@Override
		protected String label() {
			return "Mem";
		}

		@Override
		protected String value() {
			Runtime rt = Runtime.getRuntime();
			long used = rt.totalMemory() - rt.freeMemory();
			if (mode.is("Megabytes")) {
				return used / 1048576L + " / " + rt.maxMemory() / 1048576L + " MB";
			}
			return Math.round(used * 100.0 / rt.maxMemory()) + "%";
		}
	}
}
