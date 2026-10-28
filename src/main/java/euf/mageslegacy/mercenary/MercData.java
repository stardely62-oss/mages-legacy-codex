package euf.mageslegacy.mercenary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * A Mercenary's account in the Mage's Shop: tokens (they exist nowhere but here), the board of offered contracts,
 * the contracts in hand, and what was bought today.
 */
public class MercData {
	public int tokens;
	public final List<Contract> offers = new ArrayList<>();
	public final List<Contract> active = new ArrayList<>();
	/** The in-game day the board was last filled; a new day brings new offers and new prices. */
	public long day = -1;
	public int rerolls;
	/** Shop id -> how many bought today. */
	public final Map<String, Integer> bought = new HashMap<>();
	public int completed;
	/** Today's goods in the shop (ware ids), rolled with the board; one of them is the deal of the day. */
	public final List<String> stock = new ArrayList<>();
	public String deal = "";

	public void clear() {
		tokens = 0;
		offers.clear();
		active.clear();
		day = -1;
		rerolls = 0;
		bought.clear();
		completed = 0;
		stock.clear();
		deal = "";
	}

	public CompoundTag toNbt() {
		CompoundTag nbt = new CompoundTag();
		nbt.putInt("tokens", tokens);
		nbt.put("offers", list(offers));
		nbt.put("active", list(active));
		nbt.putLong("day", day);
		nbt.putInt("rerolls", rerolls);
		CompoundTag today = new CompoundTag();
		bought.forEach(today::putInt);
		nbt.put("bought", today);
		nbt.putInt("completed", completed);
		ListTag wares = new ListTag();
		stock.forEach(id -> wares.add(StringTag.valueOf(id)));
		nbt.put("stock", wares);
		nbt.putString("deal", deal);
		return nbt;
	}

	public void readNbt(CompoundTag nbt) {
		clear();
		tokens = nbt.getInt("tokens");
		read(nbt.getList("offers", Tag.TAG_COMPOUND), offers);
		read(nbt.getList("active", Tag.TAG_COMPOUND), active);
		day = nbt.contains("day") ? nbt.getLong("day") : -1;
		rerolls = nbt.getInt("rerolls");
		CompoundTag today = nbt.getCompound("bought");
		for (String id : today.getAllKeys()) {
			bought.put(id, today.getInt(id));
		}
		completed = nbt.getInt("completed");
		for (Tag e : nbt.getList("stock", Tag.TAG_STRING)) {
			stock.add(e.getAsString());
		}
		deal = nbt.getString("deal");
	}

	private static ListTag list(List<Contract> contracts) {
		ListTag list = new ListTag();
		contracts.forEach(c -> list.add(c.toNbt()));
		return list;
	}

	private static void read(ListTag list, List<Contract> into) {
		for (int i = 0; i < list.size(); i++) {
			into.add(Contract.fromNbt(list.getCompound(i)));
		}
	}
}
