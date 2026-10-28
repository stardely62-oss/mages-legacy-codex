package euf.mageslegacy.frozen;

import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.fx.ModParticles;
import euf.mageslegacy.net.RacePackets;
import euf.mageslegacy.race.AbilityResult;
import euf.mageslegacy.race.AbilitySlots;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceData;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talents;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import euf.mageslegacy.platform.Network;
import euf.mageslegacy.platform.Network.Server;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Everything the Frozen do: sand of time as their resource, the trail of their last seconds, Blur (a blow now and
 * then passes through them), the Rewind back along that trail, the Frozen Dome, the Quickening of blocks; and their
 * weaknesses (slow natural healing, shorter potions, numbness when the hourglass is full).
 */
public final class Frozen {
	public static final int REWIND_COST = 30;
	public static final int REWIND_COST_CHEAP = 20;
	public static final int ALLY_COST = 40;
	public static final int DOME_COST = 45;
	public static final int DOME_COST_CHEAP = 35;
	public static final int QUICKEN_COST = 20;
	public static final int FREEZE_COST = 25;
	public static final int CURRENT_COST = 30;
	private static final int REWIND_COOLDOWN = 200;
	private static final int REWIND_COOLDOWN_QUICK = 140;
	private static final int REWIND_TICKS = 10;
	private static final int TRAIL = 80;
	private static final int TRAIL_LONG = 120;
	private static final int RETURN_WINDOW = 60;
	private static final int DOME_COOLDOWN = 500;
	private static final int DOME_TICKS = 120;
	private static final int DOME_TICKS_LONG = 180;
	private static final int QUICKEN_COOLDOWN = 40;
	private static final int QUICKEN_TICKS = 600;
	private static final int QUICKEN_TICKS_LONG = 1200;
	private static final int QUICKEN_SPEED = 10;
	private static final int QUICKEN_MAX = 27;
	private static final double QUICKEN_RANGE = 8;
	private static final int FREEZE_SLOT = AbilitySlots.cooldownSlot(Race.FROZEN, 3);
	private static final int FREEZE_COOLDOWN = 400;
	private static final int FREEZE_TICKS = 60;
	private static final float SHATTER_DAMAGE = 6;
	private static final int STOP_SLOT = 5;
	private static final int STOP_COOLDOWN = 6000;
	private static final int STOP_TICKS = 60;
	private static final int STOP_RADIUS = 16;
	private static final int STRIKE_TICKS = 80;
	private static final int ETERNAL_SLOT = 7;
	private static final int ETERNAL_TICKS = 120;
	private static final int CURRENT_SLOT = AbilitySlots.cooldownSlot(Race.FROZEN, 4);
	private static final int CURRENT_TICKS = 600;
	private static final int CURRENT_COOLDOWN = 6000;
	/** The Eternal Moment comes back in 20 minutes, like every race's one escape from death. */
	private static final int LONG_COOLDOWN = 24000;
	public static final float BLUR_CHANCE = 0.1f;
	/** How long Quick Step and Quick Hand last after sand is spent. */
	private static final int SPENT_TEMPO_TICKS = 160;
	public static final float BLUR_CHANCE_OVERTAKE = 0.15f;

	/** Colours of the glowing effects: pale ice, golden sand, the white of a stopped moment. */
	private static final int ICE_RGB = 0x9FCBF2;
	private static final int SAND_RGB = 0xE8C98A;
	private static final int STILL_RGB = 0xE8F4FF;
	/** Ice shards and grains of sand (the race's own particles); fine ice dust for the frozen silhouette. */
	private static final ParticleOptions ICE = ModParticles.ICE_SHARD.get();
	private static final ParticleOptions SAND = ModParticles.SAND.get();
	private static final DustParticleOptions ICE_DUST = new DustParticleOptions(new Vector3f(0.62f, 0.80f, 0.95f), 1.2f);

	/** Where every player was, newest last: one point a tick, a few seconds deep. */
	private record Point(Vec3 pos, float health, ResourceKey<Level> world) {}

	/** A Rewind under way: the player flies along {@code path} (newest first) over a few ticks. */
	private static final class Flight {
		final ServerPlayer player;
		final ServerPlayer caster;
		final List<Vec3> path;
		final float health;
		final boolean back;
		int tick;

		Flight(ServerPlayer player, ServerPlayer caster, List<Vec3> path, float health, boolean back) {
			this.player = player;
			this.caster = caster;
			this.path = path;
			this.health = health;
			this.back = back;
		}
	}

	/** Double Loop: the way back to where the Rewind started, open for a moment after it. */
	private record Way(List<Vec3> path, long until) {}

	/** A Frozen Dome, or (with {@code stop}) the Still Frame's wider, total halt. */
	private static final class Dome {
		final UUID owner;
		final ServerPlayer player;
		final ServerLevel world;
		Vec3 center;
		final double radius;
		final long until;
		final boolean stop;
		/** Projectile uuid -> its velocity and whether it had gravity, to let it go as it was. */
		final Map<UUID, Held> held = new HashMap<>();
		final Set<UUID> rewarded = new HashSet<>();

		Dome(ServerPlayer player, ServerLevel world, Vec3 center, double radius, long until, boolean stop) {
			this.owner = player.getUUID();
			this.player = player;
			this.world = world;
			this.center = center;
			this.radius = radius;
			this.until = until;
			this.stop = stop;
		}

		boolean contains(Entity e) {
			return e.level() == world && e.getBoundingBox().getCenter().distanceToSqr(center) < radius * radius;
		}
	}

	private record Held(Vec3 velocity, boolean noGravity) {}

	private record Quick(UUID owner, ServerLevel world, BlockPos pos, long until) {}

	private record Hold(ServerLevel world, long until) {}

	private record Eternal(long until, float health) {}

	/** Shattered Moment: when the Stopping Gaze lets go, its enemy cracks. */
	private record Shatter(ServerLevel world, UUID owner, long at) {}

	private static final Map<UUID, ArrayDeque<Point>> TRAILS = new HashMap<>();
	private static final List<Flight> FLIGHTS = new ArrayList<>();
	private static final Map<UUID, Way> WAYS = new HashMap<>();
	private static final List<Dome> DOMES = new ArrayList<>();
	private static final List<Quick> QUICK = new ArrayList<>();
	private static final Map<UUID, Hold> HELD_MOBS = new HashMap<>();
	private static final Map<UUID, Eternal> ETERNAL = new HashMap<>();
	private static final Map<UUID, Shatter> SHATTER = new HashMap<>();
	/** Strike from the Past: until when the next blow of a Frozen is doubled. */
	private static final Map<UUID, Long> STRIKE = new HashMap<>();
	/** Doubled Moment: blows landed since the last doubled one. */
	private static final Map<UUID, Integer> HITS = new HashMap<>();
	/** The Current: until when a Frozen's abilities recharge twice as fast. */
	private static final Map<UUID, Long> CURRENT = new HashMap<>();
	/** Silhouettes left where a Rewind began: the frozen shape glints for a second. */
	private static final List<Hold> SHAPES_AT = new ArrayList<>();
	private static final List<Vec3> SHAPES = new ArrayList<>();
	/** Sand spent towards experience. */
	private static final Map<UUID, Float> SPENT = new HashMap<>();
	private static boolean quickChanged;

