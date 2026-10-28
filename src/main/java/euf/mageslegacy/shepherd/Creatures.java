package euf.mageslegacy.shepherd;

import euf.mageslegacy.mixin.MobEntityAccessor;
import euf.mageslegacy.race.RaceState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A Shepherd's creatures, echoes and bonded beasts alike: who owns what, the goals that make them follow and defend
 * their Shepherd, and the marks that tell them whom to attack.
 */
public final class Creatures {
	/** Bonded beasts carry this tag plus "mages_legacy_owner:<uuid>", so the bond survives saves and restarts. */
	public static final String BOND_TAG = "mages_legacy_bond";
	public static final String OWNER_TAG = "mages_legacy_owner:";
	/** A bonded beast told to stay put. */
	public static final String STAY_TAG = "mages_legacy_stay";

	/** Creature uuid -> its Shepherd, for loaded bonded beasts (echoes live in {@link Echoes}). */
	private static final Map<UUID, UUID> BEAST_OWNERS = new HashMap<>();
	/** Mobs that already got their new goals (goals aren't saved, so a reloaded beast is taught again). */
	private static final Set<Mob> TAUGHT = Collections.newSetFromMap(new WeakHashMap<>());
	/** Shepherd -> the monster they marked (Hunter's Mark) and until when. */
	private static final Map<UUID, Mark> MARKS = new HashMap<>();

	private record Mark(UUID target, long until) {}

	private Creatures() {
	}

	public static void reset() {
		BEAST_OWNERS.clear();
		MARKS.clear();
	}

	@Nullable
	public static UUID ownerOf(Entity entity) {
		UUID echo = Echoes.ownerOf(entity);
		return echo != null ? echo : BEAST_OWNERS.get(entity.getUUID());
	}

	public static boolean isCreatureOf(Entity entity, UUID owner) {
		return owner.equals(ownerOf(entity));
	}

	public static void registerBeast(Entity entity, UUID owner) {
		BEAST_OWNERS.put(entity.getUUID(), owner);
	}

	public static void forgetBeast(UUID entity) {
		BEAST_OWNERS.remove(entity);
	}

	/** Reads the owner from a loaded bonded beast's tags. */
	@Nullable
	public static UUID taggedOwner(Entity entity) {
		for (String tag : entity.getTags()) {
			if (tag.startsWith(OWNER_TAG)) {
				try {
					return UUID.fromString(tag.substring(OWNER_TAG.length()));
				} catch (IllegalArgumentException ignored) {
					return null;
				}
			}
		}
		return null;
	}

