package euf.mageslegacy.client;

import euf.mageslegacy.bloodkin.Bloodkin;
import euf.mageslegacy.client.screen.LegacyUi;
import euf.mageslegacy.frozen.Frozen;
import euf.mageslegacy.mycelid.Mycelid;
import euf.mageslegacy.race.AbilitySlots;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.Talents;
import euf.mageslegacy.shepherd.Bonds;
import euf.mageslegacy.shepherd.EchoType;
import euf.mageslegacy.shepherd.Shepherd;
import euf.mageslegacy.masked.Face;
import euf.mageslegacy.masked.Masked;
import euf.mageslegacy.tempter.Tempter;
import euf.mageslegacy.voidborn.Voidborn;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * The race resource as a glowing vial left of the hotbar (its icon beside it), and the three abilities in framed
 * slots right of it, with key caps, a cooldown veil with the seconds left, and a red tint when the resource is short.
 */
public final class RaceHud {
	/** Item icons, the fallback while the Codex icons are missing. */
	public static final Item[] MYCELID_ICONS = {Items.RED_MUSHROOM, Items.SPORE_BLOSSOM, Items.MYCELIUM, Items.MOSS_CARPET, Items.WITHER_ROSE};
	public static final String[] MYCELID_ABILITY_ICONS = {"ability_spore_jump", "ability_spore_cloud", "ability_network", "ability_carpet", "swarm_bloom"};
	public static final int[] MYCELID_COSTS = {15, 30, 40, Mycelid.CARPET_COST, Mycelid.BLOOM_COST};
	/** The Whistle's orders as marks on its slot: Follow, Guard, Hunt. */
	public static final String[] STANCE_MARKS = {"↩", "◈", "⚔"};
	// Keys H and Alt: the abilities talents open show their talent's icon
	public static final Item[] BLOODKIN_ICONS = {Items.REDSTONE, Items.NETHER_WART, Items.IRON_SWORD, Items.BAT_SPAWN_EGG, Items.ENCHANTED_GOLDEN_APPLE};
	public static final String[] BLOODKIN_ABILITY_ICONS = {"ability_dash", "ability_harvest", "ability_sacrifice", "ability_swarm", "fang_feast"};
	public static final Item[] FROZEN_ICONS = {Items.CLOCK, Items.PACKED_ICE, Items.SAND, Items.ICE, Items.HEART_OF_THE_SEA};
	public static final String[] FROZEN_ABILITY_ICONS = {"ability_rewind", "ability_dome", "ability_quicken", "ability_stop_gaze", "flow_current"};
	public static final Item[] VOIDBORN_ICONS = {Items.ENDER_PEARL, Items.CRYING_OBSIDIAN, Items.CHORUS_FRUIT, Items.ENDER_CHEST, Items.NETHER_STAR};
	public static final String[] VOIDBORN_ABILITY_ICONS = {"ability_void_step", "ability_well", "ability_excise", "ability_pocket", "abyss_gaze"};
	public static final Item[] SHEPHERD_ICONS = {Items.BONE, Items.GOAT_HORN, Items.FEATHER, Items.NOTE_BLOCK, Items.WHITE_BANNER};
	public static final String[] SHEPHERD_ABILITY_ICONS = {"ability_call", "ability_rally", "ability_whistle", "ability_song", "legion_army"};
	public static final Item[] MERCENARY_ICONS = {Items.REDSTONE, Items.EMERALD, Items.IRON_CHESTPLATE, Items.WRITABLE_BOOK, Items.GOLD_BLOCK};
	public static final String[] MERCENARY_ABILITY_ICONS = {"ability_credit_strike", "ability_exchange", "ability_pledge", "ability_shop", "deal_stake"};
	public static final Item[] TEMPTER_ICONS = {Items.ROSE_BUSH, Items.PHANTOM_MEMBRANE, Items.ENDER_EYE, Items.WHITE_BED, Items.REDSTONE_BLOCK};
	public static final String[] TEMPTER_ABILITY_ICONS = {"ability_kiss", "ability_wings", "ability_gaze", "ability_lullaby", "thread_break"};
	public static final Item[] MASKED_ICONS = {Items.SHEARS, Items.ZOMBIE_HEAD, Items.ARMOR_STAND, Items.BELL, Items.CARVED_PUMPKIN};
	public static final String[] MASKED_ABILITY_ICONS = {"ability_tear", "ability_power", "ability_change", "ability_kin", "gal_thousand"};

