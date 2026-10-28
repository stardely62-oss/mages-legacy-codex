package euf.mageslegacy.race;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import euf.mageslegacy.bloodkin.Bloodkin;
import euf.mageslegacy.frozen.Frozen;
import euf.mageslegacy.mycelid.Mycelid;
import euf.mageslegacy.net.RacePackets;
import euf.mageslegacy.shepherd.Shepherd;
import euf.mageslegacy.mercenary.Mercenary;
import euf.mageslegacy.masked.Masked;
import euf.mageslegacy.tempter.Tempter;
import euf.mageslegacy.voidborn.Pocket;
import euf.mageslegacy.voidborn.Voidborn;
import java.util.Arrays;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** /race shows your race; operators can set or reset races and hand out experience and resource. */
public final class RaceCommand {
	private RaceCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("race")
				.executes(RaceCommand::show)
				.then(Commands.literal("set").requires(s -> s.hasPermission(2))
						.then(Commands.argument("targets", EntityArgument.players())
								.then(Commands.argument("race", StringArgumentType.word())
										.suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Race.values()).map(Race::id), b))
										.executes(RaceCommand::set))))
				.then(Commands.literal("reset").requires(s -> s.hasPermission(2))
						.then(Commands.argument("targets", EntityArgument.players()).executes(RaceCommand::reset)))
				.then(Commands.literal("xp").requires(s -> s.hasPermission(2))
						.then(Commands.argument("targets", EntityArgument.players())
								.then(Commands.argument("amount", IntegerArgumentType.integer(0))
										.executes(c -> each(c, (p, d) -> Progression.addXp(p, d, IntegerArgumentType.getInteger(c, "amount")))))))
				.then(Commands.literal("resource").requires(s -> s.hasPermission(2))
						.then(Commands.argument("targets", EntityArgument.players())
								.then(Commands.argument("amount", IntegerArgumentType.integer(0, 100))
										.executes(c -> each(c, (p, d) -> d.addResource(IntegerArgumentType.getInteger(c, "amount") - d.resource)))))));
	}

	private interface Action {
		void apply(ServerPlayer player, RaceData data);
	}

	private static int each(CommandContext<CommandSourceStack> c, Action action) throws CommandSyntaxException {
		Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "targets");
		for (ServerPlayer player : players) {
			RaceData data = RaceState.of(player);
			action.apply(player, data);
			RacePackets.sync(player, data);
		}
		return players.size();
	}

	private static int show(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		RaceData data = RaceState.of(player);
		if (data.race == null) {
			c.getSource().sendSuccess(() -> Component.translatable("mages_legacy.command.no_race"), false);
		} else {
			c.getSource().sendSuccess(() -> Component.translatable("mages_legacy.command.show", Component.translatable(data.race.key()),
					data.level, data.freePoints()), false);
		}
		return 1;
	}

	private static int set(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		Race race = Race.byId(StringArgumentType.getString(c, "race")).orElse(null);
		if (race == null) {
			c.getSource().sendFailure(Component.translatable("mages_legacy.command.unknown_race"));
			return 0;
		}
		return each(c, (p, d) -> {
			cleanup(p, d);
			d.reset(race);
		});
	}

	private static int reset(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		return each(c, (p, d) -> {
			cleanup(p, d);
			d.reset(null);
		});
	}

	/** What the old race left on the player: Mycelid armour and health bonuses, a Shepherd's echoes and beasts, a Voidborn in its pocket. */
	private static void cleanup(ServerPlayer player, RaceData data) {
		Mycelid.removeCapArmor(player);
		if (data.race == Race.SHEPHERD) {
			Shepherd.cleanup(player, data);
		}
		if (data.race == Race.FROZEN) {
			Frozen.cleanup(player);
		}
		if (data.race == Race.BLOODKIN) {
			Bloodkin.cleanup(player);
		}
		if (data.race == Race.MERCENARY) {
			Mercenary.cleanup(player);
		}
		if (data.race == Race.TEMPTER) {
			Tempter.cleanup(player);
		}
		if (data.race == Race.MASKED) {
			Masked.cleanup(player);
		}
		if (data.race == Race.VOIDBORN) {
			Voidborn.cleanup(player);
			// Without the race there is no key to come back out: step out of the pocket now
			if (Pocket.in(player)) {
				Pocket.leave(player, data, false);
			}
		}
	}
}
