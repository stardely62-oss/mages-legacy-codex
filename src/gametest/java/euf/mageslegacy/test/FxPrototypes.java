package euf.mageslegacy.test;

import com.lowdragmc.lowdraglib.utils.GradientColor;
import com.lowdragmc.photon.client.fx.EntityEffect;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXEffect;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.gameobject.emitter.data.EmissionSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.TextureMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.color.Gradient;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.Circle;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.IShape;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.Sphere;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleConfig;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;
import team.lodestar.lodestone.systems.easing.Easing;
import team.lodestar.lodestone.systems.particle.builder.WorldParticleBuilder;
import team.lodestar.lodestone.systems.particle.data.GenericParticleData;
import team.lodestar.lodestone.systems.particle.data.color.ColorParticleData;
import team.lodestar.lodestone.systems.particle.data.spin.SpinParticleData;
import team.lodestar.lodestone.systems.particle.world.type.LodestoneWorldParticleType;

/**
 * Trial effects for choosing a VFX library: the Tempter's Kiss (a burst of hearts over the kissed, its life flowing to
 * her) and Charm (hearts circling a thrall), built once with Photon and once with Lodestone, for the harness to
 * photograph next to our own renderer. Only the harness uses this; the mod itself doesn't depend on either.
 */
final class FxPrototypes {
	private static final Identifier HEART = new Identifier("mages_legacy", "textures/fx/heart.png");
	private static final Identifier GLOW = new Identifier("mages_legacy", "textures/fx/glow.png");
	private static final Identifier SPARK = new Identifier("mages_legacy", "textures/fx/spark.png");
	private static final int ROSE = 0xFFFF6FA8;
	private static final int PALE = 0xFFFFD6E6;

	/** Effects started here that the harness has to stop later (looping ones). */
	private static final List<FXEffect> RUNNING = new ArrayList<>();

	private FxPrototypes() {
	}

	// ---- Photon: effects built from its own emitters in code ----

