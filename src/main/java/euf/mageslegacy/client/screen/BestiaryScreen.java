package euf.mageslegacy.client.screen;

import euf.mageslegacy.client.ClientRaceState;
import euf.mageslegacy.client.MagesLegacyClient;
import euf.mageslegacy.client.RaceHud;
import euf.mageslegacy.race.Race;
import euf.mageslegacy.race.Talents;
import euf.mageslegacy.shepherd.BeastKind;
import euf.mageslegacy.shepherd.Bonds;
import euf.mageslegacy.shepherd.EchoType;
import euf.mageslegacy.shepherd.Shepherd;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The Shepherd's bestiary. Left: three squads of echoes on tabs (the Call brings the active one all at once), the
 * active squad's make-up and price, then every echo with its progress: left click adds one to the squad, right click
 * takes one away. Right: the bonded beasts, the beasts listening to a Song right now, and every beast that can be
 * bonded with its own way in the tooltip.
 */
public class BestiaryScreen extends Screen {
	private static final int CARD_H = 22;
	private static final int SQUAD_H = 40;
	private static final int SPIRIT = 0xFF9FE8E0;

	private int echoScroll;
	/** The bonded beast being renamed, or -1; the field sits over its row. */
	private int renaming = -1;
	private EditBox nameField;

	public BestiaryScreen() {
		super(Component.translatable("mages_legacy.bestiary.title"));
	}

	@Override
	protected void init() {
		nameField = addRenderableWidget(new EditBox(font, 0, 0, 100, 10, Component.translatable("mages_legacy.bestiary.name")));
		nameField.setMaxLength(24);
		nameField.setBordered(false);
		nameField.setTextColor(LegacyUi.TEXT);
		nameField.visible = false;
		renaming = -1;
	}

