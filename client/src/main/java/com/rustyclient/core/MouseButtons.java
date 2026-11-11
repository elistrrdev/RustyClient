package com.rustyclient.core;

import net.minecraft.client.Minecraft;

import java.util.HashSet;
import java.util.Set;

public final class MouseButtons {
	private static final Set<Integer> DOWN = new HashSet<>();

	private MouseButtons() {
	}

	public static void update(int button, int action) {
		if (action == 1) {
			DOWN.add(button);
		} else if (action == 0) {
			DOWN.remove(button);
		}
	}

	public static boolean down(int button) {
		if (!Minecraft.getInstance().isWindowActive()) {
			DOWN.clear();
			return false;
		}
		return DOWN.contains(button);
	}
}
