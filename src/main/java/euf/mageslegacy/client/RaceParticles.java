package euf.mageslegacy.client;

import euf.mageslegacy.fx.ModParticles;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/** How each of the races' particles moves and looks (see {@link ModParticles}). */
public final class RaceParticles {
	private static final int FULL_BRIGHT = 0xF000F0;
	/** The bat frames, kept for the swarm's bats, which the client makes itself to circle a body (see {@link #swarmBat}). */
	private static SpriteSet batSprites;

	private RaceParticles() {
	}

	public static void register() {
		ParticleFactoryRegistry registry = ParticleFactoryRegistry.getInstance();
		registry.register(ModParticles.SAND.get(), sprites -> (type, world, x, y, z, vx, vy, vz) -> new Sand(world, x, y, z, vx, vy, vz, sprites, false));
		registry.register(ModParticles.STILL_SAND.get(), sprites -> (type, world, x, y, z, vx, vy, vz) -> new Sand(world, x, y, z, vx, vy, vz, sprites, true));
		registry.register(ModParticles.ICE_SHARD.get(), sprites -> (type, world, x, y, z, vx, vy, vz) -> new Shard(world, x, y, z, vx, vy, vz, sprites));
		registry.register(ModParticles.SPORE.get(), sprites -> (type, world, x, y, z, vx, vy, vz) -> new Spore(world, x, y, z, vx, vy, vz, sprites));
		registry.register(ModParticles.WISP.get(), sprites -> (type, world, x, y, z, vx, vy, vz) -> new Wisp(world, x, y, z, vx, vy, vz, sprites));
		registry.register(ModParticles.MOTE.get(), sprites -> (type, world, x, y, z, vx, vy, vz) -> new Mote(world, x, y, z, vx, vy, vz, sprites));
		registry.register(ModParticles.BLOOD.get(), sprites -> (type, world, x, y, z, vx, vy, vz) -> new Drop(world, x, y, z, vx, vy, vz, sprites));
		registry.register(ModParticles.BLOOD_MOTE.get(), sprites -> (type, world, x, y, z, vx, vy, vz) -> new Mote(world, x, y, z, vx, vy, vz, sprites));
		registry.register(ModParticles.EMBER.get(), sprites -> (type, world, x, y, z, vx, vy, vz) -> new Ember(world, x, y, z, vx, vy, vz, sprites));
		registry.register(ModParticles.BLOOD_MIST.get(), sprites -> (type, world, x, y, z, vx, vy, vz) -> new Mist(world, x, y, z, vx, vy, vz, sprites));
		registry.register(ModParticles.VOID_MOTE.get(), sprites -> (type, world, x, y, z, vx, vy, vz) -> new VoidMote(world, x, y, z, vx, vy, vz, sprites));
		registry.register(ModParticles.STAR.get(), sprites -> (type, world, x, y, z, vx, vy, vz) -> new Star(world, x, y, z, vx, vy, vz, sprites));
		registry.register(ModParticles.BAT.get(), sprites -> {
			batSprites = sprites;
			return (type, world, x, y, z, vx, vy, vz) -> new Bat(world, x, y, z, vx, vy, vz, sprites, null);
		});
	}

	/** Fades in over the first few ticks and out over the last third. */
	private static float lifeAlpha(int age, int maxAge, float fadeIn) {
		float in = fadeIn <= 0 ? 1 : Math.min(1, age / fadeIn);
		float out = Math.min(1, (maxAge - age) / (maxAge / 3f));
		return Mth.clamp(Math.min(in, out), 0, 1);
	}

	/** A grain of sand: tiny, falls and drifts; a still one hangs in the air and glints. */
	static final class Sand extends TextureSheetParticle {
		private final boolean still;

		Sand(ClientLevel world, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites, boolean still) {
			super(world, x, y, z);
			this.still = still;
			setSprite(sprites.get(random));
			xd = still ? 0 : vx + (random.nextDouble() - 0.5) * 0.02;
			yd = still ? 0 : vy;
			zd = still ? 0 : vz + (random.nextDouble() - 0.5) * 0.02;
			gravity = still ? 0 : 0.35f;
			friction = 0.92f;
			quadSize = 0.05f + random.nextFloat() * 0.04f;
			lifetime = still ? 60 + random.nextInt(40) : 20 + random.nextInt(20);
			float tint = 0.85f + random.nextFloat() * 0.15f;
			setColor(tint, tint, tint);
		}

		@Override
		public void tick() {
			super.tick();
			alpha = lifeAlpha(age, lifetime, 4);
			if (still) {
				// A slow glint, each grain on its own beat
				float glint = 0.75f + 0.25f * Mth.sin((age + hashCode() % 20) * 0.35f);
				setColor(glint, glint, glint);
			}
		}

