package com.rustyclient.module.hud;

import com.rustyclient.core.Category;
import com.rustyclient.core.Module;
import com.rustyclient.core.setting.BoolSetting;
import com.rustyclient.core.setting.ColorSetting;
import com.rustyclient.render.Draw;
import com.rustyclient.render.Tex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class Saturation extends Module {
	private static final Identifier FULL = Identifier.withDefaultNamespace("textures/gui/sprites/hud/food_full.png");
	private static final Identifier EMPTY = Identifier.withDefaultNamespace("textures/gui/sprites/hud/food_empty.png");

	private final BoolSetting overlay = bool("Outline hunger icons", GENERAL, true);
	private final BoolSetting tooltip = bool("Food values in tooltip", GENERAL, true);
	private final ColorSetting color = colorAlpha("Outline color", APPEARANCE, 0xFFFFD43B);
	private Tex outline;
	private String outlineKey = "";
	private int texel;
	private long checked;

	public Saturation() {
		super("Saturation", "drumstick", Category.HUD, false);
	}

	public void draw(GuiGraphicsExtractor g) {
		if (!enabled() || !overlay.on()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null || com.rustyclient.platform.Compat.hudHidden() || mc.gameMode == null || !mc.gameMode.canHurtPlayer()) {
			return;
		}
		if (player.getVehicle() instanceof LivingEntity) {
			return;
		}
		float saturation = player.getFoodData().getSaturationLevel();
		if (saturation <= 0f) {
			return;
		}
		int gs = Math.round(Draw.guiScale());
		Tex tex = texture(mc, gs);
		if (tex == null) {
			return;
		}
		int right = mc.getWindow().getGuiScaledWidth() / 2 + 91;
		int top = mc.getWindow().getGuiScaledHeight() - 39;
		Draw d = new Draw(g, 1f);
		try {
			for (int i = 0; i < 10; i++) {
				float part = saturation - i * 2;
				if (part <= 0f) {
					break;
				}
				int x = (right - i * 8 - 9) * gs - texel;
				int y = top * gs - texel;
				int w = part >= 2f ? tex.width : tex.width / 2;
				d.texture(tex, x, y, w, tex.height, color.argb());
			}
		} finally {
			d.end();
		}
	}

	private Tex texture(Minecraft mc, int gs) {
		long now = System.currentTimeMillis();
		if (outline != null && now - checked < 2000 && outlineKey.endsWith("|" + gs)) {
			return outline;
		}
		checked = now;
		try {
			Optional<Resource> full = mc.getResourceManager().getResource(FULL);
			if (full.isEmpty()) {
				return outline;
			}
			Optional<Resource> empty = mc.getResourceManager().getResource(EMPTY);
			String key = full.get().sourcePackId() + "|" + empty.map(Resource::sourcePackId).orElse("") + "|" + gs;
			if (outline != null && key.equals(outlineKey)) {
				return outline;
			}
			BufferedImage a = read(full.get());
			BufferedImage b = empty.isPresent() ? read(empty.get()) : null;
			int w = a.getWidth();
			int h = Math.min(a.getHeight(), w);
			boolean[] solid = new boolean[w * h];
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					boolean on = (a.getRGB(x, y) >>> 24) > 16;
					if (!on && b != null && x < b.getWidth() && y < b.getHeight()) {
						on = (b.getRGB(x, y) >>> 24) > 16;
					}
					solid[y * w + x] = on;
				}
			}
			int scale = Math.max(1, Math.round(gs * 9f / w));
			int tw = (w + 2) * scale, th = (h + 2) * scale;
			float[] coverage = new float[tw * th];
			for (int y = 0; y < h + 2; y++) {
				for (int x = 0; x < w + 2; x++) {
					int sx = x - 1, sy = y - 1;
					boolean inside = sx >= 0 && sy >= 0 && sx < w && sy < h && solid[sy * w + sx];
					if (inside || !near(solid, w, h, sx, sy)) {
						continue;
					}
					for (int py = 0; py < scale; py++) {
						for (int px = 0; px < scale; px++) {
							coverage[(y * scale + py) * tw + x * scale + px] = 1f;
						}
					}
				}
			}
			if (outline != null) {
				outline.close();
			}
			outline = Tex.alpha("saturation", tw, th, coverage);
			texel = scale;
			outlineKey = key;
		} catch (Exception ignored) {
		}
		return outline;
	}

	private static boolean near(boolean[] solid, int w, int h, int x, int y) {
		for (int dy = -1; dy <= 1; dy++) {
			for (int dx = -1; dx <= 1; dx++) {
				int nx = x + dx, ny = y + dy;
				if (nx >= 0 && ny >= 0 && nx < w && ny < h && solid[ny * w + nx]) {
					return true;
				}
			}
		}
		return false;
	}

	private static BufferedImage read(Resource resource) throws Exception {
		try (InputStream in = resource.open()) {
			return ImageIO.read(in);
		}
	}

	public void tooltip(ItemStack stack, List<Component> lines) {
		if (!enabled() || !tooltip.on() || stack.isEmpty()) {
			return;
		}
		FoodProperties food = stack.get(DataComponents.FOOD);
		if (food == null) {
			return;
		}
		Component line = Component.literal("+" + number(food.nutrition() / 2f) + " hunger bars")
			.withStyle(style -> style.withColor(TextColor.fromRgb(0xE8A33D)))
			.append(Component.literal("  +" + number(food.saturation()) + " saturation").withStyle(style -> style.withColor(TextColor.fromRgb(0xFFD43B))));
		lines.add(Math.min(1, lines.size()), line);
	}

	private static String number(float value) {
		if (Math.abs(value - Math.round(value)) < 0.05f) {
			return String.valueOf(Math.round(value));
		}
		return String.format(Locale.ROOT, "%.1f", value);
	}
}
