package com.cosmicplayer.stafftracker.ui;

import com.cosmicplayer.stafftracker.InteractionLog.Interaction;
import com.cosmicplayer.stafftracker.InteractionLog.LoggedMessage;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * A conversation laid out as chat bubbles. The player's messages sit on
 * the left with their head icon, the staff member's on the right in the
 * accent tint. Built once for a width, then drawn wherever needed.
 */
public class ChatThread {
    private static final int AVATAR_SIZE = 12;
    private static final int AVATAR_GAP = 4;
    private static final int OPPOSITE_MARGIN = 30;
    private static final int BUBBLE_PADDING_X = 5;
    private static final int BUBBLE_PADDING_Y = 4;
    private static final int LINE_HEIGHT = 10;
    private static final int CAPTION_HEIGHT = 10;
    private static final int MESSAGE_GAP = 5;
    private static final int INCOMING_BUBBLE = 0x16FFFFFF;

    /** One bubble with its caption. The caption is null inside a run from the same side. */
    private record Bubble(boolean outgoing, String caption, boolean showAvatar,
                          List<String> lines, int bubbleWidth) {}

    private final String player;
    private final List<Bubble> bubbles = new ArrayList<>();
    private final int width;
    private int height;

    private ChatThread(String player, int width) {
        this.player = player;
        this.width = width;
    }

    /** Lays out an interaction's messages for the given area width. */
    public static ChatThread build(Interaction interaction, int width) {
        ChatThread thread = new ChatThread(interaction.player(), width);
        int maxTextWidth = width - AVATAR_SIZE - AVATAR_GAP - OPPOSITE_MARGIN - 2 * BUBBLE_PADDING_X;

        boolean lastOutgoing = false;
        boolean first = true;
        for (LoggedMessage message : interaction.messages()) {
            boolean newRun = first || message.outgoing() != lastOutgoing;
            String caption = newRun
                    ? (message.outgoing() ? "Me" : interaction.player()) + "  ·  " + message.planet()
                            + "  ·  " + Format.time(message.time())
                    : null;

            List<String> lines = TextPainter.wrap(message.text(), Theme.FONT_BODY, false, maxTextWidth);
            float textWidth = 0;
            for (String line : lines) {
                textWidth = Math.max(textWidth, TextPainter.width(line, Theme.FONT_BODY, false));
            }
            thread.bubbles.add(new Bubble(message.outgoing(), caption, newRun && !message.outgoing(),
                    lines, Math.round(textWidth) + 2 * BUBBLE_PADDING_X));

            thread.height += (caption != null ? CAPTION_HEIGHT : 0)
                    + lines.size() * LINE_HEIGHT + 2 * BUBBLE_PADDING_Y + MESSAGE_GAP;
            lastOutgoing = message.outgoing();
            first = false;
        }
        return thread;
    }

    public int height() {
        return height;
    }

    /** Draws the thread top down from the given position. The caller sets the scissor. */
    public void draw(DrawContext context, int x, int y) {
        int leftX = x + AVATAR_SIZE + AVATAR_GAP;

        for (Bubble bubble : bubbles) {
            if (bubble.caption() != null) {
                float captionX = bubble.outgoing()
                        ? x + width - TextPainter.width(bubble.caption(), Theme.FONT_SMALL, false)
                        : leftX + 1;
                TextPainter.draw(context, bubble.caption(), captionX, y + 1, Theme.FONT_SMALL, Theme.TEXT_DIM, false);
                y += CAPTION_HEIGHT;
            }

            int bubbleHeight = bubble.lines().size() * LINE_HEIGHT + 2 * BUBBLE_PADDING_Y;
            int bubbleX = bubble.outgoing() ? x + width - bubble.bubbleWidth() : leftX;
            int color = bubble.outgoing() ? Theme.withAlpha(Theme.ACCENT, 0x3C) : INCOMING_BUBBLE;
            Theme.roundedRect(context, bubbleX, y, bubble.bubbleWidth(), bubbleHeight, 5, color);

            if (bubble.showAvatar() && player != null) {
                PlayerHeads.draw(context, player, x, y, AVATAR_SIZE);
            }

            int lineY = y + BUBBLE_PADDING_Y + 1;
            for (String line : bubble.lines()) {
                TextPainter.draw(context, line, bubbleX + BUBBLE_PADDING_X, lineY, Theme.FONT_BODY, Theme.TEXT, false);
                lineY += LINE_HEIGHT;
            }
            y += bubbleHeight + MESSAGE_GAP;
        }
    }
}
