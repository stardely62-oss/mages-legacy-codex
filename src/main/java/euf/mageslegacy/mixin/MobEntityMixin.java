package euf.mageslegacy.mixin;

import euf.mageslegacy.masked.Masked;
import euf.mageslegacy.shepherd.Echoes;
import euf.mageslegacy.tempter.Tempter;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Echoes are spirits: zombie and skeleton echoes don't catch fire in the sun. A Masked's own kind doesn't take it for
 * prey, and a Masked's retinue never turns on a player; a Tempter's
 * sleepers and thralls touch no one, and the infatuated spare her.
 */
@Mixin(Mob.class)
public abstract class MobEntityMixin {
	@Inject(method = "isAffectedByDaylight", at = @At("HEAD"), cancellable = true)
	private void mages_legacy$echoesIgnoreTheSun(CallbackInfoReturnable<Boolean> cir) {
		if (Echoes.isEcho((Mob) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void mages_legacy$kin(LivingEntity target, CallbackInfo ci) {
		Mob self = (Mob) (Object) this;
		if (target instanceof Player player && !self.level().isClientSide() && (Masked.inRetinue(self) || Masked.hidesFrom(self, player) || Tempter.blocksTarget(self, player))) {
			ci.cancel();
		}
	}
}
