package euf.mageslegacy.race;

import euf.mageslegacy.masked.MaskData;
import euf.mageslegacy.mercenary.MercData;
import euf.mageslegacy.shepherd.ShepherdData;
import euf.mageslegacy.voidborn.VoidData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** One player's race, resource, experience and talents. Cooldowns and chains are not saved. */
public class RaceData {
	public static final int MAX_LEVEL = 50;
	public static final float MAX_RESOURCE = 100;

	public Race race;
	public float resource;
	public int xp;
	public int level;
	public final Set<String> talents = new LinkedHashSet<>();
	/** Effect ids of the mushroom tastes now active, oldest first. */
	public final List<String> tastes = new ArrayList<>();
	/** The Shepherds' bestiary, bonds and rituals (empty for other races). */
	public final ShepherdData shepherd = new ShepherdData();
	/** The Voidborn's pocket room and way back (empty for other races). */
	public final VoidData voidborn = new VoidData();
	/** The Mercenary's account in the Mage's Shop (empty for other races). */
	public final MercData merc = new MercData();
	/** The Masked's gallery of faces (empty for other races). */
	public final MaskData masked = new MaskData();

	// Not saved: server ticks at which each ability slot is ready again, and the spore jump chain
	public final Map<Integer, Long> readyAt = new HashMap<>();
	public int chainLeft;
	public long chainUntil;
	public boolean dirty = true;
	/** Whether sneak was held with the key being handled now (the Call's recall, the Rewind of an ally). Not saved. */
	public boolean alt;
	/**
	 * Race experience taken from each capped source in the current window: source -> {window start (world time),
	 * experience taken}. Saved, so leaving and coming back doesn't refill it. See {@link Progression#addXp(ServerPlayer, RaceData, String, int, int)}.
	 */
	public final Map<String, long[]> xpTaken = new HashMap<>();
	/** The legend and the choice of race were sent to the client this session. */
	public boolean prompted;

	public boolean has(String talent) {
		return talents.contains(talent);
	}

	/** A key whose ability a talent opens, pressed before it: say which talent, rather than nothing at all. */
	public static AbilityResult talentLocked(ServerPlayer player, String talent) {
		player.displayClientMessage(Component.translatable("mages_legacy.ability.talent_locked", Component.translatable("mages_legacy.talent." + talent)), true);
		return AbilityResult.HINTED;
	}

	public void addResource(float amount) {
		float before = resource;
		resource = Math.max(0, Math.min(MAX_RESOURCE, resource + amount));
		dirty |= (int) before != (int) resource;
	}

	/**
	 * Experience from one level to the next, steeper the higher: about 2 000 to level 10, 8 000 to 20, 19 000 to 30
	 * and 67 000 to 50.
	 */
	public static int xpForNext(int level) {
		return 50 + 20 * level + level * level;
	}

	/** Adds race experience; returns how many levels were gained. */
	public int addXp(int amount) {
		if (amount <= 0 || level >= MAX_LEVEL) {
			return 0;
		}
		xp += amount;
		int gained = 0;
		while (level < MAX_LEVEL && xp >= xpForNext(level)) {
			xp -= xpForNext(level);
			level++;
			gained++;
		}
		if (level >= MAX_LEVEL) {
			xp = 0;
		}
		dirty = true;
		return gained;
	}

	/** One point per level, two more at every tenth level: 60 at level 50. */
	public int totalPoints() {
		return level + 2 * (level / 10);
	}

	public int spentPoints() {
		return talents.stream().mapToInt(id -> Talents.byId(id).map(t -> t.cost()).orElse(0)).sum();
	}

	public int freePoints() {
		return totalPoints() - spentPoints();
	}

	public void reset(Race newRace) {
		race = newRace;
		resource = 0;
		xp = 0;
		level = 0;
		talents.clear();
		tastes.clear();
		xpTaken.clear();
		shepherd.clear();
		merc.clear();
		masked.clear();
		readyAt.clear();
		chainLeft = 0;
		prompted = false;
		dirty = true;
	}

	public CompoundTag toNbt() {
		CompoundTag nbt = new CompoundTag();
		if (race != null) {
			nbt.putString("race", race.id());
		}
		nbt.putFloat("resource", resource);
		nbt.putInt("xp", xp);
		nbt.putInt("level", level);
		ListTag list = new ListTag();
		talents.forEach(t -> list.add(StringTag.valueOf(t)));
		nbt.put("talents", list);
		ListTag tasteList = new ListTag();
		tastes.forEach(t -> tasteList.add(StringTag.valueOf(t)));
		nbt.put("tastes", tasteList);
		CompoundTag taken = new CompoundTag();
		xpTaken.forEach((source, window) -> taken.putLongArray(source, window));
		nbt.put("xp_taken", taken);
		nbt.put("shepherd", shepherd.toNbt());
		nbt.put("voidborn", voidborn.toNbt());
		nbt.put("merc", merc.toNbt());
		nbt.put("masked", masked.toNbt());
		return nbt;
	}

	public static RaceData fromNbt(CompoundTag nbt) {
		RaceData data = new RaceData();
		data.race = Race.byId(nbt.getString("race")).orElse(null);
		data.resource = nbt.getFloat("resource");
		data.xp = nbt.getInt("xp");
		data.level = nbt.getInt("level");
		for (Tag e : nbt.getList("talents", Tag.TAG_STRING)) {
			data.talents.add(e.getAsString());
		}
		for (Tag e : nbt.getList("tastes", Tag.TAG_STRING)) {
			data.tastes.add(e.getAsString());
		}
		CompoundTag taken = nbt.getCompound("xp_taken");
		for (String source : taken.getAllKeys()) {
			long[] window = taken.getLongArray(source);
			if (window.length == 2) {
				data.xpTaken.put(source, window);
			}
		}
		data.shepherd.readNbt(nbt.getCompound("shepherd"));
		data.voidborn.readNbt(nbt.getCompound("voidborn"));
		data.merc.readNbt(nbt.getCompound("merc"));
		data.masked.readNbt(nbt.getCompound("masked"));
		return data;
	}
}
