package com.rustyclient.module.hud;

import com.rustyclient.core.Category;
import com.rustyclient.core.HudModule;
import com.rustyclient.core.Theme;
import com.rustyclient.core.setting.BoolSetting;
import com.rustyclient.core.setting.ChoiceSetting;
import com.rustyclient.core.setting.ColorSetting;
import com.rustyclient.core.setting.NumberSetting;
import com.rustyclient.core.setting.TextSetting;
import com.rustyclient.platform.Compat;
import com.rustyclient.render.Draw;
import com.rustyclient.render.Fonts;
import com.rustyclient.render.Tex;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;

public final class InfoHuds {
	private InfoHuds() {
	}

	abstract static class Panel extends HudModule {
		final BoolSetting background = bool("Draw background", GENERAL, true);
		final BoolSetting shadow = bool("Text shadow", GENERAL, false);
		final ColorSetting textColor = color("Text color", APPEARANCE, 0xFFF2EEEA);
		final ColorSetting accentColor = color("Accent color", APPEARANCE, Theme.DEFAULT_ACCENT);
		final ColorSetting backgroundColor = colorAlpha("Background color", APPEARANCE, 0x8C0F0E0D);

		Panel(String name, String icon, boolean on, double fx, double fy) {
			super(name, icon, Category.HUD, on, fx, fy);
		}

		void panel(Draw d, float w, float h) {
			width = w;
			height = h;
			if (background.on()) {
				d.round(0, 0, w, h, radius(), backgroundColor.argb());
			}
		}

		int accent() {
			return Theme.hudAccent(accentColor.argb());
		}
	}

	public static final class Coordinates extends Panel {
		private final BoolSetting biome = bool("Show biome", GENERAL, true);
		private final BoolSetting facing = bool("Show facing", GENERAL, true);
		private final ChoiceSetting layout = choice("Layout", APPEARANCE, "Vertical", "Vertical", "Horizontal");
		private final ChoiceSetting decimals = choice("Precision", APPEARANCE, "Whole", "Whole", "One decimal");

		public Coordinates() {
			super("Coordinates", "locate-fixed", false, 0.01, 0.13);
		}

		private String fmt(double v) {
			return decimals.is("Whole") ? String.valueOf((long) Math.floor(v)) : String.format(Locale.ROOT, "%.1f", v);
		}

		@Override
		public void render(Draw d, boolean editing) {
			Player p = mc.player;
			double x = p == null ? 0 : p.getX(), y = p == null ? 64 : p.getY(), z = p == null ? 0 : p.getZ();
			List<String[]> rows = new ArrayList<>();
			rows.add(new String[]{"X", fmt(x)});
			rows.add(new String[]{"Y", fmt(y)});
			rows.add(new String[]{"Z", fmt(z)});
			if (facing.on() && p != null) {
				rows.add(new String[]{"F", p.getDirection().getName().substring(0, 1).toUpperCase(Locale.ROOT) + p.getDirection().getName().substring(1)});
			}
			if (biome.on() && p != null && mc.level != null) {
				Holder<Biome> holder = mc.level.getBiome(p.blockPosition());
				String id = holder.unwrapKey().map(k -> k.identifier().getPath()).orElse("unknown");
				rows.add(new String[]{"B", pretty(id)});
			}
			Fonts.Style st = Fonts.Style.SEMIBOLD;
			float size = 9;
			if (layout.is("Horizontal")) {
				float w = 6;
				for (String[] r : rows) {
					w += d.width(r[0], st, size) + 4 + d.width(r[1], st, size) + 8;
				}
				w -= 2;
				panel(d, w, 17);
				float cx = 6;
				for (String[] r : rows) {
					d.textShadowed(r[0], cx, 8.5f, st, size, accent(), shadow.on());
					cx += d.width(r[0], st, size) + 4;
					d.textShadowed(r[1], cx, 8.5f, st, size, textColor.argb(), shadow.on());
					cx += d.width(r[1], st, size) + 8;
				}
				return;
			}
			float lineH = 11;
			float w = 0;
			for (String[] r : rows) {
				w = Math.max(w, 10 + d.width(r[1], st, size));
			}
			w = Math.max(70, w + 14);
			float h = rows.size() * lineH + 8;
			panel(d, w, h);
			float cy = 4 + lineH / 2f;
			for (String[] r : rows) {
				d.textShadowed(r[0], 6, cy, st, size, accent(), shadow.on());
				d.textShadowed(r[1], 17, cy, st, size, textColor.argb(), shadow.on());
				cy += lineH;
			}
		}

