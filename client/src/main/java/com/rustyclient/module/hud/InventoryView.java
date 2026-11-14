package com.rustyclient.module.hud;

import com.rustyclient.core.Category;
import com.rustyclient.core.HudModule;
import com.rustyclient.core.Theme;
import com.rustyclient.core.setting.BoolSetting;
import com.rustyclient.core.setting.ChoiceSetting;
import com.rustyclient.core.setting.ColorSetting;
import com.rustyclient.core.setting.NumberSetting;
import com.rustyclient.render.Draw;
import com.rustyclient.render.Fonts;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class InventoryView extends HudModule {
	private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/inventory.png");
	private static final String SLOTS = "Slots";
	private static final String COUNT = "Item count";
	private static final int COLS = 9;
	private static final int ROWS = 3;

	private final BoolSetting showCount = bool("Show item count", GENERAL, true);
	private final BoolSetting showDurability = bool("Show durability bars", GENERAL, true);
	private final BoolSetting showHotbar = bool("Show hotbar row", GENERAL, false);
	private final BoolSetting hideEmpty = bool("Hide when inventory is empty", GENERAL, false);
	private final BoolSetting samples = bool("Show samples in editor", GENERAL, true);
	private final ChoiceSetting style = choice("Style", APPEARANCE, "Inventory texture", "Inventory texture", "Slots only", "Rusty", "Items only");
	private final NumberSetting opacity = number("Opacity", APPEARANCE, 1, 0.1, 1, 0.05, NumberSetting.percent());
	private final ColorSetting backgroundColor = colorAlpha("Background color", APPEARANCE, 0x8C0F0E0D);
	private final ColorSetting slotColor = colorAlpha("Slot color", SLOTS, 0x50FFFFFF);
	private final ColorSetting filledColor = colorAlpha("Filled slot color", SLOTS, 0x50FFFFFF);
	private final BoolSetting emptySlots = bool("Show empty slots", SLOTS, true);
	private final NumberSetting slotSpacing = number("Slot spacing", SLOTS, 2, 0, 8, 0.5, NumberSetting.decimal(""));
	private final NumberSetting slotRadius = number("Slot border radius", SLOTS, 2, 0, 8, 0.5, NumberSetting.decimal("px"));
	private final NumberSetting padding = number("Padding", SLOTS, 3, 0, 10, 0.5, NumberSetting.decimal(""));
	private final ColorSetting countColor = color("Count color", COUNT, 0xFFFFFFFF);
	private final BoolSetting countShadow = bool("Count shadow", COUNT, true);
	private final BoolSetting hideSingle = bool("Hide count of 1", COUNT, true);

	public InventoryView() {
		super("Inventory View", "backpack", Category.HUD, false, 0.99, 0.5);
		backgroundColor.visibleWhen(this::rusty);
		slotColor.visibleWhen(this::custom);
		filledColor.visibleWhen(this::custom);
		emptySlots.visibleWhen(this::custom);
		slotSpacing.visibleWhen(this::custom);
		slotRadius.visibleWhen(this::custom);
		padding.visibleWhen(this::rusty);
		opacity.visibleWhen(() -> !custom());
		countColor.visibleWhen(showCount::on);
		countShadow.visibleWhen(showCount::on);
		hideSingle.visibleWhen(showCount::on);
	}

	private boolean rusty() {
		return style.is("Rusty");
	}

	private boolean custom() {
		return style.is("Rusty") || style.is("Items only");
	}

	@Override
	protected boolean usesRadius() {
		return rusty();
	}

	private ItemStack[] stacks(boolean editing) {
		int rows = showHotbar.on() ? ROWS + 1 : ROWS;
		ItemStack[] out = new ItemStack[rows * COLS];
		Player p = mc.player;
		boolean any = false;
		for (int i = 0; i < out.length; i++) {
			ItemStack s = ItemStack.EMPTY;
			if (p != null) {
				int slot = i < ROWS * COLS ? i + COLS : i - ROWS * COLS;
				s = p.getInventory().getItem(slot);
			}
			out[i] = s;
			any |= !s.isEmpty();
		}
		if (!any && editing && samples.on()) {
			out[0] = new ItemStack(Items.ENDER_PEARL, 16);
			out[1] = new ItemStack(Items.GOLDEN_APPLE, 8);
			out[2] = new ItemStack(Items.COBBLESTONE, 64);
			out[4] = new ItemStack(Items.DIAMOND_PICKAXE);
			out[4].setDamageValue(900);
			out[9] = new ItemStack(Items.ARROW, 37);
			out[13] = new ItemStack(Items.COOKED_BEEF, 23);
			out[17] = new ItemStack(Items.WATER_BUCKET);
			out[20] = new ItemStack(Items.OAK_LOG, 42);
			out[26] = new ItemStack(Items.TOTEM_OF_UNDYING);
		}
		return out;
	}

	@Override
	public boolean visibleNow() {
		if (!hideEmpty.on()) {
			return true;
		}
		for (ItemStack s : stacks(false)) {
			if (!s.isEmpty()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void render(Draw d, boolean editing) {
		ItemStack[] items = stacks(editing);
		int rows = items.length / COLS;
		if (custom()) {
			renderCustom(d, items, rows);
		} else {
			renderTexture(d, items, rows, style.is("Inventory texture"));
		}
	}

	private void renderTexture(Draw d, ItemStack[] items, int rows, boolean panel) {
		int tint = Theme.withAlpha(0xFFFFFFFF, Math.round(opacity.f() * 255));
		float ox = panel ? 7 : 0;
		float oy = panel ? 7 : 0;
		float gridH = ROWS * 18 + (rows > ROWS ? 22 : 0);
		if (panel) {
			width = 176;
			height = 7 + gridH + 7;
			d.textureRegion(TEXTURE, 256, 256, 0, 0, 0, 0, 176, 7, tint);
			d.textureRegion(TEXTURE, 256, 256, 0, 83, 0, 7, 176, ROWS * 18, tint);
			if (rows > ROWS) {
				d.textureRegion(TEXTURE, 256, 256, 0, 137, 0, 7 + ROWS * 18, 176, 22, tint);
			}
			d.textureRegion(TEXTURE, 256, 256, 0, 159, 0, height - 7, 176, 7, tint);
		} else {
			width = COLS * 18;
			height = gridH;
			d.textureRegion(TEXTURE, 256, 256, 7, 83, 0, 0, COLS * 18, ROWS * 18, tint);
			if (rows > ROWS) {
				d.textureRegion(TEXTURE, 256, 256, 7, 141, 0, ROWS * 18 + 4, COLS * 18, 18, tint);
			}
		}
		for (int i = 0; i < items.length; i++) {
			int col = i % COLS, row = i / COLS;
			float x = ox + col * 18 + 1;
			float y = oy + row * 18 + 1 + (row >= ROWS ? 4 : 0);
			drawStack(d, items[i], x, y);
		}
	}

	private void renderCustom(Draw d, ItemStack[] items, int rows) {
		boolean back = rusty();
		float pad = back ? padding.f() : 0;
		float gap = slotSpacing.f();
		float cell = 18;
		float hotbarGap = rows > ROWS ? gap + 3 : 0;
		width = pad * 2 + COLS * cell + (COLS - 1) * gap;
		height = pad * 2 + rows * cell + (rows - 1) * gap + hotbarGap;
		if (back) {
			d.round(0, 0, width, height, radius(), backgroundColor.argb());
		}
		float r = Math.min(slotRadius.f(), cell / 2f);
		for (int i = 0; i < items.length; i++) {
			int col = i % COLS, row = i / COLS;
			float x = pad + col * (cell + gap);
			float y = pad + row * (cell + gap) + (row >= ROWS ? hotbarGap : 0);
			ItemStack s = items[i];
			if (emptySlots.on() || !s.isEmpty()) {
				int c = s.isEmpty() ? slotColor.argb() : filledColor.argb();
				if ((c >>> 24) != 0) {
					d.round(x, y, cell, cell, r, c);
				}
			}
			drawStack(d, s, x + 1, y + 1);
		}
	}

	private void drawStack(Draw d, ItemStack s, float x, float y) {
		if (s == null || s.isEmpty()) {
			return;
		}
		d.item(s, x, y, 16, false);
		if (showDurability.on() && s.isBarVisible()) {
			float bx = x + 2, by = y + 13;
			d.rect(bx, by, 13, 2, 0xFF000000);
			d.rect(bx, by, s.getBarWidth(), 1, s.getBarColor() | 0xFF000000);
		}
		if (showCount.on() && (s.getCount() > 1 || !hideSingle.on())) {
			String text = String.valueOf(s.getCount());
			float w = d.width(text, Fonts.Style.SEMIBOLD, 9);
			d.textShadowed(text, x + 17 - w, y + 12.5f, Fonts.Style.SEMIBOLD, 9, countColor.argb(), countShadow.on());
		}
	}
}
