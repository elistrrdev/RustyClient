package com.rustyclient.gui;

import com.rustyclient.core.Theme;
import com.rustyclient.module.ClientSettings;
import com.rustyclient.render.Draw;
import com.rustyclient.render.Fonts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class Ui {
	public record Hit(float x, float y, float w, float h, Consumer<Integer> action, Drag drag) {
		boolean contains(float mx, float my) {
			return mx >= x && my >= y && mx < x + w && my < y + h;
		}
	}

	public interface Drag {
		void update(float mx, float my);
	}

	public Draw d;
	public float mx = -1000;
	public float my = -1000;
	public float dt;
	private List<Hit> hits = new ArrayList<>();
	private List<Hit> building = new ArrayList<>();
	private final Map<String, Float> anims = new HashMap<>();
	private float clipX = -1e9f, clipY = -1e9f, clipW = 2e9f, clipH = 2e9f;
	public Drag activeDrag;
	public boolean blockHover;

	public void begin(Draw d, float mouseUnitsX, float mouseUnitsY, float dt) {
		this.d = d;
		this.mx = mouseUnitsX;
		this.my = mouseUnitsY;
		this.dt = dt;
		this.building = new ArrayList<>();
		this.blockHover = false;
		resetClip();
	}

	public void end() {
		hits = building;
	}

	public void clip(float x, float y, float w, float h) {
		clipX = x;
		clipY = y;
		clipW = w;
		clipH = h;
	}

	public void resetClip() {
		clipX = -1e9f;
		clipY = -1e9f;
		clipW = 2e9f;
		clipH = 2e9f;
	}

	private boolean inClip(float px, float py) {
		return px >= clipX && py >= clipY && px < clipX + clipW && py < clipY + clipH;
	}

	public boolean hover(float x, float y, float w, float h) {
		if (blockHover || activeDrag != null) {
			return false;
		}
		return mx >= x && my >= y && mx < x + w && my < y + h && inClip(mx, my);
	}

	public void click(float x, float y, float w, float h, Consumer<Integer> action) {
		float cx = Math.max(x, clipX), cy = Math.max(y, clipY);
		float cx2 = Math.min(x + w, clipX + clipW), cy2 = Math.min(y + h, clipY + clipH);
		if (cx2 <= cx || cy2 <= cy) {
			return;
		}
		building.add(new Hit(cx, cy, cx2 - cx, cy2 - cy, action, null));
	}

	public void click(float x, float y, float w, float h, Runnable action) {
		click(x, y, w, h, button -> {
			if (button == 0) {
				action.run();
			}
		});
	}

	public void drag(float x, float y, float w, float h, Drag drag) {
		float cx = Math.max(x, clipX), cy = Math.max(y, clipY);
		float cx2 = Math.min(x + w, clipX + clipW), cy2 = Math.min(y + h, clipY + clipH);
		if (cx2 <= cx || cy2 <= cy) {
			return;
		}
		building.add(new Hit(cx, cy, cx2 - cx, cy2 - cy, null, drag));
	}

	public void catchAll(float x, float y, float w, float h) {
		building.add(new Hit(x, y, w, h, b -> {
		}, null));
	}

	public boolean press(float x, float y, int button) {
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (hit.contains(x, y)) {
				if (hit.drag() != null) {
					if (button == 0) {
						activeDrag = hit.drag();
						activeDrag.update(x, y);
					}
				} else if (hit.action() != null) {
					hit.action().accept(button);
				}
				return true;
			}
		}
		return false;
	}

	public void release() {
		activeDrag = null;
	}

	public void dragTo(float x, float y) {
		if (activeDrag != null) {
			activeDrag.update(x, y);
		}
	}

	public float anim(String key, float target, float speed) {
		ClientSettings settings = ClientSettings.get();
		if (settings != null && !settings.animations.on()) {
			anims.put(key, target);
			return target;
		}
		float value = anims.getOrDefault(key, target);
		float k = 1f - (float) Math.exp(-speed * dt);
		value += (target - value) * k;
		if (Math.abs(target - value) < 0.002f) {
			value = target;
		}
		anims.put(key, value);
		return value;
	}

	public void setAnim(String key, float value) {
		anims.put(key, value);
	}

	public boolean toggle(String key, float x, float y, boolean on) {
		float t = anim(key, on ? 1f : 0f, 18f);
		float w = 40, h = 22;
		int bg = Theme.mix(0xFF3A3634, Theme.accent(), t);
		d.round(x, y, w, h, h / 2f, bg);
		float knob = 16;
		float kx = x + 3 + (w - knob - 6) * t;
		int knobColor = Theme.mix(0xFFB8B2AC, Theme.INK, t);
		d.circle(kx + knob / 2f, y + h / 2f, knob / 2f, knobColor);
		return on;
	}

	public void chipText(String text, float x, float y, float w, float h, int bg, int fg, Fonts.Style style, float size, float radius) {
		d.round(x, y, w, h, radius, bg);
		d.textCenter(text, x + w / 2f, y + h / 2f, style, size, fg);
	}
}
