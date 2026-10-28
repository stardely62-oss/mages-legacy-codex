package euf.mageslegacy.client;

import com.mojang.blaze3d.platform.InputConstants;
import euf.mageslegacy.MagesLegacy;
import euf.mageslegacy.client.screen.BestiaryScreen;
import euf.mageslegacy.client.screen.LoreScreen;
import euf.mageslegacy.client.screen.MercShopScreen;
import euf.mageslegacy.client.screen.RaceSelectScreen;
import euf.mageslegacy.client.screen.TalentScreen;
import euf.mageslegacy.net.RacePackets;
import euf.mageslegacy.race.AbilitySlots;
import euf.mageslegacy.race.Race;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import euf.mageslegacy.platform.Network.Client;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import euf.mageslegacy.platform.Network;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.FriendlyByteBuf;
import org.lwjgl.glfw.GLFW;

public class MagesLegacyClient implements ClientModInitializer {
	public static final String CATEGORY = "category.mages_legacy";
	public static final KeyMapping[] ABILITY_KEYS = {
			key("ability_1", GLFW.GLFW_KEY_R), key("ability_2", GLFW.GLFW_KEY_V), key("ability_3", GLFW.GLFW_KEY_Z), key("ability_4", GLFW.GLFW_KEY_H),
			key("ability_5", GLFW.GLFW_KEY_LEFT_ALT)};
	// Defaults clear of vanilla and of JourneyMap (which takes G, B, N, J, C, O, F, [, \, - and =)
	public static final KeyMapping TREE_KEY = key("talents", GLFW.GLFW_KEY_K);
	public static final KeyMapping BESTIARY_KEY = key("bestiary", GLFW.GLFW_KEY_U);
	public static final KeyMapping SQUAD_KEY = key("next_squad", GLFW.GLFW_KEY_Y);
	private static boolean keysSettled;

	/**
	 * The key as printed on an English keyboard (R, V, G, H; Alt for either Alt), whatever layout the system has switched to: GLFW names
	 * letter keys by the active layout, which turned R into К. Other keys keep their localized names.
	 */
	public static String keyLabel(KeyMapping binding) {
		String key = binding.saveString();
		String prefix = "key.keyboard.";
		if (key.startsWith(prefix) && key.length() == prefix.length() + 1) {
			return key.substring(prefix.length()).toUpperCase(Locale.ROOT);
		}
		// "Left Alt" won't fit a key cap: the side goes
		for (String side : List.of("left.", "right.")) {
			String name = key.startsWith(prefix + side) ? key.substring(prefix.length() + side.length()) : "";
			if (List.of("alt", "shift", "control").contains(name)) {
				return name.equals("control") ? "Ctrl" : name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
			}
		}
		return binding.getTranslatedKeyMessage().getString();
	}

	private static KeyMapping key(String name, int code) {
		return KeyBindingHelper.registerKeyBinding(new KeyMapping("key.mages_legacy." + name, InputConstants.Type.KEYSYM, code, CATEGORY));
	}

