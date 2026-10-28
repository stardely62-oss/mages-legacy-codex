package euf.mageslegacy.race;

import net.minecraft.world.entity.player.Player;

/**
 * What common code may ask about the client's own race. The client sets it from the sync packet; on a dedicated
 * server it simply stays empty.
 */
public final class ClientRaceView {
	public static volatile Race race;
	/** The client's own player wears a spider's face (or has one under the Thousand Faces): it climbs walls. */
	public static volatile boolean climbs;

	private ClientRaceView() {
	}

	public static boolean isMycelid(Player player) {
		return race == Race.MYCELID;
	}
}
