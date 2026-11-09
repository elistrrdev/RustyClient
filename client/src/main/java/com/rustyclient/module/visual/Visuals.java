package com.rustyclient.module.visual;

import com.mojang.blaze3d.platform.NativeImage;
import com.rustyclient.core.Category;
import com.rustyclient.core.Module;
import com.rustyclient.core.Modules;
import com.rustyclient.core.Theme;
import com.rustyclient.core.setting.BoolSetting;
import com.rustyclient.core.setting.ChoiceSetting;
import com.rustyclient.core.setting.ColorSetting;
import com.rustyclient.core.setting.KeySetting;
import com.rustyclient.core.setting.NumberSetting;
import com.rustyclient.mixin.OverlayTextureAccessor;
import com.rustyclient.platform.Compat;
import com.rustyclient.render.Draw;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public final class Visuals {
	private Visuals() {
	}

	public static final class Crosshair extends Module {
		public final ChoiceSetting shape = choice("Shape", APPEARANCE, "Cross", "Cross", "Dot", "Circle", "Square", "Arrow");
		public final NumberSetting size = number("Size", APPEARANCE, 5, 1, 15, 0.5, NumberSetting.decimal("px"));
		public final NumberSetting gap = number("Gap", APPEARANCE, 2, 0, 10, 0.5, NumberSetting.decimal("px"));
		public final NumberSetting thickness = number("Thickness", APPEARANCE, 1, 0.5, 4, 0.5, NumberSetting.decimal("px"));
		public final ColorSetting color = colorAlpha("Color", APPEARANCE, 0xFFFFFFFF);
		public final BoolSetting outline = bool("Outline", GENERAL, true);
		public final BoolSetting dot = bool("Center dot", GENERAL, false);
		public final BoolSetting targetColor = bool("Highlight on entity", GENERAL, true);
		public final BoolSetting thirdPerson = bool("Show in third person", GENERAL, false);

		public Crosshair() {
			super("Crosshair", "focus", Category.VISUAL, false);
		}

		public void draw(Draw d, float cx, float cy) {
			int c = color.argb();
			if (targetColor.on() && mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult) {
				c = Theme.withAlpha(0xFFEF5B52, c >>> 24);
			}
			float s = size.f(), g = gap.f(), t = thickness.f();
			int o = Theme.withAlpha(0xFF000000, Math.round((c >>> 24) * 0.75f));
			float ol = outline.on() ? Math.max(0.5f, 1f / d.s) : 0;
			switch (shape.get()) {
				case "Dot" -> {
					if (ol > 0) {
						d.circle(cx, cy, s / 2f + ol, o);
					}
					d.circle(cx, cy, s / 2f, c);
				}
				case "Circle" -> {
					if (ol > 0) {
						d.ring(cx - s - ol, cy - s - ol, (s + ol) * 2, (s + ol) * 2, s + ol, t + ol * 2, o);
					}
					d.ring(cx - s, cy - s, s * 2, s * 2, s, t, c);
				}
				case "Square" -> {
					if (ol > 0) {
						d.ring(cx - s - ol, cy - s - ol, (s + ol) * 2, (s + ol) * 2, 0, t + ol * 2, o);
					}
					d.ring(cx - s, cy - s, s * 2, s * 2, 0, t, c);
				}
				case "Arrow" -> {
					for (int i = 0; i < Math.round(s * 2); i++) {
						float k = i / 2f;
						d.rect(cx - k - t / 2f, cy + k, t, t, c);
						d.rect(cx + k - t / 2f, cy + k, t, t, c);
					}
				}
				default -> {
					float[][] arms = {{cx - t / 2f, cy - g - s, t, s}, {cx - t / 2f, cy + g, t, s}, {cx - g - s, cy - t / 2f, s, t}, {cx + g, cy - t / 2f, s, t}};
					if (ol > 0) {
						for (float[] a : arms) {
							d.rect(a[0] - ol, a[1] - ol, a[2] + ol * 2, a[3] + ol * 2, o);
						}
					}
					for (float[] a : arms) {
						d.rect(a[0], a[1], a[2], a[3], c);
					}
				}
			}
			if (dot.on() && !shape.is("Dot")) {
				d.circle(cx, cy, Math.max(0.6f, t * 0.6f), c);
			}
		}

		public boolean shouldDraw() {
			if (mc.player == null) {
				return false;
			}
			return mc.options.getCameraType().isFirstPerson() || thirdPerson.on();
		}
	}

	public static final class BlockOverlay extends Module {
		public final ColorSetting outlineColor = colorAlpha("Outline color", APPEARANCE, 0xCC1AB0DD);
		public final NumberSetting width = number("Outline width", APPEARANCE, 2.5, 1, 8, 0.5, NumberSetting.decimal("px"));
		public final BoolSetting fill = bool("Fill block", GENERAL, true);
		public final ColorSetting fillColor = colorAlpha("Fill color", APPEARANCE, 0x261AB0DD);
		public final BoolSetting outline = bool("Draw outline", GENERAL, true);

		public BlockOverlay() {
			super("Block Overlay", "box", Category.VISUAL, false);
		}

		public int outline() {
			int c = outlineColor.argb();
			return (c & 0xFFFFFF) == (Theme.DEFAULT_ACCENT & 0xFFFFFF) ? Theme.withAlpha(Theme.accent(), c >>> 24) : c;
		}

		public int fill() {
			int c = fillColor.argb();
			return (c & 0xFFFFFF) == (Theme.DEFAULT_ACCENT & 0xFFFFFF) ? Theme.withAlpha(Theme.accent(), c >>> 24) : c;
		}
	}

	public static final class MotionBlur extends Module {
		private static final Identifier[] EFFECTS = new Identifier[10];

		static {
			for (int i = 0; i < EFFECTS.length; i++) {
				EFFECTS[i] = Identifier.fromNamespaceAndPath("rustyclient", "motion_blur_" + (i + 1));
			}
		}

		public final NumberSetting strength = number("Strength", APPEARANCE, 5, 1, 10, 1, NumberSetting.integer(""));

		public MotionBlur() {
			super("Motion Blur", "wind", Category.VISUAL, false);
		}

		public static Identifier effect() {
			MotionBlur m = Modules.get(MotionBlur.class);
			if (m == null || !m.enabled() || Minecraft.getInstance().player == null) {
				return null;
			}
			return EFFECTS[Math.max(1, Math.min(EFFECTS.length, m.strength.i())) - 1];
		}
	}

	public static final class Fullbright extends Module {
		public final NumberSetting strength = number("Strength", APPEARANCE, 1, 0.1, 1, 0.05, NumberSetting.percent());

		public Fullbright() {
			super("Fullbright", "sun", Category.VISUAL, false);
		}

		public static float strength() {
			Fullbright m = Modules.get(Fullbright.class);
			return m != null && m.enabled() ? m.strength.f() : 0f;
		}
	}

	public static final class Zoom extends Module {
		public final KeySetting key = key("Zoom key", GENERAL, "key.keyboard.c");
		public final NumberSetting factor = number("Zoom factor", APPEARANCE, 4, 1.5, 20, 0.5, NumberSetting.decimal("x"));
		public final BoolSetting smooth = bool("Smooth zoom", GENERAL, true);
		public final BoolSetting scroll = bool("Scroll to adjust", GENERAL, true);
		public final BoolSetting cinematic = bool("Smooth camera while zoomed", GENERAL, false);
		public final BoolSetting reduceSensitivity = bool("Lower sensitivity", GENERAL, true);
		private boolean active;
		private double extra = 1;
		private float current = 1f;
		private long last = System.nanoTime();
		private boolean savedSmooth;

		public Zoom() {
			super("Zoom", "zoom-in", Category.VISUAL, true);
		}

		@Override
		protected boolean hasToggleKey() {
			return false;
		}

		public boolean active() {
			return enabled() && active;
		}

		@Override
		public void alwaysTick() {
			boolean down = enabled() && Compat.screen() == null && Compat.isKeyDown(key.get());
			if (down != active) {
				active = down;
				if (down) {
					extra = 1;
					savedSmooth = mc.options.smoothCamera;
					if (cinematic.on()) {
						mc.options.smoothCamera = true;
					}
				} else if (cinematic.on()) {
					mc.options.smoothCamera = savedSmooth;
				}
			}
		}

		public float fovDivisor() {
			long now = System.nanoTime();
			float dt = Math.min(0.1f, (now - last) / 1e9f);
			last = now;
			float target = active() ? (float) Math.max(1, factor.v() * extra) : 1f;
			if (smooth.on()) {
				current += (target - current) * (1f - (float) Math.exp(-dt * 14f));
				if (Math.abs(current - target) < 0.001f) {
					current = target;
				}
			} else {
				current = target;
			}
			return current;
		}

		public boolean scroll(double amount) {
			if (!active() || !scroll.on() || amount == 0) {
				return false;
			}
			extra = Math.max(0.25, Math.min(8, extra * (amount > 0 ? 1.15 : 1 / 1.15)));
			return true;
		}

		public double sensitivityScale() {
			return active() && reduceSensitivity.on() ? 1.0 / Math.max(1, current) : 1.0;
		}
	}

	public static final class Freelook extends Module {
		public final KeySetting key = key("Freelook key", GENERAL, "key.keyboard.left.alt");
		public final BoolSetting hold = bool("Hold to look", GENERAL, true);
		public final BoolSetting invert = bool("Invert pitch", GENERAL, false);
		private boolean active;
		private boolean keyWas;
		private CameraType previous;
		public float yaw;
		public float pitch;

		public Freelook() {
			super("Freelook", "eye", Category.VISUAL, false);
		}

		@Override
		protected boolean hasToggleKey() {
			return false;
		}

		public boolean active() {
			return enabled() && active && mc.player != null;
		}

		@Override
		public void alwaysTick() {
			boolean down = enabled() && Compat.screen() == null && Compat.isKeyDown(key.get());
			boolean next = active;
			if (hold.on()) {
				next = down;
			} else if (down && !keyWas) {
				next = !active;
			}
			keyWas = down;
			if (!enabled()) {
				next = false;
			}
			if (next != active) {
				active = next;
				if (active && mc.player != null) {
					yaw = mc.player.getYRot();
					pitch = mc.player.getXRot();
					previous = mc.options.getCameraType();
					if (previous.isFirstPerson()) {
						mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
					}
				} else if (previous != null) {
					mc.options.setCameraType(previous);
					previous = null;
				}
			}
		}

		public void turn(double dx, double dy) {
			yaw += (float) (dx * 0.15);
			pitch += (float) ((invert.on() ? -dy : dy) * 0.15);
			pitch = Math.max(-90f, Math.min(90f, pitch));
		}
	}

	public static final class Nametags extends Module {
		public final BoolSetting own = bool("Show own nametag", GENERAL, true);
		public final BoolSetting background = bool("Draw background", GENERAL, true);
		public final NumberSetting opacity = number("Background opacity", APPEARANCE, 0.25, 0, 1, 0.05, NumberSetting.percent());

		public Nametags() {
			super("Nametags", "tag", Category.VISUAL, false);
		}

		public float backgroundOpacity(float vanilla) {
			if (!enabled()) {
				return vanilla;
			}
			return background.on() ? opacity.f() : 0f;
		}
	}

	public static final class HitColor extends Module {
		public final ColorSetting color = color("Hit color", APPEARANCE, 0xFFFF0000);
		public final NumberSetting intensity = number("Intensity", APPEARANCE, 0.3, 0, 1, 0.05, NumberSetting.percent());
		private int applied = -1;

		public HitColor() {
			super("Hit Color", "sword", Category.VISUAL, false);
		}

		@Override
		public void alwaysTick() {
			int target = enabled() ? Theme.withAlpha(color.argb(), Math.round(intensity.f() * 255)) : 0xB2FF0000;
			if (target == applied) {
				return;
			}
			try {
				DynamicTexture tex = ((OverlayTextureAccessor) Minecraft.getInstance().gameRenderer.overlayTexture()).rusty$texture();
				NativeImage image = tex.getPixels();
				if (image == null) {
					return;
				}
				for (int y = 0; y < 8; y++) {
					for (int x = 0; x < 16; x++) {
						image.setPixel(x, y, target);
					}
				}
				tex.upload();
				applied = target;
			} catch (Throwable ignored) {
				applied = target;
			}
		}
	}

	public static final class DamageTint extends Module {
		public final NumberSetting threshold = number("Start below health", APPEARANCE, 10, 2, 20, 1, NumberSetting.integer(" hp"));
		public final NumberSetting strength = number("Strength", APPEARANCE, 0.6, 0.1, 1, 0.05, NumberSetting.percent());
		public final ColorSetting color = color("Tint color", APPEARANCE, 0xFFC0262D);
		public final BoolSetting pulse = bool("Pulse at low health", GENERAL, true);

		public DamageTint() {
			super("Damage Tint", "heart-crack", Category.VISUAL, false);
		}

		public void draw(Draw d, int w, int h) {
			if (mc.player == null || mc.player.isCreative() || mc.player.isSpectator()) {
				return;
			}
			float hp = mc.player.getHealth();
			float max = threshold.f();
			if (hp >= max) {
				return;
			}
			float f = (1f - hp / max) * strength.f();
			if (pulse.on() && hp <= 6) {
				f *= 0.8f + 0.2f * (float) Math.sin(System.currentTimeMillis() / 180.0);
			}
			int edge = Theme.withAlpha(color.argb(), Math.round(Math.min(1f, f) * 200));
			float band = Math.min(w, h) * 0.22f;
			d.gradientV(0, 0, w, band, edge, edge & 0xFFFFFF);
			d.gradientV(0, h - band, w, band, edge & 0xFFFFFF, edge);
			d.pixelColumnGradients(0, 0, band, h, p -> {
				int a = Math.round((edge >>> 24) * (1f - p / 1000f));
				int c = Theme.withAlpha(edge, a);
				return new int[]{c, c};
			});
			d.pixelColumnGradients(w - band, 0, band, h, p -> {
				int a = Math.round((edge >>> 24) * (p / 1000f));
				int c = Theme.withAlpha(edge, a);
				return new int[]{c, c};
			});
		}
	}

	public static final class ItemPhysics extends Module {
		public final BoolSetting flatBlocks = bool("Lay blocks flat", GENERAL, false);

		public ItemPhysics() {
			super("Item Physics", "package-open", Category.VISUAL, false);
		}
	}

	public static final class Particles extends Module {
		public final NumberSetting multiplier = number("Crit multiplier", APPEARANCE, 2, 1, 10, 1, NumberSetting.integer("x"));
		public final BoolSetting alwaysCrits = bool("Always show crits", GENERAL, true);
		public final BoolSetting alwaysSharpness = bool("Always show magic crits", GENERAL, false);

		public Particles() {
			super("Particles", "sparkles", Category.VISUAL, false);
		}

		public void onAttack(net.minecraft.world.entity.Entity target) {
			if (mc.level == null || !(target instanceof net.minecraft.world.entity.LivingEntity)) {
				return;
			}
			int n = multiplier.i();
			for (int i = 0; i < n; i++) {
				if (alwaysCrits.on() || i > 0) {
					mc.particleEngine.createTrackingEmitter(target, net.minecraft.core.particles.ParticleTypes.CRIT);
				}
				if (alwaysSharpness.on()) {
					mc.particleEngine.createTrackingEmitter(target, net.minecraft.core.particles.ParticleTypes.ENCHANTED_HIT);
				}
			}
		}
	}

	public static final class CustomHand extends Module {
		public final NumberSetting x = number("Horizontal offset", APPEARANCE, 0, -1, 1, 0.02, NumberSetting.decimal(""));
		public final NumberSetting y = number("Vertical offset", APPEARANCE, 0, -1, 1, 0.02, NumberSetting.decimal(""));
		public final NumberSetting z = number("Depth offset", APPEARANCE, 0, -1, 1, 0.02, NumberSetting.decimal(""));
		public final NumberSetting scale = number("Item scale", APPEARANCE, 1, 0.3, 1.5, 0.05, NumberSetting.times());
		public final NumberSetting shield = number("Shield height", APPEARANCE, 0, 0, 0.5, 0.02, NumberSetting.decimal(""));
		public final BoolSetting offhand = bool("Apply to offhand", GENERAL, true);

		public CustomHand() {
			super("Custom Hand", "hand", Category.VISUAL, false);
		}

		public static void apply(com.mojang.blaze3d.vertex.PoseStack pose, boolean mainHand, net.minecraft.world.item.ItemStack stack) {
			CustomHand m = Modules.get(CustomHand.class);
			if (m == null || !m.enabled() || (!mainHand && !m.offhand.on())) {
				return;
			}
			float side = mainHand ? 1f : -1f;
			pose.translate(m.x.f() * side, m.y.f(), m.z.f());
			if (stack != null && stack.getItem() instanceof net.minecraft.world.item.ShieldItem) {
				pose.translate(0f, -m.shield.f(), 0f);
			}
			float k = m.scale.f();
			pose.scale(k, k, k);
		}
	}

	public static final class TimeWeather extends Module {
		public final BoolSetting changeTime = bool("Change time", GENERAL, true);
		public final NumberSetting time = number("Time of day", APPEARANCE, 6000, 0, 23999, 250, v -> {
			long t = ((long) v + 6000) % 24000;
			return String.format(java.util.Locale.ROOT, "%02d:%02d", t / 1000, (t % 1000) * 60 / 1000);
		});
		public final ChoiceSetting weather = choice("Weather", APPEARANCE, "Clear", "Server", "Clear", "Rain", "Thunder");

		public TimeWeather() {
			super("Time and Weather", "cloud-sun", Category.VISUAL, false);
		}

		public static long overrideTime(long total) {
			TimeWeather m = Modules.get(TimeWeather.class);
			if (m == null || !m.enabled() || !m.changeTime.on()) {
				return total;
			}
			return (total / 24000L) * 24000L + (long) m.time.v();
		}

		public static float rain(float vanilla) {
			TimeWeather m = Modules.get(TimeWeather.class);
			if (m == null || !m.enabled()) {
				return vanilla;
			}
			return switch (m.weather.get()) {
				case "Clear" -> 0f;
				case "Rain", "Thunder" -> 1f;
				default -> vanilla;
			};
		}

		public static float thunder(float vanilla) {
			TimeWeather m = Modules.get(TimeWeather.class);
			if (m == null || !m.enabled()) {
				return vanilla;
			}
			return switch (m.weather.get()) {
				case "Clear", "Rain" -> 0f;
				case "Thunder" -> 1f;
				default -> vanilla;
			};
		}
	}

	public static final class ClearFluids extends Module {
		public final BoolSetting water = bool("Clear water", GENERAL, true);
		public final BoolSetting lava = bool("Clear lava", GENERAL, true);
		public final NumberSetting distance = number("View distance", APPEARANCE, 96, 16, 256, 8, NumberSetting.integer(" blocks"));

		public ClearFluids() {
			super("Clear Water and Lava", "droplets", Category.VISUAL, false);
		}

		public static void apply(net.minecraft.client.renderer.fog.FogData fog, boolean isLava) {
			ClearFluids m = Modules.get(ClearFluids.class);
			if (m == null || !m.enabled() || (isLava ? !m.lava.on() : !m.water.on())) {
				return;
			}
			fog.environmentalStart = isLava ? 0f : -8f;
			fog.environmentalEnd = m.distance.f();
		}
	}

	public static final class ScreenOverlays extends Module {
		public final NumberSetting hurtCam = number("Hurt camera shake", APPEARANCE, 0, 0, 1, 0.05, NumberSetting.percent());
		public final NumberSetting fireHeight = number("Fire overlay lowered", APPEARANCE, 0.3, 0, 0.6, 0.02, NumberSetting.decimal(""));

		public ScreenOverlays() {
			super("Screen Overlays", "monitor", Category.VISUAL, false);
		}

		public static float hurtScale() {
			ScreenOverlays m = Modules.get(ScreenOverlays.class);
			return m != null && m.enabled() ? m.hurtCam.f() : 1f;
		}

		public static float fireOffset() {
			ScreenOverlays m = Modules.get(ScreenOverlays.class);
			return m != null && m.enabled() ? m.fireHeight.f() : 0f;
		}
	}
}
