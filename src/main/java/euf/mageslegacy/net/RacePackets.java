package euf.mageslegacy.net;

import euf.mageslegacy.MagesLegacy;
import euf.mageslegacy.bloodkin.Bloodkin;
import euf.mageslegacy.frozen.Frozen;
import euf.mageslegacy.mercenary.Contracts;
import euf.mageslegacy.mercenary.Mercenary;
import euf.mageslegacy.mercenary.Shop;
import euf.mageslegacy.mycelid.Mycelid;
import euf.mageslegacy.race.AbilityResult;
import euf.mageslegacy.race.AbilitySlots;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceData;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.shepherd.Bonds;
import euf.mageslegacy.shepherd.Shepherd;
import euf.mageslegacy.masked.Masked;
import euf.mageslegacy.tempter.Tempter;
import euf.mageslegacy.voidborn.Voidborn;
import java.util.Locale;
import euf.mageslegacy.platform.Network;
import euf.mageslegacy.platform.Network.Server;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Channel ids and the server side of every packet. The server re-checks everything a client asks for. */
public final class RacePackets {
	public static final ResourceLocation SYNC = MagesLegacy.id("sync");
	public static final ResourceLocation OPEN_CHOICE = MagesLegacy.id("open_choice");
	public static final ResourceLocation CHOOSE = MagesLegacy.id("choose");
	public static final ResourceLocation ABILITY = MagesLegacy.id("ability");
	public static final ResourceLocation UNLOCK = MagesLegacy.id("unlock");
	/** Squads: [0, index] picks the active one, [1, index, echo id, delta] adds or takes one echo. */
	public static final ResourceLocation PRESET = MagesLegacy.id("preset");
	/** Bonded beasts: [0, index, name] renames, [1, index] makes the leader. */
	public static final ResourceLocation BEAST = MagesLegacy.id("beast");
	/** Server to client: entity ids of the echoes in the player's world, so they are drawn as spirits. */
	public static final ResourceLocation ECHOES = MagesLegacy.id("echoes");
	/** Server to client: which players in the client's world wear which face (entity id, face), drawn in that shape. */
	public static final ResourceLocation FACES = MagesLegacy.id("faces");
	/** Server to client: the quickened blocks of the client's world and the ticks each has left. */
	public static final ResourceLocation QUICKENED = MagesLegacy.id("quickened");
	/**
	 * The Mage's Shop: [0, index] takes an offered contract, [1, index] gives up one in hand, [2, index] hands over
	 * what a gathering contract wants, [3] buys fresh offers, [4, ware id] buys a ware.
	 */
	public static final ResourceLocation MERC = MagesLegacy.id("merc");
	/** Server to client: an ability effect to draw (see {@link euf.mageslegacy.fx.Fx}). */
	public static final ResourceLocation FX = MagesLegacy.id("fx");

	private RacePackets() {
	}

	public static void registerServerReceivers() {
		Server.registerGlobalReceiver(CHOOSE, (server, player, handler, buf, sender) -> {
			String id = buf.readUtf(32);
			server.execute(() -> choose(player, Race.byId(id).orElse(null)));
		});
		Server.registerGlobalReceiver(ABILITY, (server, player, handler, buf, sender) -> {
			int slot = buf.readVarInt();
			boolean alt = buf.readBoolean();
			server.execute(() -> useAbility(player, slot, alt));
		});
		Server.registerGlobalReceiver(PRESET, (server, player, handler, buf, sender) -> {
			int action = buf.readVarInt();
			int index = buf.readVarInt();
			String id = action == 1 ? buf.readUtf(64) : "";
			int delta = action == 1 ? buf.readVarInt() : 0;
			server.execute(() -> {
				RaceData data = RaceState.of(player);
				if (data == null || data.race != Race.SHEPHERD) {
					return;
				}
				if (action == 0) {
					Shepherd.selectPreset(player, data, index);
				} else {
					Shepherd.editPreset(data, index, id, delta);
				}
				sync(player, data);
			});
		});
		Server.registerGlobalReceiver(BEAST, (server, player, handler, buf, sender) -> {
			int action = buf.readVarInt();
			int index = buf.readVarInt();
			String name = action == 0 ? buf.readUtf(64) : "";
			server.execute(() -> {
				RaceData data = RaceState.of(player);
				if (data == null || data.race != Race.SHEPHERD) {
					return;
				}
				if (action == 0) {
					Bonds.rename(player, data, index, name);
				} else {
					Bonds.toggleLeader(data, index);
				}
				sync(player, data);
			});
		});
		Server.registerGlobalReceiver(MERC, (server, player, handler, buf, sender) -> {
			int action = buf.readVarInt();
			int index = action <= 2 ? buf.readVarInt() : 0;
			String id = action == 4 ? buf.readUtf(64) : "";
			server.execute(() -> {
				RaceData data = RaceState.of(player);
				if (data == null || data.race != Race.MERCENARY) {
					return;
				}
				Contracts.refresh(player, data);
				switch (action) {
					case 0 -> Contracts.accept(player, data, index);
					case 1 -> Contracts.abandon(player, data, index);
					case 2 -> Contracts.deliver(player, data, index);
					case 3 -> Contracts.reroll(player, data);
					case 4 -> Shop.buy(player, data, id);
					case 5 -> Mercenary.openedShop(player);
					case 6 -> Mercenary.closedShop(player);
					default -> {
					}
				}
				sync(player, data);
			});
		});
		Server.registerGlobalReceiver(UNLOCK, (server, player, handler, buf, sender) -> {
			String id = buf.readUtf(64);
			server.execute(() -> {
				RaceData data = RaceState.of(player);
				if (data != null) {
					Progression.UnlockResult result = Progression.unlock(player, data, id);
					player.displayClientMessage(Component.translatable("mages_legacy.unlock." + result.name().toLowerCase(Locale.ROOT)), true);
				}
			});
		});
	}

