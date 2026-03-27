package com.scriptica.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

public final class FlatButtonWidget extends ClickableWidget {
	private static final float SCALE = 0.55f;

	private final TextRenderer textRenderer;
	private final Runnable onPress;
	private final int baseColor;
	private final int hoverColor;
	private final int textColor;

	public FlatButtonWidget(TextRenderer textRenderer, int x, int y, int width, int height, String label, Runnable onPress) {
		this(textRenderer, x, y, width, height, label, onPress,
			0x1A0F172A,
			0x331F2937,
			0xFFE5E7EB);
	}

	public FlatButtonWidget(
		TextRenderer textRenderer,
		int x,
		int y,
		int width,
		int height,
		String label,
		Runnable onPress,
		int baseColor,
		int hoverColor,
		int textColor
	) {
		super(x, y, width, height, Text.literal(label));
		this.textRenderer = textRenderer;
		this.onPress = onPress;
		this.baseColor = baseColor;
		this.hoverColor = hoverColor;
		this.textColor = textColor;
	}

	@Override
	protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
		int x0 = getX();
		int y0 = getY();
		int x1 = x0 + width;
		int y1 = y0 + height;

		boolean hovered = isMouseOver(mouseX, mouseY);
		int bg;
		if (!active) bg = 0x1A0F172A;
		else if (hovered) bg = hoverColor;
		else bg = baseColor;

		context.fill(x0, y0, x1, y1, bg);
		context.fill(x0, y0, x1, y0 + 1, 0x111E293B);
		context.fill(x0, y1 - 1, x1, y1, 0x111E293B);
		context.fill(x0, y0, x0 + 1, y1, 0x111E293B);
		context.fill(x1 - 1, y0, x1, y1, 0x111E293B);

		String label = getMessage().getString();
		int tw = (int) (textRenderer.getWidth(label) * SCALE);
		int tx = x0 + (width - tw) / 2;
		int ty = y0 + (height - (int) (textRenderer.fontHeight * SCALE)) / 2;
		int tc = active ? textColor : 0xFF64748B;

		context.getMatrices().pushMatrix();
		context.getMatrices().scale(SCALE, SCALE);
		context.drawTextWithShadow(textRenderer, label, (int) (tx / SCALE), (int) (ty / SCALE), tc);
		context.getMatrices().popMatrix();
	}

	@Override
	public void onClick(Click click, boolean doubled) {
		if (!active) return;
		if (onPress != null) onPress.run();
	}

	@Override
	protected void appendClickableNarrations(NarrationMessageBuilder builder) {
		builder.put(NarrationPart.TITLE, getMessage());
	}
}

