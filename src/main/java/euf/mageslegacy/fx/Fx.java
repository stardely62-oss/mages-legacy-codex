package euf.mageslegacy.fx;

import euf.mageslegacy.net.RacePackets;
import java.util.ArrayList;
import java.util.List;
import euf.mageslegacy.platform.Network;
import euf.mageslegacy.platform.Network.Server;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Ability effects drawn by the clients as glowing geometry particles can't make: rings that spread, domes, ribbons
 * of light along a path, pillars, and a tint over one player's own screen. The server only says what and where.
 */
public final class Fx {
	public static final int RING = 0;
	public static final int SPHERE = 1;
	public static final int RIBBON = 2;
	public static final int PILLAR = 3;
	public static final int SCREEN = 4;
	public static final int FLIGHT = 5;
	public static final int SLASH = 6;
	public static final int TENDRIL = 7;
	public static final int MARK = 8;
	public static final int HELIX = 9;
	public static final int GHOSTS = 10;
	public static final int SHAKE = 11;
	public static final int FOV = 12;
	public static final int PULSE = 13;
	public static final int AURA = 14;
	public static final int SWARM = 15;
	public static final int BURST = 16;
	public static final int RIFT = 17;
	public static final int VORTEX = 18;
	public static final int VOID_SCREEN = 19;
	public static final int PHASE = 20;
	public static final int CRYSTALS = 21;
	public static final int AURORA = 23;
	public static final int NEBULA = 24;
	public static final int COMET = 25;
	public static final int THREADS = 26;
	public static final int COINS = 27;
	public static final int EXCHANGE = 28;
	public static final int SEAL = 29;
	public static final int DOOR = 30;
	public static final int FLIP = 31;
	public static final int HEARTS = 32;
	public static final int WINGS = 33;
	public static final int SIGIL = 34;
	public static final int HEARTBREAK = 35;
	public static final int LULLABY = 36;
	public static final int MASK = 37;
	public static final int MASKS = 38;
	public static final int WEB = 39;
	public static final int DOOR_CLOSE = 40;
	/**
	 * A flag on a colour: lay it on as real colour under its glow, the way blood should look, rather than glow alone
	 * (which a bright day washes to pink, and which can't show a dark colour at all).
	 */
	public static final int INK = 0x1000000;
	/** How far away players still see an effect. */
	private static final double RANGE = 64;

	private Fx() {
	}

	/** A flat ring on the ground spreading from {@code radius0} to {@code radius1} and fading. */
	public static void ring(ServerLevel world, Vec3 at, float radius0, float radius1, int ticks, int rgb) {
		FriendlyByteBuf buf = start(RING, ticks, rgb);
		writeVec(buf, at);
		buf.writeFloat(radius0);
		buf.writeFloat(radius1);
		send(world, at, buf);
	}

	/**
	 * A glowing dome that grows in, holds and fades away; {@code follow} is the entity it rides with (or null), and
	 * {@code full} draws a whole sphere instead of the upper half.
	 */
	public static void sphere(ServerLevel world, Vec3 at, float radius, int ticks, int rgb, Entity follow, boolean full) {
		FriendlyByteBuf buf = start(SPHERE, ticks, rgb);
		writeVec(buf, at);
		buf.writeFloat(radius);
		buf.writeVarInt(follow == null ? -1 : follow.getId());
		buf.writeBoolean(full);
		send(world, at, buf);
	}

	/** A ribbon of light along {@code path} (oldest first), shading from {@code rgb} to {@code rgb2}, then fading. */
	public static void ribbon(ServerLevel world, List<Vec3> path, int ticks, int rgb, int rgb2, float height) {
		if (path.size() < 2) {
			return;
		}
		FriendlyByteBuf buf = start(RIBBON, ticks, rgb);
		buf.writeInt(rgb2);
		buf.writeFloat(height);
		// Twenty-odd points are plenty for a smooth streak; the last one always goes in
		int step = Math.max(1, path.size() / 20);
		List<Vec3> points = new ArrayList<>();
		for (int i = 0; i < path.size(); i += step) {
			points.add(path.get(i));
		}
		if (points.get(points.size() - 1) != path.get(path.size() - 1)) {
			points.add(path.get(path.size() - 1));
		}
		buf.writeVarInt(points.size());
		points.forEach(p -> writeVec(buf, p));
		send(world, path.get(0), buf);
	}

