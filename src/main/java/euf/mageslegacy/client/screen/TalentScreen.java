package euf.mageslegacy.client.screen;

import euf.mageslegacy.client.ClientRaceState;
import euf.mageslegacy.client.MagesLegacyClient;
import euf.mageslegacy.client.RaceHud;
import euf.mageslegacy.race.AbilitySlots;
import euf.mageslegacy.race.Progression;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.Talent;
import euf.mageslegacy.race.Talents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The race's talent tree: a header with the emblem, level, experience and free points; three branches in carved
 * columns, tiers in rows; the abilities along the bottom. Talents you can learn now breathe in the race's colour,
 * learned ones are framed in gold and their links glow.
 */
public class TalentScreen extends Screen {
	private static final int NODE = 24;
	private static final int TOP = 80;
	/** Whether the guide was opened this session; until then its button glows. */
	private static boolean guideSeen;
	private static final String[] ROMAN = {"I", "II", "III", "IV"};

	public enum NodeState { OWNED, AVAILABLE, NO_POINTS, NEEDS_TALENT, TIER_CLOSED }

	private final Map<String, int[]> positions = new HashMap<>();
	private boolean showGuide;
	/** How far the tree and the guide are scrolled, when they don't fit on the screen. */
	private int treeScroll;
	private int guideScroll;
	private int guideMaxScroll;
	/** The traits page: the race's strengths and weaknesses, and what the learned talents give. */
	private boolean showTraits;
	private int traitsScroll;
	private int traitsMaxScroll;

	public TalentScreen() {
		super(Component.translatable("mages_legacy.screen.talents"));
	}

	private Race race() {
		return ClientRaceState.race;
	}

	private int columnWidth() {
		return Math.min(180, (width - 70) / 3);
	}

	private int columnX(int branch) {
		int w = columnWidth();
		return (width - 3 * w - 2 * 12) / 2 + 8 + branch * (w + 12);
	}

	/** Rows spread to fill the room between the branch titles and the ability bar. */
	private int rowGap() {
		int tiers = tiers();
		return tiers <= 1 ? 40 : Math.max(30, Math.min(44, (height - 30 - 14 - TOP - NODE - 10) / (tiers - 1)));
	}

	private int rowY(int tier) {
		return TOP + (tier - 1) * rowGap();
	}

	/** The tree scrolls between the header and the ability bar. */
	private int treeTop() {
		return 36;
	}

	private int treeBottom() {
		return RaceHud.hasAbilities(race()) ? height - 36 : height - 6;
	}

	private int treeMaxScroll() {
		return Math.max(0, rowY(tiers()) + NODE + 12 - treeBottom());
	}

	private boolean inTree(double mouseY) {
		return mouseY >= treeTop() && mouseY < treeBottom();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double amount) {
		if (showGuide) {
			guideScroll = Math.max(0, Math.min(guideMaxScroll, guideScroll - (int) (amount * 20)));
			return true;
		}
		if (showTraits) {
			traitsScroll = Math.max(0, Math.min(traitsMaxScroll, traitsScroll - (int) (amount * 20)));
			return true;
		}
		if (inTree(mouseY)) {
			treeScroll = Math.max(0, Math.min(treeMaxScroll(), treeScroll - (int) (amount * 20)));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, amount);
	}

	/** A thin scrollbar: the track, and a thumb as long as the visible part. */
	private static void scrollbar(GuiGraphics ctx, int x, int top, int bottom, int scroll, int max, int color) {
		int track = bottom - top;
		int thumb = Math.max(12, track * track / (track + max));
		int y = top + (track - thumb) * scroll / Math.max(1, max);
		ctx.fill(x, top, x + 3, bottom, LegacyUi.BLACK);
		ctx.fill(x, y, x + 3, y + thumb, color);
	}

	private int guideButtonX() {
		return (width + 260) / 2 + 6;
	}

	/** The traits button sits left of the header, the guide's twin. */
	private int traitsButtonX() {
		return (width - 260) / 2 - 26;
	}

	private int tiers() {
		int max = 1;
		for (Talent t : Talents.of(race())) {
			max = Math.max(max, t.tier());
		}
		return max;
	}

