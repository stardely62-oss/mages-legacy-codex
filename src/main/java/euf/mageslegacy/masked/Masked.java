package euf.mageslegacy.masked;

import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.net.RacePackets;
import euf.mageslegacy.race.AbilityResult;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceData;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talents;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import euf.mageslegacy.platform.Network;
import euf.mageslegacy.platform.Network.Server;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Masked: the nameless mage gave them no face of their own, only the knack of taking others'. The Tearing (R)
 * takes the face off a weakened monster and puts it on: the wearer takes its shape, its gift and its weakness, and its
 * kind takes the wearer for one of its own. The face's Power (V) is that monster's own trick; the Change (Z) goes on
 * to the next face of the gallery; Kin (H) makes the wearer's kind a retinue; the Thousand Faces (Alt, crown) wears
 * them all at once. A worn face wears out, and without one the Masked are weaker than a plain human.
 */
public final class Masked {
	/** Pale bone of the faces, laid on as real colour (see {@link Fx#INK}) under a sickly green glint. */
	public static final int BONE_RGB = 0xE8DCC0;
	public static final int GLINT_RGB = 0xB8E07A;
	private static final int BONE_INK = Fx.INK | 0x3A332A;

	public static final int SWAP_COST = 10;
	public static final int SWAP_COST_CHEAP = 5;
	public static final int KIN_COST = 40;
	public static final int KIN_COST_KING = 25;
	public static final int THOUSAND_COST = 50;
	private static final int RIP_COOLDOWN = 60;
	private static final int SWAP_COOLDOWN = 40;
	private static final int SWAP_COOLDOWN_CHEAP = 20;
	private static final int KIN_COOLDOWN = 600;
	private static final int THOUSAND_COOLDOWN = 6000;
	private static final int THOUSAND_TICKS = 400;
	private static final int LAST_SLOT = 9;
	private static final int LAST_COOLDOWN = 24000;
	/** A worn face lasts ten minutes (fifteen with Long Wear); the gallery's others keep. */
	private static final float WEAR_PER_SECOND = 100f / 600;
	private static final float WEAR_PER_SECOND_LONG = 100f / 900;
	/** A face comes off a monster with at most this much of its life left (Weak Spot: more). */
	private static final float RIP_AT = 0.35f;
	private static final float RIP_AT_WEAK = 0.45f;

	private static final net.minecraft.resources.ResourceLocation FACELESS_HEALTH = euf.mageslegacy.MagesLegacy.id("attribute/masked/faceless_health");
	private static final net.minecraft.resources.ResourceLocation ZOMBIE_HEALTH = euf.mageslegacy.MagesLegacy.id("attribute/masked/zombie_health");
	private static final net.minecraft.resources.ResourceLocation WITCH_HEALTH = euf.mageslegacy.MagesLegacy.id("attribute/masked/witch_health");
	private static final net.minecraft.resources.ResourceLocation WITHER_SPEED = euf.mageslegacy.MagesLegacy.id("attribute/masked/wither_speed");

	/** Monsters of the wearer's kind that follow it for a while (Kin). */
	private record Retinue(UUID owner, long until) {}

	/** A face still felt for a while after the Change (Lingering Trace). */
	private record Trace(Face face, long until) {}

	/** Blows charged by the wither skeleton's Black Blade. */
	private record Blade(int charges, long until) {}

	/** Until when each Masked's kind sees through the face (it struck one of them). */
	private static final Map<UUID, Long> EXPOSED = new HashMap<>();
	private static final Map<UUID, Retinue> RETINUE = new HashMap<>();
	private static final Map<UUID, Long> THOUSAND = new HashMap<>();
	private static final Map<UUID, Trace> TRACE = new HashMap<>();
	private static final Map<UUID, Blade> BLADE = new HashMap<>();
	/** A face was put on or taken off since the last time everyone was told who wears what. */
	private static boolean facesChanged;

	private Masked() {
	}

	public static void reset() {
		EXPOSED.clear();
		RETINUE.clear();
		THOUSAND.clear();
		TRACE.clear();
		BLADE.clear();
		facesChanged = true;
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

	private static RaceData masked(Player player) {
		if (!(player instanceof ServerPlayer sp)) {
			return null;
		}
		RaceData data = RaceState.of(sp);
		return data != null && data.race == Race.MASKED ? data : null;
	}

	public static boolean thousand(ServerPlayer player) {
		return THOUSAND.getOrDefault(player.getUUID(), 0L) > now(player.getServer());
	}

	/** The faces whose gifts the Masked has now: the worn one, the whole gallery under the Thousand Faces, a lingering trace. */
	public static Set<Face> active(ServerPlayer player, RaceData data) {
		Set<Face> faces = EnumSet.noneOf(Face.class);
		if (data.masked.worn != null) {
			faces.add(data.masked.worn);
		}
		if (thousand(player)) {
			faces.addAll(data.masked.gallery.keySet());
		}
		Trace trace = TRACE.get(player.getUUID());
		if (trace != null && trace.until() > now(player.getServer()) && data.masked.gallery.containsKey(trace.face())) {
			faces.add(trace.face());
		}
		return faces;
	}

	public static boolean wears(Player player, Face face) {
		RaceData data = masked(player);
		return data != null && active((ServerPlayer) player, data).contains(face);
	}

	public static int gallerySize(RaceData data) {
		return 3 + (data.has(Talents.GAL_SLOTS) ? 1 : 0) + (data.has(Talents.GAL_MORE) ? 1 : 0);
	}

	public static int swapCost(RaceData data) {
		return data.has(Talents.GAL_CHEAP) ? SWAP_COST_CHEAP : SWAP_COST;
	}

	public static int kinCost(RaceData data) {
		return data.has(Talents.KIN_KING) ? KIN_COST_KING : KIN_COST;
	}

	public static int powerCost(ServerPlayer player, RaceData data) {
		return data.masked.worn == null ? 0 : thousand(player) ? 0 : data.masked.worn.cost;
	}

	// ---- the disguise ----

	/**
	 * Whether a monster leaves this player be: its own retinue never turns on it; the kind of a face it has on takes it
	 * for one of its own (unless it struck one of them lately); with Own Scent, other monsters notice it only closer.
	 */
	public static boolean hidesFrom(Mob mob, Player target) {
		RaceData data = masked(target);
		if (data == null) {
			return false;
		}
		ServerPlayer player = (ServerPlayer) target;
		Retinue retinue = RETINUE.get(mob.getUUID());
		if (retinue != null && retinue.owner().equals(player.getUUID())) {
			return true;
		}
		if (disguisedFrom(player, data, mob)) {
			return true;
		}
		if (data.has(Talents.KIN_SCENT) && data.masked.worn != null) {
			double range = mob.getAttributeValue(Attributes.FOLLOW_RANGE) * 0.6;
			return mob.distanceToSqr(player) > range * range;
		}
		return false;
	}

	private static boolean disguisedFrom(ServerPlayer player, RaceData data, Mob mob) {
		if (EXPOSED.getOrDefault(player.getUUID(), 0L) > now(player.getServer())) {
			return false;
		}
		Set<Face> faces = data.has(Talents.KIN_ALL) ? data.masked.gallery.keySet() : active(player, data);
		for (Face face : faces) {
			if (face.matches(mob.getType())) {
				return true;
			}
		}
		return false;
	}

	/** Endermen take an enderman's face for one of their own: meeting its eyes doesn't anger them. */
	public static boolean kin(Player player) {
		return wears(player, Face.ENDERMAN);
	}

	/** The spider's face climbs walls (the client asks about its own player, see {@code ClientRaceView}). */
	public static boolean climbs(Player player) {
		return wears(player, Face.SPIDER);
	}

	/** Monsters of the worn kind near the Masked that are after it now forget it. */
	private static void calmKin(ServerPlayer player, RaceData data) {
		for (Mob m : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(32),
				m -> m.isAlive() && m.getTarget() == player)) {
			if (hidesFrom(m, player)) {
				m.setTarget(null);
			}
		}
	}

