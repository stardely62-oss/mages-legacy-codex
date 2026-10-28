package euf.mageslegacy.mixin.client;

import euf.mageslegacy.client.FxRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A Rewind carries the player along its path in server steps. Vanilla snaps the client to each step (it sets the
 * previous position to the new one), which looks like a string of jumps; during such a flight the previous position
 * is kept, so the frames in between glide from step to step.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPlayNetworkHandlerMixin {
	@Unique
	private Vec3 mages_legacy$before;

	@Inject(method = "onPlayerPositionLook", at = @At("HEAD"))
	private void mages_legacy$rememberStep(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		// The handler first runs on the network thread only to hop over to the main one
		mages_legacy$before = client.isSameThread() && client.player != null && FxRenderer.inFlight() ? client.player.position() : null;
	}

	@Inject(method = "onPlayerPositionLook", at = @At("TAIL"))
	private void mages_legacy$glide(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
		Player player = Minecraft.getInstance().player;
		if (mages_legacy$before == null || player == null) {
			return;
		}
		player.xo = mages_legacy$before.x;
		player.yo = mages_legacy$before.y;
		player.zo = mages_legacy$before.z;
		player.xOld = mages_legacy$before.x;
		player.yOld = mages_legacy$before.y;
		player.zOld = mages_legacy$before.z;
		mages_legacy$before = null;
	}
}
