package euf.mageslegacy.masked;

import java.util.List;
import java.util.Optional;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * A face one of the Masked can tear off and wear: the monsters it comes from (the first is the shape the wearer takes),
 * the spawn egg the HUD shows for it, and the price and cooldown of its power (V). Each face also brings a passive gift
 * and a weakness of its kind; {@link Masked} knows what they are.
 */
public enum Face {
	ZOMBIE("zombie", Items.ZOMBIE_SPAWN_EGG, 15, 120, EntityType.ZOMBIE, EntityType.HUSK, EntityType.ZOMBIE_VILLAGER),
	SKELETON("skeleton", Items.SKELETON_SPAWN_EGG, 20, 160, EntityType.SKELETON, EntityType.STRAY),
	DROWNED("drowned", Items.DROWNED_SPAWN_EGG, 15, 160, EntityType.DROWNED),
	CREEPER("creeper", Items.CREEPER_SPAWN_EGG, 30, 300, EntityType.CREEPER),
	SPIDER("spider", Items.SPIDER_SPAWN_EGG, 15, 160, EntityType.SPIDER, EntityType.CAVE_SPIDER),
	ENDERMAN("enderman", Items.ENDERMAN_SPAWN_EGG, 20, 200, EntityType.ENDERMAN),
	BLAZE("blaze", Items.BLAZE_SPAWN_EGG, 20, 140, EntityType.BLAZE),
	WITCH("witch", Items.WITCH_SPAWN_EGG, 20, 200, EntityType.WITCH),
	WITHER_SKELETON("wither_skeleton", Items.WITHER_SKELETON_SPAWN_EGG, 20, 200, EntityType.WITHER_SKELETON),
	PHANTOM("phantom", Items.PHANTOM_SPAWN_EGG, 20, 200, EntityType.PHANTOM),
	ILLAGER("illager", Items.EVOKER_SPAWN_EGG, 25, 200, EntityType.EVOKER, EntityType.VINDICATOR, EntityType.PILLAGER);

	public final String id;
	public final Item egg;
	public final int cost;
	public final int cooldown;
	public final List<EntityType<?>> types;

	Face(String id, Item egg, int cost, int cooldown, EntityType<?>... types) {
		this.id = id;
		this.egg = egg;
		this.cost = cost;
		this.cooldown = cooldown;
		this.types = List.of(types);
	}

	/** The shape the wearer takes. */
	public EntityType<?> look() {
		return types.get(0);
	}

	public boolean matches(EntityType<?> type) {
		return types.contains(type);
	}

	public static Optional<Face> of(EntityType<?> type) {
		for (Face face : values()) {
			if (face.matches(type)) {
				return Optional.of(face);
			}
		}
		return Optional.empty();
	}

	public static Optional<Face> byId(String id) {
		for (Face face : values()) {
			if (face.id.equals(id)) {
				return Optional.of(face);
			}
		}
		return Optional.empty();
	}
}
