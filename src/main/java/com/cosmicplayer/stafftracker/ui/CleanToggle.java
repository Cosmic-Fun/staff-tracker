package com.cosmicplayer.stafftracker.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.function.Consumer;

/** A settings row with a label on the left and a small animated pill switch on the right. */
public class CleanToggle extends CleanWidget {
    private static final int PILL_WIDTH = 18;
    private static final int PILL_HEIGHT = 10;
    private static final int KNOB_SIZE = 6;

    private final String label;
    private final Consumer<Boolean> onChange;
    private boolean value;
    private float knob;

    public CleanToggle(int x, int y, int width, int height, String label, boolean value, Consumer<Boolean> onChange) {
        super(x, y, width, height, Text.literal(label));
        this.label = label;
        this.value = value;
        this.knob = value ? 1.0f : 0.0f;
        this.onChange = onChange;
    }

    @Override
    protected void onPress() {
        value = !value;
        onChange.accept(value);
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        TextPainter.drawInRow(context, label, getX() + 2, getY(), height, Theme.FONT_BODY, Theme.TEXT, false);

        knob = MathHelper.lerp(0.4f, knob, value ? 1.0f : 0.0f);
        int pillX = getX() + width - 2 - PILL_WIDTH;
        int pillY = getY() + (height - PILL_HEIGHT) / 2;
        int track = Theme.lerpColor(Theme.TRACK, Theme.ACCENT, knob);
        Theme.roundedRect(context, pillX, pillY, PILL_WIDTH, PILL_HEIGHT, PILL_HEIGHT / 2, track);

        int knobX = pillX + 2 + Math.round((PILL_WIDTH - 4 - KNOB_SIZE) * knob);
        Theme.roundedRect(context, knobX, pillY + 2, KNOB_SIZE, KNOB_SIZE, KNOB_SIZE / 2, Theme.TEXT);
    }
}