	private RaceHud() {
	}

	public static boolean hasAbilities(Race race) {
		return race == Race.MYCELID || race == Race.SHEPHERD || race == Race.FROZEN || race == Race.BLOODKIN || race == Race.VOIDBORN
				|| race == Race.MERCENARY || race == Race.TEMPTER || race == Race.MASKED;
	}

	/** The main abilities: three each; the Shepherds have a fourth (the Song), the Voidborn too (the Pocket). Keys past these: {@link #shown}. */
	public static int abilityCount(Race race) {
		return race == Race.SHEPHERD || race == Race.VOIDBORN ? 4 : hasAbilities(race) ? 3 : 0;
	}

	/** Whether key {@code slot} has an ability for this player now (one a talent opens shows once it's learnt). */
	public static boolean shown(Race race, int slot) {
		return hasAbilities(race) && AbilitySlots.has(race, slot, ClientRaceState.talents);
	}

	/** Ticks until the ability on key {@code slot} is ready. */
	public static int cooldown(Race race, int slot) {
		return ClientRaceState.cooldown(AbilitySlots.cooldownSlot(race, slot));
	}

	/** The Codex icon of an ability key (0..4), or null if the race has none yet. */
	@Nullable
	public static String abilityIcon(Race race, int slot) {
		return switch (race) {
			case MYCELID -> MYCELID_ABILITY_ICONS[slot];
			case SHEPHERD -> SHEPHERD_ABILITY_ICONS[slot];
			case FROZEN -> FROZEN_ABILITY_ICONS[slot];
			case BLOODKIN -> BLOODKIN_ABILITY_ICONS[slot];
			case VOIDBORN -> VOIDBORN_ABILITY_ICONS[slot];
			case MERCENARY -> MERCENARY_ABILITY_ICONS[slot];
			case TEMPTER -> TEMPTER_ABILITY_ICONS[slot];
			case MASKED -> MASKED_ABILITY_ICONS[slot];
			default -> null;
		};
	}

	/**
	 * What an ability costs this player now: Light Spore makes the jump cheaper, the Call costs what the picked echo
	 * does (-1 while none is picked), Shadow for Shadow makes the swap cheaper.
	 */
	public static int cost(Race race, int slot) {
		// The Mercenaries pay in health, hunger and armour, not from a bar
		if (race == Race.MERCENARY) {
			return 0;
		}
		if (race == Race.MASKED) {
			return switch (slot) {
				case 1 -> ClientRaceState.extra.getInt("powerCost");
				case 2 -> ClientRaceState.talents.contains(Talents.GAL_CHEAP) ? Masked.SWAP_COST_CHEAP : Masked.SWAP_COST;
				case 3 -> ClientRaceState.talents.contains(Talents.KIN_KING) ? Masked.KIN_COST_KING : Masked.KIN_COST;
				case 4 -> Masked.THOUSAND_COST;
				default -> 0;
			};
		}
		if (race == Race.TEMPTER) {
			return switch (slot) {
				case 0 -> ClientRaceState.talents.contains(Talents.KISS_CHEAP) ? Tempter.KISS_COST_CHEAP : Tempter.KISS_COST;
				case 1 -> ClientRaceState.talents.contains(Talents.WING_CHEAP) ? Tempter.WINGS_COST_CHEAP : Tempter.WINGS_COST;
				case 2 -> ClientRaceState.talents.contains(Talents.CHARM_CHEAP) ? Tempter.CHARM_COST_CHEAP : Tempter.CHARM_COST;
				case 3 -> Tempter.LULLABY_COST;
				default -> Tempter.BREAK_COST;
			};
		}
		if (race == Race.SHEPHERD) {
			return switch (slot) {
				case 0 -> squadCost(false);
				case 1 -> Shepherd.RALLY_COST;
				case 3 -> Bonds.SONG_COST;
				case 4 -> Shepherd.ARMY_COST;
				default -> 0;
			};
		}
		if (race == Race.BLOODKIN) {
			// The Sacrifice costs health, not blood
			return switch (slot) {
				case 0 -> Bloodkin.DASH_COST;
				case 1 -> Bloodkin.HARVEST_COST;
				case 3 -> Bloodkin.SWARM_COST;
				case 4 -> Bloodkin.FEAST_COST;
				default -> 0;
			};
		}
		if (race == Race.VOIDBORN) {
			return switch (slot) {
				case 0 -> Voidborn.stepCost(ClientRaceState.talents.contains(Talents.STEP_CHEAP));
				case 1 -> Voidborn.WELL_COST;
				case 2 -> Voidborn.exciseCost(ClientRaceState.talents.contains(Talents.EXCISE_CHEAP));
				case 4 -> Voidborn.GAZE_COST;
				default -> Voidborn.pocketCost(ClientRaceState.talents.contains(Talents.POCKET_CHEAP));
			};
		}
		if (race == Race.FROZEN) {
			return switch (slot) {
				case 0 -> Frozen.rewindCost(ClientRaceState.talents.contains(Talents.LOOP_CHEAP));
				case 1 -> Frozen.domeCost(ClientRaceState.talents.contains(Talents.STASIS_CHEAP));
				case 3 -> Frozen.FREEZE_COST;
				case 4 -> Frozen.CURRENT_COST;
				default -> Frozen.QUICKEN_COST;
			};
		}
		if (slot == 0 && ClientRaceState.talents.contains(Talents.NET_CHEAP)) {
			return Mycelid.JUMP_COST_CHEAP;
		}
		return MYCELID_COSTS[slot];
	}

