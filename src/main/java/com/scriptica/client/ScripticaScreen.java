package com.scriptica.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

import java.io.IOException;

public final class ScripticaScreen extends Screen {
    private static int persistedLeftPanelWidth = 120;
    private static int persistedConsoleHeight = 140;
    private static float persistedEditorScale = 0.32f;

    private MiniTextFieldWidget scriptNameField;
    private MiniTextFieldWidget searchField;
    private MiniTextFieldWidget filterField;

    private ScriptListWidget scriptList;
    private ScriptEditorWidget editor;
    private ConsoleWidget console;

    private FlatButtonWidget runBtn;
    private FlatButtonWidget stopBtn;
    private FlatButtonWidget saveBtn;
    private FlatButtonWidget loadBtn;
    private FlatButtonWidget newBtn;
    private FlatButtonWidget delBtn;

    private int leftPanelWidth = persistedLeftPanelWidth;
    private int consoleHeight = persistedConsoleHeight;

    private DragMode dragMode = DragMode.NONE;

    private int layoutPad = 8;
    private int layoutGap = 8;
    private int layoutTopY = 8;
    private int layoutLeftHeaderH = 40;
    private int layoutRightHeaderH = 30;
    private int layoutDividerX = 0;
    private int layoutDividerY = 0;
    private int layoutDividerTop = 0;
    private int layoutDividerBottom = 0;

    private String lastLoadedName = "";
    private String lastLoadedSource = "";

    public ScripticaScreen() {
        super(Text.literal("Scriptica"));
    }

    @Override
    protected void init() {
        int pad = 8;

        scriptNameField = addDrawableChild(new MiniTextFieldWidget(textRenderer, pad, pad, 200, 16, "Script name"));
        scriptNameField.setText(lastLoadedName.isBlank() ? "hello" : lastLoadedName);

        runBtn = addDrawableChild(new FlatButtonWidget(textRenderer, 0, 0, 44, 16, "Run", this::onRun));
        stopBtn = addDrawableChild(new FlatButtonWidget(textRenderer, 0, 0, 52, 16, "Stop", () -> ScriptRunner.instance().stop()));
        saveBtn = addDrawableChild(new FlatButtonWidget(textRenderer, 0, 0, 52, 16, "Save", this::onSave));
        loadBtn = addDrawableChild(new FlatButtonWidget(textRenderer, 0, 0, 52, 16, "Load", this::onLoad));
        newBtn = addDrawableChild(new FlatButtonWidget(textRenderer, 0, 0, 44, 16, "New", this::onNew));
        delBtn = addDrawableChild(new FlatButtonWidget(textRenderer, 0, 0, 44, 16, "Del", this::onDelete));

        searchField = addDrawableChild(new MiniTextFieldWidget(textRenderer, pad, pad, 240, 16, "Search (Ctrl+F)"));
        filterField = addDrawableChild(new MiniTextFieldWidget(textRenderer, pad, pad, 200, 16, "Filter scripts"));

        scriptList = addDrawableChild(new ScriptListWidget(textRenderer, pad, pad, 200, 200, this::openFromList));
        editor = addDrawableChild(new ScriptEditorWidget(textRenderer, pad, pad, 300, 300));
        console = addDrawableChild(new ConsoleWidget(textRenderer, pad, pad, 300, 120));

        searchField.setChangedListener(editor::setSearchQuery);
        filterField.setChangedListener(scriptList::setFilter);

        editor.setFontScale(persistedEditorScale);

        if (lastLoadedSource.isBlank()) {
            editor.setText(defaultScript());
            lastLoadedSource = editor.getText();
        } else {
            editor.setText(lastLoadedSource);
        }

        scriptList.refresh();
        scriptList.setSelectedName(scriptName());

        layout();
        setInitialFocus(editor);
    }

    @Override
    public void removed() {
        persistedLeftPanelWidth = leftPanelWidth;
        persistedConsoleHeight = consoleHeight;
        persistedEditorScale = editor == null ? persistedEditorScale : editor.getFontScale();
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        layout();
    }

