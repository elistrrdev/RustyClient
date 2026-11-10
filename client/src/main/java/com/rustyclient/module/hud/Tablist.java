package com.rustyclient.module.hud;

import com.mojang.authlib.GameProfile;
import com.rustyclient.RustyClient;
import com.rustyclient.core.Category;
import com.rustyclient.core.Module;
import com.rustyclient.core.Modules;
import com.rustyclient.core.RustyUsers;
import com.rustyclient.core.setting.BoolSetting;
import com.rustyclient.core.setting.ChoiceSetting;
import com.rustyclient.core.setting.ColorSetting;
import com.rustyclient.core.setting.NumberSetting;
import com.rustyclient.mixin.TabMixins;
import com.rustyclient.module.ClientSettings;
import com.rustyclient.platform.Compat;
import com.rustyclient.render.Draw;
import com.rustyclient.render.Fonts;
import net.minecraft.ChatFormatting;
import net.minecraft.Optionull;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class Tablist extends Module {
	private static final String SORTING = "Sorting";
	private static final String PING = "Ping";
	private static final String LAYOUT = "Layout";
	private static final String TEXT = "Text";
	private static final Identifier[] PING_SPRITES = {
		Identifier.withDefaultNamespace("icon/ping_1"),
		Identifier.withDefaultNamespace("icon/ping_2"),
		Identifier.withDefaultNamespace("icon/ping_3"),
		Identifier.withDefaultNamespace("icon/ping_4"),
		Identifier.withDefaultNamespace("icon/ping_5")
	};
	private static final Identifier PING_UNKNOWN = Identifier.withDefaultNamespace("icon/ping_unknown");
	private static final Identifier HEART_CONTAINER = Identifier.withDefaultNamespace("hud/heart/container");
	private static final Identifier HEART_FULL = Identifier.withDefaultNamespace("hud/heart/full");
	private static final Identifier HEART_HALF = Identifier.withDefaultNamespace("hud/heart/half");
	private static final Identifier HEART_EXTRA_FULL = Identifier.withDefaultNamespace("hud/heart/absorbing_full_blinking");
	private static final Identifier HEART_EXTRA_HALF = Identifier.withDefaultNamespace("hud/heart/absorbing_half_blinking");
	private static final Comparator<PlayerInfo> VANILLA_ORDER = Comparator.<PlayerInfo>comparingInt(p -> -p.getTabListOrder())
		.thenComparingInt(p -> p.getGameMode() == GameType.SPECTATOR ? 1 : 0)
		.thenComparing(p -> Optionull.mapOrDefault(p.getTeam(), PlayerTeam::getName, ""))
		.thenComparing(p -> p.getProfile().name(), String::compareToIgnoreCase);

	private final BoolSetting header = bool("Show header", GENERAL, true);
	private final BoolSetting footer = bool("Show footer", GENERAL, true);
	private final ChoiceSetting heads = choice("Player heads", GENERAL, "Auto", "Auto", "Always", "Never");
	private final BoolSetting scores = bool("Show scores", GENERAL, true);
	private final BoolSetting count = bool("Show player count", GENERAL, false);
	private final BoolSetting hideSpectators = bool("Hide spectators", GENERAL, false);
	private final BoolSetting highlightSelf = bool("Highlight yourself", GENERAL, false);
	private final BoolSetting shadow = bool("Text shadow", TEXT, true);
	private final ChoiceSetting font = choice("Font", TEXT, "Global", "Global", "Vanilla", "Rusty Client");
	private final NumberSetting textSize = number("Text size", TEXT, 9, 6, 14, 0.5, NumberSetting.decimal(""));
	private final ChoiceSetting nameAlign = choice("Name alignment", TEXT, "Left", "Left", "Center");
	private final ChoiceSetting headerAlign = choice("Header alignment", TEXT, "Center", "Left", "Center", "Right");
	private final ColorSetting nameColor = color("Name color", TEXT, 0xFFFFFFFF);
	private final ColorSetting spectatorColor = colorAlpha("Spectator color", TEXT, 0x90FFFFFF);
	private final ChoiceSetting ping = choice("Ping style", PING, "Bars", "Bars", "Number", "Bars and number", "Hidden");
	private final BoolSetting pingColors = bool("Color ping number", PING, true);
	private final BoolSetting pingMs = bool("Show ms", PING, true);
	private final ChoiceSetting sort = choice("Sort by", SORTING, "Server order", "Server order", "Name", "Ping");
	private final BoolSetting selfFirst = bool("Yourself first", SORTING, false);
	private final NumberSetting maxPlayers = number("Max players", SORTING, 80, 10, 200, 1, NumberSetting.integer(""));
	private final NumberSetting scale = number("Scale", LAYOUT, 1, 0.5, 2, 0.05, NumberSetting.times());
	private final NumberSetting maxRows = number("Rows per column", LAYOUT, 20, 5, 40, 1, NumberSetting.integer(""));
	private final NumberSetting rowHeight = number("Row height", LAYOUT, 8, 8, 16, 0.5, NumberSetting.decimal(""));
	private final NumberSetting rowGap = number("Row spacing", LAYOUT, 1, 0, 6, 0.5, NumberSetting.decimal(""));
	private final NumberSetting columnGap = number("Column spacing", LAYOUT, 5, 0, 24, 0.5, NumberSetting.decimal(""));
	private final NumberSetting padding = number("Padding", LAYOUT, 1, 0, 10, 0.5, NumberSetting.decimal(""));
	private final NumberSetting minWidth = number("Minimum column width", LAYOUT, 0, 0, 300, 1, NumberSetting.integer(""));
	private final NumberSetting top = number("Distance from top", LAYOUT, 10, 0, 120, 1, NumberSetting.integer(""));
	private final BoolSetting onePanel = bool("One background panel", APPEARANCE, false);
	private final NumberSetting radius = number("Border radius", APPEARANCE, 0, 0, 12, 0.5, NumberSetting.decimal("px"));
	private final ColorSetting background = colorAlpha("Background color", APPEARANCE, 0x80000000);
	private final ColorSetting headerBackground = colorAlpha("Header and footer background", APPEARANCE, 0x80000000);
	private final ColorSetting rowColor = colorAlpha("Row color", APPEARANCE, 0x20FFFFFF);
	private final ColorSetting selfColor = colorAlpha("Your row color", APPEARANCE, 0x401AB0DD);

	public Tablist() {
		super("Tablist", "users", Category.HUD, false);
		pingColors.visibleWhen(() -> ping.is("Number") || ping.is("Bars and number"));
		pingMs.visibleWhen(() -> ping.is("Number") || ping.is("Bars and number"));
		selfColor.visibleWhen(highlightSelf::on);
		headerBackground.visibleWhen(() -> !onePanel.on());
	}

	public static boolean render(GuiGraphicsExtractor g, PlayerTabOverlay overlay, Scoreboard scoreboard, Objective objective) {
		Tablist tablist = Modules.get(Tablist.class);
		if (tablist == null || !tablist.enabled()) {
			return false;
		}
		Draw d = new Draw(g, Draw.guiScale() * tablist.scale.f());
		try {
			d.hudText(tablist.vanillaFont());
			tablist.draw(d, overlay, scoreboard, objective);
		} catch (Throwable t) {
			RustyClient.LOG.error("tablist failed to render", t);
		} finally {
			d.end();
		}
		return true;
	}

	private boolean vanillaFont() {
		if (font.is("Vanilla")) {
			return true;
		}
		if (font.is("Rusty Client")) {
			return false;
		}
		ClientSettings settings = ClientSettings.get();
		return settings == null || settings.hudFont.is("Vanilla");
	}

	private List<PlayerInfo> players() {
		List<PlayerInfo> list = new ArrayList<>(mc.player.connection.getListedOnlinePlayers());
		if (hideSpectators.on()) {
			list.removeIf(p -> p.getGameMode() == GameType.SPECTATOR);
		}
		Comparator<PlayerInfo> order = switch (sort.get()) {
			case "Name" -> Comparator.comparing(p -> p.getProfile().name(), String::compareToIgnoreCase);
			case "Ping" -> Comparator.<PlayerInfo>comparingInt(p -> p.getLatency() < 0 ? Integer.MAX_VALUE : p.getLatency()).thenComparing(VANILLA_ORDER);
			default -> VANILLA_ORDER;
		};
		if (selfFirst.on()) {
			UUID self = mc.player.getUUID();
			order = Comparator.<PlayerInfo>comparingInt(p -> self.equals(p.getProfile().id()) ? 0 : 1).thenComparing(order);
		}
		list.sort(order);
		int max = maxPlayers.i();
		return list.size() > max ? list.subList(0, max) : list;
	}

	private static Component name(PlayerInfo info) {
		MutableComponent name = info.getTabListDisplayName() != null
			? info.getTabListDisplayName().copy()
			: PlayerTeam.formatNameForTeam(info.getTeam(), Component.literal(info.getProfile().name()));
		return info.getGameMode() == GameType.SPECTATOR ? name.withStyle(ChatFormatting.ITALIC) : name;
	}

	private float width(Draw d, Component text) {
		if (d.vanillaFont()) {
			return Draw.vanillaWidth(text, textSize.f());
		}
		return d.styledWidth(text, Fonts.Style.SEMIBOLD, textSize.f());
	}

	private void text(Draw d, Component text, float x, float y, float h, int argb) {
		float size = textSize.f();
		if (d.vanillaFont()) {
			d.vanillaText(text, x, y + (h - 8f * size / 9f) / 2f, size, argb, d.hudShadow(shadow.on()));
			return;
		}
		d.styledText(text, x, y + h / 2f, Fonts.Style.SEMIBOLD, size, argb, shadow.on());
	}

	private static List<Component> lines(Component text) {
		List<Component> out = new ArrayList<>();
		MutableComponent[] current = {Component.empty()};
		text.visit((style, part) -> {
			String[] pieces = part.split("\n", -1);
			String carry = "";
			for (int i = 0; i < pieces.length; i++) {
				if (i > 0) {
					out.add(current[0]);
					current[0] = Component.empty();
				}
				String piece = carry + pieces[i];
				if (!piece.isEmpty()) {
					current[0].append(Component.literal(piece).withStyle(style));
				}
				carry = carry(carry, pieces[i]);
			}
			return Optional.empty();
		}, Style.EMPTY);
		out.add(current[0]);
		return out;
	}

	private static String carry(String carry, String text) {
		StringBuilder out = new StringBuilder(carry);
		for (int i = 0; i + 1 < text.length(); i++) {
			if (text.charAt(i) != '§') {
				continue;
			}
			char code = text.charAt(i + 1);
			ChatFormatting format = ChatFormatting.getByCode(code);
			if (format == ChatFormatting.RESET) {
				out.setLength(0);
			} else if (format != null && net.minecraft.network.chat.TextColor.fromLegacyFormat(format) != null) {
				out.setLength(0);
				out.append('§').append(code);
			} else if (format != null) {
				out.append('§').append(code);
			}
			i++;
		}
		return out.toString();
	}

	private record Entry(PlayerInfo info, Component name, float nameWidth, boolean icon, int score, Component scoreText, float scoreWidth) {
	}

	private static int pingColor(int latency) {
		if (latency < 0) {
			return 0xFFAAAAAA;
		}
		if (latency < 80) {
			return 0xFF55FF55;
		}
		if (latency < 150) {
			return 0xFFFFFF55;
		}
		if (latency < 250) {
			return 0xFFFFAA00;
		}
		return 0xFFFF5555;
	}

	private String pingText(int latency) {
		if (latency < 0) {
			return "?";
		}
		return latency + (pingMs.on() ? "ms" : "");
	}

	private void draw(Draw d, PlayerTabOverlay overlay, Scoreboard scoreboard, Objective objective) {
		if (mc.player == null || mc.level == null) {
			return;
		}
		List<PlayerInfo> infos = players();
		boolean showScores = scores.on() && objective != null;
		boolean hearts = showScores && objective.getRenderType() == ObjectiveCriteria.RenderType.HEARTS;
		float rowH = rowHeight.f();
		float pitch = rowH + rowGap.f();
		float iconW = 11f * textSize.f() / 9f;
		float spacer = width(d, Component.literal(" "));
		List<Entry> entries = new ArrayList<>(infos.size());
		float maxName = 0;
		float maxScore = 0;
		boolean showIcons = RustyUsers.tablistIcon();
		for (PlayerInfo info : infos) {
			Component name = name(info);
			boolean icon = showIcons && RustyUsers.uses(info.getProfile().id());
			float nameWidth = width(d, name) + (icon ? iconW : 0);
			maxName = Math.max(maxName, nameWidth);
			int score = 0;
			Component scoreText = null;
			float scoreWidth = 0;
			if (showScores) {
				ReadOnlyScoreInfo scoreInfo = scoreboard.getPlayerScoreInfo(ScoreHolder.fromGameProfile(info.getProfile()), objective);
				if (scoreInfo != null) {
					score = scoreInfo.value();
					if (!hearts) {
						scoreText = scoreInfo.formatValue(objective.numberFormatOrDefault(StyledFormat.PLAYER_LIST_DEFAULT));
						scoreWidth = width(d, scoreText);
						maxScore = Math.max(maxScore, scoreWidth > 0 ? spacer + scoreWidth : 0);
					}
				}
			}
			entries.add(new Entry(info, name, nameWidth, icon, score, scoreText, scoreWidth));
		}

		int slots = entries.size();
		int limit = maxRows.i();
		int rows = slots;
		int cols = 1;
		while (rows > limit) {
			cols++;
			rows = (slots + cols - 1) / cols;
		}
		boolean showHead = heads.is("Always") || (heads.is("Auto") && Compat.tabHeads());
		float headW = showHead ? rowH + 1 : 0;
		float scoreArea = !showScores ? 0 : hearts ? 90 : maxScore;
		float pingW = 0;
		if (ping.is("Bars")) {
			pingW = 11;
		} else if (ping.is("Number") || ping.is("Bars and number")) {
			float numberW = width(d, Component.literal(pingText(999)));
			pingW = numberW + 3 + (ping.is("Bars and number") ? 11 : 0);
		}
		float screenW = Draw.windowWidth() / d.s;
		float colGap = columnGap.f();
		float slotW = Math.min(cols * (headW + maxName + scoreArea + pingW + 2), screenW - 50) / cols;
		slotW = Math.max(slotW, minWidth.f());
		float listW = slotW * cols + (cols - 1) * colGap;
		float pad = padding.f();
		float lineH = 9f * textSize.f() / 9f;

		List<Component> headerLines = new ArrayList<>();
		Component headerText = ((TabMixins.Fields) overlay).rusty$header();
		if (header.on() && headerText != null) {
			headerLines.addAll(lines(headerText));
		}
		if (count.on()) {
			headerLines.add(Component.literal(infos.size() + (infos.size() == 1 ? " player" : " players")).withStyle(ChatFormatting.GRAY));
		}
		List<Component> footerLines = new ArrayList<>();
		Component footerText = ((TabMixins.Fields) overlay).rusty$footer();
		if (footer.on() && footerText != null) {
			footerLines.addAll(lines(footerText));
		}
		float maxW = listW;
		for (Component line : headerLines) {
			maxW = Math.max(maxW, width(d, line));
		}
		for (Component line : footerLines) {
			maxW = Math.max(maxW, width(d, line));
		}
		float boxW = maxW + pad * 2;
		float boxX = screenW / 2f - boxW / 2f;
		float y = top.f();
		float headerH = headerLines.isEmpty() ? 0 : headerLines.size() * lineH + pad * 2;
		float listH = rows * pitch - rowGap.f() + pad * 2;
		float footerH = footerLines.isEmpty() ? 0 : footerLines.size() * lineH + pad * 2;
		float gap = onePanel.on() ? 0 : 1;
		float r = radius.f();

		if (onePanel.on()) {
			float total = headerH + listH + footerH;
			d.round(boxX, y, boxW, total, Math.min(r, Math.min(boxW, total) / 2f), background.argb());
		}

		if (!headerLines.isEmpty()) {
			if (!onePanel.on()) {
				d.round(boxX, y, boxW, headerH, Math.min(r, Math.min(boxW, headerH) / 2f), headerBackground.argb());
			}
			float ly = y + pad;
			for (Component line : headerLines) {
				drawLine(d, line, boxX + pad, maxW, ly, lineH);
				ly += lineH;
			}
			y += headerH + gap;
		}

		if (!onePanel.on()) {
			d.round(boxX, y, boxW, listH, Math.min(r, Math.min(boxW, listH) / 2f), background.argb());
		}
		float listX = screenW / 2f - listW / 2f;
		float listY = y + pad;
		UUID self = mc.player.getUUID();
		for (int i = 0; i < slots; i++) {
			int col = i / rows;
			int row = i % rows;
			float x = listX + col * (slotW + colGap);
			float ry = listY + row * pitch;
			Entry entry = entries.get(i);
			PlayerInfo info = entry.info();
			boolean isSelf = self.equals(info.getProfile().id());
			int fill = highlightSelf.on() && isSelf ? selfColor.argb() : rowColor.argb();
			d.rect(x, ry, slotW, rowH, fill);
			float cx = x;
			if (showHead) {
				drawHead(d, info, cx, ry, rowH);
				cx += headW;
			}
			float nameStart = cx;
			float nameRoom = slotW - headW - pingW - (showScores && info.getGameMode() != GameType.SPECTATOR ? scoreArea : 0);
			if (nameAlign.is("Center")) {
				nameStart = cx + Math.max(0, (nameRoom - entry.nameWidth()) / 2f);
			}
			float tx = nameStart;
			if (entry.icon()) {
				float size = textSize.f();
				d.vanillaText(RustyUsers.icon(), tx, ry + (rowH - 8f * size / 9f) / 2f, size, 0xFFFFFFFF, d.hudShadow(shadow.on()));
				tx += iconW;
			}
			boolean spectator = info.getGameMode() == GameType.SPECTATOR;
			text(d, entry.name(), tx, ry, rowH, spectator ? spectatorColor.argb() : nameColor.argb());
			if (showScores && !spectator) {
				float left = cx + maxName + 1;
				float right = left + scoreArea;
				if (right - left > 5) {
					if (hearts) {
						drawHearts(d, entry.score(), left, right, ry, rowH);
					} else if (entry.scoreText() != null) {
						text(d, entry.scoreText(), right - entry.scoreWidth(), ry, rowH, 0xFFFFFFFF);
					}
				}
			}
			drawPing(d, info.getLatency(), x + slotW, ry, rowH);
		}
		y += listH + gap;

		if (!footerLines.isEmpty()) {
			if (!onePanel.on()) {
				d.round(boxX, y, boxW, footerH, Math.min(r, Math.min(boxW, footerH) / 2f), headerBackground.argb());
			}
			float ly = y + pad;
			for (Component line : footerLines) {
				drawLine(d, line, boxX + pad, maxW, ly, lineH);
				ly += lineH;
			}
		}
	}

	private void drawLine(Draw d, Component line, float x, float w, float y, float h) {
		float lw = width(d, line);
		float lx = switch (headerAlign.get()) {
			case "Left" -> x;
			case "Right" -> x + w - lw;
			default -> x + (w - lw) / 2f;
		};
		text(d, line, lx, y, h, 0xFFFFFFFF);
	}

	private void drawHead(Draw d, PlayerInfo info, float x, float y, float size) {
		GameProfile profile = info.getProfile();
		Player player = mc.level.getPlayerByUUID(profile.id());
		boolean flip = player != null && AvatarRenderer.isPlayerUpsideDown(player);
		d.g.pose().pushMatrix();
		d.g.pose().translate(d.originX() + x * d.s, d.originY() + y * d.s);
		float k = size * d.s / 8f;
		d.g.pose().scale(k, k);
		PlayerFaceExtractor.extractRenderState(d.g, info.getSkin().body().texturePath(), 0, 0, 8, info.showHat(), flip, -1);
		d.g.pose().popMatrix();
	}

	private void drawPing(Draw d, int latency, float right, float y, float h) {
		if (ping.is("Hidden")) {
			return;
		}
		float x = right;
		if (ping.is("Bars") || ping.is("Bars and number")) {
			x -= 11;
			d.spriteAt(pingSprite(latency), x, y + (h - 8) / 2f, 10, 8, 0xFFFFFFFF);
			if (ping.is("Bars")) {
				return;
			}
		}
		Component number = Component.literal(pingText(latency));
		float w = width(d, number);
		text(d, number, x - w - 1.5f, y, h, pingColors.on() ? pingColor(latency) : 0xFFFFFFFF);
	}

	private static Identifier pingSprite(int latency) {
		if (latency < 0) {
			return PING_UNKNOWN;
		}
		if (latency < 150) {
			return PING_SPRITES[4];
		}
		if (latency < 300) {
			return PING_SPRITES[3];
		}
		if (latency < 600) {
			return PING_SPRITES[2];
		}
		if (latency < 1000) {
			return PING_SPRITES[1];
		}
		return PING_SPRITES[0];
	}

	private void drawHearts(Draw d, int score, float left, float right, float y, float h) {
		int full = Mth.positiveCeilDiv(Math.max(score, 0), 2);
		int total = Math.max(score, 20) / 2;
		if (full <= 0) {
			return;
		}
		int per = Mth.floor(Math.min((right - left - 4) / total, 9f));
		if (per <= 3) {
			float pct = Mth.clamp(score / 20f, 0f, 1f);
			int color = 0xFF000000 | (int) ((1f - pct) * 255f) << 16 | (int) (pct * 255f) << 8;
			Component text = Component.literal(String.format(java.util.Locale.ROOT, "%.1f hp", score / 2f));
			text(d, text, (left + right - width(d, text)) / 2f, y, h, color);
			return;
		}
		float hy = y + (h - 9) / 2f;
		for (int i = full; i < total; i++) {
			d.spriteAt(HEART_CONTAINER, left + i * per, hy, 9, 9, 0xFFFFFFFF);
		}
		for (int i = 0; i < full; i++) {
			float hx = left + i * per;
			d.spriteAt(HEART_CONTAINER, hx, hy, 9, 9, 0xFFFFFFFF);
			if (i * 2 + 1 < score) {
				d.spriteAt(i >= 10 ? HEART_EXTRA_FULL : HEART_FULL, hx, hy, 9, 9, 0xFFFFFFFF);
			}
			if (i * 2 + 1 == score) {
				d.spriteAt(i >= 10 ? HEART_EXTRA_HALF : HEART_HALF, hx, hy, 9, 9, 0xFFFFFFFF);
			}
		}
	}
}