	private void layout() {
		positions.clear();
		Race race = race();
		if (race == null) {
			return;
		}
		Map<String, List<Talent>> cells = new HashMap<>();
		for (Talent t : Talents.of(race)) {
			cells.computeIfAbsent(t.branch() + ":" + t.tier(), k -> new ArrayList<>()).add(t);
		}
		cells.forEach((cell, list) -> {
			int gap = list.size() <= 1 ? 16 : Math.max(4, Math.min(16, (columnWidth() - 8 - list.size() * NODE) / (list.size() - 1)));
			for (int i = 0; i < list.size(); i++) {
				Talent t = list.get(i);
				int center = columnX(t.branch()) + columnWidth() / 2;
				int x = center - (list.size() * (NODE + gap) - gap) / 2 + i * (NODE + gap);
				positions.put(t.id(), new int[] {x, rowY(t.tier())});
			}
		});
	}

	@Override
	protected void init() {
		layout();
	}

	public static NodeState state(Talent t) {
		if (ClientRaceState.talents.contains(t.id())) {
			return NodeState.OWNED;
		}
		if (t.tier() > ClientRaceState.openTiers) {
			return NodeState.TIER_CLOSED;
		}
		if (t.requires() != null && !ClientRaceState.talents.contains(t.requires())) {
			return NodeState.NEEDS_TALENT;
		}
		return ClientRaceState.freePoints >= t.cost() ? NodeState.AVAILABLE : NodeState.NO_POINTS;
	}

	public boolean click(String talentId) {
		Talent t = Talents.byId(talentId).orElse(null);
		if (t == null || state(t) != NodeState.AVAILABLE) {
			return false;
		}
		MagesLegacyClient.sendUnlock(talentId);
		return true;
	}

	public void toggleTraits() {
		showGuide = false;
		showTraits = !showTraits;
	}

