package com.rustyclient.render;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Icons {
	private record Element(Shape shape, boolean fill) {
	}

	private static final Map<String, List<Element>> SHAPES = new HashMap<>();
	private static final Map<String, Tex> CACHE = new HashMap<>();
	private static boolean loaded;

	private Icons() {
	}

	public static Tex get(String name, int px) {
		load();
		String key = name + "@" + px;
		Tex tex = CACHE.get(key);
		if (tex == null) {
			tex = render(name, px);
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

	private static Tex render(String name, int px) {
		int size = Math.max(2, px);
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		List<Element> elements = SHAPES.get(name);
		if (elements != null) {
			Graphics2D g = image.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			float scale = size / 24f;
			g.setTransform(AffineTransform.getScaleInstance(scale, scale));
			float stroke = Math.max(2f, 1.35f / scale);
			g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.setColor(Color.WHITE);
			for (Element element : elements) {
				if (element.fill()) {
					g.fill(element.shape());
				} else {
					g.draw(element.shape());
				}
			}
			g.dispose();
		}
		return Tex.image("icon", image);
	}

	private static synchronized void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		try (InputStream in = Icons.class.getResourceAsStream("/assets/rustyclient/icons.txt")) {
			if (in == null) {
				return;
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			String line;
			while ((line = reader.readLine()) != null) {
				int tab = line.indexOf('\t');
				if (tab < 0) {
					continue;
				}
				List<Element> elements = new ArrayList<>();
				for (String part : line.substring(tab + 1).split("\\|")) {
					Element element = parseElement(part);
					if (element != null) {
						elements.add(element);
					}
				}
				SHAPES.put(line.substring(0, tab), elements);
			}
		} catch (Exception ignored) {
		}
	}

	private static Element parseElement(String part) {
		String[] tokens = part.trim().split(" ");
		if (tokens.length == 0) {
			return null;
		}
		Map<String, String> attrs = new HashMap<>();
		for (int i = 1; i < tokens.length; i++) {
			int eq = tokens[i].indexOf('=');
			if (eq > 0) {
				attrs.put(tokens[i].substring(0, eq), tokens[i].substring(eq + 1));
			}
		}
		boolean fill = "currentColor".equals(attrs.get("fill"));
		switch (tokens[0]) {
			case "path":
				return new Element(SvgPath.parse(attrs.getOrDefault("d", "")), fill);
			case "circle": {
				float cx = num(attrs, "cx"), cy = num(attrs, "cy"), r = num(attrs, "r");
				return new Element(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2), fill);
			}
			case "ellipse": {
				float cx = num(attrs, "cx"), cy = num(attrs, "cy"), rx = num(attrs, "rx"), ry = num(attrs, "ry");
				return new Element(new Ellipse2D.Float(cx - rx, cy - ry, rx * 2, ry * 2), fill);
			}
			case "rect": {
				float x = num(attrs, "x"), y = num(attrs, "y"), w = num(attrs, "width"), h = num(attrs, "height");
				float rx = attrs.containsKey("rx") ? num(attrs, "rx") : num(attrs, "ry");
				float ry = attrs.containsKey("ry") ? num(attrs, "ry") : rx;
				return new Element(new RoundRectangle2D.Float(x, y, w, h, rx * 2, ry * 2), fill);
			}
			case "line":
				return new Element(new Line2D.Float(num(attrs, "x1"), num(attrs, "y1"), num(attrs, "x2"), num(attrs, "y2")), fill);
			case "polyline":
			case "polygon": {
				String[] values = attrs.getOrDefault("points", "").split("[ ,]+");
				Path2D.Float path = new Path2D.Float();
				for (int i = 0; i + 1 < values.length; i += 2) {
					float x = Float.parseFloat(values[i]);
					float y = Float.parseFloat(values[i + 1]);
					if (i == 0) {
						path.moveTo(x, y);
					} else {
						path.lineTo(x, y);
					}
				}
				if (tokens[0].equals("polygon")) {
					path.closePath();
				}
				return new Element(path, fill);
			}
			default:
				return null;
		}
	}

	private static float num(Map<String, String> attrs, String key) {
		String value = attrs.get(key);
		if (value == null || value.isEmpty()) {
			return 0f;
		}
		try {
			return Float.parseFloat(value);
		} catch (NumberFormatException e) {
			return 0f;
		}
	}
}
