package com.cosmicplayer.stafftracker.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import java.util.function.IntConsumer;

/**
 * A switch with a few options in a track. The thumb slides to the
 * selected one. With a label it renders as a settings row with the track
 * on the right. Without one the track centers in the widget bounds.
 */
public class SegmentedControl extends CleanWidget {
    private static final int TRACK_HEIGHT = 14;
    private static final int THUMB_INSET = 2;

    private final String label;
    private final String[] options;
    private final IntConsumer onSelect;
    private int selected;
    private float thumb;

    public SegmentedControl(int x, int y, int width, int height, String label, String[] options,
                            int selected, IntConsumer onSelect) {
        super(x, y, width, height, Theme.text(label != null ? label : options[selected]));
        this.label = label;
        this.options = options;
        this.selected = selected;
        this.thumb = selected;
        this.onSelect = onSelect;
    }

    @Override
    public void onClick(Click click, boolean doubled) {
        if (click.x() < trackX()) {
            return;
        }
        int segment = (int) ((click.x() - trackX()) / segmentWidth());
        selected = MathHelper.clamp(segment, 0, options.length - 1);
        onSelect.accept(selected);
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        if (label != null) {
            context.drawText(textRenderer, getMessage(), getX() + 2, getY() + (height - 8) / 2 + 1, Theme.TEXT, false);
        }

        int trackX = trackX();
        int trackY = getY() + (height - TRACK_HEIGHT) / 2;
        int trackWidth = trackWidth();
        int segmentWidth = segmentWidth();
        Theme.roundedRect(context, trackX, trackY, trackWidth, TRACK_HEIGHT, 4, Theme.TRACK_DARK);

        // Thin separators between segments. Skipped beside the thumb so it reads as one piece.
        for (int i = 1; i < options.length; i++) {
            if (i == selected || i == selected + 1) {
                continue;
            }
            int lineX = trackX + i * segmentWidth();
            context.fill(lineX, trackY + 4, lineX + 1, trackY + TRACK_HEIGHT - 4, 0x2EFFFFFF);
        }

        thumb = MathHelper.lerp(0.4f, thumb, selected);
        int thumbX = trackX + THUMB_INSET + Math.round(thumb * segmentWidth);
        int thumbHeight = TRACK_HEIGHT - 2 * THUMB_INSET;
        Theme.roundedRect(context, thumbX, trackY + THUMB_INSET, segmentWidth - 2 * THUMB_INSET,
                thumbHeight, 3, Theme.THUMB);

        for (int i = 0; i < options.length; i++) {
            int centerX = trackX + i * segmentWidth + segmentWidth / 2;
            int color = i == selected ? Theme.TEXT : Theme.TEXT_DIM;
            Theme.drawCenteredText(context, textRenderer, Theme.textSmall(options[i]), centerX, trackY + 3, color);
        }
    }

    private int trackX() {
        return label == null
                ? getX() + (width - trackWidth()) / 2
                : getX() + width - trackWidth();
    }

    /** Always a multiple of the segment width, so no remainder pools on one side. */
    private int trackWidth() {
        return segmentWidth() * options.length;
    }

    private int segmentWidth() {
        if (label == null) {
            return width / options.length;
        }
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        int widest = 0;
        for (String option : options) {
            widest = Math.max(widest, textRenderer.getWidth(Theme.textSmall(option)));
        }
        return widest + 16;
    }
}
