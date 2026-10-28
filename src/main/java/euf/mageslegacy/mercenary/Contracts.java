package euf.mageslegacy.mercenary;

import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.RaceData;
import euf.mageslegacy.race.Talents;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The board of the Mage's Shop. Every 20 minutes of play it offers a few contracts from the tiers the Mercenary has
 * opened (the same milestones as the talent tree); the Mercenary takes some, works them off and is paid in race
 * experience (the only way the race grows) and tokens. The board is the limit: a farm can't run it faster.
 */
public final class Contracts {
	/** Race experience and tokens a contract pays, by tier. */
	public static final int[] XP = {80, 140, 220, 320};
	public static final int[] TOKENS = {10, 16, 24, 34};
	/** A day of the board: 20 minutes of play, whatever the beds and the Pocket do to the sun. */
	public static final long DAY = 24000;

	private record Template(Contract.Kind kind, String target, int amount, int tier) {}

	private static final List<Template> TEMPLATES = new ArrayList<>();

	static {
		// Tier I: the overworld
		hunt(1, "zombie", 10);
		hunt(1, "skeleton", 8);
		hunt(1, "spider", 8);
		hunt(1, "creeper", 5);
		hunt(1, "drowned", 6);
		hunt(1, "slime", 6);
		hunt(1, "husk", 6);
		hunt(1, "stray", 5);
		hunt(1, "witch", 2);
		hunt(1, "pillager", 4);
		gather(1, "coal", 32);
		gather(1, "iron_ingot", 12);
		gather(1, "copper_ingot", 24);
		gather(1, "leather", 8);
		gather(1, "string", 12);
		gather(1, "bone", 16);
		gather(1, "gunpowder", 8);
		gather(1, "wheat", 32);
		gather(1, "redstone", 24);
		gather(1, "lapis_lazuli", 16);
		for (String biome : List.of("desert", "jungle", "swamp", "snowy_plains", "badlands", "savanna", "taiga", "dark_forest", "cherry_grove",
				"mangrove_swamp", "ice_spikes", "flower_forest")) {
			explore(1, biome);
		}
		// Tier II: after the Nether
		hunt(2, "blaze", 6);
		hunt(2, "magma_cube", 6);
		hunt(2, "wither_skeleton", 4);
		hunt(2, "ghast", 2);
		hunt(2, "hoglin", 3);
		hunt(2, "enderman", 5);
		gather(2, "quartz", 32);
		gather(2, "gold_ingot", 12);
		gather(2, "glowstone_dust", 16);
		gather(2, "blaze_rod", 4);
		gather(2, "obsidian", 8);
		gather(2, "nether_wart", 16);
		gather(2, "ender_pearl", 4);
		for (String biome : List.of("crimson_forest", "warped_forest", "basalt_deltas", "soul_sand_valley")) {
			explore(2, biome);
		}
		// Tier III: after the Wither
		hunt(3, "guardian", 6);
		hunt(3, "vindicator", 6);
		hunt(3, "evoker", 1);
		hunt(3, "phantom", 4);
		hunt(3, "ravager", 1);
		hunt(3, "piglin_brute", 2);
		gather(3, "diamond", 4);
		gather(3, "emerald", 8);
		gather(3, "prismarine_shard", 16);
		gather(3, "amethyst_shard", 16);
		gather(3, "ghast_tear", 2);
		gather(3, "slime_ball", 12);
		for (String biome : List.of("deep_dark", "lush_caves", "dripstone_caves", "mushroom_fields")) {
			explore(3, biome);
		}
		// Tier IV: after the dragon
		hunt(4, "shulker", 4);
		hunt(4, "enderman", 15);
		hunt(4, "elder_guardian", 1);
		gather(4, "chorus_fruit", 32);
		gather(4, "shulker_shell", 2);
		gather(4, "phantom_membrane", 4);
		gather(4, "popped_chorus_fruit", 16);
		for (String biome : List.of("end_highlands", "end_midlands", "end_barrens", "small_end_islands")) {
			explore(4, biome);
		}
	}

