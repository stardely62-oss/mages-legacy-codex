package euf.mageslegacy.voidborn;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A Voidborn's own: which room of the pocket dimension is theirs, and where they stepped in from. */
public class VoidData {
	/** The room's number (it lies at x = number * Pocket.SPACING); -1 until the first visit. */
	public int room = -1;
	/** How far the room's island has been laid from its middle (0: not yet). */
	public int builtHalf;
	/** Where to come back out to: the world, the spot and the facing. Null when not in the pocket. */
	public ResourceKey<Level> returnWorld;
	public Vec3 returnPos;
	public float returnYaw;
	public float returnPitch;

	public CompoundTag toNbt() {
		CompoundTag nbt = new CompoundTag();
		nbt.putInt("room", room);
		nbt.putInt("builtHalf", builtHalf);
		if (returnWorld != null && returnPos != null) {
			nbt.putString("returnWorld", returnWorld.location().toString());
			nbt.putDouble("rx", returnPos.x);
			nbt.putDouble("ry", returnPos.y);
			nbt.putDouble("rz", returnPos.z);
			nbt.putFloat("ryaw", returnYaw);
			nbt.putFloat("rpitch", returnPitch);
		}
		return nbt;
	}

	public void readNbt(CompoundTag nbt) {
		room = nbt.contains("room") ? nbt.getInt("room") : -1;
		builtHalf = nbt.getInt("builtHalf");
		if (nbt.contains("returnWorld")) {
			ResourceLocation id = ResourceLocation.tryParse(nbt.getString("returnWorld"));
			returnWorld = id == null ? null : ResourceKey.create(Registries.DIMENSION, id);
			returnPos = new Vec3(nbt.getDouble("rx"), nbt.getDouble("ry"), nbt.getDouble("rz"));
			returnYaw = nbt.getFloat("ryaw");
			returnPitch = nbt.getFloat("rpitch");
		}
	}
}
