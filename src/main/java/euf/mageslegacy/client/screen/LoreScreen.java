package euf.mageslegacy.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import euf.mageslegacy.race.Race;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * The legend of the nameless mage, told as a short book before the choice of race: a page at a time, each with its
 * picture and a line or two that write themselves out. The third page lights the eight emblems one by one, each with
 * what the mage gave that people. A click finishes the writing, the next click turns the page.
 */
public class LoreScreen extends Screen {
	/** Pictures per page; a page whose picture isn't there yet shows the mage's. */
	private static final String[] PICTURES = {"lore_people", "lore_mage", null, "lore_sleep", "lore_wake"};
	private static final ResourceLocation FALLBACK = LegacyUi.gui("lore_mage");
	private static final int PAGES = PICTURES.length;
	private static final int DIVISION = 2;
	/** Letters written per second, and how long the page takes to fade in. */
	private static final float LETTERS = 45;
	private static final float FADE = 0.5f;
	/** On the division page, one emblem every this many seconds. */
	private static final float EMBLEM_EVERY = 0.8f;
	/** How long one emblem takes to come up out of the dark. */
	private static final float EMBLEM_FADE = 1.1f;

	private int page;
	private long pageAt;
	private boolean revealed;
	private LegacyButton backButton;
	private LegacyButton nextButton;

	public LoreScreen() {
		super(Component.translatable("mages_legacy.lore.intro.title"));
	}

	@Override
	protected void init() {
		if (pageAt == 0) {
			pageAt = Util.getMillis();
		}
		backButton = addRenderableWidget(new LegacyButton(width / 2 - 170, height - 28, 110, 20, Component.translatable("mages_legacy.screen.back"), b -> turn(-1)));
		nextButton = addRenderableWidget(new LegacyButton(width / 2 + 60, height - 28, 110, 20, Component.translatable("mages_legacy.screen.next"), b -> advance())
				.primary());
	}

	/** Straight to the choice of race (the harness, and the last page's button). */
	public void next() {
		minecraft.setScreen(new RaceSelectScreen());
	}

	/** Finishes writing the page at once; also used by the harness before its screenshot. */
	public void revealAll() {
		revealed = true;
	}

	/** Opens a page already written out (the harness photographs them). */
	public void showPage(int index) {
		turn(index - page);
		revealAll();
	}

	private void advance() {
		if (!done()) {
			revealAll();
		} else if (page == PAGES - 1) {
			next();
		} else {
			turn(1);
		}
	}

	private void turn(int by) {
		page = Math.max(0, Math.min(PAGES - 1, page + by));
		pageAt = Util.getMillis();
		revealed = false;
	}

	private float elapsed() {
		return revealed ? 1000 : (Util.getMillis() - pageAt) / 1000f;
	}

	private Component text() {
		return Component.translatable("mages_legacy.lore.page." + (page + 1));
	}

	/** Whether the page has finished writing itself out. */
	private boolean done() {
		float t = elapsed();
		if (page == DIVISION) {
			return t >= FADE + (Race.values().length - 1) * EMBLEM_EVERY + EMBLEM_FADE + 0.6f;
		}
		return t >= FADE + text().getString().length() / LETTERS;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		advance();
		return true;
	}

