package com.rustyclient.module;

import com.rustyclient.core.Category;
import com.rustyclient.core.Module;
import com.rustyclient.core.Modules;
import com.rustyclient.core.setting.BoolSetting;
import com.rustyclient.core.setting.NumberSetting;
import com.rustyclient.platform.Compat;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;
import java.util.function.DoubleFunction;

public final class Performance extends Module {
	public static final String FRAME_RATE = "Frame rate";
	public static final String ENTITIES = "Entity distance";
	public static final String PARTICLES = "Particles";
	public static final String MODS = "Optimization mods";
	private static final int UNLIMITED = 260;
	private static final int FAR = 129;
	private static final List<String[]> OPTIMIZATION_MODS = List.of(
		new String[]{"sodium", "Sodium", "Faster chunk rendering"},
		new String[]{"lithium", "Lithium", "Faster game logic"},
		new String[]{"ferritecore", "FerriteCore", "Lower memory use"},
		new String[]{"immediatelyfast", "ImmediatelyFast", "Faster HUD and text drawing"},
		new String[]{"entityculling", "EntityCulling", "Skips hidden entities"}
	);

	public final BoolSetting limitUnfocused = bool("Limit FPS when unfocused", FRAME_RATE, false);
	public final NumberSetting unfocusedFps = number("Unfocused FPS", FRAME_RATE, 30, 5, 120, 5, NumberSetting.integer(" FPS"));
	public final BoolSetting limitMenus = bool("Limit FPS in menus", FRAME_RATE, false);
	public final NumberSetting menuFps = number("Menu FPS", FRAME_RATE, 60, 10, 240, 5, NumberSetting.integer(" FPS"));
	public final NumberSetting players = number("Players", ENTITIES, FAR, 8, FAR, 1, blocks());
	public final NumberSetting mobs = number("Mobs", ENTITIES, FAR, 8, FAR, 1, blocks());
	public final NumberSetting items = number("Dropped items", ENTITIES, FAR, 8, FAR, 1, blocks());
	public final NumberSetting armorStands = number("Armor stands", ENTITIES, FAR, 8, FAR, 1, blocks());
	public final NumberSetting frames = number("Item frames and paintings", ENTITIES, FAR, 8, FAR, 1, blocks());
	public final NumberSetting others = number("Other entities", ENTITIES, FAR, 8, FAR, 1, blocks());
	public final NumberSetting blockEntities = number("Chests, signs and other block entities", ENTITIES, FAR, 8, FAR, 1, blocks());
	public final BoolSetting explosions = bool("Show explosions", PARTICLES, true);
	public final BoolSetting blockParticles = bool("Show block breaking and running particles", PARTICLES, true);
	public final BoolSetting campfire = bool("Show campfire smoke", PARTICLES, true);
	public final BoolSetting potions = bool("Show potion effect swirls", PARTICLES, true);
	public final BoolSetting rain = bool("Show rain splashes", PARTICLES, true);
	public final BoolSetting drips = bool("Show dripping liquids", PARTICLES, true);
	public final BoolSetting fireworks = bool("Show firework sparks", PARTICLES, true);
	public final BoolSetting totems = bool("Show totem particles", PARTICLES, true);

	public Performance() {
		super("Performance", "gauge", Category.UTILITY, true);
		unfocusedFps.visibleWhen(limitUnfocused::on);
		menuFps.visibleWhen(limitMenus::on);
	}

	private static DoubleFunction<String> blocks() {
		return v -> v >= FAR ? "Unlimited" : Math.round(v) + " blocks";
	}

	public static Performance get() {
		return Modules.get(Performance.class);
	}

	@Override
	public boolean hidden() {
		return true;
	}

	@Override
	public boolean enabled() {
		return true;
	}

	public record ModStatus(String name, String description, String version) {
	}

	public static List<ModStatus> mods() {
		return OPTIMIZATION_MODS.stream()
			.map(m -> new ModStatus(m[1], m[2], FabricLoader.getInstance().getModContainer(m[0]).map(c -> c.getMetadata().getVersion().getFriendlyString().split("[+]")[0]).orElse(null)))
			.toList();
	}

