package com.rustyclient.module.utility;

import com.rustyclient.core.Category;
import com.rustyclient.core.Module;
import com.rustyclient.core.Modules;
import com.rustyclient.core.Theme;
import com.rustyclient.core.setting.BoolSetting;
import com.rustyclient.core.setting.ChoiceSetting;
import com.rustyclient.core.setting.ColorSetting;
import com.rustyclient.core.setting.NumberSetting;
import com.rustyclient.platform.Compat;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ItemPreviews extends Module {
	private static final String CONTAINERS = "Containers";
	private static final String MAPS = "Maps";
	private static final int SLOTS = 27;
	private static final int SLOT = 18;

	private final BoolSetting shulkers = bool("Shulker box preview", GENERAL, true);
	private final BoolSetting containers = bool("Other containers", GENERAL, true);
	private final BoolSetting maps = bool("Map preview", GENERAL, true);
	private final BoolSetting shiftOnly = bool("Only while Shift is held", GENERAL, false);
	private final BoolSetting compact = bool("Compact (stack same items)", CONTAINERS, false);
	private final BoolSetting hideEmpty = bool("Hide empty containers", CONTAINERS, true);
	private final BoolSetting tint = bool("Shulker color background", CONTAINERS, true);
	private final ChoiceSetting slots = choice("Slot style", CONTAINERS, "Boxes", "Boxes", "Plain");
	private final ColorSetting background = colorAlpha("Background color", CONTAINERS, 0xC0100F0E);
	private final ColorSetting slotColor = colorAlpha("Slot color", CONTAINERS, 0x50FFFFFF);
	private final NumberSetting mapSize = number("Map size", MAPS, 96, 48, 160, 8, NumberSetting.integer("px"));
	private final BoolSetting mapFrame = bool("Map frame", MAPS, true);

	public ItemPreviews() {
		super("Item Previews", "scan-eye", Category.UTILITY, false);
		compact.visibleWhen(() -> shulkers.on() || containers.on());
		hideEmpty.visibleWhen(() -> shulkers.on() || containers.on());
		tint.visibleWhen(shulkers::on);
		slots.visibleWhen(() -> (shulkers.on() || containers.on()) && !compact.on());
		mapFrame.visibleWhen(maps::on);
		mapSize.visibleWhen(maps::on);
	}

	public static void register() {
		ClientTooltipComponentCallback.EVENT.register(data -> {
			ItemPreviews module = Modules.get(ItemPreviews.class);
			if (module == null) {
				return null;
			}
			if (data instanceof ContainerData container) {
				return module.new ContainerTooltip(container);
			}
			if (data instanceof MapData map) {
				return module.new MapTooltip(map);
			}
			return null;
		});
	}

	public record ContainerData(List<ItemStack> items, int columns, int rows, int color, boolean compact) implements TooltipComponent {
	}

	public record MapData(MapId id) implements TooltipComponent {
	}

	public static Optional<TooltipComponent> image(ItemStack stack) {
		ItemPreviews module = Modules.get(ItemPreviews.class);
		if (module == null || !module.enabled() || stack.isEmpty()) {
			return Optional.empty();
		}
		if (module.shiftOnly.on() && !Compat.isKeyDown("key.keyboard.left.shift") && !Compat.isKeyDown("key.keyboard.right.shift")) {
			return Optional.empty();
		}
		MapId id = stack.get(DataComponents.MAP_ID);
		if (id != null) {
			return module.maps.on() ? Optional.of(new MapData(id)) : Optional.empty();
		}
		ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
		if (contents == null) {
			return Optional.empty();
		}
		boolean shulker = stack.getItem() instanceof BlockItem block && block.getBlock() instanceof ShulkerBoxBlock;
		if (shulker ? !module.shulkers.on() : !module.containers.on()) {
			return Optional.empty();
		}
		NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
		contents.copyInto(items);
		int last = -1;
		for (int i = 0; i < items.size(); i++) {
			if (!items.get(i).isEmpty()) {
				last = i;
			}
		}
		if (last < 0 && module.hideEmpty.on()) {
			return Optional.empty();
		}
		int color = 0;
		if (shulker && module.tint.on()) {
			DyeColor dye = ((ShulkerBoxBlock) ((BlockItem) stack.getItem()).getBlock()).getColor();
			color = dye == null ? 0xFF8B5F8F : (0xFF000000 | dye.getTextureDiffuseColor());
		}
		if (module.compact.on()) {
			List<ItemStack> merged = new ArrayList<>();
			for (ItemStack item : items) {
				if (item.isEmpty()) {
					continue;
				}
				boolean added = false;
				for (ItemStack other : merged) {
					if (ItemStack.isSameItemSameComponents(other, item)) {
						other.grow(item.getCount());
						added = true;
						break;
					}
				}
				if (!added) {
					merged.add(item.copy());
				}
			}
			merged.sort((a, b) -> Integer.compare(b.getCount(), a.getCount()));
			int columns = Math.max(1, Math.min(9, merged.size()));
			int rows = Math.max(1, (merged.size() + 8) / 9);
			return Optional.of(new ContainerData(merged, columns, rows, color, true));
		}
		int rows = shulker ? 3 : Math.max(1, (last + 9) / 9);
		return Optional.of(new ContainerData(new ArrayList<>(items.subList(0, rows * 9)), 9, rows, color, false));
	}

	private static int blend(int base, int tint) {
		if (tint == 0) {
			return base;
		}
		int a = base >>> 24;
		return Theme.withAlpha(Theme.mix(base | 0xFF000000, tint, 0.35f), a);
	}

	final class ContainerTooltip implements ClientTooltipComponent {
		private final ContainerData data;

		ContainerTooltip(ContainerData data) {
			this.data = data;
		}

		@Override
		public int getHeight(Font font) {
			return data.rows() * SLOT + 6;
		}

		@Override
		public int getWidth(Font font) {
			return data.columns() * SLOT + 4;
		}

		@Override
		public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor g) {
			int width = data.columns() * SLOT + 4;
			int height = data.rows() * SLOT + 4;
			g.fill(x, y, x + width, y + height, blend(background.argb(), data.color()));
			boolean boxes = !data.compact() && slots.is("Boxes");
			for (int i = 0; i < data.items().size(); i++) {
				int sx = x + 2 + (i % data.columns()) * SLOT;
				int sy = y + 2 + (i / data.columns()) * SLOT;
				if (boxes) {
					g.fill(sx + 1, sy + 1, sx + SLOT - 1, sy + SLOT - 1, slotColor.argb());
				}
				ItemStack stack = data.items().get(i);
				if (stack.isEmpty()) {
					continue;
				}
				g.item(stack, sx + 1, sy + 1);
				if (data.compact() && stack.getCount() > stack.getMaxStackSize()) {
					g.itemDecorations(font, stack, sx + 1, sy + 1, compactCount(stack.getCount()));
				} else {
					g.itemDecorations(font, stack, sx + 1, sy + 1);
				}
			}
		}

		private String compactCount(int count) {
			if (count >= 10000) {
				return (count / 1000) + "k";
			}
			return String.valueOf(count);
		}
	}

	final class MapTooltip implements ClientTooltipComponent {
		private final MapData data;
		private final MapRenderState state = new MapRenderState();

		MapTooltip(MapData data) {
			this.data = data;
		}

		private int size() {
			return mapSize.i();
		}

		@Override
		public int getHeight(Font font) {
			return size() + 6;
		}

		@Override
		public int getWidth(Font font) {
			return size() + 4;
		}

		@Override
		public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor g) {
			Minecraft mc = Minecraft.getInstance();
			int size = size();
			if (mapFrame.on()) {
				g.fill(x, y, x + size + 4, y + size + 4, 0xFF5B4632);
				g.fill(x + 1, y + 1, x + size + 3, y + size + 3, 0xFFD7C69E);
			}
			MapItemSavedData saved = mc.level == null ? null : MapItem.getSavedData(data.id(), mc.level);
			if (saved == null) {
				String text = "Map not loaded yet";
				g.text(font, text, x + 2 + (size - font.width(text)) / 2, y + 2 + size / 2 - 4, 0xFF5B4632, false);
				return;
			}
			g.pose().pushMatrix();
			g.pose().translate(x + 2, y + 2);
			g.pose().scale(size / 128f, size / 128f);
			mc.getMapRenderer().extractRenderState(data.id(), saved, state);
			g.map(state);
			g.pose().popMatrix();
		}
	}
}
