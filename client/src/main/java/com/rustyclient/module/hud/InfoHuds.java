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
		private static final String SHOWN = "Shown";
		private final BoolSetting showX = bool("Show X", SHOWN, true);
		private final BoolSetting showY = bool("Show Y", SHOWN, true);
		private final BoolSetting showZ = bool("Show Z", SHOWN, true);
		private final BoolSetting facing = bool("Show facing", SHOWN, true);
		private final BoolSetting axis = bool("Show facing axis", SHOWN, false);
		private final BoolSetting yaw = bool("Show yaw and pitch", SHOWN, false);
		private final BoolSetting biome = bool("Show biome", SHOWN, true);
		private final BoolSetting dimension = bool("Show dimension", SHOWN, false);
		private final BoolSetting other = bool("Show other dimension", SHOWN, false);
		private final BoolSetting chunk = bool("Show chunk", SHOWN, false);
		private final BoolSetting labels = bool("Show labels", GENERAL, true);
		private final BoolSetting colon = bool("Colon after labels", GENERAL, false);
		private final BoolSetting axisHints = bool("Axis direction hints", GENERAL, false);
		private final ChoiceSetting layout = choice("Layout", APPEARANCE, "Vertical", "Vertical", "Horizontal", "Compact");
		private final ChoiceSetting decimals = choice("Precision", APPEARANCE, "Whole", "Whole", "One decimal", "Two decimals");
		private final ChoiceSetting labelStyle = choice("Label style", APPEARANCE, "Letters", "Letters", "Words");
		private final NumberSetting textSize = number("Text size", APPEARANCE, 9, 5, 16, 0.5, NumberSetting.decimal(""));
		private final NumberSetting lineSpacing = number("Line spacing", APPEARANCE, 2, 0, 8, 0.5, NumberSetting.decimal(""));
		private final NumberSetting minWidth = number("Minimum width", APPEARANCE, 70, 0, 200, 1, NumberSetting.integer(""));

		public Coordinates() {
			super("Coordinates", "locate-fixed", false, 0.01, 0.13);
			colon.visibleWhen(labels::on);
			labelStyle.visibleWhen(labels::on);
			lineSpacing.visibleWhen(() -> !layout.is("Horizontal"));
			minWidth.visibleWhen(() -> !layout.is("Horizontal"));
			axis.visibleWhen(facing::on);
		}

		private String fmt(double v) {
			return switch (decimals.get()) {
				case "One decimal" -> String.format(Locale.ROOT, "%.1f", v);
				case "Two decimals" -> String.format(Locale.ROOT, "%.2f", v);
				default -> String.valueOf((long) Math.floor(v));
			};
		}

		private String label(String letter, String word) {
			if (!labels.on()) {
				return "";
			}
			String l = labelStyle.is("Words") ? word : letter;
			return colon.on() ? l + ":" : l;
		}

		private String hint(Player p, char which) {
			if (!axisHints.on() || p == null) {
				return "";
			}
			net.minecraft.core.Direction d = p.getDirection();
			if (which == 'x' && d.getAxis() == net.minecraft.core.Direction.Axis.X) {
				return d.getStepX() > 0 ? " +" : " -";
			}
			if (which == 'z' && d.getAxis() == net.minecraft.core.Direction.Axis.Z) {
				return d.getStepZ() > 0 ? " +" : " -";
			}
			return "";
		}

		private static String capital(String s) {
			return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
		}

		private List<String[]> rows(Player p) {
			double x = p == null ? 0 : p.getX(), y = p == null ? 64 : p.getY(), z = p == null ? 0 : p.getZ();
			List<String[]> rows = new ArrayList<>();
			if (layout.is("Compact")) {
				List<String> parts = new ArrayList<>();
				if (showX.on()) {
					parts.add(fmt(x));
				}
				if (showY.on()) {
					parts.add(fmt(y));
				}
				if (showZ.on()) {
					parts.add(fmt(z));
				}
				if (!parts.isEmpty()) {
					rows.add(new String[]{label("XYZ", "Position"), String.join(", ", parts)});
				}
			} else {
				if (showX.on()) {
					rows.add(new String[]{label("X", "X"), fmt(x) + hint(p, 'x')});
				}
				if (showY.on()) {
					rows.add(new String[]{label("Y", "Y"), fmt(y)});
				}
				if (showZ.on()) {
					rows.add(new String[]{label("Z", "Z"), fmt(z) + hint(p, 'z')});
				}
			}
			if (facing.on() && p != null) {
				net.minecraft.core.Direction d = p.getDirection();
				String text = capital(d.getName());
				if (axis.on()) {
					text += d.getAxis() == net.minecraft.core.Direction.Axis.X ? (d.getStepX() > 0 ? " (+X)" : " (-X)") : (d.getStepZ() > 0 ? " (+Z)" : " (-Z)");
				}
				rows.add(new String[]{label("F", "Facing"), text});
			}
			if (yaw.on() && p != null) {
				float heading = ((p.getYRot() % 360f) + 360f) % 360f;
				rows.add(new String[]{label("R", "Rotation"), String.format(Locale.ROOT, "%.1f / %.1f", heading, p.getXRot())});
			}
			if (biome.on() && p != null && mc.level != null) {
				Holder<Biome> holder = mc.level.getBiome(p.blockPosition());
				String id = holder.unwrapKey().map(k -> k.identifier().getPath()).orElse("unknown");
				rows.add(new String[]{label("B", "Biome"), pretty(id)});
			}
			if ((dimension.on() || other.on()) && p != null && mc.level != null) {
				String dim = mc.level.dimension().identifier().getPath();
				if (dimension.on()) {
					rows.add(new String[]{label("D", "World"), pretty(dim)});
				}
				if (other.on()) {
					if (dim.equals("the_nether")) {
						rows.add(new String[]{label("O", "Overworld"), fmt(x * 8) + ", " + fmt(z * 8)});
					} else if (dim.equals("overworld")) {
						rows.add(new String[]{label("N", "Nether"), fmt(x / 8) + ", " + fmt(z / 8)});
					}
				}
			}
			if (chunk.on() && p != null) {
				int bx = p.blockPosition().getX(), bz = p.blockPosition().getZ();
				rows.add(new String[]{label("C", "Chunk"), (bx >> 4) + ", " + (bz >> 4) + "  (" + (bx & 15) + ", " + (bz & 15) + ")"});
			}
			return rows;
		}

		@Override
		public void render(Draw d, boolean editing) {
			Player p = mc.player;
			List<String[]> rows = rows(p);
			Fonts.Style st = Fonts.Style.SEMIBOLD;
			float size = textSize.f();
			float pad = 6;
			if (rows.isEmpty()) {
				panel(d, 40, editing ? 14 : 0.1f);
				return;
			}
			if (layout.is("Horizontal")) {
				float w = pad;
				for (String[] r : rows) {
					w += (r[0].isEmpty() ? 0 : d.width(r[0], st, size) + 4) + d.width(r[1], st, size) + 8;
				}
				w = w - 8 + pad;
				float h = size + 8;
				panel(d, w, h);
				float cx = pad;
				for (String[] r : rows) {
					if (!r[0].isEmpty()) {
						d.textShadowed(r[0], cx, h / 2f, st, size, accent(), shadow.on());
						cx += d.width(r[0], st, size) + 4;
					}
					d.textShadowed(r[1], cx, h / 2f, st, size, textColor.argb(), shadow.on());
					cx += d.width(r[1], st, size) + 8;
				}
				return;
			}
			float labelW = 0;
			for (String[] r : rows) {
				labelW = Math.max(labelW, r[0].isEmpty() ? 0 : d.width(r[0], st, size) + 4);
			}
			float lineH = size + lineSpacing.f();
			float valueW = 0;
			for (String[] r : rows) {
				valueW = Math.max(valueW, d.width(r[1], st, size));
			}
			float w = Math.max(minWidth.f(), pad + labelW + valueW + pad);
			float h = rows.size() * lineH - lineSpacing.f() + 8;
			panel(d, w, h);
			float cy = 4 + size / 2f;
			for (String[] r : rows) {
				if (!r[0].isEmpty()) {
					d.textShadowed(r[0], pad, cy, st, size, accent(), shadow.on());
				}
				d.textShadowed(r[1], pad + labelW, cy, st, size, textColor.argb(), shadow.on());
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
				String num = String.valueOf(Math.round(heading) % 360);
				d.textCenter(num, w / 2f, 19.5f, Fonts.Style.SEMIBOLD, 7, textColor.argb());
				float right = w / 2f + d.visualWidth(num, Fonts.Style.SEMIBOLD, 7) / 2f;
				d.textMid("°", right + 0.5f, 19.5f, Fonts.Style.SEMIBOLD, 7, textColor.argb());
			}
		}
	}

	public static final class ArmorStatus extends HudModule {
		private static final String POSITION = "Position";
		private static final String WARNING = "Low durability";
		private static final int SIZE = 22;
		private static final int STEP = 20;
		private static final Identifier HOTBAR = Identifier.withDefaultNamespace("hud/hotbar");
		private static final Identifier HOTBAR_OFFHAND = Identifier.withDefaultNamespace("hud/hotbar_offhand_left");
		private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		private static final Identifier[] EMPTY = {
			Identifier.withDefaultNamespace("container/slot/helmet"),
			Identifier.withDefaultNamespace("container/slot/chestplate"),
			Identifier.withDefaultNamespace("container/slot/leggings"),
			Identifier.withDefaultNamespace("container/slot/boots")
		};

		private final BoolSetting icons = bool("Empty slot icons", GENERAL, true);
		private final BoolSetting reversed = bool("Reversed order", GENERAL, false);
		private final BoolSetting mainHand = bool("Main hand durability", GENERAL, false);
		private final BoolSetting offHand = bool("Offhand durability", GENERAL, true);
		private final BoolSetting shadow = bool("Text shadow", GENERAL, true);
		private final ChoiceSetting anchor = choice("Anchor", POSITION, "Hotbar", "Free", "Hotbar", "Top center", "Top", "Center", "Bottom");
		private final ChoiceSetting side = choice("Side", POSITION, "Left", "Left", "Right");
		private final NumberSetting offsetX = number("Offset X", POSITION, 0, -200, 200, 1, NumberSetting.integer(""));
		private final NumberSetting offsetY = number("Offset Y", POSITION, 0, -200, 200, 1, NumberSetting.integer(""));
		private final ChoiceSetting offhandSlot = choice("Offhand slot", POSITION, "Adhere", "Ignore", "Adhere", "Always leave space");
		private final ChoiceSetting shown = choice("Slots shown", APPEARANCE, "Not empty", "Not empty", "If any present", "Always", "Damaged pieces");
		private final ChoiceSetting style = choice("Slot style", APPEARANCE, "Hotbar", "Hotbar", "Rounded corners", "Rounded", "None");
		private final ChoiceSetting orientation = choice("Orientation", APPEARANCE, "Horizontal", "Horizontal", "Vertical");
		private final ChoiceSetting durability = choice("Durability", APPEARANCE, "Bar", "Bar", "Value", "Percentage");
		private final NumberSetting opacity = number("Slot opacity", APPEARANCE, 1, 0.1, 1, 0.05, NumberSetting.percent());
		private final BoolSetting warning = bool("Warning icon", WARNING, true);
		private final BoolSetting sound = bool("Warning sound", WARNING, true);
		private final NumberSetting minValue = number("Warn at durability", WARNING, 20, 1, 200, 1, NumberSetting.integer(""));
		private final NumberSetting minPercent = number("Warn at percent", WARNING, 0.1, 0.01, 0.5, 0.01, NumberSetting.percent());
		private final NumberSetting bob = number("Warning bobbing", WARNING, 3, 0, 8, 1, NumberSetting.integer(""));
		private final ColorSetting warningColor = color("Warning color", WARNING, 0xFFFF4D4D);
		private final ItemStack[] last = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};

		public ArmorStatus() {
			super("Armor Status", "shield", Category.HUD, false, 0.985, 0.975);
			side.visibleWhen(() -> !anchor.is("Free") && !anchor.is("Top center"));
			offsetX.visibleWhen(() -> !anchor.is("Free"));
			offsetY.visibleWhen(() -> !anchor.is("Free"));
			offhandSlot.visibleWhen(() -> anchor.is("Hotbar"));
			sound.visibleWhen(warning::on);
			minValue.visibleWhen(warning::on);
			minPercent.visibleWhen(warning::on);
			bob.visibleWhen(warning::on);
			warningColor.visibleWhen(warning::on);
			anchor.onChange(() -> {
				if (anchor.is("Free")) {
					fx = 0.985;
					fy = 0.975;
				}
			});
		}

		@Override
		protected boolean usesRadius() {
			return false;
		}

		@Override
		public boolean dimsWithMenu() {
			return true;
		}

		private boolean vertical() {
			return orientation.is("Vertical");
		}

		private boolean low(ItemStack stack) {
			if (stack.isEmpty() || !stack.isDamageableItem()) {
				return false;
			}
			int damage = stack.getDamageValue(), max = stack.getMaxDamage();
			double left = 1.0 - (double) damage / max;
			return left <= minPercent.v() || max - damage <= minValue.i();
		}

		private List<ItemStack> items(Player p, boolean editing) {
			List<ItemStack> all = new ArrayList<>();
			boolean any = false;
			for (EquipmentSlot slot : SLOTS) {
				ItemStack s = p == null ? ItemStack.EMPTY : p.getItemBySlot(slot);
				all.add(s);
				any |= !s.isEmpty();
			}
			if (editing) {
				ItemStack[] samples = {new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE), new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS)};
				for (int i = 0; i < all.size(); i++) {
					if (all.get(i).isEmpty()) {
						all.set(i, samples[i]);
					}
				}
				return all;
			}
			List<ItemStack> out = new ArrayList<>();
			for (int i = 0; i < all.size(); i++) {
				ItemStack s = all.get(i);
				boolean keep = switch (shown.get()) {
					case "Always" -> true;
					case "If any present" -> any;
					case "Damaged pieces" -> low(s);
					default -> !s.isEmpty();
				};
				out.add(keep ? s : null);
			}
			return out;
		}

		private List<int[]> order(List<ItemStack> items) {
			List<int[]> slots = new ArrayList<>();
			for (int i = 0; i < items.size(); i++) {
				if (items.get(i) != null) {
					slots.add(new int[]{i});
				}
			}
			if (reversed.on()) {
				java.util.Collections.reverse(slots);
			}
			return slots;
		}

		private float extra() {
			float e = 0;
			if (warning.on()) {
				e += 10 + bob.f() / 2f;
			}
			if (!durability.is("Bar")) {
				e += 9;
			}
			return e;
		}

		private boolean extrasBefore() {
			return !anchor.is("Top") && !anchor.is("Top center");
		}

		@Override
		public boolean visibleNow() {
			return !order(items(mc.player, false)).isEmpty();
		}

		@Override
		public void tick() {
			if (!warning.on() || !sound.on() || mc.player == null) {
				return;
			}
			EquipmentSlot[] all = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
			boolean play = false;
			for (int i = 0; i < all.length; i++) {
				ItemStack now = mc.player.getItemBySlot(all[i]);
				ItemStack before = last[i];
				last[i] = now.copy();
				boolean changed = before.getDamageValue() != now.getDamageValue() && low(now);
				boolean hand = all[i] == EquipmentSlot.MAINHAND || all[i] == EquipmentSlot.OFFHAND;
				boolean switched = all[i] == EquipmentSlot.MAINHAND && !before.is(now.getItem());
				boolean wanted = !hand || (all[i] == EquipmentSlot.MAINHAND ? mainHand.on() : offHand.on());
				if (changed && !switched && wanted) {
					play = true;
				}
			}
			if (play) {
				mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(), 0.6f, 0.8f));
			}
		}

		@Override
		public float pixelX() {
			if (anchor.is("Free")) {
				return super.pixelX();
			}
			float gs = Draw.guiScale();
			float guiW = Draw.windowWidth() / gs;
			float w = pixelWidth() / gs;
			boolean left = side.is("Left");
			float ox = offsetX.f();
			float x = switch (anchor.get()) {
				case "Top center" -> (guiW - w) / 2f + ox;
				case "Hotbar" -> {
					float added = hotbarGap(left);
					yield left ? guiW / 2f - 98 - added - w - ox : guiW / 2f + 98 + added + ox;
				}
				default -> left ? ox : guiW - w - ox;
			};
			return Math.round(x) * gs;
		}

		private float hotbarGap(boolean left) {
			Player p = mc.player;
			if (p == null || offhandSlot.is("Ignore")) {
				return 0;
			}
			boolean mainArmSide = (p.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT) == left;
			if (offhandSlot.is("Always leave space")) {
				return mainArmSide ? 23 : 29;
			}
			if (mainArmSide) {
				return mc.options.attackIndicator().get() == net.minecraft.client.AttackIndicatorStatus.HOTBAR && p.getAttackStrengthScale(0f) < 1f ? 23 : 0;
			}
			return p.getOffhandItem().isEmpty() ? 0 : 29;
		}

		@Override
		public float pixelY() {
			if (anchor.is("Free")) {
				return super.pixelY();
			}
			float gs = Draw.guiScale();
			float guiH = Draw.windowHeight() / gs;
			float h = pixelHeight() / gs;
			float oy = offsetY.f();
			float y = switch (anchor.get()) {
				case "Top", "Top center" -> oy;
				case "Center" -> (guiH - h) / 2f + oy;
				default -> guiH - h - oy;
			};
			return Math.round(y) * gs;
		}

		@Override
		public void setPixelPosition(float x, float y) {
			if (!anchor.is("Free")) {
				float px = pixelX(), py = pixelY();
				anchor.set("Free");
				super.setPixelPosition(px, py);
			}
			super.setPixelPosition(x, y);
		}

		private String duraText(ItemStack stack) {
			int left = stack.getMaxDamage() - stack.getDamageValue();
			if (durability.is("Percentage")) {
				if (stack.getDamageValue() == 0) {
					return "";
				}
				return (int) Math.floor(left * 100.0 / stack.getMaxDamage()) + "%";
			}
			return String.valueOf(left);
		}

		@Override
		public void render(Draw d, boolean editing) {
			List<ItemStack> items = items(mc.player, editing);
			List<int[]> slots = order(items);
			int n = Math.max(1, slots.size());
			int length = SIZE + (n - 1) * STEP;
			float ex = extra();
			boolean vertical = vertical();
			float textW = 0;
			if (vertical && !durability.is("Bar")) {
				textW = d.width("100%", Fonts.Style.SEMIBOLD, 9) + 2;
			}
			float sideExtra = vertical ? Math.max(warning.on() ? 10 : 0, textW) + (warning.on() && textW > 0 ? 10 : 0) : 0;
			width = vertical ? SIZE + sideExtra : length;
			height = vertical ? length : SIZE + ex;
			if (slots.isEmpty()) {
				return;
			}
			boolean extrasLeft = vertical && fx > 0.5 && anchor.is("Free") || vertical && !anchor.is("Free") && side.is("Right");
			float bx = vertical && extrasLeft ? sideExtra : 0;
			float by = !vertical && extrasBefore() ? ex : 0;
			drawSlots(d, bx, by, slots.size(), vertical);
			for (int i = 0; i < slots.size(); i++) {
				int index = slots.get(i)[0];
				ItemStack stack = items.get(index);
				float x = bx + (vertical ? 0 : STEP * i);
				float y = by + (vertical ? STEP * i : 0);
				if (stack.isEmpty()) {
					if (icons.on()) {
						d.spriteAt(EMPTY[index], x + 3, y + 3, 16, 16, 0xFFFFFFFF);
					}
					continue;
				}
				d.item(stack, x + 3, y + 3, 16, durability.is("Bar"));
				float bobOffset = bob.f() <= 0 ? 0 : (float) Math.sin(System.currentTimeMillis() / 160.0 + i) * bob.f() / 2f;
				if (!vertical) {
					float cy = extrasBefore() ? y : y + SIZE;
					if (!durability.is("Bar") && stack.isDamageableItem()) {
						String t = duraText(stack);
						float ty = extrasBefore() ? cy - 4.5f : cy + 4.5f;
						centeredText(d, t, x + SIZE / 2f, ty, 0xFF000000 | stack.getBarColor());
						cy = extrasBefore() ? cy - 9 : cy + 9;
					}
					if (warning.on() && low(stack)) {
						float wy = extrasBefore() ? cy - 5 + bobOffset : cy + 5 + bobOffset;
						d.icon("triangle-alert", x + SIZE / 2f, wy, 8, warningColor.argb());
					}
				} else {
					float cx = extrasLeft ? x : x + SIZE;
					float mid = y + SIZE / 2f;
					if (!durability.is("Bar") && stack.isDamageableItem()) {
						String t = duraText(stack);
						float tw = d.width(t, Fonts.Style.SEMIBOLD, 9);
						float tx = extrasLeft ? cx - tw - 1 : cx + 1;
						d.textShadowed(t, tx, mid, Fonts.Style.SEMIBOLD, 9, 0xFF000000 | stack.getBarColor(), shadow.on());
						cx = extrasLeft ? cx - textW : cx + textW;
					}
					if (warning.on() && low(stack)) {
						float wx = extrasLeft ? cx - 5 : cx + 5;
						d.icon("triangle-alert", wx, mid + bobOffset, 8, warningColor.argb());
					}
				}
			}
		}

		private void centeredText(Draw d, String text, float cx, float cy, int color) {
			if (text.isEmpty()) {
				return;
			}
			float w = d.visualWidth(text, Fonts.Style.SEMIBOLD, 9);
			d.textShadowed(text, cx - w / 2f, cy, Fonts.Style.SEMIBOLD, 9, color, shadow.on());
		}

		private void drawSlots(Draw d, float x, float y, int count, boolean vertical) {
			if (style.is("None")) {
				return;
			}
			int textureWidth = SIZE + (count - 1) * STEP;
			int color = Theme.withAlpha(0xFFFFFFFF, Math.round(opacity.f() * 255));
			var pose = d.g.pose();
			pose.pushMatrix();
			pose.translate(d.originX() + x * d.s, d.originY() + y * d.s);
			pose.scale(d.s, d.s);
			if (vertical) {
				pose.rotate((float) (Math.PI / 2));
				pose.translate(0, -SIZE);
			}
			var g = d.g;
			var pipe = net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED;
			switch (style.get()) {
				case "Rounded corners" -> {
					if (count > 1) {
						g.blitSprite(pipe, HOTBAR_OFFHAND, 29, 24, 0, 1, 0, 0, 3, SIZE, color);
						g.blitSprite(pipe, HOTBAR, 182, 22, 3, 0, 3, 0, textureWidth - 6, SIZE, color);
						g.blitSprite(pipe, HOTBAR_OFFHAND, 29, 24, SIZE - 3, 1, textureWidth - 3, 0, 3, SIZE, color);
					} else {
						g.blitSprite(pipe, HOTBAR_OFFHAND, 29, 24, 0, 1, 0, 0, SIZE, SIZE, color);
					}
				}
				case "Rounded" -> {
					if (count > 1) {
						int border = (SIZE - STEP) / 2;
						g.blitSprite(pipe, HOTBAR_OFFHAND, 29, 24, 0, 1, 0, 0, SIZE - border, SIZE, color);
						for (int i = 1; i < count - 1; i++) {
							g.blitSprite(pipe, HOTBAR_OFFHAND, 29, 24, border, 1, border + i * STEP, 0, STEP, SIZE, color);
						}
						g.blitSprite(pipe, HOTBAR_OFFHAND, 29, 24, 1, 1, textureWidth - STEP - border, 0, SIZE - border, SIZE, color);
					} else {
						g.blitSprite(pipe, HOTBAR_OFFHAND, 29, 24, 0, 1, 0, 0, SIZE, SIZE, color);
					}
				}
				default -> {
					g.blitSprite(pipe, HOTBAR, 182, 22, 0, 0, 0, 0, textureWidth - 3, SIZE, color);
					g.blitSprite(pipe, HOTBAR, 182, 22, 182 - 3, 0, textureWidth - 3, 0, 3, SIZE, color);
				}
			}
			pose.popMatrix();
		}

		public void drawHands(net.minecraft.client.gui.GuiGraphicsExtractor g) {
			if (!enabled() || mc.player == null || Compat.hudHidden() || durability.is("Bar") && !warning.on()) {
				return;
			}
			Player player = mc.player;
			net.minecraft.client.gui.Font font = mc.font;
			ItemStack off = player.getOffhandItem();
			if (offHand.on() && off.isDamageableItem()) {
				int x = player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? g.guiWidth() / 2 - 91 - 29 : g.guiWidth() / 2 + 91 + 7;
				int y = g.guiHeight() - SIZE;
				if (!durability.is("Bar")) {
					String t = duraText(off);
					y -= font.lineHeight;
					g.centeredText(font, t, x + SIZE / 2, y, 0xFF000000 | off.getBarColor());
				}
				if (warning.on() && low(off)) {
					hudWarning(g, x + SIZE / 2, y - 6);
				}
			}
			ItemStack main = player.getMainHandItem();
			if (mainHand.on() && main.isDamageableItem() && !durability.is("Bar")) {
				int x = g.guiWidth() / 2 - 91 + player.getInventory().getSelectedSlot() * 20 + SIZE / 2;
				int y = g.guiHeight() - SIZE - 1;
				String t = duraText(main);
				int tx = x - font.width(t) / 2;
				g.text(font, t, tx + 1, y, 0xFF000000, false);
				g.text(font, t, tx - 1, y, 0xFF000000, false);
				g.text(font, t, tx, y + 1, 0xFF000000, false);
				g.text(font, t, tx, y - 1, 0xFF000000, false);
				g.text(font, t, tx, y, 0xFF000000 | main.getBarColor(), false);
			}
		}

		private void hudWarning(net.minecraft.client.gui.GuiGraphicsExtractor g, int cx, int cy) {
			Draw d = new Draw(g, Draw.guiScale());
			try {
				float bobOffset = bob.f() <= 0 ? 0 : (float) Math.sin(System.currentTimeMillis() / 160.0) * bob.f() / 2f;
				d.icon("triangle-alert", cx, cy + bobOffset, 8, warningColor.argb());
			} finally {
				d.end();
			}
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
			if (list.isEmpty() && editing) {
				list.add(new MobEffectInstance(net.minecraft.world.effect.MobEffects.SPEED, 1800, 1));
				list.add(new MobEffectInstance(net.minecraft.world.effect.MobEffects.STRENGTH, 900, 0));
				list.add(new MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 3600, 0));
			}
			list.sort((a, b) -> Integer.compare(b.getDuration(), a.getDuration()));
			float row = 22;
			float w = 110;
			for (MobEffectInstance e : list) {
				w = Math.max(w, 26 + d.width(name(e), Fonts.Style.SEMIBOLD, 9) + 8);
			}
			if (list.isEmpty()) {
				panel(d, w, 0.1f);
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

	public static final class ItemCooldowns extends Panel {
		private static final String BAR = "Bar";
		private final BoolSetting showTime = bool("Show time left", GENERAL, true);
		private final BoolSetting showNames = bool("Show item names", GENERAL, false);
		private final BoolSetting showBar = bool("Show progress bar", GENERAL, true);
		private final BoolSetting samples = bool("Show samples in editor", GENERAL, true);
		private final ChoiceSetting layout = choice("Layout", APPEARANCE, "Vertical", "Vertical", "Horizontal");
		private final ChoiceSetting timeFormat = choice("Time format", APPEARANCE, "One decimal", "One decimal", "Whole seconds", "Percent");
		private final ChoiceSetting sort = choice("Order", APPEARANCE, "Ending first", "Ending first", "Ending last", "Newest first");
		private final NumberSetting max = number("Items shown", APPEARANCE, 5, 1, 10, 1, NumberSetting.integer(""));
		private final NumberSetting textSize = number("Text size", APPEARANCE, 9, 5, 16, 0.5, NumberSetting.decimal(""));
		private final NumberSetting spacing = number("Spacing", APPEARANCE, 2, 0, 10, 0.5, NumberSetting.decimal(""));
		private final NumberSetting barHeight = number("Bar height", BAR, 2, 1, 6, 0.5, NumberSetting.decimal("px"));
		private final ColorSetting barColor = color("Bar color", BAR, Theme.DEFAULT_ACCENT);
		private final ColorSetting barBack = colorAlpha("Bar background", BAR, 0x40FFFFFF);
		private final BoolSetting warn = bool("Highlight the last second", BAR, true);
		private final ColorSetting warnColor = color("Last second color", BAR, 0xFFEF5B52);

		public ItemCooldowns() {
			super("Item Cooldowns", "hourglass", false, 0.5, 0.78);
			background.set(false);
			barHeight.visibleWhen(showBar::on);
			barColor.visibleWhen(showBar::on);
			barBack.visibleWhen(showBar::on);
			warnColor.visibleWhen(warn::on);
			timeFormat.visibleWhen(showTime::on);
		}

		private record Entry(ItemStack stack, float seconds, float fraction, int start) {
		}

		private List<Entry> entries(boolean editing) {
			List<Entry> list = new ArrayList<>();
			Player p = mc.player;
			if (p != null) {
				com.rustyclient.mixin.CooldownsAccessor cooldowns = (com.rustyclient.mixin.CooldownsAccessor) p.getCooldowns();
				float now = cooldowns.rusty$tickCount() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
				for (Map.Entry<Identifier, Object> e : cooldowns.rusty$cooldowns().entrySet()) {
					com.rustyclient.mixin.CooldownInstanceAccessor inst = (com.rustyclient.mixin.CooldownInstanceAccessor) e.getValue();
					float total = Math.max(1, inst.rusty$endTime() - inst.rusty$startTime());
					float left = inst.rusty$endTime() - now;
					if (left <= 0) {
						continue;
					}
					list.add(new Entry(stackFor(p, e.getKey()), left / 20f, Math.min(1f, left / total), inst.rusty$startTime()));
				}
			}
			if (list.isEmpty() && editing && samples.on()) {
				list.add(new Entry(new ItemStack(Items.ENDER_PEARL), 0.6f, 0.6f, 2));
				list.add(new Entry(new ItemStack(Items.SHIELD), 3.4f, 0.68f, 1));
				list.add(new Entry(new ItemStack(Items.CHORUS_FRUIT), 0.3f, 0.3f, 0));
			}
			switch (sort.get()) {
				case "Ending last" -> list.sort((a, b) -> Float.compare(b.seconds(), a.seconds()));
				case "Newest first" -> list.sort((a, b) -> Integer.compare(b.start(), a.start()));
				default -> list.sort((a, b) -> Float.compare(a.seconds(), b.seconds()));
			}
			return list.size() > max.i() ? list.subList(0, max.i()) : list;
		}

		private ItemStack stackFor(Player p, Identifier group) {
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
				ItemStack s = p.getInventory().getItem(i);
				if (!s.isEmpty() && group.equals(p.getCooldowns().getCooldownGroup(s))) {
					return s;
				}
			}
			return net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(group).map(ItemStack::new).orElse(new ItemStack(Items.BARRIER));
		}

		private String time(Entry e) {
			return switch (timeFormat.get()) {
				case "Whole seconds" -> (int) Math.ceil(e.seconds()) + "s";
				case "Percent" -> Math.round(e.fraction() * 100) + "%";
				default -> String.format(Locale.ROOT, "%.1fs", e.seconds());
			};
		}

		private String label(Entry e) {
			String time = showTime.on() ? time(e) : "";
			if (!showNames.on()) {
				return time;
			}
			String name = e.stack().getHoverName().getString();
			return time.isEmpty() ? name : name + "  " + time;
		}

		@Override
		public boolean visibleNow() {
			return !entries(false).isEmpty();
		}

		@Override
		public void render(Draw d, boolean editing) {
			List<Entry> list = entries(editing);
			float size = textSize.f();
			float bar = showBar.on() ? barHeight.f() : 0;
			float gap = spacing.f();
			int text = textColor.argb();
			if (layout.is("Horizontal")) {
				float cellH = 18 + (showTime.on() || showNames.on() ? size + 2 : 0) + (bar > 0 ? bar + 2 : 0);
				float x = 2;
				List<Float> widths = new ArrayList<>();
				for (Entry e : list) {
					widths.add(Math.max(20, d.width(label(e), Fonts.Style.SEMIBOLD, size) + 4));
				}
				float total = 2;
				for (float w : widths) {
					total += w + gap;
				}
				panel(d, Math.max(24, total - gap + 2), Math.max(20, cellH + 2));
				for (int i = 0; i < list.size(); i++) {
					Entry e = list.get(i);
					float w = widths.get(i);
					d.item(e.stack(), x + (w - 16) / 2f, 2, 16, false);
					float y = 19;
					String label = label(e);
					if (!label.isEmpty()) {
						d.textShadowed(label, x + (w - d.width(label, Fonts.Style.SEMIBOLD, size)) / 2f, y + size / 2f + 1, Fonts.Style.SEMIBOLD, size, warning(e) ? warnColor.argb() : text, shadow.on());
						y += size + 2;
					}
					if (bar > 0) {
						bar(d, e, x + 2, y + 1, w - 4, bar);
					}
					x += w + gap;
				}
				return;
			}
			float rowH = Math.max(16, (showTime.on() || showNames.on() ? size + 2 : 0) + (bar > 0 ? bar + 3 : 0));
			float textW = 0;
			for (Entry e : list) {
				textW = Math.max(textW, d.width(label(e), Fonts.Style.SEMIBOLD, size));
			}
			float w = 22 + Math.max(bar > 0 ? 36 : 0, textW) + 4;
			float h = list.isEmpty() ? 18 : list.size() * rowH + (list.size() - 1) * gap + 2;
			panel(d, Math.max(40, w), h);
			float y = 1;
			for (Entry e : list) {
				d.item(e.stack(), 2, y + (rowH - 16) / 2f, 16, false);
				String label = label(e);
				float ty = y + (bar > 0 && !label.isEmpty() ? (rowH - bar - 3) / 2f : rowH / 2f);
				if (!label.isEmpty()) {
					d.textShadowed(label, 22, ty, Fonts.Style.SEMIBOLD, size, warning(e) ? warnColor.argb() : text, shadow.on());
				}
				if (bar > 0) {
					float by = label.isEmpty() ? y + (rowH - bar) / 2f : y + rowH - bar - 1;
					bar(d, e, 22, by, Math.max(36, w - 26), bar);
				}
				y += rowH + gap;
			}
		}

		private boolean warning(Entry e) {
			return warn.on() && e.seconds() <= 1f;
		}

		private void bar(Draw d, Entry e, float x, float y, float w, float h) {
			d.round(x, y, w, h, h / 2f, barBack.argb());
			int color = warning(e) ? warnColor.argb() : Theme.hudAccent(barColor.argb());
			d.round(x, y, Math.max(h, w * e.fraction()), h, h / 2f, color);
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