	private void finishRename() {
		if (renaming >= 0) {
			MagesLegacyClient.renameBeast(renaming, nameField.getValue());
		}
		renaming = -1;
		nameField.visible = false;
		nameField.setFocused(false);
		setFocused(null);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (renaming >= 0 && (keyCode == 257 || keyCode == 335)) { // Enter
			finishRename();
			return true;
		}
		if (renaming >= 0 && keyCode == 256) { // Escape cancels the rename, not the screen
			renaming = -1;
			nameField.visible = false;
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void tick() {
		
	}

	/** Beast rows on the right, as {x, y, w} for row {@code index}. */
	private int[] beastRow(int index) {
		return new int[] {rightX(), top() + 12 + index * 23, beastWidth()};
	}

	private CompoundTag extra() {
		return ClientRaceState.extra;
	}

	private boolean quiet() {
		return ClientRaceState.talents.contains(Talents.LEGION_CHEAP);
	}

	private boolean learned(EchoType type) {
		return extra().getCompound("kills").getInt(type.id()) >= type.killsNeeded();
	}

	private int active() {
		return Math.floorMod(extra().getInt("activePreset"), 3);
	}

	/** The echoes get the wider column (their names are long); the beasts and the ritual the rest. */
	private int colWidth() {
		return Math.min(300, (width - 40) * 58 / 100);
	}

	private int beastWidth() {
		return Math.min(260, width - 40 - colWidth());
	}

	private int leftX() {
		return (width - colWidth() - beastWidth() - 16) / 2;
	}

	private int rightX() {
		return leftX() + colWidth() + 16;
	}

	private int top() {
		return 40;
	}

	private int cardW() {
		return (colWidth() - 6) / 2;
	}

	/** Where the echo cards start and end on screen; they scroll when there are more than fit. */
	private int cardsTop() {
		return top() + 12 + SQUAD_H + 8;
	}

	private int cardsBottom() {
		return height - 14;
	}

	private int maxScroll() {
		int rows = (EchoType.ALL.size() + 1) / 2;
		return Math.max(0, rows * (CARD_H + 3) - (cardsBottom() - cardsTop()));
	}

	private int[] cardPos(int index) {
		return new int[] {leftX() + (index % 2) * (cardW() + 6), cardsTop() + (index / 2) * (CARD_H + 3) - echoScroll};
	}

	private int[] tabPos(int index) {
		int w = (colWidth() - 8) / 3;
		return new int[] {leftX() + index * (w + 4), top() + 12, w};
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (renaming >= 0 && nameField.isMouseOver(mouseX, mouseY)) {
			return super.mouseClicked(mouseX, mouseY, button);
		}
		finishRename();
		// A bonded beast: left click names it, right click makes it the leader
		ListTag bonds = extra().getList("bonds", Tag.TAG_COMPOUND);
		for (int i = 0; i < bonds.size(); i++) {
			int[] r = beastRow(i);
			if (mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + 20) {
				if (button == 1) {
					MagesLegacyClient.toggleLeader(i);
				} else {
					renaming = i;
					nameField.setX(r[0] + 21);
					nameField.setY(r[1] + 3);
					nameField.setWidth(r[2] - 60);
					nameField.setValue(bonds.getCompound(i).getString("name"));
					nameField.visible = true;
					setFocused(nameField);
					nameField.setFocused(true);
				}
				return true;
			}
		}
		for (int i = 0; i < 3; i++) {
			int[] t = tabPos(i);
			if (mouseX >= t[0] && mouseX < t[0] + t[2] && mouseY >= t[1] && mouseY < t[1] + 13) {
				MagesLegacyClient.selectSquad(i);
				return true;
			}
		}
		if (mouseY >= cardsTop() && mouseY < cardsBottom()) {
			for (int i = 0; i < EchoType.ALL.size(); i++) {
				int[] p = cardPos(i);
				if (mouseX >= p[0] && mouseX < p[0] + cardW() && mouseY >= p[1] && mouseY < p[1] + CARD_H) {
					EchoType type = EchoType.ALL.get(i);
					if (button == 1) {
						edit(type.id(), -1);
					} else if (learned(type)) {
						edit(type.id(), 1);
					}
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double amount) {
		if (mouseX < leftX() + colWidth()) {
			echoScroll = Mth.clamp(echoScroll - (int) (amount * 20), 0, maxScroll());
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, amount);
	}

	/** One more (+1) or one less (-1) of this echo in the active squad (also used by the harness). */
	public void edit(String id, int delta) {
		MagesLegacyClient.editSquad(active(), id, delta);
	}

	@Override
	public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
		int color = LegacyUi.argb(Race.SHEPHERD.color);
		LegacyUi.background(ctx, width, height, Race.SHEPHERD);
		LegacyUi.heading(ctx, title.copy().withStyle(ChatFormatting.BOLD), width / 2, 8, LegacyUi.GOLD);
		int used = extra().getInt("capUsed");
		int max = extra().getInt("capMax");
		Component cap = Component.translatable("mages_legacy.bestiary.capacity", used, max);
		ctx.drawCenteredString(font, cap, width / 2, 21, used >= max ? LegacyUi.RED : LegacyUi.GOLD);

		echoScroll = Mth.clamp(echoScroll, 0, maxScroll());
		List<Component> tooltip = drawEchoes(ctx, color, mouseX, mouseY);
		List<Component> beastTooltip = drawBeasts(ctx, color, mouseX, mouseY);
		super.render(ctx, mouseX, mouseY, delta);
		if (tooltip != null) {
			ctx.renderComponentTooltip(font, tooltip, mouseX, mouseY);
		} else if (beastTooltip != null) {
			ctx.renderComponentTooltip(font, beastTooltip, mouseX, mouseY);
		}
	}

	private List<Component> drawEchoes(GuiGraphics ctx, int color, int mouseX, int mouseY) {
		int x = leftX();
		int w = colWidth();
		LegacyUi.panel(ctx, x - 6, top() - 6, x + w + 6, height - 8);
		ctx.drawString(font, Component.translatable("mages_legacy.bestiary.echoes").withStyle(ChatFormatting.BOLD), x, top(), SPIRIT);
		Component hint = Component.translatable("mages_legacy.bestiary.squad_hint");
		ctx.drawString(font, hint, x + w - font.width(hint), top(), LegacyUi.MUTED);
		List<Component> tooltip = null;

		// Squad tabs, then the active squad's make-up and price
		for (int i = 0; i < 3; i++) {
			int[] t = tabPos(i);
			boolean on = i == active();
			boolean hot = mouseX >= t[0] && mouseX < t[0] + t[2] && mouseY >= t[1] && mouseY < t[1] + 13;
			LegacyUi.button(ctx, t[0], t[1], t[2], 13, true, hot, on, SPIRIT);
			ctx.drawCenteredString(font, Component.translatable("mages_legacy.bestiary.squad", Shepherd.ROMAN[i]), t[0] + t[2] / 2, t[1] + 3,
					on ? 0xFFFFFFFF : LegacyUi.MUTED);
		}
		int sy = top() + 30;
		Map<String, Integer> squad = RaceHud.activeSquad();
		CompoundTag alive = extra().getCompound("alive");
		int ix = x;
		int voice = 0;
		int slots = 0;
		if (squad.isEmpty()) {
			ctx.drawString(font, Component.translatable("mages_legacy.bestiary.squad_empty").withStyle(ChatFormatting.ITALIC), x, sy + 4,
					LegacyUi.MUTED);
		}
		for (Map.Entry<String, Integer> e : squad.entrySet()) {
			EchoType type = EchoType.byId(e.getKey()).orElse(null);
			if (type == null) {
				continue;
			}
			voice += type.voiceCost(quiet()) * e.getValue();
			slots += type.capacity() * e.getValue();
			if (ix + 34 > x + w - 70) {
				continue;
			}
			LegacyUi.portrait(ctx, "echo", type.path(), type.icon(), ix, sy, true);
			String count = "×" + e.getValue();
			boolean all = type.units(alive.getInt(e.getKey())) >= e.getValue();
			ctx.drawString(font, count, ix + 16, sy + 5, all ? LegacyUi.GREEN : 0xFFFFFFFF);
			ix += 20 + font.width(count);
		}
		if (!squad.isEmpty()) {
			String price = voice + "  ◆" + slots;
			ctx.drawString(font, price, x + w - font.width(price), sy + 5, slots > extra().getInt("capMax") ? LegacyUi.RED : SPIRIT);
		}

		// Every echo; left click adds to the squad, right click takes away
		ctx.enableScissor(x - 4, cardsTop() - 2, x + w + 4, cardsBottom());
		for (int i = 0; i < EchoType.ALL.size(); i++) {
			EchoType type = EchoType.ALL.get(i);
			int[] p = cardPos(i);
			if (p[1] + CARD_H < cardsTop() || p[1] > cardsBottom()) {
				continue;
			}
			boolean known = learned(type);
			int inSquad = squad.getOrDefault(type.id(), 0);
			boolean hot = mouseY >= cardsTop() && mouseY < cardsBottom() && mouseX >= p[0] && mouseX < p[0] + cardW() && mouseY >= p[1]
					&& mouseY < p[1] + CARD_H;
			if (hot) {
				tooltip = echoTooltip(type, inSquad);
			}
			int rim = inSquad > 0 ? SPIRIT : hot && known ? color : known ? LegacyUi.RIM : LegacyUi.RIM_DARK;
			LegacyUi.panel(ctx, p[0], p[1], p[0] + cardW(), p[1] + CARD_H, rim);
			LegacyUi.portrait(ctx, "echo", type.path(), type.icon(), p[0] + 3, p[1] + 3, known);
			// A portrait dims by itself; only a spawn egg standing in for one needs the veil
			if (!known && !LegacyUi.exists(LegacyUi.gui("echo/" + type.path()))) {
				ctx.pose().pushPose();
				ctx.pose().translate(0, 0, 200);
				ctx.fill(p[0] + 3, p[1] + 3, p[0] + 19, p[1] + 19, 0xA0000000);
				ctx.pose().popPose();
			}
			int tx = p[0] + 22;
			int tw = cardW() - 25;
			String badge = inSquad > 0 ? "×" + inSquad : "";
			int nameW = tw - (badge.isEmpty() ? 0 : font.width(badge) + 3);
			ctx.drawString(font, font.plainSubstrByWidth(type.type().getDescription().getString(), nameW), tx, p[1] + 3,
					known ? LegacyUi.TEXT : LegacyUi.MUTED);
			if (!badge.isEmpty()) {
				ctx.drawString(font, badge, tx + tw - font.width(badge), p[1] + 3, SPIRIT);
			}
			int kills = Math.min(extra().getCompound("kills").getInt(type.id()), type.killsNeeded());
			if (known) {
				String price = type.voiceCost(quiet()) + "  ◆" + type.capacity() + (type.group() > 1 ? "  (" + type.group() + ")" : "");
				ctx.drawString(font, price, tx, p[1] + 12, SPIRIT);
			} else {
				// Progress towards learning it: the count, and a thin bar beside it that fills with kills
				int need = type.killsNeeded();
				String count = kills + "/" + need;
				ctx.drawString(font, count, tx, p[1] + 12, LegacyUi.MUTED);
				int bx = tx + font.width(count) + 4;
				int bw = Math.max(4, tx + tw - bx);
				ctx.fill(bx, p[1] + 14, bx + bw, p[1] + 17, LegacyUi.BLACK);
				ctx.fill(bx, p[1] + 14, bx + bw * kills / need, p[1] + 17, LegacyUi.mix(color, SPIRIT, 0.5f));
			}
		}
		ctx.disableScissor();
		if (maxScroll() > 0) {
			int track = cardsBottom() - cardsTop();
			int thumb = Math.max(12, track * track / (track + maxScroll()));
			int ty = cardsTop() + (track - thumb) * echoScroll / maxScroll();
			ctx.fill(x + w + 2, cardsTop(), x + w + 5, cardsBottom(), LegacyUi.BLACK);
			ctx.fill(x + w + 2, ty, x + w + 5, ty + thumb, SPIRIT);
		}
		return tooltip;
	}

	/** The bonded beasts, those listening to a Song, and who can be bonded; returns the tooltip under the mouse. */
	private List<Component> drawBeasts(GuiGraphics ctx, int color, int mouseX, int mouseY) {
		List<Component> tooltip = null;
		int x = rightX();
		int w = beastWidth();
		LegacyUi.panel(ctx, x - 6, top() - 6, x + w + 6, height - 8);
		ctx.drawString(font, Component.translatable("mages_legacy.bestiary.beasts").withStyle(ChatFormatting.BOLD), x, top(), color);
		// How to bond and care, behind a "?" in the corner: the column keeps its room for the beasts themselves
		int qx = x + w - 11;
		boolean helpHot = mouseX >= qx && mouseX < qx + 11 && mouseY >= top() - 2 && mouseY < top() + 9;
		LegacyUi.button(ctx, qx, top() - 2, 11, 11, true, helpHot, false, color);
		ctx.drawCenteredString(font, "?", qx + 6, top(), helpHot ? 0xFFFFFFFF : LegacyUi.GOLD);
		if (helpHot) {
			tooltip = new ArrayList<>();
			tooltip.add(Component.translatable("mages_legacy.bestiary.help").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
			for (Component step : Lines.of("mages_legacy.bestiary.ritual", MagesLegacyClient.keyLabel(MagesLegacyClient.ABILITY_KEYS[Bonds.SONG_SLOT]))) {
				TalentScreen.wrapInto(tooltip, step.copy().withStyle(ChatFormatting.WHITE));
			}
		}
		int y = top() + 12;

		// The bottom block: every beast that can be bonded
		int perRow = Math.max(1, (w + 2) / 19);
		int iconRows = (BeastKind.ALL.size() + perRow - 1) / perRow;
		int bottomH = 8 + 12 + iconRows * 19;
		int bottomY = height - 14 - bottomH;

		ListTag bonds = extra().getList("bonds", Tag.TAG_COMPOUND);
		boolean alphaCrown = ClientRaceState.talents.contains(Talents.PACK_ALPHA);
		for (int i = 0; i < bonds.size() && y + 20 < bottomY; i++) {
			CompoundTag b = bonds.getCompound(i);
			BeastKind kind = BeastKind.byId(b.getString("kind")).orElse(null);
			if (kind == null) {
				continue;
			}
			y = beastRow(i)[1];
			boolean hot = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 20;
			boolean leader = b.getBoolean("leader");
			LegacyUi.panel(ctx, x, y, x + w, y + 20, leader ? LegacyUi.GOLD : hot ? color : LegacyUi.RIM_DARK);
			LegacyUi.portrait(ctx, "beast", kind.id(), kind.icon(), x + 2, y + 2, true);
			if (leader) {
				// A small crown over the leader's icon
				ctx.pose().pushPose();
				ctx.pose().translate(0, 0, 250);
				ctx.drawString(font, "♛", x + 1, y - 2, LegacyUi.GOLD, true);
				ctx.pose().popPose();
			}
			String name = b.getString("name").isEmpty() ? Component.translatable("mages_legacy.beast." + kind.id()).getString() : b.getString("name");
			Component level = Component.translatable("mages_legacy.screen.level", b.getInt("level"));
			if (renaming != i) {
				ctx.drawString(font, font.plainSubstrByWidth(name, w - 26 - font.width(level)), x + 21, y + 2, LegacyUi.TEXT);
			} else {
				ctx.fill(x + 19, y + 1, x + w - 36, y + 12, LegacyUi.BLACK);
			}
			ctx.drawString(font, level, x + w - 3 - font.width(level), y + 2, LegacyUi.GOLD);
			int rest = b.getInt("restSeconds");
			boolean staying = b.getBoolean("staying");
			Component line = rest > 0 ? Component.translatable("mages_legacy.bestiary.resting", (rest + 59) / 60).withStyle(ChatFormatting.YELLOW)
					: staying ? Component.translatable("mages_legacy.bestiary.staying").withStyle(ChatFormatting.AQUA)
					: Component.translatable("mages_legacy.beast." + kind.id() + ".skill").withStyle(ChatFormatting.GRAY);
			// Care marks on the right of the second line: a steaming bowl while fed, a heart while it wants petting
			int fed = b.getInt("fedSeconds");
			String fedMark = fed > 0 ? "♨" : "";
			String petMark = b.getBoolean("petReady") && rest == 0 ? "❤" : "";
			String marks = fedMark + petMark;
			int marksW = marks.isEmpty() ? 0 : font.width(marks) + 3;
			ctx.drawString(font, Language.getInstance().getVisualOrder(font.substrByWidth(line, w - 24 - marksW)), x + 21, y + 11, 0xFFFFFFFF);
			int mx = x + w - 3 - font.width(marks);
			ctx.drawString(font, fedMark, mx, y + 11, LegacyUi.GREEN);
			ctx.drawString(font, petMark, mx + font.width(fedMark), y + 11, 0xFFFF7EB6);
			if (hot) {
				tooltip = new ArrayList<>();
				tooltip.add(Component.literal(name).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
				tooltip.add(Component.translatable("mages_legacy.beast." + kind.id() + ".skill").withStyle(ChatFormatting.YELLOW));
				TalentScreen.wrapInto(tooltip, Component.translatable("mages_legacy.beast." + kind.id() + ".skill_desc").withStyle(ChatFormatting.GRAY));
				tooltip.add(Component.translatable("mages_legacy.bestiary.beast_level", b.getInt("level"), b.getInt("xp"), Bonds.xpForNext(b.getInt("level")))
						.withStyle(ChatFormatting.GREEN));
				int power = b.getInt("level") / 5 + (ClientRaceState.talents.contains(Talents.PACK_SKILL) ? 1 : 0) + (leader && alphaCrown ? 2 : 0);
				tooltip.add(Component.translatable("mages_legacy.bestiary.power", power).withStyle(ChatFormatting.AQUA));
				if (leader) {
					tooltip.add(Component.translatable(alphaCrown ? "mages_legacy.bestiary.leader" : "mages_legacy.bestiary.leader_no_crown")
							.withStyle(ChatFormatting.GOLD));
				}
				// Care: fed or hungry (and what it eats), whether it would like petting, whether it waits somewhere
				tooltip.add(fed > 0 ? Component.translatable("mages_legacy.bestiary.fed", (fed + 59) / 60).withStyle(ChatFormatting.GREEN)
						: Component.translatable("mages_legacy.bestiary.hungry", Component.translatable("mages_legacy.beast." + kind.id() + ".treat"))
								.withStyle(ChatFormatting.YELLOW));
				if (b.getBoolean("petReady") && rest == 0) {
					tooltip.add(Component.translatable("mages_legacy.bestiary.wants_pet").withStyle(ChatFormatting.LIGHT_PURPLE));
				}
				if (staying) {
					tooltip.add(Component.translatable("mages_legacy.bestiary.staying").withStyle(ChatFormatting.AQUA));
				}
				tooltip.add(Component.translatable("mages_legacy.bestiary.beast_hint").withStyle(ChatFormatting.DARK_GRAY));
			}
			y += 23;
		}
		if (bonds.isEmpty()) {
			ctx.drawString(font, Component.translatable("mages_legacy.bestiary.no_beasts").withStyle(ChatFormatting.ITALIC), x, y, LegacyUi.MUTED);
			y += 12;
		}

		// Beasts listening to a Song right now, with how far their ritual has come
		ListTag rituals = extra().getList("rituals", Tag.TAG_COMPOUND);
		if (!rituals.isEmpty() && y + 26 < bottomY) {
			y += 4;
			ctx.drawString(font, Component.translatable("mages_legacy.bestiary.listening").withStyle(ChatFormatting.BOLD), x, y, LegacyUi.GOLD);
			y += 11;
			for (int i = 0; i < rituals.size() && y + 18 < bottomY; i++) {
				CompoundTag r = rituals.getCompound(i);
				BeastKind kind = BeastKind.byId(r.getString("kind")).orElse(null);
				if (kind == null) {
					continue;
				}
				boolean hot = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 18;
				LegacyUi.portrait(ctx, "beast", kind.id(), kind.icon(), x, y, true);
				Component name = Component.translatable("mages_legacy.beast." + kind.id());
				String progress = r.getInt("progress") + "/" + Bonds.needed(kind);
				ctx.drawString(font, name, x + 19, y + 4, LegacyUi.TEXT);
				int bx = x + 19 + font.width(name) + 6;
				ctx.drawString(font, progress, bx, y + 4, LegacyUi.GREEN);
				Component left = Component.translatable("mages_legacy.bestiary.listen_left", r.getInt("minutes"));
				ctx.drawString(font, left, x + w - font.width(left), y + 4, LegacyUi.MUTED);
				if (hot) {
					tooltip = beastKindTooltip(kind);
				}
				y += 18;
			}
		}

		y = Math.max(y + 4, bottomY);
		LegacyUi.divider(ctx, x, x + w, y, color);
		y += 8;
		ctx.drawString(font, Component.translatable("mages_legacy.bestiary.tameable").withStyle(ChatFormatting.BOLD), x, y + 2, color);
		y += 13;
		for (int i = 0; i < BeastKind.ALL.size(); i++) {
			BeastKind kind = BeastKind.ALL.get(i);
			int ix = x + (i % perRow) * 19;
			int iy = y + (i / perRow) * 19;
			boolean hot = mouseX >= ix && mouseX < ix + 18 && mouseY >= iy && mouseY < iy + 18;
			ctx.fill(ix, iy, ix + 18, iy + 18, LegacyUi.BLACK);
			LegacyUi.outline(ctx, ix, iy, ix + 18, iy + 18, hot ? color : LegacyUi.RIM_DARK);
			LegacyUi.portrait(ctx, "beast", kind.id(), kind.icon(), ix + 1, iy + 1, true);
			if (hot) {
				tooltip = beastKindTooltip(kind);
			}
		}
		return tooltip;
	}

	private List<Component> beastKindTooltip(BeastKind kind) {
		List<Component> lines = new ArrayList<>();
		String id = "mages_legacy.beast." + kind.id();
		lines.add(Component.translatable(id).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		lines.add(Component.translatable(id + ".skill").withStyle(ChatFormatting.YELLOW));
		TalentScreen.wrapInto(lines, Component.translatable(id + ".skill_desc").withStyle(ChatFormatting.GRAY));
		int needed = Bonds.needed(kind);
		TalentScreen.wrapInto(lines, Component.translatable("mages_legacy.bestiary.taming", Component.translatable(id + ".taming", needed).getString())
				.withStyle(ChatFormatting.WHITE));
		lines.add(Component.translatable("mages_legacy.bestiary.beast_cost", Shepherd.beastCost(kind, ClientRaceState.talents.contains(Talents.PACK_LIGHT)))
				.withStyle(ChatFormatting.AQUA));
		lines.add(Component.translatable("mages_legacy.bestiary.treat", Component.translatable(id + ".treat")).withStyle(ChatFormatting.GRAY));
		return lines;
	}

	private List<Component> echoTooltip(EchoType type, int inSquad) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("mages_legacy.echo.name", type.type().getDescription()).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
		TalentScreen.wrapInto(lines, Component.translatable("mages_legacy.echo.trait." + type.path())
				.withStyle(ChatFormatting.YELLOW));
		if (type.group() > 1) {
			lines.add(Component.translatable("mages_legacy.bestiary.group", type.group()).withStyle(ChatFormatting.GRAY));
		}
		int kills = extra().getCompound("kills").getInt(type.id());
		int need = type.killsNeeded();
		if (kills >= need) {
			lines.add(Component.translatable("mages_legacy.bestiary.cost", type.voiceCost(quiet()), type.capacity()).withStyle(ChatFormatting.GRAY));
			lines.add(Component.translatable("mages_legacy.bestiary.in_squad", inSquad).withStyle(ChatFormatting.AQUA));
			lines.add(Component.translatable("mages_legacy.bestiary.pick").withStyle(ChatFormatting.GREEN));
		} else {
			lines.add(Component.translatable("mages_legacy.bestiary.need", need - kills).withStyle(ChatFormatting.GRAY));
		}
		return lines;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
