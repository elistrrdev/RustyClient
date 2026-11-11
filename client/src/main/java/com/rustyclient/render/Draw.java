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
	private boolean vanilla;
	private boolean hud;
	private boolean bold;

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

	public Draw hudText(boolean vanillaFont) {
		this.hud = true;
		this.vanilla = vanillaFont;
		return this;
	}

	public boolean vanillaFont() {
		return vanilla;
	}

	public Draw vanillaBold(boolean on) {
		this.bold = on;
		return this;
	}

	private float vanillaScale(float size) {
		float k = Math.max(0.25f, size * s / 9f);
		float whole = Math.round(k);
		return whole >= 1 && Math.abs(k - whole) < 0.12f ? whole : k;
	}

	private static net.minecraft.network.chat.Component boldText(String text) {
		return net.minecraft.network.chat.Component.literal(text).withStyle(net.minecraft.ChatFormatting.BOLD);
	}

	private float vanillaWidthPx(String text, float size) {
		net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
		return (bold ? font.width(boldText(text)) : font.width(text)) * vanillaScale(size);
	}

	private float vanillaVisualPx(String text, float size) {
		return Math.max(0, vanillaWidthPx(text, size) - vanillaScale(size));
	}

	private void vanillaRun(String text, float px, float topPx, float size, int argb, boolean shadow) {
		if ((argb >>> 24) == 0 || alpha <= 0f) {
			return;
		}
		float k = vanillaScale(size);
		g.pose().pushMatrix();
		g.pose().translate(Math.round(px), Math.round(topPx));
		g.pose().scale(k, k);
		if (bold) {
			g.text(Minecraft.getInstance().font, boldText(text), 0, 0, col(argb), shadow);
		} else {
			g.text(Minecraft.getInstance().font, text, 0, 0, col(argb), shadow);
		}
		g.pose().popMatrix();
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
		if ((argb >>> 24) == 0 || alpha <= 0f) {
			return;
		}
		int size = Math.max(1, Math.round(r * 2 * s));
		int x0 = Math.round(ox + cx * s - size / 2f);
		int y0 = Math.round(oy + cy * s - size / 2f);
		blit(Shapes.disc(size), x0, y0, size, size, 0, 0, size, size, col(argb));
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

	public void texture(Tex tex, int x, int y, int w, int h, int argb) {
		if ((argb >>> 24) == 0 || alpha <= 0f) {
			return;
		}
		blit(tex, x, y, w, h, 0, 0, w, h, col(argb));
	}

	public void image(Tex tex, float x, float y, float w, float h, int argb) {
		if (tex == null || (argb >>> 24) == 0 || alpha <= 0f) {
			return;
		}
		int x0 = sx(x), y0 = sy(y);
		blit(tex, x0, y0, sx(x + w) - x0, sy(y + h) - y0, 0, 0, tex.width, tex.height, col(argb));
	}

	public void region(Tex tex, float x, float y, float w, float h, int u, int v, int uw, int vh, int argb) {
		if (tex == null || (argb >>> 24) == 0 || alpha <= 0f) {
			return;
		}
		int x0 = sx(x), y0 = sy(y);
		blit(tex, x0, y0, sx(x + w) - x0, sy(y + h) - y0, u, v, uw, vh, col(argb));
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

	public float visualWidth(String text, Fonts.Style style, float size) {
		if (vanilla) {
			return vanillaVisualPx(text, size) / s;
		}
		return font(style, size).width(text) / s;
	}

	public float width(String text, Fonts.Style style, float size) {
		if (vanilla) {
			return vanillaWidthPx(text, size) / s;
		}
		return font(style, size).width(text) / s;
	}

	public float text(String text, float x, float y, Fonts.Style style, float size, int argb) {
		if (vanilla) {
			vanillaRun(text, ox + x * s, oy + y * s, size, argb, false);
			return vanillaWidthPx(text, size) / s;
		}
		FontAtlas atlas = font(style, size);
		float baseline = oy + y * s + atlas.ascent;
		return drawRun(atlas, text, ox + x * s, baseline, argb) / s;
	}

	public float textMid(String text, float x, float cy, Fonts.Style style, float size, int argb) {
		return textMid(text, x, cy, style, size, argb, false);
	}

	private float textMid(String text, float x, float cy, Fonts.Style style, float size, int argb, boolean shadow) {
		if (vanilla) {
			vanillaRun(text, ox + x * s, oy + cy * s - vanillaScale(size) * 3.5f, size, argb, shadow);
			return vanillaWidthPx(text, size) / s;
		}
		FontAtlas atlas = font(style, size);
		float baseline = oy + cy * s + atlas.capHeight / 2f;
		return drawRun(atlas, text, ox + x * s, baseline, argb) / s;
	}

	public void textCenter(String text, float cx, float cy, Fonts.Style style, float size, int argb) {
		if (vanilla) {
			float w = vanillaVisualPx(text, size);
			vanillaRun(text, ox + cx * s - w / 2f, oy + cy * s - vanillaScale(size) * 3.5f, size, argb, hudShadow(false));
			return;
		}
		FontAtlas atlas = font(style, size);
		float w = atlas.width(text);
		float baseline = oy + cy * s + atlas.capHeight / 2f;
		drawRun(atlas, text, ox + cx * s - w / 2f, baseline, argb);
	}

	public void textRight(String text, float rx, float cy, Fonts.Style style, float size, int argb) {
		if (vanilla) {
			float w = vanillaVisualPx(text, size);
			vanillaRun(text, ox + rx * s - w, oy + cy * s - vanillaScale(size) * 3.5f, size, argb, hudShadow(false));
			return;
		}
		FontAtlas atlas = font(style, size);
		float w = atlas.width(text);
		float baseline = oy + cy * s + atlas.capHeight / 2f;
		drawRun(atlas, text, ox + rx * s - w, baseline, argb);
	}

	public boolean hudShadow(boolean shadow) {
		if (!hud) {
			return shadow;
		}
		com.rustyclient.module.ClientSettings settings = com.rustyclient.module.ClientSettings.get();
		if (settings == null || settings.textShadow.is("Per module")) {
			return shadow;
		}
		return settings.textShadow.is("Always");
	}

	public void textShadowed(String text, float x, float cy, Fonts.Style style, float size, int argb, boolean shadow) {
		shadow = hudShadow(shadow);
		if (vanilla) {
			textMid(text, x, cy, style, size, argb, shadow);
			return;
		}
		if (shadow) {
			int a = (argb >>> 24) * 3 / 5;
			float off = Math.max(1f, s * 0.6f) / s;
			textMid(text, x + off, cy + off, style, size, (a << 24));
		}
		textMid(text, x, cy, style, size, argb);
	}

	private float drawRun(FontAtlas atlas, String text, float x, float baseline, int argb) {
		text = com.rustyclient.module.utility.NameProtect.apply(text);
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

	public void roundSides(float x, float y, float w, float h, float r, boolean top, boolean bottom, int argb) {
		if ((argb >>> 24) == 0 || alpha <= 0f) {
			return;
		}
		int x0 = sx(x), y0 = sy(y), x1 = sx(x + w), y1 = sy(y + h);
		int pw = x1 - x0, ph = y1 - y0;
		if (pw <= 0 || ph <= 0) {
			return;
		}
		int sides = (top ? 1 : 0) + (bottom ? 1 : 0);
		int radius = sides == 0 ? 0 : Math.max(0, Math.min(Math.round(r * s), Math.min(pw / 2, ph / sides)));
		int c = col(argb);
		if (radius == 0) {
			g.fill(x0, y0, x1, y1, c);
			return;
		}
		Tex tex = Shapes.get(radius, 0, 0);
		int size = tex.width;
		int midTop = y0, midBottom = y1;
		if (top) {
			blit(tex, x0, y0, radius, radius, 0, 0, radius, radius, c);
			blit(tex, x1 - radius, y0, radius, radius, size - radius, 0, radius, radius, c);
			if (pw > radius * 2) {
				g.fill(x0 + radius, y0, x1 - radius, y0 + radius, c);
			}
			midTop = y0 + radius;
		}
		if (bottom) {
			blit(tex, x0, y1 - radius, radius, radius, 0, size - radius, radius, radius, c);
			blit(tex, x1 - radius, y1 - radius, radius, radius, size - radius, size - radius, radius, radius, c);
			if (pw > radius * 2) {
				g.fill(x0 + radius, y1 - radius, x1 - radius, y1, c);
			}
			midBottom = y1 - radius;
		}
		if (midBottom > midTop) {
			g.fill(x0, midTop, x1, midBottom, c);
		}
	}

	public void knob(float cx, float cy, float r, boolean active, int color) {
		if (alpha <= 0f) {
			return;
		}
		int body = Math.max(2, Math.round(r * 2 * s));
		int edge = Math.max(1, Math.round(Math.max(0.75f, 1f / s) * s));
		int dot = Math.max(1, Math.round(r * (active ? 0.62f : 0.5f) * 2 * s));
		if ((body - dot) % 2 != 0) {
			dot += dot + 1 <= body ? 1 : -1;
		}
		int x0 = Math.round(ox + cx * s - body / 2f);
		int y0 = Math.round(oy + cy * s - body / 2f);
		disc(x0 - edge, y0 - edge, body + edge * 2, 0x24FFFFFF);
		disc(x0, y0, body, 0xFF45423F);
		disc(x0 + (body - dot) / 2, y0 + (body - dot) / 2, dot, color);
	}

	private void disc(int x, int y, int size, int argb) {
		if ((argb >>> 24) == 0 || size <= 0) {
			return;
		}
		blit(Shapes.disc(size), x, y, size, size, 0, 0, size, size, col(argb));
	}

	public void spriteRegion(net.minecraft.resources.Identifier sprite, int texW, int texH, int u, int v, float x, float y, int w, int h, int argb) {
		if (alpha <= 0f || w <= 0 || h <= 0) {
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(ox + x * s, oy + y * s);
		g.pose().scale(s, s);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, texW, texH, u, v, 0, 0, w, h, col(argb));
		g.pose().popMatrix();
	}

	public void spriteAt(net.minecraft.resources.Identifier sprite, float x, float y, int w, int h, int argb) {
		if (alpha <= 0f) {
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(ox + x * s, oy + y * s);
		g.pose().scale(s, s);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, 0, 0, w, h, col(argb));
		g.pose().popMatrix();
	}

	private static net.minecraft.network.chat.Component defaultFont(net.minecraft.network.chat.Component text) {
		net.minecraft.network.chat.MutableComponent out = net.minecraft.network.chat.Component.empty();
		text.visit((style, part) -> {
			out.append(net.minecraft.network.chat.Component.literal(part).withStyle(style.withFont(net.minecraft.network.chat.FontDescription.DEFAULT)));
			return java.util.Optional.empty();
		}, net.minecraft.network.chat.Style.EMPTY);
		return out;
	}

	private record Run(String text, int color, boolean bold, boolean strike, boolean underline, net.minecraft.network.chat.Style style) {
	}

	private static final String SMALL_CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";
	private static final float SMALL_CAPS_SIZE = 0.8f;

	private static int smallCap(int cp) {
		int i = SMALL_CAPS.indexOf(cp);
		return i >= 0 && cp != 'x' ? 'A' + i : -1;
	}

	private static boolean hasSmallCaps(String text) {
		for (int i = 0; i < text.length(); i++) {
			if (smallCap(text.charAt(i)) >= 0) {
				return true;
			}
		}
		return false;
	}

	private static java.util.List<Run> runs(net.minecraft.network.chat.Component text, int argb) {
		java.util.List<Run> out = new java.util.ArrayList<>();
		text.visit((st, part) -> {
			int base = st.getColor() != null ? (argb & 0xFF000000) | (st.getColor().getValue() & 0xFFFFFF) : argb;
			int color = base;
			boolean bold = st.isBold(), strike = st.isStrikethrough(), underline = st.isUnderlined();
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < part.length(); i++) {
				char ch = part.charAt(i);
				if (ch == '§' && i + 1 < part.length()) {
					if (!sb.isEmpty()) {
						out.add(new Run(sb.toString(), color, bold, strike, underline, st));
						sb.setLength(0);
					}
					net.minecraft.ChatFormatting f = net.minecraft.ChatFormatting.getByCode(part.charAt(i + 1));
					if (f == net.minecraft.ChatFormatting.RESET) {
						color = base;
						bold = st.isBold();
						strike = st.isStrikethrough();
						underline = st.isUnderlined();
					} else if (f != null && net.minecraft.network.chat.TextColor.fromLegacyFormat(f) != null) {
						color = (argb & 0xFF000000) | (net.minecraft.network.chat.TextColor.fromLegacyFormat(f).getValue() & 0xFFFFFF);
						bold = false;
						strike = false;
						underline = false;
					} else if (f == net.minecraft.ChatFormatting.BOLD) {
						bold = true;
					} else if (f == net.minecraft.ChatFormatting.STRIKETHROUGH) {
						strike = true;
					} else if (f == net.minecraft.ChatFormatting.UNDERLINE) {
						underline = true;
					}
					i++;
					continue;
				}
				sb.append(ch);
			}
			if (!sb.isEmpty()) {
				out.add(new Run(sb.toString(), color, bold, strike, underline, st));
			}
			return java.util.Optional.empty();
		}, net.minecraft.network.chat.Style.EMPTY);
		return out;
	}

	private boolean ownFont(String text, Fonts.Style style, float size) {
		FontAtlas atlas = font(style, size);
		for (int i = 0; i < text.length(); ) {
			int cp = text.codePointAt(i);
			i += Character.charCount(cp);
			if (!Character.isWhitespace(cp) && smallCap(cp) < 0 && !atlas.canDisplay(cp)) {
				return false;
			}
		}
		return true;
	}

	private static net.minecraft.network.chat.Component runComponent(Run run) {
		net.minecraft.network.chat.Style st = run.style().withColor(net.minecraft.network.chat.TextColor.fromRgb(run.color() & 0xFFFFFF)).withBold(run.bold()).withStrikethrough(run.strike()).withUnderlined(run.underline());
		return net.minecraft.network.chat.Component.literal(run.text()).withStyle(st);
	}

	private java.util.List<String[]> pieces(String text) {
		java.util.List<String[]> out = new java.util.ArrayList<>();
		if (!hasSmallCaps(text)) {
			out.add(new String[]{text, ""});
			return out;
		}
		StringBuilder normal = new StringBuilder(), small = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			int cap = smallCap(c);
			if (cap < 0 && c >= 'a' && c <= 'z') {
				cap = Character.toUpperCase(c);
			}
			if (cap >= 0) {
				if (!normal.isEmpty()) {
					out.add(new String[]{normal.toString(), ""});
					normal.setLength(0);
				}
				small.append((char) cap);
			} else {
				if (!small.isEmpty()) {
					out.add(new String[]{small.toString(), "s"});
					small.setLength(0);
				}
				normal.append(c);
			}
		}
		if (!normal.isEmpty()) {
			out.add(new String[]{normal.toString(), ""});
		}
		if (!small.isEmpty()) {
			out.add(new String[]{small.toString(), "s"});
		}
		return out;
	}

	private float ownWidth(String text, Fonts.Style style, float size) {
		float w = 0;
		for (String[] piece : pieces(text)) {
			w += width(piece[0], style, piece[1].isEmpty() ? size : size * SMALL_CAPS_SIZE);
		}
		return w;
	}

	private float runWidth(Run run, Fonts.Style style, float size) {
		Fonts.Style fs = run.bold() ? Fonts.Style.BOLD : style;
		if (ownFont(run.text(), fs, size)) {
			return ownWidth(run.text(), fs, size);
		}
		return Minecraft.getInstance().font.width(runComponent(run)) * vanillaScale(size) / s;
	}

	public float styledWidth(net.minecraft.network.chat.Component text, Fonts.Style style, float size) {
		if (vanilla) {
			return Minecraft.getInstance().font.width(defaultFont(text)) * vanillaScale(size) / s;
		}
		float w = 0;
		for (Run run : runs(text, 0xFFFFFFFF)) {
			w += runWidth(run, style, size);
		}
		return w;
	}

	public void styledText(net.minecraft.network.chat.Component text, float x, float cy, Fonts.Style style, float size, int argb, boolean shadow) {
		boolean drop = hudShadow(shadow);
		if (vanilla) {
			if ((argb >>> 24) == 0 || alpha <= 0f) {
				return;
			}
			float k = vanillaScale(size);
			g.pose().pushMatrix();
			g.pose().translate(Math.round(ox + x * s), Math.round(oy + cy * s - k * 3.5f));
			g.pose().scale(k, k);
			g.text(Minecraft.getInstance().font, defaultFont(text), 0, 0, col(argb), drop);
			g.pose().popMatrix();
			return;
		}
		float pen = x;
		for (Run run : runs(text, argb)) {
			Fonts.Style fs = run.bold() ? Fonts.Style.BOLD : style;
			float w = runWidth(run, style, size);
			if (ownFont(run.text(), fs, size)) {
				float cap = font(fs, size).capHeight / s;
				float px = pen;
				for (String[] piece : pieces(run.text())) {
					boolean small = !piece[1].isEmpty();
					float ps = small ? size * SMALL_CAPS_SIZE : size;
					float pcy = small ? cy + (cap - font(fs, ps).capHeight / s) / 2f : cy;
					textShadowed(piece[0], px, pcy, fs, ps, run.color(), drop);
					px += width(piece[0], fs, ps);
				}
				float t = Math.max(1f / s, size / 10f);
				if (run.strike()) {
					if (drop) {
						rect(pen + t * 0.7f, cy - t / 2f + t * 0.7f, w, t, ((run.color() >>> 24) * 3 / 5) << 24);
					}
					rect(pen, cy - t / 2f, w, t, run.color());
				}
				if (run.underline()) {
					float uy = cy + cap / 2f + t * 0.8f;
					rect(pen, uy, w, t, run.color());
				}
			} else if ((argb >>> 24) != 0 && alpha > 0f) {
				float k = vanillaScale(size);
				g.pose().pushMatrix();
				g.pose().translate(Math.round(ox + pen * s), Math.round(oy + cy * s - k * 3.5f));
				g.pose().scale(k, k);
				g.text(Minecraft.getInstance().font, runComponent(run), 0, 0, col(run.color()), drop);
				g.pose().popMatrix();
			}
			pen += w;
		}
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
