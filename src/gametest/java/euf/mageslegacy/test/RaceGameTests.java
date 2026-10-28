package euf.mageslegacy.test;

import euf.mageslegacy.MagesLegacy;
import euf.mageslegacy.bloodkin.Bloodkin;
import euf.mageslegacy.frozen.Frozen;
import euf.mageslegacy.mercenary.Contract;
import euf.mageslegacy.mercenary.Contracts;
import euf.mageslegacy.mercenary.Mercenary;
import euf.mageslegacy.mercenary.Shop;
import euf.mageslegacy.mycelid.Mycelid;
import euf.mageslegacy.net.RacePackets;
import euf.mageslegacy.race.AbilityResult;
import euf.mageslegacy.masked.Face;
import euf.mageslegacy.masked.Masked;
import euf.mageslegacy.tempter.Tempter;
import euf.mageslegacy.race.Progression.UnlockResult;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceData;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talent;
import euf.mageslegacy.race.Talents;
import euf.mageslegacy.shepherd.BeastKind;
import euf.mageslegacy.shepherd.Bonds;
import euf.mageslegacy.shepherd.Creatures;
import euf.mageslegacy.shepherd.EchoType;
import euf.mageslegacy.shepherd.Echoes;
import euf.mageslegacy.shepherd.Shepherd;
import euf.mageslegacy.shepherd.ShepherdData;
import euf.mageslegacy.voidborn.Pocket;
import euf.mageslegacy.voidborn.Voidborn;
import java.util.List;
import java.util.UUID;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.PolarBear;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

@GameTestHolder("mages_legacy")
@PrefixGameTestTemplate(false)
public class RaceGameTests {
 private static final String EMPTY_STRUCTURE = "empty";
	static ServerPlayer mycelid(GameTestHelper ctx, BlockPos at) {
		ServerPlayer player = (ServerPlayer) ctx.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		RaceState.of(player).reset(Race.MYCELID);
		return player;
	}

	static void floor(ServerLevel world, BlockPos from, int sizeX, int sizeZ) {
		for (int x = 0; x < sizeX; x++) {
			for (int z = 0; z < sizeZ; z++) {
				world.setBlockAndUpdate(from.offset(x, -1, z), Blocks.STONE.defaultBlockState());
			}
		}
	}

