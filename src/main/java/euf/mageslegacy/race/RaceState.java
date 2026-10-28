package euf.mageslegacy.race;

import euf.mageslegacy.MagesLegacy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/** Every player's race data and every mycelium node, saved with the overworld. */
public class RaceState extends SavedData {
	private final Map<UUID, RaceData> players = new HashMap<>();
	/** Mycelium nodes per owner, oldest first. */
	private final Map<UUID, List<GlobalPos>> nodes = new HashMap<>();

	public static RaceState get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(RaceState::new, (tag, registries) -> fromNbt(tag), null), MagesLegacy.MOD_ID);
	}

	/** The player's data, or null on the client or before the server is up. */
	public static RaceData of(Player player) {
		MinecraftServer server = player.getServer();
		return server == null || player.level().isClientSide ? null : get(server).data(player.getUUID());
	}

	public static Optional<Race> raceOf(Player player) {
		RaceData data = of(player);
		return data == null ? Optional.empty() : Optional.ofNullable(data.race);
	}

	public RaceData data(UUID player) {
		// Data changes in place all the time; saving it on every world save is cheap
		setDirty();
		return players.computeIfAbsent(player, id -> new RaceData());
	}

	/** The next free room of the pocket dimension: one past the highest any Voidborn has had. */
	public int nextPocketRoom() {
		return players.values().stream().mapToInt(d -> d.voidborn.room).max().orElse(-1) + 1;
	}

	public List<GlobalPos> nodes(UUID owner) {
		return nodes.computeIfAbsent(owner, id -> new ArrayList<>());
	}

	public Optional<UUID> nodeOwner(ResourceKey<Level> dimension, BlockPos pos) {
		GlobalPos key = GlobalPos.of(dimension, pos);
		return nodes.entrySet().stream().filter(e -> e.getValue().contains(key)).map(Map.Entry::getKey).findFirst();
	}

	public void removeNode(ResourceKey<Level> dimension, BlockPos pos) {
		GlobalPos key = GlobalPos.of(dimension, pos);
		nodes.values().forEach(list -> list.remove(key));
		setDirty();
	}

	@Override
	public CompoundTag save(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider registries) {
		CompoundTag playerTag = new CompoundTag();
		players.forEach((id, data) -> playerTag.put(id.toString(), data.toNbt()));
		nbt.put("players", playerTag);
		CompoundTag nodeTag = new CompoundTag();
		nodes.forEach((id, list) -> {
			ListTag entries = new ListTag();
			for (GlobalPos pos : list) {
				CompoundTag entry = new CompoundTag();
				entry.putString("dim", pos.dimension().location().toString());
				entry.putLong("pos", pos.pos().asLong());
				entries.add(entry);
			}
			nodeTag.put(id.toString(), entries);
		});
		nbt.put("nodes", nodeTag);
		return nbt;
	}

	private static RaceState fromNbt(CompoundTag nbt) {
		RaceState state = new RaceState();
		CompoundTag playerTag = nbt.getCompound("players");
		for (String key : playerTag.getAllKeys()) {
			state.players.put(UUID.fromString(key), RaceData.fromNbt(playerTag.getCompound(key)));
		}
		CompoundTag nodeTag = nbt.getCompound("nodes");
		for (String key : nodeTag.getAllKeys()) {
			List<GlobalPos> list = new ArrayList<>();
			for (Tag e : nodeTag.getList(key, Tag.TAG_COMPOUND)) {
				CompoundTag entry = (CompoundTag) e;
				ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(entry.getString("dim")));
				list.add(GlobalPos.of(dim, BlockPos.of(entry.getLong("pos"))));
			}
			state.nodes.put(UUID.fromString(key), list);
		}
		return state;
	}
}
