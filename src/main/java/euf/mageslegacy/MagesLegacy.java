package euf.mageslegacy;

import euf.mageslegacy.block.MyceliumNodeBlock;
import euf.mageslegacy.bloodkin.Bloodkin;
import euf.mageslegacy.frozen.Frozen;
import euf.mageslegacy.fx.ModParticles;
import euf.mageslegacy.item.OblivionScrollItem;
import euf.mageslegacy.masked.Masked;
import euf.mageslegacy.mercenary.Mercenary;
import euf.mageslegacy.tempter.Tempter;
import euf.mageslegacy.mycelid.Mycelid;
import euf.mageslegacy.net.RacePackets;
import euf.mageslegacy.race.AbilitySlots;
import euf.mageslegacy.race.ClientRaceView;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceCommand;
import euf.mageslegacy.race.RaceData;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.race.Talents;
import euf.mageslegacy.shepherd.Bonds;
import euf.mageslegacy.shepherd.Creatures;
import euf.mageslegacy.shepherd.Echoes;
import euf.mageslegacy.shepherd.Shepherd;
import euf.mageslegacy.voidborn.Voidborn;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.PushReaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MagesLegacy implements ModInitializer {
	public static final String MOD_ID = "mages_legacy";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Grown by the Mycelids' third ability; drops nothing and has no item. */
	public static final Block MYCELIUM_NODE = Registry.register(BuiltInRegistries.BLOCK, id("mycelium_node"),
			new MyceliumNodeBlock(FabricBlockSettings.copyOf(Blocks.RED_MUSHROOM_BLOCK).noOcclusion().noCollission()
					.strength(0.5f).lightLevel(state -> 7).sound(SoundType.FUNGUS).pushReaction(PushReaction.DESTROY)));

	/** The Scroll of Oblivion: gives back every talent point (see {@link OblivionScrollItem}). */
	public static final Item OBLIVION_SCROLL = Registry.register(BuiltInRegistries.ITEM, id("oblivion_scroll"),
			new OblivionScrollItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		Talents.init();
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> entries.accept(OBLIVION_SCROLL));
		ModParticles.register();
		RacePackets.registerServerReceivers();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> RaceCommand.register(dispatcher));
		ServerTickEvents.END_SERVER_TICK.register(MagesLegacy::tick);
		// Before the worlds tick: a Voidborn rested in the pocket brings the morning (see Voidborn.tickSleep)
		ServerTickEvents.START_SERVER_TICK.register(Voidborn::tickSleep);
		EntitySleepEvents.ALLOW_SLEEPING.register((player, pos) -> player instanceof ServerPlayer sp && Voidborn.mayNotSleep(sp)
				? Player.BedSleepingProblem.OTHER_PROBLEM : null);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			Mycelid.reset();
			Frozen.reset();
			Bloodkin.reset();
			Voidborn.reset();
			Mercenary.reset();
			Tempter.reset();
			Masked.reset();
			Echoes.reset();
			Creatures.reset();
		});

		// A fresh session: send everything again, and the legend with the choice of race if there is no race yet
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
			RaceData data = RaceState.of(handler.player);
			if (data != null) {
				data.prompted = false;
				data.dirty = true;
			}
			Voidborn.onJoin(handler.player);
		}));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			RaceData data = RaceState.of(newPlayer);
			if (data != null && !alive) {
				data.tastes.clear();
				RacePackets.sync(newPlayer, data);
			}
		});
		// Mycelids eat raw mushrooms and fungi (right click into the air)
		UseItemCallback.EVENT.register((player, world, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (!Mycelid.DIET.containsKey(stack.getItem())) {
				return InteractionResultHolder.pass(stack);
			}
			if (world.isClientSide) {
				return ClientRaceView.isMycelid(player) ? InteractionResultHolder.success(stack) : InteractionResultHolder.pass(stack);
			}
			RaceData data = RaceState.of(player);
			if (data == null || data.race != Race.MYCELID) {
				return InteractionResultHolder.pass(stack);
			}
			return Mycelid.eat((ServerPlayer) player, data, stack) ? InteractionResultHolder.success(stack) : InteractionResultHolder.fail(stack);
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			Mycelid.onDeath(entity);
			Frozen.onDeath(entity);
			Tempter.onDeath(entity, source);
			Masked.onDeath(entity, source);
		});
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player) {
				return Mycelid.allowDeath(player) && Frozen.allowDeath(player) && Bloodkin.allowDeath(player) && Voidborn.allowDeath(player)
						&& Mercenary.allowDeath(player) && Tempter.allowDeath(player) && Masked.allowDeath(player);
			}
			// Echoes fade and bonded beasts go to rest instead of dying
			return Echoes.allowDeath(entity) && Bonds.allowDeath(entity);
		});
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, killer, victim) -> {
			if (killer instanceof ServerPlayer player) {
				Mycelid.onKill(player, victim);
				Bloodkin.onKill(player, victim);
				Voidborn.onKill(player, victim);
				Mercenary.onKill(player, victim);
				Progression.onKill(player, victim);
			}
			Shepherd.onKill(killer, victim);
		});
		// Shepherds: the Song of Bonds and the feeding of the ritual
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || !Shepherd.is(serverPlayer)) {
				return InteractionResult.PASS;
			}
			return Bonds.interact(serverPlayer, RaceState.of(serverPlayer), entity, hand);
		});
		// The goat's way: bare-handed headbutts at a listening goat count and do no harm
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!world.isClientSide() && player instanceof ServerPlayer serverPlayer && Shepherd.is(serverPlayer)
					&& Bonds.headbutt(serverPlayer, RaceState.of(serverPlayer), entity)) {
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
		// Echoes left in saved chunks go; bonded beasts get their owner and goals back
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			Echoes.onLoad(entity);
			if (entity.getTags().contains(Creatures.BOND_TAG)) {
				UUID owner = Creatures.taggedOwner(entity);
				if (owner != null) {
					Bonds.onLoad(entity, RaceState.get(world.getServer()).data(owner), owner);
				}
			}
		});
		LOGGER.info("Mage's Legacy loaded");
	}

	private static void tick(MinecraftServer server) {
		Mycelid.tick(server);
		Frozen.tick(server);
		Bloodkin.tick(server);
		Voidborn.tick(server);
		Mercenary.tick(server);
		Tempter.tick(server);
		Masked.tick(server);
		int ticks = server.getTickCount();
		if (ticks % 20 == 0) {
			Echoes.tickSecond(server);
		}
		if (ticks % 10 != 0) {
			return;
		}
		RaceState state = RaceState.get(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			RaceData data = state.data(player.getUUID());
			if (ticks % 20 == 0 && player.isAlive() && !player.isSpectator()) {
				if (data.race == Race.MYCELID) {
					Mycelid.tickSecond(player, data);
				} else if (data.race == Race.SHEPHERD) {
					Shepherd.tickSecond(player, data);
				} else if (data.race == Race.FROZEN) {
					Frozen.tickSecond(player, data);
				} else if (data.race == Race.BLOODKIN) {
					Bloodkin.tickSecond(player, data);
				} else if (data.race == Race.VOIDBORN) {
					Voidborn.tickSecond(player, data);
				} else if (data.race == Race.MERCENARY) {
					Mercenary.tickSecond(player, data);
				} else if (data.race == Race.TEMPTER) {
					Tempter.tickSecond(player, data);
				} else if (data.race == Race.MASKED) {
					Masked.tickSecond(player, data);
				}
			}
			// The client registers our channels a moment after joining, so the prompt waits for the tick
			if (data.race == null && !data.prompted) {
				data.prompted = RacePackets.openChoice(player);
			}
			// Only the ability slots show on the client; talent timers (Rebirth, Fed Network) don't need a sync every tick
			boolean coolingDown = data.readyAt.entrySet().stream().anyMatch(e -> AbilitySlots.onKey(data.race, e.getKey()) && e.getValue() > ticks)
					|| data.chainLeft > 0;
			if (data.dirty || coolingDown) {
				RacePackets.sync(player, data);
			}
		}
	}
}
