package com.rustyclient.gui;

import com.rustyclient.core.Config;
import com.rustyclient.core.HudModule;
import com.rustyclient.core.Modules;
import com.rustyclient.core.Theme;
import com.rustyclient.module.ClientSettings;
import com.rustyclient.platform.Compat;
import com.rustyclient.render.Draw;
import com.rustyclient.render.Fonts;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class HudEditorScreen extends Screen {
	private static HudEditorScreen active;

	private final RustyScreen parent;
	private HudModule dragging;
	private HudModule scaling;
	private HudModule hovered;
	private float grabX;
	private float grabY;
	private double scaleStart;
	private float scaleStartW;
	private float scaleOriginX;
	private float scaleOriginY;
	private final List<float[]> guides = new ArrayList<>();
	private double mouseX;
	private double mouseY;

	public HudEditorScreen(RustyScreen parent) {
		super(Component.literal("Edit HUD"));
		this.parent = parent;
	}

	public static boolean editing() {
		return active != null;
	}

	@Override
	protected void init() {
		active = this;
	}

	@Override
	public void removed() {
		active = null;
		Config.save();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partial) {
	}

	private float px(double gui) {
		return (float) (gui * Draw.guiScale());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
		active = this;
		Draw d = new Draw(g, 1f);
		int ww = Draw.windowWidth(), wh = Draw.windowHeight();
		float gs = Draw.guiScale();
		float m = px(mouseX), n = px(mouseY);
		d.rect(0, 0, ww, wh, 0x30000000);
		int line = Theme.withAlpha(0xFFFFFFFF, 0x14);
		d.rect(ww / 2f - 0.5f, 0, 1, wh, line);
		d.rect(0, wh / 2f - 0.5f, ww, 1, line);
		hovered = null;
		for (HudModule hud : Modules.hud()) {
			if (!hud.enabled()) {
				continue;
			}
			float x = hud.pixelX(), y = hud.pixelY(), w = hud.pixelWidth(), h = hud.pixelHeight();
			if (m >= x && n >= y && m < x + w && n < y + h) {
				hovered = hud;
			}
		}
		for (HudModule hud : Modules.hud()) {
			if (!hud.enabled()) {
				continue;
			}
			float x = hud.pixelX(), y = hud.pixelY(), w = hud.pixelWidth(), h = hud.pixelHeight();
			boolean focus = hud == dragging || hud == scaling || hud == hovered;
			int border = focus ? Theme.accent() : Theme.withAlpha(0xFFFFFFFF, 0x50);
			float pad = 2 * gs;
			d.round(x - pad, y - pad, w + pad * 2, h + pad * 2, 3 * gs, focus ? Theme.withAlpha(Theme.accent(), 0x22) : 0x18FFFFFF);
			d.ring(x - pad, y - pad, w + pad * 2, h + pad * 2, 3 * gs, Math.max(1, gs / 2f), border);
			if (focus) {
				float hs = 5 * gs;
				d.round(x + w + pad - hs / 2f, y + h + pad - hs / 2f, hs, hs, hs / 3f, Theme.accent());
				String label = hud.name + "  " + Math.round(hud.scale.v() * 100) + "%";
				float size = 6 * gs;
				float lw = d.width(label, Fonts.Style.SEMIBOLD, size) + 8 * gs;
				float ly = y - pad - 12 * gs;
				if (ly < 2) {
					ly = y + h + pad + 3 * gs;
				}
				d.round(x - pad, ly, lw, 10 * gs, 3 * gs, Theme.FRAME);
				d.textMid(label, x - pad + 4 * gs, ly + 5 * gs, Fonts.Style.SEMIBOLD, size, Theme.TEXT);
			}
		}
		for (float[] guide : guides) {
			if (guide[0] == 0) {
				d.rect(guide[1] - 0.5f, 0, 1, wh, Theme.accent());
			} else {
				d.rect(0, guide[1] - 0.5f, ww, 1, Theme.accent());
			}
		}
		float bh = 22 * gs;
		float bw = 70 * gs;
		float gap = 6 * gs;
		float total = bw * 3 + gap * 2;
		float bx = (ww - total) / 2f, by = wh - bh - 14 * gs;
		button(d, "Done", bx, by, bw, bh, true, m, n);
		button(d, "Modules", bx + bw + gap, by, bw, bh, false, m, n);
		button(d, "Reset", bx + (bw + gap) * 2, by, bw, bh, false, m, n);
		String hint = "Drag to move, drag the corner to scale, right click for settings";
		d.textCenter(hint, ww / 2f, by - 9 * gs, Fonts.Style.MEDIUM, 6.5f * gs, Theme.withAlpha(0xFFFFFFFF, 0xB0));
		d.end();
	}

	private void button(Draw d, String label, float x, float y, float w, float h, boolean primary, float m, float n) {
		boolean hover = m >= x && n >= y && m < x + w && n < y + h;
		float gs = Draw.guiScale();
		int bg = primary ? (hover ? Theme.accentHover() : Theme.accent()) : (hover ? Theme.SURFACE_3 : Theme.SURFACE);
		d.round(x, y, w, h, 5 * gs, bg);
		d.textCenter(label, x + w / 2f, y + h / 2f, Fonts.Style.SEMIBOLD, 7 * gs, primary ? Theme.INK : Theme.TEXT);
	}

	private int buttonAt(float m, float n) {
		float gs = Draw.guiScale();
		int ww = Draw.windowWidth(), wh = Draw.windowHeight();
		float bh = 22 * gs, bw = 70 * gs, gap = 6 * gs;
		float total = bw * 3 + gap * 2;
		float bx = (ww - total) / 2f, by = wh - bh - 14 * gs;
		for (int i = 0; i < 3; i++) {
			float x = bx + (bw + gap) * i;
			if (m >= x && n >= by && m < x + bw && n < by + bh) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public void mouseMoved(double x, double y) {
		mouseX = x;
		mouseY = y;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		mouseX = event.x();
		mouseY = event.y();
		float m = px(event.x()), n = px(event.y());
		int button = buttonAt(m, n);
		if (button >= 0 && event.button() == 0) {
			if (button == 0) {
				onClose();
			} else if (button == 1) {
				backToMenu();
			} else {
				RustyScreen.resetHud();
			}
			return true;
		}
		float gs = Draw.guiScale();
		for (int i = Modules.hud().size() - 1; i >= 0; i--) {
			HudModule hud = Modules.hud().get(i);
			if (!hud.enabled()) {
				continue;
			}
			float x = hud.pixelX(), y = hud.pixelY(), w = hud.pixelWidth(), h = hud.pixelHeight();
			float pad = 2 * gs, hs = 7 * gs;
			if (event.button() == 0 && m >= x + w + pad - hs && n >= y + h + pad - hs && m < x + w + pad + hs && n < y + h + pad + hs) {
				scaling = hud;
				scaleStart = hud.scale.v();
				scaleStartW = Math.max(1, w);
				scaleOriginX = x;
				scaleOriginY = y;
				return true;
			}
			if (m >= x - pad && n >= y - pad && m < x + w + pad && n < y + h + pad) {
				if (event.button() == 1) {
					parent.openConfig(hud);
					parent.resetOpenAnimation();
					Compat.setScreen(parent);
					return true;
				}
				dragging = hud;
				grabX = m - x;
				grabY = n - y;
				return true;
			}
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		mouseX = event.x();
		mouseY = event.y();
		float m = px(event.x()), n = px(event.y());
		guides.clear();
		if (dragging != null) {
			float w = dragging.pixelWidth(), h = dragging.pixelHeight();
			float x = m - grabX, y = n - grabY;
			ClientSettings settings = ClientSettings.get();
			if (settings == null || settings.snapping.on()) {
				float[] sx = snap(x, w, true, dragging);
				float[] sy = snap(y, h, false, dragging);
				x = sx[0];
				y = sy[0];
				if (sx[1] >= 0) {
					guides.add(new float[]{0, sx[1]});
				}
				if (sy[1] >= 0) {
					guides.add(new float[]{1, sy[1]});
				}
			}
			dragging.setPixelPosition(x, y);
		} else if (scaling != null) {
			float target = Math.max(4, m - scaleOriginX);
			double next = scaleStart * target / scaleStartW;
			scaling.scale.set(next);
			scaling.setPixelPosition(scaleOriginX, scaleOriginY);
		}
		return true;
	}

	private float[] snap(float pos, float size, boolean horizontal, HudModule self) {
		float limit = horizontal ? Draw.windowWidth() : Draw.windowHeight();
		float threshold = 4 * Draw.guiScale();
		List<Float> lines = new ArrayList<>();
		float margin = 2 * Draw.guiScale();
		lines.add(margin);
		lines.add(limit / 2f);
		lines.add(limit - margin);
		for (HudModule other : Modules.hud()) {
			if (other == self || !other.enabled()) {
				continue;
			}
			float o = horizontal ? other.pixelX() : other.pixelY();
			float os = horizontal ? other.pixelWidth() : other.pixelHeight();
			lines.add(o);
			lines.add(o + os / 2f);
			lines.add(o + os);
		}
		float best = threshold;
		float result = pos;
		float guide = -1;
		float[] anchors = {0, size / 2f, size};
		for (float line : lines) {
			for (float anchor : anchors) {
				float diff = Math.abs(pos + anchor - line);
				if (diff < best) {
					best = diff;
					result = line - anchor;
					guide = line;
				}
			}
		}
		return new float[]{Math.max(0, Math.min(limit - size, result)), guide};
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = null;
		scaling = null;
		guides.clear();
		Config.markDirty();
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double h, double v) {
		if (hovered != null) {
			hovered.scale.set(hovered.scale.v() + v * 0.05);
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) {
			backToMenu();
			return true;
		}
		return super.keyPressed(event);
	}

	private void backToMenu() {
		parent.resetOpenAnimation();
		Compat.setScreen(parent);
	}
}
