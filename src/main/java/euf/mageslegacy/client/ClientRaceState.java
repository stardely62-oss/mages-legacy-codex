package euf.mageslegacy.client;

import euf.mageslegacy.race.AbilitySlots;
import euf.mageslegacy.race.ClientRaceView;
import euf.mageslegacy.race.Race;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

/** The client's copy of its own race data, from the sync packet; cooldowns count down locally between syncs. */
public final class ClientRaceState {
	public static Race race;
	public static float resource;
	public static int level;
	public static int xp;
	public static int xpNext = 40;
	public static int freePoints;
	public static int openTiers = 1;
	public static final Set<String> talents = new HashSet<>();
	private static final int[] cooldowns = new int[AbilitySlots.COOLDOWNS];
	public static int chainLeft;
	/** Race extras from the sync: for Shepherds the capacity, bestiary kills, picked echo and bonds. */
	public static CompoundTag extra = new CompoundTag();
	private static long receivedAt;
	private static long clientTicks;

	private ClientRaceState() {
	}

	public static void read(FriendlyByteBuf buf) {
		String id = buf.readUtf();
		race = Race.byId(id).orElse(null);
		ClientRaceView.race = race;
		resource = buf.readFloat();
		level = buf.readVarInt();
		xp = buf.readVarInt();
		xpNext = buf.readVarInt();
		freePoints = buf.readVarInt();
		openTiers = buf.readVarInt();
		talents.clear();
		int count = buf.readVarInt();
		for (int i = 0; i < count; i++) {
			talents.add(buf.readUtf());
		}
		for (int slot = 0; slot < AbilitySlots.COOLDOWNS; slot++) {
			cooldowns[slot] = buf.readVarInt();
		}
		chainLeft = buf.readVarInt();
		CompoundTag nbt = buf.readNbt();
		extra = nbt == null ? new CompoundTag() : nbt;
		ClientRaceView.climbs = race == Race.MASKED && ("spider".equals(extra.getString("face"))
				|| extra.getBoolean("thousand") && extra.getList("gallery", Tag.TAG_STRING).contains(StringTag.valueOf("spider")));
		receivedAt = clientTicks;
	}

	public static void tick() {
		clientTicks++;
	}

	/** Client ticks counted since the game started, for timers sent by the server. */
	public static long clientTicks() {
		return clientTicks;
	}

	/** Ticks until the slot is ready again. */
	public static int cooldown(int slot) {
		return (int) Math.max(0, cooldowns[slot] - (clientTicks - receivedAt));
	}

	public static void clear() {
		race = null;
		ClientRaceView.race = null;
		ClientRaceView.climbs = false;
		talents.clear();
		resource = 0;
		level = 0;
	}
}
