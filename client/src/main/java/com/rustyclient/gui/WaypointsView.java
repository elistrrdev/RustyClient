package com.rustyclient.gui;

import com.rustyclient.core.Modules;
import com.rustyclient.core.Theme;
import com.rustyclient.module.utility.Waypoints;
import com.rustyclient.render.Draw;
import com.rustyclient.render.Fonts;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class WaypointsView {
	private final RustyScreen screen;
	private final Map<Waypoints.Point, TextInput> names = new IdentityHashMap<>();
	private boolean allWorlds;

	WaypointsView(RustyScreen screen) {
		this.screen = screen;
	}

	void draw() {
		Ui ui = screen.ui;
		Draw d = ui.d;
		float x0 = RustyScreen.CONTENT_X;
		float fullW = RustyScreen.PANEL_W - x0 - RustyScreen.PAD_R;
		d.textMid("Waypoints", x0, 39, Fonts.Style.BOLD, 18, Theme.TEXT);
		Waypoints module = Modules.get(Waypoints.class);
		if (module == null) {
			return;
		}
		float cx = x0 + d.width("Waypoints", Fonts.Style.BOLD, 18) + 16;
		cx = chip("This world", cx, !allWorlds, () -> allWorlds = false);
		chip("All worlds", cx, allWorlds, () -> allWorlds = true);

		float right = RustyScreen.PANEL_W - RustyScreen.PAD_R;
		boolean inWorld = net.minecraft.client.Minecraft.getInstance().player != null;
		if (inWorld) {
			right -= button(d, ui, "plus", "Add here", right, 39, true, () -> {
				Waypoints.Point p = module.addHere();
				if (p != null) {
					allWorlds = false;
				}
			}) + 8;
		}
		if (!module.enabled()) {
			button(d, ui, "zap", "Turn on", right, 39, false, () -> module.setEnabled(true));
		}

		String here = module.world();
		List<Waypoints.Point> list = new ArrayList<>();
		for (Waypoints.Point p : module.points()) {
			if (allWorlds || p.world.equals(here)) {
				list.add(p);
			}
		}
		float gy = 78;
		float rowH = 56, gap = 8;
		screen.viewHeight = RustyScreen.PANEL_H - gy - 1;
		screen.contentHeight = list.size() * (rowH + gap) + 12;
		screen.scroll = ui.anim("scroll", screen.scrollTarget, 18f);
		screen.clampScroll();
		d.scissor(x0 - 2, gy, fullW + 4, screen.viewHeight);
		ui.clip(x0 - 2, gy, fullW + 4, screen.viewHeight);
		for (int i = 0; i < list.size(); i++) {
			float y = gy + i * (rowH + gap) - screen.scroll;
			if (y + rowH < gy - 4 || y > gy + screen.viewHeight + 4) {
				continue;
			}
			row(module, list.get(i), x0, y, fullW, rowH);
		}
		if (list.isEmpty()) {
			String hint = inWorld ? "No waypoints here yet. Press Add here or your add waypoint key." : "Join a world to add waypoints.";
			d.textCenter(hint, x0 + fullW / 2f, gy + 80, Fonts.Style.MEDIUM, 13, Theme.MUTED);
		}
		ui.resetClip();
		d.endScissor();
		screen.scrollbar(x0 + fullW + 7, gy + 4, screen.viewHeight - 8);
		names.keySet().retainAll(module.points());
	}

	private void row(Waypoints module, Waypoints.Point p, float x, float y, float w, float h) {
		Ui ui = screen.ui;
		Draw d = ui.d;
		boolean hover = ui.hover(x, y, w, h);
		d.round(x, y, w, h, 12, hover ? Theme.SURFACE_2 : Theme.SURFACE);

		float sx = x + 28, sy = y + h / 2f;
		boolean hoverSwatch = ui.hover(sx - 14, sy - 14, 28, 28);
		d.circle(sx, sy, hoverSwatch ? 11 : 10, Theme.withAlpha(p.color, 0x55));
		d.circle(sx, sy, 7, p.color);
		ui.click(sx - 14, sy - 14, 28, 28, b -> {
			int index = 0;
			for (int i = 0; i < Waypoints.COLORS.length; i++) {
				if (Waypoints.COLORS[i] == p.color) {
					index = i;
				}
			}
			int step = b == 1 ? Waypoints.COLORS.length - 1 : 1;
			p.color = Waypoints.COLORS[(index + step) % Waypoints.COLORS.length];
			module.save();
		});

		TextInput input = names.computeIfAbsent(p, point -> {
			TextInput t = new TextInput(32);
			t.text = point.name;
			t.onCommit(text -> {
				String clean = text.trim();
				point.name = clean.isEmpty() ? point.name : clean;
				t.text = point.name;
				module.save();
			});
			return t;
		});
		boolean focused = screen.focused == input;
		if (!focused) {
			input.text = p.name;
		}
		float nx = x + 52, nw = 260, nh = 30, ny = y + 7;
		boolean hoverName = ui.hover(nx - 8, ny, nw, nh);
		if (focused || hoverName) {
			d.round(nx - 8, ny, nw, nh, 8, focused ? Theme.SURFACE_3 : Theme.SURFACE_2);
		}
		if (focused) {
			d.ring(nx - 8, ny, nw, nh, 8, 1, Theme.accent());
		}
		input.draw(ui, nx, ny + nh / 2f, nw - 16, "Name", focused, Fonts.Style.SEMIBOLD, 14);
		ui.click(nx - 8, ny, nw, nh, () -> screen.focused = input);

		String coords = (int) Math.floor(p.x) + "  " + (int) Math.floor(p.y) + "  " + (int) Math.floor(p.z);
		String where = allWorlds ? "   " + Waypoints.worldLabel(p.world) : "";
		d.textMid(coords + where, nx, y + h - 13, Fonts.Style.MEDIUM, 11, Theme.MUTED);

		float bx = x + w - 16;
		bx -= iconButton(d, ui, "trash-2", bx, sy, Theme.DANGER, () -> {
			if (screen.focused == input) {
				screen.focused = null;
			}
			module.remove(p);
		}) + 6;
		bx -= iconButton(d, ui, p.visible ? "eye" : "eye-off", bx, sy, p.visible ? Theme.TEXT_2 : Theme.FAINT, () -> {
			p.visible = !p.visible;
			module.save();
		}) + 14;
		double dist = module.distanceTo(p);
		if (dist >= 0) {
			String text = dist >= 1000 ? String.format(Locale.ROOT, "%.1fkm", dist / 1000) : String.format(Locale.ROOT, "%.0fm", dist);
			d.textRight(text, bx, sy, Fonts.Style.SEMIBOLD, 13, Theme.accentText());
		}
		if (p.death) {
			float tw = d.width("Death", Fonts.Style.SEMIBOLD, 10) + 14;
			float tx = nx + Math.min(nw - 8, d.width(p.name, Fonts.Style.SEMIBOLD, 14) + 12);
			if (!focused) {
				d.round(tx, ny + nh / 2f - 9, tw, 18, 9, Theme.withAlpha(Theme.DANGER, 0x30));
				d.textCenter("Death", tx + tw / 2f, ny + nh / 2f, Fonts.Style.SEMIBOLD, 10, Theme.DANGER);
			}
		}
	}

	private float iconButton(Draw d, Ui ui, String icon, float right, float cy, int color, Runnable action) {
		float size = 34;
		float x = right - size, y = cy - size / 2f;
		boolean hover = ui.hover(x, y, size, size);
		if (hover) {
			d.round(x, y, size, size, 8, Theme.SURFACE_3);
		}
		d.icon(icon, x + size / 2f, cy, 16, hover ? Theme.TEXT : color);
		ui.click(x, y, size, size, action);
		return size;
	}

	private float button(Draw d, Ui ui, String icon, String label, float right, float cy, boolean primary, Runnable action) {
		float w = d.width(label, Fonts.Style.SEMIBOLD, 13) + 46, h = 36;
		float x = right - w, y = cy - h / 2f;
		boolean hover = ui.hover(x, y, w, h);
		int bg = primary ? (hover ? Theme.accentHover() : Theme.accent()) : (hover ? Theme.SURFACE_3 : Theme.SURFACE_2);
		int fg = primary ? Theme.INK : Theme.TEXT;
		d.round(x, y, w, h, 10, bg);
		d.icon(icon, x + 20, cy, 15, fg);
		d.textMid(label, x + 34, cy, Fonts.Style.SEMIBOLD, 13, fg);
		ui.click(x, y, w, h, action);
		return w;
	}

	private float chip(String label, float x, boolean active, Runnable action) {
		Ui ui = screen.ui;
		Draw d = ui.d;
		float w = d.width(label, Fonts.Style.SEMIBOLD, 13) + 28;
		float h = 30, y = 24;
		boolean hover = ui.hover(x, y, w, h);
		float t = ui.anim("wchip" + label, active ? 1f : 0f, 16f);
		d.round(x, y, w, h, h / 2f, Theme.mix(hover ? Theme.SURFACE_3 : Theme.SURFACE_2, Theme.accent(), t));
		d.textCenter(label, x + w / 2f, y + h / 2f, Fonts.Style.SEMIBOLD, 13, Theme.mix(hover ? Theme.TEXT : Theme.TEXT_3, Theme.INK, t));
		ui.click(x, y, w, h, action);
		return x + w + 9;
	}
}
