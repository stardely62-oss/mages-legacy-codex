package euf.mageslegacy.bloodkin;

import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.fx.ModParticles;
import euf.mageslegacy.race.AbilityResult;
import euf.mageslegacy.race.AbilitySlots;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceData;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Everything the Bloodkin do: blood as their resource, which only hunting fills and which slowly drains away;
 * vampirism; Thirst when the blood runs low (faster and harder-hitting, but no natural healing); the Blood Dash, the
 * Harvest and the Sacrifice; and their weaknesses (sunlight burns a bare head, ordinary food barely feeds them).
 * Hearing heartbeats through walls is drawn by the client (see the outline mixin).
 */
public final class Bloodkin {
	public static final int DASH_COST = 20;
	public static final int HARVEST_COST = 40;
	public static final int SWARM_COST = 30;
	public static final int FEAST_COST = 30;
	public static final int TRANSFUSE_COST = 25;
	private static final int TRANSFUSE_SLOT = 7;
	private static final int TRANSFUSE_COOLDOWN = 200;
	public static final int THIRST_BELOW = 20;
	public static final int SENSE = 12;
	public static final int SENSE_WIDE = 24;
	private static final int DASH_COOLDOWN = 120;
	private static final int DASH_TICKS = 5;
	private static final int DOUBLE_WINDOW = 60;
	private static final int HARVEST_COOLDOWN = 400;
	private static final int SACRIFICE_COOLDOWN = 400;
	private static final int SWARM_SLOT = AbilitySlots.cooldownSlot(Race.BLOODKIN, 3);
	private static final int SWARM_COOLDOWN = 600;
	private static final int SWARM_TICKS = 60;
	/** Long Flight: the swarm holds longer and gathers again sooner. */
	private static final int SWARM_COOLDOWN_LONG = 400;
	private static final int SWARM_TICKS_LONG = 100;
	/** All the ways a blow grows (Thirst, the night, Execution, a mark, an Ambush) add up, to twice the blow at most. */
	private static final float MAX_BONUS = 1.0f;
	private static final int FEAST_SLOT = AbilitySlots.cooldownSlot(Race.BLOODKIN, 4);
	private static final int FEAST_TICKS = 160;
	private static final int PRINCE_SLOT = 6;
	private static final int LONG_COOLDOWN = 24000;
	private static final int FEAST_COOLDOWN = 6000;
	private static final int OFFERING_TICKS = 100;
	private static final int MARK_TICKS = 120;

	/** Colours of the glowing effects: fresh blood, and the dark wine of old blood. */
	private static final int BLOOD_RGB = 0xE0283C;
	private static final int WINE_RGB = 0x6A0F1E;
	/** The same, laid on as real colour under the glow (see {@link Fx#INK}). */
	private static final int BLOOD_INK = Fx.INK | BLOOD_RGB;
	private static final int WINE_INK = Fx.INK | WINE_RGB;
	/** Blood lying on the ground: darker than blood in the air. */
	private static final int POOL_INK = Fx.INK | 0x8E0E1E;
	/** The deep red a Bloodkin's own heart beats in at the screen's edges. */
	private static final int HEART_RGB = 0xB01226;

	private record Dash(ServerPlayer player, List<Vec3> path, Set<UUID> hit, boolean free) {}

	private record Bleed(ServerLevel world, long until, float perSecond, UUID owner) {}

	private record Swarm(ServerPlayer player, long until, List<Bat> bats) {}

	private static final List<Dash> DASHES = new ArrayList<>();
	private static final Map<UUID, Integer> DASH_STEP = new HashMap<>();
	private static final Map<UUID, Long> DOUBLE = new HashMap<>();
	private static final Map<UUID, Bleed> BLEEDING = new HashMap<>();
	private static final List<Swarm> SWARMS = new ArrayList<>();
	/** Until when: the Sacrifice's doubled vampirism, the Feast, a Harvest mark (by victim), the Storm's next pulses. */
	private static final Map<UUID, Long> OFFERING = new HashMap<>();
	private static final Map<UUID, Long> FEAST = new HashMap<>();
	private static final Map<UUID, Long> MARKED = new HashMap<>();
	private static final Map<UUID, List<Long>> STORM = new HashMap<>();
	/** Blows landed (for the Bite on every fourth), and blood drunk towards race experience. */
	private static final Map<UUID, Integer> BLOWS = new HashMap<>();
	private static final Map<UUID, Float> DRUNK = new HashMap<>();

	private Bloodkin() {
	}

	public static void reset() {
		DASHES.clear();
		DASH_STEP.clear();
		DOUBLE.clear();
		BLEEDING.clear();
		SWARMS.forEach(s -> s.bats().forEach(Entity::discard));
		SWARMS.clear();
		OFFERING.clear();
		FEAST.clear();
		MARKED.clear();
		STORM.clear();
		BLOWS.clear();
		DRUNK.clear();
	}