	private Contracts() {
	}

	private static void hunt(int tier, String id, int amount) {
		TEMPLATES.add(new Template(Contract.Kind.HUNT, "minecraft:" + id, amount, tier));
	}

	private static void gather(int tier, String id, int amount) {
		TEMPLATES.add(new Template(Contract.Kind.GATHER, "minecraft:" + id, amount, tier));
	}

	private static void explore(int tier, String id) {
		TEMPLATES.add(new Template(Contract.Kind.EXPLORE, "minecraft:" + id, 1, tier));
	}

	public static long today(ServerPlayer player) {
		return player.getServer().overworld().getGameTime() / DAY;
	}

	public static int slots(RaceData data) {
		return data.has(Talents.CON_SLOTS) ? 3 : 2;
	}

	public static int offerCount(RaceData data) {
		return data.has(Talents.CON_OFFERS) ? 4 : 3;
	}

	/** A new day: new offers on the board, new prices in the shop. */
	public static void refresh(ServerPlayer player, RaceData data) {
		MercData merc = data.merc;
		long today = today(player);
		if (merc.day == today) {
			return;
		}
		merc.day = today;
		merc.rerolls = 0;
		merc.bought.clear();
		fill(player, data);
		Shop.restock(player, data);
	}

	/** Lays out a fresh board. */
	public static void fill(ServerPlayer player, RaceData data) {
		MercData merc = data.merc;
		merc.offers.clear();
		RandomSource random = player.getRandom();
		int maxTier = Progression.openTiers(player);
		for (int i = 0; i < offerCount(data); i++) {
			Contract c = roll(random, data, maxTier);
			if (c != null) {
				merc.offers.add(c);
			}
		}
		data.dirty = true;
	}

	/** One offer: mostly from the highest open tier, never the same job twice on the board or in hand. */
	private static Contract roll(RandomSource random, RaceData data, int maxTier) {
		int tier = maxTier > 1 && random.nextFloat() < 0.6f ? maxTier : 1 + random.nextInt(maxTier);
		List<Template> pool = new ArrayList<>();
		for (Template t : TEMPLATES) {
			if (t.tier() == tier && data.merc.offers.stream().noneMatch(c -> c.target.equals(t.target()))
					&& data.merc.active.stream().noneMatch(c -> c.target.equals(t.target()))) {
				pool.add(t);
			}
		}
		if (pool.isEmpty()) {
			return null;
		}
		Template t = pool.get(random.nextInt(pool.size()));
		boolean elite = data.has(Talents.CON_ELITE) && t.kind() != Contract.Kind.EXPLORE && random.nextFloat() < 0.25f;
		float pay = elite ? 2.5f : 1;
		int xp = Math.round(XP[tier - 1] * pay * (data.has(Talents.CON_XP) ? 1.2f : 1));
		int tokens = Math.round(TOKENS[tier - 1] * pay * (data.has(Talents.CON_TOKENS) ? 1.25f : 1));
		return new Contract(t.kind(), t.target(), elite ? t.amount() * 2 : t.amount(), tier, elite, tokens, xp);
	}

