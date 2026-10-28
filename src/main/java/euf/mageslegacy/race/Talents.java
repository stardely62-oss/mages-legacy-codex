package euf.mageslegacy.race;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Every talent of every race. Branch names live under mages_legacy.branch.<race>.<index>. */
public final class Talents {
	private static final Map<String, Talent> BY_ID = new LinkedHashMap<>();

	// Mycelids: Swarm (clouds and poison), Network (jumps and nodes), Flesh (diet and endurance). Per branch three
	// talents on tier I, four on II, three on III and a crown on IV: 75 points for all, 60 at level 50, so a build
	// has to choose. Within a tier the order here is the order on screen.
	public static final String SWARM_WIDE = add("swarm_wide", Race.MYCELID, 0, 1, 1, null, Items.SPORE_BLOSSOM);
	public static final String SWARM_TRAIL = add("swarm_trail", Race.MYCELID, 0, 1, 1, null, Items.POISONOUS_POTATO);
	public static final String SWARM_QUICK = add("swarm_quick", Race.MYCELID, 0, 1, 1, null, Items.CLOCK);
	public static final String SWARM_POTENT = add("swarm_potent", Race.MYCELID, 0, 2, 2, SWARM_WIDE, Items.SPIDER_EYE);
	public static final String SWARM_INFECTION = add("swarm_infection", Race.MYCELID, 0, 2, 2, SWARM_POTENT, Items.BONE);
	public static final String SWARM_WEAKNESS = add("swarm_weakness", Race.MYCELID, 0, 2, 2, SWARM_TRAIL, Items.STONE_SWORD);
	public static final String SWARM_MENDING = add("swarm_mending", Race.MYCELID, 0, 2, 2, SWARM_QUICK, Items.GLISTERING_MELON_SLICE);
	public static final String SWARM_LINGERING = add("swarm_lingering", Race.MYCELID, 0, 3, 3, SWARM_INFECTION, Items.FERMENTED_SPIDER_EYE);
	public static final String SWARM_BURST = add("swarm_burst", Race.MYCELID, 0, 3, 3, SWARM_WEAKNESS, Items.FIRE_CHARGE);
	public static final String SWARM_FOLLOW = add("swarm_follow", Race.MYCELID, 0, 3, 3, SWARM_MENDING, Items.LEAD);
	public static final String SWARM_BLOOM = add("swarm_bloom", Race.MYCELID, 0, 4, 5, SWARM_LINGERING, Items.WITHER_ROSE);

	public static final String NET_CHAIN = add("net_chain", Race.MYCELID, 1, 1, 1, null, Items.MYCELIUM);
	public static final String NET_REACH = add("net_reach", Race.MYCELID, 1, 1, 1, null, Items.CHORUS_FRUIT);
	public static final String NET_LIGHT_STEP = add("net_light_step", Race.MYCELID, 1, 1, 1, null, Items.FEATHER);
	public static final String NET_NODES = add("net_nodes", Race.MYCELID, 1, 2, 2, NET_CHAIN, Items.ROOTED_DIRT);
	public static final String NET_FAR = add("net_far", Race.MYCELID, 1, 2, 2, NET_NODES, Items.WARPED_ROOTS);
	public static final String NET_CHEAP = add("net_cheap", Race.MYCELID, 1, 2, 2, NET_REACH, Items.SUGAR);
	public static final String NET_HASTE = add("net_haste", Race.MYCELID, 1, 2, 2, NET_LIGHT_STEP, Items.RABBIT_FOOT);
	public static final String NET_SHARED = add("net_shared", Race.MYCELID, 1, 3, 3, NET_FAR, Items.GLOW_LICHEN);
	public static final String NET_ROOTS = add("net_roots", Race.MYCELID, 1, 3, 3, NET_REACH, Items.HANGING_ROOTS);
	public static final String NET_CLEANSE = add("net_cleanse", Race.MYCELID, 1, 3, 3, NET_CHEAP, Items.MILK_BUCKET);
	public static final String NET_MIND = add("net_mind", Race.MYCELID, 1, 4, 5, NET_SHARED, Items.ENDER_EYE);

	public static final String FLESH_TWO_TASTES = add("flesh_two_tastes", Race.MYCELID, 2, 1, 1, null, Items.BROWN_MUSHROOM);
	public static final String FLESH_RICH = add("flesh_rich", Race.MYCELID, 2, 1, 1, null, Items.RED_MUSHROOM);
	public static final String FLESH_FIREPROOF = add("flesh_fireproof", Race.MYCELID, 2, 1, 1, null, Items.WATER_BUCKET);
	public static final String FLESH_DARK_REGEN = add("flesh_dark_regen", Race.MYCELID, 2, 2, 2, FLESH_TWO_TASTES, Items.GLOW_BERRIES);
	public static final String FLESH_CAP = add("flesh_cap", Race.MYCELID, 2, 2, 2, FLESH_DARK_REGEN, Items.RED_MUSHROOM_BLOCK);
	public static final String FLESH_MEAT = add("flesh_meat", Race.MYCELID, 2, 2, 2, FLESH_RICH, Items.COOKED_BEEF);
	public static final String FLESH_SPORE_BLOOD = add("flesh_spore_blood", Race.MYCELID, 2, 2, 2, FLESH_FIREPROOF, Items.REDSTONE);
	public static final String FLESH_SUNPROOF = add("flesh_sunproof", Race.MYCELID, 2, 3, 3, FLESH_CAP, Items.SHROOMLIGHT);
	public static final String FLESH_FEAST = add("flesh_feast", Race.MYCELID, 2, 3, 3, FLESH_MEAT, Items.MUSHROOM_STEW);
	public static final String FLESH_HEARTY = add("flesh_hearty", Race.MYCELID, 2, 3, 3, FLESH_SPORE_BLOOD, Items.GOLDEN_APPLE);
	public static final String FLESH_REBIRTH = add("flesh_rebirth", Race.MYCELID, 2, 4, 5, FLESH_SUNPROOF, Items.TOTEM_OF_UNDYING);