	/** The first (and, without a command, only) choice of race. */
	public static boolean choose(ServerPlayer player, Race race) {
		RaceData data = RaceState.of(player);
		if (data == null || data.race != null || race == null || !race.playable) {
			return false;
		}
		data.reset(race);
		player.displayClientMessage(Component.translatable("mages_legacy.message.chosen", Component.translatable(race.key())).withStyle(ChatFormatting.GOLD), false);
		if (race == Race.SHEPHERD) {
			player.displayClientMessage(Component.translatable("mages_legacy.message.shepherd_hint").withStyle(ChatFormatting.YELLOW), false);
		}
		if (race == Race.MERCENARY) {
			player.displayClientMessage(Component.translatable("mages_legacy.message.mercenary_hint").withStyle(ChatFormatting.YELLOW), false);
		}
		if (race == Race.TEMPTER) {
			player.displayClientMessage(Component.translatable("mages_legacy.message.tempter_hint").withStyle(ChatFormatting.YELLOW), false);
		}
		if (race == Race.MASKED) {
			player.displayClientMessage(Component.translatable("mages_legacy.message.masked_hint").withStyle(ChatFormatting.YELLOW), false);
		}
		sync(player, data);
		return true;
	}

	/** A key pressed: {@code alt} is whether the client held sneak with it (it changes some abilities, never picks one). */
	public static void useAbility(ServerPlayer player, int slot, boolean alt) {
		RaceData data = RaceState.of(player);
		if (data == null || data.race == null || slot < 0 || slot >= AbilitySlots.KEYS || player.isSpectator()) {
			return;
		}
		data.alt = alt;
		// Abilities open with the race's growth
		if (data.level < AbilitySlots.unlockLevel(data.race, slot)) {
			player.displayClientMessage(Component.translatable("mages_legacy.ability.level_locked", AbilitySlots.unlockLevel(data.race, slot)), true);
			player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.7f, 0.5f);
			return;
		}
		AbilityResult result = switch (data.race) {
			case MYCELID -> Mycelid.use(player, data, slot);
			case SHEPHERD -> Shepherd.use(player, data, slot);
			case FROZEN -> Frozen.use(player, data, slot);
			case BLOODKIN -> Bloodkin.use(player, data, slot);
			case VOIDBORN -> Voidborn.use(player, data, slot);
			case MERCENARY -> Mercenary.use(player, data, slot);
			case TEMPTER -> Tempter.use(player, data, slot);
			case MASKED -> Masked.use(player, data, slot);
			default -> AbilityResult.LOCKED;
		};
		if (result != AbilityResult.OK) {
			// A low knock, so a refusal is heard even when the line above the hotbar goes unread
			player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.7f, 0.5f);
		}
		if (result != AbilityResult.OK && result != AbilityResult.HINTED) {
			// Lacking the resource names it: spores, voice...
			String key = "mages_legacy.ability." + result.name().toLowerCase(Locale.ROOT);
			player.displayClientMessage(Component.translatable(result == AbilityResult.NO_RESOURCE ? key + "." + data.race.id() : key), true);
		}
		sync(player, data);
	}

	/** Shows the legend and the choice of race; false while the client has not registered our channels yet. */
	public static boolean openChoice(ServerPlayer player) {
		if (!Server.canSend(player, OPEN_CHOICE)) {
			return false;
		}
		Server.send(player, OPEN_CHOICE, PacketByteBufs.empty());
		return true;
	}

	/** Everything the HUD and the tree screen show. */
	public static void sync(ServerPlayer player, RaceData data) {
		// Until the client has registered our channels, the data stays dirty and goes out with a later tick
		if (!Server.canSend(player, SYNC)) {
			return;
		}
		data.dirty = false;
		FriendlyByteBuf buf = Network.create();
		buf.writeUtf(data.race == null ? "" : data.race.id());
		buf.writeFloat(data.resource);
		buf.writeVarInt(data.level);
		buf.writeVarInt(data.xp);
		buf.writeVarInt(RaceData.xpForNext(data.level));
		buf.writeVarInt(data.freePoints());
		buf.writeVarInt(Progression.openTiers(player));
		buf.writeVarInt(data.talents.size());
		data.talents.forEach(buf::writeUtf);
		long now = player.server.getTickCount();
		for (int slot = 0; slot < AbilitySlots.COOLDOWNS; slot++) {
			buf.writeVarInt((int) Math.max(0, data.readyAt.getOrDefault(slot, 0L) - now));
		}
		buf.writeVarInt(data.chainLeft > 0 && now <= data.chainUntil ? data.chainLeft : 0);
		// Race-specific extras: the Shepherd's capacity, bestiary and bonds
		buf.writeNbt(data.race == Race.SHEPHERD ? Shepherd.clientInfo(player, data) : data.race == Race.VOIDBORN ? Voidborn.clientInfo(player)
				: data.race == Race.MERCENARY ? Mercenary.clientInfo(player, data) : data.race == Race.TEMPTER ? Tempter.clientInfo(player)
				: data.race == Race.MASKED ? Masked.clientInfo(player, data) : new CompoundTag());
		Server.send(player, SYNC, buf);
	}
}
