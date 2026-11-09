package com.rustyclient.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

public final class Draw {
	public final GuiGraphicsExtractor g;
	public final float s;
	private final float guiScale;
	private float ox;
	private float oy;
	private float alpha = 1f;

	public Draw(GuiGraphicsExtractor g, float unitPx) {
		this.g = g;
		this.s = unitPx;
		this.guiScale = Minecraft.getInstance().getWindow().getGuiScale();
		g.pose().pushMatrix();
		g.pose().scale(1f / guiScale, 1f / guiScale);
	}

	public static float guiScale() {
		return Minecraft.getInstance().getWindow().getGuiScale();
	}

	public static int windowWidth() {
		return Minecraft.getInstance().getWindow().getWidth();
	}

	public static int windowHeight() {
		return Minecraft.getInstance().getWindow().getHeight();
	}

	public void end() {
		g.pose().popMatrix();
	}

	public void origin(float x, float y) {
		this.ox = x;
		this.oy = y;
	}

	public float originX() {
		return ox;
	}

	public float originY() {
		return oy;
	}

	public void alpha(float a) {
		this.alpha = Math.max(0f, Math.min(1f, a));
	}

	public float alpha() {
		return alpha;
	}

	public int px(float v) {
		return Math.round(v * s);
	}

	private int sx(float x) {
		return Math.round(ox + x * s);
	}

	private int sy(float y) {
		return Math.round(oy + y * s);
	}

	private int col(int argb) {
		if (alpha >= 1f) {
			return argb;
		}
		int a = Math.round((argb >>> 24) * alpha);
		return (a << 24) | (argb & 0xFFFFFF);
	}

	public void rect(float x, float y, float w, float h, int argb) {
		if ((argb >>> 24) == 0 || alpha <= 0f) {
			return;
		}
		g.fill(sx(x), sy(y), sx(x + w), sy(y + h), col(argb));
	}

	public void round(float x, float y, float w, float h, float r, int argb) {
		shape(x, y, w, h, r, 0, 0, argb);
	}

	public void ring(float x, float y, float w, float h, float r, float t, int argb) {
		shape(x, y, w, h, r, Math.max(1, Math.round(t * s)), 0, argb);
	}

	public void shadow(float x, float y, float w, float h, float r, float blur, int argb) {
		shape(x, y, w, h, r, 0, Math.max(1, Math.round(blur * s)), argb);
	}

	public void cornerMask(float x, float y, float w, float h, float r, int argb) {
		if ((argb >>> 24) == 0 || alpha <= 0f) {
			return;
		}
		int x0 = sx(x), y0 = sy(y), x1 = sx(x + w), y1 = sy(y + h);
		int radius = Math.max(1, Math.min(Math.round(r * s), Math.min(x1 - x0, y1 - y0) / 2));
		Tex tex = Shapes.get(radius, -1, 0);
		int size = tex.width;
		int c = col(argb);
		blit(tex, x0, y0, radius, radius, 0, 0, radius, radius, c);
		blit(tex, x1 - radius, y0, radius, radius, size - radius, 0, radius, radius, c);
		blit(tex, x0, y1 - radius, radius, radius, 0, size - radius, radius, radius, c);
		blit(tex, x1 - radius, y1 - radius, radius, radius, size - radius, size - radius, radius, radius, c);
	}

	public void gradientV(float x, float y, float w, float h, int top, int bottom) {
		if (alpha <= 0f) {
			return;
		}
		g.fillGradient(sx(x), sy(y), sx(x + w), sy(y + h), col(top), col(bottom));
	}

	public void pixelColumnGradients(float x, float y, float w, float h, java.util.function.IntFunction<int[]> column) {
		int x0 = sx(x), y0 = sy(y), x1 = sx(x + w), y1 = sy(y + h);
		int span = Math.max(1, x1 - x0);
		for (int px = x0; px < x1; px++) {
			int[] colors = column.apply(Math.round((px - x0) * 1000f / span));
			g.fillGradient(px, y0, px + 1, y1, col(colors[0]), col(colors[1]));
		}
	}

