package com.cosmicplayer.stafftracker.ui;

import com.cosmicplayer.stafftracker.HelpData;
import com.cosmicplayer.stafftracker.HelpData.PeriodTotal;
import com.cosmicplayer.stafftracker.InteractionLog;
import com.cosmicplayer.stafftracker.InteractionLog.DatedInteraction;
import com.cosmicplayer.stafftracker.InteractionLog.Interaction;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.time.LocalDate;
import java.util.List;

/**
 * The History section of the dock window. Period lists drill into days,
 * days into interactions, and interactions into a bubble chat view.
 * A search query swaps the list for matching interactions by player.
 *
 * Every list row except search results has a three dot menu holding
 * Delete. Every delete goes through a popup that shows what is being
 * removed.
 */
public class HistoryPanel extends CleanWidget {
    public static final int TAB_DAYS = 0;
    public static final int TAB_WEEKS = 1;

    private static final int ROW_HEIGHT = 18;
    private static final int HEADER_HEIGHT = 14;
    private static final int LIST_GAP = 6;
    private static final int BACK_WIDTH = 14;
    private static final int LABEL_X = 13;
    private static final int BOX_INSET = 1;
    private static final int DOTS_WIDTH = 16;
    private static final int MENU_WIDTH = 64;
    private static final int MENU_ENTRY_HEIGHT = 14;
    private static final int BORDER = 0x26FFFFFF;
    private static final int MENU_BACKGROUND = 0xFA101010;

    private enum View {
        PERIODS, PERIOD_DAYS, DAY, CHAT, SEARCH
    }

    private final StaffTrackerScreen screen;

    private int tab = TAB_DAYS;
    private View view = View.PERIODS;
    private List<PeriodTotal> entries = List.of();

    // PERIOD_DAYS state: the week or month being browsed.
    private String periodTitle;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private List<PeriodTotal> periodDays = List.of();

    // DAY state: the day being browsed and whether a period page opened it.
    private LocalDate day;
    private int dayCount;
    private boolean dayFromPeriod;
    private List<Interaction> interactions = List.of();

    // CHAT state: the conversation being read and which page opened it.
    private Interaction chat;
    private ChatThread chatThread;
    private boolean chatFromSearch;

    // SEARCH state: the query and its matches across every logged day.
    private String query = "";
    private List<DatedInteraction> results = List.of();

    // Row menu state. The menu opens while hovering a row's dots and closes
    // once the mouse leaves both the dots and the menu itself.
    private int menuRow = -1;
    private int menuX;
    private int menuY;

    private double scroll;
    private int hoveredRow = -1;
    private float chevronSlide;

    public HistoryPanel(StaffTrackerScreen screen, int x, int y, int width, int height) {
        super(x, y, width, height, Text.literal("History"));
        this.screen = screen;
        refresh();
    }

    public void setTab(int tab) {
        this.tab = tab;
        this.view = View.PERIODS;
        refresh();
        resetListState();
    }

    /** Called by the search box. Searches players across every logged day. */
    public void setSearch(String query) {
        this.query = query;
        if (query.isEmpty()) {
            view = View.PERIODS;
            refresh();
        } else {
            results = InteractionLog.search(query);
            view = View.SEARCH;
        }
        resetListState();
    }

    /** Called after a popup deletes data, so the open page reflects it. */
    public void afterDelete() {
        switch (view) {
            case PERIOD_DAYS -> periodDays = HelpData.get().daysBetween(periodStart, periodEnd).reversed();
            case DAY -> {
                interactions = InteractionLog.forDay(day).reversed();
                goBack();
                return;
            }
            case SEARCH -> results = InteractionLog.search(query);
            default -> refresh();
        }
        refresh();
        scroll = MathHelper.clamp(scroll, 0, Math.max(0, contentHeight() - listHeight()));
    }

    private void refresh() {
        HelpData data = HelpData.get();
        entries = switch (tab) {
            case TAB_DAYS -> data.days();
            case TAB_WEEKS -> data.weeks();
            default -> data.months();
        };
    }

