package com.cosmicplayer.stafftracker.ui;

import com.cosmicplayer.stafftracker.HelpData;
import com.cosmicplayer.stafftracker.HelpData.PeriodTotal;
import com.cosmicplayer.stafftracker.InteractionLog;
import com.cosmicplayer.stafftracker.InteractionLog.Interaction;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import java.time.LocalDate;
import java.util.List;

/**
 * A popup card that shows exactly what a delete will remove before it
 * runs. The body is browsable: a range shows its days, a day shows its
 * interactions, and an interaction shows the full conversation.
 */
public class ConfirmPopup {
    private static final int WIDTH = 300;
    private static final int HEIGHT = 172;
    private static final int PADDING = 10;
    private static final int ROW_HEIGHT = 16;
    private static final int BUTTON_HEIGHT = 13;
    private static final int BUTTON_WIDTH = 46;
    private static final int BORDER = 0x14FFFFFF;

    private enum Page {
        DAYS, INTERACTIONS, THREAD, NOTE
    }

    private final String rootTitle;
    private final String rootSubtitle;
    private final String confirmLabel;
    private final Runnable onConfirm;
    private final Page rootPage;

    // Range state, only set for week and month deletes.
    private List<PeriodTotal> dayRows;

    // Current page state. Drilling pushes pages, the back chevron pops them.
    private Page page;
    private LocalDate currentDay;
    private int currentDayCount;
    private List<Interaction> interactionRows;
    private Interaction threadInteraction;
    private ChatThread thread;
    private String note;
    private double scroll;

    private ConfirmPopup(Page rootPage, String title, String subtitle, String confirmLabel, Runnable onConfirm) {
        this.rootPage = rootPage;
        this.page = rootPage;
        this.rootTitle = title;
        this.rootSubtitle = subtitle;
        this.confirmLabel = confirmLabel;
        this.onConfirm = onConfirm;
    }

    /** Confirmation for undoing a count, showing the conversation it removes. */
    public static ConfirmPopup forThread(String title, String subtitle, String confirmLabel,
                                         Runnable onConfirm, Interaction interaction) {
        ConfirmPopup popup = new ConfirmPopup(Page.THREAD, title, subtitle, confirmLabel, onConfirm);
        popup.threadInteraction = interaction;
        popup.thread = ChatThread.build(interaction, bodyInnerWidth());
        return popup;
    }

    /** Confirmation with a plain note, for counts with nothing logged. */
    public static ConfirmPopup forNote(String title, String subtitle, String confirmLabel,
                                       Runnable onConfirm, String note) {
        ConfirmPopup popup = new ConfirmPopup(Page.NOTE, title, subtitle, confirmLabel, onConfirm);
        popup.note = note;
        return popup;
    }

    /** Confirmation for deleting one day, with its interactions browsable. */
    public static ConfirmPopup forDay(LocalDate day, int count, Runnable onConfirm) {
        ConfirmPopup popup = new ConfirmPopup(Page.INTERACTIONS, "Delete " + Format.day(day) + "?",
                Format.count(count) + " helped", "Delete", onConfirm);
        popup.currentDay = day;
        popup.currentDayCount = count;
        popup.interactionRows = InteractionLog.forDay(day).reversed();
        return popup;
    }

    /** Confirmation for deleting a week or month, with its days browsable. */
    public static ConfirmPopup forRange(String title, LocalDate start, LocalDate end, Runnable onConfirm) {
        ConfirmPopup popup = new ConfirmPopup(Page.DAYS, "Delete " + title + "?", "", "Delete", onConfirm);
        popup.dayRows = HelpData.get().daysBetween(start, end).stream()
                .filter(row -> row.count() > 0 || !InteractionLog.forDay(row.start()).isEmpty())
                .toList()
                .reversed();
        return popup;
    }

