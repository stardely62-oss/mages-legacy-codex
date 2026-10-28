package euf.mageslegacy.shepherd;

import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.race.AbilityResult;
import euf.mageslegacy.race.AbilitySlots;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceData;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talents;
import euf.mageslegacy.shepherd.ShepherdData.Bond;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Everything the Shepherds do that isn't the echoes or the bonds themselves: the voice (their resource) and the bond
 * capacity, their weaknesses (less health, a weak hand, heavy armour crowds the bonds), the abilities (Call, Rally,
 * the Whistle; the Song of Bonds lives in {@link Bonds}), the auras of the Voice branch, and race experience from what their creatures do.
 */
public final class Shepherd {
	public static final int BASE_CAPACITY = 4;
	/** Colours of the glowing effects: horn gold, spirit cyan, and one per Whistle order (heel, guard, hunt). */
	static final int GOLD_RGB = 0xE8C27A;
	static final int SPIRIT_RGB = 0x9FE8E0;
	private static final int[] STANCE_RGB = {0x9FE8E0, 0xE8C27A, 0xFF8A6A};
	public static final int RALLY_COST = 25;
	public static final float RALLY_HEAL = 0.3f;
	public static final float RALLY_HEAL_WARM = 0.5f;
	public static final int ARMY_COST = 40;
	public static final int CREATURE_KILL_XP = 3;
	public static final int ECHO_LEARNED_XP = 20;
	private static final int CALL_COOLDOWN = 20;
	private static final int RALLY_COOLDOWN = 600;
	private static final int WHISTLE_COOLDOWN = 10;
	/** The Gather that comes with the Whistle's "Follow": its own quiet cooldown, no key. */
	private static final int GATHER_SLOT = 5;
	private static final int GATHER_COOLDOWN = 160;
	private static final int ARMY_SLOT = AbilitySlots.cooldownSlot(Race.SHEPHERD, 4);
	private static final int ARMY_COOLDOWN = 6000;
	private static final int ARMY_KINDS = 6;
	private static final net.minecraft.resources.ResourceLocation FRAIL = euf.mageslegacy.MagesLegacy.id("attribute/shepherd/frail");

	private Shepherd() {
	}

	public static boolean is(ServerPlayer player) {
		return RaceState.raceOf(player).orElse(null) == Race.SHEPHERD;
	}

	// ---- capacity ----

	/** Bond slots: 4, more from talents, one less for every 2 points of heavy armour (see {@link #heavyArmor}). */
	public static int capacityMax(ServerPlayer player, RaceData data) {
		int max = BASE_CAPACITY + (data.has(Talents.LEGION_CAPACITY) ? 2 : 0) + (data.has(Talents.LEGION_MORE) ? 2 : 0)
				+ (data.has(Talents.VOICE_CAPACITY) ? 1 : 0);
		return Math.max(1, max - heavyArmor(player, data) / 2);
	}

	/**
	 * Heavy armour points: iron and chainmail 1 a piece (none with Used to Iron), diamond and netherite 2. Every 2
	 * points take a bond slot: a full iron set costs 2, a full diamond one 4.
	 */
	public static int heavyArmor(ServerPlayer player, RaceData data) {
		int points = 0;
		for (ItemStack stack : player.getArmorSlots()) {
			if (stack.getItem() instanceof ArmorItem armor) {
				var material = armor.getMaterial();
				if (material == ArmorMaterials.DIAMOND || material == ArmorMaterials.NETHERITE) {
					points += 2;
				} else if ((material == ArmorMaterials.IRON || material == ArmorMaterials.CHAIN) && !data.has(Talents.VOICE_ARMOR)) {
					points += 1;
				}
			}
		}
		return points;
	}

	public static int capacityUsed(RaceData data, UUID owner) {
		int beasts = 0;
		for (Bond bond : data.shepherd.bonds) {
			beasts += BeastKind.byId(bond.kind).map(k -> beastCost(k, data)).orElse(0);
		}
		return beasts + Echoes.capacityUsed(owner);
	}

	/** Bond slots a beast takes: Light Bonds take one off (never below one). */
	public static int beastCost(BeastKind kind, RaceData data) {
		return beastCost(kind, data.has(Talents.PACK_LIGHT));
	}

