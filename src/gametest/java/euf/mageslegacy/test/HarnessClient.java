package euf.mageslegacy.test;

import euf.mageslegacy.MagesLegacy;
import euf.mageslegacy.client.ClientRaceState;
import euf.mageslegacy.client.FaceVisuals;
import euf.mageslegacy.masked.Face;
import euf.mageslegacy.client.FxRenderer;
import euf.mageslegacy.client.MagesLegacyClient;
import euf.mageslegacy.client.RaceHud;
import euf.mageslegacy.client.screen.BestiaryScreen;
import euf.mageslegacy.client.screen.LoreScreen;
import euf.mageslegacy.client.screen.MercShopScreen;
import euf.mageslegacy.client.screen.RaceSelectScreen;
import euf.mageslegacy.client.screen.TalentScreen;
import euf.mageslegacy.frozen.Frozen;
import euf.mageslegacy.mycelid.Mycelid;
import euf.mageslegacy.net.RacePackets;
import euf.mageslegacy.race.AbilityResult;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceData;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talents;
import euf.mageslegacy.shepherd.Bonds;
import euf.mageslegacy.shepherd.EchoType;
import euf.mageslegacy.shepherd.Shepherd;
import euf.mageslegacy.voidborn.Pocket;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * With -Dmages_legacy.harness=true (runHarness): a new player sees the legend, picks the Mycelids, learns a talent,
 * then jumps, raises a spore cloud, eats a mushroom and travels through mycelium nodes, with screenshots on the way.
 * Writes harness-result.txt; scripts/ci-harness.sh fails the build unless it says PASS and every screenshot exists.
 */
