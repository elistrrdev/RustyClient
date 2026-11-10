package com.rustyclient.module.hud;

import com.rustyclient.core.Category;
import com.rustyclient.core.HudModule;
import com.rustyclient.core.Theme;
import com.rustyclient.core.setting.BoolSetting;
import net.minecraft.world.BossEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.components.LerpingBossEvent;
import com.rustyclient.core.setting.NumberSetting;
import com.rustyclient.core.setting.ChoiceSetting;
import com.rustyclient.core.setting.ColorSetting;
import com.rustyclient.mixin.BossOverlayAccessor;
import com.rustyclient.platform.Compat;
import com.rustyclient.render.Draw;
import com.rustyclient.render.Fonts;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class VanillaHuds {
	private VanillaHuds() {
	}

	public static final class ScoreboardHud extends HudModule {
		private final BoolSetting hideNumbers = bool("Hide red numbers", GENERAL, true);
		private final BoolSetting shadow = bool("Text shadow", GENERAL, false);
		private final BoolSetting hide = bool("Hide scoreboard", GENERAL, false);
		private final BoolSetting overrideFonts = bool("Override server fonts", GENERAL, true);
		private final ColorSetting background = colorAlpha("Background color", APPEARANCE, 0x4D000000);
		private final ColorSetting titleBackground = colorAlpha("Title background", APPEARANCE, 0x66000000);

		public ScoreboardHud() {
			super("Scoreboard", "list-ordered", Category.HUD, false, 0.995, 0.45);
		}

		@Override
		public void render(Draw d, boolean editing) {
			if (hide.on()) {
				width = 60;
				height = 20;
				if (editing) {
					d.round(0, 0, width, height, radius(), 0x40000000);
				}
				return;
			}
			Scoreboard sb = mc.level == null ? null : mc.level.getScoreboard();
			Objective obj = null;
			if (sb != null && mc.player != null) {
				PlayerTeam team = sb.getPlayersTeam(mc.player.getScoreboardName());
				if (team != null) {
					DisplaySlot slot = Compat.teamSlot(team);
					if (slot != null) {
						obj = sb.getDisplayObjective(slot);
					}
				}
				if (obj == null) {
					obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
				}
			}
			List<Component[]> lines = new ArrayList<>();
			Component title;
			if (obj == null) {
				if (!editing) {
					width = 60;
					height = 20;
					return;
				}
				title = Component.literal("Scoreboard").withStyle(ChatFormatting.YELLOW);
				for (int i = 0; i < 4; i++) {
					lines.add(new Component[]{Component.literal("Line " + (i + 1)), Component.literal(String.valueOf(4 - i)).withStyle(ChatFormatting.RED)});
				}
			} else {
				title = obj.getDisplayName();
				List<PlayerScoreEntry> entries = new ArrayList<>(sb.listPlayerScores(obj));
				entries.removeIf(PlayerScoreEntry::isHidden);
				entries.sort(Comparator.comparingInt(PlayerScoreEntry::value).reversed().thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER));
				if (entries.size() > 15) {
					entries = entries.subList(0, 15);
				}
				for (PlayerScoreEntry e : entries) {
					PlayerTeam team = sb.getPlayersTeam(e.owner());
					Component name = PlayerTeam.formatNameForTeam(team, e.ownerName());
					Component value = e.formatValue(obj.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT));
					lines.add(new Component[]{name, value});
				}
			}
			float size = 9;
			Fonts.Style st = Fonts.Style.SEMIBOLD;
			float w = textWidth(d, title, st, size);
			for (Component[] l : lines) {
				float lw = textWidth(d, l[0], st, size) + (hideNumbers.on() ? 0 : textWidth(d, l[1], st, size) + 9);
				w = Math.max(w, lw);
			}
			w += 4;
			float lineH = 9;
			float titleH = lineH + 1;
			float h = titleH + lines.size() * lineH;
			width = w;
			height = h;
			float r = radius(Math.min(w, h) / 2f);
			d.roundSides(0, 0, w, titleH, r, true, lines.isEmpty(), titleBackground.argb());
			if (!lines.isEmpty()) {
				d.roundSides(0, titleH, w, h - titleH, r, false, true, background.argb());
			}
			float off = d.vanillaFont() ? 3.5f : 4.5f;
			float tw = textWidth(d, title, st, size);
			text(d, title, (w - tw) / 2f, 1 + off, st, size);
			float y = titleH;
			for (Component[] l : lines) {
				text(d, l[0], 2, y + off, st, size);
				if (!hideNumbers.on()) {
					float vw = textWidth(d, l[1], st, size);
					text(d, l[1], w - 2 - vw, y + off, st, size);
				}
				y += lineH;
			}
		}

		private float textWidth(Draw d, Component text, Fonts.Style st, float size) {
			if (d.vanillaFont() && !overrideFonts.on()) {
				return Draw.vanillaWidth(text, size);
			}
			return d.styledWidth(text, st, size);
		}

		private void text(Draw d, Component text, float x, float cy, Fonts.Style st, float size) {
			if (d.vanillaFont() && !overrideFonts.on()) {
				d.vanillaText(text, x, cy - 3.5f, size, 0xFFFFFFFF, shadow.on());
				return;
			}
			d.styledText(text, x, cy, st, size, 0xFFFFFFFF, shadow.on());
		}
	}

	public static final class BossBar extends HudModule {
		private static final String BAR = "Bar";
		private static final String TEXT = "Text";
		private final BoolSetting hide = bool("Hide boss bars", GENERAL, false);
		private final BoolSetting showName = bool("Show name", GENERAL, true);
		private final BoolSetting showBar = bool("Show bar", GENERAL, true);
		private final BoolSetting percent = bool("Show percentage", GENERAL, false);
		private final BoolSetting shadow = bool("Text shadow", GENERAL, true);
		private final BoolSetting keepColors = bool("Keep name colors", GENERAL, true);
		private final ChoiceSetting style = choice("Bar style", BAR, "Vanilla", "Vanilla", "Flat");
		private final BoolSetting customColor = bool("Custom bar color", BAR, false);
		private final ColorSetting barColor = colorAlpha("Bar color", BAR, 0xFFE04CC8);
		private final ColorSetting backColor = colorAlpha("Bar background", BAR, 0x99000000);
		private final BoolSetting notches = bool("Show notches", BAR, true);
		private final NumberSetting barWidth = number("Bar width", BAR, 182, 60, 320, 1, NumberSetting.integer(""));
		private final NumberSetting barHeight = number("Bar height", BAR, 5, 2, 12, 0.5, NumberSetting.decimal(""));
		private final NumberSetting opacity = number("Bar opacity", BAR, 1, 0.1, 1, 0.05, NumberSetting.percent());
		private final ChoiceSetting align = choice("Alignment", TEXT, "Center", "Left", "Center", "Right");
		private final NumberSetting textSize = number("Text size", TEXT, 9, 5, 16, 0.5, NumberSetting.decimal(""));
		private final ColorSetting nameColor = color("Name color", TEXT, 0xFFFFFFFF);
		private final ColorSetting percentColor = color("Percentage color", TEXT, 0xFFFFFFFF);
		private final NumberSetting spacing = number("Bar spacing", APPEARANCE, 5, 0, 20, 0.5, NumberSetting.decimal(""));
		private final NumberSetting maxBars = number("Max bars", APPEARANCE, 6, 1, 12, 1, NumberSetting.integer(""));

		public BossBar() {
			super("Boss Bar", "skull", Category.HUD, false, 0.5, 0.0);
			customColor.visibleWhen(showBar::on);
			barColor.visibleWhen(() -> showBar.on() && (customColor.on() || style.is("Flat")));
			backColor.visibleWhen(() -> showBar.on() && style.is("Flat"));
			notches.visibleWhen(() -> showBar.on() && style.is("Vanilla"));
			barWidth.visibleWhen(() -> showBar.on() && style.is("Flat"));
			barHeight.visibleWhen(() -> showBar.on() && style.is("Flat"));
			nameColor.visibleWhen(() -> showName.on() && !keepColors.on());
			percentColor.visibleWhen(percent::on);
		}

		@Override
		protected boolean usesRadius() {
			return style.is("Flat");
		}

		public boolean hideBars() {
			return hide.on();
		}

		private List<LerpingBossEvent> events() {
			try {
				return new ArrayList<>(((BossOverlayAccessor) Compat.bossOverlay()).rusty$events().values());
			} catch (Throwable t) {
				return new ArrayList<>();
			}
		}

		public int count() {
			return events().size();
		}

		private float barW() {
			return style.is("Flat") ? barWidth.f() : 182;
		}

		private float barH() {
			return style.is("Flat") ? barHeight.f() : 5;
		}

		private static int flatColor(BossEvent.BossBarColor color) {
			return switch (color) {
				case BLUE -> 0xFF00B7EC;
				case RED -> 0xFFEC3A3A;
				case GREEN -> 0xFF1DEC00;
				case YELLOW -> 0xFFE9EC00;
				case PURPLE -> 0xFF7B00EC;
				case WHITE -> 0xFFECECEC;
				default -> 0xFFEC00B8;
			};
		}

		@Override
		public boolean visibleNow() {
			return !hide.on() && count() > 0;
		}

		@Override
		public void render(Draw d, boolean editing) {
			List<LerpingBossEvent> list = events();
			List<Object[]> rows = new ArrayList<>();
			for (LerpingBossEvent e : list) {
				rows.add(new Object[]{e.getName(), e.getProgress(), e.getColor(), e.getOverlay()});
			}
			if (rows.isEmpty() && editing) {
				rows.add(new Object[]{Component.literal("Ender Dragon").withStyle(ChatFormatting.LIGHT_PURPLE), 0.7f, BossEvent.BossBarColor.PINK, BossEvent.BossBarOverlay.PROGRESS});
			}
			while (rows.size() > maxBars.i()) {
				rows.remove(rows.size() - 1);
			}
			float ts = textSize.f();
			float bw = barW(), bh = barH();
			float nameH = showName.on() || percent.on() ? ts + 1 : 0;
			float rowH = nameH + (showBar.on() ? bh : 0);
			float w = bw;
			for (Object[] r : rows) {
				w = Math.max(w, label(d, (Component) r[0], (float) r[1]) + 2);
			}
			width = w;
			height = rows.isEmpty() ? rowH : rows.size() * rowH + Math.max(0, rows.size() - 1) * spacing.f();
			if (hide.on() && !editing) {
				return;
			}
			float y = 0;
			for (Object[] r : rows) {
				Component name = (Component) r[0];
				float progress = (float) r[1];
				if (nameH > 0) {
					float lw = label(d, name, progress);
					float lx = align.is("Left") ? 0 : align.is("Right") ? w - lw : (w - lw) / 2f;
					drawLabel(d, name, progress, lx, y + ts / 2f);
				}
				if (showBar.on()) {
					float bx = align.is("Left") ? 0 : align.is("Right") ? w - bw : (w - bw) / 2f;
					drawBar(d, bx, y + nameH, bw, bh, progress, (BossEvent.BossBarColor) r[2], (BossEvent.BossBarOverlay) r[3]);
				}
				y += rowH + spacing.f();
			}
		}

		private String percentText(float progress) {
			return Math.round(progress * 100) + "%";
		}

		private float label(Draw d, Component name, float progress) {
			float w = 0;
			if (showName.on()) {
				w += keepColors.on() ? d.styledWidth(name, Fonts.Style.SEMIBOLD, textSize.f()) : d.width(name.getString(), Fonts.Style.SEMIBOLD, textSize.f());
			}
			if (percent.on()) {
				w += (showName.on() ? d.width(" ", Fonts.Style.SEMIBOLD, textSize.f()) : 0) + d.width(percentText(progress), Fonts.Style.SEMIBOLD, textSize.f());
			}
			return w;
		}

		private void drawLabel(Draw d, Component name, float progress, float x, float cy) {
			float ts = textSize.f();
			if (showName.on()) {
				if (keepColors.on()) {
					d.styledText(name, x, cy, Fonts.Style.SEMIBOLD, ts, 0xFFFFFFFF, shadow.on());
					x += d.styledWidth(name, Fonts.Style.SEMIBOLD, ts);
				} else {
					d.textShadowed(name.getString(), x, cy, Fonts.Style.SEMIBOLD, ts, nameColor.argb(), shadow.on());
					x += d.width(name.getString(), Fonts.Style.SEMIBOLD, ts);
				}
				x += d.width(" ", Fonts.Style.SEMIBOLD, ts);
			}
			if (percent.on()) {
				d.textShadowed(percentText(progress), x, cy, Fonts.Style.SEMIBOLD, ts, percentColor.argb(), shadow.on());
			}
		}

		private void drawBar(Draw d, float x, float y, float w, float h, float progress, BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay) {
			int alpha = Math.round(opacity.f() * 255);
			if (style.is("Flat")) {
				int fill = customColor.on() ? barColor.argb() : flatColor(color);
				float r = radius(h / 2f);
				d.round(x, y, w, h, r, Theme.withAlpha(backColor.argb(), Math.round((backColor.argb() >>> 24) * opacity.f())));
				if (progress > 0) {
					d.round(x, y, Math.max(Math.min(w, r * 2), w * progress), h, r, Theme.withAlpha(fill, Math.round((fill >>> 24) * opacity.f())));
				}
				return;
			}
			String key = customColor.on() ? "white" : color.getName();
			int tint = customColor.on() ? Theme.withAlpha(barColor.argb(), Math.round((barColor.argb() >>> 24) * opacity.f())) : Theme.withAlpha(0xFFFFFFFF, alpha);
			int fillW = (int) (progress * 183f);
			d.spriteRegion(Identifier.withDefaultNamespace("boss_bar/" + key + "_background"), 182, 5, 0, 0, x, y, 182, 5, tint);
			if (notches.on() && overlay != BossEvent.BossBarOverlay.PROGRESS) {
				d.spriteRegion(Identifier.withDefaultNamespace("boss_bar/" + overlay.getName() + "_background"), 182, 5, 0, 0, x, y, 182, 5, Theme.withAlpha(0xFFFFFFFF, alpha));
			}
			if (fillW > 0) {
				d.spriteRegion(Identifier.withDefaultNamespace("boss_bar/" + key + "_progress"), 182, 5, 0, 0, x, y, fillW, 5, tint);
				if (notches.on() && overlay != BossEvent.BossBarOverlay.PROGRESS) {
					d.spriteRegion(Identifier.withDefaultNamespace("boss_bar/" + overlay.getName() + "_progress"), 182, 5, 0, 0, x, y, fillW, 5, Theme.withAlpha(0xFFFFFFFF, alpha));
				}
			}
		}
	}
}