		private static String pretty(String id) {
			StringBuilder out = new StringBuilder();
			for (String part : id.split("_")) {
				if (!part.isEmpty()) {
					out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
				}
			}
			return out.toString().trim();
		}
	}

	public static final class Direction extends Panel {
		private final BoolSetting degrees = bool("Show degrees", GENERAL, true);

		public Direction() {
			super("Direction", "compass", false, 0.5, 0.01);
		}

		@Override
		public void render(Draw d, boolean editing) {
			float yaw = mc.player == null ? 0 : mc.player.getYRot();
			float heading = ((yaw % 360f) + 540f) % 360f;
			float w = 160, h = degrees.on() ? 26 : 17;
			panel(d, w, h);
			d.scissor(2, 0, w - 4, h);
			float pxPerDeg = 1.3f;
			String[] names = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
			for (int i = -360; i <= 720; i += 15) {
				float diff = i - (heading + 180f);
				float x = w / 2f + diff * pxPerDeg;
				if (x < -10 || x > w + 10) {
					continue;
				}
				int idx = Math.floorMod(i, 360);
				if (idx % 45 == 0) {
					String n = names[(idx / 45) % 8];
					boolean cardinal = n.length() == 1;
					d.textCenter(n, x, 8.5f, Fonts.Style.BOLD, cardinal ? 9 : 7, cardinal ? (n.equals("N") ? accent() : textColor.argb()) : Theme.fade(textColor.argb(), 0.7f));
				} else {
					d.rect(x - 0.5f, 6, 1, 5, Theme.fade(textColor.argb(), 0.4f));
				}
			}
			d.endScissor();
			d.rect(w / 2f - 0.5f, 0, 1, 3, accent());
			if (degrees.on()) {
				String deg = Math.round(heading) % 360 + "°";
				d.textCenter(deg, w / 2f, 19.5f, Fonts.Style.SEMIBOLD, 7, textColor.argb());
			}
		}
	}

	public static final class ArmorStatus extends Panel {
		private final BoolSetting mainHand = bool("Show held item", GENERAL, true);
		private final ChoiceSetting durability = choice("Durability", APPEARANCE, "Value", "Value", "Percent", "Hidden");
		private final ChoiceSetting layout = choice("Layout", APPEARANCE, "Vertical", "Vertical", "Horizontal");

		public ArmorStatus() {
			super("Armor Status", "shield", false, 0.99, 0.5);
			background.set(false);
		}

		@Override
		public void render(Draw d, boolean editing) {
			List<ItemStack> items = new ArrayList<>();
			Player p = mc.player;
			EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
			for (EquipmentSlot slot : slots) {
				ItemStack s = p == null ? ItemStack.EMPTY : p.getItemBySlot(slot);
				if (!s.isEmpty()) {
					items.add(s);
				}
			}
			if (mainHand.on() && p != null && !p.getMainHandItem().isEmpty()) {
				items.add(p.getMainHandItem());
			}
			if (items.isEmpty() && editing) {
				items.add(new ItemStack(Items.DIAMOND_HELMET));
				items.add(new ItemStack(Items.DIAMOND_CHESTPLATE));
				items.add(new ItemStack(Items.DIAMOND_LEGGINGS));
				items.add(new ItemStack(Items.DIAMOND_BOOTS));
			}
			boolean vertical = layout.is("Vertical");
			float row = 18;
			float textW = 0;
			for (ItemStack s : items) {
				textW = Math.max(textW, d.width(dura(s), Fonts.Style.SEMIBOLD, 8));
			}
			float w = vertical ? 22 + textW + 6 : Math.max(1, items.size()) * (durability.is("Hidden") ? 20 : 20 + textW + 4);
			float h = vertical ? Math.max(1, items.size()) * row + 2 : 20;
			panel(d, w, h);
			float x = 2, y = 1;
			for (ItemStack s : items) {
				d.item(s, x, y + 0.5f, 16, false);
				String t = dura(s);
				if (!t.isEmpty()) {
					d.textShadowed(t, x + 19, y + 9, Fonts.Style.SEMIBOLD, 8, duraColor(s), shadow.on());
				}
				if (vertical) {
					y += row;
				} else {
					x += durability.is("Hidden") ? 20 : 20 + textW + 4;
				}
			}
		}

