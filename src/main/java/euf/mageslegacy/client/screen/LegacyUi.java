package euf.mageslegacy.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import euf.mageslegacy.MagesLegacy;
import euf.mageslegacy.race.Race;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The look of the Mage's Legacy screens: dark carved stone with an antique gold rim, the race's own colour as the
 * accent, embers drifting up in the background, the Codex-drawn emblems and icons. Everything else is drawn with
 * fills, so it scales with the GUI.
 */
public final class LegacyUi {
	public static final int STONE = 0xFF120E14;
	public static final int STONE_LIGHT = 0xFF2C2430;
	public static final int RIM = 0xFF6B5636;
	public static final int RIM_DARK = 0xFF3A2E22;
	public static final int GOLD = 0xFFE8C060;
	public static final int PARCHMENT = 0xFFE2D6C0;
	public static final int TEXT = 0xFFEDE6DA;
	public static final int MUTED = 0xFF9A9088;
	public static final int GREEN = 0xFF9CE08A;
	public static final int RED = 0xFFE07A7A;
	public static final int BLACK = 0xFF050406;

	/** The five race colours, for the legend's embers. */
	private static final int[] RACE_COLORS = {0x7FB069, 0xD9B26F, 0x8FB8DE, 0xB23A48, 0x9B7FD4, 0xC8823C, 0xD46A8E, 0xBFB6A3};

	private static final Map<ResourceLocation, Boolean> PRESENT = new HashMap<>();

	private LegacyUi() {
	}

	public static ResourceLocation gui(String path) {
		return ResourceLocation.fromNamespaceAndPath(MagesLegacy.MOD_ID, "textures/gui/" + path + ".png");
	}

	public static ResourceLocation emblem(Race race) {
		return gui("emblem/" + race.id());
	}

	public static ResourceLocation icon(String name) {
		return gui("icon/" + name);
	}

	/** Whether a texture ships with the mod, so screens can fall back to an item icon for art not drawn yet. */
	public static boolean exists(ResourceLocation texture) {
		return PRESENT.computeIfAbsent(texture, id -> Minecraft.getInstance().getResourceManager().getResource(id).isPresent());
	}

	public static int argb(int rgb) {
		return 0xFF000000 | rgb;
	}

	/** Seconds since start, for animations. */
	public static float time() {
		return (Util.getMillis() % 3_600_000L) / 1000f;
	}

	public static float hash(int i, int salt) {
		int h = i * 0x9E3779B1 + salt * 0x85EBCA77;
		h ^= h >>> 15;
		h *= 0x2C1B3C6D;
		h ^= h >>> 12;
		return (h & 0xFFFFFF) / (float) 0x1000000;
	}

	public static int alpha(int color, float alpha) {
		int a = (int) (((color >>> 24) & 0xFF) * Math.max(0, Math.min(1, alpha)));
		return (a << 24) | (color & 0xFFFFFF);
	}

	/** Mixes two colours, t = 0 gives a, 1 gives b. */
	public static int mix(int a, int b, float t) {
		int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
		int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
		int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		int al = (int) (((a >>> 24) & 0xFF) * (1 - t) + ((b >>> 24) & 0xFF) * t);
		return (al << 24) | (r << 16) | (g << 8) | bl;
	}

	/**
	 * The screen behind: a dark haze with embers drifting up. With a race they glow in its colour; without one (the
	 * legend, the choice) in all five.
	 */
	public static void background(GuiGraphics context, int width, int height, Race race) {
		context.fillGradient(0, 0, width, height, 0xE8100C12, 0xF4050406);
		float t = time();
		context.drawManaged(() -> {
			for (int i = 0; i < 54; i++) {
				float speed = 5 + hash(i, 1) * 12;
				int x = (int) (hash(i, 2) * width + Math.sin(t * 0.6 + i) * 6);
				int y = (int) (height + 10 - (t * speed + hash(i, 3) * (height + 20)) % (height + 20));
				float glow = 0.3f + 0.35f * (float) Math.sin(t * 1.8 + i * 1.7);
				int rgb = race != null ? race.color : RACE_COLORS[i % RACE_COLORS.length];
				int s = hash(i, 4) > 0.8f ? 2 : 1;
				context.fill(x, y, x + s, y + s, alpha(argb(rgb), glow));
			}
		});
	}