    private void resetListState() {
        scroll = 0;
        hoveredRow = -1;
        chevronSlide = 0.0f;
        closeMenu();
    }

    private void closeMenu() {
        menuRow = -1;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        // No hover behavior while a popup covers the screen.
        if (screen.hasPopup()) {
            closeMenu();
        } else {
            if (menuRow >= 0 && !isOverDots(menuRow, mouseX, mouseY) && !isOverMenu(mouseX, mouseY)) {
                closeMenu();
            }
            // Hovering a row's dots opens its menu without a click.
            if (menuRow < 0 && view != View.CHAT && view != View.SEARCH) {
                int hovered = rowAt(mouseX, mouseY);
                if (hovered >= 0 && isOverDots(hovered, mouseX, mouseY)) {
                    openMenu(hovered);
                }
            }
        }

        if (view != View.PERIODS) {
            drawHeader(context, mouseX, mouseY);
        }
        if (view == View.CHAT) {
            drawChat(context);
        } else {
            drawList(context, mouseX, mouseY);
        }
        if (menuRow >= 0) {
            drawMenu(context, mouseX, mouseY);
        }
    }

    /** Back chevron, page title, and a right side action or note. */
    private void drawHeader(DrawContext context, int mouseX, int mouseY) {
        int textY = contentTop() + 3;
        if (view != View.SEARCH) {
            int chevronColor = isOverBack(mouseX, mouseY) ? Theme.ACCENT : Theme.TEXT;
            TextPainter.draw(context, "‹", getX() + 3, textY, Theme.FONT_BODY, chevronColor, true);
        }

        String title = headerTitle();
        float titleX = view == View.SEARCH ? getX() + 3 : getX() + BACK_WIDTH + 2;
        TextPainter.draw(context, title, titleX, textY, Theme.FONT_BODY, Theme.TEXT, false);

        // Day and period pages put the helped total on the right, clear of the
        // centered search box. Other pages keep the subtitle beside the title.
        String subtitle = headerSubtitle();
        if (view == View.PERIOD_DAYS || view == View.DAY) {
            float subtitleX = getX() + boxWidth() - 4 - TextPainter.width(subtitle, Theme.FONT_SMALL, false);
            TextPainter.draw(context, subtitle, subtitleX, textY, Theme.FONT_SMALL, Theme.TEXT_DIM, false);
        } else {
            float subtitleX = titleX + TextPainter.width(title, Theme.FONT_BODY, false) + 6;
            TextPainter.draw(context, subtitle, subtitleX, textY, Theme.FONT_SMALL, Theme.TEXT_DIM, false);
        }

        if (view == View.CHAT) {
            String route = planetRoute(chat);
            float routeX = getX() + boxWidth() - 4 - TextPainter.width(route, Theme.FONT_SMALL, false);
            TextPainter.draw(context, route, routeX, textY, Theme.FONT_SMALL, Theme.TEXT_DIM, false);
        }
    }

    private String headerTitle() {
        return switch (view) {
            case PERIOD_DAYS -> periodTitle;
            case DAY -> Format.day(day);
            case CHAT -> chat.player() != null ? chat.player() : "Unknown player";
            case SEARCH -> "Results";
            default -> "";
        };
    }

    private String headerSubtitle() {
        return switch (view) {
            case PERIOD_DAYS -> Format.count(periodDays.stream().mapToInt(PeriodTotal::count).sum()) + " helped";
            case DAY -> Format.count(dayCount) + " helped";
            case CHAT -> Format.time(chat.time());
            case SEARCH -> results.size() + (results.size() == 1 ? " match" : " matches");
            default -> "";
        };
    }

    /** The staff planet and the helped player's planet, like "aether › celestial". */
    private static String planetRoute(Interaction interaction) {
        if (interaction.staffPlanet() == null) {
            return "no planets logged";
        }
        String playerPlanet = interaction.playerPlanet() != null ? interaction.playerPlanet() : "unknown";
        return interaction.staffPlanet() + " › " + playerPlanet;
    }

