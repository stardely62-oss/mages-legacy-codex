package euf.mageslegacy.client;

import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.fx.ModParticles;
import euf.mageslegacy.voidborn.Voidborn;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;

/**
 * Draws the ability effects the server announces (see {@link Fx}) as glowing additive geometry: spreading rings,
 * domes with a bright rim and turning bands, ribbons of light along a path, rising pillars; and a tint over the edges
 * of the screen for the player's own big moments.
 */
public final class FxRenderer {
	private static final List<Effect> EFFECTS = new ArrayList<>();
	private static final List<Effect> SCREENS = new ArrayList<>();
	/** The player's own camera: shakes and rushes of the field of view, each fading out over its ticks. */
	private static final List<float[]> SHAKES = new ArrayList<>();
	private static final List<float[]> FOVS = new ArrayList<>();
	/** Until when (client ticks) the server is carrying this player along a path. */
	private static long flightUntil;
	/** Whether the shapes now being drawn are the ink under the glow, and how thick the current effect lays it. */
	private static boolean inkPass;
	/** Whether the shapes now being drawn are solid bodies (see {@link FxLayers#SOLID}). */
	private static boolean solidPass;
	private static float inkStrength;
	/** The camera this frame: nothing is drawn right at the eye (see {@link #vertex}). */
	private static Vec3d eye = Vec3d.ZERO;

	/**
	 * Optional: also draws the glow into a bloom pass (Shimmer's, when the harness tries it). Given a drawing that
	 * takes a buffer source, it runs that drawing where the bloom wants it.
	 */
	public static java.util.function.Consumer<java.util.function.Consumer<VertexConsumerProvider>> bloom;

	private FxRenderer() {
	}

	private abstract static class Effect {
		final int ticks;
		final int rgb;
		/** Drawn twice: as real colour first, then as glow (see {@link Fx#INK}). */
		final boolean ink;
		int age;

		Effect(int ticks, int rgb) {
			this.ticks = Math.max(1, ticks);
			this.rgb = rgb & 0xFFFFFF;
			this.ink = (rgb & Fx.INK) != 0;
		}

		/** 0 at the start, 1 at the end, smooth between ticks. */
		float progress(float delta) {
			return MathHelper.clamp((age + delta) / ticks, 0, 1);
		}

		/** Once a client tick, for effects that make particles of their own. */
		void tick() {
		}

		/** Whether it is drawn as ink too: when its colour asks for it, or always (the void's dark). */
		boolean inks() {
			return ink;
		}

		/** Whether it has solid bodies, drawn first with depth (see {@link FxLayers#SOLID}). */
		boolean solid() {
			return false;
		}

		/** How thick its ink is: a flash or a ghost only tints, a stream of blood is solid. */
		float inkStrength() {
			return 0.85f;
		}

