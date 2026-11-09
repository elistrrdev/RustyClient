package com.rustyclient.module.hud;

import com.rustyclient.core.Category;
import com.rustyclient.core.HudModule;
import com.rustyclient.core.Theme;
import com.rustyclient.core.setting.BoolSetting;
import com.rustyclient.core.setting.ChoiceSetting;
import com.rustyclient.core.setting.ColorSetting;
import com.rustyclient.core.setting.NumberSetting;
import com.rustyclient.platform.Compat;
import com.rustyclient.render.Draw;
import com.rustyclient.render.Fonts;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.List;
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
				d.round(0, 0, w, h, 4, backgroundColor.argb());
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
		private final NumberSetting max = number("Packs shown", APPEARANCE, 3, 1, 8, 1, NumberSetting.integer(""));

		public PackDisplay() {
			super("Pack Display", "layers", false, 0.01, 0.9);
		}

		@Override
		public void render(Draw d, boolean editing) {
			List<String> names = new ArrayList<>();
			Minecraft.getInstance().getResourcePackRepository().getSelectedPacks().forEach(p -> {
				String id = p.getId();
				if (!id.equals("vanilla") && !id.startsWith("fabric") && !id.equals("rustyclient")) {
					names.add(0, p.getTitle().getString());
				}
			});
			if (names.isEmpty()) {
				names.add("Default");
			}
			while (names.size() > max.i()) {
				names.remove(names.size() - 1);
			}
			float w = 60;
			for (String n : names) {
				w = Math.max(w, d.width(n, Fonts.Style.SEMIBOLD, 8.5f) + 22);
			}
			float row = 12;
			panel(d, w, names.size() * row + 6);
			float y = 3 + row / 2f;
			for (String n : names) {
				d.icon("layers", 8, y, 7, accent());
				d.textShadowed(n, 15, y, Fonts.Style.SEMIBOLD, 8.5f, textColor.argb(), shadow.on());
				y += row;
			}
		}
	}
}