	/** A carved stone slab: black outline, dark body, an antique gold rim and a rivet in every corner. */
	public static void panel(GuiGraphics context, int x0, int y0, int x1, int y1) {
		panel(context, x0, y0, x1, y1, RIM);
	}

	public static void panel(GuiGraphics context, int x0, int y0, int x1, int y1, int rim) {
		context.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, BLACK);
		context.fillGradient(x0, y0, x1, y1, 0xF018131C, 0xF00C090E);
		outline(context, x0, y0, x1, y1, rim);
		outline(context, x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0x60000000);
		for (int[] c : new int[][] {{x0, y0}, {x1 - 1, y0}, {x0, y1 - 1}, {x1 - 1, y1 - 1}}) {
			diamond(context, c[0], c[1], 2, BLACK);
			diamond(context, c[0], c[1], 1, rim);
		}
	}

	public static void outline(GuiGraphics context, int x0, int y0, int x1, int y1, int color) {
		context.fill(x0, y0, x1, y0 + 1, color);
		context.fill(x0, y1 - 1, x1, y1, color);
		context.fill(x0, y0, x0 + 1, y1, color);
		context.fill(x1 - 1, y0, x1, y1, color);
	}

	public static void diamond(GuiGraphics context, int x, int y, int r, int color) {
		for (int dy = -r; dy <= r; dy++) {
			int half = r - Math.abs(dy);
			context.fill(x - half, y + dy, x + half + 1, y + dy + 1, color);
		}
	}

	/** A thin line fading out at both ends, with a gem in the middle. */
	public static void divider(GuiGraphics context, int x0, int x1, int y, int gem) {
		int mid = (x0 + x1) / 2;
		int half = (x1 - x0) / 2;
		int steps = 12;
		for (int i = 0; i < steps; i++) {
			int a = half * i / steps;
			int b = half * (i + 1) / steps;
			int color = alpha(RIM, (i + 1) / (float) steps);
			context.fill(x0 + a, y, x0 + b, y + 1, color);
			context.fill(x1 - b, y, x1 - a, y + 1, color);
		}
		diamond(context, mid, y, 3, BLACK);
		diamond(context, mid, y, 2, gem);
	}

	/** A heading between two short fading lines, in gold or the given colour. */
	public static void heading(GuiGraphics context, net.minecraft.network.chat.Component text, int cx, int y, int color) {
		var tr = Minecraft.getInstance().font;
		int tw = tr.width(text);
		context.drawString(tr, text, cx - tw / 2, y, color);
		int len = 28;
		for (int i = 0; i < len; i++) {
			int c = alpha(color, 0.8f * (1 - i / (float) len));
			context.fill(cx - tw / 2 - 6 - i, y + 4, cx - tw / 2 - 5 - i, y + 5, c);
			context.fill(cx + tw / 2 + 5 + i, y + 4, cx + tw / 2 + 6 + i, y + 5, c);
		}
	}

	/**
	 * A stone button with a gold rim that takes on the accent colour under the mouse. The primary one is filled with
	 * the accent and breathes while it can be pressed.
	 */
	public static void button(GuiGraphics context, int x, int y, int w, int h, boolean active, boolean hovered, boolean primary, int accent) {
		int x1 = x + w;
		int y1 = y + h;
		context.fill(x, y, x1, y1, BLACK);
		if (!active) {
			context.fill(x + 1, y + 1, x1 - 1, y1 - 1, 0xFF110E12);
			outline(context, x + 1, y + 1, x1 - 1, y1 - 1, 0xFF2A2328);
			return;
		}
		if (primary) {
			float pulse = hovered ? 1 : 0.5f + 0.5f * (float) Math.sin(time() * 3);
			int top = mix(0xFF2A2026, accent, hovered ? 0.62f : 0.45f);
			int bottom = mix(0xFF120E12, accent, hovered ? 0.3f : 0.2f);
			context.fillGradient(x + 1, y + 1, x1 - 1, y1 - 1, top, bottom);
			outline(context, x + 1, y + 1, x1 - 1, y1 - 1, mix(RIM, GOLD, pulse));
		} else {
			context.fillGradient(x + 1, y + 1, x1 - 1, y1 - 1, hovered ? 0xFF342A30 : 0xFF241C22, hovered ? 0xFF1C161A : 0xFF141014);
			outline(context, x + 1, y + 1, x1 - 1, y1 - 1, hovered ? accent : RIM);
		}
		context.fill(x + 2, y + 2, x1 - 2, y + 3, 0x22FFFFFF);
	}

	/** A Codex texture drawn at the given size (icons are 32 px textures, emblems 64), dimmed when not available. */
	public static void texture(GuiGraphics context, ResourceLocation texture, int x, int y, int size, int textureSize, boolean bright) {
		RenderSystem.enableBlend();
		if (!bright) {
			RenderSystem.setShaderColor(0.42f, 0.4f, 0.44f, 1f);
		}
		context.blit(texture, x, y, size, size, 0, 0, textureSize, textureSize, textureSize, textureSize);
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
	}

	public static void icon(GuiGraphics context, ResourceLocation icon, int x, int y, int size, boolean bright) {
		texture(context, icon, x, y, size, 32, bright);
	}

	/**
	 * A creature's portrait from the bestiary art (textures/gui/beast or gui/echo, 32 px), or its spawn egg while the
	 * portrait isn't drawn yet.
	 */
	public static void portrait(GuiGraphics context, String folder, String id, Item fallback, int x, int y, boolean bright) {
		ResourceLocation texture = gui(folder + "/" + id);
		if (exists(texture)) {
			texture(context, texture, x, y, 16, 32, bright);
		} else {
			context.renderItem(new ItemStack(fallback), x, y);
		}
	}

	public static void emblem(GuiGraphics context, Race race, int x, int y, int size, boolean bright) {
		texture(context, emblem(race), x, y, size, 64, bright);
	}

	/** A soft glow behind something round: rings of fading colour. */
	public static void glow(GuiGraphics context, int cx, int cy, int radius, int color, float strength) {
		context.drawManaged(() -> glowRings(context, cx, cy, radius, color, strength));
	}

	private static void glowRings(GuiGraphics context, int cx, int cy, int radius, int color, float strength) {
		for (int r = radius; r > 0; r -= 2) {
			float a = strength * (1 - r / (float) radius) * 0.35f;
			int c = alpha(color, a);
			for (int dy = -r; dy <= r; dy += 1) {
				int half = (int) Math.sqrt(r * r - dy * dy);
				if (dy % 2 == 0) {
					context.fill(cx - half, cy + dy, cx + half, cy + dy + 2, c);
				}
			}
		}
	}

	/** A key cap: a small dark box with the key's letter, as on a keyboard. */
	public static void keyCap(GuiGraphics context, String key, int rightX, int bottomY, int color) {
		var tr = Minecraft.getInstance().font;
		int w = Math.max(9, tr.width(key) + 4);
		int x0 = rightX - w;
		int y0 = bottomY - 10;
		context.fill(x0, y0, rightX, bottomY, BLACK);
		context.fill(x0 + 1, y0 + 1, rightX - 1, bottomY - 1, 0xFF2A2328);
		context.fill(x0 + 1, bottomY - 2, rightX - 1, bottomY - 1, 0xFF181418);
		context.drawString(tr, key, x0 + (w - tr.width(key)) / 2 + 1, y0 + 1, color, false);
	}
}
