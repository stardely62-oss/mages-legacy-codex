package euf.mageslegacy.shepherd;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Beasts a Shepherd can bond with. Every one starts with the Song of Bonds, then wins its trust its own way
 * ({@link Taming}) in {@code steps} (feedings, songs, headbutts, kills, seconds); {@code food} is what that way needs
 * in hand, {@code capacity} the bond slots it takes, {@code treat} what it likes to eat once it's yours (feeding
 * heals it and keeps it fed). Texts: mages_legacy.beast.<id>, .skill (short), .skill_desc, .taming and .treat.
 */
public record BeastKind(String id, EntityType<?> type, Taming taming, int steps, Predicate<ItemStack> food, int capacity, Item icon,
		Predicate<ItemStack> treat) {
	/** How a beast is won over after the Song. */
	public enum Taming {
		/** Feed it, a minute apart, the last time by a campfire (the wolf). */
		FEED_BY_FIRE,
		/** Crouch beside it with its food in hand for some seconds (the cat). */
		PATIENCE,
		/** Sing to it while it sleeps, then feed it once (the fox). */
		ASLEEP,
		/** Bring it different kinds of its food (the bee: flowers). */
		KINDS,
		/** Butt heads with it bare-handed (no harm done), then feed it (the goat). */
		HEADBUTTS,
		/** Kill monsters near it: it respects strength (the polar bear). */
		HUNT,
		/** Sing to it, a minute apart (the parrot: by a playing jukebox; the bat: underground). */
		SONGS,
		/** Feed it, a minute apart (the frog, the turtle, the rabbit). */
		FEED,
		/** Walk it on a lead for a while (the llama). */
		LEAD,
		/** Feed it while you swim beside it, a minute apart (the axolotl, the dolphin). */
		WATER_GIFT
	}

	public static final List<BeastKind> ALL = List.of(
			new BeastKind("wolf", EntityType.WOLF, Taming.FEED_BY_FIRE, 4, s -> s.is(net.minecraft.tags.ItemTags.MEAT), 2, Items.WOLF_SPAWN_EGG,
					s -> s.is(net.minecraft.tags.ItemTags.MEAT)),
			new BeastKind("cat", EntityType.CAT, Taming.PATIENCE, 20, s -> s.is(Items.COD) || s.is(Items.SALMON), 1, Items.CAT_SPAWN_EGG,
					s -> s.is(Items.COD) || s.is(Items.SALMON)),
			new BeastKind("fox", EntityType.FOX, Taming.ASLEEP, 1, s -> s.is(Items.GLOW_BERRIES), 1, Items.FOX_SPAWN_EGG,
					s -> s.is(Items.SWEET_BERRIES) || s.is(Items.GLOW_BERRIES)),
			new BeastKind("bee", EntityType.BEE, Taming.KINDS, 3, s -> s.is(ItemTags.FLOWERS), 1, Items.BEE_SPAWN_EGG,
					s -> s.is(ItemTags.FLOWERS)),
			new BeastKind("axolotl", EntityType.AXOLOTL, Taming.WATER_GIFT, 2, s -> s.is(Items.TROPICAL_FISH_BUCKET), 1, Items.AXOLOTL_SPAWN_EGG,
					s -> s.is(Items.TROPICAL_FISH) || s.is(Items.TROPICAL_FISH_BUCKET)),
			new BeastKind("goat", EntityType.GOAT, Taming.HEADBUTTS, 5, s -> s.is(Items.WHEAT), 2, Items.GOAT_SPAWN_EGG,
					s -> s.is(Items.WHEAT)),
			new BeastKind("polar_bear", EntityType.POLAR_BEAR, Taming.HUNT, 5, s -> false, 3, Items.POLAR_BEAR_SPAWN_EGG,
					s -> s.is(Items.COD) || s.is(Items.SALMON)),
			new BeastKind("parrot", EntityType.PARROT, Taming.SONGS, 3, s -> false, 1, Items.PARROT_SPAWN_EGG,
					s -> s.is(Items.WHEAT_SEEDS) || s.is(Items.MELON_SEEDS) || s.is(Items.PUMPKIN_SEEDS) || s.is(Items.BEETROOT_SEEDS)),
			new BeastKind("frog", EntityType.FROG, Taming.FEED, 3, s -> s.is(Items.SLIME_BALL), 1, Items.FROG_SPAWN_EGG,
					s -> s.is(Items.SLIME_BALL)),
			new BeastKind("llama", EntityType.LLAMA, Taming.LEAD, 120, s -> false, 2, Items.LLAMA_SPAWN_EGG,
					s -> s.is(Items.WHEAT) || s.is(Items.HAY_BLOCK)),
			new BeastKind("rabbit", EntityType.RABBIT, Taming.FEED, 2, s -> s.is(Items.GOLDEN_CARROT), 1, Items.RABBIT_SPAWN_EGG,
					s -> s.is(Items.CARROT) || s.is(Items.GOLDEN_CARROT) || s.is(Items.DANDELION)),
			new BeastKind("turtle", EntityType.TURTLE, Taming.FEED, 3, s -> s.is(Items.SEAGRASS), 1, Items.TURTLE_SPAWN_EGG,
					s -> s.is(Items.SEAGRASS)),
			new BeastKind("dolphin", EntityType.DOLPHIN, Taming.WATER_GIFT, 3, s -> s.is(Items.COD) || s.is(Items.SALMON), 2, Items.DOLPHIN_SPAWN_EGG,
					s -> s.is(Items.COD) || s.is(Items.SALMON)),
			new BeastKind("bat", EntityType.BAT, Taming.SONGS, 3, s -> false, 1, Items.BAT_SPAWN_EGG,
					s -> s.is(Items.SWEET_BERRIES) || s.is(Items.GLOW_BERRIES)));

	public static Optional<BeastKind> of(EntityType<?> type) {
		return ALL.stream().filter(k -> k.type() == type).findFirst();
	}

	public static Optional<BeastKind> byId(String id) {
		return ALL.stream().filter(k -> k.id().equals(id)).findFirst();
	}
}
