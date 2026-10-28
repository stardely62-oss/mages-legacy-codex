package euf.mageslegacy.tempter;

import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.race.AbilityResult;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceData;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talents;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Tempters: the nameless mage's longing to be loved, a hunter that lives by the kiss. The Kiss (R) rushes in,
 * drinks a creature's life and leaves it infatuated: it won't raise a hand against her and takes her blows harder. The
 * Wings (V) carry her through the air, the Charm (Z) makes a monster her thrall for a while, the Lullaby (H) puts the
 * monsters around to sleep (a sleeper's kiss is twice as sweet), and the Broken Heart (Alt, crown) breaks every heart
 * she has won. The undead have no heart: no kiss, charm or song takes them.
 */
public final class Tempter {
	/** Rose of the kiss, and dark wine laid on as real colour under the glow (see {@link Fx#INK}). */
	public static final int ROSE_RGB = 0xFF6FA8;
	private static final int WINE_INK = Fx.INK | 0x6A1838;
	private static final int DREAM_RGB = 0xC8A8FF;

	public static final int KISS_COST = 20;
	public static final int KISS_COST_CHEAP = 12;
	public static final int WINGS_COST = 15;
	public static final int WINGS_COST_CHEAP = 8;
	public static final int CHARM_COST = 30;
	public static final int CHARM_COST_CHEAP = 20;
	public static final int LULLABY_COST = 40;
	public static final int BREAK_COST = 50;
	private static final int KISS_COOLDOWN = 120;
	private static final int KISS_COOLDOWN_QUICK = 80;
	private static final int WINGS_COOLDOWN = 160;
	private static final int CHARM_COOLDOWN = 400;
	private static final int LULLABY_COOLDOWN = 600;
	private static final int BREAK_COOLDOWN = 6000;
	private static final int UNDYING_SLOT = 9;
	private static final int UNDYING_COOLDOWN = 24000;
	private static final int INFATUATION_TICKS = 120;
	private static final int INFATUATION_TICKS_LONG = 200;
	private static final int THRALL_TICKS = 200;
	private static final int THRALL_TICKS_LONG = 300;
	private static final int SLEEP_TICKS = 80;
	private static final int SLEEP_TICKS_LONG = 120;
	/** After the Wings, how long the second flight, the dive and the free kiss wait. */
	private static final int AFTER_WINGS = 60;
	/** A minute without a living soul near brings the longing. */
	private static final int LONELY_AFTER = 1200;
	private static final net.minecraft.resources.ResourceLocation FRAIL_HEALTH = euf.mageslegacy.MagesLegacy.id("attribute/tempter/frail_health");
	private static final net.minecraft.resources.ResourceLocation NIGHT_SPEED = euf.mageslegacy.MagesLegacy.id("attribute/tempter/night_speed");

	/** A creature won over by a Tempter (infatuated, a thrall or asleep): for whom, until when. */
	private record Spell(UUID tempter, long until) {}

	/** Infatuated: won't strike its Tempter and takes her blows a quarter harder. */
	private static final Map<UUID, Spell> INFATUATED = new HashMap<>();
	/** Thralls: fight the other monsters for their Tempter and never touch a player. */
	private static final Map<UUID, Spell> THRALLS = new HashMap<>();
	/** Asleep: no target, no step, until a blow (other than a kiss) wakes them. */
	private static final Map<UUID, Spell> ASLEEP = new HashMap<>();
	/** When each Tempter last spread her Wings (for the second flight, the dive and the free kiss). */
	private static final Map<UUID, Long> WINGS = new HashMap<>();
	private static final Map<UUID, Boolean> SECOND_FLIGHT = new HashMap<>();
	private static final Map<UUID, Long> LAST_COMPANY = new HashMap<>();

	private Tempter() {
	}

	public static void reset() {
		INFATUATED.clear();
		THRALLS.clear();
		ASLEEP.clear();
		WINGS.clear();
		SECOND_FLIGHT.clear();
		LAST_COMPANY.clear();
	}

	public static boolean is(Player player) {
		return player instanceof ServerPlayer sp && RaceState.raceOf(sp).orElse(null) == Race.TEMPTER;
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

	/** Whether a creature has a heart to win: no undead, no golems, no players. */
	public static boolean hearted(LivingEntity e) {
		return !e.isInvertedHealAndHarm() && !(e instanceof IronGolem) && !(e instanceof Player);
	}

	private static boolean held(Map<UUID, Spell> spells, Entity mob, long now) {
		Spell spell = spells.get(mob.getUUID());
		return spell != null && spell.until() > now;
	}

	private static boolean heldBy(Map<UUID, Spell> spells, Entity mob, Player tempter, long now) {
		Spell spell = spells.get(mob.getUUID());
		return spell != null && spell.until() > now && spell.tempter().equals(tempter.getUUID());
	}

	public static boolean infatuated(Entity mob, Player tempter) {
		return mob.getServer() != null && heldBy(INFATUATED, mob, tempter, now(mob.getServer()));
	}

	public static boolean asleep(Entity mob) {
		return mob.getServer() != null && held(ASLEEP, mob, now(mob.getServer()));
	}

	public static boolean thrall(Entity mob) {
		return mob.getServer() != null && held(THRALLS, mob, now(mob.getServer()));
	}

	/** Whether a creature leaves this player be: a sleeper and a thrall touch no one, the infatuated not their Tempter. */
	public static boolean blocksTarget(Mob mob, Player target) {
		if (mob.getServer() == null) {
			return false;
		}
		long now = now(mob.getServer());
		return held(ASLEEP, mob, now) || held(THRALLS, mob, now) || heldBy(INFATUATED, mob, target, now);
	}

	private static boolean night(ServerLevel world) {
		return world.isNight() || world.dimensionType().hasFixedTime();
	}

	// ---- once a second ----

	public static void tickSecond(ServerPlayer player, RaceData data) {
		ServerLevel world = player.serverLevel();
		long now = now(player.getServer());
		// Adoration: a slow trickle of her own, more from every heart she holds and every friendly face near
		int won = 0;
		for (Mob m : world.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(16), m -> m.isAlive())) {
			if (heldBy(INFATUATED, m, player, now) || heldBy(THRALLS, m, player, now)) {
				won++;
			}
			if (heldBy(ASLEEP, m, player, now) && data.has(Talents.SLEEP_DREAM)) {
				// Sweet Dreams: the sleepers pay for their dreams
				m.hurt(world.damageSources().indirectMagic(player, player), 1);
				won++;
			}
		}
		int friends = world.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(16),
				e -> e != player && e.isAlive() && (e instanceof Player || e instanceof Villager)).size();
		boolean company = won > 0 || friends > 0 || !world.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(16),
				e -> e != player && e.isAlive() && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand)).isEmpty();
		if (company) {
			LAST_COMPANY.put(player.getUUID(), now);
		}
		long alone = now - LAST_COMPANY.computeIfAbsent(player.getUUID(), id -> now);
		float gain = 0.4f + won * 0.4f + friends * 0.3f;
		if (alone > LONELY_AFTER) {
			gain = -0.5f;
			refresh(player, MobEffects.WEAKNESS, 0);
		}
		data.addResource(gain);

		// Frail: two hearts short of a plain human
		AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
		if (health != null && health.getModifier(FRAIL_HEALTH) == null) {
			health.addTransientModifier(new AttributeModifier(FRAIL_HEALTH, -4, AttributeModifier.Operation.ADD_VALUE));
			player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
		}
		// Night Huntress: quicker under the moon
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		boolean nightSpeed = data.has(Talents.WING_NIGHT) && night(world);
		if (speed != null && nightSpeed != (speed.getModifier(NIGHT_SPEED) != null)) {
			if (nightSpeed) {
				speed.addTransientModifier(new AttributeModifier(NIGHT_SPEED, 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			} else {
				speed.removeModifier(NIGHT_SPEED);
			}
		}
		// Village golems can't stand a Tempter
		for (IronGolem golem : world.getEntitiesOfClass(IronGolem.class, player.getBoundingBox().inflate(16),
				g -> g.isAlive() && !g.isPlayerCreated() && g.getTarget() == null)) {
			golem.setTarget(player);
		}
	}

	private static void refresh(ServerPlayer player, net.minecraft.core.Holder<MobEffect> effect, int amplifier) {
		MobEffectInstance current = player.getEffect(effect);
		if (current == null || current.getDuration() <= 30) {
			player.addEffect(new MobEffectInstance(effect, 60, amplifier, true, false, true));
		}
	}

	// ---- every tick ----

	public static void tick(MinecraftServer server) {
		long now = now(server);
		INFATUATED.values().removeIf(s -> s.until() < now);
		ASLEEP.values().removeIf(s -> s.until() < now);
		// A thrall whose charm ends stays infatuated a while (Lingering Heart)
		THRALLS.entrySet().removeIf(e -> {
			if (e.getValue().until() >= now) {
				return false;
			}
			ServerPlayer tempter = server.getPlayerList().getPlayer(e.getValue().tempter());
			if (tempter != null && RaceState.of(tempter).has(Talents.CHARM_HEART)) {
				INFATUATED.put(e.getKey(), new Spell(tempter.getUUID(), now + 200));
				RaceState.of(tempter).addResource(10);
			}
			return true;
		});
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			// Gliding Wings: held crouching in the air, she glides
			if (player.isShiftKeyDown() && !player.onGround() && player.getDeltaMovement().y < 0 && is(player) && RaceState.of(player).has(Talents.WING_GLIDE)) {
				player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 10, 0, true, false, false));
			}
			Long flew = WINGS.get(player.getUUID());
			// Swooping Dive: the first landing after the Wings shakes the ground
			if (flew != null && now - flew > 4 && now - flew < 80 && player.onGround() && is(player)) {
				WINGS.remove(player.getUUID());
				if (RaceState.of(player).has(Talents.WING_DIVE)) {
					dive(player, RaceState.of(player));
				}
			}
		}
		if (now % 5 == 0) {
			holdSleepers(server, now);
		}
		if (now % 10 == 0) {
			leadThralls(server, now);
		}
	}

	private static void holdSleepers(MinecraftServer server, long now) {
		for (ServerLevel world : server.getAllLevels()) {
			for (Map.Entry<UUID, Spell> e : ASLEEP.entrySet()) {
				if (world.getEntity(e.getKey()) instanceof Mob mob && mob.isAlive()) {
					mob.setTarget(null);
					mob.getNavigation().stop();
					mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 9, true, false));
					if (now % 20 == 0) {
						world.sendParticles(ParticleTypes.NOTE, mob.getX(), mob.getY() + mob.getBbHeight() + 0.3, mob.getZ(), 1, 0.2, 0, 0.2, 0.5);
					}
				}
			}
		}
	}

	/** Thralls turn on the nearest monster that isn't another of hers, and keep near her otherwise. */
	private static void leadThralls(MinecraftServer server, long now) {
		for (ServerLevel world : server.getAllLevels()) {
			for (Map.Entry<UUID, Spell> e : THRALLS.entrySet()) {
				if (!(world.getEntity(e.getKey()) instanceof Mob mob) || !mob.isAlive()) {
					continue;
				}
				ServerPlayer tempter = server.getPlayerList().getPlayer(e.getValue().tempter());
				if (tempter == null || tempter.level() != world) {
					continue;
				}
				LivingEntity target = mob.getTarget();
				if (target == null || !target.isAlive() || target instanceof Player || held(THRALLS, target, now)) {
					LivingEntity rival = world.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(12),
							o -> o != mob && o.isAlive() && o instanceof Enemy && !held(THRALLS, o, now) && !held(ASLEEP, o, now)).stream()
							.min(Comparator.comparingDouble(o -> o.distanceToSqr(mob))).orElse(null);
					mob.setTarget(rival);
					if (rival == null && mob.distanceToSqr(tempter) > 64) {
						mob.getNavigation().moveTo(tempter, 1.2);
					}
				}
				// Bait: the monsters around the thrall go for it, not for her
				if (RaceState.of(tempter).has(Talents.CHARM_LURE)) {
					for (Mob other : world.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(8),
							o -> o != mob && o.isAlive() && o instanceof Enemy && o.getTarget() == tempter && !held(THRALLS, o, now))) {
						other.setTarget(mob);
					}
				}
			}
		}
	}

	// ---- abilities ----

	public static AbilityResult use(ServerPlayer player, RaceData data, int slot) {
		return switch (slot) {
			case 0 -> kiss(player, data);
			case 1 -> wings(player, data);
			case 2 -> charm(player, data);
			case 3 -> lullaby(player, data);
			case 4 -> data.has(Talents.THREAD_BREAK) ? breakHeart(player, data) : RaceData.talentLocked(player, Talents.THREAD_BREAK);
			default -> AbilityResult.LOCKED;
		};
	}

	public static int kissCost(RaceData data) {
		return data.has(Talents.KISS_CHEAP) ? KISS_COST_CHEAP : KISS_COST;
	}

	public static int wingsCost(RaceData data) {
		return data.has(Talents.WING_CHEAP) ? WINGS_COST_CHEAP : WINGS_COST;
	}

	public static int charmCost(RaceData data) {
		return data.has(Talents.CHARM_CHEAP) ? CHARM_COST_CHEAP : CHARM_COST;
	}

	public static double kissReach(RaceData data) {
		return data.has(Talents.KISS_REACH) ? 12 : 8;
	}

	private static boolean justFlew(ServerPlayer player) {
		Long flew = WINGS.get(player.getUUID());
		return flew != null && now(player.getServer()) - flew <= AFTER_WINGS;
	}

	/**
	 * The Kiss (R): a rush to the creature you look at and a kiss that drinks 6 of its life into yours (twice as much
	 * from a sleeper, half again by night with the Succubus crown); it stays infatuated for 6 seconds. No heart, no kiss.
	 * With Kiss of Life, R at a player heals them instead.
	 */
	public static AbilityResult kiss(ServerPlayer player, RaceData data) {
		if (!ready(player, data, 0)) {
			return AbilityResult.COOLDOWN;
		}
		boolean free = data.has(Talents.WING_KISS) && justFlew(player);
		int cost = free ? 0 : kissCost(data);
		if (data.resource < cost) {
			return AbilityResult.NO_RESOURCE;
		}
		LivingEntity target = lookedAt(player, kissReach(data), e -> !(e instanceof TamableAnimal tame && tame.isTame())
				&& (data.has(Talents.KISS_ALLY) || !(e instanceof Player)));
		if (target == null) {
			return AbilityResult.NO_SIGHT;
		}
		ServerLevel world = player.serverLevel();
		boolean moon = data.has(Talents.WING_SUCCUBUS) && night(world);
		int cd = data.has(Talents.KISS_QUICK) ? KISS_COOLDOWN_QUICK : KISS_COOLDOWN;
		if (target instanceof Player friend) {
			data.addResource(-cost);
			cooldown(player, data, 0, moon ? cd * 3 / 4 : cd);
			friend.heal(6);
			friend.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0, true, true, true));
			Fx.hearts(world, player, friend, 6, 24, ROSE_RGB);
			world.sendParticles(ParticleTypes.HEART, friend.getX(), friend.getY() + friend.getBbHeight() + 0.2, friend.getZ(), 4, 0.3, 0.2, 0.3, 0);
			world.playSound(null, player.blockPosition(), SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.PLAYERS, 1.0f, 1.2f);
			return AbilityResult.OK;
		}
		if (!hearted(target)) {
			player.displayClientMessage(Component.translatable("mages_legacy.tempter.no_heart"), true);
			return AbilityResult.HINTED;
		}
		data.addResource(-cost);
		cooldown(player, data, 0, moon ? cd * 3 / 4 : cd);
		long now = now(player.getServer());
		boolean sleeper = held(ASLEEP, target, now);
		// The rush: up to the creature's side
		Vec3 side = target.position().add(player.position().subtract(target.position()).normalize().scale(target.getBbWidth() / 2 + 0.8));
		Vec3 from = player.position();
		player.teleportTo(side.x, target.getY(), side.z);
		float drain = (data.has(Talents.KISS_DEEP) ? 9 : 6) * (sleeper ? 2 : 1) * (moon ? 1.5f : 1);
		target.hurt(world.damageSources().indirectMagic(player, player), drain);
		player.heal(drain);
		// The kiss doesn't wake a sleeper: it deepens the dream into love
		INFATUATED.put(target.getUUID(), new Spell(player.getUUID(), now + (data.has(Talents.KISS_CHARM) ? INFATUATION_TICKS_LONG : INFATUATION_TICKS)));
		if (target instanceof Mob mob && mob.getTarget() == player) {
			mob.setTarget(null);
		}
		if (data.has(Talents.KISS_POISON)) {
			target.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1));
		}
		if (data.has(Talents.KISS_SHIELD)) {
			player.setAbsorptionAmount(Math.min(8, player.getAbsorptionAmount() + 4));
		}
		if (data.has(Talents.KISS_SHARE)) {
			for (Player friend : world.getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(6), p -> p != player && p.isAlive())) {
				friend.heal(drain / 2);
			}
		}
		if (data.has(Talents.WING_RUSH)) {
			player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 1, true, true, true));
		}
		Progression.addXp(player, data, "tempter_kiss", 1, 30);
		// A rose streak where she went; over the kissed a heart beats once, and its life flows to her as little hearts
		Fx.ribbon(world, List.of(from, player.position()), 12, WINE_INK, ROSE_RGB, 1.0f);
		Fx.hearts(world, target, player, sleeper ? 10 : 6, 24, ROSE_RGB);
		Fx.pulse(player, 2, 20, ROSE_RGB);
		world.sendParticles(ParticleTypes.HEART, target.getX(), target.getY() + target.getBbHeight() + 0.2, target.getZ(), sleeper ? 6 : 3, 0.3, 0.2, 0.3, 0);
		world.playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.8f, 1.5f);
		world.playSound(null, player.blockPosition(), SoundEvents.ALLAY_AMBIENT_WITH_ITEM, SoundSource.PLAYERS, 1.0f, 0.7f);
		return AbilityResult.OK;
	}

	/**
	 * The Wings (V): a leap through the air the way she looks, and a slow fall after. With Second Flight the next one
	 * within 3 seconds is free.
	 */
	public static AbilityResult wings(ServerPlayer player, RaceData data) {
		long now = now(player.getServer());
		boolean second = data.has(Talents.WING_TWICE) && justFlew(player) && SECOND_FLIGHT.getOrDefault(player.getUUID(), false);
		if (!second && !ready(player, data, 1)) {
			return AbilityResult.COOLDOWN;
		}
		int cost = second ? 0 : wingsCost(data);
		if (data.resource < cost) {
			return AbilityResult.NO_RESOURCE;
		}
		ServerLevel world = player.serverLevel();
		data.addResource(-cost);
		if (!second) {
			int cd = data.has(Talents.WING_NIGHT) && night(world) ? WINGS_COOLDOWN / 2 : WINGS_COOLDOWN;
			cooldown(player, data, 1, cd);
		}
		SECOND_FLIGHT.put(player.getUUID(), !second);
		WINGS.put(player.getUUID(), now);
		Vec3 look = player.getViewVector(1.0f);
		double power = data.has(Talents.WING_FAR) ? 1.6 : 1.2;
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
		player.setDeltaMovement(flat.x * power, 0.55 + Math.max(0, look.y) * 0.5, flat.z * power);
		player.hurtMarked = true;
		player.fallDistance = 0;
		player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 50, 0, true, false, true));
		// Dark wings unfold from her back and beat once; the air rings where she sprang
		Fx.wings(world, player, 1.5f, 36, ROSE_RGB);
		Fx.ring(world, player.position(), 0.4f, 3.0f, 10, ROSE_RGB);
		Fx.fov(player, 0.15f, 10);
		world.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.6f, 1.6f);
		return AbilityResult.OK;
	}

	/** Swooping Dive: landing after the Wings knocks the creatures around back for 5, and with Swoon infatuates them. */
	private static void dive(ServerPlayer player, RaceData data) {
		ServerLevel world = player.serverLevel();
		long now = now(player.getServer());
		for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(3),
				e -> e != player && e.isAlive() && !(e instanceof Player) && !held(THRALLS, e, now) && !(e instanceof TamableAnimal t && t.isTame()))) {
			e.hurt(world.damageSources().indirectMagic(player, player), 5);
			Vec3 push = e.position().subtract(player.position()).normalize();
			e.push(push.x * 0.8, 0.3, push.z * 0.8);
			e.hurtMarked = true;
			if (data.has(Talents.WING_SWOON) && hearted(e)) {
				INFATUATED.put(e.getUUID(), new Spell(player.getUUID(), now + INFATUATION_TICKS));
				if (e instanceof Mob mob && mob.getTarget() == player) {
					mob.setTarget(null);
				}
			}
		}
		Fx.ring(world, player.position(), 0.5f, 4, 12, ROSE_RGB);
		Fx.shake(player, 1.2f, 8);
		world.playSound(null, player.blockPosition(), SoundEvents.GENERIC_SMALL_FALL, SoundSource.PLAYERS, 1.2f, 0.6f);
	}

	/** The Charm (Z): the creature you look at (16 blocks) becomes a thrall for 10 seconds and fights the other monsters. */
	public static AbilityResult charm(ServerPlayer player, RaceData data) {
		if (!ready(player, data, 2)) {
			return AbilityResult.COOLDOWN;
		}
		int cost = charmCost(data);
		if (data.resource < cost) {
			return AbilityResult.NO_RESOURCE;
		}
		LivingEntity target = lookedAt(player, 16, e -> e instanceof Mob && !(e instanceof TamableAnimal tame && tame.isTame()));
		if (!(target instanceof Mob mob)) {
			return AbilityResult.NO_SIGHT;
		}
		if (!hearted(mob)) {
			player.displayClientMessage(Component.translatable("mages_legacy.tempter.no_heart"), true);
			return AbilityResult.HINTED;
		}
		data.addResource(-cost);
		cooldown(player, data, 2, CHARM_COOLDOWN);
		long now = now(player.getServer());
		// One thrall at a time (two with Two Hearts): a new one lets the oldest go
		int allowed = data.has(Talents.CHARM_MORE) ? 2 : 1;
		List<UUID> mine = new ArrayList<>(THRALLS.entrySet().stream().filter(e -> e.getValue().tempter().equals(player.getUUID()))
				.sorted(Comparator.comparingLong(e -> e.getValue().until())).map(Map.Entry::getKey).toList());
		while (mine.size() >= allowed) {
			THRALLS.remove(mine.remove(0));
		}
		int ticks = data.has(Talents.CHARM_LONG) ? THRALL_TICKS_LONG : THRALL_TICKS;
		THRALLS.put(mob.getUUID(), new Spell(player.getUUID(), now + ticks));
		ASLEEP.remove(mob.getUUID());
		mob.setTarget(null);
		if (data.has(Talents.CHARM_STRONG)) {
			mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, 0));
			mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 0));
		}
		ServerLevel world = player.serverLevel();
		// Her eyes on it, and a heart that turns over its head for as long as it is hers
		Fx.hearts(world, player, mob, 4, 16, ROSE_RGB);
		Fx.sigil(world, mob, Fx.SIGIL_HEART, ticks, ROSE_RGB);
		Fx.aura(world, mob, ticks, ROSE_RGB, Fx.MOTES);
		world.sendParticles(ParticleTypes.HEART, mob.getX(), mob.getY() + mob.getBbHeight() + 0.2, mob.getZ(), 5, 0.3, 0.2, 0.3, 0);
		world.playSound(null, mob.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2f, 0.8f);
		world.playSound(null, player.blockPosition(), SoundEvents.EVOKER_PREPARE_WOLOLO, SoundSource.PLAYERS, 0.7f, 1.6f);
		return AbilityResult.OK;
	}

	/** The Lullaby (H): every monster with a heart within 8 blocks falls asleep for 4 seconds; any blow but a kiss wakes it. */
	public static AbilityResult lullaby(ServerPlayer player, RaceData data) {
		if (!ready(player, data, 3)) {
			return AbilityResult.COOLDOWN;
		}
		if (data.resource < LULLABY_COST) {
			return AbilityResult.NO_RESOURCE;
		}
		ServerLevel world = player.serverLevel();
		long now = now(player.getServer());
		double radius = data.has(Talents.SLEEP_WIDE) ? 12 : 8;
		List<Mob> sleepers = world.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(radius),
				m -> m.isAlive() && m instanceof Enemy && hearted(m) && !held(THRALLS, m, now) && m.distanceToSqr(player) <= radius * radius);
		if (sleepers.isEmpty()) {
			return AbilityResult.NO_ENEMIES;
		}
		data.addResource(-LULLABY_COST);
		cooldown(player, data, 3, LULLABY_COOLDOWN);
		long until = now + (data.has(Talents.SLEEP_LONG) ? SLEEP_TICKS_LONG : SLEEP_TICKS);
		for (Mob m : sleepers) {
			ASLEEP.put(m.getUUID(), new Spell(player.getUUID(), until));
			m.setTarget(null);
			m.getNavigation().stop();
			Fx.sigil(world, m, Fx.SIGIL_MOON, (int) (until - now), DREAM_RGB);
		}
		Fx.lullaby(world, player.position(), (float) radius, 50, DREAM_RGB);
		world.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1.2f, 0.7f);
		world.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 0.6f);
		return AbilityResult.OK;
	}

	/**
	 * The Broken Heart (Alt, the Charm's crown): every heart she has won within 16 blocks (infatuated, a thrall or asleep
	 * under her song) breaks: 8 damage and a quarter of its life, 30 at most, and it staggers.
	 */
	public static AbilityResult breakHeart(ServerPlayer player, RaceData data) {
		if (!ready(player, data, 4)) {
			return AbilityResult.COOLDOWN;
		}
		if (data.resource < BREAK_COST) {
			return AbilityResult.NO_RESOURCE;
		}
		ServerLevel world = player.serverLevel();
		long now = now(player.getServer());
		List<Mob> won = world.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(16),
				m -> m.isAlive() && (heldBy(INFATUATED, m, player, now) || heldBy(THRALLS, m, player, now) || heldBy(ASLEEP, m, player, now)));
		if (won.isEmpty()) {
			player.displayClientMessage(Component.translatable("mages_legacy.tempter.no_hearts"), true);
			return AbilityResult.HINTED;
		}
		data.addResource(-BREAK_COST);
		cooldown(player, data, 4, BREAK_COOLDOWN);
		for (Mob m : won) {
			INFATUATED.remove(m.getUUID());
			THRALLS.remove(m.getUUID());
			ASLEEP.remove(m.getUUID());
			m.hurt(world.damageSources().indirectMagic(player, player), Math.min(30, 8 + m.getMaxHealth() * 0.25f));
			m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3));
			Fx.heartbreak(world, m, 30, ROSE_RGB);
		}
		Fx.ring(world, player.position(), 0.5f, 16, 18, ROSE_RGB);
		Fx.burst(world, player.position().add(0, 1, 0), 3.0f, 10, WINE_INK);
		Fx.screen(player, 18, ROSE_RGB);
		Fx.shake(player, 2.0f, 12);
		world.playSound(null, player.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2f, 0.5f);
		world.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.5f, 0.6f);
		return AbilityResult.OK;
	}

	private static LivingEntity lookedAt(ServerPlayer player, double range, java.util.function.Predicate<LivingEntity> filter) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0f);
		Vec3 end = eye.add(look.scale(range));
		AABB box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
				e -> e instanceof LivingEntity living && living.isAlive() && e != player && filter.test(living), range * range);
		return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
	}

	// ---- damage ----

	/** A fall on a Tempter: Soft Wings halve it. */
	public static float modifyDamage(ServerPlayer player, DamageSource source, float amount) {
		RaceData data = RaceState.of(player);
		if (data != null && data.has(Talents.WING_SOFT) && source.is(DamageTypeTags.IS_FALL)) {
			return amount * 0.5f;
		}
		return amount;
	}

	/**
	 * Any creature's blow, for the Tempters' sake: a blow wakes a sleeper (her kiss doesn't); her own blows strike the
	 * infatuated a quarter harder (and heal her a little with the Lover's Bite).
	 */
	public static float onHit(LivingEntity victim, DamageSource source, float amount) {
		if (amount <= 0 || victim.getServer() == null) {
			return amount;
		}
		Entity attacker = source.getEntity();
		boolean kiss = attacker instanceof ServerPlayer p && is(p) && source.is(DamageTypes.INDIRECT_MAGIC);
		if (!kiss) {
			ASLEEP.remove(victim.getUUID());
		}
		if (attacker instanceof ServerPlayer player && is(player) && infatuated(victim, player)) {
			amount *= 1.25f;
			RaceData data = RaceState.of(player);
			if (source.is(DamageTypes.PLAYER_ATTACK) && source.getDirectEntity() == player) {
				data.addResource(3);
				if (data.has(Talents.KISS_LIFE)) {
					player.heal(amount * 0.15f);
				}
			}
		}
		return amount;
	}

	/** A monster died: by a thrall's hand (Grateful Thrall pays), or won by her (the hunt pays). */
	public static void onDeath(LivingEntity victim, DamageSource source) {
		if (!(victim instanceof Enemy) || victim.getServer() == null) {
			return;
		}
		long now = now(victim.getServer());
		Spell spell = THRALLS.get(victim.getUUID());
		if (spell == null) {
			spell = INFATUATED.get(victim.getUUID());
		}
		INFATUATED.remove(victim.getUUID());
		THRALLS.remove(victim.getUUID());
		ASLEEP.remove(victim.getUUID());
		if (spell != null && spell.until() > now) {
			ServerPlayer tempter = victim.getServer().getPlayerList().getPlayer(spell.tempter());
			if (tempter != null) {
				Progression.addXp(tempter, RaceState.of(tempter), Progression.HUNT, 2, Progression.HUNT_CAP);
				if (RaceState.of(tempter).has(Talents.WING_SUCCUBUS)) {
					// The Succubus: every heart won and taken lets her fly again at once
					RaceState.of(tempter).readyAt.put(1, 0L);
					RaceState.of(tempter).dirty = true;
				}
			}
		}
		Spell master = source.getEntity() == null ? null : THRALLS.get(source.getEntity().getUUID());
		ServerPlayer tempter = master == null ? null : victim.getServer().getPlayerList().getPlayer(master.tempter());
		if (tempter != null) {
			RaceData data = RaceState.of(tempter);
			Progression.addXp(tempter, data, Progression.HUNT, 2, Progression.HUNT_CAP);
			if (data.has(Talents.CHARM_RETURN)) {
				data.addResource(5);
				tempter.heal(2);
			}
		}
	}

	/** The Undying (the Kiss's crown): a killing blow takes the life of the nearest creature with a heart instead, once in 20 minutes. */
	public static boolean allowDeath(ServerPlayer player) {
		RaceData data = RaceState.of(player);
		if (data == null || data.race != Race.TEMPTER || !data.has(Talents.KISS_UNDYING) || !ready(player, data, UNDYING_SLOT)) {
			return true;
		}
		ServerLevel world = player.serverLevel();
		LivingEntity instead = world.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(8), m -> m.isAlive() && hearted(m)).stream()
				.min(Comparator.comparingDouble(m -> m.distanceToSqr(player))).orElse(null);
		if (instead == null) {
			return true;
		}
		instead.hurt(world.damageSources().indirectMagic(player, player), Float.MAX_VALUE);
		player.setHealth(player.getMaxHealth() * 0.3f);
		data.readyAt.put(UNDYING_SLOT, now(player.getServer()) + UNDYING_COOLDOWN);
		data.dirty = true;
		Fx.hearts(world, instead, player, 12, 30, ROSE_RGB);
		Fx.heartbreak(world, instead, 30, ROSE_RGB);
		Fx.screen(player, 24, ROSE_RGB);
		world.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6f, 1.5f);
		player.displayClientMessage(Component.translatable("mages_legacy.tempter.undying").withStyle(ChatFormatting.LIGHT_PURPLE), false);
		return false;
	}

	/** Takes the Tempter's spells and changes away (the race was reset or changed). */
	public static void cleanup(ServerPlayer player) {
		UUID id = player.getUUID();
		INFATUATED.values().removeIf(s -> s.tempter().equals(id));
		THRALLS.values().removeIf(s -> s.tempter().equals(id));
		ASLEEP.values().removeIf(s -> s.tempter().equals(id));
		WINGS.remove(id);
		SECOND_FLIGHT.remove(id);
		AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.removeModifier(FRAIL_HEALTH);
		}
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(NIGHT_SPEED);
		}
	}

	/** What the HUD needs: how many hearts she holds now. */
	public static CompoundTag clientInfo(ServerPlayer player) {
		CompoundTag nbt = new CompoundTag();
		long now = now(player.getServer());
		int hearts = 0;
		for (Map<UUID, Spell> spells : List.of(INFATUATED, THRALLS)) {
			for (Spell s : spells.values()) {
				if (s.tempter().equals(player.getUUID()) && s.until() > now) {
					hearts++;
				}
			}
		}
		nbt.putInt("hearts", hearts);
		return nbt;
	}
}
