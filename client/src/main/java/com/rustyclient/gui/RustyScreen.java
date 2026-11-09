package com.rustyclient.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.rustyclient.RustyClient;
import com.rustyclient.core.Category;
import com.rustyclient.core.Config;
import com.rustyclient.core.HudModule;
import com.rustyclient.core.Module;
import com.rustyclient.core.Modules;
import com.rustyclient.core.Theme;
import com.rustyclient.core.setting.KeySetting;
import com.rustyclient.module.ClientSettings;
import com.rustyclient.platform.Compat;
import com.rustyclient.render.Draw;
import com.rustyclient.render.Fonts;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RustyScreen extends Screen {
	public enum Page {
		MODULES, PROFILE, SETTINGS, CONFIG
	}

	public static final float PANEL_W = 1118;
	public static final float PANEL_H = 678;
	public static final float RAIL_W = 63;
	public static final float CONTENT_X = 84;
	public static final float PAD_R = 20;
	public static final float DEFAULT_FIT = 0.66f;

	private static Page lastPage = Page.MODULES;
	private static Category lastCategory = null;

	final Ui ui = new Ui();
	Page page = lastPage;
	Category category = lastCategory;
	Module configModule;
	final TextInput search = new TextInput(40);
	TextInput focused;
	KeySetting listening;
	final ConfigView configView = new ConfigView(this);
	final ProfileView profileView = new ProfileView(this);
	float scroll;
	float scrollTarget;
	float contentHeight;
	float viewHeight;
	private long lastFrame = System.nanoTime();
	private long opened = System.nanoTime();
	private boolean closing;
	private long closeStart;
	private double mouseX = -1;
	private double mouseY = -1;
	float scale;
	float panelX;
	float panelY;

	public RustyScreen() {
		super(Component.literal("Rusty Client"));
		if (page == Page.CONFIG) {
			page = Page.MODULES;
		}
	}

	public static void open() {
		Theme.refresh(true);
		Compat.setScreen(new RustyScreen());
	}

	public void openConfig(Module module) {
		configModule = module;
		switchPage(Page.CONFIG);
	}

	void switchPage(Page next) {
		if (next != Page.CONFIG) {
			lastPage = next;
		}
		page = next;
		scroll = 0;
		scrollTarget = 0;
		focused = null;
		listening = null;
		configView.closePopups();
		ui.setAnim("page", 0f);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partial) {
		ClientSettings settings = ClientSettings.get();
		if (settings == null || settings.blurBehind.on()) {
			extractBlurredBackground(g);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;
		if (mouseX < 0) {
			mouseX = mx;
			mouseY = my;
		}
		ClientSettings settings = ClientSettings.get();
		float userScale = settings == null ? 1f : settings.uiScale.f();
		int ww = Draw.windowWidth();
		int wh = Draw.windowHeight();
		scale = Math.max(0.45f, Math.min(ww * DEFAULT_FIT / PANEL_W, wh * DEFAULT_FIT / PANEL_H) * userScale);
		scale = Math.min(scale, Math.min(ww * 0.98f / PANEL_W, wh * 0.98f / PANEL_H));
		float openT = Math.min(1f, (now - opened) / 1e9f / 0.18f);
		float closeT = closing ? Math.min(1f, (now - closeStart) / 1e9f / 0.12f) : 0f;
		boolean anim = settings == null || settings.animations.on();
		float appear = anim ? ease(openT) * (1f - closeT) : (closing ? 0f : 1f);
		if (closing && (closeT >= 1f || !anim)) {
			finishClose();
			return;
		}
		g.fill(0, 0, width, height, Theme.withAlpha(0xFF000000, Math.round(110 * appear)));
		Draw d = new Draw(g, scale);
		float offset = (1f - appear) * 14f * scale;
		panelX = Math.round((ww - PANEL_W * scale) / 2f);
		panelY = Math.round((wh - PANEL_H * scale) / 2f + offset);
		d.origin(panelX, panelY);
		d.alpha(appear);
		float gs = Draw.guiScale();
		float umx = (float) ((mouseX * gs - panelX) / scale);
		float umy = (float) ((mouseY * gs - panelY) / scale);
		ui.begin(d, umx, umy, dt);
		drawFrame();
		float pageT = ui.anim("page", 1f, 14f);
		float baseAlpha = appear;
		d.alpha(baseAlpha * (0.35f + 0.65f * pageT));
		switch (page) {
			case MODULES -> drawModules();
			case CONFIG -> configView.draw(configModule);
			case PROFILE -> profileView.draw();
			case SETTINGS -> configView.draw(ClientSettings.get());
		}
		d.alpha(baseAlpha);
		configView.drawPopups();
		ui.end();
		d.end();
	}

	private static float ease(float t) {
		float u = 1f - t;
		return 1f - u * u * u;
	}

	private void drawFrame() {
		Draw d = ui.d;
		d.shadow(0, 0, PANEL_W, PANEL_H, 16, 28, 0x8C000000);
		d.round(0, 0, PANEL_W, PANEL_H, 16, Theme.MAIN);
		d.ring(0, 0, PANEL_W, PANEL_H, 16, 1, Theme.BORDER);
		d.rect(RAIL_W, 1, 1, PANEL_H - 2, Theme.BORDER);
		ui.catchAll(0, 0, PANEL_W, PANEL_H);
		railButton("layout-grid", 68, page == Page.MODULES || page == Page.CONFIG && configModule != ClientSettings.get(), () -> switchPage(Page.MODULES));
		railButton("circle-user", 112, page == Page.PROFILE, () -> switchPage(Page.PROFILE));
		railButton("settings", 156, page == Page.SETTINGS, () -> switchPage(Page.SETTINGS));
		float ex = 12.5f, ey = PANEL_H - 95;
		boolean hoverEdit = ui.hover(ex, ey, 38, 38);
		d.round(ex, ey, 38, 38, 8, hoverEdit ? Theme.SURFACE_3 : Theme.SURFACE);
		d.icon("grid-2x2-plus", ex + 19, ey + 19, 18, hoverEdit ? Theme.TEXT : Theme.TEXT_3);
		ui.click(ex, ey, 38, 38, () -> Compat.setScreen(new HudEditorScreen(this)));
		float cy = PANEL_H - 33;
		boolean hoverClose = ui.hover(ex, cy - 19, 38, 38);
		if (hoverClose) {
			d.round(ex, cy - 19, 38, 38, 8, Theme.SURFACE);
		}
		d.icon("x", ex + 19, cy, 17, hoverClose ? Theme.TEXT : Theme.MUTED);
		ui.click(ex, cy - 19, 38, 38, this::onClose);
	}

	private void railButton(String icon, float y, boolean active, Runnable action) {
		Draw d = ui.d;
		float x = 12.5f;
		boolean hover = ui.hover(x, y, 38, 38);
		float t = ui.anim("rail" + icon, active ? 1f : 0f, 16f);
		if (t > 0.01f) {
			d.round(x, y, 38, 38, 8, Theme.fade(Theme.accent(), t));
		} else if (hover) {
			d.round(x, y, 38, 38, 8, Theme.SURFACE);
		}
		int color = active ? Theme.INK : (hover ? Theme.TEXT : Theme.MUTED);
		d.icon(icon, x + 19, y + 19, 18, color);
		ui.click(x, y, 38, 38, action);
	}

	private List<Module> visibleModules() {
		List<Module> list = new ArrayList<>();
		String q = search.text.trim().toLowerCase(Locale.ROOT);
		for (Module module : Modules.all()) {
			if (module.hidden()) {
				continue;
			}
			if (category != null && module.category != category) {
				continue;
			}
			if (!q.isEmpty() && !module.name.toLowerCase(Locale.ROOT).contains(q) && !module.category.label.toLowerCase(Locale.ROOT).contains(q)) {
				continue;
			}
			list.add(module);
		}
		return list;
	}

	private void drawModules() {
		Draw d = ui.d;
		d.textMid("Modules", CONTENT_X, 39, Fonts.Style.BOLD, 18, Theme.TEXT);
		float x = CONTENT_X + d.width("Modules", Fonts.Style.BOLD, 18) + 16;
		x = chip("All", x, category == null, () -> setCategory(null));
		for (Category c : Category.values()) {
			Category target = c;
			x = chip(c.label, x, category == c, () -> setCategory(target));
		}
		float sw = 230, sh = 36;
		float sx = PANEL_W - PAD_R - sw, sy = 21;
		boolean focusedSearch = focused == search;
		boolean hoverSearch = ui.hover(sx, sy, sw, sh);
		d.round(sx, sy, sw, sh, 10, Theme.SURFACE);
		if (focusedSearch) {
			d.ring(sx, sy, sw, sh, 10, 1, Theme.accent());
		} else if (hoverSearch) {
			d.ring(sx, sy, sw, sh, 10, 1, Theme.SURFACE_3);
		}
		d.icon("search", sx + 20, sy + sh / 2f, 13, Theme.MUTED);
		search.draw(ui, sx + 38, sy + sh / 2f, sw - 50, "Search modules", focusedSearch, Fonts.Style.MEDIUM, 13);
		ui.click(sx, sy, sw, sh, () -> focused = search);

		List<Module> list = visibleModules();
		float gx = CONTENT_X, gy = 78;
		float gw = PANEL_W - CONTENT_X - PAD_R;
		float gap = 12, vgap = 13;
		float cw = (gw - gap * 3) / 4f, ch = 185;
		viewHeight = PANEL_H - gy - 1;
		int rows = (list.size() + 3) / 4;
		contentHeight = rows * ch + Math.max(0, rows - 1) * vgap + 20;
		scroll = ui.anim("scroll", scrollTarget, 18f);
		clampScroll();
		d.scissor(gx - 2, gy, gw + 4, viewHeight);
		ui.clip(gx - 2, gy, gw + 4, viewHeight);
		for (int i = 0; i < list.size(); i++) {
			int col = i % 4, row = i / 4;
			float cx = gx + col * (cw + gap);
			float cy = gy + row * (ch + vgap) - scroll;
			if (cy + ch < gy - 4 || cy > gy + viewHeight + 4) {
				continue;
			}
			card(list.get(i), cx, cy, cw, ch);
		}
		if (list.isEmpty()) {
			d.textCenter("No modules match your search", gx + gw / 2f, gy + 80, Fonts.Style.MEDIUM, 13, Theme.MUTED);
		}
		ui.resetClip();
		d.endScissor();
		scrollbar(gx + gw + 7, gy + 4, viewHeight - 8);
	}

	void scrollbar(float x, float y, float h) {
		if (contentHeight <= viewHeight + 1) {
			return;
		}
		float ratio = viewHeight / contentHeight;
		float bh = Math.max(30, h * ratio);
		float max = contentHeight - viewHeight;
		float by = y + (h - bh) * (scroll / max);
		ui.d.round(x, by, 4, bh, 2, Theme.SURFACE_3);
	}

	void clampScroll() {
		float max = Math.max(0, contentHeight - viewHeight);
		if (scrollTarget > max) {
			scrollTarget = max;
		}
		if (scrollTarget < 0) {
			scrollTarget = 0;
		}
		if (scroll > max + 0.5f) {
			scroll = max;
			ui.setAnim("scroll", max);
		}
	}

	private void setCategory(Category c) {
		category = c;
		lastCategory = c;
		scrollTarget = 0;
	}

	private float chip(String label, float x, boolean active, Runnable action) {
		Draw d = ui.d;
		float w = d.width(label, Fonts.Style.SEMIBOLD, 13) + 28;
		float h = 30, y = 24;
		boolean hover = ui.hover(x, y, w, h);
		float t = ui.anim("chip" + label, active ? 1f : 0f, 16f);
		int bg = Theme.mix(hover ? Theme.SURFACE_3 : Theme.SURFACE_2, Theme.accent(), t);
		d.round(x, y, w, h, h / 2f, bg);
		int fg = Theme.mix(hover ? Theme.TEXT : Theme.TEXT_3, Theme.INK, t);
		d.textCenter(label, x + w / 2f, y + h / 2f, Fonts.Style.SEMIBOLD, 13, fg);
		ui.click(x, y, w, h, action);
		return x + w + 9;
	}

	private void card(Module module, float x, float y, float w, float h) {
		Draw d = ui.d;
		boolean hover = ui.hover(x, y, w, h);
		float ht = ui.anim("card" + module.id, hover ? 1f : 0f, 16f);
		d.round(x, y, w, h, 12, Theme.mix(Theme.SURFACE, Theme.SURFACE_2, ht));
		float et = ui.anim("en" + module.id, module.enabled() ? 1f : 0f, 14f);
		d.icon(module.icon, x + w / 2f, y + 62, 32, Theme.mix(Theme.MUTED, Theme.accent(), et));
		d.textCenter(module.name, x + w / 2f, y + 119, Fonts.Style.BOLD, 14, Theme.TEXT);
		float bx = x + 14, by = y + 140, bw = w - 28, bh = 32;
		boolean hoverButton = ui.hover(bx, by, bw, bh);
		int off = hoverButton ? 0xFF2E2B2A : 0xFF262423;
		int on = hoverButton ? Theme.accentHover() : Theme.accent();
		d.round(bx, by, bw, bh, 8, Theme.mix(off, on, et));
		d.textCenter(module.enabled() ? "Enabled" : "Disabled", bx + bw / 2f, by + bh / 2f, Fonts.Style.BOLD, 12.5f, Theme.mix(Theme.MUTED, Theme.INK, et));
		ui.click(x, y, w, h, b -> {
			if (b == 0 || b == 1) {
				openConfig(module);
			}
		});
		ui.click(bx, by, bw, bh, b -> {
			if (b == 0) {
				module.toggle();
			} else if (b == 1) {
				openConfig(module);
			}
		});
	}

	@Override
	public void mouseMoved(double x, double y) {
		mouseX = x;
		mouseY = y;
	}

	private float ux(double x) {
		return (float) ((x * Draw.guiScale() - panelX) / scale);
	}

	private float uy(double y) {
		return (float) ((y * Draw.guiScale() - panelY) / scale);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		mouseX = event.x();
		mouseY = event.y();
		float x = ux(event.x()), y = uy(event.y());
		if (listening != null) {
			listening = null;
			return true;
		}
		TextInput before = focused;
		focused = null;
		if (configView.popupPress(x, y, Ui.button(event))) {
			return true;
		}
		boolean hit = ui.press(x, y, Ui.button(event));
		if (focused == null && before != null && before != search) {
			before.commit();
		}
		if (!hit && Ui.button(event) == 0 && (x < 0 || y < 0 || x > PANEL_W || y > PANEL_H)) {
			onClose();
		}
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		ui.release();
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		mouseX = event.x();
		mouseY = event.y();
		ui.dragTo(ux(event.x()), uy(event.y()));
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double h, double v) {
		if (configView.popupScroll(v)) {
			return true;
		}
		scrollTarget -= (float) v * 60f;
		clampScroll();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (listening != null) {
			if (event.isEscape()) {
				listening.set(KeySetting.NONE);
			} else {
				listening.set(InputConstants.getKey(event).getName());
			}
			listening = null;
			return true;
		}
		if (focused != null) {
			if (event.isEscape() || event.isConfirmation()) {
				focused.commit();
				focused = null;
				return true;
			}
			focused.key(event);
			return true;
		}
		if (configView.popupKey(event)) {
			return true;
		}
		if (event.isEscape()) {
			if (page == Page.CONFIG) {
				switchPage(lastPage == Page.CONFIG ? Page.MODULES : lastPage);
				return true;
			}
			onClose();
			return true;
		}
		ClientSettings settings = ClientSettings.get();
		if (settings != null && InputConstants.getKey(event).getName().equals(settings.menuKey.get())) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (focused != null) {
			focused.type(event.codepointAsString());
			return true;
		}
		if (page == Page.MODULES && listening == null && event.isAllowedChatCharacter()) {
			String s = event.codepointAsString();
			if (!s.isBlank()) {
				focused = search;
				search.type(s);
			}
			return true;
		}
		return false;
	}

	@Override
	public void onClose() {
		if (focused != null) {
			focused.commit();
		}
		ClientSettings settings = ClientSettings.get();
		if (settings != null && !settings.animations.on()) {
			finishClose();
			return;
		}
		if (!closing) {
			closing = true;
			closeStart = System.nanoTime();
		}
	}

	private void finishClose() {
		Config.save();
		Compat.setScreen(null);
	}

	void resetOpenAnimation() {
		opened = System.nanoTime();
		closing = false;
		lastFrame = System.nanoTime();
	}

	@Override
	public void removed() {
		Config.save();
	}

	public static void resetHud() {
		for (HudModule hud : Modules.hud()) {
			hud.resetPosition();
		}
		RustyClient.LOG.info("hud layout reset");
	}
}
