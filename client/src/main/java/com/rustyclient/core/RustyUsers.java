package com.rustyclient.core;

import com.rustyclient.mixin.AvatarAccessor;
import com.rustyclient.module.ClientSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RustyUsers {
	public static final int MARK = 0x80;
	public static final Identifier LOGO = Identifier.fromNamespaceAndPath("rustyclient", "logo");
	private static final Set<UUID> USERS = ConcurrentHashMap.newKeySet();
	private static Boolean shared;

	private RustyUsers() {
	}

	public static boolean sharing() {
		ClientSettings settings = ClientSettings.get();
		return settings == null || settings.shareUsage.on();
	}

	public static int mark(int parts) {
		return sharing() ? parts | MARK : parts & ~MARK;
	}

	public static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			USERS.clear();
			shared = null;
			return;
		}
		boolean share = sharing();
		if (shared != null && shared != share) {
			mc.options.broadcastOptions();
		}
		shared = share;
		for (Player player : mc.level.players()) {
			if (player == mc.player) {
				continue;
			}
			byte parts = player.getEntityData().get(AvatarAccessor.rusty$parts());
			if ((parts & MARK) != 0) {
				USERS.add(player.getUUID());
			} else {
				USERS.remove(player.getUUID());
			}
		}
	}

	public static boolean uses(UUID id) {
		if (id == null) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && id.equals(mc.player.getUUID())) {
			return true;
		}
		return USERS.contains(id);
	}

	public static boolean nametagIcon() {
		ClientSettings settings = ClientSettings.get();
		return settings == null || settings.nametagIcon.on();
	}

	public static boolean tablistIcon() {
		ClientSettings settings = ClientSettings.get();
		return settings == null || settings.tablistIcon.on();
	}

	public static Component icon() {
		return Component.object(new AtlasSprite(AtlasIds.GUI, LOGO)).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF)).withBold(false).withItalic(false));
	}

	public static Component prefixed(Component name) {
		MutableComponent out = Component.empty();
		out.append(icon());
		out.append(Component.literal(" ").withStyle(ChatFormatting.RESET));
		out.append(name);
		return out;
	}
}
