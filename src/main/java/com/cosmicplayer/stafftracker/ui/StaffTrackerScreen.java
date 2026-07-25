package com.cosmicplayer.stafftracker.ui;

import com.cosmicplayer.stafftracker.HelpData;
import com.cosmicplayer.stafftracker.StaffTrackerConfig;
import com.cosmicplayer.stafftracker.StaffTrackerConfig.HudView;
import com.cosmicplayer.stafftracker.hud.CounterHud;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.util.Util;

/**
 * The mod's single window, opened from the pause menu. A rail on the left
 * switches between the Settings and History sections. Adjust opens the
 * fullscreen HUD editor and comes back here when done so it doesn't just close.
 */
public class StaffTrackerScreen extends CleanScreen {
    private enum Section {
        SETTINGS, HISTORY
    }

    private static final int RAIL_WIDTH = 96;
    private static final int ROW_HEIGHT = 16;
    private static final int ROW_GAP = 3;
    private static final long UNDO_FEEDBACK_MS = 1500;
    private static final String[] VIEW_OPTIONS = {HudView.DAY.option, HudView.WEEK.option, HudView.MONTH.option};
    private static final String[] HISTORY_TABS = {"Days", "Weeks", "Months"};

    private Section section = Section.SETTINGS;
    private int historyTab = HistoryPanel.TAB_DAYS;
    private KeybindRow keybindRow;
    private HistoryPanel historyPanel;
    private CleanButton undoButton;
    private long undoFeedbackUntil = -1;

    public StaffTrackerScreen(Screen parent) {
        super("Staff Tracker", parent);
        this.panelWidth = 380;
        this.panelHeight = 176;
    }

    @Override
    protected boolean showTitle() {
        return false;
    }

    @Override
    protected void init() {
        CounterHud.suppressed = true;
        keybindRow = null;
        historyPanel = null;
        undoButton = null;
        initRail();
        if (section == Section.SETTINGS) {
            initSettings();
        } else {
            initHistory();
        }
    }

    private void initRail() {
        int x = panelX() + 6;
        int w = RAIL_WIDTH - 12;
        int y = panelY() + 26;

        addDrawableChild(new RailButton(x, y, w, ROW_HEIGHT, "Settings", section == Section.SETTINGS,
                () -> switchTo(Section.SETTINGS)));
        y += ROW_HEIGHT + 2;
        addDrawableChild(new RailButton(x, y, w, ROW_HEIGHT, "History", section == Section.HISTORY,
                () -> switchTo(Section.HISTORY)));
        y += ROW_HEIGHT + 2;
        addDrawableChild(new RailButton(x, y, w, ROW_HEIGHT, "Adjust HUD", false,
                () -> this.client.setScreen(new HudPositionScreen(this))));
    }

    private void initSettings() {
        StaffTrackerConfig config = StaffTrackerConfig.get();
        int x = contentAreaX();
        int w = contentAreaWidth();
        int y = panelY() + 30;

        addDrawableChild(new CleanToggle(x, y, w, ROW_HEIGHT, "Counter HUD", config.hudEnabled, v -> config.hudEnabled = v));
        y += ROW_HEIGHT + ROW_GAP;
        addDrawableChild(new CleanToggle(x, y, w, ROW_HEIGHT, "Show label", config.showLabel, v -> config.showLabel = v));
        y += ROW_HEIGHT + ROW_GAP;
        addDrawableChild(new SegmentedControl(x, y, w, ROW_HEIGHT, "Counter view", VIEW_OPTIONS,
                config.hudView.ordinal(), i -> config.hudView = HudView.values()[i]));
        y += ROW_HEIGHT + ROW_GAP;
        keybindRow = addDrawableChild(new KeybindRow(x, y, w, ROW_HEIGHT, "Count Keybind"));

        undoButton = addDrawableChild(new CleanButton(x, footerY(), 80, ROW_HEIGHT, "Undo count", this::onUndoPressed)
                .withStyle(CleanButton.Style.DANGER).glass());
        addDrawableChild(doneButton());
    }

    private void initHistory() {
        int x = contentAreaX();
        int w = contentAreaWidth();
        int top = panelY() + 34;
        int bottom = panelY() + panelHeight - 10;
        historyPanel = addDrawableChild(new HistoryPanel(x, top, w, bottom - top));
        historyPanel.setTab(historyTab);

        int selectorWidth = 110;
        addDrawableChild(new SegmentedControl(x + w - selectorWidth - 4, panelY() + 12, selectorWidth, ROW_HEIGHT,
                null, HISTORY_TABS, historyTab, tab -> {
            historyTab = tab;
            historyPanel.setTab(tab);
        }));
    }

    private CleanButton doneButton() {
        int x = contentAreaX() + contentAreaWidth() - 50;
        return new CleanButton(x, footerY(), 50, ROW_HEIGHT, "Done", b -> close()).glass();
    }

    private void switchTo(Section target) {
        if (section != target) {
            section = target;
            clearAndInit();
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);

        Theme.roundedRectEdges(context, panelX(), panelY(), RAIL_WIDTH, panelHeight, 6, Theme.RAIL, true, false);
        context.drawText(this.textRenderer, Theme.textSmall("STAFF TRACKER"), panelX() + 13, panelY() + 12, Theme.TEXT_DIM, false);

        String header = section == Section.SETTINGS ? "Settings" : "History";
        context.drawText(this.textRenderer, Theme.text(header), contentAreaX() + 2, panelY() + 14, Theme.TEXT, false);
    }

    /** Removes one count from today and briefly confirms it on the button itself.
     * COMEBACK: change static count to a dynamic count depending on presses.
     */
    private void onUndoPressed(CleanButton button) {
        HelpData data = HelpData.get();
        if (data.today() > 0) {
            data.undo();
            button.setLabel("Removed 1");
        } else {
            button.setLabel("Nothing today");
        }
        undoFeedbackUntil = Util.getMeasuringTimeMs() + UNDO_FEEDBACK_MS;
    }

    @Override
    public void tick() {
        super.tick();
        if (undoFeedbackUntil > 0 && Util.getMeasuringTimeMs() >= undoFeedbackUntil) {
            undoFeedbackUntil = -1;
            if (undoButton != null) {
                undoButton.setLabel("Undo count");
            }
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (keybindRow != null && keybindRow.isListening()) {
            keybindRow.handleKey(input);
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (keybindRow != null && keybindRow.isListening()) {
            keybindRow.handleMouse(click);
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (historyPanel != null && historyPanel.isMouseOver(mouseX, mouseY)) {
            historyPanel.scrollBy(verticalAmount);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    /** This just shows the HUD under the settings window so you can see the edits you're making live. */
    @Override
    protected void renderBehindPanel(DrawContext context, int mouseX, int mouseY, float delta) {
        if (StaffTrackerConfig.get().hudEnabled) {
            CounterHud.renderPanel(context, this.client, false);
        }
    }

    @Override
    public void removed() {
        CounterHud.suppressed = false;
        StaffTrackerConfig.save();
    }

    private int contentAreaX() {
        return panelX() + RAIL_WIDTH + 14;
    }

    private int contentAreaWidth() {
        return panelWidth - RAIL_WIDTH - 28;
    }

    private int footerY() {
        return panelY() + panelHeight - 26;
    }
}
