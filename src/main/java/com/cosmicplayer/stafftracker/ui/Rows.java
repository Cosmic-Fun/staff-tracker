package com.cosmicplayer.stafftracker.ui;

import com.cosmicplayer.stafftracker.InteractionLog.Interaction;
import net.minecraft.client.gui.DrawContext;

/**
 * Shared row drawing for the history lists and the delete popups, so the
 * same data always lays out the same way in both.
 */
final class Rows {
    /** Player head size inside a row. */
    static final int HEAD_SIZE = 12;

    private static final int STRIPE = 0x07FFFFFF;

    private Rows() {
    }

    /** The faint zebra stripe behind every other row. */
    static void stripe(DrawContext context, int index, int x, int y, int width, int rowHeight) {
        if (index % 2 == 1) {
            context.fill(x, y, x + width, y + rowHeight, STRIPE);
        }
    }

    /**
     * The left side of an interaction row: when it happened, the player's
     * head, then their name. Unknown players get the placeholder head.
     */
    static void interaction(DrawContext context, Interaction row, String when, float whenX, int headX,
                            int y, int rowHeight) {
        TextPainter.drawInRow(context, when, whenX, y, rowHeight, Theme.FONT_SMALL, Theme.TEXT_DIM, false);
        int headY = y + (rowHeight - HEAD_SIZE) / 2;
        if (row.player() != null) {
            PlayerHeads.draw(context, row.player(), headX, headY, HEAD_SIZE);
        } else {
            PlayerHeads.drawUnknown(context, headX, headY, HEAD_SIZE);
        }
        String name = row.player() != null ? row.player() : "Unknown player";
        TextPainter.drawInRow(context, name, headX + HEAD_SIZE + 5, y, rowHeight, Theme.FONT_BODY, Theme.TEXT, false);
    }

    /** Small dim text ending at the given right edge, like a planet name. */
    static void rightDetail(DrawContext context, String text, float rightEdge, int y, int rowHeight) {
        float x = rightEdge - TextPainter.width(text, Theme.FONT_SMALL, false);
        TextPainter.drawInRow(context, text, x, y, rowHeight, Theme.FONT_SMALL, Theme.TEXT_DIM, false);
    }
}