	public void toggleGuide() {
		showGuide = !showGuide;
		guideSeen = true;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int gx = guideButtonX();
		if (mouseX >= gx && mouseX < gx + 20 && mouseY >= 8 && mouseY < 28) {
			showTraits = false;
			toggleGuide();
			return true;
		}
		int tx = traitsButtonX();
		if (mouseX >= tx && mouseX < tx + 20 && mouseY >= 8 && mouseY < 28) {
			toggleTraits();
			return true;
		}
		if (showGuide || showTraits) {
			showGuide = false;
			showTraits = false;
			return true;
		}
		double treeY = mouseY + treeScroll;
		for (Map.Entry<String, int[]> e : positions.entrySet()) {
			int[] p = e.getValue();
			if (inTree(mouseY) && mouseX >= p[0] && mouseX < p[0] + NODE && treeY >= p[1] && treeY < p[1] + NODE) {
				click(e.getKey());
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
		Race race = race();
		LegacyUi.background(ctx, width, height, race);
		if (race == null) {
			super.render(ctx, mouseX, mouseY, delta);
			return;
		}
		if (positions.isEmpty()) {
			layout();
		}
		int color = LegacyUi.argb(race.color);
		List<Component> headerTooltip = drawHeader(ctx, race, color, mouseX, mouseY);

		// The tree scrolls when the screen is too short for it: clip it to its strip and shift it up
		treeScroll = Math.max(0, Math.min(treeMaxScroll(), treeScroll));
		int treeMouseY = inTree(mouseY) ? mouseY + treeScroll : -1000;
		ctx.enableScissor(0, treeTop(), width, treeBottom());
		ctx.pose().pushPose();
		ctx.pose().translate(0, -treeScroll, 0);

		// Branch columns with their titles and one-line legends
		int columnBottom = rowY(tiers()) + NODE + 10;
		for (int branch = 0; branch < 3; branch++) {
			int x = columnX(branch);
			int w = columnWidth();
			String key = "mages_legacy.branch." + race.id() + "." + branch;
			LegacyUi.panel(ctx, x, 40, x + w, columnBottom);
			LegacyUi.heading(ctx, Component.translatable(key).withStyle(ChatFormatting.BOLD), x + w / 2, 45, LegacyUi.GOLD);
			List<FormattedCharSequence> lore = font.split(Component.translatable(key + ".lore").withStyle(ChatFormatting.ITALIC), w - 10);
			for (int i = 0; i < Math.min(2, lore.size()); i++) {
				ctx.drawCenteredString(font, lore.get(i), x + w / 2, 57 + i * 9, LegacyUi.MUTED);
			}
		}
		// Tier numerals at the left edge: gold when open, dark red while the milestone is still ahead
		for (int tier = 1; tier <= tiers(); tier++) {
			boolean open = tier <= ClientRaceState.openTiers;
			int cx = columnX(0) - 12;
			int cy = rowY(tier) + NODE / 2;
			LegacyUi.diamond(ctx, cx, cy, 8, LegacyUi.BLACK);
			LegacyUi.diamond(ctx, cx, cy, 7, open ? LegacyUi.RIM : 0xFF4A2226);
			LegacyUi.diamond(ctx, cx, cy, 6, 0xFF18131A);
			String numeral = ROMAN[tier - 1];
			ctx.drawString(font, numeral, cx - font.width(numeral) / 2 + 1, cy - 3, open ? LegacyUi.GOLD : 0xFF9A4040, true);
		}

		drawLinks(ctx, race, color);
		Talent hovered = drawNodes(ctx, race, color, mouseX, treeMouseY);
		ctx.pose().popPose();
		ctx.disableScissor();
		if (treeMaxScroll() > 0) {
			scrollbar(ctx, columnX(2) + columnWidth() + 6, treeTop() + 4, treeBottom() - 4, treeScroll, treeMaxScroll(), color);
		}
		int abilityHovered = drawAbilities(ctx, race, color, mouseX, mouseY);

		drawGuideButton(ctx, color, mouseX, mouseY);
		drawTraitsButton(ctx, race, color, mouseX, mouseY);
		super.render(ctx, mouseX, mouseY, delta);
		if (showGuide) {
			drawGuide(ctx, race, color);
			return;
		}
		if (showTraits) {
			drawTraits(ctx, race, color);
			return;
		}
		if (hovered != null) {
			ctx.renderComponentTooltip(font, tooltip(hovered), mouseX, mouseY);
		} else if (abilityHovered >= 0) {
			String key = "mages_legacy.ability." + race.id() + "." + (abilityHovered + 1);
			List<Component> lines = new ArrayList<>();
			lines.add(Component.translatable(key + ".name").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
			wrapInto(lines, Component.translatable(key + ".lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			wrapInto(lines, Component.translatable(key + ".desc").withStyle(ChatFormatting.WHITE));
			ctx.renderComponentTooltip(font, lines, mouseX, mouseY);
		} else if (headerTooltip != null) {
			ctx.renderComponentTooltip(font, headerTooltip, mouseX, mouseY);
		}
	}

	/** Emblem, race name and level, the experience bar and the free points gem. Returns a tooltip if hovered. */
	private List<Component> drawHeader(GuiGraphics ctx, Race race, int color, int mouseX, int mouseY) {
		int w = 260;
		int x0 = (width - w) / 2;
		int x1 = x0 + w;
		LegacyUi.panel(ctx, x0, 4, x1, 32);
		LegacyUi.glow(ctx, x0 + 16, 18, 16, color, 0.7f);
		if (LegacyUi.exists(LegacyUi.emblem(race))) {
			LegacyUi.emblem(ctx, race, x0 + 4, 6, 24, true);
		}
		Component name = Component.translatable(race.key()).withStyle(ChatFormatting.BOLD);
		ctx.drawString(font, name, x0 + 32, 8, color);
		Component level = Component.translatable("mages_legacy.screen.level", ClientRaceState.level);
		ctx.drawString(font, level, x0 + 32 + font.width(name) + 6, 8, LegacyUi.TEXT);

		// Experience bar with a light running along it
		int bx0 = x0 + 32;
		int bx1 = x1 - 40;
		int by = 21;
		ctx.fill(bx0 - 1, by - 1, bx1 + 1, by + 5, LegacyUi.BLACK);
		ctx.fill(bx0, by, bx1, by + 4, 0xFF221C24);
		int fill = (bx1 - bx0) * Math.min(ClientRaceState.xp, ClientRaceState.xpNext) / Math.max(1, ClientRaceState.xpNext);
		if (fill > 0) {
			ctx.fillGradient(bx0, by, bx0 + fill, by + 4, LegacyUi.mix(color, 0xFFFFFFFF, 0.3f), LegacyUi.mix(color, 0xFF000000, 0.3f));
			int shine = bx0 + (int) ((LegacyUi.time() * 40) % Math.max(1, fill + 20)) - 10;
			ctx.enableScissor(bx0, by, bx0 + fill, by + 4);
			ctx.fillGradient(shine, by, shine + 8, by + 4, 0x70FFFFFF, 0x20FFFFFF);
			ctx.disableScissor();
		}

		// Free points: a gem with the number, glowing when something can be learned
		int gx = x1 - 22;
		int gy = 18;
		boolean any = ClientRaceState.freePoints > 0;
		if (any) {
			LegacyUi.glow(ctx, gx, gy, 12, LegacyUi.GREEN, 0.6f + 0.4f * (float) Math.sin(LegacyUi.time() * 3));
		}
		LegacyUi.diamond(ctx, gx, gy, 9, LegacyUi.BLACK);
		LegacyUi.diamond(ctx, gx, gy, 8, any ? 0xFF3C6A34 : 0xFF2A2328);
		LegacyUi.diamond(ctx, gx, gy, 6, any ? 0xFF243E20 : 0xFF181418);
		String points = String.valueOf(ClientRaceState.freePoints);
		ctx.drawString(font, points, gx - font.width(points) / 2 + 1, gy - 3, any ? LegacyUi.GREEN : LegacyUi.MUTED, true);

		if (mouseY >= 4 && mouseY < 32 && mouseX >= x0 && mouseX < x1) {
			return List.of(Component.translatable("mages_legacy.screen.tree_header", Component.translatable(race.key()), ClientRaceState.level,
					ClientRaceState.xp, ClientRaceState.xpNext), Component.translatable("mages_legacy.screen.points", ClientRaceState.freePoints)
					.withStyle(any ? ChatFormatting.GREEN : ChatFormatting.GRAY));
		}
		return null;
	}

	/** The "?" next to the header: opens the guide to experience, points and tiers; glows until first opened. */
	private void drawGuideButton(GuiGraphics ctx, int color, int mouseX, int mouseY) {
		int x = guideButtonX();
		boolean hot = mouseX >= x && mouseX < x + 20 && mouseY >= 8 && mouseY < 28;
		if (!guideSeen) {
			LegacyUi.glow(ctx, x + 10, 18, 16, LegacyUi.GOLD, 0.5f + 0.5f * (float) Math.sin(LegacyUi.time() * 3));
		}
		LegacyUi.button(ctx, x, 8, 20, 20, true, hot || showGuide, false, color);
		ctx.drawCenteredString(font, "?", x + 10, 14, LegacyUi.GOLD);
	}

	/** The traits button: the race's emblem-coloured book, left of the header. */
	private void drawTraitsButton(GuiGraphics ctx, Race race, int color, int mouseX, int mouseY) {
		int x = traitsButtonX();
		boolean hot = mouseX >= x && mouseX < x + 20 && mouseY >= 8 && mouseY < 28;
		LegacyUi.button(ctx, x, 8, 20, 20, true, hot || showTraits, false, color);
		ctx.renderItem(new ItemStack(Items.KNOWLEDGE_BOOK), x + 2, 10);
		if (hot && !showTraits) {
			ctx.renderTooltip(font, Component.translatable("mages_legacy.traits.title"), x, 44);
		}
	}

	/**
	 * Everything that works for this player without a key: the race's strengths and weaknesses (the same lines as on
	 * the choice screen) and every learned talent with what it gives, in the tree's order. Scrolls when long.
	 */
	private void drawTraits(GuiGraphics ctx, Race race, int color) {
		ctx.pose().pushPose();
		ctx.pose().translate(0, 0, 300);
		ctx.fill(0, 0, width, height, 0xA0000000);
		int w = Math.min(360, width - 30);
		int x0 = (width - w) / 2;
		int h = traitsBody(ctx, race, color, x0, 0, w, true);
		int shown = Math.min(h, height - 8);
		traitsMaxScroll = h - shown;
		traitsScroll = Math.max(0, Math.min(traitsMaxScroll, traitsScroll));
		int y0 = (height - shown) / 2;
		LegacyUi.panel(ctx, x0, y0, x0 + w, y0 + shown);
		if (traitsMaxScroll > 0) {
			scrollbar(ctx, x0 + w - 6, y0 + 4, y0 + shown - 4, traitsScroll, traitsMaxScroll, color);
		}
		ctx.enableScissor(x0 + 1, y0 + 2, x0 + w - 1, y0 + shown - 2);
		traitsBody(ctx, race, color, x0, y0 - traitsScroll, w, false);
		ctx.disableScissor();
		ctx.pose().popPose();
	}

	/** Lays out (and unless {@code measure}, draws) the traits page from {@code y0}; returns its height. */
	private int traitsBody(GuiGraphics ctx, Race race, int color, int x0, int y0, int w, boolean measure) {
		int x = x0 + 12;
		int right = x0 + w - 12;
		int y = y0 + 8;
		if (!measure) {
			LegacyUi.heading(ctx, Component.translatable("mages_legacy.traits.title").withStyle(ChatFormatting.BOLD), width / 2, y, LegacyUi.GOLD);
		}
		y += 20;
		y = traitList(ctx, Component.translatable("mages_legacy.screen.strengths"), Lines.of(race.key() + ".perk"), LegacyUi.GREEN, x, right, y, measure);
		y = traitList(ctx, Component.translatable("mages_legacy.screen.weaknesses"), Lines.of(race.key() + ".weak"), LegacyUi.RED, x, right, y, measure);
		if (!measure) {
			ctx.drawString(font, Component.translatable("mages_legacy.traits.talents").withStyle(ChatFormatting.BOLD), x, y, LegacyUi.GOLD);
		}
		y += 14;
		boolean any = false;
		for (Talent t : Talents.of(race)) {
			if (!ClientRaceState.talents.contains(t.id())) {
				continue;
			}
			any = true;
			if (!measure) {
				ResourceLocation icon = LegacyUi.icon(t.id());
				if (LegacyUi.exists(icon)) {
					LegacyUi.icon(ctx, icon, x, y, 16, true);
				} else {
					ctx.renderItem(new ItemStack(t.icon()), x, y);
				}
				ctx.drawString(font, Component.translatable(t.key()), x + 22, y, LegacyUi.GOLD);
			}
			y += 10;
			for (FormattedCharSequence row : font.split(Component.translatable(t.key() + ".desc"), right - x - 22)) {
				if (!measure) {
					ctx.drawString(font, row, x + 22, y, LegacyUi.PARCHMENT, true);
				}
				y += 10;
			}
			y += 6;
		}
		if (!any) {
			if (!measure) {
				ctx.drawString(font, Component.translatable("mages_legacy.traits.none"), x + 10, y, LegacyUi.MUTED);
			}
			y += 12;
		}
		return y + 4 - y0;
	}

	/** A heading and its lines as gems of one colour, wrapped; then a divider. Returns where the next part starts. */
	private int traitList(GuiGraphics ctx, Component title, List<Component> lines, int color, int x, int right, int y, boolean measure) {
		if (!measure) {
			ctx.drawString(font, title.copy().withStyle(ChatFormatting.BOLD), x, y, color);
		}
		y += 14;
		for (Component line : lines) {
			if (!measure) {
				LegacyUi.diamond(ctx, x + 3, y + 3, 2, color);
			}
			for (FormattedCharSequence row : font.split(line, right - x - 10)) {
				if (!measure) {
					ctx.drawString(font, row, x + 10, y, LegacyUi.TEXT, true);
				}
				y += 10;
			}
			y += 2;
		}
		y += 4;
		if (!measure) {
			LegacyUi.divider(ctx, x, right, y, color);
		}
		return y + 8;
	}

	/**
	 * The guide over the tree: what gives race experience (and how much), how points come with levels, and which
	 * milestone opens each tier. Texts come from mages_legacy.xp.<race>.N (+ .amount) and mages_legacy.guide.*.
	 */
	private void drawGuide(GuiGraphics ctx, Race race, int color) {
		ctx.pose().pushPose();
		ctx.pose().translate(0, 0, 300);
		ctx.fill(0, 0, width, height, 0xA0000000);
		int w = Math.min(330, width - 30);
		List<Component> xp = Lines.of("mages_legacy.xp." + race.id());
		List<Component> points = Lines.of("mages_legacy.guide.points");
		int h = 30 + 14 + xp.size() * 11 + 8 + 14;
		for (Component line : points) {
			h += font.split(line, w - 24).size() * 10 + 2;
		}
		h += 8 + 14 + Progression.MAX_TIER * 12 + 6;
		int x0 = (width - w) / 2;
		int shown = Math.min(h, height - 8);
		guideMaxScroll = h - shown;
		guideScroll = Math.max(0, Math.min(guideMaxScroll, guideScroll));
		int y0 = (height - shown) / 2;
		LegacyUi.panel(ctx, x0, y0, x0 + w, y0 + shown);
		if (guideMaxScroll > 0) {
			scrollbar(ctx, x0 + w - 6, y0 + 4, y0 + shown - 4, guideScroll, guideMaxScroll, color);
		}
		// Taller than the screen: the content scrolls inside the panel
		ctx.enableScissor(x0 + 1, y0 + 2, x0 + w - 1, y0 + shown - 2);
		y0 -= guideScroll;
		int x = x0 + 12;
		int right = x0 + w - 12;
		int y = y0 + 8;
		LegacyUi.heading(ctx, Component.translatable("mages_legacy.guide.title").withStyle(ChatFormatting.BOLD), width / 2, y, LegacyUi.GOLD);
		y += 20;

		ctx.drawString(font, Component.translatable("mages_legacy.guide.xp").withStyle(ChatFormatting.BOLD), x, y, color);
		y += 14;
		for (int i = 0; i < xp.size(); i++) {
			LegacyUi.diamond(ctx, x + 3, y + 3, 2, color);
			String amount = Component.translatable("mages_legacy.xp." + race.id() + "." + (i + 1) + ".amount").getString();
			int aw = font.width(amount);
			ctx.drawString(font, font.plainSubstrByWidth(xp.get(i).getString(), right - x - 16 - aw), x + 10, y, LegacyUi.TEXT);
			ctx.drawString(font, amount, right - aw, y, LegacyUi.GREEN);
			y += 11;
		}
		y += 4;
		LegacyUi.divider(ctx, x, right, y, color);
		y += 8;

		ctx.drawString(font, Component.translatable("mages_legacy.guide.points_title").withStyle(ChatFormatting.BOLD), x, y, LegacyUi.GREEN);
		y += 14;
		for (Component line : points) {
			LegacyUi.diamond(ctx, x + 3, y + 3, 2, LegacyUi.GREEN);
			for (FormattedCharSequence row : font.split(line, right - x - 10)) {
				ctx.drawString(font, row, x + 10, y, LegacyUi.PARCHMENT, true);
				y += 10;
			}
			y += 2;
		}
		y += 4;
		LegacyUi.divider(ctx, x, right, y, color);
		y += 8;

		ctx.drawString(font, Component.translatable("mages_legacy.guide.tiers").withStyle(ChatFormatting.BOLD), x, y, LegacyUi.GOLD);
		y += 14;
		for (int tier = 1; tier <= Progression.MAX_TIER; tier++) {
			boolean open = tier <= ClientRaceState.openTiers;
			ctx.drawString(font, ROMAN[tier - 1], x + 2, y, open ? LegacyUi.GOLD : 0xFF9A4040);
			ctx.drawString(font, Component.translatable("mages_legacy.guide.tier." + tier), x + 24, y, open ? LegacyUi.TEXT : LegacyUi.MUTED);
			if (open) {
				String done = "✔";
				ctx.drawString(font, done, right - font.width(done), y, LegacyUi.GREEN);
			}
			y += 12;
		}
		ctx.disableScissor();
		ctx.pose().popPose();
	}

	/** Links from each talent to the one it needs: gold once learned, the race's colour if it can be learned now. */
	private void drawLinks(GuiGraphics ctx, Race race, int color) {
		for (Talent t : Talents.of(race)) {
			int[] p = positions.get(t.id());
			int[] q = t.requires() == null ? null : positions.get(t.requires());
			if (p == null || q == null) {
				continue;
			}
			NodeState state = state(t);
			int c = state == NodeState.OWNED ? LegacyUi.GOLD
					: state == NodeState.AVAILABLE || state == NodeState.NO_POINTS ? LegacyUi.mix(0xFF3A3238, color, 0.6f) : 0xFF3A3238;
			int fromX = q[0] + NODE / 2;
			int toX = p[0] + NODE / 2;
			// A link over more than one tier turns just above its target, clear of the rows it passes
			int midY = p[1] - q[1] > rowGap() ? p[1] - (rowGap() - NODE) / 2 : q[1] + NODE + (p[1] - q[1] - NODE) / 2;
			ctx.fill(fromX - 1, q[1] + NODE, fromX + 1, midY + 1, c);
			ctx.fill(Math.min(fromX, toX) - 1, midY - 1, Math.max(fromX, toX) + 1, midY + 1, c);
			ctx.fill(toX - 1, midY, toX + 1, p[1], c);
		}
	}

	private Talent drawNodes(GuiGraphics ctx, Race race, int color, int mouseX, int mouseY) {
		Talent hovered = null;
		float pulse = 0.5f + 0.5f * (float) Math.sin(LegacyUi.time() * 3);
		for (Talent t : Talents.of(race)) {
			int[] p = positions.get(t.id());
			NodeState state = state(t);
			boolean hot = mouseX >= p[0] && mouseX < p[0] + NODE && mouseY >= p[1] && mouseY < p[1] + NODE;
			if (hot) {
				hovered = t;
			}
			int cx = p[0] + NODE / 2;
			int cy = p[1] + NODE / 2;
			if (state == NodeState.AVAILABLE) {
				LegacyUi.glow(ctx, cx, cy, 20, color, 0.5f + 0.5f * pulse);
			} else if (state == NodeState.OWNED) {
				LegacyUi.glow(ctx, cx, cy, 18, LegacyUi.GOLD, 0.45f);
			}
			int rim = switch (state) {
				case OWNED -> LegacyUi.GOLD;
				case AVAILABLE -> LegacyUi.mix(color, 0xFFFFFFFF, 0.35f * pulse);
				case NO_POINTS -> LegacyUi.mix(LegacyUi.RIM_DARK, color, 0.45f);
				case NEEDS_TALENT -> 0xFF3A3238;
				case TIER_CLOSED -> 0xFF4A2226;
			};
			if (hot) {
				rim = LegacyUi.mix(rim, 0xFFFFFFFF, 0.4f);
			}
			ctx.fill(p[0] - 1, p[1] - 1, p[0] + NODE + 1, p[1] + NODE + 1, LegacyUi.BLACK);
			LegacyUi.outline(ctx, p[0], p[1], p[0] + NODE, p[1] + NODE, rim);
			ctx.fillGradient(p[0] + 1, p[1] + 1, p[0] + NODE - 1, p[1] + NODE - 1,
					state == NodeState.OWNED ? 0xFF3E3220 : 0xFF221C24, state == NodeState.OWNED ? 0xFF241C12 : 0xFF141016);
			boolean bright = state == NodeState.OWNED || state == NodeState.AVAILABLE || state == NodeState.NO_POINTS;
			ResourceLocation icon = LegacyUi.icon(t.id());
			if (LegacyUi.exists(icon)) {
				LegacyUi.icon(ctx, icon, p[0] + 4, p[1] + 4, 16, bright);
			} else {
				ctx.renderItem(new ItemStack(t.icon()), p[0] + 4, p[1] + 4);
				if (!bright) {
					ctx.pose().pushPose();
					ctx.pose().translate(0, 0, 200);
					ctx.fill(p[0] + 1, p[1] + 1, p[0] + NODE - 1, p[1] + NODE - 1, 0x90000000);
					ctx.pose().popPose();
				}
			}
			if (state == NodeState.OWNED) {
				ctx.pose().pushPose();
				ctx.pose().translate(0, 0, 200);
				LegacyUi.diamond(ctx, p[0] + NODE - 1, p[1] + NODE - 1, 3, LegacyUi.BLACK);
				LegacyUi.diamond(ctx, p[0] + NODE - 1, p[1] + NODE - 1, 2, LegacyUi.GOLD);
				ctx.pose().popPose();
			}
		}
		return hovered;
	}

	/** The abilities: icon, name, key cap and cost. Returns the hovered slot, or -1. */
	private int drawAbilities(GuiGraphics ctx, Race race, int color, int mouseX, int mouseY) {
		if (!RaceHud.hasAbilities(race)) {
			return -1;
		}
		List<Integer> slots = new ArrayList<>();
		for (int slot = 0; slot < AbilitySlots.KEYS; slot++) {
			if (RaceHud.shown(race, slot)) {
				slots.add(slot);
			}
		}
		int count = slots.size();
		int tileW = Math.min(150, (width - 40 - (count - 1) * 8) / count);
		int total = count * tileW + (count - 1) * 8;
		int y = height - 30;
		int hovered = -1;
		String resourceIcon = RaceHud.resourceIcon(race);
		for (int place = 0; place < count; place++) {
			int slot = slots.get(place);
			int x = (width - total) / 2 + place * (tileW + 8);
			boolean hot = mouseX >= x && mouseX < x + tileW && mouseY >= y && mouseY < y + 24;
			if (hot) {
				hovered = slot;
			}
			LegacyUi.panel(ctx, x, y, x + tileW, y + 24, hot ? color : LegacyUi.RIM_DARK);
			RaceHud.drawAbility(ctx, race, slot, x + 4, y + 4, true);
			String key = "mages_legacy.ability." + race.id() + "." + (slot + 1);
			// A long name shrinks to fit (down to 70%) before it is ever cut
			String name = Component.translatable(key + ".name").getString();
			int room = tileW - 52;
			float scale = Math.max(0.7f, Math.min(1, room / (float) font.width(name)));
			ctx.pose().pushPose();
			ctx.pose().translate(x + 24, y + 3 + (1 - scale) * 4, 0);
			ctx.pose().scale(scale, scale, 1);
			ctx.drawString(font, font.plainSubstrByWidth(name, (int) (room / scale)), 0, 0, LegacyUi.GOLD);
			ctx.pose().popPose();
			int cx = x + 24;
			if (!RaceHud.locked(race, slot) && resourceIcon != null && LegacyUi.exists(LegacyUi.icon(resourceIcon))) {
				LegacyUi.icon(ctx, LegacyUi.icon(resourceIcon), cx - 1, y + 13, 9, true);
				cx += 10;
			}
			if (RaceHud.locked(race, slot)) {
				ctx.drawString(font, Component.translatable("mages_legacy.screen.opens_at", AbilitySlots.unlockLevel(race, slot)), x + 24, y + 13,
						LegacyUi.RED);
			} else {
				ctx.drawString(font, (RaceHud.cost(race, slot) <= 0 ? "—" : String.valueOf(RaceHud.cost(race, slot))), cx, y + 13,
						LegacyUi.mix(color, 0xFFFFFFFF, 0.3f));
			}
			LegacyUi.keyCap(ctx, MagesLegacyClient.keyLabel(MagesLegacyClient.ABILITY_KEYS[slot]), x + tileW - 4, y + 20, 0xFFFFFFFF);
		}
		return hovered;
	}

	public static List<Component> tooltip(Talent t) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable(t.key()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		wrapInto(lines, Component.translatable(t.key() + ".lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		wrapInto(lines, Component.translatable(t.key() + ".desc").withStyle(ChatFormatting.WHITE));
		lines.add(Component.translatable("mages_legacy.screen.cost", t.cost()).withStyle(ChatFormatting.YELLOW));
		NodeState state = state(t);
		switch (state) {
			case OWNED -> lines.add(Component.translatable("mages_legacy.screen.state.owned").withStyle(ChatFormatting.GOLD));
			case AVAILABLE -> lines.add(Component.translatable("mages_legacy.screen.state.available").withStyle(ChatFormatting.GREEN));
			case NO_POINTS -> lines.add(Component.translatable("mages_legacy.screen.state.no_points").withStyle(ChatFormatting.RED));
			case NEEDS_TALENT -> lines.add(Component.translatable("mages_legacy.screen.state.needs",
					Component.translatable("mages_legacy.talent." + t.requires())).withStyle(ChatFormatting.RED));
			case TIER_CLOSED -> lines.add(Component.translatable("mages_legacy.screen.state.tier_" + t.tier()).withStyle(ChatFormatting.DARK_RED));
		}
		return lines;
	}

	static void wrapInto(List<Component> lines, Component text) {
		// Tooltips do not wrap by themselves; split long lines at about 40 characters on spaces
		String plain = text.getString();
		StringBuilder line = new StringBuilder();
		for (String word : plain.split(" ")) {
			if (line.length() + word.length() > 40 && line.length() > 0) {
				lines.add(Component.literal(line.toString()).setStyle(text.getStyle()));
				line.setLength(0);
			}
			if (line.length() > 0) {
				line.append(' ');
			}
			line.append(word);
		}
		if (line.length() > 0) {
			lines.add(Component.literal(line.toString()).setStyle(text.getStyle()));
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