	private Frozen() {
	}

	public static void reset() {
		TRAILS.clear();
		FLIGHTS.clear();
		WAYS.clear();
		DOMES.clear();
		QUICK.clear();
		HELD_MOBS.clear();
		ETERNAL.clear();
		SHATTER.clear();
		STRIKE.clear();
		HITS.clear();
		CURRENT.clear();
		SHAPES_AT.clear();
		SHAPES.clear();
		SPENT.clear();
	}

	public static boolean is(ServerPlayer player) {
		return RaceState.raceOf(player).orElse(null) == Race.FROZEN;
	}

	private static long now(MinecraftServer server) {
		return server.getTickCount();
	}

	private static boolean ready(ServerPlayer player, RaceData data, int slot) {
		return data.readyAt.getOrDefault(slot, 0L) <= now(player.getServer());
	}

	private static void cooldown(ServerPlayer player, RaceData data, int slot, int ticks) {
		// In the Current time runs twice as fast for the Frozen: the three abilities recharge in half the time
		Long current = CURRENT.get(player.getUUID());
		if (slot <= 2 && current != null && current > now(player.getServer())) {
			ticks /= 2;
		}
		// Fast Hourglass: the Rewind and the Dome recharge a quarter faster
		if (slot <= 1 && data.has(Talents.FLOW_RECHARGE)) {
			ticks = ticks * 3 / 4;
		}
		data.readyAt.put(slot, now(player.getServer()) + ticks);
		data.dirty = true;
	}