	// Shepherds: Legion (echoes of the fallen), Pack (bonded beasts), Voice (the Shepherd itself). Same shape and
	// prices as the Mycelid tree.
	public static final String LEGION_CHEAP = add("legion_cheap", Race.SHEPHERD, 0, 1, 1, null, Items.BONE);
	public static final String LEGION_LAST_BREATH = add("legion_last_breath", Race.SHEPHERD, 0, 1, 1, null, Items.PACKED_ICE);
	public static final String LEGION_STURDY = add("legion_sturdy", Race.SHEPHERD, 0, 1, 1, null, Items.SHIELD);
	public static final String LEGION_CAPACITY = add("legion_capacity", Race.SHEPHERD, 0, 2, 2, LEGION_CHEAP, Items.ZOMBIE_HEAD);
	public static final String LEGION_SLEEPLESS = add("legion_sleepless", Race.SHEPHERD, 0, 2, 2, LEGION_CAPACITY, Items.CLOCK);
	public static final String LEGION_RETURN = add("legion_return", Race.SHEPHERD, 0, 2, 2, LEGION_LAST_BREATH, Items.SOUL_LANTERN);
	public static final String LEGION_WEAPONS = add("legion_weapons", Race.SHEPHERD, 0, 2, 2, LEGION_STURDY, Items.IRON_SWORD);
	public static final String LEGION_MORE = add("legion_more", Race.SHEPHERD, 0, 3, 3, LEGION_SLEEPLESS, Items.WITHER_SKELETON_SKULL);
	public static final String LEGION_LEECH = add("legion_leech", Race.SHEPHERD, 0, 3, 3, LEGION_RETURN, Items.SOUL_SAND);
	public static final String LEGION_RESONANCE = add("legion_resonance", Race.SHEPHERD, 0, 3, 3, LEGION_WEAPONS, Items.ECHO_SHARD);
	public static final String LEGION_ARMY = add("legion_army", Race.SHEPHERD, 0, 4, 5, LEGION_MORE, Items.WHITE_BANNER);

	public static final String PACK_FED = add("pack_fed", Race.SHEPHERD, 1, 1, 1, null, Items.COOKED_BEEF);
	public static final String PACK_THICK = add("pack_thick", Race.SHEPHERD, 1, 1, 1, null, Items.RABBIT_HIDE);
	public static final String PACK_FAST = add("pack_fast", Race.SHEPHERD, 1, 1, 1, null, Items.RABBIT_FOOT);
	public static final String PACK_LIGHT = add("pack_light", Race.SHEPHERD, 1, 2, 2, PACK_FED, Items.LEAD);
	public static final String PACK_LEVELS = add("pack_levels", Race.SHEPHERD, 1, 2, 2, PACK_LIGHT, Items.EXPERIENCE_BOTTLE);
	public static final String PACK_ARMOR = add("pack_armor", Race.SHEPHERD, 1, 2, 2, PACK_THICK, Items.LEATHER_HORSE_ARMOR);
	public static final String PACK_SKILL = add("pack_skill", Race.SHEPHERD, 1, 2, 2, PACK_FAST, Items.NETHER_STAR);
	public static final String PACK_NOT_TODAY = add("pack_not_today", Race.SHEPHERD, 1, 3, 3, PACK_LEVELS, Items.TOTEM_OF_UNDYING);
	public static final String PACK_FURY = add("pack_fury", Race.SHEPHERD, 1, 3, 3, PACK_ARMOR, Items.BLAZE_POWDER);
	public static final String PACK_INSTANT = add("pack_instant", Race.SHEPHERD, 1, 3, 3, PACK_SKILL, Items.FEATHER);
	public static final String PACK_ALPHA = add("pack_alpha", Race.SHEPHERD, 1, 4, 5, PACK_NOT_TODAY, Items.GOLDEN_HELMET);

	public static final String VOICE_DEEP = add("voice_deep", Race.SHEPHERD, 2, 1, 1, null, Items.NOTE_BLOCK);
	public static final String VOICE_CAPACITY = add("voice_capacity", Race.SHEPHERD, 2, 1, 1, null, Items.LEAD);
	public static final String VOICE_ARMOR = add("voice_armor", Race.SHEPHERD, 2, 1, 1, null, Items.IRON_CHESTPLATE);
	public static final String VOICE_RALLY = add("voice_rally", Race.SHEPHERD, 2, 2, 2, VOICE_DEEP, Items.GOAT_HORN);
	public static final String VOICE_MEND = add("voice_mend", Race.SHEPHERD, 2, 2, 2, VOICE_CAPACITY, Items.GLISTERING_MELON_SLICE);
	public static final String VOICE_STRENGTH = add("voice_strength", Race.SHEPHERD, 2, 2, 2, VOICE_ARMOR, Items.IRON_AXE);
	public static final String VOICE_LIFE = add("voice_life", Race.SHEPHERD, 2, 2, 2, VOICE_STRENGTH, Items.GOLDEN_APPLE);
	public static final String VOICE_AURA = add("voice_aura", Race.SHEPHERD, 2, 3, 3, VOICE_RALLY, Items.BEACON);
	public static final String VOICE_BANNER = add("voice_banner", Race.SHEPHERD, 2, 3, 3, VOICE_MEND, Items.WHITE_BANNER);
	public static final String VOICE_LOSS = add("voice_loss", Race.SHEPHERD, 2, 3, 3, VOICE_LIFE, Items.TOTEM_OF_UNDYING);
	public static final String VOICE_HEART = add("voice_heart", Race.SHEPHERD, 2, 4, 5, VOICE_AURA, Items.HEART_OF_THE_SEA);