    private void drawList(DrawContext context, int mouseX, int mouseY) {
        int listY = listTop();
        int listHeight = listHeight();
        Theme.roundedRectOutline(context, getX(), listY, boxWidth(), listHeight, 5, BORDER, 0.5f);

        int rowCount = rowCount();
        if (rowCount == 0) {
            TextPainter.drawCentered(context, emptyMessage(), getX() + boxWidth() / 2.0f,
                    listY + listHeight / 2.0f - 4, Theme.FONT_BODY, Theme.TEXT_DIM, false);
            return;
        }

        int hovered = rowAt(mouseX, mouseY);
        if (hovered != hoveredRow) {
            hoveredRow = hovered;
            chevronSlide = 0.0f;
        }
        chevronSlide = MathHelper.lerp(0.35f, chevronSlide, hoveredRow >= 0 ? 1.0f : 0.0f);

        context.enableScissor(getX() + BOX_INSET, listY + BOX_INSET,
                getX() + boxWidth() - BOX_INSET, listY + listHeight - BOX_INSET);
        int y = listY + BOX_INSET - (int) scroll;
        for (int i = 0; i < rowCount; i++, y += ROW_HEIGHT) {
            if (y + ROW_HEIGHT < listY || y > listY + listHeight) {
                continue;
            }
            Rows.stripe(context, i, rowX(), y, rowWidth(), ROW_HEIGHT);
            float slide = i == hoveredRow ? chevronSlide : 0.0f;
            switch (view) {
                case DAY -> drawInteractionRow(context, interactions.get(i), null, y, i, slide, mouseX, mouseY);
                case SEARCH -> {
                    DatedInteraction result = results.get(i);
                    drawInteractionRow(context, result.interaction(), result.day(), y, i, slide, mouseX, mouseY);
                }
                default -> drawPeriodRow(context, currentRows().get(i), y, i, mouseX, mouseY);
            }
        }
        context.disableScissor();
    }

    private void drawPeriodRow(DrawContext context, PeriodTotal row, int y, int index, int mouseX, int mouseY) {
        // The current period gets a small accent dot in front of its label.
        if (isCurrentPeriod(row.start())) {
            Theme.roundedRect(context, rowX() + 5, y + ROW_HEIGHT / 2 - 1, 3, 3, 1, Theme.ACCENT);
        }
        String primary = primaryLabel(row.start());
        TextPainter.drawInRow(context, primary, rowX() + LABEL_X, y, ROW_HEIGHT, Theme.FONT_BODY, Theme.TEXT, false);

        String secondary = secondaryLabel(row.start());
        if (secondary != null) {
            float secondaryX = rowX() + LABEL_X + TextPainter.width(primary, Theme.FONT_BODY, false) + 6;
            TextPainter.drawInRow(context, secondary, secondaryX, y, ROW_HEIGHT, Theme.FONT_SMALL, Theme.TEXT_DIM, false);
        }

        boolean dotsActive = menuRow == index || isOverDots(index, mouseX, mouseY);
        drawDots(context, y, dotsActive ? Theme.TEXT : Theme.TEXT_DIM);

        boolean zero = row.count() == 0;
        String count = Format.count(row.count());
        float countX = rowX() + rowWidth() - DOTS_WIDTH + 2 - TextPainter.width(count, Theme.FONT_BODY, true);
        TextPainter.drawInRow(context, count, countX, y, ROW_HEIGHT, Theme.FONT_BODY, zero ? Theme.TEXT_DIM : Theme.TEXT, true);
    }