	/** A colour fading from full to nothing over a particle's life. */
	private static Gradient fade(float holdUntil) {
		Gradient gradient = new Gradient();
		GradientColor color = gradient.getGradientColor();
		try {
			java.lang.reflect.Field alpha = GradientColor.class.getDeclaredField("aP");
			alpha.setAccessible(true);
			alpha.set(color, new ArrayList<>(List.of(new Vec2f(0, 0), new Vec2f(0.08f, 1), new Vec2f(holdUntil, 1), new Vec2f(1, 0))));
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return gradient;
	}

	private static ParticleEmitter emitter(Identifier texture, int argb, float size, float lifetime, float speed, IShape shape, int burst, float rate,
			int duration) {
		ParticleEmitter emitter = new ParticleEmitter();
		ParticleConfig c = emitter.config;
		c.setDuration(duration);
		c.setLooping(false);
		c.setStartLifetime(NumberFunction.constant(lifetime));
		c.setStartSpeed(NumberFunction.constant(speed));
		c.setStartSize(new NumberFunction3(size, size, size));
		c.setStartColor(NumberFunction.color(argb));
		c.setSimulationSpace(ParticleConfig.Space.World);
		c.emission.setEmissionRate(NumberFunction.constant(rate));
		if (burst > 0) {
			EmissionSetting.Burst b = new EmissionSetting.Burst();
			b.setCount(NumberFunction.constant(burst));
			c.emission.getBursts().add(b);
		}
		c.shape.setShape(shape);
		c.material.setMaterial(new TextureMaterial(texture));
		c.renderer.setBloomEffect(true);
		c.colorOverLifetime.setEnable(true);
		c.colorOverLifetime.setColor(fade(0.55f));
		return emitter;
	}

	private static FX fx(ParticleEmitter... emitters) {
		FX fx = new FX();
		for (ParticleEmitter e : emitters) {
			fx.getMainFX().objects().add(e);
		}
		return fx;
	}

	private static Sphere sphere(float radius) {
		Sphere s = new Sphere();
		s.setRadius(radius);
		return s;
	}

	/** An effect at a point that may move every frame (a stream along a path). */
	private static final class MovingEffect extends FXEffect {
		private final Function<Float, Vec3d> path;
		private final long start;

		MovingEffect(FX fx, World world, Function<Float, Vec3d> path) {
			super(fx, world);
			this.path = path;
			this.start = world.getTime();
		}

		@Override
		public void start() {
			runtime = fx.createRuntime();
			Vec3d p = path.apply(0f);
			runtime.getRoot().updatePos(new Vector3f((float) p.x, (float) p.y, (float) p.z));
			runtime.emmit(this);
		}

		@Override
		public void updateFXObjectFrame(IFXObject fxObject, float partialTicks) {
			if (runtime != null && fxObject == runtime.root) {
				Vec3d p = path.apply(level.getTime() - start + partialTicks);
				runtime.root.updatePos(new Vector3f((float) p.x, (float) p.y, (float) p.z));
			}
		}
	}

	/** The Kiss with Photon: a heart bursts over the kissed in glowing hearts and sparks, and a comet of light flies to her. */
	static void photonKiss(Entity kissed, Entity kisser) {
		World world = kissed.getWorld();
		Vec3d top = kissed.getPos().add(0, kissed.getHeight() + 0.4, 0);
		ParticleEmitter hearts = emitter(HEART, ROSE, 0.35f, 28, 0.9f, sphere(0.35f), 18, 0, 10);
		hearts.config.forceOverLifetime.setEnable(true);
		hearts.config.forceOverLifetime.setForce(new NumberFunction3(0, 0.6, 0));
		ParticleEmitter sparks = emitter(SPARK, PALE, 0.22f, 16, 2.2f, sphere(0.2f), 24, 0, 10);
		ParticleEmitter flash = emitter(GLOW, ROSE, 1.8f, 10, 0, sphere(0.01f), 1, 0, 10);
		MovingEffect burst = new MovingEffect(fx(hearts, sparks, flash), world, t -> top);
		burst.start();
		// The life drawn out: a glowing head trailing hearts and motes, arcing from the kissed to her chest
		ParticleEmitter trail = emitter(GLOW, ROSE, 0.6f, 12, 0, sphere(0.05f), 0, 1.5f, 18);
		ParticleEmitter crumbs = emitter(HEART, PALE, 0.16f, 18, 0.2f, sphere(0.15f), 0, 0.8f, 18);
		Vec3d from = kissed.getPos().add(0, kissed.getHeight() * 0.7, 0);
		MovingEffect stream = new MovingEffect(fx(trail, crumbs), world, t -> {
			Vec3d to = kisser.getPos().add(0, kisser.getHeight() * 0.6, 0);
			double u = Math.min(1, t / 16.0);
			Vec3d apex = from.lerp(to, 0.5).add(0, 1.2, 0);
			return from.multiply((1 - u) * (1 - u)).add(apex.multiply(2 * (1 - u) * u)).add(to.multiply(u * u));
		});
		stream.start();
	}

	/** The Charm with Photon: hearts circling over the thrall, a soft glow under them, for as long as it lasts. */
	static void photonCharm(Entity thrall) {
		ParticleEmitter ring = emitter(HEART, ROSE, 0.25f, 30, 0, circle(0.7f), 0, 0.6f, 100);
		ring.config.velocityOverLifetime.setEnable(true);
		ring.config.velocityOverLifetime.setOrbital(new NumberFunction3(0, 2.5, 0));
		ring.config.setSimulationSpace(ParticleConfig.Space.Local);
		ParticleEmitter halo = emitter(GLOW, ROSE, 1.2f, 20, 0, sphere(0.01f), 0, 0.25f, 100);
		halo.config.setSimulationSpace(ParticleConfig.Space.Local);
		EntityEffect effect = new EntityEffect(fx(ring, halo), thrall.getWorld(), thrall, EntityEffect.AutoRotate.NONE);
		effect.setOffset(0, thrall.getHeight() + 0.5, 0);
		effect.start();
		RUNNING.add(effect);
	}

	private static Circle circle(float radius) {
		Circle c = new Circle();
		c.setRadius(radius);
		c.setRadiusThickness(0);
		return c;
	}

	static void stopPhoton() {
		for (FXEffect e : RUNNING) {
			if (e.getRuntime() != null) {
				e.getRuntime().destroy(true);
			}
		}
		RUNNING.clear();
	}

	// ---- Lodestone: Malum's particle builder ----

	/**
	 * WorldParticleBuilder.create for a particle type, called by reflection: its other overloads name Porting Lib's
	 * classes, which javac would have to see to choose between them.
	 */
	private static WorldParticleBuilder builder(LodestoneWorldParticleType type) {
		try {
			return (WorldParticleBuilder) WorldParticleBuilder.class.getMethod("create", LodestoneWorldParticleType.class).invoke(null, type);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static LodestoneWorldParticleType type(String name) {
		return (LodestoneWorldParticleType) Registries.PARTICLE_TYPE.get(new Identifier("lodestone", name));
	}

	private static final Color ROSE_C = new Color(255, 111, 168);
	private static final Color WINE_C = new Color(150, 20, 70);
	private static final Color PALE_C = new Color(255, 220, 235);

	/** The Kiss with Lodestone: a bloom of wisps and twinkles over the kissed, and a line of light to her. */
	static void lodestoneKiss(Entity kissed, Entity kisser) {
		World world = kissed.getWorld();
		Vec3d top = kissed.getPos().add(0, kissed.getHeight() + 0.4, 0);
		builder(type("wisp"))
				.setScaleData(GenericParticleData.create(0.45f, 0f).setEasing(Easing.QUARTIC_OUT).build())
				.setTransparencyData(GenericParticleData.create(0.9f, 0f).setEasing(Easing.QUAD_IN).build())
				.setColorData(ColorParticleData.create(ROSE_C, WINE_C).setCoefficient(1.4f).build())
				.setSpinData(SpinParticleData.create(0.2f, 0.4f).build())
				.setLifetime(30)
				.setRandomMotion(0.08f, 0.08f)
				.setRandomOffset(0.3f)
				.enableNoClip()
				.repeat(world, top.x, top.y, top.z, 30);
		builder(type("twinkle"))
				.setScaleData(GenericParticleData.create(0.3f, 0f).build())
				.setTransparencyData(GenericParticleData.create(1f, 0f).build())
				.setColorData(ColorParticleData.create(PALE_C, ROSE_C).build())
				.setLifetime(20)
				.setRandomMotion(0.15f, 0.15f)
				.enableNoClip()
				.repeat(world, top.x, top.y, top.z, 16);
		Vec3d from = kissed.getPos().add(0, kissed.getHeight() * 0.7, 0);
		Vec3d to = kisser.getPos().add(0, kisser.getHeight() * 0.6, 0);
		for (int i = 0; i <= 16; i++) {
			double u = i / 16.0;
			Vec3d apex = from.lerp(to, 0.5).add(0, 1.2, 0);
			Vec3d p = from.multiply((1 - u) * (1 - u)).add(apex.multiply(2 * (1 - u) * u)).add(to.multiply(u * u));
			builder(type("wisp"))
					.setScaleData(GenericParticleData.create(0.25f, 0.05f).build())
					.setTransparencyData(GenericParticleData.create(0.8f, 0f).build())
					.setColorData(ColorParticleData.create(ROSE_C, PALE_C).build())
					.setLifetime(14 + i)
					.setLifeDelay(i)
					.enableNoClip()
					.spawn(world, p.x, p.y, p.z);
		}
	}

	/** The Charm with Lodestone: a ring of star motes turning over the thrall. Spawned again each tick by the harness. */
	static void lodestoneCharmTick(Entity thrall, long tick) {
		World world = thrall.getWorld();
		Vec3d c = thrall.getPos().add(0, thrall.getHeight() + 0.5, 0);
		for (int i = 0; i < 3; i++) {
			double a = tick * 0.25 + i * Math.PI * 2 / 3;
			builder(type("star"))
					.setScaleData(GenericParticleData.create(0.2f, 0f).build())
					.setTransparencyData(GenericParticleData.create(0.9f, 0f).build())
					.setColorData(ColorParticleData.create(ROSE_C, WINE_C).build())
					.setSpinData(SpinParticleData.create(0.3f).build())
					.setLifetime(16)
					.enableNoClip()
					.spawn(world, c.x + Math.cos(a) * 0.7, c.y, c.z + Math.sin(a) * 0.7);
		}
		builder(type("wisp"))
				.setScaleData(GenericParticleData.create(0.6f, 0f).build())
				.setTransparencyData(GenericParticleData.create(0.25f, 0f).build())
				.setColorData(ColorParticleData.create(ROSE_C, WINE_C).build())
				.setLifetime(10)
				.enableNoClip()
				.spawn(world, c.x, c.y, c.z);
	}

	static boolean photonLoaded() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("photon");
	}

	static boolean lodestoneLoaded() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("lodestone");
	}

	static World world() {
		return MinecraftClient.getInstance().world;
	}
}
