package com.rustyclient.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class SlotsSetting extends Setting<Integer> {
	public static final int SLOTS = 9;

	public SlotsSetting(String id, String name, String section) {
		super(id, name, section, 0);
	}

	public boolean selected(int slot) {
		return (value & (1 << slot)) != 0;
	}

	public boolean none() {
		return value == 0;
	}

	public boolean protects(int slot) {
		return none() || selected(slot);
	}

	public void toggle(int slot) {
		set(value ^ (1 << slot));
	}

	@Override
	protected Integer sanitize(Integer v) {
		return v == null ? null : v & ((1 << SLOTS) - 1);
	}

	@Override
	public JsonElement save() {
		return new JsonPrimitive(value);
	}

	@Override
	public void load(JsonElement element) {
		if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
			Integer clean = sanitize(element.getAsInt());
			if (clean != null) {
				value = clean;
			}
		}
	}
}