	public static int framerate(int vanilla) {
		Performance p = get();
		if (p == null) {
			return vanilla;
		}
		Minecraft mc = Minecraft.getInstance();
		int limit = vanilla;
		if (p.limitUnfocused.on() && !mc.getWindow().isFocused()) {
			limit = Math.min(limit, p.unfocusedFps.i());
		}
		if (p.limitMenus.on() && mc.level != null && Compat.screen() != null) {
			limit = Math.min(limit, p.menuFps.i());
		}
		return limit >= UNLIMITED ? vanilla : limit;
	}

	private NumberSetting distanceFor(Entity entity) {
		if (entity instanceof Player) {
			return players;
		}
		if (entity instanceof ItemEntity) {
			return items;
		}
		if (entity instanceof ArmorStand) {
			return armorStands;
		}
		if (entity instanceof HangingEntity) {
			return frames;
		}
		if (entity instanceof Mob) {
			return mobs;
		}
		return others;
	}

	public static boolean hideEntity(Entity entity, double camX, double camY, double camZ) {
		Performance p = get();
		if (p == null || entity == Minecraft.getInstance().player || entity == Minecraft.getInstance().getCameraEntity()) {
			return false;
		}
		double limit = p.distanceFor(entity).v();
		if (limit >= FAR) {
			return false;
		}
		return entity.distanceToSqr(camX, camY, camZ) > limit * limit;
	}

	public static boolean hideBlockEntity(BlockPos pos) {
		Performance p = get();
		if (p == null || p.blockEntities.v() >= FAR) {
			return false;
		}
		Vec3 cam = Compat.camera().position();
		double limit = p.blockEntities.v();
		return pos.distToCenterSqr(cam) > limit * limit;
	}

	public static boolean hideParticle(ParticleOptions options) {
		Performance p = get();
		if (p == null) {
			return false;
		}
		ParticleType<?> type = options.getType();
		return (!p.explosions.on() && EXPLOSIONS.contains(type))
			|| (!p.campfire.on() && CAMPFIRE.contains(type))
			|| (!p.potions.on() && POTIONS.contains(type))
			|| (!p.rain.on() && RAIN.contains(type))
			|| (!p.drips.on() && DRIPS.contains(type))
			|| (!p.fireworks.on() && type == ParticleTypes.FIREWORK)
			|| (!p.totems.on() && type == ParticleTypes.TOTEM_OF_UNDYING);
	}

	public static boolean hideBlockParticles() {
		Performance p = get();
		return p != null && !p.blockParticles.on();
	}

	private static final Set<ParticleType<?>> EXPLOSIONS = Set.of(ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER);
	private static final Set<ParticleType<?>> CAMPFIRE = Set.of(ParticleTypes.CAMPFIRE_COSY_SMOKE, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE);
	private static final Set<ParticleType<?>> POTIONS = Set.of(ParticleTypes.EFFECT, ParticleTypes.ENTITY_EFFECT, ParticleTypes.INSTANT_EFFECT);
	private static final Set<ParticleType<?>> RAIN = Set.of(ParticleTypes.RAIN, ParticleTypes.SPLASH);
	private static final Set<ParticleType<?>> DRIPS = Set.of(
		ParticleTypes.DRIPPING_WATER, ParticleTypes.FALLING_WATER, ParticleTypes.DRIPPING_LAVA, ParticleTypes.FALLING_LAVA, ParticleTypes.LANDING_LAVA,
		ParticleTypes.DRIPPING_HONEY, ParticleTypes.FALLING_HONEY, ParticleTypes.LANDING_HONEY,
		ParticleTypes.DRIPPING_OBSIDIAN_TEAR, ParticleTypes.FALLING_OBSIDIAN_TEAR, ParticleTypes.LANDING_OBSIDIAN_TEAR,
		ParticleTypes.DRIPPING_DRIPSTONE_LAVA, ParticleTypes.FALLING_DRIPSTONE_LAVA, ParticleTypes.DRIPPING_DRIPSTONE_WATER, ParticleTypes.FALLING_DRIPSTONE_WATER
	);
}
