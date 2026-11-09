package com.rustyclient.render;

import java.awt.geom.Path2D;

final class SvgPath {
	private final String d;
	private int pos;

	private SvgPath(String d) {
		this.d = d;
	}

	static Path2D.Float parse(String d) {
		Path2D.Float path = new Path2D.Float();
		try {
			new SvgPath(d).run(path);
		} catch (RuntimeException ignored) {
		}
		return path;
	}

	private void run(Path2D.Float path) {
		float cx = 0, cy = 0, sx = 0, sy = 0, lcx = 0, lcy = 0;
		char cmd = 0;
		char prev = 0;
		while (true) {
			skip();
			if (pos >= d.length()) {
				return;
			}
			char c = d.charAt(pos);
			if (Character.isLetter(c)) {
				cmd = c;
				pos++;
			} else if (cmd == 0) {
				return;
			}
			boolean rel = Character.isLowerCase(cmd);
			switch (Character.toUpperCase(cmd)) {
				case 'M': {
					float x = num(), y = num();
					if (rel) {
						x += cx;
						y += cy;
					}
					path.moveTo(x, y);
					cx = sx = x;
					cy = sy = y;
					cmd = rel ? 'l' : 'L';
					lcx = cx;
					lcy = cy;
					prev = 'M';
					continue;
				}
				case 'L': {
					float x = num(), y = num();
					if (rel) {
						x += cx;
						y += cy;
					}
					path.lineTo(x, y);
					cx = x;
					cy = y;
					break;
				}
				case 'H': {
					float x = num();
					if (rel) {
						x += cx;
					}
					path.lineTo(x, cy);
					cx = x;
					break;
				}
				case 'V': {
					float y = num();
					if (rel) {
						y += cy;
					}
					path.lineTo(cx, y);
					cy = y;
					break;
				}
				case 'C': {
					float x1 = num(), y1 = num(), x2 = num(), y2 = num(), x = num(), y = num();
					if (rel) {
						x1 += cx;
						y1 += cy;
						x2 += cx;
						y2 += cy;
						x += cx;
						y += cy;
					}
					path.curveTo(x1, y1, x2, y2, x, y);
					lcx = x2;
					lcy = y2;
					cx = x;
					cy = y;
					prev = 'C';
					continue;
				}
				case 'S': {
					float x2 = num(), y2 = num(), x = num(), y = num();
					if (rel) {
						x2 += cx;
						y2 += cy;
						x += cx;
						y += cy;
					}
					float x1 = prev == 'C' ? 2 * cx - lcx : cx;
					float y1 = prev == 'C' ? 2 * cy - lcy : cy;
					path.curveTo(x1, y1, x2, y2, x, y);
					lcx = x2;
					lcy = y2;
					cx = x;
					cy = y;
					prev = 'C';
					continue;
				}
				case 'Q': {
					float x1 = num(), y1 = num(), x = num(), y = num();
					if (rel) {
						x1 += cx;
						y1 += cy;
						x += cx;
						y += cy;
					}
					path.quadTo(x1, y1, x, y);
					lcx = x1;
					lcy = y1;
					cx = x;
					cy = y;
					prev = 'Q';
					continue;
				}
				case 'T': {
					float x = num(), y = num();
					if (rel) {
						x += cx;
						y += cy;
					}
					float x1 = prev == 'Q' ? 2 * cx - lcx : cx;
					float y1 = prev == 'Q' ? 2 * cy - lcy : cy;
					path.quadTo(x1, y1, x, y);
					lcx = x1;
					lcy = y1;
					cx = x;
					cy = y;
					prev = 'Q';
					continue;
				}
				case 'A': {
					float rx = num(), ry = num(), rot = num();
					boolean large = flag(), sweep = flag();
					float x = num(), y = num();
					if (rel) {
						x += cx;
						y += cy;
					}
					arc(path, cx, cy, rx, ry, rot, large, sweep, x, y);
					cx = x;
					cy = y;
					break;
				}
				case 'Z': {
					path.closePath();
					cx = sx;
					cy = sy;
					cmd = 0;
					break;
				}
				default:
					return;
			}
			prev = Character.toUpperCase(cmd == 0 ? 'Z' : cmd);
		}
	}