	// ---- once a second ----

	public static void tickSecond(ServerPlayer player, RaceData data) {
		MaskData mask = data.masked;
		Face worn = mask.worn;
		if (worn != null) {
			// The face wears out while it's on; Kept Faces wear half as fast
			float wear = data.has(Talents.GAL_LONG) ? WEAR_PER_SECOND_LONG : WEAR_PER_SECOND;
			if (data.has(Talents.GAL_KEEP)) {
				wear /= 2;
			}
			float fresh = mask.gallery.getOrDefault(worn, 0f) - wear;
			if (fresh <= 0) {
				crumble(player, data, worn);
				worn = null;
			} else {
				mask.gallery.put(worn, fresh);
			}
		}
		// Essence flows while a face is on, more with a full gallery (Collector's Essence); none when faceless
		if (worn != null) {
			data.addResource(0.4f + (data.has(Talents.GAL_ESSENCE) ? 0.15f * mask.gallery.size() : 0));
		}
		data.dirty = true;

		Set<Face> faces = active(player, data);
		// The gifts
		modifier(player, Attributes.MAX_HEALTH, ZOMBIE_HEALTH, "masked_zombie", 4, faces.contains(Face.ZOMBIE));
		if (faces.contains(Face.DROWNED) && player.isInWater()) {
			refresh(player, MobEffects.WATER_BREATHING, 0);
			refresh(player, MobEffects.DOLPHINS_GRACE, 0);
		}
		if (faces.contains(Face.SPIDER)) {
			player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false, true));
		}
		if (faces.contains(Face.BLAZE)) {
			refresh(player, MobEffects.FIRE_RESISTANCE, 0);
		}
		if (faces.contains(Face.WITCH)) {
			player.removeEffect(MobEffects.POISON);
		}
		if (faces.contains(Face.WITHER_SKELETON)) {
			player.removeEffect(MobEffects.WITHER);
		}

		// The weaknesses: only the worn face's, and Borrowed Skin softens them
		boolean soft = data.has(Talents.KIN_SKIN);
		ServerLevel world = player.serverLevel();
		boolean sun = world.isDay() && !world.isRaining() && world.canSeeSky(BlockPos.containing(player.getEyePosition()))
				&& player.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && !player.isInWaterOrRain();
		if (sun && (worn == Face.ZOMBIE || worn == Face.SKELETON || worn == Face.DROWNED || worn == Face.PHANTOM)) {
			if (!soft || player.getRandom().nextBoolean()) {
				player.igniteForSeconds(soft ? 2 : 4);
			}
		}
		if ((worn == Face.ENDERMAN || worn == Face.BLAZE) && player.isInWaterOrRain()) {
			player.hurt(world.damageSources().drown(), soft ? 0.5f : 1.0f);
		}
		if (worn == Face.CREEPER && !world.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(soft ? 5 : 8),
				e -> e.isAlive() && (e instanceof Cat || e instanceof Ocelot)).isEmpty()) {
			refresh(player, MobEffects.WEAKNESS, soft ? 0 : 1);
			refresh(player, MobEffects.MOVEMENT_SLOWDOWN, 0);
		}
		modifier(player, Attributes.MAX_HEALTH, WITCH_HEALTH, "masked_witch", soft ? -2 : -4, worn == Face.WITCH);
		modifierMultiply(player, Attributes.MOVEMENT_SPEED, WITHER_SPEED, "masked_wither", soft ? -0.05 : -0.1, worn == Face.WITHER_SKELETON);
		if (worn == Face.ILLAGER) {
			for (IronGolem golem : world.getEntitiesOfClass(IronGolem.class, player.getBoundingBox().inflate(16),
					g -> g.isAlive() && !g.isPlayerCreated() && g.getTarget() == null)) {
				golem.setTarget(player);
			}
		}
		// Faceless: weaker than a plain human (Bare Bone softens it)
		modifier(player, Attributes.MAX_HEALTH, FACELESS_HEALTH, "masked_faceless", data.has(Talents.RIP_FACELESS) ? -2 : -4, worn == null);

		calmKin(player, data);
	}

	private static void crumble(ServerPlayer player, RaceData data, Face face) {
		data.masked.gallery.remove(face);
		data.masked.worn = null;
		facesChanged = true;
		ServerLevel world = player.serverLevel();
		Fx.burst(world, player.getEyePosition(), 1.2f, 10, BONE_RGB);
		world.sendParticles(ParticleTypes.ASH, player.getX(), player.getEyeY(), player.getZ(), 20, 0.3, 0.3, 0.3, 0.02);
		world.playSound(null, player.blockPosition(), SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 1.0f, 0.6f);
		player.displayClientMessage(Component.translatable("mages_legacy.masked.crumbled", Component.translatable("mages_legacy.face." + face.id)).withStyle(ChatFormatting.GRAY), false);
	}

	private static void refresh(ServerPlayer player, net.minecraft.core.Holder<MobEffect> effect, int amplifier) {
		MobEffectInstance current = player.getEffect(effect);
		if (current == null || current.getDuration() <= 30) {
			player.addEffect(new MobEffectInstance(effect, 60, amplifier, true, false, true));
		}
	}

	private static void modifier(ServerPlayer player, net.minecraft.core.Holder<Attribute> attribute, net.minecraft.resources.ResourceLocation id, String name, double amount, boolean want) {
		set(player, attribute, id, name, amount, AttributeModifier.Operation.ADD_VALUE, want);
	}

	private static void modifierMultiply(ServerPlayer player, net.minecraft.core.Holder<Attribute> attribute, net.minecraft.resources.ResourceLocation id, String name, double amount, boolean want) {
		set(player, attribute, id, name, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, want);
	}

	/** Adds or removes an attribute change so it matches whether it applies right now (and its amount). */
	private static void set(ServerPlayer player, net.minecraft.core.Holder<Attribute> attribute, net.minecraft.resources.ResourceLocation id, String name, double amount,
			AttributeModifier.Operation operation, boolean want) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		AttributeModifier current = instance.getModifier(id);
		if (want == (current != null) && (current == null || current.amount() == amount)) {
			return;
		}
		if (current != null) {
			instance.removeModifier(id);
		}
		if (want) {
			instance.addTransientModifier(new AttributeModifier(id, amount, operation));
		}
		player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
	}

	// ---- every tick ----

	public static void tick(MinecraftServer server) {
		long now = now(server);
		EXPOSED.values().removeIf(until -> until < now);
		THOUSAND.values().removeIf(until -> until < now);
		TRACE.values().removeIf(t -> t.until() < now);
		BLADE.values().removeIf(b -> b.until() < now || b.charges() <= 0);
		RETINUE.values().removeIf(r -> r.until() < now);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			// The phantom's face: held crouching in the air, it glides down
			if (player.isShiftKeyDown() && !player.onGround() && player.getDeltaMovement().y < 0 && wears(player, Face.PHANTOM)) {
				player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 10, 0, true, false, false));
			}
		}
		if (now % 10 == 0) {
			leadRetinues(server);
		}
		if (facesChanged || now % 40 == 0) {
			facesChanged = false;
			broadcast(server);
		}
	}

	/** The retinue fights what its Masked fights, or what's after it, and keeps close. */
	private static void leadRetinues(MinecraftServer server) {
		for (ServerLevel world : server.getAllLevels()) {
			for (Map.Entry<UUID, Retinue> e : RETINUE.entrySet()) {
				if (!(world.getEntity(e.getKey()) instanceof Mob mob) || !mob.isAlive()) {
					continue;
				}
				ServerPlayer owner = server.getPlayerList().getPlayer(e.getValue().owner());
				if (owner == null || owner.level() != world) {
					continue;
				}
				LivingEntity target = foe(owner, mob);
				if (target != null) {
					mob.setTarget(target);
				} else {
					if (mob.getTarget() instanceof Player) {
						mob.setTarget(null);
					}
					if (mob.distanceToSqr(owner) > 64) {
						mob.getNavigation().moveTo(owner, 1.2);
					}
				}
			}
		}
	}

	private static LivingEntity foe(ServerPlayer owner, Mob mob) {
		LivingEntity struck = owner.getLastHurtMob();
		if (struck != null && struck.isAlive() && !(struck instanceof Player) && owner.tickCount - owner.getLastHurtMobTimestamp() < 100
				&& !RETINUE.containsKey(struck.getUUID()) && struck != mob) {
			return struck;
		}
		LivingEntity attacker = owner.getLastHurtByMob();
		if (attacker != null && attacker.isAlive() && !(attacker instanceof Player) && !RETINUE.containsKey(attacker.getUUID()) && attacker != mob) {
			return attacker;
		}
		return owner.serverLevel().getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(12),
				m -> m.isAlive() && m != mob && m instanceof Enemy && m.getTarget() == owner && !RETINUE.containsKey(m.getUUID())).stream()
				.min(Comparator.comparingDouble(m -> m.distanceToSqr(mob))).orElse(null);
	}

	/** Tells every player who in their world wears which face, so the client draws the wearer in that shape. */
	public static void broadcast(MinecraftServer server) {
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		for (ServerPlayer viewer : players) {
			if (!Server.canSend(viewer, RacePackets.FACES)) {
				continue;
			}
			List<int[]> faces = new ArrayList<>();
			for (ServerPlayer player : players) {
				RaceData data = masked(player);
				if (data != null && data.masked.worn != null && player.level() == viewer.level()) {
					faces.add(new int[] {player.getId(), data.masked.worn.ordinal()});
				}
			}
			FriendlyByteBuf buf = Network.create();
			buf.writeVarInt(faces.size());
			for (int[] f : faces) {
				buf.writeVarInt(f[0]);
				buf.writeVarInt(f[1]);
			}
			Server.send(viewer, RacePackets.FACES, buf);
		}
	}

	// ---- abilities ----

	public static AbilityResult use(ServerPlayer player, RaceData data, int slot) {
		return switch (slot) {
			case 0 -> tear(player, data);
			case 1 -> power(player, data);
			case 2 -> change(player, data);
			case 3 -> kinCall(player, data);
			case 4 -> data.has(Talents.GAL_THOUSAND) ? thousandFaces(player, data) : RaceData.talentLocked(player, Talents.GAL_THOUSAND);
			default -> AbilityResult.LOCKED;
		};
	}

	public static double ripReach(RaceData data) {
		return data.has(Talents.RIP_REACH) ? 8 : 5;
	}

	/**
	 * The Tearing (R): the face of the weakened monster you look at comes off in your hand and goes on at once. The
	 * monster dies of it; the face joins the gallery (the oldest is let go when it's full) and is fresh again if it was
	 * there already. With Tear Open, a monster not yet weak enough is torn at instead.
	 */
	public static AbilityResult tear(ServerPlayer player, RaceData data) {
		if (!ready(player, data, 0)) {
			return AbilityResult.COOLDOWN;
		}
		LivingEntity target = lookedAt(player, ripReach(data), e -> e instanceof Enemy);
		if (target == null) {
			return AbilityResult.NO_SIGHT;
		}
		Face face = Face.of(target.getType()).orElse(null);
		ServerLevel world = player.serverLevel();
		float threshold = data.has(Talents.RIP_WEAK) ? RIP_AT_WEAK : RIP_AT;
		if (face == null || target.getHealth() > target.getMaxHealth() * threshold) {
			if (data.has(Talents.RIP_TEAR)) {
				// Tear Open: no face yet, but a wound
				cooldown(player, data, 0, RIP_COOLDOWN);
				target.hurt(world.damageSources().indirectMagic(player, player), data.has(Talents.RIP_EXECUTE) ? 8 : 4);
				if (data.has(Talents.RIP_EXECUTE)) {
					target.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
				}
				Fx.slash(world, target.position().add(0, target.getBbHeight() * 0.7, 0), target.position().subtract(player.position()), 0.8f + target.getBbWidth(), 30, 8, BONE_INK);
				world.playSound(null, target.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.0f, 0.6f);
				return AbilityResult.OK;
			}
			player.displayClientMessage(Component.translatable(face == null ? "mages_legacy.masked.no_face" : "mages_legacy.masked.too_strong",
					Math.round(threshold * 100)), true);
			return AbilityResult.HINTED;
		}
		cooldown(player, data, 0, RIP_COOLDOWN);
		Vec3 at = target.position().add(0, target.getBbHeight() * 0.8, 0);
		target.hurt(world.damageSources().indirectMagic(player, player), Float.MAX_VALUE);

		MaskData mask = data.masked;
		boolean known = mask.gallery.containsKey(face);
		if (!known && mask.gallery.size() >= gallerySize(data)) {
			// A full gallery lets go of its oldest face that isn't worn
			mask.gallery.keySet().stream().filter(f -> f != mask.worn).findFirst().ifPresent(mask.gallery::remove);
		}
		mask.gallery.remove(face);
		mask.gallery.put(face, 100f);
		data.addResource(data.has(Talents.RIP_FEED) ? 40 : 25);
		wear(player, data, face);
		if (!known) {
			// A new face: what it gives, what it costs and what it can do
			player.displayClientMessage(Component.translatable("mages_legacy.face." + face.id + ".desc").withStyle(ChatFormatting.GRAY), false);
		}
		if (data.has(Talents.RIP_HEAL)) {
			player.heal(4);
		}
		if (data.has(Talents.RIP_ARMOR)) {
			euf.mageslegacy.platform.Absorption.add(player, 4);
		}
		if (data.has(Talents.RIP_SHOCK)) {
			for (Mob m : world.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(8), m -> m.isAlive() && face.matches(m.getType()))) {
				m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
				m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
			}
		}
		if (data.has(Talents.RIP_CHAIN)) {
			data.readyAt.put(1, 0L);
		}
		Progression.addXp(player, data, "masked_rip", 3, 60);
		// The face torn off: a pale slash across where it was, bone dust, and the face flies to the Masked
		Fx.slash(world, at, at.subtract(player.getEyePosition()), 1.0f + target.getBbWidth(), 20, 10, BONE_INK);
		Fx.burst(world, at, 1.0f + target.getBbWidth(), 8, BONE_RGB);
		Fx.mask(world, at, player, 18, BONE_RGB);
		world.sendParticles(ParticleTypes.WHITE_ASH, at.x, at.y, at.z, 20, 0.3, 0.3, 0.3, 0.02);
		world.playSound(null, target.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.2f, 0.5f);
		world.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1.0f, 0.7f);
		return AbilityResult.OK;
	}

	/** Puts a face of the gallery on: the shape changes, its kind calms down. */
	private static void wear(ServerPlayer player, RaceData data, Face face) {
		data.masked.worn = face;
		data.dirty = true;
		facesChanged = true;
		ServerLevel world = player.serverLevel();
		// The face wraps around the wearer: bone strands closing in, a pale flash
		Fx.helix(world, player, 0.9f, 2.1f, 3, true, 14, BONE_RGB);
		Fx.burst(world, player.getEyePosition(), 1.4f, 8, BONE_INK);
		Fx.screen(player, 10, BONE_RGB);
		world.playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.0f, 0.8f);
		calmKin(player, data);
	}

	/** The Change (Z): the next face of the gallery goes on. */
	public static AbilityResult change(ServerPlayer player, RaceData data) {
		if (!ready(player, data, 2)) {
			return AbilityResult.COOLDOWN;
		}
		MaskData mask = data.masked;
		Face next = next(mask);
		if (next == null || next == mask.worn) {
			player.displayClientMessage(Component.translatable("mages_legacy.masked.one_face"), true);
			return AbilityResult.HINTED;
		}
		int cost = swapCost(data);
		if (data.resource < cost) {
			return AbilityResult.NO_RESOURCE;
		}
		data.addResource(-cost);
		cooldown(player, data, 2, data.has(Talents.GAL_CHEAP) ? SWAP_COOLDOWN_CHEAP : SWAP_COOLDOWN);
		long now = now(player.getServer());
		if (data.has(Talents.GAL_TRACE) && mask.worn != null) {
			TRACE.put(player.getUUID(), new Trace(mask.worn, now + 200));
		}
		ServerLevel world = player.serverLevel();
		// The old face left standing a moment where the wearer was; the gallery's faces turn once round the head
		Fx.ghosts(world, List.of(player.position(), player.position()), player.getYRot(), 1, 16, BONE_INK);
		Fx.masks(world, player, Math.max(2, mask.gallery.size()), 0.9f, 18, BONE_RGB);
		wear(player, data, next);
		if (data.has(Talents.GAL_QUICK)) {
			player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 1, true, true, true));
			for (Mob m : world.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(8), m -> m.isAlive() && m.getTarget() == player)) {
				m.setTarget(null);
			}
		}
		player.displayClientMessage(Component.translatable("mages_legacy.masked.now", Component.translatable("mages_legacy.face." + next.id)).withStyle(ChatFormatting.GRAY), true);
		return AbilityResult.OK;
	}

	/** The face after the worn one in the gallery (the first one when faceless), or null with an empty gallery. */
	public static Face next(MaskData mask) {
		List<Face> faces = new ArrayList<>(mask.gallery.keySet());
		if (faces.isEmpty()) {
			return null;
		}
		int at = mask.worn == null ? -1 : faces.indexOf(mask.worn);
		return faces.get((at + 1) % faces.size());
	}

	/** The face's Power (V): the worn monster's own trick. */
	public static AbilityResult power(ServerPlayer player, RaceData data) {
		Face face = data.masked.worn;
		if (face == null) {
			player.displayClientMessage(Component.translatable("mages_legacy.masked.faceless"), true);
			return AbilityResult.HINTED;
		}
		if (!ready(player, data, 1)) {
			return AbilityResult.COOLDOWN;
		}
		int cost = powerCost(player, data);
		if (data.resource < cost) {
			return AbilityResult.NO_RESOURCE;
		}
		float m = data.has(Talents.GAL_POWER) ? 1.25f : 1.0f;
		AbilityResult result = switch (face) {
			case ZOMBIE -> grip(player, m);
			case SKELETON -> volley(player, m);
			case DROWNED -> trident(player, m);
			case CREEPER -> blast(player, m);
			case SPIDER -> web(player, m);
			case ENDERMAN -> step(player);
			case BLAZE -> flame(player, m);
			case WITCH -> brew(player, m);
			case WITHER_SKELETON -> blade(player);
			case PHANTOM -> rise(player, m);
			case ILLAGER -> fangs(player, m);
		};
		if (result != AbilityResult.OK) {
			return result;
		}
		data.addResource(-cost);
		cooldown(player, data, 1, thousand(player) ? face.cooldown / 2 : face.cooldown);
		if (!player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(16), m2 -> m2.isAlive() && m2 instanceof Enemy).isEmpty()) {
			Progression.addXp(player, data, "masked_power", 1, 20);
		}
		return AbilityResult.OK;
	}

	private static LivingEntity foeInSight(ServerPlayer player, double range) {
		return lookedAt(player, range, e -> !(e instanceof Player) && !(e instanceof TamableAnimal tame && tame.isTame()) && !RETINUE.containsKey(e.getUUID()));
	}

	/** Zombie, the Dead Grip: 5 damage, held fast for three seconds, and 3 of it back as life. */
	private static AbilityResult grip(ServerPlayer player, float m) {
		LivingEntity target = foeInSight(player, 5);
		if (target == null) {
			return AbilityResult.NO_SIGHT;
		}
		ServerLevel world = player.serverLevel();
		target.hurt(world.damageSources().indirectMagic(player, player), 5 * m);
		target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 3));
		player.heal(3);
		Fx.tendril(world, player, target, 12, BONE_INK, GLINT_RGB);
		Fx.burst(world, target.position().add(0, target.getBbHeight() * 0.6, 0), 0.8f + target.getBbWidth(), 8, GLINT_RGB);
		world.playSound(null, target.blockPosition(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.PLAYERS, 0.8f, 0.8f);
		return AbilityResult.OK;
	}

	/** Skeleton, the Volley: five arrows in a fan. */
	private static AbilityResult volley(ServerPlayer player, float m) {
		ServerLevel world = player.serverLevel();
		for (int i = -2; i <= 2; i++) {
			Arrow arrow = new Arrow(world, player, new ItemStack(Items.ARROW), null);
			arrow.shootFromRotation(player, player.getXRot(), player.getYRot() + i * 6, 0, 2.8f, 1.0f);
			arrow.setBaseDamage(1.2 * m);
			arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
			world.addFreshEntity(arrow);
		}
		Fx.ring(world, player.getEyePosition().add(player.getViewVector(1.0f)), 0.2f, 1.5f, 8, BONE_RGB);
		world.playSound(null, player.blockPosition(), SoundEvents.SKELETON_SHOOT, SoundSource.PLAYERS, 1.2f, 0.8f);
		return AbilityResult.OK;
	}

	/** Drowned, the Trident: one thrown hard, gone when it has struck. */
	private static AbilityResult trident(ServerPlayer player, float m) {
		ServerLevel world = player.serverLevel();
		ThrownTrident trident = new ThrownTrident(world, player, new ItemStack(Items.TRIDENT));
		trident.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, 2.5f * Math.min(1.2f, m), 0.5f);
		trident.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
		world.addFreshEntity(trident);
		world.playSound(null, player.blockPosition(), SoundEvents.DROWNED_SHOOT, SoundSource.PLAYERS, 1.0f, 1.0f);
		return AbilityResult.OK;
	}

	/** Creeper, the Blast: everything within 5 blocks takes up to 10, the Masked itself 4 (its own face halves that). */
	private static AbilityResult blast(ServerPlayer player, float m) {
		ServerLevel world = player.serverLevel();
		Vec3 at = player.position();
		for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(5),
				e -> e != player && e.isAlive() && !(e instanceof Player) && !RETINUE.containsKey(e.getUUID()))) {
			double d = Math.sqrt(e.distanceToSqr(at));
			if (d > 5) {
				continue;
			}
			e.hurt(world.damageSources().indirectMagic(player, player), (float) (10 * m * (1 - d / 6)));
			Vec3 push = e.position().subtract(at).normalize().scale(1.2 * (1 - d / 6));
			e.push(push.x, 0.4, push.z);
			e.hurtMarked = true;
		}
		player.hurt(world.damageSources().explosion(player, player), 4);
		world.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 1, at.z, 1, 0, 0, 0, 0);
		Fx.ring(world, at, 0.5f, 6, 14, GLINT_RGB);
		Fx.shake(player, 2.0f, 10);
		world.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2f, 1.0f);
		return AbilityResult.OK;
	}

	/** Spider, the Web: the creature you look at (16 blocks) and all within 3 of it stuck and poisoned. */
	private static AbilityResult web(ServerPlayer player, float m) {
		LivingEntity target = foeInSight(player, 16);
		if (target == null) {
			return AbilityResult.NO_SIGHT;
		}
		ServerLevel world = player.serverLevel();
		for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(3),
				e -> e != player && e.isAlive() && !(e instanceof Player) && !RETINUE.containsKey(e.getUUID()))) {
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, Math.round(100 * m), 3));
			e.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0));
			world.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.COBWEB)), e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 12, 0.4, 0.4, 0.4, 0.02);
		}
		Fx.tendril(world, player, target, 16, BONE_INK, 0xF4F0E8);
		Fx.web(world, target.position(), 3.0f, 70, 0xF4F0E8);
		world.playSound(null, target.blockPosition(), SoundEvents.SPIDER_AMBIENT, SoundSource.PLAYERS, 1.0f, 0.7f);
		return AbilityResult.OK;
	}

	/** Enderman, the Step: to the block you look at, up to 16 away. */
	private static AbilityResult step(ServerPlayer player) {
		HitResult hit = player.pick(16, 1.0f, false);
		if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
			return AbilityResult.NO_TARGET;
		}
		ServerLevel world = player.serverLevel();
		BlockPos pos = block.getBlockPos().relative(block.getDirection());
		Vec3 to = null;
		for (int up = 0; up <= 2 && to == null; up++) {
			Vec3 at = Vec3.atBottomCenterOf(pos.above(up));
			if (world.noCollision(player, player.getBoundingBox().move(at.subtract(player.position())))) {
				to = at;
			}
		}
		if (to == null) {
			return AbilityResult.NO_SPACE;
		}
		Vec3 from = player.position();
		player.teleportTo(to.x, to.y, to.z);
		player.fallDistance = 0;
		Fx.ghosts(world, List.of(from, from), player.getYRot(), 1, 14, Fx.INK | 0x2A1A3A);
		world.sendParticles(ParticleTypes.PORTAL, from.x, from.y + 1, from.z, 40, 0.3, 0.8, 0.3, 0.3);
		world.sendParticles(ParticleTypes.PORTAL, to.x, to.y + 1, to.z, 40, 0.3, 0.8, 0.3, 0.3);
		world.playSound(null, BlockPos.containing(from), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);
		world.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.2f);
		return AbilityResult.OK;
	}

	/** Blaze, the Blaze's Fire: the creature you look at (16 blocks) and those by it burn, 4 damage at once. */
	private static AbilityResult flame(ServerPlayer player, float m) {
		LivingEntity target = foeInSight(player, 16);
		if (target == null) {
			return AbilityResult.NO_SIGHT;
		}
		ServerLevel world = player.serverLevel();
		for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(2),
				e -> e != player && e.isAlive() && !(e instanceof Player) && !RETINUE.containsKey(e.getUUID()))) {
			e.igniteForSeconds(6);
			e.hurt(world.damageSources().indirectMagic(player, player), (e == target ? 4 : 2) * m);
			world.sendParticles(ParticleTypes.FLAME, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 16, 0.3, 0.5, 0.3, 0.03);
		}
		Fx.ribbon(world, List.of(player.getEyePosition(), target.position().add(0, target.getBbHeight() / 2, 0)), 12, Fx.INK | 0x5A2008, 0xFFB040, 0.6f);
		Fx.burst(world, target.position().add(0, target.getBbHeight() / 2, 0), 1.5f, 8, 0xFFB040);
		world.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0f, 1.0f);
		return AbilityResult.OK;
	}

	/** Witch, the Brew: a thrown flask of poison, weakness and slowness, and a sip of regeneration for herself. */
	private static AbilityResult brew(ServerPlayer player, float m) {
		ServerLevel world = player.serverLevel();
		ItemStack flask = new ItemStack(Items.SPLASH_POTION);
        flask.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS, new PotionContents(java.util.Optional.empty(), java.util.Optional.of(0x4E8A3A), List.of(
				new MobEffectInstance(MobEffects.POISON, Math.round(120 * m), 1),
				new MobEffectInstance(MobEffects.WEAKNESS, 160, 0),
				new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 160, 0))));

		ThrownPotion potion = new ThrownPotion(world, player);
		potion.setItem(flask);
		potion.shootFromRotation(player, player.getXRot(), player.getYRot(), -20.0f, 0.9f, 1.0f);
		world.addFreshEntity(potion);
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0, true, true, true));
		world.sendParticles(ParticleTypes.WITCH, player.getX(), player.getEyeY(), player.getZ(), 12, 0.4, 0.3, 0.4, 0.02);
		world.playSound(null, player.blockPosition(), SoundEvents.WITCH_THROW, SoundSource.PLAYERS, 1.0f, 1.0f);
		return AbilityResult.OK;
	}

	/** Wither skeleton, the Black Blade: the next three blows within 6 seconds strike 4 harder and wither. */
	private static AbilityResult blade(ServerPlayer player) {
		ServerLevel world = player.serverLevel();
		BLADE.put(player.getUUID(), new Blade(3, now(player.getServer()) + 120));
		Fx.aura(world, player, 120, 0x5A5A5A, Fx.EMBERS);
		Fx.burst(world, player.position().add(0, 1, 0), 1.2f, 8, Fx.INK | 0x202020);
		world.playSound(null, player.blockPosition(), SoundEvents.WITHER_SKELETON_AMBIENT, SoundSource.PLAYERS, 1.0f, 0.7f);
		return AbilityResult.OK;
	}

	/** Phantom, the Rise: up into the air on the phantom's wings, the creatures around knocked back, a slow glide down. */
	private static AbilityResult rise(ServerPlayer player, float m) {
		ServerLevel world = player.serverLevel();
		for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(3),
				e -> e != player && e.isAlive() && !(e instanceof Player) && !RETINUE.containsKey(e.getUUID()))) {
			e.hurt(world.damageSources().indirectMagic(player, player), 4 * m);
			Vec3 push = e.position().subtract(player.position()).normalize().scale(1.0);
			e.push(push.x, 0.3, push.z);
			e.hurtMarked = true;
		}
		player.push(0, 1.5, 0);
		player.hurtMarked = true;
		player.fallDistance = 0;
		player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 120, 0, true, true, true));
		Fx.ring(world, player.position(), 0.4f, 4, 12, 0x8FA8C8);
		Fx.ribbon(world, List.of(player.position(), player.position().add(0, 6, 0)), 14, Fx.INK | 0x1A2438, 0x8FA8C8, 1.0f);
		world.playSound(null, player.blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 1.2f, 0.8f);
		world.playSound(null, player.blockPosition(), SoundEvents.PHANTOM_SWOOP, SoundSource.PLAYERS, 1.0f, 1.0f);
		return AbilityResult.OK;
	}

	/** Illager, the Evoker's Fangs: a row of fangs bursts from the ground along the look, 12 blocks. */
	private static AbilityResult fangs(ServerPlayer player, float m) {
		ServerLevel world = player.serverLevel();
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		double dx = -Math.sin(yaw);
		double dz = Math.cos(yaw);
		int count = m > 1 ? 14 : 12;
		int placed = 0;
		for (int i = 1; i <= count; i++) {
			double x = player.getX() + dx * i * 1.0;
			double z = player.getZ() + dz * i * 1.0;
			Double y = ground(world, x, player.getY() + 2, z);
			if (y == null) {
				continue;
			}
			world.addFreshEntity(new EvokerFangs(world, x, y, z, player.getYRot() * Mth.DEG_TO_RAD, i, player));
			placed++;
		}
		if (placed == 0) {
			return AbilityResult.NO_SPACE;
		}
		world.playSound(null, player.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0f, 1.0f);
		return AbilityResult.OK;
	}

	/** The top of the ground under a point, searching 5 blocks down, or null. */
	private static Double ground(ServerLevel world, double x, double fromY, double z) {
		BlockPos pos = BlockPos.containing(x, fromY, z);
		for (int i = 0; i < 6; i++) {
			BlockPos below = pos.below();
			if (world.getBlockState(pos).getCollisionShape(world, pos).isEmpty() && world.getBlockState(below).isFaceSturdy(world, below, net.minecraft.core.Direction.UP)) {
				return (double) pos.getY();
			}
			pos = below;
		}
		return null;
	}

	/** Kin (H): monsters of the worn face's kind near you (4, more with talents) follow you and fight for you. */
	public static AbilityResult kinCall(ServerPlayer player, RaceData data) {
		Face face = data.masked.worn;
		if (face == null) {
			player.displayClientMessage(Component.translatable("mages_legacy.masked.faceless"), true);
			return AbilityResult.HINTED;
		}
		if (!ready(player, data, 3)) {
			return AbilityResult.COOLDOWN;
		}
		int cost = kinCost(data);
		if (data.resource < cost) {
			return AbilityResult.NO_RESOURCE;
		}
		ServerLevel world = player.serverLevel();
		Set<Face> kinds = data.has(Talents.KIN_ALL) ? data.masked.gallery.keySet() : Set.of(face);
		List<Mob> kin = new ArrayList<>(world.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(16),
				m -> m.isAlive() && Face.of(m.getType()).map(kinds::contains).orElse(false) && !RETINUE.containsKey(m.getUUID())));
		if (kin.isEmpty()) {
			player.displayClientMessage(Component.translatable("mages_legacy.masked.no_kin"), true);
			return AbilityResult.HINTED;
		}
		data.addResource(-cost);
		cooldown(player, data, 3, KIN_COOLDOWN);
		kin.sort(Comparator.comparingDouble(m -> m.distanceToSqr(player)));
		int size = data.has(Talents.KIN_KING) ? 10 : data.has(Talents.KIN_RETINUE) ? 6 : 4;
		int ticks = data.has(Talents.KIN_LONG) ? 600 : 400;
		long until = now(player.getServer()) + ticks;
		for (Mob m : kin.subList(0, Math.min(size, kin.size()))) {
			RETINUE.put(m.getUUID(), new Retinue(player.getUUID(), until));
			if (m.getTarget() instanceof Player) {
				m.setTarget(null);
			}
			if (data.has(Talents.KIN_STRONG)) {
				m.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, data.has(Talents.KIN_KING) ? 1 : 0));
				m.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 0));
			}
			Fx.tendril(world, player, m, 20, BONE_INK, BONE_RGB);
			Fx.sigil(world, m, Fx.SIGIL_MASK, ticks, BONE_RGB);
		}
		Fx.ring(world, player.position(), 0.5f, 16, 18, BONE_RGB);
		world.playSound(null, player.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 0.6f, 1.4f);
		return AbilityResult.OK;
	}

	/** The Thousand Faces (Alt, the Gallery's crown): 20 seconds of every gallery face's gift at once, the powers free and twice as quick. */
	public static AbilityResult thousandFaces(ServerPlayer player, RaceData data) {
		if (!ready(player, data, 4)) {
			return AbilityResult.COOLDOWN;
		}
		if (data.masked.gallery.size() < 2) {
			player.displayClientMessage(Component.translatable("mages_legacy.masked.one_face"), true);
			return AbilityResult.HINTED;
		}
		if (data.resource < THOUSAND_COST) {
			return AbilityResult.NO_RESOURCE;
		}
		data.addResource(-THOUSAND_COST);
		cooldown(player, data, 4, THOUSAND_COOLDOWN);
		THOUSAND.put(player.getUUID(), now(player.getServer()) + THOUSAND_TICKS);
		data.readyAt.put(1, 0L);
		ServerLevel world = player.serverLevel();
		// Faces all around the wearer at once: shades in a ring, strands closing in, the world flickering pale
		List<Vec3> ring = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			double a = i * Math.PI / 3;
			ring.add(player.position().add(Math.cos(a) * 1.8, 0, Math.sin(a) * 1.8));
		}
		Fx.ghosts(world, ring, player.getYRot(), 6, 30, BONE_INK);
		Fx.masks(world, player, 12, 2.2f, 70, BONE_RGB);
		Fx.aura(world, player, THOUSAND_TICKS, GLINT_RGB, Fx.MOTES);
		Fx.screen(player, 20, BONE_RGB);
		world.playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.2f, 0.7f);
		world.playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.0f, 0.5f);
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

	/** A blow on one of the Masked: the face's gifts and weaknesses; with Kin Guard, its kind turns on the attacker. */
	public static float modifyDamage(ServerPlayer player, DamageSource source, float amount) {
		RaceData data = masked(player);
		if (data == null || amount <= 0) {
			return amount;
		}
		Set<Face> faces = active(player, data);
		if (faces.contains(Face.CREEPER) && source.is(DamageTypeTags.IS_EXPLOSION)) {
			amount *= 0.5f;
		}
		if (faces.contains(Face.ENDERMAN) && source.is(DamageTypeTags.IS_FALL)) {
			amount *= 0.5f;
		}
		if (data.masked.worn == Face.SPIDER && source.is(DamageTypeTags.IS_FIRE)) {
			amount *= data.has(Talents.KIN_SKIN) ? 1.25f : 1.5f;
		}
		if (data.has(Talents.KIN_GUARD) && data.masked.worn != null && source.getEntity() instanceof LivingEntity attacker
				&& !(attacker instanceof Player)) {
			for (Mob m : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(16),
					m -> m.isAlive() && m != attacker && data.masked.worn.matches(m.getType()))) {
				m.setTarget(attacker);
			}
		}
		return amount;
	}

	/** Any blow a Masked strikes: its kind sees through it; faceless hands are weak; the faces' gifts. */
	public static float onHit(LivingEntity victim, DamageSource source, float amount) {
		if (amount <= 0 || !(source.getEntity() instanceof ServerPlayer player)) {
			return amount;
		}
		RaceData data = masked(player);
		if (data == null || victim == player) {
			return amount;
		}
		long now = now(player.getServer());
		Set<Face> faces = active(player, data);
		for (Face face : faces) {
			if (face.matches(victim.getType())) {
				// Struck one of its own: the kind sees through the face for a while
				EXPOSED.put(player.getUUID(), now + (data.has(Talents.KIN_DEEP) ? 100 : 300));
			}
		}
		if (source.is(DamageTypes.PLAYER_ATTACK) && source.getDirectEntity() == player) {
			if (data.masked.worn == null) {
				amount *= data.has(Talents.RIP_FACELESS) ? 0.95f : 0.85f;
			}
			if (faces.contains(Face.ILLAGER)) {
				amount *= 1.15f;
			}
			if (faces.contains(Face.WITHER_SKELETON)) {
				victim.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
			}
			Blade blade = BLADE.get(player.getUUID());
			if (blade != null && blade.until() > now && blade.charges() > 0) {
				amount += 4;
				victim.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
				BLADE.put(player.getUUID(), new Blade(blade.charges() - 1, blade.until()));
				Fx.slash(player.serverLevel(), victim.position().add(0, victim.getBbHeight() * 0.6, 0), victim.position().subtract(player.position()),
						0.8f + victim.getBbWidth(), 60, 8, Fx.INK | 0x101010);
			}
		} else if (source.getDirectEntity() instanceof AbstractArrow && !(source.getDirectEntity() instanceof ThrownTrident) && faces.contains(Face.SKELETON)) {
			amount *= 1.3f;
		}
		return amount;
	}

	/** A monster died: by the Masked's hand (Fresh Blood keeps its face fresh) or its retinue's (the hunt pays). */
	public static void onDeath(LivingEntity victim, DamageSource source) {
		if (victim.getServer() == null) {
			return;
		}
		RETINUE.remove(victim.getUUID());
		if (!(victim instanceof Enemy)) {
			return;
		}
		if (source.getEntity() instanceof ServerPlayer player) {
			RaceData data = masked(player);
			if (data != null && data.masked.worn != null) {
				data.addResource(2);
				if (data.has(Talents.GAL_FRESH)) {
					data.masked.gallery.computeIfPresent(data.masked.worn, (f, fresh) -> Math.min(100, fresh + 5));
				}
			}
			return;
		}
		Retinue retinue = source.getEntity() == null ? null : RETINUE.get(source.getEntity().getUUID());
		ServerPlayer owner = retinue == null ? null : victim.getServer().getPlayerList().getPlayer(retinue.owner());
		RaceData data = owner == null ? null : masked(owner);
		if (data != null) {
			Progression.addXp(owner, data, Progression.HUNT, 2, Progression.HUNT_CAP);
			if (data.has(Talents.KIN_HEAL)) {
				owner.heal(2);
			}
			if (data.has(Talents.KIN_FEAST)) {
				data.addResource(5);
			}
		}
	}

	/** The Last Face (the Tearing's crown): a killing blow tears off the worn face instead, once in 20 minutes. */
	public static boolean allowDeath(ServerPlayer player) {
		RaceData data = masked(player);
		if (data == null || !data.has(Talents.RIP_LAST) || data.masked.worn == null || !ready(player, data, LAST_SLOT)) {
			return true;
		}
		Face face = data.masked.worn;
		data.masked.gallery.remove(face);
		data.masked.worn = null;
		facesChanged = true;
		player.setHealth(player.getMaxHealth() * 0.4f);
		data.readyAt.put(LAST_SLOT, now(player.getServer()) + LAST_COOLDOWN);
		data.dirty = true;
		ServerLevel world = player.serverLevel();
		Fx.burst(world, player.getEyePosition(), 2.5f, 12, BONE_INK);
		Fx.ghosts(world, List.of(player.position(), player.position()), player.getYRot(), 1, 30, BONE_INK);
		Fx.screen(player, 24, BONE_RGB);
		world.sendParticles(ParticleTypes.WHITE_ASH, player.getX(), player.getEyeY(), player.getZ(), 40, 0.4, 0.4, 0.4, 0.03);
		world.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6f, 0.7f);
		player.displayClientMessage(Component.translatable("mages_legacy.masked.last_face", Component.translatable("mages_legacy.face." + face.id)).withStyle(ChatFormatting.GRAY), false);
		return false;
	}

	/** Takes the Masked's faces, retinue and timers away (the race was reset or changed) and its attribute changes off. */
	public static void cleanup(ServerPlayer player) {
		UUID id = player.getUUID();
		EXPOSED.remove(id);
		THOUSAND.remove(id);
		TRACE.remove(id);
		BLADE.remove(id);
		RETINUE.values().removeIf(r -> r.owner().equals(id));
		for (net.minecraft.resources.ResourceLocation modifier : List.of(FACELESS_HEALTH, ZOMBIE_HEALTH, WITCH_HEALTH)) {
			AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
			if (health != null) {
				health.removeModifier(modifier);
			}
		}
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(WITHER_SPEED);
		}
		player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
		facesChanged = true;
	}

	/** What the HUD needs: the worn face and how fresh, the next one for Z, the gallery, the Thousand Faces. */
	public static CompoundTag clientInfo(ServerPlayer player, RaceData data) {
		CompoundTag nbt = new CompoundTag();
		MaskData mask = data.masked;
		nbt.putString("face", mask.worn == null ? "" : mask.worn.id);
		nbt.putInt("fresh", mask.worn == null ? 0 : (int) Math.ceil(mask.gallery.getOrDefault(mask.worn, 0f)));
		Face next = next(mask);
		nbt.putString("next", next == null || next == mask.worn ? "" : next.id);
		ListTag gallery = new ListTag();
		mask.gallery.keySet().forEach(f -> gallery.add(StringTag.valueOf(f.id)));
		nbt.put("gallery", gallery);
		nbt.putInt("powerCost", powerCost(player, data));
		nbt.putBoolean("thousand", thousand(player));
		return nbt;
	}

	/** Whether an entity is in some Masked's retinue (the mixin keeps it off players). */
	public static boolean inRetinue(Entity entity) {
		return RETINUE.containsKey(entity.getUUID());
	}
}
