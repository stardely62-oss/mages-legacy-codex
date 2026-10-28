package euf.mageslegacy.mycelid;

import euf.mageslegacy.MagesLegacy;
import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.fx.ModParticles;
import euf.mageslegacy.net.RacePackets;
import euf.mageslegacy.race.AbilityResult;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceData;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Everything the Mycelids do: spores as their resource, night sight and regeneration in the dark, the mushroom diet,
 * the spore jump, the spore cloud and the mycelium network; and their weaknesses (sun, fire, meat).
 */
public final class Mycelid {
	public static final int JUMP_COST = 15;
	public static final int CHAIN_COST = 5;
	public static final int CLOUD_COST = 30;
	public static final int NODE_COST = 40;
	/** The longest flight of a spore jump, in ticks; see {@link #jumpDelay}. */
	public static final int JUMP_DELAY = 17;
	/** Glowing spore green, and dark moss laid on as real colour under it (see {@link Fx#INK}). */
	private static final int SPORE_RGB = 0x9CFF8A;
	private static final int MOSS_INK = Fx.INK | 0x4E7A3A;
	public static final int CLOUD_KILL_XP = 4;
	public static final int DARK_KILL_XP = 3;
	public static final int CARPET_COST = 25;
	private static final int CARPET_COOLDOWN = 600;
	private static final int CARPET_TICKS = 400;
	private static final double CARPET_RADIUS = 6;
	/** What the carpet takes from each monster on it every second, and gives its Mycelid back in spores. */
	private static final float CARPET_BITE = 1.5f;
	private static final float CARPET_SPORES = 2;
	public static final int BLOOM_COST = 50;
	private static final int BLOOM_COOLDOWN = 6000;
	private static final int BLOOM_TICKS = 200;
	private static final double BLOOM_RADIUS = 12;
	/** Experience from eating (raw mushrooms and stews together) and from kills, at most this much in ten minutes. */
	private static final String DIET_XP = "mycelid_diet";
	/** A taste lasts 45 seconds (a minute with Rich Tastes), and the same one comes back 2 minutes after it was eaten. */
	private static final int TASTE_TICKS = 900;
	private static final int TASTE_TICKS_RICH = 1200;
	private static final int AFTERTASTE = 2400;
	private static final int DIET_XP_CAP = 25;
	private static final int JUMP_COOLDOWN = 100;
	private static final int CLOUD_COOLDOWN = 200;
	private static final int CLOUD_COOLDOWN_QUICK = 140;
	public static final int JUMP_COST_CHEAP = 10;
	private static final int REBIRTH_SLOT = 9;
	private static final int FED_NETWORK_SLOT = 8;
	private static final int FED_NETWORK_COOLDOWN = 600;
	private static final int REBIRTH_COOLDOWN = 24000;
	private static final int MIND_RADIUS = 24;
	private static final int NODE_COOLDOWN = 100;
	private static final net.minecraft.resources.ResourceLocation CAP_ARMOR = euf.mageslegacy.MagesLegacy.id("attribute/mycelid/cap_armor");
	private static final net.minecraft.resources.ResourceLocation HEARTY_HEALTH = euf.mageslegacy.MagesLegacy.id("attribute/mycelid/hearty_health");

	/** Mushroom -> the effect it gives as a taste. */
	public static final Map<Item, net.minecraft.core.Holder<MobEffect>> DIET = Map.of(
			Items.RED_MUSHROOM, MobEffects.DAMAGE_BOOST,
			Items.BROWN_MUSHROOM, MobEffects.DAMAGE_RESISTANCE,
			Items.CRIMSON_FUNGUS, MobEffects.FIRE_RESISTANCE,
			Items.WARPED_FUNGUS, MobEffects.MOVEMENT_SPEED);

	private record PendingJump(UUID player, Vec3 target, long at, Vec3 from, int ticks) {}

	/** A spore cloud; one that follows sits on its owner while they are in the same world. */
	private record Cloud(ServerLevel world, Vec3 center, UUID owner, double radius, int potency, long start, long until, boolean follows) {}

	/** Greenish-grey and dark violet spore dust for the cloud. */

	private static final List<PendingJump> JUMPS = new ArrayList<>();
	private static final List<Cloud> CLOUDS = new ArrayList<>();
	/** A Mycelium Carpet: ground that counts as mycelium for the Mycelids, holds monsters fast and feeds on them. */
	private record Carpet(ServerLevel world, Vec3 center, UUID owner, double radius, long until) {}

	/** A monster the Great Bloom infected: until when, and whose spores burst out of it if it dies. */
	private record Infection(UUID owner, long until) {}

	private static final List<Carpet> CARPETS = new ArrayList<>();
	private static final Map<UUID, Infection> INFECTED = new HashMap<>();
	/** Until when each Mycelid's Great Bloom lasts: then monsters dying in its clouds burst into new ones. */
	private static final Map<UUID, Long> BLOOMING = new HashMap<>();
	/** When each player may taste each effect again. Not saved: a relog costs at most one aftertaste. */
	private static final Map<UUID, Map<net.minecraft.core.Holder<MobEffect>, Long>> TASTED = new HashMap<>();

	private Mycelid() {
	}

	public static void reset() {
		JUMPS.clear();
		CLOUDS.clear();
		TASTED.clear();
		CARPETS.clear();
		INFECTED.clear();
		BLOOMING.clear();
		MycelidFx.reset();
	}

	// ---- passives, once a second ----

	public static boolean inDark(ServerPlayer player) {
		return player.level().getMaxLocalRawBrightness(BlockPos.containing(player.getEyePosition())) <= 7;
	}

	public static boolean onMycelium(ServerPlayer player) {
		if (onCarpet(player)) {
			return true;
		}
		BlockState below = player.level().getBlockState(player.blockPosition().below());
		return below.is(Blocks.MYCELIUM) || below.is(Blocks.PODZOL) || below.is(Blocks.RED_MUSHROOM_BLOCK)
				|| below.is(Blocks.BROWN_MUSHROOM_BLOCK) || below.is(Blocks.MUSHROOM_STEM) || below.is(MagesLegacy.MYCELIUM_NODE.get());
	}

	/** Lands where no rain falls: deserts, savannas, badlands, the Nether and the End. */
	public static boolean dry(ServerPlayer player) {
		return !player.level().getBiome(player.blockPosition()).value().hasPrecipitation();
	}

