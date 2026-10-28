package euf.mageslegacy.masked;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** A Masked's gallery: the faces taken, each with how fresh it still is (100 to 0), and which one is worn. */
public class MaskData {
	/** Face -> freshness, in the order they were taken. */
	public final Map<Face, Float> gallery = new LinkedHashMap<>();
	/** The face worn now, or null: faceless. */
	public Face worn;

	public void clear() {
		gallery.clear();
		worn = null;
	}

	public CompoundTag toNbt() {
		CompoundTag nbt = new CompoundTag();
		// A list, not a compound: the order faces were taken is the order Z goes through them
		ListTag faces = new ListTag();
		gallery.forEach((face, fresh) -> {
			CompoundTag entry = new CompoundTag();
			entry.putString("id", face.id);
			entry.putFloat("fresh", fresh);
			faces.add(entry);
		});
		nbt.put("gallery", faces);
		if (worn != null) {
			nbt.putString("worn", worn.id);
		}
		return nbt;
	}

	public void readNbt(CompoundTag nbt) {
		clear();
		for (Tag e : nbt.getList("gallery", Tag.TAG_COMPOUND)) {
			CompoundTag entry = (CompoundTag) e;
			Face.byId(entry.getString("id")).ifPresent(face -> gallery.put(face, entry.getFloat("fresh")));
		}
		worn = Face.byId(nbt.getString("worn")).filter(gallery::containsKey).orElse(null);
	}
}
