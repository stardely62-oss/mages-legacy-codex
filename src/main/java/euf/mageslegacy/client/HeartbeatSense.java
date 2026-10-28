package euf.mageslegacy.client;

import euf.mageslegacy.bloodkin.Bloodkin;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.Talents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * A Bloodkin hears the hearts around it: every creature with blood nearby shows through walls in a red outline, on
 * this player's screen only.
 */
public final class HeartbeatSense {
	public static final int OUTLINE_RGB = 0xD0283C;

	private HeartbeatSense() {
	}

	public static boolean hears(Entity entity) {
		Minecraft client = Minecraft.getInstance();
		if (ClientRaceState.race != Race.BLOODKIN || client.player == null || entity == client.player || !Bloodkin.hasBlood(entity)
				|| Bloodkin.swarmBat(entity)) {
			return false;
		}
		int range = ClientRaceState.talents.contains(Talents.NIGHT_SENSE) ? Bloodkin.SENSE_WIDE : Bloodkin.SENSE;
		return entity.distanceToSqr(client.player) < range * range;
	}
}