	/** A pillar of light rising from {@code at}. */
	public static void pillar(ServerLevel world, Vec3 at, float radius, float height, int ticks, int rgb) {
		FriendlyByteBuf buf = start(PILLAR, ticks, rgb);
		writeVec(buf, at);
		buf.writeFloat(radius);
		buf.writeFloat(height);
		send(world, at, buf);
	}

	/** A tint over this one player's screen edges (a flash at its start). */
	public static void screen(ServerPlayer player, int ticks, int rgb) {
		if (Server.canSend(player, RacePackets.FX)) {
			Server.send(player, RacePackets.FX, start(SCREEN, ticks, rgb));
		}
	}

	/**
	 * This player is about to be carried along a path by the server for {@code ticks}: its client smooths the
	 * movement between the server's steps instead of snapping to each.
	 */
	public static void flight(ServerPlayer player, int ticks) {
		if (Server.canSend(player, RacePackets.FX)) {
			Server.send(player, RacePackets.FX, start(FLIGHT, ticks, 0));
		}
	}

	/**
	 * A crescent cut through {@code at}: it sweeps across along {@code dir} in a flash, a white-hot edge on a band of
	 * colour, and fades as it widens. {@code roll} turns it from flat (0) to upright (90 degrees).
	 */
	public static void slash(ServerLevel world, Vec3 at, Vec3 dir, float size, float roll, int ticks, int rgb) {
		FriendlyByteBuf buf = start(SLASH, ticks, rgb);
		writeVec(buf, at);
		writeVec(buf, dir);
		buf.writeFloat(size);
		buf.writeFloat(roll);
		send(world, at, buf);
	}

	/**
	 * A living stream from one entity to another: a winding ribbon, thick at its source and thin where it arrives,
	 * with bright pulses running along it.
	 */
	public static void tendril(ServerLevel world, Entity from, Entity to, int ticks, int rgb, int rgb2) {
		FriendlyByteBuf buf = start(TENDRIL, ticks, rgb);
		buf.writeInt(rgb2);
		buf.writeVarInt(from.getId());
		buf.writeVarInt(to.getId());
		send(world, from.position(), buf);
	}

	/** Marks on the ground in a race's manner: blood, time, a voice, the void, mycelium. */
	public static final int POOL = 0;
	public static final int CLOCK = 1;
	public static final int RIPPLES = 3;
	public static final int CRACKS = 4;
	public static final int MYCELIUM = 5;

	/**
	 * A mark on the ground in a race's own manner ({@link #POOL}, {@link #CLOCK}, {@link #RIPPLES}, {@link #CRACKS},
	 * {@link #MYCELIUM}):
	 * it opens, holds (riding with {@code follow} if given, or staying where it was made) and fades.
	 */
	public static void mark(ServerLevel world, Vec3 at, Entity follow, float radius, int ticks, int rgb, int style) {
		FriendlyByteBuf buf = start(MARK, ticks, rgb);
		writeVec(buf, at);
		buf.writeVarInt(follow == null ? -1 : follow.getId());
		buf.writeFloat(radius);
		buf.writeVarInt(style);
		send(world, at, buf);
	}

	/** Strands of light that wind up around an entity, rising, then draw into it ({@code inward}) or fly apart. */
	public static void helix(ServerLevel world, Entity around, float radius, float height, int strands, boolean inward, int ticks, int rgb) {
		FriendlyByteBuf buf = start(HELIX, ticks, rgb);
		buf.writeVarInt(around.getId());
		buf.writeFloat(radius);
		buf.writeFloat(height);
		buf.writeVarInt(strands);
		buf.writeBoolean(inward);
		send(world, around.position(), buf);
	}

	/**
	 * Glowing afterimages of a body left along {@code path}, facing {@code yaw}, the oldest fading first. They stay
	 * behind the body: none where it ends up.
	 */
	public static void ghosts(ServerLevel world, List<Vec3> path, float yaw, int count, int ticks, int rgb) {
		if (path.size() < 2) {
			return;
		}
		FriendlyByteBuf buf = start(GHOSTS, ticks, rgb);
		buf.writeFloat(yaw);
		buf.writeVarInt(count);
		for (int i = 0; i < count; i++) {
			writeVec(buf, path.get((int) ((path.size() - 1) * 0.75 * (i + 0.5) / count)));
		}
		send(world, path.get(0), buf);
	}

