package euf.mageslegacy.mercenary;

import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.race.AbilityResult;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceData;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Mercenaries: they served the nameless mage for pay, and he paid them in power on his own terms. Nothing comes
 * free: their abilities cost blood, hunger and the armour on their backs, and they grow only by the contracts of the
 * Mage's Shop (H). Strike on Credit (R) pays health for a doubled blow, the Exchange (V) trades effects and life with
 * an enemy, the Pledge (Z) pawns the armour for safety, the Double Stake (Alt, crown) takes power now and pays later.
 */
public final class Mercenary {
	/** Copper and the gold of coins; copper laid on as real colour under the glow (see {@link Fx#INK}). */
	public static final int COPPER_RGB = 0xC8823C;
	public static final int GOLD_RGB = 0xF2C94C;
	private static final int COPPER_INK = Fx.INK | 0x8A4E22;

	public static final int STRIKE_HEALTH = 3;
	public static final int STRIKE_HEALTH_CHEAP = 2;
	private static final int STRIKE_COOLDOWN = 160;
	private static final int STRIKE_WINDOW = 120;
	/** Strike on Credit doubles a blow, but never adds more than this. */
	private static final float STRIKE_MAX_BONUS = 10;
	public static final int EXCHANGE_HUNGER = 3;
	private static final int EXCHANGE_COOLDOWN = 400;
	private static final double EXCHANGE_RANGE = 12;
	private static final int PLEDGE_COOLDOWN = 600;
	private static final int PLEDGE_TICKS = 160;
	private static final int PLEDGE_TICKS_LONG = 240;
	private static final float PLEDGE_WEAR = 0.04f;
	private static final int STAKE_COOLDOWN = 6000;
	private static final int STAKE_TICKS = 300;
	private static final int SECOND_WIND_SLOT = 8;
	private static final int SECOND_WIND_COOLDOWN = 1200;
	private static final int RANSOM_SLOT = 9;
	private static final int RANSOM_COOLDOWN = 24000;
	public static final int RANSOM_TOKENS = 30;

	private static final net.minecraft.resources.ResourceLocation HIDE_ARMOR = euf.mageslegacy.MagesLegacy.id("attribute/mercenary/hide_armor");
	private static final net.minecraft.resources.ResourceLocation VETERAN_ARMOR = euf.mageslegacy.MagesLegacy.id("attribute/mercenary/veteran_armor");
	private static final net.minecraft.resources.ResourceLocation VETERAN_TOUGHNESS = euf.mageslegacy.MagesLegacy.id("attribute/mercenary/veteran_toughness");
	private static final net.minecraft.resources.ResourceLocation MARCH_SPEED = euf.mageslegacy.MagesLegacy.id("attribute/mercenary/march_speed");
	private static final net.minecraft.resources.ResourceLocation IRON_KNOCKBACK = euf.mageslegacy.MagesLegacy.id("attribute/mercenary/iron_knockback");

	/** Strike on Credit: blows left in the charge, and until when it holds. */
	private record Strike(int blows, long until) {}

	private static final Map<UUID, Strike> STRIKES = new HashMap<>();
	private static final Map<UUID, Long> PLEDGES = new HashMap<>();
	private static final Map<UUID, Long> STAKES = new HashMap<>();
	/** The last creature hit by a Strike on Credit (Greed: killing it with that blow pays the cooldown back). */
	private static final Map<UUID, UUID> STRUCK = new HashMap<>();

	private Mercenary() {
	}

	public static void reset() {
		STRIKES.clear();
		PLEDGES.clear();
		STAKES.clear();
		STRUCK.clear();
	}

	public static boolean is(Player player) {
		return player instanceof ServerPlayer sp && RaceState.raceOf(sp).orElse(null) == Race.MERCENARY;
	}

	private static long now(MinecraftServer server) {
		return server.getTickCount();
	}

	private static boolean ready(ServerPlayer player, RaceData data, int slot) {
		return data.readyAt.getOrDefault(slot, 0L) <= now(player.getServer());
	}

	/** Out of work, out of nerve: with no contract in hand the abilities take half as long again to come back. */
	private static void cooldown(ServerPlayer player, RaceData data, int slot, int ticks) {
		if (data.merc.active.isEmpty()) {
			ticks = ticks * 3 / 2;
		}
		data.readyAt.put(slot, now(player.getServer()) + ticks);
		data.dirty = true;
	}