	public static boolean is(Player player) {
		return player instanceof ServerPlayer sp && RaceState.raceOf(sp).orElse(null) == Race.BLOODKIN;
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

	/** Whether a creature has blood at all: not the undead, golems or armour stands. */
	public static boolean bloodful(Entity entity) {
		return entity instanceof LivingEntity living && !living.isInvertedHealAndHarm() && !(entity instanceof AbstractGolem) && !(entity instanceof ArmorStand);
	}

	/** A living creature with blood to drink right now. */
	/** A bat of some Bloodkin's swarm: part of a body, not prey, so heartbeat sense skips it. */
	public static boolean swarmBat(Entity entity) {
		return entity instanceof Bat && entity.isSilent();
	}

	public static boolean hasBlood(Entity entity) {
		return bloodful(entity) && entity.isAlive();
	}

	/**
	 * An enemy for the Dash and the Harvest: a monster, or a mob hunting a player. Villagers, livestock, pets and
	 * bonded beasts are never caught in them (a bite still drinks from anything with blood).
	 */
	private static boolean prey(Entity e, ServerPlayer player) {
		return e instanceof LivingEntity living && living.isAlive() && e != player && !(e instanceof Player)
				&& !(e instanceof TamableAnimal tame && tame.isTame()) && !e.getTags().contains("mages_legacy_bond")
				&& (e instanceof Enemy || (e instanceof Mob m && m.getTarget() instanceof Player));
	}

	public static boolean isNight(ServerLevel world) {
		return world.dimensionType().hasSkyLight() && world.isNight();
	}

	public static boolean thirsty(RaceData data) {
		return data.resource < THIRST_BELOW;
	}

	/**
	 * Blood drunk: into the resource, and every 10 drops of a monster's blood a point of race experience (a pen of
	 * cows feeds the thirst, it doesn't teach).
	 */
	private static void drink(ServerPlayer player, RaceData data, float blood) {
		drink(player, data, blood, true);
	}

	private static void drink(ServerPlayer player, RaceData data, float blood, boolean teaches) {
		if (blood <= 0) {
			return;
		}
		data.addResource(blood);
		if (!teaches) {
			return;
		}
		float total = DRUNK.getOrDefault(player.getUUID(), 0f) + blood;
		int xp = (int) (total / 10);
		DRUNK.put(player.getUUID(), total - xp * 10);
		if (xp > 0) {
			Progression.addXp(player, data, "bloodkin_drink", xp, 40);
		}
	}

	// ---- once a second ----

	public static void tickSecond(ServerPlayer player, RaceData data) {
		ServerLevel world = player.serverLevel();
		boolean night = isNight(world);
		// Blood drains away by itself; Restraint slows it, Night Blood stops it at night
		if (!(night && data.has(Talents.NIGHT_BLOOD))) {
			data.addResource(data.has(Talents.THIRST_SLOW) ? -0.15f : -0.3f);
		}
		if (night && data.has(Talents.NIGHT_BLOOD)) {
			refresh(player, MobEffects.REGENERATION, 0, 60, 20);
		}
		if (thirsty(data)) {
			refresh(player, MobEffects.MOVEMENT_SPEED, 0, 40, 20);
		}
		if ((night || world.getMaxLocalRawBrightness(BlockPos.containing(player.getEyePosition())) <= 7) && data.has(Talents.NIGHT_EYES)) {
			refresh(player, MobEffects.NIGHT_VISION, 0, 300, 220);
		}
		if (night && (data.has(Talents.NIGHT_HUNTER) || data.has(Talents.NIGHT_PRINCE))) {
			refresh(player, MobEffects.MOVEMENT_SPEED, 0, 40, 20);
		}
		// Quiet Step: at night monsters lose a Bloodkin they hunt from afar
		if (night && data.has(Talents.NIGHT_HUSH)) {
			for (Mob m : world.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(24),
					e -> e.getTarget() == player && e.distanceToSqr(player) > 64)) {
				m.setTarget(null);
			}
		}
		sun(player, data, world);
	}

	/** Whether a player stands in direct sunlight. */
	public static boolean sunlit(Player player) {
		Level world = player.level();
		return world.dimensionType().hasSkyLight() && world.isDay() && !world.isRaining() && !player.isInWaterOrRain()
				&& world.canSeeSky(BlockPos.containing(player.getEyePosition()));
	}

	/**
	 * Direct sunlight dries a Bloodkin's blood twice as fast, helmet or not (Twilight Cloak softens that to half again);
	 * a bare head burns as well (Shade halves it, Twilight Cloak turns it into mere weakness). Its healing stops in
	 * the sun too (see {@link #naturalHealing}).
	 */
	private static void sun(ServerPlayer player, RaceData data, ServerLevel world) {
		if (!sunlit(player)) {
			return;
		}
		data.addResource(data.has(Talents.NIGHT_CLOAK) ? -0.15f : -0.3f);
		if (!player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
			return;
		}
		if (data.has(Talents.NIGHT_CLOAK)) {
			refresh(player, MobEffects.WEAKNESS, 0, 60, 30);
			return;
		}
		if (data.has(Talents.NIGHT_SHADE) && world.getGameTime() / 20 % 2 == 0) {
			return;
		}
		player.setSecondsOnFire(3);
	}

	private static void refresh(ServerPlayer player, MobEffect effect, int amplifier, int duration, int below) {
		MobEffectInstance current = player.getEffect(effect);
		if (current == null || current.getAmplifier() < amplifier || current.getDuration() <= below) {
			player.addEffect(new MobEffectInstance(effect, duration, amplifier, true, false, true));
		}
	}

	// ---- weaknesses ----

	/** Natural healing stops while thirsty (unless On the Edge), and in direct sunlight (halved with Twilight Cloak). */
	public static float naturalHealing(Player player) {
		if (!is(player)) {
			return 1;
		}
		RaceData data = RaceState.of((ServerPlayer) player);
		if (thirsty(data) && !data.has(Talents.THIRST_EDGE)) {
			return 0;
		}
		if (sunlit(player)) {
			return data.has(Talents.NIGHT_CLOAK) ? 0.5f : 0;
		}
		return 1;
	}