	/** Every loaded creature of this Shepherd in its world within {@code radius}. */
	public static List<LivingEntity> around(ServerPlayer owner, double radius) {
		List<LivingEntity> list = new ArrayList<>();
		for (LivingEntity e : owner.serverLevel().getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(radius),
				e -> e.isAlive() && isCreatureOf(e, owner.getUUID()))) {
			list.add(e);
		}
		return list;
	}

	public static void mark(ServerPlayer owner, LivingEntity target, long until) {
		MARKS.put(owner.getUUID(), new Mark(target.getUUID(), until));
	}

	@Nullable
	static LivingEntity marked(ServerPlayer owner) {
		Mark mark = MARKS.get(owner.getUUID());
		if (mark == null || mark.until() < owner.getServer().getTickCount()) {
			return null;
		}
		return owner.serverLevel().getEntity(mark.target()) instanceof LivingEntity living && living.isAlive() ? living : null;
	}

	@Nullable
	static ServerPlayer owner(Mob mob) {
		UUID id = ownerOf(mob);
		MinecraftServer server = mob.getServer();
		if (id == null || server == null) {
			return null;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(id);
		return player != null && player.level() == mob.level() ? player : null;
	}

	/**
	 * Turns a mob into a Shepherd's creature: no more hunting players; instead it follows its Shepherd, defends it,
	 * attacks what it marks and, if it can fight, the monsters near it. Bees and axolotls don't pick fights on land.
	 */
	public static void teach(Mob mob, boolean fights) {
		if (!TAUGHT.add(mob)) {
			return;
		}
		MobEntityAccessor ai = (MobEntityAccessor) mob;
		if (fights) {
			ai.mages_legacy$targets().getAvailableGoals().clear();
			ai.mages_legacy$targets().addGoal(1, new DefendOwnerGoal(mob));
			// Not the neutral ones (a hit on one zombified piglin brings the whole horde down on the Shepherd) and not
			// creepers (a melee echo would set one off next to its Shepherd); those only when they attack
			ai.mages_legacy$targets().addGoal(3, new NearestAttackableTargetGoal<>(mob, Mob.class, 10, true, false, e ->
					e instanceof Enemy && !(e instanceof NeutralMob) && !(e instanceof Creeper) && ownerOf(e) == null
							&& nearOwner(mob, e, huntRadius(stance(mob)))));
		}
		ai.mages_legacy$goals().addGoal(fights ? 6 : 2, new FollowOwnerGoal(mob));
	}

	/** The Whistle's order this creature's Shepherd gave (guard while the Shepherd is away). */
	static int stance(Mob mob) {
		ServerPlayer owner = owner(mob);
		return owner == null ? ShepherdData.GUARD : RaceState.of(owner).shepherd.stance;
	}

	/** How far from the Shepherd its creatures pick fights on their own: never on Follow, 8 on Guard, 24 on Hunt. */
	static double huntRadius(int stance) {
		return stance == ShepherdData.HUNT ? 24 : stance == ShepherdData.GUARD ? 8 : 0;
	}

	private static boolean nearOwner(Mob mob, LivingEntity target, double radius) {
		ServerPlayer owner = owner(mob);
		return owner != null && owner.distanceToSqr(target) < radius * radius;
	}

	/** A spot near the owner with room to stand, for creatures that fell too far behind. */
	static Vec3 near(ServerPlayer owner) {
		ServerLevel world = owner.serverLevel();
		for (int i = 0; i < 10; i++) {
			BlockPos pos = owner.blockPosition().offset(owner.getRandom().nextIntBetweenInclusive(-2, 2), 0, owner.getRandom().nextIntBetweenInclusive(-2, 2));
			for (int dy = 1; dy >= -2; dy--) {
				BlockPos feet = pos.above(dy);
				if (world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()
						&& world.getBlockState(feet.above()).getCollisionShape(world, feet.above()).isEmpty()
						&& !world.getBlockState(feet.below()).getCollisionShape(world, feet.below()).isEmpty()) {
					return Vec3.atBottomCenterOf(feet);
				}
			}
		}
		return owner.position();
	}

	/** Walks after the Shepherd; jumps to its side when left more than 24 blocks behind. */
	static class FollowOwnerGoal extends Goal {
		private final Mob mob;
		private ServerPlayer owner;
		private int recalc;

		FollowOwnerGoal(Mob mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			owner = owner(mob);
			if (owner == null || owner.isSpectator() || mob.getTarget() != null || mob.getTags().contains(STAY_TAG)) {
				return false;
			}
			// An axolotl dries out on land: it only follows a Shepherd who swims
			if (mob instanceof Axolotl && !owner.isInWater()) {
				return false;
			}
			// Guards keep close: they start after their Shepherd at 4 blocks, the others at 8
			return mob.distanceToSqr(owner) > (stance(mob) == ShepherdData.GUARD ? 16 : 64);
		}

		@Override
		public boolean canContinueToUse() {
			return owner != null && owner.isAlive() && mob.getTarget() == null && mob.distanceToSqr(owner) > 9
					&& !mob.getTags().contains(STAY_TAG);
		}

		@Override
		public void start() {
			recalc = 0;
		}

		@Override
		public void stop() {
			mob.getNavigation().stop();
		}

		@Override
		public void tick() {
			mob.getLookControl().setLookAt(owner, 10, mob.getMaxHeadXRot());
			if (--recalc > 0) {
				return;
			}
			recalc = 10;
			if (mob.distanceToSqr(owner) > 24 * 24) {
				Vec3 spot = near(owner);
				mob.moveTo(spot.x, spot.y, spot.z, mob.getYRot(), mob.getXRot());
				mob.getNavigation().stop();
			} else {
				mob.getNavigation().moveTo(owner, mob instanceof Bee ? 1.0 : 1.2);
			}
		}
	}

	/** Attacks whoever hurts the Shepherd, then what the Shepherd attacks or marked. */
	static class DefendOwnerGoal extends Goal {
		private final Mob mob;
		private LivingEntity target;

		DefendOwnerGoal(Mob mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Flag.TARGET));
		}

		@Override
		public boolean canUse() {
			ServerPlayer owner = owner(mob);
			if (owner == null) {
				return false;
			}
			LivingEntity candidate = marked(owner);
			if (candidate == null) {
				candidate = owner.getLastHurtByMob();
			}
			if (candidate == null && owner.tickCount - owner.getLastHurtMobTimestamp() < 100) {
				candidate = owner.getLastHurtMob();
			}
			if (candidate == null || candidate == owner || candidate == mob || !candidate.isAlive()
					|| isCreatureOf(candidate, owner.getUUID()) || candidate == mob.getTarget()) {
				return false;
			}
			target = candidate;
			return true;
		}

		@Override
		public void start() {
			mob.setTarget(target);
		}

		@Override
		public boolean canContinueToUse() {
			return false;
		}
	}
}
