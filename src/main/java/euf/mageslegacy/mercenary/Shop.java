package euf.mageslegacy.mercenary;

import euf.mageslegacy.MagesLegacy;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.RaceData;
import euf.mageslegacy.race.Talents;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * The Mage's Shop: what tokens buy. Services work on the Mercenary alone (race experience, a hired strength); goods
 * are real items anyone can use, so a Mercenary can equip a whole party. Some goods wait for a milestone tier.
 * Names and descriptions live under mages_legacy.shop.<id> and .desc.
 */
public final class Shop {
	public enum Kind { SERVICE, GOODS }

	/**
	 * One ware: its price in tokens, the tier it waits for, how many a day (0: no limit) and how much dearer each one
	 * bought today makes the next.
	 */
	public record Ware(String id, Kind kind, int price, int tier, int dailyMax, int step, Supplier<ItemStack> icon) {}

	/** Hired strengths last 5 minutes. */
	private static final int HIRE_TICKS = 6000;
	/** Race experience the Mage's Learning gives: dearer than a contract's, so the work stays the way to grow. */
	public static final int LEARNING_XP = 60;

	/** How many goods the shop has on a given day, out of the whole pool below. */
	public static final int STOCK = 7;
	/** The deal of the day is this much cheaper. */
	private static final float DEAL = 0.7f;