	public void circle(float cx, float cy, float r, int argb) {
		round(cx - r, cy - r, r * 2, r * 2, r, argb);
	}

	private void shape(float x, float y, float w, float h, float r, int thickness, int blur, int argb) {
		if ((argb >>> 24) == 0 || alpha <= 0f) {
			return;
		}
		int x0 = sx(x) - blur;
		int y0 = sy(y) - blur;
		int x1 = sx(x + w) + blur;
		int y1 = sy(y + h) + blur;
		int pw = x1 - x0;
		int ph = y1 - y0;
		if (pw <= 0 || ph <= 0) {
			return;
		}
		int radius = Math.round(r * s);
		radius = Math.max(0, Math.min(radius, Math.min(pw - blur * 2, ph - blur * 2) / 2));
		int n = radius + blur;
		if (n == 0) {
			g.fill(x0, y0, x1, y1, col(argb));
			return;
		}
		Tex tex = Shapes.get(radius, thickness, blur);
		int c = col(argb);
		int size = tex.width;
		int midW = pw - n * 2;
		int midH = ph - n * 2;
		int leftN = Math.min(n, pw / 2);
		int topN = Math.min(n, ph / 2);
		int rightN = pw - leftN - Math.max(0, midW);
		int bottomN = ph - topN - Math.max(0, midH);
		blit(tex, x0, y0, leftN, topN, 0, 0, leftN, topN, c);
		blit(tex, x1 - rightN, y0, rightN, topN, size - rightN, 0, rightN, topN, c);
		blit(tex, x0, y1 - bottomN, leftN, bottomN, 0, size - bottomN, leftN, bottomN, c);
		blit(tex, x1 - rightN, y1 - bottomN, rightN, bottomN, size - rightN, size - bottomN, rightN, bottomN, c);
		if (midW > 0) {
			blit(tex, x0 + leftN, y0, midW, topN, n, 0, 1, topN, c);
			blit(tex, x0 + leftN, y1 - bottomN, midW, bottomN, n, size - bottomN, 1, bottomN, c);
		}
		if (midH > 0) {
			blit(tex, x0, y0 + topN, leftN, midH, 0, n, leftN, 1, c);
			blit(tex, x1 - rightN, y0 + topN, rightN, midH, size - rightN, n, rightN, 1, c);
		}
		if (midW > 0 && midH > 0) {
			if (thickness == 0) {
				g.fill(x0 + leftN, y0 + topN, x0 + leftN + midW, y0 + topN + midH, c);
			}
		}
	}

	private void blit(Tex tex, int x, int y, int w, int h, int u, int v, int uw, int vh, int color) {
		if (w <= 0 || h <= 0 || uw <= 0 || vh <= 0) {
			return;
		}
		g.blit(RenderPipelines.GUI_TEXTURED, tex.id, x, y, (float) u, (float) v, w, h, uw, vh, tex.width, tex.height, color);
	}

	public void icon(String name, float cx, float cy, float size, int argb) {
		if ((argb >>> 24) == 0 || alpha <= 0f) {
			return;
		}
		int px = Math.max(2, Math.round(size * s));
		Tex tex = Icons.get(name, px);
		int x = Math.round(ox + cx * s - px / 2f);
		int y = Math.round(oy + cy * s - px / 2f);
		blit(tex, x, y, px, px, 0, 0, px, px, col(argb));
	}

	public FontAtlas font(Fonts.Style style, float size) {
		return Fonts.atlas(style, Math.round(size * s));
	}

	public float width(String text, Fonts.Style style, float size) {
		return font(style, size).width(text) / s;
	}

	public float text(String text, float x, float y, Fonts.Style style, float size, int argb) {
		FontAtlas atlas = font(style, size);
		float baseline = oy + y * s + atlas.ascent;
		return drawRun(atlas, text, ox + x * s, baseline, argb) / s;
	}

