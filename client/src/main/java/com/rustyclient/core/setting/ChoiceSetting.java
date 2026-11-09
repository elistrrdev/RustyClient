package com.rustyclient.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;

public final class ChoiceSetting extends Setting<String> {
	public final List<String> options;

	public ChoiceSetting(String id, String name, String section, String defaultValue, String... options) {
		super(id, name, section, defaultValue);
		this.options = List.of(options);
	}

	public boolean is(String option) {
		return value.equals(option);
	}

	public int index() {
		return Math.max(0, options.indexOf(value));
	}

	@Override
	protected String sanitize(String v) {
		return options.contains(v) ? v : null;
	}

	@Override
	public JsonElement save() {
		return new JsonPrimitive(value);
	}

	@Override
	public void load(JsonElement element) {
		if (element != null && element.isJsonPrimitive() && options.contains(element.getAsString())) {
			value = element.getAsString();
		}
	}
}
