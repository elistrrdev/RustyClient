package com.rustyclient.module.hud;

import com.rustyclient.core.Category;
import com.rustyclient.core.Clicks;
import com.rustyclient.core.HudModule;
import com.rustyclient.core.Theme;
import com.rustyclient.core.setting.BoolSetting;
import com.rustyclient.core.setting.ChoiceSetting;
import com.rustyclient.core.setting.ColorSetting;
import com.rustyclient.core.setting.NumberSetting;
import com.rustyclient.render.Draw;
import com.rustyclient.render.Fonts;
import net.minecraft.client.KeyMapping;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class Keystrokes extends HudModule {
	private static final String MOUSE = "Mouse buttons";
	private static final String LAYOUT = "Layout";
	private static final String TEXT = "Text";
	private static final String ANIMATION = "Animation";
	private static final String BORDER = "Border";

	private final BoolSetting movement = bool("Show WASD", GENERAL, true);
	private final BoolSetting mouse = bool("Show mouse buttons", GENERAL, true);
	private final BoolSetting space = bool("Show space bar", GENERAL, true);
	private final BoolSetting modifiers = bool("Show sneak and sprint", GENERAL, false);
	private final BoolSetting shadow = bool("Text shadow", GENERAL, false);
	private final BoolSetting upper = bool("Uppercase labels", GENERAL, true);

	private final ChoiceSetting mouseText = choice("Mouse button text", MOUSE, "Label and CPS", "Label and CPS", "Label only", "CPS only");
	private final ChoiceSetting mouseLabels = choice("Mouse labels", MOUSE, "LMB / RMB", "LMB / RMB", "L / R", "Left / Right", "Bound key");
	private final ChoiceSetting cpsFormat = choice("CPS format", MOUSE, "12 CPS", "12 CPS", "12 cps", "12");
	private final BoolSetting hideZeroCps = bool("Hide CPS at zero", MOUSE, false);

	private final NumberSetting keySize = number("Key size", LAYOUT, 22, 14, 40, 1, NumberSetting.integer(""));
	private final NumberSetting gap = number("Key gap", LAYOUT, 2, 0, 8, 0.5, NumberSetting.decimal(""));
	private final NumberSetting mouseHeight = number("Mouse button height", LAYOUT, 24, 12, 44, 1, NumberSetting.integer(""));
	private final NumberSetting modifierHeight = number("Sneak and sprint height", LAYOUT, 15, 10, 36, 1, NumberSetting.integer(""));
	private final NumberSetting spaceHeight = number("Space bar height", LAYOUT, 13, 6, 30, 1, NumberSetting.integer(""));
	private final ChoiceSetting spaceContent = choice("Space bar shows", LAYOUT, "Line", "Line", "Label", "Nothing");
	private final NumberSetting lineWidth = number("Space line width", LAYOUT, 0.35, 0.1, 0.9, 0.05, NumberSetting.percent());

	private final ChoiceSetting weight = choice("Font weight", TEXT, "Bold", "Bold", "Semibold", "Medium");
	private final NumberSetting keyText = number("Key text size", TEXT, 9, 5, 18, 0.5, NumberSetting.decimal(""));
	private final NumberSetting mouseLabelText = number("Mouse label size", TEXT, 8.5, 5, 18, 0.5, NumberSetting.decimal(""));
	private final NumberSetting cpsText = number("CPS text size", TEXT, 8, 5, 18, 0.5, NumberSetting.decimal(""));
	private final NumberSetting smallText = number("Sneak and sprint text size", TEXT, 7.5, 5, 18, 0.5, NumberSetting.decimal(""));

	private final ChoiceSetting animation = choice("Press animation", ANIMATION, "Fade", "Fade", "Instant");
	private final NumberSetting fadeSpeed = number("Fade speed", ANIMATION, 22, 4, 60, 1, NumberSetting.integer(""));
	private final NumberSetting pressScale = number("Pressed size", ANIMATION, 1, 0.8, 1, 0.01, NumberSetting.percent());

	private final BoolSetting border = bool("Draw border", BORDER, false);
	private final NumberSetting borderWidth = number("Border width", BORDER, 1, 0.5, 4, 0.5, NumberSetting.decimal("px"));
	private final ColorSetting borderColor = colorAlpha("Border color", BORDER, 0x60FFFFFF);
	private final ColorSetting borderPressed = colorAlpha("Pressed border color", BORDER, 0xFF1AB0DD);

	private final ColorSetting background = colorAlpha("Background color", APPEARANCE, 0x8C0F0E0D);
	private final ColorSetting pressedColor = colorAlpha("Pressed color", APPEARANCE, Theme.DEFAULT_ACCENT);
	private final ColorSetting textColor = color("Text color", APPEARANCE, 0xFFF2EEEA);
	private final ColorSetting pressedText = color("Pressed text color", APPEARANCE, Theme.INK);
	private final ColorSetting cpsColor = colorAlpha("CPS color", APPEARANCE, 0xCCF2EEEA);

	private final Map<String, Float> fade = new HashMap<>();
	private long last = System.nanoTime();

	public Keystrokes() {
		super("Keystrokes", "keyboard", Category.HUD, false, 0.01, 0.3);
		mouseLabels.visibleWhen(() -> mouse.on() && !mouseText.is("CPS only"));
		cpsFormat.visibleWhen(() -> mouse.on() && !mouseText.is("Label only"));
		hideZeroCps.visibleWhen(() -> mouse.on() && mouseText.is("Label and CPS"));
		mouseText.visibleWhen(mouse::on);
		mouseHeight.visibleWhen(mouse::on);
		modifierHeight.visibleWhen(modifiers::on);
		smallText.visibleWhen(modifiers::on);
		spaceHeight.visibleWhen(space::on);
		spaceContent.visibleWhen(space::on);
		lineWidth.visibleWhen(() -> space.on() && spaceContent.is("Line"));
		mouseLabelText.visibleWhen(() -> mouse.on() && !mouseText.is("CPS only"));
		cpsText.visibleWhen(() -> mouse.on() && !mouseText.is("Label only"));
		fadeSpeed.visibleWhen(() -> animation.is("Fade"));
		borderWidth.visibleWhen(border::on);
		borderColor.visibleWhen(border::on);
		borderPressed.visibleWhen(border::on);
	}

	private Fonts.Style style() {
		if (weight.is("Medium")) {
			return Fonts.Style.MEDIUM;
		}
		return weight.is("Semibold") ? Fonts.Style.SEMIBOLD : Fonts.Style.BOLD;
	}

	@Override
	public void render(Draw d, boolean editing) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - last) / 1e9f);
		last = now;
		float k = keySize.f(), g = gap.f();
		float w = k * 3 + g * 2;
		float half = (w - g) / 2f;
		float y = 0;
		boolean any = false;
		if (movement.on()) {
			key(d, "w", mc.options.keyUp, k + g, y, k, k, keyText.f(), dt);
			y += k + g;
			key(d, "a", mc.options.keyLeft, 0, y, k, k, keyText.f(), dt);
			key(d, "s", mc.options.keyDown, k + g, y, k, k, keyText.f(), dt);
			key(d, "d", mc.options.keyRight, (k + g) * 2, y, k, k, keyText.f(), dt);
			y += k + g;
			any = true;
		}
		if (modifiers.on()) {
			float h = modifierHeight.f();
			key(d, "shift", mc.options.keyShift, 0, y, half, h, smallText.f(), dt);
			key(d, "sprint", mc.options.keySprint, half + g, y, half, h, smallText.f(), dt);
			y += h + g;
			any = true;
		}
		if (mouse.on()) {
			float h = mouseHeight.f();
			mouseKey(d, "lmb", true, mc.options.keyAttack, Clicks.left(), 0, y, half, h, dt);
			mouseKey(d, "rmb", false, mc.options.keyUse, Clicks.right(), half + g, y, half, h, dt);
			y += h + g;
			any = true;
		}
		if (space.on()) {
			float h = spaceHeight.f();
			float t = press("space", mc.options.keyJump.isDown(), dt);
			float[] r = box(d, 0, y, w, h, t);
			int color = Theme.mix(textColor.argb(), pressedText.argb(), t);
			if (spaceContent.is("Line")) {
				float lw = r[2] * lineWidth.f();
				float lh = Math.max(1f, h / 9f);
				d.round(r[0] + (r[2] - lw) / 2f, r[1] + r[3] / 2f - lh / 2f, lw, lh, lh / 2f, color);
			} else if (spaceContent.is("Label")) {
				centered(d, label(mc.options.keyJump), r[0] + r[2] / 2f, r[1] + r[3] / 2f, Math.min(keyText.f(), h * 0.75f), color);
			}
			y += h + g;
			any = true;
		}
		width = w;
		height = any ? Math.max(1, y - g) : 10;
	}

	private float press(String id, boolean down, float dt) {
		float target = down ? 1f : 0f;
		if (animation.is("Instant")) {
			fade.put(id, target);
			return target;
		}
		float v = fade.getOrDefault(id, 0f);
		v += (target - v) * (1f - (float) Math.exp(-dt * fadeSpeed.f()));
		fade.put(id, v);
		return v;
	}

	private float[] box(Draw d, float x, float y, float w, float h, float t) {
		float shrink = 1f - (1f - pressScale.f()) * t;
		float bw = w * shrink, bh = h * shrink;
		float bx = x + (w - bw) / 2f, by = y + (h - bh) / 2f;
		int pressed = Theme.hudAccent(pressedColor.argb());
		float r = radius(Math.min(bw, bh) / 2f);
		d.round(bx, by, bw, bh, r, Theme.mix(background.argb(), pressed, t));
		if (border.on()) {
			int c = Theme.mix(borderColor.argb(), Theme.hudAccent(borderPressed.argb()), t);
			d.ring(bx, by, bw, bh, r, borderWidth.f(), c);
		}
		return new float[]{bx, by, bw, bh};
	}

	private void centered(Draw d, String text, float cx, float cy, float size, int color) {
		if (text.isEmpty()) {
			return;
		}
		float tw = d.width(text, style(), size);
		d.textShadowed(text, cx - tw / 2f, cy, style(), size, color, shadow.on());
	}

	private String fit(Draw d, String text, float max, float size) {
		String out = text;
		while (out.length() > 1 && d.width(out, style(), size) > max) {
			out = out.substring(0, out.length() - 1);
		}
		return out;
	}

	private String label(KeyMapping mapping) {
		String label = mapping.getTranslatedKeyMessage().getString();
		if (upper.on()) {
			label = label.toUpperCase(Locale.ROOT);
		}
		return label;
	}

	private void key(Draw d, String id, KeyMapping mapping, float x, float y, float w, float h, float size, float dt) {
		float t = press(id, mapping.isDown(), dt);
		float[] r = box(d, x, y, w, h, t);
		int color = Theme.mix(textColor.argb(), pressedText.argb(), t);
		float s = Math.min(size, h * 0.8f);
		String text = fit(d, label(mapping), r[2] - 3, s);
		centered(d, text, r[0] + r[2] / 2f, r[1] + r[3] / 2f, s, color);
	}

	private String mouseLabel(boolean left, KeyMapping mapping) {
		String text = switch (mouseLabels.get()) {
			case "L / R" -> left ? "L" : "R";
			case "Left / Right" -> left ? "Left" : "Right";
			case "Bound key" -> mapping.getTranslatedKeyMessage().getString();
			default -> left ? "LMB" : "RMB";
		};
		return upper.on() ? text.toUpperCase(Locale.ROOT) : text;
	}

	private String cps(int clicks) {
		return switch (cpsFormat.get()) {
			case "12 cps" -> clicks + " cps";
			case "12" -> String.valueOf(clicks);
			default -> clicks + " CPS";
		};
	}

	private void mouseKey(Draw d, String id, boolean left, KeyMapping mapping, int clicks, float x, float y, float w, float h, float dt) {
		float t = press(id, mapping.isDown(), dt);
		float[] r = box(d, x, y, w, h, t);
		int color = Theme.mix(textColor.argb(), pressedText.argb(), t);
		int cpsTint = Theme.mix(cpsColor.argb(), pressedText.argb(), t);
		float cx = r[0] + r[2] / 2f, cy = r[1] + r[3] / 2f;
		boolean showLabel = !mouseText.is("CPS only");
		boolean showCps = !mouseText.is("Label only") && !(hideZeroCps.on() && clicks == 0 && mouseText.is("Label and CPS"));
		float ls = mouseLabelText.f(), cs = cpsText.f();
		if (showLabel && showCps) {
			float spacing = Math.max(1f, Math.min(ls, cs) * 0.25f);
			float total = ls + spacing + cs;
			float scale = Math.min(1f, (r[3] - 2f) / total);
			ls *= scale;
			cs *= scale;
			spacing *= scale;
			total = ls + spacing + cs;
			float top = cy - total / 2f;
			String label = fit(d, mouseLabel(left, mapping), r[2] - 3, ls);
			centered(d, label, cx, top + ls / 2f, ls, color);
			centered(d, fit(d, cps(clicks), r[2] - 3, cs), cx, top + ls + spacing + cs / 2f, cs, cpsTint);
		} else if (showLabel) {
			float s = Math.min(ls, r[3] * 0.8f);
			centered(d, fit(d, mouseLabel(left, mapping), r[2] - 3, s), cx, cy, s, color);
		} else if (showCps) {
			float s = Math.min(cs, r[3] * 0.8f);
			centered(d, fit(d, cps(clicks), r[2] - 3, s), cx, cy, s, cpsTint);
		}
	}
}
