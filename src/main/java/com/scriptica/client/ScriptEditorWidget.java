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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ScriptEditorWidget extends ClickableWidget {
	private static final Set<String> KEYWORDS = new HashSet<>();

	static {
		KEYWORDS.add("let");
		KEYWORDS.add("if");
		KEYWORDS.add("else");
		KEYWORDS.add("while");
		KEYWORDS.add("func");
		KEYWORDS.add("return");
		KEYWORDS.add("true");
		KEYWORDS.add("false");
		KEYWORDS.add("null");
		KEYWORDS.add("wait");
	}

	private static final float MIN_SCALE = 0.20f;
	private static final float MAX_SCALE = 1.20f;

	private final TextRenderer textRenderer;

	private String text = "";
	private int cursor = 0;
	private int scrollLine = 0;
	private String search = "";
	private float fontScale = 0.32f;

	// selection
	private int selectionAnchor = 0;
	private int selectionStart = 0;
	private int selectionEnd = 0;

	private static final int MAX_HISTORY = 200;

	private final ArrayList<EditState> undoStack = new ArrayList<>();
	private final ArrayList<EditState> redoStack = new ArrayList<>();
	private boolean applyingHistory = false;

	private record EditState(String text, int cursor, int selectionAnchor, int selectionStart, int selectionEnd, int scrollLine) {}


	public ScriptEditorWidget(TextRenderer textRenderer, int x, int y, int width, int height) {
		super(x, y, width, height, Text.empty());
		this.textRenderer = textRenderer;
	}

	private EditState snapshot() {
		return new EditState(text, cursor, selectionAnchor, selectionStart, selectionEnd, scrollLine);
	}

	private void pushUndo() {
		if (applyingHistory) return;
		EditState cur = snapshot();
		if (!undoStack.isEmpty()) {
			EditState last = undoStack.get(undoStack.size() - 1);
			if (last.text().equals(cur.text())
				&& last.cursor() == cur.cursor()
				&& last.selectionStart() == cur.selectionStart()
				&& last.selectionEnd() == cur.selectionEnd()) {
				return;
			}
		}
		undoStack.add(cur);
		if (undoStack.size() > MAX_HISTORY) undoStack.remove(0);
		redoStack.clear();
	}

	private void restore(EditState s) {
		applyingHistory = true;
		this.text = s.text() == null ? "" : s.text();
		this.cursor = clampInt(s.cursor(), 0, this.text.length());
		this.selectionAnchor = clampInt(s.selectionAnchor(), 0, this.text.length());
		this.selectionStart = clampInt(s.selectionStart(), 0, this.text.length());
		this.selectionEnd = clampInt(s.selectionEnd(), 0, this.text.length());
		this.scrollLine = Math.max(0, s.scrollLine());
		applyingHistory = false;
	}

	private void undo() {
		if (undoStack.isEmpty()) return;
		redoStack.add(snapshot());
		EditState prev = undoStack.remove(undoStack.size() - 1);
		restore(prev);
	}

	private void redo() {
		if (redoStack.isEmpty()) return;
		undoStack.add(snapshot());
		if (undoStack.size() > MAX_HISTORY) undoStack.remove(0);
		EditState next = redoStack.remove(redoStack.size() - 1);
		restore(next);
	}

	private static int clampInt(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}
	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text == null ? "" : text;
		this.cursor = Math.max(0, Math.min(this.cursor, this.text.length()));
		clearSelection();
		this.scrollLine = 0;

		undoStack.clear();
		redoStack.clear();
	}

	public void setSearchQuery(String q) {
		this.search = q == null ? "" : q;
	}

	public float getFontScale() {
		return fontScale;
	}

	public void setFontScale(float scale) {
		this.fontScale = clamp(scale, MIN_SCALE, MAX_SCALE);
	}

	public void adjustFontScale(int dir) {
		float next = fontScale + (dir * 0.03f);
		setFontScale(next);
	}

	private boolean hasSelection() {
		return selectionStart != selectionEnd;
	}

	private void clearSelection() {
		selectionAnchor = cursor;
		selectionStart = cursor;
		selectionEnd = cursor;
	}

	private void setCursor(int newCursor, boolean selecting) {
		cursor = Math.max(0, Math.min(newCursor, text.length()));
		if (!selecting) {
			clearSelection();
			return;
		}
		int a = selectionAnchor;
		selectionStart = Math.min(a, cursor);
		selectionEnd = Math.max(a, cursor);
	}

	private String selectedText() {
		if (!hasSelection()) return "";
		return text.substring(selectionStart, selectionEnd);
	}

	private void deleteSelectionIfAny() {
		if (!hasSelection()) return;
		text = text.substring(0, selectionStart) + text.substring(selectionEnd);
		cursor = selectionStart;
		clearSelection();
	}

	private void insertStringAtCursor(String s) {
		if (s == null || s.isEmpty()) return;
		pushUndo();
		deleteSelectionIfAny();
		text = text.substring(0, cursor) + s + text.substring(cursor);
		cursor += s.length();
		clearSelection();
	}

	private void backspace() {
		if (hasSelection()) {
			pushUndo();
			deleteSelectionIfAny();
			return;
		}
		if (cursor <= 0) return;
		pushUndo();
		text = text.substring(0, cursor - 1) + text.substring(cursor);
		cursor--;
		clearSelection();
	}

	private void deleteForward() {
		if (hasSelection()) {
			pushUndo();
			deleteSelectionIfAny();
			return;
		}
		if (cursor >= text.length()) return;
		pushUndo();
		text = text.substring(0, cursor) + text.substring(cursor + 1);
		clearSelection();
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (!this.isFocused()) return false;

		if (input.hasCtrlOrCmd() && input.getKeycode() == 90) { // Ctrl+Z
			if (input.hasShift()) redo();
			else undo();
			return true;
		}
		if (input.hasCtrlOrCmd() && input.getKeycode() == 89) { // Ctrl+Y
			redo();
			return true;
		}

		if (input.isSelectAll()) {
			selectionAnchor = 0;
			cursor = text.length();
			selectionStart = 0;
			selectionEnd = text.length();
			return true;
		}

		if (input.isCopy()) {
			String sel = selectedText();
			if (!sel.isEmpty()) {
				ScripticaClientMod.client().keyboard.setClipboard(sel);
			}
			return true;
		}

		if (input.isCut()) {
			String sel = selectedText();
			if (!sel.isEmpty()) {
				ScripticaClientMod.client().keyboard.setClipboard(sel);
				pushUndo();
				deleteSelectionIfAny();
			}
			return true;
		}

		if (input.isPaste()) {
			String clip = ScripticaClientMod.client().keyboard.getClipboard();
			insertStringAtCursor(clip);
			return true;
		}

		boolean selecting = input.hasShift();
		int keyCode = input.getKeycode();
		switch (keyCode) {
			case 259 -> { // backspace
				backspace();
				return true;
			}
			case 261 -> { // delete
				deleteForward();
				return true;
			}
			case 262 -> { // right
				if (!selecting) selectionAnchor = cursor;
				setCursor(cursor + 1, selecting);
				return true;
			}
			case 263 -> { // left
				if (!selecting) selectionAnchor = cursor;
				setCursor(cursor - 1, selecting);
				return true;
			}
			case 264 -> { // down
				if (!selecting) selectionAnchor = cursor;
				setCursor(moveCursorVertical(cursor, 1), selecting);
				return true;
			}
			case 265 -> { // up
				if (!selecting) selectionAnchor = cursor;
				setCursor(moveCursorVertical(cursor, -1), selecting);
				return true;
			}
			case 268 -> { // home
				if (!selecting) selectionAnchor = cursor;
				setCursor(lineStart(cursor), selecting);
				return true;
			}
			case 269 -> { // end
				if (!selecting) selectionAnchor = cursor;
				setCursor(lineEnd(cursor), selecting);
				return true;
			}
			case 257, 335 -> { // enter
				insertStringAtCursor("\n");
				return true;
			}
			case 258 -> { // tab
				insertStringAtCursor("  ");
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	@Override
	public boolean charTyped(CharInput input) {
		if (!this.isFocused()) return false;

		if (!input.isValidChar()) return false;

		String s = input.asString();
		if (s.equals("\r") || s.equals("\n")) return true;
		if (s.equals("\t")) {
			insertStringAtCursor("  ");
			return true;
		}

		insertStringAtCursor(s);
		return true;
	}

	@Override
	public boolean mouseDragged(Click click, double offsetX, double offsetY) {
		if (!isFocused()) return false;
		int next = cursorFromMouse(click.x(), click.y());
		setCursor(next, true);
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		List<String> lines = splitLines(text);
		int lineHeightPx = (int) ((textRenderer.fontHeight + 2) * fontScale);
		int maxVisible = Math.max(1, (int) ((height - 10) / (double) lineHeightPx));
		int maxScroll = Math.max(0, lines.size() - maxVisible);
		scrollLine = Math.max(0, Math.min(maxScroll, scrollLine - (int) Math.signum(verticalAmount)));
		return true;
	}

	@Override
	protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
		int x0 = getX();
		int y0 = getY();
		int x1 = x0 + width;
		int y1 = y0 + height;

		context.fill(x0, y0, x1, y1, 0x330F111A);
		context.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0x22111827);

		int gutterW = 40;
		context.fill(x0 + 1, y0 + 1, x0 + gutterW, y1 - 1, 0x1A0B1220);
		context.fill(x0 + gutterW, y0 + 1, x0 + gutterW + 1, y1 - 1, 0x221F2937);

		List<String> lines = splitLines(text);
		int lineHeightPx = (int) ((textRenderer.fontHeight + 2) * fontScale);
		int maxVisible = Math.max(1, (int) ((height - 10) / (double) lineHeightPx));
		int maxScroll = Math.max(0, lines.size() - maxVisible);

		int[] lc = cursorLineCol(cursor);
		int cLine = lc[0];
		if (cLine < scrollLine) scrollLine = cLine;
		if (cLine >= scrollLine + maxVisible) scrollLine = cLine - maxVisible + 1;
		scrollLine = Math.max(0, Math.min(maxScroll, scrollLine));

		String q = search == null ? "" : search;
		boolean hasSearch = !q.isBlank();

		int drawY = y0 + 5;
		int textX = x0 + gutterW + 6;

		context.enableScissor(x0 + 2, y0 + 2, x1 - 2, y1 - 2);

		for (int i = 0; i < maxVisible; i++) {
			int li = scrollLine + i;
			if (li >= lines.size()) break;

			String line = lines.get(li);
			String ln = String.valueOf(li + 1);
			int lnX = x0 + gutterW - 6 - (int) (textRenderer.getWidth(ln) * fontScale);
			int lnColor = (li == cLine) ? 0xFFE5E7EB : 0xFF6B7280;

			context.getMatrices().pushMatrix();
			context.getMatrices().scale(fontScale, fontScale);
			context.drawTextWithShadow(textRenderer, ln, (int) (lnX / fontScale), (int) (drawY / fontScale), lnColor);
			context.getMatrices().popMatrix();

			drawSelectionForLine(context, li, line, textX, drawY);
			if (hasSearch) {
				drawSearchHighlights(context, line, q, textX, drawY, 0x5538BDF8);
			}

			context.getMatrices().pushMatrix();
			context.getMatrices().scale(fontScale, fontScale);
			int sx = (int) (textX / fontScale);
			int sy = (int) (drawY / fontScale);
			drawHighlightedLine(context, line, sx, sy);
			context.getMatrices().popMatrix();

			drawY += lineHeightPx;
		}

		context.disableScissor();

		if (isFocused()) {
			int[] clc = cursorLineCol(cursor);
			int lineIdx = clc[0];
			int col = clc[1];
			if (lineIdx >= 0 && lineIdx < lines.size()) {
				String line = lines.get(lineIdx);
				String before = line.substring(0, Math.min(col, line.length()));
				int cx = textX + (int) (textRenderer.getWidth(before) * fontScale);
				int cy = y0 + 5 + (lineIdx - scrollLine) * lineHeightPx;
				context.fill(cx, cy, cx + 1, cy + (int) (textRenderer.fontHeight * fontScale) + 1, 0xFFFFFFFF);
			}
		}

		String scaleLabel = String.format("%d%%", Math.round(fontScale * 100));
		float hudScale = 0.65f;
		int sw = (int) Math.ceil(textRenderer.getWidth(scaleLabel) * hudScale);
		context.getMatrices().pushMatrix();
		context.getMatrices().scale(hudScale, hudScale);
		context.drawTextWithShadow(textRenderer, scaleLabel, (int) ((x1 - 6 - sw) / hudScale), (int) ((y0 + 4) / hudScale), 0xFF94A3B8);
		context.getMatrices().popMatrix();
	}

	private void drawSelectionForLine(DrawContext context, int lineIndex, String line, int textX, int y) {
		if (!hasSelection()) return;

		int lineStartIdx = indexFromLineCol(lineIndex, 0);
		int lineEndIdx = lineStartIdx + line.length();

		int selA = selectionStart;
		int selB = selectionEnd;
		if (selB <= lineStartIdx || selA > lineEndIdx) return;

		int from = Math.max(selA, lineStartIdx);
		int to = Math.min(selB, lineEndIdx);

		int startCol = Math.max(0, from - lineStartIdx);
		int endCol = Math.max(startCol, to - lineStartIdx);

		int x0 = textX + (int) (textRenderer.getWidth(line.substring(0, Math.min(startCol, line.length()))) * fontScale);
		int x1 = textX + (int) (textRenderer.getWidth(line.substring(0, Math.min(endCol, line.length()))) * fontScale);
		context.fill(x0, y - 1, x1, y + (int) (textRenderer.fontHeight * fontScale) + 2, 0x553B82F6);
	}

	private void drawSearchHighlights(DrawContext context, String line, String q, int x, int y, int color) {
		String needle = q;
		if (needle.isBlank()) return;
		String hay = line;

		String hayLower = hay.toLowerCase();
		String needleLower = needle.toLowerCase();

		int from = 0;
		while (from < hayLower.length()) {
			int idx = hayLower.indexOf(needleLower, from);
			if (idx < 0) break;
			int end = idx + needleLower.length();

			int x0 = x + (int) (textRenderer.getWidth(hay.substring(0, idx)) * fontScale);
			int x1 = x + (int) (textRenderer.getWidth(hay.substring(0, end)) * fontScale);
			context.fill(x0, y - 1, x1, y + (int) (textRenderer.fontHeight * fontScale) + 2, color);

			from = end;
		}
	}

	private void drawHighlightedLine(DrawContext context, String line, int x, int y) {
		int px = x;
		int i = 0;
		boolean inString = false;
		while (i < line.length()) {
			char c = line.charAt(i);

			if (!inString && c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '/') {
				String rest = line.substring(i);
				context.drawTextWithShadow(textRenderer, rest, px, y, 0xFF64748B);
				return;
			}

			if (c == '"') {
				inString = !inString;
				context.drawTextWithShadow(textRenderer, "\"", px, y, 0xFFF59E0B);
				px += textRenderer.getWidth("\"");
				i++;
				continue;
			}

			if (inString) {
				int start = i;
				while (i < line.length() && line.charAt(i) != '"') i++;
				String chunk = line.substring(start, i);
				context.drawTextWithShadow(textRenderer, chunk, px, y, 0xFFFBBF24);
				px += textRenderer.getWidth(chunk);
				continue;
			}

			if (Character.isWhitespace(c)) {
				int start = i;
				while (i < line.length() && Character.isWhitespace(line.charAt(i))) i++;
				String chunk = line.substring(start, i);
				context.drawTextWithShadow(textRenderer, chunk, px, y, 0xFFE5E7EB);
				px += textRenderer.getWidth(chunk);
				continue;
			}

			if (Character.isDigit(c)) {
				int start = i;
				while (i < line.length() && (Character.isDigit(line.charAt(i)) || line.charAt(i) == '.')) i++;
				String chunk = line.substring(start, i);
				context.drawTextWithShadow(textRenderer, chunk, px, y, 0xFF38BDF8);
				px += textRenderer.getWidth(chunk);
				continue;
			}

			if (isIdentStart(c)) {
				int start = i;
				i++;
				while (i < line.length() && isIdentPart(line.charAt(i))) i++;
				String ident = line.substring(start, i);
				int color = KEYWORDS.contains(ident) ? 0xFFC084FC : 0xFFE5E7EB;
				context.drawTextWithShadow(textRenderer, ident, px, y, color);
				px += textRenderer.getWidth(ident);
				continue;
			}

			context.drawTextWithShadow(textRenderer, String.valueOf(c), px, y, 0xFF93C5FD);
			px += textRenderer.getWidth(String.valueOf(c));
			i++;
		}
	}

	private static boolean isIdentStart(char c) {
		return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
	}

	private static boolean isIdentPart(char c) {
		return isIdentStart(c) || (c >= '0' && c <= '9');
	}

	@Override
	public void onClick(Click click, boolean doubled) {
		this.setFocused(true);
		selectionAnchor = cursorFromMouse(click.x(), click.y());
		setCursor(selectionAnchor, false);
	}

	private int cursorFromMouse(double mouseX, double mouseY) {
		int gutterW = 40;
		int lineHeightPx = (int) ((textRenderer.fontHeight + 2) * fontScale);
		int relY = (int) (mouseY - (getY() + 5));
		int clickedLine = scrollLine + Math.max(0, relY / Math.max(1, lineHeightPx));
		List<String> lines = splitLines(text);
		clickedLine = Math.max(0, Math.min(clickedLine, lines.size() - 1));

		String line = lines.get(clickedLine);
		int textX = getX() + gutterW + 6;
		double relX = mouseX - textX;
		double unscaledX = relX / Math.max(0.0001, fontScale);

		int col = 0;
		for (int i = 1; i <= line.length(); i++) {
			if (textRenderer.getWidth(line.substring(0, i)) > unscaledX) break;
			col = i;
		}
		return indexFromLineCol(clickedLine, col);
	}

	@Override
	protected void appendClickableNarrations(NarrationMessageBuilder builder) {
		builder.put(NarrationPart.TITLE, Text.literal("Script editor"));
	}

	private int[] cursorLineCol(int index) {
		int line = 0;
		int col = 0;
		for (int i = 0; i < index && i < text.length(); i++) {
			char ch = text.charAt(i);
			if (ch == '\n') {
				line++;
				col = 0;
			} else {
				col++;
			}
		}
		return new int[] {line, col};
	}

	private int indexFromLineCol(int line, int col) {
		int l = 0;
		int idx = 0;
		while (idx < text.length() && l < line) {
			if (text.charAt(idx) == '\n') l++;
			idx++;
		}
		return Math.max(0, Math.min(text.length(), idx + col));
	}

	private int lineStart(int index) {
		int i = Math.max(0, Math.min(index, text.length()));
		while (i > 0 && text.charAt(i - 1) != '\n') i--;
		return i;
	}

	private int lineEnd(int index) {
		int i = Math.max(0, Math.min(index, text.length()));
		while (i < text.length() && text.charAt(i) != '\n') i++;
		return i;
	}

	private int moveCursorVertical(int index, int dir) {
		int[] lc = cursorLineCol(index);
		int line = lc[0];
		int col = lc[1];
		List<String> lines = splitLines(text);
		int nextLine = Math.max(0, Math.min(lines.size() - 1, line + dir));
		int nextCol = Math.min(col, lines.get(nextLine).length());
		return indexFromLineCol(nextLine, nextCol);
	}

	private static List<String> splitLines(String s) {
		List<String> out = new ArrayList<>();
		if (s == null || s.isEmpty()) {
			out.add("");
			return out;
		}
		int start = 0;
		for (int i = 0; i < s.length(); i++) {
			if (s.charAt(i) == '\n') {
				out.add(s.substring(start, i));
				start = i + 1;
			}
		}
		out.add(s.substring(start));
		return out;
	}

	private static float clamp(float v, float min, float max) {
		return Math.max(min, Math.min(max, v));
	}
}




