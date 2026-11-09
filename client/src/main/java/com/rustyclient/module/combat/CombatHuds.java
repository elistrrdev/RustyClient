package com.rustyclient.module.combat;

import com.rustyclient.core.Category;
import com.rustyclient.core.Combat;
import com.rustyclient.core.HudModule;
import com.rustyclient.core.TextHud;
import com.rustyclient.core.Theme;
import com.rustyclient.core.setting.BoolSetting;
import com.rustyclient.core.setting.ChoiceSetting;
import com.rustyclient.core.setting.ColorSetting;
import com.rustyclient.core.setting.NumberSetting;
import com.rustyclient.render.Draw;
import com.rustyclient.render.Fonts;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;

import java.util.Locale;
import java.util.Map;

public final class CombatHuds {
	private CombatHuds() {
	}

	public static final class ComboCounter extends TextHud {
		private final BoolSetting hideZero = bool("Hide when zero", GENERAL, false);

		public ComboCounter() {
			super("Combo Counter", "flame", Category.HUD, false, 0.01, 0.64);
		}

		@Override
		public boolean visibleNow() {
			return !hideZero.on() || Combat.combo() > 0;
		}

		@Override
		protected String label() {
			return "Combo";
		}

		@Override
		protected String value() {
			return String.valueOf(Combat.combo());
		}
	}

	public static final class ReachDisplay extends TextHud {
		public ReachDisplay() {
			super("Reach Display", "ruler", Category.HUD, false, 0.01, 0.70);
		}

		@Override
		protected String label() {
			return "Reach";
		}

		@Override
		protected String value() {
			double r = Combat.reach();
			return r < 0 ? "0.00" : String.format(Locale.ROOT, "%.2f", r);
		}
	}

	public static final class TargetHud extends HudModule {
		private final BoolSetting lookTarget = bool("Show looked at entity", GENERAL, true);
		private final BoolSetting armor = bool("Show armor", GENERAL, true);
		private final BoolSetting shadow = bool("Text shadow", GENERAL, false);
		private final ColorSetting background = colorAlpha("Background color", APPEARANCE, 0xD9131211);
		private final ColorSetting barColor = color("Health bar color", APPEARANCE, Theme.DEFAULT_ACCENT);
		private float shown = 1f;
		private long last = System.nanoTime();

		public TargetHud() {
			super("Target HUD", "target", Category.COMBAT, false, 0.5, 0.62);
		}

		private LivingEntity find() {
			LivingEntity t = Combat.target(4000);
			if (t != null) {
				return t;
			}
			if (lookTarget.on() && mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity living) {
				return living;
			}
			return null;
		}

		@Override
		public void render(Draw d, boolean editing) {
			long now = System.nanoTime();
			float dt = Math.min(0.1f, (now - last) / 1e9f);
			last = now;
			LivingEntity t = find();
			if (t == null && editing) {
				t = mc.player;
			}
			float w = 150, h = 48;
			width = w;
			height = h;
			if (t == null) {
				return;
			}
			float max = Math.max(1, t.getMaxHealth());
			float hp = t.getHealth();
			float frac = Math.max(0, Math.min(1, hp / max));
			shown += (frac - shown) * (1f - (float) Math.exp(-dt * 10f));
			d.round(0, 0, w, h, 8, background.argb());
			float head = 34;
			if (t instanceof AbstractClientPlayer player) {
				int px = Math.round(d.originX() + 7 * d.s);
				int py = Math.round(d.originY() + 7 * d.s);
				PlayerFaceExtractor.extractRenderState(d.g, player.getSkin(), px, py, Math.round(head * d.s));
			} else {
				d.round(7, 7, head, head, 6, Theme.SURFACE_3);
				d.icon("skull", 7 + head / 2f, 7 + head / 2f, 18, Theme.MUTED);
			}
			float tx = 48;
			String name = t.getName().getString();
			d.textShadowed(name, tx, 13, Fonts.Style.BOLD, 10, Theme.TEXT, shadow.on());
			String hpText = String.format(Locale.ROOT, "%.1f", hp + t.getAbsorptionAmount());
			d.textShadowed(hpText, tx, 25, Fonts.Style.MONO_MEDIUM, 8, Theme.TEXT_3, shadow.on());
			if (mc.player != null && t != mc.player) {
				String dist = String.format(Locale.ROOT, "%.1fm", mc.player.distanceTo(t));
				d.textRight(dist, w - 8, 13, Fonts.Style.MONO, 7.5f, Theme.MUTED);
			}
			float bx = tx, by = 34, bw = w - tx - 8, bh = 6;
			d.round(bx, by, bw, bh, 3, Theme.SURFACE_3);
			int bar = Theme.hudAccent(barColor.argb());
			if (frac < 0.3f) {
				bar = Theme.DANGER;
			}
			d.round(bx, by, Math.max(bh, bw * shown), bh, 3, bar);
			if (armor.on()) {
				float ax = w - 8 - 4 * 11;
				EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
				for (EquipmentSlot slot : slots) {
					ItemStack s = t.getItemBySlot(slot);
					if (!s.isEmpty()) {
						d.item(s, ax, 18, 10, false);
					}
					ax += 11;
				}
			}
		}
	}