    private void layout() {
        final int pad = 8;
        final int gap = 8;
        final int inputH = 16;
        final int rowGap = 4;
        final int headerPadTop = 6;
        final int headerPadBottom = 6;
        final int topY = pad;

        layoutPad = pad;
        layoutGap = gap;
        layoutTopY = topY;
        layoutLeftHeaderH = headerPadTop + (inputH * 2) + rowGap + headerPadBottom;

        // Allow more resizing range (thin sidebar + narrow editor if needed)
        int minRightW = 180;
        int minLeftW = 90;
        int maxLeft = Math.max(minLeftW, width - pad * 2 - minRightW);
        leftPanelWidth = clamp(leftPanelWidth, minLeftW, maxLeft);

        int leftX = pad;
        int rightX = leftX + leftPanelWidth + gap;
        int rightW = width - pad - rightX;

        int by1 = topY + headerPadTop;
        int by2 = by1 + inputH + rowGap;
        int btnGap = 6;

        scriptNameField.setDimensionsAndPosition(leftPanelWidth, inputH, leftX, by1);
        filterField.setDimensionsAndPosition(leftPanelWidth, inputH, leftX, by2);

        layoutRightHeaderH = layoutRightHeader(rightX, by1, rightW, inputH, btnGap, rowGap, headerPadTop, headerPadBottom);

        int scriptsTop = topY + layoutLeftHeaderH + 8;
        int contentTop = topY + layoutRightHeaderH + 8;

        // Console can be resized both ways: keep editor >= 120px
        int minConsole = 60;
        int available = Math.max(0, height - pad - contentTop - 8);
        int maxConsole = Math.max(minConsole, available - 120);
        consoleHeight = clamp(consoleHeight, minConsole, maxConsole);

        int consoleY = height - pad - consoleHeight;
        int editorH = Math.max(120, consoleY - contentTop - 8);

        int leftH = height - pad - scriptsTop;
        scriptList.setDimensionsAndPosition(leftPanelWidth, leftH, leftX, scriptsTop);

        editor.setDimensionsAndPosition(rightW, editorH, rightX, contentTop);
        console.setDimensionsAndPosition(rightW, consoleHeight, rightX, consoleY);

        layoutDividerX = leftX + leftPanelWidth + gap / 2;
        layoutDividerY = console.getY() - 3;
        layoutDividerTop = Math.min(scriptsTop, contentTop) - 4;
        layoutDividerBottom = height - pad;
    }

    private int layoutRightHeader(
        int x,
        int y,
        int w,
        int rowH,
        int gap,
        int rowGap,
        int headerPadTop,
        int headerPadBottom
    ) {
        if (w <= 0) {
            return headerPadTop + rowH + headerPadBottom;
        }

        int cx = x;
        int cy = y;
        int rowsUsed = 1;

        FlatButtonWidget[] buttons = new FlatButtonWidget[] {runBtn, stopBtn, saveBtn, loadBtn, newBtn, delBtn};
        for (FlatButtonWidget b : buttons) {
            int bw = preferredButtonWidth(b);
            if (cx != x && cx + bw > x + w) {
                cy += rowH + rowGap;
                cx = x;
                rowsUsed++;
            }
            bw = Math.min(bw, w);
            b.setDimensionsAndPosition(bw, rowH, cx, cy);
            cx += bw + gap;
        }

        int minSearchW = Math.min(140, w);
        int remaining = (x + w) - cx;
        if (remaining >= minSearchW) {
            searchField.setDimensionsAndPosition(remaining, rowH, cx, cy);
        } else {
            cy += rowH + rowGap;
            rowsUsed++;
            searchField.setDimensionsAndPosition(w, rowH, x, cy);
        }

        return headerPadTop + (rowsUsed * rowH) + ((rowsUsed - 1) * rowGap) + headerPadBottom;
    }

    private int preferredButtonWidth(FlatButtonWidget btn) {
        String label = btn.getMessage().getString();
        int textW = (int) Math.ceil(textRenderer.getWidth(label) * 0.55f);
        return clamp(textW + 18, 34, 72);
    }

    private void openFromList(String name) {
        scriptNameField.setText(name);
        onLoad();
    }

    private String scriptName() {
        return ScriptStorage.sanitizeName(scriptNameField.getText());
    }

    private void onRun() {
        ScriptRunner.instance().runAsync(editor.getText());
    }

    private void onSave() {
        String name = scriptName();
        try {
            ScriptStorage.save(name, editor.getText());
            ScripticaLog.info("Saved: " + name);
            lastLoadedName = name;
            lastLoadedSource = editor.getText();
            scriptList.refresh();
            scriptList.setSelectedName(name);
        } catch (IOException e) {
            ScripticaLog.error("Save failed: " + e.getMessage());
        }
    }

    private void onLoad() {
        String name = scriptName();
        try {
            String src = ScriptStorage.load(name);
            editor.setText(src);
            ScripticaLog.info("Loaded: " + name);
            lastLoadedName = name;
            lastLoadedSource = src;
            scriptList.setSelectedName(name);
        } catch (IOException e) {
            ScripticaLog.error("Load failed: " + e.getMessage());
        }
    }

    private void onDelete() {
        String name = scriptName();
        try {
            ScriptStorage.delete(name);
            ScripticaLog.info("Deleted: " + name);
            scriptList.refresh();
        } catch (IOException e) {
            ScripticaLog.error("Delete failed: " + e.getMessage());
        }
    }

    private void onNew() {
        editor.setText(defaultScript());
        ScripticaLog.info("New script buffer");
    }