    /** Draws the dim layer and the card over the whole screen. */
    public void render(DrawContext context, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        context.fill(0, 0, screenWidth, screenHeight, 0x90000000);

        int x = cardX(screenWidth);
        int y = cardY(screenHeight);
        Theme.roundedRect(context, x, y, WIDTH, HEIGHT, 8, 0xFC0D0D0D);

        float titleX = x + PADDING;
        if (page != rootPage) {
            int chevronColor = isOverBack(screenWidth, screenHeight, mouseX, mouseY) ? Theme.ACCENT : Theme.TEXT;
            TextPainter.draw(context, "‹", titleX, y + 8, Theme.FONT_BODY, chevronColor, true);
            titleX += 10;
        }
        TextPainter.draw(context, pageTitle(), titleX, y + 8, Theme.FONT_BODY, Theme.TEXT, true);
        String subtitle = pageSubtitle();
        float subtitleX = x + WIDTH - PADDING - TextPainter.width(subtitle, Theme.FONT_SMALL, false);
        TextPainter.draw(context, subtitle, subtitleX, y + 9, Theme.FONT_SMALL, Theme.TEXT_DIM, false);

        drawBody(context, x, y, screenHeight, mouseX, mouseY);

        drawButton(context, cancelX(screenWidth), buttonY(screenHeight), "Cancel", Theme.TEXT,
                isOverCancel(screenWidth, screenHeight, mouseX, mouseY));
        drawButton(context, confirmX(screenWidth), buttonY(screenHeight), confirmLabel, Theme.DANGER,
                isOverConfirm(screenWidth, screenHeight, mouseX, mouseY));
    }

    private void drawBody(DrawContext context, int x, int y, int screenHeight, int mouseX, int mouseY) {
        int bodyX = x + PADDING;
        int bodyY = y + 24;
        int boxWidth = WIDTH - 2 * PADDING;
        int bodyHeight = buttonY(screenHeight) - 6 - bodyY;
        Theme.roundedRectOutline(context, bodyX, bodyY, boxWidth, bodyHeight, 5, BORDER, 0.5f);

        context.enableScissor(bodyX + 1, bodyY + 1, bodyX + boxWidth - 1, bodyY + bodyHeight - 1);
        switch (page) {
            case DAYS -> drawDayRows(context, bodyX, bodyY, boxWidth, bodyHeight);
            case INTERACTIONS -> drawInteractionRows(context, bodyX, bodyY, boxWidth, bodyHeight, mouseX, mouseY);
            case THREAD -> {
                if (thread.height() == 0) {
                    TextPainter.drawCentered(context, "No messages were logged.", bodyX + boxWidth / 2.0f,
                            bodyY + bodyHeight / 2.0f - 4, Theme.FONT_BODY, Theme.TEXT_DIM, false);
                } else {
                    thread.draw(context, bodyX + 6, bodyY + 5 - (int) scroll);
                }
            }
            case NOTE -> TextPainter.drawCentered(context, note, bodyX + boxWidth / 2.0f,
                    bodyY + bodyHeight / 2.0f - 4, Theme.FONT_BODY, Theme.TEXT_DIM, false);
        }
        context.disableScissor();
    }

    private void drawDayRows(DrawContext context, int bodyX, int bodyY, int boxWidth, int bodyHeight) {
        if (dayRows.isEmpty()) {
            TextPainter.drawCentered(context, "Nothing recorded in this period.", bodyX + boxWidth / 2.0f,
                    bodyY + bodyHeight / 2.0f - 4, Theme.FONT_BODY, Theme.TEXT_DIM, false);
            return;
        }
        int y = bodyY + 1 - (int) scroll;
        for (int i = 0; i < dayRows.size(); i++, y += ROW_HEIGHT) {
            if (y + ROW_HEIGHT < bodyY || y > bodyY + bodyHeight) {
                continue;
            }
            Rows.stripe(context, i, bodyX + 1, y, boxWidth - 2, ROW_HEIGHT);
            PeriodTotal row = dayRows.get(i);
            int chats = InteractionLog.forDay(row.start()).size();
            TextPainter.drawInRow(context, Format.day(row.start()), bodyX + 6, y, ROW_HEIGHT,
                    Theme.FONT_BODY, Theme.TEXT, false);
            String detail = Format.count(row.count()) + " helped  ·  " + chats
                    + (chats == 1 ? " chat" : " chats");
            Rows.rightDetail(context, detail, bodyX + boxWidth - 6, y, ROW_HEIGHT);
        }
    }