	/** The active squad from the sync: echo type id -> how many. */
	public static Map<String, Integer> activeSquad() {
		ListTag presets = ClientRaceState.extra.getList("presets", Tag.TAG_COMPOUND);
		int index = ClientRaceState.extra.getInt("activePreset");
		Map<String, Integer> squad = new LinkedHashMap<>();
		if (index < presets.size()) {
			CompoundTag preset = presets.getCompound(index);
			for (String key : preset.getAllKeys()) {
				squad.put(key, preset.getInt(key));
			}
		}
		return squad;
	}

	/**
	 * Voice for the squad members not on the field yet: all of them ({@code cheapest} false, shown on the slot) or
	 * the cheapest one (what a Call needs to bring anyone). -1 for an empty squad, 0 when all are there.
	 */
	public static int squadCost(boolean cheapest) {
		Map<String, Integer> squad = activeSquad();
		if (squad.isEmpty()) {
			return -1;
		}
		CompoundTag alive = ClientRaceState.extra.getCompound("alive");
		boolean quiet = ClientRaceState.talents.contains(Talents.LEGION_CHEAP);
		int total = 0;
		int min = Integer.MAX_VALUE;
		for (Map.Entry<String, Integer> e : squad.entrySet()) {
			EchoType type = EchoType.byId(e.getKey()).orElse(null);
			int lacking = type == null ? 0 : e.getValue() - type.units(alive.getInt(e.getKey()));
			if (lacking > 0) {
				total += lacking * type.voiceCost(quiet);
				min = Math.min(min, type.voiceCost(quiet));
			}
		}
		return cheapest ? (min == Integer.MAX_VALUE ? 0 : min) : total;
	}

	/** The race resource's icon, or null. */
	@Nullable
	public static String resourceIcon(Race race) {
		return switch (race) {
			case MYCELID -> "res_spores";
			case SHEPHERD -> "res_voice";
			case FROZEN -> "res_sand";
			case BLOODKIN -> "res_blood";
			case VOIDBORN -> "res_void";
			case MERCENARY -> "res_contract";
			case TEMPTER -> "res_adoration";
			case MASKED -> "res_essence";
			default -> null;
		};
	}