	// The Frozen: Loop (the Rewind), Stasis (the Dome), Flow (time running faster for the Frozen themselves). Same shape and
	// prices as the other trees.
	public static final String LOOP_HEAL = add("loop_heal", Race.FROZEN, 0, 1, 1, null, Items.GLISTERING_MELON_SLICE);
	public static final String LOOP_CHEAP = add("loop_cheap", Race.FROZEN, 0, 1, 1, null, Items.SAND);
	public static final String LOOP_QUICK = add("loop_quick", Race.FROZEN, 0, 1, 1, null, Items.CLOCK);
	public static final String LOOP_LONG = add("loop_long", Race.FROZEN, 0, 2, 2, LOOP_HEAL, Items.COMPASS);
	public static final String LOOP_CLEANSE = add("loop_cleanse", Race.FROZEN, 0, 2, 2, LOOP_LONG, Items.MILK_BUCKET);
	public static final String LOOP_SHARDS = add("loop_shards", Race.FROZEN, 0, 2, 2, LOOP_CHEAP, Items.PACKED_ICE);
	public static final String LOOP_HASTE = add("loop_haste", Race.FROZEN, 0, 2, 2, LOOP_QUICK, Items.FEATHER);
	public static final String LOOP_STRIKE = add("loop_strike", Race.FROZEN, 0, 3, 3, LOOP_CLEANSE, Items.DIAMOND_SWORD);
	public static final String LOOP_RETURN = add("loop_return", Race.FROZEN, 0, 3, 3, LOOP_SHARDS, Items.ENDER_PEARL);
	public static final String LOOP_ALLY = add("loop_ally", Race.FROZEN, 0, 3, 3, LOOP_HASTE, Items.LEAD);
	public static final String LOOP_ETERNAL = add("loop_eternal", Race.FROZEN, 0, 4, 5, LOOP_STRIKE, Items.NETHER_STAR);

	public static final String STASIS_WIDE = add("stasis_wide", Race.FROZEN, 1, 1, 1, null, Items.BLUE_ICE);
	public static final String STASIS_LONG = add("stasis_long", Race.FROZEN, 1, 1, 1, null, Items.CLOCK);
	public static final String STASIS_CHEAP = add("stasis_cheap", Race.FROZEN, 1, 1, 1, null, Items.SAND);
	public static final String STASIS_DEEP = add("stasis_deep", Race.FROZEN, 1, 2, 2, STASIS_WIDE, Items.STONE_SWORD);
	public static final String STASIS_RETURN = add("stasis_return", Race.FROZEN, 1, 2, 2, STASIS_DEEP, Items.ARROW);
	public static final String STASIS_FOLLOW = add("stasis_follow", Race.FROZEN, 1, 2, 2, STASIS_LONG, Items.LEATHER_BOOTS);
	public static final String STASIS_MEND = add("stasis_mend", Race.FROZEN, 1, 2, 2, STASIS_CHEAP, Items.GOLDEN_APPLE);
	public static final String STASIS_FREEZE = add("stasis_freeze", Race.FROZEN, 1, 3, 3, STASIS_RETURN, Items.ICE);
	public static final String STASIS_SHATTER = add("stasis_shatter", Race.FROZEN, 1, 3, 3, STASIS_FOLLOW, Items.GLASS_PANE);
	public static final String STASIS_WARD = add("stasis_ward", Race.FROZEN, 1, 3, 3, STASIS_MEND, Items.SHIELD);
	public static final String STASIS_STOP = add("stasis_stop", Race.FROZEN, 1, 4, 5, STASIS_FREEZE, Items.PAINTING);

	public static final String FLOW_HASTE = add("flow_haste", Race.FROZEN, 2, 1, 1, null, Items.IRON_PICKAXE);
	public static final String FLOW_SWIFT = add("flow_swift", Race.FROZEN, 2, 1, 1, null, Items.RABBIT_FOOT);
	public static final String FLOW_AREA = add("flow_area", Race.FROZEN, 2, 1, 1, null, Items.FURNACE);
	public static final String FLOW_VISCOUS = add("flow_viscous", Race.FROZEN, 2, 2, 2, FLOW_HASTE, Items.SLIME_BALL);
	public static final String FLOW_ECHO = add("flow_echo", Race.FROZEN, 2, 2, 2, FLOW_VISCOUS, Items.IRON_SWORD);
	public static final String FLOW_FEED = add("flow_feed", Race.FROZEN, 2, 2, 2, FLOW_SWIFT, Items.SAND);
	public static final String FLOW_REGEN = add("flow_regen", Race.FROZEN, 2, 2, 2, FLOW_AREA, Items.APPLE);
	public static final String FLOW_OVERTAKE = add("flow_overtake", Race.FROZEN, 2, 3, 3, FLOW_ECHO, Items.FEATHER);
	public static final String FLOW_SAND = add("flow_sand", Race.FROZEN, 2, 3, 3, FLOW_FEED, Items.SANDSTONE);
	public static final String FLOW_RECHARGE = add("flow_recharge", Race.FROZEN, 2, 3, 3, FLOW_REGEN, Items.CLOCK);
	public static final String FLOW_CURRENT = add("flow_current", Race.FROZEN, 2, 4, 5, FLOW_OVERTAKE, Items.HEART_OF_THE_SEA);