	// ---- once a second ----

	public static void tickSecond(ServerPlayer player, RaceData data) {
		Contracts.tickSecond(player, data);
		// The race bar shows the work: how far along the first contract in hand is
		float shown = 0;
		if (!data.merc.active.isEmpty()) {
			Contract c = data.merc.active.get(0);
			shown = 100f * c.progress / c.amount;
		}
		if ((int) shown != (int) data.resource) {
			data.resource = shown;
			data.dirty = true;
		}
		modifier(player, Attributes.ARMOR, HIDE_ARMOR, "merc_hide", 2, data.has(Talents.CRAFT_HIDE));
		modifier(player, Attributes.ARMOR, VETERAN_ARMOR, "merc_veteran", 2, data.has(Talents.CRAFT_VETERAN));
		modifier(player, Attributes.ARMOR_TOUGHNESS, VETERAN_TOUGHNESS, "merc_veteran_toughness", 2, data.has(Talents.CRAFT_VETERAN));
		modifier(player, Attributes.KNOCKBACK_RESISTANCE, IRON_KNOCKBACK, "merc_iron", 0.5, data.has(Talents.CRAFT_IRON));
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null && data.has(Talents.CRAFT_MARCH) != (speed.getModifier(MARCH_SPEED) != null)) {
			if (data.has(Talents.CRAFT_MARCH)) {
				speed.addTransientModifier(new AttributeModifier(MARCH_SPEED, 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			} else {
				speed.removeModifier(MARCH_SPEED);
			}
		}
		// Tip-off: the creatures a hunt is after glow for the Mercenary
		if (data.has(Talents.CON_SCOUT)) {
			ServerLevel world = player.serverLevel();
			for (LivingEntity mob : world.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(32),
					e -> e.isAlive() && hunted(data, e))) {
				mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, true, false));
			}
		}
		if (data.has(Talents.CRAFT_LEGEND) && !data.merc.active.isEmpty()) {
			refresh(player, MobEffects.DAMAGE_RESISTANCE, 0);
		}
	}

	private static void refresh(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int amplifier) {
		MobEffectInstance current = player.getEffect(effect);
		if (current == null || current.getDuration() <= 30) {
			player.addEffect(new MobEffectInstance(effect, 60, amplifier, true, false, true));
		}
	}

	/** Adds or removes a talent's attribute bonus so it matches whether the talent is learnt. */
	private static void modifier(ServerPlayer player, net.minecraft.core.Holder<Attribute> attribute, net.minecraft.resources.ResourceLocation id, String name, double amount, boolean want) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null || want == (instance.getModifier(id) != null)) {
			return;
		}
		if (want) {
			instance.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
		} else {
			instance.removeModifier(id);
		}
	}

	/** Takes the talents' attribute bonuses off (the race was reset or changed). */
	public static void cleanup(ServerPlayer player) {
		modifier(player, Attributes.ARMOR, HIDE_ARMOR, "merc_hide", 2, false);
		modifier(player, Attributes.ARMOR, VETERAN_ARMOR, "merc_veteran", 2, false);
		modifier(player, Attributes.ARMOR_TOUGHNESS, VETERAN_TOUGHNESS, "merc_veteran_toughness", 2, false);
		modifier(player, Attributes.KNOCKBACK_RESISTANCE, IRON_KNOCKBACK, "merc_iron", 0.5, false);
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(MARCH_SPEED);
		}
		STRIKES.remove(player.getUUID());
		PLEDGES.remove(player.getUUID());
		STAKES.remove(player.getUUID());
	}

	/** Whether a creature is what one of the Mercenary's hunts is after. */
	public static boolean hunted(RaceData data, LivingEntity e) {
		String id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
		return data.merc.active.stream().anyMatch(c -> c.kind == Contract.Kind.HUNT && c.target.equals(id));
	}

	// ---- every tick ----

	public static void tick(MinecraftServer server) {
		long now = now(server);
		STRIKES.values().removeIf(s -> s.until() < now);
		PLEDGES.values().removeIf(until -> until < now);
		// The Double Stake comes due: a fifth of the health left, never the last of it
		STAKES.entrySet().removeIf(e -> {
			if (e.getValue() > now) {
				return false;
			}
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			if (player != null && player.isAlive()) {
				player.setHealth(Math.max(1, player.getHealth() * 0.8f));
				ServerLevel world = player.serverLevel();
				Fx.burst(world, player.position().add(0, 1, 0), 1.6f, 8, COPPER_INK);
				Fx.pulse(player, 1, 16, COPPER_RGB);
				world.playSound(null, player.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1.0f, 0.6f);
				player.displayClientMessage(Component.translatable("mages_legacy.merc.stake_due").withStyle(ChatFormatting.GOLD), true);
			}
			return true;
		});
	}

	// ---- abilities ----

	public static AbilityResult use(ServerPlayer player, RaceData data, int slot) {
		return switch (slot) {
			case 0 -> strike(player, data);
			case 1 -> exchange(player, data);
			case 2 -> pledge(player, data);
			case 4 -> data.has(Talents.DEAL_STAKE) ? stake(player, data) : RaceData.talentLocked(player, Talents.DEAL_STAKE);
			// The Shop opens on the client; a press that reaches the server does nothing
			default -> AbilityResult.HINTED;
		};
	}

	public static int strikeHealth(RaceData data) {
		return data.has(Talents.DEAL_CHEAP) ? STRIKE_HEALTH_CHEAP : STRIKE_HEALTH;
	}

	/** Strike on Credit (R): pay health now, and the next blow within 6 seconds lands twice as hard. */
	public static AbilityResult strike(ServerPlayer player, RaceData data) {
		if (!ready(player, data, 0)) {
			return AbilityResult.COOLDOWN;
		}
		int price = strikeHealth(data);
		// The price may never be the last of one's blood
		if (player.getHealth() <= price + 2) {
			return AbilityResult.NO_RESOURCE;
		}
		player.setHealth(player.getHealth() - price);
		cooldown(player, data, 0, STRIKE_COOLDOWN);
		STRIKES.put(player.getUUID(), new Strike(data.has(Talents.DEAL_DOUBLE) ? 2 : 1, now(player.getServer()) + STRIKE_WINDOW));
		ServerLevel world = player.serverLevel();
		// Blood bought on credit: a few coins tossed up and the seal of the debt flickering at the hand
		Fx.coins(world, player.position().add(0, 1.2, 0), 4, 0.6f, 24);
		Fx.aura(world, player, STRIKE_WINDOW, GOLD_RGB, Fx.MOTES);
		Fx.pulse(player, 1, 14, 0xB01226);
		world.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.PLAYERS, 1.0f, 0.7f);
		world.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 0.6f);
		return AbilityResult.OK;
	}

	/**
	 * The Exchange (V): with the creature you look at (12 blocks) you trade what you carry: your harmful effects go to
	 * it, its good ones come to you, and some of its life becomes your absorption. It costs hunger.
	 */
	public static AbilityResult exchange(ServerPlayer player, RaceData data) {
		if (!ready(player, data, 1)) {
			return AbilityResult.COOLDOWN;
		}
		boolean free = data.has(Talents.DEAL_FREE);
		if (!free && player.getFoodData().getFoodLevel() < EXCHANGE_HUNGER + 3) {
			return AbilityResult.NO_RESOURCE;
		}
		LivingEntity target = lookedAt(player, EXCHANGE_RANGE);
		if (target == null) {
			return AbilityResult.NO_SIGHT;
		}
		if (!free) {
			player.getFoodData().setFoodLevel(player.getFoodData().getFoodLevel() - EXCHANGE_HUNGER);
		}
		cooldown(player, data, 1, EXCHANGE_COOLDOWN);
		List<MobEffectInstance> mine = new ArrayList<>();
		for (MobEffectInstance e : player.getActiveEffects()) {
			if (!e.getEffect().value().isBeneficial() && !e.isAmbient()) {
				mine.add(e);
			}
		}
		List<MobEffectInstance> theirs = new ArrayList<>();
		for (MobEffectInstance e : target.getActiveEffects()) {
			if (e.getEffect().value().isBeneficial()) {
				theirs.add(e);
			}
		}
		for (MobEffectInstance e : mine) {
			player.removeEffect(e.getEffect());
			target.addEffect(new MobEffectInstance(e));
		}
		for (MobEffectInstance e : theirs) {
			target.removeEffect(e.getEffect());
			player.addEffect(new MobEffectInstance(e));
		}
		float drain = data.has(Talents.DEAL_SHARP) ? 6 : 4;
		ServerLevel world = player.serverLevel();
		target.hurt(world.damageSources().indirectMagic(player, player), drain);
		player.setAbsorptionAmount(Math.min(8, player.getAbsorptionAmount() + drain));
		if (data.has(Talents.DEAL_CURSE)) {
			target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0));
			target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 0));
		}
		// The trade: what was hers goes out along one arc, what was the creature's comes back along the other
		Fx.exchange(world, player, target, 16, GOLD_RGB, 0xC0303A);
		world.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_TRADE, SoundSource.PLAYERS, 1.0f, 0.6f);
		world.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 0.8f);
		return AbilityResult.OK;
	}

	/** The Pledge (Z): the armour is pawned for a while: Resistance II, and every piece worn pays with a little of its wear. */
	public static AbilityResult pledge(ServerPlayer player, RaceData data) {
		if (!ready(player, data, 2)) {
			return AbilityResult.COOLDOWN;
		}
		List<EquipmentSlot> worn = new ArrayList<>();
		for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
			if (player.getItemBySlot(slot).getItem() instanceof ArmorItem) {
				worn.add(slot);
			}
		}
		if (worn.isEmpty()) {
			player.displayClientMessage(Component.translatable("mages_legacy.merc.no_armor"), true);
			return AbilityResult.HINTED;
		}
		cooldown(player, data, 2, PLEDGE_COOLDOWN);
		if (!data.has(Talents.DEAL_BUYOUT)) {
			for (EquipmentSlot slot : worn) {
				ItemStack stack = player.getItemBySlot(slot);
				if (stack.isDamageableItem()) {
					stack.hurtAndBreak(Math.max(1, Math.round(stack.getMaxDamage() * PLEDGE_WEAR)), player, slot);
				}
			}
		}
		int ticks = data.has(Talents.DEAL_LONG) ? PLEDGE_TICKS_LONG : PLEDGE_TICKS;
		player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 1, true, false, true));
		PLEDGES.put(player.getUUID(), now(player.getServer()) + ticks);
		ServerLevel world = player.serverLevel();
		// The pawnbroker's seal comes down on the body, and its rim turns at the waist for as long as the pledge holds
		Fx.seal(world, player, 1.1f, ticks, GOLD_RGB);
		Fx.shake(player, 1.0f, 6);
		world.playSound(null, player.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.5f, 1.4f);
		world.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 1.0f, 0.8f);
		return AbilityResult.OK;
	}

	/** The Double Stake (Alt, the Deals' crown): 15 seconds of blows half again as hard that heal a quarter; then a fifth of the health is paid. */
	public static AbilityResult stake(ServerPlayer player, RaceData data) {
		if (!ready(player, data, 4)) {
			return AbilityResult.COOLDOWN;
		}
		cooldown(player, data, 4, STAKE_COOLDOWN);
		STAKES.put(player.getUUID(), now(player.getServer()) + STAKE_TICKS);
		ServerLevel world = player.serverLevel();
		// All on one toss: a great coin flies up spinning over the head, comes down, and gold spills everywhere
		Fx.flip(world, player, 28, GOLD_RGB);
		Fx.coins(world, player.position().add(0, 1.6, 0), 16, 1.2f, 40);
		Fx.aura(world, player, STAKE_TICKS, GOLD_RGB, Fx.MOTES);
		Fx.fov(player, 0.15f, 14);
		Fx.shake(player, 1.5f, 10);
		world.playSound(null, player.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0f, 0.7f);
		world.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 0.6f);
		return AbilityResult.OK;
	}

	private static LivingEntity lookedAt(ServerPlayer player, double range) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0f);
		Vec3 end = eye.add(look.scale(range));
		AABB box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
				e -> e instanceof LivingEntity living && living.isAlive() && e != player && !(e instanceof Player), range * range);
		return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
	}

	// ---- damage ----

	/**
	 * A Mercenary's blow: Strike on Credit doubles it, the Double Stake adds half and drinks a quarter, and the Craft
	 * and the work add up: the contract's quarry, a firm grip, a bow, the finishing blow, a pack on one's back.
	 */
	public static float onHit(LivingEntity victim, DamageSource source, float amount) {
		if (!(source.getEntity() instanceof ServerPlayer player) || victim == player || !is(player) || amount <= 0) {
			return amount;
		}
		RaceData data = RaceState.of(player);
		long now = now(player.getServer());
		boolean melee = source.is(DamageTypes.PLAYER_ATTACK) && source.getDirectEntity() == player;
		boolean projectile = source.is(DamageTypeTags.IS_PROJECTILE);
		float bonus = 0;
		if (hunted(data, victim)) {
			bonus += data.has(Talents.CRAFT_MARK) ? 0.25f : 0.1f;
		}
		if (melee && data.has(Talents.CRAFT_GRIP)) {
			bonus += 0.1f;
		}
		if (projectile && data.has(Talents.CRAFT_RANGED)) {
			bonus += 0.2f;
		}
		if (data.has(Talents.CRAFT_EXECUTE) && victim instanceof Enemy && victim.getHealth() < victim.getMaxHealth() * 0.3f) {
			bonus += 0.3f;
		}
		if (data.has(Talents.CRAFT_WOLF) && hunters(player) >= 3) {
			bonus += 0.2f;
		}
		if (data.has(Talents.CRAFT_LEGEND) && !data.merc.active.isEmpty()) {
			bonus += 0.15f;
		}
		boolean staking = STAKES.getOrDefault(player.getUUID(), 0L) > now;
		if (staking && (melee || projectile)) {
			bonus += 0.5f;
		}
		// Everything that makes a blow harder adds up, to twice the blow at most (as the Bloodkin's)
		amount *= 1 + Math.min(1.0f, bonus);
		ServerLevel world = player.serverLevel();
		Strike strike = STRIKES.get(player.getUUID());
		if (melee && strike != null && strike.until() >= now) {
			float extra = Math.min(STRIKE_MAX_BONUS, amount);
			amount += extra;
			if (strike.blows() > 1) {
				STRIKES.put(player.getUUID(), new Strike(strike.blows() - 1, strike.until()));
			} else {
				STRIKES.remove(player.getUUID());
			}
			STRUCK.put(player.getUUID(), victim.getUUID());
			if (data.has(Talents.DEAL_INTEREST)) {
				player.heal(extra * 0.3f);
			}
			// The debt paid out in one blow: a crossed copper cut and a spray of gold
			Vec3 center = victim.position().add(0, victim.getBbHeight() * 0.6, 0);
			Fx.slash(world, center, player.getViewVector(1.0f), 0.9f + victim.getBbWidth(), 35, 9, COPPER_INK);
			Fx.slash(world, center, player.getViewVector(1.0f), 0.9f + victim.getBbWidth(), 145, 9, GOLD_RGB);
			Fx.coins(world, center, 10, 1.0f, 32);
			Fx.shake(player, 1.4f, 6);
			world.playSound(null, victim.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 0.7f);
			world.playSound(null, victim.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.8f, 1.2f);
		}
		if (staking && melee) {
			player.heal(amount * 0.25f);
		}
		return amount;
	}

	/** How many mobs within 16 blocks are after this player. */
	private static int hunters(ServerPlayer player) {
		return player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(16),
				m -> m.isAlive() && m.getTarget() == player).size();
	}

	/** Blows on a Mercenary: the Iron Pledge gives a part back; Second Wind catches a fall below a third of the health. */
	public static float modifyDamage(ServerPlayer player, DamageSource source, float amount) {
		RaceData data = RaceState.of(player);
		if (data == null || amount <= 0) {
			return amount;
		}
		long now = now(player.getServer());
		if (data.has(Talents.DEAL_IRON) && PLEDGES.getOrDefault(player.getUUID(), 0L) > now && source.getEntity() instanceof LivingEntity attacker
				&& attacker != player && source.getDirectEntity() == attacker) {
			attacker.hurt(player.damageSources().thorns(player), amount * 0.3f);
		}
		if (data.has(Talents.CRAFT_SECOND) && player.getHealth() - amount < player.getMaxHealth() * 0.3f && player.getHealth() - amount > 0
				&& ready(player, data, SECOND_WIND_SLOT)) {
			data.readyAt.put(SECOND_WIND_SLOT, now + SECOND_WIND_COOLDOWN);
			player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 1, true, true, true));
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 80, 0, true, true, true));
		}
		return amount;
	}

	/** A creature the Mercenary killed: it counts for the hunts; a kill with a Strike on Credit pays back its cooldown (Greed). */
	public static void onKill(ServerPlayer player, LivingEntity victim) {
		if (!is(player)) {
			return;
		}
		RaceData data = RaceState.of(player);
		Contracts.onKill(player, data, victim);
		if (data.has(Talents.DEAL_GREED) && victim.getUUID().equals(STRUCK.remove(player.getUUID()))) {
			data.readyAt.remove(0);
			data.dirty = true;
		}
	}

	/** The Ransom (crown of the Contracts): a killing blow is bought off for 30 tokens, once in 20 minutes. */
	public static boolean allowDeath(ServerPlayer player) {
		RaceData data = RaceState.of(player);
		if (data == null || data.race != Race.MERCENARY || !data.has(Talents.CON_RANSOM) || data.merc.tokens < RANSOM_TOKENS
				|| !ready(player, data, RANSOM_SLOT)) {
			return true;
		}
		data.merc.tokens -= RANSOM_TOKENS;
		data.readyAt.put(RANSOM_SLOT, now(player.getServer()) + RANSOM_COOLDOWN);
		data.dirty = true;
		player.setHealth(player.getMaxHealth() * 0.4f);
		ServerLevel world = player.serverLevel();
		Fx.burst(world, player.position().add(0, 1, 0), 3.0f, 12, GOLD_RGB);
		Fx.pillar(world, player.position(), 0.8f, 4, 24, GOLD_RGB);
		Fx.screen(player, 24, GOLD_RGB);
		world.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6f, 1.3f);
		world.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_TRADE, SoundSource.PLAYERS, 1.0f, 0.5f);
		player.displayClientMessage(Component.translatable("mages_legacy.merc.ransom", RANSOM_TOKENS).withStyle(ChatFormatting.GOLD), false);
		return false;
	}

	/** The Mage's Shop is called: its door stands in the air before the Mercenary and opens on warm light, until it is left. */
	public static void openedShop(ServerPlayer player) {
		ServerLevel world = player.serverLevel();
		Vec3 look = player.getViewVector(1.0f).multiply(1, 0, 1);
		look = look.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : look.normalize();
		Fx.door(world, player.position().add(look.scale(3.0)), player.getYRot(), player, 6000, GOLD_RGB);
		world.playSound(null, player.blockPosition(), SoundEvents.WOODEN_DOOR_OPEN, SoundSource.PLAYERS, 1.0f, 0.6f);
		world.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.2f);
	}

	/** The Mercenary left the Shop: the door swings shut behind them. */
	public static void closedShop(ServerPlayer player) {
		ServerLevel world = player.serverLevel();
		Fx.doorClose(world, player);
		world.playSound(null, player.blockPosition(), SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.PLAYERS, 1.0f, 0.6f);
	}

	/** What the client's Shop screen needs: the account in the Shop and today's prices. */
	public static net.minecraft.nbt.CompoundTag clientInfo(ServerPlayer player, RaceData data) {
		Contracts.refresh(player, data);
		Shop.ensureStock(player, data);
		net.minecraft.nbt.CompoundTag nbt = data.merc.toNbt();
		nbt.putInt("slots", Contracts.slots(data));
		nbt.putInt("rerollPrice", Shop.discounted(data, Contracts.rerollPrice(data)));
		net.minecraft.nbt.CompoundTag prices = new net.minecraft.nbt.CompoundTag();
		for (Shop.Ware ware : Shop.CATALOG) {
			if (ware.kind() == Shop.Kind.GOODS && !data.merc.stock.contains(ware.id())) {
				continue;
			}
			prices.putInt(ware.id(), Shop.soldOut(data, ware) ? -1 : Progression.tierOpen(player, ware.tier()) ? Shop.price(data, ware) : -2);
		}
		nbt.put("prices", prices);
		nbt.putLong("nextDay", (Contracts.today(player) + 1) * Contracts.DAY - player.getServer().overworld().getGameTime());
		return nbt;
	}
}
