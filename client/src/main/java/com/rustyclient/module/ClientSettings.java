package com.rustyclient.module;

import com.rustyclient.core.Category;
import com.rustyclient.core.Module;
import com.rustyclient.core.Modules;
import com.rustyclient.core.RustyUsers;
import com.rustyclient.core.setting.BoolSetting;
import com.rustyclient.core.setting.ChoiceSetting;
import com.rustyclient.core.setting.ColorSetting;
import com.rustyclient.core.setting.KeySetting;
import com.rustyclient.core.setting.NumberSetting;

public final class ClientSettings extends Module {
	public final KeySetting menuKey;
	public final NumberSetting uiScale;
	public final BoolSetting animations;
	public final BoolSetting blurBehind;
	public final BoolSetting followLauncher;
	public final ColorSetting accent;
	public final BoolSetting snapping;
	public final BoolSetting hideInF3;
	public final ChoiceSetting hudFont;
	public final ChoiceSetting textShadow;
	public final NumberSetting hudRadius;
	public final BoolSetting nametagIcon;
	public final BoolSetting tablistIcon;
	public final BoolSetting shareUsage;

	public ClientSettings() {
		super("Client", "settings", Category.UTILITY, true);
		menuKey = key("Menu key", "Controls", "key.keyboard.right.shift");
		snapping = bool("Snap HUD elements", "Controls", true);
		uiScale = number("Menu scale", "Interface", 1.0, 0.75, 1.5, 0.05, NumberSetting.times());
		animations = bool("Animations", "Interface", true);
		blurBehind = bool("Blur behind menu", "Interface", true);
		hideInF3 = bool("Hide HUD in debug screen", "Interface", true);
		followLauncher = bool("Use launcher accent", "Accent", true);
		accent = color("Accent color", "Accent", 0xFF1AB0DD);
		accent.visibleWhen(() -> !followLauncher.on());
		hudFont = choice("HUD font", "Text", "Vanilla", "Vanilla", "Rusty Client");
		textShadow = choice("Text shadow", "Text", "Per module", "Per module", "Always", "Never");
		hudRadius = number("HUD border radius", "HUD style", 0, 0, 12, 0.5, NumberSetting.decimal("px"));
		nametagIcon = bool("Rusty Client icon on nametags", "Rusty Client users", true);
		tablistIcon = bool("Rusty Client icon in tab list", "Rusty Client users", true);
		shareUsage = bool("Show others that I use Rusty Client", "Rusty Client users", true);
	}

	public static ClientSettings get() {
		return Modules.get(ClientSettings.class);
	}

	@Override
	public void alwaysTick() {
		RustyUsers.tick(mc);
	}

	@Override
	public boolean hidden() {
		return true;
	}

	@Override
	protected boolean hasToggleKey() {
		return false;
	}

	@Override
	public boolean enabled() {
		return true;
	}
}
