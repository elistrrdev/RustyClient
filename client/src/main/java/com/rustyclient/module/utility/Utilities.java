package com.rustyclient.module.utility;

import com.rustyclient.RustyClient;
import com.rustyclient.core.Category;
import com.rustyclient.core.Module;
import com.rustyclient.core.Modules;
import com.rustyclient.core.TextHud;
import com.rustyclient.core.Theme;
import com.rustyclient.core.setting.BoolSetting;
import com.rustyclient.core.setting.ChoiceSetting;
import com.rustyclient.core.setting.KeySetting;
import com.rustyclient.core.setting.NumberSetting;
import com.rustyclient.core.setting.TextSetting;
import com.rustyclient.module.ClientSettings;
import com.rustyclient.platform.Compat;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Stream;

public final class Utilities {
	private Utilities() {
	}

	public static final class ToggleSprint extends TextHud {
		private final BoolSetting sprint = bool("Toggle sprint", GENERAL, true);
		private final BoolSetting sneak = bool("Toggle sneak", GENERAL, false);
		private final BoolSetting showHud = bool("Show status on HUD", GENERAL, true);
		private boolean sprinting;
		private boolean sneaking;
		private boolean sprintWas;
		private boolean sneakWas;

		public ToggleSprint() {
			super("Toggle Sprint", "footprints", Category.UTILITY, false, 0.01, 0.97);
			background.set(false);
			showLabel.set(false);
		}

		@Override
		public boolean visibleNow() {
			return showHud.on() && status() != null;
		}

		private String status() {
			if (mc.player == null) {
				return null;
			}
			if (sneaking) {
				return "Sneaking (Toggled)";
			}
			if (mc.options.keyShift.isDown() && mc.player.isShiftKeyDown()) {
				return "Sneaking (Key Held)";
			}
			if (sprinting) {
				return "Sprinting (Toggled)";
			}
			if (mc.player.isSprinting()) {
				return mc.options.keySprint.isDown() ? "Sprinting (Key Held)" : "Sprinting (Vanilla)";
			}
			return null;
		}

		@Override
		public void tick() {
			if (Compat.screen() == null) {
				boolean sprintDown = Compat.isKeyDown(mc.options.keySprint.saveString());
				boolean sneakDown = Compat.isKeyDown(mc.options.keyShift.saveString());
				if (sprint.on() && sprintDown && !sprintWas) {
					sprinting = !sprinting;
				}
				if (sneak.on() && sneakDown && !sneakWas) {
					sneaking = !sneaking;
				}
				sprintWas = sprintDown;
				sneakWas = sneakDown;
			}
			if (!sprint.on()) {
				sprinting = false;
			}
			if (!sneak.on()) {
				sneaking = false;
			}
			if (sprinting) {
				mc.options.keySprint.setDown(true);
			}
			if (sneaking) {
				mc.options.keyShift.setDown(true);
			}
		}

		@Override
		protected void onDisable() {
			if (sprinting) {
				mc.options.keySprint.setDown(false);
			}
			if (sneaking) {
				mc.options.keyShift.setDown(false);
			}
			sprinting = false;
			sneaking = false;
		}

		@Override
		protected String label() {
			return "";
		}

		@Override
		protected String value() {
			String status = status();
			return status == null ? "Sprinting (Toggled)" : status;
		}
	}

	public static final class AutoGG extends Module {
		private final TextSetting message = text("Message", GENERAL, "gg", 64);
		private final TextSetting triggers = text("Trigger words", GENERAL, "you won,winner:,victory!,1st killer,game over,you died! want to play again", 256);
		private final NumberSetting delay = number("Delay", APPEARANCE, 1, 0, 5, 0.25, NumberSetting.decimal("s"));
		private long sendAt = -1;
		private long lastSent;

		public AutoGG() {
			super("Auto GG", "party-popper", Category.UTILITY, false);
		}