	/** A burst: a shell that flashes out from {@code at} to {@code radius} in a few ticks and is gone. */
	public static void burst(ServerLevel world, Vec3 at, float radius, int ticks, int rgb) {
		FriendlyByteBuf buf = start(BURST, ticks, rgb);
		writeVec(buf, at);
		buf.writeFloat(radius);
		send(world, at, buf);
	}

	/** What an aura gives off: crimson embers, grains of time, glowing spores, spirit wisps, golden motes. */
	public static final int EMBERS = 0;
	public static final int GRAINS = 1;
	public static final int SPORES = 2;
	public static final int WISPS = 3;
	public static final int MOTES = 4;

	/** Particles of {@code kind} rising off an entity for {@code ticks}, made by the clients themselves. */
	public static void aura(ServerLevel world, Entity around, int ticks, int rgb, int kind) {
		FriendlyByteBuf buf = start(AURA, ticks, rgb);
		buf.writeVarInt(around.getId());
		buf.writeVarInt(kind);
		send(world, around.position(), buf);
	}

	/** {@code count} bats circling an entity for {@code ticks}: a body scattered into a swarm. */
	public static void swarm(ServerLevel world, Entity around, int count, int ticks) {
		FriendlyByteBuf buf = start(SWARM, ticks, 0);
		buf.writeVarInt(around.getId());
		buf.writeVarInt(count);
		send(world, around.position(), buf);
	}

	/**
	 * A tear in the world standing upright at {@code at}, {@code height} tall and {@code width} across at its widest:
	 * it rips open, a starry dark inside a violet edge, holds and seals shut.
	 */
	public static void rift(ServerLevel world, Vec3 at, float height, float width, int ticks, int rgb) {
		FriendlyByteBuf buf = start(RIFT, ticks, rgb);
		writeVec(buf, at);
		buf.writeFloat(height);
		buf.writeFloat(width);
		send(world, at, buf);
	}

	/** A black hole at {@code at}: a dark core, a spinning disk of light around it, motes falling in. */
	public static void vortex(ServerLevel world, Vec3 at, float radius, int ticks, int rgb) {
		FriendlyByteBuf buf = start(VORTEX, ticks, rgb);
		writeVec(buf, at);
		buf.writeFloat(radius);
		send(world, at, buf);
	}

	/** This player's view drowns in the void for a moment: dark, with stars rushing past. */
	public static void voidScreen(ServerPlayer player, int ticks) {
		sendTo(player, start(VOID_SCREEN, ticks, 0), buf -> {
		});
	}

	/** This player passes through solid blocks for {@code ticks}: its client lets it, and glides it along. */
	public static void phase(ServerPlayer player, int ticks) {
		flight(player, ticks);
		sendTo(player, start(PHASE, ticks, 0), buf -> {
		});
	}

	/**
	 * Crystals of ice bursting up out of the ground: {@code count} tapered prisms around {@code at} at
	 * {@code radius}, leaning outwards, {@code height} tall; they grow with a snap, glint, and shatter at the end.
	 */
	public static void crystals(ServerLevel world, Vec3 at, float radius, int count, float height, int ticks, int rgb) {
		FriendlyByteBuf buf = start(CRYSTALS, ticks, rgb);
		writeVec(buf, at);
		buf.writeFloat(radius);
		buf.writeVarInt(count);
		buf.writeFloat(height);
		send(world, at, buf);
	}

	/**
	 * A cloud of spores like a glowing nebula: soft billows turning slowly around {@code at}, breathing, dark haze
	 * under the light and motes twinkling inside, riding with {@code follow} if given.
	 */
	public static void nebula(ServerLevel world, Vec3 at, Entity follow, float radius, int ticks, int rgb, int rgb2) {
		FriendlyByteBuf buf = start(NEBULA, ticks, rgb);
		buf.writeInt(rgb2);
		writeVec(buf, at);
		buf.writeVarInt(follow == null ? -1 : follow.getId());
		buf.writeFloat(radius);
		send(world, at, buf);
	}

	/** A glowing head of spores flying in an arc from {@code from} to {@code to} over {@code ticks}, trailing a tail. */
	public static void comet(ServerLevel world, Vec3 from, Vec3 to, int ticks, int rgb) {
		FriendlyByteBuf buf = start(COMET, ticks, rgb);
		writeVec(buf, from);
		writeVec(buf, to);
		send(world, from, buf);
	}

