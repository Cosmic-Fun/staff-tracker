package com.cosmicplayer.stafftracker.ui;

import com.cosmicplayer.stafftracker.HelpData;
import com.cosmicplayer.stafftracker.HelpData.PeriodTotal;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.time.LocalDate;
import java.util.List;

/**
 * The History section of the dock window. A list of periods
 * with their counts. Clicking a week or month row goes into its
 * days, and the header chevron backs out again.
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
    private static final int SCROLLBAR_GUTTER = 6;
    private static final int STRIPE = 0x07FFFFFF;
    private static final int BORDER = 0x26FFFFFF;

    private int tab = TAB_DAYS;
    private List<PeriodTotal> entries = List.of();
    private List<PeriodTotal> detailDays;
    private String detailTitle;
    private double scroll;

    // Hovering a clickable row slides its chevron out.
    private int hoveredRow = -1;
    private float chevronSlide;

    public HistoryPanel(int x, int y, int width, int height) {
        super(x, y, width, height, Theme.text("History"));
        refresh();
    }

    public void setTab(int tab) {
        this.tab = tab;
        this.detailDays = null;
        refresh();
    }

    private void refresh() {
        HelpData data = HelpData.get();
        entries = switch (tab) {
            case TAB_DAYS -> data.days();
            case TAB_WEEKS -> data.weeks();
            default -> data.months();
        };
        scroll = 0;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        if (detailDays != null) {
            drawDetailHeader(context, mouseX, mouseY);
        }
        drawList(context, mouseX, mouseY);
    }

    /** A plain back chevron, the period title, and its total. The chevron is the click target. */
    private void drawDetailHeader(DrawContext context, int mouseX, int mouseY) {
        TextRenderer textRenderer = textRenderer();
        int textY = getY() + 3;
        int chevronColor = isOverBack(mouseX, mouseY) ? Theme.ACCENT : Theme.TEXT;
        context.drawText(textRenderer, Theme.textBold("‹"), getX() + 3, textY, chevronColor, false);
        context.drawText(textRenderer, Theme.text(detailTitle), getX() + BACK_WIDTH + 2, textY, Theme.TEXT, false);

        int detailTotal = detailDays.stream().mapToInt(PeriodTotal::count).sum();
        Text total = Theme.textSmall(Format.count(detailTotal) + " players helped");
        int totalX = getX() + boxWidth() - 4 - textRenderer.getWidth(total);
        context.drawText(textRenderer, total, totalX, textY, Theme.TEXT_DIM, false);
    }

    private void drawList(DrawContext context, int mouseX, int mouseY) {
        TextRenderer textRenderer = textRenderer();
        List<PeriodTotal> rows = detailDays != null ? detailDays : entries;
        int listY = listTop();
        int listHeight = listHeight();

        Theme.roundedRectOutline(context, getX(), listY, boxWidth(), listHeight, 5, BORDER, 0.5f);

        if (rows.isEmpty()) {
            Theme.drawCenteredText(context, textRenderer, Theme.text("No players helped yet."),
                    getX() + boxWidth() / 2, listY + listHeight / 2 - 4, Theme.TEXT_DIM);
            return;
        }

        boolean clickable = detailDays == null && tab != TAB_DAYS;
        int hovered = clickable ? rowAt(mouseX, mouseY) : -1;
        if (hovered != hoveredRow) {
            hoveredRow = hovered;
            chevronSlide = 0.0f;
        }
        chevronSlide = MathHelper.lerp(0.35f, chevronSlide, hoveredRow >= 0 ? 1.0f : 0.0f);

        // Stripes run flush to the border so the list reads as one box.
        context.enableScissor(getX() + BOX_INSET, listY + BOX_INSET,
                getX() + boxWidth() - BOX_INSET, listY + listHeight - BOX_INSET);
        int y = listY + BOX_INSET - (int) scroll;
        for (int i = 0; i < rows.size(); i++, y += ROW_HEIGHT) {
            if (y + ROW_HEIGHT < listY || y > listY + listHeight) {
                continue;
            }
            if (i % 2 == 1) {
                context.fill(rowX(), y, rowX() + rowWidth(), y + ROW_HEIGHT, STRIPE);
            }
            drawRow(context, textRenderer, rows.get(i), y, clickable, i == hoveredRow ? chevronSlide : 0.0f);
        }
        context.disableScissor();
        drawScrollbar(context, rows.size(), listY, listHeight);
    }

    private void drawRow(DrawContext context, TextRenderer textRenderer, PeriodTotal row, int y,
                         boolean clickable, float slide) {
        // Primary and secondary share one y so their baselines line up.
        int textY = y + (ROW_HEIGHT - 8) / 2;

        // The current period gets a small accent dot in front of its label.
        if (isCurrentPeriod(row.start())) {
            Theme.roundedRect(context, rowX() + 5, y + ROW_HEIGHT / 2 - 1, 3, 3, 1, Theme.ACCENT);
        }
        Text primary = Theme.text(primaryLabel(row.start()));
        context.drawText(textRenderer, primary, rowX() + LABEL_X, textY, Theme.TEXT, false);

        String secondary = secondaryLabel(row.start());
        if (secondary != null) {
            int secondaryX = rowX() + LABEL_X + textRenderer.getWidth(primary) + 6;
            context.drawText(textRenderer, Theme.textSmall(secondary), secondaryX, textY, Theme.TEXT_DIM, false);
        }

        int rightEdge = rowX() + rowWidth() - 5;
        if (clickable) {
            Text chevron = Theme.textSmall("›");
            int chevronX = rightEdge - textRenderer.getWidth(chevron) + Math.round(slide * 3);
            int chevronColor = Theme.lerpColor(Theme.TEXT_DIM, Theme.TEXT, slide);
            context.drawText(textRenderer, chevron, chevronX, textY, chevronColor, false);
            rightEdge -= textRenderer.getWidth(chevron) + 6;
        }
        boolean zero = row.count() == 0;
        Text count = Theme.textBold(Format.count(row.count()));
        context.drawText(textRenderer, count, rightEdge - textRenderer.getWidth(count), textY,
                zero ? Theme.TEXT_DIM : Theme.TEXT, false);
    }

    /** Adds a scrollbar in its own little gutter to the right of the bordered box. */
    private void drawScrollbar(DrawContext context, int rowCount, int listY, int listHeight) {
        int contentHeight = rowCount * ROW_HEIGHT + 2 * BOX_INSET;
        if (contentHeight <= listHeight) {
            return;
        }
        int barHeight = Math.max(10, listHeight * listHeight / contentHeight);
        int barY = listY + (int) (scroll / (contentHeight - listHeight) * (listHeight - barHeight));
        Theme.roundedRect(context, getX() + width - 2, barY, 2, barHeight, 1, 0x40FFFFFF);
    }

    private String primaryLabel(LocalDate start) {
        LocalDate today = LocalDate.now();
        if (detailDays != null) {
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
        if (detailDays != null) {
            return start.equals(today) ? Format.fullDay(start) : null;
        }
        return switch (tab) {
            case TAB_DAYS -> start.isAfter(today.minusDays(2)) ? Format.fullDay(start) : null;
            case TAB_WEEKS -> start.equals(HelpData.weekStart(today)) ? Format.weekRange(start) : null;
            default -> start.equals(today.withDayOfMonth(1)) ? Format.monthOnly(start) : null;
        };
    }

    /** True when the row covers today, this week, or this month, matching the tab. */
    private boolean isCurrentPeriod(LocalDate start) {
        LocalDate today = LocalDate.now();
        if (detailDays != null) {
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
        if (detailDays != null) {
            if (isOverBack((int) click.x(), (int) click.y())) {
                detailDays = null;
                scroll = 0;
            }
            return;
        }
        int index = rowAt((int) click.x(), (int) click.y());
        if (tab != TAB_DAYS && index >= 0) {
            openDetail(entries.get(index));
        }
    }

    private void openDetail(PeriodTotal row) {
        LocalDate start = row.start();
        LocalDate end;
        if (tab == TAB_WEEKS) {
            detailTitle = Format.weekRange(start);
            end = start.plusDays(6);
        } else {
            detailTitle = Format.month(start);
            end = start.withDayOfMonth(start.lengthOfMonth());
        }
        detailDays = HelpData.get().daysBetween(start, end);
        scroll = 0;
    }

    /** Scroll the list. Called by the screen so the mouse wheel works anywhere over the panel. */
    public void scrollBy(double amount) {
        List<PeriodTotal> rows = detailDays != null ? detailDays : entries;
        int maxScroll = Math.max(0, rows.size() * ROW_HEIGHT + 2 * BOX_INSET - listHeight());
        scroll = MathHelper.clamp(scroll - amount * ROW_HEIGHT, 0, maxScroll);
    }

    private boolean isOverBack(int mouseX, int mouseY) {
        return mouseX >= getX() && mouseX < getX() + BACK_WIDTH
                && mouseY >= getY() && mouseY < getY() + HEADER_HEIGHT;
    }

    /** The row index under the mouse, or -1 when outside the list box. */
    private int rowAt(int mouseX, int mouseY) {
        List<PeriodTotal> rows = detailDays != null ? detailDays : entries;
        int listY = listTop();
        if (mouseX < getX() || mouseX >= getX() + boxWidth()
                || mouseY < listY + BOX_INSET || mouseY >= listY + listHeight()) {
            return -1;
        }
        int index = (mouseY - listY - BOX_INSET + (int) scroll) / ROW_HEIGHT;
        return index < rows.size() ? index : -1;
    }

    /** The bordered box stops short of the widget edge so the scrollbar overlaps nothing. */
    private int boxWidth() {
        return width - SCROLLBAR_GUTTER;
    }

    private int rowX() {
        return getX() + BOX_INSET;
    }

    private int rowWidth() {
        return boxWidth() - 2 * BOX_INSET;
    }

    /** The list fills the panel. The drill down header only takes room when open. */
    private int listTop() {
        return detailDays != null ? getY() + HEADER_HEIGHT + LIST_GAP : getY();
    }

    private int listHeight() {
        return getY() + height - listTop();
    }

    private static TextRenderer textRenderer() {
        return MinecraftClient.getInstance().textRenderer;
    }
}
