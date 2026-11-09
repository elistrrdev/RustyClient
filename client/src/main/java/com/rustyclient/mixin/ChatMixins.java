package com.rustyclient.mixin;

import com.rustyclient.core.Theme;
import com.rustyclient.module.utility.Utilities;
import com.rustyclient.platform.Compat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.StringDecomposer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

public final class ChatMixins {
	private ChatMixins() {
	}

	@Mixin(ChatComponent.class)
	public abstract static class Messages {
		@Unique
		private String rusty$lastPlain;
		@Unique
		private int rusty$repeat = 1;
		@Unique
		private GuiMessage rusty$lastAdded;

		@ModifyVariable(method = "addMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
		private Component rusty$decorate(Component message) {
			Utilities.Chat chat = Utilities.Chat.active();
			if (chat == null) {
				return message;
			}
			Component result = message;
			if (chat.stack.on()) {
				String plain = message.getString();
				ChatAccessor acc = (ChatAccessor) this;
				List<GuiMessage> all = acc.rusty$allMessages();
				if (plain.equals(rusty$lastPlain) && !all.isEmpty() && all.get(0) == rusty$lastAdded) {
					rusty$repeat++;
					GuiMessage old = all.remove(0);
					acc.rusty$trimmedMessages().removeIf(line -> line.parent() == old);
					MutableComponent copy = message.copy();
					copy.append(Component.literal(" (x" + rusty$repeat + ")").withStyle(ChatFormatting.GRAY));
					result = copy;
				} else {
					rusty$repeat = 1;
					rusty$lastPlain = plain;
				}
			}
			return chat.stamp(result);
		}

		@Inject(method = "addMessage", at = @At("TAIL"))
		private void rusty$remember(Component message, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
			List<GuiMessage> all = ((ChatAccessor) this).rusty$allMessages();
			rusty$lastAdded = all.isEmpty() ? null : all.get(0);
		}

		@ModifyConstant(method = {"addMessageToDisplayQueue", "addMessageToQueue"}, constant = @Constant(intValue = 100), require = 0)
		private int rusty$history(int vanilla) {
			Utilities.Chat chat = Utilities.Chat.active();
			return chat == null ? vanilla : chat.historyLimit(vanilla);
		}
	}

	@Mixin(ChatScreen.class)
	public abstract static class Copy {
		@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
		private void rusty$copy(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
			Utilities.Chat chat = Utilities.Chat.active();
			if (chat == null || !chat.copy.on() || event.button() != 1) {
				return;
			}
			Minecraft mc = Minecraft.getInstance();
			ChatAccessor acc = (ChatAccessor) Compat.chat();
			double scale = acc.rusty$scale();
			int lineH = Math.max(1, acc.rusty$lineHeight());
			double x = event.x() / scale - 4;
			double y = (mc.getWindow().getGuiScaledHeight() - event.y() - 40) / (scale * lineH);
			if (x < 0 || x > acc.rusty$width() / scale + 8 || y < 0) {
				return;
			}
			int index = (int) Math.floor(y) + acc.rusty$scrollPos();
			List<GuiMessage.Line> lines = acc.rusty$trimmedMessages();
			if (index < 0 || index >= lines.size()) {
				return;
			}
			GuiMessage parent = lines.get(index).parent();
			String text = parent.content().getString().replaceAll("^\\[[0-9:apm ]+\\] ", "");
			mc.keyboardHandler.setClipboard(StringDecomposer.getPlainText(Component.literal(text)));
			Compat.chat().addClientSystemMessage(Component.literal("Copied message to clipboard").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(Theme.accent() & 0xFFFFFF))));
			cir.setReturnValue(true);
		}
	}
}