	/**
	 * Threads of mycelium tearing up out of the ground in a ring of {@code radius} and curling in to a point
	 * {@code height} above {@code at}, a bud of light at each tip; they grow, sway, and draw back into the ground.
	 */
	public static void threads(ServerLevel world, Vec3 at, float radius, float height, int count, int ticks, int rgb) {
		FriendlyByteBuf buf = start(THREADS, ticks, rgb);
		writeVec(buf, at);
		buf.writeFloat(radius);
		buf.writeFloat(height);
		buf.writeVarInt(count);
		send(world, at, buf);
	}

	/** Curtains of light like the northern lights, waving in a ring above {@code at}, shading to {@code rgb2} upwards. */
	public static void aurora(ServerLevel world, Vec3 at, float radius, int ticks, int rgb, int rgb2) {
		FriendlyByteBuf buf = start(AURORA, ticks, rgb);
		buf.writeInt(rgb2);
		writeVec(buf, at);
		buf.writeFloat(radius);
		send(world, at, buf);
	}

	// ---- the Mercenaries: coin and contract ----

	/** {@code count} gold coins flung out of {@code at}, spinning and glinting as they arc and fall. */
	public static void coins(ServerLevel world, Vec3 at, int count, float speed, int ticks) {
		FriendlyByteBuf buf = start(COINS, ticks, 0);
		writeVec(buf, at);
		buf.writeVarInt(count);
		buf.writeFloat(speed);
		send(world, at, buf);
	}

	/** Two orbs trading places along crossing arcs, {@code rgb} going from {@code a} to {@code b} and {@code rgb2} back. */
	public static void exchange(ServerLevel world, Entity a, Entity b, int ticks, int rgb, int rgb2) {
		FriendlyByteBuf buf = start(EXCHANGE, ticks, rgb);
		buf.writeInt(rgb2);
		buf.writeVarInt(a.getId());
		buf.writeVarInt(b.getId());
		send(world, a.position(), buf);
	}

	/** A great coin seal coming down on an entity, stamped on with a flash, then turning round its waist as a ring. */
	public static void seal(ServerLevel world, Entity on, float radius, int ticks, int rgb) {
		FriendlyByteBuf buf = start(SEAL, ticks, rgb);
		buf.writeVarInt(on.getId());
		buf.writeFloat(radius);
		send(world, on.position(), buf);
	}

	/**
	 * An arched door of light standing at {@code at} facing {@code yaw}: it traces itself and swings open on warm light,
	 * and stays open until {@link #doorClose} for its {@code owner} (or {@code ticks} at most, or the owner leaves).
	 */
	public static void door(ServerLevel world, Vec3 at, float yaw, Entity owner, int ticks, int rgb) {
		FriendlyByteBuf buf = start(DOOR, ticks, rgb);
		writeVec(buf, at);
		buf.writeFloat(yaw);
		buf.writeVarInt(owner.getId());
		send(world, at, buf);
	}

	/** The door {@code owner} opened swings shut and fades. */
	public static void doorClose(ServerLevel world, Entity owner) {
		FriendlyByteBuf buf = start(DOOR_CLOSE, 1, 0);
		buf.writeVarInt(owner.getId());
		send(world, owner.position(), buf);
	}

	/** A giant coin tossed above an entity's head: it spins, hangs, and comes down on it. */
	public static void flip(ServerLevel world, Entity over, int ticks, int rgb) {
		FriendlyByteBuf buf = start(FLIP, ticks, rgb);
		buf.writeVarInt(over.getId());
		send(world, over.position(), buf);
	}

	// ---- the Tempters: hearts, wings and dreams ----

	/** A stream of small hearts flowing from {@code from} into {@code to}, a big heart beating once at the source. */
	public static void hearts(ServerLevel world, Entity from, Entity to, int count, int ticks, int rgb) {
		FriendlyByteBuf buf = start(HEARTS, ticks, rgb);
		buf.writeVarInt(from.getId());
		buf.writeVarInt(to.getId());
		buf.writeVarInt(count);
		send(world, from.position(), buf);
	}

	/** Membranous wings spreading from an entity's back, one great beat, a glide, and folding away. */
	public static void wings(ServerLevel world, Entity on, float span, int ticks, int rgb) {
		FriendlyByteBuf buf = start(WINGS, ticks, rgb);
		buf.writeVarInt(on.getId());
		buf.writeFloat(span);
		send(world, on.position(), buf);
	}

