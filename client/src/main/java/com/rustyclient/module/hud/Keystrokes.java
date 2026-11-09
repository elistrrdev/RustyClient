package com.rustyclient.module.hud;

import com.rustyclient.core.Category;
import com.rustyclient.core.Clicks;
import com.rustyclient.core.HudModule;
import com.rustyclient.core.Theme;
import com.rustyclient.core.setting.BoolSetting;
import com.rustyclient.core.setting.ColorSetting;
import com.rustyclient.core.setting.NumberSetting;
import com.rustyclient.render.Draw;
import com.rustyclient.render.Fonts;
import net.minecraft.client.KeyMapping;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class Keystrokes extends HudModule {
	private final BoolSetting mouse = bool("Show mouse buttons", GENERAL, true);
	private final BoolSetting space = bool("Show space bar", GENERAL, true);
	private final BoolSetting cps = bool("Show CPS", GENERAL, true);
	private final BoolSetting shadow = bool("Text shadow", GENERAL, false);
	private final NumberSetting opacity = number("Opacity", APPEARANCE, 0.55, 0, 1, 0.05, NumberSetting.percent());
	private final NumberSetting radius = number("Corner radius", APPEARANCE, 4, 0, 11, 0.5, NumberSetting.decimal("px"));
	private final ColorSetting textColor = color("Text color", APPEARANCE, 0xFFF2EEEA);
	private final ColorSetting pressedColor = color("Pressed color", APPEARANCE, Theme.DEFAULT_ACCENT);
	private final Map<String, Float> fade = new HashMap<>();
	private long last = System.nanoTime();

	public Keystrokes() {
		super("Keystrokes", "keyboard", Category.HUD, true, 0.01, 0.3);
	}

	@Override
	public void render(Draw d, boolean editing) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - last) / 1e9f);
		last = now;
		float k = 22, gap = 2;
		float w = k * 3 + gap * 2;
		float y = 0;
		key(d, "w", mc.options.keyUp, k + gap, y, k, k, dt);
		y += k + gap;
		key(d, "a", mc.options.keyLeft, 0, y, k, k, dt);
		key(d, "s", mc.options.keyDown, k + gap, y, k, k, dt);
		key(d, "d", mc.options.keyRight, (k + gap) * 2, y, k, k, dt);
		y += k + gap;
		if (mouse.on()) {
			float mw = (w - gap) / 2f;
			mouseKey(d, "lmb", "LMB", mc.options.keyAttack, Clicks.left(), 0, y, mw, k, dt);
			mouseKey(d, "rmb", "RMB", mc.options.keyUse, Clicks.right(), mw + gap, y, mw, k, dt);
			y += k + gap;
		}
		if (space.on()) {
			float t = press("space", mc.options.keyJump.isDown(), dt);
			float h = 13;
			box(d, 0, y, w, h, t);
			d.rect(w / 2f - 12, y + h / 2f - 0.75f, 24, 1.5f, Theme.mix(textColor.argb(), Theme.INK, t));
			y += h;
		}
		width = w;
		height = y;
	}

	private float press(String id, boolean down, float dt) {
		float v = fade.getOrDefault(id, 0f);
		float target = down ? 1f : 0f;
		v += (target - v) * (1f - (float) Math.exp(-dt * 22f));
		fade.put(id, v);
		return v;
	}

	private void box(Draw d, float x, float y, float w, float h, float t) {
		int idle = Theme.withAlpha(Theme.FRAME, (int) Math.round(opacity.v() * 255));
		int pressed = Theme.hudAccent(pressedColor.argb());
		d.round(x, y, w, h, radius.f(), Theme.mix(idle, pressed, t));
	}

	private void key(Draw d, String id, KeyMapping mapping, float x, float y, float w, float h, float dt) {
		float t = press(id, mapping.isDown(), dt);
		box(d, x, y, w, h, t);
		String label = mapping.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT);
		if (label.length() > 3) {
			label = label.substring(0, 3);
		}
		int color = Theme.mix(textColor.argb(), Theme.INK, t);
		d.textShadowed(label, x + w / 2f - d.width(label, Fonts.Style.BOLD, 9) / 2f, y + h / 2f, Fonts.Style.BOLD, 9, color, shadow.on() && t < 0.5f);
	}

	private void mouseKey(Draw d, String id, String label, KeyMapping mapping, int clicks, float x, float y, float w, float h, float dt) {
		float t = press(id, mapping.isDown(), dt);
		box(d, x, y, w, h, t);
		int color = Theme.mix(textColor.argb(), Theme.INK, t);
		if (cps.on()) {
			d.textCenter(label, x + w / 2f, y + h / 2f - 3.5f, Fonts.Style.BOLD, 8, color);
			d.textCenter(clicks + " CPS", x + w / 2f, y + h / 2f + 5f, Fonts.Style.MONO_MEDIUM, 5.5f, Theme.fade(color, 0.8f));
		} else {
			d.textCenter(label, x + w / 2f, y + h / 2f, Fonts.Style.BOLD, 8.5f, color);
		}
	}
}
