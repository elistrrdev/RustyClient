package com.rustyclient.module.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rustyclient.core.Category;
import com.rustyclient.core.Module;
import com.rustyclient.core.Modules;
import com.rustyclient.core.setting.BoolSetting;
import com.rustyclient.core.setting.NumberSetting;
import com.rustyclient.platform.Compat;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

public final class ItemPhysics extends Module {
	private static final String AIR = "In the air";
	private static final float AIR_DEGREES_PER_TICK = 16f;
	private static final Map<Integer, Tumble> TUMBLE = new HashMap<>();
	private static long lastCleanup;

	public final BoolSetting flatBlocks = bool("Lay blocks flat", GENERAL, false);
	public final BoolSetting randomRotation = bool("Random ground rotation", GENERAL, true);
	public final NumberSetting itemSize = number("Item size", APPEARANCE, 1, 0.5, 2, 0.05, NumberSetting.times());
	public final BoolSetting airRotation = bool("Rotate in the air", AIR, false);
	public final NumberSetting airSpeed = number("Rotation speed", AIR, 1, 0.1, 3, 0.05, NumberSetting.times());
	public final BoolSetting followSpeed = bool("Faster when moving fast", AIR, true);
	public final BoolSetting slowInWater = bool("Slower in water", AIR, true);
	public final NumberSetting settleSpeed = number("Landing speed", AIR, 1, 0.2, 3, 0.05, NumberSetting.times());

	public ItemPhysics() {
		super("Item Physics", "package-open", Category.VISUAL, false);
		airSpeed.visibleWhen(airRotation::on);
		followSpeed.visibleWhen(airRotation::on);
		slowInWater.visibleWhen(airRotation::on);
		settleSpeed.visibleWhen(airRotation::on);
	}

	public interface Holder {
		void rusty$motion(int id, boolean ground, boolean water, float speed);

		int rusty$id();

		boolean rusty$ground();

		boolean rusty$water();

		float rusty$speed();
	}

	public static void capture(ItemEntity entity, ItemEntityRenderState state) {
		if (state instanceof Holder holder) {
			Vec3 motion = entity.getDeltaMovement();
			holder.rusty$motion(entity.getId(), entity.onGround(), entity.isInWater(), (float) motion.length());
		}
	}

	public static void apply(ItemEntityRenderState state, PoseStack pose) {
		ItemPhysics physics = Modules.get(ItemPhysics.class);
		if (physics == null || !physics.enabled()) {
			return;
		}
		physics.transform(state, pose);
	}

	private void transform(ItemEntityRenderState state, PoseStack pose) {
		float spin = ItemEntity.getSpin(state.ageInTicks, state.bobOffset);
		float bob = Mth.sin(state.ageInTicks / 10.0F + state.bobOffset) * 0.1F + 0.1F;
		Compat.rotate(pose, Axis.YP.rotation(-spin));
		pose.translate(0f, -bob, 0f);
		AABB box = state.item.getModelBoundingBox();
		boolean block = box.getYsize() > 0.3;
		boolean flat = !block || flatBlocks.on();
		float yaw = randomRotation.on() ? (state.seed & 0xFFFF) % 360 : 0f;
		float k = itemSize.f();
		float lift = (float) -box.minY + 0.0625F;
		float tumble = airRotation.on() ? tumble(state, flat ? 180f : 90f) : 0f;
		if (!airRotation.on() || Math.abs(tumble) < 0.01f) {
			if (!flat) {
				pose.translate(0f, -lift + lift * k, 0f);
				Compat.rotate(pose, Axis.YP.rotationDegrees(yaw));
				scale(pose, k);
				return;
			}
			pose.translate(0f, -lift + (float) box.getZsize() / 2f * k + 0.002f, 0f);
			Compat.rotate(pose, Axis.XP.rotationDegrees(90f));
			Compat.rotate(pose, Axis.ZP.rotationDegrees(yaw));
			scale(pose, k);
			return;
		}
		float rad = tumble * Mth.DEG_TO_RAD;
		float cos = Math.abs(Mth.cos(rad));
		float sin = Math.abs(Mth.sin(rad));
		float half;
		if (flat) {
			half = ((float) box.getZsize() / 2f * cos + (float) box.getYsize() / 2f * sin) * k + 0.002f;
		} else {
			half = ((float) box.getYsize() / 2f * cos + (float) box.getZsize() / 2f * sin) * k + 0.0625f;
		}
		float center = flat ? 0f : (float) (box.minY + box.maxY) / 2f * k;
		pose.translate(0f, -lift + half, 0f);
		Compat.rotate(pose, Axis.YP.rotationDegrees(flat ? -yaw : yaw));
		Compat.rotate(pose, Axis.XP.rotationDegrees((flat ? 90f : 0f) + tumble));
		pose.translate(0f, -center, 0f);
		scale(pose, k);
	}

	private static void scale(PoseStack pose, float k) {
		if (Math.abs(k - 1f) > 0.001f) {
			pose.scale(k, k, k);
		}
	}

	private static final class Tumble {
		float angle;
		float age;
		long seen;
	}

	private float tumble(ItemEntityRenderState state, float step) {
		if (!(state instanceof Holder holder)) {
			return 0f;
		}
		long now = System.currentTimeMillis();
		cleanup(now);
		Tumble t = TUMBLE.get(holder.rusty$id());
		if (t == null) {
			t = new Tumble();
			t.age = state.ageInTicks;
			TUMBLE.put(holder.rusty$id(), t);
		}
		float dt = Mth.clamp(state.ageInTicks - t.age, 0f, 5f);
		t.age = state.ageInTicks;
		t.seen = now;
		if (dt <= 0f) {
			return t.angle;
		}
		if (!holder.rusty$ground()) {
			float rate = AIR_DEGREES_PER_TICK * airSpeed.f();
			if (followSpeed.on()) {
				rate *= Mth.clamp(holder.rusty$speed() * 4f, 0.35f, 2.5f);
			}
			if (slowInWater.on() && holder.rusty$water()) {
				rate *= 0.3f;
			}
			t.angle = (t.angle + rate * dt) % 360f;
		} else {
			float rest = Math.round(t.angle / step) * step;
			float ease = 1f - (float) Math.exp(-0.35f * settleSpeed.f() * dt);
			t.angle += (rest - t.angle) * ease;
			if (Math.abs(rest - t.angle) < 0.05f) {
				t.angle = rest % 360f;
			}
		}
		return t.angle;
	}

	private static void cleanup(long now) {
		if (now - lastCleanup < 2000) {
			return;
		}
		lastCleanup = now;
		TUMBLE.values().removeIf(t -> now - t.seen > 3000);
	}
}
