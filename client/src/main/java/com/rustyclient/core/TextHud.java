package com.rustyclient.core;

import com.rustyclient.core.setting.BoolSetting;
import com.rustyclient.core.setting.ChoiceSetting;
import com.rustyclient.core.setting.ColorSetting;
import com.rustyclient.render.Draw;
import com.rustyclient.render.Fonts;

public abstract class TextHud extends HudModule {
	public final BoolSetting background;
	public final BoolSetting shadow;
	public final BoolSetting showLabel;
	public final ChoiceSetting align;
	public final ColorSetting textColor;
	public final ColorSetting labelColor;
	public final ColorSetting backgroundColor;

	protected TextHud(String name, String icon, Category category, boolean defaultEnabled, double fx, double fy) {
		super(name, icon, category, defaultEnabled, fx, fy);
		this.background = bool("Draw background", GENERAL, true);
		this.shadow = bool("Text shadow", GENERAL, false);
		this.showLabel = bool("Show label", GENERAL, true);
		this.align = choice("Alignment", APPEARANCE, "Center", "Left", "Center", "Right");
		this.textColor = color("Text color", APPEARANCE, 0xFFF2EEEA);
		this.labelColor = color("Label color", APPEARANCE, 0xFF1AB0DD);
		this.backgroundColor = colorAlpha("Background color", APPEARANCE, 0x8C0F0E0D);
	}

	protected abstract String label();

	protected abstract String value();

	protected int valueColor(int fallback) {
		return fallback;
	}

	protected String sample() {
		return value();
	}

	@Override
	public void render(Draw d, boolean editing) {
		String value = value();
		if (value == null) {
			value = sample();
		}
		String label = showLabel.on() ? label() : "";
		Fonts.Style style = Fonts.Style.SEMIBOLD;
		float size = 9;
		float lw = label.isEmpty() ? 0 : d.width(label, style, size) + 4;
		float vw = d.width(value, style, size);
		float content = lw + vw;
		float padX = background.on() ? 6 : 1;
		float w = Math.max(background.on() ? 52 : content + padX * 2, content + padX * 2);
		float h = background.on() ? 17 : 11;
		width = w;
		height = h;
		if (background.on()) {
			d.round(0, 0, w, h, radius(), backgroundColor.argb());
		}
		float x;
		if (align.is("Left")) {
			x = padX;
		} else if (align.is("Right")) {
			x = w - padX - content;
		} else {
			x = (w - content) / 2f;
		}
		float cy = h / 2f;
		if (!label.isEmpty()) {
			d.textShadowed(label, x, cy, style, size, Theme.hudAccent(labelColor.argb()), shadow.on());
		}
		d.textShadowed(value, x + lw, cy, style, size, valueColor(textColor.argb()), shadow.on());
	}
}
