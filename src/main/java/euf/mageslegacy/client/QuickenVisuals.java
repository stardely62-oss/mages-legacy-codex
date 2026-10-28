package euf.mageslegacy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The blocks a Frozen quickened, as the server told this client: each shows a small hourglass with the seconds left
 * above it, like a name tag, to anyone close by.
 */
public final class QuickenVisuals {
	private static final int GOLD = 0xFFE8C98A;
	private static final double SHOW_WITHIN = 24;
	/** Block -> the client tick its quickening ends. */
	private static final Map<BlockPos, Long> UNTIL = new HashMap<>();

	private QuickenVisuals() {
	}

	public static void read(FriendlyByteBuf buf) {
		long ticks = ClientRaceState.clientTicks();
		UNTIL.clear();
		int count = buf.readVarInt();
		for (int i = 0; i < count; i++) {
			BlockPos pos = buf.readBlockPos();
			UNTIL.put(pos, ticks + buf.readVarInt());
		}
	}

	public static void clear() {
		UNTIL.clear();
	}

	public static void render(RenderContext context) {
		if (UNTIL.isEmpty() || context.consumers() == null) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		long now = ClientRaceState.clientTicks();
		UNTIL.values().removeIf(until -> until <= now);
		Camera camera = context.camera();
		Vec3 cam = camera.getPosition();
		PoseStack matrices = context.matrixStack();
		MultiBufferSource consumers = context.consumers();
		Font font = client.font;
		for (Map.Entry<BlockPos, Long> e : UNTIL.entrySet()) {
			Vec3 at = Vec3.atCenterOf(e.getKey()).add(0, 0.95, 0);
			if (at.distanceToSqr(cam) > SHOW_WITHIN * SHOW_WITHIN) {
				continue;
			}
			long seconds = (e.getValue() - now + 19) / 20;
			String label = "⌛ " + seconds / 60 + ":" + String.format("%02d", seconds % 60);
			matrices.pushPose();
			matrices.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
			matrices.mulPose(camera.rotation());
			matrices.scale(-0.025f, -0.025f, 0.025f);
			Matrix4f matrix = matrices.last().pose();
			float x = -font.width(label) / 2f;
			font.drawInBatch(label, x, 0, GOLD, false, matrix, consumers, Font.DisplayMode.NORMAL, 0x60000000,
					LightTexture.FULL_BRIGHT);
			matrices.popPose();
		}
	}
}
