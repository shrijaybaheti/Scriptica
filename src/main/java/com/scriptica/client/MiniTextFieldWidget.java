package com.scriptica.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

import java.util.Objects;
import java.util.function.Consumer;

public final class MiniTextFieldWidget extends ClickableWidget {
	private static final float SCALE = 0.55f;

	private final TextRenderer textRenderer;
	private String text = "";
	private String placeholder = "";
	private int cursor = 0;
	private Consumer<String> changedListener;

	public MiniTextFieldWidget(TextRenderer textRenderer, int x, int y, int width, int height, String placeholder) {
		super(x, y, width, height, Text.empty());
		this.textRenderer = Objects.requireNonNull(textRenderer, "textRenderer");
		this.placeholder = placeholder == null ? "" : placeholder;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text == null ? "" : text;
		this.cursor = Math.max(0, Math.min(this.cursor, this.text.length()));
		onChanged();
	}

	public void setPlaceholder(String placeholder) {
		this.placeholder = placeholder == null ? "" : placeholder;
	}

	public void setChangedListener(Consumer<String> changedListener) {
		this.changedListener = changedListener;
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (!isFocused()) return false;

		if (input.isPaste()) {
			String clip = ScripticaClientMod.client().keyboard.getClipboard();
			insertString(clip);
			return true;
		}

		int key = input.getKeycode();
		switch (key) {
			case 259 -> { // backspace
				if (cursor > 0) {
					text = text.substring(0, cursor - 1) + text.substring(cursor);
					cursor--;
					onChanged();
				}
				return true;
			}
			case 261 -> { // delete
				if (cursor < text.length()) {
					text = text.substring(0, cursor) + text.substring(cursor + 1);
					onChanged();
				}
				return true;
			}
			case 263 -> { // left
				cursor = Math.max(0, cursor - 1);
				return true;
			}
			case 262 -> { // right
				cursor = Math.min(text.length(), cursor + 1);
				return true;
			}
			case 268 -> { // home
				cursor = 0;
				return true;
			}
			case 269 -> { // end
				cursor = text.length();
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	@Override
	public boolean charTyped(CharInput input) {
		if (!isFocused()) return false;
		if (!input.isValidChar()) return false;
		String s = input.asString();
		if (s.equals("\r") || s.equals("\n")) return true;
		if (s.equals("\t")) {
			insertString("  ");
			return true;
		}
		insertString(s);
		return true;
	}

	private void insertString(String s) {
		if (s == null || s.isEmpty()) return;
		text = text.substring(0, cursor) + s + text.substring(cursor);
		cursor += s.length();
		onChanged();
	}

	private void onChanged() {
		if (changedListener != null) changedListener.accept(text);
	}

	@Override
	public void onClick(Click click, boolean doubled) {
		setFocused(true);

		String display = text;
		if (display.isEmpty()) {
			cursor = 0;
			return;
		}

		int x0 = getX() + 6;
		int relX = (int) (click.x() - x0);
		double unscaledX = relX / SCALE;

		int col = 0;
		for (int i = 1; i <= display.length(); i++) {
			if (textRenderer.getWidth(display.substring(0, i)) > unscaledX) break;
			col = i;
		}
		cursor = col;
	}

	@Override
	protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
		int x0 = getX();
		int y0 = getY();
		int x1 = x0 + width;
		int y1 = y0 + height;

		int border = isFocused() ? 0xFF38BDF8 : 0x111E293B;
		context.fill(x0, y0, x1, y1, 0x1A0F172A);
		context.fill(x0, y0, x1, y0 + 1, border);
		context.fill(x0, y1 - 1, x1, y1, border);
		context.fill(x0, y0, x0 + 1, y1, border);
		context.fill(x1 - 1, y0, x1, y1, border);

		String display = text.isEmpty() ? placeholder : text;
		int color = text.isEmpty() ? 0xFF64748B : 0xFFE5E7EB;

		int tx = x0 + 6;
		int ty = y0 + (height - (int) (textRenderer.fontHeight * SCALE)) / 2;

		context.enableScissor(x0 + 2, y0 + 2, x1 - 2, y1 - 2);
		context.getMatrices().pushMatrix();
		context.getMatrices().scale(SCALE, SCALE);
		int sx = (int) (tx / SCALE);
		int sy = (int) (ty / SCALE);
		context.drawTextWithShadow(textRenderer, display, sx, sy, color);
		context.getMatrices().popMatrix();

		if (isFocused()) {
			String before = text.substring(0, Math.min(cursor, text.length()));
			int cx = tx + (int) (textRenderer.getWidth(before) * SCALE);
			int cy = ty;
			context.fill(cx, cy, cx + 1, cy + (int) (textRenderer.fontHeight * SCALE) + 1, 0xFFFFFFFF);
		}
		context.disableScissor();
	}

	@Override
	protected void appendClickableNarrations(NarrationMessageBuilder builder) {
		builder.put(NarrationPart.TITLE, Text.literal("Input"));
	}
}

