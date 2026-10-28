package euf.mageslegacy.client;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
/** Render data from the corresponding native NeoForge render stage. */
public record RenderContext(PoseStack matrixStack, Camera camera, MultiBufferSource consumers, float tickDelta) {}