	static void done(GameTestHelper ctx, ServerPlayer player) {
		player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
		ctx.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void dataSavesAndLevelsGrantPoints(GameTestHelper ctx) {
		RaceData data = new RaceData();
		data.reset(Race.MYCELID);
		data.addResource(42);
		check(data.addXp(RaceData.xpForNext(0) + RaceData.xpForNext(1)) == 2 && data.level == 2, "two levels from exact xp, got " + data.level);
		data.talents.add(Talents.SWARM_WIDE);
		data.tastes.add("minecraft:strength");
		RaceData copy = RaceData.fromNbt(data.toNbt());
		check(copy.race == Race.MYCELID && copy.level == 2 && (int) copy.resource == 42 && copy.has(Talents.SWARM_WIDE)
				&& copy.tastes.contains("minecraft:strength"), "race data must survive a save");
		check(copy.freePoints() == 1, "2 levels = 2 points, 1 spent: " + copy.freePoints());
		data.addXp(1_000_000);
		check(data.level == RaceData.MAX_LEVEL && data.totalPoints() == 60, "level 50 gives 60 points, got " + data.totalPoints());
		data.addResource(500);
		check(data.resource == RaceData.MAX_RESOURCE, "resource is capped at 100");
		ctx.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "choose")
	public void aRaceIsChosenOnce(GameTestHelper ctx) {
		ServerPlayer player = (ServerPlayer) ctx.makeMockServerPlayerInLevel();
		RaceData data = RaceState.of(player);
		check(data.race == null, "a new player has no race");
		check(RacePackets.choose(player, Race.MYCELID) && data.race == Race.MYCELID, "picking the Mycelids should work");
		check(!RacePackets.choose(player, Race.MYCELID), "the choice is made once");
		check(RaceState.get(ctx.getLevel().getServer()).data(player.getUUID()) == data, "the data lives in the saved state");
		done(ctx, player);
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "talents")
	public void talentsNeedPointsPrerequisitesAndMilestones(GameTestHelper ctx) {
		ServerPlayer player = mycelid(ctx, ctx.absolutePos(new BlockPos(1, 2, 1)));
		RaceData data = RaceState.of(player);
		check(Progression.unlock(player, data, Talents.SWARM_WIDE) == UnlockResult.NO_POINTS, "level 0 has no points");
		Progression.addXp(player, data, 1500);
		check(data.freePoints() >= 6, "enough points now: " + data.freePoints());
		check(Progression.unlock(player, data, Talents.SWARM_POTENT) == UnlockResult.NEEDS_TALENT, "the branch goes in order");
		check(Progression.unlock(player, data, Talents.SWARM_WIDE) == UnlockResult.OK && data.has(Talents.SWARM_WIDE), "tier 1 opens at once");
		check(Progression.unlock(player, data, Talents.SWARM_WIDE) == UnlockResult.ALREADY, "no double buy");
		check(Progression.unlock(player, data, Talents.SWARM_POTENT) == UnlockResult.NEEDS_MILESTONE, "tier 2 waits for the Nether");
		check(Progression.unlock(player, data, "nonsense") == UnlockResult.UNKNOWN, "unknown talents are refused");

		AdvancementHolder nether = player.server.getAdvancements().get(ResourceLocation.parse("story/enter_the_nether"));
		for (String criterion : nether.value().criteria().keySet()) {
			player.getAdvancements().award(nether, criterion);
		}
		check(data.level < 10 && Progression.openTiers(player) == 1, "the Nether alone isn't enough below level 10 (level " + data.level + ")");
		Progression.addXp(player, data, 5000);
		check(data.level >= 10 && Progression.openTiers(player) == 2, "the Nether and level 10 open tier 2");
		// A source a farm could feed gives no more than its cap in ten minutes
		RaceData probe = new RaceData();
		probe.race = Race.MYCELID;
		for (int i = 0; i < 20; i++) {
			Progression.addXp(player, probe, "test_farm", 5, 25);
		}
		check(probe.xp == 25, "a capped source stops at its cap, got " + probe.xp);
		Progression.addXp(player, probe, "test_other", 5, 25);
		check(probe.xp == 30, "another source has its own cap, got " + probe.xp);
		int before = data.freePoints();
		check(Progression.unlock(player, data, Talents.SWARM_POTENT) == UnlockResult.OK, "tier 2 after the Nether");
		check(data.freePoints() == before - 2, "tier 2 talents cost 2");
		check(Progression.unlock(player, data, Talents.SWARM_LINGERING) == UnlockResult.NEEDS_TALENT, "infection comes before the long bloom");
		done(ctx, player);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theTreeHasRoomForBuilds(GameTestHelper ctx) {
		treeShape(Race.MYCELID);
		treeShape(Race.SHEPHERD);
		treeShape(Race.FROZEN);
		treeShape(Race.BLOODKIN);
		ctx.succeed();
	}

	private static void treeShape(Race race) {
		List<Talent> all = Talents.of(race);
		check(all.size() == 33, "33 talents for " + race + ", got " + all.size());
		int[] perTier = {0, 3, 4, 3, 1};
		for (int branch = 0; branch < 3; branch++) {
			for (int tier = 1; tier <= 4; tier++) {
				int b = branch;
				int t = tier;
				long count = all.stream().filter(x -> x.branch() == b && x.tier() == t).count();
				check(count == perTier[tier], "branch " + branch + " tier " + tier + " should have " + perTier[tier] + ", has " + count);
			}
		}
		int total = all.stream().mapToInt(Talent::cost).sum();
		check(total == 75 && total > 60, "the whole tree costs 75, more than the 60 points of level 50: " + total);
		for (Talent t : all) {
			if (t.requires() != null) {
				Talent req = Talents.byId(t.requires()).orElse(null);
				check(req != null && req.branch() == t.branch() && req.tier() <= t.tier(), t.id() + " needs a talent of its own branch, not higher");
			}
		}
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "cloud_talents", timeoutTicks = 100)
	public void cloudTalentsWeakenMonstersAndMendPlayers(GameTestHelper ctx) {
		ServerLevel world = ctx.getLevel();
		BlockPos at = ctx.absolutePos(new BlockPos(2, 2, 2));
		floor(world, at.offset(-2, 0, -2), 5, 5);
		ServerPlayer player = mycelid(ctx, at);
		Zombie zombie = EntityType.ZOMBIE.create(world);
		zombie.moveTo(at.getX() + 2.5, at.getY(), at.getZ() + 0.5, 0, 0);
		zombie.setNoAi(true);
		world.addFreshEntity(zombie);
		RaceData data = RaceState.of(player);
		data.talents.add(Talents.SWARM_WEAKNESS);
		data.talents.add(Talents.SWARM_MENDING);
		data.addResource(30);
		check(Mycelid.sporeCloud(player, data) == AbilityResult.OK, "the cloud should rise");
		ctx.runAfterDelay(25, () -> {
			MobEffectInstance weakness = zombie.getEffect(MobEffects.WEAKNESS);
			check(weakness != null && weakness.getAmplifier() == 1, "Rot of Strength: the zombie gets Weakness II");
			MobEffectInstance regen = player.getEffect(MobEffects.REGENERATION);
			check(regen != null && regen.getAmplifier() == 1, "Mending Mist: the player gets Regeneration II");
			zombie.discard();
			done(ctx, player);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "flesh_talents", timeoutTicks = 120)
	public void bodyTalentsChangeDamageHealthAndCosts(GameTestHelper ctx) {
		ServerLevel world = ctx.getLevel();
		BlockPos at = ctx.absolutePos(new BlockPos(2, 2, 2));
		floor(world, at.offset(-2, 0, -2), 5, 5);
		ServerPlayer player = mycelid(ctx, at);
		RaceData data = RaceState.of(player);
		check(Mycelid.jumpCost(data) == 15, "a plain jump costs 15");
		data.talents.add(Talents.NET_CHEAP);
		check(Mycelid.jumpCost(data) == 10, "Light Spore: the jump costs 10");
		data.talents.add(Talents.FLESH_HEARTY);
		data.talents.add(Talents.NET_LIGHT_STEP);
		data.talents.add(Talents.FLESH_FIREPROOF);
		// Spawn protection of new players lasts 3 seconds
		ctx.runAfterDelay(70, () -> {
			Mycelid.tickSecond(player, data);
			check(Math.abs(player.getMaxHealth() - 24) < 0.01f, "Dense Body: 24 max health, got " + player.getMaxHealth());
			player.setHealth(20);
			player.invulnerableTime = 0;
			player.hurt(world.damageSources().fall(), 4);
			check(Math.abs(player.getHealth() - 18) < 0.01f, "Soft Step halves the fall: 20 - 2 = 18, got " + player.getHealth());
			player.setHealth(20);
			player.invulnerableTime = 0;
			player.hurt(world.damageSources().inFire(), 4);
			check(Math.abs(player.getHealth() - 15.4f) < 0.01f, "Damp Flesh: fire 1.15x, 20 - 4.6 = 15.4, got " + player.getHealth());
			Mycelid.removeCapArmor(player);
			check(Math.abs(player.getMaxHealth() - 20) < 0.01f, "the bonus health goes with the race");
			done(ctx, player);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "rebirth", timeoutTicks = 120)
	public void rebirthSproutsAtTheNearestNode(GameTestHelper ctx) {
		ServerLevel world = ctx.getLevel();
		BlockPos at = ctx.absolutePos(new BlockPos(1, 2, 1));
		floor(world, at.offset(-1, 0, -1), 8, 3);
		ServerPlayer player = mycelid(ctx, at);
		RaceData data = RaceState.of(player);
		BlockPos node = at.offset(5, 0, 0);
		Mycelid.placeNodeAt(world, node, player.getUUID(), 3);
		data.talents.add(Talents.FLESH_REBIRTH);
		ctx.runAfterDelay(70, () -> {
			player.invulnerableTime = 0;
			player.hurt(world.damageSources().generic(), 1000);
			check(player.isAlive(), "Rebirth: the killing blow does not kill");
			check(Math.abs(player.getHealth() - player.getMaxHealth() / 2) < 0.01f, "half health after rebirth, got " + player.getHealth());
			check(player.blockPosition().closerThan(node.above(), 1.5), "sprouted at the node, is at " + player.blockPosition());
			check(Mycelid.allowDeath(player), "once in 20 minutes: a second killing blow right away is a real death");
			world.removeBlock(node, false);
			done(ctx, player);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "carpet", timeoutTicks = 100)
	public void theCarpetIsMyceliumAndTheBloomBurstsTheCrowd(GameTestHelper ctx) {
		ServerLevel world = ctx.getLevel();
		BlockPos at = ctx.absolutePos(new BlockPos(4, 2, 4));
		floor(world, at.offset(-4, 0, -4), 9, 9);
		ServerPlayer player = mycelid(ctx, at);
		RaceData data = RaceState.of(player);
		data.addResource(100);
		Zombie zombie = beast(ctx, EntityType.ZOMBIE, at.offset(3, 0, 0));
		// The carpet: the ground around counts as mycelium, and holds the monster on it
		check(Mycelid.use(player, data, 3) == AbilityResult.OK, "the carpet spreads");
		check(Mycelid.onMycelium(player) && Mycelid.onCarpet(zombie), "the Mycelid stands on mycelium, the zombie on the carpet");
		check(Mycelid.use(player, data, 3) == AbilityResult.COOLDOWN, "one carpet at a time");
		// The Great Bloom is the Swarm's crown, on Alt
		check(Mycelid.use(player, data, 4) == AbilityResult.HINTED, "without the crown Alt only says which talent opens it");
		data.talents.add(Talents.SWARM_BLOOM);
		data.addResource(100);
		check(Mycelid.use(player, data, 4) == AbilityResult.OK && zombie.hasEffect(MobEffects.GLOWING), "the bloom infects the zombie");
		int clouds = Mycelid.activeClouds();
		zombie.kill();
		check(Mycelid.activeClouds() == clouds + 1, "the infected zombie bursts into a spore cloud");
		done(ctx, player);
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "scroll")
	public void theScrollOfOblivionGivesEveryPointBack(GameTestHelper ctx) {
		ServerPlayer player = mycelid(ctx, ctx.absolutePos(new BlockPos(1, 2, 1)));
		RaceData data = RaceState.of(player);
		data.addXp(5000);
		int level = data.level;
		data.talents.add(Talents.SWARM_WIDE);
		data.talents.add(Talents.FLESH_HEARTY);
		check(data.freePoints() < data.totalPoints(), "some points spent first");
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MagesLegacy.OBLIVION_SCROLL.get(), 2));
		check(MagesLegacy.OBLIVION_SCROLL.get().use(ctx.getLevel(), player, InteractionHand.MAIN_HAND).getResult().consumesAction(), "the scroll is read");
		check(data.talents.isEmpty() && data.freePoints() == data.totalPoints(), "every talent forgotten, every point back");
		check(data.level == level && data.race == Race.MYCELID, "the race and its level stay");
		check(player.getMainHandItem().getCount() == 1, "one scroll used up");
		check(!MagesLegacy.OBLIVION_SCROLL.get().use(ctx.getLevel(), player, InteractionHand.MAIN_HAND).getResult().consumesAction()
				&& player.getMainHandItem().getCount() == 1, "nothing to forget: the scroll stays");
		done(ctx, player);
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "diet")
	public void mushroomsGiveSporesAndOneTasteAtATime(GameTestHelper ctx) {
		ServerPlayer player = mycelid(ctx, ctx.absolutePos(new BlockPos(1, 2, 1)));
		RaceData data = RaceState.of(player);
		player.getFoodData().setFoodLevel(10);
		ItemStack red = new ItemStack(Items.RED_MUSHROOM, 3);
		check(Mycelid.eat(player, data, red), "a Mycelid eats a raw red mushroom");
		check(red.getCount() == 2 && player.getFoodData().getFoodLevel() == 12, "one mushroom eaten, +2 food");
		check((int) data.resource == 10 && player.hasEffect(MobEffects.DAMAGE_BOOST), "spores and the strength taste");
		check(!Mycelid.eat(player, data, red), "a short pause between mushrooms");
		ItemStack brown = new ItemStack(Items.BROWN_MUSHROOM);
		check(Mycelid.eat(player, data, brown), "a brown mushroom next");
		check(player.hasEffect(MobEffects.DAMAGE_RESISTANCE) && !player.hasEffect(MobEffects.DAMAGE_BOOST),
				"only one taste at a time: the older one fades");

		// The same taste comes back only after its aftertaste: the mushroom still feeds, but gives no strength
		player.getCooldowns().removeCooldown(Items.RED_MUSHROOM);
		float sporesRed = data.resource;
		check(Mycelid.eat(player, data, red), "red again is still food");
		check(data.resource > sporesRed && !player.hasEffect(MobEffects.DAMAGE_BOOST), "but the strength taste waits for its aftertaste");

		data.talents.add(Talents.FLESH_TWO_TASTES);
		check(Mycelid.eat(player, data, new ItemStack(Items.WARPED_FUNGUS)), "a warped fungus");
		check(player.hasEffect(MobEffects.DAMAGE_RESISTANCE) && player.hasEffect(MobEffects.MOVEMENT_SPEED), "two tastes with the talent");
		check(player.getEffect(MobEffects.MOVEMENT_SPEED).getDuration() <= 900, "a taste lasts 45 seconds");

		// Meat feeds a Mycelid half as well (8 hunger -> 4); goes through the eatFood mixin
		player.getFoodData().setFoodLevel(4);
		player.eat(ctx.getLevel(), new ItemStack(Items.COOKED_BEEF));
		check(player.getFoodData().getFoodLevel() == 8, "steak should give 4 food to a Mycelid, food is " + player.getFoodData().getFoodLevel());
		float sporesBefore = data.resource;
		player.eat(ctx.getLevel(), new ItemStack(Items.MUSHROOM_STEW));
		check(data.resource >= sporesBefore + 14, "a mushroom stew feeds the spores");
		done(ctx, player);
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "jump", timeoutTicks = 200)
	public void sporeJumpSproutsWhereYouLook(GameTestHelper ctx) {
		ServerLevel world = ctx.getLevel();
		BlockPos start = ctx.absolutePos(new BlockPos(1, 2, 0));
		floor(world, start.offset(-1, 0, 0), 3, 30);
		ServerPlayer player = mycelid(ctx, start);
		// Yaw 0 looks south (+Z), a little down so the spore lands on the floor
		player.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0, 25);
		RaceData data = RaceState.of(player);
		data.addResource(40);
		Vec3 from = player.position();
		check(Mycelid.sporeJump(player, data) == AbilityResult.OK, "the jump should start");
		check((int) data.resource == 25, "a jump costs 15 spores");
		check(Mycelid.sporeJump(player, data) == AbilityResult.COOLDOWN, "no second jump right away");
		ctx.runAfterDelay(Mycelid.JUMP_DELAY + 3, () -> {
			double moved = player.position().subtract(from).horizontalDistance();
			check(moved > 2 && moved <= 16.6, "the player should sprout up to 16 blocks ahead, moved " + moved);
			check(player.getZ() > from.z, "forward is south, now at " + player.position());
			check(!world.noCollision(player, player.getBoundingBox().move(0, -0.3, 0)), "landed on the floor");
			// Looking almost level: the spore flies the full reach and the Mycelid settles on the floor there
			net.minecraft.world.phys.Vec3 second = player.position();
			player.moveTo(second.x, second.y, second.z, 0, 3);
			data.readyAt.clear();
			data.chainLeft = 0;
			check(Mycelid.sporeJump(player, data) == AbilityResult.OK, "the level jump should start");
			ctx.runAfterDelay(Mycelid.JUMP_DELAY + 3, () -> {
				double far = player.position().subtract(second).horizontalDistance();
				check(far > 12 && far <= 16.6, "a level jump should cover most of the 16 blocks, moved " + far);
				// Looking a little up into the open sky: the spores drift down and settle on the floor far ahead, never in
				// the air and never right at the Mycelid's feet
				data.addResource(100);
				net.minecraft.world.phys.Vec3 third = player.position();
				player.moveTo(third.x, third.y, third.z, 180, -15);
				data.readyAt.clear();
				data.chainLeft = 0;
				AbilityResult up = Mycelid.sporeJump(player, data);
				ctx.runAfterDelay(Mycelid.JUMP_DELAY + 3, () -> {
					double upward = player.position().subtract(third).horizontalDistance();
					check(up == AbilityResult.OK && upward > 10, "a jump aimed a little up carries far, moved " + upward + " (" + up + ")");
					check(!world.noCollision(player, player.getBoundingBox().move(0, -0.3, 0)),
							"never left hanging in the air, now at " + player.position());
					done(ctx, player);
				});
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "cloud", timeoutTicks = 100)
	public void sporeCloudChokesMonstersAndHealsPlayers(GameTestHelper ctx) {
		ServerLevel world = ctx.getLevel();
		BlockPos at = ctx.absolutePos(new BlockPos(2, 2, 2));
		floor(world, at.offset(-2, 0, -2), 5, 5);
		ServerPlayer player = mycelid(ctx, at);
		Zombie zombie = EntityType.ZOMBIE.create(world);
		zombie.moveTo(at.getX() + 2.5, at.getY(), at.getZ() + 0.5, 0, 0);
		zombie.setNoAi(true);
		world.addFreshEntity(zombie);
		RaceData data = RaceState.of(player);
		data.addResource(30);
		check(Mycelid.sporeCloud(player, data) == AbilityResult.OK, "the cloud should rise");
		check(data.resource < 1, "a cloud costs 30 spores");
		check(Mycelid.sporeCloud(player, data) == AbilityResult.COOLDOWN, "one cloud at a time");
		ctx.runAfterDelay(25, () -> {
			check(zombie.hasEffect(MobEffects.BLINDNESS) && zombie.hasEffect(MobEffects.WITHER),
					"the zombie goes blind and rots (the undead are immune to poison)");
			check(player.hasEffect(MobEffects.REGENERATION), "the Mycelid breathes them in and heals");
			zombie.discard();
			done(ctx, player);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "dark", timeoutTicks = 200)
	public void darknessFeedsAndFireBurns(GameTestHelper ctx) {
		ServerLevel world = ctx.getLevel();
		BlockPos at = ctx.absolutePos(new BlockPos(2, 2, 2));
		// A closed stone box: no light gets in
		for (int x = -1; x <= 1; x++) {
			for (int y = -1; y <= 2; y++) {
				for (int z = -1; z <= 1; z++) {
					if (x != 0 || z != 0 || y == -1 || y == 2) {
						world.setBlockAndUpdate(at.offset(x, y, z), Blocks.STONE.defaultBlockState());
					}
				}
			}
		}
		world.setBlockAndUpdate(at.below(), Blocks.MYCELIUM.defaultBlockState());
		ServerPlayer player = mycelid(ctx, at);
		RaceData data = RaceState.of(player);
		ServerPlayer human = (ServerPlayer) ctx.makeMockServerPlayerInLevel();
		human.setGameMode(GameType.SURVIVAL);
		// Wait out the light update in the box and the 3 s of spawn protection new players get
		ctx.runAfterDelay(70, () -> {
			check(Mycelid.inDark(player) && Mycelid.onMycelium(player), "inside a box on mycelium it is dark");
			float before = data.resource;
			Mycelid.tickSecond(player, data);
			check(player.hasEffect(MobEffects.NIGHT_VISION) && player.hasEffect(MobEffects.REGENERATION),
					"night vision and regeneration in the dark");
			check(Math.abs(data.resource - before - 2.0f) < 0.01f, "0.5 + 0.5 dark + 1 mycelium = 2 spores a second, got " + (data.resource - before));

			player.setHealth(20);
			player.invulnerableTime = 0;
			player.hurt(world.damageSources().inFire(), 4);
			check(Math.abs(player.getHealth() - 14) < 0.01f, "fire hurts a Mycelid 1.5x: 20 - 6 = 14, got " + player.getHealth());
			human.setHealth(20);
			human.hurt(world.damageSources().inFire(), 4);
			check(Math.abs(human.getHealth() - 16) < 0.01f, "a player without a race takes plain fire damage, got " + human.getHealth());
			world.removePlayerImmediately(human, Entity.RemovalReason.DISCARDED);
			done(ctx, player);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "nodes")
	public void nodesFormANetworkAnyoneCanTravel(GameTestHelper ctx) {
		ServerLevel world = ctx.getLevel();
		BlockPos base = ctx.absolutePos(new BlockPos(0, 2, 0));
		floor(world, base, 12, 3);
		ServerPlayer owner = mycelid(ctx, base);
		List<BlockPos> spots = List.of(base, base.offset(3, 0, 0), base.offset(6, 0, 0), base.offset(9, 0, 0));
		for (BlockPos spot : spots) {
			Mycelid.placeNodeAt(world, spot, owner.getUUID(), 3);
		}
		RaceState state = RaceState.get(world.getServer());
		check(!world.getBlockState(spots.get(0)).is(MagesLegacy.MYCELIUM_NODE.get()), "past the limit the oldest node withers");
		check(state.nodes(owner.getUUID()).size() == 3, "three nodes in the network");

		// A stranger uses the network: from node 2 to node 3
		ServerPlayer guest = (ServerPlayer) ctx.makeMockServerPlayerInLevel();
		guest.moveTo(spots.get(1).getX() + 1.5, spots.get(1).getY(), spots.get(1).getZ() + 1.5, 0, 0);
		check(Mycelid.travel(guest, spots.get(1)), "travel through a node");
		check(guest.blockPosition().closerThan(spots.get(2), 1.5), "the guest should come out at the next node, is at " + guest.blockPosition());

		world.destroyBlock(spots.get(2), false);
		check(!state.nodes(owner.getUUID()).contains(GlobalPos.of(world.dimension(), spots.get(2))), "a broken node leaves the network");
		check(state.nodeOwner(world.dimension(), spots.get(3)).orElse(null).equals(owner.getUUID()), "nodes know their owner");
		world.removePlayerImmediately(guest, Entity.RemovalReason.DISCARDED);
		for (BlockPos spot : spots) {
			world.removeBlock(spot, false);
		}
		done(ctx, owner);
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "node_farm")
	public void walkingBetweenNodesFarmsNothing(GameTestHelper ctx) {
		ServerLevel world = ctx.getLevel();
		BlockPos base = ctx.absolutePos(new BlockPos(0, 2, 0));
		floor(world, base, 8, 3);
		ServerPlayer player = mycelid(ctx, base);
		RaceData data = RaceState.of(player);
		data.talents.add(Talents.NET_SHARED);
		BlockPos a = base;
		BlockPos b = base.offset(5, 0, 0);
		Mycelid.placeNodeAt(world, a, player.getUUID(), 3);
		Mycelid.placeNodeAt(world, b, player.getUUID(), 3);
		int xp = data.xp;
		check(Mycelid.travel(player, a) && Mycelid.travel(player, b) && Mycelid.travel(player, a), "back and forth works");
		check(data.xp == xp, "travelling gives no race experience: " + xp + " -> " + data.xp);
		check(Math.abs(data.resource - 10) < 0.01f, "Fed Network pays 10 spores once, not on every trip: " + data.resource);
		world.removeBlock(a, false);
		world.removeBlock(b, false);
		done(ctx, player);
	}

	// ---- Shepherds ----

	static ServerPlayer shepherd(GameTestHelper ctx, BlockPos at) {
		ServerPlayer player = (ServerPlayer) ctx.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		RaceState.of(player).reset(Race.SHEPHERD);
		return player;
	}

	static void learn(ServerPlayer player, RaceData data, EchoType type) {
		for (int i = 0; i < type.kills(); i++) {
			Shepherd.countKill(player, data, type);
		}
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "echo_learn")
	public void echoesAreLearnedFromKills(GameTestHelper ctx) {
		ServerPlayer player = shepherd(ctx, ctx.absolutePos(new BlockPos(1, 2, 1)));
		RaceData data = RaceState.of(player);
		EchoType zombie = EchoType.of(EntityType.ZOMBIE).orElseThrow();
		for (int i = 0; i < zombie.kills() - 1; i++) {
			Shepherd.countKill(player, data, zombie);
		}
		check(!Shepherd.learned(data, zombie), "19 zombies are not enough");
		check(Shepherd.call(player, data) == AbilityResult.NO_ECHO, "nothing to call yet");
		int xp = data.xp + data.level * 1000;
		Shepherd.countKill(player, data, zombie);
		check(Shepherd.learned(data, zombie), "the 20th zombie teaches its echo");
		check(data.shepherd.presets.get(0).getOrDefault(zombie.id(), 0) == 1, "the first learned echo goes into the first squad");
		check(data.xp + data.level * 1000 > xp, "learning an echo gives race experience");
		RaceData copy = RaceData.fromNbt(data.toNbt());
		check(copy.shepherd.kills(zombie.id()) == 20 && copy.shepherd.presets.get(0).getOrDefault(zombie.id(), 0) == 1, "the bestiary survives a save");
		done(ctx, player);
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "echo_call", timeoutTicks = 200)
	public void theCallRaisesAnEchoThatFadesWithoutLoot(GameTestHelper ctx) {
		ServerLevel world = ctx.getLevel();
		BlockPos at = ctx.absolutePos(new BlockPos(3, 2, 3));
		floor(world, at.offset(-3, 0, -3), 7, 7);
		ServerPlayer player = shepherd(ctx, at);
		RaceData data = RaceState.of(player);
		learn(player, data, EchoType.of(EntityType.ZOMBIE).orElseThrow());
		data.addResource(100);
		check(Shepherd.call(player, data) == AbilityResult.OK, "the Call should raise the echo");
		check(Math.abs(data.resource - 85) < 0.01f, "a zombie echo costs 15 voice, left " + data.resource);
		check(Echoes.count(player.getUUID()) == 1 && Shepherd.capacityUsed(data, player.getUUID()) == 1, "one echo, one bond slot");
		Mob echo = world.getEntitiesOfClass(Mob.class, new AABB(at).inflate(8), Echoes::isEcho).stream().findFirst().orElse(null);
		check(echo != null, "the echo stands in the world");
		ctx.runAfterDelay(70, () -> {
			float before = echo.getHealth();
			echo.invulnerableTime = 0;
			echo.hurt(world.damageSources().playerAttack(player), 4);
			check(echo.getHealth() == before, "a Shepherd can't hurt its own echo");
			echo.invulnerableTime = 0;
			echo.hurt(world.damageSources().generic(), 1000);
			check(!echo.isAlive() && Echoes.count(player.getUUID()) == 0, "a fallen echo fades");
			check(world.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(8), e -> true).isEmpty(), "an echo drops nothing");
			done(ctx, player);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "echo_capacity", timeoutTicks = 100)
	public void heavyArmourCrowdsTheBonds(GameTestHelper ctx) {
		ServerLevel world = ctx.getLevel();
		BlockPos at = ctx.absolutePos(new BlockPos(3, 2, 3));
		floor(world, at.offset(-3, 0, -3), 7, 7);
		ServerPlayer player = shepherd(ctx, at);
		RaceData data = RaceState.of(player);
		check(Shepherd.capacityMax(player, data) == 4, "4 bond slots to start");
		player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
		player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
		player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
		check(Shepherd.capacityMax(player, data) == 1, "2 diamond + 2 iron = 6 points = 3 slots, got " + Shepherd.capacityMax(player, data));
		data.talents.add(Talents.VOICE_ARMOR);
		player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
		check(Shepherd.capacityMax(player, data) == 3, "Used to Iron: only the diamond chestplate counts (2 points = 1 slot)");
		learn(player, data, EchoType.of(EntityType.SKELETON).orElseThrow());
		data.shepherd.presets.get(0).clear();
		data.shepherd.presets.get(0).put(EchoType.of(EntityType.SKELETON).orElseThrow().id(), 2);
		data.addResource(100);
		check(Shepherd.call(player, data) == AbilityResult.OK && Echoes.count(player.getUUID()) == 1, "one skeleton fits (2 of 3 slots), the other doesn't");
		data.readyAt.clear();
		check(Shepherd.call(player, data) == AbilityResult.NO_CAPACITY, "no room for the second one"); 
		data.alt = true;
		check(Shepherd.call(player, data) == AbilityResult.OK && Echoes.count(player.getUUID()) == 0, "sneak + Call sends them back");
		data.alt = false;
		done(ctx, player);
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "bond", timeoutTicks = 200)
	public void theBondRitualNeedsSongFoodAndFire(GameTestHelper ctx) {
		ServerLevel world = ctx.getLevel();
		BlockPos at = ctx.absolutePos(new BlockPos(3, 2, 3));
		holdChunks(world, at);
		floor(world, at.offset(-3, 0, -3), 7, 7);
		ServerPlayer player = shepherd(ctx, at);
		RaceData data = RaceState.of(player);
		data.addResource(100);
		Wolf wolf = EntityType.WOLF.create(world);
		wolf.moveTo(at.getX() + 2.5, at.getY(), at.getZ() + 0.5, 0, 0);
		wolf.setNoAi(true);
		world.addFreshEntity(wolf);

		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BEEF, 8));
		check(Bonds.interact(player, data, wolf, InteractionHand.MAIN_HAND) == InteractionResult.PASS, "food before the Song does nothing special");
		// The Song is its own ability now: on the beast in sight (yaw 0 looks south, +Z), then a cooldown
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		check(Bonds.song(player, data) == AbilityResult.NO_BEAST, "no beast in sight, no Song");
		wolf.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 2.5, 0, 0);
		// A wolf is low: look down at it
		player.setXRot(30);
		check(Bonds.song(player, data) == AbilityResult.OK, "the Song of Bonds on the wolf ahead");
		player.setXRot(0);
		check(Math.abs(data.resource - 80) < 0.01f, "the Song costs 20 voice");
		check(Bonds.song(player, data) == AbilityResult.COOLDOWN, "and then waits 30 seconds");
		player.setShiftKeyDown(true);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		check(Bonds.interact(player, data, wolf, InteractionHand.MAIN_HAND) == InteractionResult.PASS, "sneak + right click no longer sings");
		player.setShiftKeyDown(false);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BEEF, 8));
		check(Bonds.interact(player, data, wolf, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS, "first feeding");
		check(Bonds.interact(player, data, wolf, InteractionHand.MAIN_HAND) == InteractionResult.FAIL, "not again right away");
		ShepherdData.Ritual ritual = data.shepherd.rituals.get(wolf.getUUID());
		ritual.lastFeed -= Bonds.FEED_GAP;
		check(Bonds.interact(player, data, wolf, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS, "second feeding a minute later");
		ritual.lastFeed -= Bonds.FEED_GAP;
		check(Bonds.interact(player, data, wolf, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS, "third feeding");
		ritual.lastFeed -= Bonds.FEED_GAP;
		check(Bonds.interact(player, data, wolf, InteractionHand.MAIN_HAND) == InteractionResult.FAIL, "the last one needs a campfire");
		world.setBlockAndUpdate(at.offset(-2, 0, 0), Blocks.CAMPFIRE.defaultBlockState());
		check(Bonds.interact(player, data, wolf, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS, "by the fire the bond is made");
		check(data.shepherd.bonds.size() == 1 && wolf.isTame() && player.getUUID().equals(wolf.getOwnerUUID()), "the wolf is the Shepherd's");
		check(Shepherd.capacityUsed(data, player.getUUID()) == 2, "a wolf takes 2 bond slots");
		check(player.getMainHandItem().getCount() == 4, "four pieces of meat eaten");

		// It falls: rests instead of dying, and comes back when the rest is over
		wolf.invulnerableTime = 0;
		wolf.hurt(world.damageSources().generic(), 1000);
		ShepherdData.Bond bond = data.shepherd.bonds.get(0);
		check(wolf.isRemoved() && bond.resting(), "a fallen beast goes to rest");
		bond.restingUntil = world.getGameTime();
		Bonds.tickOwner(player, data);
		check(!bond.resting(), "the rest is over");
		// A freshly spawned body may take a tick or two to show in the test world's books
		ctx.runAtTickTime(ctx.getTick() + 3, () -> {
			check(world.getEntity(bond.entity) instanceof Wolf back && back.isAlive(), "and comes back to its Shepherd");
			world.getEntity(bond.entity).discard();
			world.removeBlock(at.offset(-2, 0, 0), false);
			releaseChunks(world, at);
			done(ctx, player);
		});
	}

	static <T extends Mob> T beast(GameTestHelper ctx, EntityType<T> type, BlockPos at) {
		T mob = type.create(ctx.getLevel());
		mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		mob.setNoAi(true);
		ctx.getLevel().addFreshEntity(mob);
		return mob;
	}

	static void sing(ServerPlayer player, RaceData data, Mob mob) {
		Bonds.singTo(player, data, mob);
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "taming", timeoutTicks = 200)
	public void everyBeastIsWonItsOwnWay(GameTestHelper ctx) {
		ServerLevel world = ctx.getLevel();
		BlockPos at = ctx.absolutePos(new BlockPos(4, 2, 4));
		floor(world, at.offset(-4, 0, -4), 9, 9);
		ServerPlayer player = shepherd(ctx, at);
		RaceData data = RaceState.of(player);
		data.talents.add(Talents.VOICE_CAPACITY);
		data.talents.add(Talents.LEGION_CAPACITY);
		data.talents.add(Talents.LEGION_MORE);

		// The bee: two different flowers
		Bee bee = beast(ctx, EntityType.BEE, at.offset(2, 1, 0));
		data.addResource(100);
		sing(player, data, bee);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.POPPY, 4));
		check(Bonds.interact(player, data, bee, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS, "the first flower");
		check(Bonds.interact(player, data, bee, InteractionHand.MAIN_HAND) == InteractionResult.FAIL, "not the same flower twice");
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DANDELION));
		check(Bonds.interact(player, data, bee, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS && data.shepherd.bond(bee.getUUID()).isEmpty(),
				"a second kind is not yet enough");
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CORNFLOWER));
		check(Bonds.interact(player, data, bee, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS && data.shepherd.bond(bee.getUUID()).isPresent(),
				"a third kind of flower wins the bee");

		// The goat: three bare-handed headbutts, then wheat
		Goat goat = beast(ctx, EntityType.GOAT, at.offset(-2, 0, 0));
		data.addResource(100);
		sing(player, data, goat);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT));
		check(Bonds.interact(player, data, goat, InteractionHand.MAIN_HAND) == InteractionResult.FAIL, "wheat before the headbutts is not enough");
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		for (int i = 0; i < 5; i++) {
			data.shepherd.rituals.get(goat.getUUID()).lastFeed -= 20;
			check(Bonds.headbutt(player, data, goat), "a headbutt counts and is cancelled");
		}
		check(goat.getHealth() == goat.getMaxHealth(), "the headbutts did no harm");
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT));
		check(Bonds.interact(player, data, goat, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS && data.shepherd.bond(goat.getUUID()).isPresent(),
				"then wheat wins the goat");

		// The parrot: three songs a minute apart, with music playing
		Parrot parrot = beast(ctx, EntityType.PARROT, at.offset(0, 0, 2));
		data.addResource(100);
		check(Bonds.singTo(player, data, parrot) == AbilityResult.HINTED, "no music, no listening");
		world.setBlockAndUpdate(at.offset(0, 0, 4), Blocks.JUKEBOX.defaultBlockState().setValue(JukeboxBlock.HAS_RECORD, true));
		for (int i = 0; i < 3; i++) {
			data.addResource(100);
			sing(player, data, parrot);
			ShepherdData.Ritual r = data.shepherd.rituals.get(parrot.getUUID());
			if (r != null) {
				r.lastFeed -= Bonds.FEED_GAP;
			}
		}
		check(data.shepherd.bond(parrot.getUUID()).isPresent() && parrot.isTame(), "three songs win the parrot");

		// The cat: crouching beside it with fish for ten seconds
		Cat cat = beast(ctx, EntityType.CAT, at.offset(1, 0, -1));
		data.addResource(100);
		sing(player, data, cat);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COD, 2));
		player.setShiftKeyDown(true);
		for (int i = 0; i < 20; i++) {
			Bonds.tickOwner(player, data);
		}
		player.setShiftKeyDown(false);
		check(data.shepherd.bond(cat.getUUID()).isPresent() && player.getMainHandItem().getCount() == 1, "patience wins the cat, for one fish");

		// The polar bear: three monsters killed in front of it
		PolarBear bear = beast(ctx, EntityType.POLAR_BEAR, at.offset(-2, 0, 2));
		data.addResource(100);
		sing(player, data, bear);
		for (int i = 0; i < 5; i++) {
			Zombie zombie = beast(ctx, EntityType.ZOMBIE, at.offset(-3, 0, 3));
			Bonds.onHunt(player, data, zombie);
			zombie.discard();
		}
		check(data.shepherd.bond(bear.getUUID()).isPresent(), "strength wins the polar bear");

		// The fox only listens while she sleeps
		Fox fox = beast(ctx, EntityType.FOX, at.offset(3, 0, 3));
		data.addResource(100);
		check(Bonds.singTo(player, data, fox) == AbilityResult.HINTED, "an awake fox won't listen");
		for (Mob m : List.of(bee, goat, parrot, cat, bear, fox)) {
			m.discard();
		}
		done(ctx, player);
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "care", timeoutTicks = 200)
	public void bondedBeastsArePettedFedAndToldToStay(GameTestHelper ctx) {
		ServerLevel world = ctx.getLevel();
		BlockPos at = ctx.absolutePos(new BlockPos(4, 2, 4));
		floor(world, at.offset(-4, 0, -4), 9, 9);
		ServerPlayer player = shepherd(ctx, at);
		RaceData data = RaceState.of(player);
		data.addResource(100);
		Rabbit rabbit = beast(ctx, EntityType.RABBIT, at.offset(2, 0, 0));
		sing(player, data, rabbit);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_CARROT, 2));
		Bonds.interact(player, data, rabbit, InteractionHand.MAIN_HAND);
		data.shepherd.rituals.get(rabbit.getUUID()).lastFeed -= Bonds.FEED_GAP;
		Bonds.interact(player, data, rabbit, InteractionHand.MAIN_HAND);
		ShepherdData.Bond bond = data.shepherd.bond(rabbit.getUUID()).orElseThrow();
		check(Bonds.singTo(player, data, rabbit) == AbilityResult.HINTED, "singing to your own beast only reminds how to care for it");

		// Petting: hearts always, the reward once in two minutes
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		float voice = data.resource;
		int xp = bond.xp + bond.level * 1000;
		check(Bonds.interact(player, data, rabbit, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS, "petting");
		check(data.resource > voice && bond.xp + bond.level * 1000 > xp, "petting warms the voice and teaches the beast");
		voice = data.resource;
		Bonds.interact(player, data, rabbit, InteractionHand.MAIN_HAND);
		check(data.resource == voice, "petting again right away gives nothing more");

		// Feeding: its treat heals it and keeps it fed; a fed, healthy beast refuses more
		rabbit.setHealth(1);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CARROT, 3));
		check(Bonds.interact(player, data, rabbit, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS && rabbit.getHealth() > 1, "a carrot heals the rabbit");
		long now = world.getGameTime();
		check(bond.fedUntil > now && Bonds.fedBonus(data, bond, now) > 1, "the rabbit is fed and bites harder");
		rabbit.setHealth(rabbit.getMaxHealth());
		check(Bonds.interact(player, data, rabbit, InteractionHand.MAIN_HAND) == InteractionResult.FAIL && player.getMainHandItem().getCount() == 2,
				"a fed, healthy beast won't eat more");

		// Sneak + right click: it stays, and doesn't jump back to its Shepherd
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.setShiftKeyDown(true);
		Bonds.interact(player, data, rabbit, InteractionHand.MAIN_HAND);
		player.setShiftKeyDown(false);
		check(bond.staying && rabbit.getTags().contains(Creatures.STAY_TAG), "told to stay");
		check(RaceData.fromNbt(data.toNbt()).shepherd.bonds.get(0).staying, "staying survives a save");

		// Not Today: the first killing blow is shrugged off, the next one sends it to rest
		data.talents.add(Talents.PACK_NOT_TODAY);
		rabbit.invulnerableTime = 0;
		rabbit.hurt(world.damageSources().generic(), 1000);
		check(rabbit.isAlive() && !bond.resting() && rabbit.getHealth() > 0, "Not Today keeps the rabbit in the fight");
		rabbit.invulnerableTime = 0;
		rabbit.removeAllEffects();
		rabbit.hurt(world.damageSources().generic(), 1000);
		check(bond.resting(), "but only once in five minutes");
		bond.body = null;

		// Last Breath: a falling echo bursts into frost
		data.talents.add(Talents.LEGION_LAST_BREATH);
		EchoType zombieType = EchoType.of(EntityType.ZOMBIE).orElseThrow();
		learn(player, data, zombieType);
		Mob echo = Echoes.summon(player, data, zombieType, Vec3.atBottomCenterOf(at.offset(-2, 0, 0)), false, 0, 0);
		Zombie foe = beast(ctx, EntityType.ZOMBIE, at.offset(-3, 0, 1));
		float before = foe.getHealth();
		echo.invulnerableTime = 0;
		echo.hurt(world.damageSources().generic(), 1000);
		check(foe.getHealth() < before && foe.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the frost hurts and slows the zombie nearby");
		foe.discard();
		Echoes.dismiss(world.getServer(), player.getUUID());
		done(ctx, player);
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "echo_traits", timeoutTicks = 100)
	public void everyEchoHitsInItsOwnWay(GameTestHelper ctx) {
		ServerLevel world = ctx.getLevel();
		BlockPos at = ctx.absolutePos(new BlockPos(3, 2, 3));
		floor(world, at.offset(-3, 0, -3), 7, 7);
		ServerPlayer player = shepherd(ctx, at);
		RaceData data = RaceState.of(player);
		Mob vindicator = Echoes.summon(player, data, EchoType.of(EntityType.VINDICATOR).orElseThrow(), Vec3.atBottomCenterOf(at.offset(2, 0, 0)),
				false, 0, 0);
		Mob stray = Echoes.summon(player, data, EchoType.of(EntityType.STRAY).orElseThrow(), Vec3.atBottomCenterOf(at.offset(-2, 0, 0)),
				false, 0, 0);
		Zombie zombie = beast(ctx, EntityType.ZOMBIE, at.offset(0, 0, 2));
		check(Echoes.onHit(vindicator, zombie, 4) == 4, "a healthy foe takes the plain blow");
		zombie.setHealth(5);
		check(Echoes.onHit(vindicator, zombie, 4) == 8, "the Executioner doubles it on the nearly dead");
		Echoes.onHit(stray, zombie, 2);
		MobEffectInstance slow = zombie.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
		check(slow != null && slow.getAmplifier() == 2, "a stray echo's arrows freeze: Slowness III");
		Echoes.dismiss(world.getServer(), player.getUUID());
		zombie.discard();
		done(ctx, player);
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "squad", timeoutTicks = 100)
	public void theCallBringsTheWholeSquadAtOnce(GameTestHelper ctx) {
		ServerLevel world = ctx.getLevel();
		BlockPos at = ctx.absolutePos(new BlockPos(4, 2, 4));
		floor(world, at.offset(-4, 0, -4), 9, 9);
		ServerPlayer player = shepherd(ctx, at);
		RaceData data = RaceState.of(player);
		EchoType zombie = EchoType.of(EntityType.ZOMBIE).orElseThrow();
		EchoType silverfish = EchoType.of(EntityType.SILVERFISH).orElseThrow();
		learn(player, data, zombie);
		learn(player, data, silverfish);
		check(Shepherd.editPreset(data, 0, zombie.id(), 1) && Shepherd.editPreset(data, 0, silverfish.id(), 1), "the squad is edited");
		check(!Shepherd.editPreset(data, 0, EchoType.of(EntityType.RAVAGER).orElseThrow().id(), 1), "an unlearned echo can't join");
		data.addResource(100);
		check(Shepherd.call(player, data) == AbilityResult.OK, "the Call");
		check(Echoes.count(player.getUUID()) == 5, "two zombies and three silverfish at once, got " + Echoes.count(player.getUUID()));
		check(Shepherd.capacityUsed(data, player.getUUID()) == 3, "the three silverfish share one bond slot");
		check(Math.abs(data.resource - 60) < 0.01f, "2 x 15 + 10 voice, left " + data.resource);
		data.readyAt.clear();
		Shepherd.call(player, data);
		check(Echoes.count(player.getUUID()) == 5 && Math.abs(data.resource - 60) < 0.01f, "a full squad isn't called twice");
		Shepherd.selectPreset(player, data, 1);
		data.readyAt.clear();
		check(Shepherd.call(player, data) == AbilityResult.NO_ECHO, "squad II is empty");
		Echoes.dismiss(world.getServer(), player.getUUID());
		done(ctx, player);
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "taming2", timeoutTicks = 100)
	public void theLlamaWalksAndTheRabbitTakesAGift(GameTestHelper ctx) {
		ServerLevel world = ctx.getLevel();
		BlockPos at = ctx.absolutePos(new BlockPos(4, 2, 4));
		floor(world, at.offset(-4, 0, -4), 9, 9);
		ServerPlayer player = shepherd(ctx, at);
		RaceData data = RaceState.of(player);
		Rabbit rabbit = beast(ctx, EntityType.RABBIT, at.offset(2, 0, 0));
		data.addResource(100);
		sing(player, data, rabbit);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_CARROT, 2));
		check(Bonds.interact(player, data, rabbit, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS, "the first golden carrot");
		data.shepherd.rituals.get(rabbit.getUUID()).lastFeed -= Bonds.FEED_GAP;
		check(Bonds.interact(player, data, rabbit, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS && data.shepherd.bond(rabbit.getUUID()).isPresent(),
				"the second wins the rabbit");
		Llama llama = beast(ctx, EntityType.LLAMA, at.offset(-2, 0, 0));
		data.addResource(100);
		sing(player, data, llama);
		llama.setLeashedTo(player, true);
		for (int i = 0; i < 120; i++) {
			Bonds.tickOwner(player, data);
		}
		check(data.shepherd.bond(llama.getUUID()).isPresent(), "two minutes on the lead win the llama");

		// Beasts grow from what happens around them; levels and the leader make their skill stronger; names stick
		ShepherdData.Bond bond = data.shepherd.bond(rabbit.getUUID()).orElseThrow();
		check(Bonds.power(data, bond) == 0, "a fresh beast has power 0");
		for (int i = 0; i < 12; i++) {
			Zombie zombie = beast(ctx, EntityType.ZOMBIE, at.offset(1, 0, 1));
			Shepherd.onKill(player, zombie);
			zombie.discard();
		}
		check(bond.level >= 2, "a rabbit that never fights still learns from kills nearby: level " + bond.level);
		while (bond.level < 5) {
			Bonds.beastXp(player, data, bond, rabbit, 50);
		}
		check(Bonds.power(data, bond) == 1, "level 5 gives power 1");
		data.talents.add(Talents.PACK_ALPHA);
		Bonds.toggleLeader(data, data.shepherd.bonds.indexOf(bond));
		check(bond.leader && Bonds.power(data, bond) == 3, "the chosen leader with the Alpha crown: +2 power");
		Bonds.rename(player, data, data.shepherd.bonds.indexOf(bond), "Пушок");
		check("Пушок".equals(rabbit.getCustomName().getString()) && bond.name.equals("Пушок"), "the name is on the beast and in the bond");
		rabbit.discard();
		llama.discard();
		done(ctx, player);
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "whistle", timeoutTicks = 100)
	public void theWhistleOrdersThePackAndTheRallyHealsIt(GameTestHelper ctx) {
		ServerLevel world = ctx.getLevel();
		BlockPos at = ctx.absolutePos(new BlockPos(4, 2, 4));
		floor(world, at.offset(-4, 0, -4), 9, 9);
		ServerPlayer player = shepherd(ctx, at);
		RaceData data = RaceState.of(player);
		EchoType zombieType = EchoType.of(EntityType.ZOMBIE).orElseThrow();
		learn(player, data, zombieType);
		Mob echo = Echoes.summon(player, data, zombieType, Vec3.atBottomCenterOf(at.offset(-2, 0, 0)), false, 0, 0);
		Zombie foe = beast(ctx, EntityType.ZOMBIE, at.offset(3, 0, 3));

		// The Whistle: Guard -> Hunt -> Follow, for free
		check(data.shepherd.stance == ShepherdData.GUARD, "the pack starts on Guard");
		float guarded = Shepherd.modifyDamage(echo, world.damageSources().mobAttack(foe), 10);
		check(Math.abs(guarded - 8) < 0.01f, "on Guard a creature takes 20% less, got " + guarded);
		data.addResource(-data.resource);
		check(Shepherd.whistle(player, data) == AbilityResult.OK && data.shepherd.stance == ShepherdData.HUNT, "Hunt, and it costs no voice");
		Shepherd.tickSecond(player, data);
		check(echo.getTarget() == foe, "on Hunt the echo goes for the zombie four blocks away");
		data.readyAt.clear();
		Shepherd.whistle(player, data);
		check(data.shepherd.stance == ShepherdData.FOLLOW && echo.getTarget() == null, "Follow calls it back from a foe that hurts nobody");
		check(RaceData.fromNbt(data.toNbt()).shepherd.stance == ShepherdData.FOLLOW, "the order survives a save");

		// The Rally heals the pack a lot and the Shepherd a little
		echo.setHealth(2);
		player.setHealth(6);
		data.addResource(100);
		check(Shepherd.rally(player, data) == AbilityResult.OK, "the Rally");
		check(echo.getHealth() >= 2 + echo.getMaxHealth() * Shepherd.RALLY_HEAL - 0.01f, "the echo heals 30%, now " + echo.getHealth());
		check(Math.abs(player.getHealth() - 10) < 0.01f, "the Shepherd heals 2 hearts, now " + player.getHealth());
		foe.discard();

		// Whistling the pack back to "Follow" brings the far ones to the Shepherd's side
		echo.moveTo(at.getX() + 12.5, at.getY(), at.getZ() + 0.5, 0, 0);
		for (int i = 0; i < 3; i++) {
			data.readyAt.clear();
			Shepherd.whistle(player, data);
		}
		check(data.shepherd.stance == ShepherdData.FOLLOW && echo.distanceToSqr(player) < 16, "Follow gathers the far echo, now "
				+ Math.sqrt(echo.distanceToSqr(player)));

		// Alt is the Army of Oblivion crown
		data.addResource(100);
		check(Shepherd.use(player, data, 4) == AbilityResult.HINTED, "without the crown Alt only says which talent opens it");
		data.talents.add(Talents.LEGION_ARMY);
		check(Shepherd.use(player, data, 4) == AbilityResult.OK && data.resource <= 100 - Shepherd.ARMY_COST, "the Army rises for its voice");

		// Light Bonds: a wolf takes one slot instead of two
		RaceData light = data;
		check(Shepherd.beastCost(BeastKind.byId("wolf").orElseThrow(), light) == 2, "a wolf takes two slots");
		light.talents.add(Talents.PACK_LIGHT);
		check(Shepherd.beastCost(BeastKind.byId("wolf").orElseThrow(), light) == 1 && Shepherd.beastCost(BeastKind.byId("rabbit").orElseThrow(), light) == 1,
				"Light Bonds: the wolf takes one, the rabbit still one");
		Echoes.dismiss(world.getServer(), player.getUUID());
		done(ctx, player);
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "shepherd_body", timeoutTicks = 100)
	public void shepherdsAreFrailButTheirCreaturesSpareThem(GameTestHelper ctx) {
		ServerLevel world = ctx.getLevel();
		BlockPos at = ctx.absolutePos(new BlockPos(2, 2, 2));
		ServerPlayer player = shepherd(ctx, at);
		RaceData data = RaceState.of(player);
		Shepherd.tickSecond(player, data);
		check(Math.abs(player.getMaxHealth() - 16) < 0.01f, "a Shepherd has 2 hearts less, got " + player.getMaxHealth());
		Zombie zombie = EntityType.ZOMBIE.create(world);
		zombie.moveTo(at.getX() + 1.5, at.getY(), at.getZ() + 0.5, 0, 0);
		check(Math.abs(Shepherd.modifyDamage(zombie, world.damageSources().playerAttack(player), 4) - 3) < 0.01f, "its own blow is a quarter weaker");
		data.talents.add(Talents.VOICE_LIFE);
		data.talents.add(Talents.VOICE_STRENGTH);
		Shepherd.tickSecond(player, data);
		check(Math.abs(player.getMaxHealth() - 18) < 0.01f, "Sturdy Body gives one heart back, got " + player.getMaxHealth());
		check(Math.abs(Shepherd.modifyDamage(zombie, world.damageSources().playerAttack(player), 10) - 9) < 0.01f, "Steady Hand: a tenth weaker");
		zombie.discard();
		done(ctx, player);
	}

	static ServerPlayer frozen(GameTestHelper ctx, BlockPos at) {
		ServerPlayer player = (ServerPlayer) ctx.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		RaceState.of(player).reset(Race.FROZEN);
		return player;
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "rewind", timeoutTicks = 100)
	public void theRewindFliesBackAlongTheTrail(GameTestHelper ctx) {
		ServerLevel world = ctx.getLevel();
		BlockPos at = ctx.absolutePos(new BlockPos(1, 2, 1));
		floor(world, at.offset(-1, 0, -1), 9, 3);
		ServerPlayer player = frozen(ctx, at);
		RaceData data = RaceState.of(player);
		data.addResource(100);
		check(Frozen.rewind(player, data) == AbilityResult.HINTED, "no trail yet, no Rewind");
		Vec3 start = player.position();
		// Walk six blocks east over 30 ticks, the trail recording every tick
		for (int i = 0; i <= 30; i++) {
			player.moveTo(start.x + i * 0.2, start.y, start.z, 0, 0);
			Frozen.record(player);
		}
		Vec3 left = player.position();
		check(Frozen.rewind(player, data) == AbilityResult.OK, "the Rewind");
		check(Math.abs(data.resource - 70) < 0.01f, "it costs 30 sand, left " + data.resource);
		check(Frozen.avoids(player, world.damageSources().generic()), "nothing touches a Frozen in flight");
		for (int i = 0; i < 12; i++) {
			Frozen.tick(world.getServer());
		}
		check(player.position().distanceTo(start) < 0.5, "back where it was 30 ticks ago, at " + player.position() + " from " + left);
		check(!Frozen.avoids(player, world.damageSources().generic()), "and solid again on landing");
		check(Frozen.rewind(player, data) == AbilityResult.COOLDOWN, "then it waits");
		done(ctx, player);
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "frozen_dome", timeoutTicks = 100)
	public void theDomeHoldsEnemiesAndArrows(GameTestHelper ctx) {
		ServerLevel world = ctx.getLevel();
		BlockPos at = ctx.absolutePos(new BlockPos(4, 2, 4));
		floor(world, at.offset(-4, 0, -4), 9, 9);
		ServerPlayer player = frozen(ctx, at);
		RaceData data = RaceState.of(player);
		Zombie zombie = beast(ctx, EntityType.ZOMBIE, at.offset(2, 0, 0));
		Arrow arrow = new Arrow(world, at.getX() - 2.0, at.getY() + 1.5, at.getZ() + 0.5, new ItemStack(Items.ARROW), null);
		arrow.setDeltaMovement(1, 0, 0);
		arrow.setNoGravity(true);
		world.addFreshEntity(arrow);
		data.addResource(100);
		check(Frozen.dome(player, data) == AbilityResult.OK, "the Dome");
		Frozen.tick(world.getServer());
		MobEffectInstance slow = zombie.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
		check(slow != null && slow.getAmplifier() >= 3, "the zombie under the Dome all but stops");
		check(arrow.getDeltaMovement().lengthSqr() < 1.0E-6, "the arrow hangs in the air");
		check(Frozen.dome(player, data) == AbilityResult.COOLDOWN, "one Dome at a time");
		zombie.discard();
		arrow.discard();
		done(ctx, player);
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "quicken", timeoutTicks = 100)
	public void theQuickeningSpeedsWorkingBlocks(GameTestHelper ctx) {
		ServerLevel world = ctx.getLevel();
		BlockPos at = ctx.absolutePos(new BlockPos(1, 2, 1));
		floor(world, at.offset(-1, 0, -1), 3, 3);
		world.setBlockAndUpdate(at.below(), Blocks.FARMLAND.defaultBlockState());
		world.setBlockAndUpdate(at, Blocks.WHEAT.defaultBlockState());
		world.setBlockAndUpdate(at.east(), Blocks.FURNACE.defaultBlockState());
		check(Frozen.quickenable(world, at) && Frozen.quickenable(world, at.east()), "crops and furnaces can be quickened");
		check(!Frozen.quickenable(world, at.offset(-1, -1, 0)), "plain stone can't");
		ServerPlayer player = frozen(ctx, at);
		RaceData data = RaceState.of(player);
		data.addResource(100);
		player.setXRot(90);
		check(Frozen.quicken(player, data) == AbilityResult.OK && Frozen.quickened(world, at), "looking down at the wheat quickens it");
		// A burning furnace, quickened: 25 ticks of the Quickening smelt as much as 250 plain ones (one item)
		if (world.getBlockEntity(at.east()) instanceof AbstractFurnaceBlockEntity furnace) {
			furnace.setItem(0, new ItemStack(Items.RAW_IRON, 8));
			furnace.setItem(1, new ItemStack(Items.COAL, 4));
			// Facing east and down at the furnace beside the wheat
			// (a living entity looks where its head turns, so the head too)
			player.setXRot(45);
			player.setYRot(-90);
			player.setYHeadRot(-90);
			data.readyAt.clear();
			AbilityResult quickened = Frozen.quicken(player, data);
			for (int i = 0; i < 25; i++) {
				Frozen.tick(world.getServer());
			}
			net.minecraft.world.phys.HitResult hit = player.pick(6, 1.0f, false);
			check(furnace.getItem(2).getCount() >= 1, "the quickened furnace smelts: " + quickened + ", quickened " + Frozen.quickened(world, at.east())
					+ ", look " + hit.getType() + " at " + (hit instanceof net.minecraft.world.phys.BlockHitResult b ? b.getBlockPos().subtract(at) : null)
					+ ", furnace " + furnace.saveWithoutMetadata(ctx.getLevel().registryAccess()));
		}
		world.removeBlock(at.east(), false);
		done(ctx, player);
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "frozen_body", timeoutTicks = 100)
	public void theFrozenHealSlowAndHoldOnToTheMoment(GameTestHelper ctx) {
		ServerPlayer player = frozen(ctx, ctx.absolutePos(new BlockPos(1, 2, 1)));
		RaceData data = RaceState.of(player);
		check(Frozen.naturalHealing(player) == 0.5f, "natural healing at half speed");
		MobEffectInstance speed = Frozen.shorten(player, new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600));
		check(speed.getDuration() == 420, "a potion lasts 30% shorter, got " + speed.getDuration());
		// Doubled Moment: every third blow lands half again as hard; Viscous Time slows
		data.talents.add(Talents.FLOW_ECHO);
		data.talents.add(Talents.FLOW_VISCOUS);
		Zombie zombie = EntityType.ZOMBIE.create(ctx.getLevel());
		float[] blows = new float[3];
		for (int i = 0; i < 3; i++) {
			blows[i] = Frozen.onHit(zombie, player.damageSources().playerAttack(player), 4);
		}
		check(blows[0] == 4 && blows[1] == 4 && blows[2] == 6, "the third blow is doubled by half: " + blows[2]);
		check(zombie.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the zombie's time thickens");
		data.talents.add(Talents.LOOP_ETERNAL);
		player.setHealth(player.getMaxHealth());
		float first = Frozen.modifyDamage(player, player.damageSources().generic(), player.getMaxHealth() - 2);
		player.setHealth(player.getHealth() - first);
		check(Frozen.modifyDamage(player, player.damageSources().generic(), 100) <= player.getHealth() - 1,
				"the Eternal Moment began on the low blow: nothing kills now");
		done(ctx, player);
	}

	static ServerPlayer bloodkin(GameTestHelper ctx, BlockPos at) {
		ServerPlayer player = (ServerPlayer) ctx.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		RaceState.of(player).reset(Race.BLOODKIN);
		// A helmet against the sun, unless a test wants it bare
		player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		return player;
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "blood", timeoutTicks = 100)
	public void bloodComesOnlyFromTheLiving(GameTestHelper ctx) {
		ServerLevel world = ctx.getLevel();
		BlockPos at = ctx.absolutePos(new BlockPos(2, 2, 2));
		ServerPlayer player = bloodkin(ctx, at);
		RaceData data = RaceState.of(player);
		data.addResource(50);
		player.setHealth(10);
		Pig pig = beast(ctx, EntityType.PIG, at.offset(1, 0, 0));
		float dealt = Bloodkin.onHit(pig, world.damageSources().playerAttack(player), 6);
		check(dealt == 6 && Math.abs(player.getHealth() - 10.6f) < 0.01f, "a blow on a pig heals 10%: health " + player.getHealth());
		check(Math.abs(data.resource - 56) < 0.01f, "and gives its damage in blood: " + data.resource);
		Zombie zombie = beast(ctx, EntityType.ZOMBIE, at.offset(-1, 0, 0));
		Bloodkin.onHit(zombie, world.damageSources().playerAttack(player), 6);
		check(Math.abs(data.resource - 56) < 0.01f && Math.abs(player.getHealth() - 10.6f) < 0.01f, "the undead have no blood to drink");
		// Thirst: low blood, harder blows, no natural healing
		data.addResource(-100);
		check(Bloodkin.onHit(zombie, world.damageSources().playerAttack(player), 10) == 12, "in Thirst blows are 20% harder");
		check(Bloodkin.naturalHealing(player) == 0, "in Thirst natural healing stops");
		// The ways a blow grows add up rather than multiply: Deep Thirst +35% and Execution +50% make +85%, not x2.03
		data.talents.add(Talents.THIRST_HUNGER);
		data.talents.add(Talents.FANG_EXECUTE);
		zombie.setHealth(2);
		float stacked = Bloodkin.onHit(zombie, world.damageSources().playerAttack(player), 10);
		check(Math.abs(stacked - 18.5f) < 0.01f, "bonuses add up: 10 -> 18.5, got " + stacked);
		data.talents.remove(Talents.THIRST_HUNGER);
		data.talents.remove(Talents.FANG_EXECUTE);
		data.addResource(50);
		Bloodkin.tickSecond(player, data);
		check(data.resource < 50, "blood drains away by itself");
		pig.discard();
		zombie.discard();
		done(ctx, player);
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "blood_dash", timeoutTicks = 100)
	public void theDashCutsThroughAndTheHarvestDrinks(GameTestHelper ctx) {
		ServerLevel world = ctx.getLevel();
		BlockPos at = ctx.absolutePos(new BlockPos(4, 2, 1));
		floor(world, at.offset(-4, 0, -1), 9, 9);
		ServerPlayer player = bloodkin(ctx, at);
		RaceData data = RaceState.of(player);
		data.addResource(100);
		// A spider three blocks ahead (yaw 0 looks south, +Z)
		Spider spider = beast(ctx, EntityType.SPIDER, at.offset(0, 0, 3));
		check(Bloodkin.dash(player, data) == AbilityResult.OK, "the Blood Dash");
		for (int i = 0; i < 6; i++) {
			Bloodkin.tick(world.getServer());
		}
		check(player.getZ() - (at.getZ() + 0.5) > 4, "the dash carried the Bloodkin on, now at z+" + (player.getZ() - at.getZ() - 0.5));
		check(spider.getHealth() < spider.getMaxHealth(), "and cut the spider on its way");
		check(Bloodkin.dash(player, data) == AbilityResult.COOLDOWN, "then it waits");

		// The Harvest: two spiders around, both bleed into the Bloodkin
		Spider other = beast(ctx, EntityType.SPIDER, at.offset(2, 0, 5));
		spider.setHealth(spider.getMaxHealth());
		// The dash's cut just landed: let its hurt frames pass, as a real moment would
		spider.invulnerableTime = 0;
		player.setHealth(10);
		float blood = data.resource;
		check(Bloodkin.harvest(player, data) == AbilityResult.OK, "the Harvest");
		check(spider.getHealth() < spider.getMaxHealth() && other.getHealth() < other.getMaxHealth(), "both spiders lose blood");
		check(Math.abs(player.getHealth() - 14) < 0.01f, "2 health from each, now " + player.getHealth());
		check(Math.abs(data.resource - (blood - 40 + 10)) < 0.01f, "40 spent, 5 drunk from each: " + data.resource);

		// The Sacrifice: health for blood, never deadly
		player.setHealth(20);
		data.addResource(-100);
		check(Bloodkin.sacrifice(player, data) == AbilityResult.OK && Math.abs(player.getHealth() - 16) < 0.01f && data.resource == 40,
				"4 health for 40 blood");
		data.readyAt.clear();
		player.setHealth(4);
		check(Bloodkin.sacrifice(player, data) == AbilityResult.HINTED && player.getHealth() == 4, "the Sacrifice never kills");
		// Alt before the Swarm talent: a hint naming the talent; sneaking never changes which ability a key casts
		data.readyAt.clear();
		player.setHealth(20);
		check(Bloodkin.use(player, data, 4) == AbilityResult.HINTED && player.getHealth() == 20, "Alt without the Swarm only hints");
		data.alt = true;
		check(Bloodkin.use(player, data, 2) == AbilityResult.OK && player.getHealth() < 20, "sneak + Z is still the Sacrifice");
		data.alt = false;
		spider.discard();
		other.discard();
		done(ctx, player);
	}

	/** Keeps the chunks around a test loaded for entities too, so a freshly spawned mob can be found and pulled. */
	static void holdChunks(ServerLevel world, BlockPos at) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				world.setChunkForced((at.getX() >> 4) + dx, (at.getZ() >> 4) + dz, true);
			}
		}
	}

	static void releaseChunks(ServerLevel world, BlockPos at) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				world.setChunkForced((at.getX() >> 4) + dx, (at.getZ() >> 4) + dz, false);
			}
		}
	}

	static ServerPlayer mercenary(GameTestHelper ctx, BlockPos at) {
		ServerPlayer player = (ServerPlayer) ctx.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		RaceState.of(player).reset(Race.MERCENARY);
		return player;
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "merc_contracts", timeoutTicks = 100)
	public void mercenariesGrowOnlyByContractsAndSpendTokensInTheShop(GameTestHelper ctx) {
		BlockPos at = ctx.absolutePos(new BlockPos(2, 2, 2));
		ServerPlayer player = mercenary(ctx, at);
		RaceData data = RaceState.of(player);
		// Kills and every other capped source teach a Mercenary nothing
		Progression.addXp(player, data, Progression.KILLS, 1, 60);
		check(data.xp == 0 && data.level == 0, "no race experience but from contracts");
		// A new day lays out the board
		Contracts.refresh(player, data);
		check(data.merc.offers.size() == 3, "three offers on the board, got " + data.merc.offers.size());
		check(Contracts.accept(player, data, 0) && data.merc.active.size() == 1 && data.merc.offers.size() == 2, "an offer taken in hand");
		Contracts.accept(player, data, 0);
		check(!Contracts.accept(player, data, 0) && data.merc.active.size() == 2, "two contracts in hand at most");
		data.merc.active.clear();

		// A hunt: two zombies
		Contract hunt = new Contract(Contract.Kind.HUNT, "minecraft:zombie", 2, 1, false, 10, 80);
		data.merc.active.add(hunt);
		Zombie zombie = beast(ctx, EntityType.ZOMBIE, at.offset(2, 0, 0));
		Contracts.onKill(player, data, zombie);
		check(hunt.progress == 1 && data.merc.tokens == 0, "one of two");
		Contracts.onKill(player, data, zombie);
		check(data.merc.active.isEmpty() && data.merc.tokens == 10 && data.level == 1, "the hunt pays tokens and race experience, level " + data.level);

		// Gathering: three iron ingots handed over from the inventory
		Contract gather = new Contract(Contract.Kind.GATHER, "minecraft:iron_ingot", 3, 1, false, 10, 80);
		data.merc.active.add(gather);
		player.getInventory().add(new ItemStack(Items.IRON_INGOT, 5));
		check(Contracts.deliver(player, data, 0) && data.merc.active.isEmpty() && data.merc.tokens == 20, "the ingots are handed in");
		check(player.getInventory().countItem(Items.IRON_INGOT) == 2, "and two stay, got " + player.getInventory().countItem(Items.IRON_INGOT));

		// The shop: today's goods for anyone (only what's out today), learning for the Mercenary, dearer each time, three a day
		Shop.restock(player, data);
		check(data.merc.stock.size() == Shop.STOCK && data.merc.stock.contains(data.merc.deal), "seven goods out, one a deal: " + data.merc.stock);
		check(data.merc.stock.stream().noneMatch(id -> Shop.ware(id).get().tier() > 1), "only open tiers in stock");
		data.merc.stock.remove("pearls");
		check(!Shop.buy(player, data, "pearls"), "nothing that isn't out today");
		data.merc.stock.add("arrows");
		data.merc.deal = "";
		check(Shop.buy(player, data, "arrows") && data.merc.tokens == 17 && player.getInventory().countItem(Items.ARROW) == 32, "arrows bought");
		int xpBefore = data.xp + RaceData.xpForNext(0) * data.level;
		data.merc.tokens = 100;
		check(Shop.buy(player, data, "learning") && data.merc.tokens == 88, "learning costs 12");
		check(Shop.buy(player, data, "learning") && data.merc.tokens == 70, "the second costs 18");
		check(Shop.buy(player, data, "learning") && !Shop.buy(player, data, "learning"), "three a day");
		check(data.xp + RaceData.xpForNext(0) * data.level > xpBefore, "learning teaches");
		check(!Shop.buy(player, data, "netherite_scrap"), "netherite waits for the last tier");
		zombie.discard();
		done(ctx, player);
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "merc_deals", timeoutTicks = 100)
	public void mercenaryDealsArePaidInHealthHungerAndArmour(GameTestHelper ctx) {
		ServerLevel world = ctx.getLevel();
		BlockPos at = ctx.absolutePos(new BlockPos(2, 2, 1));
		floor(world, at.offset(-2, 0, -1), 5, 6);
		ServerPlayer player = mercenary(ctx, at);
		RaceData data = RaceState.of(player);
		Zombie zombie = beast(ctx, EntityType.ZOMBIE, at.offset(0, 0, 3));
		// Strike on Credit: 3 health now, the next blow doubled; with no contract in hand the cooldown is half again
		player.setHealth(20);
		long now = world.getServer().getTickCount();
		check(Mercenary.use(player, data, 0) == AbilityResult.OK && Math.abs(player.getHealth() - 17) < 0.01f, "paid 3 health, now " + player.getHealth());
		check(data.readyAt.get(0) - now >= 240, "no contract: a longer cooldown");
		float struck = Mercenary.onHit(zombie, world.damageSources().playerAttack(player), 5);
		check(Math.abs(struck - 10) < 0.01f, "the blow doubled, got " + struck);
		check(Math.abs(Mercenary.onHit(zombie, world.damageSources().playerAttack(player), 5) - 5) < 0.01f, "only the one blow");
		player.setHealth(4);
		data.readyAt.clear();
		check(Mercenary.use(player, data, 0) == AbilityResult.NO_RESOURCE, "never the last of one's health");
		player.setHealth(20);

		// The Exchange: the weakness goes to the zombie, its speed comes over, hunger pays (no poison: the undead shrug it off)
		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 0));
		zombie.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 0));
		player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 12);
		int food = player.getFoodData().getFoodLevel();
		check(Mercenary.use(player, data, 1) == AbilityResult.OK, "the exchange");
		check(!player.hasEffect(MobEffects.WEAKNESS) && player.hasEffect(MobEffects.MOVEMENT_SPEED) && zombie.hasEffect(MobEffects.WEAKNESS)
				&& player.getAbsorptionAmount() >= 4 && player.getFoodData().getFoodLevel() == food - Mercenary.EXCHANGE_HUNGER, "effects and life traded");

		// The Pledge: Resistance II for the wear of the armour
		check(Mercenary.use(player, data, 2) == AbilityResult.HINTED, "nothing to pawn without armour");
		player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		check(Mercenary.use(player, data, 2) == AbilityResult.OK && player.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier() == 1
				&& player.getItemBySlot(EquipmentSlot.CHEST).getDamageValue() > 0, "the armour pawned");
		zombie.discard();
		done(ctx, player);
	}

	static ServerPlayer tempter(GameTestHelper ctx, BlockPos at) {
		ServerPlayer player = (ServerPlayer) ctx.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		RaceState.of(player).reset(Race.TEMPTER);
		return player;
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "tempter", timeoutTicks = 100)
	public void temptersKissCharmAndSingMonstersToSleep(GameTestHelper ctx) {
		ServerLevel world = ctx.getLevel();
		BlockPos at = ctx.absolutePos(new BlockPos(3, 2, 1));
		floor(world, at.offset(-3, 0, -1), 7, 10);
		ServerPlayer player = tempter(ctx, at);
		RaceData data = RaceState.of(player);
		data.addResource(100);
		player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 20);

		// No kiss for the undead
		Zombie zombie = beast(ctx, EntityType.ZOMBIE, at.offset(0, 0, 2));
		check(Tempter.use(player, data, 0) == AbilityResult.HINTED, "no kiss for the zombie in front");
		zombie.moveTo(at.getX() - 2.5, at.getY(), at.getZ() + 6.5, 0, 0);

		// The Kiss drinks a spider's life and leaves it infatuated: it won't hunt her, and her blows on it land harder
		Spider spider = beast(ctx, EntityType.SPIDER, at.offset(0, 0, 2));
		player.setHealth(10);
		AbilityResult kissed = Tempter.use(player, data, 0);
		check(kissed == AbilityResult.OK, "the kiss: " + kissed);
		check(player.getHealth() > 10, "the kiss drinks the spider's life, now " + player.getHealth());
		check(Tempter.infatuated(spider, player), "the spider is infatuated");
		spider.setTarget(player);
		check(spider.getTarget() == null, "an infatuated spider won't hunt her");
		check(Math.abs(Tempter.onHit(spider, world.damageSources().playerAttack(player), 8) - 10) < 0.01f, "her blows land a quarter harder");

		// The Charm: a thrall
		Spider thrall = beast(ctx, EntityType.SPIDER, at.offset(2, 0, 7));
		player.moveTo(player.getX(), player.getY(), player.getZ(), 0, 0);
		player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, thrall.position().add(0, 0.4, 0));
		data.addResource(100);
		check(Tempter.use(player, data, 2) == AbilityResult.OK && Tempter.thrall(thrall), "the charmed spider is a thrall");

		// The Lullaby: hearts asleep, the undead awake; a blow wakes the sleeper, a kiss doesn't
		Spider sleeper = beast(ctx, EntityType.SPIDER, at.offset(-2, 0, 2));
		data.addResource(100);
		check(Tempter.use(player, data, 3) == AbilityResult.OK, "the lullaby");
		check(Tempter.asleep(sleeper) && !Tempter.asleep(zombie), "the spider sleeps, the zombie doesn't");
		Tempter.onHit(sleeper, world.damageSources().mobAttack(zombie), 1);
		check(!Tempter.asleep(sleeper), "a blow wakes it");

		// The Wings: up and away
		data.addResource(100);
		check(Tempter.use(player, data, 1) == AbilityResult.OK && player.getDeltaMovement().y > 0, "the wings lift her");
		zombie.discard();
		spider.discard();
		thrall.discard();
		sleeper.discard();
		done(ctx, player);
	}

	static ServerPlayer masked(GameTestHelper ctx, BlockPos at) {
		ServerPlayer player = (ServerPlayer) ctx.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		RaceState.of(player).reset(Race.MASKED);
		return player;
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "masked", timeoutTicks = 100)
	public void theMaskedTearFacesAndPassForTheirKind(GameTestHelper ctx) {
		ServerLevel world = ctx.getLevel();
		BlockPos at = ctx.absolutePos(new BlockPos(3, 2, 1));
		floor(world, at.offset(-3, 0, -1), 7, 8);
		ServerPlayer player = masked(ctx, at);
		RaceData data = RaceState.of(player);
		player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 20);
		Zombie victim = beast(ctx, EntityType.ZOMBIE, at.offset(0, 0, 2));
		check(Masked.use(player, data, 0) == AbilityResult.HINTED, "a whole zombie keeps its face");
		victim.setHealth(5);
		check(Masked.use(player, data, 0) == AbilityResult.OK && data.masked.worn == Face.ZOMBIE, "the zombie's face goes on");
		check(victim.isDeadOrDying(), "and the zombie dies of it");
		Masked.tickSecond(player, data);
		check(player.getMaxHealth() == 24, "a zombie face gives two hearts, max health " + player.getMaxHealth());

		// Its kind takes the Masked for its own, others don't; a blow on one of them gives it away
		Zombie kin = beast(ctx, EntityType.ZOMBIE, at.offset(2, 0, 4));
		Spider spider = beast(ctx, EntityType.SPIDER, at.offset(-2, 0, 4));
		kin.setTarget(player);
		spider.setTarget(player);
		check(kin.getTarget() == null, "a zombie won't hunt a zombie face");
		check(spider.getTarget() == player, "a spider will");
		Masked.onHit(kin, world.damageSources().playerAttack(player), 1);
		kin.setTarget(player);
		check(kin.getTarget() == player, "after a blow the kin see through the face");

		// The zombie face's power, then a second face and the change back
		spider.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 2.5, 0, 0);
		data.addResource(100);
		float health = spider.getHealth();
		check(Masked.use(player, data, 1) == AbilityResult.OK && spider.getHealth() < health, "the dead grip");
		spider.setHealth(3);
		data.readyAt.clear();
		check(Masked.use(player, data, 0) == AbilityResult.OK && data.masked.worn == Face.SPIDER && data.masked.gallery.size() == 2, "a spider face too");
		check(Masked.use(player, data, 2) == AbilityResult.OK && data.masked.worn == Face.ZOMBIE, "the change goes back to the zombie");

		// A face worn out crumbles, and faceless the Masked is frail
		data.masked.gallery.put(Face.ZOMBIE, 0.01f);
		Masked.tickSecond(player, data);
		check(data.masked.worn == null && !data.masked.gallery.containsKey(Face.ZOMBIE) && player.getMaxHealth() == 16,
				"faceless: " + data.masked.worn + ", max health " + player.getMaxHealth());
		check(Masked.use(player, data, 1) == AbilityResult.HINTED, "no power without a face");
		kin.discard();
		done(ctx, player);
	}

	static ServerPlayer voidborn(GameTestHelper ctx, BlockPos at) {
		ServerPlayer player = (ServerPlayer) ctx.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		RaceState.of(player).reset(Race.VOIDBORN);
		RaceState.of(player).addResource(100);
		return player;
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "void_step", timeoutTicks = 100)
	public void theVoidStepPassesThroughWallsButNeverIntoThem(GameTestHelper ctx) {
		ServerLevel world = ctx.getLevel();
		BlockPos at = ctx.absolutePos(new BlockPos(3, 2, 1));
		floor(world, at.offset(-2, 0, -1), 5, 28);
		// A wall three blocks thick, two blocks ahead (yaw 0 looks south, +Z)
		for (int dx = -2; dx <= 2; dx++) {
			for (int dy = 0; dy <= 3; dy++) {
				for (int dz = 2; dz <= 4; dz++) {
					world.setBlockAndUpdate(at.offset(dx, dy, dz), Blocks.STONE.defaultBlockState());
				}
			}
		}
		ServerPlayer player = voidborn(ctx, at);
		RaceData data = RaceState.of(player);
		check(Voidborn.step(player, data) == AbilityResult.OK, "the Void Step");
		check(Voidborn.phasing(player), "the body passes through matter while it goes");
		ctx.runAtTickTime(ctx.getTick() + 14, () -> {
			check(!Voidborn.phasing(player), "and is solid again after");
			check(player.getZ() > at.getZ() + 5, "it came out past the wall, at z+" + (player.getZ() - at.getZ()));
			check(world.noCollision(player, player.getBoundingBox()), "and not inside a block");
			done(ctx, player);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "void_stuck", timeoutTicks = 100)
	public void theVoidStepNeverLeavesTheBodyInRockOrPastBedrock(GameTestHelper ctx) {
		ServerLevel world = ctx.getLevel();
		BlockPos at = ctx.absolutePos(new BlockPos(3, 2, 1));
		floor(world, at.offset(-2, 0, -1), 5, 28);
		// Solid rock all the way ahead: nowhere to come out, so it doesn't go and costs nothing
		for (int dx = -2; dx <= 2; dx++) {
			for (int dy = -1; dy <= 4; dy++) {
				for (int dz = 1; dz <= 26; dz++) {
					world.setBlockAndUpdate(at.offset(dx, dy, dz), Blocks.STONE.defaultBlockState());
				}
			}
		}
		ServerPlayer player = voidborn(ctx, at);
		RaceData data = RaceState.of(player);
		check(Voidborn.step(player, data) == AbilityResult.NO_SPACE && data.resource == 100, "no room anywhere ahead: no step, no cost");
		// Bedrock: the step stops short of it, it never passes through
		for (int dx = -2; dx <= 2; dx++) {
			for (int dy = -1; dy <= 4; dy++) {
				for (int dz = 1; dz <= 26; dz++) {
					world.setBlockAndUpdate(at.offset(dx, dy, dz), dz == 3 ? Blocks.BEDROCK.defaultBlockState() : dy == -1 ? Blocks.STONE.defaultBlockState()
							: Blocks.AIR.defaultBlockState());
				}
			}
		}
		check(Voidborn.step(player, data) == AbilityResult.OK, "the step towards bedrock");
		ctx.runAtTickTime(ctx.getTick() + 14, () -> {
			check(player.getZ() < at.getZ() + 3, "it stopped before the bedrock, at z+" + (player.getZ() - at.getZ()));
			check(world.noCollision(player, player.getBoundingBox()), "and stands free");
			done(ctx, player);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "void_well", timeoutTicks = 120)
	public void theWellDrawsEnemiesIn(GameTestHelper ctx) {
		ServerLevel world = ctx.getLevel();
		BlockPos at = ctx.absolutePos(new BlockPos(4, 2, 1));
		floor(world, at.offset(-4, 0, -1), 9, 14);
		// A block to aim at: the Well opens in front of it
		world.setBlockAndUpdate(at.offset(0, 1, 8), Blocks.STONE.defaultBlockState());
		ServerPlayer player = voidborn(ctx, at);
		Zombie zombie = EntityType.ZOMBIE.create(world);
		zombie.moveTo(at.getX() + 4.5, at.getY(), at.getZ() + 6.5, 0, 0);
		world.addFreshEntity(zombie);
		holdChunks(world, at);
		ctx.runAtTickTime(ctx.getTick() + 5, () -> {
			Vec3 center = new Vec3(at.getX() + 0.5, at.getY() + 1.62, at.getZ() + 6.5);
			// Opened at a known spot; aiming it is the client harness's part
			Voidborn.openWell(player, RaceState.of(player), center);
			zombie.setDeltaMovement(Vec3.ZERO);
			Voidborn.tick(world.getServer());
			Vec3 toCenter = center.subtract(zombie.getBoundingBox().getCenter()).normalize();
			check(zombie.getDeltaMovement().dot(toCenter) > 0.05, "the zombie is pulled towards the Well: " + zombie.getDeltaMovement() + ", zombie at "
					+ zombie.position() + " removed " + zombie.isRemoved() + ", player at " + player.position() + " removed " + player.isRemoved()
					+ ", zombies found near " + world.getEntitiesOfClass(Zombie.class, new AABB(center, center).inflate(8), z -> true).size());
			zombie.discard();
			releaseChunks(world, at);
			done(ctx, player);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "void_excise", timeoutTicks = 120)
	public void theExcisionTakesACreatureAwayAndBringsItBack(GameTestHelper ctx) {
		ServerLevel world = ctx.getLevel();
		BlockPos at = ctx.absolutePos(new BlockPos(3, 2, 1));
		floor(world, at.offset(-2, 0, -1), 5, 8);
		ServerPlayer player = voidborn(ctx, at);
		RaceData data = RaceState.of(player);
		Zombie zombie = beast(ctx, EntityType.ZOMBIE, at.offset(0, 0, 4));
		// The chunks held for entities, and a few ticks for the zombie to settle before it is looked at
		holdChunks(world, at);
		ctx.runAtTickTime(ctx.getTick() + 5, () -> {
			UUID id = zombie.getUUID();
			ServerLevel pocket = Pocket.world(world.getServer());
			// The zombie itself; aiming at it is the client harness's part
			AbilityResult result = Voidborn.excise(player, data, zombie);
			// The test server is made from a preset without datapack dimensions: there the Excision just refuses (the
			// client harness, in a real world, tries the pocket itself)
			if (pocket == null) {
				check(result == AbilityResult.LOCKED && world.getEntity(id) != null && data.resource == 100,
						"no pocket: nothing happens, nothing spent (" + result + ", zombie " + world.getEntity(id) + ", void " + data.resource + ")");
				zombie.discard();
				releaseChunks(world, at);
				done(ctx, player);
				return;
			}
			check(result == AbilityResult.OK, "the Excision: " + result);
			check(world.getEntity(id) == null && pocket.getEntity(id) != null, "the zombie is in the pocket now");
			check(pocket.getBlockState(BlockPos.containing(Pocket.center(data.voidborn.room)).below()).is(Blocks.AMETHYST_BLOCK), "the room was laid");
			// Leaving the race lets go of whatever it held: it comes back where it was
			Voidborn.cleanup(player);
			ctx.runAtTickTime(ctx.getTick() + 3, () -> {
				Entity back = world.getEntity(id);
				check(back != null && back.position().distanceTo(Vec3.atBottomCenterOf(at.offset(0, 0, 4))) < 0.5, "back on its spot: " + back);
				back.discard();
				releaseChunks(world, at);
				done(ctx, player);
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "void_body", timeoutTicks = 100)
	public void waterBurnsAndBedsRefuseAVoidborn(GameTestHelper ctx) {
		ServerLevel world = ctx.getLevel();
		BlockPos at = ctx.absolutePos(new BlockPos(2, 2, 2));
		floor(world, at.offset(-1, 0, -1), 3, 3);
		world.setBlockAndUpdate(at, Blocks.WATER.defaultBlockState());
		ServerPlayer player = voidborn(ctx, at);
		ctx.runAtTickTime(ctx.getTick() + 70, () -> {
			player.invulnerableTime = 0;
			player.setHealth(20);
			Voidborn.tickSecond(player, RaceState.of(player));
			check(player.getHealth() < 20, "water burns: " + player.getHealth());
			check(Voidborn.mayNotSleep(player), "the world's beds won't hold it");
			world.removeBlock(at, false);
			done(ctx, player);
		});
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new GameTestAssertException(message);
		}
	}
}