	// The Bloodkin: Fang (the dash and the bite), Thirst (the blood itself, the Harvest, the Sacrifice), Night (senses,
	// the dark, the bat swarm). Same shape and prices as the other trees.
	public static final String FANG_LEECH = add("fang_leech", Race.BLOODKIN, 0, 1, 1, null, Items.GHAST_TEAR);
	public static final String FANG_BLEED = add("fang_bleed", Race.BLOODKIN, 0, 1, 1, null, Items.REDSTONE);
	public static final String FANG_REACH = add("fang_reach", Race.BLOODKIN, 0, 1, 1, null, Items.FEATHER);
	public static final String FANG_BITE = add("fang_bite", Race.BLOODKIN, 0, 2, 2, FANG_LEECH, Items.BONE);
	public static final String FANG_EXECUTE = add("fang_execute", Race.BLOODKIN, 0, 2, 2, FANG_BITE, Items.SKELETON_SKULL);
	public static final String FANG_DEEP = add("fang_deep", Race.BLOODKIN, 0, 2, 2, FANG_BLEED, Items.FERMENTED_SPIDER_EYE);
	public static final String FANG_DOUBLE = add("fang_double", Race.BLOODKIN, 0, 2, 2, FANG_REACH, Items.RABBIT_FOOT);
	public static final String FANG_FRENZY = add("fang_frenzy", Race.BLOODKIN, 0, 3, 3, FANG_EXECUTE, Items.BLAZE_POWDER);
	public static final String FANG_OPEN = add("fang_open", Race.BLOODKIN, 0, 3, 3, FANG_DEEP, Items.RED_DYE);
	public static final String FANG_TRAIL = add("fang_trail", Race.BLOODKIN, 0, 3, 3, FANG_DOUBLE, Items.GLISTERING_MELON_SLICE);
	public static final String FANG_FEAST = add("fang_feast", Race.BLOODKIN, 0, 4, 5, FANG_FRENZY, Items.ENCHANTED_GOLDEN_APPLE);

	public static final String THIRST_SLOW = add("thirst_slow", Race.BLOODKIN, 1, 1, 1, null, Items.GLASS_BOTTLE);
	public static final String THIRST_HARVEST = add("thirst_harvest", Race.BLOODKIN, 1, 1, 1, null, Items.NETHER_WART);
	public static final String THIRST_SACRIFICE = add("thirst_sacrifice", Race.BLOODKIN, 1, 1, 1, null, Items.IRON_SWORD);
	public static final String THIRST_HUNGER = add("thirst_hunger", Race.BLOODKIN, 1, 2, 2, THIRST_SLOW, Items.ROTTEN_FLESH);
	public static final String THIRST_EDGE = add("thirst_edge", Race.BLOODKIN, 1, 2, 2, THIRST_HUNGER, Items.GOLDEN_APPLE);
	public static final String THIRST_DRAIN = add("thirst_drain", Race.BLOODKIN, 1, 2, 2, THIRST_HARVEST, Items.CRIMSON_FUNGUS);
	public static final String THIRST_OFFERING = add("thirst_offering", Race.BLOODKIN, 1, 2, 2, THIRST_SACRIFICE, Items.TOTEM_OF_UNDYING);
	public static final String THIRST_RAGE = add("thirst_rage", Race.BLOODKIN, 1, 3, 3, THIRST_EDGE, Items.MAGMA_CREAM);
	public static final String THIRST_MARK = add("thirst_mark", Race.BLOODKIN, 1, 3, 3, THIRST_DRAIN, Items.TARGET);
	public static final String THIRST_VESSEL = add("thirst_vessel", Race.BLOODKIN, 1, 3, 3, THIRST_OFFERING, Items.CAULDRON);
	public static final String THIRST_STORM = add("thirst_storm", Race.BLOODKIN, 1, 4, 5, THIRST_RAGE, Items.CRIMSON_ROOTS);

	public static final String NIGHT_EYES = add("night_eyes", Race.BLOODKIN, 2, 1, 1, null, Items.ENDER_EYE);
	public static final String NIGHT_SENSE = add("night_sense", Race.BLOODKIN, 2, 1, 1, null, Items.SCULK_SENSOR);
	public static final String NIGHT_SHADE = add("night_shade", Race.BLOODKIN, 2, 1, 1, null, Items.LEATHER_HELMET);
	public static final String NIGHT_HUNTER = add("night_hunter", Race.BLOODKIN, 2, 2, 2, NIGHT_EYES, Items.IRON_SWORD);
	public static final String NIGHT_BATS = add("night_bats", Race.BLOODKIN, 2, 2, 2, NIGHT_HUNTER, Items.BAT_SPAWN_EGG);
	public static final String NIGHT_HUSH = add("night_hush", Race.BLOODKIN, 2, 2, 2, NIGHT_SENSE, Items.LEATHER_BOOTS);
	public static final String NIGHT_CLOAK = add("night_cloak", Race.BLOODKIN, 2, 2, 2, NIGHT_SHADE, Items.BLACK_BANNER);
	public static final String NIGHT_SWARM = add("night_swarm", Race.BLOODKIN, 2, 3, 3, NIGHT_BATS, Items.SPIDER_EYE);
	public static final String NIGHT_AMBUSH = add("night_ambush", Race.BLOODKIN, 2, 3, 3, NIGHT_HUSH, Items.STONE_SWORD);
	public static final String NIGHT_BLOOD = add("night_blood", Race.BLOODKIN, 2, 3, 3, NIGHT_CLOAK, Items.CLOCK);
	public static final String NIGHT_PRINCE = add("night_prince", Race.BLOODKIN, 2, 4, 5, NIGHT_SWARM, Items.WITHER_ROSE);