	@Override
	public void onInitializeClient() {
		BlockRenderLayerMap.INSTANCE.putBlock(MagesLegacy.MYCELIUM_NODE, RenderType.cutout());
		RaceParticles.register();
		Client.registerGlobalReceiver(RacePackets.SYNC, (client, handler, buf, sender) -> {
			FriendlyByteBuf copy = PacketByteBufs.copy(buf);
			client.execute(() -> {
				ClientRaceState.read(copy);
				// The choice was made (here or by an operator): the lore and choice screens are done
				if (ClientRaceState.race != null && (client.screen instanceof LoreScreen || client.screen instanceof RaceSelectScreen)) {
					client.setScreen(null);
				}
			});
		});
		Client.registerGlobalReceiver(RacePackets.ECHOES, (client, handler, buf, sender) -> {
			FriendlyByteBuf copy = PacketByteBufs.copy(buf);
			client.execute(() -> EchoVisuals.read(copy));
		});
		Client.registerGlobalReceiver(RacePackets.FACES, (client, handler, buf, sender) -> {
			FriendlyByteBuf copy = PacketByteBufs.copy(buf);
			client.execute(() -> FaceVisuals.read(copy));
		});
		Client.registerGlobalReceiver(RacePackets.QUICKENED, (client, handler, buf, sender) -> {
			FriendlyByteBuf copy = PacketByteBufs.copy(buf);
			client.execute(() -> QuickenVisuals.read(copy));
		});
		Client.registerGlobalReceiver(RacePackets.FX, (client, handler, buf, sender) -> {
			FriendlyByteBuf copy = PacketByteBufs.copy(buf);
			client.execute(() -> FxRenderer.read(copy));
		});
		Client.registerGlobalReceiver(RacePackets.OPEN_CHOICE, (client, handler, buf, sender) ->
				client.execute(() -> client.setScreen(new LoreScreen())));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			ClientRaceState.clear();
			EchoVisuals.clear();
			FaceVisuals.clear();
			QuickenVisuals.clear();
			FxRenderer.clear();
		}));
		HudRenderCallback.EVENT.register(RaceHud::render);
		WorldRenderEvents.AFTER_ENTITIES.register(QuickenVisuals::render);
		WorldRenderEvents.AFTER_TRANSLUCENT.register(FxRenderer::render);
		HudRenderCallback.EVENT.register(FxRenderer::renderScreen);
		ClientTickEvents.END_CLIENT_TICK.register(MagesLegacyClient::tick);
	}

	private static void tick(Minecraft client) {
		ClientRaceState.tick();
		FxRenderer.tick();
		FaceVisuals.tick(client);
		if (!keysSettled && client.options != null) {
			keysSettled = true;
			settleKeys(client);
		}
		if (client.player == null) {
			return;
		}
		for (int slot = 0; slot < ABILITY_KEYS.length; slot++) {
			// H and X only where the race has something there (a talent not learnt yet still gets an answer)
			if (pressed(client, ABILITY_KEYS[slot], slot) && ClientRaceState.race != null && RaceHud.hasAbilities(ClientRaceState.race)
					&& AbilitySlots.talent(ClientRaceState.race, slot) != null) {
				// The Mercenary's H is the Mage's Shop, a screen of its own
				if (ClientRaceState.race == Race.MERCENARY && slot == 3) {
					RaceHud.pressed(slot);
					client.setScreen(new MercShopScreen());
					// The others see its door open
					sendMerc(5, 0, "");
				} else {
					useAbility(slot);
				}
			}
		}
		if (pressed(client, TREE_KEY, ABILITY_KEYS.length)) {
			client.setScreen(ClientRaceState.race == null ? new LoreScreen() : new TalentScreen());
		}
		if (pressed(client, BESTIARY_KEY, ABILITY_KEYS.length + 1) && ClientRaceState.race == Race.SHEPHERD) {
			client.setScreen(new BestiaryScreen());
		}
		if (pressed(client, SQUAD_KEY, ABILITY_KEYS.length + 2) && ClientRaceState.race == Race.SHEPHERD) {
			selectSquad(ClientRaceState.extra.getInt("activePreset") + 1);
		}
	}

	/** Our keys, all of them. */
	public static List<KeyMapping> ourKeys() {
		List<KeyMapping> keys = new ArrayList<>(List.of(ABILITY_KEYS));
		keys.addAll(List.of(TREE_KEY, BESTIARY_KEY, SQUAD_KEY));
		return keys;
	}

	/** Other bindings (vanilla's or another mod's) on the same key as ours. */
	public static List<KeyMapping> clashes(Minecraft client, KeyMapping ours) {
		List<KeyMapping> list = new ArrayList<>();
		for (KeyMapping other : client.options.keyMappings) {
			if (other != ours && !other.isUnbound() && !ours.isUnbound() && other.same(ours) && !ourKeys().contains(other)) {
				list.add(other);
			}
		}
		return list;
	}

	/**
	 * Once a start: a key of ours that shares its key with another mod's (an older default, or a mod installed later)
	 * goes back to our default when that one is free, so a modpack works without anyone opening the controls.
	 */
	private static void settleKeys(Minecraft client) {
		boolean changed = false;
		for (KeyMapping ours : ourKeys()) {
			if (clashes(client, ours).isEmpty() || ours.isDefault()) {
				continue;
			}
			KeyMapping probe = ours;
			boolean defaultFree = java.util.Arrays.stream(client.options.keyMappings)
					.noneMatch(other -> other != probe && KeyBindingHelper.getBoundKeyOf(other).equals(probe.getDefaultKey()));
			if (defaultFree) {
				MagesLegacy.LOGGER.info("Key {} clashed with another mod; back to its default {}", ours.getName(), ours.getDefaultKey().getName());
				ours.setKey(ours.getDefaultKey());
				changed = true;
			}
		}
		if (changed) {
			KeyMapping.resetMapping();
			client.options.save();
		}
	}

	/** Whether each of our keys was down last tick, by the physical key (see {@link #pressed}). */
	private static final boolean[] DOWN = new boolean[ABILITY_KEYS.length + 3];

	/**
	 * Whether one of our keys was pressed this tick. Minecraft gives a key to one binding only, so when another mod
	 * (JourneyMap, say) binds the same key, ours never hears it: then the physical key itself is watched, and both mods
	 * act on it. Not while a screen is open, so typing in the chat doesn't cast anything.
	 */
	private static boolean pressed(Minecraft client, KeyMapping binding, int index) {
		boolean fired = false;
		while (binding.consumeClick()) {
			fired = true;
		}
		InputConstants.Key key = KeyBindingHelper.getBoundKeyOf(binding);
		boolean down = key.getType() == InputConstants.Type.KEYSYM && key.getValue() != InputConstants.UNKNOWN.getValue()
				&& InputConstants.isKeyDown(client.getWindow().getWindow(), key.getValue());
		boolean edge = down && !DOWN[index];
		DOWN[index] = down;
		return fired || (edge && client.screen == null);
	}

	public static void useAbility(int slot) {
		Minecraft client = Minecraft.getInstance();
		RaceHud.pressed(slot);
		FriendlyByteBuf buf = Network.create();
		buf.writeVarInt(slot);
		// Sneak as this client sees it, sent along: the server's idea of a crouch can lag behind the key
		buf.writeBoolean(client.options.keyShift.isDown() || client.player != null && client.player.isShiftKeyDown());
		Client.send(RacePackets.ABILITY, buf);
	}

	public static void sendChoice(String raceId) {
		FriendlyByteBuf buf = Network.create();
		buf.writeUtf(raceId);
		Client.send(RacePackets.CHOOSE, buf);
	}

	/** Makes squad {@code index} (wrapping around) the one the Call brings. */
	public static void selectSquad(int index) {
		int wrapped = Math.floorMod(index, 3);
		ClientRaceState.extra.putInt("activePreset", wrapped);
		FriendlyByteBuf buf = Network.create();
		buf.writeVarInt(0);
		buf.writeVarInt(wrapped);
		Client.send(RacePackets.PRESET, buf);
	}

	public static void renameBeast(int index, String name) {
		FriendlyByteBuf buf = Network.create();
		buf.writeVarInt(0);
		buf.writeVarInt(index);
		buf.writeUtf(name);
		Client.send(RacePackets.BEAST, buf);
	}

	public static void toggleLeader(int index) {
		FriendlyByteBuf buf = Network.create();
		buf.writeVarInt(1);
		buf.writeVarInt(index);
		Client.send(RacePackets.BEAST, buf);
	}

	/** One more (+1) or one less (-1) echo of a kind in a squad. */
	public static void editSquad(int index, String echo, int delta) {
		FriendlyByteBuf buf = Network.create();
		buf.writeVarInt(1);
		buf.writeVarInt(index);
		buf.writeUtf(echo);
		buf.writeVarInt(delta);
		Client.send(RacePackets.PRESET, buf);
	}

	/** The Mage's Shop: see {@link RacePackets#MERC}. */
	public static void sendMerc(int action, int index, String ware) {
		FriendlyByteBuf buf = Network.create();
		buf.writeVarInt(action);
		if (action <= 2) {
			buf.writeVarInt(index);
		}
		if (action == 4) {
			buf.writeUtf(ware);
		}
		Client.send(RacePackets.MERC, buf);
	}

	public static void sendUnlock(String talent) {
		FriendlyByteBuf buf = Network.create();
		buf.writeUtf(talent);
		Client.send(RacePackets.UNLOCK, buf);
	}
}