	/**
	 * Services are always there; goods come and go: each day the shop puts out {@link #STOCK} of them, rolled from the
	 * pool of the tiers the Mercenary has opened, one of them at a discount.
	 */
	public static final List<Ware> CATALOG = List.of(
			new Ware("learning", Kind.SERVICE, 12, 1, 3, 6, () -> new ItemStack(Items.EXPERIENCE_BOTTLE)),
			new Ware("hire_strength", Kind.SERVICE, 10, 1, 0, 0, () -> new ItemStack(Items.BLAZE_POWDER)),
			new Ware("hire_guard", Kind.SERVICE, 12, 1, 0, 0, () -> new ItemStack(Items.SHIELD)),
			new Ware("hire_haste", Kind.SERVICE, 8, 1, 0, 0, () -> new ItemStack(Items.GOLDEN_PICKAXE)),
			new Ware("arrows", Kind.GOODS, 3, 1, 0, 0, () -> new ItemStack(Items.ARROW, 32)),
			new Ware("spectral_arrows", Kind.GOODS, 5, 1, 0, 0, () -> new ItemStack(Items.SPECTRAL_ARROW, 16)),
			new Ware("torches", Kind.GOODS, 2, 1, 0, 0, () -> new ItemStack(Items.TORCH, 32)),
			new Ware("steak", Kind.GOODS, 4, 1, 0, 0, () -> new ItemStack(Items.COOKED_BEEF, 16)),
			new Ware("iron", Kind.GOODS, 6, 1, 0, 0, () -> new ItemStack(Items.IRON_INGOT, 8)),
			new Ware("name_tag", Kind.GOODS, 6, 1, 0, 0, () -> new ItemStack(Items.NAME_TAG)),
			new Ware("saddle", Kind.GOODS, 6, 1, 0, 0, () -> new ItemStack(Items.SADDLE)),
			new Ware("leads", Kind.GOODS, 4, 1, 0, 0, () -> new ItemStack(Items.LEAD, 2)),
			new Ware("pearls", Kind.GOODS, 10, 1, 0, 0, () -> new ItemStack(Items.ENDER_PEARL, 4)),
			new Ware("bottles", Kind.GOODS, 8, 1, 0, 0, () -> new ItemStack(Items.EXPERIENCE_BOTTLE, 8)),
			new Ware("healing", Kind.GOODS, 8, 1, 0, 0, () -> PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.STRONG_HEALING)),
			new Ware("golden_apple", Kind.GOODS, 15, 1, 0, 0, () -> new ItemStack(Items.GOLDEN_APPLE)),
			new Ware("oblivion", Kind.GOODS, 40, 1, 0, 0, () -> new ItemStack(MagesLegacy.OBLIVION_SCROLL)),
			new Ware("blaze_rods", Kind.GOODS, 10, 2, 0, 0, () -> new ItemStack(Items.BLAZE_ROD, 4)),
			new Ware("fire_resistance", Kind.GOODS, 10, 2, 0, 0, () -> PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.LONG_FIRE_RESISTANCE)),
			new Ware("ghast_tears", Kind.GOODS, 12, 2, 0, 0, () -> new ItemStack(Items.GHAST_TEAR, 2)),
			new Ware("diamonds", Kind.GOODS, 18, 2, 1, 0, () -> new ItemStack(Items.DIAMOND, 2)),
			new Ware("book", Kind.GOODS, 20, 2, 1, 0, () -> new ItemStack(Items.ENCHANTED_BOOK)),
			new Ware("ender_eyes", Kind.GOODS, 14, 3, 0, 0, () -> new ItemStack(Items.ENDER_EYE, 4)),
			new Ware("shulker_shells", Kind.GOODS, 30, 3, 1, 0, () -> new ItemStack(Items.SHULKER_SHELL, 2)),
			new Ware("totem", Kind.GOODS, 45, 3, 1, 0, () -> new ItemStack(Items.TOTEM_OF_UNDYING)),
			new Ware("netherite_scrap", Kind.GOODS, 60, 4, 1, 0, () -> new ItemStack(Items.NETHERITE_SCRAP)));

	/** What a random book of the shop can hold: useful enchantments at a good level, never the best of the best. */
	private static final List<EnchantmentInstance> BOOKS = List.of(
			new EnchantmentInstance(Enchantments.MENDING, 1), new EnchantmentInstance(Enchantments.UNBREAKING, 3),
			new EnchantmentInstance(Enchantments.SHARPNESS, 4), new EnchantmentInstance(Enchantments.ALL_DAMAGE_PROTECTION, 3),
			new EnchantmentInstance(Enchantments.BLOCK_EFFICIENCY, 4), new EnchantmentInstance(Enchantments.BLOCK_FORTUNE, 2),
			new EnchantmentInstance(Enchantments.MOB_LOOTING, 2), new EnchantmentInstance(Enchantments.POWER_ARROWS, 4),
			new EnchantmentInstance(Enchantments.FALL_PROTECTION, 4), new EnchantmentInstance(Enchantments.SILK_TOUCH, 1));

	private Shop() {
	}

	public static Optional<Ware> ware(String id) {
		return CATALOG.stream().filter(w -> w.id().equals(id)).findFirst();
	}

	/** Trusted Man takes 15% off everything. */
	public static int discounted(RaceData data, int price) {
		return data.has(Talents.CON_DISCOUNT) ? (int) Math.ceil(price * 0.85f) : price;
	}

	/** What the ware costs this Mercenary now, with today's purchases, the deal of the day and the discount. */
	public static int price(RaceData data, Ware ware) {
		int price = ware.price() + ware.step() * data.merc.bought.getOrDefault(ware.id(), 0);
		if (ware.id().equals(data.merc.deal)) {
			price = Math.max(1, Math.round(price * DEAL));
		}
		return discounted(data, price);
	}

	/** Puts out today's goods: a fresh roll from the open tiers, one of them the deal of the day. */
	public static void restock(ServerPlayer player, RaceData data) {
		List<Ware> pool = new ArrayList<>(CATALOG.stream().filter(w -> w.kind() == Kind.GOODS && Progression.tierOpen(player, w.tier())).toList());
		Collections.shuffle(pool, new java.util.Random(player.getRandom().nextLong()));
		data.merc.stock.clear();
		pool.stream().limit(STOCK).forEach(w -> data.merc.stock.add(w.id()));
		data.merc.deal = data.merc.stock.isEmpty() ? "" : data.merc.stock.get(player.getRandom().nextInt(data.merc.stock.size()));
		data.dirty = true;
	}

	/** A shop from before the goods rotated has none out yet: put them out now. */
	public static void ensureStock(ServerPlayer player, RaceData data) {
		if (data.merc.stock.isEmpty()) {
			restock(player, data);
		}
	}

	public static boolean soldOut(RaceData data, Ware ware) {
		return ware.dailyMax() > 0 && data.merc.bought.getOrDefault(ware.id(), 0) >= ware.dailyMax();
	}

	public static boolean buy(ServerPlayer player, RaceData data, String id) {
		Ware ware = ware(id).orElse(null);
		if (ware == null) {
			return false;
		}
		if (ware.kind() == Kind.GOODS && !data.merc.stock.contains(id)) {
			player.displayClientMessage(Component.translatable("mages_legacy.merc.not_today"), true);
			return false;
		}
		if (!Progression.tierOpen(player, ware.tier())) {
			player.displayClientMessage(Component.translatable("mages_legacy.merc.locked_ware"), true);
			return false;
		}
		if (soldOut(data, ware)) {
			player.displayClientMessage(Component.translatable("mages_legacy.merc.sold_out"), true);
			return false;
		}
		int price = price(data, ware);
		if (data.merc.tokens < price) {
			player.displayClientMessage(Component.translatable("mages_legacy.merc.no_tokens"), true);
			return false;
		}
		data.merc.tokens -= price;
		data.merc.bought.merge(id, 1, Integer::sum);
		data.dirty = true;
		switch (id) {
			case "learning" -> Progression.addXp(player, data, LEARNING_XP);
			case "hire_strength" -> hire(player, MobEffects.DAMAGE_BOOST, 0);
			case "hire_guard" -> hire(player, MobEffects.DAMAGE_RESISTANCE, 0);
			case "hire_haste" -> hire(player, MobEffects.DIG_SPEED, 1);
			case "book" -> player.getInventory().placeItemBackInInventory(EnchantedBookItem.createForEnchantment(BOOKS.get(player.getRandom().nextInt(BOOKS.size()))));
			default -> player.getInventory().placeItemBackInInventory(ware.icon().get());
		}
		player.playNotifySound(SoundEvents.VILLAGER_TRADE, SoundSource.PLAYERS, 0.7f, 0.9f);
		player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.4f);
		return true;
	}

	private static void hire(ServerPlayer player, MobEffect effect, int amplifier) {
		player.addEffect(new MobEffectInstance(effect, HIRE_TICKS, amplifier, true, false, true));
	}
}
