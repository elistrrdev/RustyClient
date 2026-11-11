package com.rustyclient.gui;

import com.rustyclient.RustyClient;
import com.rustyclient.core.Profiles;
import com.rustyclient.core.Theme;
import com.rustyclient.render.Draw;
import com.rustyclient.render.Fonts;
import net.minecraft.client.Minecraft;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ProfilesView {
	private enum Mode {
		DETAILS, CREATE, IMPORT
	}

	private static final float LIST_W = 300;
	private static final float GAP = 18;
	private static final float TOP = 74;
	private static final float ROW_H = 58;
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault());

	private final RustyScreen screen;
	private Mode mode = Mode.DETAILS;
	private Profiles.Profile selected;
	private float listScroll;
	private float listTarget;
	private float listContent;
	private final Map<Profiles.Profile, TextInput> names = new IdentityHashMap<>();
	private final Map<Profiles.Profile, TextInput> descriptions = new IdentityHashMap<>();
	private final Map<Profiles.Profile, TextInput> servers = new IdentityHashMap<>();
	private final TextInput newName = new TextInput(32);
	private boolean newGlobal;
	private boolean newDefaults;
	private boolean newSwitch = true;
	private Profiles.Profile armedDelete;
	private long deleteUntil;
	private Profiles.Profile armedReset;
	private long resetUntil;
	private String toast;
	private long toastUntil;
	private List<Profiles.VersionProfiles> others;

	ProfilesView(RustyScreen screen) {
		this.screen = screen;
	}

	void draw() {
		Ui ui = screen.ui;
		Draw d = ui.d;
		float x0 = RustyScreen.CONTENT_X;
		float right = RustyScreen.PANEL_W - RustyScreen.PAD_R;
		if (selected == null || !Profiles.all().contains(selected)) {
			selected = Profiles.active();
		}
		d.textMid("Profiles", x0, 39, Fonts.Style.BOLD, 18, Theme.TEXT);
		float px = x0 + d.width("Profiles", Fonts.Style.BOLD, 18) + 16;
		String version = "Minecraft " + Profiles.version();
		float pw = d.width(version, Fonts.Style.SEMIBOLD, 12) + 24;
		d.round(px, 25, pw, 28, 14, Theme.SURFACE_2);
		d.textCenter(version, px + pw / 2f, 39, Fonts.Style.SEMIBOLD, 12, Theme.TEXT_3);
		if (toast != null && System.currentTimeMillis() < toastUntil) {
			d.textMid(toast, px + pw + 14, 39, Fonts.Style.SEMIBOLD, 12, Theme.accentText());
		}
		float bx = right;
		bx -= button(d, ui, "download", "Import", bx, 39, mode == Mode.IMPORT ? Style.ACTIVE : Style.NORMAL, () -> {
			if (mode == Mode.IMPORT) {
				mode = Mode.DETAILS;
			} else {
				mode = Mode.IMPORT;
				others = Profiles.otherVersions();
			}
			resetScroll();
		}) + 8;
		button(d, ui, "plus", "New profile", bx, 39, Style.PRIMARY, () -> {
			mode = Mode.CREATE;
			newName.text = "";
			newGlobal = false;
			newDefaults = false;
			newSwitch = true;
			screen.focused = newName;
			resetScroll();
		});

		drawList(x0);
		float dx = x0 + LIST_W + GAP;
		float dw = right - dx;
		screen.viewHeight = RustyScreen.PANEL_H - TOP - 1;
		screen.scroll = ui.anim("scroll", screen.scrollTarget, 18f);
		screen.clampScroll();
		d.scissor(dx - 4, TOP, dw + 8, screen.viewHeight);
		ui.clip(dx - 4, TOP, dw + 8, screen.viewHeight);
		float y = TOP + 4 - screen.scroll;
		float end = switch (mode) {
			case CREATE -> drawCreate(dx, y, dw);
			case IMPORT -> drawImport(dx, y, dw);
			default -> drawDetails(selected, dx, y, dw);
		};
		screen.contentHeight = end + screen.scroll - TOP + 12;
		ui.resetClip();
		d.endScissor();
		screen.scrollbar(dx + dw + 7, TOP + 4, screen.viewHeight - 8);
	}

	private void resetScroll() {
		screen.scrollTarget = 0;
		screen.scroll = 0;
		screen.ui.setAnim("scroll", 0);
	}

	private void select(Profiles.Profile p) {
		if (selected != p || mode != Mode.DETAILS) {
			selected = p;
			mode = Mode.DETAILS;
			resetScroll();
		}
	}

	private void notify(String text) {
		toast = text;
		toastUntil = System.currentTimeMillis() + 2600;
	}

	boolean scroll(float x, float y, double v) {
		float x0 = RustyScreen.CONTENT_X;
		if (x < x0 - 4 || x > x0 + LIST_W + 4 || y < TOP) {
			return false;
		}
		listTarget -= (float) v * 60f;
		return true;
	}

	private void drawList(float x0) {
		Ui ui = screen.ui;
		Draw d = ui.d;
		float viewH = RustyScreen.PANEL_H - TOP - 1;
		float max = Math.max(0, listContent - viewH);
		listTarget = Math.max(0, Math.min(max, listTarget));
		listScroll = ui.anim("plist", listTarget, 18f);
		d.scissor(x0 - 4, TOP, LIST_W + 8, viewH);
		ui.clip(x0 - 4, TOP, LIST_W + 8, viewH);
		float y = TOP + 4 - listScroll;
		List<Profiles.Profile> version = new ArrayList<>();
		List<Profiles.Profile> global = new ArrayList<>();
		for (Profiles.Profile p : Profiles.all()) {
			(p.scope == Profiles.Scope.GLOBAL ? global : version).add(p);
		}
		d.textMid("Only on " + Profiles.version(), x0, y + 8, Fonts.Style.SEMIBOLD, 13, Theme.TEXT_3);
		y += 21;
		for (Profiles.Profile p : version) {
			listRow(p, x0, y);
			y += ROW_H + 8;
		}
		y += 12;
		d.textMid("All versions", x0, y + 8, Fonts.Style.SEMIBOLD, 13, Theme.TEXT_3);
		y += 21;
		if (global.isEmpty()) {
			d.round(x0, y, LIST_W, 46, 12, Theme.SURFACE);
			d.textMid("No profiles for all versions yet", x0 + 14, y + 23, Fonts.Style.MEDIUM, 12, Theme.MUTED);
			y += 54;
		}
		for (Profiles.Profile p : global) {
			listRow(p, x0, y);
			y += ROW_H + 8;
		}
		listContent = y + listScroll - TOP + 8;
		ui.resetClip();
		d.endScissor();
		if (listContent > viewH + 1) {
			float h = viewH - 8;
			float bh = Math.max(30, h * viewH / listContent);
			float by = TOP + 4 + (h - bh) * (listScroll / Math.max(1, listContent - viewH));
			d.round(x0 + LIST_W + 5, by, 4, bh, 2, Theme.SURFACE_3);
		}
	}

	private void listRow(Profiles.Profile p, float x, float y) {
		Ui ui = screen.ui;
		Draw d = ui.d;
		boolean hover = ui.hover(x, y, LIST_W, ROW_H);
		boolean sel = p == selected && mode == Mode.DETAILS;
		boolean active = p == Profiles.active();
		d.round(x, y, LIST_W, ROW_H, 12, sel ? Theme.SURFACE_2 : (hover ? Theme.SURFACE_2 : Theme.SURFACE));
		if (sel) {
			d.ring(x, y, LIST_W, ROW_H, 12, 1, Theme.accent());
		}
		tile(d, p, x + 12, y + 12, 34);
		float tx = x + 58;
		float maxName = LIST_W - 58 - 14 - (p.locked ? 20 : 0) - (p.isDefault() ? 62 : 0);
		d.textMid(fit(d, p.name, Fonts.Style.SEMIBOLD, 14, maxName), tx, y + 21, Fonts.Style.SEMIBOLD, 14, Theme.TEXT);
		String sub;
		int subColor;
		if (active) {
			sub = Profiles.unsaved() ? "Active, changes not saved" : "Active";
			subColor = Theme.accentText();
		} else {
			int n = p.enabledModules();
			sub = n == 1 ? "1 module on" : n + " modules on";
			subColor = Theme.MUTED;
		}
		d.textMid(sub, tx, y + 39, Fonts.Style.MEDIUM, 11, subColor);
		float rx = x + LIST_W - 14;
		if (p.isDefault()) {
			float tw = d.width("Default", Fonts.Style.SEMIBOLD, 10) + 16;
			d.round(rx - tw, y + 12, tw, 18, 9, Theme.SURFACE_3);
			d.textCenter("Default", rx - tw / 2f, y + 21, Fonts.Style.SEMIBOLD, 10, Theme.TEXT_3);
			rx -= tw + 6;
		}
		if (p.locked) {
			d.icon("lock", rx - 7, y + 21, 12, Theme.MUTED);
		}
		if (active) {
			d.circle(x + LIST_W - 20, y + 39, 3.5f, Theme.accent());
		}
		ui.click(x, y, LIST_W, ROW_H, b -> {
			if (b == 0) {
				select(p);
			} else if (b == 1 && p != Profiles.active()) {
				Profiles.activate(p);
				select(p);
			}
		});
	}

	private void tile(Draw d, Profiles.Profile p, float x, float y, float size) {
		d.round(x, y, size, size, size * 0.26f, Theme.withAlpha(p.color, 0x2E));
		d.icon(p.icon, x + size / 2f, y + size / 2f, size * 0.47f, p.color);
	}

	private float drawDetails(Profiles.Profile p, float x, float y, float w) {
		if (p == null) {
			return y;
		}
		Ui ui = screen.ui;
		Draw d = ui.d;
		boolean active = p == Profiles.active();
		d.round(x, y, w, 96, 14, Theme.SURFACE);
		tile(d, p, x + 18, y + 20, 56);
		float iconRight = x + w - 14;
		if (!p.isDefault()) {
			boolean armed = armedDelete == p && System.currentTimeMillis() < deleteUntil;
			iconRight -= iconButton(d, ui, "trash-2", iconRight, y + 34, armed ? Theme.DANGER : Theme.TEXT_3, armed, () -> {
				if (armedDelete == p && System.currentTimeMillis() < deleteUntil) {
					armedDelete = null;
					String name = p.name;
					Profiles.delete(p);
					selected = Profiles.active();
					notify("Deleted " + name);
				} else {
					armedDelete = p;
					deleteUntil = System.currentTimeMillis() + 3000;
					notify("Click delete again to remove " + p.name);
				}
			}) + 4;
		}
		iconRight -= iconButton(d, ui, "upload", iconRight, y + 34, Theme.TEXT_3, false, () -> {
			String text = Profiles.export(p);
			if (!text.isEmpty()) {
				Minecraft.getInstance().keyboardHandler.setClipboard(text);
				notify("Copied " + p.name + " to the clipboard");
			}
		}) + 4;
		iconRight -= iconButton(d, ui, "copy", iconRight, y + 34, Theme.TEXT_3, false, () -> {
			Profiles.Profile copy = Profiles.duplicate(p);
			select(copy);
			notify("Made " + copy.name);
		});
		float nx = x + 92;
		float nw = iconRight - nx - 16;
		TextInput name = names.computeIfAbsent(p, k -> {
			TextInput t = new TextInput(32);
			t.onCommit(text -> {
				String clean = text.trim();
				if (!clean.isEmpty()) {
					k.name = clean;
					Profiles.write(k);
				}
				t.text = k.name;
			});
			return t;
		});
		boolean nameFocused = screen.focused == name;
		if (!nameFocused) {
			name.text = p.name;
		}
		editable(d, ui, name, nx, y + 18, nw, 32, "Profile name", Fonts.Style.BOLD, 18);
		TextInput desc = descriptions.computeIfAbsent(p, k -> {
			TextInput t = new TextInput(64);
			t.onCommit(text -> {
				k.description = text.trim();
				Profiles.write(k);
				t.text = k.description;
			});
			return t;
		});
		if (screen.focused != desc) {
			desc.text = p.description;
		}
		editable(d, ui, desc, nx, y + 52, nw, 26, "Add a description", Fonts.Style.MEDIUM, 12);
		y += 108;

		d.round(x, y, w, 56, 14, Theme.SURFACE);
		float ay = y + 28;
		if (active) {
			if (Profiles.unsaved()) {
				d.icon("triangle-alert", x + 24, ay, 15, 0xFFF2C14E);
				d.textMid(p.locked ? "Locked, your changes are not saved to it" : "Changes not saved to this profile", x + 42, ay, Fonts.Style.SEMIBOLD, 13, Theme.TEXT_2);
				float r = x + w - 12;
				r -= button(d, ui, "rotate-ccw", "Revert", r, ay, Style.NORMAL, () -> {
					Profiles.revert();
					notify("Reverted to " + p.name);
				}) + 8;
				if (!p.locked) {
					button(d, ui, "save", "Save to profile", r, ay, Style.PRIMARY, () -> {
						Profiles.saveCurrent(p);
						notify("Saved to " + p.name);
					});
				}
			} else {
				d.icon("check", x + 24, ay, 15, Theme.accentText());
				String text = p.locked ? "Active and locked, changes are not saved to it" : (p.autoSave ? "Active, changes are saved to it automatically" : "Active, changes are saved when you press Save");
				d.textMid(text, x + 42, ay, Fonts.Style.SEMIBOLD, 13, Theme.TEXT_2);
				if (!p.autoSave && !p.locked) {
					button(d, ui, "save", "Save", x + w - 12, ay, Style.NORMAL, () -> {
						Profiles.saveCurrent(p);
						notify("Saved to " + p.name);
					});
				}
			}
		} else {
			float r = x + 12;
			float uw = button(d, ui, "check", "Use this profile", r + measure(d, "Use this profile"), ay, Style.PRIMARY, () -> {
				Profiles.activate(p);
				notify("Switched to " + p.name);
			});
			r += uw + 8;
			if (!p.locked) {
				button(d, ui, "save", "Save current settings here", r + measure(d, "Save current settings here"), ay, Style.NORMAL, () -> {
					Profiles.saveCurrent(p);
					notify("Saved current settings to " + p.name);
				});
			}
		}
		y += 68;

		float colW = (w - 10) / 2f;
		float lx = x, rx = x + colW + 10;
		float ly = y, ry = y;

		ly = section(d, "Look", lx, ly);
		float sx = lx;
		for (int color : Profiles.COLORS) {
			boolean on = (p.color & 0xFFFFFF) == (color & 0xFFFFFF);
			boolean hover = ui.hover(sx, ly, 30, 30);
			if (on) {
				d.ring(sx - 1, ly - 1, 32, 32, 9, 2, color);
				d.round(sx + 3, ly + 3, 24, 24, 6, color);
			} else {
				d.round(sx, ly, 30, 30, 8, hover ? Theme.mix(color, 0xFFFFFFFF, 0.15f) : color);
			}
			ui.click(sx, ly, 30, 30, () -> {
				p.color = color;
				Profiles.write(p);
			});
			sx += 36;
		}
		ly += 42;
		float cell = 34, cgap = 6;
		int perRow = Math.max(1, (int) ((colW + cgap) / (cell + cgap)));
		for (int i = 0; i < Profiles.ICONS.length; i++) {
			String icon = Profiles.ICONS[i];
			float cx = lx + (i % perRow) * (cell + cgap);
			float cy = ly + (i / perRow) * (cell + cgap);
			boolean on = icon.equals(p.icon);
			boolean hover = ui.hover(cx, cy, cell, cell);
			d.round(cx, cy, cell, cell, 8, on ? Theme.withAlpha(p.color, 0x33) : (hover ? Theme.SURFACE_3 : Theme.SURFACE));
			if (on) {
				d.ring(cx, cy, cell, cell, 8, 1, p.color);
			}
			d.icon(icon, cx + cell / 2f, cy + cell / 2f, 16, on ? p.color : (hover ? Theme.TEXT : Theme.MUTED));
			ui.click(cx, cy, cell, cell, () -> {
				p.icon = icon;
				Profiles.write(p);
			});
		}
		ly += ((Profiles.ICONS.length + perRow - 1) / perRow) * (cell + cgap) + 15;

		ly = section(d, "Behaviour", lx, ly);
		toggleRow(d, ui, "Save changes automatically", p.autoSave, lx, ly, colW, () -> {
			p.autoSave = !p.autoSave;
			Profiles.write(p);
			if (p == Profiles.active() && p.autoSave && !p.locked) {
				Profiles.saveCurrent(p);
			}
		});
		ly += 51;
		toggleRow(d, ui, "Lock profile", p.locked, lx, ly, colW, () -> {
			p.locked = !p.locked;
			Profiles.write(p);
		});
		ly += 51;
		d.textMid("Available on", lx, ly + 13, Fonts.Style.SEMIBOLD, 13, Theme.TEXT_2);
		if (p.isDefault()) {
			d.textMid("The default profile always belongs to " + Profiles.version(), lx, ly + 37, Fonts.Style.MEDIUM, 12, Theme.MUTED);
			ly += 60;
		} else {
			float cx = lx;
			cx = choiceChip(d, ui, "Only " + Profiles.version(), cx, ly + 29, p.scope == Profiles.Scope.VERSION, () -> Profiles.setScope(p, Profiles.Scope.VERSION));
			choiceChip(d, ui, "All versions", cx, ly + 29, p.scope == Profiles.Scope.GLOBAL, () -> Profiles.setScope(p, Profiles.Scope.GLOBAL));
			ly += 70;
		}

		ry = section(d, "Included in this profile", rx, ry);
		for (Profiles.Part part : Profiles.Part.values()) {
			toggleRow(d, ui, part.label, p.parts.contains(part), rx, ry, colW, () -> {
				if (p.parts.contains(part)) {
					if (p.parts.size() > 1) {
						p.parts.remove(part);
					}
				} else {
					p.parts.add(part);
				}
				Profiles.write(p);
			});
			ry += 51;
		}
		ry = hint(d, "Parts that are off stay as they are when you switch to this profile.", rx, ry, colW) + 15;

		ry = section(d, "Auto switch", rx, ry);
		TextInput serverInput = servers.computeIfAbsent(p, k -> {
			TextInput t = new TextInput(160);
			t.onCommit(text -> {
				k.servers = text.trim();
				Profiles.write(k);
				t.text = k.servers;
			});
			return t;
		});
		if (screen.focused != serverInput) {
			serverInput.text = p.servers;
		}
		d.round(rx, ry, colW, 42, 10, Theme.SURFACE);
		d.textMid("Servers", rx + 12, ry + 21, Fonts.Style.SEMIBOLD, 13, Theme.TEXT_2);
		float fx = rx + 84, fw = colW - 96;
		boolean sf = screen.focused == serverInput;
		d.round(fx, ry + 7, fw, 28, 7, Theme.SURFACE_3);
		if (sf) {
			d.ring(fx, ry + 7, fw, 28, 7, 1, Theme.accent());
		}
		serverInput.draw(ui, fx + 10, ry + 21, fw - 20, "hypixel.net, play.example.com", sf, Fonts.Style.MEDIUM, 12);
		ui.click(fx, ry + 7, fw, 28, () -> screen.focused = serverInput);
		ry += 51;
		toggleRow(d, ui, "Use in singleplayer", p.singleplayer, rx, ry, colW, () -> {
			p.singleplayer = !p.singleplayer;
			Profiles.write(p);
		});
		ry += 51;
		toggleRow(d, ui, "Switch back when leaving", p.switchBack, rx, ry, colW, () -> {
			p.switchBack = !p.switchBack;
			Profiles.write(p);
		});
		ry += 51;
		ry = hint(d, "Joining one of these servers switches to this profile. Subdomains count too.", rx, ry, colW) + 15;

		y = Math.max(ly, ry);
		y = section(d, "Info", x, y);
		String[][] info = {
			{"Made for", Profiles.versionLabel(p.createdFor)},
			{"Created", DATE.format(Instant.ofEpochMilli(p.created))},
			{"Last saved", DATE.format(Instant.ofEpochMilli(p.updated))},
			{"Modules on", String.valueOf(p == Profiles.active() ? countEnabled() : p.enabledModules())}
		};
		for (int i = 0; i < info.length; i++) {
			float ix = x + (i % 2) * (colW + 10);
			float iy = y + (i / 2) * 51;
			d.round(ix, iy, colW, 42, 10, Theme.SURFACE);
			d.textMid(info[i][0], ix + 12, iy + 21, Fonts.Style.SEMIBOLD, 13, Theme.TEXT_2);
			d.textRight(info[i][1], ix + colW - 12, iy + 21, Fonts.Style.MEDIUM, 12, Theme.MUTED);
		}
		y += 102;
		actionRow(d, ui, "Profiles folder", "Open", x, y, colW, false, () -> RustyClient.openFolder(p.file().getParent()));
		if (!p.locked) {
			boolean armed = armedReset == p && System.currentTimeMillis() < resetUntil;
			actionRow(d, ui, "Reset to default settings", armed ? "Confirm" : "Reset", x + colW + 10, y, colW, true, () -> {
				if (armedReset == p && System.currentTimeMillis() < resetUntil) {
					armedReset = null;
					Profiles.resetToDefaults(p);
					notify("Reset " + p.name + " to default settings");
				} else {
					armedReset = p;
					resetUntil = System.currentTimeMillis() + 3000;
				}
			});
		}
		y += 51;
		return y;
	}

	private int countEnabled() {
		int n = 0;
		for (com.rustyclient.core.Module module : com.rustyclient.core.Modules.all()) {
			if (!module.hidden() && module.enabled()) {
				n++;
			}
		}
		return n;
	}

	private float drawCreate(float x, float y, float w) {
		Ui ui = screen.ui;
		Draw d = ui.d;
		d.textMid("New profile", x, y + 14, Fonts.Style.BOLD, 16, Theme.TEXT);
		y += 36;
		d.round(x, y, w, 42, 10, Theme.SURFACE);
		d.textMid("Name", x + 12, y + 21, Fonts.Style.SEMIBOLD, 13, Theme.TEXT_2);
		float fx = x + 84, fw = w - 96;
		boolean focused = screen.focused == newName;
		d.round(fx, y + 7, fw, 28, 7, Theme.SURFACE_3);
		if (focused) {
			d.ring(fx, y + 7, fw, 28, 7, 1, Theme.accent());
		}
		newName.draw(ui, fx + 10, y + 21, fw - 20, "New profile", focused, Fonts.Style.MEDIUM, 12.5f);
		ui.click(fx, y + 7, fw, 28, () -> screen.focused = newName);
		y += 60;
		d.textMid("Available on", x, y + 13, Fonts.Style.SEMIBOLD, 13, Theme.TEXT_2);
		float cx = choiceChip(d, ui, "Only " + Profiles.version(), x, y + 29, !newGlobal, () -> newGlobal = false);
		choiceChip(d, ui, "All versions", cx, y + 29, newGlobal, () -> newGlobal = true);
		y += 70;
		y = hint(d, newGlobal ? "Shows up on 26.1, 26.2 and 26.3. Changes made on any version are saved to it." : "Only shows up on " + Profiles.versionLabel(Profiles.version()) + ".", x, y - 6, w) + 14;
		d.textMid("Start from", x, y + 13, Fonts.Style.SEMIBOLD, 13, Theme.TEXT_2);
		cx = choiceChip(d, ui, "Current settings", x, y + 29, !newDefaults, () -> newDefaults = false);
		choiceChip(d, ui, "Default settings", cx, y + 29, newDefaults, () -> newDefaults = true);
		y += 76;
		toggleRow(d, ui, "Switch to it now", newSwitch, x, y, (w - 10) / 2f, () -> newSwitch = !newSwitch);
		y += 63;
		float bx = x + measure(d, "Create profile");
		button(d, ui, "plus", "Create profile", bx, y + 18, Style.PRIMARY, this::create);
		button(d, ui, "x", "Cancel", bx + 8 + measure(d, "Cancel"), y + 18, Style.NORMAL, () -> {
			mode = Mode.DETAILS;
			if (screen.focused == newName) {
				screen.focused = null;
			}
		});
		return y + 48;
	}

	private void create() {
		if (screen.focused == newName) {
			screen.focused = null;
		}
		Profiles.Profile p = Profiles.create(newName.text, newGlobal ? Profiles.Scope.GLOBAL : Profiles.Scope.VERSION, newDefaults);
		if (newSwitch) {
			Profiles.activate(p);
		}
		select(p);
		notify("Created " + p.name);
	}

	private float drawImport(float x, float y, float w) {
		Ui ui = screen.ui;
		Draw d = ui.d;
		d.textMid("Import a profile", x, y + 14, Fonts.Style.BOLD, 16, Theme.TEXT);
		y += 36;
		d.round(x, y, w, 56, 14, Theme.SURFACE);
		d.icon("clipboard-paste", x + 24, y + 28, 15, Theme.TEXT_3);
		d.textMid("Paste a profile that was copied with Export", x + 42, y + 28, Fonts.Style.SEMIBOLD, 13, Theme.TEXT_2);
		button(d, ui, "clipboard-paste", "Paste", x + w - 12, y + 28, Style.PRIMARY, () -> {
			Profiles.Profile p = Profiles.importText(Minecraft.getInstance().keyboardHandler.getClipboard());
			if (p == null) {
				notify("The clipboard holds no Rusty Client profile");
			} else {
				select(p);
				notify("Imported " + p.name);
			}
		});
		y += 70;
		if (others == null) {
			others = Profiles.otherVersions();
		}
		if (others.isEmpty()) {
			hint(d, "Profiles from other versions can only be imported when Rusty Client runs from the launcher.", x, y, w);
			return y + 30;
		}
		for (Profiles.VersionProfiles group : others) {
			y = section(d, "From " + Profiles.versionLabel(group.version()), x, y);
			if (group.profiles().isEmpty()) {
				d.round(x, y, w, 46, 12, Theme.SURFACE);
				d.textMid("Nothing saved on " + group.version() + " yet. Start " + group.version() + " once to create its profile.", x + 14, y + 23, Fonts.Style.MEDIUM, 12, Theme.MUTED);
				y += 54 + 15;
				continue;
			}
			for (Profiles.Profile p : group.profiles()) {
				d.round(x, y, w, ROW_H, 12, Theme.SURFACE);
				tile(d, p, x + 12, y + 12, 34);
				d.textMid(fit(d, p.name, Fonts.Style.SEMIBOLD, 14, w - 200), x + 58, y + 21, Fonts.Style.SEMIBOLD, 14, Theme.TEXT);
				int n = p.enabledModules();
				String sub = (p.isDefault() ? "Default profile, " : "") + (n == 1 ? "1 module on" : n + " modules on");
				d.textMid(sub, x + 58, y + 39, Fonts.Style.MEDIUM, 11, Theme.MUTED);
				button(d, ui, "download", "Import", x + w - 12, y + ROW_H / 2f, Style.NORMAL, () -> {
					Profiles.Profile copy = Profiles.importProfile(p, group.version());
					select(copy);
					notify("Imported " + copy.name + " from " + group.version());
				});
				y += ROW_H + 8;
			}
			y += 15;
		}
		return y;
	}

	private enum Style {
		NORMAL, PRIMARY, ACTIVE
	}

	private float measure(Draw d, String label) {
		return d.width(label, Fonts.Style.SEMIBOLD, 13) + 46;
	}

	private float button(Draw d, Ui ui, String icon, String label, float right, float cy, Style style, Runnable action) {
		float w = measure(d, label), h = 36;
		float x = right - w, y = cy - h / 2f;
		boolean hover = ui.hover(x, y, w, h);
		int bg;
		int fg;
		if (style == Style.PRIMARY) {
			bg = hover ? Theme.accentHover() : Theme.accent();
			fg = Theme.INK;
		} else if (style == Style.ACTIVE) {
			bg = Theme.accentSoft();
			fg = Theme.accentText();
		} else {
			bg = hover ? Theme.SURFACE_3 : Theme.SURFACE_2;
			fg = Theme.TEXT;
		}
		d.round(x, y, w, h, 10, bg);
		d.icon(icon, x + 20, cy, 15, fg);
		d.textMid(label, x + 34, cy, Fonts.Style.SEMIBOLD, 13, fg);
		ui.click(x, y, w, h, action);
		return w;
	}

	private float iconButton(Draw d, Ui ui, String icon, float right, float cy, int color, boolean armed, Runnable action) {
		float size = 34;
		float x = right - size, y = cy - size / 2f;
		boolean hover = ui.hover(x, y, size, size);
		if (armed) {
			d.round(x, y, size, size, 8, Theme.withAlpha(Theme.DANGER, 0x30));
		} else if (hover) {
			d.round(x, y, size, size, 8, Theme.SURFACE_3);
		}
		d.icon(icon, x + size / 2f, cy, 16, hover && !armed ? Theme.TEXT : color);
		ui.click(x, y, size, size, action);
		return size;
	}

	private void editable(Draw d, Ui ui, TextInput input, float x, float y, float w, float h, String placeholder, Fonts.Style style, float size) {
		boolean focused = screen.focused == input;
		boolean hover = ui.hover(x - 8, y, w, h);
		if (focused || hover) {
			d.round(x - 8, y, w, h, 8, focused ? Theme.SURFACE_3 : Theme.SURFACE_2);
		}
		if (focused) {
			d.ring(x - 8, y, w, h, 8, 1, Theme.accent());
		}
		input.draw(ui, x, y + h / 2f, w - 16, placeholder, focused, style, size);
		ui.click(x - 8, y, w, h, () -> screen.focused = input);
	}

	private float section(Draw d, String name, float x, float y) {
		d.textMid(name, x, y + 8, Fonts.Style.SEMIBOLD, 13, Theme.TEXT_3);
		return y + 25;
	}

	private float hint(Draw d, String text, float x, float y, float w) {
		List<String> lines = wrap(d, text, w, Fonts.Style.MEDIUM, 11.5f);
		for (String line : lines) {
			d.textMid(line, x, y + 8, Fonts.Style.MEDIUM, 11.5f, Theme.MUTED);
			y += 16;
		}
		return y;
	}

	private List<String> wrap(Draw d, String text, float w, Fonts.Style style, float size) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String next = line.isEmpty() ? word : line + " " + word;
			if (!line.isEmpty() && d.width(next, style, size) > w) {
				lines.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(next);
			}
		}
		if (!line.isEmpty()) {
			lines.add(line.toString());
		}
		return lines;
	}

	private String fit(Draw d, String text, Fonts.Style style, float size, float max) {
		if (d.width(text, style, size) <= max) {
			return text;
		}
		String cut = text;
		while (cut.length() > 1 && d.width(cut + "...", style, size) > max) {
			cut = cut.substring(0, cut.length() - 1);
		}
		return cut + "...";
	}

	private void toggleRow(Draw d, Ui ui, String label, boolean on, float x, float y, float w, Runnable action) {
		boolean hover = ui.hover(x, y, w, 42);
		d.round(x, y, w, 42, 10, hover ? Theme.SURFACE_2 : Theme.SURFACE);
		d.textMid(label, x + 12, y + 21, Fonts.Style.SEMIBOLD, 13, Theme.TEXT_2);
		ui.toggle("pt" + label + System.identityHashCode(selected), x + w - 52, y + 10, on);
		ui.click(x, y, w, 42, action);
	}

	private float choiceChip(Draw d, Ui ui, String label, float x, float y, boolean active, Runnable action) {
		float w = d.width(label, Fonts.Style.SEMIBOLD, 12.5f) + 30, h = 32;
		boolean hover = ui.hover(x, y, w, h);
		float t = ui.anim("pc" + label + System.identityHashCode(selected), active ? 1f : 0f, 18f);
		d.round(x, y, w, h, 8, Theme.mix(hover ? Theme.SURFACE_3 : Theme.SURFACE_2, Theme.accent(), t));
		d.textCenter(label, x + w / 2f, y + h / 2f, Fonts.Style.SEMIBOLD, 12.5f, Theme.mix(hover ? Theme.TEXT : Theme.MUTED, Theme.INK, t));
		ui.click(x, y, w, h, action);
		return x + w + 6;
	}

	private void actionRow(Draw d, Ui ui, String label, String button, float x, float y, float w, boolean danger, Runnable action) {
		d.round(x, y, w, 42, 10, Theme.SURFACE);
		d.textMid(label, x + 12, y + 21, Fonts.Style.SEMIBOLD, 13, Theme.TEXT_2);
		float bw = d.width(button, Fonts.Style.SEMIBOLD, 12) + 26;
		float bx = x + w - 10 - bw;
		boolean hover = ui.hover(bx, y + 8, bw, 26);
		int bg = danger ? (hover ? Theme.mix(Theme.DANGER, 0xFFFFFFFF, 0.14f) : Theme.DANGER) : (hover ? Theme.accentHover() : Theme.accent());
		d.round(bx, y + 8, bw, 26, 7, bg);
		d.textCenter(button, bx + bw / 2f, y + 21, Fonts.Style.SEMIBOLD, 12, danger ? 0xFFFFFFFF : Theme.INK);
		ui.click(bx, y + 8, bw, 26, action);
	}
}