		private String dura(ItemStack s) {
			if (durability.is("Hidden")) {
				return "";
			}
			if (!s.isDamageableItem()) {
				return s.getCount() > 1 ? String.valueOf(s.getCount()) : "";
			}
			int left = s.getMaxDamage() - s.getDamageValue();
			return durability.is("Percent") ? Math.round(left * 100f / s.getMaxDamage()) + "%" : String.valueOf(left);
		}

		private int duraColor(ItemStack s) {
			if (!s.isDamageableItem()) {
				return textColor.argb();
			}
			float f = 1f - (float) s.getDamageValue() / s.getMaxDamage();
			if (f > 0.5f) {
				return textColor.argb();
			}
			return f > 0.2f ? 0xFFF2C14E : Theme.DANGER;
		}
	}

	public static final class PotionHud extends Panel {
		private final BoolSetting icons = bool("Show icons", GENERAL, true);
		private final BoolSetting hideVanilla = bool("Hide vanilla effects", GENERAL, true);
		private final BoolSetting blink = bool("Blink when ending", GENERAL, true);

		public PotionHud() {
			super("Potion HUD", "flask-conical", false, 0.01, 0.42);
			background.set(false);
		}

		public boolean hidesVanilla() {
			return enabled() && hideVanilla.on();
		}

		@Override
		public void render(Draw d, boolean editing) {
			List<MobEffectInstance> list = new ArrayList<>();
			if (mc.player != null) {
				list.addAll(mc.player.getActiveEffects());
			}
			list.sort((a, b) -> Integer.compare(b.getDuration(), a.getDuration()));
			float row = 22;
			float w = 110;
			for (MobEffectInstance e : list) {
				w = Math.max(w, 26 + d.width(name(e), Fonts.Style.SEMIBOLD, 9) + 8);
			}
			if (list.isEmpty()) {
				panel(d, w, editing ? row : 0.1f);
				if (editing) {
					d.textShadowed("Speed II", 26, 7, Fonts.Style.SEMIBOLD, 9, textColor.argb(), shadow.on());
					d.textShadowed("1:30", 26, 16, Fonts.Style.MEDIUM, 7, Theme.fade(textColor.argb(), 0.7f), shadow.on());
				}
				return;
			}
			panel(d, w, list.size() * row);
			float y = 0;
			for (MobEffectInstance e : list) {
				boolean ending = !e.isInfiniteDuration() && e.getDuration() < 200;
				float a = 1f;
				if (blink.on() && ending) {
					a = 0.45f + 0.55f * (float) Math.abs(Math.sin(System.currentTimeMillis() / 250.0));
				}
				float prev = d.alpha();
				d.alpha(prev * a);
				if (icons.on()) {
					d.sprite(Compat.effectSprite(e.getEffect()), 3, y + 2, 18);
				}
				float tx = icons.on() ? 26 : 5;
				d.textShadowed(name(e), tx, y + 7, Fonts.Style.SEMIBOLD, 9, effectColor(e.getEffect()), shadow.on());
				d.textShadowed(time(e), tx, y + 16, Fonts.Style.MEDIUM, 7, Theme.fade(textColor.argb(), 0.75f), shadow.on());
				d.alpha(prev);
				y += row;
			}
		}

		private int effectColor(Holder<MobEffect> effect) {
			return effect.value().isBeneficial() ? textColor.argb() : 0xFFF07E7E;
		}

		private String name(MobEffectInstance e) {
			String n = e.getEffect().value().getDisplayName().getString();
			int amp = e.getAmplifier();
			if (amp > 0) {
				String[] roman = {"II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
				n += " " + (amp - 1 < roman.length ? roman[amp - 1] : String.valueOf(amp + 1));
			}
			return n;
		}

		private String time(MobEffectInstance e) {
			if (e.isInfiniteDuration()) {
				return "**:**";
			}
			int s = e.getDuration() / 20;
			return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
		}
	}

	public static final class ItemCounter extends Panel {
		private final BoolSetting arrows = bool("Arrows", GENERAL, true);
		private final BoolSetting totems = bool("Totems", GENERAL, true);
		private final BoolSetting pearls = bool("Ender pearls", GENERAL, true);
		private final BoolSetting gapples = bool("Golden apples", GENERAL, false);
		private final BoolSetting hideZero = bool("Hide empty", GENERAL, true);

