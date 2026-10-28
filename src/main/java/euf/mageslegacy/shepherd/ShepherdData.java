package euf.mageslegacy.shepherd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * What a Shepherd keeps: kills per mob type (the bestiary of echoes), the echo picked for the Call, the bonded beasts,
 * and bond rituals still in progress. Saved with the rest of the race data.
 */
public class ShepherdData {
	/** Entity type id -> how many of them this Shepherd (or its creatures) killed. */
	public final Map<String, Integer> kills = new HashMap<>();
	public String selectedEcho = "";
	/**
	 * Three squads of echoes the Call brings at once (echo type id -> how many); {@code activePreset} is the one
	 * the Call uses now.
	 */
	public final List<Map<String, Integer>> presets = List.of(new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>());
	public int activePreset;
	/** The Whistle's order to the pack: {@link #FOLLOW}, {@link #GUARD} or {@link #HUNT}. */
	public int stance = GUARD;
	public static final int FOLLOW = 0;
	public static final int GUARD = 1;
	public static final int HUNT = 2;
	public final List<Bond> bonds = new ArrayList<>();
	/** Animal uuid -> a bond ritual under way. */
	public final Map<UUID, Ritual> rituals = new HashMap<>();

	/**
	 * A bonded beast. While it rests after falling, {@code restingUntil} is the world time it comes back and
	 * {@code body} holds the entity, to bring it back as it was.
	 */
	public static class Bond {
		public UUID entity;
		public String kind;
		public String name = "";
		public int level = 1;
		public int xp;
		public long restingUntil;
		public CompoundTag body;
		/** Chosen in the bestiary to lead the pack (the Alpha crown). */
		public boolean leader;
		/** World time its last meal wears off (fed beasts heal and hit harder), when it was last petted. */
		public long fedUntil;
		public long pettedAt;
		/** World time Not Today last saved it from a killing blow. */
		public long savedAt;
		/** Told to stay put (sneak + right click): it doesn't follow until called again. */
		public boolean staying;

		public boolean resting() {
			return body != null;
		}

		CompoundTag toNbt() {
			CompoundTag nbt = new CompoundTag();
			nbt.putUUID("entity", entity);
			nbt.putString("kind", kind);
			nbt.putString("name", name);
			nbt.putInt("level", level);
			nbt.putInt("xp", xp);
			nbt.putLong("restingUntil", restingUntil);
			nbt.putBoolean("leader", leader);
			nbt.putLong("fedUntil", fedUntil);
			nbt.putLong("pettedAt", pettedAt);
			nbt.putLong("savedAt", savedAt);
			nbt.putBoolean("staying", staying);
			if (body != null) {
				nbt.put("body", body);
			}
			return nbt;
		}

		static Bond fromNbt(CompoundTag nbt) {
			Bond bond = new Bond();
			bond.entity = nbt.getUUID("entity");
			bond.kind = nbt.getString("kind");
			bond.name = nbt.getString("name");
			bond.level = Math.max(1, nbt.getInt("level"));
			bond.xp = nbt.getInt("xp");
			bond.restingUntil = nbt.getLong("restingUntil");
			bond.leader = nbt.getBoolean("leader");
			bond.fedUntil = nbt.getLong("fedUntil");
			bond.pettedAt = nbt.getLong("pettedAt");
			bond.savedAt = nbt.getLong("savedAt");
			bond.staying = nbt.getBoolean("staying");
			bond.body = nbt.contains("body") ? nbt.getCompound("body") : null;
			return bond;
		}
	}

	/**
	 * A bond ritual: the Song was sung until {@code listenUntil}; {@code feedings} so far (or songs, headbutts, kills,
	 * seconds of patience: whatever this beast's way counts), the last at {@code lastFeed}; {@code note} remembers
	 * e.g. which flower came first.
	 */
	public static class Ritual {
		public long listenUntil;
		public int feedings;
		public long lastFeed;
		public String note = "";
		/** Which beast kind listens (for the bestiary). */
		public String kind = "";
	}

	public Optional<Bond> bond(UUID entity) {
		return bonds.stream().filter(b -> b.entity.equals(entity)).findFirst();
	}

	public int kills(String type) {
		return kills.getOrDefault(type, 0);
	}

	public void clear() {
		kills.clear();
		selectedEcho = "";
		presets.forEach(Map::clear);
		activePreset = 0;
		stance = GUARD;
		bonds.clear();
		rituals.clear();
	}

	public CompoundTag toNbt() {
		CompoundTag nbt = new CompoundTag();
		CompoundTag killNbt = new CompoundTag();
		kills.forEach(killNbt::putInt);
		nbt.put("kills", killNbt);
		nbt.putString("selected", selectedEcho);
		ListTag presetList = new ListTag();
		for (Map<String, Integer> preset : presets) {
			CompoundTag p = new CompoundTag();
			preset.forEach(p::putInt);
			presetList.add(p);
		}
		nbt.put("presets", presetList);
		nbt.putInt("activePreset", activePreset);
		nbt.putInt("stance", stance);
		ListTag bondList = new ListTag();
		bonds.forEach(b -> bondList.add(b.toNbt()));
		nbt.put("bonds", bondList);
		ListTag ritualList = new ListTag();
		rituals.forEach((id, r) -> {
			CompoundTag e = new CompoundTag();
			e.putUUID("id", id);
			e.putLong("listenUntil", r.listenUntil);
			e.putInt("feedings", r.feedings);
			e.putLong("lastFeed", r.lastFeed);
			e.putString("note", r.note);
			e.putString("kind", r.kind);
			ritualList.add(e);
		});
		nbt.put("rituals", ritualList);
		return nbt;
	}

	public void readNbt(CompoundTag nbt) {
		clear();
		CompoundTag killNbt = nbt.getCompound("kills");
		for (String key : killNbt.getAllKeys()) {
			kills.put(key, killNbt.getInt(key));
		}
		selectedEcho = nbt.getString("selected");
		ListTag presetList = nbt.getList("presets", Tag.TAG_COMPOUND);
		for (int i = 0; i < presets.size() && i < presetList.size(); i++) {
			CompoundTag p = presetList.getCompound(i);
			for (String key : p.getAllKeys()) {
				presets.get(i).put(key, p.getInt(key));
			}
		}
		activePreset = Math.floorMod(nbt.getInt("activePreset"), presets.size());
		stance = nbt.contains("stance") ? Math.floorMod(nbt.getInt("stance"), 3) : GUARD;
		for (Tag e : nbt.getList("bonds", Tag.TAG_COMPOUND)) {
			bonds.add(Bond.fromNbt((CompoundTag) e));
		}
		for (Tag e : nbt.getList("rituals", Tag.TAG_COMPOUND)) {
			CompoundTag c = (CompoundTag) e;
			Ritual r = new Ritual();
			r.listenUntil = c.getLong("listenUntil");
			r.feedings = c.getInt("feedings");
			r.lastFeed = c.getLong("lastFeed");
			r.note = c.getString("note");
			r.kind = c.getString("kind");
			rituals.put(c.getUUID("id"), r);
		}
	}
}