    private static String defaultScript() {
        return """
// Grass miner demo
// Mines nearby grass blocks (minecraft:grass_block).
// Tip: put a shovel in your hotbar for fastest mining.

func pickTool() {
  if (invSelect("minecraft:netherite_shovel")) return;
  if (invSelect("minecraft:diamond_shovel")) return;
  if (invSelect("minecraft:iron_shovel")) return;
  if (invSelect("minecraft:stone_shovel")) return;
  if (invSelect("minecraft:wooden_shovel")) return;
}

pickTool();
print("[Scriptica] Grass miner running. Press Stop to cancel.");

while (true) {
  // Find nearest grass block within 5 blocks of you
  let b = nearestBlock("minecraft:grass_block", 5);
  if (b == null) {
    wait(5);
    continue;
  }

  // Aim + hold attack for a short time
  mineBlock(b.x, b.y, b.z, 8);
  wait(2);
}
""";
    }


    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.isEscape()) {
            close();
            return true;
        }

        if (input.hasCtrlOrCmd() && input.getKeycode() == 83) {
            onSave();
            return true;
        }
        if (input.hasCtrlOrCmd() && input.getKeycode() == 70) {
            setFocused(searchField);
            return true;
        }
        if (input.hasCtrlOrCmd() && input.getKeycode() == 82) {
            onRun();
            return true;
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isCtrlOrCmdDown() && editor != null && editor.isMouseOver(mouseX, mouseY)) {
            int dir = verticalAmount > 0 ? 1 : -1;
            editor.adjustFontScale(dir);
            persistedEditorScale = editor.getFontScale();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (hitVerticalDivider(click.x(), click.y())) {
            dragMode = DragMode.VERTICAL;
            setDragging(true);
            setFocused(null);
            return true;
        }
        if (hitHorizontalDivider(click.x(), click.y())) {
            dragMode = DragMode.HORIZONTAL;
            setDragging(true);
            setFocused(null);
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (dragMode == DragMode.VERTICAL) {
            leftPanelWidth = (int) Math.round(click.x()) - layoutPad - (layoutGap / 2);
            layout();
            return true;
        }
        if (dragMode == DragMode.HORIZONTAL) {
            consoleHeight = (height - layoutPad) - (int) Math.round(click.y());
            layout();
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (dragMode != DragMode.NONE) {
            dragMode = DragMode.NONE;
            setDragging(false);
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(null);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x00000000);

        int headerBottom = layoutTopY + Math.max(layoutLeftHeaderH, layoutRightHeaderH) + 8;
        context.fill(layoutPad, layoutTopY, width - layoutPad, headerBottom, 0x220B1220);
        context.fill(layoutPad, headerBottom, width - layoutPad, headerBottom + 1, 0x331F2937);

        super.render(context, mouseX, mouseY, delta);

        drawDividers(context, mouseX, mouseY);

        String name = scriptName();
        boolean dirty = editor != null && (!editor.getText().equals(lastLoadedSource) || !name.equalsIgnoreCase(lastLoadedName));
        String status = (ScriptRunner.instance().isRunning() ? "RUNNING" : "IDLE") + (dirty ? " • UNSAVED" : "");
        drawSmallText(context, status, layoutPad, height - 10, dirty ? 0xFFF59E0B : 0xFFE5E7EB, 0.70f);
        drawSmallText(context, "Ctrl+Scroll: zoom", width - layoutPad - 120, height - 10, 0xFF64748B, 0.70f);
    }

    private void drawDividers(DrawContext context, int mouseX, int mouseY) {
        boolean vHot = hitVerticalDivider(mouseX, mouseY) || dragMode == DragMode.VERTICAL;
        int vColor = vHot ? 0xFF38BDF8 : 0x66334155;
        int vW = vHot ? 3 : 1;
        int vx0 = layoutDividerX - (vW / 2);
        context.fill(vx0, layoutDividerTop, vx0 + vW, layoutDividerBottom, vColor);

        boolean hHot = hitHorizontalDivider(mouseX, mouseY) || dragMode == DragMode.HORIZONTAL;
        int hColor = hHot ? 0xFF38BDF8 : 0x66334155;
        int hH = hHot ? 3 : 1;
        int hy0 = layoutDividerY - (hH / 2);
        context.fill(editor.getX(), hy0, editor.getX() + editor.getWidth(), hy0 + hH, hColor);
    }

    private boolean hitVerticalDivider(double x, double y) {
        return x >= layoutDividerX - 10 && x <= layoutDividerX + 10 && y >= layoutDividerTop && y <= layoutDividerBottom;
    }

    private boolean hitHorizontalDivider(double x, double y) {
        int dividerY = layoutDividerY;
        return x >= editor.getX() && x <= editor.getX() + editor.getWidth() && y >= dividerY - 10 && y <= dividerY + 10;
    }

    private boolean isCtrlOrCmdDown() {
        return InputUtil.isKeyPressed(client.getWindow(), InputUtil.GLFW_KEY_LEFT_CONTROL)
            || InputUtil.isKeyPressed(client.getWindow(), InputUtil.GLFW_KEY_RIGHT_CONTROL)
            || InputUtil.isKeyPressed(client.getWindow(), InputUtil.GLFW_KEY_LEFT_SUPER)
            || InputUtil.isKeyPressed(client.getWindow(), InputUtil.GLFW_KEY_RIGHT_SUPER);
    }

    private void drawSmallText(DrawContext context, String s, int x, int y, int color, float scale) {
        context.getMatrices().pushMatrix();
        context.getMatrices().scale(scale, scale);
        context.drawTextWithShadow(textRenderer, s, (int) (x / scale), (int) (y / scale), color);
        context.getMatrices().popMatrix();
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private enum DragMode {
        NONE,
        VERTICAL,
        HORIZONTAL
    }
}