		public void onMessage(Component text) {
			if (!enabled() || mc.getCurrentServer() == null) {
				return;
			}
			String s = text.getString().toLowerCase(Locale.ROOT);
			for (String trigger : triggers.get().toLowerCase(Locale.ROOT).split(",")) {
				String t = trigger.trim();
				if (!t.isEmpty() && s.contains(t)) {
					if (System.currentTimeMillis() - lastSent > 10000 && sendAt < 0) {
						sendAt = System.currentTimeMillis() + Math.round(delay.v() * 1000);
					}
					return;
				}
			}
		}

		@Override
		public void tick() {
			if (sendAt > 0 && System.currentTimeMillis() >= sendAt) {
				sendAt = -1;
				lastSent = System.currentTimeMillis();
				if (mc.player != null && mc.getConnection() != null && !message.get().isBlank()) {
					mc.getConnection().sendChat(message.get().trim());
				}
			}
		}
	}

	public static final class ScreenshotHelper extends Module {
		private final BoolSetting copy = bool("Copy to clipboard", GENERAL, true);
		private final BoolSetting announce = bool("Chat message with actions", GENERAL, true);
		private final KeySetting folderKey = key("Open folder key", KEYBIND, KeySetting.NONE);
		private boolean screenshotWas;
		private boolean folderWas;
		private long lookAfter = -1;
		private long since;

		public ScreenshotHelper() {
			super("Screenshot Helper", "camera", Category.UTILITY, false);
		}

		private Path dir() {
			return FabricLoader.getInstance().getGameDir().resolve("screenshots");
		}

		@Override
		public void alwaysTick() {
			if (!enabled()) {
				return;
			}
			boolean down = Compat.isKeyDown(mc.options.keyScreenshot.saveString());
			if (down && !screenshotWas) {
				since = System.currentTimeMillis() - 1000;
				lookAfter = System.currentTimeMillis() + 400;
			}
			screenshotWas = down;
			boolean folder = folderKey.bound() && Compat.screen() == null && Compat.isKeyDown(folderKey.get());
			if (folder && !folderWas) {
				RustyClient.openFolder(dir());
			}
			folderWas = folder;
			if (lookAfter > 0 && System.currentTimeMillis() > lookAfter) {
				Path latest = latest();
				if (latest != null) {
					lookAfter = -1;
					handle(latest);
				} else if (System.currentTimeMillis() - lookAfter > 4000) {
					lookAfter = -1;
				}
			}
		}

		private Path latest() {
			try (Stream<Path> files = Files.list(dir())) {
				return files.filter(p -> p.toString().endsWith(".png")).filter(p -> {
					try {
						return Files.getLastModifiedTime(p).toMillis() >= since;
					} catch (Exception e) {
						return false;
					}
				}).max((a, b) -> {
					try {
						return Files.getLastModifiedTime(a).compareTo(Files.getLastModifiedTime(b));
					} catch (Exception e) {
						return 0;
					}
				}).orElse(null);
			} catch (Exception e) {
				return null;
			}
		}

