package com.rustyclient.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(ChatComponent.class)
public interface ChatAccessor {
	@Accessor("allMessages")
	List<GuiMessage> rusty$allMessages();

	@Accessor("trimmedMessages")
	List<GuiMessage.Line> rusty$trimmedMessages();

	@Accessor("chatScrollbarPos")
	int rusty$scrollPos();

	@Invoker("getScale")
	double rusty$scale();

	@Invoker("getLineHeight")
	int rusty$lineHeight();

	@Invoker("getWidth")
	int rusty$width();
}
