package euf.mageslegacy.voidborn;

import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.fx.ModParticles;
import euf.mageslegacy.race.AbilityResult;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceData;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talents;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Everything the Voidborn do. Part of each of them stayed in the void, so space gives way to them: the Void Step
 * carries them through solid rock, the Well pulls enemies into one point, the Excision tears a creature out of the
 * world into their pocket for a while, and the Pocket is a world of their own. Water burns them, rain weakens them,
 * and the world's beds won't let them sleep: only in the pocket.
 */
public final class Voidborn {
	public static final int STEP_COST = 20;
	public static final int WELL_COST = 40;
	public static final int EXCISE_COST = 35;
	public static final int POCKET_COST = 20;
	public static final double STEP_RANGE = 24;
	/** The share of arrows and fireballs that pass through a Voidborn. */
	public static final float PROJECTILE_PASS = 0.15f;
	private static final int STEP_TICKS = 10;
	private static final int STEP_COOLDOWN = 160;
	private static final int WELL_TICKS = 80;
	private static final int WELL_COOLDOWN = 500;
	private static final double WELL_RADIUS = 8;
	private static final int EXCISE_COOLDOWN = 300;
	private static final double EXCISE_RANGE = 24;
	private static final int POCKET_COOLDOWN = 200;
	private static final int GAZE_SLOT = 4;
	public static final int GAZE_COST = 50;
	private static final int GAZE_COOLDOWN = 6000;
	private static final int HEARTH_SLOT = 6;
	private static final int LONG_COOLDOWN = 24000;
	/** Ticks between the rift opening and the step through it. */
	private static final int POCKET_DELAY = 8;

	/** The void's colours: violet light, the near-black of the void itself laid on as real colour, starlight. */
	public static final int VOID_RGB = 0x9B7FD4;
	public static final int VOID_INK = Fx.INK | 0x2A1446;
	public static final int STAR_RGB = 0xE8E0FF;

	private record Step(ServerPlayer player, List<Vec3> path, Set<UUID> hit, boolean through, List<ServerPlayer> companions) {}

	private record Well(ServerLevel world, Vec3 center, double radius, long until, ServerPlayer owner, Set<UUID> pulled) {}

	/** An open way left by a Rift Step: whoever walks into its start comes out at its end. */
	private record Door(ServerLevel world, Vec3 from, Vec3 to, long until) {}

	/**
	 * A creature held in its puller's pocket: where it came from, and when it goes back. One taken by the Excision
	 * stays until its Voidborn presses the key again ({@code until} is then far off); the Gaze's come back by themselves.
	 */
	private record Held(Entity entity, UUID owner, ServerLevel origin, Vec3 pos, float yaw, long until, boolean ally, boolean excised) {}

	/** A step into (or out of) the pocket, made a moment after the rift opens; {@code home}: out to one's spawn. */
	private record Passage(ServerPlayer player, long at, boolean in, boolean home) {}

	private static final List<Step> STEPS = new ArrayList<>();
	private static final java.util.Map<UUID, Integer> STEP_AT = new java.util.HashMap<>();
	private static final List<Well> WELLS = new ArrayList<>();
	private static final List<Held> HELD = new ArrayList<>();
	private static final List<Passage> PASSAGES = new ArrayList<>();
	private static final List<Door> DOORS = new ArrayList<>();
	/** When each Voidborn was last hurt (server ticks): the pocket doesn't open for one still under blows. */
	private static final java.util.Map<UUID, Long> LAST_HURT = new java.util.HashMap<>();
	private static final int POCKET_CALM = 100;
	/** Until when (client world time) this client's own player phases through blocks; set by the effect packet. */
	public static long clientPhaseUntil;

	private Voidborn() {
	}

	public static void reset() {
		STEPS.clear();
		STEP_AT.clear();
		WELLS.clear();
		HELD.clear();
		PASSAGES.clear();
		DOORS.clear();
		LAST_HURT.clear();
	}

	/** A Voidborn took a blow: for a few seconds its pocket won't open. */
	public static void onHurt(LivingEntity self, float amount) {
		if (amount > 0 && self instanceof ServerPlayer player && is(player)) {
			LAST_HURT.put(player.getUUID(), now(player.getServer()));
		}
	}

	// ---- what the talents change ----

	public static int stepCost(boolean cheap) {
		return cheap ? 14 : STEP_COST;
	}

	public static double stepRange(RaceData data) {
		return data.has(Talents.STEP_FAR) ? 32 : STEP_RANGE;
	}

	public static int exciseCost(boolean cheap) {
		return cheap ? 25 : EXCISE_COST;
	}

	public static int pocketCost(boolean cheap) {
		return cheap ? 10 : POCKET_COST;
	}

	public static double wellRadius(RaceData data) {
		return data.has(Talents.WELL_WIDE) ? 11 : WELL_RADIUS;
	}

	public static boolean is(Player player) {
		return player instanceof ServerPlayer sp && RaceState.raceOf(sp).orElse(null) == Race.VOIDBORN;
	}

	private static long now(MinecraftServer server) {
		return server.getTickCount();
	}

	private static boolean ready(ServerPlayer player, RaceData data, int slot) {
		return data.readyAt.getOrDefault(slot, 0L) <= now(player.getServer());
	}

	private static void cooldown(ServerPlayer player, RaceData data, int slot, int ticks) {
		data.readyAt.put(slot, now(player.getServer()) + ticks);
		data.dirty = true;
	}

	/**
	 * Whether this player is passing through solid matter right now: then blocks don't hold or smother it. On the
	 * server that is a Void Step under way; on a client, its own player while the server says so.
	 */
	public static boolean phasing(Player player) {
		if (player.level().isClientSide()) {
			return player.isLocalPlayer() && player.level().getGameTime() <= clientPhaseUntil;
		}
		for (Step s : STEPS) {
			if (s.player() == player) {
				return true;
			}
		}
		return false;
	}

	/** An enemy for the Well and the Step: a monster, or a mob hunting a player. Pets and other players never. */
	private static boolean enemy(Entity e, ServerPlayer player) {
		return e instanceof LivingEntity living && living.isAlive() && e != player && !(e instanceof Player)
				&& !(e instanceof TamableAnimal tame && tame.isTame()) && !e.getTags().contains("mages_legacy_bond")
				&& (e instanceof Enemy || (e instanceof Mob m && m.getTarget() instanceof Player));
	}

