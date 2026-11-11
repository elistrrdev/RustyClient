package com.rustyclient.mixin;

import com.rustyclient.module.utility.NameProtect;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.util.StringDecomposer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StringDecomposer.class)
public abstract class NameProtectMixin {
	@Inject(method = "iterateFormatted(Ljava/lang/String;ILnet/minecraft/network/chat/Style;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z", at = @At("HEAD"), cancellable = true)
	private static void rusty$formatted(String text, int start, Style style, Style reset, FormattedCharSink sink, CallbackInfoReturnable<Boolean> cir) {
		if (start != 0 || !NameProtect.active()) {
			return;
		}
		String replaced = NameProtect.apply(text);
		if (replaced != null && !replaced.equals(text)) {
			cir.setReturnValue(StringDecomposer.iterateFormatted(replaced, 0, style, reset, sink));
		}
	}

	@ModifyVariable(method = "iterate", at = @At("HEAD"), argsOnly = true)
	private static String rusty$plain(String text) {
		return NameProtect.active() ? NameProtect.apply(text) : text;
	}
}
