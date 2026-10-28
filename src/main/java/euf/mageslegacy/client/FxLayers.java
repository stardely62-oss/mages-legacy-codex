package euf.mageslegacy.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/**
 * Our own render layer: plain see-through colour, the "ink" under the glow. Light added on top of a bright day only
 * turns red into pink, and a dark colour added is nothing at all; blood needs real colour laid over the world, and
 * the glow after it. It writes no depth, so it never hides what is drawn later, and shows both faces.
 */
public final class FxLayers extends RenderType {
	public static final RenderType INK = create("mages_legacy_ink", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1 << 16, false, true,
			CompositeState.builder()
					.setShaderState(POSITION_COLOR_SHADER)
					.setTransparencyState(TRANSLUCENT_TRANSPARENCY)
					.setCullState(NO_CULL)
					.setWriteMaskState(COLOR_WRITE)
					.createCompositeState(false));

	/**
	 * Solid shapes with a body (a heart with thickness, a wing's bones and skin): real colour that writes depth, so
	 * their own faces hide one another properly; both faces shown.
	 */
	public static final RenderType SOLID = create("mages_legacy_solid", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1 << 16, false, true,
			CompositeState.builder()
					.setShaderState(POSITION_COLOR_SHADER)
					.setTransparencyState(TRANSLUCENT_TRANSPARENCY)
					.setCullState(NO_CULL)
					.setWriteMaskState(COLOR_DEPTH_WRITE)
					.createCompositeState(false));

	private FxLayers(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean translucent, Runnable start,
			Runnable end) {
		super(name, format, mode, size, crumbling, translucent, start, end);
	}
}