	/** Ordinary food feeds a Bloodkin only a third. */
	public static void afterEating(ServerPlayer player, FoodProperties food) {
		int lost = food.getNutrition() * 2 / 3;
		player.getFoodData().setFoodLevel(Math.max(0, player.getFoodData().getFoodLevel() - lost));
		player.getFoodData().setSaturation(Math.min(player.getFoodData().getSaturationLevel(),
				player.getFoodData().getFoodLevel()) / 3);
	}

	// ---- blows ----

	/**
	 * A Bloodkin's own blow on a living creature: Thirst, the night, Execution, the Bite, a Harvest mark and an Ambush
	 * from behind make it harder; then it heals (vampirism) and gives blood; with Torn Wound it bleeds.
	 */
	public static float onHit(LivingEntity victim, DamageSource source, float amount) {
		if (!(source.getEntity() instanceof ServerPlayer player) || victim == player || !is(player) || amount <= 0) {
			return amount;
		}
		RaceData data = RaceState.of(player);
		ServerLevel world = player.serverLevel();
		long now = now(player.getServer());
		// A real blow (a weapon, a fist, the Dash), not the Harvest's pull or a bleeding wound: only those drink
		boolean melee = source.is(DamageTypes.PLAYER_ATTACK) && source.getDirectEntity() == player;
		boolean night = isNight(world);
		float bonus = 0;
		if (thirsty(data)) {
			bonus += data.has(Talents.THIRST_HUNGER) ? 0.35f : 0.2f;
		}
		if (night && data.has(Talents.NIGHT_PRINCE)) {
			bonus += 0.25f;
		} else if (night && data.has(Talents.NIGHT_HUNTER)) {
			bonus += 0.15f;
		}
		if (data.has(Talents.FANG_EXECUTE) && victim.getHealth() < victim.getMaxHealth() * 0.25f) {
			bonus += 0.5f;
		}
		Long mark = MARKED.get(victim.getUUID());
		if (mark != null && mark > now) {
			bonus += 0.25f;
		}
		// Ambush: a blow at a back that doesn't see you
		if (melee && data.has(Talents.NIGHT_AMBUSH)) {
			Vec3 facing = victim.getViewVector(1.0f).multiply(1, 0, 1);
			Vec3 toPlayer = player.position().subtract(victim.position()).multiply(1, 0, 1);
			if (facing.lengthSqr() > 0 && toPlayer.lengthSqr() > 0 && facing.normalize().dot(toPlayer.normalize()) < -0.3) {
				bonus += 0.3f;
			}
		}
		amount *= 1 + Math.min(MAX_BONUS, bonus);
		if (!melee || !hasBlood(victim)) {
			return amount;
		}
		float heal = 0;
		if (data.has(Talents.FANG_BITE) && BLOWS.merge(player.getUUID(), 1, Integer::sum) >= 4) {
			BLOWS.remove(player.getUUID());
			amount += 4;
			heal += 4;
			world.playSound(null, victim.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 0.6f);
			splash(world, victim, 10);
			// The Bite: two fangs' worth of cut, crossed
			Vec3 center = victim.position().add(0, victim.getBbHeight() * 0.6, 0);
			Fx.slash(world, center, player.getViewVector(1.0f), 0.9f + victim.getBbWidth(), 55, 8, BLOOD_INK);
			Fx.slash(world, center, player.getViewVector(1.0f), 0.9f + victim.getBbWidth(), 125, 8, BLOOD_INK);
			Fx.shake(player, 1.5f, 6);
		}
		heal += amount * lifesteal(player, data, now);
		player.heal(heal);
		Long feasting = FEAST.get(player.getUUID());
		if (feasting != null && feasting > now) {
			// In the Feast every blow is drunk: a stream of blood from the wound straight into the Bloodkin
			Fx.tendril(world, victim, player, 12, WINE_INK, BLOOD_INK);
		}
		drink(player, data, Math.min(amount, 8), victim instanceof Enemy);
		if (data.has(Talents.FANG_BLEED)) {
			bleed(world, victim, player, data);
		}
		splash(world, victim, 4);
		return amount;
	}

	/** The share of a blow that heals: 10%, 15% with the Greedy Fang, doubled after a Sacrifice, all of it in the Feast. */
	private static float lifesteal(ServerPlayer player, RaceData data, long now) {
		Long feast = FEAST.get(player.getUUID());
		if (feast != null && feast > now) {
			return 1.0f;
		}
		float rate = data.has(Talents.FANG_LEECH) ? 0.15f : 0.1f;
		Long offering = OFFERING.get(player.getUUID());
		if (offering != null && offering > now) {
			rate *= 2;
		}
		return rate;
	}