public class HarnessClient implements ClientModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("mages_legacy-harness");

	/** A step of the scenario: its action, then a wait; {@code until}, if given, holds the step back until it's true (or 100 ticks). */
	private record Step(int waitAfter, boolean needsTerrain, Consumer<Minecraft> action, java.util.function.Predicate<Minecraft> until) {}

	private final Deque<Step> steps = new ArrayDeque<>();
	private final List<String> results = new ArrayList<>();
	private int ticks;
	private int nextStepTick = 60;

	private volatile BlockPos arena;
	private volatile UUID zombie;
	private Vec3 beforeJump;

	@Override
	public void onInitializeClient() {
		if (!Boolean.getBoolean("mages_legacy.harness")) {
			return;
		}
		// A new player: the legend opens by itself
		step(40, true, c -> {
			check("lore_screen_on_join", c.screen instanceof LoreScreen);
			if (c.screen instanceof LoreScreen lore) {
				lore.revealAll();
			}
		});
		// Keys: JourneyMap is installed, the third ability was left on JourneyMap's G; the mod moved it, nothing clashes
		step(1, false, c -> {
			check("journeymap_loaded", FabricLoader.getInstance().isModLoaded("journeymap"));
			check("old_key_settled", MagesLegacyClient.ABILITY_KEYS[2].isDefault());
			StringBuilder clashes = new StringBuilder();
			for (KeyMapping ours : MagesLegacyClient.ourKeys()) {
				for (KeyMapping other : MagesLegacyClient.clashes(c, ours)) {
					clashes.append(ours.getName()).append(" = ").append(other.getName()).append("; ");
				}
			}
			LOGGER.info("[harness] key clashes: {}", clashes.length() == 0 ? "none" : clashes);
			check("no_key_clashes", clashes.length() == 0);
		});
		step(5, false, c -> {
			screenshot(c, "lore");
			if (c.screen instanceof LoreScreen lore) {
				lore.showPage(2);
			}
		});
		step(5, false, c -> {
			screenshot(c, "lore_division");
			if (c.screen instanceof LoreScreen lore) {
				lore.showPage(4);
			}
		});
		step(5, false, c -> screenshot(c, "lore_last"));
		step(20, false, c -> {
			if (c.screen instanceof LoreScreen lore) {
				lore.next();
			}
		});
		step(20, false, c -> {
			check("choice_screen", c.screen instanceof RaceSelectScreen);
			screenshot(c, "choose_mycelid");
			if (c.screen instanceof RaceSelectScreen screen) {
				screen.select(Race.VOIDBORN);
			}
		});
		step(10, false, c -> {
			screenshot(c, "choose_voidborn");
			if (c.screen instanceof RaceSelectScreen screen) {
				screen.select(Race.MERCENARY);
			}
		});
		step(10, false, c -> {
			screenshot(c, "choose_mercenary");
			if (c.screen instanceof RaceSelectScreen screen) {
				screen.select(Race.TEMPTER);
			}
		});
		step(10, false, c -> {
			screenshot(c, "choose_tempter");
			if (c.screen instanceof RaceSelectScreen screen) {
				screen.select(Race.MASKED);
			}
		});
		step(10, false, c -> screenshot(c, "choose_masked"));
		step(20, false, c -> {
			if (c.screen instanceof RaceSelectScreen screen) {
				screen.select(Race.MYCELID);
				screen.choose();
			}
		});
		step(20, false, c -> {
			check("chosen_on_server", serverRace(c) == Race.MYCELID);
			check("chosen_on_client", ClientRaceState.race == Race.MYCELID);
			check("choice_screen_closed", c.screen == null);
			commands(c, "gamerule sendCommandFeedback false", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
					"gamerule doMobSpawning false", "time set 1000", "weather clear", "gamemode survival @a",
					"effect give @a minecraft:resistance 600 4 true", "effect give @a minecraft:saturation 600 0 true",
					"race xp @a 20000", "race resource @a 100", "clear @a",
					"give @a minecraft:red_mushroom 8", "give @a minecraft:brown_mushroom 8");
			onServer(c, s -> {
				arena = prepareArena(s.overworld());
				return null;
			});
		});
		step(40, true, c -> tp(c, Vec3.atBottomCenterOf(arena).add(-6, 0, 0), Vec3.atBottomCenterOf(arena).add(4, 0, 0)));
		step(20, true, c -> screenshot(c, "hud"));

		// The talent tree: learn the jump chain with a click
		step(10, false, c -> c.setScreen(new TalentScreen()));
		step(20, false, c -> {
			check("tree_has_points", ClientRaceState.freePoints > 0);
			if (c.screen instanceof TalentScreen tree) {
				check("tree_click_learns", tree.click(Talents.NET_CHAIN));
			}
		});
		step(20, false, c -> {
			check("talent_on_server", onServer(c, s -> RaceState.get(s).data(c.player.getUUID()).has(Talents.NET_CHAIN)));
			screenshot(c, "talents");
			if (c.screen instanceof TalentScreen tree) {
				tree.toggleGuide();
			}
		});
		// A short screen (GUI scale 4): the guide and the tree no longer fit and must scroll
		step(5, false, c -> {
			screenshot(c, "talents_guide");
			c.options.guiScale().set(4);
			c.resizeDisplay();
		});
		step(5, false, c -> {
			if (c.screen instanceof TalentScreen tree) {
				tree.mouseScrolled(c.screen.width / 2.0, c.screen.height / 2.0, -3);
			}
		});
		step(5, false, c -> {
			screenshot(c, "talents_guide_scrolled");
			if (c.screen instanceof TalentScreen tree) {
				tree.toggleGuide();
				tree.mouseScrolled(c.screen.width / 2.0, c.screen.height / 2.0, -10);
			}
		});
		step(5, false, c -> {
			screenshot(c, "talents_scrolled");
			c.options.guiScale().set(0);
			c.resizeDisplay();
			c.setScreen(null);
		});

		// Spore jump towards the arena centre
		step(10, false, c -> {
			beforeJump = c.player.position();
			MagesLegacyClient.useAbility(0);
		});
		step(4, false, c -> screenshot(c, "spore_jump"));
		step(20, false, c -> {
			double moved = c.player.position().subtract(beforeJump).horizontalDistance();
			check("spore_jump_moved", moved > 2);
			check("jump_chain_open", ClientRaceState.chainLeft == 2);
			LOGGER.info("[harness] jumped {} blocks", String.format(Locale.ROOT, "%.1f", moved));
		});

		// Spore cloud around a zombie
		step(10, false, c -> {
			commands(c, "race resource @a 100");
			onServer(c, s -> {
				ServerLevel world = s.overworld();
				Zombie mob = EntityType.ZOMBIE.create(world);
				mob.moveTo(arena.getX() + 2.5, arena.getY(), arena.getZ() + 0.5, 90, 0);
				mob.setNoAi(true);
				mob.setPersistenceRequired();
				// A helmet keeps it from burning in the morning sun
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
				world.addFreshEntity(mob);
				zombie = mob.getUUID();
				return null;
			});
			tp(c, Vec3.atBottomCenterOf(arena).add(-1, 0, 0), Vec3.atBottomCenterOf(arena).add(2.5, 1, 0));
		});
		step(30, false, c -> {
			MagesLegacyClient.useAbility(1);
			// From behind the shoulder, so the whole cloud is in the frame
			c.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		});
		step(30, false, c -> {
			screenshot(c, "spore_cloud");
			c.options.setCameraType(CameraType.FIRST_PERSON);
			String effects = onServer(c, s -> s.overworld().getEntity(zombie) instanceof Zombie z
					? z.getActiveEffects().toString() + " hp " + z.getHealth() : "no zombie");
			LOGGER.info("[harness] zombie in the cloud: {}", effects);
			check("cloud_chokes_zombie", onServer(c, s -> s.overworld().getEntity(zombie) instanceof Zombie z
					&& z.hasEffect(MobEffects.BLINDNESS)
					&& (z.hasEffect(MobEffects.WITHER) || z.hasEffect(MobEffects.POISON))));
		});

		// Eat a raw mushroom from the hand
		step(10, false, c -> {
			selectItem(c, Items.RED_MUSHROOM);
			c.player.setXRot(-60);
			c.gameMode.useItem(c.player, InteractionHand.MAIN_HAND);
		});
		step(10, false, c -> check("mushroom_taste", onServer(c, s ->
				s.getPlayerList().getPlayer(c.player.getUUID()).hasEffect(MobEffects.DAMAGE_BOOST))));

		// H: the Mycelium Carpet, seen from above the shoulder; then Alt with the Great Bloom crown
		step(10, false, c -> {
			resetCooldowns(c);
			UUID me = c.player.getUUID();
			onServer(c, s -> {
				ServerPlayer player = s.getPlayerList().getPlayer(me);
				RaceData data = RaceState.get(s).data(me);
				data.talents.add(Talents.SWARM_BLOOM);
				data.addResource(100);
				RacePackets.sync(player, data);
				return null;
			});
			tp(c, Vec3.atBottomCenterOf(arena).add(-1, 0, 0), Vec3.atBottomCenterOf(arena).add(2.5, 0, 0));
		});
		step(10, false, c -> MagesLegacyClient.useAbility(3));
		step(25, false, c -> {
			check("carpet_is_mycelium", onServer(c, s -> Mycelid.onMycelium(s.getPlayerList().getPlayer(c.player.getUUID()))));
			c.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			c.player.setXRot(45);
		});
		step(5, false, c -> screenshot(c, "mycelium_carpet"));
		step(10, false, c -> {
			commands(c, "race resource @a 100");
			MagesLegacyClient.useAbility(4);
		});
		step(12, false, c -> {
			screenshot(c, "great_bloom");
			c.options.setCameraType(CameraType.FIRST_PERSON);
			check("bloom_infects", onServer(c, s -> s.overworld().getEntity(zombie) instanceof Zombie z
					&& z.hasEffect(MobEffects.GLOWING)));
		});

		// Mycelium nodes open after the Nether: grow two and travel between them
		step(10, false, c -> {
			commands(c, "advancement grant @a only minecraft:story/enter_the_nether", "race resource @a 100");
			tp(c, Vec3.atBottomCenterOf(arena).add(-7, 0, 2), Vec3.atBottomCenterOf(arena).add(-7, -1, 4));
		});
		step(20, false, c -> MagesLegacyClient.useAbility(2));
		step(20, false, c -> {
			commands(c, "race resource @a 100");
			resetCooldowns(c);
			tp(c, Vec3.atBottomCenterOf(arena).add(7, 0, -2), Vec3.atBottomCenterOf(arena).add(7, -1, -4));
		});
		step(20, false, c -> MagesLegacyClient.useAbility(2));
		step(20, false, c -> {
			check("two_nodes", onServer(c, s -> RaceState.get(s).nodes(c.player.getUUID()).size() == 2));
			tp(c, Vec3.atBottomCenterOf(arena).add(5, 0, -5.5), Vec3.atBottomCenterOf(arena).add(7, 0.3, -3));
		});
		step(30, true, c -> screenshot(c, "node"));
		step(10, false, c -> {
			BlockPos node = arena.offset(7, 0, -3);
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(node), Direction.UP, node, false);
			c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND, hit);
		});
		step(20, false, c -> {
			check("node_travel", c.player.blockPosition().closerThan(arena.offset(-7, 1, 3), 2));
			screenshot(c, "node_arrived");
		});

		// ---- Shepherds: switch race, learn echoes, the bestiary, a real Call, a bond, the Rally ----
		step(10, false, c -> {
			commands(c, "race set @a shepherd", "race xp @a 20000", "race resource @a 100", "kill @e[type=!player]");
			UUID me = c.player.getUUID();
			onServer(c, s -> {
				ServerPlayer player = s.getPlayerList().getPlayer(me);
				RaceData data = RaceState.get(s).data(me);
				for (EntityType<?> type : List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER)) {
					EchoType echo = EchoType.of(type).orElseThrow();
					for (int i = 0; i < echo.kills(); i++) {
						Shepherd.countKill(player, data, echo);
					}
				}
				for (int i = 0; i < 4; i++) {
					Shepherd.countKill(player, data, EchoType.of(EntityType.WITHER_SKELETON).orElseThrow());
				}
				RacePackets.sync(player, data);
				return null;
			});
		});
		step(20, false, c -> {
			check("shepherd_chosen", serverRace(c) == Race.SHEPHERD && ClientRaceState.race == Race.SHEPHERD);
			check("echoes_learned", RaceHud.activeSquad().getOrDefault("minecraft:zombie", 0) == 1);
			c.setScreen(new BestiaryScreen());
		});
		step(20, false, c -> screenshot(c, "bestiary"));
		step(10, false, c -> {
			c.setScreen(null);
			tp(c, Vec3.atBottomCenterOf(arena).add(-6, 0, 0), Vec3.atBottomCenterOf(arena).add(-2, 0, 0));
		});
		step(10, false, c -> KeyMapping.click(MagesLegacyClient.ABILITY_KEYS[0].getDefaultKey()));
		step(30, false, c -> {
			check("echo_called", ClientRaceState.extra.getInt("echoes") == 1);
			MagesLegacyClient.editSquad(0, "minecraft:skeleton", 1);
			resetCooldowns(c);
		});
		step(10, false, c -> KeyMapping.click(MagesLegacyClient.ABILITY_KEYS[0].getDefaultKey()));
		step(30, false, c -> {
			check("two_echoes", ClientRaceState.extra.getInt("echoes") == 2 && ClientRaceState.extra.getInt("capUsed") == 3);
			c.options.hideGui = true;
			tp(c, Vec3.atBottomCenterOf(arena).add(-8, 0.5, 3), Vec3.atBottomCenterOf(arena).add(-3, 1, 0));
		});
		step(20, true, c -> screenshot(c, "echoes"));
		// Sneak + Call sends the echoes back; one Call then brings the whole squad at once
		step(5, false, c -> c.options.keyShift.setDown(true));
		step(5, false, c -> KeyMapping.click(MagesLegacyClient.ABILITY_KEYS[0].getDefaultKey()));
		step(20, false, c -> {
			c.options.keyShift.setDown(false);
			check("echoes_dismissed", ClientRaceState.extra.getInt("echoes") == 0 && ClientRaceState.extra.getInt("capUsed") == 0);
			commands(c, "race resource @a 100");
			resetCooldowns(c);
		});
		step(10, false, c -> KeyMapping.click(MagesLegacyClient.ABILITY_KEYS[0].getDefaultKey()));
		step(20, false, c -> {
			check("squad_at_once", ClientRaceState.extra.getInt("echoes") == 2);
			c.setScreen(new BestiaryScreen());
		});
		step(20, false, c -> screenshot(c, "bestiary_squad"));
		step(5, false, c -> c.setScreen(null));
		step(5, false, c -> c.options.keyShift.setDown(true));
		step(5, false, c -> KeyMapping.click(MagesLegacyClient.ABILITY_KEYS[0].getDefaultKey()));
		step(20, false, c -> c.options.keyShift.setDown(false));

		// A wolf, the Song (its own key, H, on the wolf in sight), three feedings, the last by a campfire
		step(10, false, c -> {
			c.options.hideGui = false;
			commands(c, "race resource @a 100", "clear @a", "give @a minecraft:beef 8");
			onServer(c, s -> {
				ServerLevel world = s.overworld();
				Wolf wolf = EntityType.WOLF.create(world);
				// Where the camera below looks: the centre of block (3, 0, 2) off the arena
				wolf.moveTo(arena.getX() + 4.0, arena.getY(), arena.getZ() + 3.0, 0, 0);
				wolf.setNoAi(true);
				world.addFreshEntity(wolf);
				world.setBlockAndUpdate(arena.offset(5, 0, 2), Blocks.CAMPFIRE.defaultBlockState());
				return null;
			});
			tp(c, Vec3.atBottomCenterOf(arena).add(1.5, 0, 2.5), Vec3.atBottomCenterOf(arena).add(3.5, 0.5, 2.5));
			c.player.getInventory().selected = 8;
		});
		step(10, false, c -> KeyMapping.click(MagesLegacyClient.ABILITY_KEYS[3].getDefaultKey()));
		step(10, false, c -> {
			UUID me = c.player.getUUID();
			screenshot(c, "song");
			boolean sung = onServer(c, s -> !RaceState.get(s).data(me).shepherd.rituals.isEmpty());
			check("song_key", sung);
			if (!sung) {
				// Diagnose: the key's binding, and what the Song says when called on the server for the same look
				LOGGER.info("[harness] song key bound to {}, race {}, yaw {} pitch {}", MagesLegacyClient.ABILITY_KEYS[3].getBoundKeyTranslationKey(),
						ClientRaceState.race, c.player.getYRot(), c.player.getXRot());
				AbilityResult direct = onServer(c, s -> Bonds.song(s.getPlayerList().getPlayer(me), RaceState.get(s).data(me)));
				LOGGER.info("[harness] the Song called on the server: {}", direct);
			}
			selectItem(c, Items.BEEF);
		});
		for (int i = 0; i < 4; i++) {
			// The ritual wants a minute between feedings; the harness moves the last one back before each click
			// (the click itself reaches the server a tick later)
			step(2, false, c -> {
				UUID me = c.player.getUUID();
				onServer(c, s -> {
					RaceState.get(s).data(me).shepherd.rituals.values().forEach(r -> r.lastFeed -= 2400);
					return null;
				});
			});
			step(10, false, c -> interactWolf(c));
		}
		// The bond is made on the server a tick after the last click and reaches the client with the next sync
		step(20, false, c -> { });
		step(20, false, c -> {
			check("wolf_bonded", ClientRaceState.extra.getList("bonds", 10).size() == 1);
			screenshot(c, "bonded");
			resetCooldowns(c);
			c.player.getInventory().selected = 8;
		});
		// Petting the wolf with a real right click, empty-handed
		step(10, false, c -> interactWolf(c));
		step(10, false, c -> {
			UUID me = c.player.getUUID();
			check("wolf_petted", onServer(c, s -> RaceState.get(s).data(me).shepherd.bonds.get(0).pettedAt > 0));
		});
		step(10, false, c -> KeyMapping.click(MagesLegacyClient.ABILITY_KEYS[1].getDefaultKey()));
		step(20, false, c -> {
			UUID me = c.player.getUUID();
			check("rally_strengthens", onServer(c, s -> s.overworld().getEntitiesOfClass(Wolf.class,
					new AABB(arena).inflate(12), w -> me.equals(w.getOwnerUUID())).stream().allMatch(w -> w.hasEffect(MobEffects.DAMAGE_BOOST))));
			screenshot(c, "hud_shepherd");
			c.setScreen(new BestiaryScreen());
		});
		step(20, false, c -> {
			screenshot(c, "bestiary_bonded");
			c.setScreen(new TalentScreen());
		});
		step(20, false, c -> {
			screenshot(c, "talents_shepherd");
			c.setScreen(null);
		});
		// ---- The Frozen: walk, a real Rewind seen from behind mid-flight, the Dome, the tree ----
		step(10, false, c -> {
			commands(c, "race set @a frozen", "race xp @a 20000", "race resource @a 100", "kill @e[type=!player]");
			c.options.hideGui = false;
			tp(c, Vec3.atBottomCenterOf(arena).add(-7, 0, 0), Vec3.atBottomCenterOf(arena).add(0, 1.2, 0));
		});
		step(20, false, c -> check("frozen_chosen", serverRace(c) == Race.FROZEN && ClientRaceState.race == Race.FROZEN));
		// Seven blocks east in half-block steps, so the trail has a path to fly back along
		for (int i = 1; i <= 14; i++) {
			double x = -7 + i * 0.5;
			step(4, false, c -> tp(c, Vec3.atBottomCenterOf(arena).add(x, 0, 0), Vec3.atBottomCenterOf(arena).add(x + 7, 1.2, 0)));
		}
		step(5, false, c -> {
			c.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			screenshot(c, "frozen_before");
		});
		// The key reaches the server a tick or two later; the flight itself takes half a second
		step(4, false, c -> KeyMapping.click(MagesLegacyClient.ABILITY_KEYS[0].getDefaultKey()));
		step(3, false, c -> screenshot(c, "rewind_mid"));
		step(12, false, c -> screenshot(c, "rewind_late"));
		step(5, false, c -> {
			UUID me = c.player.getUUID();
			double x = onServer(c, s -> s.getPlayerList().getPlayer(me).getX());
			check("rewind_back", x < Vec3.atBottomCenterOf(arena).x - 2.5);
			screenshot(c, "rewind_end");
			c.options.setCameraType(CameraType.FIRST_PERSON);
			resetCooldowns(c);
			UUID frozenId = c.player.getUUID();
			onServer(c, s -> {
				// Two blocks beside the Frozen, wherever the Rewind left it: well inside the Dome
				ServerLevel world = s.overworld();
				Vec3 here = s.getPlayerList().getPlayer(frozenId).position();
				Zombie zombie = EntityType.ZOMBIE.create(world);
				zombie.moveTo(here.x, here.y, here.z + 2, 0, 0);
				zombie.setNoAi(true);
				world.addFreshEntity(zombie);
				return null;
			});
		});
		step(5, false, c -> KeyMapping.click(MagesLegacyClient.ABILITY_KEYS[1].getDefaultKey()));
		step(10, false, c -> {
			check("frozen_dome", onServer(c, s -> s.overworld().getEntitiesOfClass(Zombie.class, new AABB(arena).inflate(12),
					z -> z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)).size() == 1));
			tp(c, Vec3.atBottomCenterOf(arena).add(-1, 0, -5), Vec3.atBottomCenterOf(arena).add(-4, 1, 0));
		});
		step(10, true, c -> screenshot(c, "frozen_dome"));
		// The Quickening the way a player uses it: a burning furnace in front, a real G
		step(10, false, c -> {
			resetCooldowns(c);
			commands(c, "race resource @a 100");
			onServer(c, s -> {
				ServerLevel world = s.overworld();
				BlockPos furnace = arena.offset(4, 0, -3);
				world.setBlockAndUpdate(furnace, Blocks.FURNACE.defaultBlockState());
				if (world.getBlockEntity(furnace) instanceof AbstractFurnaceBlockEntity be) {
					be.setItem(0, new ItemStack(Items.RAW_IRON, 16));
					be.setItem(1, new ItemStack(Items.COAL, 8));
				}
				return null;
			});
			tp(c, Vec3.atBottomCenterOf(arena).add(4, 0, -1), Vec3.atCenterOf(arena.offset(4, 0, -3)));
		});
		// The press, then three seconds (a step waits after its action)
		step(60, false, c -> KeyMapping.click(MagesLegacyClient.ABILITY_KEYS[2].getDefaultKey()));
		step(10, false, c -> {
			UUID me = c.player.getUUID();
			float sand = onServer(c, s -> RaceState.get(s).data(me).resource);
			int smelted = onServer(c, s -> s.overworld().getBlockEntity(arena.offset(4, 0, -3)) instanceof AbstractFurnaceBlockEntity be
					? be.getItem(2).getCount() : -1);
			LOGGER.info("[harness] quickening: sand {}, iron smelted in 3 s: {}", sand, smelted);
			LOGGER.info("[harness] quickening: furnace quickened {}, nbt {}", onServer(c, s -> Frozen.quickened(s.overworld(), arena.offset(4, 0, -3))),
					onServer(c, s -> s.overworld().getBlockEntity(arena.offset(4, 0, -3)) instanceof AbstractFurnaceBlockEntity be ? be.saveWithoutMetadata().toString() : "none"));
			// A plain furnace smelts one item in 10 s; quickened, three seconds give a couple
			check("quicken_key", sand < 95 && smelted >= 2);
			screenshot(c, "quicken");
		});
		step(10, false, c -> {
			screenshot(c, "hud_frozen");
			c.setScreen(new TalentScreen());
		});
		step(20, false, c -> {
			screenshot(c, "talents_frozen");
			c.setScreen(null);
		});
		// ---- The Bloodkin: a real Dash through a spider, the Harvest, a pig heard through a wall, the Sacrifice ----
		step(10, false, c -> {
			commands(c, "race set @a bloodkin", "race xp @a 20000", "race resource @a 100", "kill @e[type=!player]",
					"item replace entity @a armor.head with minecraft:leather_helmet");
			c.options.hideGui = false;
			c.options.setCameraType(CameraType.FIRST_PERSON);
			onServer(c, s -> {
				spawnStill(s.overworld(), EntityType.SPIDER, arena.offset(-3, 0, 0));
				return null;
			});
			tp(c, Vec3.atBottomCenterOf(arena).add(-7, 0, 0), Vec3.atBottomCenterOf(arena).add(0, 1.0, 0));
		});
		step(10, false, c -> check("bloodkin_chosen", serverRace(c) == Race.BLOODKIN && ClientRaceState.race == Race.BLOODKIN));
		step(5, false, c -> KeyMapping.click(MagesLegacyClient.ABILITY_KEYS[0].getDefaultKey()));
		step(1, false, c -> {
			LOGGER.info("[harness] dash fx: {}", FxRenderer.describe());
			screenshot(c, "blood_dash");
			c.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		});
		step(8, false, c -> {
			screenshot(c, "blood_dash_side");
			c.options.setCameraType(CameraType.FIRST_PERSON);
		});
		step(5, false, c -> {
			check("dash_cuts", onServer(c, s -> s.overworld().getEntitiesOfClass(Spider.class, new AABB(arena).inflate(12),
					sp -> sp.getHealth() < sp.getMaxHealth()).size() == 1));
			resetCooldowns(c);
			UUID me = c.player.getUUID();
			onServer(c, s -> {
				Vec3 here = s.getPlayerList().getPlayer(me).position();
				spawnStill(s.overworld(), EntityType.SPIDER, BlockPos.containing(here).offset(3, 0, 2));
				spawnStill(s.overworld(), EntityType.SPIDER, BlockPos.containing(here).offset(3, 0, -2));
				return null;
			});
		});
		step(5, false, c -> KeyMapping.click(MagesLegacyClient.ABILITY_KEYS[1].getDefaultKey()));
		step(1, false, c -> {
			LOGGER.info("[harness] harvest fx: {}", FxRenderer.describe());
			screenshot(c, "blood_harvest");
			c.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
		});
		step(4, false, c -> {
		});
		step(6, false, c -> {
			screenshot(c, "blood_harvest_side");
			c.options.setCameraType(CameraType.FIRST_PERSON);
		});
		step(5, false, c -> {
			check("harvest_drinks", onServer(c, s -> s.overworld().getEntitiesOfClass(Spider.class, new AABB(arena).inflate(16),
					sp -> sp.getHealth() < sp.getMaxHealth()).size() >= 2));
			commands(c, "kill @e[type=!player]");
			// A pig behind a stone wall: a Bloodkin hears its heart through it
			onServer(c, s -> {
				ServerLevel world = s.overworld();
				for (int dz = -2; dz <= 2; dz++) {
					for (int dy = 0; dy < 3; dy++) {
						world.setBlockAndUpdate(arena.offset(-2, dy, dz), Blocks.STONE.defaultBlockState());
					}
				}
				spawnStill(world, EntityType.PIG, arena.offset(0, 0, 0));
				return null;
			});
			tp(c, Vec3.atBottomCenterOf(arena).add(-6, 0, 0), Vec3.atBottomCenterOf(arena).add(0, 0.8, 0));
		});
		step(20, true, c -> screenshot(c, "heartbeat"));
		step(5, false, c -> {
			resetCooldowns(c);
			KeyMapping.click(MagesLegacyClient.ABILITY_KEYS[2].getDefaultKey());
		});
		step(4, false, c -> screenshot(c, "blood_sacrifice"));
		step(10, false, c -> {
			UUID me = c.player.getUUID();
			check("sacrifice_pays", onServer(c, s -> s.getPlayerList().getPlayer(me).getHealth() < s.getPlayerList().getPlayer(me).getMaxHealth()));
			screenshot(c, "hud_bloodkin");
			c.setScreen(new TalentScreen());
		});
		step(20, false, c -> {
			screenshot(c, "talents_bloodkin");
			c.setScreen(null);
			onServer(c, s -> {
				for (int dz = -2; dz <= 2; dz++) {
					for (int dy = 0; dy < 3; dy++) {
						s.overworld().removeBlock(arena.offset(-2, dy, dz), false);
					}
				}
				return null;
			});
		});
		// H: the bat swarm, the way a player presses it
		step(5, false, c -> {
			resetCooldowns(c);
			UUID me = c.player.getUUID();
			onServer(c, s -> {
				ServerPlayer player = s.getPlayerList().getPlayer(me);
				RaceData data = RaceState.get(s).data(me);
				data.addResource(100);
				player.setHealth(player.getMaxHealth());
				RacePackets.sync(player, data);
				return null;
			});
		});
		step(4, false, c -> KeyMapping.click(MagesLegacyClient.ABILITY_KEYS[3].getDefaultKey()));
		step(5, false, c -> {
			UUID me = c.player.getUUID();
			boolean sneakSeen = onServer(c, s -> RaceState.get(s).data(me).readyAt.getOrDefault(4, 0L) > s.getTickCount());
			boolean sacrificed = onServer(c, s -> s.getPlayerList().getPlayer(me).getHealth() < s.getPlayerList().getPlayer(me).getMaxHealth());
			int bats = onServer(c, s -> s.overworld().getEntitiesOfClass(Bat.class, new AABB(arena).inflate(24), b -> true).size());
			LOGGER.info("[harness] H: swarm cooldown {}, sacrificed {}, bats {}", sneakSeen, sacrificed, bats);
			check("h_swarm", sneakSeen && !sacrificed && bats > 0);
			c.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		});
		step(10, false, c -> {
			screenshot(c, "bat_swarm");
			c.options.setCameraType(CameraType.FIRST_PERSON);
		});
		// Alt with the Feast crown: the crown of blood, seen from behind
		step(60, false, c -> {
			resetCooldowns(c);
			UUID me = c.player.getUUID();
			onServer(c, s -> {
				ServerPlayer player = s.getPlayerList().getPlayer(me);
				RaceData data = RaceState.get(s).data(me);
				data.talents.add(Talents.FANG_FEAST);
				data.addResource(100);
				RacePackets.sync(player, data);
				return null;
			});
		});
		step(4, false, c -> KeyMapping.click(MagesLegacyClient.ABILITY_KEYS[4].getDefaultKey()));
		step(8, false, c -> {
			UUID me = c.player.getUUID();
			check("alt_feast", onServer(c, s -> RaceState.get(s).data(me).readyAt.getOrDefault(5, 0L) > s.getTickCount()));
			c.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		});
		step(6, false, c -> {
			screenshot(c, "blood_feast");
			c.options.setCameraType(CameraType.FIRST_PERSON);
			TalentScreen screen = new TalentScreen();
			c.setScreen(screen);
			screen.toggleTraits();
		});
		step(20, false, c -> {
			screenshot(c, "traits_bloodkin");
			c.setScreen(null);
		});
		// ---- The Voidborn: a Void Step through a wall, the Well, the Excision, into the Pocket and back ----
		step(10, false, c -> {
			commands(c, "race set @a voidborn", "race xp @a 20000", "race resource @a 100", "kill @e[type=!player]", "weather clear", "time set 6000");
			onServer(c, s -> {
				ServerLevel world = s.overworld();
				for (int dx = -4; dx <= 4; dx++) {
					for (int dy = 0; dy <= 3; dy++) {
						for (int dz = 2; dz <= 4; dz++) {
							world.setBlockAndUpdate(arena.offset(dx, dy, dz), Blocks.STONE_BRICKS.defaultBlockState());
						}
					}
				}
				return null;
			});
			c.options.setCameraType(CameraType.FIRST_PERSON);
			tp(c, Vec3.atBottomCenterOf(arena).add(0, 0, -3), Vec3.atBottomCenterOf(arena).add(0, 1.6, 6));
		});
		step(10, false, c -> check("voidborn_chosen", serverRace(c) == Race.VOIDBORN && ClientRaceState.race == Race.VOIDBORN));
		step(4, false, c -> KeyMapping.click(MagesLegacyClient.ABILITY_KEYS[0].getDefaultKey()));
		step(14, false, c -> {
			LOGGER.info("[harness] void step fx: {}", FxRenderer.describe());
			screenshot(c, "void_step_mid");
		});
		step(4, false, c -> {
			double z = c.player.getZ() - arena.getZ();
			LOGGER.info("[harness] void step: now at z+{}", z);
			check("void_step_through", z > 4.5 && c.level.noCollision(c.player, c.player.getBoundingBox()));
			c.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		});
		step(4, false, c -> screenshot(c, "void_step_end"));
		// The Well, opened on the ground ahead, with three zombies around it
		step(40, false, c -> {
			c.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			resetCooldowns(c);
			commands(c, "race resource @a 100");
			onServer(c, s -> {
				ServerLevel world = s.overworld();
				for (int dx = -4; dx <= 4; dx++) {
					for (int dy = 0; dy <= 3; dy++) {
						for (int dz = 2; dz <= 4; dz++) {
							world.removeBlock(arena.offset(dx, dy, dz), false);
						}
					}
				}
				spawnStill(world, EntityType.ZOMBIE, arena.offset(-3, 0, 4));
				spawnStill(world, EntityType.ZOMBIE, arena.offset(3, 0, 4));
				spawnStill(world, EntityType.ZOMBIE, arena.offset(0, 0, 6));
				return null;
			});
			tp(c, Vec3.atBottomCenterOf(arena).add(0, 0, -5), Vec3.atBottomCenterOf(arena).add(0, 0, 3));
		});
		step(20, false, c -> KeyMapping.click(MagesLegacyClient.ABILITY_KEYS[1].getDefaultKey()));
		step(80, false, c -> {
			LOGGER.info("[harness] well fx: {}", FxRenderer.describe());
			screenshot(c, "void_well");
		});
		step(10, false, c -> {
			c.options.setCameraType(CameraType.FIRST_PERSON);
			commands(c, "kill @e[type=!player]");
			resetCooldowns(c);
			commands(c, "race resource @a 100");
			onServer(c, s -> {
				spawnStill(s.overworld(), EntityType.ZOMBIE, arena.offset(0, 0, 3));
				return null;
			});
			tp(c, Vec3.atBottomCenterOf(arena).add(0, 0, -3), Vec3.atBottomCenterOf(arena).add(0, 1.2, 3));
		});
		// The Excision: the zombie is gone into the pocket for six seconds, then back
		step(30, false, c -> KeyMapping.click(MagesLegacyClient.ABILITY_KEYS[2].getDefaultKey()));
		step(140, false, c -> {
			UUID me = c.player.getUUID();
			LOGGER.info("[harness] excision: cooling {}, void {}, looking at {}",
					onServer(c, s -> RaceState.get(s).data(me).readyAt.getOrDefault(2, 0L) > s.getTickCount()),
					onServer(c, s -> RaceState.get(s).data(me).resource), c.hitResult);
			screenshot(c, "void_excise");
			int here = onServer(c, s -> s.overworld().getEntitiesOfClass(Zombie.class, new AABB(arena).inflate(12), z -> z.isAlive()).size());
			LOGGER.info("[harness] excision: zombies left here {}", here);
			check("excise_away", here == 0);
		});
		// It waits in the pocket until Z is pressed again
		step(20, false, c -> {
			UUID me = c.player.getUUID();
			check("excise_holds", ClientRaceState.extra.getBoolean("holding")
					&& onServer(c, s -> RaceState.get(s).data(me).readyAt.getOrDefault(2, 0L) <= s.getTickCount()));
			KeyMapping.click(MagesLegacyClient.ABILITY_KEYS[2].getDefaultKey());
		});
		step(10, false, c -> {
			int here = onServer(c, s -> s.overworld().getEntitiesOfClass(Zombie.class, new AABB(arena).inflate(12), z -> z.isAlive()).size());
			LOGGER.info("[harness] excision: zombies back {}", here);
			check("excise_back", here == 1);
			screenshot(c, "hud_voidborn");
			commands(c, "kill @e[type=!player]");
			resetCooldowns(c);
			commands(c, "race resource @a 100");
		});
		// Into the Pocket through a rift, and back out to the same spot
		step(4, false, c -> KeyMapping.click(MagesLegacyClient.ABILITY_KEYS[3].getDefaultKey()));
		step(40, false, c -> screenshot(c, "pocket_rift"));
		step(10, false, c -> {
			LOGGER.info("[harness] pocket: client world {}", c.level.dimension().location());
			check("pocket_in", c.level.dimension() == Pocket.WORLD);
			c.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		});
		step(10, false, c -> {
			screenshot(c, "pocket");
			c.options.setCameraType(CameraType.FIRST_PERSON);
		});
		step(40, false, c -> KeyMapping.click(MagesLegacyClient.ABILITY_KEYS[3].getDefaultKey()));
		step(10, false, c -> {
			double away = c.player.position().distanceTo(Vec3.atBottomCenterOf(arena).add(0, 0, -3));
			LOGGER.info("[harness] pocket: back in {} at {} from the start", c.level.dimension().location(), away);
			check("pocket_out", c.level.dimension() == Level.OVERWORLD && away < 1.5);
		});
		step(10, false, c -> c.setScreen(new TalentScreen()));
		step(20, false, c -> {
			screenshot(c, "talents_voidborn");
			c.setScreen(null);
		});

		// ---- The Mercenaries: the Mage's Shop on H, a contract taken, a ware bought, a Strike on Credit ----
		step(10, false, c -> commands(c, "race set @a mercenary", "race xp @a 20000", "kill @e[type=!player]"));
		step(30, false, c -> {
			check("mercenary_chosen", serverRace(c) == Race.MERCENARY && ClientRaceState.race == Race.MERCENARY);
			KeyMapping.click(MagesLegacyClient.ABILITY_KEYS[3].getDefaultKey());
		});
		step(20, false, c -> {
			check("shop_opens", c.screen instanceof MercShopScreen);
			check("board_filled", ClientRaceState.extra.getList("offers", 10).size() == 3);
			screenshot(c, "merc_board");
			MagesLegacyClient.sendMerc(0, 0, "");
		});
		step(20, false, c -> {
			UUID me = c.player.getUUID();
			check("contract_taken", onServer(c, s -> RaceState.get(s).data(me).merc.active.size() == 1));
			screenshot(c, "merc_board_taken");
			onServer(c, s -> {
				RaceData data = RaceState.get(s).data(me);
				data.merc.tokens = 60;
				// Arrows out today at the full price, whatever the roll
				if (!data.merc.stock.contains("arrows")) {
					data.merc.stock.add("arrows");
				}
				data.merc.deal = "";
				RacePackets.sync(s.getPlayerList().getPlayer(me), data);
				return null;
			});
		});
		step(10, false, c -> {
			if (c.screen instanceof MercShopScreen shop) {
				shop.showShop(true);
			}
		});
		step(20, false, c -> {
			screenshot(c, "merc_shop");
			MagesLegacyClient.sendMerc(4, 0, "arrows");
		});
		step(20, false, c -> {
			UUID me = c.player.getUUID();
			check("ware_bought", onServer(c, s -> RaceState.get(s).data(me).merc.tokens == 57
					&& s.getPlayerList().getPlayer(me).getInventory().countItem(Items.ARROW) >= 32));
			c.setScreen(null);
			onServer(c, s -> {
				s.getPlayerList().getPlayer(me).setHealth(20);
				return null;
			});
		});
		step(10, false, c -> KeyMapping.click(MagesLegacyClient.ABILITY_KEYS[0].getDefaultKey()));
		step(10, false, c -> {
			UUID me = c.player.getUUID();
			check("credit_strike_paid", onServer(c, s -> s.getPlayerList().getPlayer(me).getHealth() < 20));
			c.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		});
		step(5, false, c -> {
			screenshot(c, "credit_strike");
			c.options.setCameraType(CameraType.FIRST_PERSON);
		});

		// The Mercenaries' own shapes: the Exchange's crossing orbs, the Pledge's seal, the Stake's coin, the Shop's door
		step(10, false, c -> {
			resetCooldowns(c);
			commands(c, "kill @e[type=!player]", spider(arena, 0, 20));
			grant(c, Talents.DEAL_STAKE);
			tp(c, Vec3.atBottomCenterOf(arena).add(-5, 0, 0), Vec3.atBottomCenterOf(arena).add(0, 0.5, 0));
		});
		step(4, false, c -> {
			c.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			key(1);
		});
		shotWhen("Exchange", 0.45f, "fx_merc_exchange");
		step(4, false, c -> {
			UUID me = c.player.getUUID();
			onServer(c, s -> {
				s.getPlayerList().getPlayer(me).setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(Items.IRON_CHESTPLATE));
				return null;
			});
			key(2);
		});
		shotWhen("Seal", 0.025f, "fx_merc_seal_falling");
		shotWhen("Seal", 0.1f, "fx_merc_seal_ring");
		step(9, false, c -> key(4));
		shotWhen("Flip", 0.35f, "fx_merc_stake_flip");
		shotWhen("Flip", 0.8f, "fx_merc_stake_coins");
		// The Shop's door: open while the Shop is (the screen itself would hide it, so only the message is sent), shut after
		step(10, false, c -> {
			c.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			MagesLegacyClient.sendMerc(5, 0, "");
		});
		shotWhen("Door", 0.005f, "fx_merc_door");
		step(60, false, c -> check("shop_door_stays_open", FxRenderer.progressOf("Door") >= 0));
		step(40, false, c -> MagesLegacyClient.sendMerc(6, 0, ""));
		step(5, false, c -> check("shop_door_shuts", FxRenderer.progressOf("Door") < 0));

		// ---- The Tempters: the Kiss's hearts, the Wings, the Charm's heart, the Lullaby's moons, the Broken Heart ----
		step(10, false, c -> {
			c.options.setCameraType(CameraType.FIRST_PERSON);
			commands(c, "race set @a tempter", "race xp @a 20000", "race resource @a 100", "kill @e[type=!player]", "time set night",
					spider(arena, 0, 16), spider(arena.offset(2, 0, 3), 0, 16), spider(arena.offset(1, 0, -3), 0, 16));
		});
		step(20, false, c -> {
			check("tempter_chosen", serverRace(c) == Race.TEMPTER && ClientRaceState.race == Race.TEMPTER);
			grant(c, Talents.THREAD_BREAK);
			tp(c, Vec3.atBottomCenterOf(arena).add(-6, 0, 0), Vec3.atBottomCenterOf(arena).add(0, 0.5, 0));
		});
		step(4, false, c -> {
			c.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			key(0);
		});
		shotWhen("Hearts", 0.35f, "fx_tempter_kiss");
		step(6, false, c -> key(1));
		shotWhen("Wings", 0.15f, "fx_tempter_wings_up");
		shotWhen("Wings", 0.35f, "fx_tempter_wings_beat");
		step(30, false, c -> {
			resetCooldowns(c);
			commands(c, "race resource @a 100");
			tp(c, Vec3.atBottomCenterOf(arena).add(-4, 0, -1), Vec3.atBottomCenterOf(arena).add(2, 0.5, 3));
		});
		step(4, false, c -> key(2));
		shotWhen("Sigil", 0.1f, "fx_tempter_charm");
		step(4, false, c -> {
			commands(c, "race resource @a 100");
			key(3);
		});
		shotWhen("Lullaby", 0.3f, "fx_tempter_lullaby");
		step(4, false, c -> {
			commands(c, "race resource @a 100");
			key(4);
		});
		shotWhen("Heartbreak", 0.55f, "fx_tempter_heartbreak");
		step(10, false, c -> commands(c, "time set day", "kill @e[type=!player]"));

		// ---- The Masked: a face torn off a weakened zombie, then a spider's; the wearer drawn as the monster ----
		step(10, false, c -> {
			c.options.setCameraType(CameraType.FIRST_PERSON);
			commands(c, "race set @a masked", "race xp @a 20000", "race resource @a 100", "kill @e[type=!player]",
					String.format(Locale.ROOT, "summon zombie %d %d %d {NoAI:1b,Health:4f}", arena.getX(), arena.getY(), arena.getZ()));
		});
		step(20, false, c -> {
			check("masked_chosen", serverRace(c) == Race.MASKED && ClientRaceState.race == Race.MASKED);
			tp(c, Vec3.atBottomCenterOf(arena).add(-3, 0, 0), Vec3.atBottomCenterOf(arena).add(0, 1, 0));
		});
		step(6, false, c -> {
			c.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			KeyMapping.click(MagesLegacyClient.ABILITY_KEYS[0].getDefaultKey());
		});
		shotWhen("Mask", 0.5f, "fx_masked_tear");
		step(20, false, c -> {
			c.options.setCameraType(CameraType.FIRST_PERSON);
			UUID me = c.player.getUUID();
			check("masked_zombie_face", onServer(c, s -> RaceState.get(s).data(me).masked.worn == Face.ZOMBIE));
			check("masked_drawn_as_zombie", FaceVisuals.of(c.player.getId()) == Face.ZOMBIE);
			c.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
		});
		step(10, false, c -> {
			screenshot(c, "masked_zombie");
			resetCooldowns(c);
			commands(c, String.format(Locale.ROOT, "summon spider %d %d %d {NoAI:1b,Health:3f}", arena.getX(), arena.getY(), arena.getZ()));
			c.options.setCameraType(CameraType.FIRST_PERSON);
		});
		step(10, false, c -> KeyMapping.click(MagesLegacyClient.ABILITY_KEYS[0].getDefaultKey()));
		step(30, false, c -> {
			check("masked_drawn_as_spider", FaceVisuals.of(c.player.getId()) == Face.SPIDER);
			c.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		});
		step(10, false, c -> screenshot(c, "masked_spider"));
		step(10, false, c -> {
			c.options.setCameraType(CameraType.FIRST_PERSON);
			// The HUD with the face's egg on V and Z
			screenshot(c, "masked_hud");
		});
		// The web of the spider's face, the Change's carousel, Kin's masks over the retinue, the Thousand Faces
		step(10, false, c -> {
			resetCooldowns(c);
			commands(c, "race resource @a 100", "kill @e[type=!player]", String.format(Locale.ROOT, "summon zombie %d %d %d {NoAI:1b}", arena.getX(), arena.getY(), arena.getZ()));
			grant(c, Talents.GAL_THOUSAND);
			tp(c, Vec3.atBottomCenterOf(arena).add(-5, 0, 0), Vec3.atBottomCenterOf(arena).add(0, 0.5, 0));
		});
		step(4, false, c -> {
			c.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			key(1);
		});
		shotWhen("Web", 0.35f, "fx_masked_web");
		step(4, false, c -> key(2));
		shotWhen("Masks", 0.4f, "fx_masked_change");
		// The Change put the zombie's face back on: its kin are zombies
		step(10, false, c -> commands(c, "race resource @a 100",
				String.format(Locale.ROOT, "summon zombie %d %d %d {NoAI:1b}", arena.getX() + 1, arena.getY(), arena.getZ() + 2),
				String.format(Locale.ROOT, "summon zombie %d %d %d {NoAI:1b}", arena.getX() + 1, arena.getY(), arena.getZ() - 2)));
		step(4, false, c -> key(3));
		shotWhen("Sigil", 0.04f, "fx_masked_kin");
		step(4, false, c -> {
			commands(c, "race resource @a 100");
			c.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			key(4);
		});
		shotWhen("Masks", 0.3f, "fx_masked_thousand");
		step(5, false, c -> c.options.setCameraType(CameraType.FIRST_PERSON));
		// ---- Trial VFX libraries: the Kiss and the Charm in our renderer, in Photon and in Lodestone, same spot, same camera ----
		step(10, false, c -> {
			c.options.setCameraType(CameraType.FIRST_PERSON);
			commands(c, "race set @a tempter", "race xp @a 20000", "kill @e[type=!player]", "time set 18000");
		});
		step(20, false, c -> {
			check("trial_photon_loaded", FxPrototypes.photonLoaded());
			check("trial_lodestone_loaded", FxPrototypes.lodestoneLoaded());
		});
		String[] backends = {"own", "photon", "lodestone"};
		for (String backend : backends) {
			// A fresh spider each time, so nothing of the last trial lingers on it
			step(10, false, c -> {
				commands(c, "kill @e[type=spider]", spider(arena, 0, 16), "race resource @a 100");
				resetCooldowns(c);
			});
			step(10, false, this::trialCamera);
			step(4, false, c -> {
				if (backend.equals("own")) {
					trialLook(c);
				}
			});
			step(6, false, c -> {
				c.gui.getChat().clearMessages(false);
				trialKiss(c, backend);
			});
			step(10, false, c -> screenshot(c, "trial_" + backend + "_kiss_a"));
			step(20, false, c -> screenshot(c, "trial_" + backend + "_kiss_b"));
			step(10, false, c -> commands(c, "kill @e[type=spider]", spider(arena, 0, 16), "race resource @a 100"));
			step(10, false, this::trialCamera);
			for (int i = 0; i < 26; i++) {
				long tick = i;
				step(1, false, c -> trialCharm(c, backend, tick));
			}
			step(4, false, c -> {
				screenshot(c, "trial_" + backend + "_charm");
				FxPrototypes.stopPhoton();
			});
		}
		step(10, false, c -> {
			commands(c, "time set day", "kill @e[type=spider]");
			c.options.setCameraType(CameraType.FIRST_PERSON);
		});

		step(20, false, this::finish);

		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	// ---- server-side helpers ----

	/** A summon command for a still spider at {@code at} (offset by {@code dx}), with {@code health}. */
	private static String spider(BlockPos at, int dx, int health) {
		return String.format(Locale.ROOT, "summon spider %d %d %d {NoAI:1b,Health:%df}", at.getX() + dx, at.getY(), at.getZ(), health);
	}

	/** Learns talents for the player straight in the data (the harness doesn't climb the tree for every crown). */
	private static void grant(Minecraft client, String... talents) {
		UUID me = client.player.getUUID();
		onServer(client, s -> {
			RaceData data = RaceState.get(s).data(me);
			data.talents.addAll(java.util.List.of(talents));
			RacePackets.sync(s.getPlayerList().getPlayer(me), data);
			return null;
		});
	}

	/** The camera for the trial effects: the player off to one side, the spider in the left half of the view. */
	private void trialCamera(Minecraft client) {
		tp(client, Vec3.atBottomCenterOf(arena).add(-4, 0, 3), Vec3.atBottomCenterOf(arena).add(2, 1, 3));
		client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
	}

	/** The player turns to the trial's spider where it stands (on the server, so the ability sees it). */
	private static void trialLook(Minecraft client) {
		net.minecraft.world.entity.Entity target = trialTarget(client);
		if (target != null) {
			tp(client, client.player.position(), target.position().add(0, 0.4, 0));
		}
	}

	private static void trialKiss(Minecraft client, String backend) {
		net.minecraft.world.entity.Entity target = trialTarget(client);
		if (target == null) {
			return;
		}
		switch (backend) {
			case "photon" -> FxPrototypes.photonKiss(target, client.player);
			case "lodestone" -> FxPrototypes.lodestoneKiss(target, client.player);
			default -> {
				// Our own: the real Kiss (the server turned the player to the spider a moment before, see trialLook)
				key(0);
			}
		}
	}

	private static void trialCharm(Minecraft client, String backend, long tick) {
		net.minecraft.world.entity.Entity target = trialTarget(client);
		if (target == null) {
			return;
		}
		switch (backend) {
			case "photon" -> {
				if (tick == 0) {
					FxPrototypes.photonCharm(target);
				}
			}
			case "lodestone" -> FxPrototypes.lodestoneCharmTick(target, tick);
			default -> {
				if (tick == 2) {
					key(2);
				} else if (tick == 0) {
					trialLook(client);
				}
			}
		}
	}

	/** The spider the trial effects are played on. */
	private static net.minecraft.world.entity.Entity trialTarget(Minecraft client) {
		return client.level.getEntitiesOfClass(net.minecraft.world.entity.monster.Spider.class, client.player.getBoundingBox().inflate(24), e -> e.isAlive())
				.stream().findFirst().orElse(null);
	}

	private static void key(int slot) {
		KeyMapping.click(MagesLegacyClient.ABILITY_KEYS[slot].getDefaultKey());
	}

	/** A 21x13 mycelium field at the spawn with 6 blocks of air above; returns the block above its centre. */
	private static BlockPos prepareArena(ServerLevel world) {
		BlockPos spawn = world.getSharedSpawnPos();
		// Heightmaps of unloaded chunks read as the bottom of the world: generate the chunk first
		world.getChunk(spawn.getX() >> 4, spawn.getZ() >> 4);
		BlockPos top = world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn);
		for (int dx = -10; dx <= 10; dx++) {
			for (int dz = -6; dz <= 6; dz++) {
				world.setBlockAndUpdate(top.offset(dx, -1, dz), Blocks.MYCELIUM.defaultBlockState());
				world.setBlockAndUpdate(top.offset(dx, -2, dz), Blocks.DIRT.defaultBlockState());
				for (int dy = 0; dy < 6; dy++) {
					world.setBlockAndUpdate(top.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
				}
			}
		}
		world.setBlockAndUpdate(top.offset(-3, 0, 4), Blocks.RED_MUSHROOM.defaultBlockState());
		world.setBlockAndUpdate(top.offset(4, 0, 5), Blocks.BROWN_MUSHROOM.defaultBlockState());
		return top;
	}

	/** A mob that stands still where it's put (no AI), for the scenario to aim at. */
	private static void spawnStill(ServerLevel world, EntityType<? extends Mob> type, BlockPos at) {
		Mob mob = type.create(world);
		if (mob != null) {
			mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
			mob.setNoAi(true);
			world.addFreshEntity(mob);
		}
	}

	/** A real right click on the nearest wolf, the way a player would. */
	private static void interactWolf(Minecraft client) {
		Wolf wolf = client.level.getEntitiesOfClass(Wolf.class, client.player.getBoundingBox().inflate(6), w -> true)
				.stream().findFirst().orElse(null);
		if (wolf != null) {
			client.gameMode.interact(client.player, wolf, InteractionHand.MAIN_HAND);
		}
	}

	private static Race serverRace(Minecraft client) {
		UUID me = client.player.getUUID();
		return onServer(client, s -> RaceState.get(s).data(me).race);
	}

	private static void resetCooldowns(Minecraft client) {
		UUID me = client.player.getUUID();
		onServer(client, s -> {
			RaceData data = RaceState.get(s).data(me);
			data.readyAt.clear();
			return null;
		});
	}

	private static <T> T onServer(Minecraft client, Function<MinecraftServer, T> action) {
		MinecraftServer server = client.getSingleplayerServer();
		return server.submit(() -> action.apply(server)).join();
	}

	private static void commands(Minecraft client, String... commands) {
		onServer(client, server -> {
			for (String command : commands) {
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
			}
			return null;
		});
	}

	// ---- client-side helpers ----

	/**
	 * A screenshot taken once the effect {@code effect} is drawn and {@code progress} of the way through: the server
	 * lags on CI, so a fixed wait would catch it too early or too late.
	 */
	private void shotWhen(String effect, float progress, String name) {
		// The chat would cover the ground in front: cleared a frame before the picture is taken
		steps.add(new Step(1, false, c -> c.gui.getChat().clearMessages(false), c -> FxRenderer.progressOf(effect) >= progress));
		steps.add(new Step(2, false, c -> {
			LOGGER.info("[harness] {} at {}", name, FxRenderer.describe());
			screenshot(c, name);
		}, null));
	}

	private void step(int waitAfter, boolean needsTerrain, Consumer<Minecraft> action) {
		steps.add(new Step(waitAfter, needsTerrain, action, null));
	}

	private void tick(Minecraft client) {
		if (client.player == null || client.level == null || client.getSingleplayerServer() == null || steps.isEmpty()) {
			return;
		}
		ticks++;
		if (ticks < nextStepTick) {
			return;
		}
		Step next = steps.peek();
		if (next.until() != null && !next.until().test(client) && ticks < nextStepTick + 100) {
			return;
		}
		if (next.needsTerrain() && !client.levelRenderer.hasRenderedAllChunks() && ticks < nextStepTick + 400) {
			return;
		}
		steps.poll();
		try {
			next.action().accept(client);
		} catch (RuntimeException e) {
			LOGGER.error("[harness] step failed", e);
			check("step_without_exception", false);
		}
		nextStepTick = ticks + next.waitAfter();
	}

	/** Teleports the player (server side) to {@code from}, looking at {@code at}. */
	private static void tp(Minecraft client, Vec3 from, Vec3 at) {
		Vec3 d = at.subtract(from.add(0, client.player.getEyeHeight(), 0));
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
		commands(client, String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", from.x, from.y, from.z, yaw, pitch));
	}

	private static void selectItem(Minecraft client, Item item) {
		for (int i = 0; i < 9; i++) {
			if (client.player.getInventory().getItem(i).is(item)) {
				client.player.getInventory().selected = i;
				return;
			}
		}
	}

	private void screenshot(Minecraft client, String name) {
		Screenshot.grab(client.gameDirectory, name + ".png", client.getMainRenderTarget(), message -> {});
		LOGGER.info("[harness] screenshot {}.png", name);
	}

	private void finish(Minecraft client) {
		boolean pass = !results.isEmpty() && results.stream().allMatch(line -> line.endsWith(": OK"));
		results.add("RESULT " + (pass ? "PASS" : "FAIL"));
		try {
			Files.write(client.gameDirectory.toPath().resolve("harness-result.txt"), results, StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOGGER.error("[harness] could not write the result file", e);
		}
		LOGGER.info("[harness] {}", results.get(results.size() - 1));
		client.stop();
	}

	private void check(String name, boolean ok) {
		String line = name + ": " + (ok ? "OK" : "FAIL");
		results.add(line);
		LOGGER.info("[harness] {}", line);
	}
}