    private void drawInteractionRows(DrawContext context, int bodyX, int bodyY, int boxWidth, int bodyHeight,
                                     int mouseX, int mouseY) {
        if (interactionRows.isEmpty()) {
            TextPainter.drawCentered(context, "No logged conversations.", bodyX + boxWidth / 2.0f,
                    bodyY + bodyHeight / 2.0f - 4, Theme.FONT_BODY, Theme.TEXT_DIM, false);
            return;
        }
        int hovered = rowAt(bodyX, bodyY, boxWidth, bodyHeight, mouseX, mouseY);
        int y = bodyY + 1 - (int) scroll;
        for (int i = 0; i < interactionRows.size(); i++, y += ROW_HEIGHT) {
            if (y + ROW_HEIGHT < bodyY || y > bodyY + bodyHeight) {
                continue;
            }
            if (i == hovered) {
                context.fill(bodyX + 1, y, bodyX + boxWidth - 1, y + ROW_HEIGHT, Theme.HOVER);
            } else {
                Rows.stripe(context, i, bodyX + 1, y, boxWidth - 2, ROW_HEIGHT);
            }
            Interaction row = interactionRows.get(i);
            Rows.interaction(context, row, Format.time(row.time()), bodyX + 6, bodyX + 34, y, ROW_HEIGHT);

            String planet = row.playerPlanet() != null ? row.playerPlanet() : "";
            Rows.rightDetail(context, planet, bodyX + boxWidth - 6, y, ROW_HEIGHT);
        }
    }

    private void drawButton(DrawContext context, int x, int y, String label, int color, boolean hovered) {
        Theme.roundedRect(context, x, y, BUTTON_WIDTH, BUTTON_HEIGHT, 3, hovered ? 0x66000000 : 0x4D000000);
        Theme.roundedRectOutline(context, x, y, BUTTON_WIDTH, BUTTON_HEIGHT, 3,
                hovered ? 0x46FFFFFF : 0x28FFFFFF, 0.5f);
        TextPainter.drawCenteredInRow(context, label, x + BUTTON_WIDTH / 2.0f, y, BUTTON_HEIGHT,
                Theme.FONT_SMALL, color, false);
    }

    private String pageTitle() {
        if (page == rootPage) {
            return rootTitle;
        }
        return switch (page) {
            case INTERACTIONS -> Format.day(currentDay);
            case THREAD -> threadInteraction.player() != null ? threadInteraction.player() : "Unknown player";
            default -> rootTitle;
        };
    }

    private String pageSubtitle() {
        if (page == Page.DAYS) {
            int total = dayRows.stream().mapToInt(PeriodTotal::count).sum();
            return Format.count(total) + " helped";
        }
        if (page == rootPage) {
            return rootSubtitle;
        }
        return switch (page) {
            case INTERACTIONS -> Format.count(currentDayCount) + " helped";
            case THREAD -> Format.time(threadInteraction.time());
            default -> rootSubtitle;
        };
    }

    /** Handles a click. Returns true when the popup should close. */
    public boolean mouseClicked(int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (isOverConfirm(screenWidth, screenHeight, mouseX, mouseY)) {
            onConfirm.run();
            return true;
        }
        if (isOverCancel(screenWidth, screenHeight, mouseX, mouseY)) {
            return true;
        }
        if (page != rootPage && isOverBack(screenWidth, screenHeight, mouseX, mouseY)) {
            goBack();
            return false;
        }

        int bodyX = cardX(screenWidth) + PADDING;
        int bodyY = cardY(screenHeight) + 24;
        int boxWidth = WIDTH - 2 * PADDING;
        int bodyHeight = buttonY(screenHeight) - 6 - bodyY;
        int row = rowAt(bodyX, bodyY, boxWidth, bodyHeight, mouseX, mouseY);
        if (row >= 0 && page == Page.DAYS) {
            openDay(dayRows.get(row));
            return false;
        }
        if (row >= 0 && page == Page.INTERACTIONS) {
            openThread(interactionRows.get(row));
            return false;
        }

        // Clicking off the card cancels.
        int x = cardX(screenWidth);
        int y = cardY(screenHeight);
        return mouseX < x || mouseX >= x + WIDTH || mouseY < y || mouseY >= y + HEIGHT;
    }