    /**
     * A row for one interaction: time, head, name, then planet on the right.
     * Day rows end in a dots menu, search results end in a chevron.
     */
    private void drawInteractionRow(DrawContext context, Interaction row, LocalDate resultDay, int y,
                                    int index, float slide, int mouseX, int mouseY) {
        String when = resultDay != null
                ? Format.monthDay(resultDay) + " · " + Format.time(row.time())
                : Format.time(row.time());
        Rows.interaction(context, row, when, rowX() + 5, rowX() + (resultDay != null ? 62 : 34), y, ROW_HEIGHT);

        float planetRight;
        if (resultDay == null) {
            boolean dotsActive = menuRow == index || isOverDots(index, mouseX, mouseY);
            drawDots(context, y, dotsActive ? Theme.TEXT : Theme.TEXT_DIM);
            planetRight = rowX() + rowWidth() - DOTS_WIDTH + 2;
        } else {
            int rightEdge = rowX() + rowWidth() - 5;
            float chevronWidth = TextPainter.width("›", Theme.FONT_SMALL, false);
            float chevronX = rightEdge - chevronWidth + slide * 3;
            int chevronColor = Theme.lerpColor(Theme.TEXT_DIM, Theme.TEXT, slide);
            TextPainter.drawInRow(context, "›", chevronX, y, ROW_HEIGHT, Theme.FONT_SMALL, chevronColor, false);
            planetRight = rightEdge - chevronWidth - 6;
        }

        String planet = row.playerPlanet() != null ? row.playerPlanet() : "";
        Rows.rightDetail(context, planet, planetRight, y, ROW_HEIGHT);
    }

    /** Three tiny vertical dots on the right edge of a row. Hovering them opens the row menu. */
    private void drawDots(DrawContext context, int rowY, int color) {
        int x = rowX() + rowWidth() - 8;
        int top = rowY + ROW_HEIGHT / 2 - 4;
        for (int i = 0; i < 3; i++) {
            context.fill(x, top + i * 3, x + 1, top + i * 3 + 1, color);
        }
    }

    /** The little Delete menu anchored under a row's dots. */
    private void drawMenu(DrawContext context, int mouseX, int mouseY) {
        int menuHeight = menuHeight();
        Theme.roundedRect(context, menuX, menuY, MENU_WIDTH, menuHeight, 4, MENU_BACKGROUND);
        Theme.roundedRectOutline(context, menuX, menuY, MENU_WIDTH, menuHeight, 4, BORDER, 0.5f);

        int entryY = menuY + 2;
        if (menuEntryAt(mouseX, mouseY) == 0) {
            Theme.roundedRect(context, menuX + 2, entryY, MENU_WIDTH - 4, MENU_ENTRY_HEIGHT, 3, Theme.HOVER);
        }
        TextPainter.drawInRow(context, "Delete", menuX + 8, entryY, MENU_ENTRY_HEIGHT, Theme.FONT_SMALL, Theme.DANGER, false);
    }

    private void drawChat(DrawContext context) {
        int listY = listTop();
        int listHeight = listHeight();
        Theme.roundedRectOutline(context, getX(), listY, boxWidth(), listHeight, 5, BORDER, 0.5f);

        if (chatThread == null || chatThread.height() == 0) {
            TextPainter.drawCentered(context, "No messages were logged.", getX() + boxWidth() / 2.0f,
                    listY + listHeight / 2.0f - 4, Theme.FONT_BODY, Theme.TEXT_DIM, false);
            return;
        }

        context.enableScissor(getX() + BOX_INSET, listY + BOX_INSET,
                getX() + boxWidth() - BOX_INSET, listY + listHeight - BOX_INSET);
        chatThread.draw(context, rowX() + 5, listY + 5 - (int) scroll);
        context.disableScissor();
    }

    private String emptyMessage() {
        return switch (view) {
            case DAY -> "No logged interactions for this day.";
            case SEARCH -> "No matches for \"" + query + "\".";
            default -> "No players helped yet.";
        };
    }

