package com.rustyclient.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class ColorSetting extends Setting<Integer> {
	public final boolean alpha;

	public ColorSetting(String id, String name, String section, int defaultValue, boolean alpha) {
		super(id, name, section, defaultValue);
		this.alpha = alpha;
	}

	public int argb() {
		return value;
	}

	@Override
	protected Integer sanitize(Integer v) {
		if (v == null) {
			return null;
		}
		return alpha ? v : (v | 0xFF000000);
	}

	@Override
	public JsonElement save() {
		return new JsonPrimitive(String.format("#%08X", value));
	}

	@Override
	public void load(JsonElement element) {
		if (element == null || !element.isJsonPrimitive()) {
			return;
		}
		String s = element.getAsString().replace("#", "");
		try {
			long parsed = Long.parseLong(s, 16);
			int argb = s.length() <= 6 ? (int) (0xFF000000L | parsed) : (int) parsed;
			value = alpha ? argb : (argb | 0xFF000000);
		} catch (NumberFormatException ignored) {
		}
	}
}
