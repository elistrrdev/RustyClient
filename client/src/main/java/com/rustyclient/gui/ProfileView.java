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
	private boolean revealed;

	ProfileView(RustyScreen screen) {
		this.screen = screen;
	}

	void hide() {
		revealed = false;
	}

	private void uuid(String uuid, float x, float cy) {
		Ui ui = screen.ui;
		Draw d = ui.d;
		float size = 12;
		float w = d.width(uuid, Fonts.Style.MEDIUM, size);
		float padX = 8, h = 22;
		float bx = x - padX, by = cy - h / 2f, bw = w + padX * 2;
		boolean hover = ui.hover(bx, by, bw, h);
		float t = ui.anim("uuid_reveal", revealed ? 1f : 0f, 16f);
		if (t < 0.999f) {
			float hide = 1f - t;
			d.round(bx, by, bw, h, h / 2f, Theme.withAlpha(hover ? Theme.SURFACE_3 : Theme.SURFACE_2, Math.round(255 * hide)));
			float px = x;
			String[] parts = uuid.split("-");
			float dash = d.width("-", Fonts.Style.MEDIUM, size);
			for (String part : parts) {
				float pw = d.width(part, Fonts.Style.MEDIUM, size);
				d.shadow(px + 1, cy - 2.5f, Math.max(2, pw - 2), 5, 2.5f, 4.5f, Theme.withAlpha(Theme.MUTED, Math.round(150 * hide)));
				px += pw + dash;
			}
		}
		if (t > 0.001f) {
			d.textMid(uuid, x, cy, Fonts.Style.MEDIUM, size, Theme.withAlpha(Theme.MUTED, Math.round(255 * t)));
		}
		ui.click(bx, by, bw, h, () -> revealed = !revealed);
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
		uuid(uuid, tx, cardY + 76);
		String brand = "Rusty Client";
		String version = RustyClient.version();
		float brandW = d.width(brand, Fonts.Style.SEMIBOLD, 11);
		float versionW = d.width(version, Fonts.Style.SEMIBOLD, 11);
		float bw = brandW + 5 + versionW + 20;
		d.round(tx, cardY + 92, bw, 22, 11, Theme.accentSoft());
		d.textMid(brand, tx + 10, cardY + 103, Fonts.Style.SEMIBOLD, 11, Theme.accentText());
		d.textMid(version, tx + 10 + brandW + 5, cardY + 103, Fonts.Style.SEMIBOLD, 11, Theme.accentText());

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
