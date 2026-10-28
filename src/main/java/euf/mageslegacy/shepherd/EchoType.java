package euf.mageslegacy.shepherd;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * The echoes a Shepherd can learn: kill enough of a kind and its echo answers the Call. {@code kills} unlock it,
 * {@code capacity} is the bond slots it takes while it walks with you, {@code voice} what the Call costs. Small fry
 * come as a {@code group}: one place in a squad, one price and one bond slot bring several of them.
 */
public record EchoType(EntityType<?> type, int kills, int capacity, int voice, Item icon, int group) {
	public EchoType(EntityType<?> type, int kills, int capacity, int voice, Item icon) {
		this(type, kills, capacity, voice, icon, 1);
	}

	/** How many squad places a number of living echoes of this kind fill (a group counts while any of it lives). */
	public int units(int entities) {
		return (entities + group - 1) / group;
	}

	public static final List<EchoType> ALL = List.of(
			new EchoType(EntityType.ZOMBIE, 20, 1, 15, Items.ZOMBIE_SPAWN_EGG),
			new EchoType(EntityType.HUSK, 15, 1, 15, Items.HUSK_SPAWN_EGG),
			new EchoType(EntityType.DROWNED, 15, 1, 15, Items.DROWNED_SPAWN_EGG),
			new EchoType(EntityType.SKELETON, 25, 2, 25, Items.SKELETON_SPAWN_EGG),
			new EchoType(EntityType.STRAY, 15, 2, 25, Items.STRAY_SPAWN_EGG),
			new EchoType(EntityType.SPIDER, 20, 2, 20, Items.SPIDER_SPAWN_EGG),
			new EchoType(EntityType.CAVE_SPIDER, 15, 1, 15, Items.CAVE_SPIDER_SPAWN_EGG),
			new EchoType(EntityType.VINDICATOR, 10, 3, 35, Items.VINDICATOR_SPAWN_EGG),
			new EchoType(EntityType.BLAZE, 15, 3, 35, Items.BLAZE_SPAWN_EGG),
			new EchoType(EntityType.WITHER_SKELETON, 10, 4, 45, Items.WITHER_SKELETON_SPAWN_EGG),
			new EchoType(EntityType.SILVERFISH, 20, 1, 10, Items.SILVERFISH_SPAWN_EGG, 3),
			new EchoType(EntityType.PILLAGER, 15, 2, 25, Items.PILLAGER_SPAWN_EGG),
			new EchoType(EntityType.WITCH, 10, 3, 35, Items.WITCH_SPAWN_EGG),
			new EchoType(EntityType.VEX, 10, 2, 25, Items.VEX_SPAWN_EGG),
			new EchoType(EntityType.RAVAGER, 5, 6, 60, Items.RAVAGER_SPAWN_EGG));

	public String id() {
		return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
	}

	/** The id without its namespace ("zombie"): portraits and trait texts go by it. */
	public String path() {
		return BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
	}

	/** Kills needed to learn it. */
	public int killsNeeded() {
		return kills;
	}

	/** Voice the Call costs; Quiet Call takes a quarter off. */
	public int voiceCost(boolean quiet) {
		return quiet ? Math.round(voice * 0.75f) : voice;
	}

	public static Optional<EchoType> byId(String id) {
		return ALL.stream().filter(e -> e.id().equals(id)).findFirst();
	}

	public static Optional<EchoType> of(EntityType<?> type) {
		return ALL.stream().filter(e -> e.type() == type).findFirst();
	}
}
