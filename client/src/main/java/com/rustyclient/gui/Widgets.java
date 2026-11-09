package com.rustyclient.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.rustyclient.core.Theme;
import com.rustyclient.core.setting.KeySetting;
import com.rustyclient.render.Fonts;

import java.util.Locale;

public final class Widgets {
	private Widgets() {
	}

	public static String keyLabel(String name) {
		if (name == null || name.isEmpty() || KeySetting.NONE.equals(name)) {
			return "NONE";
		}
		if (name.startsWith("key.mouse.")) {
			String rest = name.substring("key.mouse.".length());
			return switch (rest) {
				case "left" -> "LMB";
				case "right" -> "RMB";
				case "middle" -> "MMB";
				default -> "MOUSE" + rest.toUpperCase(Locale.ROOT);
			};
		}
		String rest = name.startsWith("key.keyboard.") ? name.substring("key.keyboard.".length()) : name;
		rest = rest.replace("keypad.", "NUM");
		String side = "";
		if (rest.startsWith("left.")) {
			side = "L";
			rest = rest.substring(5);
		} else if (rest.startsWith("right.")) {
			side = "R";
			rest = rest.substring(6);
		}
		rest = switch (rest) {
			case "control" -> "CTRL";
			case "win", "super", "gui" -> "WIN";
			case "bracket" -> side.equals("L") ? "[" : "]";
			case "page.up" -> "PGUP";
			case "page.down" -> "PGDN";
			case "caps.lock" -> "CAPS";
			case "grave.accent" -> "`";
			case "backspace" -> "BKSP";
			case "escape" -> "ESC";
			case "enter" -> "ENTER";
			default -> rest;
		};
		if (rest.equals("[") || rest.equals("]")) {
			return rest;
		}
		String label = (side + rest).replace(".", "").toUpperCase(Locale.ROOT);
		if (label.length() > 9) {
			try {
				label = InputConstants.getKey(name).getDisplayName().getString().toUpperCase(Locale.ROOT);
			} catch (Exception ignored) {
			}
		}
		return label;
	}

	public static float keyChip(Ui ui, String label, float right, float cy, boolean listening) {
		float w = Math.max(44, ui.d.width(label, Fonts.Style.SEMIBOLD, 11) + 22);
		float h = 26;
		float x = right - w;
		int bg = listening ? Theme.accentSoft() : Theme.SURFACE_3;
		ui.d.round(x, cy - h / 2f, w, h, 6, bg);
		if (listening) {
			ui.d.ring(x, cy - h / 2f, w, h, 6, 1, Theme.accent());
		}
		ui.d.textCenter(label, x + w / 2f, cy, Fonts.Style.SEMIBOLD, 11, listening ? Theme.accentText() : Theme.TEXT_2);
		return w;
	}
}