		@Override
		public int getLightColor(float tint) {
			return still ? FULL_BRIGHT : super.getLightColor(tint);
		}

		@Override
		public ParticleRenderType getRenderType() {
			return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
		}
	}

	/** A shard of ice: tumbles as it falls, catching the light. */
	static final class Shard extends TextureSheetParticle {
		private final float spin;

		Shard(ClientLevel world, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
			super(world, x, y, z, vx, vy, vz);
			setSprite(sprites.get(random));
			xd = vx + (random.nextDouble() - 0.5) * 0.06;
			yd = vy + 0.02 + random.nextDouble() * 0.04;
			zd = vz + (random.nextDouble() - 0.5) * 0.06;
			gravity = 0.6f;
			friction = 0.94f;
			quadSize = 0.08f + random.nextFloat() * 0.07f;
			lifetime = 18 + random.nextInt(16);
			spin = (random.nextFloat() - 0.5f) * 0.5f;
			roll = random.nextFloat() * Mth.TWO_PI;
			oRoll = roll;
		}

		@Override
		public void tick() {
			super.tick();
			oRoll = roll;
			if (!onGround) {
				roll += spin;
			}
			alpha = lifeAlpha(age, lifetime, 0);
		}

		@Override
		public int getLightColor(float tint) {
			// Ice keeps a little of its own light
			return Math.max(super.getLightColor(tint), 0xB000B0);
		}

		@Override
		public ParticleRenderType getRenderType() {
			return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
		}
	}

	/** A spore: floats up, swaying; swells as it ages and bursts into a ring at the end. Glows faintly. */
	static final class Spore extends TextureSheetParticle {
		private final SpriteSet sprites;
		private final float sway;

		Spore(ClientLevel world, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
			super(world, x, y, z);
			this.sprites = sprites;
			xd = vx * 0.5;
			yd = vy * 0.5 + 0.008 + random.nextDouble() * 0.01;
			zd = vz * 0.5;
			gravity = -0.02f;
			friction = 0.96f;
			quadSize = 0.07f + random.nextFloat() * 0.06f;
			lifetime = 40 + random.nextInt(40);
			sway = random.nextFloat() * Mth.TWO_PI;
			setSpriteFromAge(sprites);
		}

		@Override
		public void tick() {
			super.tick();
			xd += Mth.sin(age * 0.15f + sway) * 0.002;
			zd += Mth.cos(age * 0.13f + sway) * 0.002;
			setSpriteFromAge(sprites);
			alpha = lifeAlpha(age, lifetime, 6) * 0.9f;
		}

		@Override
		public int getLightColor(float tint) {
			return FULL_BRIGHT;
		}

		@Override
		public ParticleRenderType getRenderType() {
			return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
		}
	}

	/** A golden mote of the Song: carried along its velocity with a gentle wave, glowing warm, pulsing as it goes. */
	static final class Mote extends TextureSheetParticle {
		private final SpriteSet sprites;
		private final float phase;

		Mote(ClientLevel world, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
			super(world, x, y, z);
			this.sprites = sprites;
			xd = vx;
			yd = vy;
			zd = vz;
			gravity = 0;
			friction = 0.9f;
			quadSize = 0.08f + random.nextFloat() * 0.05f;
			lifetime = 26 + random.nextInt(14);
			phase = random.nextFloat() * Mth.TWO_PI;
			setSpriteFromAge(sprites);
		}

		@Override
		public void tick() {
			super.tick();
			yd += Mth.sin(age * 0.4f + phase) * 0.004;
			setSpriteFromAge(sprites);
			alpha = lifeAlpha(age, lifetime, 4);
		}

		@Override
		public int getLightColor(float tint) {
			return FULL_BRIGHT;
		}

		@Override
		public ParticleRenderType getRenderType() {
			return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
		}
	}

	/** A drop of blood: flung out, falls, and lies a moment on the ground as a small stain before it fades. */
	static final class Drop extends TextureSheetParticle {
		private final SpriteSet sprites;
		private boolean landed;

		Drop(ClientLevel world, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
			super(world, x, y, z);
			this.sprites = sprites;
			xd = vx + (random.nextDouble() - 0.5) * 0.08;
			yd = vy + 0.05 + random.nextDouble() * 0.08;
			zd = vz + (random.nextDouble() - 0.5) * 0.08;
			gravity = 1.0f;
			friction = 0.96f;
			quadSize = 0.06f + random.nextFloat() * 0.06f;
			lifetime = 30 + random.nextInt(20);
			setSprite(sprites.get(random.nextInt(2), 2));
		}

