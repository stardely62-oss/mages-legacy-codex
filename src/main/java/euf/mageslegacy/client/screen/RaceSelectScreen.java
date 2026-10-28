package euf.mageslegacy.client.screen;

import euf.mageslegacy.client.MagesLegacyClient;
import euf.mageslegacy.client.RaceHud;
import euf.mageslegacy.race.AbilitySlots;
import euf.mageslegacy.race.Race;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * A card for every race with its emblem on top (the sleeping ones too, with their legend); below, the chosen race: emblem, name and legend on the left, strengths,
 * weaknesses and ability tiles on the right. What an ability does is in its tooltip. The legend and the traits
 * scroll with the wheel when a large GUI scale leaves them too little room.
 */
public class RaceSelectScreen extends Screen {
	private static final int CARD_Y = 24;
	private static final int CARD_H = 52;
	private static final int TILE_H = 38;
	private static final int TILES_PER_ROW = 3;

	private Race selected = Race.MYCELID;
	private LegacyButton chooseButton;
	/** Scroll offsets of the legend and the traits, and how far each can go (set while drawing). */
	private int legendScroll;
	private int traitsScroll;
	private int legendMax;
	private int traitsMax;

	public RaceSelectScreen() {
		super(Component.translatable("mages_legacy.screen.choose_title"));
	}

	@Override
	protected void init() {
		chooseButton = addRenderableWidget(new LegacyButton(width / 2 + 4, height - 26, 150, 20, Component.empty(), b -> choose()).primary());
		addRenderableWidget(new LegacyButton(width / 2 - 154, height - 26, 150, 20, Component.translatable("mages_legacy.screen.back_to_legend"),
				b -> minecraft.setScreen(new LoreScreen())));
		updateButton();
	}

	private void updateButton() {
		chooseButton.active = selected.playable;
		chooseButton.accent(LegacyUi.argb(selected.color));
		chooseButton.setMessage(selected.playable
				? Component.translatable("mages_legacy.screen.choose", Component.translatable(selected.key()))
				: Component.translatable("mages_legacy.screen.soon"));
	}

	public void select(Race race) {
		selected = race;
		legendScroll = 0;
		traitsScroll = 0;
		updateButton();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double amount) {
		if (mouseY >= detailTop() && mouseY < detailBottom()) {
			if (mouseX < width / 2.0) {
				legendScroll = Math.max(0, Math.min(legendMax, legendScroll - (int) (amount * 12)));
			} else {
				traitsScroll = Math.max(0, Math.min(traitsMax, traitsScroll - (int) (amount * 12)));
			}
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, amount);
	}

	/** A thin scrollbar along the right edge of a scrolled area. */
	private void scrollbar(GuiGraphics ctx, int x, int top, int bottom, int scroll, int max, int color) {
		if (max <= 0) {
			return;
		}
		int track = bottom - top;
		int thumb = Math.max(10, track * track / (track + max));
		int ty = top + (track - thumb) * scroll / max;
		ctx.fill(x, top, x + 2, bottom, LegacyUi.BLACK);
		ctx.fill(x, ty, x + 2, ty + thumb, color);
	}

	public Race selected() {
		return selected;
	}

	public void choose() {
		if (selected.playable) {
			MagesLegacyClient.sendChoice(selected.id());
		}
	}

	private static int cards() {
		return Race.values().length;
	}

	private int cardWidth() {
		return Math.min(110, (width - 24 - (cards() - 1) * 6) / cards());
	}

	private int cardX(int index) {
		int total = cards() * cardWidth() + (cards() - 1) * 6;
		return (width - total) / 2 + index * (cardWidth() + 6);
	}

	private int colWidth() {
		return Math.min(290, (width - 52) / 2);
	}

	private int detailTop() {
		return CARD_Y + CARD_H + 12;
	}