    private String primaryLabel(LocalDate start) {
        LocalDate today = LocalDate.now();
        if (view == View.PERIOD_DAYS) {
            return start.equals(today) ? "Today" : Format.fullDay(start);
        }
        return switch (tab) {
            case TAB_DAYS -> Format.day(start);
            case TAB_WEEKS -> start.equals(HelpData.weekStart(today)) ? "This Week" : Format.weekRange(start);
            default -> start.equals(today.withDayOfMonth(1)) ? "This Month" : Format.month(start);
        };
    }

    private String secondaryLabel(LocalDate start) {
        LocalDate today = LocalDate.now();
        if (view == View.PERIOD_DAYS) {
            return start.equals(today) ? Format.fullDay(start) : null;
        }
        return switch (tab) {
            case TAB_DAYS -> start.isAfter(today.minusDays(2)) ? Format.fullDay(start) : null;
            case TAB_WEEKS -> start.equals(HelpData.weekStart(today)) ? Format.weekRange(start) : null;
            default -> start.equals(today.withDayOfMonth(1)) ? Format.monthOnly(start) : null;
        };
    }

    /** True when the row covers today, this week, or this month, matching the view. */
    private boolean isCurrentPeriod(LocalDate start) {
        LocalDate today = LocalDate.now();
        if (view == View.PERIOD_DAYS) {
            return start.equals(today);
        }
        return switch (tab) {
            case TAB_DAYS -> start.equals(today);
            case TAB_WEEKS -> start.equals(HelpData.weekStart(today));
            default -> start.equals(today.withDayOfMonth(1));
        };
    }

    @Override
    public void onClick(Click click, boolean doubled) {
        int mouseX = (int) click.x();
        int mouseY = (int) click.y();

        if (menuRow >= 0 && isOverMenu(mouseX, mouseY)) {
            onMenuEntryClicked(menuEntryAt(mouseX, mouseY));
            return;
        }
        if (view != View.PERIODS && view != View.SEARCH && isOverBack(mouseX, mouseY)) {
            goBack();
            return;
        }
        int index = rowAt(mouseX, mouseY);
        if (index < 0 || view == View.CHAT) {
            return;
        }
        if (isOverDots(index, mouseX, mouseY)) {
            openMenu(index);
            return;
        }
        openRow(index);
    }

    private void openRow(int index) {
        switch (view) {
            case PERIODS -> openFromPeriods(entries.get(index));
            case PERIOD_DAYS -> openDay(periodDays.get(index), true);
            case DAY -> openChat(interactions.get(index), false);
            case SEARCH -> openChat(results.get(index).interaction(), true);
            default -> {
            }
        }
    }

    private void openMenu(int index) {
        menuRow = index;
        menuX = rowX() + rowWidth() - MENU_WIDTH - 2;
        menuY = rowTop(index) + ROW_HEIGHT - 2;
        // Flip the menu above the row when it would poke out of the panel.
        if (menuY + menuHeight() > getY() + height) {
            menuY = rowTop(index) - menuHeight() + 2;
        }
    }

    private void onMenuEntryClicked(int entry) {
        int row = menuRow;
        closeMenu();
        if (entry != 0) {
            return;
        }
        if (view == View.DAY) {
            openInteractionDeletePopup(interactions.get(row));
        } else {
            openDeletePopupForRow(currentRows().get(row));
        }
    }

    /** Shows the full conversation of one helped player before deleting it. */
    private void openInteractionDeletePopup(Interaction interaction) {
        String name = interaction.player() != null ? interaction.player() : "Unknown player";
        screen.openPopup(ConfirmPopup.forThread(
                "Delete " + name + "?",
                Format.time(interaction.time()),
                "Delete",
                () -> {
                    HelpData.get().deleteInteraction(day, interaction);
                    dayCount = Math.max(0, dayCount - 1);
                    interactions = InteractionLog.forDay(day).reversed();
                    refresh();
                    scroll = MathHelper.clamp(scroll, 0, Math.max(0, contentHeight() - listHeight()));
                },
                interaction));
    }