	/** Too big for any pocket: the dragon, the Wither, the Warden, the elder guardian. */
	private static boolean tooBig(Entity e) {
		return e instanceof EnderDragon || e instanceof WitherBoss || e instanceof Warden || e instanceof ElderGuardian;
	}

	// ---- once a second ----

	public static void tickSecond(ServerPlayer player, RaceData data) {
		ServerLevel world = player.serverLevel();
		boolean home = Pocket.in(player) || world.dimension() == Level.END;
		boolean dark = world.getMaxLocalRawBrightness(BlockPos.containing(player.getEyePosition())) <= 7;
		data.addResource(home ? 1.8f : dark ? 1.2f : 0.6f);
		// Water burns them as it burns the endermen; rain saps them
		if (world.getFluidState(player.blockPosition()).is(FluidTags.WATER) || world.getFluidState(BlockPos.containing(player.getEyePosition())).is(FluidTags.WATER)) {
			player.hurt(world.damageSources().magic(), data.has(Talents.POCKET_WATER) ? 1 : 2);
			world.sendParticles(ModParticles.VOID_MOTE, player.getX(), player.getY() + 1, player.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
			world.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 1.4f);
		} else if (world.isRainingAt(BlockPos.containing(player.getEyePosition()))) {
			refresh(player, MobEffects.WEAKNESS, 60);
			if (!data.has(Talents.POCKET_STORM)) {
				refresh(player, MobEffects.MOVEMENT_SLOWDOWN, 60);
			}
		}
		if (data.has(Talents.POCKET_REST) && Pocket.in(player)) {
			refresh(player, MobEffects.REGENERATION, 60);
		}
	}

	private static void refresh(ServerPlayer player, MobEffect effect, int duration) {
		MobEffectInstance current = player.getEffect(effect);
		if (current == null || current.getDuration() <= 30) {
			player.addEffect(new MobEffectInstance(effect, duration, 0, true, false, true));
		}
	}

