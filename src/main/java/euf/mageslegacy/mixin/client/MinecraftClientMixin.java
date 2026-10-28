package euf.mageslegacy.mixin.client;

import euf.mageslegacy.client.HeartbeatSense;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A Bloodkin sees the hearts it hears: those creatures get an outline drawn through walls. */
@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void mages_legacy$heartbeats(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (HeartbeatSense.hears(entity)) {
			cir.setReturnValue(true);
		}
	}
}
