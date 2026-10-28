package euf.mageslegacy.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import euf.mageslegacy.client.FxRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The camera's share of an ability's weight: it shakes on a blow and its view rushes wider on a dash. */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
	/** Where a hurt tilts the view, the abilities shake it. */
	@Inject(method = "bobHurt", at = @At("HEAD"))
	private void mages_legacy$shake(PoseStack matrices, float tickDelta, CallbackInfo ci) {
		FxRenderer.shake(matrices, tickDelta);
	}

	/** The world's field of view (not the hand's) widens for a rush. */
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void mages_legacy$rush(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
		if (changingFov) {
			cir.setReturnValue(cir.getReturnValue() * FxRenderer.fovScale(tickDelta));
		}
	}
}