	public static int beastCost(BeastKind kind, boolean light) {
		return light ? Math.max(1, kind.capacity() - 1) : kind.capacity();
	}

	public static int capacityFree(ServerPlayer player, RaceData data) {
		return capacityMax(player, data) - capacityUsed(data, player.getUUID());
	}

	// ---- once a second ----

	public static void tickSecond(ServerPlayer player, RaceData data) {
		List<LivingEntity> near = Creatures.around(player, 16);
		// The voice comes back by itself, faster with creatures around: about two minutes for a full bar alone
		float regen = 0.7f + Math.min(1.0f, near.size() * 0.2f);
		data.addResource(data.has(Talents.VOICE_DEEP) ? regen * 1.5f : regen);

		frail(player, data.has(Talents.VOICE_LIFE) ? -2 : -4);
		if (data.has(Talents.VOICE_AURA)) {
			for (LivingEntity c : near) {
				c.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, false));
			}
		}
		if (data.has(Talents.PACK_FURY) && player.getHealth() < player.getMaxHealth() / 2) {
			for (LivingEntity c : near) {
				if (!Echoes.isEcho(c)) {
					c.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60, 1, true, false));
					c.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 0, true, false));
				}
			}
		}
		stance(player, data, near);
		Bonds.tickOwner(player, data);
	}

	/** Maximum health lost: 4 (2 hearts), 2 with Sturdy Body; 0 takes the penalty away. */
	public static void frail(ServerPlayer player, double amount) {
		AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
		if (health == null) {
			return;
		}
		AttributeModifier current = health.getModifier(FRAIL);
		if (current != null && current.amount() == amount) {
			return;
		}
		if (current != null) {
			health.removeModifier(FRAIL);
		}
		if (amount != 0) {
			health.addTransientModifier(new AttributeModifier(FRAIL, amount, AttributeModifier.Operation.ADD_VALUE));
			player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
		}
	}

	/** The race changed: echoes go, beasts are released, the health penalty ends. */
	public static void cleanup(ServerPlayer player, RaceData data) {
		Echoes.dismiss(player.getServer(), player.getUUID());
		Bonds.releaseAll(player, data);
		frail(player, 0);
	}

	// ---- damage ----

	/** A Shepherd and its own creatures never hurt each other (a stray arrow, a swing in the crowd). */
	public static boolean friendlyFire(LivingEntity victim, DamageSource source) {
		Entity attacker = source.getEntity();
		if (attacker == null || attacker == victim) {
			return false;
		}
		UUID victimSide = victim instanceof ServerPlayer p ? p.getUUID() : Creatures.ownerOf(victim);
		UUID attackerSide = attacker instanceof ServerPlayer p ? p.getUUID() : Creatures.ownerOf(attacker);
		return victimSide != null && victimSide.equals(attackerSide);
	}

	/** Damage changes for Shepherds and their creatures (friendly fire is stopped earlier, in {@link #friendlyFire}). */
	public static float modifyDamage(LivingEntity victim, DamageSource source, float amount) {
		if (friendlyFire(victim, source)) {
			return amount;
		}
		Entity attacker = source.getEntity();
		if (attacker instanceof ServerPlayer shepherd && source.getDirectEntity() == shepherd && is(shepherd)) {
			RaceData data = RaceState.of(shepherd);
			// A Shepherd's own blow is a quarter weaker; Steady Hand only narrows it to a tenth
			amount *= data.has(Talents.VOICE_STRENGTH) ? 0.9f : 0.75f;
		}
		if (attacker != null && Echoes.isEcho(attacker)) {
			amount = Echoes.onHit(attacker, victim, amount);
		}
		UUID creatureOwner = attacker == null || attacker instanceof ServerPlayer ? null : Creatures.ownerOf(attacker);
		ServerPlayer master = creatureOwner == null || attacker.getServer() == null ? null
				: attacker.getServer().getPlayerList().getPlayer(creatureOwner);
		if (master != null) {
			RaceData data = RaceState.of(master);
			// A foe marked by a skeleton echo's arrows takes more from the Shepherd's creatures
			if (victim == Creatures.marked(master)) {
				amount *= 1.3f;
			}
			// The Banner: creatures near their Shepherd hit harder
			if (data.has(Talents.VOICE_BANNER) && attacker.distanceToSqr(master) < 16 * 16) {
				amount *= 1.2f;
			}
			// A fed beast bites harder
			Optional<Bond> bond = data.shepherd.bond(attacker.getUUID());
			if (bond.isPresent()) {
				amount *= Bonds.fedBonus(data, bond.get(), master.serverLevel().getGameTime());
			}
		}
		if (attacker != null && attacker.getTags().contains(Creatures.BOND_TAG)) {
			Bonds.onBeastHit(attacker, victim);
		}
		// On Guard the pack stands firm: a fifth less damage
		UUID guardOwner = victim instanceof ServerPlayer ? null : Creatures.ownerOf(victim);
		if (guardOwner != null && dataOf(victim, guardOwner).map(d -> d.shepherd.stance == ShepherdData.GUARD).orElse(false)) {
			amount *= 0.8f;
		}
		UUID beastOwner = victim.getTags().contains(Creatures.BOND_TAG) ? Creatures.ownerOf(victim) : null;
		if (beastOwner != null && dataOf(victim, beastOwner).map(d -> d.has(Talents.PACK_ARMOR)).orElse(false)) {
			amount *= 0.7f;
		}
		if (victim instanceof ServerPlayer shepherd && is(shepherd) && amount > 0) {
			RaceData data = RaceState.of(shepherd);
			if (Bonds.bearGuards(shepherd, data)) {
				amount *= 0.8f;
			}
			if (data.has(Talents.VOICE_HEART)) {
				amount = shareHeart(shepherd, source, amount);
			}
		}
		return amount;
	}

	private static Optional<RaceData> dataOf(Entity entity, UUID owner) {
		return entity.getServer() == null ? Optional.empty() : Optional.of(RaceState.get(entity.getServer()).data(owner));
	}

	private static boolean ownerHas(Entity creature, String talent) {
		UUID owner = Creatures.ownerOf(creature);
		return owner != null && dataOf(creature, owner).map(d -> d.has(talent)).orElse(false);
	}

	/** One Heart: half the blow is shared out among the creatures within 16 blocks. */
	private static float shareHeart(ServerPlayer shepherd, DamageSource source, float amount) {
		List<LivingEntity> near = Creatures.around(shepherd, 16);
		if (near.isEmpty()) {
			return amount;
		}
		float shared = amount / 2;
		for (LivingEntity c : near) {
			c.hurt(shepherd.damageSources().magic(), shared / near.size());
		}
		return amount - shared;
	}

	// ---- abilities ----

	public static AbilityResult use(ServerPlayer player, RaceData data, int slot) {
		return switch (slot) {
			case 0 -> call(player, data);
			case 1 -> rally(player, data);
			case 2 -> whistle(player, data);
			case 3 -> Bonds.song(player, data);
			case 4 -> data.has(Talents.LEGION_ARMY) ? army(player, data) : RaceData.talentLocked(player, Talents.LEGION_ARMY);
			default -> AbilityResult.LOCKED;
		};
	}

	private static long now(ServerPlayer player) {
		return player.getServer().getTickCount();
	}

	static boolean ready(ServerPlayer player, RaceData data, int slot) {
		return data.readyAt.getOrDefault(slot, 0L) <= now(player);
	}

	static void cooldown(ServerPlayer player, RaceData data, int slot, int ticks) {
		data.readyAt.put(slot, now(player) + ticks);
		data.dirty = true;
	}

	public static boolean learned(RaceData data, EchoType type) {
		return data.shepherd.kills(type.id()) >= type.killsNeeded();
	}

	public static final int PRESET_MAX = 12;

	/** The echoes of the active squad that aren't walking with this Shepherd yet, in the squad's order. */
	public static List<EchoType> missing(RaceData data, Map<String, Integer> alive) {
		List<EchoType> list = new ArrayList<>();
		data.shepherd.presets.get(data.shepherd.activePreset).forEach((id, count) -> EchoType.byId(id).ifPresent(type -> {
			for (int i = type.units(alive.getOrDefault(id, 0)); i < count; i++) {
				list.add(type);
			}
		}));
		return list;
	}

	/**
	 * Call: the active squad rises where you look, all at once. Echoes of it already with you stay; only the missing
	 * ones come, as many as the voice and the bond slots allow. Sneaking sends all your echoes back.
	 */
	public static AbilityResult call(ServerPlayer player, RaceData data) {
		if (data.alt) {
			int gone = Echoes.dismiss(player.getServer(), player.getUUID());
			player.displayClientMessage(Component.translatable("mages_legacy.echo.dismissed", gone), true);
			return AbilityResult.OK;
		}
		if (!ready(player, data, 0)) {
			return AbilityResult.COOLDOWN;
		}
		if (data.shepherd.presets.get(data.shepherd.activePreset).isEmpty()) {
			return AbilityResult.NO_ECHO;
		}
		List<EchoType> missing = missing(data, Echoes.countByType(player.getUUID()));
		if (missing.isEmpty()) {
			player.displayClientMessage(Component.translatable("mages_legacy.echo.squad_full"), true);
			return AbilityResult.OK;
		}
		boolean quiet = data.has(Talents.LEGION_CHEAP);
		Vec3 base = Echoes.callSpot(player);
		int summoned = 0;
		boolean lackVoice = false;
		boolean lackRoom = false;
		for (EchoType type : missing) {
			int cost = type.voiceCost(quiet);
			if (!learned(data, type)) {
				continue;
			}
			if (data.resource < cost) {
				lackVoice = true;
				continue;
			}
			if (capacityFree(player, data) < type.capacity()) {
				lackRoom = true;
				continue;
			}
			// A group (silverfish) comes several at once for one price and one slot
			int spot = summoned;
			boolean any = false;
			for (int g = 0; g < type.group(); g++) {
				any |= Echoes.summon(player, data, type, Echoes.around(player.serverLevel(), base, spot++), false, 0,
						g == 0 ? cost : 0) != null;
			}
			if (any) {
				data.addResource(-cost);
				summoned++;
			}
		}
		if (summoned == 0) {
			return lackVoice ? AbilityResult.NO_RESOURCE : lackRoom ? AbilityResult.NO_CAPACITY : AbilityResult.NO_SPACE;
		}
		// Souls answer: a faint spirit ring on the ground and wisps welling up out of it
		Fx.ring(player.serverLevel(), base, 0.3f, 3.5f, 16, SPIRIT_RGB);
		player.serverLevel().sendParticles(Echoes.SPIRIT, base.x, base.y + 0.2, base.z, 30, 1.2, 0.1, 1.2, 0.02);
		// A summoning circle burns where they rise
		Fx.mark(player.serverLevel(), base, null, 3.0f, 40, SPIRIT_RGB, Fx.RIPPLES);
		Fx.burst(player.serverLevel(), base.add(0, 1, 0), 2.5f, 8, SPIRIT_RGB);
		Fx.aurora(player.serverLevel(), base, 2.4f, 50, SPIRIT_RGB, GOLD_RGB);
		// The souls rise with a blow: monsters standing where they come up are thrown back
		for (LivingEntity m : player.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(base, base).inflate(4),
				e -> e instanceof Enemy && e.isAlive() && !Echoes.isEcho(e))) {
			m.hurt(player.serverLevel().damageSources().indirectMagic(player, player), 4);
			Vec3 away = m.position().subtract(base);
			m.knockback(0.9, -away.x, -away.z);
		}
		if (summoned < missing.size()) {
			player.displayClientMessage(Component.translatable("mages_legacy.echo.partial", summoned, missing.size(),
					Component.translatable(lackVoice ? "mages_legacy.echo.lack_voice" : "mages_legacy.echo.lack_room")), true);
		}
		cooldown(player, data, 0, CALL_COOLDOWN);
		return AbilityResult.OK;
	}

	/** Bestiary: one more (+1) or one less (-1) of a learned echo in a squad. */
	public static boolean editPreset(RaceData data, int index, String id, int delta) {
		if (index < 0 || index >= data.shepherd.presets.size()) {
			return false;
		}
		Optional<EchoType> type = EchoType.byId(id);
		if (type.isEmpty() || (delta > 0 && !learned(data, type.get()))) {
			return false;
		}
		Map<String, Integer> preset = data.shepherd.presets.get(index);
		int total = preset.values().stream().mapToInt(Integer::intValue).sum();
		int now = preset.getOrDefault(id, 0);
		int next = Math.max(0, now + Integer.signum(delta));
		if (next > now && total >= PRESET_MAX) {
			return false;
		}
		if (next == 0) {
			preset.remove(id);
		} else {
			preset.put(id, next);
		}
		data.dirty = true;
		return true;
	}

	/** Makes squad {@code index} the one the Call brings, and says what it holds. */
	public static void selectPreset(ServerPlayer player, RaceData data, int index) {
		data.shepherd.activePreset = Math.floorMod(index, data.shepherd.presets.size());
		data.dirty = true;
		MutableComponent list = Component.empty();
		data.shepherd.presets.get(data.shepherd.activePreset).forEach((id, count) -> EchoType.byId(id).ifPresent(type -> {
			if (!list.getSiblings().isEmpty()) {
				list.append(", ");
			}
			list.append(type.type().getDescription()).append(" ×" + count);
		}));
		player.displayClientMessage(Component.translatable("mages_legacy.echo.squad", ROMAN[data.shepherd.activePreset],
				list.getSiblings().isEmpty() ? Component.translatable("mages_legacy.echo.squad_empty") : list).withStyle(ChatFormatting.AQUA), true);
	}

	public static final String[] ROMAN = {"I", "II", "III"};

	/**
	 * Rally: every creature within 24 blocks grows stronger and faster for 10 seconds (Loud Rally adds resistance)
	 * and heals; the players around heal 2 hearts.
	 */
	public static AbilityResult rally(ServerPlayer player, RaceData data) {
		if (!ready(player, data, 1)) {
			return AbilityResult.COOLDOWN;
		}
		if (data.resource < RALLY_COST) {
			return AbilityResult.NO_RESOURCE;
		}
		data.addResource(-RALLY_COST);
		cooldown(player, data, 1, RALLY_COOLDOWN);
		ServerLevel world = player.serverLevel();
		boolean loud = data.has(Talents.VOICE_RALLY);
		// The horn heals the pack a lot (30% of their health, half with Warm Hands, which also cleanse them) and the
		// players around only a little
		boolean warm = data.has(Talents.VOICE_MEND);
		for (LivingEntity c : Creatures.around(player, 24)) {
			c.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, loud ? 1 : 0, false, true));
			c.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 0, false, true));
			c.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, loud ? 1 : 0, false, true));
			c.heal(c.getMaxHealth() * (warm ? RALLY_HEAL_WARM : RALLY_HEAL));
			if (warm) {
				c.getActiveEffects().stream().filter(e -> !e.getEffect().value().isBeneficial()).map(MobEffectInstance::getEffect).toList()
						.forEach(c::removeEffect);
			}
			world.sendParticles(ParticleTypes.ANGRY_VILLAGER, c.getX(), c.getY() + c.getBbHeight() + 0.3, c.getZ(), 1, 0, 0, 0, 0);
			world.sendParticles(ParticleTypes.HEART, c.getX(), c.getY() + c.getBbHeight() + 0.2, c.getZ(), 2, 0.3, 0.2, 0.3, 0);
			// A golden thread of the horn's call to each one of the pack
			Fx.tendril(world, player, c, 18, GOLD_RGB, SPIRIT_RGB);
		}
		for (ServerPlayer p : world.getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(24),
				p -> p.isAlive() && !p.isSpectator())) {
			p.heal(4);
		}
		world.playSound(null, player.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 0.8f, 1.3f);
		// The horn's call rolls out as a golden wave to the edge of its reach, a spirit one just behind
		Fx.ring(world, player.position(), 0.5f, 24, 18, GOLD_RGB);
		Fx.ring(world, player.position(), 0.3f, 16, 22, SPIRIT_RGB);
		world.sendParticles(Echoes.SPIRIT, player.getX(), player.getY() + 1.6, player.getZ(), 30, 1.0, 0.4, 1.0, 0.05);
		Fx.burst(world, player.position().add(0, 1, 0), 3.0f, 8, GOLD_RGB);
		Fx.aurora(world, player.position(), 4.0f, 70, GOLD_RGB, SPIRIT_RGB);
		Fx.mark(world, player.position(), player, 2.5f, 30, GOLD_RGB, Fx.RIPPLES);
		Fx.shake(player, 1.5f, 10);
		return AbilityResult.OK;
	}

	/**
	 * The Army of Oblivion (Alt, the Legion's crown): one echo of each of the six mightiest kinds learned (by the voice
	 * their Call costs) rises at once for 30 seconds, outside the bond capacity.
	 */
	public static AbilityResult army(ServerPlayer player, RaceData data) {
		if (!ready(player, data, ARMY_SLOT)) {
			return AbilityResult.COOLDOWN;
		}
		List<EchoType> mightiest = EchoType.ALL.stream().filter(type -> learned(data, type))
				.sorted((a, b) -> Integer.compare(b.voice(), a.voice())).limit(ARMY_KINDS).toList();
		if (mightiest.isEmpty()) {
			return AbilityResult.NO_ECHO;
		}
		if (data.resource < ARMY_COST) {
			return AbilityResult.NO_RESOURCE;
		}
		data.addResource(-ARMY_COST);
		cooldown(player, data, ARMY_SLOT, ARMY_COOLDOWN);
		mightiest.forEach(type -> Echoes.summon(player, data, type, Creatures.near(player), true, 600, 0));
		ServerLevel world = player.serverLevel();
		// Everyone ever beaten rises at once: a spirit wave, the horn's gold behind it
		Fx.ring(world, player.position(), 0.5f, 14, 22, SPIRIT_RGB);
		Fx.aurora(world, player.position(), 5.0f, 90, SPIRIT_RGB, GOLD_RGB);
		Fx.burst(world, player.position().add(0, 1, 0), 3.5f, 10, SPIRIT_RGB);
		Fx.shake(player, 2.0f, 12);
		world.sendParticles(Echoes.SPIRIT, player.getX(), player.getY() + 1, player.getZ(), 60, 2.5, 0.8, 2.5, 0.04);
		world.playSound(null, player.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 1.0f, 0.6f);
		world.playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 2.0f, 0.5f);
		return AbilityResult.OK;
	}

	/**
	 * The Gather, part of the Whistle's "Follow": your creatures within 64 blocks come to your side, at most once in
	 * 8 seconds. Returns whether anyone came.
	 */
	private static boolean gather(ServerPlayer player, RaceData data) {
		ServerLevel world = player.serverLevel();
		if (!ready(player, data, GATHER_SLOT)) {
			return false;
		}
		// Beasts told to stay keep their post, and those close by simply turn
		List<LivingEntity> creatures = Creatures.around(player, 64);
		creatures.removeIf(c -> c.getTags().contains(Creatures.STAY_TAG) || c.distanceToSqr(player) < 8 * 8);
		if (creatures.isEmpty()) {
			return false;
		}
		for (int i = 0; i < creatures.size(); i++) {
			LivingEntity c = creatures.get(i);
			world.sendParticles(Echoes.SPIRIT, c.getX(), c.getY() + 1, c.getZ(), 10, 0.3, 0.5, 0.3, 0);
			Vec3 spot = Echoes.around(world, Creatures.near(player), i);
			c.moveTo(spot.x, spot.y, spot.z, c.getYRot(), c.getXRot());
			if (c instanceof Mob mob) {
				mob.setTarget(null);
				mob.getNavigation().stop();
			}
			Fx.tendril(world, c, player, 12, SPIRIT_RGB, GOLD_RGB);
		}
		world.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7f, 1.2f);
		Fx.ring(world, player.position(), 10, 1, 12, SPIRIT_RGB);
		Fx.helix(world, player, 2.0f, 2.2f, 3, true, 14, SPIRIT_RGB);
		cooldown(player, data, GATHER_SLOT, GATHER_COOLDOWN);
		return true;
	}

	/**
	 * The Whistle: the pack's order changes, Follow (close, fighting only back; the far ones come to your side at once),
	 * Guard (in a ring around you, picking off what comes near, taking less damage) or Hunt (going after every monster
	 * within 24 blocks). It costs no voice.
	 */
	public static AbilityResult whistle(ServerPlayer player, RaceData data) {
		ServerLevel world = player.serverLevel();
		if (!ready(player, data, 2)) {
			return AbilityResult.COOLDOWN;
		}
		ShepherdData shepherd = data.shepherd;
		shepherd.stance = (shepherd.stance + 1) % 3;
		data.dirty = true;
		cooldown(player, data, 2, WHISTLE_COOLDOWN);
		for (LivingEntity c : Creatures.around(player, 32)) {
			// Called back to heel: whatever they chased that isn't hurting anyone of ours is let go
			if (shepherd.stance == ShepherdData.FOLLOW && c instanceof Mob mob && !threatens(mob.getTarget(), player)) {
				mob.setTarget(null);
			}
			world.sendParticles(ParticleTypes.NOTE, c.getX(), c.getY() + c.getBbHeight() + 0.3, c.getZ(), 0, shepherd.stance / 2.0, 0, 0, 1);
		}
		float pitch = shepherd.stance == ShepherdData.HUNT ? 2.0f : shepherd.stance == ShepherdData.GUARD ? 1.6f : 1.2f;
		// The whistle's ripple: a quick small wave, coloured by the order (heel, guard, hunt)
		Fx.ring(world, player.position(), 0.3f, 7, 10, STANCE_RGB[shepherd.stance]);
		Fx.burst(world, player.getEyePosition(), 1.0f, 5, STANCE_RGB[shepherd.stance]);
		world.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.PLAYERS, 1.2f, pitch);
		world.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.PLAYERS, 1.0f, pitch * 0.85f);
		player.displayClientMessage(Component.translatable("mages_legacy.whistle." + shepherd.stance).withStyle(ChatFormatting.GOLD), true);
		if (shepherd.stance == ShepherdData.FOLLOW) {
			gather(player, data);
		}
		return AbilityResult.OK;
	}

	/** Whether this mob is fighting the Shepherd or one of its creatures. */
	private static boolean threatens(@Nullable LivingEntity mob, ServerPlayer owner) {
		if (!(mob instanceof Mob m) || !mob.isAlive()) {
			return false;
		}
		LivingEntity target = m.getTarget();
		return target == owner || (target != null && Creatures.isCreatureOf(target, owner.getUUID()));
	}

	/**
	 * The Whistle's order, once a second. Hunt: idle creatures go for the nearest monster within 24 blocks. Guard:
	 * monsters coming for the Shepherd are met by the nearest idle creature. Follow: whatever a creature chases far
	 * from the Shepherd without being a threat is let go.
	 */
	private static void stance(ServerPlayer player, RaceData data, List<LivingEntity> near) {
		int stance = data.shepherd.stance;
		ServerLevel world = player.serverLevel();
		if (stance == ShepherdData.FOLLOW) {
			for (LivingEntity c : near) {
				if (c instanceof Mob mob && mob.getTarget() != null && mob.getTarget().distanceToSqr(player) > 64
						&& !threatens(mob.getTarget(), player)) {
					mob.setTarget(null);
				}
			}
			return;
		}
		double radius = Creatures.huntRadius(stance);
		List<Mob> foes = world.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(stance == ShepherdData.GUARD ? 12 : radius),
				e -> e.isAlive() && e instanceof Enemy && Creatures.ownerOf(e) == null
						&& (stance == ShepherdData.HUNT || e.getTarget() == player || e.distanceToSqr(player) < radius * radius));
		if (foes.isEmpty()) {
			return;
		}
		for (LivingEntity c : near) {
			if (c instanceof Mob mob && (mob.getTarget() == null || !mob.getTarget().isAlive())
					&& mob.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
				Mob nearest = null;
				for (Mob foe : foes) {
					if (nearest == null || foe.distanceToSqr(mob) < nearest.distanceToSqr(mob)) {
						nearest = foe;
					}
				}
				mob.setTarget(nearest);
			}
		}
	}

	/** Loss's Echo: one of your creatures fell (an echo faded in battle, a beast went to rest). */
	public static void onLoss(ServerPlayer owner, RaceData data) {
		if (data.has(Talents.VOICE_LOSS)) {
			owner.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 300, 0, true, true, true));
			data.addResource(10);
		}
	}

	@Nullable
	static LivingEntity lookedAt(ServerPlayer player, double range, java.util.function.Predicate<LivingEntity> filter) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0f);
		Vec3 end = eye.add(look.scale(range));
		AABB box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
				e -> e instanceof LivingEntity living && living.isAlive() && e != player && filter.test(living), range * range);
		return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
	}

	// ---- experience and the bestiary ----

	/**
	 * Someone killed something. A Shepherd's own kills and its creatures' kills fill the bestiary; creatures' kills
	 * also give race experience (and a bonded beast its own), and with Harvest some voice.
	 */
	public static void onKill(Entity killer, LivingEntity victim) {
		if (killer.getServer() == null) {
			return;
		}
		UUID ownerId = killer instanceof ServerPlayer p ? p.getUUID() : Creatures.ownerOf(killer);
		ServerPlayer owner = ownerId == null ? null : killer.getServer().getPlayerList().getPlayer(ownerId);
		if (owner == null || !is(owner) || Creatures.ownerOf(victim) != null) {
			return;
		}
		RaceData data = RaceState.of(owner);
		EchoType.of(victim.getType()).ifPresent(type -> countKill(owner, data, type));
		if (!(victim instanceof Enemy)) {
			return;
		}
		Bonds.onHunt(owner, data, victim);
		// Every bonded beast near the fight learns from it; the one that struck the blow learns most
		for (ShepherdData.Bond bond : data.shepherd.bonds) {
			if (!bond.resting() && owner.serverLevel().getEntity(bond.entity) instanceof Mob beast && beast.isAlive()
					&& beast.distanceToSqr(victim) < 16 * 16) {
				Bonds.beastXp(owner, data, bond, beast, beast == killer ? 10 : 4);
			}
		}
		if (killer == owner) {
			return;
		}
		Progression.addXp(owner, data, Progression.HUNT, CREATURE_KILL_XP, Progression.HUNT_CAP);
		if (Echoes.isEcho(killer) && data.has(Talents.LEGION_LEECH)) {
			data.addResource(5);
		}
	}

	public static void countKill(ServerPlayer owner, RaceData data, EchoType type) {
		boolean before = learned(data, type);
		data.shepherd.kills.merge(type.id(), 1, Integer::sum);
		data.dirty = true;
		if (!before && learned(data, type)) {
			// The first echo learned goes straight into the first squad, so the Call works at once
			if (data.shepherd.presets.stream().allMatch(Map::isEmpty)) {
				data.shepherd.presets.get(0).put(type.id(), 1);
				data.shepherd.selectedEcho = type.id();
			}
			owner.displayClientMessage(Component.translatable("mages_legacy.echo.learned", type.type().getDescription()).withStyle(ChatFormatting.AQUA), false);
			owner.level().playSound(null, owner.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.5f, 0.6f);
			Progression.addXp(owner, data, ECHO_LEARNED_XP);
		}
	}


	/** What the client's HUD and bestiary need beyond the common sync. */
	public static CompoundTag clientInfo(ServerPlayer player, RaceData data) {
		CompoundTag nbt = data.shepherd.toNbt();
		nbt.remove("rituals");
		ListTag bonds = nbt.getList("bonds", 10);
		long time = player.serverLevel().getGameTime();
		for (int i = 0; i < bonds.size(); i++) {
			CompoundTag b = bonds.getCompound(i);
			b.remove("body");
			b.putInt("restSeconds", (int) Math.max(0, (b.getLong("restingUntil") - time) / 20));
			b.putInt("fedSeconds", (int) Math.max(0, (b.getLong("fedUntil") - time) / 20));
			b.putBoolean("petReady", b.getLong("pettedAt") == 0 || time - b.getLong("pettedAt") >= Bonds.PET_GAP);
		}
		nbt.putInt("capUsed", capacityUsed(data, player.getUUID()));
		nbt.putInt("capMax", capacityMax(player, data));
		nbt.putInt("echoes", Echoes.count(player.getUUID()));
		CompoundTag alive = new CompoundTag();
		Echoes.countByType(player.getUUID()).forEach(alive::putInt);
		nbt.put("alive", alive);
		ListTag rituals = new ListTag();
		long now = player.serverLevel().getGameTime();
		data.shepherd.rituals.forEach((id, r) -> {
			if (r.listenUntil > now && !r.kind.isEmpty()) {
				CompoundTag e = new CompoundTag();
				e.putString("kind", r.kind);
				e.putInt("progress", r.feedings);
				e.putInt("minutes", (int) ((r.listenUntil - now) / 1200));
				rituals.add(e);
			}
		});
		nbt.put("rituals", rituals);
		return nbt;
	}
}