	private int detailBottom() {
		return height - 34;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		for (Race race : Race.values()) {
			int x = cardX(race.ordinal());
			if (mouseX >= x && mouseX < x + cardWidth() && mouseY >= CARD_Y && mouseY < CARD_Y + CARD_H) {
				select(race);
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
		LegacyUi.background(ctx, width, height, null);
		LegacyUi.heading(ctx, title, width / 2, 9, LegacyUi.GOLD);
		for (Race race : Race.values()) {
			drawCard(ctx, race, mouseX, mouseY);
		}
		int leftX = width / 2 - colWidth() - 8;
		int rightX = width / 2 + 8;
		drawLegend(ctx, leftX);
		int hoveredSlot = drawTraits(ctx, rightX, mouseX, mouseY);
		super.render(ctx, mouseX, mouseY, delta);
		if (hoveredSlot > 0) {
			ctx.renderComponentTooltip(font, abilityTooltip(selected, hoveredSlot), mouseX, mouseY);
		}
	}

	private void drawCard(GuiGraphics ctx, Race race, int mouseX, int mouseY) {
		int x = cardX(race.ordinal());
		int w = cardWidth();
		int color = LegacyUi.argb(race.color);
		boolean isSelected = race == selected;
		boolean hot = mouseX >= x && mouseX < x + w && mouseY >= CARD_Y && mouseY < CARD_Y + CARD_H;
		int lift = isSelected ? 2 : 0;
		int y = CARD_Y - lift;
		LegacyUi.panel(ctx, x, y, x + w, y + CARD_H, isSelected ? color : hot ? LegacyUi.mix(LegacyUi.RIM, color, 0.6f) : LegacyUi.RIM_DARK);
		int cx = x + w / 2;
		if (isSelected) {
			float pulse = 0.75f + 0.25f * (float) Math.sin(LegacyUi.time() * 2.5);
			LegacyUi.glow(ctx, cx, y + 20, 22, color, pulse);
			ctx.fill(x + 1, y + CARD_H - 3, x + w - 1, y + CARD_H - 1, color);
		}
		if (LegacyUi.exists(LegacyUi.emblem(race))) {
			LegacyUi.emblem(ctx, race, cx - 16, y + 4, 32, race.playable || isSelected || hot);
		} else {
			LegacyUi.diamond(ctx, cx, y + 20, 10, color);
		}
		Component name = Component.translatable(race.key());
		int nameColor = race.playable ? (isSelected ? 0xFFFFFFFF : color) : (isSelected ? color : 0xFF7A7078);
		ctx.drawCenteredString(font, font.plainSubstrByWidth(name.getString(), w - 4), cx, y + 39, nameColor);
	}

	/** Left column: the emblem with a glow, the race's name and tagline, then its legend. */
	private void drawLegend(GuiGraphics ctx, int x) {
		int w = colWidth();
		int top = detailTop();
		int bottom = detailBottom();
		int color = LegacyUi.argb(selected.color);
		LegacyUi.panel(ctx, x - 6, top - 6, x + w + 6, bottom);

		int emblem = 40;
		LegacyUi.glow(ctx, x + emblem / 2, top + emblem / 2, 30, color, 0.8f);
		if (LegacyUi.exists(LegacyUi.emblem(selected))) {
			LegacyUi.emblem(ctx, selected, x, top, emblem, true);
		}
		ctx.pose().pushPose();
		ctx.pose().translate(x + emblem + 8, top + 6, 0);
		ctx.pose().scale(1.5f, 1.5f, 1f);
		ctx.drawString(font, Component.translatable(selected.key()), 0, 0, color);
		ctx.pose().popPose();
		ctx.drawString(font, Component.translatable(selected.key() + ".tagline").withStyle(ChatFormatting.ITALIC), x + emblem + 8, top + 24,
				LegacyUi.MUTED);

		int y = top + emblem + 6;
		LegacyUi.divider(ctx, x, x + w, y, color);
		// The legend scrolls inside the rest of the column
		int areaTop = y + 8;
		int areaBottom = bottom - 4;
		ctx.enableScissor(x - 2, areaTop, x + w + 2, areaBottom);
		y = areaTop - legendScroll;
		for (Component line : Lines.of(selected.key() + ".lore")) {
			for (FormattedCharSequence row : font.split(line.copy().withStyle(ChatFormatting.ITALIC), w - 4)) {
				ctx.drawString(font, row, x, y, LegacyUi.PARCHMENT, true);
				y += 10;
			}
			y += 4;
		}
		ctx.disableScissor();
		legendMax = Math.max(0, y + legendScroll - areaBottom);
		legendScroll = Math.min(legendScroll, legendMax);
		scrollbar(ctx, x + w + 2, areaTop, areaBottom, legendScroll, legendMax, color);
	}

	/** Right column: strengths and weaknesses as gem lists, then the ability tiles. Returns the hovered ability slot. */
	private int drawTraits(GuiGraphics ctx, int x, int mouseX, int mouseY) {
		int w = colWidth();
		int top = detailTop();
		int bottom = detailBottom();
		LegacyUi.panel(ctx, x - 6, top - 6, x + w + 6, bottom);
		int count = abilityCount(selected);
		boolean abilities = count > 0;
		int rows = (count + TILES_PER_ROW - 1) / TILES_PER_ROW;
		int tilesH = rows * TILE_H + (rows - 1) * 4;
		int listBottom = bottom - (abilities ? tilesH + 18 : 22);

		// Strengths and weaknesses scroll above the ability tiles
		ctx.enableScissor(x - 2, top, x + w + 2, listBottom);
		int y = top - traitsScroll;
		y = section(ctx, Component.translatable("mages_legacy.screen.strengths"), x, y, LegacyUi.GREEN);
		y = gems(ctx, Lines.of(selected.key() + ".perk"), x, y, w - 4, LegacyUi.GREEN, 0xFFD6EBD0, Integer.MAX_VALUE);
		y += 4;
		y = section(ctx, Component.translatable("mages_legacy.screen.weaknesses"), x, y, LegacyUi.RED);
		y = gems(ctx, Lines.of(selected.key() + ".weak"), x, y, w - 4, LegacyUi.RED, 0xFFEBD0D0, Integer.MAX_VALUE);
		ctx.disableScissor();
		traitsMax = Math.max(0, y + traitsScroll - listBottom);
		traitsScroll = Math.min(traitsScroll, traitsMax);
		scrollbar(ctx, x + w + 2, top, listBottom, traitsScroll, traitsMax, LegacyUi.argb(selected.color));

		if (!abilities) {
			// A race not awakened yet: a sleeping seal instead of abilities
			Component soon = Component.translatable("mages_legacy.screen.soon").withStyle(ChatFormatting.ITALIC);
			int cy = bottom - 16;
			LegacyUi.divider(ctx, x, x + w, cy - 6, LegacyUi.argb(selected.color));
			ctx.drawCenteredString(font, soon, x + w / 2, cy, LegacyUi.MUTED);
			return 0;
		}
		int tilesTop = bottom - tilesH - 6;
		LegacyUi.divider(ctx, x, x + w, tilesTop - 8, LegacyUi.argb(selected.color));
		int tileW = (w - 4 * (TILES_PER_ROW - 1)) / TILES_PER_ROW;
		int hovered = 0;
		for (int slot = 1; slot <= count; slot++) {
			int row = (slot - 1) / TILES_PER_ROW;
			int col = (slot - 1) % TILES_PER_ROW;
			// A shorter last row sits in the middle
			int inRow = Math.min(TILES_PER_ROW, count - row * TILES_PER_ROW);
			int rowX = x + (w - inRow * tileW - (inRow - 1) * 4) / 2;
			int tx = rowX + col * (tileW + 4);
			int ty = tilesTop + row * (TILE_H + 4);
			boolean hot = mouseX >= tx && mouseX < tx + tileW && mouseY >= ty && mouseY < ty + TILE_H;
			if (hot) {
				hovered = slot;
			}
			LegacyUi.panel(ctx, tx, ty, tx + tileW, ty + TILE_H, hot ? LegacyUi.argb(selected.color) : LegacyUi.RIM_DARK);
			String key = "mages_legacy.ability." + selected.id() + "." + slot;
			int cx = tx + tileW / 2;
			String icon = RaceHud.abilityIcon(selected, slot - 1);
			if (icon != null && LegacyUi.exists(LegacyUi.icon(icon))) {
				LegacyUi.icon(ctx, LegacyUi.icon(icon), cx - 8, ty + 4, 16, true);
			}
			Component name = Component.translatable(key + ".name");
			ctx.drawCenteredString(font, font.plainSubstrByWidth(name.getString(), tileW - 4), cx, ty + 23, LegacyUi.GOLD);
		}
		return hovered;
	}

	/** How many abilities a race has: the main three plus whatever it has on H and Alt; 0 for a race not awakened yet. */
	private static int abilityCount(Race race) {
		int count = 0;
		while (count < AbilitySlots.KEYS && I18n.exists("mages_legacy.ability." + race.id() + "." + (count + 1) + ".name")) {
			count++;
		}
		return count;
	}

	private int section(GuiGraphics ctx, Component title, int x, int y, int color) {
		ctx.drawString(font, title.copy().withStyle(ChatFormatting.BOLD), x, y, color);
		return y + 12;
	}

	private int gems(GuiGraphics ctx, List<Component> lines, int x, int y, int w, int gem, int color, int bottom) {
		for (Component line : lines) {
			List<FormattedCharSequence> rows = font.split(line, w - 10);
			if (y + 9 > bottom) {
				return y;
			}
			LegacyUi.diamond(ctx, x + 3, y + 3, 2, gem);
			for (FormattedCharSequence row : rows) {
				if (y + 9 > bottom) {
					return y;
				}
				ctx.drawString(font, row, x + 10, y, color, true);
				y += 10;
			}
			y += 1;
		}
		return y;
	}

	private static List<Component> abilityTooltip(Race race, int slot) {
		String key = "mages_legacy.ability." + race.id() + "." + slot;
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable(key + ".name").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		if (I18n.exists(key + ".lore")) {
			TalentScreen.wrapInto(lines, Component.translatable(key + ".lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
		TalentScreen.wrapInto(lines, Component.translatable(key + ".desc").withStyle(ChatFormatting.WHITE));
		return lines;
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
