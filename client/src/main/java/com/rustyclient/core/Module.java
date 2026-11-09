package com.rustyclient.core;

import com.rustyclient.core.setting.BoolSetting;
import com.rustyclient.core.setting.ChoiceSetting;
import com.rustyclient.core.setting.ColorSetting;
import com.rustyclient.core.setting.KeySetting;
import com.rustyclient.core.setting.NumberSetting;
import com.rustyclient.core.setting.Setting;
import com.rustyclient.core.setting.TextSetting;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleFunction;

public abstract class Module {
	public static final String GENERAL = "General";
	public static final String APPEARANCE = "Appearance";
	public static final String KEYBIND = "Keybind";

	public final String id;
	public final String name;
	public final String icon;
	public final Category category;
	public final List<Setting<?>> settings = new ArrayList<>();
	public final KeySetting toggleKey;
	protected final Minecraft mc = Minecraft.getInstance();
	private final boolean defaultEnabled;
	private boolean enabled;
	private boolean keyWasDown;

	protected Module(String name, String icon, Category category, boolean defaultEnabled) {
		this.name = name;
		this.icon = icon;
		this.category = category;
		this.id = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
		this.defaultEnabled = defaultEnabled;
		this.enabled = defaultEnabled;
		this.toggleKey = new KeySetting("toggle_key", "Toggle key", KEYBIND, KeySetting.NONE);
	}

	public final void finishSettings() {
		if (hasToggleKey()) {
			settings.add(toggleKey);
		}
	}

	protected boolean hasToggleKey() {
		return true;
	}

	public boolean hidden() {
		return false;
	}

	public boolean enabled() {
		return enabled;
	}

	public boolean defaultEnabled() {
		return defaultEnabled;
	}

	public void setEnabled(boolean value) {
		if (value == enabled) {
			return;
		}
		enabled = value;
		if (value) {
			onEnable();
		} else {
			onDisable();
		}
		Config.markDirty();
	}

	public void loadEnabled(boolean value) {
		enabled = value;
	}

	public void toggle() {
		setEnabled(!enabled);
	}

	protected void onEnable() {
	}

	protected void onDisable() {
	}

	public void tick() {
	}

	public void alwaysTick() {
	}

	public final void pollKey(boolean down) {
		if (down && !keyWasDown) {
			onKeyPressed();
		}
		keyWasDown = down;
	}

	protected void onKeyPressed() {
		toggle();
	}

	protected <S extends Setting<?>> S add(S setting) {
		settings.add(setting);
		return setting;
	}

	protected BoolSetting bool(String name, String section, boolean def) {
		return add(new BoolSetting(key(name), name, section, def));
	}

	protected NumberSetting number(String name, String section, double def, double min, double max, double step, DoubleFunction<String> format) {
		return add(new NumberSetting(key(name), name, section, def, min, max, step, format));
	}

	protected ChoiceSetting choice(String name, String section, String def, String... options) {
		return add(new ChoiceSetting(key(name), name, section, def, options));
	}

	protected ColorSetting color(String name, String section, int def) {
		return add(new ColorSetting(key(name), name, section, def, false));
	}

	protected ColorSetting colorAlpha(String name, String section, int def) {
		return add(new ColorSetting(key(name), name, section, def, true));
	}

	protected KeySetting key(String name, String section, String def) {
		return add(new KeySetting(key(name), name, section, def));
	}

	protected TextSetting text(String name, String section, String def, int max) {
		return add(new TextSetting(key(name), name, section, def, max));
	}

	private static String key(String name) {
		return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
	}
}