	/** Nothing touches a Voidborn inside the rock; and about one arrow or fireball in seven passes through the rest of them. */
	public static boolean avoids(LivingEntity self, DamageSource source) {
		if (!(self instanceof ServerPlayer player) || !is(player)) {
			return false;
		}
		if (phasing(player)) {
			return true;
		}
		if (source.is(DamageTypeTags.IS_PROJECTILE) && player.getRandom().nextFloat() < PROJECTILE_PASS) {
			ServerLevel world = player.serverLevel();
			world.sendParticles(ModParticles.VOID_MOTE, player.getX(), player.getY() + 1.2, player.getZ(), 10, 0.25, 0.4, 0.25, 0.03);
			world.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.4f, 1.8f);
			return true;
		}
		return false;
	}

	/** Endermen take a Voidborn's look for one of their own. */
	public static boolean kin(Player player) {
		return is(player);
	}

	/** A creature died at a Voidborn's hand: a little void flows back; an enderman is worth a lot of experience. */
	public static void onKill(ServerPlayer player, LivingEntity victim) {
		if (!is(player) || !(victim instanceof Enemy)) {
			return;
		}
		RaceData data = RaceState.of(player);
		data.addResource(3);
		// The dark is the void's own: a kill in it teaches more
		if (player.serverLevel().getMaxLocalRawBrightness(BlockPos.containing(player.getEyePosition())) <= 7) {
			Progression.addXp(player, data, Progression.HUNT, 2, Progression.HUNT_CAP);
		}
		if (data.has(Talents.WELL_HUNGER) && WELLS.stream().anyMatch(w -> w.owner() == player && w.pulled().contains(victim.getUUID()))) {
			data.addResource(10);
		}
		// Kin of the void teach the most, though an enderman farm is no faster a road than any other hunt
		if (victim instanceof EnderMan) {
			Progression.addXp(player, data, Progression.HUNT, 8, Progression.HUNT_CAP);
		}
	}

	/** The world's beds won't hold a Voidborn: they sleep only in their pocket. */
	public static boolean mayNotSleep(ServerPlayer player) {
		if (!is(player) || Pocket.in(player)) {
			return false;
		}
		player.displayClientMessage(Component.translatable("mages_legacy.voidborn.no_sleep"), true);
		return true;
	}

	/**
	 * Sleeping in the pocket passes the night out in the world too: once a Voidborn has slept there long enough and
	 * nobody in the overworld is left awake, morning comes.
	 */
	public static void tickSleep(MinecraftServer server) {
		ServerLevel pocket = Pocket.world(server);
		if (pocket == null) {
			return;
		}
		boolean rested = pocket.players().stream().anyMatch(p -> p.isSleeping() && p.getSleepTimer() >= 99);
		ServerLevel overworld = server.overworld();
		if (!rested || !overworld.isNight() || overworld.players().stream().anyMatch(p -> !p.isSpectator() && !p.isSleeping())) {
			return;
		}
		long time = overworld.getDayTime() + 24000;
		overworld.setDayTime(time - time % 24000);
		// A night slept at home in the void
		for (ServerPlayer p : pocket.players()) {
			if (p.isSleeping() && is(p)) {
				Progression.addXp(p, RaceState.of(p), 10);
			}
		}
		for (ServerLevel w : List.of(pocket, overworld)) {
			for (ServerPlayer p : w.players()) {
				if (p.isSleeping()) {
					p.stopSleepInBed(false, true);
				}
			}
		}
	}

	// ---- abilities ----

	public static AbilityResult use(ServerPlayer player, RaceData data, int slot) {
		return switch (slot) {
			case 0 -> step(player, data);
			case 1 -> well(player, data);
			case 2 -> excise(player, data);
			case 3 -> pocket(player, data);
			case 4 -> data.has(Talents.ABYSS_GAZE) ? gaze(player, data) : RaceData.talentLocked(player, Talents.ABYSS_GAZE);
			default -> AbilityResult.LOCKED;
		};
	}

	/**
	 * Void Step: half a second's passage through the void, 24 blocks where you look, through walls, floors and
	 * ceilings alike, coming out where the body fits (never inside the rock, never in lava). Bedrock and other
	 * unbreakable things stop it. What it passes through on the way takes 4 and is slowed.
	 */
	public static AbilityResult step(ServerPlayer player, RaceData data) {
		if (STEPS.stream().anyMatch(s -> s.player() == player)) {
			return AbilityResult.HINTED;
		}
		long now = now(player.getServer());
		int cost = stepCost(data.has(Talents.STEP_CHEAP));
		if (!ready(player, data, 0)) {
			return AbilityResult.COOLDOWN;
		}
		if (data.resource < cost) {
			return AbilityResult.NO_RESOURCE;
		}
		List<Vec3> path = stepPath(player, stepRange(data));
		if (path.size() < 2) {
			return AbilityResult.NO_SPACE;
		}
		data.addResource(-cost);
		cooldown(player, data, 0, data.has(Talents.STEP_QUICK) ? 100 : STEP_COOLDOWN);
		ServerLevel world = player.serverLevel();
		boolean through = path.stream().anyMatch(p -> !fits(player, p));
		player.stopRiding();
		// Taken Along: one player standing right beside goes through too
		List<ServerPlayer> companions = !data.has(Talents.STEP_ALLY) ? List.of()
				: world.getPlayers(p -> p != player && p.isAlive() && !p.isSpectator() && p.distanceToSqr(player) < 4).stream().limit(1).toList();
		STEPS.add(new Step(player, path, new HashSet<>(), through, companions));
		STEP_AT.put(player.getUUID(), 0);
		player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, STEP_TICKS + 4, 0, true, false, false));
		Vec3 start = path.get(0);
		Vec3 end = path.get(path.size() - 1);
		// The world tears where the body goes in and where it will come out; between, a dark streak through the rock
		boolean door = data.has(Talents.STEP_RIFT);
		Fx.rift(world, start.add(0, 1, 0), 2.4f, 1.3f, door ? 100 : 16, VOID_RGB);
		if (door) {
			DOORS.add(new Door(world, start, path.get(path.size() - 1), now + 100));
		}
		Fx.rift(world, end.add(0, 1, 0), 2.4f, 1.3f, STEP_TICKS + 14, VOID_RGB);
		Fx.ribbon(world, path, STEP_TICKS + 12, VOID_INK, VOID_RGB, 1.1f);
		Fx.burst(world, start.add(0, 1, 0), 1.3f, 6, VOID_INK);
		Fx.phase(player, STEP_TICKS);
		Fx.voidScreen(player, STEP_TICKS + 4);
		Fx.fov(player, 0.3f, STEP_TICKS + 6);
		world.sendParticles(ModParticles.VOID_MOTE, start.x, start.y + 1, start.z, 24, 0.4, 0.7, 0.4, 0.06);
		world.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 0.5f);
		world.playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 0.6f, 1.6f);
		return AbilityResult.OK;
	}

	/**
	 * The way through: points a quarter block apart along the look, through anything breakable, as far as the body
	 * can come out. From the far end back, the first spot where the body fits (or fits a little higher or lower)
	 * ends it; the path then runs straight to that spot. Empty if there is none.
	 */
	static List<Vec3> stepPath(ServerPlayer player, double range) {
		ServerLevel world = player.serverLevel();
		Vec3 look = player.getViewVector(1.0f).normalize();
		Vec3 start = player.position();
		List<Vec3> line = new ArrayList<>();
		line.add(start);
		for (double d = 0.25; d <= range; d += 0.25) {
			Vec3 p = start.add(look.scale(d));
			if (unbreakable(player, p) || !world.getWorldBorder().isWithinBounds(BlockPos.containing(p))) {
				break;
			}
			line.add(p);
		}
		double[] lifts = {0, 0.5, 1, -0.5, -1, 1.5, -1.5};
		for (int i = line.size() - 1; i >= 6; i--) {
			for (double lift : lifts) {
				Vec3 spot = line.get(i).add(0, lift, 0);
				if (fits(player, spot)) {
					List<Vec3> path = new ArrayList<>(line.subList(0, i + 1));
					path.set(path.size() - 1, spot);
					return path;
				}
			}
		}
		return List.of();
	}

	/** Whether the body would stand free at this spot: nothing solid, no lava. */
	private static boolean fits(ServerPlayer player, Vec3 spot) {
		AABB box = player.getBoundingBox().move(spot.subtract(player.position()));
		ServerLevel world = player.serverLevel();
		return world.noCollision(player, box) && world.getBlockStatesIfLoaded(box).noneMatch(s -> s.getFluidState().is(FluidTags.LAVA));
	}

	/** Whether the body at this spot would overlap something nothing can pass: bedrock, barriers, portal frames. */
	private static boolean unbreakable(ServerPlayer player, Vec3 spot) {
		ServerLevel world = player.serverLevel();
		AABB box = player.getBoundingBox().move(spot.subtract(player.position()));
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
			if (world.getBlockState(pos).getDestroySpeed(world, pos) < 0) {
				return true;
			}
		}
		return false;
	}

	private static void tickSteps(long now) {
		for (Iterator<Step> it = STEPS.iterator(); it.hasNext(); ) {
			Step s = it.next();
			ServerPlayer player = s.player();
			if (player.isRemoved() || !player.isAlive()) {
				it.remove();
				STEP_AT.remove(player.getUUID());
				continue;
			}
			ServerLevel world = player.serverLevel();
			int step = STEP_AT.merge(player.getUUID(), 1, Integer::sum);
			int last = s.path().size() - 1;
			// Eased: slow out of the rift, fast through the rock, slow into the light again
			float t0 = ease((float) (step - 1) / STEP_TICKS);
			float t1 = ease((float) step / STEP_TICKS);
			Vec3 a = s.path().get(Math.min(last, Math.round(t0 * last)));
			Vec3 b = s.path().get(Math.min(last, Math.round(t1 * last)));
			AABB swept = player.getBoundingBox().move(a.subtract(player.position())).minmax(player.getBoundingBox().move(b.subtract(player.position())));
			for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class, swept, e -> enemy(e, player) && !s.hit().contains(e.getUUID()))) {
				s.hit().add(e.getUUID());
				RaceData data = RaceState.of(player);
				boolean sever = data.has(Talents.STEP_SEVER);
				e.hurt(world.damageSources().indirectMagic(player, player), sever ? 9 : data.has(Talents.STEP_CUT) ? 6 : 4);
				e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, sever ? 2 : 1));
				if (data.has(Talents.STEP_REND)) {
					e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 1));
				}
				if (data.has(Talents.STEP_MEND)) {
					player.heal(2);
				}
				if (sever) {
					Fx.slash(world, e.position().add(0, e.getBbHeight() / 2, 0), b.subtract(a), 1.2f + e.getBbWidth(), 90, 9, VOID_RGB);
				}
				Fx.burst(world, e.position().add(0, e.getBbHeight() / 2, 0), 0.8f + e.getBbWidth(), 6, VOID_RGB);
			}
			for (int i = 0; i <= 3; i++) {
				Vec3 p = a.lerp(b, i / 3.0);
				world.sendParticles(ModParticles.VOID_MOTE, p.x, p.y + 1, p.z, 2, 0.25, 0.5, 0.25, 0.02);
				world.sendParticles(ModParticles.STAR, p.x, p.y + 1, p.z, 1, 0.4, 0.6, 0.4, 0.0);
			}
			player.setDeltaMovement(Vec3.ZERO);
			player.fallDistance = 0;
			player.connection.teleport(b.x, b.y, b.z, player.getYRot(), player.getXRot());
			if (step >= STEP_TICKS) {
				it.remove();
				STEP_AT.remove(player.getUUID());
				emerge(player, b, s.through());
				for (ServerPlayer companion : s.companions()) {
					if (companion.isAlive() && companion.serverLevel() == world) {
						companion.teleportTo(world, b.x + 0.6, b.y, b.z, companion.getYRot(), companion.getXRot());
						companion.fallDistance = 0;
						Fx.rift(world, b.add(0.6, 1, 0), 2.2f, 1.2f, 12, VOID_RGB);
					}
				}
			}
		}
	}

	private static float ease(float t) {
		t = Math.max(0, Math.min(1, t));
		return t * t * (3 - 2 * t);
	}

	/** Out of the void again: a flash, cracks in the ground where the body landed, the world shaking a little. */
	private static void emerge(ServerPlayer player, Vec3 at, boolean through) {
		ServerLevel world = player.serverLevel();
		player.removeEffect(MobEffects.INVISIBILITY);
		// Veil: monsters that hunted the Voidborn lose it in the void
		if (RaceState.of(player).has(Talents.STEP_VEIL)) {
			for (Mob m : world.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(32), m -> m.getTarget() == player)) {
				m.setTarget(null);
			}
		}
		player.setDeltaMovement(Vec3.ZERO);
		player.hurtMarked = true;
		player.fallDistance = 0;
		Fx.burst(world, at.add(0, 1, 0), 1.7f, 7, VOID_RGB);
		Fx.ring(world, at, 0.3f, 2.8f, 10, VOID_INK);
		Fx.mark(world, at, null, 1.6f, 40, VOID_INK, Fx.CRACKS);
		Fx.shake(player, 1.2f, 7);
		// Exit Funnel: the void closing behind the body drags the enemies around to where it came out
		if (RaceState.of(player).has(Talents.STEP_PULL)) {
			for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(5), e -> enemy(e, player))) {
				Vec3 to = at.subtract(e.position());
				e.setDeltaMovement(to.scale(0.25).add(0, 0.25, 0));
				e.hurtMarked = true;
				Fx.tendril(world, e, player, 8, VOID_RGB, STAR_RGB);
			}
			Fx.vortex(world, at.add(0, 1, 0), 1.4f, 16, VOID_RGB);
		}
		world.sendParticles(ModParticles.VOID_MOTE, at.x, at.y + 1, at.z, 30, 0.2, 0.4, 0.2, 0.12);
		world.sendParticles(ModParticles.STAR, at.x, at.y + 1, at.z, 12, 0.6, 0.8, 0.6, 0.0);
		world.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.2f);
		if (through) {
			Progression.addXp(player, RaceState.of(player), "voidborn_step", 1, 20);
		}
	}

	/** Where the look meets a block within {@code range}, or {@code range} along it. */
	private static Vec3 aim(ServerPlayer player, double range) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0f).scale(range));
		BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation().add(hit.getDirection().getNormal().getX() * 1.5, hit.getDirection().getNormal().getY() * 1.5,
				hit.getDirection().getNormal().getZ() * 1.5);
	}

	/**
	 * The Well: a black hole where you look (up to 20 blocks) for 4 seconds. Enemies within 8 blocks, their arrows
	 * and loose items are dragged into it; at its heart it hurts; at the end it collapses with a blow.
	 */
	public static AbilityResult well(ServerPlayer player, RaceData data) {
		if (!ready(player, data, 1)) {
			return AbilityResult.COOLDOWN;
		}
		if (data.resource < WELL_COST) {
			return AbilityResult.NO_RESOURCE;
		}
		data.addResource(-WELL_COST);
		cooldown(player, data, 1, WELL_COOLDOWN);
		openWell(player, data, aim(player, 20));
		return AbilityResult.OK;
	}

	/** A Well of this player's opens at {@code center}. */
	public static void openWell(ServerPlayer player, RaceData data, Vec3 center) {
		ServerLevel world = player.serverLevel();
		int ticks = data.has(Talents.WELL_LONG) ? 120 : WELL_TICKS;
		double radius = wellRadius(data);
		WELLS.add(new Well(world, center, radius, now(player.getServer()) + ticks, player, new HashSet<>()));
		Fx.vortex(world, center, (float) radius * 0.45f, ticks, VOID_RGB);
		Fx.mark(world, ground(world, center), null, (float) radius * 0.45f, ticks + 10, VOID_INK, Fx.CRACKS);
		Fx.shake(player, 0.8f, 8);
		world.playSound(null, BlockPos.containing(center), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2f, 0.5f);
		world.playSound(null, BlockPos.containing(center), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.5f, 1.6f);
	}

	/** The ground under a point (up to 12 blocks down), or the point itself over a drop. */
	private static Vec3 ground(ServerLevel world, Vec3 p) {
		BlockPos.MutableBlockPos pos = BlockPos.containing(p).mutable();
		for (int i = 0; i < 12; i++) {
			net.minecraft.world.phys.shapes.VoxelShape shape = world.getBlockState(pos).getCollisionShape(world, pos);
			if (!shape.isEmpty() && pos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y) <= p.y) {
				return new Vec3(p.x, pos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y), p.z);
			}
			pos.move(net.minecraft.core.Direction.DOWN);
		}
		return p;
	}

	private static void tickWells(MinecraftServer server, long now) {
		for (Iterator<Well> it = WELLS.iterator(); it.hasNext(); ) {
			Well w = it.next();
			ServerPlayer owner = w.owner().isRemoved() ? null : w.owner();
			ServerLevel world = w.world();
			AABB reach = new AABB(w.center(), w.center()).inflate(w.radius());
			RaceData data = owner == null ? null : RaceState.of(owner);
			if (owner != null) {
				for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class, reach, e -> enemy(e, owner))) {
					pull(e, w.center(), 0.16, w.radius());
					w.pulled().add(e.getUUID());
					if (now % 20 == 0 && e.getBoundingBox().getCenter().distanceTo(w.center()) < 1.8) {
						e.hurt(world.damageSources().indirectMagic(owner, owner), data.has(Talents.WELL_HEART) ? 3 : 2);
					}
				}
			}
			// Arrows not of their own, and loose things; with Swallowing, arrows reaching the heart are gone
			for (Entity e : world.getEntities(null, reach, e -> e instanceof ItemEntity
					|| e instanceof Projectile p && (owner == null || p.getOwner() != owner))) {
				pull(e, w.center(), 0.12, w.radius());
				if (e instanceof Projectile && data != null && data.has(Talents.WELL_EAT) && e.position().distanceTo(w.center()) < 1.2) {
					e.discard();
					data.addResource(2);
				}
			}
			if (now % 4 == 0) {
				world.sendParticles(ModParticles.STAR, w.center().x, w.center().y, w.center().z, 2, 1.5, 1.5, 1.5, 0.0);
			}
			if (w.until() <= now) {
				it.remove();
				collapse(w, owner);
			}
		}
	}

	/** Drags an entity towards a point, harder the nearer it is, never past it. */
	private static void pull(Entity e, Vec3 center, double strength, double radius) {
		Vec3 to = center.subtract(e.getBoundingBox().getCenter());
		double d = to.length();
		if (d < 0.3) {
			e.setDeltaMovement(e.getDeltaMovement().scale(0.3));
		} else {
			double force = strength * (0.4 + 0.6 * (1 - Math.min(1, d / radius)));
			e.setDeltaMovement(e.getDeltaMovement().scale(0.6).add(to.normalize().scale(force * Math.min(1, d))));
		}
		e.hurtMarked = true;
		e.fallDistance = 0;
	}

	/** The Well collapses: a blow at its heart for whatever it holds, a flash, and the world drawing breath. */
	private static void collapse(Well w, ServerPlayer owner) {
		ServerLevel world = w.world();
		Vec3 c = w.center();
		if (owner != null) {
			RaceData data = RaceState.of(owner);
			for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(4), e -> enemy(e, owner))) {
				e.hurt(world.damageSources().indirectMagic(owner, owner), data.has(Talents.WELL_COLLAPSE) ? 8 : 6);
			}
			if (data.has(Talents.WELL_HOLD)) {
				for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(w.radius()), e -> enemy(e, owner))) {
					e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
				}
			}
			if (data != null && !w.pulled().isEmpty()) {
				Progression.addXp(owner, data, "voidborn_well", Math.min(8, w.pulled().size()), 40);
			}
			Fx.shake(owner, 1.6f, 10);
		}
		Fx.burst(world, c, 4.0f, 9, VOID_RGB);
		Fx.ring(world, ground(world, c), 6, 0.3f, 10, VOID_INK);
		world.sendParticles(ModParticles.VOID_MOTE, c.x, c.y, c.z, 40, 0.3, 0.3, 0.3, 0.25);
		world.sendParticles(ModParticles.STAR, c.x, c.y, c.z, 20, 1.2, 1.2, 1.2, 0.0);
		world.playSound(null, BlockPos.containing(c), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.7f, 0.5f);
		world.playSound(null, BlockPos.containing(c), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 0.6f);
	}

	/**
	 * Excision: the creature you look at (up to 24 blocks, in plain sight) is torn into your pocket for 6 seconds and
	 * then put back where it was. An enemy comes back dazed; a friend comes back mended. Bosses are too big for it.
	 */
	public static AbilityResult excise(ServerPlayer player, RaceData data) {
		return excise(player, data, lookedAt(player, EXCISE_RANGE));
	}

	/** The Excision of a given creature (the one looked at, or one a test picks). */
	public static AbilityResult excise(ServerPlayer player, RaceData data, LivingEntity target) {
		// Pressed at nothing while holding someone: everyone held comes back now
		if (target == null || HELD.stream().anyMatch(h -> h.entity() == target)) {
			if (holding(player)) {
				release(player);
				return AbilityResult.OK;
			}
			return AbilityResult.NO_SIGHT;
		}
		if (tooBig(target)) {
			return AbilityResult.NO_SIGHT;
		}
		boolean ally = !enemy(target, player);
		// Only one enemy at a time; friends, as many as you like
		if (!ally && HELD.stream().anyMatch(h -> h.excised() && !h.ally() && h.owner().equals(player.getUUID()))) {
			player.displayClientMessage(Component.translatable("mages_legacy.voidborn.one_enemy"), true);
			return AbilityResult.HINTED;
		}
		if (!ready(player, data, 2)) {
			return AbilityResult.COOLDOWN;
		}
		int cost = exciseCost(data.has(Talents.EXCISE_CHEAP));
		if (data.resource < cost) {
			return AbilityResult.NO_RESOURCE;
		}
		ServerLevel pocket = Pocket.world(player.getServer());
		if (pocket == null) {
			return AbilityResult.LOCKED;
		}
		data.addResource(-cost);
		data.dirty = true;
		take(player, data, pocket, target, ally);
		// A player taken brings the players standing right beside them along
		if (target instanceof Player) {
			for (ServerPlayer other : player.serverLevel().getPlayers(p -> p != player && p != target && p.isAlive() && !p.isSpectator()
					&& p.distanceToSqr(target) < 9 && HELD.stream().noneMatch(h -> h.entity() == p))) {
				take(player, data, pocket, other, true);
			}
		}
		if (!ally) {
			Progression.addXp(player, data, "voidborn_excise", 3, 30);
		}
		return AbilityResult.OK;
	}

	/** Lets go of everyone this Voidborn's Excision holds: they come back where they were. */
	private static void release(ServerPlayer player) {
		long now = now(player.getServer());
		HELD.replaceAll(h -> h.excised() && h.owner().equals(player.getUUID())
				? new Held(h.entity(), h.owner(), h.origin(), h.pos(), h.yaw(), now, h.ally(), true) : h);
	}

	/** One creature torn into the pocket: an enemy into the sealed cell, a friend onto the island. */
	private static void take(ServerPlayer player, RaceData data, ServerLevel pocket, LivingEntity target, boolean ally) {
		ServerLevel world = player.serverLevel();
		int room = Pocket.room(player, data);
		Pocket.build(pocket, room, data.voidborn, Pocket.half(data));
		Vec3 from = target.position();
		float yaw = target.getYRot();
		// A thread of void to it, a rift behind it, and it is drawn in
		Fx.tendril(world, player, target, 10, VOID_RGB, STAR_RGB);
		Fx.rift(world, from.add(0, target.getBbHeight() / 2, 0), target.getBbHeight() + 1.2f, target.getBbWidth() + 1.0f, 18, VOID_RGB);
		Fx.burst(world, from.add(0, target.getBbHeight() / 2, 0), 1.2f + target.getBbWidth(), 7, VOID_INK);
		implode(world, from.add(0, target.getBbHeight() / 2, 0));
		world.playSound(null, target.blockPosition(), SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.2f, 0.5f);
		target.stopRiding();
		target.ejectPassengers();
		Vec3 into = ally ? Pocket.center(room).add(world.getRandom().nextDouble() * 2 - 1, 0, world.getRandom().nextDouble() * 2 - 1) : Pocket.cell(room);
		pocket.setChunkForced((int) Math.floor(into.x) >> 4, (int) Math.floor(into.z) >> 4, true);
		Entity moved = Pocket.move(target, pocket, into, yaw);
		// Dropped from High: an enemy comes back out of the sky, if there is sky above its spot
		Vec3 back = from;
		if (!ally && data.has(Talents.EXCISE_DROP)) {
			Vec3 up = from.add(0, 10, 0);
			if (world.noCollision(target.getBoundingBox().move(0, 10, 0))) {
				back = up;
			}
		}
		HELD.add(new Held(moved, player.getUUID(), world, back, yaw, Long.MAX_VALUE, ally, true));
	}


	/** Motes of void drawn into a point from all around. */
	private static void implode(ServerLevel world, Vec3 c) {
		for (int i = 0; i < 24; i++) {
			double a = world.getRandom().nextDouble() * Math.PI * 2;
			double y = (world.getRandom().nextDouble() - 0.5) * 2;
			Vec3 out = new Vec3(Math.cos(a) * 1.6, y, Math.sin(a) * 1.6);
			Vec3 at = c.add(out);
			world.sendParticles(ModParticles.VOID_MOTE, at.x, at.y, at.z, 0, -out.x * 0.12, -out.y * 0.12, -out.z * 0.12, 1);
		}
	}

	/** The living creature the eye rests on within {@code range}, if no wall stands between. */
	private static LivingEntity lookedAt(ServerPlayer player, double range) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0f);
		BlockHitResult wall = player.level().clip(new ClipContext(eye, eye.add(look.scale(range)), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, player));
		double reach = wall.getType() == HitResult.Type.MISS ? range : wall.getLocation().distanceTo(eye);
		Vec3 end = eye.add(look.scale(reach));
		AABB box = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box, e -> e instanceof LivingEntity l && l.isAlive() && e != player, reach * reach);
		return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
	}

	/**
	 * Someone joins the game standing in a pocket that isn't theirs (taken in by a friend's Excision, then gone
	 * offline): nobody holds them any more, so they come out at their spawn point.
	 */
	public static void onJoin(ServerPlayer player) {
		RaceData data = RaceState.of(player);
		if (!Pocket.in(player) || (data != null && data.race == Race.VOIDBORN && Pocket.inOwnRoom(player, data))
				|| HELD.stream().anyMatch(h -> h.entity() == player)) {
			return;
		}
		ServerLevel to = player.getServer().getLevel(player.getRespawnDimension());
		BlockPos spawn = player.getRespawnPosition();
		Vec3 at = to != null && spawn != null
				? Player.findRespawnPositionAndUseSpawnBlock(to, spawn, player.getRespawnAngle(), player.isRespawnForced(), true).orElse(null) : null;
		if (at == null) {
			to = player.getServer().overworld();
			at = Vec3.atBottomCenterOf(to.getSharedSpawnPos());
		}
		player.teleportTo(to, at.x, at.y, at.z, player.getYRot(), player.getXRot());
	}

	/** Whether this Voidborn holds someone in its pocket by the Excision now. */
	public static boolean holding(ServerPlayer player) {
		return HELD.stream().anyMatch(h -> h.excised() && h.owner().equals(player.getUUID()));
	}

	/** What the client shows of a Voidborn beyond the shared data: whether its Excision holds someone. */
	public static net.minecraft.nbt.CompoundTag clientInfo(ServerPlayer player) {
		net.minecraft.nbt.CompoundTag nbt = new net.minecraft.nbt.CompoundTag();
		nbt.putBoolean("holding", holding(player));
		return nbt;
	}

	private static void tickHeld(MinecraftServer server, long now) {
		for (Iterator<Held> it = HELD.iterator(); it.hasNext(); ) {
			Held h = it.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(h.owner());
			// A held one is let go when its Voidborn lets go, leaves or dies
			boolean ownerGone = owner == null || !owner.isAlive();
			// An enemy held costs its Voidborn 1 void a second; when the void runs out, it falls back out
			boolean dry = false;
			if (h.excised() && !h.ally() && !ownerGone && h.until() > now && now % 20 == 0) {
				RaceData upkeep = RaceState.of(owner);
				if (upkeep.resource < 1) {
					dry = true;
				} else {
					upkeep.addResource(-1);
				}
			}
			if (h.until() > now && !dry && !ownerGone && !h.entity().isRemoved()) {
				// Gnawing Void: an enemy held by the Excision wears away, down to a third of its health
				if (h.excised() && !h.ally() && now % 20 == 0 && h.entity() instanceof LivingEntity living
						&& RaceState.of(owner).has(Talents.EXCISE_LONG) && living.getHealth() > living.getMaxHealth() / 3) {
					living.hurt(living.damageSources().magic(), 2);
				}
				continue;
			}
			it.remove();
			if (owner != null && h.excised()) {
				// However it ended (let go, or gone inside), the Excision now starts to recharge
				RaceData data = RaceState.of(owner);
				if (ready(owner, data, 2)) {
					cooldown(owner, data, 2, EXCISE_COOLDOWN);
				}
				data.dirty = true;
			}
			Entity e = h.entity();
			ServerLevel pocket = Pocket.world(server);
			if (pocket != null && HELD.stream().noneMatch(o -> o.owner().equals(h.owner()))) {
				Vec3 cell = e.position();
				pocket.setChunkForced((int) Math.floor(cell.x) >> 4, (int) Math.floor(cell.z) >> 4, false);
			}
			if (e.isRemoved() || (e instanceof LivingEntity l && !l.isAlive())) {
				continue;
			}
			Entity back = Pocket.move(e, h.origin(), h.pos(), h.yaw());
			ServerLevel world = h.origin();
			Vec3 c = h.pos().add(0, back.getBbHeight() / 2, 0);
			Fx.rift(world, c, back.getBbHeight() + 1.2f, back.getBbWidth() + 1.0f, 16, VOID_RGB);
			Fx.burst(world, c, 1.2f + back.getBbWidth(), 7, VOID_RGB);
			world.sendParticles(ModParticles.VOID_MOTE, c.x, c.y, c.z, 24, 0.2, 0.3, 0.2, 0.14);
			world.playSound(null, BlockPos.containing(h.pos()), SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.2f, 0.8f);
			if (back instanceof LivingEntity living) {
				if (h.ally()) {
					living.heal(6);
				} else {
					living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
					living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
				}
			}
		}
	}

	/**
	 * The Pocket: a rift opens before you and a moment later you are through it, in your own room of the pocket
	 * world; pressed there, the same way back to the very spot you left. Going in costs void; coming out is free.
	 */
	public static AbilityResult pocket(ServerPlayer player, RaceData data) {
		if (PASSAGES.stream().anyMatch(p -> p.player() == player)) {
			return AbilityResult.HINTED;
		}
		boolean in = !Pocket.inOwnRoom(player, data);
		Long hurt = LAST_HURT.get(player.getUUID());
		if (in && hurt != null && now(player.getServer()) - hurt < POCKET_CALM) {
			player.displayClientMessage(Component.translatable("mages_legacy.voidborn.pocket_calm"), true);
			return AbilityResult.HINTED;
		}
		// The pocket is a home, not a way out of a fight: it won't open while monsters near are after you
		if (in && !player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(16),
				m -> m.isAlive() && m.getTarget() == player).isEmpty()) {
			player.displayClientMessage(Component.translatable("mages_legacy.voidborn.pocket_hunted"), true);
			return AbilityResult.HINTED;
		}
		if (in) {
			if (!ready(player, data, 3)) {
				return AbilityResult.COOLDOWN;
			}
			int cost = pocketCost(data.has(Talents.POCKET_CHEAP));
			if (data.resource < cost) {
				return AbilityResult.NO_RESOURCE;
			}
			data.addResource(-cost);
			cooldown(player, data, 3, data.has(Talents.POCKET_QUICK) ? 80 : POCKET_COOLDOWN);
		}
		ServerLevel world = player.serverLevel();
		Vec3 ahead = player.position().add(player.getViewVector(1.0f).multiply(1, 0, 1).normalize().scale(1.2)).add(0, 1.3, 0);
		Fx.rift(world, ahead, 2.8f, 1.7f, POCKET_DELAY + 18, VOID_RGB);
		Fx.voidScreen(player, POCKET_DELAY * 2 + 6);
		Fx.fov(player, -0.15f, POCKET_DELAY + 4);
		world.playSound(null, player.blockPosition(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.4f, 1.8f);
		world.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 0.6f);
		PASSAGES.add(new Passage(player, now(player.getServer()) + POCKET_DELAY, in, !in && data.alt && data.has(Talents.POCKET_HOME)));
		return AbilityResult.OK;
	}

	private static void tickPassages(long now) {
		for (Iterator<Passage> it = PASSAGES.iterator(); it.hasNext(); ) {
			Passage p = it.next();
			if (p.at() > now) {
				continue;
			}
			it.remove();
			ServerPlayer player = p.player();
			if (player.isRemoved() || !player.isAlive()) {
				continue;
			}
			RaceData data = RaceState.of(player);
			if (p.in()) {
				Pocket.enter(player, data);
			} else {
				Pocket.leave(player, data, p.home());
				if (data.has(Talents.POCKET_WARD)) {
					player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 1, true, true, true));
				}
			}
			// The rift closing behind on the far side
			ServerLevel world = player.serverLevel();
			Vec3 at = player.position().add(0, 1.3, 0);
			Fx.rift(world, at, 2.8f, 1.7f, 14, VOID_RGB);
			world.sendParticles(ModParticles.STAR, at.x, at.y, at.z, 20, 0.8, 1.0, 0.8, 0.0);
			world.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.2f);
		}
	}

	/** Rift Step doors: whoever steps into the start of the way comes out at its end. */
	private static void tickDoors(MinecraftServer server, long now) {
		DOORS.removeIf(d -> d.until() < now);
		for (Door d : DOORS) {
			for (ServerPlayer p : d.world().getPlayers(p -> p.isAlive() && !p.isSpectator() && p.position().distanceTo(d.from()) < 1.0
					&& !phasing(p))) {
				p.teleportTo(d.world(), d.to().x, d.to().y, d.to().z, p.getYRot(), p.getXRot());
				p.fallDistance = 0;
				Fx.burst(d.world(), d.to().add(0, 1, 0), 1.2f, 6, VOID_RGB);
				d.world().playSound(null, p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.3f);
			}
		}
	}

	/** Pocket Garden: in a Voidborn's room, whatever grows there grows three times as fast. */
	private static void tickGardens(MinecraftServer server) {
		ServerLevel pocket = Pocket.world(server);
		if (pocket == null) {
			return;
		}
		for (ServerPlayer player : pocket.players()) {
			RaceData data = RaceState.of(player);
			if (data == null || !data.has(Talents.POCKET_GARDEN) || !Pocket.inOwnRoom(player, data)) {
				continue;
			}
			BlockPos c = BlockPos.containing(Pocket.center(data.voidborn.room));
			int half = Pocket.half(data);
			for (BlockPos pos : BlockPos.betweenClosed(c.offset(-half, -1, -half), c.offset(half, 2, half))) {
				net.minecraft.world.level.block.state.BlockState state = pocket.getBlockState(pos);
				if (state.isRandomlyTicking() && (state.getBlock() instanceof net.minecraft.world.level.block.BonemealableBlock || state.is(net.minecraft.world.level.block.Blocks.SUGAR_CANE)
						|| state.is(net.minecraft.world.level.block.Blocks.CACTUS) || state.is(net.minecraft.world.level.block.Blocks.NETHER_WART))) {
					for (int i = 0; i < 2; i++) {
						state = pocket.getBlockState(pos);
						if (state.isRandomlyTicking()) {
							state.randomTick(pocket, pos.immutable(), pocket.getRandom());
						}
					}
				}
			}
		}
	}

	/**
	 * The Mage's Gaze crown (Alt): every enemy within 16 blocks falls into the void at once for 4 seconds, and comes
	 * back dazed. Once in 5 minutes.
	 */
	public static AbilityResult gaze(ServerPlayer player, RaceData data) {
		if (!ready(player, data, GAZE_SLOT)) {
			return AbilityResult.COOLDOWN;
		}
		if (data.resource < GAZE_COST) {
			return AbilityResult.NO_RESOURCE;
		}
		ServerLevel pocket = Pocket.world(player.getServer());
		ServerLevel world = player.serverLevel();
		List<LivingEntity> enemies = world.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(16),
				e -> enemy(e, player) && !tooBig(e) && e.distanceToSqr(player) < 256).stream().limit(12).toList();
		if (pocket == null) {
			return AbilityResult.LOCKED;
		}
		if (enemies.isEmpty()) {
			return AbilityResult.NO_ENEMIES;
		}
		data.addResource(-GAZE_COST);
		cooldown(player, data, GAZE_SLOT, GAZE_COOLDOWN);
		int room = Pocket.room(player, data);
		Pocket.build(pocket, room, data.voidborn, Pocket.half(data));
		Vec3 cell = Pocket.cell(room);
		pocket.setChunkForced((int) Math.floor(cell.x) >> 4, (int) Math.floor(cell.z) >> 4, true);
		long until = now(player.getServer()) + 80;
		for (LivingEntity e : enemies) {
			Vec3 from = e.position();
			Fx.rift(world, from.add(0, e.getBbHeight() / 2, 0), e.getBbHeight() + 1.2f, e.getBbWidth() + 1.0f, 16, VOID_RGB);
			implode(world, from.add(0, e.getBbHeight() / 2, 0));
			e.stopRiding();
			e.ejectPassengers();
			Entity moved = Pocket.move(e, pocket, cell, e.getYRot());
			HELD.add(new Held(moved, player.getUUID(), world, from, e.getYRot(), until, false, false));
		}
		// The mage's eye opens over the Voidborn for a moment
		Fx.vortex(world, player.position().add(0, 4, 0), 1.6f, 30, VOID_RGB);
		Fx.mark(world, player.position(), null, 6, 60, VOID_INK, Fx.CRACKS);
		Fx.voidScreen(player, 12);
		Fx.shake(player, 2.5f, 14);
		world.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.8f, 0.5f);
		return AbilityResult.OK;
	}

	/**
	 * Hearth of the Mage crown: a killing blow sends a Voidborn into its Pocket instead, with half its health and
	 * everything it carries, once in 20 minutes. It comes out (H) at its spawn point.
	 */
	public static boolean allowDeath(ServerPlayer player) {
		RaceData data = RaceState.of(player);
		if (data == null || data.race != Race.VOIDBORN || !data.has(Talents.POCKET_HEARTH) || !ready(player, data, HEARTH_SLOT)
				|| Pocket.world(player.getServer()) == null) {
			return true;
		}
		cooldown(player, data, HEARTH_SLOT, LONG_COOLDOWN);
		player.setHealth(player.getMaxHealth() / 2);
		player.clearFire();
		player.removeAllEffects();
		Fx.rift(player.serverLevel(), player.position().add(0, 1.3, 0), 2.8f, 1.7f, 20, VOID_RGB);
		Fx.voidScreen(player, 24);
		boolean inPocket = Pocket.inOwnRoom(player, data);
		if (!inPocket) {
			Pocket.enter(player, data);
			// Out again to safety, not back into whatever killed it
			data.voidborn.returnWorld = player.getRespawnDimension();
			BlockPos spawn = player.getRespawnPosition() != null ? player.getRespawnPosition() : player.getServer().overworld().getSharedSpawnPos();
			data.voidborn.returnPos = Vec3.atBottomCenterOf(spawn.above());
		}
		player.displayClientMessage(Component.translatable("mages_legacy.voidborn.hearth").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), false);
		return false;
	}

	// ---- every tick ----

	public static void tick(MinecraftServer server) {
		long now = now(server);
		tickSteps(now);
		tickWells(server, now);
		tickHeld(server, now);
		tickPassages(now);
		tickDoors(server, now);
		Pocket.catchFallers(server);
		if (now % 20 == 0) {
			tickGardens(server);
		}
	}

	/** The race changed: whatever it held goes back, its wells close. */
	public static void cleanup(ServerPlayer player) {
		WELLS.removeIf(w -> w.owner() == player);
		STEPS.removeIf(s -> s.player() == player);
		HELD.replaceAll(h -> h.owner().equals(player.getUUID()) ? new Held(h.entity(), h.owner(), h.origin(), h.pos(), h.yaw(), 0, h.ally(), h.excised()) : h);
	}
}