	public static final class HitDelay extends HudModule {
		private final ChoiceSetting style = choice("Style", APPEARANCE, "Bar", "Bar", "Ring", "Text");
		private final BoolSetting hideReady = bool("Hide when ready", GENERAL, true);
		private final ColorSetting color = color("Color", APPEARANCE, Theme.DEFAULT_ACCENT);

		public HitDelay() {
			super("Hit Delay Indicator", "timer", Category.COMBAT, false, 0.5, 0.56);
		}

		private float progress() {
			return mc.player == null ? 1f : mc.player.getAttackStrengthScale(0f);
		}

		@Override
		public boolean visibleNow() {
			return !hideReady.on() || progress() < 1f;
		}

		@Override
		public void render(Draw d, boolean editing) {
			float p = editing && progress() >= 1f ? 0.6f : progress();
			int c = Theme.hudAccent(color.argb());
			if (style.is("Ring")) {
				width = 18;
				height = 18;
				int segments = 24;
				for (int i = 0; i < segments; i++) {
					double a = Math.PI * 2 * i / segments - Math.PI / 2;
					float x = 9 + (float) Math.cos(a) * 7, y = 9 + (float) Math.sin(a) * 7;
					d.circle(x, y, 1.3f, i < p * segments ? c : 0x66000000);
				}
				return;
			}
			if (style.is("Text")) {
				String s = Math.round(p * 100) + "%";
				width = d.width(s, Fonts.Style.BOLD, 9) + 2;
				height = 11;
				d.textShadowed(s, 1, 5.5f, Fonts.Style.BOLD, 9, c, true);
				return;
			}
			width = 40;
			height = 4;
			d.round(0, 0, 40, 4, 2, 0x88000000);
			d.round(0, 0, Math.max(4, 40 * p), 4, 2, c);
		}
	}

	public static final class TotemPops extends HudModule {
		private final BoolSetting background = bool("Draw background", GENERAL, true);
		private final BoolSetting self = bool("Count your own pops", GENERAL, true);
		private final NumberSetting max = number("Players shown", APPEARANCE, 5, 1, 10, 1, NumberSetting.integer(""));
		private final ColorSetting textColor = color("Text color", APPEARANCE, 0xFFF2EEEA);

		public TotemPops() {
			super("Totem Pop Counter", "heart-pulse", Category.COMBAT, false, 0.99, 0.3);
		}

		public boolean countSelf() {
			return self.on();
		}

		@Override
		public void render(Draw d, boolean editing) {
			Map<String, Integer> pops = Combat.pops();
			float row = 12;
			int n = Math.min(max.i(), pops.size());
			float w = 90;
			for (Map.Entry<String, Integer> e : pops.entrySet()) {
				w = Math.max(w, d.width(e.getKey(), Fonts.Style.SEMIBOLD, 8.5f) + 50);
			}
			float h = Math.max(1, n) * row + 18;
			width = w;
			height = h;
			if (background.on()) {
				d.round(0, 0, w, h, 4, 0x8C0F0E0D);
			}
			d.icon("heart-pulse", 9, 8, 7, Theme.accent());
			d.textMid("Totem pops", 16, 8, Fonts.Style.BOLD, 8.5f, Theme.accent());
			float y = 14 + row / 2f;
			if (pops.isEmpty()) {
				d.textMid(editing ? "Steve  2" : "None yet", 6, y, Fonts.Style.SEMIBOLD, 8.5f, Theme.fade(textColor.argb(), 0.6f));
				return;
			}
			int i = 0;
			for (Map.Entry<String, Integer> e : pops.entrySet()) {
				if (i++ >= n) {
					break;
				}
				d.textMid(e.getKey(), 6, y, Fonts.Style.SEMIBOLD, 8.5f, textColor.argb());
				d.textRight(String.valueOf(e.getValue()), w - 6, y, Fonts.Style.MONO_MEDIUM, 8.5f, Theme.accent());
				y += row;
			}
		}
	}
}
