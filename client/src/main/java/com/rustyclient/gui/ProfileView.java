package com.rustyclient.gui;

import com.rustyclient.RustyClient;
import com.rustyclient.core.Module;
import com.rustyclient.core.Modules;
import com.rustyclient.core.Theme;
import com.rustyclient.platform.Compat;
import com.rustyclient.render.Draw;
import com.rustyclient.render.Fonts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.ServerData;

final class ProfileView {
	private final RustyScreen screen;

	ProfileView(RustyScreen screen) {
		this.screen = screen;
	}

	void draw() {
		Ui ui = screen.ui;
		Draw d = ui.d;
		Minecraft mc = Minecraft.getInstance();
		float x0 = RustyScreen.CONTENT_X;
		float fullW = RustyScreen.PANEL_W - x0 - RustyScreen.PAD_R;
		d.textMid("Profile", x0, 39, Fonts.Style.BOLD, 18, Theme.TEXT);

		float cardY = 74;
		float cardH = 132;
		d.round(x0, cardY, fullW, cardH, 12, Theme.SURFACE);
		float head = 84;
		float hx = x0 + 24, hy = cardY + (cardH - head) / 2f;
		d.round(hx - 4, hy - 4, head + 8, head + 8, 12, Theme.SURFACE_3);
		if (mc.player != null) {
			int px = Math.round(d.originX() + hx * d.s);
			int py = Math.round(d.originY() + hy * d.s);
			int size = Math.round(head * d.s);
			PlayerFaceExtractor.extractRenderState(d.g, mc.player.getSkin(), px, py, size);
		} else {
			d.icon("user", hx + head / 2f, hy + head / 2f, 40, Theme.MUTED);
		}
		String name = mc.getUser().getName();
		float tx = hx + head + 24;
		d.textMid(name, tx, cardY + 46, Fonts.Style.BOLD, 22, Theme.TEXT);
		String uuid = mc.getUser().getProfileId() == null ? "Offline" : mc.getUser().getProfileId().toString();
		d.textMid(uuid, tx, cardY + 76, Fonts.Style.MONO, 12, Theme.MUTED);
		String badge = "Rusty Client " + RustyClient.version();
		float bw = d.width(badge, Fonts.Style.SEMIBOLD, 11) + 20;
		d.round(tx, cardY + 92, bw, 22, 11, Theme.accentSoft());
		d.textCenter(badge, tx + bw / 2f, cardY + 103, Fonts.Style.SEMIBOLD, 11, Theme.accentText());

		float gy = cardY + cardH + 14;
		float gap = 12;
		float tw = (fullW - gap * 2) / 3f;
		int enabled = 0, total = 0;
		for (Module module : Modules.all()) {
			if (!module.hidden()) {
				total++;
				if (module.enabled()) {
					enabled++;
				}
			}
		}
		long seconds = (System.currentTimeMillis() - RustyClient.START) / 1000;
		String session = String.format("%dh %02dm", seconds / 3600, (seconds / 60) % 60);
		stat("clock", "Session time", session, x0, gy, tw);
		stat("layout-grid", "Modules enabled", enabled + " of " + total, x0 + tw + gap, gy, tw);
		stat("monitor", "Minecraft", Compat.gameVersion(), x0 + (tw + gap) * 2, gy, tw);

		float iy = gy + 96;
		String where;
		ServerData server = mc.getCurrentServer();
		if (server != null) {
			where = server.ip;
		} else if (mc.hasSingleplayerServer()) {
			where = "Singleplayer";
		} else {
			where = "Main menu";
		}
		stat("server", "Playing on", where, x0, iy, tw);
		stat("gauge", "Frame rate", mc.getFps() + " fps", x0 + tw + gap, iy, tw);
		Runtime rt = Runtime.getRuntime();
		long used = (rt.totalMemory() - rt.freeMemory()) / 1048576L;
		stat("memory-stick", "Memory", used + " / " + rt.maxMemory() / 1048576L + " MB", x0 + (tw + gap) * 2, iy, tw);
	}

	private void stat(String icon, String label, String value, float x, float y, float w) {
		Draw d = screen.ui.d;
		d.round(x, y, w, 84, 12, Theme.SURFACE);
		d.round(x + 16, y + 22, 40, 40, 10, Theme.SURFACE_3);
		d.icon(icon, x + 36, y + 42, 18, Theme.accent());
		d.textMid(label, x + 70, y + 32, Fonts.Style.SEMIBOLD, 12, Theme.MUTED);
		String shown = value;
		if (d.width(shown, Fonts.Style.BOLD, 15) > w - 86) {
			while (shown.length() > 1 && d.width(shown + "...", Fonts.Style.BOLD, 15) > w - 86) {
				shown = shown.substring(0, shown.length() - 1);
			}
			shown = shown + "...";
		}
		d.textMid(shown, x + 70, y + 53, Fonts.Style.BOLD, 15, Theme.TEXT);
	}
}