		@Override
		public void tick() {
			super.tick();
			if (onGround && !landed) {
				landed = true;
				setSprite(sprites.get(2, 2));
				quadSize *= 1.6f;
				lifetime = Math.min(lifetime, age + 25);
			}
			alpha = lifeAlpha(age, lifetime, 0);
		}

		@Override
		public ParticleRenderType getRenderType() {
			return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
		}
	}

	/** A spirit wisp: rises, flickering between its frames, glowing; fades as it goes up. */
	static final class Wisp extends TextureSheetParticle {
		private final SpriteSet sprites;

		Wisp(ClientLevel world, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
			super(world, x, y, z);
			this.sprites = sprites;
			xd = vx * 0.4 + (random.nextDouble() - 0.5) * 0.01;
			yd = vy * 0.4 + 0.03 + random.nextDouble() * 0.02;
			zd = vz * 0.4 + (random.nextDouble() - 0.5) * 0.01;
			gravity = -0.01f;
			friction = 0.95f;
			quadSize = 0.12f + random.nextFloat() * 0.08f;
			lifetime = 24 + random.nextInt(16);
			setSprite(sprites.get(random));
		}

		@Override
		public void tick() {
			super.tick();
			if (age % 3 == 0) {
				setSprite(sprites.get(random));
			}
			alpha = lifeAlpha(age, lifetime, 3) * 0.95f;
		}

		@Override
		public int getLightColor(float tint) {
			return FULL_BRIGHT;
		}

		@Override
		public ParticleRenderType getRenderType() {
			return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
		}
	}

	/**
	 * A bat that circles {@code around} (a Bloodkin scattered into the swarm) for {@code ticks} instead of flying off,
	 * or null if the sprites aren't loaded.
	 */
	@Nullable
	public static Particle swarmBat(ClientLevel world, Entity around, int ticks) {
		if (batSprites == null) {
			return null;
		}
		Bat bat = new Bat(world, around.getX(), around.getY() + 1, around.getZ(), 0, 0, 0, batSprites, around);
		bat.setLifetime(ticks);
		return bat;
	}

	/** A crimson ember: rises swirling off a body, glowing, and shrinks away. */
	static final class Ember extends TextureSheetParticle {
		private final SpriteSet sprites;
		private final float phase;
		private final float startScale;

		Ember(ClientLevel world, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
			super(world, x, y, z);
			this.sprites = sprites;
			xd = vx + (random.nextDouble() - 0.5) * 0.02;
			yd = vy + 0.03 + random.nextDouble() * 0.03;
			zd = vz + (random.nextDouble() - 0.5) * 0.02;
			gravity = -0.04f;
			friction = 0.93f;
			startScale = 0.06f + random.nextFloat() * 0.06f;
			quadSize = startScale;
			lifetime = 16 + random.nextInt(14);
			phase = random.nextFloat() * Mth.TWO_PI;
			setSpriteFromAge(sprites);
		}

		@Override
		public void tick() {
			super.tick();
			xd += Mth.sin(age * 0.5f + phase) * 0.004;
			zd += Mth.cos(age * 0.5f + phase) * 0.004;
			setSpriteFromAge(sprites);
			quadSize = startScale * (1 - (float) age / lifetime * 0.7f);
			alpha = lifeAlpha(age, lifetime, 2);
		}

		@Override
		public int getLightColor(float tint) {
			return FULL_BRIGHT;
		}

		@Override
		public ParticleRenderType getRenderType() {
			return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
		}
	}

	/** A puff of dark blood mist: thrown out, slows, swells and thins to nothing. */
	static final class Mist extends TextureSheetParticle {
		private final float startScale;

		Mist(ClientLevel world, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
			super(world, x, y, z);
			setSprite(sprites.get(random));
			xd = vx;
			yd = vy + 0.005;
			zd = vz;
			gravity = -0.005f;
			friction = 0.86f;
			startScale = 0.35f + random.nextFloat() * 0.3f;
			quadSize = startScale;
			lifetime = 24 + random.nextInt(16);
			roll = random.nextFloat() * Mth.TWO_PI;
			oRoll = roll;
		}

		@Override
		public void tick() {
			super.tick();
			oRoll = roll;
			roll += 0.03f;
			quadSize = startScale * (1 + (float) age / lifetime * 1.2f);
			alpha = lifeAlpha(age, lifetime, 3) * 0.8f;
		}

		@Override
		public ParticleRenderType getRenderType() {
			return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
		}
	}

