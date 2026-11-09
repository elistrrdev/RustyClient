package com.rustyclient.module.hud;

import com.rustyclient.core.Category;
import com.rustyclient.core.Clicks;
import com.rustyclient.core.TextHud;
import com.rustyclient.core.setting.BoolSetting;
import com.rustyclient.core.setting.ChoiceSetting;
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
		private final BoolSetting rightClicks = bool("Show right clicks", GENERAL, false);

		public Cps() {
			super("CPS", "mouse", Category.HUD, false, 0.01, 0.52);
		}

		@Override
		protected String label() {
			return "CPS";
		}

		@Override
		protected String value() {
			return rightClicks.on() ? Clicks.left() + " | " + Clicks.right() : String.valueOf(Clicks.left());
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
		private final ChoiceSetting show = choice("Show", APPEARANCE, "Ping", "Ping", "Address", "Both");

		public ServerInfo() {
			super("Server Info", "server", Category.HUD, false, 0.01, 0.07);
		}

		@Override
		protected String label() {
			return show.is("Address") ? "IP" : "Ping";
		}

		private int ping() {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null || mc.getConnection() == null) {
				return -1;
			}
			PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
			return info == null ? -1 : info.getLatency();
		}

		private String address() {
			ServerData server = Minecraft.getInstance().getCurrentServer();
			if (server != null) {
				return server.ip;
			}
			return Minecraft.getInstance().hasSingleplayerServer() ? "Singleplayer" : "Offline";
		}

		@Override
		protected String value() {
			int ping = ping();
			String p = ping < 0 ? "0 ms" : ping + " ms";
			if (show.is("Address")) {
				return address();
			}
			if (show.is("Both")) {
				return p + "  " + address();
			}
			return p;
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
