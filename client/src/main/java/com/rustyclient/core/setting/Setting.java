package com.rustyclient.core.setting;

import com.google.gson.JsonElement;
import com.rustyclient.core.Config;

import java.util.function.BooleanSupplier;

public abstract class Setting<T> {
	public final String id;
	public final String name;
	public final String section;
	public final T defaultValue;
	protected T value;
	private BooleanSupplier visible = () -> true;
	private Runnable onChange = () -> {
	};

	protected Setting(String id, String name, String section, T defaultValue) {
		this.id = id;
		this.name = name;
		this.section = section;
		this.value = defaultValue;
		this.defaultValue = defaultValue;
	}

	public T get() {
		return value;
	}

	public void set(T value) {
		T clean = sanitize(value);
		if (clean == null || clean.equals(this.value)) {
			return;
		}
		this.value = clean;
		onChange.run();
		Config.markDirty();
	}

	public void reset() {
		set(defaultValue);
	}

	protected T sanitize(T value) {
		return value;
	}

	public boolean visible() {
		return visible.getAsBoolean();
	}

	public Setting<T> visibleWhen(BooleanSupplier supplier) {
		this.visible = supplier;
		return this;
	}

	public Setting<T> onChange(Runnable runnable) {
		this.onChange = runnable;
		return this;
	}

	public abstract JsonElement save();

	public abstract void load(JsonElement element);
}
