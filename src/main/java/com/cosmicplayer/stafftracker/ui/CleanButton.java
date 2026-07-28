package com.cosmicplayer.stafftracker.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * A flat text button. A soft pill appears behind it on hover or keyboard focus.
 */
public class CleanButton extends CleanWidget {
    public enum Style {
        NORMAL, ACCENT, DANGER
    }

    public interface PressAction {
        void onPress(CleanButton button);
    }

    private final PressAction action;
    private Style style = Style.NORMAL;
    private boolean glass;
    private String label;

    public CleanButton(int x, int y, int width, int height, String label, PressAction action) {
        super(x, y, width, height, Text.literal(label));
        this.label = label;
        this.action = action;
    }

    public CleanButton withStyle(Style style) {
        this.style = style;
        return this;
    }

    /** Dark translucent fill with a faint border, like tinted glass over the panel. */
    public CleanButton glass() {
        this.glass = true;
        return this;
    }

    public void setLabel(String label) {
        this.label = label;
        setMessage(Text.literal(label));
    }

    @Override
    protected void onPress() {
        action.onPress(this);
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean highlighted = isHovered() || isFocused();
        if (glass) {
            Theme.roundedRect(context, getX(), getY(), width, height, 4, highlighted ? 0x66000000 : 0x4D000000);
            Theme.roundedRectOutline(context, getX(), getY(), width, height, 4,
                    highlighted ? 0x46FFFFFF : 0x28FFFFFF, 0.5f);
        } else if (highlighted) {
            Theme.roundedRect(context, getX(), getY(), width, height, 4, Theme.HOVER);
        }
        int color = switch (style) {
            case NORMAL -> Theme.TEXT;
            case ACCENT -> Theme.ACCENT;
            case DANGER -> Theme.DANGER;
        };
        TextPainter.drawCenteredInRow(context, label, getX() + width / 2.0f, getY(), height,
                Theme.FONT_BODY, color, false);
    }
}