	private static void bleed(ServerLevel world, LivingEntity victim, ServerPlayer owner, RaceData data) {
		boolean deep = data.has(Talents.FANG_DEEP);
		BLEEDING.put(victim.getUUID(), new Bleed(world, now(world.getServer()) + (deep ? 120 : 60), deep ? 2 : 1, owner.getUUID()));
		if (deep) {
			victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0));
		}
	}

	/** Drops of blood off a creature. */
	private static void splash(ServerLevel world, Entity e, int count) {
		world.sendParticles(ModParticles.BLOOD.get(), e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), count, e.getBbWidth() * 0.3, e.getBbHeight() * 0.2,
				e.getBbWidth() * 0.3, 0.05);
	}

	/** A living creature died at a Bloodkin's hand: a little hunger stilled, and Frenzy, Rage and experience. */
	public static void onKill(ServerPlayer player, LivingEntity victim) {
		if (!is(player) || !bloodful(victim)) {
			return;
		}
		RaceData data = RaceState.of(player);
		player.getFoodData().eat(2, 0.2f);
		if (data.has(Talents.FANG_FRENZY)) {
			player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 100, 0, true, true, true));
			player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 0, true, true, true));
		}
		// Thirst teaches through the hunt, not the slaughter of a chicken pen
		if (thirsty(data) && victim instanceof Enemy) {
			Progression.addXp(player, data, Progression.HUNT, 4, Progression.HUNT_CAP);
			if (data.has(Talents.THIRST_RAGE)) {
				data.addResource(15);
			}
		}
	}

	/** The Prince of the Night: at night a killing blow scatters the Bloodkin into bats instead, once in 20 minutes. */
	public static boolean allowDeath(ServerPlayer player) {
		RaceData data = RaceState.of(player);
		if (data == null || data.race != Race.BLOODKIN || !data.has(Talents.NIGHT_PRINCE) || !isNight(player.serverLevel())
				|| !ready(player, data, PRINCE_SLOT)) {
			return true;
		}
		player.setHealth(player.getMaxHealth() * 0.3f);
		cooldown(player, data, PRINCE_SLOT, LONG_COOLDOWN);
		startSwarm(player, data);
		player.displayClientMessage(Component.translatable("mages_legacy.bloodkin.prince").withStyle(ChatFormatting.DARK_RED), false);
		return false;
	}

	/** Nothing touches a Bloodkin scattered into bats. */
	public static boolean avoids(LivingEntity victim) {
		for (Swarm s : SWARMS) {
			if (s.player() == victim) {
				return true;
			}
		}
		return false;
	}

	// ---- abilities ----

	public static AbilityResult use(ServerPlayer player, RaceData data, int slot) {
		return switch (slot) {
			case 0 -> dash(player, data);
			case 1 -> harvest(player, data);
			case 2 -> {
				// Looking at a friend, the Bloodkin gives blood instead of shedding it
				LivingEntity friend = lookedAtFriend(player, 8);
				yield friend != null ? transfuse(player, data, friend) : sacrifice(player, data);
			}
			case 3 -> swarm(player, data);
			case 4 -> data.has(Talents.FANG_FEAST) ? feast(player, data) : RaceData.talentLocked(player, Talents.FANG_FEAST);
			default -> AbilityResult.LOCKED;
		};
	}

	public static double dashRange(RaceData data) {
		return data.has(Talents.FANG_REACH) ? 14 : 10;
	}

	/**
	 * Blood Dash: a rush of 10 blocks along your look, stopped only by walls, through everything in the way: each one hit takes 6
	 * and bleeds. With Double Rush a second dash follows free within 3 seconds.
	 */
	public static AbilityResult dash(ServerPlayer player, RaceData data) {
		if (DASHES.stream().anyMatch(d -> d.player() == player)) {
			return AbilityResult.HINTED;
		}
		long now = now(player.getServer());
		Long free = DOUBLE.remove(player.getUUID());
		boolean second = free != null && free >= now;
		if (!second && !ready(player, data, 0)) {
			return AbilityResult.COOLDOWN;
		}
		if (!second && data.resource < DASH_COST) {
			return AbilityResult.NO_RESOURCE;
		}
		List<Vec3> path = dashPath(player, dashRange(data));
		if (path.size() < 2) {
			return AbilityResult.NO_SPACE;
		}
		if (!second) {
			data.addResource(-DASH_COST);
			cooldown(player, data, 0, DASH_COOLDOWN);
		}
		player.stopRiding();
		DASHES.add(new Dash(player, path, new HashSet<>(), second));
		DASH_STEP.put(player.getUUID(), 0);
		ServerLevel world = player.serverLevel();
		Fx.flight(player, DASH_TICKS);
		Fx.ribbon(world, path, 20, WINE_INK, BLOOD_INK, 0.9f);
		// Blood shades of the body left along the way, and the view rushing wide
		Fx.ghosts(world, path, player.getYRot(), 4, 16, BLOOD_INK);
		Fx.fov(player, 0.22f, 12);
		Fx.shake(player, 0.8f, 5);
		world.sendParticles(ModParticles.BLOOD_MIST.get(), player.getX(), player.getY() + 0.9, player.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
		world.playSound(null, player.blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 1.0f, 0.6f);
		world.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.PLAYERS, 0.8f, 1.4f);
		return AbilityResult.OK;
	}

	/** Points along the look, a quarter block apart, as far as the body fits. */
	private static List<Vec3> dashPath(ServerPlayer player, double range) {
		List<Vec3> path = new ArrayList<>();
		Vec3 look = player.getViewVector(1.0f);
		// Looking about level the dash runs along the ground; only a clear look up or down makes it climb or dive
		double rise = Math.abs(look.y) < 0.5 ? 0 : Math.max(-0.5, Math.min(0.5, look.y));
		Vec3 dir = new Vec3(look.x, rise, look.z);
		if (dir.lengthSqr() < 1.0E-4) {
			return path;
		}
		dir = dir.normalize();
		Vec3 start = player.position();
		path.add(start);
		Vec3 at = start;
		ServerLevel world = player.serverLevel();
		for (double d = 0.25; d <= range; d += 0.25) {
			Vec3 next = at.add(dir.scale(0.25));
			if (!world.noCollision(player, player.getBoundingBox().move(next.subtract(player.position())))) {
				// Up a step, as walking would
				Vec3 stepped = next.add(0, 0.6, 0);
				if (!world.noCollision(player, player.getBoundingBox().move(stepped.subtract(player.position())))) {
					break;
				}
				next = stepped;
			}
			at = next;
			path.add(at);
		}
		return path;
	}

	private static void tickDashes(long now) {
		for (Iterator<Dash> it = DASHES.iterator(); it.hasNext(); ) {
			Dash d = it.next();
			ServerPlayer player = d.player();
			if (player.isRemoved() || !player.isAlive()) {
				it.remove();
				continue;
			}
			ServerLevel world = player.serverLevel();
			RaceData data = RaceState.of(player);
			int step = DASH_STEP.merge(player.getUUID(), 1, Integer::sum);
			int last = d.path().size() - 1;
			int from = Math.min(last, Math.round((float) (step - 1) * last / DASH_TICKS));
			int to = Math.min(last, Math.round((float) step * last / DASH_TICKS));
			Vec3 a = d.path().get(from);
			Vec3 b = d.path().get(to);
			// Everything the body sweeps through on this stretch is cut
			AABB swept = player.getBoundingBox().move(a.subtract(player.position())).minmax(player.getBoundingBox().move(b.subtract(player.position())))
					.inflate(0.4);
			for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class, swept, e -> prey(e, player) && !d.hit().contains(e.getUUID()))) {
				d.hit().add(e.getUUID());
				e.hurt(world.damageSources().playerAttack(player), 6);
				Vec3 along = b.subtract(a);
				Fx.slash(world, e.position().add(0, e.getBbHeight() * 0.55, 0), along, 1.1f + e.getBbWidth(), 20 + world.getRandom().nextFloat() * 40, 9, BLOOD_INK);
				Fx.shake(player, 2.2f, 7);
				if (hasBlood(e)) {
					bleed(world, e, player, data);
					splash(world, e, 12);
					// Blood thrown on along the cut
					Vec3 spray = along.lengthSqr() > 1.0E-6 ? along.normalize() : Vec3.ZERO;
					for (int k = 0; k < 10; k++) {
						world.sendParticles(ModParticles.BLOOD.get(), e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 0,
								spray.x * 0.35 + (world.getRandom().nextDouble() - 0.5) * 0.2, 0.15 + world.getRandom().nextDouble() * 0.15,
								spray.z * 0.35 + (world.getRandom().nextDouble() - 0.5) * 0.2, 1);
					}
				}
				Progression.addXp(player, data, "bloodkin_dash", 1, 30);
				if (data.has(Talents.FANG_TRAIL)) {
					player.heal(3);
				}
			}
			for (int i = 0; i <= 4; i++) {
				Vec3 p = a.lerp(b, i / 4.0);
				world.sendParticles(ModParticles.BLOOD.get(), p.x, p.y + 1, p.z, 2, 0.2, 0.4, 0.2, 0.02);
			}
			player.setDeltaMovement(Vec3.ZERO);
			player.fallDistance = 0;
			player.connection.teleport(b.x, b.y, b.z, player.getYRot(), player.getXRot());
			if (to >= last) {
				it.remove();
				DASH_STEP.remove(player.getUUID());
				if (data.has(Talents.FANG_DOUBLE) && !d.free()) {
					DOUBLE.put(player.getUUID(), now + DOUBLE_WINDOW);
				}
				world.sendParticles(ModParticles.BLOOD.get(), b.x, b.y + 0.5, b.z, 16, 0.4, 0.3, 0.4, 0.1);
				world.sendParticles(ModParticles.BLOOD_MIST.get(), b.x, b.y + 0.8, b.z, 10, 0.4, 0.4, 0.4, 0.04);
				Fx.burst(world, b.add(0, 1, 0), 1.1f, 6, BLOOD_INK);
				Fx.ring(world, b, 0.3f, 2.2f, 10, BLOOD_INK);
			}
		}
	}

	public static double harvestRadius(RaceData data) {
		return data.has(Talents.THIRST_HARVEST) ? 12 : 9;
	}

	/**
	 * Harvest: blood is drawn out of every enemy within 9 blocks: each takes 4, and you heal 2 (4 with Greedy Harvest) and drink 5
	 * from each. Mark of the Harvest leaves them taking more from you; the Blood Storm crown pulls twice more.
	 */
	public static AbilityResult harvest(ServerPlayer player, RaceData data) {
		if (!ready(player, data, 1)) {
			return AbilityResult.COOLDOWN;
		}
		if (data.resource < HARVEST_COST) {
			return AbilityResult.NO_RESOURCE;
		}
		data.addResource(-HARVEST_COST);
		cooldown(player, data, 1, HARVEST_COOLDOWN);
		pull(player, data);
		if (data.has(Talents.THIRST_STORM)) {
			long now = now(player.getServer());
			STORM.put(player.getUUID(), new ArrayList<>(List.of(now + 20, now + 40)));
		}
		return AbilityResult.OK;
	}

	/** One pull of the Harvest. */
	private static void pull(ServerPlayer player, RaceData data) {
		ServerLevel world = player.serverLevel();
		double radius = harvestRadius(data);
		long now = now(player.getServer());
		int drawn = 0;
		for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
				e -> prey(e, player) && hasBlood(e) && e.distanceToSqr(player) < radius * radius)) {
			e.hurt(world.damageSources().indirectMagic(player, player), 4);
			player.heal(data.has(Talents.THIRST_DRAIN) ? 4 : 2);
			drink(player, data, 5);
			if (data.has(Talents.THIRST_MARK)) {
				MARKED.put(e.getUUID(), now + MARK_TICKS);
			}
			// A stream of blood from the victim to the Bloodkin: a living tendril, and drops carried along it
			Fx.tendril(world, e, player, 20, WINE_INK, BLOOD_INK);
			// The tendril yanks the victim a couple of blocks towards the Bloodkin
			Vec3 yank = player.position().subtract(e.position());
			if (yank.lengthSqr() > 4) {
				e.setDeltaMovement(yank.normalize().scale(0.55).add(0, 0.2, 0));
				e.hurtMarked = true;
			}
			Fx.pillar(world, e.position(), 0.35f + e.getBbWidth() * 0.4f, e.getBbHeight() + 0.8f, 10, BLOOD_INK);
			splash(world, e, 8);
			Vec3 from = e.position().add(0, e.getBbHeight() * 0.6, 0);
			Vec3 way = player.position().add(0, 1, 0).subtract(from);
			for (int i = 0; i < 10; i++) {
				Vec3 at = from.add(way.scale(i / 10.0 * 0.3));
				Vec3 push = way.scale(0.07 + world.getRandom().nextDouble() * 0.015);
				world.sendParticles(ModParticles.BLOOD_MOTE.get(), at.x, at.y, at.z, 0, push.x, push.y, push.z, 1);
			}
			Progression.addXp(player, data, "bloodkin_harvest", 2, 30);
			drawn++;
		}
		// The circle closes in on the Bloodkin, a seal of blood opening under its feet
		Fx.ring(world, player.position(), (float) radius, 0.5f, 14, BLOOD_INK);
		Fx.mark(world, player.position(), null, (float) radius * 0.5f, 30, POOL_INK, Fx.POOL);
		Fx.pulse(player, 2, 30, HEART_RGB);
		world.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.5f, 0.8f);
		if (drawn > 0) {
			Fx.shake(player, 1.0f, 10);
			world.playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 1.0f, 0.6f);
		}
	}

	/** Blood Sacrifice: your own health for blood at once. It never kills. Offering of Blood doubles vampirism after. */
	public static AbilityResult sacrifice(ServerPlayer player, RaceData data) {
		if (!ready(player, data, 2)) {
			return AbilityResult.COOLDOWN;
		}
		float price = data.has(Talents.THIRST_SACRIFICE) ? 2 : 4;
		if (player.getHealth() <= price + 1) {
			player.displayClientMessage(Component.translatable("mages_legacy.bloodkin.too_weak"), true);
			return AbilityResult.HINTED;
		}
		boolean vessel = data.has(Talents.THIRST_VESSEL);
		player.setHealth(player.getHealth() - price);
		data.addResource(vessel ? 50 : 40);
		cooldown(player, data, 2, vessel ? SACRIFICE_COOLDOWN / 2 : SACRIFICE_COOLDOWN);
		if (data.has(Talents.THIRST_OFFERING)) {
			OFFERING.put(player.getUUID(), now(player.getServer()) + OFFERING_TICKS);
		}
		ServerLevel world = player.serverLevel();
		splash(world, player, 20);
		// Blood wrung out of the body winds up around it and is drunk back in
		Fx.helix(world, player, 1.1f, 2.4f, 3, true, 22, BLOOD_INK);
		Fx.mark(world, player.position(), null, 1.5f, 26, POOL_INK, Fx.POOL);
		Fx.screen(player, 14, BLOOD_RGB);
		Fx.pulse(player, 1, 18, HEART_RGB);
		Fx.shake(player, 1.6f, 8);
		Fx.ring(world, player.position(), 0.3f, 2.5f, 12, WINE_INK);
		world.playSound(null, player.blockPosition(), SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, SoundSource.PLAYERS, 1.0f, 0.6f);
		world.playSound(null, player.blockPosition(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.8f, 0.5f);
		return AbilityResult.OK;
	}

	/** A friend in sight within {@code range}: another player, or a tamed or bonded creature. */
	private static LivingEntity lookedAtFriend(ServerPlayer player, double range) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0f);
		net.minecraft.world.phys.BlockHitResult wall = player.level().clip(new net.minecraft.world.level.ClipContext(eye, eye.add(look.scale(range)),
				net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
		double reach = wall.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? range : wall.getLocation().distanceTo(eye);
		Vec3 end = eye.add(look.scale(reach));
		AABB box = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1);
		net.minecraft.world.phys.EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player, eye, end, box,
				e -> e instanceof LivingEntity l && l.isAlive() && e != player && !(e instanceof Enemy) && (e instanceof Player p && !p.isSpectator()
						|| e instanceof TamableAnimal t && t.isTame() || euf.mageslegacy.shepherd.Creatures.ownerOf(e) != null), reach * reach);
		return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
	}

	/**
	 * Blood Transfusion (Z, looking at a friend): the Bloodkin's blood into a friend's veins: 25 blood and 2 of its
	 * own health for 6 health, Regeneration and, for a player, Strength for 8 seconds. Once in 10 seconds.
	 */
	public static AbilityResult transfuse(ServerPlayer player, RaceData data, LivingEntity friend) {
		if (!ready(player, data, TRANSFUSE_SLOT)) {
			return AbilityResult.COOLDOWN;
		}
		if (data.resource < TRANSFUSE_COST) {
			return AbilityResult.NO_RESOURCE;
		}
		if (player.getHealth() <= 3) {
			player.displayClientMessage(Component.translatable("mages_legacy.bloodkin.too_weak"), true);
			return AbilityResult.HINTED;
		}
		data.addResource(-TRANSFUSE_COST);
		cooldown(player, data, TRANSFUSE_SLOT, TRANSFUSE_COOLDOWN);
		player.setHealth(player.getHealth() - 2);
		friend.heal(6);
		friend.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0, true, true, true));
		if (friend instanceof Player) {
			friend.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 160, 0, true, true, true));
		}
		ServerLevel world = player.serverLevel();
		// A stream of blood from vein to vein, and it winds into the friend
		Fx.tendril(world, player, friend, 18, WINE_INK, BLOOD_INK);
		Fx.helix(world, friend, friend.getBbWidth() + 0.6f, friend.getBbHeight() + 0.4f, 3, true, 20, BLOOD_INK);
		Fx.burst(world, friend.position().add(0, friend.getBbHeight() / 2, 0), 1.2f + friend.getBbWidth(), 7, BLOOD_INK);
		Fx.pulse(player, 1, 18, HEART_RGB);
		world.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART, friend.getX(), friend.getY() + friend.getBbHeight() + 0.2, friend.getZ(), 4, 0.3, 0.2,
				0.3, 0);
		world.playSound(null, friend.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 1.0f, 1.2f);
		world.playSound(null, friend.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0f, 1.2f);
		Progression.addXp(player, data, "bloodkin_transfuse", 3, 15);
		return AbilityResult.OK;
	}

	/** The Feast crown (H): for 8 seconds every blow heals in full. Once in 5 minutes. */
	public static AbilityResult feast(ServerPlayer player, RaceData data) {
		if (!ready(player, data, FEAST_SLOT)) {
			return AbilityResult.COOLDOWN;
		}
		if (data.resource < FEAST_COST) {
			return AbilityResult.NO_RESOURCE;
		}
		data.addResource(-FEAST_COST);
		cooldown(player, data, FEAST_SLOT, FEAST_COOLDOWN);
		FEAST.put(player.getUUID(), now(player.getServer()) + FEAST_TICKS);
		ServerLevel world = player.serverLevel();
		// A crown of blood: it erupts around the Bloodkin, a seal burns under it and embers rise off it all the Feast long
		Fx.screen(player, 24, BLOOD_RGB);
		Fx.pulse(player, FEAST_TICKS / 20, FEAST_TICKS, HEART_RGB);
		Fx.aura(world, player, FEAST_TICKS, BLOOD_INK, Fx.EMBERS);
		Fx.mark(world, player.position(), null, 2.4f, FEAST_TICKS, POOL_INK, Fx.POOL);
		Fx.helix(world, player, 1.3f, 3.0f, 4, false, 26, BLOOD_INK);
		Fx.burst(world, player.position().add(0, 1, 0), 3.0f, 8, BLOOD_INK);
		Fx.shake(player, 2.5f, 12);
		Fx.ring(world, player.position(), 0.5f, 6, 16, BLOOD_INK);
		world.playSound(null, player.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.4f, 1.8f);
		return AbilityResult.OK;
	}

	/**
	 * Bat Swarm (Alt): the Bloodkin scatters into bats for 3 seconds, flying where it looks, untouchable;
	 * with the Biting Swarm whatever it flies through bleeds.
	 */
	public static AbilityResult swarm(ServerPlayer player, RaceData data) {
		if (!ready(player, data, SWARM_SLOT)) {
			return AbilityResult.COOLDOWN;
		}
		if (data.resource < SWARM_COST) {
			return AbilityResult.NO_RESOURCE;
		}
		data.addResource(-SWARM_COST);
		cooldown(player, data, SWARM_SLOT, data.has(Talents.NIGHT_BATS) ? SWARM_COOLDOWN_LONG : SWARM_COOLDOWN);
		startSwarm(player, data);
		return AbilityResult.OK;
	}

	private static void startSwarm(ServerPlayer player, RaceData data) {
		int ticks = data.has(Talents.NIGHT_BATS) ? SWARM_TICKS_LONG : SWARM_TICKS;
		ServerLevel world = player.serverLevel();
		List<Bat> bats = new ArrayList<>();
		for (int i = 0; i < 7; i++) {
			Bat bat = EntityType.BAT.create(world);
			if (bat == null) {
				continue;
			}
			// Burst out onto the ring at once, not out of the Bloodkin's own face
			double a = Math.PI * 2 * i / 7;
			bat.moveTo(player.getX() + Math.cos(a) * 1.6, player.getY() + 0.4 + world.getRandom().nextDouble(),
					player.getZ() + Math.sin(a) * 1.6, world.getRandom().nextFloat() * 360, 0);
			bat.setInvulnerable(true);
			bat.addTag("mages_legacy_swarm");
			// Silence is synced, so clients can tell the swarm from wild bats (see swarmBat)
			bat.setSilent(true);
			world.addFreshEntity(bat);
			bats.add(bat);
		}
		SWARMS.add(new Swarm(player, now(player.getServer()) + ticks, bats));
		player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks + 4, 0, true, false, false));
		// The body bursts into a cloud of bats: a flash of dark blood, bats flung out, and a flock that circles it
		Fx.swarm(world, player, 26, ticks);
		Fx.burst(world, player.position().add(0, 1, 0), 2.4f, 8, WINE_INK);
		Fx.ring(world, player.position(), 0.4f, 3.5f, 12, BLOOD_INK);
		Fx.fov(player, 0.16f, ticks);
		Fx.shake(player, 1.6f, 8);
		world.sendParticles(ModParticles.BLOOD_MIST.get(), player.getX(), player.getY() + 1, player.getZ(), 24, 0.5, 0.7, 0.5, 0.06);
		world.sendParticles(ModParticles.BAT.get(), player.getX(), player.getY() + 1.1, player.getZ(), 16, 0.3, 0.4, 0.3, 0.35);
		world.playSound(null, player.blockPosition(), SoundEvents.BAT_TAKEOFF, SoundSource.PLAYERS, 1.2f, 0.8f);
	}

	private static void tickSwarms(long now) {
		for (Iterator<Swarm> it = SWARMS.iterator(); it.hasNext(); ) {
			Swarm s = it.next();
			ServerPlayer player = s.player();
			if (player.isRemoved() || !player.isAlive() || s.until() < now) {
				s.bats().forEach(Entity::discard);
				if (!player.isRemoved()) {
					player.removeEffect(MobEffects.INVISIBILITY);
					player.fallDistance = 0;
					// The flock gathers back into a body
					ServerLevel world = player.serverLevel();
					world.sendParticles(ModParticles.BLOOD_MIST.get(), player.getX(), player.getY() + 1, player.getZ(), 16, 0.4, 0.6, 0.4, 0.02);
					Fx.helix(world, player, 1.6f, 2.2f, 3, true, 10, WINE_INK);
					Fx.burst(world, player.position().add(0, 1, 0), 1.3f, 6, BLOOD_INK);
					Fx.shake(player, 1.0f, 6);
					world.playSound(null, player.blockPosition(), SoundEvents.BAT_TAKEOFF, SoundSource.PLAYERS, 1.0f, 1.4f);
				}
				it.remove();
				continue;
			}
			// The swarm flies where the Bloodkin looks; the bats flutter around it
			Vec3 look = player.getViewVector(1.0f);
			player.setDeltaMovement(look.scale(0.7));
			player.hurtMarked = true;
			player.fallDistance = 0;
			ServerLevel world = player.serverLevel();
			for (Bat bat : s.bats()) {
				// A ring around the body, never in the Bloodkin's own face
				double a = world.getRandom().nextDouble() * Math.PI * 2;
				double r = 1.4 + world.getRandom().nextDouble();
				Vec3 spot = player.position().add(Math.cos(a) * r, 0.2 + world.getRandom().nextDouble() * 1.4, Math.sin(a) * r);
				bat.setDeltaMovement(spot.subtract(bat.position()).scale(0.4));
				bat.hurtMarked = true;
			}
			RaceData data = RaceState.of(player);
			if (data.has(Talents.NIGHT_SWARM)) {
				for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(1.5),
						e -> prey(e, player) && hasBlood(e) && !BLEEDING.containsKey(e.getUUID()))) {
					bleed(world, e, player, data);
					splash(world, e, 6);
				}
			}
		}
	}

	// ---- every tick ----

	public static void tick(MinecraftServer server) {
		long now = now(server);
		tickDashes(now);
		tickSwarms(now);
		STORM.entrySet().removeIf(e -> {
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			if (player == null) {
				return true;
			}
			List<Long> pulses = e.getValue();
			if (!pulses.isEmpty() && pulses.get(0) <= now) {
				pulses.remove(0);
				pull(player, RaceState.of(player));
			}
			return pulses.isEmpty();
		});
		if (now % 20 == 0) {
			MARKED.values().removeIf(until -> until < now);
			BLEEDING.entrySet().removeIf(e -> {
				Bleed b = e.getValue();
				Entity entity = b.world().getEntity(e.getKey());
				if (!(entity instanceof LivingEntity living) || !living.isAlive() || b.until() < now) {
					return true;
				}
				ServerPlayer owner = server.getPlayerList().getPlayer(b.owner());
				living.hurt(owner != null ? b.world().damageSources().indirectMagic(owner, owner) : b.world().damageSources().magic(), b.perSecond());
				splash(b.world(), living, 3);
				// Open Vein: the bleeding feeds its Bloodkin
				if (owner != null && RaceState.of(owner).has(Talents.FANG_OPEN)) {
					drink(owner, RaceState.of(owner), b.perSecond(), living instanceof Enemy);
				}
				return false;
			});
		}
	}

	/** The race changed: its bats fly off, its marks and moments end. */
	public static void cleanup(ServerPlayer player) {
		UUID id = player.getUUID();
		SWARMS.removeIf(s -> {
			if (s.player() == player) {
				s.bats().forEach(Entity::discard);
				return true;
			}
			return false;
		});
		FEAST.remove(id);
		OFFERING.remove(id);
		DOUBLE.remove(id);
	}
}