	public static boolean inSun(ServerPlayer player) {
		ServerLevel world = player.serverLevel();
		return world.dimensionType().hasSkyLight() && world.isDay() && !world.isRaining()
				&& world.canSeeSky(BlockPos.containing(player.getEyePosition()));
	}

	public static void tickSecond(ServerPlayer player, RaceData data) {
		boolean dark = inDark(player);
		boolean mycelium = onMycelium(player);
		boolean sun = inSun(player);
		// Dry lands starve the spores: they don't grow and the dark doesn't mend, unless the Mycelid stands on its own
		// mycelium (Damp Flesh keeps half the growth)
		boolean dry = dry(player) && !mycelium;
		float growth = 0.5f + (dark ? 0.5f : 0) + (mycelium ? 1.0f : 0);
		data.addResource(dry ? (data.has(Talents.FLESH_FIREPROOF) ? growth / 2 : 0) : growth);
		// Fire eats the spores, fire resistance or not
		if (player.isOnFire()) {
			data.addResource(-5);
		}

		if (dark) {
			refresh(player, MobEffects.NIGHT_VISION, 0, 300, 220);
		}
		if ((dark && !dry) || mycelium) {
			refresh(player, MobEffects.REGENERATION, data.has(Talents.FLESH_DARK_REGEN) ? 1 : 0, 100, 20);
		}
		if (sun && !data.has(Talents.FLESH_SUNPROOF)) {
			refresh(player, MobEffects.WEAKNESS, 0, 60, 30);
			player.causeFoodExhaustion(0.1f);
		}
		modifier(player, Attributes.ARMOR, CAP_ARMOR, "mycelid_cap", 4, data.has(Talents.FLESH_CAP) && !sun);
		modifier(player, Attributes.MAX_HEALTH, HEARTY_HEALTH, "mycelid_hearty", 4, data.has(Talents.FLESH_HEARTY));
		if (data.has(Talents.NET_MIND)) {
			forestMind(player);
		}
	}

