package com.rustyclient.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Modules {
	private static final List<Module> ALL = new ArrayList<>();
	private static final Map<Class<?>, Module> BY_CLASS = new HashMap<>();
	private static final List<HudModule> HUD = new ArrayList<>();

	private Modules() {
	}

	public static void register(Module module) {
		module.finishSettings();
		ALL.add(module);
		BY_CLASS.put(module.getClass(), module);
		if (module instanceof HudModule hud) {
			HUD.add(hud);
		}
	}

	public static List<Module> all() {
		return Collections.unmodifiableList(ALL);
	}

	public static List<HudModule> hud() {
		return Collections.unmodifiableList(HUD);
	}

	@SuppressWarnings("unchecked")
	public static <T extends Module> T get(Class<T> type) {
		return (T) BY_CLASS.get(type);
	}

	public static boolean on(Class<? extends Module> type) {
		Module module = BY_CLASS.get(type);
		return module != null && module.enabled();
	}
}