    /** Delete popup for a row in the period lists, without opening the row. */
    private void openDeletePopupForRow(PeriodTotal row) {
        LocalDate start = row.start();
        if (view == View.PERIOD_DAYS || tab == TAB_DAYS) {
            openDayDeletePopup(start, row.count());
        } else if (tab == TAB_WEEKS) {
            openRangeDeletePopup(Format.weekRange(start), start, start.plusDays(6));
        } else {
            openRangeDeletePopup(Format.month(start), start, start.withDayOfMonth(start.lengthOfMonth()));
        }
    }

    /** Shows every conversation the day holds before deleting it. */
    private void openDayDeletePopup(LocalDate target, int count) {
        screen.openPopup(ConfirmPopup.forDay(target, count, () -> {
            HelpData.get().deleteDay(target);
            afterDelete();
        }));
    }

    /** Shows the browsable per day breakdown of a week or month before deleting it. */
    private void openRangeDeletePopup(String title, LocalDate start, LocalDate end) {
        screen.openPopup(ConfirmPopup.forRange(title, start, end, () -> {
            HelpData.get().deleteRange(start, end);
            afterDelete();
        }));
    }

    /** True while a conversation page is open. The screen hides the search box then. */
    public boolean inChat() {
        return view == View.CHAT;
    }

    /** True on the top level lists, where the tabs show and search sits in the strip. */
    public boolean showsTopStrip() {
        return view == View.PERIODS || view == View.SEARCH;
    }

    /**
     * Fits the header search box into the gap between the page title and
     * the helped total, centered there, so it can never overlap either.
     */
    public void layoutHeaderSearch(SearchField field) {
        float titleEnd = getX() + BACK_WIDTH + 2
                + TextPainter.width(headerTitle(), Theme.FONT_BODY, false) + 10;
        float subtitleStart = getX() + boxWidth() - 4
                - TextPainter.width(headerSubtitle(), Theme.FONT_SMALL, false) - 10;
        int width = (int) MathHelper.clamp(subtitleStart - titleEnd - 8, 60, 140);
        field.setWidth(width);
        field.setX(Math.round(titleEnd + (subtitleStart - titleEnd - width) / 2.0f));
    }

    private void openFromPeriods(PeriodTotal row) {
        if (tab == TAB_DAYS) {
            openDay(row, false);
            return;
        }
        LocalDate start = row.start();
        if (tab == TAB_WEEKS) {
            periodTitle = Format.weekRange(start);
            periodEnd = start.plusDays(6);
        } else {
            periodTitle = Format.month(start);
            periodEnd = start.withDayOfMonth(start.lengthOfMonth());
        }
        periodStart = start;
        periodDays = HelpData.get().daysBetween(start, periodEnd).reversed();
        view = View.PERIOD_DAYS;
        resetListState();
    }

    private void openDay(PeriodTotal row, boolean fromPeriod) {
        day = row.start();
        dayCount = row.count();
        dayFromPeriod = fromPeriod;
        interactions = InteractionLog.forDay(day).reversed();
        view = View.DAY;
        clearSearchBox();
        resetListState();
    }

    private void openChat(Interaction interaction, boolean fromSearch) {
        chat = interaction;
        chatFromSearch = fromSearch;
        chatThread = ChatThread.build(interaction, rowWidth() - 10);
        view = View.CHAT;
        resetListState();
    }

    private void goBack() {
        switch (view) {
            case CHAT -> {
                // Returning to search results keeps the query. Everything else clears it.
                if (chatFromSearch) {
                    view = View.SEARCH;
                } else {
                    view = View.DAY;
                }
            }
            case DAY -> {
                clearSearchBox();
                if (dayFromPeriod) {
                    periodDays = HelpData.get().daysBetween(periodStart, periodEnd).reversed();
                    view = View.PERIOD_DAYS;
                } else {
                    refresh();
                    view = View.PERIODS;
                }
            }
            default -> {
                clearSearchBox();
                refresh();
                view = View.PERIODS;
            }
        }
        resetListState();
    }