		public ItemCounter() {
			super("Item Counter", "package", false, 0.99, 0.7);
			background.set(false);
		}

		private int count(net.minecraft.world.item.Item item) {
			Player p = mc.player;
			if (p == null) {
				return 0;
			}
			int n = 0;
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
				ItemStack s = p.getInventory().getItem(i);
				if (s.is(item)) {
					n += s.getCount();
				}
			}
			return n;
		}

		@Override
		public void render(Draw d, boolean editing) {
			List<Object[]> rows = new ArrayList<>();
			if (arrows.on()) {
				rows.add(new Object[]{new ItemStack(Items.ARROW), count(Items.ARROW) + count(Items.SPECTRAL_ARROW) + count(Items.TIPPED_ARROW)});
			}
			if (totems.on()) {
				rows.add(new Object[]{new ItemStack(Items.TOTEM_OF_UNDYING), count(Items.TOTEM_OF_UNDYING)});
			}
			if (pearls.on()) {
				rows.add(new Object[]{new ItemStack(Items.ENDER_PEARL), count(Items.ENDER_PEARL)});
			}
			if (gapples.on()) {
				rows.add(new Object[]{new ItemStack(Items.GOLDEN_APPLE), count(Items.GOLDEN_APPLE) + count(Items.ENCHANTED_GOLDEN_APPLE)});
			}
			if (hideZero.on() && !editing) {
				rows.removeIf(r -> (int) r[1] == 0);
			}
			float row = 18;
			panel(d, 48, Math.max(row, rows.size() * row));
			float y = 1;
			for (Object[] r : rows) {
				d.item((ItemStack) r[0], 2, y, 16, false);
				d.textShadowed(String.valueOf(r[1]), 22, y + 8.5f, Fonts.Style.SEMIBOLD, 9, textColor.argb(), shadow.on());
				y += row;
			}
		}
	}

	public static final class PackDisplay extends Panel {
		private static final String LIST = "Pack list";
		private final BoolSetting icons = bool("Show pack icons", GENERAL, true);
		private final BoolSetting names = bool("Show pack names", GENERAL, true);
		private final BoolSetting descriptions = bool("Show descriptions", GENERAL, false);
		private final BoolSetting header = bool("Show header", GENERAL, false);
		private final BoolSetting count = bool("Show pack count", GENERAL, true);
		private final BoolSetting colors = bool("Keep pack text colors", GENERAL, false);
		private final BoolSetting showDefault = bool("Show Default when empty", GENERAL, true);
		private final BoolSetting builtIn = bool("Show built-in packs", LIST, true);
		private final BoolSetting server = bool("Show server packs", LIST, true);
		private final BoolSetting samples = bool("Show sample packs in editor", LIST, true);
		private final ChoiceSetting order = choice("Order", LIST, "Highest first", "Highest first", "Lowest first");
		private final NumberSetting max = number("Packs shown", LIST, 3, 1, 12, 1, NumberSetting.integer(""));
		private final NumberSetting maxName = number("Max name length", LIST, 32, 6, 64, 1, NumberSetting.integer(""));
		private final TextSetting title = text("Header text", LIST, "Resource Packs", 32);
		private final ChoiceSetting layout = choice("Layout", APPEARANCE, "Vertical", "Vertical", "Horizontal");
		private final ChoiceSetting align = choice("Alignment", APPEARANCE, "Left", "Left", "Right");
		private final NumberSetting iconSize = number("Icon size", APPEARANCE, 14, 8, 40, 1, NumberSetting.integer(""));
		private final NumberSetting textSize = number("Name size", APPEARANCE, 8.5, 5, 16, 0.5, NumberSetting.decimal(""));
		private final NumberSetting descSize = number("Description size", APPEARANCE, 7, 5, 14, 0.5, NumberSetting.decimal(""));
		private final NumberSetting spacing = number("Row spacing", APPEARANCE, 2, 0, 10, 0.5, NumberSetting.decimal(""));
		private final NumberSetting padding = number("Padding", APPEARANCE, 4, 0, 12, 0.5, NumberSetting.decimal(""));
		private final BoolSetting iconFrame = bool("Icon frame", APPEARANCE, false);
		private final ColorSetting frameColor = colorAlpha("Icon frame color", APPEARANCE, 0x60FFFFFF);
		private final ColorSetting descColor = colorAlpha("Description color", APPEARANCE, 0xB0F2EEEA);
		private final Map<String, Tex> cache = new HashMap<>();
		private final Set<String> failed = new HashSet<>();
		private Tex unknown;

