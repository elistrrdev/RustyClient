package com.rustyclient.core;

import com.rustyclient.core.setting.BoolSetting;
import com.rustyclient.core.setting.ChoiceSetting;
import com.rustyclient.core.setting.NumberSetting;
import com.rustyclient.module.ClientSettings;
import com.rustyclient.render.Draw;

public abstract class HudModule extends Module {
	public final NumberSetting scale;
	public final ChoiceSetting font;
	public final BoolSetting customRadius;
	public final NumberSetting radius;
	public float width = 40;
	public float height = 12;
	public double fx;
	public double fy;
	private final double defaultFx;
	private final double defaultFy;

	protected HudModule(String name, String icon, Category category, boolean defaultEnabled, double fx, double fy) {
		super(name, icon, category, defaultEnabled);
		this.fx = fx;
		this.fy = fy;
		this.defaultFx = fx;
		this.defaultFy = fy;
		this.scale = number("Scale", APPEARANCE, 1.0, 0.5, 2.5, 0.05, NumberSetting.times());
		this.font = choice("Font", APPEARANCE, "Global", "Global", "Vanilla", "Rusty Client");
		this.customRadius = bool("Custom border radius", APPEARANCE, false);
		this.radius = number("Border radius", APPEARANCE, 4, 0, 12, 0.5, NumberSetting.decimal("px"));
		this.radius.visibleWhen(() -> usesRadius() && customRadius.on());
		this.customRadius.visibleWhen(this::usesRadius);
	}

	protected boolean usesRadius() {
		return true;
	}

	public float radius() {
		if (customRadius.on()) {
			return radius.f();
		}
		ClientSettings settings = ClientSettings.get();
		return settings == null ? 4f : settings.hudRadius.f();
	}

	public float radius(float max) {
		return Math.min(radius(), max);
	}

	public boolean vanillaFont() {
		if (font.is("Vanilla")) {
			return true;
		}
		if (font.is("Rusty Client")) {
			return false;
		}
		ClientSettings settings = ClientSettings.get();
		return settings == null || settings.hudFont.is("Vanilla");
	}

	public abstract void render(Draw d, boolean editing);

	public boolean visibleNow() {
		return true;
	}

	public void resetPosition() {
		fx = defaultFx;
		fy = defaultFy;
		scale.reset();
		Config.markDirty();
	}

	public float unitPx() {
		return Draw.guiScale() * scale.f();
	}

	public float pixelWidth() {
		return width * unitPx();
	}

	public float pixelHeight() {
		return height * unitPx();
	}

	public float pixelX() {
		return (float) (fx * Math.max(0, Draw.windowWidth() - pixelWidth()));
	}

	public float pixelY() {
		return (float) (fy * Math.max(0, Draw.windowHeight() - pixelHeight()));
	}

	public void setPixelPosition(float x, float y) {
		float maxX = Math.max(1, Draw.windowWidth() - pixelWidth());
		float maxY = Math.max(1, Draw.windowHeight() - pixelHeight());
		fx = Math.max(0, Math.min(1, x / maxX));
		fy = Math.max(0, Math.min(1, y / maxY));
		Config.markDirty();
	}
}
