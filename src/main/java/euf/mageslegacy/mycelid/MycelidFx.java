package euf.mageslegacy.mycelid;

import euf.mageslegacy.fx.ModParticles;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * How the Mycelid abilities look: the body crumbling into spores, a glowing arc for the jump, rings that spread over
 * the ground, a spore vortex in the cloud, a node growing in a spiral. Vanilla particles only (coloured dust that
 * fades from spore green to rot violet), sent from the server so everyone around sees them.
 */
public final class MycelidFx {
	private static final Vector3f SPORE_GREEN = new Vector3f(0.61f, 1.0f, 0.54f);
	private static final Vector3f MOSS = new Vector3f(0.50f, 0.69f, 0.41f);
	private static final Vector3f ROT_VIOLET = new Vector3f(0.42f, 0.28f, 0.42f);
	private static final Vector3f BONE = new Vector3f(0.88f, 0.84f, 0.74f);

	/** Bright spores that wither to violet as they fall. */
	/** Glowing spores (the race's own particle): they float up, sway and burst. */
	public static final ParticleOptions SPORE = ModParticles.SPORE.get();
	public static final ParticleOptions SPORE_BIG = ModParticles.SPORE.get();
	public static final ParticleOptions MOSS_DUST = new DustParticleOptions(MOSS, 1.2f);
	public static final ParticleOptions BONE_DUST = new DustParticleOptions(BONE, 0.8f);

	/** A ring that spreads over the ground for a few ticks. */
	private record Ring(ServerLevel world, Vec3 center, double from, double to, long start, int ticks, ParticleOptions effect) {}

	private static final List<Ring> RINGS = new ArrayList<>();

	private MycelidFx() {
	}

	public static void reset() {
		RINGS.clear();
	}

	private static void point(ServerLevel world, ParticleOptions effect, double x, double y, double z) {
		world.sendParticles(effect, x, y, z, 1, 0, 0, 0, 0);
	}

	/** A spreading ring: from radius {@code from} to {@code to} over {@code ticks}. */
	public static void ring(ServerLevel world, Vec3 center, double from, double to, int ticks, ParticleOptions effect) {
		RINGS.add(new Ring(world, center, from, to, world.getServer().getTickCount(), ticks, effect));
	}

	/** A still circle of points, like a mark on the ground. */
	public static void circle(ServerLevel world, Vec3 center, double radius, int points, ParticleOptions effect) {
		for (int i = 0; i < points; i++) {
			double a = i * Math.PI * 2 / points;
			point(world, effect, center.x + Math.cos(a) * radius, center.y + 0.1, center.z + Math.sin(a) * radius);
		}
	}

	/** The body crumbles into spores that swirl up: the start of a jump. */
	public static void crumble(ServerLevel world, Vec3 feet) {
		for (int i = 0; i < 28; i++) {
			double h = i / 28.0 * 1.9;
			double a = i * 0.9;
			double r = 0.45 - h * 0.12;
			point(world, i % 3 == 0 ? BONE_DUST : SPORE, feet.x + Math.cos(a) * r, feet.y + h, feet.z + Math.sin(a) * r);
		}
		world.sendParticles(ParticleTypes.MYCELIUM, feet.x, feet.y + 1, feet.z, 25, 0.3, 0.6, 0.3, 0.02);
	}

	/** The spore's flight: a high arc from start to landing, drawn up to {@code progress} (0..1). */
	public static void arc(ServerLevel world, Vec3 from, Vec3 to, double progress) {
		double height = 1.2 + from.distanceTo(to) * 0.15;
		int steps = 6;
		for (int i = 0; i < steps; i++) {
			double t = Math.max(0, progress - i * 0.03);
			Vec3 p = from.lerp(to, t).add(0, 1.0 + Math.sin(t * Math.PI) * height, 0);
			point(world, i == 0 ? SPORE_BIG : SPORE, p.x, p.y, p.z);
		}
	}

	/** Where the spore will sprout: a small glowing ring on the ground, so others see where you'll appear. */
	public static void landingMark(ServerLevel world, Vec3 feet, double pulse) {
		circle(world, feet, 0.5 + pulse * 0.3, 10, MOSS_DUST);
	}