	/** An ability's icon: the Codex texture if it ships, the item otherwise. */
	public static void drawAbility(GuiGraphics ctx, Race race, int slot, int x, int y, boolean bright) {
		// The Masked's Power shows the worn face, the Change the face that comes next
		if (race == Race.MASKED && (slot == 1 || slot == 2)) {
			Face face = Face.byId(ClientRaceState.extra.getString(slot == 1 ? "face" : "next")).orElse(null);
			if (face != null) {
				ctx.renderItem(new ItemStack(face.egg), x, y);
				return;
			}
		}
		String icon = abilityIcon(race, slot);
		ResourceLocation texture = icon == null ? null : LegacyUi.icon(icon);
		if (texture != null && LegacyUi.exists(texture)) {
			LegacyUi.icon(ctx, texture, x, y, 16, bright);
		} else if (race == Race.MYCELID) {
			ctx.renderItem(new ItemStack(MYCELID_ICONS[slot]), x, y);
		} else if (race == Race.FROZEN) {
			ctx.renderItem(new ItemStack(FROZEN_ICONS[slot]), x, y);
		} else if (race == Race.BLOODKIN) {
			ctx.renderItem(new ItemStack(BLOODKIN_ICONS[slot]), x, y);
		} else if (race == Race.VOIDBORN) {
			ctx.renderItem(new ItemStack(VOIDBORN_ICONS[slot]), x, y);
		} else if (race == Race.MERCENARY) {
			ctx.renderItem(new ItemStack(MERCENARY_ICONS[slot]), x, y);
		} else if (race == Race.TEMPTER) {
			ctx.renderItem(new ItemStack(TEMPTER_ICONS[slot]), x, y);
		} else if (race == Race.MASKED) {
			ctx.renderItem(new ItemStack(MASKED_ICONS[slot]), x, y);
		} else if (race == Race.SHEPHERD) {
			// The Call shows the first echo of its squad
			EchoType echo = slot == 0 ? activeSquad().keySet().stream().findFirst().flatMap(EchoType::byId).orElse(null) : null;
			if (echo != null) {
				LegacyUi.portrait(ctx, "echo", echo.path(), echo.icon(), x, y, bright);
			} else {
				ctx.renderItem(new ItemStack(SHEPHERD_ICONS[slot]), x, y);
			}
		}
	}

	/** When each key was last pressed (client ticks), for the slot's answer: a bright pulse, or a red shake. */
	private static final long[] PRESSED = new long[AbilitySlots.KEYS];
	/** Whether the slot looked ready when it was pressed (after that it is cooling, so it can't be asked then). */
	private static final boolean[] PRESSED_READY = new boolean[AbilitySlots.KEYS];

	public static void pressed(int slot) {
		Race race = ClientRaceState.race;
		PRESSED[slot] = ClientRaceState.clientTicks();
		PRESSED_READY[slot] = race != null && shown(race, slot) && !locked(race, slot) && (cooldown(race, slot) == 0 || chain(race, slot))
				&& slotCost(race, slot) >= 0 && ClientRaceState.resource >= slotCost(race, slot);
	}

	/** An ability the race hasn't grown into yet. */
	public static boolean locked(Race race, int slot) {
		return ClientRaceState.level < AbilitySlots.unlockLevel(race, slot);
	}

	/** A Voidborn's Excision holding someone: pressing it lets go, for free. */
	private static boolean releases(Race race, int slot) {
		return race == Race.VOIDBORN && slot == 2 && ClientRaceState.extra.getBoolean("holding");
	}

	/** A Mycelid's spore jump chaining on: free of its cooldown for now. */
	private static boolean chain(Race race, int slot) {
		return race == Race.MYCELID && slot == 0 && ClientRaceState.chainLeft > 0;
	}

	/** What the slot asks for right now: a chained jump, or only the squad members still missing for the Call. */
	private static int slotCost(Race race, int slot) {
		if (releases(race, slot)) {
			return 0;
		}
		return chain(race, slot) ? Mycelid.CHAIN_COST : race == Race.SHEPHERD && slot == 0 ? squadCost(true) : cost(race, slot);
	}