	/**
	 * A bat: beats its wings and flies along its velocity, weaving a little; one of a swarm instead circles its
	 * Bloodkin on a tilted orbit of its own, darting in and out, until the swarm gathers back into a body.
	 */
	static final class Bat extends TextureSheetParticle {
		private final SpriteSet sprites;
		@Nullable
		private final Entity around;
		private final float orbit;
		private final float speed;
		private final float tilt;
		private final float height;
		private final int beat;
		private float turn;

		Bat(ClientLevel world, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites, @Nullable Entity around) {
			super(world, x, y, z);
			this.sprites = sprites;
			this.around = around;
			xd = vx;
			yd = vy;
			zd = vz;
			gravity = 0;
			friction = 0.98f;
			hasPhysics = false;
			quadSize = 0.14f + random.nextFloat() * 0.08f;
			lifetime = 18 + random.nextInt(14);
			orbit = 0.9f + random.nextFloat() * 1.3f;
			speed = (0.18f + random.nextFloat() * 0.16f) * (random.nextBoolean() ? 1 : -1);
			tilt = (random.nextFloat() - 0.5f) * 1.2f;
			height = 0.3f + random.nextFloat() * 1.6f;
			turn = random.nextFloat() * Mth.TWO_PI;
			beat = random.nextInt(3);
			setSprite(sprites.get(beat, 2));
		}

		@Override
		public void tick() {
			if (around != null) {
				// Gone when the swarm gathers back into a body
				if (around.isRemoved() || age++ >= lifetime) {
					remove();
					return;
				}
				xo = x;
				yo = y;
				zo = z;
				turn += speed;
				float r = orbit * (0.85f + 0.25f * Mth.sin(age * 0.21f + tilt * 5));
				double tx = around.getX() + Mth.cos(turn) * r;
				double tz = around.getZ() + Mth.sin(turn) * r;
				double ty = around.getY() + height + Mth.sin(turn) * tilt + 0.15 * Mth.sin(age * 0.6f);
				// Chases its place on the orbit rather than snapping to it, so the swarm trails behind a fast body
				setPos(x + (tx - x) * 0.45, y + (ty - y) * 0.45, z + (tz - z) * 0.45);
			} else {
				super.tick();
				yd += Mth.sin(age * 0.7f) * 0.01;
				alpha = lifeAlpha(age, lifetime, 0);
			}
			// The wings beat up, level, down, level
			int frame = (age / 2 + beat) % 4;
			setSprite(sprites.get(frame == 3 ? 1 : frame, 2));
		}

		@Override
		public ParticleRenderType getRenderType() {
			return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
		}
	}

	/** A mote of the void: flies along its velocity (or drifts), dark with a lit rim, and dwindles to nothing. */
	static final class VoidMote extends TextureSheetParticle {
		private final float startScale;

		VoidMote(ClientLevel world, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
			super(world, x, y, z);
			setSprite(sprites.get(random));
			xd = vx + (random.nextDouble() - 0.5) * 0.01;
			yd = vy + (random.nextDouble() - 0.5) * 0.01;
			zd = vz + (random.nextDouble() - 0.5) * 0.01;
			gravity = 0;
			friction = 0.9f;
			hasPhysics = false;
			startScale = 0.08f + random.nextFloat() * 0.08f;
			quadSize = startScale;
			lifetime = 14 + random.nextInt(12);
		}

		@Override
		public void tick() {
			super.tick();
			quadSize = startScale * (1 - (float) age / lifetime);
			alpha = lifeAlpha(age, lifetime, 1);
		}

		@Override
		public int getLightColor(float tint) {
			return FULL_BRIGHT;
		}

		@Override
		public ParticleRenderType getRenderType() {
			return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
		}
	}

	/** A star: hangs almost still, swelling to a glint and back on its own beat. */
	static final class Star extends TextureSheetParticle {
		private final SpriteSet sprites;
		private final float beat;

		Star(ClientLevel world, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
			super(world, x, y, z);
			this.sprites = sprites;
			xd = vx * 0.2;
			yd = vy * 0.2 + 0.002;
			zd = vz * 0.2;
			gravity = 0;
			friction = 0.95f;
			hasPhysics = false;
			quadSize = 0.06f + random.nextFloat() * 0.05f;
			lifetime = 20 + random.nextInt(30);
			beat = random.nextFloat() * Mth.TWO_PI;
			setSprite(sprites.get(0, 2));
		}

		@Override
		public void tick() {
			super.tick();
			float glint = 0.5f + 0.5f * Mth.sin(age * 0.45f + beat);
			setSprite(sprites.get(Math.round(glint * 2), 2));
			alpha = lifeAlpha(age, lifetime, 3);
		}

		@Override
		public int getLightColor(float tint) {
			return FULL_BRIGHT;
		}

		@Override
		public ParticleRenderType getRenderType() {
			return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
		}
	}
}