	// The Voidborn: Step (passing through), Abyss (the Well and the Excision), Pocket (the world of their own).
	public static final String STEP_FAR = add("step_far", Race.VOIDBORN, 0, 1, 1, null, Items.ENDER_PEARL);
	public static final String STEP_CHEAP = add("step_cheap", Race.VOIDBORN, 0, 1, 1, null, Items.AMETHYST_SHARD);
	public static final String STEP_QUICK = add("step_quick", Race.VOIDBORN, 0, 1, 1, null, Items.FEATHER);
	public static final String STEP_CUT = add("step_cut", Race.VOIDBORN, 0, 2, 2, STEP_FAR, Items.IRON_SWORD);
	public static final String STEP_REND = add("step_rend", Race.VOIDBORN, 0, 2, 2, STEP_CUT, Items.FERMENTED_SPIDER_EYE);
	public static final String STEP_PULL = add("step_pull", Race.VOIDBORN, 0, 2, 2, STEP_QUICK, Items.LEAD);
	public static final String STEP_VEIL = add("step_veil", Race.VOIDBORN, 0, 2, 2, STEP_CHEAP, Items.GLASS);
	public static final String STEP_MEND = add("step_mend", Race.VOIDBORN, 0, 3, 3, STEP_REND, Items.GLISTERING_MELON_SLICE);
	public static final String STEP_RIFT = add("step_rift", Race.VOIDBORN, 0, 3, 3, STEP_PULL, Items.ENDER_EYE);
	public static final String STEP_ALLY = add("step_ally", Race.VOIDBORN, 0, 3, 3, STEP_VEIL, Items.SADDLE);
	public static final String STEP_SEVER = add("step_sever", Race.VOIDBORN, 0, 4, 5, STEP_MEND, Items.NETHERITE_SWORD);

	public static final String WELL_WIDE = add("well_wide", Race.VOIDBORN, 1, 1, 1, null, Items.CRYING_OBSIDIAN);
	public static final String WELL_LONG = add("well_long", Race.VOIDBORN, 1, 1, 1, null, Items.CLOCK);
	public static final String EXCISE_LONG = add("excise_long", Race.VOIDBORN, 1, 1, 1, null, Items.CHORUS_FRUIT);
	public static final String WELL_HEART = add("well_heart", Race.VOIDBORN, 1, 2, 2, WELL_WIDE, Items.MAGMA_CREAM);
	public static final String WELL_COLLAPSE = add("well_collapse", Race.VOIDBORN, 1, 2, 2, WELL_HEART, Items.TNT);
	public static final String WELL_EAT = add("well_eat", Race.VOIDBORN, 1, 2, 2, WELL_LONG, Items.ARROW);
	public static final String EXCISE_CHEAP = add("excise_cheap", Race.VOIDBORN, 1, 2, 2, EXCISE_LONG, Items.GOLD_NUGGET);
	public static final String WELL_HOLD = add("well_hold", Race.VOIDBORN, 1, 3, 3, WELL_COLLAPSE, Items.COBWEB);
	public static final String WELL_HUNGER = add("well_hunger", Race.VOIDBORN, 1, 3, 3, WELL_EAT, Items.ECHO_SHARD);
	public static final String EXCISE_DROP = add("excise_drop", Race.VOIDBORN, 1, 3, 3, EXCISE_CHEAP, Items.ANVIL);
	public static final String ABYSS_GAZE = add("abyss_gaze", Race.VOIDBORN, 1, 4, 5, WELL_HOLD, Items.NETHER_STAR);

	public static final String POCKET_CHEAP = add("pocket_cheap", Race.VOIDBORN, 2, 1, 1, null, Items.ENDER_CHEST);
	public static final String POCKET_QUICK = add("pocket_quick", Race.VOIDBORN, 2, 1, 1, null, Items.COMPASS);
	public static final String POCKET_REST = add("pocket_rest", Race.VOIDBORN, 2, 1, 1, null, Items.RED_BED);
	public static final String POCKET_WIDE = add("pocket_wide", Race.VOIDBORN, 2, 2, 2, POCKET_REST, Items.PURPUR_BLOCK);
	public static final String POCKET_WATER = add("pocket_water", Race.VOIDBORN, 2, 2, 2, POCKET_WIDE, Items.WATER_BUCKET);
	public static final String POCKET_GUESTS = add("pocket_guests", Race.VOIDBORN, 2, 2, 2, POCKET_CHEAP, Items.CAKE);
	public static final String POCKET_WARD = add("pocket_ward", Race.VOIDBORN, 2, 2, 2, POCKET_QUICK, Items.SHIELD);
	public static final String POCKET_GARDEN = add("pocket_garden", Race.VOIDBORN, 2, 3, 3, POCKET_WATER, Items.WHEAT);
	public static final String POCKET_STORM = add("pocket_storm", Race.VOIDBORN, 2, 3, 3, POCKET_GUESTS, Items.LEATHER_HELMET);
	public static final String POCKET_HOME = add("pocket_home", Race.VOIDBORN, 2, 3, 3, POCKET_WARD, Items.RECOVERY_COMPASS);
	public static final String POCKET_HEARTH = add("pocket_hearth", Race.VOIDBORN, 2, 4, 5, POCKET_GARDEN, Items.RESPAWN_ANCHOR);