	public static boolean accept(ServerPlayer player, RaceData data, int index) {
		MercData merc = data.merc;
		if (index < 0 || index >= merc.offers.size()) {
			return false;
		}
		if (merc.active.size() >= slots(data)) {
			player.displayClientMessage(Component.translatable("mages_legacy.merc.no_slot", slots(data)), true);
			return false;
		}
		merc.active.add(merc.offers.remove(index));
		data.dirty = true;
		player.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 1.1f);
		return true;
	}

	public static boolean abandon(ServerPlayer player, RaceData data, int index) {
		if (index < 0 || index >= data.merc.active.size()) {
			return false;
		}
		data.merc.active.remove(index);
		data.dirty = true;
		player.playNotifySound(SoundEvents.BOOK_PUT, SoundSource.PLAYERS, 1.0f, 0.7f);
		return true;
	}

	/** Hands over what a gathering contract asks for, as much as the inventory holds. */
	public static boolean deliver(ServerPlayer player, RaceData data, int index) {
		if (index < 0 || index >= data.merc.active.size()) {
			return false;
		}
		Contract c = data.merc.active.get(index);
		ResourceLocation id = ResourceLocation.tryParse(c.target);
		if (c.kind != Contract.Kind.GATHER || id == null) {
			return false;
		}
		Item item = BuiltInRegistries.ITEM.get(id);
		int need = c.amount - c.progress;
		var inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize() && need > 0; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(item)) {
				int take = Math.min(need, stack.getCount());
				stack.shrink(take);
				need -= take;
				c.progress += take;
			}
		}
		data.dirty = true;
		if (c.done()) {
			complete(player, data, c);
		} else {
			player.displayClientMessage(Component.translatable("mages_legacy.merc.delivered", c.progress, c.amount), true);
		}
		return true;
	}

	/** A creature killed by the Mercenary counts towards its hunts. */
	public static void onKill(ServerPlayer player, RaceData data, LivingEntity victim) {
		String id = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).toString();
		for (Contract c : List.copyOf(data.merc.active)) {
			if (c.kind == Contract.Kind.HUNT && c.target.equals(id) && !c.done()) {
				c.progress += data.has(Talents.CON_BOUNTY) && player.getRandom().nextFloat() < 0.25f ? 2 : 1;
				c.progress = Math.min(c.progress, c.amount);
				data.dirty = true;
				if (c.done()) {
					complete(player, data, c);
				}
				return;
			}
		}
	}

	/** Once a second: a land the Mercenary was sent to, reached. */
	public static void tickSecond(ServerPlayer player, RaceData data) {
		refresh(player, data);
		var biome = player.serverLevel().getBiome(player.blockPosition()).unwrapKey();
		if (biome.isEmpty()) {
			return;
		}
		String here = biome.get().location().toString();
		for (Contract c : List.copyOf(data.merc.active)) {
			if (c.kind == Contract.Kind.EXPLORE && c.target.equals(here)) {
				c.progress = c.amount;
				complete(player, data, c);
			}
		}
	}

	/** The pay: tokens into the Shop and race experience, the only kind a Mercenary gets. */
	public static void complete(ServerPlayer player, RaceData data, Contract c) {
		data.merc.active.remove(c);
		data.merc.tokens += c.tokens;
		data.merc.completed++;
		data.dirty = true;
		player.displayClientMessage(Component.translatable("mages_legacy.merc.completed", c.tokens, c.xp).withStyle(ChatFormatting.GOLD), false);
		Progression.addXp(player, data, c.xp);
		ServerLevel world = player.serverLevel();
		world.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 0.6f, 0.8f);
		world.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.4f);
		Fx.burst(world, player.position().add(0, 1, 0), 2.0f, 10, Mercenary.GOLD_RGB);
		Fx.helix(world, player, 0.9f, 2.2f, 3, true, 18, Mercenary.GOLD_RGB);
		if (data.has(Talents.CON_RUSH)) {
			player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 1, true, true, true));
			player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 600, 0, true, true, true));
			player.heal(6);
		}
	}

	/** Fresh offers for tokens: 5, 10, 20... a day; the first one free with Connections. */
	public static int rerollPrice(RaceData data) {
		if (data.has(Talents.CON_REROLL) && data.merc.rerolls == 0) {
			return 0;
		}
		return 5 << Math.min(4, data.merc.rerolls);
	}

	public static boolean reroll(ServerPlayer player, RaceData data) {
		int price = Shop.discounted(data, rerollPrice(data));
		if (data.merc.tokens < price) {
			player.displayClientMessage(Component.translatable("mages_legacy.merc.no_tokens"), true);
			return false;
		}
		data.merc.tokens -= price;
		data.merc.rerolls++;
		fill(player, data);
		player.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 0.8f);
		return true;
	}
}
