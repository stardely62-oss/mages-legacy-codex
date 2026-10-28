package euf.mageslegacy.mixin;

import euf.mageslegacy.bloodkin.Bloodkin;
import euf.mageslegacy.frozen.Frozen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Natural healing per race: the Frozen heal half as fast (stuck in a moment), thirsty Bloodkin not at all. */
@Mixin(FoodData.class)
public abstract class HungerManagerMixin {
	@Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;heal(F)V"))
	private void mages_legacy$slowHealing(Player player, float amount) {
		player.heal(amount * Frozen.naturalHealing(player) * Bloodkin.naturalHealing(player));
	}
}
