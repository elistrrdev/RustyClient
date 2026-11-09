package com.rustyclient.core;

import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;

public final class Clicks {
	private static final ArrayDeque<Long> LEFT = new ArrayDeque<>();
	private static final ArrayDeque<Long> RIGHT = new ArrayDeque<>();
	private static boolean leftDown;
	private static boolean rightDown;

	private Clicks() {
	}

	public static void poll() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options == null) {
			return;
		}
		boolean l = mc.options.keyAttack.isDown();
		boolean r = mc.options.keyUse.isDown();
		long now = System.currentTimeMillis();
		if (l && !leftDown) {
			LEFT.addLast(now);
		}
		if (r && !rightDown) {
			RIGHT.addLast(now);
		}
		leftDown = l;
		rightDown = r;
		trim(LEFT, now);
		trim(RIGHT, now);
	}

	private static void trim(ArrayDeque<Long> queue, long now) {
		while (!queue.isEmpty() && now - queue.peekFirst() > 1000) {
			queue.pollFirst();
		}
	}

	public static int left() {
		trim(LEFT, System.currentTimeMillis());
		return LEFT.size();
	}

	public static int right() {
		trim(RIGHT, System.currentTimeMillis());
		return RIGHT.size();
	}
}