	// Mercenaries: Deals (the abilities paid in blood, hunger and armour), Contracts (the work and the Mage's Shop),
	// Craft (a professional's blows and hide).
	public static final String DEAL_CHEAP = add("deal_cheap", Race.MERCENARY, 0, 1, 1, null, Items.REDSTONE);
	public static final String DEAL_SHARP = add("deal_sharp", Race.MERCENARY, 0, 1, 1, null, Items.FLINT);
	public static final String DEAL_LONG = add("deal_long", Race.MERCENARY, 0, 1, 1, null, Items.CLOCK);
	public static final String DEAL_DOUBLE = add("deal_double", Race.MERCENARY, 0, 2, 2, DEAL_CHEAP, Items.IRON_SWORD);
	public static final String DEAL_GREED = add("deal_greed", Race.MERCENARY, 0, 2, 2, DEAL_DOUBLE, Items.GOLD_INGOT);
	public static final String DEAL_CURSE = add("deal_curse", Race.MERCENARY, 0, 2, 2, DEAL_SHARP, Items.FERMENTED_SPIDER_EYE);
	public static final String DEAL_IRON = add("deal_iron", Race.MERCENARY, 0, 2, 2, DEAL_LONG, Items.IRON_CHESTPLATE);
	public static final String DEAL_INTEREST = add("deal_interest", Race.MERCENARY, 0, 3, 3, DEAL_GREED, Items.GLISTERING_MELON_SLICE);
	public static final String DEAL_FREE = add("deal_free", Race.MERCENARY, 0, 3, 3, DEAL_CURSE, Items.BREAD);
	public static final String DEAL_BUYOUT = add("deal_buyout", Race.MERCENARY, 0, 3, 3, DEAL_IRON, Items.ANVIL);
	public static final String DEAL_STAKE = add("deal_stake", Race.MERCENARY, 0, 4, 5, DEAL_INTEREST, Items.GOLD_BLOCK);

	public static final String CON_SLOTS = add("con_slots", Race.MERCENARY, 1, 1, 1, null, Items.PAPER);
	public static final String CON_TOKENS = add("con_tokens", Race.MERCENARY, 1, 1, 1, null, Items.GOLD_NUGGET);
	public static final String CON_SCOUT = add("con_scout", Race.MERCENARY, 1, 1, 1, null, Items.SPYGLASS);
	public static final String CON_OFFERS = add("con_offers", Race.MERCENARY, 1, 2, 2, CON_SLOTS, Items.BOOK);
	public static final String CON_REROLL = add("con_reroll", Race.MERCENARY, 1, 2, 2, CON_OFFERS, Items.WRITABLE_BOOK);
	public static final String CON_XP = add("con_xp", Race.MERCENARY, 1, 2, 2, CON_TOKENS, Items.EXPERIENCE_BOTTLE);
	public static final String CON_BOUNTY = add("con_bounty", Race.MERCENARY, 1, 2, 2, CON_SCOUT, Items.BOW);
	public static final String CON_ELITE = add("con_elite", Race.MERCENARY, 1, 3, 3, CON_REROLL, Items.WITHER_SKELETON_SKULL);
	public static final String CON_DISCOUNT = add("con_discount", Race.MERCENARY, 1, 3, 3, CON_XP, Items.EMERALD);
	public static final String CON_RUSH = add("con_rush", Race.MERCENARY, 1, 3, 3, CON_BOUNTY, Items.SUGAR);
	public static final String CON_RANSOM = add("con_ransom", Race.MERCENARY, 1, 4, 5, CON_ELITE, Items.TOTEM_OF_UNDYING);

	public static final String CRAFT_GRIP = add("craft_grip", Race.MERCENARY, 2, 1, 1, null, Items.STONE_SWORD);
	public static final String CRAFT_HIDE = add("craft_hide", Race.MERCENARY, 2, 1, 1, null, Items.LEATHER_CHESTPLATE);
	public static final String CRAFT_MARCH = add("craft_march", Race.MERCENARY, 2, 1, 1, null, Items.LEATHER_BOOTS);
	public static final String CRAFT_RANGED = add("craft_ranged", Race.MERCENARY, 2, 2, 2, CRAFT_GRIP, Items.CROSSBOW);
	public static final String CRAFT_EXECUTE = add("craft_execute", Race.MERCENARY, 2, 2, 2, CRAFT_RANGED, Items.IRON_AXE);
	public static final String CRAFT_VETERAN = add("craft_veteran", Race.MERCENARY, 2, 2, 2, CRAFT_HIDE, Items.IRON_HELMET);
	public static final String CRAFT_SECOND = add("craft_second", Race.MERCENARY, 2, 2, 2, CRAFT_MARCH, Items.GOLDEN_CARROT);
	public static final String CRAFT_WOLF = add("craft_wolf", Race.MERCENARY, 2, 3, 3, CRAFT_EXECUTE, Items.BONE);
	public static final String CRAFT_IRON = add("craft_iron", Race.MERCENARY, 2, 3, 3, CRAFT_VETERAN, Items.IRON_BLOCK);
	public static final String CRAFT_MARK = add("craft_mark", Race.MERCENARY, 2, 3, 3, CRAFT_SECOND, Items.TARGET);
	public static final String CRAFT_LEGEND = add("craft_legend", Race.MERCENARY, 2, 4, 5, CRAFT_WOLF, Items.NETHERITE_SWORD);

