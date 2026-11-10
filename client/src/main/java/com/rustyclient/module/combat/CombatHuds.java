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

	public static final class HitDelay extends HudModule {
		private final ChoiceSetting style = choice("Style", APPEARANCE, "Bar", "Bar", "Ring", "Text");
		private final BoolSetting hideReady = bool("Hide when ready", GENERAL, true);
		private final ColorSetting color = color("Color", APPEARANCE, Theme.DEFAULT_ACCENT);

		public HitDelay() {
			super("Attack Indicator", "timer", Category.COMBAT, false, 0.5, 0.56);
		}

		@Override
		protected String legacyId() {
			return "hit_delay_indicator";
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
			d.round(0, 0, 40, 4, radius(2f), 0x88000000);
			d.round(0, 0, Math.max(4, 40 * p), 4, radius(2f), c);
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
				d.round(0, 0, w, h, radius(), 0x8C0F0E0D);
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
				d.textRight(String.valueOf(e.getValue()), w - 6, y, Fonts.Style.SEMIBOLD, 8.5f, Theme.accent());
				y += row;
			}
		}
	}
}
