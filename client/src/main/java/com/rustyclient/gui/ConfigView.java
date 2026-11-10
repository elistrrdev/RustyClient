package com.rustyclient.gui;

import com.rustyclient.core.Config;
import com.rustyclient.core.HudModule;
import com.rustyclient.core.Module;
import com.rustyclient.core.Theme;
import com.rustyclient.core.setting.BoolSetting;
import com.rustyclient.core.setting.ChoiceSetting;
import com.rustyclient.core.setting.ColorSetting;
import com.rustyclient.core.setting.KeySetting;
import com.rustyclient.core.setting.NumberSetting;
import com.rustyclient.core.setting.Setting;
import com.rustyclient.core.setting.TextSetting;
import com.rustyclient.module.ClientSettings;
import com.rustyclient.platform.Compat;
import com.rustyclient.render.Draw;
import com.rustyclient.render.Fonts;
import com.rustyclient.RustyClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.input.KeyEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ConfigView {
	private static final float COL_GAP = 10;
	private static final int[] SWATCHES = {0xFFF2EEEA, 0xFF1AB0DD, 0xFFF2C14E, 0xFF6CCB8C, 0xFFF07EA8};

	private final RustyScreen screen;
	private final Map<TextSetting, TextInput> textInputs = new HashMap<>();
	private final ColorPicker picker = new ColorPicker();

	ConfigView(RustyScreen screen) {
		this.screen = screen;
	}

	void closePopups() {
		picker.close();
	}

	void draw(Module module) {
		if (module == null) {
			screen.switchPage(RustyScreen.Page.MODULES);
			return;
		}
		Ui ui = screen.ui;
		Draw d = ui.d;
		boolean settingsPage = module instanceof ClientSettings;
		float x0 = RustyScreen.CONTENT_X;
		float titleX = x0;
		if (!settingsPage) {
			boolean hoverBack = ui.hover(x0, 22, 34, 34);
			d.round(x0, 22, 34, 34, 8, hoverBack ? Theme.SURFACE_3 : Theme.SURFACE);
			d.icon("arrow-left", x0 + 17, 39, 16, hoverBack ? Theme.TEXT : Theme.TEXT_3);
			ui.click(x0, 22, 34, 34, () -> screen.switchPage(RustyScreen.Page.MODULES));
			titleX = x0 + 46;
		}
		d.textMid(settingsPage ? "Settings" : module.name, titleX, 39, Fonts.Style.BOLD, 18, Theme.TEXT);
		if (!settingsPage) {
			float tw = d.width(module.name, Fonts.Style.BOLD, 18);
			String state = module.enabled() ? "Enabled" : "Disabled";
			float pw = d.width(state, Fonts.Style.SEMIBOLD, 11) + 20;
			float px = titleX + tw + 12;
			boolean hoverPill = ui.hover(px, 28, pw, 22);
			int bg = module.enabled() ? Theme.accentSoft() : (hoverPill ? Theme.SURFACE_3 : Theme.SURFACE_2);
			d.round(px, 28, pw, 22, 11, bg);
			d.textCenter(state, px + pw / 2f, 39, Fonts.Style.SEMIBOLD, 11, module.enabled() ? Theme.accentText() : Theme.MUTED);
			ui.click(px, 28, pw, 22, module::toggle);
			if (module instanceof HudModule hud) {
				String label = "Reset position";
				float rw = d.width(label, Fonts.Style.SEMIBOLD, 12) + 40;
				float rx = RustyScreen.PANEL_W - RustyScreen.PAD_R - rw;
				boolean hoverReset = ui.hover(rx, 22, rw, 34);
				d.round(rx, 22, rw, 34, 8, hoverReset ? Theme.SURFACE_3 : Theme.SURFACE);
				d.icon("rotate-ccw", rx + 17, 39, 13, Theme.TEXT_3);
				d.textMid(label, rx + 30, 39, Fonts.Style.SEMIBOLD, 12, Theme.TEXT_2);
				ui.click(rx, 22, rw, 34, hud::resetPosition);
			}
		}

		float top = 74;
		float viewH = RustyScreen.PANEL_H - top - 1;
		float fullW = RustyScreen.PANEL_W - x0 - RustyScreen.PAD_R;
		float colW = (fullW - COL_GAP) / 2f;
		screen.viewHeight = viewH;
		screen.scroll = ui.anim("scroll", screen.scrollTarget, 18f);
		screen.clampScroll();
		d.scissor(x0 - 4, top, fullW + 8, viewH);
		ui.clip(x0 - 4, top, fullW + 8, viewH);
		float y = top + 8 - screen.scroll;

		Map<String, List<Setting<?>>> sections = new LinkedHashMap<>();
		for (Setting<?> setting : module.settings) {
			if (setting.visible()) {
				sections.computeIfAbsent(setting.section, k -> new ArrayList<>()).add(setting);
			}
		}
		List<Setting<?>> general = sections.remove(Module.GENERAL);
		if (general != null) {
			List<Setting<?>> flow = new ArrayList<>();
			List<Setting<?>> rest = new ArrayList<>();
			for (Setting<?> s : general) {
				(s instanceof BoolSetting ? flow : rest).add(s);
			}
			sectionLabel(d, Module.GENERAL, x0, y);
			y += 17;
			for (int i = 0; i < flow.size(); i++) {
				float rx = x0 + (i % 2) * (colW + COL_GAP);
				float ry = y + (i / 2) * 51;
				boolRow((BoolSetting) flow.get(i), rx, ry, colW);
			}
			y += ((flow.size() + 1) / 2) * 51;
			if (!rest.isEmpty()) {
				sections.put("Options", rest);
			}
			y += 21;
		}
		float leftY = y, rightY = y;
		for (Map.Entry<String, List<Setting<?>>> entry : sections.entrySet()) {
			String name = entry.getKey();
			boolean left;
			if (name.equals(Module.APPEARANCE)) {
				left = true;
			} else if (name.equals(Module.KEYBIND)) {
				left = false;
			} else {
				left = leftY <= rightY;
			}
			float cx = left ? x0 : x0 + colW + COL_GAP;
			float cy = left ? leftY : rightY;
			sectionLabel(d, name, cx, cy);
			cy += 17;
			for (Setting<?> setting : entry.getValue()) {
				cy += item(setting, cx, cy, colW);
			}
			cy += 21;
			if (left) {
				leftY = cy;
			} else {
				rightY = cy;
			}
		}
		float end = Math.max(leftY, rightY);
		if (module instanceof ClientSettings) {
			end = actions(x0, end, colW, fullW);
		}
		screen.contentHeight = end + screen.scroll - top + 4;
		ui.resetClip();
		d.endScissor();
		screen.scrollbar(x0 + fullW + 7, top + 4, viewH - 8);
	}

	private float actions(float x0, float y, float colW, float fullW) {
		Ui ui = screen.ui;
		Draw d = ui.d;
		sectionLabel(d, "Layout and files", x0, y);
		y += 17;
		actionRow("HUD layout", "Edit", x0, y, colW, () -> Compat.setScreen(new HudEditorScreen(screen)));
		boolean pending = RustyScreen.resetPending();
		actionRow(pending ? "Click again to reset" : "Reset HUD layout", pending ? "Confirm" : "Reset", x0 + colW + COL_GAP, y, colW, pending, RustyScreen::pressReset);
		y += 51;
		actionRow("Config folder", "Open", x0, y, colW, () -> RustyClient.openFolder(Config.file().getParent()));
		actionRow("Screenshots folder", "Open", x0 + colW + COL_GAP, y, colW, () -> RustyClient.openFolder(FabricLoader.getInstance().getGameDir().resolve("screenshots")));
		y += 51;
		return y + 12;
	}

	private void actionRow(String label, String button, float x, float y, float w, Runnable action) {
		actionRow(label, button, x, y, w, false, action);
	}

	private void actionRow(String label, String button, float x, float y, float w, boolean danger, Runnable action) {
		Ui ui = screen.ui;
		Draw d = ui.d;
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

	private void sectionLabel(Draw d, String name, float x, float y) {
		d.textMid(name, x, y, Fonts.Style.SEMIBOLD, 13, Theme.TEXT_3);
	}

	private float item(Setting<?> setting, float x, float y, float w) {
		if (setting instanceof BoolSetting b) {
			boolRow(b, x, y, w);
			return 51;
		}
		if (setting instanceof NumberSetting n) {
			slider(n, x, y, w);
			return 60;
		}
		if (setting instanceof ChoiceSetting c) {
			return choice(c, x, y, w);
		}
		if (setting instanceof ColorSetting c) {
			colors(c, x, y, w);
			return 70;
		}
		if (setting instanceof KeySetting k) {
			keyRow(k, x, y, w);
			return 51;
		}
		if (setting instanceof TextSetting t) {
			textRow(t, x, y, w);
			return 51;
		}
		return 0;
	}

	private void boolRow(BoolSetting setting, float x, float y, float w) {
		Ui ui = screen.ui;
		Draw d = ui.d;
		boolean hover = ui.hover(x, y, w, 42);
		d.round(x, y, w, 42, 10, hover ? Theme.SURFACE_2 : Theme.SURFACE);
		d.textMid(setting.name, x + 12, y + 21, Fonts.Style.SEMIBOLD, 13, Theme.TEXT_2);
		ui.toggle("tg" + System.identityHashCode(setting), x + w - 52, y + 10, setting.on());
		ui.click(x, y, w, 42, setting::toggle);
	}

	private void slider(NumberSetting setting, float x, float y, float w) {
		Ui ui = screen.ui;
		Draw d = ui.d;
		d.textMid(setting.name, x, y + 13, Fonts.Style.SEMIBOLD, 13, Theme.TEXT_2);
		d.textRight(setting.text(), x + w - 6, y + 13, Fonts.Style.MEDIUM, 11, Theme.accentText());
		float ty = y + 40;
		float tx = x, tw = w - 6;
		String key = "sl" + System.identityHashCode(setting);
		float frac = ui.anim(key, (float) setting.fraction(), 22f);
		float kr = 6.5f;
		float kx = tx + kr + (tw - kr * 2) * frac;
		d.round(tx, ty - 2.5f, tw, 5, 2.5f, Theme.SURFACE_3);
		d.round(tx, ty - 2.5f, Math.max(5, kx - tx), 5, 2.5f, Theme.accent());
		boolean hover = ui.hover(tx - 8, ty - 12, tw + 16, 24);
		boolean active = hover || ui.activeDrag != null;
		d.knob(kx, ty, active ? 7f : kr, active, Theme.accent());
		ui.drag(tx - 8, ty - 12, tw + 16, 24, (mx, my) -> setting.setFraction((mx - tx - kr) / (tw - kr * 2)));
	}

	private float choice(ChoiceSetting setting, float x, float y, float w) {
		Ui ui = screen.ui;
		Draw d = ui.d;
		d.textMid(setting.name, x, y + 13, Fonts.Style.SEMIBOLD, 13, Theme.TEXT_2);
		float bx = x, by = y + 29;
		float rowH = 32;
		float lines = 1;
		for (String option : setting.options) {
			float bw = d.width(option, Fonts.Style.SEMIBOLD, 12.5f) + 30;
			if (bx + bw > x + w && bx > x) {
				bx = x;
				by += rowH + 6;
				lines++;
			}
			boolean active = setting.is(option);
			boolean hover = ui.hover(bx, by, bw, rowH);
			float t = ui.anim("ch" + System.identityHashCode(setting) + option, active ? 1f : 0f, 18f);
			d.round(bx, by, bw, rowH, 8, Theme.mix(hover ? Theme.SURFACE_3 : Theme.SURFACE_2, Theme.accent(), t));
			d.textCenter(option, bx + bw / 2f, by + rowH / 2f, Fonts.Style.SEMIBOLD, 12.5f, Theme.mix(hover ? Theme.TEXT : Theme.MUTED, Theme.INK, t));
			String value = option;
			ui.click(bx, by, bw, rowH, () -> setting.set(value));
			bx += bw + 6;
		}
		return 70 + (lines - 1) * (rowH + 6);
	}

	private void colors(ColorSetting setting, float x, float y, float w) {
		Ui ui = screen.ui;
		Draw d = ui.d;
		d.textMid(setting.name, x, y + 13, Fonts.Style.SEMIBOLD, 13, Theme.TEXT_2);
		float sx = x, sy = y + 29;
		int current = setting.argb() & 0xFFFFFF;
		boolean matched = false;
		for (int swatch : SWATCHES) {
			int value = setting.alpha ? ((setting.argb() & 0xFF000000) | (swatch & 0xFFFFFF)) : swatch;
			boolean active = (swatch & 0xFFFFFF) == current;
			matched |= active;
			swatchBox(sx, sy, value | 0xFF000000, active);
			ui.click(sx, sy, 30, 30, () -> setting.set(value));
			sx += 36;
		}
		if (!matched) {
			swatchBox(sx, sy, setting.argb() | 0xFF000000, true);
			sx += 36;
		}
		boolean hover = ui.hover(sx, sy, 30, 30);
		boolean open = picker.target == setting;
		d.round(sx, sy, 30, 30, 8, open ? Theme.SURFACE_3 : (hover ? Theme.SURFACE_3 : Theme.SURFACE_2));
		d.icon("pipette", sx + 15, sy + 15, 14, open ? Theme.accentText() : Theme.TEXT_3);
		float anchorX = sx, anchorY = sy + 36;
		ui.click(sx, sy, 30, 30, () -> {
			if (picker.target == setting) {
				picker.close();
			} else {
				picker.open(setting, anchorX, anchorY);
			}
		});
		String hex = Theme.hex(setting.argb());
		d.textMid(hex, sx + 42, sy + 15, Fonts.Style.MEDIUM, 11, Theme.MUTED);
	}

	private void swatchBox(float x, float y, int color, boolean active) {
		Draw d = screen.ui.d;
		if (active) {
			d.ring(x - 1, y - 1, 32, 32, 9, 2, Theme.accent());
			d.round(x + 3, y + 3, 24, 24, 6, color);
		} else {
			d.round(x, y, 30, 30, 8, color);
		}
	}

	private void keyRow(KeySetting setting, float x, float y, float w) {
		Ui ui = screen.ui;
		Draw d = ui.d;
		boolean hover = ui.hover(x, y, w, 42);
		d.round(x, y, w, 42, 10, hover ? Theme.SURFACE_2 : Theme.SURFACE);
		d.textMid(setting.name, x + 12, y + 21, Fonts.Style.SEMIBOLD, 13, Theme.TEXT_2);
		boolean listening = screen.listening == setting;
		String label = listening ? "PRESS A KEY" : Widgets.keyLabel(setting.get());
		Widgets.keyChip(ui, label, x + w - 12, y + 21, listening);
		ui.click(x, y, w, 42, b -> {
			if (b == 0) {
				screen.listening = setting;
			} else if (b == 1) {
				setting.set(KeySetting.NONE);
			}
		});
	}

	private void textRow(TextSetting setting, float x, float y, float w) {
		Ui ui = screen.ui;
		Draw d = ui.d;
		TextInput input = textInputs.computeIfAbsent(setting, s -> {
			TextInput t = new TextInput(s.maxLength);
			t.text = s.get();
			t.onCommit(s::set);
			t.onChange(s::set);
			return t;
		});
		if (screen.focused != input) {
			input.text = setting.get();
		}
		d.round(x, y, w, 42, 10, Theme.SURFACE);
		float lw = d.width(setting.name, Fonts.Style.SEMIBOLD, 13);
		d.textMid(setting.name, x + 12, y + 21, Fonts.Style.SEMIBOLD, 13, Theme.TEXT_2);
		float fx = x + lw + 28, fw = w - lw - 40;
		boolean focused = screen.focused == input;
		d.round(fx, y + 7, fw, 28, 7, Theme.SURFACE_3);
		if (focused) {
			d.ring(fx, y + 7, fw, 28, 7, 1, Theme.accent());
		}
		input.draw(ui, fx + 10, y + 21, fw - 20, "Type here", focused, Fonts.Style.MEDIUM, 12.5f);
		ui.click(fx, y + 7, fw, 28, () -> screen.focused = input);
	}

	void drawPopups() {
		if (picker.target != null) {
			picker.draw(screen.ui, screen);
		}
	}

	boolean popupPress(float x, float y, int button) {
		if (picker.target == null) {
			return false;
		}
		if (picker.press(screen.ui, screen, x, y, button)) {
			return true;
		}
		picker.close();
		return false;
	}

	boolean popupScroll(double v) {
		picker.close();
		return false;
	}

	boolean popupKey(KeyEvent event) {
		if (picker.target != null && event.isEscape()) {
			picker.close();
			return true;
		}
		return false;
	}
}