		public PackDisplay() {
			super("Pack Display", "layers", false, 0.01, 0.9);
			descSize.visibleWhen(descriptions::on);
			descColor.visibleWhen(descriptions::on);
			title.visibleWhen(header::on);
			count.visibleWhen(header::on);
			iconSize.visibleWhen(icons::on);
			iconFrame.visibleWhen(icons::on);
			frameColor.visibleWhen(() -> icons.on() && iconFrame.on());
			textSize.visibleWhen(names::on);
			maxName.visibleWhen(() -> names.on() || descriptions.on());
			colors.visibleWhen(names::on);
		}

		private record Entry(String id, Pack pack, String name, Component styled, String description) {
		}

		private List<Entry> entries(boolean editing) {
			List<Entry> list = new ArrayList<>();
			Pack vanilla = null;
			for (Pack p : Minecraft.getInstance().getResourcePackRepository().getSelectedPacks()) {
				String id = p.getId();
				if (id.equals("vanilla")) {
					vanilla = p;
					continue;
				}
				if (id.startsWith("fabric") || id.equals("rustyclient")) {
					continue;
				}
				if (!builtIn.on() && p.getPackSource() == PackSource.BUILT_IN) {
					continue;
				}
				if (!server.on() && p.getPackSource() == PackSource.SERVER) {
					continue;
				}
				list.add(new Entry(id, p, p.getTitle().getString(), p.getTitle(), firstLine(p.getDescription().getString())));
			}
			if (order.is("Highest first")) {
				Collections.reverse(list);
			}
			if (list.isEmpty() && editing && samples.on()) {
				list.add(new Entry("sample_a", null, "Faithful 32x", Component.literal("Faithful 32x"), "A sample pack"));
				list.add(new Entry("sample_b", null, "Low Fire", Component.literal("Low Fire"), "Lower fire overlay"));
			}
			if (list.isEmpty() && showDefault.on()) {
				String description = vanilla == null ? "" : firstLine(vanilla.getDescription().getString());
				list.add(new Entry("vanilla", vanilla, "Default", Component.literal("Default"), description));
			}
			while (list.size() > max.i()) {
				list.remove(list.size() - 1);
			}
			return list;
		}

		private static String firstLine(String text) {
			int nl = text.indexOf('\n');
			return (nl >= 0 ? text.substring(0, nl) : text).trim();
		}

		private String clip(String text) {
			int limit = maxName.i();
			return text.length() > limit ? text.substring(0, Math.max(1, limit - 3)).trim() + "..." : text;
		}

		private Tex icon(Entry e) {
			if (e.pack() == null) {
				return unknown();
			}
			Tex tex = cache.get(e.id());
			if (tex != null) {
				return tex;
			}
			if (failed.contains(e.id())) {
				return unknown();
			}
			byte[] bytes = Compat.packIcon(e.pack());
			try {
				if (bytes != null) {
					tex = Tex.color("pack", NativeImage.read(bytes));
					cache.put(e.id(), tex);
					return tex;
				}
			} catch (Exception ignored) {
			}
			failed.add(e.id());
			return unknown();
		}

		private Tex unknown() {
			if (unknown == null) {
				try (InputStream in = Minecraft.getInstance().getResourceManager().open(Identifier.withDefaultNamespace("textures/misc/unknown_pack.png"))) {
					unknown = Tex.color("pack_unknown", NativeImage.read(in));
				} catch (Exception ignored) {
				}
			}
			return unknown;
		}

		private void release(List<Entry> shown) {
			Set<String> keep = new HashSet<>();
			for (Entry e : shown) {
				keep.add(e.id());
			}
			cache.entrySet().removeIf(entry -> {
				if (keep.contains(entry.getKey())) {
					return false;
				}
				entry.getValue().close();
				return true;
			});
			failed.retainAll(keep);
		}

		private Component styledName(Entry e) {
			return Component.literal(clip(e.name())).withStyle(e.styled().getStyle());
		}

		private float nameWidth(Draw d, Entry e) {
			if (!names.on()) {
				return 0;
			}
			if (colors.on()) {
				return Draw.vanillaWidth(styledName(e), textSize.f());
			}
			return d.width(clip(e.name()), Fonts.Style.SEMIBOLD, textSize.f());
		}

