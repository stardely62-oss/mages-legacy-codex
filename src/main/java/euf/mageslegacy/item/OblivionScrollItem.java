package euf.mageslegacy.item;

import euf.mageslegacy.fx.Fx;
import euf.mageslegacy.mycelid.Mycelid;
import euf.mageslegacy.net.RacePackets;
import euf.mageslegacy.race.RaceData;
import euf.mageslegacy.race.RaceState;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The Scroll of Oblivion: read, it gives back to the nameless mage everything the race has learned, and every talent
 * point returns to be spent anew. The race, its level and its experience stay.
 */
public class OblivionScrollItem extends Item {
	private static final int GOLD_RGB = 0xE8C27A;

	public OblivionScrollItem(Properties settings) {
		super(settings);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
		ItemStack stack = user.getItemInHand(hand);
		if (!(user instanceof ServerPlayer player)) {
			return InteractionResultHolder.success(stack);
		}
		RaceData data = RaceState.of(player);
		if (data == null || data.race == null || data.talents.isEmpty()) {
			player.displayClientMessage(Component.translatable("mages_legacy.scroll.nothing"), true);
			return InteractionResultHolder.fail(stack);
		}
		int points = forget(player, data);
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}
		ServerLevel here = player.serverLevel();
		Fx.burst(here, player.position().add(0, 1, 0), 1.6f, 10, GOLD_RGB);
		here.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(), 60, 0.6, 0.8, 0.6, 0.6);
		here.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 0.7f);
		here.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 0.6f);
		player.displayClientMessage(Component.translatable("mages_legacy.scroll.done", points).withStyle(ChatFormatting.GOLD), false);
		return InteractionResultHolder.success(stack);
	}

	/** Forgets every talent the race has learned; returns how many points came back. */
	public static int forget(ServerPlayer player, RaceData data) {
		int points = data.spentPoints();
		data.talents.clear();
		Mycelid.removeCapArmor(player);
		data.dirty = true;
		RacePackets.sync(player, data);
		return points;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext tooltipContext, List<Component> tooltip, TooltipFlag context) {
		tooltip.add(Component.translatable("mages_legacy.scroll.lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		tooltip.add(Component.translatable("mages_legacy.scroll.desc").withStyle(ChatFormatting.GOLD));
	}
}
