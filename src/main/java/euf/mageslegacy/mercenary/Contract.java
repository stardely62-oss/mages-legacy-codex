package euf.mageslegacy.mercenary;

import java.util.Locale;
import net.minecraft.nbt.CompoundTag;

/**
 * One job from the board in the Mage's Shop: hunt so many of a creature, bring so many of an item, or set foot in a
 * land. What it pays is fixed when it is offered.
 */
public final class Contract {
	public enum Kind { HUNT, GATHER, EXPLORE }

	public Kind kind;
	/** An entity type id (hunt), an item id (gather) or a biome id (explore). */
	public String target;
	public int amount;
	public int progress;
	public int tier;
	/** Elite Orders: twice the work, two and a half times the pay. */
	public boolean elite;
	public int tokens;
	public int xp;

	public Contract(Kind kind, String target, int amount, int tier, boolean elite, int tokens, int xp) {
		this.kind = kind;
		this.target = target;
		this.amount = amount;
		this.tier = tier;
		this.elite = elite;
		this.tokens = tokens;
		this.xp = xp;
	}

	public boolean done() {
		return progress >= amount;
	}

	public CompoundTag toNbt() {
		CompoundTag nbt = new CompoundTag();
		nbt.putString("kind", kind.name().toLowerCase(Locale.ROOT));
		nbt.putString("target", target);
		nbt.putInt("amount", amount);
		nbt.putInt("progress", progress);
		nbt.putInt("tier", tier);
		nbt.putBoolean("elite", elite);
		nbt.putInt("tokens", tokens);
		nbt.putInt("xp", xp);
		return nbt;
	}

	public static Contract fromNbt(CompoundTag nbt) {
		Kind kind;
		try {
			kind = Kind.valueOf(nbt.getString("kind").toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			kind = Kind.HUNT;
		}
		Contract c = new Contract(kind, nbt.getString("target"), Math.max(1, nbt.getInt("amount")), nbt.getInt("tier"), nbt.getBoolean("elite"),
				nbt.getInt("tokens"), nbt.getInt("xp"));
		c.progress = nbt.getInt("progress");
		return c;
	}
}
