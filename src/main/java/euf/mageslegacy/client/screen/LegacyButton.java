package euf.mageslegacy.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** A button in the Mage's Legacy style ({@link LegacyUi#button}): an optional icon and a label, centred. */
public class LegacyButton extends Button {
	@Nullable
	private ResourceLocation icon;
	private boolean primary;
	private int accent = LegacyUi.GOLD;

	public LegacyButton(int x, int y, int width, int height, Component message, OnPress onPress) {
		super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
	}

	public LegacyButton primary() {
		this.primary = true;
		return this;
	}

	public LegacyButton icon(@Nullable ResourceLocation icon) {
		this.icon = icon;
		return this;
	}

	public LegacyButton accent(int accent) {
		this.accent = accent;
		return this;
	}

	@Override
	protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
		boolean hot = active && isHoveredOrFocused();
		LegacyUi.button(context, getX(), getY(), width, height, active, hot, primary, accent);
		Font tr = Minecraft.getInstance().font;
		Component message = getMessage();
		int textW = tr.width(message);
		int iconW = icon == null || !LegacyUi.exists(icon) ? 0 : 16;
		int gap = iconW > 0 && textW > 0 ? 4 : 0;
		int x = getX() + (width - iconW - gap - textW) / 2;
		if (iconW > 0) {
			LegacyUi.icon(context, icon, x, getY() + (height - 16) / 2, 16, active);
			x += iconW + gap;
		}
		int color = !active ? 0xFF6A6068 : hot ? 0xFFFFFFFF : LegacyUi.TEXT;
		context.drawString(tr, message, x, getY() + (height - 8) / 2, color);
	}
}
