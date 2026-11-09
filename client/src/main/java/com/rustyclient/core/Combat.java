package com.rustyclient.core;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Combat {
	private record Pending(LivingEntity target, int tick) {
	}

	private static final List<Pending> PENDING = new ArrayList<>();
	private static final Map<String, Integer> POPS = new LinkedHashMap<>();
	private static final Map<String, Long> POP_TIMES = new LinkedHashMap<>();
	private static int ticks;
	private static int combo;
	private static long lastHit;
	private static double reach = -1;
	private static long reachTime;
	private static LivingEntity target;
	private static long targetTime;
	private static int lastHurtTime;

	private Combat() {
	}

	public static void onAttack(Entity entity) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		HitResult hit = mc.hitResult;
		if (hit instanceof EntityHitResult ehr && ehr.getEntity() == entity) {
			reach = mc.player.getEyePosition().distanceTo(hit.getLocation());
			reachTime = System.currentTimeMillis();
		}
		if (entity instanceof LivingEntity living) {
			target = living;
			targetTime = System.currentTimeMillis();
			PENDING.add(new Pending(living, ticks));
		}
	}

	public static void tick() {
		ticks++;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			PENDING.clear();
			combo = 0;
			return;
		}
		Iterator<Pending> it = PENDING.iterator();
		while (it.hasNext()) {
			Pending p = it.next();
			if (p.target().hurtTime > 0 && p.target().hurtTime >= 8) {
				combo++;
				lastHit = System.currentTimeMillis();
				it.remove();
			} else if (ticks - p.tick() > 6 || !p.target().isAlive()) {
				it.remove();
			}
		}
		int hurt = mc.player.hurtTime;
		if (hurt > lastHurtTime && hurt >= 9) {
			combo = 0;
		}
		lastHurtTime = hurt;
		if (combo > 0 && System.currentTimeMillis() - lastHit > 3000) {
			combo = 0;
		}
		if (target != null && (!target.isAlive() || target.isRemoved())) {
			if (System.currentTimeMillis() - targetTime > 1500) {
				target = null;
			}
		}
	}

	public static void onTotemPop(Entity entity) {
		String name = entity.getName().getString();
		POPS.merge(name, 1, Integer::sum);
		POP_TIMES.put(name, System.currentTimeMillis());
	}

	public static void onDeath(Entity entity) {
		String name = entity.getName().getString();
		POPS.remove(name);
		POP_TIMES.remove(name);
	}

	public static Map<String, Integer> pops() {
		long now = System.currentTimeMillis();
		POP_TIMES.entrySet().removeIf(e -> now - e.getValue() > 60000);
		POPS.keySet().retainAll(POP_TIMES.keySet());
		return POPS;
	}

	public static void clearPops() {
		POPS.clear();
		POP_TIMES.clear();
	}

	public static int combo() {
		return combo;
	}

	public static double reach() {
		return System.currentTimeMillis() - reachTime > 5000 ? -1 : reach;
	}

	public static LivingEntity target(long keepMs) {
		if (target != null && System.currentTimeMillis() - targetTime < keepMs && !target.isRemoved()) {
			return target;
		}
		return null;
	}
}
