package com.rustyclient.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class TextSetting extends Setting<String> {
	public final int maxLength;

	public TextSetting(String id, String name, String section, String defaultValue, int maxLength) {
		super(id, name, section, defaultValue);
		this.maxLength = maxLength;
	}

	@Override
	protected String sanitize(String v) {
		if (v == null) {
			return null;
		}
		return v.length() > maxLength ? v.substring(0, maxLength) : v;
	}

	@Override
	public JsonElement save() {
		return new JsonPrimitive(value);
	}

	@Override
	public void load(JsonElement element) {
		if (element != null && element.isJsonPrimitive()) {
			String clean = sanitize(element.getAsString());
			if (clean != null) {
				value = clean;
			}
		}
	}
}