	public float textMid(String text, float x, float cy, Fonts.Style style, float size, int argb) {
		FontAtlas atlas = font(style, size);
		float baseline = oy + cy * s + atlas.capHeight / 2f;
		return drawRun(atlas, text, ox + x * s, baseline, argb) / s;
	}

	public void textCenter(String text, float cx, float cy, Fonts.Style style, float size, int argb) {
		FontAtlas atlas = font(style, size);
		float w = atlas.width(text);
		float baseline = oy + cy * s + atlas.capHeight / 2f;
		drawRun(atlas, text, ox + cx * s - w / 2f, baseline, argb);
	}

	public void textRight(String text, float rx, float cy, Fonts.Style style, float size, int argb) {
		FontAtlas atlas = font(style, size);
		float w = atlas.width(text);
		float baseline = oy + cy * s + atlas.capHeight / 2f;
		drawRun(atlas, text, ox + rx * s - w, baseline, argb);
	}

	public void textShadowed(String text, float x, float cy, Fonts.Style style, float size, int argb, boolean shadow) {
		if (shadow) {
			int a = (argb >>> 24) * 3 / 5;
			float off = Math.max(1f, s * 0.6f) / s;
			textMid(text, x + off, cy + off, style, size, (a << 24));
		}
		textMid(text, x, cy, style, size, argb);
	}

	private float drawRun(FontAtlas atlas, String text, float x, float baseline, int argb) {
		if ((argb >>> 24) == 0 || alpha <= 0f) {
			return atlas.width(text);
		}
		int c = col(argb);
		float pen = x;
		int by = Math.round(baseline);
		for (int i = 0; i < text.length(); ) {
			int cp = text.codePointAt(i);
			i += Character.charCount(cp);
			FontAtlas.Glyph glyph = atlas.glyph(cp);
			if (glyph.w() > 0) {
				Tex tex = atlas.texture();
				int gx = Math.round(pen) + glyph.ox();
				int gy = by + glyph.oy();
				blit(tex, gx, gy, glyph.w(), glyph.h(), glyph.u(), glyph.v(), glyph.w(), glyph.h(), c);
			}
			pen += glyph.advance();
		}
		atlas.texture();
		return pen - x;
	}

	public void item(net.minecraft.world.item.ItemStack stack, float x, float y, float size, boolean decorations) {
		if (stack == null || stack.isEmpty() || alpha <= 0.05f) {
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(ox + x * s, oy + y * s);
		float k = size * s / 16f;
		g.pose().scale(k, k);
		g.item(stack, 0, 0);
		if (decorations) {
			g.itemDecorations(Minecraft.getInstance().font, stack, 0, 0);
		}
		g.pose().popMatrix();
	}

	public void sprite(net.minecraft.resources.Identifier sprite, float x, float y, float size) {
		g.pose().pushMatrix();
		g.pose().translate(ox + x * s, oy + y * s);
		float k = size * s / 18f;
		g.pose().scale(k, k);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, 0, 0, 18, 18, col(0xFFFFFFFF));
		g.pose().popMatrix();
	}

	public void vanillaText(net.minecraft.network.chat.Component text, float x, float y, float size, int argb, boolean shadow) {
		g.pose().pushMatrix();
		g.pose().translate(ox + x * s, oy + y * s);
		float k = size * s / 9f;
		g.pose().scale(k, k);
		g.text(Minecraft.getInstance().font, text, 0, 0, col(argb), shadow);
		g.pose().popMatrix();
	}

	public static float vanillaWidth(net.minecraft.network.chat.Component text, float size) {
		return Minecraft.getInstance().font.width(text) * size / 9f;
	}

	public void scissor(float x, float y, float w, float h) {
		g.enableScissor(sx(x), sy(y), sx(x + w), sy(y + h));
	}

	public void endScissor() {
		g.disableScissor();
	}
}