	// Tempters: Kiss (the drain itself, the Undying crown), Wings (the flight and the hunt by night, the Succubus
	// crown), Charm (thralls and the lullaby, the Broken Heart crown on Alt).
	public static final String KISS_DEEP = add("kiss_deep", Race.TEMPTER, 0, 1, 1, null, Items.ROSE_BUSH);
	public static final String KISS_CHEAP = add("kiss_cheap", Race.TEMPTER, 0, 1, 1, null, Items.SUGAR);
	public static final String KISS_REACH = add("kiss_reach", Race.TEMPTER, 0, 1, 1, null, Items.FEATHER);
	public static final String KISS_POISON = add("kiss_poison", Race.TEMPTER, 0, 2, 2, KISS_DEEP, Items.SPIDER_EYE);
	public static final String KISS_CHARM = add("kiss_charm", Race.TEMPTER, 0, 2, 2, KISS_POISON, Items.POPPY);
	public static final String KISS_QUICK = add("kiss_quick", Race.TEMPTER, 0, 2, 2, KISS_CHEAP, Items.RABBIT_FOOT);
	public static final String KISS_SHARE = add("kiss_share", Race.TEMPTER, 0, 2, 2, KISS_REACH, Items.HONEY_BOTTLE);
	public static final String KISS_LIFE = add("kiss_life", Race.TEMPTER, 0, 3, 3, KISS_CHARM, Items.GHAST_TEAR);
	public static final String KISS_SHIELD = add("kiss_shield", Race.TEMPTER, 0, 3, 3, KISS_QUICK, Items.GOLDEN_APPLE);
	public static final String KISS_ALLY = add("kiss_ally", Race.TEMPTER, 0, 3, 3, KISS_SHARE, Items.CAKE);
	public static final String KISS_UNDYING = add("kiss_undying", Race.TEMPTER, 0, 4, 5, KISS_LIFE, Items.TOTEM_OF_UNDYING);

	public static final String WING_FAR = add("wing_far", Race.TEMPTER, 1, 1, 1, null, Items.PHANTOM_MEMBRANE);
	public static final String WING_CHEAP = add("wing_cheap", Race.TEMPTER, 1, 1, 1, null, Items.FEATHER);
	public static final String WING_SOFT = add("wing_soft", Race.TEMPTER, 1, 1, 1, null, Items.WHITE_WOOL);
	public static final String WING_TWICE = add("wing_twice", Race.TEMPTER, 1, 2, 2, WING_FAR, Items.ELYTRA);
	public static final String WING_DIVE = add("wing_dive", Race.TEMPTER, 1, 2, 2, WING_TWICE, Items.ANVIL);
	public static final String WING_KISS = add("wing_kiss", Race.TEMPTER, 1, 2, 2, WING_CHEAP, Items.PINK_TULIP);
	public static final String WING_GLIDE = add("wing_glide", Race.TEMPTER, 1, 2, 2, WING_SOFT, Items.STRING);
	public static final String WING_SWOON = add("wing_swoon", Race.TEMPTER, 1, 3, 3, WING_DIVE, Items.PINK_PETALS);
	public static final String WING_RUSH = add("wing_rush", Race.TEMPTER, 1, 3, 3, WING_KISS, Items.SUGAR);
	public static final String WING_NIGHT = add("wing_night", Race.TEMPTER, 1, 3, 3, WING_GLIDE, Items.CLOCK);
	public static final String WING_SUCCUBUS = add("wing_succubus", Race.TEMPTER, 1, 4, 5, WING_SWOON, Items.DRAGON_BREATH);

	public static final String CHARM_MORE = add("charm_more", Race.TEMPTER, 2, 1, 1, null, Items.LEAD);
	public static final String CHARM_LONG = add("charm_long", Race.TEMPTER, 2, 1, 1, null, Items.CLOCK);
	public static final String SLEEP_WIDE = add("sleep_wide", Race.TEMPTER, 2, 1, 1, null, Items.WHITE_BED);
	public static final String CHARM_STRONG = add("charm_strong", Race.TEMPTER, 2, 2, 2, CHARM_MORE, Items.IRON_SWORD);
	public static final String CHARM_LURE = add("charm_lure", Race.TEMPTER, 2, 2, 2, CHARM_STRONG, Items.BELL);
	public static final String CHARM_CHEAP = add("charm_cheap", Race.TEMPTER, 2, 2, 2, CHARM_LONG, Items.AMETHYST_SHARD);
	public static final String SLEEP_LONG = add("sleep_long", Race.TEMPTER, 2, 2, 2, SLEEP_WIDE, Items.PINK_BED);
	public static final String CHARM_HEART = add("charm_heart", Race.TEMPTER, 2, 3, 3, CHARM_LURE, Items.RED_DYE);
	public static final String CHARM_RETURN = add("charm_return", Race.TEMPTER, 2, 3, 3, CHARM_CHEAP, Items.EMERALD);
	public static final String SLEEP_DREAM = add("sleep_dream", Race.TEMPTER, 2, 3, 3, SLEEP_LONG, Items.PHANTOM_MEMBRANE);
	public static final String THREAD_BREAK = add("thread_break", Race.TEMPTER, 2, 4, 5, CHARM_HEART, Items.REDSTONE_BLOCK);

