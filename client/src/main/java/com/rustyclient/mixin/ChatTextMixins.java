package com.rustyclient.mixin;

import com.rustyclient.module.utility.Utilities;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class ChatTextMixins {
	private static final String ACCEPT = "Lnet/minecraft/client/gui/ActiveTextCollector;accept(Lnet/minecraft/client/gui/TextAlignment;IILnet/minecraft/client/gui/ActiveTextCollector$Parameters;Lnet/minecraft/util/FormattedCharSequence;)V";

	private ChatTextMixins() {
	}

	@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$DrawingBackgroundGraphicsAccess")
	public abstract static class Background {
		@Shadow
		@Final
		private GuiGraphicsExtractor graphics;

		@Inject(method = "handleMessage", at = @At("HEAD"))
		private void rusty$draw(int top, float opacity, FormattedCharSequence message, CallbackInfoReturnable<Boolean> cir) {
			Utilities.Chat.drawLine(graphics, top, opacity, message);
		}

		@ModifyArg(method = "handleMessage", at = @At(value = "INVOKE", target = ACCEPT), index = 3)
		private ActiveTextCollector.Parameters rusty$hide(ActiveTextCollector.Parameters parameters) {
			return Utilities.Chat.customText() ? parameters.withOpacity(0f) : parameters;
		}
	}

	@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$DrawingFocusedGraphicsAccess")
	public abstract static class Focused {
		@Shadow
		@Final
		private GuiGraphicsExtractor graphics;

		@Inject(method = "handleMessage", at = @At("HEAD"))
		private void rusty$draw(int top, float opacity, FormattedCharSequence message, CallbackInfoReturnable<Boolean> cir) {
			Utilities.Chat.drawLine(graphics, top, opacity, message);
		}

		@ModifyArg(method = "handleMessage", at = @At(value = "INVOKE", target = ACCEPT), index = 3)
		private ActiveTextCollector.Parameters rusty$hide(ActiveTextCollector.Parameters parameters) {
			return Utilities.Chat.customText() ? parameters.withOpacity(0f) : parameters;
		}
	}
}
