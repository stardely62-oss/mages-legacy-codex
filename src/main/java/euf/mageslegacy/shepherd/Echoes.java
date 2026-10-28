package euf.mageslegacy.shepherd;

import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.fx.ModParticles;
import euf.mageslegacy.net.RacePackets;
import euf.mageslegacy.race.RaceData;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import euf.mageslegacy.platform.Network;
import euf.mageslegacy.platform.Network.Server;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Echoes of the fallen: real mobs of the learned kind that fight for their Shepherd, wrapped in pale spirit light.
 * They vanish at dawn (unless the dead don't sleep), when their Shepherd leaves, or when they fall: no loot, no
 * experience. Kept only in memory; one found in a loaded chunk without its record is a leftover and is removed.
 */
public final class Echoes {
	public static final String TAG = "mages_legacy_echo";
	private static final UUID STURDY = UUID.fromString("3c9d2b7e-6a14-4f0b-9e2d-5b8c1a7f4e33");
	private static final UUID WEAPONS = UUID.fromString("8e1f4a2c-7b35-4d9e-a6c0-2f5b9d3e1a44");
	/** Spirit wisps (the race's own particle): pale cyan flames that rise and flicker. */
	public static final ParticleOptions SPIRIT = ModParticles.WISP;

	/** A living echo: whose, what kind, in which world, until when (world time), and what it cost. */
	public record Echo(UUID owner, String type, ResourceKey<Level> world, long expiresAt, boolean temporary, int voice) {}

	private static final Map<UUID, Echo> ACTIVE = new HashMap<>();

	/** Cobwebs a spider echo spun, removed again after a few seconds. */
	private record Web(ServerLevel world, BlockPos pos, long until) {}

	private static final List<Web> WEBS = new ArrayList<>();

	private Echoes() {
	}

	public static void reset() {
		ACTIVE.clear();
		WEBS.clear();
	}

	/** The kind of echo (entity type id), or "" for anything else. */
	public static String typeOf(Entity entity) {
		Echo echo = ACTIVE.get(entity.getUUID());
		return echo == null ? "" : echo.type();
	}

	/**
	 * Every kind of echo hits in its own way (texts: mages_legacy.echo.trait.<kind>). Returns the damage, which the
	 * Executioner doubles on the nearly dead.
	 */
	public static float onHit(Entity echo, LivingEntity victim, float amount) {
		Echo info = ACTIVE.get(echo.getUUID());
		if (info == null || !(victim.level() instanceof ServerLevel world)) {
			return amount;
		}
		// Resonance makes every trait stronger
		boolean strong = RaceState.get(world.getServer()).data(info.owner()).has(Talents.LEGION_RESONANCE);
		switch (info.type()) {
			case "minecraft:husk" -> {
				victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, strong ? 2 : 1));
				victim.addEffect(new MobEffectInstance(MobEffects.HUNGER, 100, 0));
			}
			case "minecraft:skeleton" -> {
				// Its arrows mark the target for the whole army
				int ticks = strong ? 200 : 100;
				victim.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0));
				ServerPlayer owner = world.getServer().getPlayerList().getPlayer(info.owner());
				if (owner != null && victim instanceof Enemy && Creatures.marked(owner) == null) {
					Creatures.mark(owner, victim, world.getServer().getTickCount() + ticks);
				}
			}
			case "minecraft:stray" -> victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, strong ? 3 : 2));
			case "minecraft:spider" -> {
				BlockPos feet = victim.blockPosition();
				if (world.random.nextFloat() < (strong ? 0.5f : 0.25f) && world.getBlockState(feet).isAir()) {
					world.setBlockAndUpdate(feet, Blocks.COBWEB.defaultBlockState());
					WEBS.add(new Web(world, feet, world.getServer().getTickCount() + 100));
				}
			}
			case "minecraft:cave_spider" -> victim.addEffect(victim.isInvertedHealAndHarm()
					? new MobEffectInstance(MobEffects.WITHER, 80, strong ? 1 : 0) : new MobEffectInstance(MobEffects.POISON, 80, strong ? 2 : 1));
			case "minecraft:vindicator" -> {
				if (victim.getHealth() - amount < victim.getMaxHealth() * (strong ? 0.45f : 0.3f)) {
					amount *= 2;
					world.sendParticles(ParticleTypes.DAMAGE_INDICATOR, victim.getX(), victim.getY() + 1, victim.getZ(), 6, 0.3, 0.3, 0.3, 0.1);
				}
			}
			case "minecraft:silverfish" -> victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, strong ? 1 : 0));
			case "minecraft:pillager" -> victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, strong ? 1 : 0));
			case "minecraft:wither_skeleton" -> {
				victim.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
				ServerPlayer owner = world.getServer().getPlayerList().getPlayer(info.owner());
				if (owner != null) {
					owner.heal(strong ? 2 : 1);
				}
			}
			default -> {
			}
		}
		return amount;
	}

	/** What some echoes do just by being near: the zombie draws monsters onto itself, the drowned and the blaze guard. */
	private static void aura(Echo echo, Mob mob, ServerPlayer owner) {
		boolean strong = RaceState.of(owner).has(Talents.LEGION_RESONANCE);
		switch (echo.type()) {
			case "minecraft:zombie" -> {
				for (Mob m : mob.level().getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(strong ? 14 : 8),
						e -> e instanceof Enemy && e.getTarget() == owner)) {
					m.setTarget(mob);
				}
			}
			case "minecraft:drowned" -> {
				if (owner.isInWater() && owner.distanceTo(mob) < (strong ? 20 : 12)) {
					owner.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 60, 0, true, false, true));
					owner.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 100, 0, true, false, true));
				}
			}
			case "minecraft:blaze" -> {
				if (owner.distanceTo(mob) < (strong ? 16 : 8)) {
					owner.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 60, 0, true, false, true));
				}
			}
			case "minecraft:witch" -> {
				// The herbalist: every 10 seconds a healing brew for the Shepherd and its creatures close by
				if (mob.getServer().getTickCount() % (strong ? 120 : 200) < 20) {
					ServerLevel world = (ServerLevel) mob.level();
					if (owner.distanceTo(mob) < 8) {
						owner.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0, true, true, true));
					}
					for (LivingEntity c : world.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(8),
							e -> Creatures.isCreatureOf(e, owner.getUUID()))) {
						c.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0, true, true));
					}
					world.sendParticles(ParticleTypes.HAPPY_VILLAGER, mob.getX(), mob.getY() + 1.5, mob.getZ(), 10, 1.5, 0.5, 1.5, 0);
				}
			}
			case "minecraft:ravager" -> {
				// A roar every 6 seconds throws back the monsters around it
				if (mob.getServer().getTickCount() % (strong ? 80 : 120) < 20) {
					ServerLevel world = (ServerLevel) mob.level();
					boolean any = false;
					for (LivingEntity m : world.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(4),
							e -> e instanceof Enemy && Creatures.ownerOf(e) == null)) {
						Vec3 away = m.position().subtract(mob.position());
						m.hurt(world.damageSources().mobAttack(mob), 3);
						m.knockback(1.5, -away.x, -away.z);
						any = true;
					}
					if (any) {
						world.playSound(null, mob.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.0f, 1.0f);
						world.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 1, mob.getZ(), 20, 1.5, 0.5, 1.5, 0.05);
					}
				}
			}
			default -> {
			}
		}
	}

	public static boolean isEcho(Entity entity) {
		return ACTIVE.containsKey(entity.getUUID());
	}

	@Nullable
	public static UUID ownerOf(Entity entity) {
		Echo echo = ACTIVE.get(entity.getUUID());
		return echo == null ? null : echo.owner();
	}

	/** Bond slots this Shepherd's echoes take (the Army of Oblivion's temporary ones are free). */
	public static int capacityUsed(UUID owner) {
		int used = 0;
		for (Map.Entry<String, Integer> e : countByType(owner).entrySet()) {
			used += EchoType.byId(e.getKey()).map(t -> t.units(e.getValue()) * t.capacity()).orElse(0);
		}
		return used;
	}

	/** How many echoes of each kind walk with this Shepherd now (the Army of Oblivion's temporary ones aside). */
	public static Map<String, Integer> countByType(UUID owner) {
		Map<String, Integer> counts = new HashMap<>();
		for (Echo echo : ACTIVE.values()) {
			if (echo.owner().equals(owner) && !echo.temporary()) {
				counts.merge(echo.type(), 1, Integer::sum);
			}
		}
		return counts;
	}

	/** Spots for a squad: the first at the Call's spot, the rest in a ring around it where there's room. */
	static Vec3 around(ServerLevel world, Vec3 base, int index) {
		if (index == 0) {
			return base;
		}
		double a = index * 2.4;
		double r = 1.5 + (index / 6) * 1.2;
		BlockPos column = BlockPos.containing(base.x + Math.cos(a) * r, base.y, base.z + Math.sin(a) * r);
		for (int dy = 1; dy >= -2; dy--) {
			BlockPos feet = column.above(dy);
			if (world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()
					&& world.getBlockState(feet.above()).getCollisionShape(world, feet.above()).isEmpty()
					&& !world.getBlockState(feet.below()).getCollisionShape(world, feet.below()).isEmpty()) {
				return Vec3.atBottomCenterOf(feet);
			}
		}
		return base;
	}

	public static int count(UUID owner) {
		return (int) ACTIVE.values().stream().filter(e -> e.owner().equals(owner)).count();
	}

	/** Where the Call lands: the block you look at within 8, else in front of you. */
	static Vec3 callSpot(ServerPlayer player) {
		ServerLevel world = player.serverLevel();
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0f).scale(8));
		BlockHitResult hit = world.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (hit.getType() == HitResult.Type.BLOCK) {
			BlockPos feet = hit.getBlockPos().relative(hit.getDirection());
			if (world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()
					&& world.getBlockState(feet.above()).getCollisionShape(world, feet.above()).isEmpty()) {
				return Vec3.atBottomCenterOf(feet);
			}
		}
		return Creatures.near(player);
	}

	/**
	 * Calls an echo of {@code type} to {@code at}. A temporary one lives {@code ticks}; a normal one until the next
	 * dawn, or until dismissed with the Undying talent.
	 */
	@Nullable
	public static Mob summon(ServerPlayer player, RaceData data, EchoType type, Vec3 at, boolean temporary, int ticks, int voice) {
		ServerLevel world = player.serverLevel();
		if (!(type.type().create(world) instanceof Mob mob)) {
			return null;
		}
		mob.moveTo(at.x, at.y, at.z, player.getYRot() + 180, 0);
		mob.finalizeSpawn(world, world.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.MOB_SUMMONED, null, null);
		// No jockeys: a skeleton riding the spider or a chicken under the zombie would be ordinary hostile mobs
		mob.getPassengers().forEach(Entity::discard);
		if (mob.getVehicle() != null) {
			mob.getVehicle().discard();
		}
		mob.setPersistenceRequired();
		mob.addTag(TAG);
		mob.setCanPickUpLoot(false);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			mob.setDropChance(slot, 0);
		}
		mob.setCustomName(Component.translatable("mages_legacy.echo.name", type.type().getDescription()).withStyle(ChatFormatting.AQUA));
		mob.setCustomNameVisible(false);
		mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, true, false));
		if (data.has(Talents.LEGION_STURDY)) {
			addModifier(mob, Attributes.MAX_HEALTH, STURDY, 0.2, AttributeModifier.Operation.MULTIPLY_BASE);
			mob.setHealth(mob.getMaxHealth());
		}
		if (data.has(Talents.LEGION_WEAPONS)) {
			addModifier(mob, Attributes.ATTACK_DAMAGE, WEAPONS, 2, AttributeModifier.Operation.ADDITION);
		}
		long time = world.getDayTime();
		long expires = temporary ? time + ticks
				: data.has(Talents.LEGION_SLEEPLESS) ? Long.MAX_VALUE
				// Dawn is the next multiple of a day in world time
				: (time / 24000 + 1) * 24000;
		ACTIVE.put(mob.getUUID(), new Echo(player.getUUID(), type.id(), world.dimension(), expires, temporary, voice));
		Creatures.teach(mob, true);
		world.addFreshEntity(mob);
		broadcast(world.getServer());
		rise(world, at);
		// A pillar of pale light and strands winding in as the echo takes shape
		Fx.pillar(world, at, 0.5f, 2.6f, 14, Shepherd.SPIRIT_RGB);
		Fx.helix(world, mob, Math.max(0.6f, mob.getBbWidth()), mob.getBbHeight() + 0.4f, 2, true, 16, Shepherd.SPIRIT_RGB);
		world.playSound(null, mob.blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.5f, 0.7f);
		world.playSound(null, mob.blockPosition(), SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, SoundSource.PLAYERS, 0.6f, 0.5f);
		return mob;
	}

	private static void addModifier(Mob mob, net.minecraft.world.entity.ai.attributes.Attribute attribute, UUID id, double amount,
			AttributeModifier.Operation operation) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance != null && instance.getModifier(id) == null) {
			instance.addPermanentModifier(new AttributeModifier(id, "mages_legacy_echo", amount, operation));
		}
	}

	/** Pale light rising out of the ground where an echo answers. */
	static void rise(ServerLevel world, Vec3 at) {
		for (int i = 0; i < 16; i++) {
			double a = i * 0.8;
			double h = i / 16.0 * 1.6;
			world.sendParticles(SPIRIT, at.x + Math.cos(a) * 0.5, at.y + h, at.z + Math.sin(a) * 0.5, 1, 0, 0, 0, 0);
		}
		world.sendParticles(ParticleTypes.SOUL, at.x, at.y + 0.5, at.z, 12, 0.3, 0.3, 0.3, 0.02);
	}

	/** An echo fades: a wisp of souls, then nothing. */
	public static void fade(Entity entity) {
		vanish(entity);
		ACTIVE.remove(entity.getUUID());
	}

	/** The fading itself, without touching the records (for loops over them). */
	private static void vanish(Entity entity) {
		if (entity.level() instanceof ServerLevel world) {
			world.sendParticles(ParticleTypes.SOUL, entity.getX(), entity.getY() + 1, entity.getZ(), 16, 0.3, 0.5, 0.3, 0.03);
			world.sendParticles(SPIRIT, entity.getX(), entity.getY() + 1, entity.getZ(), 20, 0.3, 0.6, 0.3, 0);
			world.playSound(null, entity.blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.NEUTRAL, 1.0f, 1.2f);
		}
		entity.discard();
	}

	/** Sends all of this Shepherd's echoes back. */
	public static int dismiss(MinecraftServer server, UUID owner) {
		int count = 0;
		for (Iterator<Map.Entry<UUID, Echo>> it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Echo> e = it.next();
			if (!e.getValue().owner().equals(owner)) {
				continue;
			}
			ServerLevel world = server.getLevel(e.getValue().world());
			Entity entity = world == null ? null : world.getEntity(e.getKey());
			it.remove();
			if (entity != null) {
				vanish(entity);
			}
			count++;
		}
		return count;
	}

	/**
	 * An echo would die: it fades instead, so it drops nothing. With Return, half its Call comes back to the
	 * Shepherd. Returns false to cancel the death.
	 */
	public static boolean allowDeath(Entity entity) {
		Echo echo = ACTIVE.get(entity.getUUID());
		if (echo == null) {
			return true;
		}
		ServerPlayer owner = entity.getServer() == null ? null : entity.getServer().getPlayerList().getPlayer(echo.owner());
		RaceData data = owner == null ? null : RaceState.of(owner);
		if (data != null && data.has(Talents.LEGION_RETURN) && !echo.temporary()) {
			data.addResource(echo.voice() / 2f);
		}
		if (data != null && !echo.temporary()) {
			Shepherd.onLoss(owner, data);
		}
		if (data != null && data.has(Talents.LEGION_LAST_BREATH)) {
			lastBreath(owner, entity);
		}
		fade(entity);
		return false;
	}

	/** Last Breath: a falling echo bursts into frost, hurting and slowing the monsters around it. */
	private static void lastBreath(ServerPlayer owner, Entity echo) {
		ServerLevel world = owner.serverLevel();
		for (LivingEntity m : world.getEntitiesOfClass(LivingEntity.class, echo.getBoundingBox().inflate(4),
				e -> e.isAlive() && e != echo && e instanceof Enemy && Creatures.ownerOf(e) == null)) {
			m.hurt(world.damageSources().indirectMagic(echo, owner), 4);
			m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
		}
		world.sendParticles(ParticleTypes.SNOWFLAKE, echo.getX(), echo.getY() + 1, echo.getZ(), 40, 1.5, 0.6, 1.5, 0.05);
		world.playSound(null, echo.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, 0.8f, 0.6f);
	}

	/** A mob with the echo tag came out of a saved chunk: its Shepherd's session is over, so it goes. */
	public static void onLoad(Entity entity) {
		if (entity.getTags().contains(TAG) && !ACTIVE.containsKey(entity.getUUID())) {
			entity.discard();
		}
	}

	/** Once a second: dawn, Shepherds who left, echoes left behind; and a faint aura. */
	public static void tickSecond(MinecraftServer server) {
		List<UUID> gone = new ArrayList<>();
		for (Map.Entry<UUID, Echo> e : ACTIVE.entrySet()) {
			Echo echo = e.getValue();
			ServerLevel world = server.getLevel(echo.world());
			Entity entity = world == null ? null : world.getEntity(e.getKey());
			ServerPlayer owner = server.getPlayerList().getPlayer(echo.owner());
			if (entity == null || !entity.isAlive()) {
				gone.add(e.getKey());
				continue;
			}
			if (owner == null || world.getDayTime() >= echo.expiresAt()) {
				gone.add(e.getKey());
				vanish(entity);
				continue;
			}
			if (owner.serverLevel() != world || owner.distanceToSqr(entity) > 64 * 64) {
				// Left behind in another world or far away: it answers again at its Shepherd's side
				if (owner.serverLevel() == world) {
					Vec3 spot = Creatures.near(owner);
					entity.moveTo(spot.x, spot.y, spot.z, entity.getYRot(), entity.getXRot());
				} else {
					gone.add(e.getKey());
					vanish(entity);
				}
				continue;
			}
			world.sendParticles(SPIRIT, entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(), 1,
					entity.getBbWidth() * 0.4, entity.getBbHeight() * 0.3, entity.getBbWidth() * 0.4, 0);
			if (entity instanceof Mob mob) {
				aura(echo, mob, owner);
			}
		}
		gone.forEach(ACTIVE::remove);
		long ticks = server.getTickCount();
		WEBS.removeIf(web -> {
			if (web.until() > ticks) {
				return false;
			}
			if (web.world().getBlockState(web.pos()).is(Blocks.COBWEB)) {
				web.world().removeBlock(web.pos(), false);
			}
			return true;
		});
		broadcast(server);
	}

	/** Tells every player which entities in their world are echoes (the client draws them see-through and pale). */
	public static void broadcast(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!Server.canSend(player, RacePackets.ECHOES)) {
				continue;
			}
			List<Integer> ids = new ArrayList<>();
			for (Map.Entry<UUID, Echo> e : ACTIVE.entrySet()) {
				if (e.getValue().world().equals(player.level().dimension())) {
					Entity entity = player.serverLevel().getEntity(e.getKey());
					if (entity != null) {
						ids.add(entity.getId());
					}
				}
			}
			FriendlyByteBuf buf = Network.create();
			buf.writeVarInt(ids.size());
			ids.forEach(buf::writeVarInt);
			Server.send(player, RacePackets.ECHOES, buf);
		}
	}
}