		abstract void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta);
	}

	public static void read(PacketByteBuf buf) {
		int kind = buf.readVarInt();
		int ticks = buf.readVarInt();
		int rgb = buf.readInt();
		switch (kind) {
			case Fx.RING -> EFFECTS.add(new Ring(ticks, rgb, readVec(buf), buf.readFloat(), buf.readFloat()));
			case Fx.SPHERE -> EFFECTS.add(new Sphere(ticks, rgb, readVec(buf), buf.readFloat(), buf.readVarInt(), buf.readBoolean()));
			case Fx.RIBBON -> {
				int rgb2 = buf.readInt();
				float height = buf.readFloat();
				int count = buf.readVarInt();
				List<Vec3d> points = new ArrayList<>();
				for (int i = 0; i < count; i++) {
					points.add(readVec(buf));
				}
				EFFECTS.add(new Ribbon(ticks, rgb, rgb2, height, points));
			}
			case Fx.PILLAR -> EFFECTS.add(new Pillar(ticks, rgb, readVec(buf), buf.readFloat(), buf.readFloat()));
			case Fx.FLIGHT -> flightUntil = ClientRaceState.clientTicks() + ticks + 3;
			case Fx.SCREEN -> SCREENS.add(new Effect(ticks, rgb) {
				@Override
				void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
				}
			});
			case Fx.SLASH -> EFFECTS.add(new Slash(ticks, rgb, readVec(buf), readVec(buf), buf.readFloat(), buf.readFloat()));
			case Fx.TENDRIL -> EFFECTS.add(new Tendril(ticks, rgb, buf.readInt(), buf.readVarInt(), buf.readVarInt()));
			case Fx.MARK -> EFFECTS.add(new Mark(ticks, rgb, readVec(buf), buf.readVarInt(), buf.readFloat(), buf.readVarInt()));
			case Fx.HELIX -> EFFECTS.add(new Helix(ticks, rgb, buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readBoolean()));
			case Fx.GHOSTS -> {
				float yaw = buf.readFloat();
				int count = buf.readVarInt();
				List<Vec3d> spots = new ArrayList<>();
				for (int i = 0; i < count; i++) {
					spots.add(readVec(buf));
				}
				EFFECTS.add(new Ghosts(ticks, rgb, yaw, spots));
			}
			case Fx.BURST -> EFFECTS.add(new Burst(ticks, rgb, readVec(buf), buf.readFloat()));
			case Fx.AURA -> EFFECTS.add(new Aura(ticks, rgb, buf.readVarInt(), buf.readVarInt()));
			case Fx.SWARM -> EFFECTS.add(new Swarm(ticks, buf.readVarInt(), buf.readVarInt()));
			case Fx.SHAKE -> SHAKES.add(new float[] {ticks, 0, buf.readFloat()});
			case Fx.FOV -> FOVS.add(new float[] {ticks, 0, buf.readFloat()});
			case Fx.PULSE -> SCREENS.add(new Pulse(ticks, rgb, buf.readVarInt()));
			case Fx.RIFT -> EFFECTS.add(new Rift(ticks, rgb, readVec(buf), buf.readFloat(), buf.readFloat()));
			case Fx.VORTEX -> EFFECTS.add(new Vortex(ticks, rgb, readVec(buf), buf.readFloat()));
			case Fx.VOID_SCREEN -> SCREENS.add(new VoidScreen(ticks));
			case Fx.CRYSTALS -> EFFECTS.add(new Crystals(ticks, rgb, readVec(buf), buf.readFloat(), buf.readVarInt(), buf.readFloat()));
			case Fx.NEBULA -> {
				int rgb2 = buf.readInt();
				EFFECTS.add(new Nebula(ticks, rgb, rgb2, readVec(buf), buf.readVarInt(), buf.readFloat()));
			}
			case Fx.COMET -> EFFECTS.add(new Comet(ticks, rgb, readVec(buf), readVec(buf)));
			case Fx.THREADS -> EFFECTS.add(new Threads(ticks, rgb, readVec(buf), buf.readFloat(), buf.readFloat(), buf.readVarInt()));
			case Fx.AURORA -> {
				int rgb2 = buf.readInt();
				EFFECTS.add(new Aurora(ticks, rgb, rgb2, readVec(buf), buf.readFloat()));
			}
			case Fx.COINS -> EFFECTS.add(new Coins(ticks, readVec(buf), buf.readVarInt(), buf.readFloat()));
			case Fx.EXCHANGE -> {
				int rgb2 = buf.readInt();
				EFFECTS.add(new Exchange(ticks, rgb, rgb2, buf.readVarInt(), buf.readVarInt()));
			}
			case Fx.SEAL -> EFFECTS.add(new Seal(ticks, rgb, buf.readVarInt(), buf.readFloat()));
			case Fx.DOOR -> EFFECTS.add(new Door(ticks, rgb, readVec(buf), buf.readFloat(), buf.readVarInt()));
			case Fx.DOOR_CLOSE -> {
				int owner = buf.readVarInt();
				for (Effect e : EFFECTS) {
					if (e instanceof Door door && door.owner == owner) {
						door.close();
					}
				}
			}
			case Fx.FLIP -> EFFECTS.add(new Flip(ticks, rgb, buf.readVarInt()));
			case Fx.HEARTS -> EFFECTS.add(new Hearts(ticks, rgb, buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
			case Fx.WINGS -> EFFECTS.add(new Wings(ticks, rgb, buf.readVarInt(), buf.readFloat()));
			case Fx.SIGIL -> EFFECTS.add(new Sigil(ticks, rgb, buf.readVarInt(), buf.readVarInt()));
			case Fx.HEARTBREAK -> EFFECTS.add(new Heartbreak(ticks, rgb, buf.readVarInt()));
			case Fx.LULLABY -> EFFECTS.add(new Lullaby(ticks, rgb, readVec(buf), buf.readFloat()));
			case Fx.MASK -> EFFECTS.add(new Mask(ticks, rgb, readVec(buf), buf.readVarInt()));
			case Fx.MASKS -> EFFECTS.add(new Masks(ticks, rgb, buf.readVarInt(), buf.readVarInt(), buf.readFloat()));
			case Fx.WEB -> EFFECTS.add(new Web(ticks, rgb, readVec(buf), buf.readFloat()));
			case Fx.PHASE -> {
				MinecraftClient client = MinecraftClient.getInstance();
				if (client.world != null) {
					Voidborn.clientPhaseUntil = client.world.getTime() + ticks + 2;
				}
			}
			default -> {
			}
		}
	}

	private static Vec3d readVec(PacketByteBuf buf) {
		return new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
	}

	public static void tick() {
		for (List<Effect> list : List.of(EFFECTS, SCREENS)) {
			for (Iterator<Effect> it = list.iterator(); it.hasNext(); ) {
				Effect e = it.next();
				e.tick();
				if (++e.age >= e.ticks) {
					it.remove();
				}
			}
		}
		for (List<float[]> list : List.of(SHAKES, FOVS)) {
			list.removeIf(f -> ++f[1] >= f[0]);
		}
	}

	public static void clear() {
		EFFECTS.clear();
		SCREENS.clear();
		SHAKES.clear();
		FOVS.clear();
		flightUntil = 0;
	}

	/** Shakes the camera (called as the view is set up, where a hurt tilts it): strong at first, dying away. */
	public static void shake(MatrixStack matrices, float delta) {
		if (SHAKES.isEmpty()) {
			return;
		}
		float pitch = 0;
		float roll = 0;
		float time = ClientRaceState.clientTicks() + delta;
		for (float[] f : SHAKES) {
			float left = 1 - MathHelper.clamp((f[1] + delta) / f[0], 0, 1);
			float s = f[2] * left * left;
			// Uneven sines, so it judders rather than sways
			pitch += s * (MathHelper.sin(time * 2.9f) * 0.6f + MathHelper.sin(time * 5.3f) * 0.4f);
			roll += s * (MathHelper.sin(time * 3.7f + 1) * 0.6f + MathHelper.sin(time * 6.1f) * 0.4f);
		}
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll));
	}

	/** How much wider the view is now: a rush springs out in a couple of ticks and eases back. */
	public static double fovScale(float delta) {
		double scale = 1;
		for (float[] f : FOVS) {
			float t = MathHelper.clamp((f[1] + delta) / f[0], 0, 1);
			float envelope = t < 0.12f ? t / 0.12f : (1 - (t - 0.12f) / 0.88f) * (1 - (t - 0.12f) / 0.88f);
			scale += f[2] * envelope;
		}
		return scale;
	}

	/** The effects being drawn now, by kind, for the test harness's log. */
	public static String describe() {
		StringBuilder sb = new StringBuilder();
		for (Effect e : EFFECTS) {
			sb.append(e.getClass().getSimpleName()).append('@').append(e.age).append('/').append(e.ticks).append(' ');
		}
		for (Effect e : SCREENS) {
			sb.append(e instanceof Pulse ? "Pulse" : "Screen").append('@').append(e.age).append('/').append(e.ticks).append(' ');
		}
		return sb.toString().trim();
	}

	/** How far through the furthest-on effect of this kind is (0..1), or -1 if none is drawn: for the test harness. */
	public static float progressOf(String kind) {
		float best = -1;
		for (List<Effect> list : List.of(EFFECTS, SCREENS)) {
			for (Effect e : list) {
				if (e.getClass().getSimpleName().equals(kind)) {
					best = Math.max(best, (float) e.age / e.ticks);
				}
			}
		}
		return best;
	}

	/** Whether the server is carrying this player along a path right now (its steps are then smoothed). */
	public static boolean inFlight() {
		return ClientRaceState.clientTicks() <= flightUntil;
	}

	public static void render(WorldRenderContext context) {
		if (EFFECTS.isEmpty()) {
			return;
		}
		VertexConsumerProvider.Immediate immediate = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
		Vec3d cam = context.camera().getPos();
		eye = cam;
		context.matrixStack().push();
		context.matrixStack().translate(-cam.x, -cam.y, -cam.z);
		Matrix4f m = context.matrixStack().peek().getPositionMatrix();
		// Solid bodies first, writing depth; then the ink; then the glow on top of both
		solidPass = true;
		VertexConsumer solid = immediate.getBuffer(FxLayers.SOLID);
		for (Effect e : EFFECTS) {
			if (e.solid()) {
				e.draw(m, solid, cam, context.tickDelta());
			}
		}
		immediate.draw(FxLayers.SOLID);
		solidPass = false;
		inkPass = true;
		VertexConsumer ink = immediate.getBuffer(FxLayers.INK);
		for (Effect e : EFFECTS) {
			if (e.inks()) {
				inkStrength = e.inkStrength();
				e.draw(m, ink, cam, context.tickDelta());
			}
		}
		immediate.draw(FxLayers.INK);
		inkPass = false;
		VertexConsumer vc = immediate.getBuffer(RenderLayer.getLightning());
		for (Effect e : EFFECTS) {
			e.draw(m, vc, cam, context.tickDelta());
		}
		if (bloom != null) {
			// The same glow once more into the bloom pass, with this frame's view
			Matrix4f view = new Matrix4f(m);
			float delta = context.tickDelta();
			List<Effect> glowing = new ArrayList<>(EFFECTS);
			bloom.accept(provider -> {
				eye = cam;
				VertexConsumer glow = provider.getBuffer(RenderLayer.getLightning());
				for (Effect e : glowing) {
					e.draw(view, glow, cam, delta);
				}
			});
		}
		context.matrixStack().pop();
		immediate.draw(RenderLayer.getLightning());
	}

	/** The player's own moments: a flash, then the screen's edges glow in the ability's colour and fade. */
	public static void renderScreen(DrawContext ctx, float delta) {
		if (SCREENS.isEmpty()) {
			return;
		}
		int w = ctx.getScaledWindowWidth();
		int h = ctx.getScaledWindowHeight();
		for (Effect e : SCREENS) {
			float t = e.progress(delta);
			if (e instanceof VoidScreen voidScreen) {
				voidScreen.paint(ctx, w, h, delta);
				continue;
			}
			if (e instanceof Pulse pulse) {
				edges(ctx, w, h, e.rgb, 0.4f * pulse.beat(delta) * Math.min(1, (1 - t) * 6), 7);
				continue;
			}
			if (t < 0.15f) {
				ctx.fill(0, 0, w, h, argb(e.rgb, 0.35f * (1 - t / 0.15f)));
			}
			edges(ctx, w, h, e.rgb, 0.55f * (1 - t), 6);
		}
	}

	/** The screen's edges glowing in a colour, {@code 1/depth} of the screen deep. */
	private static void edges(DrawContext ctx, int w, int h, int rgb, float a, int depth) {
		if (a <= 0.005f) {
			return;
		}
		int band = Math.max(8, Math.min(w, h) / depth);
		ctx.fillGradient(0, 0, w, band, argb(rgb, a), argb(rgb, 0));
		ctx.fillGradient(0, h - band, w, h, argb(rgb, 0), argb(rgb, a));
		// Sideways gradients aren't built in: thin strips fading towards the middle
		for (int i = 0; i < band; i += 2) {
			int c = argb(rgb, a * (1 - (float) i / band) * (1 - (float) i / band));
			ctx.fill(i, 0, i + 2, h, c);
			ctx.fill(w - i - 2, 0, w - i, h, c);
		}
	}

	private static int argb(int rgb, float alpha) {
		return ((int) (MathHelper.clamp(alpha, 0, 1) * 255) << 24) | (rgb & 0xFFFFFF);
	}

	private static void vertex(VertexConsumer vc, Matrix4f m, double x, double y, double z, int rgb, float alpha) {
		// Right at the eye any shape would fill the view: it thins away there
		double d = Math.sqrt(eye.squaredDistanceTo(x, y, z));
		if (d < 1.0) {
			alpha *= (float) MathHelper.clamp((d - 0.35) / 0.65, 0, 1);
		}
		vc.vertex(m, (float) x, (float) y, (float) z).color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF,
				(int) (MathHelper.clamp(alpha, 0, 1) * 255)).next();
	}

	/**
	 * A quad seen from both sides (the glow layer culls back faces). As ink it is the colour a shade deeper and once
	 * only (the ink layer shows both faces), and white-hot edges stay glow alone.
	 */
	private static void quad(VertexConsumer vc, Matrix4f m, Vec3d a, Vec3d b, Vec3d c, Vec3d d, int rgb, float aa, float ab, float ac, float ad) {
		if (solidPass) {
			vertex(vc, m, a.x, a.y, a.z, rgb, aa);
			vertex(vc, m, b.x, b.y, b.z, rgb, ab);
			vertex(vc, m, c.x, c.y, c.z, rgb, ac);
			vertex(vc, m, d.x, d.y, d.z, rgb, ad);
			return;
		}
		if (inkPass) {
			if ((rgb & 0xFFFFFF) == 0xFFFFFF) {
				return;
			}
			int deep = lerpColor(rgb, 0x000000, 0.3f);
			float k = inkStrength;
			vertex(vc, m, a.x, a.y, a.z, deep, aa * k);
			vertex(vc, m, b.x, b.y, b.z, deep, ab * k);
			vertex(vc, m, c.x, c.y, c.z, deep, ac * k);
			vertex(vc, m, d.x, d.y, d.z, deep, ad * k);
			return;
		}
		vertex(vc, m, a.x, a.y, a.z, rgb, aa);
		vertex(vc, m, b.x, b.y, b.z, rgb, ab);
		vertex(vc, m, c.x, c.y, c.z, rgb, ac);
		vertex(vc, m, d.x, d.y, d.z, rgb, ad);
		vertex(vc, m, d.x, d.y, d.z, rgb, ad);
		vertex(vc, m, c.x, c.y, c.z, rgb, ac);
		vertex(vc, m, b.x, b.y, b.z, rgb, ab);
		vertex(vc, m, a.x, a.y, a.z, rgb, aa);
	}

	private static float easeOut(float t) {
		return 1 - (1 - t) * (1 - t) * (1 - t);
	}

	/** A band on the ground that spreads outwards, bright at its edge, fading as it goes. */
	private static final class Ring extends Effect {
		final Vec3d at;
		final float r0;
		final float r1;

		Ring(int ticks, int rgb, Vec3d at, float r0, float r1) {
			super(ticks, rgb);
			this.at = at.add(0, 0.06, 0);
			this.r0 = r0;
			this.r1 = r1;
		}

		@Override
		float inkStrength() {
			return 0.7f;
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
			float t = progress(delta);
			float r = r0 + (r1 - r0) * easeOut(t);
			float inner = Math.max(0, r - 0.5f - r * 0.08f);
			float alpha = (float) Math.pow(1 - t, 1.5) * 0.9f;
			int segments = 48;
			for (int i = 0; i < segments; i++) {
				double a0 = Math.PI * 2 * i / segments;
				double a1 = Math.PI * 2 * (i + 1) / segments;
				Vec3d o0 = at.add(Math.cos(a0) * r, 0, Math.sin(a0) * r);
				Vec3d o1 = at.add(Math.cos(a1) * r, 0, Math.sin(a1) * r);
				Vec3d i0 = at.add(Math.cos(a0) * inner, 0, Math.sin(a0) * inner);
				Vec3d i1 = at.add(Math.cos(a1) * inner, 0, Math.sin(a1) * inner);
				quad(vc, m, i0, o0, o1, i1, rgb, 0, alpha, alpha, 0);
				// A low wall of light on the edge, so the wave reads from the side as well
				quad(vc, m, o0, o0.add(0, 0.6, 0), o1.add(0, 0.6, 0), o1, rgb, alpha * 0.8f, 0, 0, alpha * 0.8f);
			}
		}
	}

	/**
	 * A dome (or a whole sphere) that springs up, holds with slowly turning bright bands and a brighter rim where it
	 * is seen edge-on, then fades. It may ride along with an entity.
	 */
	private static final class Sphere extends Effect {
		final Vec3d at;
		final float radius;
		final int follow;
		final boolean full;

		Sphere(int ticks, int rgb, Vec3d at, float radius, int follow, boolean full) {
			super(ticks, rgb);
			this.at = at;
			this.radius = radius;
			this.follow = follow;
			this.full = full;
		}

		@Override
		float inkStrength() {
			return 0.4f;
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
			Vec3d center = at;
			if (follow >= 0 && MinecraftClient.getInstance().world != null) {
				Entity e = MinecraftClient.getInstance().world.getEntityById(follow);
				if (e != null) {
					center = e.getLerpedPos(delta);
				}
			}
			float life = age + delta;
			// Springs in over 6 ticks with a small overshoot, fades over the last 8
			float grow = Math.min(1, life / 6f);
			float scale = grow < 1 ? 1 + 2.2f * (float) Math.pow(grow - 1, 3) + 1.2f * (float) Math.pow(grow - 1, 2) : 1;
			float fade = Math.min(1, (ticks - life) / 8f);
			float r = radius * Math.max(0.05f, scale);
			// Seen from inside, the shell only hints at itself: it must not wash the whole view in its colour
			if (cam.squaredDistanceTo(center) < r * r) {
				fade *= 0.3f;
			}
			int lat = 12;
			int lon = 32;
			double spin = life * 0.015;
			for (int i = full ? -lat : 0; i < lat; i++) {
				double p0 = Math.PI / 2 * i / lat;
				double p1 = Math.PI / 2 * (i + 1) / lat;
				for (int j = 0; j < lon; j++) {
					double t0 = Math.PI * 2 * j / lon + spin;
					double t1 = Math.PI * 2 * (j + 1) / lon + spin;
					Vec3d[] v = {point(center, r, p0, t0), point(center, r, p0, t1), point(center, r, p1, t1), point(center, r, p1, t0)};
					// Every fourth band glows brighter, a lattice slowly turning
					float band = j % 4 == 0 ? 0.35f : 0;
					float[] a = new float[4];
					for (int k = 0; k < 4; k++) {
						Vec3d normal = v[k].subtract(center).normalize();
						Vec3d view = v[k].subtract(cam).normalize();
						float edge = 1 - (float) Math.abs(normal.dotProduct(view));
						a[k] = (0.05f + 0.42f * edge * edge + band * 0.25f) * fade;
					}
					quad(vc, m, v[0], v[1], v[2], v[3], rgb, a[0], a[1], a[2], a[3]);
				}
			}
			if (!full) {
				// A bright rim where the dome meets the ground
				drawRim(m, vc, center, r, fade);
			}
		}

		private void drawRim(Matrix4f m, VertexConsumer vc, Vec3d center, float r, float fade) {
			int segments = 48;
			for (int i = 0; i < segments; i++) {
				double a0 = Math.PI * 2 * i / segments;
				double a1 = Math.PI * 2 * (i + 1) / segments;
				Vec3d o0 = center.add(Math.cos(a0) * r, 0.05, Math.sin(a0) * r);
				Vec3d o1 = center.add(Math.cos(a1) * r, 0.05, Math.sin(a1) * r);
				Vec3d i0 = center.add(Math.cos(a0) * (r - 0.4), 0.05, Math.sin(a0) * (r - 0.4));
				Vec3d i1 = center.add(Math.cos(a1) * (r - 0.4), 0.05, Math.sin(a1) * (r - 0.4));
				quad(vc, m, i0, o0, o1, i1, rgb, 0, 0.8f * fade, 0.8f * fade, 0);
			}
		}

		private static Vec3d point(Vec3d c, double r, double pitch, double yaw) {
			return c.add(Math.cos(pitch) * Math.cos(yaw) * r, Math.sin(pitch) * r, Math.cos(pitch) * Math.sin(yaw) * r);
		}
	}

	/**
	 * A streak of light along a path: two crossed ribbons, shading from one colour to the other, its tail drawing in
	 * after the head as it fades.
	 */
	private static final class Ribbon extends Effect {
		final int rgb2;
		final float height;
		final List<Vec3d> points;

		Ribbon(int ticks, int rgb, int rgb2, float height, List<Vec3d> points) {
			super(ticks, rgb);
			this.rgb2 = rgb2;
			this.height = height;
			this.points = points;
		}

		@Override
		float inkStrength() {
			return 0.45f;
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
			float t = progress(delta);
			int n = points.size();
			// The tail draws in towards the head over the effect's life
			int from = (int) (t * t * (n - 1));
			float alpha = 0.85f * (1 - t * 0.6f);
			for (int i = Math.max(0, from); i < n - 1; i++) {
				Vec3d a = points.get(i).add(0, 1.0, 0);
				Vec3d b = points.get(i + 1).add(0, 1.0, 0);
				Vec3d dir = b.subtract(a);
				if (dir.lengthSquared() < 1.0E-6) {
					continue;
				}
				Vec3d side = dir.crossProduct(new Vec3d(0, 1, 0)).normalize().multiply(height * 0.35);
				Vec3d up = new Vec3d(0, height / 2, 0);
				float s0 = (float) (i - from) / Math.max(1, n - 1 - from);
				float s1 = (float) (i + 1 - from) / Math.max(1, n - 1 - from);
				int c = lerpColor(rgb, rgb2, (float) i / (n - 1));
				float a0 = alpha * s0;
				float a1 = alpha * s1;
				// Upright ribbon, brightest in its middle line
				quad(vc, m, a.subtract(up), b.subtract(up), b, a, c, 0, 0, a1, a0);
				quad(vc, m, a, b, b.add(up), a.add(up), c, a0, a1, 0, 0);
				// And a flat one across it, so the streak reads from above too
				quad(vc, m, a.subtract(side), b.subtract(side), b, a, c, 0, 0, a1, a0);
				quad(vc, m, a, b, b.add(side), a.add(side), c, a0, a1, 0, 0);
			}
		}
	}

	/** A pillar of light that shoots up from the ground and fades, brightest at its foot. */
	private static final class Pillar extends Effect {
		final Vec3d at;
		final float radius;
		final float height;

		Pillar(int ticks, int rgb, Vec3d at, float radius, float height) {
			super(ticks, rgb);
			this.at = at;
			this.radius = radius;
			this.height = height;
		}

		@Override
		float inkStrength() {
			return 0.5f;
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
			float t = progress(delta);
			float h = height * easeOut(Math.min(1, t * 3));
			float alpha = 0.8f * (1 - t);
			int segments = 12;
			for (int i = 0; i < segments; i++) {
				double a0 = Math.PI * 2 * i / segments;
				double a1 = Math.PI * 2 * (i + 1) / segments;
				Vec3d b0 = at.add(Math.cos(a0) * radius, 0, Math.sin(a0) * radius);
				Vec3d b1 = at.add(Math.cos(a1) * radius, 0, Math.sin(a1) * radius);
				quad(vc, m, b0, b1, b1.add(0, h, 0), b0.add(0, h, 0), rgb, alpha, alpha, 0, 0);
			}
		}
	}

	private static int lerpColor(int a, int b, float t) {
		int r = (int) MathHelper.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
		int g = (int) MathHelper.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
		int bl = (int) MathHelper.lerp(t, a & 0xFF, b & 0xFF);
		return (r << 16) | (g << 8) | bl;
	}

	// ---- shared shapes ----

	/** Where an entity is now, smoothly between ticks; null if this client doesn't have it. */
	private static Vec3d lerped(int id, float delta) {
		MinecraftClient client = MinecraftClient.getInstance();
		Entity e = client.world == null ? null : client.world.getEntityById(id);
		return e == null ? null : e.getLerpedPos(delta);
	}

	/** A soft line of light from a to b, {@code width} across in the plane facing {@code normal}: bright down its middle. */
	private static void glowLine(VertexConsumer vc, Matrix4f m, Vec3d a, Vec3d b, Vec3d normal, double width, int rgb, float alpha) {
		Vec3d dir = b.subtract(a);
		Vec3d side = dir.crossProduct(normal);
		if (side.lengthSquared() < 1.0E-8) {
			return;
		}
		side = side.normalize().multiply(width / 2);
		quad(vc, m, a.subtract(side), b.subtract(side), b, a, rgb, 0, 0, alpha, alpha);
		quad(vc, m, a, b, b.add(side), a.add(side), rgb, alpha, alpha, 0, 0);
	}

	/**
	 * A ribbon through {@code points} that always turns its face to the camera: {@code widths}, {@code colors} and
	 * {@code alphas} per point, bright along its spine and soft at its edges.
	 */
	private static void strip(VertexConsumer vc, Matrix4f m, Vec3d cam, List<Vec3d> points, float[] widths, int[] colors, float[] alphas) {
		for (int i = 0; i < points.size() - 1; i++) {
			Vec3d a = points.get(i);
			Vec3d b = points.get(i + 1);
			Vec3d dir = b.subtract(a);
			Vec3d sideA = dir.crossProduct(cam.subtract(a));
			Vec3d sideB = dir.crossProduct(cam.subtract(b));
			if (sideA.lengthSquared() < 1.0E-8 || sideB.lengthSquared() < 1.0E-8) {
				continue;
			}
			sideA = sideA.normalize().multiply(widths[i] / 2);
			sideB = sideB.normalize().multiply(widths[i + 1] / 2);
			quad(vc, m, a.subtract(sideA), b.subtract(sideB), b, a, colors[i], 0, 0, alphas[i + 1], alphas[i]);
			quad(vc, m, a, b, b.add(sideB), a.add(sideA), colors[i], alphas[i], alphas[i + 1], 0, 0);
		}
	}

	/** A ring of light lying flat at {@code center}. */
	private static void flatRing(VertexConsumer vc, Matrix4f m, Vec3d center, double r, double width, int rgb, float alpha, int segments) {
		Vec3d up = new Vec3d(0, 1, 0);
		for (int i = 0; i < segments; i++) {
			double a0 = Math.PI * 2 * i / segments;
			double a1 = Math.PI * 2 * (i + 1) / segments;
			glowLine(vc, m, center.add(Math.cos(a0) * r, 0, Math.sin(a0) * r), center.add(Math.cos(a1) * r, 0, Math.sin(a1) * r), up, width, rgb, alpha);
		}
	}

	/** Overshoots a little and settles: things that spring open. */
	private static float easeOutBack(float t) {
		float c = 1.7f;
		return 1 + (c + 1) * (float) Math.pow(t - 1, 3) + c * (float) Math.pow(t - 1, 2);
	}

	/** A heart's beat over a phase of 0..1: a strong lub and, just after, a softer dub. */
	static float heartbeat(float phase) {
		float lub = (float) Math.exp(-Math.pow(phase / 0.07, 2)) + (float) Math.exp(-Math.pow((phase - 1) / 0.07, 2));
		float dub = (float) Math.exp(-Math.pow((phase - 0.26) / 0.07, 2));
		return Math.min(1, lub + 0.7f * dub);
	}

	// ---- the new effects ----

	/** A crescent cut through a point: it sweeps across in a flash, white-hot along its edge, then widens and fades. */
	private static final class Slash extends Effect {
		final Vec3d center;
		final Vec3d forward;
		final Vec3d across;
		final Vec3d normal;
		final float size;

		Slash(int ticks, int rgb, Vec3d at, Vec3d dir, float size, float roll) {
			super(ticks, rgb);
			Vec3d f = dir.lengthSquared() < 1.0E-6 ? new Vec3d(0, 0, 1) : dir.normalize();
			Vec3d right = f.crossProduct(new Vec3d(0, 1, 0));
			right = right.lengthSquared() < 1.0E-6 ? new Vec3d(1, 0, 0) : right.normalize();
			Vec3d up = right.crossProduct(f).normalize();
			double r = Math.toRadians(roll);
			this.forward = f;
			this.across = right.multiply(Math.cos(r)).add(up.multiply(Math.sin(r)));
			this.normal = this.across.crossProduct(f).normalize();
			this.size = size;
			// The arc bulges forward through the point it cuts
			this.center = at.subtract(f.multiply(size * 0.55));
		}

		@Override
		float inkStrength() {
			return 0.9f;
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
			float t = progress(delta);
			float head = easeOut(Math.min(1, t / 0.3f));
			float tail = t < 0.3f ? 0 : (t - 0.3f) / 0.7f;
			float alpha = 1 - tail * tail;
			double radius = size * (1 + 0.3 * t);
			int segments = 28;
			Vec3d prevO = null;
			Vec3d prevC = null;
			Vec3d prevI = null;
			for (int k = 0; k <= segments; k++) {
				float s = (float) k / segments;
				if (s < tail || s > head) {
					prevO = null;
					continue;
				}
				double angle = (s - 0.5) * Math.toRadians(150);
				Vec3d out = across.multiply(Math.sin(angle)).add(forward.multiply(Math.cos(angle)));
				// Thin at its tips, thick in its belly
				double width = size * 0.32 * Math.sin(Math.PI * s);
				Vec3d o = center.add(out.multiply(radius));
				Vec3d c = center.add(out.multiply(radius - width * 0.3));
				Vec3d i = center.add(out.multiply(radius - width));
				if (prevO != null) {
					quad(vc, m, prevI, i, c, prevC, rgb, 0, 0, alpha * 0.9f, alpha * 0.9f);
					quad(vc, m, prevC, c, o, prevO, rgb, alpha * 0.9f, alpha * 0.9f, 0, 0);
					// The white-hot edge
					Vec3d e0 = prevC.add(prevO).multiply(0.5);
					Vec3d e1 = c.add(o).multiply(0.5);
					quad(vc, m, prevC, c, e1, e0, 0xFFFFFF, alpha, alpha, alpha * 0.6f, alpha * 0.6f);
				}
				prevO = o;
				prevC = c;
				prevI = i;
			}
		}
	}

	/** A winding stream from one entity to another, thick where it leaves and thin where it arrives, pulses running along. */
	private static final class Tendril extends Effect {
		final int rgb2;
		final int from;
		final int to;
		final float seed;

		Tendril(int ticks, int rgb, int rgb2, int from, int to) {
			super(ticks, rgb);
			this.rgb2 = rgb2;
			this.from = from;
			this.to = to;
			this.seed = (from * 31 + to) % 100 / 10f;
		}

		@Override
		float inkStrength() {
			return 1.0f;
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
			Vec3d a = lerped(from, delta);
			Vec3d b = lerped(to, delta);
			if (a == null || b == null) {
				return;
			}
			MinecraftClient client = MinecraftClient.getInstance();
			Entity source = client.world.getEntityById(from);
			a = a.add(0, source.getHeight() * 0.6, 0);
			b = b.add(0, 1.0, 0);
			float life = age + delta;
			float reach = Math.min(1, life / 4f);
			float fade = Math.min(1, (ticks - life) / 6f);
			Vec3d way = b.subtract(a);
			Vec3d side = way.crossProduct(new Vec3d(0, 1, 0));
			side = side.lengthSquared() < 1.0E-6 ? new Vec3d(1, 0, 0) : side.normalize();
			double lift = 0.6 + way.length() * 0.15;
			Vec3d p1 = a.add(0, lift, 0).add(side.multiply(Math.sin(life * 0.3 + seed) * 0.9));
			Vec3d p2 = b.add(0, lift * 0.6, 0).subtract(side.multiply(Math.cos(life * 0.27 + seed) * 0.7));
			int n = 28;
			List<Vec3d> points = new ArrayList<>();
			float[] widths = new float[n + 1];
			int[] colors = new int[n + 1];
			float[] alphas = new float[n + 1];
			for (int k = 0; k <= n; k++) {
				float s = (float) k / n * reach;
				points.add(bezier(a, p1, p2, b, s));
				// Bright pulses carried from the victim to the drinker
				float pulse = 0;
				for (int j = 0; j < 3; j++) {
					float at = (life * 0.07f + j / 3f) % 1;
					pulse = Math.max(pulse, (float) Math.exp(-Math.pow((s - at) / 0.06, 2)));
				}
				widths[k] = (0.5f * (1 - s) + 0.1f) * (1 + 0.6f * pulse);
				colors[k] = lerpColor(rgb, rgb2, s);
				alphas[k] = (0.45f + 0.55f * pulse) * fade;
			}
			strip(vc, m, cam, points, widths, colors, alphas);
		}

		private static Vec3d bezier(Vec3d a, Vec3d b, Vec3d c, Vec3d d, double t) {
			double u = 1 - t;
			return a.multiply(u * u * u).add(b.multiply(3 * u * u * t)).add(c.multiply(3 * u * t * t)).add(d.multiply(t * t * t));
		}
	}

	/**
	 * A mark on the ground in a race's own manner: a pool of blood, a clock face, ripples of a voice, cracks onto the void.
	 * It opens, holds (riding with an entity if it has one) and fades.
	 */
	private static final class Mark extends Effect {
		final Vec3d at;
		final int follow;
		final float radius;
		final int style;
		/** Fixed per mark, so its roots and spatters keep their shape from frame to frame. */
		final long seed;

		Mark(int ticks, int rgb, Vec3d at, int follow, float radius, int style) {
			super(ticks, rgb);
			this.at = at;
			this.follow = follow;
			this.radius = radius;
			this.style = style;
			this.seed = Double.doubleToLongBits(at.x * 31 + at.z * 17 + radius);
		}

		@Override
		float inkStrength() {
			return style == Fx.POOL || style == Fx.CRACKS || style == Fx.MYCELIUM ? 1.0f : 0.7f;
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
			Vec3d center = at;
			if (follow >= 0) {
				Vec3d riding = lerped(follow, delta);
				if (riding != null) {
					// A mark riding with a body stays on the ground under it, even when the body leaps
					center = ground(riding);
				}
			}
			center = center.add(0, 0.04, 0);
			float life = age + delta;
			float fade = Math.min(1, (ticks - life) / 10f) * Math.min(1, life / 2f);
			switch (style) {
				case Fx.POOL -> pool(m, vc, center, life, fade);
				case Fx.CLOCK -> clock(m, vc, center, life, fade);
				case Fx.CRACKS -> cracks(m, vc, center, life, fade);
				case Fx.MYCELIUM -> mycelium(m, vc, center, life, fade);
				default -> ripples(m, vc, center, life, fade);
			}
		}

		/** Blood spreading on the ground: a thick uneven pool with a glossy rim, spatters around it, beating if it rides a body. */
		private void pool(Matrix4f m, VertexConsumer vc, Vec3d c, float life, float fade) {
			float spread = easeOut(Math.min(1, life / 10f));
			float beat = follow >= 0 ? 0.9f + 0.1f * heartbeat((life % 20) / 20f) : 1;
			// Blood lies on the ground as colour; only its rim catches the light
			float body = inkPass ? 0.95f : 0.12f;
			blob(m, vc, c, radius * spread * beat, body, lerpColor(rgb, 0xFF8080, 0.3f), 0.45f, fade, 7);
			java.util.Random random = new java.util.Random(seed);
			for (int k = 0; k < 9; k++) {
				double a = random.nextDouble() * Math.PI * 2;
				double d = radius * (1.15 + random.nextDouble() * 0.5) * spread;
				double s = 0.08 + random.nextDouble() * 0.12;
				Vec3d p = c.add(Math.cos(a) * d, 0, Math.sin(a) * d);
				float spot = fade * body;
				quad(vc, m, p.add(-s, 0, -s), p.add(s, 0, -s), p.add(s, 0, s), p.add(-s, 0, s), rgb, spot, spot, spot, spot);
			}
		}

		/** An uneven patch lying on the ground, filled in its colour, with a lit rim; the same shape every frame. */
		private void blob(Matrix4f m, VertexConsumer vc, Vec3d c, double r0, float fill, int rim, float rimAlpha, float fade, int salt) {
			int n = 40;
			Vec3d prev = null;
			for (int i = 0; i <= n; i++) {
				double a = Math.PI * 2 * i / n;
				// A few slow lobes make the edge uneven
				double lobes = 1 + 0.18 * Math.sin(a * 3 + seed % salt) + 0.1 * Math.sin(a * 5 + seed % (salt + 4)) + 0.06 * Math.sin(a * 9 + seed % 13);
				double r = r0 * lobes;
				Vec3d p = c.add(Math.cos(a) * r, 0, Math.sin(a) * r);
				if (prev != null) {
					quad(vc, m, c, c, p, prev, rgb, fill * fade, fill * fade, fill * fade * 0.9f, fill * fade * 0.9f);
					glowLine(vc, m, prev, p, new Vec3d(0, 1, 0), 0.08, rim, rimAlpha * fade);
				}
				prev = p;
			}
		}

		/** The top of the ground under a point (a few blocks down at most), or the point itself over a drop. */
		private static Vec3d ground(Vec3d p) {
			ClientWorld world = MinecraftClient.getInstance().world;
			if (world == null) {
				return p;
			}
			BlockPos.Mutable pos = BlockPos.ofFloored(p).mutableCopy();
			for (int i = 0; i < 6; i++) {
				VoxelShape shape = world.getBlockState(pos).getCollisionShape(world, pos);
				if (!shape.isEmpty()) {
					double top = pos.getY() + shape.getMax(Direction.Axis.Y);
					if (top <= p.y + 0.01) {
						return new Vec3d(p.x, top, p.z);
					}
				}
				pos.move(Direction.DOWN);
			}
			return p;
		}

		/** A clock face: a ring, twelve marks (long at the quarters), and two hands going round, the long one fast. */
		private void clock(Matrix4f m, VertexConsumer vc, Vec3d c, float life, float fade) {
			float open = easeOutBack(Math.min(1, life / 8f));
			double r = radius * open;
			Vec3d up = new Vec3d(0, 1, 0);
			float a = 0.9f * fade;
			flatRing(vc, m, c, r, 0.14, rgb, a, 64);
			flatRing(vc, m, c, r * 0.9, 0.04, rgb, a * 0.5f, 64);
			for (int k = 0; k < 12; k++) {
				double p = k * Math.PI / 6;
				Vec3d dir = new Vec3d(Math.cos(p), 0, Math.sin(p));
				double inner = r * (k % 3 == 0 ? 0.7 : 0.8);
				glowLine(vc, m, c.add(dir.multiply(inner)), c.add(dir.multiply(r * 0.88)), up, k % 3 == 0 ? 0.12 : 0.07, rgb, a);
			}
			double minute = life * 0.25;
			double hour = life * 0.02;
			glowLine(vc, m, c, c.add(Math.cos(minute) * r * 0.78, 0, Math.sin(minute) * r * 0.78), up, 0.08, rgb, a);
			glowLine(vc, m, c, c.add(Math.cos(hour) * r * 0.5, 0, Math.sin(hour) * r * 0.5), up, 0.13, rgb, a);
			flatRing(vc, m, c, r * 0.06, 0.08, rgb, a, 12);
		}

		/**
		 * The ground cracked open onto the void: black fissures zigzagging out from the middle, glowing violet at their
		 * lips, stars glinting deep in them. They split open in a few ticks, as ground does under a blow.
		 */
		private void cracks(Matrix4f m, VertexConsumer vc, Vec3d c, float life, float fade) {
			float grow = easeOut(Math.min(1, life / 6f));
			Vec3d up = new Vec3d(0, 1, 0);
			int count = 7;
			for (int i = 0; i < count; i++) {
				double angle = Math.PI * 2 * i / count + hash(seed, i) * 0.6;
				double length = radius * (0.55 + 0.45 * hash(seed, i + 50)) * grow;
				int steps = 5;
				Vec3d p = c;
				for (int s = 0; s < steps; s++) {
					angle += (hash(seed, i * 10 + s + 100) - 0.5) * 1.2;
					Vec3d next = p.add(Math.cos(angle) * length / steps, 0, Math.sin(angle) * length / steps);
					double width = 0.24 * (1 - (double) s / steps) + 0.04;
					if (inkPass) {
						Vec3d dir = next.subtract(p).normalize();
						Vec3d side = new Vec3d(-dir.z, 0, dir.x).multiply(width / 2);
						quad(vc, m, p.subtract(side), next.subtract(side.multiply(0.7)), next.add(side.multiply(0.7)), p.add(side), 0x05020A, fade, fade, fade, fade);
					} else {
						glowLine(vc, m, p, next, up, width * 2.4, rgb, 0.5f * fade);
						glowLine(vc, m, p, next, up, width * 0.35, 0xE8E0FF, 0.5f * fade);
						if (hash(seed, i * 10 + s + 700) < 0.5) {
							float twinkle = fade * (0.5f + 0.5f * MathHelper.sin(life * 0.6f + i * 3 + s));
							double size = 0.04;
							Vec3d q = p.lerp(next, 0.5).add(0, 0.01, 0);
							quad(vc, m, q.add(-size, 0, -size), q.add(size, 0, -size), q.add(size, 0, size), q.add(-size, 0, size), 0xFFFFFF, twinkle, twinkle,
									twinkle, twinkle);
						}
					}
					p = next;
				}
			}
		}

		/**
		 * Mycelium spreading over the ground: dark moss threads laid on as colour, forking as they run out from the
		 * middle, lit along their length, buds of light at the forks and the tips that breathe slowly.
		 */
		private void mycelium(Matrix4f m, VertexConsumer vc, Vec3d c, float life, float fade) {
			float grow = easeOut(Math.min(1, life / 14f));
			Vec3d up = new Vec3d(0, 1, 0);
			int roots = 11;
			for (int i = 0; i < roots; i++) {
				double angle = Math.PI * 2 * i / roots + hash(seed, i) * 0.5;
				thread(m, vc, c, angle, radius * (0.75 + 0.25 * hash(seed, i + 40)) * grow, 6, i * 97, 2, up, life, fade);
			}
		}

		/** One thread of the mycelium and the forks it sends off, {@code depth} levels down. */
		private void thread(Matrix4f m, VertexConsumer vc, Vec3d from, double angle, double length, int steps, int salt, int depth, Vec3d up,
				float life, float fade) {
			Vec3d p = from;
			for (int s = 0; s < steps; s++) {
				angle += (hash(seed, salt + s) - 0.5) * 0.9;
				Vec3d next = p.add(Math.cos(angle) * length / steps, 0, Math.sin(angle) * length / steps);
				double width = (depth == 2 ? 0.16 : depth == 1 ? 0.1 : 0.06) * (1 - 0.6 * s / steps);
				if (inkPass) {
					Vec3d dir = next.subtract(p).normalize();
					Vec3d side = new Vec3d(-dir.z, 0, dir.x).multiply(width);
					quad(vc, m, p.subtract(side), next.subtract(side), next.add(side), p.add(side), 0x2E4A22, 0.9f * fade, 0.9f * fade, 0.9f * fade,
							0.9f * fade);
				} else {
					glowLine(vc, m, p, next, up, width * 2.2, rgb, 0.45f * fade);
					glowLine(vc, m, p, next, up, width * 0.5, 0xE6FFD8, 0.35f * fade);
				}
				// A fork: a thinner thread runs off to one side
				if (depth > 0 && hash(seed, salt + s + 300) < 0.35) {
					double side = hash(seed, salt + s + 400) < 0.5 ? -0.9 : 0.9;
					thread(m, vc, next, angle + side, length * 0.45, 3, salt * 7 + s * 13 + 1, depth - 1, up, life, fade);
				}
				// Buds of light along the way, breathing out of step
				if (!inkPass && (s == steps - 1 || hash(seed, salt + s + 500) < 0.25)) {
					float breath = fade * (0.45f + 0.55f * (0.5f + 0.5f * MathHelper.sin(life * 0.15f + salt + s)));
					double size = 0.05 + 0.03 * depth;
					Vec3d q = next.add(0, 0.01, 0);
					quad(vc, m, q.add(-size, 0, -size), q.add(size, 0, -size), q.add(size, 0, size), q.add(-size, 0, size), 0xD8FFC8, breath, breath, breath,
							breath);
				}
				p = next;
			}
		}

		/** A voice on the ground: soft rings rolling out from the middle one after another, fading as they go. */
		private void ripples(Matrix4f m, VertexConsumer vc, Vec3d c, float life, float fade) {
			for (int k = 0; k < 3; k++) {
				float phase = ((life / 22f) + k / 3f) % 1;
				float a = fade * (1 - phase) * (1 - phase);
				flatRing(vc, m, c, radius * (0.15 + 0.85 * phase), 0.16, rgb, a, 48);
			}
			flatRing(vc, m, c, radius * 0.1, 0.25, rgb, 0.5f * fade, 16);
		}
	}

	/** Strands of light winding up around an entity, rising; at the end they draw into it, or fly apart. */
	private static final class Helix extends Effect {
		final int around;
		final float radius;
		final float height;
		final int strands;
		final boolean inward;

		Helix(int ticks, int rgb, int around, float radius, float height, int strands, boolean inward) {
			super(ticks, rgb);
			this.around = around;
			this.radius = radius;
			this.height = height;
			this.strands = Math.max(1, strands);
			this.inward = inward;
		}

		@Override
		float inkStrength() {
			return 0.9f;
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
			Vec3d base = lerped(around, delta);
			if (base == null) {
				return;
			}
			float t = progress(delta);
			float life = age + delta;
			float top = easeOut(Math.min(1, t / 0.55f));
			float bottom = t < 0.45f ? 0 : (t - 0.45f) / 0.55f;
			// The last part: the strands close in on the body or open out
			float close = t < 0.6f ? 1 : inward ? 1 - (t - 0.6f) / 0.4f : 1 + (t - 0.6f) * 2.5f;
			int n = 30;
			for (int j = 0; j < strands; j++) {
				List<Vec3d> points = new ArrayList<>();
				float[] widths = new float[n + 1];
				int[] colors = new int[n + 1];
				float[] alphas = new float[n + 1];
				for (int k = 0; k <= n; k++) {
					float s = bottom + (top - bottom) * k / n;
					double angle = Math.PI * 2 * j / strands + s * 7.0 + life * 0.3;
					double r = radius * (1 - 0.35 * s) * close;
					points.add(base.add(Math.cos(angle) * r, s * height, Math.sin(angle) * r));
					float head = (float) k / n;
					widths[k] = 0.05f + 0.12f * head;
					colors[k] = lerpColor(rgb, 0xFF8080, head * head * 0.5f);
					alphas[k] = (0.25f + 0.75f * head) * (1 - bottom * 0.5f);
				}
				strip(vc, m, cam, points, widths, colors, alphas);
			}
		}
	}

	/** Afterimages of a body along a path: glowing silhouettes, the oldest fading first. */
	private static final class Ghosts extends Effect {
		final float yaw;
		final List<Vec3d> spots;

		Ghosts(int ticks, int rgb, float yaw, List<Vec3d> spots) {
			super(ticks, rgb);
			this.yaw = yaw;
			this.spots = spots;
		}

		@Override
		float inkStrength() {
			return 0.45f;
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
			float t = progress(delta);
			double rad = Math.toRadians(yaw);
			Vec3d fwd = new Vec3d(-Math.sin(rad), 0, Math.cos(rad));
			Vec3d right = new Vec3d(Math.cos(rad), 0, Math.sin(rad));
			for (int i = 0; i < spots.size(); i++) {
				// Older ones (nearer the start) fade sooner
				float own = MathHelper.clamp(t * 1.4f - (1 - (float) (i + 1) / spots.size()) * 0.4f, 0, 1);
				float a = (1 - own) * (0.25f + 0.35f * (i + 1) / spots.size());
				if (a <= 0.01f) {
					continue;
				}
				Vec3d feet = spots.get(i);
				// Head, body, arms, legs
				box(vc, m, feet, right, fwd, 0, 1.75, 0, 0.25, 0.25, 0.25, a);
				box(vc, m, feet, right, fwd, 0, 1.125, 0, 0.25, 0.375, 0.13, a);
				box(vc, m, feet, right, fwd, 0.375, 1.15, -0.05, 0.12, 0.36, 0.12, a * 0.8f);
				box(vc, m, feet, right, fwd, -0.375, 1.15, -0.05, 0.12, 0.36, 0.12, a * 0.8f);
				box(vc, m, feet, right, fwd, 0.125, 0.375, 0, 0.12, 0.375, 0.12, a * 0.8f);
				box(vc, m, feet, right, fwd, -0.125, 0.375, 0, 0.12, 0.375, 0.12, a * 0.8f);
			}
		}

		/** A box of light: centre (x right, y up, z forward) and half-sizes, its faces glowing. */
		private void box(VertexConsumer vc, Matrix4f m, Vec3d feet, Vec3d right, Vec3d fwd, double x, double y, double z, double hx, double hy, double hz,
				float a) {
			Vec3d c = feet.add(right.multiply(x)).add(0, y, 0).add(fwd.multiply(z));
			Vec3d rx = right.multiply(hx);
			Vec3d ry = new Vec3d(0, hy, 0);
			Vec3d rz = fwd.multiply(hz);
			Vec3d[] v = new Vec3d[8];
			for (int k = 0; k < 8; k++) {
				v[k] = c.add(rx.multiply((k & 1) == 0 ? -1 : 1)).add(ry.multiply((k & 2) == 0 ? -1 : 1)).add(rz.multiply((k & 4) == 0 ? -1 : 1));
			}
			int[][] faces = {{0, 1, 3, 2}, {4, 5, 7, 6}, {0, 1, 5, 4}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 3, 7, 5}};
			for (int[] f : faces) {
				// Brighter towards the top, as if lit from within
				quad(vc, m, v[f[0]], v[f[1]], v[f[2]], v[f[3]], rgb, a * 0.5f, a * 0.5f, a, a);
			}
		}
	}

	/** A flash: a shell bursting out to its radius in a few ticks, brightest at its rim, and gone. */
	private static final class Burst extends Effect {
		final Vec3d at;
		final float radius;

		Burst(int ticks, int rgb, Vec3d at, float radius) {
			super(ticks, rgb);
			this.at = at;
			this.radius = radius;
		}

		@Override
		float inkStrength() {
			return 0.3f;
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
			float t = progress(delta);
			double r = radius * easeOut(t);
			// From inside a burst is only a smear: it is seen from outside, and felt from within by the screen
			if (cam.squaredDistanceTo(at) < r * r) {
				return;
			}
			float fade = (1 - t) * (1 - t);
			int lat = 10;
			int lon = 20;
			for (int i = -lat; i < lat; i++) {
				double p0 = Math.PI / 2 * i / lat;
				double p1 = Math.PI / 2 * (i + 1) / lat;
				for (int j = 0; j < lon; j++) {
					double t0 = Math.PI * 2 * j / lon;
					double t1 = Math.PI * 2 * (j + 1) / lon;
					Vec3d[] v = {Sphere.point(at, r, p0, t0), Sphere.point(at, r, p0, t1), Sphere.point(at, r, p1, t1), Sphere.point(at, r, p1, t0)};
					float[] a = new float[4];
					for (int k = 0; k < 4; k++) {
						Vec3d normal = v[k].subtract(at).normalize();
						float edge = 1 - (float) Math.abs(normal.dotProduct(v[k].subtract(cam).normalize()));
						a[k] = (0.08f + 0.7f * edge * edge * edge) * fade;
					}
					quad(vc, m, v[0], v[1], v[2], v[3], rgb, a[0], a[1], a[2], a[3]);
				}
			}
		}
	}

	/** Particles rising off a body for a while (embers with a breath of blood mist, grains, spores...): made by this client. */
	private static final class Aura extends Effect {
		final int around;
		final DefaultParticleType particle;

		Aura(int ticks, int rgb, int around, int kind) {
			super(ticks, rgb);
			this.around = around;
			this.particle = switch (kind) {
				case Fx.GRAINS -> ModParticles.SAND;
				case Fx.SPORES -> ModParticles.SPORE;
				case Fx.WISPS -> ModParticles.WISP;
				case Fx.MOTES -> ModParticles.MOTE;
				default -> ModParticles.EMBER;
			};
		}

		@Override
		void tick() {
			MinecraftClient client = MinecraftClient.getInstance();
			Entity e = client.world == null ? null : client.world.getEntityById(around);
			if (e == null) {
				return;
			}
			ClientWorld world = client.world;
			for (int i = 0; i < 3; i++) {
				double angle = world.random.nextDouble() * Math.PI * 2;
				double r = 0.35 + world.random.nextDouble() * 0.35;
				world.addParticle(particle, e.getX() + Math.cos(angle) * r, e.getY() + world.random.nextDouble() * e.getHeight(),
						e.getZ() + Math.sin(angle) * r, 0, 0, 0);
			}
			if (particle == ModParticles.EMBER && age % 4 == 0) {
				world.addParticle(ModParticles.BLOOD_MIST, e.getX(), e.getY() + 0.2, e.getZ(), (world.random.nextDouble() - 0.5) * 0.08, 0,
						(world.random.nextDouble() - 0.5) * 0.08);
			}
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
		}
	}

	/** A body scattered into bats: they circle it while the swarm lasts, trailing blood mist. Made by this client. */
	private static final class Swarm extends Effect {
		final int around;
		final int count;

		Swarm(int ticks, int around, int count) {
			super(ticks, 0);
			this.around = around;
			this.count = count;
		}

		@Override
		void tick() {
			MinecraftClient client = MinecraftClient.getInstance();
			Entity e = client.world == null ? null : client.world.getEntityById(around);
			if (e == null) {
				return;
			}
			if (age == 0) {
				for (int i = 0; i < count; i++) {
					Particle bat = RaceParticles.swarmBat(client.world, e, ticks);
					if (bat != null) {
						client.particleManager.addParticle(bat);
					}
				}
			}
			// The swarm leaves a smear of mist where it passes
			if (age % 2 == 0) {
				client.world.addParticle(ModParticles.BLOOD_MIST, e.getX(), e.getY() + 0.6 + client.world.random.nextDouble(), e.getZ(), 0, 0, 0);
			}
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
		}
	}

	/** The player's own heart, beating at the screen's edges. */
	private static final class Pulse extends Effect {
		final int beats;

		Pulse(int ticks, int rgb, int beats) {
			super(ticks, rgb);
			this.beats = Math.max(1, beats);
		}

		float beat(float delta) {
			float phase = (age + delta) / ticks * beats % 1;
			return heartbeat(phase);
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
		}
	}

	// ---- the void ----

	/** A pseudo-random number in 0..1 fixed by a seed and an index: shapes that keep their jags from frame to frame. */
	private static float hash(long seed, int i) {
		long h = seed * 6364136223846793005L + i * 1442695040888963407L;
		h ^= h >>> 29;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 32;
		return (h & 0xFFFFFF) / (float) 0x1000000;
	}

	/** A small cross of light facing the camera. */
	private static void star(VertexConsumer vc, Matrix4f m, Vec3d p, Vec3d right, Vec3d up, double size, float alpha) {
		Vec3d r = right.multiply(size);
		Vec3d u = up.multiply(size);
		quad(vc, m, p.subtract(r), p.subtract(u), p.add(r), p.add(u), 0xFFFFFF, alpha, alpha, alpha, alpha);
		Vec3d r2 = right.multiply(size * 2.2);
		Vec3d u2 = up.multiply(size * 0.35);
		quad(vc, m, p.subtract(r2).subtract(u2), p.add(r2).subtract(u2), p.add(r2).add(u2), p.subtract(r2).add(u2), 0xE0D8FF, alpha * 0.5f, alpha * 0.5f,
				alpha * 0.5f, alpha * 0.5f);
	}

	/**
	 * A tear in the world, upright, always turned to the camera: a ragged lens of starry dark inside a violet edge,
	 * white-hot at the seam. It rips open with an overshoot, its edges flicker, and it seals shut to a bright line.
	 */
	private static final class Rift extends Effect {
		final Vec3d at;
		final float height;
		final float width;
		final long seed;

		Rift(int ticks, int rgb, Vec3d at, float height, float width) {
			super(ticks, rgb);
			this.at = at;
			this.height = height;
			this.width = width;
			this.seed = Double.doubleToLongBits(at.x * 13 + at.y * 7 + at.z * 3 + ticks);
		}

		@Override
		boolean inks() {
			return true;
		}

		@Override
		float inkStrength() {
			return 1.0f;
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
			float life = age + delta;
			float open = life < 5 ? easeOutBack(life / 5f) : 1;
			float shut = Math.min(1, (ticks - life) / 6f);
			float wide = Math.max(0, open * shut);
			Vec3d toCam = cam.subtract(at).multiply(1, 0, 1);
			Vec3d facing = toCam.lengthSquared() < 1.0E-4 ? new Vec3d(0, 0, 1) : toCam.normalize();
			Vec3d right = facing.crossProduct(new Vec3d(0, 1, 0)).normalize();
			Vec3d up = new Vec3d(0, 1, 0);
			int n = 22;
			Vec3d[] left = new Vec3d[n + 1];
			Vec3d[] rightEdge = new Vec3d[n + 1];
			Vec3d[] mid = new Vec3d[n + 1];
			for (int k = 0; k <= n; k++) {
				float s = (float) k / n;
				double y = (s - 0.5) * height * (0.6 + 0.4 * Math.min(1, open));
				double profile = Math.pow(Math.sin(Math.PI * s), 0.75);
				double flicker = 0.06 * Math.sin(life * 0.9 + k * 1.3);
				double l = width / 2 * profile * wide * (0.85 + 0.3 * hash(seed, k) + flicker);
				double r = width / 2 * profile * wide * (0.85 + 0.3 * hash(seed, k + 100) - flicker);
				double cx = width * 0.12 * (hash(seed, k + 200) - 0.5) * profile;
				Vec3d c = at.add(up.multiply(y)).add(right.multiply(cx));
				mid[k] = c;
				left[k] = c.subtract(right.multiply(l));
				rightEdge[k] = c.add(right.multiply(r));
			}
			if (inkPass) {
				for (int k = 0; k < n; k++) {
					quad(vc, m, left[k], left[k + 1], rightEdge[k + 1], rightEdge[k], 0x0A0418, 0.97f, 0.97f, 0.97f, 0.97f);
				}
				return;
			}
			float a = Math.min(1, life / 2f);
			for (int k = 0; k < n; k++) {
				// A soft violet halo, the violet edge, and a white-hot seam inside it
				glowLine(vc, m, left[k], left[k + 1], facing, 0.55, rgb, 0.16f * a);
				glowLine(vc, m, rightEdge[k], rightEdge[k + 1], facing, 0.55, rgb, 0.16f * a);
				glowLine(vc, m, left[k], left[k + 1], facing, 0.16, rgb, 0.9f * a);
				glowLine(vc, m, rightEdge[k], rightEdge[k + 1], facing, 0.16, rgb, 0.9f * a);
				glowLine(vc, m, left[k], left[k + 1], facing, 0.05, 0xFFFFFF, 0.7f * a);
				glowLine(vc, m, rightEdge[k], rightEdge[k + 1], facing, 0.05, 0xFFFFFF, 0.7f * a);
				// A faint swirl of violet deep inside
				quad(vc, m, mid[k], mid[k + 1], left[k + 1].lerp(mid[k + 1], 0.4), left[k].lerp(mid[k], 0.4), rgb, 0.12f * a, 0.12f * a, 0, 0);
			}
			// As it seals, only a bright seam is left
			if (shut < 1) {
				for (int k = 0; k < n; k++) {
					glowLine(vc, m, mid[k], mid[k + 1], facing, 0.12, 0xFFFFFF, (1 - shut) * 0.9f);
				}
			}
			// Stars far away inside it
			for (int i = 0; i < 16; i++) {
				int k = 2 + (int) (hash(seed, i + 300) * (n - 4));
				float u = hash(seed, i + 400) * 1.6f - 0.8f;
				Vec3d p = mid[k].lerp(u < 0 ? left[k] : rightEdge[k], Math.abs(u)).add(facing.multiply(-0.02));
				float twinkle = 0.4f + 0.6f * (0.5f + 0.5f * MathHelper.sin(life * 0.5f + i * 2.1f));
				star(vc, m, p, right, up, 0.035 + 0.03 * hash(seed, i + 500), twinkle * a * wide);
			}
		}
	}

	/**
	 * A black hole: a core of pure dark ringed by a white-hot ring of light, a disk of violet fire spiralling into
	 * it, and motes and stars falling in from all around. It swells in, holds, and collapses at the end.
	 */
	private static final class Vortex extends Effect {
		final Vec3d at;
		final float radius;
		/** The disk's plane, tilted a little so it reads as a disk from anywhere. */
		final Vec3d e1;
		final Vec3d e2;

		Vortex(int ticks, int rgb, Vec3d at, float radius) {
			super(ticks, rgb);
			this.at = at;
			this.radius = radius;
			Vec3d normal = new Vec3d(0.3, 1, 0.15).normalize();
			this.e1 = normal.crossProduct(new Vec3d(0, 0, 1)).normalize();
			this.e2 = normal.crossProduct(e1).normalize();
		}

		@Override
		boolean inks() {
			return true;
		}

		@Override
		float inkStrength() {
			return 1.0f;
		}

		@Override
		void tick() {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.world == null || age > ticks - 6) {
				return;
			}
			for (int i = 0; i < 5; i++) {
				double a = client.world.random.nextDouble() * Math.PI * 2;
				double b = (client.world.random.nextDouble() - 0.5) * Math.PI;
				Vec3d dir = new Vec3d(Math.cos(a) * Math.cos(b), Math.sin(b), Math.sin(a) * Math.cos(b));
				Vec3d p = at.add(dir.multiply(radius * (2 + client.world.random.nextDouble())));
				Vec3d tangent = dir.crossProduct(new Vec3d(0, 1, 0));
				Vec3d v = dir.multiply(-0.22).add(tangent.multiply(0.12));
				client.world.addParticle(ModParticles.VOID_MOTE, p.x, p.y, p.z, v.x, v.y, v.z);
			}
			if (age % 2 == 0) {
				double a = client.world.random.nextDouble() * Math.PI * 2;
				Vec3d p = at.add(e1.multiply(Math.cos(a) * radius * 1.4)).add(e2.multiply(Math.sin(a) * radius * 1.4));
				client.world.addParticle(ModParticles.STAR, p.x, p.y, p.z, 0, 0, 0);
			}
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
			float life = age + delta;
			float grow = easeOutBack(Math.min(1, life / 8f));
			float shrink = Math.min(1, (ticks - life) / 6f);
			float size = grow * shrink;
			if (size <= 0.01f) {
				return;
			}
			Vec3d n = cam.subtract(at);
			n = n.lengthSquared() < 1.0E-4 ? new Vec3d(0, 0, 1) : n.normalize();
			Vec3d right = n.crossProduct(new Vec3d(0, 1, 0));
			right = right.lengthSquared() < 1.0E-4 ? new Vec3d(1, 0, 0) : right.normalize();
			Vec3d up = right.crossProduct(n).normalize();
			double core = radius * 0.32 * size;
			int seg = 36;
			if (inkPass) {
				// The core: nothing at all, and a shadow around it where the light bends away
				for (int i = 0; i < seg; i++) {
					double a0 = Math.PI * 2 * i / seg;
					double a1 = Math.PI * 2 * (i + 1) / seg;
					Vec3d p0 = at.add(right.multiply(Math.cos(a0) * core)).add(up.multiply(Math.sin(a0) * core));
					Vec3d p1 = at.add(right.multiply(Math.cos(a1) * core)).add(up.multiply(Math.sin(a1) * core));
					quad(vc, m, at, at, p1, p0, 0x000000, 1, 1, 1, 1);
					Vec3d q0 = at.add(right.multiply(Math.cos(a0) * core * 1.9)).add(up.multiply(Math.sin(a0) * core * 1.9));
					Vec3d q1 = at.add(right.multiply(Math.cos(a1) * core * 1.9)).add(up.multiply(Math.sin(a1) * core * 1.9));
					quad(vc, m, p0, p1, q1, q0, 0x0A0418, 0.75f, 0.75f, 0, 0);
				}
				return;
			}
			// The ring of light right at its edge
			for (int i = 0; i < seg; i++) {
				double a0 = Math.PI * 2 * i / seg;
				double a1 = Math.PI * 2 * (i + 1) / seg;
				Vec3d p0 = at.add(right.multiply(Math.cos(a0) * core * 1.04)).add(up.multiply(Math.sin(a0) * core * 1.04));
				Vec3d p1 = at.add(right.multiply(Math.cos(a1) * core * 1.04)).add(up.multiply(Math.sin(a1) * core * 1.04));
				glowLine(vc, m, p0, p1, n, 0.1, 0xF4EEFF, 0.95f * shrink);
				glowLine(vc, m, p0, p1, n, 0.35, rgb, 0.45f * shrink);
			}
			// The disk: three arms of light spiralling in, white-hot inside, violet, then dark at the rim
			for (int arm = 0; arm < 3; arm++) {
				List<Vec3d> points = new ArrayList<>();
				int steps = 26;
				float[] widths = new float[steps + 1];
				int[] colors = new int[steps + 1];
				float[] alphas = new float[steps + 1];
				for (int k = 0; k <= steps; k++) {
					float s = (float) k / steps;
					double r = core * 1.25 + (radius * size - core * 1.25) * s;
					double theta = arm * Math.PI * 2 / 3 + life * 0.3 - 2.4 * Math.log(r / (core * 1.25 + 1.0E-3) + 1);
					points.add(at.add(e1.multiply(Math.cos(theta) * r)).add(e2.multiply(Math.sin(theta) * r)));
					widths[k] = (float) (0.22 + 0.5 * s) * size;
					colors[k] = s < 0.3f ? lerpColor(0xFFF0FF, rgb, s / 0.3f) : lerpColor(rgb, 0x3A1C70, (s - 0.3f) / 0.7f);
					alphas[k] = (1 - s) * 0.9f * shrink;
				}
				strip(vc, m, cam, points, widths, colors, alphas);
			}
			// Faint rings of the disk itself
			for (float f : new float[] {1.4f, 2.0f, 2.8f}) {
				double r = core * f;
				if (r > radius * size) {
					continue;
				}
				for (int i = 0; i < seg; i++) {
					double a0 = Math.PI * 2 * i / seg + life * 0.05 * f;
					double a1 = Math.PI * 2 * (i + 1) / seg + life * 0.05 * f;
					Vec3d p0 = at.add(e1.multiply(Math.cos(a0) * r)).add(e2.multiply(Math.sin(a0) * r));
					Vec3d p1 = at.add(e1.multiply(Math.cos(a1) * r)).add(e2.multiply(Math.sin(a1) * r));
					glowLine(vc, m, p0, p1, e1.crossProduct(e2), 0.14, rgb, 0.3f / f * shrink);
				}
			}
		}
	}

	/** The player's own passage through the void: the view goes dark, stars rush past from the middle outwards. */
	private static final class VoidScreen extends Effect {
		VoidScreen(int ticks) {
			super(ticks, 0);
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
		}

		void paint(DrawContext ctx, int w, int h, float delta) {
			float life = age + delta;
			float a = Math.min(1, life / 2f) * Math.min(1, (ticks - life) / 4f);
			if (a <= 0) {
				return;
			}
			ctx.fill(0, 0, w, h, argb(0x07020F, 0.92f * a));
			edges(ctx, w, h, 0x7050C0, 0.55f * a, 4);
			int cx = w / 2;
			int cy = h / 2;
			double reach = Math.hypot(w, h) / 2;
			for (int i = 0; i < 110; i++) {
				double angle = hash(7, i) * Math.PI * 2;
				double speed = 0.05 + 0.08 * hash(11, i);
				double d = (hash(13, i) + life * speed) % 1;
				double dist = d * d * reach;
				int len = (int) (1 + d * 10);
				int size = d > 0.6 ? 2 : 1;
				float bright = (float) Math.min(1, d * 3) * a;
				int color = i % 5 == 0 ? 0xC8B4FF : 0xFFFFFF;
				// A streak pointing out from the middle: a few dots along its way
				for (int k = 0; k < len; k += 2) {
					double r = dist + k;
					int x = cx + (int) (Math.cos(angle) * r);
					int y = cy + (int) (Math.sin(angle) * r);
					ctx.fill(x, y, x + size, y + size, argb(color, bright * (1 - (float) k / (len + 1))));
				}
			}
		}
	}

	// ---- the races' great shapes ----

	/** Two directions square to an axis, for building round things around it. */
	private static Vec3d[] around(Vec3d axis) {
		Vec3d u = axis.crossProduct(Math.abs(axis.y) < 0.9 ? new Vec3d(0, 1, 0) : new Vec3d(1, 0, 0)).normalize();
		return new Vec3d[] {u, axis.crossProduct(u).normalize()};
	}

	/**
	 * Crystals of ice bursting up out of the ground and leaning outwards: frosted faces, bright edges, a glint running
	 * round each one. They snap up, hold, and at the end shatter, shrinking into the ground.
	 */
	private static final class Crystals extends Effect {
		final Vec3d at;
		final float radius;
		final int count;
		final float height;
		final long seed;

		Crystals(int ticks, int rgb, Vec3d at, float radius, int count, float height) {
			super(ticks, rgb);
			this.at = at;
			this.radius = radius;
			this.count = Math.max(1, count);
			this.height = height;
			this.seed = Double.doubleToLongBits(at.x * 7 + at.z * 13 + radius);
		}

		@Override
		boolean inks() {
			return true;
		}

		@Override
		float inkStrength() {
			return 0.45f;
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
			float life = age + delta;
			float grow = easeOutBack(Math.min(1, life / 5f));
			float shatter = Math.min(1, (ticks - life) / 8f);
			for (int i = 0; i < count; i++) {
				double angle = Math.PI * 2 * i / count + hash(seed, i) * 0.4;
				Vec3d out = new Vec3d(Math.cos(angle), 0, Math.sin(angle));
				Vec3d base = at.add(out.multiply(radius * (0.8 + 0.4 * hash(seed, i + 40))));
				double h = height * (0.6 + 0.6 * hash(seed, i + 80)) * grow * shatter;
				if (h < 0.02) {
					continue;
				}
				Vec3d axis = out.multiply(0.25 + 0.3 * hash(seed, i + 120)).add(0, 1, 0).normalize();
				Vec3d[] uv = around(axis);
				double w = height * 0.12 * (0.7 + 0.6 * hash(seed, i + 160)) * Math.min(1, grow);
				Vec3d[] bottom = new Vec3d[6];
				Vec3d[] top = new Vec3d[6];
				for (int k = 0; k < 6; k++) {
					double a = Math.PI * 2 * k / 6;
					Vec3d ring = uv[0].multiply(Math.cos(a)).add(uv[1].multiply(Math.sin(a)));
					bottom[k] = base.add(ring.multiply(w));
					top[k] = base.add(axis.multiply(h * 0.78)).add(ring.multiply(w * 0.8));
				}
				Vec3d tip = base.add(axis.multiply(h));
				for (int k = 0; k < 6; k++) {
					int n = (k + 1) % 6;
					if (inkPass) {
						quad(vc, m, bottom[k], bottom[n], top[n], top[k], 0xCFE8FF, 0.55f * shatter, 0.55f * shatter, 0.65f * shatter, 0.65f * shatter);
						quad(vc, m, top[k], top[n], tip, tip, 0xE8F4FF, 0.7f * shatter, 0.7f * shatter, 0.8f * shatter, 0.8f * shatter);
						continue;
					}
					quad(vc, m, bottom[k], bottom[n], top[n], top[k], rgb, 0.1f * shatter, 0.1f * shatter, 0.2f * shatter, 0.2f * shatter);
					// The glint runs round the crystal, edge after edge
					float glint = (int) (life / 2 + i) % 6 == k ? 1 : 0.55f;
					glowLine(vc, m, bottom[k], top[k], cam.subtract(bottom[k]), 0.05, 0xFFFFFF, glint * shatter);
					glowLine(vc, m, top[k], tip, cam.subtract(top[k]), 0.05, 0xFFFFFF, glint * shatter);
				}
			}
		}
	}

	/**
	 * Curtains of light like the northern lights, waving in a ring overhead: bright at their lower hem, shading into
	 * the second colour as they rise and thin out, with rays shimmering along them.
	 */
	private static final class Aurora extends Effect {
		final int rgb2;
		final Vec3d at;
		final float radius;

		Aurora(int ticks, int rgb, int rgb2, Vec3d at, float radius) {
			super(ticks, rgb);
			this.rgb2 = rgb2;
			this.at = at;
			this.radius = radius;
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
			float life = age + delta;
			float fade = Math.min(1, life / 8f) * Math.min(1, (ticks - life) / 12f);
			int n = 56;
			double hem = 2.2 + radius * 0.2;
			double tall = 1.6 + radius * 0.35;
			for (int i = 0; i < n; i++) {
				double a0 = Math.PI * 2 * i / n;
				double a1 = Math.PI * 2 * (i + 1) / n;
				Vec3d[] lo = new Vec3d[2];
				Vec3d[] mid = new Vec3d[2];
				Vec3d[] hi = new Vec3d[2];
				double[] as = {a0, a1};
				for (int k = 0; k < 2; k++) {
					double a = as[k];
					double r = radius * (1 + 0.12 * Math.sin(a * 3 + life * 0.15));
					double y = hem + 0.4 * Math.sin(a * 2 - life * 0.1);
					Vec3d foot = at.add(Math.cos(a) * r, y, Math.sin(a) * r);
					lo[k] = foot;
					mid[k] = foot.add(0, tall * 0.35, 0);
					hi[k] = foot.add(Math.cos(a) * 0.3, tall, Math.sin(a) * 0.3);
				}
				// Rays shimmer along the curtain
				float ray = (float) (0.55 + 0.45 * Math.sin(a0 * 9 + life * 0.25));
				float al = 0.55f * fade * ray;
				quad(vc, m, lo[0], lo[1], mid[1], mid[0], rgb, al, al, al * 0.7f, al * 0.7f);
				quad(vc, m, mid[0], mid[1], hi[1], hi[0], rgb2, al * 0.7f, al * 0.7f, 0, 0);
			}
		}
	}

	// ---- the Mycelids: spores and threads ----

	/** A soft round glow turned to the camera: bright in the middle, fading to nothing at its edge. */
	private static void orb(VertexConsumer vc, Matrix4f m, Vec3d c, Vec3d cam, double r, int rgb, float alpha) {
		Vec3d n = cam.subtract(c);
		if (n.lengthSquared() < 1.0E-6) {
			return;
		}
		Vec3d[] uv = around(n.normalize());
		int seg = 14;
		for (int i = 0; i < seg; i++) {
			double a0 = Math.PI * 2 * i / seg;
			double a1 = Math.PI * 2 * (i + 1) / seg;
			Vec3d p0 = c.add(uv[0].multiply(Math.cos(a0) * r)).add(uv[1].multiply(Math.sin(a0) * r));
			Vec3d p1 = c.add(uv[0].multiply(Math.cos(a1) * r)).add(uv[1].multiply(Math.sin(a1) * r));
			quad(vc, m, c, c, p1, p0, rgb, alpha, alpha, 0, 0);
		}
	}

	/**
	 * A spore cloud like a nebula: billows of soft light turning slowly round its middle, each on its own height and
	 * pace, breathing; a dark mossy haze under them gives the cloud body by day; bright motes twinkle in its depth.
	 * It swells open, lives, and thins away.
	 */
	private static final class Nebula extends Effect {
		final int rgb2;
		final Vec3d at;
		final int follow;
		final float radius;
		final long seed;

		Nebula(int ticks, int rgb, int rgb2, Vec3d at, int follow, float radius) {
			super(ticks, rgb);
			this.rgb2 = rgb2;
			this.at = at;
			this.follow = follow;
			this.radius = radius;
			this.seed = Double.doubleToLongBits(at.x * 11 + at.z * 19 + radius);
		}

		@Override
		boolean inks() {
			return true;
		}

		@Override
		float inkStrength() {
			return 1.0f;
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
			Vec3d center = at;
			if (follow >= 0) {
				Vec3d riding = lerped(follow, delta);
				if (riding != null) {
					center = riding;
				}
			}
			float life = age + delta;
			float open = easeOut(Math.min(1, life / 10f));
			float fade = Math.min(1, (ticks - life) / 15f);
			int billows = 34;
			for (int i = 0; i < billows; i++) {
				double turn = hash(seed, i) * Math.PI * 2 + life * 0.025 * (0.4 + hash(seed, i + 300));
				double dist = radius * Math.sqrt(hash(seed, i + 100)) * open;
				double y = 0.3 + hash(seed, i + 200) * radius * 0.7 + Math.sin(life * 0.08 + i) * 0.15;
				Vec3d p = center.add(Math.cos(turn) * dist, y, Math.sin(turn) * dist);
				double size = radius * 0.4 * (0.6 + 0.8 * hash(seed, i + 400)) * open * (0.9 + 0.1 * Math.sin(life * 0.15 + i));
				float edge = (float) (1 - 0.5 * dist / (radius + 0.01));
				if (inkPass) {
					orb(vc, m, p, cam, size * 1.15, 0x173A16, 0.32f * fade * edge);
				} else {
					orb(vc, m, p, cam, size, i % 3 == 0 ? rgb2 : rgb, 0.16f * fade * edge);
				}
			}
			if (inkPass) {
				return;
			}
			// Motes deep in the cloud, each twinkling on its own beat
			for (int i = 0; i < 26; i++) {
				double turn = hash(seed, i + 500) * Math.PI * 2 - life * 0.04;
				double dist = radius * 0.85 * hash(seed, i + 600) * open;
				double y = 0.4 + hash(seed, i + 700) * radius * 0.8 + Math.sin(life * 0.12 + i * 2) * 0.25;
				Vec3d p = center.add(Math.cos(turn) * dist, y, Math.sin(turn) * dist);
				float tw = 0.5f + 0.5f * MathHelper.sin(life * 0.35f + i * 1.7f);
				orb(vc, m, p, cam, 0.07 + 0.05 * tw, 0xE6FFDC, 0.9f * tw * fade);
			}
		}
	}

	/**
	 * A comet of spores: a glowing head flying in an arc from one spot to another, its tail of fading puffs behind,
	 * spores shed along the way.
	 */
	private static final class Comet extends Effect {
		final Vec3d from;
		final Vec3d to;
		final double rise;

		Comet(int ticks, int rgb, Vec3d from, Vec3d to) {
			super(ticks + 6, rgb);
			this.from = from.add(0, 1, 0);
			this.to = to.add(0, 1, 0);
			this.rise = 1.0 + from.distanceTo(to) * 0.25;
		}

		/** Where the head is at a share of the way: on a raised arc, a little faster in the middle. */
		private Vec3d at(double s) {
			s = MathHelper.clamp(s, 0, 1);
			double e = s * s * (3 - 2 * s);
			return from.lerp(to, e).add(0, Math.sin(Math.PI * e) * rise, 0);
		}

		private double share(float life) {
			return life / (ticks - 6);
		}

		@Override
		boolean inks() {
			return true;
		}

		@Override
		float inkStrength() {
			return 0.6f;
		}

		@Override
		void tick() {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.world == null || age > ticks - 6) {
				return;
			}
			Vec3d head = at(share(age));
			for (int i = 0; i < 3; i++) {
				client.world.addParticle(ModParticles.SPORE, head.x, head.y, head.z, (client.world.random.nextDouble() - 0.5) * 0.06, 0,
						(client.world.random.nextDouble() - 0.5) * 0.06);
			}
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
			float life = age + delta;
			double s = share(life);
			float fade = s > 1 ? (float) Math.max(0, 1 - (s - 1) * 4) : 1;
			// The tail: puffs left behind, smaller and dimmer the older
			for (int k = 14; k >= 0; k--) {
				double t = s - k * 0.035;
				if (t < 0) {
					continue;
				}
				Vec3d p = at(t);
				double size = 0.45 * (1 - k / 16.0);
				if (inkPass) {
					orb(vc, m, p, cam, size * 1.2, 0x173A16, 0.35f * fade * (1 - k / 15f));
				} else {
					orb(vc, m, p, cam, size, rgb, 0.3f * fade * (1 - k / 15f));
				}
			}
			if (!inkPass && s <= 1) {
				Vec3d head = at(s);
				orb(vc, m, head, cam, 0.7, rgb, 0.45f * fade);
				orb(vc, m, head, cam, 0.25, 0xF0FFE8, 0.95f * fade);
			}
		}
	}

	/**
	 * Threads of mycelium tearing up out of the ground in a ring and curling in towards a point above its middle, a bud
	 * of light at each tip, swaying; they grow fast and at the end draw back into the ground.
	 */
	private static final class Threads extends Effect {
		final Vec3d at;
		final float radius;
		final float height;
		final int count;
		final long seed;

		Threads(int ticks, int rgb, Vec3d at, float radius, float height, int count) {
			super(ticks, rgb);
			this.at = at;
			this.radius = radius;
			this.height = height;
			this.count = Math.max(1, count);
			this.seed = Double.doubleToLongBits(at.x * 5 + at.z * 9 + height);
		}

		@Override
		boolean inks() {
			return true;
		}

		@Override
		float inkStrength() {
			return 0.8f;
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
			float life = age + delta;
			float grow = easeOut(Math.min(1, life / 7f));
			float back = Math.min(1, (ticks - life) / 10f);
			float reach = grow * back;
			if (reach <= 0.01f) {
				return;
			}
			int n = 18;
			for (int i = 0; i < count; i++) {
				double angle = Math.PI * 2 * i / count + hash(seed, i) * 0.5;
				Vec3d out = new Vec3d(Math.cos(angle), 0, Math.sin(angle));
				Vec3d base = at.add(out.multiply(radius * (0.75 + 0.5 * hash(seed, i + 30))));
				double h = height * (0.6 + 0.5 * hash(seed, i + 60));
				double sway = Math.sin(life * 0.25 + i * 1.3) * 0.25;
				Vec3d tip = at.add(out.multiply(radius * 0.15)).add(new Vec3d(-out.z, 0, out.x).multiply(sway)).add(0, h, 0);
				// Up out of the ground first, then curling in over the middle
				Vec3d c1 = base.add(out.multiply(0.3)).add(0, h * 0.7, 0);
				Vec3d c2 = tip.add(out.multiply(radius * 0.5)).add(0, h * 0.25, 0);
				List<Vec3d> points = new ArrayList<>();
				float[] widths = new float[n + 1];
				int[] colors = new int[n + 1];
				float[] alphas = new float[n + 1];
				for (int k = 0; k <= n; k++) {
					double s = (double) k / n * reach;
					double u = 1 - s;
					points.add(base.multiply(u * u * u).add(c1.multiply(3 * u * u * s)).add(c2.multiply(3 * u * s * s)).add(tip.multiply(s * s * s)));
					float along = (float) k / n;
					widths[k] = (float) (0.09 * (1 - along) + 0.025);
					colors[k] = lerpColor(inkPass ? 0x1E4A1C : rgb, inkPass ? 0x1E4A1C : 0xF0FFE8, along * along);
					alphas[k] = 0.85f * back;
				}
				strip(vc, m, cam, points, widths, colors, alphas);
				if (!inkPass) {
					// The bud of light at the growing tip
					float tw = 0.7f + 0.3f * MathHelper.sin(life * 0.4f + i);
					orb(vc, m, points.get(n), cam, 0.16 * tw, 0xF0FFE8, 0.9f * back);
					orb(vc, m, points.get(n), cam, 0.35, rgb, 0.25f * back);
				}
			}
		}
	}

	// ---- shapes of the last three races ----

	/** Right, up and toward-the-camera at a point, for flat things that face the viewer. */
	private static Vec3d[] facing(Vec3d p, Vec3d cam) {
		Vec3d n = cam.subtract(p);
		n = n.lengthSquared() < 1.0E-4 ? new Vec3d(0, 0, 1) : n.normalize();
		return frame(n);
	}

	/** Right and up of a flat thing whose face looks along {@code n}, upright in the world. */
	private static Vec3d[] frame(Vec3d n) {
		Vec3d right = new Vec3d(0, 1, 0).crossProduct(n);
		right = right.lengthSquared() < 1.0E-4 ? new Vec3d(1, 0, 0) : right.normalize();
		Vec3d up = n.crossProduct(right).normalize();
		return new Vec3d[] {right, up, n};
	}

	private static Entity entity(int id) {
		MinecraftClient client = MinecraftClient.getInstance();
		return client.world == null ? null : client.world.getEntityById(id);
	}

	/** A filled shape: quads fanned from {@code center} to its {@code rim}, solid inside, {@code rimAlpha} at the edge. */
	private static void fan(VertexConsumer vc, Matrix4f m, Vec3d center, List<Vec3d> rim, int rgb, float alpha, float rimAlpha) {
		for (int i = 0; i < rim.size(); i++) {
			Vec3d a = rim.get(i);
			Vec3d b = rim.get((i + 1) % rim.size());
			quad(vc, m, center, center, b, a, rgb, alpha, alpha, rimAlpha, rimAlpha);
		}
	}

	/** A glowing outline through {@code points}, closed or open. */
	private static void outline(VertexConsumer vc, Matrix4f m, List<Vec3d> points, boolean closed, Vec3d normal, double width, int rgb, float alpha) {
		int n = closed ? points.size() : points.size() - 1;
		for (int i = 0; i < n; i++) {
			glowLine(vc, m, points.get(i), points.get((i + 1) % points.size()), normal, width, rgb, alpha);
		}
	}

	/** A heart's outline, {@code size} half-tall, in the plane of {@code right} and {@code up}; from..to along the curve (0..2π whole). */
	private static List<Vec3d> heart(Vec3d c, Vec3d right, Vec3d up, double size, int n, double from, double to) {
		List<Vec3d> points = new ArrayList<>();
		for (int i = 0; i <= n; i++) {
			double t = from + (to - from) * i / n;
			double s = Math.sin(t);
			double x = s * s * s;
			double y = (13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t)) / 16 + 0.12;
			points.add(c.add(right.multiply(x * size)).add(up.multiply(y * size)));
		}
		return points;
	}

	/** A coin: a gold disc in the plane of {@code e1} and {@code e2}, a bright rim, an inner ring, and a glint when it turns edge-on. */
	private static void drawCoin(VertexConsumer vc, Matrix4f m, Vec3d c, Vec3d e1, Vec3d e2, double r, float alpha, Vec3d cam) {
		List<Vec3d> rim = new ArrayList<>();
		List<Vec3d> inner = new ArrayList<>();
		int seg = 14;
		for (int i = 0; i < seg; i++) {
			double a = Math.PI * 2 * i / seg;
			Vec3d d = e1.multiply(Math.cos(a)).add(e2.multiply(Math.sin(a)));
			rim.add(c.add(d.multiply(r)));
			inner.add(c.add(d.multiply(r * 0.66)));
		}
		Vec3d normal = e1.crossProduct(e2);
		if (inkPass) {
			fan(vc, m, c, rim, 0xB8862A, alpha, alpha);
			return;
		}
		fan(vc, m, c, rim, 0xF2C94C, 0.25f * alpha, 0.1f * alpha);
		outline(vc, m, rim, true, normal, r * 0.3, 0xF2C94C, 0.8f * alpha);
		outline(vc, m, inner, true, normal, r * 0.12, 0xFFE9A8, 0.5f * alpha);
		// Edge-on to the eye, the coin flashes
		Vec3d toCam = cam.subtract(c);
		if (toCam.lengthSquared() > 1.0E-4) {
			double edge = Math.abs(normal.normalize().dotProduct(toCam.normalize()));
			if (edge < 0.25) {
				Vec3d[] f = facing(c, cam);
				star(vc, m, c, f[0], f[1], Math.min(0.1, r * 0.45), 0.8f * (float) (1 - edge / 0.25) * alpha);
			}
		}
	}

	/** A face: a pale mask with a narrow chin, dark eye holes glowing sickly green inside, and a thin slit of a mouth. */
	private static void drawMask(VertexConsumer vc, Matrix4f m, Vec3d c, Vec3d n, double size, int rgb, float alpha, float eyes) {
		Vec3d[] f = frame(n);
		Vec3d right = f[0];
		Vec3d up = f[1];
		double w = size * 0.72;
		double h = size;
		List<Vec3d> rim = new ArrayList<>();
		int seg = 24;
		for (int i = 0; i < seg; i++) {
			double a = Math.PI * 2 * i / seg;
			double y = Math.cos(a);
			double x = Math.sin(a) * (y < 0 ? 1 + 0.32 * y : 1);
			// Brushed back a little at the temples, so it reads as a shell rather than a plate
			Vec3d bulge = n.multiply(-0.12 * size * (1 - Math.abs(Math.sin(a))));
			rim.add(c.add(right.multiply(x * w)).add(up.multiply(y * h)).add(bulge));
		}
		Vec3d front = c.add(n.multiply(0.1 * size));
		List<List<Vec3d>> holes = new ArrayList<>();
		for (int side = -1; side <= 1; side += 2) {
			List<Vec3d> eye = new ArrayList<>();
			Vec3d ec = front.add(right.multiply(side * 0.36 * w)).add(up.multiply(0.16 * h)).add(n.multiply(0.01));
			for (int i = 0; i < 12; i++) {
				double a = Math.PI * 2 * i / 12;
				// Slanted, narrowing to the nose: an unkind look
				double ey = Math.sin(a) * 0.11 * h - side * Math.cos(a) * 0.04 * h;
				eye.add(ec.add(right.multiply(Math.cos(a) * 0.22 * w)).add(up.multiply(ey)));
			}
			holes.add(eye);
		}
		if (inkPass) {
			fan(vc, m, front, rim, rgb, alpha, alpha);
			for (List<Vec3d> eye : holes) {
				fan(vc, m, centroid(eye), eye, 0x0E0B08, alpha, alpha);
			}
			return;
		}
		outline(vc, m, rim, true, n, size * 0.07, rgb, 0.85f * alpha);
		outline(vc, m, rim, true, n, size * 0.02, 0xFFFFFF, 0.5f * alpha);
		for (List<Vec3d> eye : holes) {
			Vec3d ec = centroid(eye);
			List<Vec3d> glint = new ArrayList<>();
			for (Vec3d p : eye) {
				glint.add(ec.lerp(p, 0.55));
			}
			fan(vc, m, ec, glint, 0xB8E07A, 0.9f * alpha * eyes, 0.2f * alpha * eyes);
			outline(vc, m, eye, true, n, size * 0.03, 0xB8E07A, 0.5f * alpha * eyes);
		}
		Vec3d mouth = front.add(up.multiply(-0.5 * h));
		glowLine(vc, m, mouth.add(right.multiply(-0.22 * w)), mouth.add(right.multiply(0.22 * w)).add(up.multiply(0.03 * h)), n, size * 0.04, 0x3A332A, 0.8f * alpha);
	}

	private static Vec3d centroid(List<Vec3d> points) {
		Vec3d sum = Vec3d.ZERO;
		for (Vec3d p : points) {
			sum = sum.add(p);
		}
		return sum.multiply(1.0 / points.size());
	}

	private static float smooth(float t) {
		t = MathHelper.clamp(t, 0, 1);
		return t * t * (3 - 2 * t);
	}

	private static Vec3d bezier(Vec3d a, Vec3d c, Vec3d b, double t) {
		return a.multiply((1 - t) * (1 - t)).add(c.multiply(2 * (1 - t) * t)).add(b.multiply(t * t));
	}

	/** Gold coins flung out of a point: each on its own arc, flipping over, flashing edge-on, settling on the ground. */
	private static final class Coins extends Effect {
		final Vec3d at;
		final int count;
		final float speed;
		final long seed;

		Coins(int ticks, Vec3d at, int count, float speed) {
			super(ticks, Fx.INK | 0xF2C94C);
			this.at = at;
			this.count = Math.max(1, count);
			this.speed = speed;
			this.seed = Double.doubleToLongBits(at.x * 3 + at.y * 5 + at.z * 11 + count);
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
			float life = age + delta;
			float fade = Math.min(1, (ticks - life) / 8f) * Math.min(1, life / 1.5f);
			for (int i = 0; i < count; i++) {
				double ang = hash(seed, i) * Math.PI * 2;
				double out = speed * (0.05 + 0.09 * hash(seed, i + 50));
				double rise = speed * (0.12 + 0.14 * hash(seed, i + 100));
				// Up and over under gravity until it lands 0.9 below where it started, then it lies there
				double landed = (rise + Math.sqrt(rise * rise + 4 * 0.015 * 0.9)) / 0.03;
				double flight = Math.min(life, landed);
				double y = rise * flight - 0.015 * flight * flight;
				Vec3d dir = new Vec3d(Math.cos(ang), 0, Math.sin(ang));
				Vec3d p = at.add(dir.multiply(out * flight)).add(0, y, 0);
				double spin = life * (0.5 + 0.4 * hash(seed, i + 150)) + hash(seed, i + 200) * 6;
				// Lying still once down
				if (life >= landed) {
					spin = Math.PI / 2;
				}
				Vec3d e1 = new Vec3d(-dir.z, 0, dir.x);
				Vec3d e2 = new Vec3d(0, 1, 0).multiply(Math.cos(spin)).add(dir.multiply(Math.sin(spin)));
				drawCoin(vc, m, p, e1, e2, 0.13, fade, cam);
			}
		}
	}

	/** Two orbs trading places: one goes along an arc to the other side, the other comes back the other way, trailing light. */
	private static final class Exchange extends Effect {
		final int rgb2;
		final int a;
		final int b;

		Exchange(int ticks, int rgb, int rgb2, int a, int b) {
			super(ticks, rgb);
			this.rgb2 = rgb2 & 0xFFFFFF;
			this.a = a;
			this.b = b;
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
			Entity ea = entity(a);
			Entity eb = entity(b);
			if (ea == null || eb == null) {
				return;
			}
			Vec3d pa = ea.getLerpedPos(delta).add(0, ea.getHeight() * 0.6, 0);
			Vec3d pb = eb.getLerpedPos(delta).add(0, eb.getHeight() * 0.6, 0);
			Vec3d mid = pa.lerp(pb, 0.5);
			Vec3d side = pb.subtract(pa).crossProduct(new Vec3d(0, 1, 0));
			side = side.lengthSquared() < 1.0E-4 ? new Vec3d(1, 0, 0) : side.normalize();
			double span = pa.distanceTo(pb);
			Vec3d c1 = mid.add(side.multiply(0.35 * span + 0.6)).add(0, 0.4 * span + 0.3, 0);
			Vec3d c2 = mid.subtract(side.multiply(0.35 * span + 0.6)).add(0, 0.4 * span + 0.3, 0);
			float t = progress(delta);
			float go = smooth(t / 0.8f);
			float fade = t < 0.8f ? 1 : 1 - (t - 0.8f) / 0.2f;
			orb(vc, m, cam, pa, c1, pb, go, rgb, fade);
			orb(vc, m, cam, pb, c2, pa, go, rgb2, fade);
		}

		private void orb(VertexConsumer vc, Matrix4f m, Vec3d cam, Vec3d from, Vec3d ctrl, Vec3d to, float t, int color, float fade) {
			List<Vec3d> trail = new ArrayList<>();
			int n = 12;
			float[] widths = new float[n + 1];
			int[] colors = new int[n + 1];
			float[] alphas = new float[n + 1];
			for (int k = 0; k <= n; k++) {
				double s = Math.max(0, t - (n - k) * 0.03);
				trail.add(bezier(from, ctrl, to, s));
				float head = (float) k / n;
				widths[k] = 0.06f + 0.36f * head;
				colors[k] = lerpColor(color, 0xFFFFFF, head * 0.3f);
				alphas[k] = head * 0.8f * fade;
			}
			strip(vc, m, cam, trail, widths, colors, alphas);
			Vec3d p = bezier(from, ctrl, to, t);
			Vec3d[] f = facing(p, cam);
			List<Vec3d> rim = new ArrayList<>();
			List<Vec3d> core = new ArrayList<>();
			for (int i = 0; i < 12; i++) {
				double a = Math.PI * 2 * i / 12;
				Vec3d d = f[0].multiply(Math.cos(a)).add(f[1].multiply(Math.sin(a)));
				rim.add(p.add(d.multiply(0.42)));
				core.add(p.add(d.multiply(0.14)));
			}
			fan(vc, m, p, rim, color, 0.7f * fade, 0);
			fan(vc, m, p, core, 0xFFFFFF, 0.9f * fade, 0.3f * fade);
			// Arrived: a flash where it lands
			if (t >= 0.98f) {
				Vec3d[] g = facing(to, cam);
				star(vc, m, to, g[0], g[1], 0.25, fade);
			}
		}
	}

	/**
	 * A great seal of copper and gold coming down on a creature from above, turning, stamped onto it with a flash and a
	 * wave along the ground; then only its notched rim is left, turning slowly round the waist until the pledge ends.
	 */
	private static final class Seal extends Effect {
		final int on;
		final float radius;

		Seal(int ticks, int rgb, int on, float radius) {
			super(ticks, rgb);
			this.on = on;
			this.radius = radius;
		}

		@Override
		boolean inks() {
			return true;
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
			Vec3d base = lerped(on, delta);
			if (base == null) {
				return;
			}
			float life = age + delta;
			float drop = Math.min(1, life / 8f);
			Vec3d up = new Vec3d(0, 1, 0);
			double turn = life * 0.12;
			Vec3d e1 = new Vec3d(Math.cos(turn), 0, Math.sin(turn));
			Vec3d e2 = new Vec3d(-Math.sin(turn), 0, Math.cos(turn));
			if (drop < 1) {
				// Falling, faster and faster
				Vec3d c = base.add(0, 3.4 - 2.4 * drop * drop, 0);
				List<Vec3d> rim = circle(c, e1, e2, radius, 32);
				if (inkPass) {
					fan(vc, m, c, rim, 0x8A4E22, 0.9f, 0.9f);
					return;
				}
				stamp(vc, m, c, e1, e2, up, 1);
				return;
			}
			if (inkPass) {
				return;
			}
			float after = life - 8;
			// The stamp: a flash at the waist and a wave along the ground
			if (after < 6) {
				float k = after / 6;
				flatRing(vc, m, base.add(0, 0.05, 0), radius * (1 + 3 * k), 0.4 * (1 - k) + 0.05, rgb, 0.8f * (1 - k), 40);
				flatRing(vc, m, base.add(0, 1.0, 0), radius * (1 + 0.6 * k), 0.12, 0xFFFFFF, 0.9f * (1 - k), 40);
			}
			// The rim left behind, turning round the waist and fading as the pledge runs out
			float hold = Math.min(1, (ticks - life) / 10f);
			stamp(vc, m, base.add(0, 1.0 + 0.06 * Math.sin(life * 0.15), 0), e1, e2, up, 0.55f * hold);
		}

		private static List<Vec3d> circle(Vec3d c, Vec3d e1, Vec3d e2, double r, int seg) {
			List<Vec3d> points = new ArrayList<>();
			for (int i = 0; i < seg; i++) {
				double a = Math.PI * 2 * i / seg;
				points.add(c.add(e1.multiply(Math.cos(a) * r)).add(e2.multiply(Math.sin(a) * r)));
			}
			return points;
		}

		/** The seal's face: a double rim with sixteen notches, and the mage's eye in the middle. */
		private void stamp(VertexConsumer vc, Matrix4f m, Vec3d c, Vec3d e1, Vec3d e2, Vec3d up, float alpha) {
			outline(vc, m, circle(c, e1, e2, radius, 40), true, up, 0.16, rgb, 0.9f * alpha);
			outline(vc, m, circle(c, e1, e2, radius * 0.78, 40), true, up, 0.06, 0xFFE9A8, 0.7f * alpha);
			for (int i = 0; i < 16; i++) {
				double a = Math.PI * 2 * i / 16;
				Vec3d d = e1.multiply(Math.cos(a)).add(e2.multiply(Math.sin(a)));
				glowLine(vc, m, c.add(d.multiply(radius * 0.8)), c.add(d.multiply(radius * 0.97)), up, 0.05, rgb, 0.8f * alpha);
			}
			// The eye: a lens of two arcs and a round pupil
			List<Vec3d> lens = new ArrayList<>();
			for (int i = 0; i <= 12; i++) {
				double s = -1 + 2.0 * i / 12;
				lens.add(c.add(e1.multiply(s * radius * 0.55)).add(e2.multiply((1 - s * s) * radius * 0.25)));
			}
			for (int i = 12; i >= 0; i--) {
				double s = -1 + 2.0 * i / 12;
				lens.add(c.add(e1.multiply(s * radius * 0.55)).add(e2.multiply(-(1 - s * s) * radius * 0.25)));
			}
			outline(vc, m, lens, true, up, 0.07, rgb, 0.85f * alpha);
			outline(vc, m, circle(c, e1, e2, radius * 0.12, 16), true, up, 0.08, 0xFFFFFF, 0.9f * alpha);
		}
	}

	/**
	 * The door of the Mage's Shop: an arched frame of gold traces itself in the air and two copper leaves swing open on a
	 * warm light with coins drifting out. It stays open as long as its Mercenary is in the Shop, then swings shut and
	 * fades (also if the Mercenary is gone or has walked away).
	 */
	private static final class Door extends Effect {
		private static final int TRACE = 10;
		private static final int OPEN = 12;
		private static final int CLOSE = 18;
		final Vec3d at;
		final Vec3d forward;
		final Vec3d right;
		final int owner;
		/** When it began to shut (its age), or -1 while it's open. */
		float closingAt = -1;

		Door(int ticks, int rgb, Vec3d at, float yaw, int owner) {
			super(ticks, rgb);
			this.at = at;
			double r = Math.toRadians(yaw);
			this.forward = new Vec3d(-Math.sin(r), 0, Math.cos(r));
			this.right = new Vec3d(Math.cos(r), 0, Math.sin(r));
			this.owner = owner;
		}

		void close() {
			if (closingAt < 0) {
				closingAt = age;
			}
		}

		@Override
		boolean inks() {
			return true;
		}

		@Override
		void tick() {
			Entity e = entity(owner);
			if (e == null || !e.isAlive() || e.squaredDistanceTo(at) > 36) {
				close();
			}
			if (closingAt >= 0 && age - closingAt >= CLOSE) {
				age = ticks;
			}
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
			float life = age + delta;
			double w = 1.8;
			double hs = 2.3;
			Vec3d up = new Vec3d(0, 1, 0);
			List<Vec3d> frame = new ArrayList<>();
			frame.add(at.add(right.multiply(-w / 2)));
			frame.add(at.add(right.multiply(-w / 2)).add(0, hs, 0));
			for (int i = 1; i < 16; i++) {
				double a = Math.PI - Math.PI * i / 16;
				frame.add(at.add(right.multiply(Math.cos(a) * w / 2)).add(0, hs + Math.sin(a) * w / 2, 0));
			}
			frame.add(at.add(right.multiply(w / 2)).add(0, hs, 0));
			frame.add(at.add(right.multiply(w / 2)));
			float traced = Math.min(1, life / TRACE);
			float open = easeOut(MathHelper.clamp((life - TRACE) / OPEN, 0, 1));
			float fade = 1;
			if (closingAt >= 0) {
				float k = (life - closingAt) / CLOSE;
				open = Math.min(open, 1 - smoothstep(k / 0.6f));
				fade = 1 - smoothstep((k - 0.5f) / 0.5f);
			}
			double angle = Math.toRadians(105) * open;
			for (int side = -1; side <= 1; side += 2) {
				Vec3d hinge = at.add(right.multiply(side * w / 2));
				Vec3d along = right.multiply(-side * Math.cos(angle)).add(forward.multiply(-Math.sin(angle)));
				Vec3d b0 = hinge;
				Vec3d b1 = hinge.add(along.multiply(w / 2));
				Vec3d t0 = b0.add(0, hs, 0);
				Vec3d t1 = b1.add(0, hs, 0);
				if (traced >= 1) {
					if (inkPass) {
						quad(vc, m, b0, b1, t1, t0, 0x6A3A18, 0.95f * fade, 0.95f * fade, 0.95f * fade, 0.95f * fade);
					} else {
						Vec3d normal = along.crossProduct(up);
						outline(vc, m, List.of(b0, b1, t1, t0), true, normal, 0.06, rgb, 0.8f * fade);
						glowLine(vc, m, b0.lerp(t0, 0.3), b1.lerp(t1, 0.3), normal, 0.04, rgb, 0.5f * fade);
						glowLine(vc, m, b0.lerp(t0, 0.75), b1.lerp(t1, 0.75), normal, 0.04, rgb, 0.5f * fade);
					}
				}
			}
			if (inkPass) {
				return;
			}
			int shown = Math.max(1, (int) Math.ceil((frame.size() - 1) * traced));
			for (int i = 0; i < shown; i++) {
				glowLine(vc, m, frame.get(i), frame.get(i + 1), forward, 0.18, rgb, 0.85f * fade);
				glowLine(vc, m, frame.get(i), frame.get(i + 1), forward, 0.05, 0xFFFFFF, 0.7f * fade);
			}
			if (open > 0) {
				// The light inside breathes while the door stands open, and coins drift out towards the Mercenary
				float breath = 0.85f + 0.15f * MathHelper.sin(life * 0.12f);
				Vec3d mid = at.add(0, hs / 2, 0);
				fan(vc, m, mid.add(0, 0.3, 0), new ArrayList<>(frame), 0xFFD27A, 0.55f * open * fade * breath, 0.15f * open * fade);
				for (int i = 0; i < 6; i++) {
					float s = ((life * 0.03f + i / 6f) % 1);
					Vec3d p = at.add(right.multiply((hash(i, 3) - 0.5) * w * 0.7)).add(0, 0.4 + hs * hash(i, 5) + s * 0.6, 0)
							.add(forward.multiply(-s * 1.6));
					double spin = life * 0.4 + i;
					Vec3d e2 = up.multiply(Math.cos(spin)).add(forward.multiply(Math.sin(spin)));
					drawCoin(vc, m, p, right, e2, 0.09, open * fade * (1 - s), cam);
				}
			}
		}
	}

	/** A giant coin tossed over a head: it climbs spinning end over end, hangs, falls onto the head and bursts in light. */
	private static final class Flip extends Effect {
		final int over;

		Flip(int ticks, int rgb, int over) {
			super(ticks, rgb);
			this.over = over;
		}

		@Override
		boolean inks() {
			return true;
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
			Entity e = entity(over);
			if (e == null) {
				return;
			}
			Vec3d base = e.getLerpedPos(delta);
			float t = progress(delta);
			float life = age + delta;
			double top = e.getHeight();
			double y;
			if (t < 0.55f) {
				y = top + 0.6 + 1.1 * easeOut(t / 0.55f);
			} else if (t < 0.75f) {
				float k = (t - 0.55f) / 0.2f;
				y = top + 1.7 - 1.4 * k * k;
			} else {
				y = top + 0.3;
			}
			Vec3d c = base.add(0, y, 0);
			if (t < 0.75f) {
				double spin = life * 0.9;
				Vec3d e1 = new Vec3d(1, 0, 0);
				Vec3d e2 = new Vec3d(0, Math.cos(spin), Math.sin(spin));
				drawCoin(vc, m, c, e1, e2, 0.5, 1, cam);
				return;
			}
			if (inkPass) {
				return;
			}
			// Landed: a burst of gold around the head and a wave at the feet
			float k = (t - 0.75f) / 0.25f;
			flatRing(vc, m, base.add(0, 0.05, 0), 0.5 + 3.5 * k, 0.3 * (1 - k) + 0.05, rgb, 0.8f * (1 - k), 40);
			Vec3d[] f = facing(c, cam);
			star(vc, m, c, f[0], f[1], 0.35 * (1 - k) + 0.08, 1 - k);
		}
	}

	// ---- solid bodies: hearts with thickness, wings of bone and skin ----

	private static final Vec3d LIGHT = new Vec3d(0.35, 1, 0.25).normalize();

	/** A colour lit as a face turned along {@code normal} would be: a soft key light from above, never fully dark. */
	private static int shade(int rgb, Vec3d normal, float floor) {
		double lit = normal.lengthSquared() < 1.0E-8 ? 0.7 : Math.abs(normal.normalize().dotProduct(LIGHT));
		return lerpColor(0x000000, rgb, (float) (floor + (1 - floor) * lit));
	}

	/**
	 * A flat outline given thickness: a front and a back face {@code depth} apart along {@code n}, and the band round
	 * its edge; each face lit by where it turns. Drawn in the solid pass only.
	 */
	private static void extrude(VertexConsumer vc, Matrix4f m, Vec3d center, List<Vec3d> rim, Vec3d n, double depth, int front, int side, float alpha) {
		Vec3d h = n.multiply(depth / 2);
		Vec3d cf = center.add(h);
		Vec3d cb = center.subtract(h);
		int lit = shade(front, n, 0.55f);
		int back = lerpColor(0x000000, lit, 0.7f);
		for (int i = 0; i < rim.size(); i++) {
			Vec3d a = rim.get(i);
			Vec3d b = rim.get((i + 1) % rim.size());
			quad(vc, m, cf, cf, b.add(h), a.add(h), lit, alpha, alpha, alpha, alpha);
			quad(vc, m, cb, cb, b.subtract(h), a.subtract(h), back, alpha, alpha, alpha, alpha);
			Vec3d sideNormal = b.subtract(a).crossProduct(n);
			quad(vc, m, a.add(h), b.add(h), b.subtract(h), a.subtract(h), shade(side, sideNormal, 0.35f), alpha, alpha, alpha, alpha);
		}
	}

	/**
	 * A heart with a body: deep rose faces and a darker wine edge in the solid pass; in the glow pass a bright rim on
	 * the face, a glint on the upper lobe and a soft halo round it.
	 */
	private static void heart3d(VertexConsumer vc, Matrix4f m, Vec3d c, Vec3d right, Vec3d up, Vec3d n, double size, int rgb, float alpha, Vec3d cam) {
		if (inkPass || alpha <= 0.01f) {
			return;
		}
		List<Vec3d> rim = heart(c, right, up, size, 32, 0, Math.PI * 2);
		rim.remove(rim.size() - 1);
		double depth = size * 0.45;
		if (solidPass) {
			extrude(vc, m, c, rim, n, depth, lerpColor(rgb, 0xC0144A, 0.45f), 0x5A0A28, alpha);
			return;
		}
		Vec3d toCam = cam.subtract(c);
		Vec3d face = toCam.dotProduct(n) >= 0 ? n : n.multiply(-1);
		Vec3d h = face.multiply(depth / 2 + 0.004);
		List<Vec3d> front = new ArrayList<>();
		for (Vec3d p : rim) {
			front.add(p.add(h));
		}
		outline(vc, m, front, true, face, size * 0.1, rgb, 0.9f * alpha);
		outline(vc, m, front, true, face, size * 0.03, 0xFFE6F0, 0.7f * alpha);
		// The glint: a small bright oval on the upper left lobe
		Vec3d g = c.add(h).add(right.multiply(-0.42 * size)).add(up.multiply(0.42 * size));
		List<Vec3d> glint = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			double a = Math.PI * 2 * i / 10;
			glint.add(g.add(right.multiply(Math.cos(a) * size * 0.14)).add(up.multiply(Math.sin(a) * size * 0.09)));
		}
		fan(vc, m, g, glint, 0xFFFFFF, 0.75f * alpha, 0);
		// A halo round it, facing the eye
		Vec3d[] f = facing(c, cam);
		List<Vec3d> halo = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			double a = Math.PI * 2 * i / 20;
			halo.add(c.add(f[0].multiply(Math.cos(a) * size * 1.7)).add(f[1].multiply(Math.sin(a) * size * 1.7)));
		}
		fan(vc, m, c, halo, rgb, 0.22f * alpha, 0);
	}

	/** A basis tumbling round a tilted axis: hearts turning over in the air. */
	private static Vec3d[] tumble(double angle, double tilt) {
		Vec3d right = new Vec3d(Math.cos(angle), Math.sin(tilt) * 0.3, Math.sin(angle)).normalize();
		Vec3d up = new Vec3d(-Math.sin(tilt) * 0.3 * Math.cos(angle), 1, 0).normalize();
		Vec3d n = right.crossProduct(up).normalize();
		up = n.crossProduct(right).normalize();
		return new Vec3d[] {right, up, n};
	}

	/** A heart beats once over the kissed; its life flows to the one who kissed as small hearts tumbling through the air. */
	private static final class Hearts extends Effect {
		final int from;
		final int to;
		final int count;

		Hearts(int ticks, int rgb, int from, int to, int count) {
			super(ticks, rgb);
			this.from = from;
			this.to = to;
			this.count = Math.max(1, count);
		}

		@Override
		boolean solid() {
			return true;
		}

		@Override
		void tick() {
			Entity src = entity(from);
			Entity dst = entity(to);
			MinecraftClient client = MinecraftClient.getInstance();
			if (src == null || dst == null || client.world == null || age > ticks * 0.6f) {
				return;
			}
			// Rose dust drawn out of the kissed along the way
			Vec3d pf = src.getPos().add(0, src.getHeight() * 0.7, 0);
			Vec3d v = dst.getPos().add(0, dst.getHeight() * 0.6, 0).subtract(pf).multiply(0.06);
			for (int i = 0; i < 2; i++) {
				client.world.addParticle(new net.minecraft.particle.DustParticleEffect(new org.joml.Vector3f(1f, 0.42f, 0.62f), 0.8f),
						pf.x + (client.world.random.nextDouble() - 0.5) * 0.4, pf.y + (client.world.random.nextDouble() - 0.5) * 0.4,
						pf.z + (client.world.random.nextDouble() - 0.5) * 0.4, v.x, v.y, v.z);
			}
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
			Entity src = entity(from);
			Entity dst = entity(to);
			if (src == null || dst == null) {
				return;
			}
			float life = age + delta;
			Vec3d pf = src.getLerpedPos(delta).add(0, src.getHeight() * 0.7, 0);
			Vec3d pt = dst.getLerpedPos(delta).add(0, dst.getHeight() * 0.6, 0);
			// The big heart over the kissed: it swells in, beats once, turns slowly, and fades
			float big = smoothstep(life / 4f) * Math.max(0, 1 - life / (ticks * 0.75f));
			if (big > 0) {
				Vec3d c = pf.add(0, src.getHeight() * 0.3 + 0.9, 0);
				double size = 0.34 * (1 + 0.3 * heartbeat(Math.min(1, life / 14f)));
				Vec3d[] b = tumble(life * 0.06 + Math.PI / 2, 0);
				heart3d(vc, m, c, b[0], b[1], b[2], size, rgb, big, cam);
			}
			Vec3d side = pt.subtract(pf).crossProduct(new Vec3d(0, 1, 0));
			side = side.lengthSquared() < 1.0E-4 ? new Vec3d(1, 0, 0) : side.normalize();
			for (int i = 0; i < count; i++) {
				float born = ticks * 0.5f * i / count;
				float u = (life - born) / (ticks * 0.45f);
				if (u <= 0 || u >= 1) {
					continue;
				}
				float eased = u * u * (3 - 2 * u);
				Vec3d p = pf.lerp(pt, eased).add(side.multiply(Math.sin(u * Math.PI * 2 + i) * 0.35 * (1 - u))).add(0, Math.sin(u * Math.PI) * 0.6, 0);
				Vec3d[] b = tumble(life * 0.35 + i * 1.3, life * 0.2 + i);
				float a = Math.min(1, u * 6) * (1 - u * u);
				heart3d(vc, m, p, b[0], b[1], b[2], 0.12 * (1 - 0.45 * u), rgb, a, cam);
			}
		}
	}

	private static float smoothstep(float t) {
		t = MathHelper.clamp(t, 0, 1);
		return t * t * (3 - 2 * t);
	}

	/**
	 * A Tempter's wings, bat-like: a two-jointed arm, four long jointed fingers that lag behind each beat, and a skin of
	 * dark wine stretched between them, billowing against the air and scalloped at its edge, veined and rimmed with rose.
	 * They unfold from the back, rise, come down in one great beat, hold a glide and fold away.
	 */
	private static final class Wings extends Effect {
		private static final float[] KEY_T = {0, 0.16f, 0.4f, 0.6f, 0.82f, 1};
		private static final float[] KEY_ANGLE = {-75, 48, -30, 12, 6, -75};
		private static final double[] OPEN = {64, 36, 8, -22};
		private static final double[] CLOSED = {-58, -64, -70, -76};
		private static final double[] LENGTH = {0.58, 0.82, 0.74, 0.54};
		private static final int U = 8;
		private static final int V = 5;
		final int on;
		final float span;

		Wings(int ticks, int rgb, int on, float span) {
			super(ticks, rgb);
			this.on = on;
			this.span = span;
		}

		@Override
		boolean solid() {
			return true;
		}

		private static float flap(float t) {
			for (int i = 0; i < KEY_T.length - 1; i++) {
				if (t <= KEY_T[i + 1]) {
					float k = smoothstep((t - KEY_T[i]) / (KEY_T[i + 1] - KEY_T[i]));
					return MathHelper.lerp(k, KEY_ANGLE[i], KEY_ANGLE[i + 1]);
				}
			}
			return KEY_ANGLE[KEY_ANGLE.length - 1];
		}

		@Override
		void tick() {
			// On the downbeat the edge of the skin sheds rose dust
			float t = (float) age / ticks;
			if (t < 0.18f || t > 0.42f) {
				return;
			}
			Entity e = entity(on);
			MinecraftClient client = MinecraftClient.getInstance();
			if (e == null || client.world == null) {
				return;
			}
			for (int i = 0; i < 3; i++) {
				double side = client.world.random.nextBoolean() ? 1 : -1;
				double yaw = Math.toRadians(e.getYaw());
				Vec3d right = new Vec3d(Math.cos(yaw), 0, Math.sin(yaw));
				Vec3d p = e.getPos().add(0, e.getHeight() * 0.8, 0).add(right.multiply(side * span * (0.6 + 0.5 * client.world.random.nextDouble())));
				client.world.addParticle(new net.minecraft.particle.DustParticleEffect(new org.joml.Vector3f(1f, 0.4f, 0.6f), 1.0f), p.x, p.y, p.z, 0, -0.05, 0);
			}
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
			if (inkPass) {
				return;
			}
			Entity e = entity(on);
			if (e == null) {
				return;
			}
			float yaw = e instanceof net.minecraft.entity.LivingEntity living ? MathHelper.lerpAngleDegrees(delta, living.prevBodyYaw, living.bodyYaw) : e.getYaw();
			double r = Math.toRadians(yaw);
			Vec3d forward = new Vec3d(-Math.sin(r), 0, Math.cos(r));
			Vec3d back = forward.multiply(-1);
			Vec3d right = new Vec3d(Math.cos(r), 0, Math.sin(r));
			Vec3d up = new Vec3d(0, 1, 0);
			Vec3d base = e.getLerpedPos(delta).add(0, e.getHeight() * 0.72, 0).add(back.multiply(0.24));
			float t = progress(delta);
			float spread = t < 0.16f ? smoothstep(t / 0.16f) : t > 0.82f ? 1 - smoothstep((t - 0.82f) / 0.18f) : 1;
			float angle = flap(t);
			// How fast the wing is moving now: the fingers trail behind it and the skin bellies against the air
			float speed = (flap(Math.min(1, t + 0.02f)) - flap(Math.max(0, t - 0.02f))) / 0.04f / ticks;
			float alpha = Math.min(1, (age + delta) / 3f) * Math.min(1, (ticks - age - delta) / 4f + 0.2f);
			for (int s = -1; s <= 1; s += 2) {
				drawWing(vc, m, cam, base.add(right.multiply(s * 0.12)), right.multiply(s), up, back, Math.toRadians(angle), spread, speed, alpha);
			}
		}

		private void drawWing(VertexConsumer vc, Matrix4f m, Vec3d cam, Vec3d shoulder, Vec3d side, Vec3d up, Vec3d back, double angle, float spread,
				float speed, float alpha) {
			Vec3d out = side.multiply(Math.cos(angle)).add(up.multiply(Math.sin(angle))).add(back.multiply(0.5 - 0.2 * spread)).normalize();
			Vec3d wingUp = side.multiply(-Math.sin(angle)).add(up.multiply(Math.cos(angle))).normalize();
			Vec3d normal = out.crossProduct(wingUp).normalize();
			// A point in the wing's own plane, x out along the arm, y up, z out of the plane (the belly of the skin)
			java.util.function.Function<double[], Vec3d> at = xy -> shoulder.add(out.multiply(xy[0] * span)).add(wingUp.multiply(xy[1] * span))
					.add(normal.multiply(xy[2] * span));
			double lag = MathHelper.clamp(speed * 0.012, -0.5, 0.5);
			double reach = 0.5 + 0.5 * spread;
			Vec3d elbow = at.apply(new double[] {0.2 * reach, 0.1, -lag * 0.15});
			Vec3d wrist = at.apply(new double[] {0.44 * reach, 0.18, -lag * 0.3});
			// The fingers: three joints each, bending more towards the tip and trailing the beat
			Vec3d[][] fingers = new Vec3d[4][4];
			for (int k = 0; k < 4; k++) {
				double phi = Math.toRadians(MathHelper.lerp(spread, CLOSED[k], OPEN[k]));
				double len = LENGTH[k] * (0.45 + 0.55 * spread);
				fingers[k][0] = wrist;
				double x = 0.44 * reach;
				double y = 0.18;
				double z = -lag * 0.3;
				for (int j = 1; j < 4; j++) {
					double bend = phi - Math.toRadians(5 * j) * (k < 2 ? 1 : -0.5);
					x += Math.cos(bend) * len / 3;
					y += Math.sin(bend) * len / 3;
					z -= lag * 0.25 * j;
					fingers[k][j] = at.apply(new double[] {x, y, z});
				}
			}
			Vec3d hip = at.apply(new double[] {0.04, -0.52, 0});
			// The skin: a grid between each pair of fingers (and the last finger and the hip), scalloped at its edge
			for (int k = 0; k < 4; k++) {
				Vec3d[] a = fingers[k];
				Vec3d[] b = k < 3 ? fingers[k + 1] : new Vec3d[] {wrist, wrist.lerp(hip, 0.4), wrist.lerp(hip, 0.75), hip};
				Vec3d[][] grid = new Vec3d[U + 1][V + 1];
				for (int i = 0; i <= U; i++) {
					for (int j = 0; j <= V; j++) {
						double v = (double) j / V;
						double u = (double) i / U * (1 - 0.3 * Math.sin(Math.PI * v));
						Vec3d pa = along(a, u);
						Vec3d pb = along(b, u);
						double belly = (0.05 + lag * 0.12) * Math.sin(Math.PI * v) * Math.sin(Math.PI * Math.min(1, u * 1.2)) * span;
						grid[i][j] = pa.lerp(pb, v).add(normal.multiply(-belly));
					}
				}
				for (int i = 0; i < U; i++) {
					for (int j = 0; j < V; j++) {
						Vec3d p00 = grid[i][j];
						Vec3d p10 = grid[i + 1][j];
						Vec3d p11 = grid[i + 1][j + 1];
						Vec3d p01 = grid[i][j + 1];
						if (solidPass) {
							Vec3d n = p10.subtract(p00).crossProduct(p01.subtract(p00));
							// Darker in the middle of each panel, warmer near the bones
							double mid = Math.sin(Math.PI * (j + 0.5) / V);
							int skin = lerpColor(0x7A2048, 0x3A0A1E, (float) mid);
							quad(vc, m, p00, p10, p11, p01, shade(skin, n, 0.5f), 0.9f * alpha, 0.9f * alpha, 0.9f * alpha, 0.9f * alpha);
						}
					}
				}
				if (!solidPass) {
					// Veins through the skin, and the scalloped edge glowing rose
					for (int vein = 1; vein <= 2; vein++) {
						int j = vein * V / 3;
						for (int i = 0; i < U; i++) {
							glowLine(vc, m, grid[i][j], grid[i + 1][j], normal, 0.025 * span, rgb, 0.22f * alpha);
						}
					}
					for (int j = 0; j < V; j++) {
						glowLine(vc, m, grid[U][j], grid[U][j + 1], normal, 0.05 * span, rgb, 0.75f * alpha);
						glowLine(vc, m, grid[U][j], grid[U][j + 1], normal, 0.015 * span, 0xFFE6F0, 0.6f * alpha);
					}
				}
			}
			// The bones: dark tapered limbs in the solid pass, a thin line of rose light along each in the glow
			bone(vc, m, cam, shoulder, elbow, 0.05 * span, 0.04 * span, alpha);
			bone(vc, m, cam, elbow, wrist, 0.04 * span, 0.03 * span, alpha);
			for (int k = 0; k < 4; k++) {
				for (int j = 0; j < 3; j++) {
					double r0 = 0.026 * span * (1 - j / 3.5);
					bone(vc, m, cam, fingers[k][j], fingers[k][j + 1], r0, r0 * 0.75, alpha);
				}
			}
			// The thumb's claw at the wrist
			Vec3d claw = wrist.add(out.multiply(0.06 * span)).add(wingUp.multiply(0.12 * span));
			bone(vc, m, cam, wrist, claw, 0.025 * span, 0.002, alpha);
		}

		/** A point {@code u} of the way along a jointed limb. */
		private static Vec3d along(Vec3d[] joints, double u) {
			double f = MathHelper.clamp(u, 0, 1) * (joints.length - 1);
			int i = Math.min(joints.length - 2, (int) f);
			return joints[i].lerp(joints[i + 1], f - i);
		}

		/** A limb from a to b, {@code r0} thick at a and {@code r1} at b: a four-sided body, lit; a line of light along it. */
		private void bone(VertexConsumer vc, Matrix4f m, Vec3d cam, Vec3d a, Vec3d b, double r0, double r1, float alpha) {
			Vec3d dir = b.subtract(a);
			if (dir.lengthSquared() < 1.0E-8) {
				return;
			}
			if (!solidPass) {
				glowLine(vc, m, a, b, cam.subtract(a), Math.max(r0, r1) * 1.6, rgb, 0.55f * alpha);
				glowLine(vc, m, a, b, cam.subtract(a), Math.max(r0, r1) * 0.5, 0xFFE6F0, 0.5f * alpha);
				return;
			}
			Vec3d[] ring = around(dir.normalize());
			Vec3d[] sides = {ring[0], ring[1], ring[0].multiply(-1), ring[1].multiply(-1)};
			for (int i = 0; i < 4; i++) {
				Vec3d s0 = sides[i];
				Vec3d s1 = sides[(i + 1) % 4];
				Vec3d n = s0.add(s1);
				quad(vc, m, a.add(s0.multiply(r0)), b.add(s0.multiply(r1)), b.add(s1.multiply(r1)), a.add(s1.multiply(r0)), shade(0x2A0A16, n, 0.45f),
						alpha, alpha, alpha, alpha);
			}
		}
	}

	/** A glyph hanging over a head while a spell lasts: a turning heart, a sleeping moon with drifting Zs, or a mask. */
	private static final class Sigil extends Effect {
		final int over;
		final int kind;

		Sigil(int ticks, int rgb, int over, int kind) {
			super(ticks, rgb);
			this.over = over;
			this.kind = kind;
		}

		@Override
		boolean inks() {
			return kind == Fx.SIGIL_MASK;
		}

		@Override
		boolean solid() {
			return kind != Fx.SIGIL_MASK;
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
			Entity e = entity(over);
			if (e == null || !e.isAlive()) {
				return;
			}
			float life = age + delta;
			float grow = easeOutBack(Math.min(1, life / 6f));
			float fade = Math.min(1, (ticks - life) / 8f);
			Vec3d c = e.getLerpedPos(delta).add(0, e.getHeight() + 0.55 + 0.08 * Math.sin(life * 0.15), 0);
			switch (kind) {
				case Fx.SIGIL_HEART -> {
					Vec3d[] b = tumble(life * 0.08, Math.sin(life * 0.05) * 0.6);
					double size = 0.24 * grow * (1 + 0.12 * heartbeat((life % 24) / 24f));
					heart3d(vc, m, c, b[0], b[1], b[2], size, rgb, fade, cam);
					if (!solidPass && !inkPass) {
						// Two motes going round it
						for (int i = 0; i < 2; i++) {
							double a = life * 0.2 + i * Math.PI;
							Vec3d p = c.add(Math.cos(a) * 0.4, 0.05 * Math.sin(a * 2), Math.sin(a) * 0.4);
							Vec3d[] f = facing(p, cam);
							star(vc, m, p, f[0], f[1], 0.05, 0.8f * fade);
						}
					}
				}
				case Fx.SIGIL_MOON -> moon(vc, m, cam, c, life, grow, fade);
				default -> {
					if (solidPass) {
						return;
					}
					Vec3d[] f = facing(c, cam);
					drawMask(vc, m, c, f[2], 0.26 * grow, rgb, fade, 0.6f + 0.4f * (float) Math.sin(life * 0.3));
				}
			}
		}

		/** A crescent moon with a body, rocking gently as if asleep, and Zs drifting up from it. */
		private void moon(VertexConsumer vc, Matrix4f m, Vec3d cam, Vec3d c, float life, float grow, float fade) {
			if (inkPass) {
				return;
			}
			double rock = Math.sin(life * 0.08) * 0.35;
			Vec3d[] f = tumble(Math.atan2(cam.z - c.z, cam.x - c.x) + Math.PI / 2, 0);
			Vec3d right = f[0].multiply(Math.cos(rock)).add(f[1].multiply(Math.sin(rock)));
			Vec3d up = f[1].multiply(Math.cos(rock)).subtract(f[0].multiply(Math.sin(rock)));
			double r = 0.22 * grow;
			List<Vec3d> rim = new ArrayList<>();
			for (int i = 0; i <= 16; i++) {
				double a = -Math.PI / 2 + Math.PI * i / 16;
				rim.add(c.add(right.multiply(Math.cos(a) * r)).add(up.multiply(Math.sin(a) * r)));
			}
			for (int i = 15; i >= 1; i--) {
				double a = -Math.PI / 2 + Math.PI * i / 16;
				rim.add(c.add(right.multiply(Math.cos(a) * r * 0.25)).add(up.multiply(Math.sin(a) * r * 0.92)));
			}
			Vec3d center = c.add(right.multiply(r * 0.6));
			if (solidPass) {
				extrude(vc, m, center, rim, f[2], r * 0.3, 0xD8C8FF, 0x6A58A8, fade);
				return;
			}
			outline(vc, m, rim, true, f[2], 0.035, rgb, 0.8f * fade);
			Vec3d[] g = facing(c, cam);
			for (int i = 0; i < 3; i++) {
				float s = (life * 0.025f + i / 3f) % 1;
				Vec3d z = c.add(g[0].multiply(0.3 + s * 0.25)).add(g[1].multiply(0.15 + s * 0.6));
				double size = 0.07 + 0.05 * s;
				Vec3d tl = z.add(g[0].multiply(-size)).add(g[1].multiply(size));
				Vec3d tr = z.add(g[0].multiply(size)).add(g[1].multiply(size));
				Vec3d bl = z.add(g[0].multiply(-size)).add(g[1].multiply(-size));
				Vec3d br = z.add(g[0].multiply(size)).add(g[1].multiply(-size));
				float a = fade * (1 - s) * Math.min(1, s * 6);
				glowLine(vc, m, tl, tr, g[2], 0.04, rgb, a);
				glowLine(vc, m, tr, bl, g[2], 0.04, rgb, a);
				glowLine(vc, m, bl, br, g[2], 0.04, rgb, a);
			}
		}
	}

	/** A heart over a creature cracks down the middle, splits, and its two halves tumble apart with shards flying. */
	private static final class Heartbreak extends Effect {
		final int over;

		Heartbreak(int ticks, int rgb, int over) {
			super(ticks, rgb);
			this.over = over;
		}

		@Override
		boolean solid() {
			return true;
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
			if (inkPass) {
				return;
			}
			Entity e = entity(over);
			Vec3d base = e == null ? null : e.getLerpedPos(delta);
			if (base == null) {
				return;
			}
			float t = progress(delta);
			float life = age + delta;
			Vec3d c = base.add(0, e.getHeight() + 0.75, 0);
			Vec3d[] f = tumble(Math.atan2(cam.z - c.z, cam.x - c.x) + Math.PI / 2 + Math.sin(life * 0.2) * 0.2, 0);
			double size = 0.42 * (t < 0.25f ? easeOutBack(t / 0.25f) : 1);
			if (t < 0.4f) {
				heart3d(vc, m, c, f[0], f[1], f[2], size, rgb, 1, cam);
				// The crack running down it, white-hot
				if (t > 0.25f && !solidPass) {
					float k = (t - 0.25f) / 0.15f;
					Vec3d front = f[2].multiply(cam.subtract(c).dotProduct(f[2]) >= 0 ? size * 0.25 : -size * 0.25);
					Vec3d prev = c.add(front).add(f[1].multiply(size * 0.58));
					int steps = 6;
					for (int i = 1; i <= steps * k; i++) {
						Vec3d next = c.add(front).add(f[1].multiply(size * (0.58 - 1.6 * i / steps))).add(f[0].multiply((i % 2 == 0 ? -1 : 1) * size * 0.1));
						glowLine(vc, m, prev, next, f[2], 0.07, 0xFFFFFF, 0.95f);
						prev = next;
					}
				}
				return;
			}
			float u = (t - 0.4f) / 0.6f;
			float alpha = 1 - u * u;
			for (int s = -1; s <= 1; s += 2) {
				List<Vec3d> half = heart(Vec3d.ZERO, new Vec3d(1, 0, 0), new Vec3d(0, 1, 0), 1, 16, s > 0 ? 0 : Math.PI, s > 0 ? Math.PI : Math.PI * 2);
				// Each half falls away from the other, turning over as it goes
				double roll = s * u * 1.4;
				double turn = s * u * 2.2;
				Vec3d right = f[0].multiply(Math.cos(turn)).add(f[2].multiply(Math.sin(turn)));
				Vec3d n = f[2].multiply(Math.cos(turn)).subtract(f[0].multiply(Math.sin(turn)));
				Vec3d offset = f[0].multiply(s * size * (0.15 + 1.3 * u)).add(f[1].multiply(-1.8 * u * u));
				List<Vec3d> rim = new ArrayList<>();
				for (Vec3d p : half) {
					double x = p.x * Math.cos(roll) - p.y * Math.sin(roll);
					double y = p.x * Math.sin(roll) + p.y * Math.cos(roll);
					rim.add(c.add(offset).add(right.multiply(x * size)).add(f[1].multiply(y * size)));
				}
				Vec3d mid = centroid(rim);
				if (solidPass) {
					extrude(vc, m, mid, rim, n, size * 0.45, lerpColor(rgb, 0xC0144A, 0.45f), 0x5A0A28, alpha);
				} else {
					outline(vc, m, rim, true, n, size * 0.08, rgb, 0.8f * alpha);
				}
			}
			if (!solidPass) {
				Vec3d[] g = facing(c, cam);
				for (int i = 0; i < 10; i++) {
					double a = Math.PI * 2 * i / 10 + 0.3;
					double d = size * (0.3 + 2.4 * u);
					Vec3d p = c.add(g[0].multiply(Math.cos(a) * d)).add(g[1].multiply(Math.sin(a) * d - u * u * 1.2));
					star(vc, m, p, g[0], g[1], 0.07 * (1 - u), alpha);
				}
			}
		}
	}

	/** A lullaby: soft waves rolling out over the ground, swaying up and down like a song, crescent moons rising from them. */
	private static final class Lullaby extends Effect {
		final Vec3d at;
		final float radius;

		Lullaby(int ticks, int rgb, Vec3d at, float radius) {
			super(ticks, rgb);
			this.at = at;
			this.radius = radius;
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
			float life = age + delta;
			float t = progress(delta);
			float fade = Math.min(1, (ticks - life) / 10f);
			Vec3d up = new Vec3d(0, 1, 0);
			for (int w = 0; w < 3; w++) {
				float k = (t * 1.6f + w / 3f) % 1;
				double r = radius * k;
				int seg = 48;
				for (int i = 0; i < seg; i++) {
					double a0 = Math.PI * 2 * i / seg;
					double a1 = Math.PI * 2 * (i + 1) / seg;
					Vec3d p0 = at.add(Math.cos(a0) * r, 0.25 + 0.18 * Math.sin(a0 * 6 + life * 0.4), Math.sin(a0) * r);
					Vec3d p1 = at.add(Math.cos(a1) * r, 0.25 + 0.18 * Math.sin(a1 * 6 + life * 0.4), Math.sin(a1) * r);
					glowLine(vc, m, p0, p1, up, 0.22, lerpColor(rgb, 0xFF6FA8, k), 0.5f * (1 - k) * fade);
				}
			}
			for (int i = 0; i < 6; i++) {
				double a = Math.PI * 2 * i / 6 + 0.4;
				float s = (life * 0.03f + i / 6f) % 1;
				Vec3d c = at.add(Math.cos(a) * radius * 0.55, 0.6 + s * 2.2, Math.sin(a) * radius * 0.55);
				Vec3d[] f = facing(c, cam);
				double r = 0.2;
				float alpha = fade * Math.min(1, s * 5) * (1 - s);
				for (int k = 0; k < 10; k++) {
					double a0 = -Math.PI / 2 + Math.PI * k / 10;
					double a1 = -Math.PI / 2 + Math.PI * (k + 1) / 10;
					Vec3d o0 = c.add(f[0].multiply(Math.cos(a0) * r)).add(f[1].multiply(Math.sin(a0) * r));
					Vec3d o1 = c.add(f[0].multiply(Math.cos(a1) * r)).add(f[1].multiply(Math.sin(a1) * r));
					Vec3d i0 = c.add(f[0].multiply(Math.cos(a0) * r * 0.3)).add(f[1].multiply(Math.sin(a0) * r));
					Vec3d i1 = c.add(f[0].multiply(Math.cos(a1) * r * 0.3)).add(f[1].multiply(Math.sin(a1) * r));
					quad(vc, m, i0, o0, o1, i1, rgb, 0.7f * alpha, 0.7f * alpha, 0.7f * alpha, 0.7f * alpha);
				}
			}
		}
	}

	/** A face torn off a creature flies in an arc, turning over, to the wearer's face, and is pressed on with a flash. */
	private static final class Mask extends Effect {
		final Vec3d from;
		final int to;

		Mask(int ticks, int rgb, Vec3d from, int to) {
			super(ticks, rgb);
			this.from = from;
			this.to = to;
		}

		@Override
		boolean inks() {
			return true;
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
			Entity e = entity(to);
			if (e == null) {
				return;
			}
			float t = progress(delta);
			double yaw = Math.toRadians(e instanceof net.minecraft.entity.LivingEntity l ? MathHelper.lerpAngleDegrees(delta, l.prevHeadYaw, l.headYaw) : e.getYaw());
			Vec3d forward = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw));
			Vec3d face = e.getLerpedPos(delta).add(0, e.getStandingEyeHeight(), 0).add(forward.multiply(0.35));
			Vec3d apex = from.lerp(face, 0.5).add(0, 1.2, 0);
			double finalAngle = Math.atan2(forward.z, forward.x);
			if (t < 0.75f) {
				float u = smooth(t / 0.75f);
				Vec3d p = bezier(from, apex, face, u);
				double psi = finalAngle + (1 - u) * Math.PI * 3;
				Vec3d n = new Vec3d(Math.cos(psi), 0.2 * Math.sin(u * Math.PI), Math.sin(psi)).normalize();
				drawMask(vc, m, p, n, 0.32, rgb, 1, 0.5f);
				return;
			}
			float v = (t - 0.75f) / 0.25f;
			drawMask(vc, m, face, forward, 0.32 * (1 - 0.4 * v), rgb, 1 - v, 1);
			if (!inkPass) {
				Vec3d[] f = facing(face, cam);
				star(vc, m, face, f[0], f[1], 0.5 * (1 - v) + 0.1, 1 - v);
			}
		}
	}

	/** Faces circling a head, facing outwards, turning faster and faster, then drawn in onto the head. */
	private static final class Masks extends Effect {
		final int around;
		final int count;
		final float radius;

		Masks(int ticks, int rgb, int around, int count, float radius) {
			super(ticks, rgb);
			this.around = around;
			this.count = Math.max(1, count);
			this.radius = radius;
		}

		@Override
		boolean inks() {
			return true;
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
			Entity e = entity(around);
			if (e == null) {
				return;
			}
			float t = progress(delta);
			float life = age + delta;
			Vec3d head = e.getLerpedPos(delta).add(0, e.getHeight() * 0.85, 0);
			double r = radius * (t < 0.7f ? easeOutBack(Math.min(1, life / 8f)) : 1 - smooth((t - 0.7f) / 0.3f));
			double spin = life * 0.05 + life * life * 0.004;
			float alpha = t < 0.7f ? 1 : 1 - smooth((t - 0.7f) / 0.3f) * 0.8f;
			for (int i = 0; i < count; i++) {
				double a = Math.PI * 2 * i / count + spin;
				Vec3d out = new Vec3d(Math.cos(a), 0, Math.sin(a));
				Vec3d p = head.add(out.multiply(r)).add(0, 0.15 * Math.sin(life * 0.2 + i * 1.7), 0);
				drawMask(vc, m, p, out, 0.26, rgb, alpha, 0.5f + 0.5f * (float) Math.sin(life * 0.4 + i));
			}
		}
	}

	/** A spider's web spun flat on the ground: the spokes shoot out, the spiral is laid round them, dew glints, it fades. */
	private static final class Web extends Effect {
		final Vec3d at;
		final float radius;
		final long seed;

		Web(int ticks, int rgb, Vec3d at, float radius) {
			super(ticks, rgb);
			this.at = at.add(0, 0.06, 0);
			this.radius = radius;
			this.seed = Double.doubleToLongBits(at.x * 17 + at.z * 5);
		}

		@Override
		void draw(Matrix4f m, VertexConsumer vc, Vec3d cam, float delta) {
			float t = progress(delta);
			float fade = t < 0.7f ? 1 : 1 - (t - 0.7f) / 0.3f;
			Vec3d up = new Vec3d(0, 1, 0);
			int spokes = 10;
			double[] angle = new double[spokes];
			double[] reach = new double[spokes];
			float shoot = easeOut(Math.min(1, t / 0.15f));
			for (int k = 0; k < spokes; k++) {
				angle[k] = Math.PI * 2 * k / spokes + (hash(seed, k) - 0.5) * 0.3;
				reach[k] = radius * (0.85 + 0.15 * hash(seed, k + 20));
				Vec3d tip = at.add(Math.cos(angle[k]) * reach[k] * shoot, 0, Math.sin(angle[k]) * reach[k] * shoot);
				glowLine(vc, m, at, tip, up, 0.05, rgb, 0.85f * fade);
			}
			for (int j = 1; j <= 6; j++) {
				float shown = MathHelper.clamp((t - 0.12f - j * 0.04f) / 0.06f, 0, 1);
				if (shown <= 0) {
					continue;
				}
				double rj = radius * j / 6.5;
				for (int k = 0; k < spokes * shown; k++) {
					int n = (k + 1) % spokes;
					Vec3d a = at.add(Math.cos(angle[k]) * Math.min(rj, reach[k]), 0, Math.sin(angle[k]) * Math.min(rj, reach[k]));
					Vec3d b = at.add(Math.cos(angle[n]) * Math.min(rj, reach[n]), 0, Math.sin(angle[n]) * Math.min(rj, reach[n]));
					// The thread sags inwards between spokes
					Vec3d sag = a.lerp(b, 0.5).lerp(at, 0.08);
					glowLine(vc, m, a, sag, up, 0.035, rgb, 0.7f * fade);
					glowLine(vc, m, sag, b, up, 0.035, rgb, 0.7f * fade);
					if (hash(seed, j * 31 + k) > 0.8) {
						Vec3d[] f = facing(a, cam);
						star(vc, m, a, f[0], f[1], 0.04, fade);
					}
				}
			}
		}
	}
}
