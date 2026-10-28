package euf.mageslegacy.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import euf.mageslegacy.client.EchoVisuals;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Echoes are drawn as spirits: see-through and tinted pale cyan. Vanilla already draws an entity see-through when it
 * is "not visible but not invisible to you" (an invisible teammate); echoes take that path, with their own colour.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	private static boolean mages_legacy$echo;

	@Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At("HEAD"))
	private void mages_legacy$remember(LivingEntity entity, float yaw, float delta, PoseStack matrices, MultiBufferSource vertices, int light,
			CallbackInfo ci) {
		mages_legacy$echo = EchoVisuals.isEcho(entity);
	}

	@Inject(method = "isVisible", at = @At("HEAD"), cancellable = true)
	private void mages_legacy$spirit(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
		if (EchoVisuals.isEcho(entity)) {
			cir.setReturnValue(false);
		}
	}

	@ModifyArg(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V"),
			index = 4)
	private float mages_legacy$red(float red) {
		return mages_legacy$echo ? 0.62f : red;
	}

	@ModifyArg(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V"),
			index = 7)
	private float mages_legacy$alpha(float alpha) {
		return mages_legacy$echo ? 0.6f : alpha;
	}
}
