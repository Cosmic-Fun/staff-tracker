package com.cosmicplayer.stafftracker.ui;

import com.cosmicplayer.stafftracker.HelpData;
import com.cosmicplayer.stafftracker.InteractionLog;
import com.cosmicplayer.stafftracker.InteractionLog.Interaction;
import com.cosmicplayer.stafftracker.StaffTrackerConfig;
import com.cosmicplayer.stafftracker.StaffTrackerConfig.HudView;
import com.cosmicplayer.stafftracker.hud.CounterHud;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.time.LocalDate;
import java.util.List;

/**
 * The mod's single window, opened from the pause menu. A rail on the left
 * switches between the Settings and History sections. Adjust opens the
 * fullscreen HUD editor and comes back here when done so it doesn't just close.
 *
 * Deletes route through a ConfirmPopup drawn over everything, which shows
 * the exact data being removed before it goes.
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
    private SearchField searchField;
    private SegmentedControl historyTabs;
    private CleanButton undoButton;
    private ConfirmPopup popup;
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
        searchField = null;
        historyTabs = null;
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

        // Search and tabs come first so they win clicks over the panel,
        // which stretches under them to give chat pages the full height.
        searchField = addDrawableChild(new SearchField(x, panelY() + 13, 134, 14,
                "Search players", query -> historyPanel.setSearch(query)));
        int selectorWidth = 110;
        historyTabs = addDrawableChild(new SegmentedControl(x + w - selectorWidth - 4, panelY() + 12, selectorWidth,
                ROW_HEIGHT, null, HISTORY_TABS, historyTab, tab -> {
            historyTab = tab;
            historyPanel.setTab(tab);
        }));

        int top = panelY() + 12;
        int bottom = panelY() + panelHeight - 10;
        historyPanel = addDrawableChild(new HistoryPanel(this, x, top, w, bottom - top));
        historyPanel.setTab(historyTab);
    }

    private CleanButton doneButton() {
        int x = contentAreaX() + contentAreaWidth() - 50;
        return new CleanButton(x, footerY(), 50, ROW_HEIGHT, "Done", b -> close()).glass();
    }

    private void switchTo(Section target) {
        if (section != target) {
            section = target;
            popup = null;
            clearAndInit();
        }
    }

    /** Shows a confirmation card over the whole window. */
    public void openPopup(ConfirmPopup popup) {
        this.popup = popup;
    }

    /** True while a confirmation card covers the window. */
    public boolean hasPopup() {
        return popup != null;
    }

    /** Empties the search box, without firing its change callback. */
    public void clearSearch() {
        if (searchField != null) {
            searchField.reset();
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);

        // The tabs only show on the top level lists. The search box follows:
        // it sits in the top strip there, moves into the header row on
        // drilled pages, and hides entirely inside a conversation.
        if (historyPanel != null) {
            boolean topStrip = historyPanel.showsTopStrip();
            historyTabs.visible = topStrip;
            searchField.visible = !historyPanel.inChat();
            if (topStrip) {
                // Long enough to reach the tabs, with a small gap before them.
                searchField.setWidth(contentAreaWidth() - 122);
                searchField.setX(contentAreaX());
                searchField.setY(panelY() + 13);
            } else {
                historyPanel.layoutHeaderSearch(searchField);
                searchField.setY(panelY() + 12);
            }
        }

        Theme.roundedRectEdges(context, panelX(), panelY(), RAIL_WIDTH, panelHeight, 6, Theme.RAIL, true, false);
        TextPainter.draw(context, "STAFF TRACKER", panelX() + 13, panelY() + 12, Theme.FONT_SMALL, Theme.TEXT_DIM, false);

        if (section == Section.SETTINGS) {
            TextPainter.draw(context, "Settings", contentAreaX() + 2, panelY() + 14, Theme.FONT_BODY, Theme.TEXT, true);
        }
    }

    @Override
    protected void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {
        if (popup != null) {
            popup.render(context, this.width, this.height, mouseX, mouseY);
        }
    }

    /** Opens the undo confirmation with the full conversation it would delete. */
    private void onUndoPressed(CleanButton button) {
        if (HelpData.get().today() == 0) {
            button.setLabel("Nothing today");
            undoFeedbackUntil = Util.getMeasuringTimeMs() + UNDO_FEEDBACK_MS;
            return;
        }

        List<Interaction> today = InteractionLog.forDay(LocalDate.now());
        Runnable confirm = () -> {
            HelpData.get().undo();
            if (undoButton != null) {
                undoButton.setLabel("Removed 1");
                undoFeedbackUntil = Util.getMeasuringTimeMs() + UNDO_FEEDBACK_MS;
            }
        };

        if (today.isEmpty() || today.getLast().player() == null) {
            String note = today.isEmpty()
                    ? "This count has no logged interaction."
                    : "No player was identified for this count.";
            openPopup(ConfirmPopup.forNote("Undo this count?", "today", "Undo", confirm, note));
            return;
        }

        Interaction last = today.getLast();
        openPopup(ConfirmPopup.forThread(
                "Undo this count?",
                last.player() + "  ·  " + Format.time(last.time()),
                "Undo",
                confirm,
                last));
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
        if (popup != null) {
            if (input.isEscape()) {
                popup = null;
            }
            return true;
        }
        if (keybindRow != null && keybindRow.isListening()) {
            keybindRow.handleKey(input);
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (popup != null) {
            if (popup.mouseClicked(this.width, this.height, (int) click.x(), (int) click.y())) {
                popup = null;
            }
            return true;
        }
        if (keybindRow != null && keybindRow.isListening()) {
            keybindRow.handleMouse(click);
            return true;
        }
        // Clicking anywhere else drops focus out of the search box.
        if (searchField != null && !searchField.isMouseOver(click.x(), click.y())) {
            searchField.setFocused(false);
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (popup != null) {
            popup.mouseScrolled(this.height, verticalAmount);
            return true;
        }
        if (historyPanel != null && historyPanel.isMouseOver(mouseX, mouseY)) {
            historyPanel.scrollBy(verticalAmount);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    // A popup swallows every other input so nothing behind it can react.
    @Override
    public boolean charTyped(CharInput input) {
        if (popup != null) {
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (popup != null) {
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (popup != null) {
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
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
