package euf.mageslegacy.shepherd;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** What a bonded fox digs up now and then: mostly trifles, sometimes something worth it. */
final class FoxFinds {
	private FoxFinds() {
	}

	static ItemStack roll(RandomSource random) {
		int r = random.nextInt(100);
		if (r < 25) {
			return new ItemStack(Items.BONE, 2);
		} else if (r < 45) {
			return new ItemStack(Items.SWEET_BERRIES, 4);
		} else if (r < 58) {
			return new ItemStack(Items.RABBIT_HIDE, 2);
		} else if (r < 72) {
			return new ItemStack(Items.GOLD_NUGGET, 4);
		} else if (r < 84) {
			return new ItemStack(Items.IRON_NUGGET, 6);
		} else if (r < 92) {
			return new ItemStack(Items.FEATHER, 3);
		} else if (r < 97) {
			return new ItemStack(Items.EMERALD);
		} else if (r < 99) {
			return new ItemStack(Items.NAME_TAG);
		}
		return new ItemStack(Items.DIAMOND);
	}
}