	@Override
	public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
		LegacyUi.background(ctx, width, height, null);
		float t = elapsed();
		float fade = Math.min(1, t / FADE);
		LegacyUi.heading(ctx, title.copy().withStyle(ChatFormatting.BOLD), width / 2, 10, LegacyUi.GOLD);
		if (page == DIVISION) {
			renderDivision(ctx, t, fade);
		} else {
			renderPage(ctx, t, fade);
		}
		// Where we are in the book
		int dots = PAGES * 12;
		for (int i = 0; i < PAGES; i++) {
			LegacyUi.diamond(ctx, width / 2 - dots / 2 + i * 12 + 6, height - 18, i == page ? 4 : 3, i == page ? LegacyUi.GOLD : LegacyUi.RIM);
		}
		backButton.visible = page > 0;
		nextButton.setMessage(Component.translatable(page == PAGES - 1 && done() ? "mages_legacy.lore.choose" : "mages_legacy.screen.next"));
		super.render(ctx, mouseX, mouseY, delta);
	}

	/** A picture as large as the screen allows above, the page's words writing themselves out below. */
	private void renderPage(GuiGraphics ctx, float t, float fade) {
		Component text = text();
		int textWidth = Math.min(320, width - 60);
		List<FormattedCharSequence> lines = font.split(text.copy().withStyle(ChatFormatting.ITALIC), textWidth);
		int textHeight = lines.size() * 11;
		int top = 28;
		int room = height - top - textHeight - 70;
		int imageH = Math.max(0, Math.min(room, Math.min(192, (width - 80) * 2 / 3)));
		int y = top;
		if (imageH >= 48) {
			int imageW = imageH * 3 / 2;
			int ix = (width - imageW) / 2;
			ResourceLocation picture = PICTURES[page] == null ? FALLBACK : LegacyUi.gui(PICTURES[page]);
			if (!LegacyUi.exists(picture)) {
				picture = FALLBACK;
			}
			LegacyUi.panel(ctx, ix - 4, y - 4, ix + imageW + 4, y + imageH + 4);
			if (LegacyUi.exists(picture)) {
				ctx.blit(picture, ix, y, imageW, imageH, 0, 0, 144, 96, 144, 96);
			}
			// The picture comes up out of the dark
			if (fade < 1) {
				ctx.fill(ix, y, ix + imageW, y + imageH, LegacyUi.alpha(0xFF000000, 1 - fade));
			}
			y += imageH + 14;
		}
		// The words, letter by letter, the newest letters still fading in
		int shown = (int) Math.max(0, (t - FADE) * LETTERS);
		int x = (width - textWidth) / 2;
		int seen = 0;
		for (FormattedCharSequence line : lines) {
			StringBuilder sb = new StringBuilder();
			line.accept((index, style, codePoint) -> {
				sb.appendCodePoint(codePoint);
				return true;
			});
			String full = sb.toString();
			int take = Math.max(0, Math.min(full.length(), shown - seen));
			if (take > 0) {
				int lineWidth = font.width(full);
				Component part = Component.literal(full.substring(0, take)).withStyle(ChatFormatting.ITALIC);
				ctx.drawString(font, part, (width - lineWidth) / 2, y, LegacyUi.PARCHMENT, true);
			}
			seen += full.length();
			y += 11;
		}
	}

	/**
	 * The division: the eight peoples' emblems coming up out of the dark one after another, slowly, each settling into
	 * a soft breathing glow; its name and what the mage gave it appear once the emblem has lit, below the glow.
	 */
	private void renderDivision(GuiGraphics ctx, float t, float fade) {
		Component head = text();
		ctx.drawCenteredString(font, head.copy().withStyle(ChatFormatting.ITALIC), width / 2, 30, LegacyUi.alpha(LegacyUi.PARCHMENT, fade));
		Race[] races = Race.values();
		int cols = 4;
		int cellW = Math.min(150, (width - 40) / cols);
		// Each cell: the emblem, a margin for its glow, the name and up to two lines of the gift
		int top = 48;
		int size = Math.max(24, Math.min(44, (height - top - 60) / 2 - 46));
		int glow = size * 3 / 4;
		int cellH = size + (glow - size / 2) + 4 + 10 + 22 + 12;
		int y0 = top + Math.max(0, (height - top - 50 - 2 * cellH) / 2);
		int x0 = (width - cols * cellW) / 2;
		for (Race race : races) {
			int i = race.ordinal();
			float lit = smooth((t - FADE - i * EMBLEM_EVERY) / EMBLEM_FADE);
			float words = smooth((t - FADE - i * EMBLEM_EVERY - EMBLEM_FADE * 0.6f) / 0.6f);
			int cx = x0 + (i % cols) * cellW + cellW / 2;
			int cy = y0 + (i / cols) * cellH;
			int color = LegacyUi.argb(race.color);
			if (lit > 0) {
				// Once lit it breathes, slowly, each in its own time
				float breath = 0.85f + 0.15f * (float) Math.sin(t * 1.6f + i * 0.8f);
				LegacyUi.glow(ctx, cx, cy + size / 2, glow, color, lit * breath * 0.9f);
			}
			if (LegacyUi.exists(LegacyUi.emblem(race))) {
				float shade = 0.35f + 0.65f * lit;
				RenderSystem.enableBlend();
				RenderSystem.setShaderColor(shade, shade, shade, 0.25f + 0.75f * lit);
				ctx.blit(LegacyUi.emblem(race), cx - size / 2, cy, size, size, 0, 0, 64, 64, 64, 64);
				RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
			} else {
				LegacyUi.diamond(ctx, cx, cy + size / 2, size / 4, LegacyUi.alpha(color, 0.3f + 0.7f * lit));
			}
			if (words > 0.02f) {
				int ty = cy + size / 2 + glow + 4;
				ctx.drawCenteredString(font, Component.translatable("mages_legacy.race." + race.id()), cx, ty, LegacyUi.alpha(color, words));
				List<FormattedCharSequence> gift = font.split(Component.translatable("mages_legacy.race." + race.id() + ".gift").withStyle(ChatFormatting.ITALIC), cellW - 8);
				int gy = ty + 11;
				for (FormattedCharSequence line : gift) {
					ctx.drawCenteredString(font, line, cx, gy, LegacyUi.alpha(LegacyUi.PARCHMENT, words));
					gy += 10;
				}
			}
		}
	}

	private static float smooth(float t) {
		t = Math.max(0, Math.min(1, t));
		return t * t * (3 - 2 * t);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
