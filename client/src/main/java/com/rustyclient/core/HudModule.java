package com.rustyclient.core;

import com.rustyclient.core.setting.NumberSetting;
import com.rustyclient.render.Draw;

public abstract class HudModule extends Module {
	public final NumberSetting scale;
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
