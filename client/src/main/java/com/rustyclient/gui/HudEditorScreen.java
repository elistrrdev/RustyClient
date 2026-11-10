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

	public void grab(HudModule hud, double guiX, double guiY) {
		mouseX = guiX;
		mouseY = guiY;
		dragging = hud;
		grabX = px(guiX) - hud.pixelX();
		grabY = px(guiY) - hud.pixelY();
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
		boolean free = freeMove();
		if (!free) {
			d.rect(ww / 2f - 0.5f, 0, 1, wh, line);
			d.rect(0, wh / 2f - 0.5f, ww, 1, line);
		}
		hovered = null;
		for (int i = Modules.hud().size() - 1; i >= 0 && hovered == null; i--) {
			HudModule hud = Modules.hud().get(i);
			if (hud.enabled() && (overHandle(hud, m, n) || overBox(hud, m, n))) {
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
			float[] handle = handle(hud);
			boolean onHandle = hud == scaling || (hud == hovered && overHandle(hud, m, n));
			float hr = (onHandle ? 4f : 3.25f) * gs;
			d.knob(handle[0], handle[1], hr, onHandle, focus ? Theme.accent() : Theme.withAlpha(0xFFFFFFFF, 0xD8));
			if (focus) {
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
		for (float[] guide : free ? List.<float[]>of() : guides) {
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
		button(d, "Done", bx, by, bw, bh, true, false, m, n);
		button(d, "Modules", bx + bw + gap, by, bw, bh, false, false, m, n);
		button(d, RustyScreen.resetPending() ? "Confirm" : "Reset", bx + (bw + gap) * 2, by, bw, bh, false, RustyScreen.resetPending(), m, n);
		String hint = "Drag to move, hold Shift to move freely, drag the corner to scale, right click for settings";
		d.textCenter(hint, ww / 2f, by - 9 * gs, Fonts.Style.MEDIUM, 6.5f * gs, Theme.withAlpha(0xFFFFFFFF, 0xB0));
		d.end();
	}

	private void button(Draw d, String label, float x, float y, float w, float h, boolean primary, boolean danger, float m, float n) {
		boolean hover = m >= x && n >= y && m < x + w && n < y + h;
		float gs = Draw.guiScale();
		int bg = primary ? (hover ? Theme.accentHover() : Theme.accent()) : (hover ? Theme.SURFACE_3 : Theme.SURFACE);
		if (danger) {
			bg = hover ? Theme.mix(Theme.DANGER, 0xFFFFFFFF, 0.14f) : Theme.DANGER;
		}
		d.round(x, y, w, h, 5 * gs, bg);
		d.textCenter(label, x + w / 2f, y + h / 2f, Fonts.Style.SEMIBOLD, 7 * gs, danger ? 0xFFFFFFFF : primary ? Theme.INK : Theme.TEXT);
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

	private static boolean freeMove() {
		return Compat.isKeyDown("key.keyboard.left.shift") || Compat.isKeyDown("key.keyboard.right.shift");
	}

	private static float[] handle(HudModule hud) {
		float gs = Draw.guiScale();
		float pad = 2 * gs, edge = 4 * gs;
		float hx = hud.pixelX() + hud.pixelWidth() + pad;
		float hy = hud.pixelY() + hud.pixelHeight() + pad;
		hx = Math.max(edge, Math.min(Draw.windowWidth() - edge, hx));
		hy = Math.max(edge, Math.min(Draw.windowHeight() - edge, hy));
		return new float[]{hx, hy};
	}

	private static boolean overHandle(HudModule hud, float m, float n) {
		float[] h = handle(hud);
		float reach = 7 * Draw.guiScale();
		float dx = m - h[0], dy = n - h[1];
		return dx * dx + dy * dy <= reach * reach;
	}

	private static boolean overBox(HudModule hud, float m, float n) {
		float pad = 2 * Draw.guiScale();
		float x = hud.pixelX(), y = hud.pixelY(), w = Math.max(1, hud.pixelWidth()), h = Math.max(1, hud.pixelHeight());
		return m >= x - pad && n >= y - pad && m < x + w + pad && n < y + h + pad;
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
		if (button >= 0 && Ui.button(event) == 0) {
			if (button == 0) {
				onClose();
			} else if (button == 1) {
				backToMenu();
			} else {
				RustyScreen.pressReset();
			}
			return true;
		}
		for (int i = Modules.hud().size() - 1; i >= 0; i--) {
			HudModule hud = Modules.hud().get(i);
			if (hud.enabled() && Ui.button(event) == 0 && overHandle(hud, m, n)) {
				scaling = hud;
				scaleStart = hud.scale.v();
				scaleStartW = Math.max(1, hud.pixelWidth());
				scaleOriginX = hud.pixelX();
				scaleOriginY = hud.pixelY();
				return true;
			}
		}
		for (int i = Modules.hud().size() - 1; i >= 0; i--) {
			HudModule hud = Modules.hud().get(i);
			if (!hud.enabled()) {
				continue;
			}
			float x = hud.pixelX(), y = hud.pixelY();
			if (overBox(hud, m, n)) {
				if (Ui.button(event) == 1) {
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
			if ((settings == null || settings.snapping.on()) && !freeMove()) {
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
			float px = hovered.pixelX(), py = hovered.pixelY();
			hovered.scale.set(hovered.scale.v() + v * 0.05);
			hovered.setPixelPosition(px, py);
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
