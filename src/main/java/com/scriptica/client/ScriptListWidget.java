package com.scriptica.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class ScriptListWidget extends ClickableWidget {
	private static final float SCALE = 0.55f;

	private final TextRenderer textRenderer;
	private final Consumer<String> onOpen;

	private List<String> allScripts = new ArrayList<>();
	private List<String> scripts = new ArrayList<>();
	private int scroll = 0;
	private String filter = "";
	private String selected = "";

	public ScriptListWidget(TextRenderer textRenderer, int x, int y, int width, int height, Consumer<String> onOpen) {
		super(x, y, width, height, Text.empty());
		this.textRenderer = textRenderer;
		this.onOpen = onOpen;
	}

	public void refresh() {
		this.allScripts = ScriptStorage.listScriptNames();
		applyFilter();
	}

	public void setFilter(String filter) {
		String next = filter == null ? "" : filter;
		if (next.equals(this.filter)) return;
		this.filter = next;
		applyFilter();
	}

	public void setSelectedName(String name) {
		this.selected = name == null ? "" : name;
	}

	private void applyFilter() {
		String f = filter.trim().toLowerCase(Locale.ROOT);
		if (f.isEmpty()) {
			this.scripts = new ArrayList<>(allScripts);
			this.scroll = 0;
			return;
		}
		List<String> out = new ArrayList<>();
		for (String s : allScripts) {
			if (s.toLowerCase(Locale.ROOT).contains(f)) out.add(s);
		}
		this.scripts = out;
		this.scroll = 0;
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
		context.drawTextWithShadow(textRenderer, "Scripts", (int) ((x0 + 6) / SCALE), (int) ((y0 + 4) / SCALE), 0xFFE5E7EB);
		context.getMatrices().popMatrix();
		context.fill(x0 + 1, y0 + 14, x1 - 1, y0 + 17, 0x221F2937);

		int lineHeight = (int) ((textRenderer.fontHeight + 2) * SCALE);
		int listTop = y0 + 18;
		int listBottom = y1 - 4;
		int maxVisible = Math.max(1, (listBottom - listTop) / lineHeight);
		int maxScroll = Math.max(0, scripts.size() - maxVisible);
		scroll = Math.max(0, Math.min(maxScroll, scroll));

		int y = listTop;
		for (int i = 0; i < maxVisible; i++) {
			int idx = scroll + i;
			if (idx >= scripts.size()) break;
			String name = scripts.get(idx);

			boolean isSel = name.equalsIgnoreCase(selected);
			if (isSel) {
				context.fill(x0 + 2, y - 1, x1 - 6, y + lineHeight - 1, 0xFF1D4ED8);
			}
			int color = isSel ? 0xFFFFFFFF : 0xFFCBD5E1;

			context.getMatrices().pushMatrix();
			context.getMatrices().scale(SCALE, SCALE);
			context.drawTextWithShadow(textRenderer, name, (int) ((x0 + 8) / SCALE), (int) (y / SCALE), color);
			context.getMatrices().popMatrix();

			y += lineHeight;
		}

		if (maxScroll > 0) {
			int barX0 = x1 - 4;
			int barX1 = x1 - 2;
			int trackTop = listTop;
			int trackBottom = listBottom;
			context.fill(barX0, trackTop, barX1, trackBottom, 0xFF111827);
			int thumbH = Math.max(10, (trackBottom - trackTop) * maxVisible / scripts.size());
			int thumbY = trackTop + (int) ((trackBottom - trackTop - thumbH) * (scroll / (double) maxScroll));
			context.fill(barX0, thumbY, barX1, thumbY + thumbH, 0xFF475569);
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		scroll = scroll - (int) Math.signum(verticalAmount);
		return true;
	}

	@Override
	public void onClick(Click click, boolean doubled) {
		int y0 = getY();
		int lineHeight = (int) ((textRenderer.fontHeight + 2) * SCALE);
		int listTop = y0 + 18;
		int relY = (int) (click.y() - listTop);
		if (relY < 0) return;
		int idx = scroll + (relY / lineHeight);
		if (idx < 0 || idx >= scripts.size()) return;

		String name = scripts.get(idx);
		selected = name;
		if (onOpen != null) onOpen.accept(name);
	}

	@Override
	protected void appendClickableNarrations(NarrationMessageBuilder builder) {
		builder.put(NarrationPart.TITLE, Text.literal("Script list"));
	}
}