    private void openDay(PeriodTotal row) {
        currentDay = row.start();
        currentDayCount = row.count();
        interactionRows = InteractionLog.forDay(currentDay).reversed();
        page = Page.INTERACTIONS;
        scroll = 0;
    }

    private void openThread(Interaction interaction) {
        threadInteraction = interaction;
        thread = ChatThread.build(interaction, bodyInnerWidth());
        page = Page.THREAD;
        scroll = 0;
    }

    private void goBack() {
        page = page == Page.THREAD && rootPage != Page.THREAD
                ? Page.INTERACTIONS
                : rootPage;
        scroll = 0;
    }

    public void mouseScrolled(int screenHeight, double amount) {
        int bodyHeight = buttonY(screenHeight) - 6 - (cardY(screenHeight) + 24);
        int maxScroll = Math.max(0, contentHeight() - bodyHeight);
        scroll = MathHelper.clamp(scroll - amount * 12, 0, maxScroll);
    }

    private int contentHeight() {
        return switch (page) {
            case DAYS -> dayRows.size() * ROW_HEIGHT + 2;
            case INTERACTIONS -> interactionRows.size() * ROW_HEIGHT + 2;
            case THREAD -> thread.height() + 10;
            case NOTE -> 0;
        };
    }

    /** The row index under the mouse, or -1 when outside the body rows. */
    private int rowAt(int bodyX, int bodyY, int bodyWidth, int bodyHeight, int mouseX, int mouseY) {
        if (page != Page.DAYS && page != Page.INTERACTIONS) {
            return -1;
        }
        if (mouseX < bodyX || mouseX >= bodyX + bodyWidth || mouseY < bodyY || mouseY >= bodyY + bodyHeight) {
            return -1;
        }
        int index = (mouseY - bodyY - 1 + (int) scroll) / ROW_HEIGHT;
        int count = page == Page.DAYS ? dayRows.size() : interactionRows.size();
        return index >= 0 && index < count ? index : -1;
    }

    /** The width chat bubbles lay out in, inset a little from the box edges. */
    private static int bodyInnerWidth() {
        return WIDTH - 2 * PADDING - 14;
    }

    private int cardX(int screenWidth) {
        return (screenWidth - WIDTH) / 2;
    }

    private int cardY(int screenHeight) {
        return (screenHeight - HEIGHT) / 2;
    }

    private int buttonY(int screenHeight) {
        return cardY(screenHeight) + HEIGHT - BUTTON_HEIGHT - 8;
    }

    private int cancelX(int screenWidth) {
        return cardX(screenWidth) + WIDTH - PADDING - 2 * BUTTON_WIDTH - 6;
    }

    private int confirmX(int screenWidth) {
        return cardX(screenWidth) + WIDTH - PADDING - BUTTON_WIDTH;
    }

    private boolean isOverBack(int screenWidth, int screenHeight, int mouseX, int mouseY) {
        int x = cardX(screenWidth);
        int y = cardY(screenHeight);
        return mouseX >= x + PADDING - 4 && mouseX < x + PADDING + 10 && mouseY >= y + 4 && mouseY < y + 20;
    }

    private boolean isOverCancel(int screenWidth, int screenHeight, int mouseX, int mouseY) {
        return isOverButton(cancelX(screenWidth), buttonY(screenHeight), mouseX, mouseY);
    }

    private boolean isOverConfirm(int screenWidth, int screenHeight, int mouseX, int mouseY) {
        return isOverButton(confirmX(screenWidth), buttonY(screenHeight), mouseX, mouseY);
    }

    private static boolean isOverButton(int x, int y, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + BUTTON_WIDTH && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
    }
}
