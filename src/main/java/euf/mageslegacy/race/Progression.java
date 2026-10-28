package euf.mageslegacy.race;

import euf.mageslegacy.net.RacePackets;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Race experience, levels, milestones that open the tiers of the tree, and buying talents. */
public final class Progression {
	public static final int MAX_TIER = 4;
	/** Tier N needs milestone N-1: none, the Nether, a summoned Wither, the dead dragon; and a race level as well. */
	private static final String[] MILESTONES = {null, "story/enter_the_nether", "nether/summon_wither", "end/kill_dragon"};
	public static final int[] TIER_LEVELS = {0, 10, 20, 30};

	public enum UnlockResult { OK, NO_RACE, UNKNOWN, OTHER_RACE, ALREADY, NO_POINTS, NEEDS_TALENT, NEEDS_MILESTONE }

	private Progression() {
	}

	public static boolean tierOpen(ServerPlayer player, int tier) {
		if (tier <= 1) {
			return true;
		}
		if (tier > MAX_TIER) {
			return false;
		}
		RaceData data = RaceState.of(player);
		if (data == null || data.level < TIER_LEVELS[tier - 1]) {
			return false;
		}
		AdvancementHolder advancement = player.server.getAdvancements().get(ResourceLocation.parse(MILESTONES[tier - 1]));
		return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
	}

	/** Highest open tier, for the client's tree screen. */
	public static int openTiers(ServerPlayer player) {
		int tier = 1;
		while (tier < MAX_TIER && tierOpen(player, tier + 1)) {
			tier++;
		}
		return tier;
	}

	/**
	 * How much stronger a race's abilities are at its level: 3% a level, two and a half times at level 50, so race
	 * magic keeps up with enchanted weapons late in the game.
	 */
	public static float power(RaceData data) {
		return 1 + 0.03f * data.level;
	}

	/**
	 * A race grows into its powers: damage from its abilities (void and spore magic, a race's own blows of power) and
	 * from a Shepherd's creatures grows with the race level. Ordinary weapon blows stay as they are.
	 */
	public static float scale(net.minecraft.world.damagesource.DamageSource source, float amount) {
		net.minecraft.world.entity.Entity attacker = source.getEntity();
		ServerPlayer owner = null;
		if (attacker instanceof ServerPlayer player && source.is(net.minecraft.world.damagesource.DamageTypes.INDIRECT_MAGIC)
				&& source.getDirectEntity() == player) {
			owner = player;
		} else if (attacker != null && !(attacker instanceof net.minecraft.world.entity.player.Player) && attacker.getServer() != null) {
			java.util.UUID id = euf.mageslegacy.shepherd.Creatures.ownerOf(attacker);
			owner = id == null ? null : attacker.getServer().getPlayerList().getPlayer(id);
		}
		RaceData data = owner == null ? null : RaceState.of(owner);
		return data == null || data.race == null ? amount : amount * power(data);
	}

	/** Every monster a race's player kills teaches a little, whatever the race. */
	public static void onKill(ServerPlayer player, net.minecraft.world.entity.LivingEntity victim) {
		RaceData data = RaceState.of(player);
		if (data != null && data.race != null && victim instanceof net.minecraft.world.entity.monster.Enemy) {
			addXp(player, data, KILLS, 1, 60);
		}
	}

	/** How long a capped source's window lasts: ten minutes of world time. */
	public static final long XP_WINDOW = 12000;
	/** Capped sources several abilities share: any monster killed, and a race's own hunting (kills its powers made). */
	public static final String KILLS = "kills";
	public static final String HUNT = "hunt";
	public static final int HUNT_CAP = 80;

	/**
	 * Experience from a source that a farm could feed forever (a pile of mushrooms, a pen of chickens, an enderman
	 * farm): at most {@code cap} from {@code source} in ten minutes, so the race grows by playing, not by repeating.
	 */
	public static void addXp(ServerPlayer player, RaceData data, String source, int amount, int cap) {
		// Mercenaries learn from their contracts and nothing else
		if (data.race == Race.MERCENARY) {
			return;
		}
		long now = player.serverLevel().getGameTime();
		long[] window = data.xpTaken.get(source);
		if (window == null || now - window[0] >= XP_WINDOW || now < window[0]) {
			window = new long[] {now, 0};
			data.xpTaken.put(source, window);
		}
		int allowed = (int) Math.min(amount, cap - window[1]);
		if (allowed <= 0) {
			return;
		}
		window[1] += allowed;
		addXp(player, data, allowed);
	}

	public static void addXp(ServerPlayer player, RaceData data, int amount) {
		int gained = data.addXp(amount);
		if (gained > 0) {
			player.displayClientMessage(Component.translatable("mages_legacy.message.level_up", data.level, data.freePoints()).withStyle(ChatFormatting.GREEN), false);
			player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 0.7f);
		}
	}

	public static UnlockResult unlock(ServerPlayer player, RaceData data, String id) {
		if (data.race == null) {
			return UnlockResult.NO_RACE;
		}
		Talent talent = Talents.byId(id).orElse(null);
		if (talent == null) {
			return UnlockResult.UNKNOWN;
		}
		if (talent.race() != data.race) {
			return UnlockResult.OTHER_RACE;
		}
		if (data.has(id)) {
			return UnlockResult.ALREADY;
		}
		if (talent.requires() != null && !data.has(talent.requires())) {
			return UnlockResult.NEEDS_TALENT;
		}
		if (!tierOpen(player, talent.tier())) {
			return UnlockResult.NEEDS_MILESTONE;
		}
		if (data.freePoints() < talent.cost()) {
			return UnlockResult.NO_POINTS;
		}
		data.talents.add(id);
		data.dirty = true;
		RacePackets.sync(player, data);
		return UnlockResult.OK;
	}
}