	/** Adds or removes a talent's attribute bonus so it matches whether the talent applies right now. */
	private static void modifier(ServerPlayer player, net.minecraft.core.Holder<Attribute> attribute, net.minecraft.resources.ResourceLocation id, String name, double amount, boolean want) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null || want == (instance.getModifier(id) != null)) {
			return;
		}
		if (want) {
			instance.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
		} else {
			instance.removeModifier(id);
			player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
		}
	}

	/** Forest Mind: monsters near any of your nodes in this world glow, so you see them through walls. */
	private static void forestMind(ServerPlayer player) {
		ServerLevel world = player.serverLevel();
		for (GlobalPos node : RaceState.get(player.getServer()).nodes(player.getUUID())) {
			if (!node.dimension().equals(world.dimension())) {
				continue;
			}
			// The mycelium under them knows every step: they glow, and the threads in the ground hold them back
			for (LivingEntity mob : world.getEntitiesOfClass(LivingEntity.class, new AABB(node.pos()).inflate(MIND_RADIUS),
					e -> e instanceof Enemy && e.isAlive())) {
				mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, true, false));
				mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, true, false));
			}
			// And the Mycelid on its own ground stands firm
			if (node.pos().closerToCenterThan(player.position(), MIND_RADIUS)) {
				refresh(player, MobEffects.DAMAGE_RESISTANCE, 0, 60, 30);
			}
		}
	}

	/** Gives the effect only when it is missing or nearly over, so timed effects (regeneration) keep their rhythm. */
	private static void refresh(ServerPlayer player, net.minecraft.core.Holder<MobEffect> effect, int amplifier, int duration, int below) {
		MobEffectInstance current = player.getEffect(effect);
		if (current == null || current.getAmplifier() < amplifier || current.getDuration() <= below) {
			player.addEffect(new MobEffectInstance(effect, duration, amplifier, true, false, true));
		}
	}

	/** Takes off the talents' attribute bonuses (the race was reset or changed). */
	public static void removeCapArmor(ServerPlayer player) {
		modifier(player, Attributes.ARMOR, CAP_ARMOR, "mycelid_cap", 4, false);
		modifier(player, Attributes.MAX_HEALTH, HEARTY_HEALTH, "mycelid_hearty", 4, false);
	}

	// ---- diet ----

	/** Eats a raw mushroom or fungus from the hand: spores, a little food and its taste. */
	public static boolean eat(ServerPlayer player, RaceData data, ItemStack stack) {
		net.minecraft.core.Holder<MobEffect> effect = DIET.get(stack.getItem());
		if (effect == null || player.getCooldowns().isOnCooldown(stack.getItem())) {
			return false;
		}
		player.getFoodData().eat(2, 0.3f);
		boolean feast = data.has(Talents.FLESH_FEAST);
		data.addResource(feast ? 15 : 10);
		if (feast) {
			player.heal(3);
		}
		// A taste is a moment's strength, not a lasting one: the same taste comes back only after its aftertaste
		Map<net.minecraft.core.Holder<MobEffect>, Long> tasted = TASTED.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
		long now = now(player);
		if (tasted.getOrDefault(effect, 0L) <= now) {
			taste(player, data, effect);
			tasted.put(effect, now + AFTERTASTE);
		} else {
			player.displayClientMessage(Component.translatable("mages_legacy.mycelid.aftertaste", (tasted.get(effect) - now + 19) / 20), true);
		}
		Progression.addXp(player, data, DIET_XP, 5, DIET_XP_CAP);
		player.getCooldowns().addCooldown(stack.getItem(), 10);
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}
		player.level().playSound(null, player.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.8f, 1.1f);
		RacePackets.sync(player, data);
		return true;
	}

	/** Adds a taste; with one taste allowed (two with the talent), the oldest one fades. */
	public static void taste(ServerPlayer player, RaceData data, net.minecraft.core.Holder<MobEffect> effect) {
		String id = BuiltInRegistries.MOB_EFFECT.getKey(effect.value()).toString();
		data.tastes.remove(id);
		data.tastes.add(id);
		int max = data.has(Talents.FLESH_TWO_TASTES) ? 2 : 1;
		while (data.tastes.size() > max) {
			net.minecraft.core.Holder<MobEffect> old = BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(data.tastes.remove(0))).orElse(null);
			if (old != null) {
				player.removeEffect(old);
			}
		}
		boolean rich = data.has(Talents.FLESH_RICH);
		// Tastes work from the inside: no swirl of particles in front of the eyes, only the icon
		// Rich Tastes raise a level, but resistance from mushrooms stops at I: II would take 40% off every blow, always
		int level = rich && effect != MobEffects.DAMAGE_RESISTANCE ? 1 : 0;
		player.addEffect(new MobEffectInstance(effect, rich ? TASTE_TICKS_RICH : TASTE_TICKS, level, false, false, true));
		data.dirty = true;
	}

	/** After eating food: stews feed the spores, meat feeds a Mycelid only half as well. */
	public static void afterEating(ServerPlayer player, RaceData data, Item item, FoodProperties food) {
		if (item == Items.MUSHROOM_STEW || item == Items.SUSPICIOUS_STEW) {
			data.addResource(15);
			Progression.addXp(player, data, DIET_XP, 5, DIET_XP_CAP);
		} else if (item.getDefaultInstance().is(net.minecraft.tags.ItemTags.MEAT) && !data.has(Talents.FLESH_MEAT)) {
			int lost = food.nutrition() / 2;
			player.getFoodData().setFoodLevel(Math.max(0, player.getFoodData().getFoodLevel() - lost));
		}
	}

	/** Fire burns half again as badly (less with Damp Flesh); talents soften falls and poison attackers. */
	public static float modifyDamage(ServerPlayer player, DamageSource source, float amount) {
		RaceData data = RaceState.of(player);
		boolean has = data != null;
		if (source.is(DamageTypeTags.IS_FIRE)) {
			return amount * (has && data.has(Talents.FLESH_FIREPROOF) ? 1.15f : 1.5f);
		}
		if (source.is(DamageTypeTags.IS_FALL) && has && data.has(Talents.NET_LIGHT_STEP)) {
			return amount * 0.5f;
		}
		if (has && data.has(Talents.FLESH_SPORE_BLOOD) && source.getEntity() instanceof LivingEntity attacker && attacker instanceof Enemy) {
			attacker.addEffect(new MobEffectInstance(attacker.isInvertedHealAndHarm() ? MobEffects.WITHER : MobEffects.POISON, 60, 0));
		}
		return amount;
	}

	/**
	 * Rebirth: a killing blow instead sprouts the Mycelid again at its nearest node in this world, with half health.
	 * Once in 20 minutes. Returns false to cancel the death.
	 */
	public static boolean allowDeath(ServerPlayer player) {
		RaceData data = RaceState.of(player);
		if (data == null || data.race != Race.MYCELID || !data.has(Talents.FLESH_REBIRTH) || !ready(player, data, REBIRTH_SLOT)) {
			return true;
		}
		ServerLevel world = player.serverLevel();
		GlobalPos nearest = null;
		for (GlobalPos node : RaceState.get(player.getServer()).nodes(player.getUUID())) {
			if (node.dimension().equals(world.dimension()) && world.getBlockState(node.pos()).is(MagesLegacy.MYCELIUM_NODE.get())
					&& (nearest == null || node.pos().distToCenterSqr(player.position()) < nearest.pos().distToCenterSqr(player.position()))) {
				nearest = node;
			}
		}
		if (nearest == null) {
			return true;
		}
		Vec3 fell = player.position();
		Vec3 to = Vec3.atBottomCenterOf(nearest.pos().above());
		player.setHealth(player.getMaxHealth() / 2);
		player.clearFire();
		player.fallDistance = 0;
		player.teleportTo(world, to.x, to.y, to.z, player.getYRot(), player.getXRot());
		MycelidFx.rebirth(world, fell, to);
		// The mycelium gives the body back: spores wind in, a column of light
		Fx.helix(world, player, 1.4f, 2.6f, 4, true, 26, SPORE_RGB);
		Fx.pillar(world, to, 0.7f, 5, 24, SPORE_RGB);
		Fx.threads(world, to, 2.4f, 1.5f, 14, 44, SPORE_RGB);
		Fx.nebula(world, to, null, 2.4f, 40, SPORE_RGB, 0xC8FFB0);
		Fx.burst(world, to.add(0, 1, 0), 3.0f, 9, MOSS_INK);
		Fx.screen(player, 30, SPORE_RGB);
		Fx.shake(player, 2.0f, 12);
		world.playSound(null, nearest.pos(), SoundEvents.FUNGUS_PLACE, SoundSource.PLAYERS, 1.2f, 0.5f);
		world.playSound(null, nearest.pos(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.5f, 1.4f);
		player.displayClientMessage(Component.translatable("mages_legacy.mycelid.reborn").withStyle(net.minecraft.ChatFormatting.GREEN), false);
		cooldown(player, data, REBIRTH_SLOT, REBIRTH_COOLDOWN);
		return false;
	}

	// ---- abilities ----

	public static AbilityResult use(ServerPlayer player, RaceData data, int slot) {
		return switch (slot) {
			case 0 -> sporeJump(player, data);
			case 1 -> sporeCloud(player, data);
			case 2 -> placeNode(player, data);
			case 3 -> carpet(player, data);
			case 4 -> data.has(Talents.SWARM_BLOOM) ? bloom(player, data) : RaceData.talentLocked(player, Talents.SWARM_BLOOM);
			default -> AbilityResult.LOCKED;
		};
	}

	private static long now(ServerPlayer player) {
		return player.getServer().getTickCount();
	}

	private static boolean ready(ServerPlayer player, RaceData data, int slot) {
		return data.readyAt.getOrDefault(slot, 0L) <= now(player);
	}

	private static void cooldown(ServerPlayer player, RaceData data, int slot, int ticks) {
		data.readyAt.put(slot, now(player) + ticks);
		data.dirty = true;
	}

	public static int jumpCost(RaceData data) {
		return data.has(Talents.NET_CHEAP) ? JUMP_COST_CHEAP : JUMP_COST;
	}

	public static double jumpRange(RaceData data) {
		return data.has(Talents.NET_REACH) ? 22 : 16;
	}

	/** How long the spores fly to a spot this far: a far jump leaves the body standing longer. */
	public static int jumpDelay(double distance) {
		return Math.min(JUMP_DELAY, 6 + (int) Math.round(distance / 2));
	}

	public static AbilityResult sporeJump(ServerPlayer player, RaceData data) {
		long now = now(player);
		boolean chained = data.chainLeft > 0 && now <= data.chainUntil;
		if (!chained && !ready(player, data, 0)) {
			return AbilityResult.COOLDOWN;
		}
		int cost = chained ? CHAIN_COST : jumpCost(data);
		if (data.resource < cost) {
			return AbilityResult.NO_RESOURCE;
		}
		if (JUMPS.stream().anyMatch(j -> j.player().equals(player.getUUID()))) {
			return AbilityResult.COOLDOWN;
		}
		// The chain's quick jumps are short hops, half the reach
		Vec3 target = landing(player, chained ? jumpRange(data) / 2 : jumpRange(data));
		if (target == null) {
			return AbilityResult.NO_SPACE;
		}
		data.addResource(-cost);
		if (chained) {
			data.chainLeft--;
		} else {
			data.chainLeft = data.has(Talents.NET_CHAIN) ? 2 : 0;
			cooldown(player, data, 0, JUMP_COOLDOWN);
		}
		data.chainUntil = now + 60;
		ServerLevel world = player.serverLevel();
		// The body crumbles into spores, one flies ahead along an arc and the Mycelid sprouts where it lands
		MycelidFx.crumble(world, player.position());
		MycelidFx.landingMark(world, target, 0);
		Fx.burst(world, player.position().add(0, 1, 0), 1.3f, 6, MOSS_INK);
		// The body scatters into a puff of spores, and a comet of them flies ahead to where it will sprout
		Fx.nebula(world, player.position(), null, 1.2f, 18, SPORE_RGB, 0xC8FFB0);
		int delay = jumpDelay(target.distanceTo(player.position()));
		Fx.comet(world, player.position(), target, delay, SPORE_RGB);
		world.playSound(null, player.blockPosition(), SoundEvents.FUNGUS_BREAK, SoundSource.PLAYERS, 1.0f, 0.7f);
		JUMPS.add(new PendingJump(player.getUUID(), target, now + delay, player.position(), delay));
		data.dirty = true;
		return AbilityResult.OK;
	}

	/**
	 * Where the spore lands along the look: on top of the block it hits, or on whatever block is under the end of the
	 * reach, never in the air; null if there is nothing to take root on.
	 */
	public static Vec3 landing(ServerPlayer player, double range) {
		ServerLevel world = player.serverLevel();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0f);
		BlockHitResult hit = world.clip(new ClipContext(eye, eye.add(look.scale(range)),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 end = hit.getType() == HitResult.Type.MISS ? eye.add(look.scale(range))
				: hit.getLocation().subtract(look.scale(0.4));
		// Spores that flew into the open sky drift down: they settle on whatever lies below the end of their flight, so
		// a jump aimed a little upwards carries as far as one aimed level instead of landing at the Mycelid's feet
		double fall = hit.getType() == HitResult.Type.MISS ? range + 4 : 4;
		// The spore marks where the feet go; walk back towards the player until the body fits, then settle on ground
		for (double back = 0; back <= range; back += 0.5) {
			Vec3 feet = end.subtract(look.scale(back));
			if (hit.getType() == HitResult.Type.BLOCK && hit.getDirection() == Direction.UP && back == 0) {
				feet = Vec3.atBottomCenterOf(hit.getBlockPos().above());
			}
			if (feet.closerThan(player.position(), 1.5)) {
				break;
			}
			Vec3 grounded = drop(player, feet, fall);
			if (grounded != null) {
				return grounded;
			}
		}
		return null;
	}

	/** Lets the body settle on a block up to 4 blocks below; null if it is stuck on the way down or there is nothing to land on. */
	private static Vec3 drop(ServerPlayer player, Vec3 feet) {
		return drop(player, feet, 4);
	}

	/** Lets the body settle on a block up to {@code depth} blocks below; null if it is stuck on the way or nothing is there. */
	private static Vec3 drop(ServerPlayer player, Vec3 feet, double depth) {
		ServerLevel world = player.serverLevel();
		for (double down = 0; down <= depth; down += 0.25) {
			AABB box = player.getDimensions(player.getPose()).makeBoundingBox(feet.subtract(0, down, 0));
			if (!world.noCollision(player, box)) {
				return null;
			}
			if (!world.noCollision(player, box.move(0, -0.3, 0))) {
				return feet.subtract(0, down, 0);
			}
		}
		// Nothing below within reach: a spore can't take root in the air
		return null;
	}

	public static AbilityResult sporeCloud(ServerPlayer player, RaceData data) {
		if (!ready(player, data, 1)) {
			return AbilityResult.COOLDOWN;
		}
		if (data.resource < CLOUD_COST) {
			return AbilityResult.NO_RESOURCE;
		}
		data.addResource(-CLOUD_COST);
		cooldown(player, data, 1, data.has(Talents.SWARM_QUICK) ? CLOUD_COOLDOWN_QUICK : CLOUD_COOLDOWN);
		double radius = data.has(Talents.SWARM_WIDE) ? 6 : 4;
		spawnCloud(player.serverLevel(), player.position(), player.getUUID(), radius,
				data.has(Talents.SWARM_POTENT) ? 2 : 1, data.has(Talents.SWARM_LINGERING) ? 240 : 120, data.has(Talents.SWARM_FOLLOW));
		MycelidFx.cloudBurst(player.serverLevel(), player.position(), radius);
		// The cloud itself: a glowing nebula of spores for as long as it lasts, and a puff as it bursts out
		ServerLevel here = player.serverLevel();
		int lasts = data.has(Talents.SWARM_LINGERING) ? 240 : 120;
		Fx.nebula(here, player.position(), data.has(Talents.SWARM_FOLLOW) ? player : null, (float) radius, lasts, SPORE_RGB, 0xC8FFB0);
		Fx.burst(here, player.position().add(0, 1, 0), (float) radius, 9, MOSS_INK);
		Fx.shake(player, 0.8f, 8);
		if (data.has(Talents.SWARM_BURST)) {
			burst(player, radius);
		}
		player.level().playSound(null, player.blockPosition(), SoundEvents.SPORE_BLOSSOM_BREAK, SoundSource.PLAYERS, 1.2f, 0.6f);
		// A cloud teaches only when it catches someone
		if (!here.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius, 2, radius), e -> e instanceof Enemy && e.isAlive()).isEmpty()) {
			Progression.addXp(player, data, "mycelid_cloud", 3, 30);
		}
		return AbilityResult.OK;
	}

	/** The spore jump's arc, start to landing, for its trail of spores. */
	private static List<Vec3> arcPath(Vec3 from, Vec3 to) {
		List<Vec3> path = new ArrayList<>();
		double apex = 1.5 + from.distanceTo(to) * 0.15;
		for (int i = 0; i <= 16; i++) {
			double t = i / 16.0;
			path.add(from.lerp(to, t).add(0, 4 * apex * t * (1 - t) - 0.6, 0));
		}
		return path;
	}

	public static void spawnCloud(ServerLevel world, Vec3 center, UUID owner, double radius, int potency, int ticks) {
		spawnCloud(world, center, owner, radius, potency, ticks, false);
	}

	public static void spawnCloud(ServerLevel world, Vec3 center, UUID owner, double radius, int potency, int ticks, boolean follows) {
		long now = world.getServer().getTickCount();
		CLOUDS.add(new Cloud(world, center, owner, radius, potency, now, now + ticks, follows));
	}

	/** Spore Burst: the cloud bursts out as it forms, hurting monsters in it and throwing them back. */
	private static void burst(ServerPlayer player, double radius) {
		ServerLevel world = player.serverLevel();
		for (LivingEntity mob : world.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius, 2, radius),
				e -> e instanceof Enemy && e.isAlive() && e.position().closerThan(player.position(), radius + 0.5))) {
			mob.hurt(world.damageSources().playerAttack(player), 4);
			Vec3 away = mob.position().subtract(player.position());
			mob.knockback(1.0, -away.x, -away.z);
		}
		MycelidFx.blast(world, player.position(), radius);
		Fx.burst(world, player.position().add(0, 1, 0), (float) radius + 1, 8, SPORE_RGB);
		Fx.ring(world, player.position(), 0.5f, (float) radius + 2, 10, MOSS_INK);
		Fx.shake(player, 2.0f, 8);
		world.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5f, 1.6f);
	}

	/** Where a cloud is now: on its owner if it follows them, where it formed otherwise. */
	private static Vec3 centerOf(Cloud cloud) {
		if (cloud.follows()) {
			ServerPlayer owner = cloud.world().getServer().getPlayerList().getPlayer(cloud.owner());
			if (owner != null && owner.isAlive() && owner.serverLevel() == cloud.world()) {
				return owner.position();
			}
		}
		return cloud.center();
	}

	public static int nodeLimit(RaceData data) {
		return data.has(Talents.NET_NODES) ? 5 : 3;
	}

	public static int nodeRange(RaceData data) {
		return data.has(Talents.NET_FAR) ? 500 : 150;
	}

	/** Whether an entity stands on a Mycelium Carpet. */
	public static boolean onCarpet(Entity entity) {
		for (Carpet carpet : CARPETS) {
			if (carpet.world() == entity.level() && Math.abs(entity.getY() - carpet.center().y) < 2.5
					&& entity.position().multiply(1, 0, 1).closerThan(carpet.center().multiply(1, 0, 1), carpet.radius())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Mycelium Carpet (H): mycelium runs out over the ground around the Mycelid for 20 seconds. For any Mycelid it is
	 * mycelium (spores grow fast, the body mends, even in dry lands) and other players on it mend too; monsters on it
	 * stick fast while the threads drink them, and what they drink comes back to the Mycelid as spores.
	 */
	public static AbilityResult carpet(ServerPlayer player, RaceData data) {
		if (!ready(player, data, 3)) {
			return AbilityResult.COOLDOWN;
		}
		if (data.resource < CARPET_COST) {
			return AbilityResult.NO_RESOURCE;
		}
		ServerLevel world = player.serverLevel();
		Vec3 center = drop(player, player.position());
		if (center == null) {
			return AbilityResult.NO_SPACE;
		}
		data.addResource(-CARPET_COST);
		cooldown(player, data, 3, CARPET_COOLDOWN);
		long now = now(player);
		CARPETS.add(new Carpet(world, center, player.getUUID(), CARPET_RADIUS, now + CARPET_TICKS));
		Fx.mark(world, center, null, (float) CARPET_RADIUS, CARPET_TICKS, SPORE_RGB, Fx.MYCELIUM);
		Fx.threads(world, center, (float) CARPET_RADIUS * 0.6f, 1.4f, 9, 30, SPORE_RGB);
		Fx.burst(world, center.add(0, 0.3, 0), (float) CARPET_RADIUS, 8, MOSS_INK);
		world.sendParticles(ParticleTypes.MYCELIUM, center.x, center.y + 0.2, center.z, 80, CARPET_RADIUS * 0.5, 0.1, CARPET_RADIUS * 0.5, 0.0);
		world.playSound(null, player.blockPosition(), SoundEvents.ROOTS_PLACE, SoundSource.PLAYERS, 1.2f, 0.6f);
		world.playSound(null, player.blockPosition(), SoundEvents.MOSS_PLACE, SoundSource.PLAYERS, 1.2f, 0.7f);
		if (!world.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(CARPET_RADIUS, 2, CARPET_RADIUS),
				e -> e instanceof Enemy && e.isAlive()).isEmpty()) {
			Progression.addXp(player, data, "mycelid_carpet", 3, 15);
		}
		return AbilityResult.OK;
	}

	/**
	 * The Great Bloom (Alt, the Swarm's crown): every monster within 12 blocks takes the spores in. For 10 seconds an
	 * infected monster that dies bursts into a spore cloud, and monsters dying in this Mycelid's clouds burst too:
	 * a chain through the whole crowd.
	 */
	public static AbilityResult bloom(ServerPlayer player, RaceData data) {
		if (!ready(player, data, 4)) {
			return AbilityResult.COOLDOWN;
		}
		if (data.resource < BLOOM_COST) {
			return AbilityResult.NO_RESOURCE;
		}
		ServerLevel world = player.serverLevel();
		List<LivingEntity> crowd = world.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(BLOOM_RADIUS, 4, BLOOM_RADIUS),
				e -> e instanceof Enemy && e.isAlive() && e.position().closerThan(player.position(), BLOOM_RADIUS));
		if (crowd.isEmpty()) {
			return AbilityResult.NO_ENEMIES;
		}
		data.addResource(-BLOOM_COST);
		cooldown(player, data, 4, BLOOM_COOLDOWN);
		long now = now(player);
		BLOOMING.put(player.getUUID(), now + BLOOM_TICKS);
		for (LivingEntity mob : crowd) {
			INFECTED.put(mob.getUUID(), new Infection(player.getUUID(), now + BLOOM_TICKS));
			mob.addEffect(new MobEffectInstance(mob.isInvertedHealAndHarm() ? MobEffects.WITHER : MobEffects.POISON, BLOOM_TICKS, 0));
			mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, BLOOM_TICKS, 0, false, false));
			// A thread of mycelium from the Mycelid into every one of them
			Fx.tendril(world, player, mob, 14, MOSS_INK, SPORE_RGB);
		}
		Fx.mark(world, player.position(), null, (float) BLOOM_RADIUS, BLOOM_TICKS, SPORE_RGB, Fx.MYCELIUM);
		Fx.nebula(world, player.position(), null, (float) BLOOM_RADIUS * 0.6f, 50, SPORE_RGB, 0xC8FFB0);
		Fx.threads(world, player.position(), 3.0f, 2.2f, 16, 40, SPORE_RGB);
		Fx.burst(world, player.position().add(0, 1, 0), (float) BLOOM_RADIUS, 10, SPORE_RGB);
		Fx.shake(player, 2.5f, 14);
		Fx.fov(player, 0.2f, 16);
		world.playSound(null, player.blockPosition(), SoundEvents.SPORE_BLOSSOM_BREAK, SoundSource.PLAYERS, 2.0f, 0.4f);
		world.playSound(null, player.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 0.4f, 1.6f);
		return AbilityResult.OK;
	}

	public static AbilityResult placeNode(ServerPlayer player, RaceData data) {
		if (!Progression.tierOpen(player, 2)) {
			return AbilityResult.LOCKED;
		}
		if (!ready(player, data, 2)) {
			return AbilityResult.COOLDOWN;
		}
		if (data.resource < NODE_COST) {
			return AbilityResult.NO_RESOURCE;
		}
		ServerLevel world = player.serverLevel();
		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = world.clip(new ClipContext(eye, eye.add(player.getViewVector(1.0f).scale(5)),
				ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		if (hit.getType() != HitResult.Type.BLOCK) {
			return AbilityResult.NO_TARGET;
		}
		BlockPos pos = hit.getBlockPos().above();
		if (!world.getBlockState(pos).canBeReplaced() || !world.getBlockState(pos.below()).isFaceSturdy(world, pos.below(), Direction.UP)) {
			return AbilityResult.NO_SPACE;
		}
		boolean spreads = RaceState.get(world.getServer()).nodes(player.getUUID()).stream()
				.noneMatch(n -> n.dimension().equals(world.dimension()) && n.pos().closerThan(pos, 32));
		placeNodeAt(world, pos, player.getUUID(), nodeLimit(data));
		data.addResource(-NODE_COST);
		cooldown(player, data, 2, NODE_COOLDOWN);
		// Only a node that spreads the network teaches: not regrowing one on the same spot over and over
		if (spreads) {
			Progression.addXp(player, data, "mycelid_node", 5, 15);
		}
		return AbilityResult.OK;
	}

	/** Grows a node and adds it to the owner's network; past the limit the oldest node withers away. */
	public static void placeNodeAt(ServerLevel world, BlockPos pos, UUID owner, int limit) {
		RaceState state = RaceState.get(world.getServer());
		List<GlobalPos> nodes = state.nodes(owner);
		world.setBlockAndUpdate(pos, MagesLegacy.MYCELIUM_NODE.get().defaultBlockState());
		nodes.add(GlobalPos.of(world.dimension(), pos));
		while (nodes.size() > limit) {
			GlobalPos old = nodes.remove(0);
			ServerLevel oldWorld = world.getServer().getLevel(old.dimension());
			if (oldWorld != null && oldWorld.getBlockState(old.pos()).is(MagesLegacy.MYCELIUM_NODE.get())) {
				oldWorld.removeBlock(old.pos(), false);
			}
		}
		state.setDirty();
		MycelidFx.nodeGrow(world, Vec3.atBottomCenterOf(pos));
		Fx.pillar(world, Vec3.atBottomCenterOf(pos), 0.4f, 2.5f, 16, SPORE_RGB);
		Fx.threads(world, Vec3.atBottomCenterOf(pos), 0.9f, 1.8f, 7, 30, SPORE_RGB);
		world.playSound(null, pos, SoundEvents.FUNGUS_PLACE, SoundSource.BLOCKS, 1.0f, 0.8f);
		world.playSound(null, pos, SoundEvents.ROOTS_PLACE, SoundSource.BLOCKS, 1.0f, 0.6f);
	}

	/**
	 * Travel through a node: to the next node of the same network (in the order they grew) within the owner's range.
	 * Anyone may travel, so a Mycelid's network serves friends too.
	 */
	public static boolean travel(ServerPlayer player, BlockPos from) {
		ServerLevel world = player.serverLevel();
		RaceState state = RaceState.get(world.getServer());
		UUID owner = state.nodeOwner(world.dimension(), from).orElse(null);
		if (owner == null) {
			return false;
		}
		List<GlobalPos> nodes = state.nodes(owner);
		int range = nodeRange(state.data(owner));
		int start = nodes.indexOf(GlobalPos.of(world.dimension(), from));
		for (int i = 1; i < nodes.size(); i++) {
			GlobalPos next = nodes.get((start + i) % nodes.size());
			if (next.dimension().equals(world.dimension()) && next.pos().closerThan(from, range)
					&& world.getBlockState(next.pos()).is(MagesLegacy.MYCELIUM_NODE.get())) {
				Vec3 to = Vec3.atBottomCenterOf(next.pos().above());
				MycelidFx.column(world, player.position());
				// Carried through the mycelium: a streak from node to node, a burst where the body comes up
				Fx.ribbon(world, List.of(player.position(), to), 16, MOSS_INK, SPORE_RGB, 0.8f);
				Fx.burst(world, to.add(0, 1, 0), 1.5f, 7, SPORE_RGB);
				Fx.fov(player, 0.15f, 10);
				player.teleportTo(world, to.x, to.y, to.z, player.getYRot(), player.getXRot());
				player.fallDistance = 0;
				MycelidFx.column(world, to);
				MycelidFx.ring(world, to, 0.3, 1.8, 6, MycelidFx.SPORE);
				world.sendParticles(ModParticles.SPORE.get(), to.x, to.y + 0.5, to.z, 40, 0.5, 1.0, 0.5, 0.02);
				world.playSound(null, next.pos(), SoundEvents.FUNGUS_PLACE, SoundSource.BLOCKS, 1.0f, 0.6f);
				RaceData travellerData = RaceState.of(player);
				// No race experience for travelling: walking back and forth between two nodes would farm it. For the
				// same reason the Fed Network bonus comes at most once in 30 seconds.
				if (travellerData != null && travellerData.race == Race.MYCELID) {
					if (travellerData.has(Talents.NET_HASTE)) {
						player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 1, false, false, true));
					}
					if (travellerData.has(Talents.NET_SHARED) && ready(player, travellerData, FED_NETWORK_SLOT)) {
						player.heal(4);
						travellerData.addResource(10);
						cooldown(player, travellerData, FED_NETWORK_SLOT, FED_NETWORK_COOLDOWN);
					}
				}
				return true;
			}
		}
		player.displayClientMessage(Component.translatable("mages_legacy.mycelid.node_alone"), true);
		return true;
	}

	// ---- every tick ----

	public static void tick(MinecraftServer server) {
		long now = server.getTickCount();
		MycelidFx.tick(now);
		for (Iterator<PendingJump> it = JUMPS.iterator(); it.hasNext(); ) {
			PendingJump jump = it.next();
			if (jump.at() > now) {
				ServerPlayer flying = server.getPlayerList().getPlayer(jump.player());
				if (flying != null) {
					double progress = 1 - (jump.at() - now) / (double) jump.ticks();
					MycelidFx.arc(flying.serverLevel(), jump.from(), jump.target(), progress);
					MycelidFx.landingMark(flying.serverLevel(), jump.target(), progress);
				}
				continue;
			}
			it.remove();
			ServerPlayer player = server.getPlayerList().getPlayer(jump.player());
			if (player == null || !player.isAlive()) {
				continue;
			}
			RaceData data = RaceState.of(player);
			ServerLevel world = player.serverLevel();
			if (data != null && data.has(Talents.SWARM_TRAIL)) {
				spawnCloud(world, jump.from(), player.getUUID(), 2.5, 0, 60);
			}
			player.teleportTo(world, jump.target().x, jump.target().y, jump.target().z, player.getYRot(), player.getXRot());
			player.fallDistance = 0;
			MycelidFx.sprout(world, jump.target());
			// The body sprouting out of the ground in a burst
			Fx.burst(world, jump.target().add(0, 1, 0), 1.8f, 7, SPORE_RGB);
			// Threads of mycelium tear up out of the ground and knit the body together; the spores thrown out burst over
			// whatever stands close
			Fx.threads(world, jump.target(), 1.9f, 1.3f, 11, 24, SPORE_RGB);
			for (LivingEntity mob : world.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(3), e -> e instanceof Enemy && e.isAlive())) {
				mob.hurt(world.damageSources().indirectMagic(player, player), 4);
				Vec3 away = mob.position().subtract(jump.target());
				mob.knockback(0.8, -away.x, -away.z);
				mob.addEffect(new MobEffectInstance(mob.isInvertedHealAndHarm() ? MobEffects.WITHER : MobEffects.POISON, 60, 0));
			}
			Fx.helix(world, player, 1.0f, 2.2f, 3, true, 14, SPORE_RGB);
			Fx.ring(world, jump.target(), 0.3f, 3.0f, 12, MOSS_INK);
			Fx.fov(player, 0.12f, 10);
			Fx.shake(player, 1.0f, 6);
			// The arc of the jump stays a moment as a trail of spores drifting down
			for (Vec3 p : arcPath(jump.from(), jump.target())) {
				world.sendParticles(ModParticles.SPORE.get(), p.x, p.y + 1, p.z, 2, 0.12, 0.12, 0.12, 0.01);
			}
			world.playSound(null, player.blockPosition(), SoundEvents.FUNGUS_PLACE, SoundSource.PLAYERS, 1.0f, 1.2f);
			world.playSound(null, player.blockPosition(), SoundEvents.MOSS_PLACE, SoundSource.PLAYERS, 0.8f, 0.8f);
			if (data != null) {
				if (data.has(Talents.NET_ROOTS)) {
					for (LivingEntity mob : world.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(3),
							e -> e instanceof Enemy)) {
						mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 4));
					}
				}
				if (data.has(Talents.NET_CLEANSE)) {
					for (MobEffectInstance effect : new ArrayList<>(player.getActiveEffects())) {
						if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
							player.removeEffect(effect.getEffect());
						}
					}
				}
				// Only a jump in a fight teaches: hopping about an empty field would farm it
				if (!world.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(8), e -> e instanceof Enemy && e.isAlive()).isEmpty()) {
					Progression.addXp(player, data, "mycelid_jump", 2, 30);
				}
			}
		}

		CARPETS.removeIf(carpet -> carpet.until() <= now);
		INFECTED.values().removeIf(infection -> infection.until() <= now);
		BLOOMING.values().removeIf(until -> until <= now);
		if (now % 20 == 0) {
			// The carpet holds monsters fast and drinks them for its Mycelid; friends on it mend; spores drift up from it
			for (Carpet carpet : CARPETS) {
				Vec3 c = carpet.center();
				double r = carpet.radius();
				ServerPlayer owner = server.getPlayerList().getPlayer(carpet.owner());
				RaceData ownerData = owner == null ? null : RaceState.of(owner);
				for (LivingEntity mob : carpet.world().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r, 2, r),
						e -> e instanceof Enemy && e.isAlive() && onCarpet(e))) {
					mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2));
					if (owner != null) {
						mob.hurt(carpet.world().damageSources().indirectMagic(owner, owner), CARPET_BITE);
						Fx.tendril(carpet.world(), mob, owner, 10, MOSS_INK, SPORE_RGB);
						if (ownerData != null && ownerData.race == Race.MYCELID) {
							ownerData.addResource(CARPET_SPORES);
						}
					}
				}
				for (Player friend : carpet.world().getPlayers(p -> p.isAlive() && !p.isSpectator() && onCarpet(p))) {
					if (RaceState.raceOf(friend).orElse(null) != Race.MYCELID) {
						friend.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0, true, false, true));
					}
				}
				carpet.world().sendParticles(ModParticles.SPORE.get(), c.x, c.y + 0.2, c.z, 12, r * 0.5, 0.1, r * 0.5, 0.01);
			}
		}

		for (Iterator<Cloud> it = CLOUDS.iterator(); it.hasNext(); ) {
			Cloud cloud = it.next();
			if (cloud.until() <= now) {
				it.remove();
				continue;
			}
			ServerLevel world = cloud.world();
			Vec3 c = centerOf(cloud);
			if (now % 3 == 0) {
				double r = cloud.radius();
				// Glowing spores drift up through the cloud (they live a few seconds, so a few at a time are plenty)
				world.sendParticles(ModParticles.SPORE.get(), c.x, c.y + 0.5, c.z, (int) (r * r * 0.4), r * 0.5, 0.5, r * 0.5, 0.0);
				world.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, c.x, c.y + 1.2, c.z, (int) (r * 4), r * 0.5, 0.6, r * 0.5, 0.0);
				MycelidFx.vortex(world, c, r, now - cloud.start());
				// The edge of the cloud, so it's clear where it ends
				for (int i = 0; i < 12; i++) {
					double a = (now * 0.05) + i * Math.PI / 6;
					world.sendParticles(ParticleTypes.MYCELIUM, c.x + Math.cos(a) * r, c.y + 0.2, c.z + Math.sin(a) * r, 1, 0.05, 0.1, 0.05, 0.0);
				}
			}
			// Bites at once, then every second
			if ((now - cloud.start()) % 20 == 0) {
				AABB area = new AABB(c, c).inflate(cloud.radius(), 2, cloud.radius());
				for (LivingEntity entity : world.getEntitiesOfClass(LivingEntity.class, area,
						e -> e.isAlive() && e.position().closerThan(c, cloud.radius() + 0.5))) {
					applyCloud(entity, cloud, owner(cloud));
				}
			}
		}
	}

	/** The owner's race data, if they are online. */
	private static RaceData owner(Cloud cloud) {
		ServerPlayer owner = cloud.world().getServer().getPlayerList().getPlayer(cloud.owner());
		return owner == null ? null : RaceState.of(owner);
	}

	/**
	 * Living monsters choke on the spores, the undead (immune to poison) rot; players breathe them in and heal. The
	 * owner's talents add weakness to monsters and stronger healing to players.
	 */
	private static void applyCloud(LivingEntity entity, Cloud cloud, RaceData owner) {
		if (entity instanceof Enemy) {
			entity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
			entity.addEffect(new MobEffectInstance(entity.isInvertedHealAndHarm() ? MobEffects.WITHER : MobEffects.POISON, 60, cloud.potency()));
			if (cloud.potency() > 0) {
				entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
			}
			if (owner != null && owner.has(Talents.SWARM_WEAKNESS)) {
				entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1));
			}
		} else if (entity instanceof ServerPlayer) {
			int level = owner != null && owner.has(Talents.SWARM_MENDING) ? 1 : 0;
			entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, level, true, false, true));
		}
	}

	/**
	 * A monster died: if it was inside a Mycelid's cloud, the owner gains race experience; with Infection also
	 * spores, and with Bloom the body bursts into a small cloud of its own.
	 */
	public static void onDeath(LivingEntity entity) {
		if (!(entity instanceof Enemy) || entity.getServer() == null) {
			return;
		}
		// Infected by a Great Bloom: the spores burst out of the body
		Infection infection = INFECTED.remove(entity.getUUID());
		if (infection != null && entity.level() instanceof ServerLevel world) {
			burstFrom(world, entity, infection.owner());
			return;
		}
		for (Cloud cloud : CLOUDS) {
			if (cloud.world() == entity.level() && entity.position().closerThan(centerOf(cloud), cloud.radius() + 0.5)) {
				ServerPlayer owner = entity.getServer().getPlayerList().getPlayer(cloud.owner());
				RaceData data = owner == null ? null : RaceState.of(owner);
				if (data == null || data.race != Race.MYCELID) {
					return;
				}
				Progression.addXp(owner, data, Progression.HUNT, CLOUD_KILL_XP, Progression.HUNT_CAP);
				if (data.has(Talents.SWARM_INFECTION)) {
					data.addResource(10);
					Progression.addXp(owner, data, Progression.HUNT, 6, Progression.HUNT_CAP);
				}
				// In a Great Bloom the clouds carry it on: whatever dies in them bursts into a cloud of its own
				if (BLOOMING.getOrDefault(cloud.owner(), 0L) > cloud.world().getServer().getTickCount()) {
					burstFrom(cloud.world(), entity, cloud.owner());
				}
				return;
			}
		}
	}

	/** A body bursts into a small spore cloud of its Mycelid's (the Great Bloom's chain). */
	private static void burstFrom(ServerLevel world, LivingEntity body, UUID owner) {
		ServerPlayer player = world.getServer().getPlayerList().getPlayer(owner);
		RaceData data = player == null ? null : RaceState.of(player);
		spawnCloud(world, body.position(), owner, 3, data != null && data.has(Talents.SWARM_POTENT) ? 2 : 1, 60);
		Fx.nebula(world, body.position(), null, 3.0f, 60, SPORE_RGB, 0xC8FFB0);
		Fx.burst(world, body.position().add(0, body.getBbHeight() / 2, 0), 2.5f, 7, SPORE_RGB);
		world.playSound(null, body.blockPosition(), SoundEvents.FUNGUS_BREAK, SoundSource.HOSTILE, 1.2f, 0.6f);
	}

	/** A Mycelid killed a monster with its own hands in the dark: the hunt the race was made for. */
	public static void onKill(ServerPlayer player, LivingEntity victim) {
		RaceData data = RaceState.of(player);
		if (data != null && data.race == Race.MYCELID && victim instanceof Enemy && inDark(player)) {
			Progression.addXp(player, data, Progression.HUNT, DARK_KILL_XP, Progression.HUNT_CAP);
		}
	}

	public static int activeClouds() {
		return CLOUDS.size();
	}
}