	/**
	 * Takes the sand; spending teaches (a point of race experience for every 10 grains), and with Quick Step and Quick
	 * Hand the time let run carries the Frozen on for a few seconds.
	 */
	private static void spend(ServerPlayer player, RaceData data, float cost) {
		data.addResource(-cost);
		if (data.has(Talents.FLOW_SWIFT)) {
			player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, SPENT_TEMPO_TICKS, 0, true, false, true));
		}
		if (data.has(Talents.FLOW_HASTE)) {
			player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, SPENT_TEMPO_TICKS, 0, true, false, true));
		}
		float total = SPENT.getOrDefault(player.getUUID(), 0f) + cost;
		int xp = (int) (total / 10);
		SPENT.put(player.getUUID(), total - xp * 10);
		if (xp > 0) {
			Progression.addXp(player, data, xp);
		}
	}

	public static int rewindCost(boolean cheap) {
		return cheap ? REWIND_COST_CHEAP : REWIND_COST;
	}

	public static int domeCost(boolean cheap) {
		return cheap ? DOME_COST_CHEAP : DOME_COST;
	}


	// ---- every tick ----

	public static void tick(MinecraftServer server) {
		long now = now(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			record(player);
		}
		tickFlights(server);
		tickDomes(now);
		tickQuick(now);
		// The hourglasses over quickened blocks: sent when the set changes, and every 5 s for those who came near since
		if (quickChanged || now % 100 == 0) {
			quickChanged = false;
			sendQuickened(server, now);
		}
		tickHeld(server, now);
		tickShatter(server, now);
		tickShapes(now);
		ETERNAL.entrySet().removeIf(e -> {
			if (e.getValue().until() > now) {
				return false;
			}
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			if (player != null && player.isAlive()) {
				// The moment ends: everything is as it was when it began
				player.setHealth(Math.min(player.getMaxHealth(), e.getValue().health()));
				ServerLevel world = player.serverLevel();
				world.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
				world.playSound(null, player.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.8f, 1.6f);
			}
			return true;
		});
	}

	/** One more point of the trail; a new world starts a new trail (a Rewind never crosses a portal). */
	public static void record(ServerPlayer player) {
		ArrayDeque<Point> trail = TRAILS.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
		if (!player.isAlive() || player.isSpectator() || isFlying(player)) {
			if (!player.isAlive()) {
				trail.clear();
			}
			return;
		}
		ResourceKey<Level> world = player.level().dimension();
		if (!trail.isEmpty() && !trail.peekLast().world().equals(world)) {
			trail.clear();
		}
		trail.addLast(new Point(player.position(), player.getHealth(), world));
		while (trail.size() > TRAIL_LONG + 1) {
			trail.removeFirst();
		}
	}

	public static boolean isFlying(Entity player) {
		for (Flight f : FLIGHTS) {
			if (f.player == player) {
				return true;
			}
		}
		return false;
	}

	// ---- once a second ----

	public static void tickSecond(ServerPlayer player, RaceData data) {
		data.addResource(data.has(Talents.FLOW_SAND) ? 1.5f : 0.75f);
		// A full hourglass stops: time stands still in the Frozen until they let some sand run
		if (data.resource >= RaceData.MAX_RESOURCE) {
			refresh(player, MobEffects.MOVEMENT_SLOWDOWN, 0, 60, 30);
		}
		for (Dome dome : DOMES) {
			// Quiet Harbour: players under the Dome mend
			if (!dome.stop && dome.owner.equals(player.getUUID()) && data.has(Talents.STASIS_MEND)) {
				for (Player p : dome.world.players()) {
					if (dome.contains(p)) {
						p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0, true, false, true));
					}
				}
			}
		}
	}

	private static void refresh(ServerPlayer player, net.minecraft.core.Holder<MobEffect> effect, int amplifier, int duration, int below) {
		MobEffectInstance current = player.getEffect(effect);
		if (current == null || current.getAmplifier() < amplifier || current.getDuration() <= below) {
			player.addEffect(new MobEffectInstance(effect, duration, amplifier, true, false, true));
		}
	}

	// ---- weaknesses ----

	/** Natural healing (from a full stomach) works at half speed, three quarters with Own Rhythm. */
	public static float naturalHealing(Player player) {
		if (!(player instanceof ServerPlayer sp) || !is(sp)) {
			return 1;
		}
		return RaceState.of(sp).has(Talents.FLOW_REGEN) ? 0.75f : 0.5f;
	}

	/**
	 * Potions and other good effects last 30% shorter on the Frozen (time slips past them).
	 * Ambient effects (beacons, the races' own passives) and short ones are left alone.
	 */
	public static MobEffectInstance shorten(LivingEntity entity, MobEffectInstance effect) {
		if (!(entity instanceof ServerPlayer player) || effect.isAmbient() || effect.isInfiniteDuration() || effect.getDuration() <= 60
				|| !effect.getEffect().value().isBeneficial() || !is(player)) {
			return effect;
		}
		return new MobEffectInstance(effect.getEffect(), (int) (effect.getDuration() * 0.7f), effect.getAmplifier(), false,
				effect.isVisible(), effect.showIcon());
	}

	// ---- damage ----

	/**
	 * Whether a blow doesn't land at all: nothing touches a Frozen in the middle of a Rewind, and Blur lets one blow
	 * in ten from a creature pass through them.
	 */
	public static boolean avoids(LivingEntity victim, DamageSource source) {
		if (!(victim instanceof ServerPlayer player)) {
			return false;
		}
		if (isFlying(player)) {
			return true;
		}
		Entity attacker = source.getEntity();
		if (attacker == null || attacker == player || !is(player)) {
			return false;
		}
		RaceData data = RaceState.of(player);
		if (player.getRandom().nextFloat() >= (data.has(Talents.FLOW_OVERTAKE) ? BLUR_CHANCE_OVERTAKE : BLUR_CHANCE)) {
			return false;
		}
		if (data.has(Talents.FLOW_OVERTAKE)) {
			player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1, true, false, true));
		}
		ServerLevel world = player.serverLevel();
		world.sendParticles(ICE, player.getX(), player.getY() + 1, player.getZ(), 14, 0.3, 0.6, 0.3, 0);
		world.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 4, 0.3, 0.5, 0.3, 0.02);
		// The blow passes through a frozen afterimage of the body
		Fx.ghosts(world, List.of(player.position(), player.position()), player.getYRot(), 1, 14, ICE_RGB);
		world.playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.6f, 1.6f);
		data.addResource(4);
		Progression.addXp(player, data, "frozen_blur", 2, 20);
		return true;
	}

	/**
	 * Blows that land on a Frozen: they give sand; under its own Dome Sand Ward takes a quarter off; the Eternal
	 * Moment keeps the Frozen alive (and starts by itself when health falls low).
	 */
	public static float modifyDamage(ServerPlayer player, DamageSource source, float amount) {
		RaceData data = RaceState.of(player);
		if (amount <= 0) {
			return amount;
		}
		data.addResource(2);
		if (data.has(Talents.STASIS_WARD) && DOMES.stream().anyMatch(d -> !d.stop && d.owner.equals(player.getUUID()) && d.contains(player))) {
			amount *= 0.75f;
		}
		UUID id = player.getUUID();
		if (!ETERNAL.containsKey(id) && data.has(Talents.LOOP_ETERNAL) && ready(player, data, ETERNAL_SLOT)
				&& player.getHealth() - amount < player.getMaxHealth() * 0.25f) {
			ETERNAL.put(id, new Eternal(now(player.getServer()) + ETERNAL_TICKS, player.getHealth()));
			cooldown(player, data, ETERNAL_SLOT, LONG_COOLDOWN);
			ServerLevel world = player.serverLevel();
			world.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 40, 0.5, 0.9, 0.5, 0.1);
			world.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 1.8f);
			player.displayClientMessage(Component.translatable("mages_legacy.frozen.eternal").withStyle(ChatFormatting.AQUA), true);
			Fx.sphere(world, player.position(), 1.4f, ETERNAL_TICKS, SAND_RGB, player, false);
			Fx.screen(player, ETERNAL_TICKS, SAND_RGB);
			// The moment closes around the body: sand winds in, a clock face burns under it
			Fx.helix(world, player, 1.3f, 2.6f, 3, true, 24, SAND_RGB);
			Fx.mark(world, player.position(), player, 2.0f, ETERNAL_TICKS, SAND_RGB, Fx.CLOCK);
			Fx.burst(world, player.position().add(0, 1, 0), 2.6f, 8, STILL_RGB);
			Fx.shake(player, 2.0f, 10);
		}
		if (ETERNAL.containsKey(id)) {
			amount = Math.max(0, Math.min(amount, player.getHealth() - 1));
		}
		return amount;
	}

	/** The Eternal Moment holds: no death while it lasts. */
	public static boolean allowDeath(ServerPlayer player) {
		RaceData data = RaceState.of(player);
		if (data == null || data.race != Race.FROZEN) {
			return true;
		}
		if (ETERNAL.containsKey(player.getUUID())) {
			player.setHealth(1);
			return false;
		}
		return true;
	}

	/**
	 * A Frozen's own blow. One on an enemy held in time (in its own Dome, or by its Stopping Gaze) lands 30% harder:
	 * the Dome and the Gaze open the moment to strike. Strike from the Past doubles the first one after a Rewind; Viscous Time slows the one hit;
	 * Doubled Moment lands every third blow twice (half again the damage); Sand in the Wounds draws sand from monsters.
	 */
	public static float onHit(LivingEntity victim, DamageSource source, float amount) {
		if (!(source.getEntity() instanceof ServerPlayer player) || source.getDirectEntity() != player || victim == player || !is(player)) {
			return amount;
		}
		RaceData data = RaceState.of(player);
		ServerLevel world = player.serverLevel();
		if (HELD_MOBS.containsKey(victim.getUUID())
				|| DOMES.stream().anyMatch(d -> !d.stop && d.owner.equals(player.getUUID()) && d.contains(victim))) {
			amount *= 1.3f;
			world.sendParticles(ICE, victim.getX(), victim.getY() + victim.getBbHeight() / 2, victim.getZ(), 5, 0.25, 0.25, 0.25, 0);
		}
		Long until = STRIKE.remove(player.getUUID());
		if (until != null && until >= now(player.getServer())) {
			amount *= 2;
			world.sendParticles(ParticleTypes.END_ROD, victim.getX(), victim.getY() + victim.getBbHeight() / 2, victim.getZ(), 12, 0.3, 0.3, 0.3, 0.1);
			world.playSound(null, victim.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.0f, 1.5f);
		}
		if (data.has(Talents.FLOW_VISCOUS)) {
			victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
		}
		if (data.has(Talents.FLOW_ECHO)) {
			int hits = HITS.merge(player.getUUID(), 1, Integer::sum);
			if (hits >= 3) {
				HITS.remove(player.getUUID());
				amount *= 1.5f;
				world.sendParticles(ICE, victim.getX(), victim.getY() + victim.getBbHeight() / 2, victim.getZ(), 10, 0.3, 0.3, 0.3, 0);
				world.playSound(null, victim.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8f, 1.6f);
			}
		}
		if (data.has(Talents.FLOW_FEED) && victim instanceof Enemy) {
			data.addResource(2);
		}
		return amount;
	}

	// ---- abilities ----

	public static AbilityResult use(ServerPlayer player, RaceData data, int slot) {
		return switch (slot) {
			case 0 -> rewind(player, data);
			case 1 -> dome(player, data);
			case 2 -> quicken(player, data);
			case 3 -> freeze(player, data);
			case 4 -> data.has(Talents.FLOW_CURRENT) ? current(player, data) : RaceData.talentLocked(player, Talents.FLOW_CURRENT);
			default -> AbilityResult.LOCKED;
		};
	}

	/** The trail this Frozen may rewind along, newest first: 4 seconds, 6 with the Long Loop. */
	private static List<Point> trail(ServerPlayer player, RaceData caster) {
		ArrayDeque<Point> deque = TRAILS.get(player.getUUID());
		List<Point> list = new ArrayList<>();
		if (deque == null) {
			return list;
		}
		int depth = caster.has(Talents.LOOP_LONG) ? TRAIL_LONG : TRAIL;
		Iterator<Point> it = deque.descendingIterator();
		while (it.hasNext() && list.size() <= depth) {
			list.add(it.next());
		}
		return list;
	}

	/**
	 * Rewind: back along your own trail to where you were 4 seconds ago, as a streak of light that nothing can touch.
	 * With Double Loop, pressing again soon after flies back to where the Rewind began. Sneaking (Another's Moment)
	 * rewinds the player you look at instead.
	 */
	public static AbilityResult rewind(ServerPlayer player, RaceData data) {
		if (isFlying(player)) {
			return AbilityResult.HINTED;
		}
		long now = now(player.getServer());
		Way way = WAYS.remove(player.getUUID());
		if (way != null && way.until() >= now && !data.alt) {
			FLIGHTS.add(new Flight(player, player, way.path(), player.getHealth(), true));
			startFx(player);
			flightFx(player, way.path());
			return AbilityResult.OK;
		}
		if (!ready(player, data, 0)) {
			return AbilityResult.COOLDOWN;
		}
		ServerPlayer target = player;
		int cost = rewindCost(data.has(Talents.LOOP_CHEAP));
		if (data.alt) {
			if (!data.has(Talents.LOOP_ALLY)) {
				return RaceData.talentLocked(player, Talents.LOOP_ALLY);
			}
			target = lookedAtPlayer(player, 16);
			if (target == null) {
				return AbilityResult.NO_SIGHT;
			}
			cost = ALLY_COST;
		}
		if (data.resource < cost) {
			return AbilityResult.NO_RESOURCE;
		}
		List<Point> trail = trail(target, data);
		if (trail.size() < 5) {
			player.displayClientMessage(Component.translatable("mages_legacy.frozen.no_trail"), true);
			return AbilityResult.HINTED;
		}
		spend(player, data, cost);
		cooldown(player, data, 0, data.has(Talents.LOOP_QUICK) ? REWIND_COOLDOWN_QUICK : REWIND_COOLDOWN);
		// A Rewind right after a blow saved the Frozen from the next one: that teaches
		if (target == player && player.hurtTime > 0) {
			Progression.addXp(player, data, "frozen_rewind", 5, 25);
		}
		fly(target, player, data, trail, true);
		return AbilityResult.OK;
	}

	/** Starts the flight of {@code target} back along {@code trail} (newest first). */
	private static void fly(ServerPlayer target, ServerPlayer caster, RaceData data, List<Point> trail, boolean shards) {
		List<Vec3> path = new ArrayList<>();
		for (Point p : trail) {
			path.add(p.pos());
		}
		Point oldest = trail.get(trail.size() - 1);
		target.stopRiding();
		FLIGHTS.add(new Flight(target, caster, path, oldest.health(), false));
		startFx(target);
		flightFx(target, path);
		if (shards && target == caster && data.has(Talents.LOOP_SHARDS)) {
			shards(caster, target.position());
		}
	}

	/** The start of a flight: the body turns to light, a frozen silhouette stays behind. */
	private static void startFx(ServerPlayer player) {
		ServerLevel world = player.serverLevel();
		player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, REWIND_TICKS + 4, 0, true, false, false));
		SHAPES.add(player.position());
		SHAPES_AT.add(new Hold(world, now(player.getServer()) + 20));
		world.sendParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 1, player.getZ(), 1, 0, 0, 0, 0);
		world.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 1.0f, 1.6f);
		world.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2f, 0.6f);
	}

	/** The flight seen by all: a streak of light from ice to sand along the way; for the flier, the screen's edges glow. */
	private static void flightFx(ServerPlayer player, List<Vec3> path) {
		Fx.flight(player, REWIND_TICKS);
		Fx.ribbon(player.serverLevel(), path, 26, ICE_RGB, SAND_RGB, 1.3f);
		Fx.screen(player, 16, ICE_RGB);
		// Frozen afterimages of the moments flown back through, and the view drawn in as time runs backwards
		Fx.ghosts(player.serverLevel(), path, player.getYRot(), 5, 22, ICE_RGB);
		Fx.fov(player, -0.12f, 14);
		Fx.shake(player, 0.6f, 6);
	}

	/** Shards of the Moment: where the Rewind began bursts into frost. */
	private static void shards(ServerPlayer owner, Vec3 at) {
		ServerLevel world = owner.serverLevel();
		for (LivingEntity m : world.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(3),
				e -> e.isAlive() && e instanceof Enemy)) {
			m.hurt(world.damageSources().indirectMagic(owner, owner), 4);
			m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
		}
		world.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y + 1, at.z, 40, 1.2, 0.6, 1.2, 0.08);
		world.sendParticles(ICE, at.x, at.y + 1, at.z, 30, 1.0, 0.6, 1.0, 0);
		Fx.ring(world, at, 0.2f, 3.5f, 10, ICE_RGB);
		Fx.burst(world, at.add(0, 1, 0), 3.0f, 8, ICE_RGB);
		world.playSound(null, BlockPos.containing(at), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0f, 0.7f);
	}

	private static void tickFlights(MinecraftServer server) {
		for (Iterator<Flight> it = FLIGHTS.iterator(); it.hasNext(); ) {
			Flight f = it.next();
			ServerPlayer player = f.player;
			if (player.isRemoved() || !player.isAlive()) {
				it.remove();
				continue;
			}
			ServerLevel world = player.serverLevel();
			int last = f.path.size() - 1;
			int from = Math.round((float) f.tick * last / REWIND_TICKS);
			f.tick++;
			int to = Math.min(last, Math.round((float) f.tick * last / REWIND_TICKS));
			// A streak of light along the stretch flown this tick
			Vec3 a = f.path.get(from);
			Vec3 b = f.path.get(to);
			for (int i = 0; i <= 6; i++) {
				Vec3 p = a.lerp(b, i / 6.0).add(0, 1.0, 0);
				world.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.05, 0.15, 0.05, 0.005);
				world.sendParticles(i % 2 == 0 ? ICE : SAND, p.x, p.y - 0.2, p.z, 2, 0.15, 0.3, 0.15, 0);
			}
			player.setDeltaMovement(Vec3.ZERO);
			player.fallDistance = 0;
			player.connection.teleport(b.x, b.y, b.z, player.getYRot(), player.getXRot());
			if (to >= last) {
				it.remove();
				land(player, f, world);
			}
		}
	}

	/** The end of a flight: a flash, the trail's health if the Body's Memory allows, monsters lose sight of you. */
	private static void land(ServerPlayer player, Flight f, ServerLevel world) {
		player.removeEffect(MobEffects.INVISIBILITY);
		player.clearFire();
		player.fallDistance = 0;
		player.setDeltaMovement(Vec3.ZERO);
		player.hurtMarked = true;
		world.sendParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 1, player.getZ(), 1, 0, 0, 0, 0);
		world.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 24, 0.3, 0.6, 0.3, 0.12);
		Fx.pillar(world, player.position(), 0.55f, 3.5f, 14, SAND_RGB);
		Fx.ring(world, player.position(), 0.3f, 3, 12, ICE_RGB);
		// Time sets again: a flash, and a clock face of sand under the feet
		Fx.burst(world, player.position().add(0, 1, 0), 1.6f, 7, STILL_RGB);
		Fx.mark(world, player.position(), null, 1.8f, 24, SAND_RGB, Fx.CLOCK);
		Fx.crystals(world, player.position(), 1.3f, 7, 1.5f, 26, ICE_RGB);
		Fx.shake(player, 1.2f, 6);
		world.sendParticles(SAND, player.getX(), player.getY() + 0.5, player.getZ(), 20, 0.5, 0.3, 0.5, 0);
		world.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.0f, 1.4f);
		world.playSound(null, player.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.5f, 2.0f);
		if (f.back) {
			return;
		}
		for (Mob m : world.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(32), e -> e.getTarget() == player)) {
			m.setTarget(null);
		}
		ServerPlayer caster = f.caster;
		RaceData data = caster.isRemoved() ? null : RaceState.of(caster);
		if (data == null) {
			return;
		}
		// Body's Memory: half of the health lost on the way comes back
		if (data.has(Talents.LOOP_HEAL) && f.health > player.getHealth()) {
			player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + (f.health - player.getHealth()) / 2));
		}
		// Strike from the Past: the first blow after landing hits twice as hard
		if (caster == player && data.has(Talents.LOOP_STRIKE)) {
			STRIKE.put(player.getUUID(), now(player.getServer()) + STRIKE_TICKS);
		}
		if (data.has(Talents.LOOP_CLEANSE)) {
			player.getActiveEffects().stream().filter(e -> !e.getEffect().value().isBeneficial()).map(MobEffectInstance::getEffect).toList()
					.forEach(player::removeEffect);
		}
		if (data.has(Talents.LOOP_HASTE)) {
			player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 1, false, true, true));
		}
		if (caster == player && data.has(Talents.LOOP_RETURN)) {
			List<Vec3> forward = new ArrayList<>(f.path);
			java.util.Collections.reverse(forward);
			WAYS.put(player.getUUID(), new Way(forward, now(player.getServer()) + RETURN_WINDOW));
		}
	}

	private static void tickShapes(long now) {
		for (int i = SHAPES.size() - 1; i >= 0; i--) {
			Hold h = SHAPES_AT.get(i);
			Vec3 at = SHAPES.get(i);
			if (h.until() < now) {
				SHAPES.remove(i);
				SHAPES_AT.remove(i);
				continue;
			}
			// A body-shaped glint of frost: a column of ice light, shoulders and head
			if (now % 2 == 0) {
				h.world().sendParticles(ICE_DUST, at.x, at.y + 0.9, at.z, 6, 0.18, 0.45, 0.18, 0);
				h.world().sendParticles(ICE_DUST, at.x, at.y + 1.6, at.z, 3, 0.12, 0.1, 0.12, 0);
				h.world().sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.1, at.z, 1, 0.2, 0.5, 0.2, 0);
			}
		}
	}

	@Nullable
	private static ServerPlayer lookedAtPlayer(ServerPlayer player, double range) {
		LivingEntity hit = lookedAt(player, range, e -> e instanceof ServerPlayer);
		return hit instanceof ServerPlayer p ? p : null;
	}

	@Nullable
	private static LivingEntity lookedAt(ServerPlayer player, double range, java.util.function.Predicate<LivingEntity> filter) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0f);
		Vec3 end = eye.add(look.scale(range));
		AABB box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
				e -> e instanceof LivingEntity living && living.isAlive() && e != player && filter.test(living), range * range);
		return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
	}

	// ---- the Dome ----

	public static double domeRadius(RaceData data) {
		return data.has(Talents.STASIS_WIDE) ? 7 : 5;
	}

	/** Frozen Dome: for 6 seconds enemies under it all but stop, and their arrows and fireballs hang in the air. */
	public static AbilityResult dome(ServerPlayer player, RaceData data) {
		if (!ready(player, data, 1)) {
			return AbilityResult.COOLDOWN;
		}
		int cost = domeCost(data.has(Talents.STASIS_CHEAP));
		if (data.resource < cost) {
			return AbilityResult.NO_RESOURCE;
		}
		spend(player, data, cost);
		cooldown(player, data, 1, DOME_COOLDOWN);
		ServerLevel world = player.serverLevel();
		long now = now(player.getServer());
		int domeTicks = data.has(Talents.STASIS_LONG) ? DOME_TICKS_LONG : DOME_TICKS;
		DOMES.add(new Dome(player, world, player.position(), domeRadius(data), now + domeTicks, false));
		// A dome of frosted glass (real colour, so it shows by day), a clock face of sand on the ground under it
		ServerPlayer follow = data.has(Talents.STASIS_FOLLOW) ? player : null;
		Fx.sphere(world, player.position(), (float) domeRadius(data), domeTicks, Fx.INK | ICE_RGB, follow, false);
		Fx.mark(world, player.position(), follow, (float) domeRadius(data) * 0.92f, domeTicks, SAND_RGB, Fx.CLOCK);
		Fx.ring(world, player.position(), 0.5f, (float) domeRadius(data), 8, SAND_RGB);
		Fx.burst(world, player.position().add(0, 1, 0), (float) domeRadius(data), 8, STILL_RGB);
		Fx.shake(player, 1.0f, 8);
		// Crystals of ice ring the Dome for as long as it stands
		if (follow == null) {
			Fx.crystals(world, player.position(), (float) domeRadius(data), (int) (domeRadius(data) * 2.4), 1.9f, domeTicks, ICE_RGB);
		}
		// The Still Frame crown: now and then the Dome stops the whole world around for a moment
		if (data.has(Talents.STASIS_STOP) && ready(player, data, STOP_SLOT)) {
			DOMES.add(new Dome(player, world, player.position(), STOP_RADIUS, now + STOP_TICKS, true));
			Fx.sphere(world, player.position(), STOP_RADIUS, STOP_TICKS, STILL_RGB, null, true);
			Fx.screen(player, STOP_TICKS, STILL_RGB);
			Fx.burst(world, player.position().add(0, 1, 0), STOP_RADIUS, 10, STILL_RGB);
			Fx.mark(world, player.position(), null, STOP_RADIUS * 0.6f, STOP_TICKS, STILL_RGB, Fx.CLOCK);
			Fx.shake(player, 2.5f, 12);
			cooldown(player, data, STOP_SLOT, STOP_COOLDOWN);
			world.playSound(null, player.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.5f, 2.0f);
			player.displayClientMessage(Component.translatable("mages_legacy.frozen.still_frame").withStyle(ChatFormatting.AQUA), true);
		}
		world.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.6f);
		world.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 0.5f);
		return AbilityResult.OK;
	}

	/** Whether this mob counts as an enemy of the Frozen's side: a monster, or anything hunting a player. */
	private static boolean enemy(Entity e) {
		return e instanceof LivingEntity && !(e instanceof Player) && e.isAlive()
				&& (e instanceof Enemy || (e instanceof Mob m && m.getTarget() instanceof Player));
	}

	private static void tickDomes(long now) {
		for (Iterator<Dome> it = DOMES.iterator(); it.hasNext(); ) {
			Dome d = it.next();
			ServerPlayer owner = d.player.isRemoved() ? null : d.player;
			RaceData data = owner == null ? null : RaceState.of(owner);
			if (!d.stop && owner != null && owner.level() == d.world && data.has(Talents.STASIS_FOLLOW)) {
				d.center = owner.position();
			}
			if (now >= d.until || owner == null) {
				release(d, owner, data);
				it.remove();
				continue;
			}
			AABB box = new AABB(d.center, d.center).inflate(d.radius);
			for (LivingEntity e : d.world.getEntitiesOfClass(LivingEntity.class, box, e -> (d.stop ? !(e instanceof Player) && e.isAlive() : enemy(e))
					&& d.contains(e))) {
				hold(e, d.stop, data != null && data.has(Talents.STASIS_DEEP));
			}
			for (Projectile p : d.world.getEntitiesOfClass(Projectile.class, box, p -> d.contains(p) && !(p.getOwner() instanceof Player))) {
				if (!d.held.containsKey(p.getUUID())) {
					if (p.getDeltaMovement().lengthSqr() < 1.0E-4) {
						continue;
					}
					d.held.put(p.getUUID(), new Held(p.getDeltaMovement(), p.isNoGravity()));
				}
				p.setNoGravity(true);
				p.setDeltaMovement(Vec3.ZERO);
				p.hurtMarked = true;
			}
			if (now % 4 == 0) {
				shell(d);
			}
		}
	}

	/** Holds a mob nearly still (or, under the Still Frame, wholly): slowness, and its motion damped. */
	private static void hold(LivingEntity e, boolean stop, boolean deep) {
		e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, stop ? 9 : 3, true, false));
		if (stop || deep) {
			e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 10, stop ? 9 : 1, true, false));
		}
		e.setDeltaMovement(e.getDeltaMovement().multiply(stop ? 0 : 0.3, stop ? 0 : 1, stop ? 0 : 0.3));
		e.hurtMarked = true;
	}

	/** Inside the Dome time stands: grains of sand hang still in the air, glinting (the shell itself is drawn by the clients). */
	private static void shell(Dome d) {
		var random = d.world.getRandom();
		int grains = d.stop ? 24 : 8;
		for (int i = 0; i < grains; i++) {
			double theta = random.nextDouble() * Math.PI * 2;
			double r = Math.sqrt(random.nextDouble()) * d.radius * 0.9;
			double y = random.nextDouble() * d.radius * 0.7;
			d.world.sendParticles(ModParticles.STILL_SAND.get(), d.center.x + Math.cos(theta) * r, d.center.y + 0.2 + y, d.center.z + Math.sin(theta) * r, 1, 0, 0,
					0, 0);
		}
	}

	/**
	 * The Dome ends: hanging projectiles fly on as they were (back at their shooters with the Turned Course), and
	 * with Brittleness the enemies inside take a shattering blow.
	 */
	private static void release(Dome d, @Nullable ServerPlayer owner, @Nullable RaceData data) {
		boolean back = data != null && data.has(Talents.STASIS_RETURN);
		for (Map.Entry<UUID, Held> e : d.held.entrySet()) {
			if (!(d.world.getEntity(e.getKey()) instanceof Projectile p) || p.isRemoved()) {
				continue;
			}
			p.setNoGravity(e.getValue().noGravity());
			Vec3 velocity = e.getValue().velocity();
			if (back && p.getOwner() instanceof LivingEntity shooter && shooter.isAlive() && owner != null) {
				velocity = shooter.getEyePosition().subtract(p.position()).normalize().scale(Math.max(1.2, velocity.length()));
				p.setOwner(owner);
			}
			p.setDeltaMovement(velocity);
			p.hurtMarked = true;
		}
		// As the Dome fades, the enemies inside are frozen solid for a moment
		if (!d.stop) {
			long until = now(d.world.getServer()) + 20;
			for (LivingEntity e : d.world.getEntitiesOfClass(LivingEntity.class, new AABB(d.center, d.center).inflate(d.radius), e -> enemy(e) && d.contains(e))) {
				HELD_MOBS.put(e.getUUID(), new Hold(d.world, until));
				Fx.crystals(d.world, e.position(), e.getBbWidth() * 0.55f + 0.2f, 6, e.getBbHeight() * 0.9f, 24, ICE_RGB);
			}
		}
		if (!d.stop && owner != null && data.has(Talents.STASIS_SHATTER)) {
			for (LivingEntity e : d.world.getEntitiesOfClass(LivingEntity.class, new AABB(d.center, d.center).inflate(d.radius), e -> enemy(e) && d.contains(e))) {
				e.hurt(d.world.damageSources().indirectMagic(owner, owner), 6);
				d.world.sendParticles(ParticleTypes.SNOWFLAKE, e.getX(), e.getY() + 1, e.getZ(), 10, 0.3, 0.4, 0.3, 0.05);
			}
			d.world.playSound(null, BlockPos.containing(d.center), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2f, 0.5f);
		}
		d.world.playSound(null, BlockPos.containing(d.center), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8f, 1.6f);
	}

	/** A monster died under a Dome: its Frozen learns from it. */
	public static void onDeath(LivingEntity entity) {
		if (!(entity instanceof Enemy) || entity.getServer() == null) {
			return;
		}
		for (Dome d : DOMES) {
			if (!d.stop && d.contains(entity) && d.rewarded.add(entity.getUUID())) {
				ServerPlayer owner = d.player;
				if (!owner.isRemoved()) {
					Progression.addXp(owner, RaceState.of(owner), Progression.HUNT, 4, Progression.HUNT_CAP);
				}
			}
		}
	}

	/** The Stopping Gaze (H): the enemy you look at freezes still for 3 seconds; Shattered Moment cracks it after. */
	public static AbilityResult freeze(ServerPlayer player, RaceData data) {
		if (!ready(player, data, FREEZE_SLOT)) {
			return AbilityResult.COOLDOWN;
		}
		if (data.resource < FREEZE_COST) {
			return AbilityResult.NO_RESOURCE;
		}
		LivingEntity target = lookedAt(player, 16, Frozen::enemy);
		if (target == null) {
			return AbilityResult.NO_SIGHT;
		}
		spend(player, data, FREEZE_COST);
		cooldown(player, data, FREEZE_SLOT, FREEZE_COOLDOWN);
		HELD_MOBS.put(target.getUUID(), new Hold(player.serverLevel(), now(player.getServer()) + FREEZE_TICKS));
		if (data.has(Talents.STASIS_FREEZE)) {
			SHATTER.put(target.getUUID(), new Shatter(player.serverLevel(), player.getUUID(), now(player.getServer()) + FREEZE_TICKS));
		}
		// A beam of frozen time from the eye to the enemy, and the enemy sealed in ice
		ServerLevel here = player.serverLevel();
		Fx.tendril(here, player, target, 12, STILL_RGB, ICE_RGB);
		Fx.crystals(here, target.position(), target.getBbWidth() * 0.55f + 0.2f, 7, target.getBbHeight() * 0.95f, FREEZE_TICKS, ICE_RGB);
		Fx.burst(here, target.position().add(0, target.getBbHeight() / 2, 0), 1.2f + target.getBbWidth(), 7, STILL_RGB);
		here.sendParticles(ICE, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), 16, 0.4, 0.4, 0.4, 0);
		player.serverLevel().playSound(null, target.blockPosition(), SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 1.0f, 0.5f);
		return AbilityResult.OK;
	}

	/** Shattered Moment: the ice a Stopping Gaze leaves cracks as it lets go, 6 damage and a heavy slowness. */
	private static void tickShatter(MinecraftServer server, long now) {
		SHATTER.entrySet().removeIf(e -> {
			Shatter shatter = e.getValue();
			if (shatter.at() > now) {
				return false;
			}
			ServerPlayer owner = server.getPlayerList().getPlayer(shatter.owner());
			if (owner != null && shatter.world().getEntity(e.getKey()) instanceof LivingEntity living && living.isAlive()) {
				living.hurt(shatter.world().damageSources().indirectMagic(owner, owner), SHATTER_DAMAGE);
				living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
				Vec3 center = living.position().add(0, living.getBbHeight() / 2, 0);
				Fx.burst(shatter.world(), center, 1.4f + living.getBbWidth(), 8, ICE_RGB);
				shatter.world().sendParticles(ICE, center.x, center.y, center.z, 24, 0.4, 0.5, 0.4, 0.15);
				shatter.world().playSound(null, living.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2f, 0.7f);
			}
			return true;
		});
	}

	private static void tickHeld(MinecraftServer server, long now) {
		HELD_MOBS.entrySet().removeIf(e -> {
			Entity entity = e.getValue().world().getEntity(e.getKey());
			if (!(entity instanceof LivingEntity living) || !living.isAlive() || e.getValue().until() < now) {
				return true;
			}
			hold(living, true, false);
			if (now % 3 == 0) {
				e.getValue().world().sendParticles(ICE, living.getX(), living.getY() + living.getBbHeight() / 2, living.getZ(), 4,
						living.getBbWidth() / 2, living.getBbHeight() / 2, living.getBbWidth() / 2, 0);
			}
			return false;
		});
	}

	// ---- the Quickening ----

	/**
	 * Quickening: the block you look at works ten times faster for 30 seconds: furnaces, brewing stands, campfires,
	 * and anything that grows. Wide Flow takes the blocks around it too, for a minute.
	 */
	public static AbilityResult quicken(ServerPlayer player, RaceData data) {
		if (!ready(player, data, 2)) {
			return AbilityResult.COOLDOWN;
		}
		int cost = QUICKEN_COST;
		if (data.resource < cost) {
			return AbilityResult.NO_RESOURCE;
		}
		ServerLevel world = player.serverLevel();
		HitResult hit = player.pick(QUICKEN_RANGE, 1.0f, false);
		if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
			player.displayClientMessage(Component.translatable("mages_legacy.frozen.nothing_to_quicken"), true);
			return AbilityResult.HINTED;
		}
		BlockPos center = blockHit.getBlockPos();
		int r = data.has(Talents.FLOW_AREA) ? 1 : 0;
		long until = now(player.getServer()) + (data.has(Talents.FLOW_AREA) ? QUICKEN_TICKS_LONG : QUICKEN_TICKS);
		int added = 0;
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
			if (quickenable(world, pos)) {
				BlockPos fixed = pos.immutable();
				QUICK.removeIf(q -> q.world() == world && q.pos().equals(fixed));
				QUICK.add(new Quick(player.getUUID(), world, fixed, until));
				quickChanged = true;
				world.sendParticles(SAND, fixed.getX() + 0.5, fixed.getY() + 1.0, fixed.getZ() + 0.5, 6, 0.3, 0.1, 0.3, 0);
				added++;
			}
		}
		if (added == 0) {
			player.displayClientMessage(Component.translatable("mages_legacy.frozen.nothing_to_quicken"), true);
			return AbilityResult.HINTED;
		}
		// One Frozen keeps no more than 27 blocks quickened: the oldest go first
		List<Quick> mine = QUICK.stream().filter(q -> q.owner().equals(player.getUUID())).toList();
		for (int i = 0; i < mine.size() - QUICKEN_MAX; i++) {
			QUICK.remove(mine.get(i));
		}
		spend(player, data, cost);
		cooldown(player, data, 2, QUICKEN_COOLDOWN);
		Progression.addXp(player, data, "frozen_quicken", 3, 15);
		// Say what was quickened and for how long: a furnace without fuel shows nothing by itself
		long seconds = (until - now(player.getServer())) / 20;
		Component what = added > 1 ? Component.translatable("mages_legacy.frozen.quickened_many", added)
				: world.getBlockState(center).getBlock().getName();
		player.displayClientMessage(Component.translatable("mages_legacy.frozen.quickened", what, seconds).withStyle(ChatFormatting.GOLD), true);
		world.sendParticles(ParticleTypes.END_ROD, center.getX() + 0.5, center.getY() + 1.1, center.getZ() + 0.5, 8, 0.3, 0.2, 0.3, 0.02);
		world.playSound(null, center, SoundEvents.SAND_FALL, SoundSource.PLAYERS, 1.0f, 1.6f);
		Fx.ring(world, Vec3.atBottomCenterOf(center.above()), 0.2f, 1.6f + r, 12, SAND_RGB);
		Fx.pillar(world, Vec3.atBottomCenterOf(center.above()), 0.45f, 1.5f, 10, SAND_RGB);
		Fx.mark(world, Vec3.atBottomCenterOf(center.above()), null, 1.1f + (float) r, 30, SAND_RGB, Fx.CLOCK);
		world.playSound(null, center, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.8f);
		return AbilityResult.OK;
	}

	/** Whether a block has anything to quicken: a working block entity (furnace, brewing stand) or random growth. */
	public static boolean quickenable(ServerLevel world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		BlockEntity be = world.getBlockEntity(pos);
		return state.isRandomlyTicking() || (be != null && ticker(world, state, be) != null);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	@Nullable
	private static BlockEntityTicker ticker(Level world, BlockState state, BlockEntity be) {
		return state.getTicker(world, (BlockEntityType) be.getType());
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void tickQuick(long now) {
		for (Iterator<Quick> it = QUICK.iterator(); it.hasNext(); ) {
			Quick q = it.next();
			if (q.until() < now || !q.world().hasChunkAt(q.pos())) {
				it.remove();
				quickChanged = true;
				continue;
			}
			BlockState state = q.world().getBlockState(q.pos());
			BlockEntity be = q.world().getBlockEntity(q.pos());
			BlockEntityTicker ticker = be == null ? null : ticker(q.world(), state, be);
			if (ticker != null) {
				for (int i = 1; i < QUICKEN_SPEED; i++) {
					ticker.tick(q.world(), q.pos(), state, be);
				}
			}
			// Growth: plants get a random tick every half second, about ten times their usual pace
			if (state.isRandomlyTicking() && now % 10 == 0) {
				state.randomTick(q.world(), q.pos(), q.world().getRandom());
			}
			if (now % 5 == 0) {
				q.world().sendParticles(SAND, q.pos().getX() + 0.5, q.pos().getY() + 1.05, q.pos().getZ() + 0.5, 3, 0.3, 0.05, 0.3, 0);
				q.world().sendParticles(ParticleTypes.WAX_OFF, q.pos().getX() + 0.5, q.pos().getY() + 0.5, q.pos().getZ() + 0.5, 1, 0.4, 0.4, 0.4, 0);
			}
		}
	}

	/** Tells every player which blocks in their world are quickened and for how long. */
	private static void sendQuickened(MinecraftServer server, long now) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!Server.canSend(player, RacePackets.QUICKENED)) {
				continue;
			}
			List<Quick> here = QUICK.stream().filter(q -> q.world() == player.level()).toList();
			FriendlyByteBuf buf = Network.create();
			buf.writeVarInt(here.size());
			for (Quick q : here) {
				buf.writeBlockPos(q.pos());
				buf.writeVarInt((int) Math.max(0, q.until() - now));
			}
			Server.send(player, RacePackets.QUICKENED, buf);
		}
	}

	public static boolean quickened(ServerLevel world, BlockPos pos) {
		return QUICK.stream().anyMatch(q -> q.world() == world && q.pos().equals(pos));
	}

	/**
	 * The Current crown (sneak + G): for 30 seconds the Frozen does everything twice as fast, its abilities
	 * recharging in half the time too. Once in 5 minutes.
	 */
	public static AbilityResult current(ServerPlayer player, RaceData data) {
		if (!ready(player, data, CURRENT_SLOT)) {
			return AbilityResult.COOLDOWN;
		}
		if (data.resource < CURRENT_COST) {
			return AbilityResult.NO_RESOURCE;
		}
		spend(player, data, CURRENT_COST);
		cooldown(player, data, CURRENT_SLOT, CURRENT_COOLDOWN);
		CURRENT.put(player.getUUID(), now(player.getServer()) + CURRENT_TICKS);
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, CURRENT_TICKS, 1, true, true, true));
		player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, CURRENT_TICKS, 1, true, true, true));
		player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, CURRENT_TICKS, 0, true, true, true));
		ServerLevel world = player.serverLevel();
		world.sendParticles(SAND, player.getX(), player.getY() + 1, player.getZ(), 40, 0.6, 0.8, 0.6, 0);
		world.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 2.0f);
		Fx.ring(world, player.position(), 0.5f, 6, 14, SAND_RGB);
		Fx.screen(player, CURRENT_TICKS, SAND_RGB);
		// Time pours through the body: sand winds up and out, and grains stream off it all the Current long
		Fx.aura(world, player, CURRENT_TICKS, SAND_RGB, Fx.GRAINS);
		Fx.helix(world, player, 1.2f, 2.8f, 4, false, 24, SAND_RGB);
		Fx.mark(world, player.position(), player, 2.2f, 40, SAND_RGB, Fx.CLOCK);
		Fx.burst(world, player.position().add(0, 1, 0), 2.6f, 8, SAND_RGB);
		Fx.fov(player, 0.12f, 20);
		Fx.shake(player, 1.5f, 10);
		return AbilityResult.OK;
	}

	/** The race changed: its domes, quickenings and moments end. */
	public static void cleanup(ServerPlayer player) {
		UUID id = player.getUUID();
		DOMES.removeIf(d -> d.owner.equals(id));
		QUICK.removeIf(q -> q.owner().equals(id));
		quickChanged = true;
		ETERNAL.remove(id);
		WAYS.remove(id);
	}
}
