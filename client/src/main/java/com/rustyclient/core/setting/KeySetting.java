package com.rustyclient.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class KeySetting extends Setting<String> {
	public static final String NONE = "key.keyboard.unknown";

	public KeySetting(String id, String name, String section, String defaultValue) {
		super(id, name, section, defaultValue);
	}

	public boolean bound() {
		return !NONE.equals(value);
	}

	@Override
	public JsonElement save() {
		return new JsonPrimitive(value);
	}

	@Override
	public void load(JsonElement element) {
		if (element != null && element.isJsonPrimitive()) {
			value = element.getAsString();
		}
	}
}