	// Masked: Gallery (more faces, longer worn, the Thousand Faces crown), Tearing (the rip itself and what it gives,
	// the Last Face crown), Kin (the disguise and the retinue of one's own kind, the King of Masks crown).
	public static final String GAL_SLOTS = add("gal_slots", Race.MASKED, 0, 1, 1, null, Items.ITEM_FRAME);
	public static final String GAL_LONG = add("gal_long", Race.MASKED, 0, 1, 1, null, Items.CLOCK);
	public static final String GAL_CHEAP = add("gal_cheap", Race.MASKED, 0, 1, 1, null, Items.LEATHER);
	public static final String GAL_MORE = add("gal_more", Race.MASKED, 0, 2, 2, GAL_SLOTS, Items.GLOW_ITEM_FRAME);
	public static final String GAL_TRACE = add("gal_trace", Race.MASKED, 0, 2, 2, GAL_MORE, Items.PHANTOM_MEMBRANE);
	public static final String GAL_FRESH = add("gal_fresh", Race.MASKED, 0, 2, 2, GAL_LONG, Items.ROTTEN_FLESH);
	public static final String GAL_QUICK = add("gal_quick", Race.MASKED, 0, 2, 2, GAL_CHEAP, Items.SUGAR);
	public static final String GAL_POWER = add("gal_power", Race.MASKED, 0, 3, 3, GAL_TRACE, Items.BLAZE_POWDER);
	public static final String GAL_KEEP = add("gal_keep", Race.MASKED, 0, 3, 3, GAL_FRESH, Items.HONEYCOMB);
	public static final String GAL_ESSENCE = add("gal_essence", Race.MASKED, 0, 3, 3, GAL_QUICK, Items.GLOWSTONE_DUST);
	public static final String GAL_THOUSAND = add("gal_thousand", Race.MASKED, 0, 4, 5, GAL_POWER, Items.CARVED_PUMPKIN);

	public static final String RIP_WEAK = add("rip_weak", Race.MASKED, 1, 1, 1, null, Items.IRON_SWORD);
	public static final String RIP_REACH = add("rip_reach", Race.MASKED, 1, 1, 1, null, Items.FISHING_ROD);
	public static final String RIP_HEAL = add("rip_heal", Race.MASKED, 1, 1, 1, null, Items.GLISTERING_MELON_SLICE);
	public static final String RIP_FEED = add("rip_feed", Race.MASKED, 1, 2, 2, RIP_WEAK, Items.EXPERIENCE_BOTTLE);
	public static final String RIP_SHOCK = add("rip_shock", Race.MASKED, 1, 2, 2, RIP_FEED, Items.SKELETON_SKULL);
	public static final String RIP_TEAR = add("rip_tear", Race.MASKED, 1, 2, 2, RIP_REACH, Items.SHEARS);
	public static final String RIP_ARMOR = add("rip_armor", Race.MASKED, 1, 2, 2, RIP_HEAL, Items.GOLDEN_APPLE);
	public static final String RIP_CHAIN = add("rip_chain", Race.MASKED, 1, 3, 3, RIP_SHOCK, Items.CHAIN);
	public static final String RIP_EXECUTE = add("rip_execute", Race.MASKED, 1, 3, 3, RIP_TEAR, Items.WITHER_ROSE);
	public static final String RIP_FACELESS = add("rip_faceless", Race.MASKED, 1, 3, 3, RIP_ARMOR, Items.BONE);
	public static final String RIP_LAST = add("rip_last", Race.MASKED, 1, 4, 5, RIP_CHAIN, Items.TOTEM_OF_UNDYING);

	public static final String KIN_DEEP = add("kin_deep", Race.MASKED, 2, 1, 1, null, Items.ENDER_EYE);
	public static final String KIN_RETINUE = add("kin_retinue", Race.MASKED, 2, 1, 1, null, Items.BELL);
	public static final String KIN_SCENT = add("kin_scent", Race.MASKED, 2, 1, 1, null, Items.FERMENTED_SPIDER_EYE);
	public static final String KIN_LONG = add("kin_long", Race.MASKED, 2, 2, 2, KIN_DEEP, Items.LEAD);
	public static final String KIN_STRONG = add("kin_strong", Race.MASKED, 2, 2, 2, KIN_LONG, Items.IRON_CHESTPLATE);
	public static final String KIN_HEAL = add("kin_heal", Race.MASKED, 2, 2, 2, KIN_RETINUE, Items.COOKED_BEEF);
	public static final String KIN_SKIN = add("kin_skin", Race.MASKED, 2, 2, 2, KIN_SCENT, Items.RABBIT_HIDE);
	public static final String KIN_GUARD = add("kin_guard", Race.MASKED, 2, 3, 3, KIN_STRONG, Items.SHIELD);
	public static final String KIN_FEAST = add("kin_feast", Race.MASKED, 2, 3, 3, KIN_HEAL, Items.SPIDER_EYE);
	public static final String KIN_ALL = add("kin_all", Race.MASKED, 2, 3, 3, KIN_SKIN, Items.ARMOR_STAND);
	public static final String KIN_KING = add("kin_king", Race.MASKED, 2, 4, 5, KIN_GUARD, Items.GOLDEN_HELMET);

	private Talents() {
	}

	private static String add(String id, Race race, int branch, int tier, int cost, String requires, Item icon) {
		BY_ID.put(id, new Talent(id, race, branch, tier, cost, requires, icon));
		return id;
	}

	public static Optional<Talent> byId(String id) {
		return Optional.ofNullable(BY_ID.get(id));
	}

	public static List<Talent> of(Race race) {
		List<Talent> list = new ArrayList<>();
		BY_ID.values().stream().filter(t -> t.race() == race).forEach(list::add);
		return Collections.unmodifiableList(list);
	}

	public static void init() {
		// Loads the class, so every talent is registered before the first lookup
	}
}