	/** A pressed slot answers at once: a pulse in the race's colour when it fires, a red blink when it can't. */
	private static void pressFlash(GuiGraphics ctx, int slot, int sx, int sy, int color) {
		long since = ClientRaceState.clientTicks() - PRESSED[slot];
		if (PRESSED[slot] == 0 || since > 8) {
			return;
		}
		float t = since / 8f;
		int a = (int) ((1 - t) * 200);
		if (PRESSED_READY[slot]) {
			// The pulse spreads past the frame as it fades
			int grow = (int) (t * 5);
			LegacyUi.outline(ctx, sx - 2 - grow, sy - 2 - grow, sx + 20 + grow, sy + 20 + grow, (a << 24) | (color & 0xFFFFFF));
			ctx.fill(sx, sy, sx + 18, sy + 18, ((a / 2) << 24) | 0xFFFFFF);
		} else {
			ctx.fill(sx, sy, sx + 18, sy + 18, ((a / 2) << 24) | 0xE02020);
		}
	}

	public static void render(GuiGraphics ctx, float tickDelta) {
		Minecraft client = Minecraft.getInstance();
		Race race = ClientRaceState.race;
		if (race == null || client.options.hideGui || client.player == null || client.player.isSpectator()) {
			return;
		}
		Font tr = client.font;
		int w = ctx.guiWidth();
		int h = ctx.guiHeight();
		int color = LegacyUi.argb(race.color);

		// The vial, clear of the offhand slot
		int barX = w / 2 - 91 - 40;
		int barTop = h - 25;
		int barH = 23;
		ctx.fill(barX - 2, barTop - 2, barX + 8, barTop + barH + 2, LegacyUi.BLACK);
		ctx.fill(barX - 1, barTop - 1, barX + 7, barTop + barH + 1, LegacyUi.RIM);
		ctx.fill(barX, barTop, barX + 6, barTop + barH, 0xFF0E0B10);
		float fraction = Math.max(0, Math.min(1, ClientRaceState.resource / 100f));
		int filled = Math.round(barH * fraction);
		if (filled > 0) {
			int top = barTop + barH - filled;
			ctx.fillGradient(barX, top, barX + 6, barTop + barH, LegacyUi.mix(color, 0xFFFFFFFF, 0.25f), LegacyUi.mix(color, 0xFF000000, 0.35f));
			ctx.fill(barX + 1, top, barX + 2, barTop + barH, 0x50FFFFFF);
			// A shimmer rising through the liquid
			int shimmer = barTop + barH - 1 - (int) ((LegacyUi.time() * 14) % Math.max(1, filled));
			ctx.fill(barX, shimmer, barX + 6, shimmer + 1, 0x60FFFFFF);
			ctx.fill(barX, top, barX + 6, top + 1, LegacyUi.mix(color, 0xFFFFFFFF, 0.6f));
		}
		String icon = resourceIcon(race);
		if (icon != null && LegacyUi.exists(LegacyUi.icon(icon))) {
			LegacyUi.icon(ctx, LegacyUi.icon(icon), barX - 18, h - 19, 16, true);
		}
		// A Mercenary's bar is the first contract in hand, done so far
		String amount = (int) ClientRaceState.resource + (race == Race.MERCENARY ? "%" : "");
		ctx.drawString(tr, amount, barX + 3 - tr.width(amount) / 2, barTop - 11, color, true);
		if (race == Race.SHEPHERD) {
			// Bond slots in use, over the voice icon; red when full
			int used = ClientRaceState.extra.getInt("capUsed");
			int max = ClientRaceState.extra.getInt("capMax");
			String cap = used + "/" + max;
			ctx.drawString(tr, cap, barX - 20 - tr.width(cap), h - 15, used >= max ? LegacyUi.RED : LegacyUi.GOLD, true);
		}
		if (race == Race.MASKED) {
			// How fresh the worn face still is, over the essence icon; red when it's about to crumble, a dash when faceless
			boolean faceless = ClientRaceState.extra.getString("face").isEmpty();
			int fresh = ClientRaceState.extra.getInt("fresh");
			String text = faceless ? "—" : fresh + "%";
			ctx.drawString(tr, text, barX - 20 - tr.width(text), h - 15, faceless || fresh <= 15 ? LegacyUi.RED : 0xFFE8DCC0, true);
		}

		if (!hasAbilities(race)) {
			return;
		}
		int x = w / 2 + 91 + 8;
		int place = 0;
		for (int slot = 0; slot < AbilitySlots.KEYS; slot++) {
			if (!shown(race, slot)) {
				continue;
			}
			int sx = x + place++ * 23;
			int sy = h - 22;
			int cooldown = cooldown(race, slot);
			boolean chain = chain(race, slot);
			boolean cooling = cooldown > 0 && !chain;
			int cost = slotCost(race, slot);
			boolean short_ = cost < 0 || ClientRaceState.resource < cost;
			boolean locked = locked(race, slot);
			boolean ready = !cooling && !short_ && !locked;
			ctx.fill(sx - 2, sy - 2, sx + 20, sy + 20, LegacyUi.BLACK);
			LegacyUi.outline(ctx, sx - 1, sy - 1, sx + 19, sy + 19, ready ? LegacyUi.mix(LegacyUi.RIM, color, 0.5f) : LegacyUi.RIM_DARK);
			ctx.fill(sx, sy, sx + 18, sy + 18, 0xD0141016);
			drawAbility(ctx, race, slot, sx + 1, sy + 1, ready);
			if (race == Race.SHEPHERD && slot == 2) {
				// The Whistle's order in the corner: back to heel, guard, hunt
				String order = STANCE_MARKS[Math.floorMod(ClientRaceState.extra.getInt("stance"), 3)];
				ctx.pose().pushPose();
				ctx.pose().translate(0, 0, 250);
				ctx.drawString(tr, order, sx, sy - 1, LegacyUi.GOLD, true);
				ctx.pose().popPose();
			}
			if (releases(race, slot)) {
				// Someone is held in the pocket: the slot glows, a mark says the key brings them back
				LegacyUi.outline(ctx, sx - 2, sy - 2, sx + 20, sy + 20, 0xFFB89BFF);
				ctx.pose().pushPose();
				ctx.pose().translate(0, 0, 250);
				ctx.drawString(tr, "↩", sx, sy - 1, 0xFFD8C8FF, true);
				ctx.pose().popPose();
			}
			if (race == Race.SHEPHERD && slot == 0) {
				// The active squad's numeral in the corner
				String numeral = Shepherd.ROMAN[Math.floorMod(ClientRaceState.extra.getInt("activePreset"), 3)];
				ctx.pose().pushPose();
				ctx.pose().translate(0, 0, 250);
				ctx.drawString(tr, numeral, sx, sy - 1, 0xFF9FE8E0, true);
				ctx.pose().popPose();
			}
			ctx.pose().pushPose();
			ctx.pose().translate(0, 0, 200);
			pressFlash(ctx, slot, sx, sy, color);
			if (locked) {
				// Not open yet: dark, with the race level it opens at
				ctx.fill(sx, sy, sx + 18, sy + 18, 0xC0000000);
				String lvl = String.valueOf(AbilitySlots.unlockLevel(race, slot));
				ctx.drawString(tr, lvl, sx + 9 - tr.width(lvl) / 2, sy + 5, 0xFF9A9088, true);
			} else if (cooling) {
				ctx.fill(sx, sy, sx + 18, sy + 18, 0x90000000);
				String secs = String.valueOf((cooldown + 19) / 20);
				ctx.drawString(tr, secs, sx + 9 - tr.width(secs) / 2, sy + 5, 0xFFFFFFFF, true);
			} else if (short_) {
				ctx.fill(sx, sy, sx + 18, sy + 18, 0x50A01818);
			}
			String key = MagesLegacyClient.keyLabel(MagesLegacyClient.ABILITY_KEYS[slot]);
			LegacyUi.keyCap(ctx, key, sx + 21, sy + 22, ready ? 0xFFFFFFFF : 0xFF9A9088);
			if (chain) {
				ctx.drawString(tr, "x" + ClientRaceState.chainLeft, sx + 1, sy + 1, LegacyUi.mix(color, 0xFFFFFFFF, 0.4f), true);
			}
			ctx.pose().popPose();
		}
	}
}
