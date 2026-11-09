package com.rustyclient.gui;

import com.rustyclient.core.Theme;
import com.rustyclient.core.setting.ColorSetting;
import com.rustyclient.render.Draw;
import com.rustyclient.render.Fonts;

import java.awt.Color;

final class ColorPicker {
	private static final float W = 228;
	private static final float H = 236;

	ColorSetting target;
	private float ax;
	private float ay;
	private float hue;
	private float sat;
	private float val;
	private float alpha = 1f;
	private final TextInput hex = new TextInput(7);

	ColorPicker() {
		hex.filter(s -> s.matches("#?[0-9a-fA-F]{0,6}"));
		hex.onChange(s -> {
			Integer parsed = s.replace("#", "").length() == 6 ? Theme.parseHex(s) : null;
			if (parsed != null && target != null) {
				fromColor(parsed);
				apply();
			}
		});
		hex.onCommit(s -> {
			if (target != null) {
				hex.text = Theme.hex(target.argb());
			}
		});
	}

	void open(ColorSetting setting, float x, float y) {
		target = setting;
		ax = x;
		ay = y;
		fromColor(setting.argb());
		alpha = setting.alpha ? (setting.argb() >>> 24) / 255f : 1f;
		hex.text = Theme.hex(setting.argb());
	}

	void close() {
		target = null;
	}

	private void fromColor(int argb) {
		float[] hsb = Color.RGBtoHSB((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, null);
		if (hsb[1] > 0.001f && hsb[2] > 0.001f) {
			hue = hsb[0];
		}
		sat = hsb[1];
		val = hsb[2];
	}

	private void apply() {
		int rgb = Color.HSBtoRGB(hue, sat, val) & 0xFFFFFF;
		int a = target.alpha ? Math.round(alpha * 255) : 255;
		target.set((a << 24) | rgb);
	}

	private float[] position(RustyScreen screen) {
		float x = ax;
		float y = ay;
		if (x + W > RustyScreen.PANEL_W - 10) {
			x = RustyScreen.PANEL_W - 10 - W;
		}
		if (y + H > RustyScreen.PANEL_H - 10) {
			y = ay - 36 - H - 6;
		}
		return new float[]{x, Math.max(10, y)};
	}

	void draw(Ui ui, RustyScreen screen) {
		Draw d = ui.d;
		float[] p = position(screen);
		float x = p[0], y = p[1];
		float h = target.alpha ? H + 22 : H;
		ui.catchAll(x, y, W, h);
		d.shadow(x, y, W, h, 12, 18, 0x99000000);
		d.round(x, y, W, h, 12, Theme.SURFACE);
		d.ring(x, y, W, h, 12, 1, Theme.BORDER);
		float px = x + 12, py = y + 12, pw = W - 24, ph = 132;
		int hueColor = Color.HSBtoRGB(hue, 1f, 1f) | 0xFF000000;
		d.pixelColumnGradients(px, py, pw, ph, permille -> {
			float s = permille / 1000f;
			int top = Theme.mix(0xFFFFFFFF, hueColor, s);
			return new int[]{top, 0xFF000000};
		});
		d.cornerMask(px, py, pw, ph, 7, Theme.SURFACE);
		float kx = px + sat * pw, ky = py + (1 - val) * ph;
		d.circle(kx, ky, 7, 0xFFFFFFFF);
		d.circle(kx, ky, 5, Color.HSBtoRGB(hue, sat, val) | 0xFF000000);
		ui.drag(px - 4, py - 4, pw + 8, ph + 8, (mx, my) -> {
			sat = clamp((mx - px) / pw);
			val = 1 - clamp((my - py) / ph);
			apply();
			hex.text = Theme.hex(target.argb());
		});
		float hy = py + ph + 12, hh = 12;
		d.pixelColumnGradients(px, hy, pw, hh, permille -> {
			int c = Color.HSBtoRGB(permille / 1000f, 1f, 1f) | 0xFF000000;
			return new int[]{c, c};
		});
		d.cornerMask(px, hy, pw, hh, 6, Theme.SURFACE);
		float hx = px + hue * pw;
		d.circle(hx, hy + hh / 2f, 8, 0xFFFFFFFF);
		d.circle(hx, hy + hh / 2f, 6, hueColor);
		ui.drag(px - 4, hy - 6, pw + 8, hh + 12, (mx, my) -> {
			hue = clamp((mx - px) / pw);
			if (sat < 0.02f && val > 0.98f) {
				sat = 1f;
			}
			apply();
			hex.text = Theme.hex(target.argb());
		});
		float ry = hy + hh + 14;
		if (target.alpha) {
			d.textMid("Opacity", px, ry + 6, Fonts.Style.SEMIBOLD, 12, Theme.TEXT_3);
			d.textRight(Math.round(alpha * 100) + "%", px + pw, ry + 6, Fonts.Style.MEDIUM, 11, Theme.accentText());
			float ty = ry + 18;
			d.round(px, ty - 2.5f, pw, 5, 2.5f, Theme.SURFACE_3);
			d.round(px, ty - 2.5f, Math.max(5, pw * alpha), 5, 2.5f, Theme.accent());
			d.circle(px + pw * alpha, ty, 7, 0xFFF4F1EE);
			ui.drag(px - 6, ty - 10, pw + 12, 20, (mx, my) -> {
				alpha = clamp((mx - px) / pw);
				apply();
			});
			ry += 22;
		}
		d.round(px, ry, 32, 32, 8, target.argb() | 0xFF000000);
		float fx = px + 40, fw = pw - 40;
		boolean focused = screen.focused == hex;
		d.round(fx, ry, fw, 32, 8, Theme.SURFACE_3);
		if (focused) {
			d.ring(fx, ry, fw, 32, 8, 1, Theme.accent());
		}
		hex.draw(ui, fx + 10, ry + 16, fw - 20, "#RRGGBB", focused, Fonts.Style.SEMIBOLD, 12);
		ui.click(fx, ry, fw, 32, () -> screen.focused = hex);
	}

	boolean press(Ui ui, RustyScreen screen, float mx, float my, int button) {
		float[] p = position(screen);
		float h = target.alpha ? H + 22 : H;
		if (mx >= p[0] && my >= p[1] && mx < p[0] + W && my < p[1] + h) {
			ui.press(mx, my, button);
			return true;
		}
		return false;
	}

	private static float clamp(float v) {
		return Math.max(0f, Math.min(1f, v));
	}
}
