package euf.mageslegacy.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import euf.mageslegacy.client.FaceVisuals;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A Masked wearing a face is drawn as that face's monster instead of itself. */
@Mixin(PlayerRenderer.class)
public abstract class PlayerEntityRendererMixin {
	@Inject(method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At("HEAD"), cancellable = true)
	private void mages_legacy$face(AbstractClientPlayer player, float yaw, float tickDelta, PoseStack matrices, MultiBufferSource vertices,
			int light, CallbackInfo ci) {
		if (FaceVisuals.render(player, yaw, tickDelta, matrices, vertices, light)) {
			ci.cancel();
		}
	}
}
