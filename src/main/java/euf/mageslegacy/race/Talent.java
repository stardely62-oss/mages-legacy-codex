package euf.mageslegacy.race;

import net.minecraft.world.item.Item;

/**
 * One node of a race's talent tree. Its name, lore line and effect text live in the language files under
 * mages_legacy.talent.<id>, .lore and .desc.
 *
 * @param branch   column of the tree, 0..2
 * @param tier     row; tier N opens with the Nth milestone (see {@link Progression})
 * @param requires a talent that must be taken first, or null
 */
public record Talent(String id, Race race, int branch, int tier, int cost, String requires, Item icon) {
	public String key() {
		return "mages_legacy.talent." + id;
	}
}
