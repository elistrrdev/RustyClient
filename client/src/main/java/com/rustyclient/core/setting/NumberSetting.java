package com.rustyclient.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Locale;
import java.util.function.DoubleFunction;

public final class NumberSetting extends Setting<Double> {
	public final double min;
	public final double max;
	public final double step;
	private final DoubleFunction<String> format;

	public NumberSetting(String id, String name, String section, double defaultValue, double min, double max, double step, DoubleFunction<String> format) {
		super(id, name, section, defaultValue);
		this.min = min;
		this.max = max;
		this.step = step;
		this.format = format;
	}

	public static DoubleFunction<String> times() {
		return v -> String.format(Locale.ROOT, "%.2fx", v);
	}

	public static DoubleFunction<String> percent() {
		return v -> Math.round(v * 100) + "%";
	}

	public static DoubleFunction<String> integer(String suffix) {
		return v -> Math.round(v) + suffix;
	}

	public static DoubleFunction<String> decimal(String suffix) {
		return v -> String.format(Locale.ROOT, "%.1f", v) + suffix;
	}

	public double v() {
		return value;
	}

	public float f() {
		return value.floatValue();
	}

	public int i() {
		return (int) Math.round(value);
	}

	public String text() {
		return format.apply(value);
	}

	public double fraction() {
		return max == min ? 0 : (value - min) / (max - min);
	}

	public void setFraction(double fraction) {
		set(min + Math.max(0, Math.min(1, fraction)) * (max - min));
	}

	@Override
	protected Double sanitize(Double v) {
		if (v == null || v.isNaN()) {
			return null;
		}
		double clamped = Math.max(min, Math.min(max, v));
		if (step > 0) {
			clamped = min + Math.round((clamped - min) / step) * step;
			clamped = Math.max(min, Math.min(max, clamped));
			clamped = Math.round(clamped * 10000.0) / 10000.0;
		}
		return clamped;
	}

	@Override
	public JsonElement save() {
		return new JsonPrimitive(value);
	}

	@Override
	public void load(JsonElement element) {
		if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
			Double clean = sanitize(element.getAsDouble());
			if (clean != null) {
				value = clean;
			}
		}
	}
}