	/** Sprouting at the landing: a burst of spores and a ring spreading over the ground. */
	public static void sprout(ServerLevel world, Vec3 feet) {
		world.sendParticles(SPORE_BIG, feet.x, feet.y + 0.6, feet.z, 18, 0.25, 0.5, 0.25, 0);
		world.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, feet.x, feet.y + 1, feet.z, 25, 0.4, 0.8, 0.4, 0.02);
		ring(world, feet, 0.3, 2.2, 8, SPORE);
	}

	/** The cloud's birth: a shockwave over the ground out to its edge. */
	public static void cloudBurst(ServerLevel world, Vec3 center, double radius) {
		ring(world, center, 0.5, radius, 10, SPORE_BIG);
		ring(world, center.add(0, 0.6, 0), 0.3, radius * 0.8, 12, MOSS_DUST);
		world.sendParticles(SPORE_BIG, center.x, center.y + 1, center.z, 30, 0.6, 0.6, 0.6, 0.05);
	}

	/** Inside a living cloud: two arms of spores slowly turning, rising and falling like breath. */
	public static void vortex(ServerLevel world, Vec3 center, double radius, long age) {
		for (int arm = 0; arm < 2; arm++) {
			for (int i = 0; i < 6; i++) {
				double r = radius * (0.2 + i * 0.14);
				double a = age * 0.12 + arm * Math.PI + i * 0.45;
				double y = 0.4 + 0.5 * Math.sin(age * 0.08 + i);
				point(world, i % 2 == 0 ? SPORE : MOSS_DUST, center.x + Math.cos(a) * r, center.y + y, center.z + Math.sin(a) * r);
			}
		}
	}

	/** A node grows: threads of mycelium spiral up out of the ground, then a ring spreads. */
	public static void nodeGrow(ServerLevel world, Vec3 bottom) {
		for (int i = 0; i < 30; i++) {
			double h = i / 30.0 * 1.4;
			double a = i * 0.7;
			double r = 0.6 - h * 0.3;
			point(world, SPORE, bottom.x + Math.cos(a) * r, bottom.y + h, bottom.z + Math.sin(a) * r);
			point(world, MOSS_DUST, bottom.x - Math.cos(a) * r, bottom.y + h, bottom.z - Math.sin(a) * r);
		}
		ring(world, bottom, 0.4, 2.5, 10, MOSS_DUST);
	}

	/** Leaving or arriving through a node: a column of spores. */
	public static void column(ServerLevel world, Vec3 feet) {
		for (int i = 0; i < 16; i++) {
			double a = i * 1.3;
			point(world, SPORE_BIG, feet.x + Math.cos(a) * 0.35, feet.y + i * 0.14, feet.z + Math.sin(a) * 0.35);
		}
		world.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, feet.x, feet.y + 1, feet.z, 20, 0.3, 0.8, 0.3, 0.02);
	}

	/** Spore Burst: a hard ring that blows outward. */
	public static void blast(ServerLevel world, Vec3 center, double radius) {
		ring(world, center.add(0, 0.3, 0), 0.5, radius + 1, 5, SPORE_BIG);
		world.sendParticles(ParticleTypes.SNEEZE, center.x, center.y + 1, center.z, 20, 0.5, 0.3, 0.5, 0.2);
	}

	/** Rebirth: the body bursts into spores where it fell and grows back at the node. */
	public static void rebirth(ServerLevel world, Vec3 from, Vec3 to) {
		world.sendParticles(SPORE_BIG, from.x, from.y + 1, from.z, 50, 0.4, 0.8, 0.4, 0.05);
		nodeGrow(world, to);
		column(world, to);
	}

	public static void tick(long now) {
		for (Iterator<Ring> it = RINGS.iterator(); it.hasNext(); ) {
			Ring ring = it.next();
			long age = now - ring.start();
			if (age > ring.ticks()) {
				it.remove();
				continue;
			}
			double r = ring.from() + (ring.to() - ring.from()) * age / ring.ticks();
			circle(ring.world(), ring.center(), r, (int) Math.max(10, r * 9), ring.effect());
		}
	}
}
