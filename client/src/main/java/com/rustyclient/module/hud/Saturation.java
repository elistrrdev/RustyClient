package com.rustyclient.module.hud;

import com.rustyclient.core.Category;
import com.rustyclient.core.Module;
import com.rustyclient.core.Modules;
import com.rustyclient.core.setting.BoolSetting;
import com.rustyclient.core.setting.ChoiceSetting;
import com.rustyclient.core.setting.ColorSetting;
import com.rustyclient.core.setting.NumberSetting;
import com.rustyclient.mixin.FoodDataAccessor;
import com.rustyclient.platform.Compat;
import com.rustyclient.render.Draw;
import com.rustyclient.render.Tex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class Saturation extends Module {
	public static final Identifier DEBUG_ENTRY = Identifier.fromNamespaceAndPath("rustyclient", "food_stats");
	private static final String TOOLTIP = "Tooltip";
	private static final String PREVIEW = "Preview";
	private static final Identifier FULL = Identifier.withDefaultNamespace("textures/gui/sprites/hud/food_full.png");
	private static final Identifier EMPTY = Identifier.withDefaultNamespace("textures/gui/sprites/hud/food_empty.png");
	private static final Identifier FOOD_FULL = Identifier.withDefaultNamespace("hud/food_full");
	private static final Identifier FOOD_HALF = Identifier.withDefaultNamespace("hud/food_half");
	private static final Identifier FOOD_FULL_HUNGER = Identifier.withDefaultNamespace("hud/food_full_hunger");
	private static final Identifier FOOD_HALF_HUNGER = Identifier.withDefaultNamespace("hud/food_half_hunger");
	private static final Identifier HEART_FULL = Identifier.withDefaultNamespace("hud/heart/full");
	private static final Identifier HEART_HALF = Identifier.withDefaultNamespace("hud/heart/half");
	private static final Identifier HEART_HARDCORE_FULL = Identifier.withDefaultNamespace("hud/heart/hardcore_full");
	private static final Identifier HEART_HARDCORE_HALF = Identifier.withDefaultNamespace("hud/heart/hardcore_half");

	private final BoolSetting saturationOverlay = bool("Saturation overlay", GENERAL, true);
	private final BoolSetting exhaustionOverlay = bool("Exhaustion bar", GENERAL, false);
	private final BoolSetting hungerPreview = bool("Hunger restore preview", GENERAL, true);
	private final BoolSetting saturationPreview = bool("Saturation restore preview", GENERAL, true);
	private final BoolSetting healthPreview = bool("Health restore preview", GENERAL, true);
	private final BoolSetting tooltip = bool("Food values in tooltip", GENERAL, true);
	private final BoolSetting debug = bool("Hunger info in debug screen", GENERAL, true);
	private final BoolSetting estimate = bool("Estimate exhaustion on servers", GENERAL, true);

	private final ChoiceSetting tooltipStyle = choice("Tooltip style", TOOLTIP, "Icons", "Icons", "Text");
	private final BoolSetting tooltipSaturation = bool("Show saturation in tooltip", TOOLTIP, true);
	private final BoolSetting tooltipShift = bool("Only while Shift is held", TOOLTIP, false);
	private final ChoiceSetting hungerUnit = choice("Hunger shown as", TOOLTIP, "Bars", "Bars", "Points");

	private final BoolSetting offhand = bool("Preview offhand food", PREVIEW, true);
	private final BoolSetting flash = bool("Flash previews", PREVIEW, true);
	private final NumberSetting previewAlpha = number("Preview opacity", PREVIEW, 0.7, 0.2, 1, 0.05, NumberSetting.percent());

	private final ChoiceSetting style = choice("Saturation style", APPEARANCE, "Inner outline", "Inner outline", "Outer outline", "Tint");
	private final ColorSetting color = colorAlpha("Saturation color", APPEARANCE, 0xFFFFD43B);
	private final ChoiceSetting exhaustionStyle = choice("Exhaustion bar style", APPEARANCE, "Thin line", "Thin line", "Behind icons");
	private final ColorSetting exhaustionColor = colorAlpha("Exhaustion bar color", APPEARANCE, 0xB0FFFFFF);
	private final ColorSetting tooltipHunger = color("Tooltip hunger color", APPEARANCE, 0xFFE8A33D);
	private final ColorSetting tooltipSat = color("Tooltip saturation color", APPEARANCE, 0xFFFFD43B);

	private Tex overlay;
	private String overlayKey = "";
	private int overlayW;
	private int overlayH;
	private int margin;
	private long checked;

	private float estimated;
	private int lastFood = -1;
	private float lastSaturation = -1;
	private float lastHealth = -1;
	private double lastX;
	private double lastY;
	private double lastZ;
	private boolean wasOnGround = true;
	private Player tracked;
	private boolean debugPushed;

	public Saturation() {
		super("Saturation", "drumstick", Category.HUD, false);
		tooltipStyle.visibleWhen(tooltip::on);
		tooltipSaturation.visibleWhen(tooltip::on);
		tooltipShift.visibleWhen(tooltip::on);
		hungerUnit.visibleWhen(tooltip::on);
		tooltipHunger.visibleWhen(tooltip::on);
		tooltipSat.visibleWhen(tooltip::on);
		exhaustionColor.visibleWhen(exhaustionOverlay::on);
		exhaustionStyle.visibleWhen(exhaustionOverlay::on);
		offhand.visibleWhen(this::previews);
		flash.visibleWhen(this::previews);
		previewAlpha.visibleWhen(this::previews);
		debug.onChange(() -> debugPushed = false);
	}

	private boolean previews() {
		return hungerPreview.on() || saturationPreview.on() || healthPreview.on();
	}

	public static Saturation active() {
		Saturation s = Modules.get(Saturation.class);
		return s != null && s.enabled() ? s : null;
	}

	@Override
	protected void onEnable() {
		debugPushed = false;
	}

	@Override
	public void alwaysTick() {
		if (!enabled() || !debug.on() || debugPushed) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.debugEntries == null) {
			return;
		}
		debugPushed = true;
		if (mc.debugEntries.getStatus(DEBUG_ENTRY) == DebugScreenEntryStatus.NEVER) {
			mc.debugEntries.setStatus(DEBUG_ENTRY, DebugScreenEntryStatus.IN_OVERLAY);
		}
	}

	@Override
	public void tick() {
		Player p = mc.player;
		if (p == null) {
			return;
		}
		FoodData food = p.getFoodData();
		if (p != tracked) {
			tracked = p;
			estimated = 0;
			lastFood = food.getFoodLevel();
			lastSaturation = food.getSaturationLevel();
			lastHealth = p.getHealth();
			lastX = p.getX();
			lastY = p.getY();
			lastZ = p.getZ();
			wasOnGround = p.onGround();
			return;
		}
		double dx = p.getX() - lastX, dy = p.getY() - lastY, dz = p.getZ() - lastZ;
		lastX = p.getX();
		lastY = p.getY();
		lastZ = p.getZ();
		if (p.getAbilities().invulnerable || p.isPassenger()) {
			wasOnGround = p.onGround();
			return;
		}
		double flat = Math.sqrt(dx * dx + dz * dz);
		if (flat < 2) {
			if (p.isSwimming() || p.isUnderWater()) {
				estimated += (float) (0.01 * Math.sqrt(dx * dx + dy * dy + dz * dz));
			} else if (p.isInWater()) {
				estimated += (float) (0.01 * flat);
			} else if (p.onGround() && p.isSprinting()) {
				estimated += (float) (0.1 * flat);
			}
		}
		if (wasOnGround && !p.onGround() && dy > 0 && mc.options.keyJump.isDown()) {
			estimated += p.isSprinting() ? 0.2f : 0.05f;
		}
		wasOnGround = p.onGround();
		MobEffectInstance hunger = p.getEffect(MobEffects.HUNGER);
		if (hunger != null) {
			estimated += 0.005f * (hunger.getAmplifier() + 1);
		}
		float health = p.getHealth();
		if (health > lastHealth && !p.hasEffect(MobEffects.REGENERATION) && lastFood >= 18) {
			estimated += (health - lastHealth) * 6f;
		} else if (health < lastHealth) {
			estimated += 0.1f;
		}
		lastHealth = health;
		int f = food.getFoodLevel();
		float s = food.getSaturationLevel();
		if (f < lastFood || s < lastSaturation - 0.01f) {
			estimated = estimated >= 4f ? estimated - 4f : 0f;
		}
		lastFood = f;
		lastSaturation = s;
		estimated = Math.max(0f, Math.min(4f, estimated));
	}

	public void onAttack() {
		if (enabled()) {
			estimated = Math.min(4f, estimated + 0.1f);
		}
	}

	private ServerPlayer serverPlayer(Player p) {
		IntegratedServer server = mc.getSingleplayerServer();
		if (server == null) {
			return null;
		}
		try {
			return server.getPlayerList().getPlayer(p.getUUID());
		} catch (Exception e) {
			return null;
		}
	}

	public boolean exactValues() {
		return mc.player != null && serverPlayer(mc.player) != null;
	}

	public float exhaustion() {
		Player p = mc.player;
		if (p == null) {
			return -1;
		}
		ServerPlayer sp = serverPlayer(p);
		if (sp != null) {
			return ((FoodDataAccessor) sp.getFoodData()).rusty$exhaustion();
		}
		return estimate.on() ? estimated : -1;
	}

	public float saturation() {
		Player p = mc.player;
		if (p == null) {
			return 0;
		}
		ServerPlayer sp = serverPlayer(p);
		return sp != null ? sp.getFoodData().getSaturationLevel() : p.getFoodData().getSaturationLevel();
	}

	public void debugLines(DebugScreenDisplayer out) {
		if (!enabled() || !debug.on() || mc.player == null) {
			return;
		}
		FoodData food = mc.player.getFoodData();
		float exhaustion = exhaustion();
		String exh = exhaustion < 0 ? "unknown" : String.format(Locale.ROOT, "%.2f / 4.00", exhaustion) + (exactValues() ? "" : " (estimated)");
		out.addLine(String.format(Locale.ROOT, "Hunger: %d / 20  Saturation: %.2f  Exhaustion: %s", food.getFoodLevel(), saturation(), exh));
	}

	private boolean showsFoodBar(Player player) {
		return player != null && !Compat.hudHidden() && mc.gameMode != null && mc.gameMode.canHurtPlayer() && !(player.getVehicle() instanceof LivingEntity);
	}

	private ItemStack heldFood(Player p) {
		ItemStack main = p.getMainHandItem();
		if (main.get(DataComponents.FOOD) != null) {
			return main;
		}
		ItemStack off = p.getOffhandItem();
		if (offhand.on() && off.get(DataComponents.FOOD) != null) {
			return off;
		}
		return ItemStack.EMPTY;
	}

	private float flashAlpha() {
		float base = previewAlpha.f();
		if (!flash.on()) {
			return base;
		}
		double wave = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 180.0);
		return base * (float) (0.25 + 0.75 * wave);
	}

	private static int alpha(int argb, float a) {
		int value = Math.round((argb >>> 24) * Math.max(0f, Math.min(1f, a)));
		return (value << 24) | (argb & 0xFFFFFF);
	}

	public void drawUnderlay(GuiGraphicsExtractor g) {
		if (exhaustionStyle.is("Behind icons")) {
			exhaustionBar(g);
		}
	}

	private void exhaustionBar(GuiGraphicsExtractor g) {
		if (!enabled() || !exhaustionOverlay.on()) {
			return;
		}
		Player player = mc.player;
		if (!showsFoodBar(player)) {
			return;
		}
		float exhaustion = exhaustion();
		if (exhaustion <= 0f) {
			return;
		}
		float gs = Draw.guiScale();
		int right = g.guiWidth() / 2 + 91;
		int top = g.guiHeight() - 39;
		float w = Math.min(1f, exhaustion / 4f) * 81f * gs;
		Draw d = new Draw(g, 1f);
		try {
			if (exhaustionStyle.is("Behind icons")) {
				d.rect(right * gs - w, top * gs, w, 9 * gs, exhaustionColor.argb());
			} else {
				float line = Math.max(1f, Math.round(gs / 2f));
				d.rect(right * gs - w, (top + 9) * gs - line, w, line, exhaustionColor.argb());
			}
		} finally {
			d.end();
		}
	}

	public void drawFood(GuiGraphicsExtractor g) {
		if (!enabled()) {
			return;
		}
		if (exhaustionStyle.is("Thin line")) {
			exhaustionBar(g);
		}
		Player player = mc.player;
		if (!showsFoodBar(player)) {
			return;
		}
		FoodData data = player.getFoodData();
		int food = data.getFoodLevel();
		float saturation = data.getSaturationLevel();
		int right = g.guiWidth() / 2 + 91;
		int top = g.guiHeight() - 39;
		ItemStack held = previews() ? heldFood(player) : ItemStack.EMPTY;
		FoodProperties props = held.isEmpty() ? null : held.get(DataComponents.FOOD);
		int newFood = food;
		float newSaturation = saturation;
		if (props != null) {
			newFood = Math.min(20, food + props.nutrition());
			newSaturation = Math.min(newFood, saturation + props.saturation());
		}
		float flashing = flashAlpha();
		if (props != null && hungerPreview.on() && newFood > food) {
			boolean hungry = player.hasEffect(MobEffects.HUNGER);
			int c = alpha(0xFFFFFFFF, flashing);
			for (int i = 0; i < 10; i++) {
				int lo = i * 2 + 1;
				if (newFood < lo || food >= lo + 1) {
					continue;
				}
				Identifier sprite = newFood >= lo + 1 ? (hungry ? FOOD_FULL_HUNGER : FOOD_FULL) : (hungry ? FOOD_HALF_HUNGER : FOOD_HALF);
				g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, right - i * 8 - 9, top, 9, 9, c);
			}
		}
		boolean drawCurrent = saturationOverlay.on() && saturation > 0f;
		boolean drawPreview = props != null && saturationPreview.on() && newSaturation > saturation;
		if (!drawCurrent && !drawPreview) {
			return;
		}
		Tex tex = texture();
		if (tex == null) {
			return;
		}
		Draw d = new Draw(g, 1f);
		try {
			float gs = Draw.guiScale();
			for (int i = 0; i < 10; i++) {
				float current = Math.max(0f, Math.min(2f, saturation - i * 2));
				float next = Math.max(0f, Math.min(2f, newSaturation - i * 2));
				float x = (right - i * 8 - 9) * gs;
				float y = top * gs;
				if (drawCurrent && current > 0f) {
					outline(d, tex, x, y, gs, current / 2f, color.argb());
				}
				if (drawPreview && next > current) {
					outline(d, tex, x, y, gs, next / 2f, alpha(color.argb(), flashing));
				}
			}
		} finally {
			d.end();
		}
	}

	private void outline(Draw d, Tex tex, float x, float y, float gs, float fraction, int argb) {
		float steps = Math.max(0.25f, (float) Math.ceil(fraction * 4f) / 4f);
		float texel = 9f * gs / overlayW;
		int full = overlayW + margin * 2;
		int show = Math.max(1, Math.round(full * steps));
		int u = full - show;
		float ox = x - margin * texel + u * texel;
		float oy = y - margin * texel;
		d.region(tex, ox, oy, show * texel, (overlayH + margin * 2) * texel, u, 0, show, overlayH + margin * 2, argb);
	}

	public void drawHealth(GuiGraphicsExtractor g) {
		if (!enabled() || !healthPreview.on()) {
			return;
		}
		Player player = mc.player;
		if (!showsFoodBar(player)) {
			return;
		}
		ItemStack held = heldFood(player);
		FoodProperties props = held.isEmpty() ? null : held.get(DataComponents.FOOD);
		if (props == null) {
			return;
		}
		FoodData data = player.getFoodData();
		float health = player.getHealth();
		float max = player.getMaxHealth();
		if (health >= max) {
			return;
		}
		int newFood = Math.min(20, data.getFoodLevel() + props.nutrition());
		float newSaturation = Math.min(newFood, data.getSaturationLevel() + props.saturation());
		float exhaustion = Math.max(0f, exhaustion());
		float gain = regen(newFood, newSaturation, exhaustion, max - health);
		if (gain <= 0f) {
			return;
		}
		int now = (int) Math.ceil(health);
		int after = (int) Math.ceil(Math.min(max, health + gain));
		if (after <= now) {
			return;
		}
		float absorption = player.getAbsorptionAmount();
		int rows = (int) Math.ceil((Math.max(max, health) + (float) Math.ceil(absorption)) / 2f / 10f);
		int rowH = Math.max(10 - (rows - 2), 3);
		int left = g.guiWidth() / 2 - 91;
		int top = g.guiHeight() - 39;
		boolean hardcore = mc.level != null && mc.level.getLevelData().isHardcore();
		int c = alpha(0xFFFFFFFF, flashAlpha());
		int hearts = (int) Math.ceil(max / 2f);
		for (int j = 0; j < hearts; j++) {
			int cur = Math.max(0, Math.min(2, now - j * 2));
			int next = Math.max(0, Math.min(2, after - j * 2));
			if (next <= cur) {
				continue;
			}
			Identifier sprite = next == 2 ? (hardcore ? HEART_HARDCORE_FULL : HEART_FULL) : (hardcore ? HEART_HARDCORE_HALF : HEART_HALF);
			g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, left + (j % 10) * 8, top - (j / 10) * rowH, 9, 9, c);
		}
	}

	private static float regen(int food, float saturation, float exhaustion, float missing) {
		float gain = 0f;
		int guard = 0;
		while (food >= 18 && gain < missing && guard++ < 2000) {
			while (exhaustion > 4f) {
				exhaustion -= 4f;
				if (saturation > 0f) {
					saturation = Math.max(saturation - 1f, 0f);
				} else {
					food = Math.max(food - 1, 0);
				}
			}
			if (food >= 20 && saturation > 0f) {
				float f = Math.min(saturation, 6f);
				gain += f / 6f;
				exhaustion += f;
			} else if (food >= 18) {
				gain += 1f;
				exhaustion += 6f;
			}
		}
		return Math.min(gain, missing);
	}

	private Tex texture() {
		long now = System.currentTimeMillis();
		String wanted = style.get();
		if (overlay != null && now - checked < 2000 && overlayKey.endsWith("|" + wanted)) {
			return overlay;
		}
		checked = now;
		try {
			Optional<Resource> full = mc.getResourceManager().getResource(FULL);
			if (full.isEmpty()) {
				return overlay;
			}
			Optional<Resource> empty = mc.getResourceManager().getResource(EMPTY);
			String key = full.get().sourcePackId() + "|" + empty.map(Resource::sourcePackId).orElse("") + "|" + wanted;
			if (overlay != null && key.equals(overlayKey)) {
				return overlay;
			}
			BufferedImage a = read(full.get());
			BufferedImage b = empty.isPresent() ? read(empty.get()) : null;
			int w = a.getWidth();
			int h = Math.min(a.getHeight(), w);
			boolean[] filled = new boolean[w * h];
			boolean[] solid = new boolean[w * h];
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					boolean on = (a.getRGB(x, y) >>> 24) > 16;
					filled[y * w + x] = on;
					if (!on && b != null && x < b.getWidth() && y < b.getHeight()) {
						on = (b.getRGB(x, y) >>> 24) > 16;
					}
					solid[y * w + x] = on;
				}
			}
			int m = wanted.equals("Outer outline") ? Math.max(1, w / 9) : 0;
			int tw = w + m * 2, th = h + m * 2;
			float[] coverage = new float[tw * th];
			for (int y = 0; y < th; y++) {
				for (int x = 0; x < tw; x++) {
					int sx = x - m, sy = y - m;
					boolean inside = sx >= 0 && sy >= 0 && sx < w && sy < h && solid[sy * w + sx];
					float value = 0f;
					if (wanted.equals("Outer outline")) {
						value = !inside && near(solid, w, h, sx, sy, m) ? 1f : 0f;
					} else if (wanted.equals("Tint")) {
						value = sx >= 0 && sy >= 0 && sx < w && sy < h && filled[sy * w + sx] ? 0.55f : 0f;
					} else if (inside && edge(solid, w, h, sx, sy)) {
						value = 1f;
					}
					coverage[y * tw + x] = value;
				}
			}
			if (overlay != null) {
				overlay.close();
			}
			overlay = Tex.alpha("saturation", tw, th, coverage);
			overlayW = w;
			overlayH = h;
			margin = m;
			overlayKey = key;
		} catch (Exception ignored) {
		}
		return overlay;
	}

	private static boolean solidAt(boolean[] solid, int w, int h, int x, int y) {
		return x >= 0 && y >= 0 && x < w && y < h && solid[y * w + x];
	}

	private static boolean edge(boolean[] solid, int w, int h, int x, int y) {
		return !solidAt(solid, w, h, x - 1, y) || !solidAt(solid, w, h, x + 1, y) || !solidAt(solid, w, h, x, y - 1) || !solidAt(solid, w, h, x, y + 1);
	}

	private static boolean near(boolean[] solid, int w, int h, int x, int y, int reach) {
		for (int dy = -reach; dy <= reach; dy++) {
			for (int dx = -reach; dx <= reach; dx++) {
				if (solidAt(solid, w, h, x + dx, y + dy)) {
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
		if (tooltipShift.on() && !Compat.isKeyDown("key.keyboard.left.shift") && !Compat.isKeyDown("key.keyboard.right.shift")) {
			return;
		}
		MutableComponent line = tooltipStyle.is("Icons") ? iconLine(food) : textLine(food);
		lines.add(Math.min(1, lines.size()), line);
	}

	private MutableComponent iconLine(FoodProperties food) {
		MutableComponent line = Component.empty();
		int n = food.nutrition();
		int full = n / 2;
		boolean half = n % 2 == 1;
		if (full + (half ? 1 : 0) > 10) {
			line.append(sprite(FOOD_FULL));
			line.append(Component.literal(" x" + number(n / 2f)).withStyle(s -> s.withColor(TextColor.fromRgb(tooltipHunger.argb() & 0xFFFFFF))));
		} else {
			for (int i = 0; i < full; i++) {
				line.append(sprite(FOOD_FULL));
			}
			if (half) {
				line.append(sprite(FOOD_HALF));
			}
		}
		if (tooltipSaturation.on()) {
			line.append(Component.literal("  " + number(food.saturation()) + " saturation").withStyle(s -> s.withColor(TextColor.fromRgb(tooltipSat.argb() & 0xFFFFFF))));
		}
		return line;
	}

	private static MutableComponent sprite(Identifier id) {
		return Component.object(new AtlasSprite(AtlasIds.GUI, id));
	}

	private MutableComponent textLine(FoodProperties food) {
		String hunger = hungerUnit.is("Points") ? "+" + food.nutrition() + " hunger" : "+" + number(food.nutrition() / 2f) + " hunger bars";
		MutableComponent line = Component.literal(hunger).withStyle(s -> s.withColor(TextColor.fromRgb(tooltipHunger.argb() & 0xFFFFFF)));
		if (tooltipSaturation.on()) {
			line.append(Component.literal("  +" + number(food.saturation()) + " saturation").withStyle(s -> s.withColor(TextColor.fromRgb(tooltipSat.argb() & 0xFFFFFF))));
		}
		return line;
	}

	private static String number(float value) {
		if (Math.abs(value - Math.round(value)) < 0.05f) {
			return String.valueOf(Math.round(value));
		}
		return String.format(Locale.ROOT, "%.1f", value);
	}
}