		private void handle(Path file) {
			if (copy.on()) {
				copyImage(file);
			}
			if (announce.on() && mc.player != null) {
				int accent = Theme.accent() & 0xFFFFFF;
				MutableComponent msg = Component.literal("Screenshot saved").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(accent)));
				msg.append(Component.literal(copy.on() ? " and copied  " : "  ").withStyle(ChatFormatting.GRAY));
				msg.append(action("[Open]", new ClickEvent.OpenFile(file.toFile()), "Open the screenshot"));
				msg.append(Component.literal(" "));
				msg.append(action("[Folder]", new ClickEvent.OpenFile(dir().toFile()), "Open the screenshots folder"));
				Compat.chat().addClientSystemMessage(msg);
			}
		}

		private static MutableComponent action(String label, ClickEvent click, String hover) {
			return Component.literal(label).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xF2EEEA)).withUnderlined(true).withClickEvent(click).withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
		}

		private static void copyImage(Path file) {
			String path = file.toAbsolutePath().toString().replace("'", "''");
			String script = "Add-Type -AssemblyName System.Windows.Forms; Add-Type -AssemblyName System.Drawing; $img = [System.Drawing.Image]::FromFile('" + path + "'); [System.Windows.Forms.Clipboard]::SetImage($img); $img.Dispose()";
			Thread thread = new Thread(() -> {
				try {
					new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-STA", "-WindowStyle", "Hidden", "-Command", script).redirectErrorStream(true).start().waitFor();
				} catch (Exception e) {
					RustyClient.LOG.warn("could not copy screenshot", e);
				}
			}, "rustyclient-clipboard");
			thread.setDaemon(true);
			thread.start();
		}
	}

	public static final class Chat extends Module {
		public final BoolSetting timestamps = bool("Timestamps", GENERAL, true);
		public final BoolSetting stack = bool("Stack repeated messages", GENERAL, true);
		public final BoolSetting longer = bool("Longer chat history", GENERAL, true);
		public final BoolSetting copy = bool("Right click to copy", GENERAL, true);
		public final ChoiceSetting format = choice("Timestamp format", APPEARANCE, "24 hour", "24 hour", "12 hour", "With seconds");

		public Chat() {
			super("Chat", "message-square", Category.UTILITY, false);
		}

		public static Chat active() {
			Chat c = Modules.get(Chat.class);
			return c != null && c.enabled() ? c : null;
		}

		public Component stamp(Component message) {
			if (!timestamps.on()) {
				return message;
			}
			String pattern = switch (format.get()) {
				case "12 hour" -> "h:mm";
				case "With seconds" -> "HH:mm:ss";
				default -> "HH:mm";
			};
			String time = LocalTime.now().format(DateTimeFormatter.ofPattern(pattern, Locale.ROOT));
			return Component.literal("[" + time + "] ").withStyle(ChatFormatting.DARK_GRAY).append(message);
		}

		public int historyLimit(int vanilla) {
			return longer.on() ? Math.max(vanilla, 1000) : vanilla;
		}
	}

	public static final class HotbarLock extends Module {
		public final BoolSetting drop = bool("Block dropping items", GENERAL, true);
		public final BoolSetting allowSneak = bool("Allow drop while sneaking", GENERAL, true);
		public final BoolSetting swap = bool("Block offhand swap", GENERAL, false);
		public final BoolSetting scroll = bool("Lock scroll wheel", GENERAL, false);

		public HotbarLock() {
			super("Hotbar Lock", "lock", Category.UTILITY, false);
		}

		@Override
		protected boolean hasToggleKey() {
			return true;
		}

		public void beforeInput() {
			if (mc.player == null || Compat.screen() != null) {
				return;
			}
			boolean sneakOk = allowSneak.on() && mc.player.isShiftKeyDown();
			if (drop.on() && !sneakOk) {
				while (mc.options.keyDrop.consumeClick()) {
				}
			}
			if (swap.on()) {
				while (mc.options.keySwapOffhand.consumeClick()) {
				}
			}
		}

		public boolean blocksScroll() {
			return enabled() && scroll.on();
		}
	}

	public static final class MenuBlur extends Module {
		public final BoolSetting containers = bool("Blur inventories", GENERAL, true);
		public final BoolSetting chat = bool("Blur chat screen", GENERAL, false);
		public final NumberSetting dim = number("Dim background", APPEARANCE, 0.25, 0, 0.8, 0.05, NumberSetting.percent());
		public final NumberSetting strength = number("Blur strength", APPEARANCE, 5, 1, 10, 1, NumberSetting.integer(""));

		public MenuBlur() {
			super("Menu Blur", "aperture", Category.UTILITY, false);
		}

		public boolean blurs(Object screen) {
			if (!enabled()) {
				return false;
			}
			boolean container = screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> && containers.on();
			boolean chatScreen = screen instanceof net.minecraft.client.gui.screens.ChatScreen && chat.on();
			return container || chatScreen;
		}

		public static int blurriness(int vanilla) {
			Object screen = Compat.screen();
			if (screen == null) {
				return vanilla;
			}
			if (screen instanceof com.rustyclient.gui.RustyScreen) {
				ClientSettings settings = ClientSettings.get();
				return settings != null && settings.blurBehind.on() && vanilla < 1 ? 5 : vanilla;
			}
			MenuBlur blur = Modules.get(MenuBlur.class);
			return blur != null && blur.blurs(screen) ? blur.strength.i() : vanilla;
		}
	}
}