	private void skip() {
		while (pos < d.length()) {
			char c = d.charAt(pos);
			if (c == ' ' || c == ',' || c == '\n' || c == '\t' || c == '\r') {
				pos++;
			} else {
				break;
			}
		}
	}

	private boolean flag() {
		skip();
		char c = d.charAt(pos++);
		return c == '1';
	}

	private float num() {
		skip();
		int start = pos;
		if (pos < d.length() && (d.charAt(pos) == '-' || d.charAt(pos) == '+')) {
			pos++;
		}
		boolean dot = false;
		while (pos < d.length()) {
			char c = d.charAt(pos);
			if (Character.isDigit(c)) {
				pos++;
			} else if (c == '.' && !dot) {
				dot = true;
				pos++;
			} else if ((c == 'e' || c == 'E') && pos + 1 < d.length()) {
				pos++;
				if (d.charAt(pos) == '-' || d.charAt(pos) == '+') {
					pos++;
				}
			} else {
				break;
			}
		}
		return Float.parseFloat(d.substring(start, pos));
	}

	private static void arc(Path2D.Float path, float x0, float y0, float rx, float ry, float angle, boolean large, boolean sweep, float x, float y) {
		if (rx == 0 || ry == 0) {
			path.lineTo(x, y);
			return;
		}
		double phi = Math.toRadians(angle);
		double cos = Math.cos(phi), sin = Math.sin(phi);
		double dx = (x0 - x) / 2.0, dy = (y0 - y) / 2.0;
		double x1 = cos * dx + sin * dy;
		double y1 = -sin * dx + cos * dy;
		double arx = Math.abs(rx), ary = Math.abs(ry);
		double lambda = (x1 * x1) / (arx * arx) + (y1 * y1) / (ary * ary);
		if (lambda > 1) {
			double s = Math.sqrt(lambda);
			arx *= s;
			ary *= s;
		}
		double num = arx * arx * ary * ary - arx * arx * y1 * y1 - ary * ary * x1 * x1;
		double den = arx * arx * y1 * y1 + ary * ary * x1 * x1;
		double coef = (large != sweep ? 1 : -1) * Math.sqrt(Math.max(0, num / den));
		double cxp = coef * (arx * y1 / ary);
		double cyp = coef * -(ary * x1 / arx);
		double cx = cos * cxp - sin * cyp + (x0 + x) / 2.0;
		double cy = sin * cxp + cos * cyp + (y0 + y) / 2.0;
		double theta = angleOf(1, 0, (x1 - cxp) / arx, (y1 - cyp) / ary);
		double delta = angleOf((x1 - cxp) / arx, (y1 - cyp) / ary, (-x1 - cxp) / arx, (-y1 - cyp) / ary);
		if (!sweep && delta > 0) {
			delta -= Math.PI * 2;
		} else if (sweep && delta < 0) {
			delta += Math.PI * 2;
		}
		int segments = (int) Math.ceil(Math.abs(delta) / (Math.PI / 2));
		double step = delta / segments;
		double t = 4.0 / 3.0 * Math.tan(step / 4);
		double a = theta;
		for (int i = 0; i < segments; i++) {
			double c1 = Math.cos(a), s1 = Math.sin(a);
			double c2 = Math.cos(a + step), s2 = Math.sin(a + step);
			double ex1 = c1 - t * s1, ey1 = s1 + t * c1;
			double ex2 = c2 + t * s2, ey2 = s2 - t * c2;
			path.curveTo(
				(float) (cx + arx * (cos * ex1) - ary * (sin * ey1)), (float) (cy + arx * (sin * ex1) + ary * (cos * ey1)),
				(float) (cx + arx * (cos * ex2) - ary * (sin * ey2)), (float) (cy + arx * (sin * ex2) + ary * (cos * ey2)),
				(float) (cx + arx * (cos * c2) - ary * (sin * s2)), (float) (cy + arx * (sin * c2) + ary * (cos * s2)));
			a += step;
		}
	}

	private static double angleOf(double ux, double uy, double vx, double vy) {
		double dot = ux * vx + uy * vy;
		double len = Math.sqrt((ux * ux + uy * uy) * (vx * vx + vy * vy));
		double ang = Math.acos(Math.max(-1, Math.min(1, dot / len)));
		return (ux * vy - uy * vx) < 0 ? -ang : ang;
	}
}
