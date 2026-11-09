package com.rustyclient.render;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

public final class FontAtlas {
	private static final int SIZE = 1024;
	private static final int PAD = 2;
	private static final int[] CONTRAST = new int[256];

	static {
		for (int i = 0; i < 256; i++) {
			CONTRAST[i] = Math.round((float) Math.pow(i / 255.0, 0.8) * 255f);
		}
	}

	public record Glyph(int u, int v, int w, int h, int ox, int oy, float advance) {
	}

	private final Font font;
	private final FontRenderContext frc;
	private final BufferedImage canvas;
	private final Map<Integer, Glyph> glyphs = new HashMap<>();
	public final float ascent;
	public final float descent;
	public final float capHeight;
	public final float xHeight;
	private Tex tex;
	private int cursorX = PAD;
	private int cursorY = PAD;
	private int rowHeight;
	private boolean dirty;
	private int dirtyTop = Integer.MAX_VALUE;
	private int dirtyBottom;

	public FontAtlas(Font base, int pixelSize) {
		this.font = base.deriveFont((float) pixelSize);
		this.frc = new FontRenderContext(null, true, false);
		this.canvas = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
		LineMetrics metrics = font.getLineMetrics("Hxg", frc);
		this.ascent = metrics.getAscent();
		this.descent = metrics.getDescent();
		this.capHeight = (float) -font.createGlyphVector(frc, "H").getVisualBounds().getY();
		this.xHeight = (float) -font.createGlyphVector(frc, "x").getVisualBounds().getY();
		for (int c = 32; c < 127; c++) {
			glyph(c);
		}
	}

	public Tex texture() {
		if (tex == null) {
			tex = Tex.image("font", canvas);
			dirty = false;
			dirtyTop = Integer.MAX_VALUE;
			dirtyBottom = 0;
		} else if (dirty) {
			tex.write(canvas, 0, dirtyTop, SIZE, dirtyBottom - dirtyTop);
			tex.upload();
			dirty = false;
			dirtyTop = Integer.MAX_VALUE;
			dirtyBottom = 0;
		}
		return tex;
	}

	public int size() {
		return SIZE;
	}

	public Glyph glyph(int codepoint) {
		Glyph glyph = glyphs.get(codepoint);
		if (glyph != null) {
			return glyph;
		}
		glyph = rasterize(codepoint);
		glyphs.put(codepoint, glyph);
		return glyph;
	}

	public float width(String text) {
		float w = 0;
		for (int i = 0; i < text.length(); ) {
			int cp = text.codePointAt(i);
			w += glyph(cp).advance();
			i += Character.charCount(cp);
		}
		return w;
	}

	public void close() {
		if (tex != null) {
			tex.close();
		}
	}

	private void sharpen(int x0, int y0, int w, int h) {
		int[] row = new int[w];
		for (int y = y0; y < y0 + h; y++) {
			canvas.getRGB(x0, y, w, 1, row, 0, w);
			for (int i = 0; i < w; i++) {
				int a = row[i] >>> 24;
				if (a > 0 && a < 255) {
					row[i] = (CONTRAST[a] << 24) | (row[i] & 0xFFFFFF);
				}
			}
			canvas.setRGB(x0, y, w, 1, row, 0, w);
		}
	}

	private Glyph rasterize(int codepoint) {
		String s = new String(Character.toChars(codepoint));
		if (!font.canDisplay(codepoint)) {
			s = "?";
		}
		GlyphVector gv = font.createGlyphVector(frc, s);
		float advance = (float) gv.getGlyphMetrics(0).getAdvanceX();
		Rectangle bounds = gv.getPixelBounds(frc, 0, 0);
		if (bounds.width <= 0 || bounds.height <= 0) {
			return new Glyph(0, 0, 0, 0, 0, 0, advance);
		}
		int w = bounds.width + 2;
		int h = bounds.height + 2;
		if (cursorX + w + PAD > SIZE) {
			cursorX = PAD;
			cursorY += rowHeight + PAD;
			rowHeight = 0;
		}
		if (cursorY + h + PAD > SIZE) {
			return new Glyph(0, 0, 0, 0, 0, 0, advance);
		}
		int gx = cursorX;
		int gy = cursorY;
		Graphics2D g = canvas.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g.setColor(java.awt.Color.WHITE);
		g.drawGlyphVector(gv, gx + 1 - bounds.x, gy + 1 - bounds.y);
		g.dispose();
		sharpen(gx, gy, w, h);
		cursorX += w + PAD;
		rowHeight = Math.max(rowHeight, h);
		dirty = true;
		dirtyTop = Math.min(dirtyTop, gy);
		dirtyBottom = Math.max(dirtyBottom, gy + h);
		return new Glyph(gx, gy, w, h, bounds.x - 1, bounds.y - 1, advance);
	}
}