    /** Empties the screen's search box and this panel's query together. */
    private void clearSearchBox() {
        query = "";
        screen.clearSearch();
    }

    /** Scroll the current page. Called by the screen so the wheel works anywhere over the panel. */
    public void scrollBy(double amount) {
        closeMenu();
        int step = view == View.CHAT ? 14 : ROW_HEIGHT;
        int maxScroll = Math.max(0, contentHeight() - listHeight());
        scroll = MathHelper.clamp(scroll - amount * step, 0, maxScroll);
    }

    private int contentHeight() {
        if (view == View.CHAT) {
            return (chatThread != null ? chatThread.height() : 0) + 10;
        }
        return rowCount() * ROW_HEIGHT + 2 * BOX_INSET;
    }

    private int rowCount() {
        return switch (view) {
            case DAY -> interactions.size();
            case SEARCH -> results.size();
            default -> currentRows().size();
        };
    }

    private List<PeriodTotal> currentRows() {
        return view == View.PERIOD_DAYS ? periodDays : entries;
    }

    private int menuHeight() {
        return MENU_ENTRY_HEIGHT + 4;
    }

    /** The menu entry index under the mouse, or -1 when outside the entries. */
    private int menuEntryAt(int mouseX, int mouseY) {
        if (!isOverMenu(mouseX, mouseY)) {
            return -1;
        }
        int entry = (mouseY - menuY - 2) / MENU_ENTRY_HEIGHT;
        return entry == 0 ? 0 : -1;
    }

    private boolean isOverMenu(int mouseX, int mouseY) {
        return menuRow >= 0 && mouseX >= menuX && mouseX < menuX + MENU_WIDTH
                && mouseY >= menuY && mouseY < menuY + menuHeight();
    }

    /** True when the mouse is over the dots of the given row. */
    private boolean isOverDots(int index, int mouseX, int mouseY) {
        if (view == View.CHAT || view == View.SEARCH) {
            return false;
        }
        int rowTop = rowTop(index);
        return mouseX >= rowX() + rowWidth() - DOTS_WIDTH && mouseX < rowX() + rowWidth()
                && mouseY >= Math.max(rowTop, listTop()) && mouseY < Math.min(rowTop + ROW_HEIGHT, listTop() + listHeight());
    }

    private int rowTop(int index) {
        return listTop() + BOX_INSET + index * ROW_HEIGHT - (int) scroll;
    }

    private boolean isOverBack(int mouseX, int mouseY) {
        return mouseX >= getX() && mouseX < getX() + BACK_WIDTH
                && mouseY >= contentTop() && mouseY < contentTop() + HEADER_HEIGHT;
    }

    /** The row index under the mouse, or -1 when outside the list box. */
    private int rowAt(int mouseX, int mouseY) {
        if (view == View.CHAT) {
            return -1;
        }
        int listY = listTop();
        if (mouseX < getX() || mouseX >= getX() + boxWidth()
                || mouseY < listY + BOX_INSET || mouseY >= listY + listHeight()) {
            return -1;
        }
        int index = (mouseY - listY - BOX_INSET + (int) scroll) / ROW_HEIGHT;
        return index < rowCount() ? index : -1;
    }

    /** The bordered box spans the full widget width. */
    private int boxWidth() {
        return width;
    }

    private int rowX() {
        return getX() + BOX_INSET;
    }

    private int rowWidth() {
        return boxWidth() - 2 * BOX_INSET;
    }

    /**
     * Drilled pages take the whole panel, including the strip where the
     * search box and tabs normally sit. The top level lists start below it.
     */
    private int contentTop() {
        return view == View.PERIODS || view == View.SEARCH ? getY() + 22 : getY();
    }

    /** The list fills the panel. The header only takes room past the top level. */
    private int listTop() {
        return view == View.PERIODS ? contentTop() : contentTop() + HEADER_HEIGHT + LIST_GAP;
    }

    private int listHeight() {
        return getY() + height - listTop();
    }
}