		private float descWidth(Draw d, Entry e) {
			if (!descriptions.on() || e.description().isEmpty()) {
				return 0;
			}
			return d.width(clip(e.description()), Fonts.Style.MEDIUM, descSize.f());
		}

		private float iconSpace() {
			return icons.on() ? iconSize.f() : 0;
		}

		private float textGap() {
			return icons.on() && (names.on() || descriptions.on()) ? 5 : 0;
		}

		private float cellWidth(Draw d, Entry e) {
			return iconSpace() + textGap() + Math.max(nameWidth(d, e), descWidth(d, e));
		}

		private float textHeight() {
			float h = names.on() ? textSize.f() : 0;
			if (descriptions.on()) {
				h += (names.on() ? 1.5f : 0) + descSize.f();
			}
			return h;
		}

		private String headerText(int n) {
			String t = title.get().isBlank() ? "Resource Packs" : title.get();
			return count.on() ? t + " (" + n + ")" : t;
		}

		@Override
		public void render(Draw d, boolean editing) {
			List<Entry> list = entries(editing);
			release(list);
			float pad = background.on() ? padding.f() : 0;
			float rowH = Math.max(iconSpace(), textHeight());
			float headerH = header.on() ? 12 : 0;
			String head = headerText(list.size());
			float headW = header.on() ? d.width(head, Fonts.Style.BOLD, 8.5f) : 0;
			boolean horizontal = layout.is("Horizontal");
			boolean right = align.is("Right");
			float gapX = Math.max(6, spacing.f() * 3);
			float inner = 0;
			for (Entry e : list) {
				float cw = cellWidth(d, e);
				inner = horizontal ? inner + cw : Math.max(inner, cw);
			}
			if (horizontal) {
				inner += Math.max(0, list.size() - 1) * gapX;
			}
			float w = Math.max(20, Math.max(inner, headW) + pad * 2);
			float body = horizontal ? rowH : list.size() * rowH + Math.max(0, list.size() - 1) * spacing.f();
			float h = Math.max(4, pad * 2 + headerH + body);
			panel(d, w, h);
			if (header.on()) {
				float hx = right ? w - pad - headW : pad;
				d.textShadowed(head, hx, pad + 4.5f, Fonts.Style.BOLD, 8.5f, accent(), shadow.on());
			}
			float x = right && horizontal ? w - pad - inner : pad;
			float y = pad + headerH;
			for (Entry e : list) {
				float cw = cellWidth(d, e);
				float cx = horizontal ? x : (right ? w - pad - cw : pad);
				drawEntry(d, e, cx, y, rowH, cw, right);
				if (horizontal) {
					x += cw + gapX;
				} else {
					y += rowH + spacing.f();
				}
			}
		}

		private void drawEntry(Draw d, Entry e, float x, float y, float rowH, float cellW, boolean right) {
			float is = iconSpace(), gap = textGap();
			float ix = right ? x + cellW - is : x;
			if (is > 0) {
				float iy = y + (rowH - is) / 2f;
				if (iconFrame.on()) {
					d.ring(ix - 1, iy - 1, is + 2, is + 2, Math.min(radius(), is / 4f), 1, frameColor.argb());
				}
				d.image(icon(e), ix, iy, is, is, 0xFFFFFFFF);
			}
			float textRight = x + cellW - is - gap;
			float tx = right ? x : x + is + gap;
			float ty = y + (rowH - textHeight()) / 2f;
			float ts = textSize.f(), ds = descSize.f();
			if (names.on()) {
				float nx = right ? textRight - nameWidth(d, e) : tx;
				if (colors.on()) {
					d.vanillaText(styledName(e), nx, ty + ts / 2f - ts * 4f / 9f, ts, textColor.argb(), shadow.on());
				} else {
					d.textShadowed(clip(e.name()), nx, ty + ts / 2f, Fonts.Style.SEMIBOLD, ts, textColor.argb(), shadow.on());
				}
				ty += ts + 1.5f;
			}
			if (descriptions.on() && !e.description().isEmpty()) {
				float dx = right ? textRight - descWidth(d, e) : tx;
				d.textShadowed(clip(e.description()), dx, ty + ds / 2f, Fonts.Style.MEDIUM, ds, descColor.argb(), shadow.on());
			}
		}
	}
}