	public static final int SIGIL_HEART = 0;
	public static final int SIGIL_MOON = 1;
	public static final int SIGIL_MASK = 2;

	/** A glyph hanging over an entity's head for as long as it lasts: a heart, a sleeping moon or a mask. */
	public static void sigil(ServerLevel world, Entity over, int kind, int ticks, int rgb) {
		FriendlyByteBuf buf = start(SIGIL, ticks, rgb);
		buf.writeVarInt(over.getId());
		buf.writeVarInt(kind);
		send(world, over.position(), buf);
	}

	/** A heart over an entity that cracks, splits in two, and falls apart in shards. */
	public static void heartbreak(ServerLevel world, Entity over, int ticks, int rgb) {
		FriendlyByteBuf buf = start(HEARTBREAK, ticks, rgb);
		buf.writeVarInt(over.getId());
		send(world, over.position(), buf);
	}

	/** Slow waves of a song rolling out over the ground to {@code radius}, crescent moons rising out of them. */
	public static void lullaby(ServerLevel world, Vec3 at, float radius, int ticks, int rgb) {
		FriendlyByteBuf buf = start(LULLABY, ticks, rgb);
		writeVec(buf, at);
		buf.writeFloat(radius);
		send(world, at, buf);
	}

	// ---- the Masked: faces ----

	/** A face torn off at {@code from} flying in an arc to an entity's face and pressed onto it. */
	public static void mask(ServerLevel world, Vec3 from, Entity to, int ticks, int rgb) {
		FriendlyByteBuf buf = start(MASK, ticks, rgb);
		writeVec(buf, from);
		buf.writeVarInt(to.getId());
		send(world, from, buf);
	}

	/** {@code count} faces circling an entity's head, faster and faster, then closing in on it. */
	public static void masks(ServerLevel world, Entity around, int count, float radius, int ticks, int rgb) {
		FriendlyByteBuf buf = start(MASKS, ticks, rgb);
		buf.writeVarInt(around.getId());
		buf.writeVarInt(count);
		buf.writeFloat(radius);
		send(world, around.position(), buf);
	}

	/** A spider's web spun out flat on the ground at {@code at}: spokes first, then the spiral. */
	public static void web(ServerLevel world, Vec3 at, float radius, int ticks, int rgb) {
		FriendlyByteBuf buf = start(WEB, ticks, rgb);
		writeVec(buf, at);
		buf.writeFloat(radius);
		send(world, at, buf);
	}

	/** This player's camera shakes, {@code strength} degrees at first, dying away over {@code ticks}. */
	public static void shake(ServerPlayer player, float strength, int ticks) {
		sendTo(player, start(SHAKE, ticks, 0), buf -> buf.writeFloat(strength));
	}

	/** This player's view widens by {@code amount} (0.2 is a fifth) at once and eases back over {@code ticks}: a rush. */
	public static void fov(ServerPlayer player, float amount, int ticks) {
		sendTo(player, start(FOV, ticks, 0), buf -> buf.writeFloat(amount));
	}

	/** This player's screen edges beat like a heart, {@code beats} times over {@code ticks}. */
	public static void pulse(ServerPlayer player, int beats, int ticks, int rgb) {
		sendTo(player, start(PULSE, ticks, rgb), buf -> buf.writeVarInt(beats));
	}

	private static void sendTo(ServerPlayer player, FriendlyByteBuf buf, java.util.function.Consumer<FriendlyByteBuf> rest) {
		if (Server.canSend(player, RacePackets.FX)) {
			rest.accept(buf);
			Server.send(player, RacePackets.FX, buf);
		}
	}

	private static FriendlyByteBuf start(int kind, int ticks, int rgb) {
		FriendlyByteBuf buf = Network.create();
		buf.writeVarInt(kind);
		buf.writeVarInt(ticks);
		buf.writeInt(rgb);
		return buf;
	}

	private static void writeVec(FriendlyByteBuf buf, Vec3 v) {
		buf.writeDouble(v.x);
		buf.writeDouble(v.y);
		buf.writeDouble(v.z);
	}

	private static void send(ServerLevel world, Vec3 at, FriendlyByteBuf buf) {
		for (ServerPlayer player : world.players()) {
			if (player.distanceToSqr(at) < RANGE * RANGE && Server.canSend(player, RacePackets.FX)) {
				Server.send(player, RacePackets.FX, new FriendlyByteBuf(buf.copy()));
			}
		}
	}
}
