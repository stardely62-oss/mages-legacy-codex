package euf.mageslegacy.mixin.client;

import euf.mageslegacy.voidborn.Voidborn;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** During a Void Step this client's player passes through blocks: it is not pushed out of them. */
@Mixin(LocalPlayer.class)
public class ClientPlayerEntityMixin {
	@Inject(method = "tickMovement", at = @At("HEAD"))
	private void mages_legacy$phase(CallbackInfo ci) {
		LocalPlayer self = (LocalPlayer) (Object) this;
		if (Voidborn.phasing(self)) {
			self.noPhysics = true;
		}
	}
}
