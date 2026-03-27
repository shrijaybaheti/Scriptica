package com.scriptica.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.List;

public final class ConsoleWidget extends ClickableWidget {
	private static final float SCALE = 0.55f;

	private final TextRenderer textRenderer;
	private int scroll = 0;

	public ConsoleWidget(TextRenderer textRenderer, int x, int y, int width, int height) {
		super(x, y, width, height, Text.empty());
		this.textRenderer = textRenderer;
	}

	@Override
	protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
		int x0 = getX();
		int y0 = getY();
		int x1 = x0 + width;
		int y1 = y0 + height;

		context.fill(x0, y0, x1, y1, 0x330B1220);
		context.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0x220F172A);

		context.getMatrices().pushMatrix();
		context.getMatrices().scale(SCALE, SCALE);
		context.drawTextWithShadow(textRenderer, "Console", (int) ((x0 + 6) / SCALE), (int) ((y0 + 4) / SCALE), 0xFFE5E7EB);
		context.getMatrices().popMatrix();
		context.fill(x0 + 1, y0 + 14, x1 - 1, y0 + 17, 0x221F2937);

		List<String> lines = ScripticaLog.snapshot();
		int lineHeight = (int) ((textRenderer.fontHeight + 1) * SCALE);
		int top = y0 + 18;
		int bottom = y1 - 4;
		int maxVisible = Math.max(1, (bottom - top) / lineHeight);
		int maxScroll = Math.max(0, lines.size() - maxVisible);
		scroll = Math.max(0, Math.min(maxScroll, scroll));

		int start = Math.max(0, lines.size() - maxVisible - scroll);
		int y = top;
		for (int i = 0; i < maxVisible; i++) {
			int idx = start + i;
			if (idx >= lines.size()) break;
			String msg = lines.get(idx);

			context.getMatrices().pushMatrix();
			context.getMatrices().scale(SCALE, SCALE);
			context.drawTextWithShadow(textRenderer, msg, (int) ((x0 + 6) / SCALE), (int) (y / SCALE), 0xFFCBD5E1);
			context.getMatrices().popMatrix();

			y += lineHeight;
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		scroll = scroll - (int) Math.signum(verticalAmount);
		return true;
	}

	@Override
	protected void appendClickableNarrations(NarrationMessageBuilder builder) {
		builder.put(NarrationPart.TITLE, Text.literal("Script console"));
	}
}

