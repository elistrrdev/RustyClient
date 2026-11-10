package com.rustyclient.module.hud;

import com.rustyclient.core.Category;
import com.rustyclient.core.HudModule;
import com.rustyclient.core.Theme;
import com.rustyclient.core.setting.BoolSetting;
import com.rustyclient.core.setting.ColorSetting;
import com.rustyclient.mixin.BossOverlayAccessor;
import com.rustyclient.platform.Compat;
import com.rustyclient.render.Draw;
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
			float w = Draw.vanillaWidth(title, size);
			for (Component[] l : lines) {
				float lw = Draw.vanillaWidth(l[0], size) + (hideNumbers.on() ? 0 : Draw.vanillaWidth(l[1], size) + 9);
				w = Math.max(w, lw);
			}
			w += 4;
			float lineH = 9;
			float h = (lines.size() + 1) * lineH + 1;
			width = w;
			height = h;
			float r = radius(Math.min(w, h) / 2f);
			d.scissor(0, 0, w, lineH + 1);
			d.round(0, 0, w, lineH + 1 + r * 2, r, titleBackground.argb());
			d.endScissor();
			d.scissor(0, lineH + 1, w, h - lineH - 1);
			d.round(0, lineH + 1 - r * 2, w, h - lineH - 1 + r * 2, r, background.argb());
			d.endScissor();
			float tw = Draw.vanillaWidth(title, size);
			d.vanillaText(title, (w - tw) / 2f, 1, size, 0xFFFFFFFF, shadow.on());
			float y = lineH + 1;
			for (Component[] l : lines) {
				d.vanillaText(l[0], 2, y, size, 0xFFFFFFFF, shadow.on());
				if (!hideNumbers.on()) {
					float vw = Draw.vanillaWidth(l[1], size);
					d.vanillaText(l[1], w - 2 - vw, y, size, 0xFFFFFFFF, shadow.on());
				}
				y += lineH;
			}
		}
	}

	public static final class BossBar extends HudModule {
		private final BoolSetting hide = bool("Hide boss bars", GENERAL, false);

		public BossBar() {
			super("Boss Bar", "skull", Category.HUD, false, 0.5, 0.0);
		}

		@Override
		protected boolean usesRadius() {
			return false;
		}

		public boolean hideBars() {
			return hide.on();
		}

		public int count() {
			try {
				return ((BossOverlayAccessor) Compat.bossOverlay()).rusty$events().size();
			} catch (Throwable t) {
				return 0;
			}
		}

		@Override
		public void render(Draw d, boolean editing) {
			width = 182;
			int n = Math.max(1, count());
			height = n * 19 + 2;
			if (editing && count() == 0) {
				d.round(0, 9, 182, 5, 2, Theme.withAlpha(0xFFE04CC8, 0xCC));
				d.textCenter("Boss bar", 91, 4, com.rustyclient.render.Fonts.Style.SEMIBOLD, 8, Theme.TEXT);
			}
		}
	}
}
