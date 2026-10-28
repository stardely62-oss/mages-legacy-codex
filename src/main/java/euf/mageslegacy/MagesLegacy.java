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

@net.neoforged.fml.common.Mod(MagesLegacy.MOD_ID)
public class MagesLegacy {
    public static final String MOD_ID = "mages_legacy";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final net.neoforged.neoforge.registries.DeferredRegister.Blocks BLOCKS = net.neoforged.neoforge.registries.DeferredRegister.createBlocks(MOD_ID);
    private static final net.neoforged.neoforge.registries.DeferredRegister.Items ITEMS = net.neoforged.neoforge.registries.DeferredRegister.createItems(MOD_ID);
    public static final java.util.function.Supplier<MyceliumNodeBlock> MYCELIUM_NODE = BLOCKS.register("mycelium_node", () -> new MyceliumNodeBlock(
        net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(Blocks.RED_MUSHROOM_BLOCK).noOcclusion().noCollission()
            .strength(0.5f).lightLevel(state -> 7).sound(net.minecraft.world.level.block.SoundType.FUNGUS).pushReaction(PushReaction.DESTROY)));
    public static final java.util.function.Supplier<Item> OBLIVION_SCROLL = ITEMS.register("oblivion_scroll", () -> new OblivionScrollItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MOD_ID, path); }
    public MagesLegacy(net.neoforged.bus.api.IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); ModParticles.REGISTER.register(bus);
        Talents.init(); RacePackets.registerServerReceivers();
        bus.addListener((net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) -> euf.mageslegacy.platform.Network.register(event, MOD_ID,
            "sync", "open_choice", "echoes", "faces", "quickened", "fx"));
        bus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(OBLIVION_SCROLL.get());
        });
        var events = net.neoforged.neoforge.common.NeoForge.EVENT_BUS;
        events.addListener((net.neoforged.neoforge.event.RegisterCommandsEvent event) -> RaceCommand.register(event.getDispatcher()));
        events.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) -> tick(event.getServer()));
        events.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Pre event) -> Voidborn.tickSleep(event.getServer()));
        events.addListener((net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent event) -> {
            if (Voidborn.mayNotSleep(event.getEntity())) event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
        });
        events.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent event) -> {
            Mycelid.reset(); Frozen.reset(); Bloodkin.reset(); Voidborn.reset(); Mercenary.reset(); Tempter.reset(); Masked.reset(); Echoes.reset(); Creatures.reset();
        });
        events.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) -> {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            RaceData data = RaceState.of(player);
            if (data != null) { data.prompted = false; data.dirty = true; }
            Voidborn.onJoin(player);
        });
        events.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) -> {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            RaceData data = RaceState.of(player);
            if (data != null && !event.isEndConquered()) { data.tastes.clear(); RacePackets.sync(player, data); }
        });
        events.addListener((net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem event) -> {
            Player player = event.getEntity();
            ItemStack stack = player.getItemInHand(event.getHand());
            if (!Mycelid.DIET.containsKey(stack.getItem())) return;
            if (player.level().isClientSide()) {
                if (ClientRaceView.isMycelid(player)) { event.setCanceled(true); event.setCancellationResult(InteractionResult.SUCCESS); }
                return;
            }
            RaceData data = RaceState.of(player);
            if (data == null || data.race != Race.MYCELID) return;
            event.setCanceled(true);
            event.setCancellationResult(Mycelid.eat((ServerPlayer) player, data, stack) ? InteractionResult.SUCCESS : InteractionResult.FAIL);
        });
        events.addListener((net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) -> {
            var entity = event.getEntity();
            if (entity.level().isClientSide()) return;
            boolean allow = entity instanceof ServerPlayer player
                ? Mycelid.allowDeath(player) && Frozen.allowDeath(player) && Bloodkin.allowDeath(player) && Voidborn.allowDeath(player)
                    && Mercenary.allowDeath(player) && Tempter.allowDeath(player) && Masked.allowDeath(player)
                : Echoes.allowDeath(entity) && Bonds.allowDeath(entity);
            if (!allow) { event.setCanceled(true); return; }
            Mycelid.onDeath(entity); Frozen.onDeath(entity); Tempter.onDeath(entity, event.getSource()); Masked.onDeath(entity, event.getSource());
            if (event.getSource().getEntity() instanceof net.minecraft.world.entity.LivingEntity killer) {
                if (killer instanceof ServerPlayer player) {
                    Mycelid.onKill(player, entity); Bloodkin.onKill(player, entity); Voidborn.onKill(player, entity);
                    Mercenary.onKill(player, entity); Progression.onKill(player, entity);
                }
                Shepherd.onKill(killer, entity);
            }
        });
        events.addListener((net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract event) -> {
            if (!(event.getEntity() instanceof ServerPlayer player) || !Shepherd.is(player)) return;
            InteractionResult result = Bonds.interact(player, RaceState.of(player), event.getTarget(), event.getHand());
            if (result != InteractionResult.PASS) { event.setCanceled(true); event.setCancellationResult(result); }
        });
        events.addListener((net.neoforged.neoforge.event.entity.player.AttackEntityEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player && Shepherd.is(player) && Bonds.headbutt(player, RaceState.of(player), event.getTarget())) event.setCanceled(true);
        });
        events.addListener((net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) -> {
            if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel world)) return;
            var entity = event.getEntity(); Echoes.onLoad(entity);
            if (entity.getTags().contains(Creatures.BOND_TAG)) {
                UUID owner = Creatures.taggedOwner(entity);
                if (owner != null) Bonds.onLoad(entity, RaceState.get(world.getServer()).data(owner), owner);
            }
        });
        LOGGER.info("Mage's Legacy loaded on NeoForge 1.21.1");
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
			euf.mageslegacy.platform.Absorption.tick(player);
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
