package com.cosmicplayer.stafftracker.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** One item in the dock's left rail. A dot, a single line label, and a pill when active. */
public class RailButton extends CleanWidget {
    private static final int DOT_SIZE = 3;

    private final String label;
    private final Runnable action;
    private final boolean active;

    public RailButton(int x, int y, int width, int height, String label, boolean active, Runnable action) {
        super(x, y, width, height, Text.literal(label));
        this.label = label;
        this.active = active;
        this.action = action;
    }

    @Override
    protected void onPress() {
        action.run();
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        if (active) {
            Theme.roundedRect(context, getX(), getY(), width, height, 5, Theme.HOVER);
        }
        int textColor = active || isHovered() || isFocused() ? Theme.TEXT : Theme.TEXT_DIM;
        int dotColor = active ? Theme.ACCENT : Theme.TEXT_DIM;
        int dotY = getY() + (height - DOT_SIZE) / 2;
        Theme.roundedRect(context, getX() + 7, dotY, DOT_SIZE, DOT_SIZE, 1, dotColor);

        TextPainter.drawInRow(context, label, getX() + 14, getY(), height, Theme.FONT_BODY, textColor, false);
    }
}
