package com.rustyclient.render;

import java.awt.Font;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public final class Fonts {
	public enum Style {
		MEDIUM("outfit-medium.ttf"),
		SEMIBOLD("outfit-semibold.ttf"),
		BOLD("outfit-bold.ttf");

		final String file;

		Style(String file) {
			this.file = file;
		}
	}

	private static final Map<Style, Font> BASE = new EnumMap<>(Style.class);
	private static final Map<Long, FontAtlas> ATLASES = new HashMap<>();

	private Fonts() {
	}

	public static FontAtlas atlas(Style style, int pixelSize) {
		int size = Math.max(4, Math.min(160, pixelSize));
		long key = ((long) style.ordinal() << 32) | size;
		FontAtlas atlas = ATLASES.get(key);
		if (atlas == null) {
			atlas = new FontAtlas(base(style), size);
			ATLASES.put(key, atlas);
		}
		return atlas;
	}

	public static void clear() {
		for (FontAtlas atlas : ATLASES.values()) {
			atlas.close();
		}
		ATLASES.clear();
	}

	private static Font base(Style style) {
		return BASE.computeIfAbsent(style, s -> {
			try (InputStream in = Fonts.class.getResourceAsStream("/assets/rustyclient/font/" + s.file)) {
				if (in == null) {
					return new Font(Font.SANS_SERIF, Font.PLAIN, 12);
				}
				return Font.createFont(Font.TRUETYPE_FONT, in);
			} catch (Exception e) {
				return new Font(Font.SANS_SERIF, Font.PLAIN, 12);
			}
		});
	}
}
