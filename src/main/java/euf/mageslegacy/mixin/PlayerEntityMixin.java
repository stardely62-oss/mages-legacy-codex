package euf.mageslegacy.mixin;

import euf.mageslegacy.bloodkin.Bloodkin;
import euf.mageslegacy.mycelid.Mycelid;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.RaceData;
import euf.mageslegacy.race.RaceState;
import euf.mageslegacy.voidborn.Voidborn;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets races react to the food a player eats (Mycelids: stews feed spores, meat feeds half), and Voidborn pass through walls. */
@Mixin(Player.class)
public abstract class PlayerEntityMixin {
	@Unique
	private Item mages_legacy$eating;

	/** A Voidborn in the middle of a Void Step moves through solid blocks. */
	@Inject(method = "tickMovement", at = @At("HEAD"))
	private void mages_legacy$phase(CallbackInfo ci) {
		Player self = (Player) (Object) this;
		if (Voidborn.phasing(self)) {
			self.noPhysics = true;
		}
	}

	@Inject(method = "eatFood", at = @At("HEAD"))
	private void mages_legacy$beforeEat(Level world, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
		// Remember the item now: eating shrinks the stack, and a stew turns into a bowl
		mages_legacy$eating = stack.isEdible() ? stack.getItem() : null;
	}

	@Inject(method = "eatFood", at = @At("RETURN"))
	private void mages_legacy$afterEat(Level world, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
		Item item = mages_legacy$eating;
		mages_legacy$eating = null;
		if (item == null || !((Object) this instanceof ServerPlayer player)) {
			return;
		}
		RaceData data = RaceState.of(player);
		FoodProperties food = item.getFoodProperties();
		if (data != null && data.race == Race.MYCELID && food != null) {
			Mycelid.afterEating(player, data, item, food);
		}
		if (data != null && data.race == Race.BLOODKIN && food != null) {
			Bloodkin.afterEating(player, food);
		}
	}
}
