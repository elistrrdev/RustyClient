package com.rustyclient.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.rustyclient.core.Theme;
import com.rustyclient.render.Fonts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;

import java.util.function.Consumer;
import java.util.function.Predicate;

public final class TextInput {
	public String text = "";
	private final int max;
	private Consumer<String> onCommit = s -> {
	};
	private Consumer<String> onChange = s -> {
	};
	private Predicate<String> filter = s -> true;

	public TextInput(int max) {
		this.max = max;
	}

	public TextInput onCommit(Consumer<String> consumer) {
		this.onCommit = consumer;
		return this;
	}

	public TextInput onChange(Consumer<String> consumer) {
		this.onChange = consumer;
		return this;
	}

	public TextInput filter(Predicate<String> predicate) {
		this.filter = predicate;
		return this;
	}

	public void type(String s) {
		StringBuilder out = new StringBuilder(text);
		for (int i = 0; i < s.length(); ) {
			int cp = s.codePointAt(i);
			i += Character.charCount(cp);
			if (cp < 32 || cp == 127) {
				continue;
			}
			if (out.length() >= max) {
				break;
			}
			String next = out + new String(Character.toChars(cp));
			if (filter.test(next)) {
				out.appendCodePoint(cp);
			}
		}
		if (!out.toString().equals(text)) {
			text = out.toString();
			onChange.accept(text);
		}
	}

	public void key(KeyEvent event) {
		if (event.key() == InputConstants.KEY_BACKSPACE) {
			if (event.hasControlDown()) {
				text = "";
			} else if (!text.isEmpty()) {
				text = text.substring(0, text.offsetByCodePoints(text.length(), -1));
			}
			onChange.accept(text);
		} else if (event.isPaste()) {
			String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
			if (clip != null) {
				type(clip.replace("\n", " ").replace("\r", ""));
			}
		} else if (event.isCopy()) {
			Minecraft.getInstance().keyboardHandler.setClipboard(text);
		} else if (event.isSelectAll() || event.key() == InputConstants.KEY_DELETE) {
			text = "";
			onChange.accept(text);
		}
	}

	public void commit() {
		onCommit.accept(text);
	}

	public void draw(Ui ui, float x, float cy, float maxW, String placeholder, boolean focused, Fonts.Style style, float size) {
		String shown = text;
		while (!shown.isEmpty() && ui.d.width(shown, style, size) > maxW - 4) {
			shown = shown.substring(1);
		}
		float w = 0;
		if (shown.isEmpty() && !focused) {
			ui.d.textMid(placeholder, x, cy, style, size, Theme.MUTED);
		} else if (shown.isEmpty()) {
			ui.d.textMid(placeholder, x, cy, style, size, Theme.FAINT);
		} else {
			w = ui.d.textMid(shown, x, cy, style, size, Theme.TEXT);
		}
		if (focused && (System.currentTimeMillis() / 530) % 2 == 0) {
			ui.d.rect(x + w + 1, cy - size * 0.55f, 1.2f, size * 1.1f, Theme.accent());
		}
	}
}
