package com.rustyclient.render;

import java.util.HashMap;
import java.util.Map;

public final class Shapes {
	private static final Map<Long, Tex> CACHE = new HashMap<>();

	private Shapes() {
	}

	public static Tex get(int radius, int thickness, int blur) {
		long key = ((long) radius << 40) | ((long) (thickness + 1) << 20) | blur;
		Tex tex = CACHE.get(key);
		if (tex == null) {
			tex = build(radius, thickness, blur);
			CACHE.put(key, tex);
		}
		return tex;
	}

	public static void clear() {
		for (Tex tex : CACHE.values()) {
			tex.close();
		}
		CACHE.clear();
	}

	private static Tex build(int radius, int thickness, int blur) {
		int n = radius + blur;
		int size = n * 2 + 1;
		float[] coverage = new float[size * size];
		float c0 = n;
		float c1 = n + 1;
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				float px = x + 0.5f;
				float py = y + 0.5f;
				float qx = Math.max(0f, Math.max(c0 - px, px - c1));
				float qy = Math.max(0f, Math.max(c0 - py, py - c1));
				float dist = qx > 0f && qy > 0f ? (float) Math.sqrt(qx * qx + qy * qy) - radius : Math.max(qx, qy) - radius;
				float value;
				if (blur > 0) {
					float t = clamp(dist / blur);
					float fall = 1f - t;
					value = fall * fall * (3f - 2f * fall);
					value *= value;
				} else if (thickness < 0) {
					value = 1f - clamp(0.5f - dist);
				} else if (thickness > 0) {
					value = clamp(0.5f - dist) * clamp(0.5f + dist + thickness);
				} else {
					value = clamp(0.5f - dist);
				}
				coverage[y * size + x] = value;
			}
		}
		return Tex.alpha("shape", size, size, coverage);
	}

	private static float clamp(float v) {
		return Math.max(0f, Math.min(1f, v));
	}
}
